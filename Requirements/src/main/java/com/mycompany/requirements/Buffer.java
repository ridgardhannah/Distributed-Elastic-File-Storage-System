package com.mycompany.requirements;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.lingala.zip4j.exception.ZipException;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;

public class Buffer {

    private static final Logger logger = req19_Logging.getLogger(Buffer.class);

    private final String broker;
    private final String clientId;
    private final MqttClient mqttClient;

    // Topics 
    private final String requestTopic = "maincontainer/sendfile";
    private final String taskQueueTopic = "processing/queue";
    private final String ackTopic = "loadBalancer/ack";
    private final String retrieveTopic = "buffer/retrieve";

    // Folder paths
    private final String MAIN_FOLDER = "/home/ntu-user/NetBeansProjects/FileTempFolder/";
    private final String RECEIVED_FOLDER = "/home/ntu-user/NetBeansProjects/BufferFolder/";
    private final String BUFFER_FOLDER = "/home/ntu-user/NetBeansProjects/BufferFolder/";
    private final String DECRYPTED_FOLDER = "/home/ntu-user/NetBeansProjects/BufferFolder/DecryptedFile/";

    // MySQL connection parameters for storing chunk info.
    private static final String MYSQL_CONNECTION_STRING = "jdbc:mysql://172.18.0.2:3306/MysqlUsers";
    private static final String MYSQL_USERNAME = "admin";
    private static final String MYSQL_PASSWORD = "Friday14!";

    public Buffer(String broker, String clientId) throws MqttException {
        this.broker = broker;
        this.clientId = clientId;

        mqttClient = new MqttClient(broker, clientId, new MemoryPersistence());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        mqttClient.connect(options);
        logger.log(Level.INFO, "Connected to MQTT broker: {0}", broker);

        // Subscribe to the file creation/deletion task queue.
        mqttClient.subscribe(taskQueueTopic);
        mqttClient.subscribe("buffer/delete");

        // Subscribe to the retrieval topic for update requests.
        mqttClient.subscribe(retrieveTopic, new IMqttMessageListener() {
            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                String payload = new String(message.getPayload());
                JSONObject json = new JSONObject(payload);
                String action = json.getString("action");

                if ("retrieveChunksForUpdate".equals(action)) {
                    String fileName = json.getString("fileName");
                    logger.log(Level.INFO, "Received update retrieval request for file: {0}", fileName);
                    handleFileUpdateRequest(fileName);
                } else if ("processNewFile".equals(action)) {
                    String fileName = json.getString("fileName"); // Change from "filename" to "fileName"
                    logger.log(Level.INFO, "Received process new file request for file: {0}", fileName);
                   
                    sendFileRequest(fileName);
                } else if ("deleteFile".equals(json.getString("action"))) {
                    String fileName = json.getString("fileName");
                    logger.log(Level.INFO, "Received deletion request for file: {0}", fileName);
                    handleFileDeletionRequest(fileName);
                } else {
                    // If needed, you can add conditional handling for other actions.
                    logger.log(Level.INFO, "Received a message on buffer/retrieve with unknown action: {0}", action);
                }
            }
        });
        logger.log(Level.INFO, "Buffer subscribed to topic: {0}", retrieveTopic);

        // Set a global callback for messages not handled by specific subscriptions.
        mqttClient.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                cause.printStackTrace();
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                // This callback handles messages not assigned to specific listeners.
                logger.log(Level.INFO, "Global callback received message on topic {0}: {1}",
                        new Object[]{topic, new String(message.getPayload())});
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                /* Not used */ }
        });
    }

    //TASK INFO
    private void handleTaskMetadata(MqttMessage message) {
        try {
            String payload = new String(message.getPayload());
            JSONObject taskMetadata = new JSONObject(payload);
            String fileName = taskMetadata.getString("fileName");
            logger.log(Level.INFO, "Received task metadata for file: {0}", fileName);
            sendFileRequest(fileName);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error handling task metadata: {0}", e.getMessage());
        }
    }
    
    public void handleFileDeletionRequest(String fileName) {
    Lock lock = FileLockManager.acquireLock(fileName);
    try {
        logger.log(Level.INFO, "Lock acquired for deletion request for file: {0}", fileName);

        // Retrieve the file chunks from storage (assumes you have implemented this method)
        List<File> chunks = retrieveChunksFromStorage(fileName);
        if (chunks.isEmpty()) {
            logger.log(Level.WARNING, "No chunks found for file deletion: {0}", fileName);
            return;
        }
        
        // Iterate over each chunk and attempt to delete it
        for (File chunk : chunks) {
            if (chunk.exists()) {
                if (chunk.delete()) {
                    logger.log(Level.INFO, "Deleted chunk: {0} for file: {1}", new Object[]{chunk.getAbsolutePath(), fileName});
                } else {
                    logger.log(Level.WARNING, "Failed to delete chunk: {0}", chunk.getAbsolutePath());
                }
            } else {
                logger.log(Level.WARNING, "Chunk does not exist: {0}", chunk.getAbsolutePath());
            }
        }
        logger.log(Level.INFO, "Deletion completed for file: {0}", fileName);
    } catch (Exception e) {
        logger.log(Level.SEVERE, "Error during file deletion for file {0} - {1}", new Object[]{fileName, e.getMessage()});
        e.printStackTrace();
    } finally {
        FileLockManager.releaseLock(fileName);
        logger.log(Level.INFO, "Lock released for deletion of file: {0}", fileName);
    }
}


    //REQUEST FILE FROM MAIN
    public void sendFileRequest(String fileName) {
        try {
            logger.log(Level.INFO, "Requesting file from MainContainer: {0}", fileName);

            // Create JSON request for MainContainer
            JSONObject request = new JSONObject();
            request.put("filename", fileName); // Note: MainContainer expects lowercase "filename"

            MqttMessage mqttMessage = new MqttMessage(request.toString().getBytes());
            logger.info("Publishing file request to MainContainer on topic: " + requestTopic);
            mqttClient.publish(requestTopic, mqttMessage);
            logger.log(Level.INFO, "File request sent to MainContainer for: {0}", fileName);

            // Start a new thread to wait for the file to arrive
            new Thread(() -> {
                String filePath = BUFFER_FOLDER + fileName;
                long timeout = 120000; // Wait up to 120 seconds.
                if (waitForFile(filePath, timeout)) {
                    logger.log(Level.INFO, "File found in Buffer folder. SFTP transfer SUCCESS for file: {0}", fileName);
                    try {
                        JSONObject ackTemp = new JSONObject();
                        ackTemp.put("filename", fileName);
                        ackTemp.put("status", "File Received in Buffer");
                        ackTemp.put("timestamp", System.currentTimeMillis());
                        mqttClient.publish(ackTopic, new MqttMessage(ackTemp.toString().getBytes()));
                        logger.log(Level.INFO, "Published temporary ACK for file: {0}", fileName);

                        // Now process the file
                        handleFilePostTransfer(fileName);
                    } catch (MqttException me) {
                        logger.log(Level.SEVERE, "Error publishing temporary ACK for file: {0} - {1}",
                                new Object[]{fileName, me.getMessage()});
                    }
                } else {
                    logger.log(Level.WARNING, "Timeout waiting for file: {0} in Buffer folder.", fileName);
                }
            }).start();
        } catch (MqttException ex) {
            logger.log(Level.SEVERE, "Failed to send file request for: {0} - {1}",
                    new Object[]{fileName, ex.getMessage()});
            ex.printStackTrace();
        }
    }

    // Polling method to wait for the file.
    private boolean waitForFile(String filePath, long timeoutMillis) {
        long startTime = System.currentTimeMillis();
        File file = new File(filePath);
        while (!file.exists()) {
            if (System.currentTimeMillis() - startTime > timeoutMillis) {
                return false;
            }
            try {
                Thread.sleep(500); // Poll every 500 ms.
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
            file = new File(filePath);
        }
        return true;
    }

//WHEN FILE IS COPIED FROM MAIN TO BUFFER    
    public void handleFilePostTransfer(String fileName) {
        // Acquire a lock for the file to ensure no concurrent access
        Lock lock = FileLockManager.acquireLock(fileName);
        try {
            logger.log(Level.INFO, "Lock acquired for file: {0}", fileName);

            // 1. Determine the plain file path in BUFFER_FOLDER.
            String plainFilePath = BUFFER_FOLDER + fileName;
            File plainFile = new File(plainFilePath);
            if (!plainFile.exists()) {
                logger.log(Level.WARNING, "Plain file {0} does not exist for processing.", plainFilePath);
                return;
            }

            // 2. Encrypt the plain file before splitting.
            String encryptionPassword = "mySecretPass"; // Hardcoded password; you can change this later.
            try {
                // Encrypt the plain file; this creates a new file at plainFilePath + ".zip"
                req7.encrypt(plainFilePath, encryptionPassword);
                logger.log(Level.INFO, "Encryption complete. Encrypted file created: {0}.zip", plainFilePath);
            } catch (ZipException ze) {
                ze.printStackTrace();
                logger.log(Level.SEVERE, "Encryption failed for file: {0} - {1}", new Object[]{fileName, ze.getMessage()});
                return;
            }

            // 3. Update our reference for splitting to use the encrypted file.
            String encryptedFilePath = plainFilePath + ".zip";
            File fileForChunking = new File(encryptedFilePath);
            if (!fileForChunking.exists()) {
                logger.log(Level.WARNING, "Encrypted file {0} not found for chunking.", encryptedFilePath);
                return;
            }

            // 4. Split the encrypted file first
            req25 splitter = new req25();
            int numberOfChunks = 4;
            List<File> chunks = splitter.splitByNumberOfFiles(fileForChunking, numberOfChunks);
            logger.log(Level.INFO, "Encrypted file {0} split into {1} chunks.", new Object[]{fileName, chunks.size()});

            // 5. Now update MySQL with the actual chunk information
            for (int i = 0; i < chunks.size(); i++) {
                File chunk = chunks.get(i);
                String chunkName = chunk.getName();
                String targetDirPath = BUFFER_FOLDER + "Chunk" + (i + 1) + "/";
                File targetDir = new File(targetDirPath);
                if (!targetDir.exists()) {
                    targetDir.mkdirs();
                }
                String chunkLocation = targetDirPath + chunkName;
                updateChunkInfoMysql(chunkName, chunkLocation);
            }

            // 6. Move each chunk into its designated folder.
            for (int i = 0; i < chunks.size(); i++) {
                File chunk = chunks.get(i);
                String targetDirPath = "/home/ntu-user/NetBeansProjects/BufferFolder/Chunk" + (i + 1) + "/";
                File targetDir = new File(targetDirPath);
                if (!targetDir.exists()) {
                    targetDir.mkdirs();
                }
                File targetFile = new File(targetDir, chunk.getName());
                boolean moved = chunk.renameTo(targetFile);
                if (!moved) {
                    java.nio.file.Files.move(chunk.toPath(), targetFile.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                logger.log(Level.INFO, "Moved chunk {0} to {1}", new Object[]{chunk.getName(), targetFile.getAbsolutePath()});

            }

            // 7. Publish an MQTT ACK message indicating file splitting is complete.
            JSONObject ack = new JSONObject();
            ack.put("filename", fileName);
            ack.put("chunks", chunks.size());
            ack.put("status", "File Split Completed (encrypted)");
            mqttClient.publish(ackTopic, new MqttMessage(ack.toString().getBytes()));
            logger.log(Level.INFO, "Published final chunking ACK for file: {0}", fileName);

            // 8. Delete the encrypted file from BUFFER_FOLDER.
            if (java.nio.file.Files.deleteIfExists(fileForChunking.toPath())) {
                logger.log(Level.INFO, "Deleted encrypted file from BUFFER_FOLDER: {0}", encryptedFilePath);
            } else {
                logger.log(Level.WARNING, "Failed to delete encrypted file: {0}", encryptedFilePath);
            }

            // 9. Delete the original plain file from BUFFER_FOLDER.
            if (java.nio.file.Files.deleteIfExists(plainFile.toPath())) {
                logger.log(Level.INFO, "Deleted original plain file from BUFFER_FOLDER: {0}", plainFilePath);
            } else {
                logger.log(Level.WARNING, "Failed to delete original plain file: {0}", plainFilePath);
            }

            // 10. Send an MQTT message to the main container instructing it to delete the file from FileTempFolder.
            JSONObject deleteMsg = new JSONObject();
            deleteMsg.put("filename", fileName);
            deleteMsg.put("path", "/home/ntu-user/NetBeansProjects/FileTempFolder/" + fileName);
            mqttClient.publish("maincontainer/delete", new MqttMessage(deleteMsg.toString().getBytes()));
            logger.log(Level.INFO, "Sent deletion instruction to Main Container for file: {0}", fileName);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing file: {0} - {1}", new Object[]{fileName, e.getMessage()});
        } finally {
            // Release the lock to allow other threads to access the file
            FileLockManager.releaseLock(fileName);
            logger.log(Level.INFO, "Lock released for file: {0}", fileName);
        }
    }

// Returns the file base name (without extension)
    private String getBaseName(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex > 0) ? fileName.substring(0, dotIndex) : fileName;
    }

// Returns the extension (including dot) or an empty string if none
    private String getExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex > 0) ? fileName.substring(dotIndex) : "";
    }

    /**
     * Updates the MySQL database with chunk information for one chunk.
     *
     * @param chunkName The name of the chunk file
     * @param chunkLocation The location where the chunk is stored
     */
    private void updateChunkInfoMysql(String chunkName, String chunkLocation) {
        Connection conn = null;
        PreparedStatement pstmt = null;
        Statement stmt = null;

        try {
            // Explicitly load the MySQL JDBC driver
            Class.forName("com.mysql.cj.jdbc.Driver");

            // Establish connection
            conn = DriverManager.getConnection(MYSQL_CONNECTION_STRING, MYSQL_USERNAME, MYSQL_PASSWORD);
            logger.info("Connection to MySQL established for updating chunk info.");

            // Begin transaction
            conn.setAutoCommit(false);

            // Create the Chunks table if it does not exist
            String createTableSQL = "CREATE TABLE IF NOT EXISTS Chunks ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY, "
                    + "uuid VARCHAR(36) NOT NULL, "
                    + "chunk_name VARCHAR(255) NOT NULL, "
                    + "safe_password VARCHAR(255) NOT NULL, "
                    + "chunk_location VARCHAR(255) NOT NULL, "
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + "UNIQUE KEY unique_chunk (chunk_name)"
                    + ")";

            stmt = conn.createStatement();
            stmt.executeUpdate(createTableSQL);

            // Generate a UUID and a safe password
            String uuid = java.util.UUID.randomUUID().toString();
            String salt = "FileChunkingSalt2025";  // Use a more complex salt
            String safePassword = generateSafePassword(uuid, salt);

            // Prepare the insert statement
            String insertSQL = "INSERT INTO Chunks (uuid, chunk_name, safe_password, chunk_location) "
                    + "VALUES (?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE "
                    + "uuid = VALUES(uuid), "
                    + "safe_password = VALUES(safe_password), "
                    + "chunk_location = VALUES(chunk_location)";

            pstmt = conn.prepareStatement(insertSQL);
            pstmt.setString(1, uuid);
            pstmt.setString(2, chunkName);
            pstmt.setString(3, safePassword);
            pstmt.setString(4, chunkLocation);

            // Execute the insert
            int rowsAffected = pstmt.executeUpdate();

            // Commit the transaction
            conn.commit();

            logger.log(Level.INFO, "Chunk information updated in MySQL: {0} with UUID: {1} (Rows affected: {2})",
                    new Object[]{chunkName, uuid, rowsAffected});

        } catch (ClassNotFoundException e) {
            logger.log(Level.SEVERE, "MySQL JDBC Driver not found: {0}", e.getMessage());
            if (conn != null) {
                try {
                    conn.rollback();
                    logger.info("Transaction rolled back due to JDBC driver error");
                } catch (SQLException rollbackEx) {
                    logger.log(Level.SEVERE, "Error rolling back transaction: {0}", rollbackEx.getMessage());
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "SQL error updating chunk info: {0}", e.getMessage());
            if (conn != null) {
                try {
                    conn.rollback();
                    logger.info("Transaction rolled back due to SQL error");
                } catch (SQLException rollbackEx) {
                    logger.log(Level.SEVERE, "Error rolling back transaction: {0}", rollbackEx.getMessage());
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected error updating chunk info: {0}", e.getMessage());
            if (conn != null) {
                try {
                    conn.rollback();
                    logger.info("Transaction rolled back due to unexpected error");
                } catch (SQLException rollbackEx) {
                    logger.log(Level.SEVERE, "Error rolling back transaction: {0}", rollbackEx.getMessage());
                }
            }
        } finally {
            // Close resources in reverse order
            try {
                if (pstmt != null) {
                    pstmt.close();
                }
                if (stmt != null) {
                    stmt.close();
                }
                if (conn != null) {
                    conn.setAutoCommit(true); // Reset to default
                    conn.close();
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Error closing database resources: {0}", e.getMessage());
            }
        }
    }

    private String generateSafePassword(String uuid, String salt) throws Exception {
        String toHash = uuid + salt;
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(toHash.getBytes("UTF-8"));

        // Convert bytes to hex
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

//UPDATE FILE
    public void handleFileUpdateRequest(String fileName) {
        Lock lock = FileLockManager.acquireLock(fileName);
        try {
            logger.log(Level.INFO, "Lock acquired for update request of file: {0}", fileName);

            List<File> chunks = retrieveChunksFromStorage(fileName);
            if (chunks.isEmpty()) {
                logger.log(Level.WARNING, "No chunks found for file update: {0}", fileName);
                return;
            }
            logger.log(Level.INFO, "Retrieved {0} chunks for file: {1}",
                    new Object[]{chunks.size(), fileName});

            File reassembledEncryptedFile = reassembleChunks(chunks);
            if (!reassembledEncryptedFile.exists()) {
                logger.log(Level.WARNING, "Reassembled encrypted file does not exist for file: {0}", fileName);
                return;
            }
            logger.log(Level.INFO, "Reassembled encrypted file for {0} at: {1}",
                    new Object[]{fileName, reassembledEncryptedFile.getAbsolutePath()});

            // Decrypt the file (this writes the decrypted file to your DECRYPTED_FOLDER)
            req7.decrypt(reassembledEncryptedFile.getAbsolutePath(), "mySecretPass");
            logger.log(Level.INFO, "Decryption complete for file: {0}", fileName);

            // Determine the location of the decrypted file.
            String decryptedFilePath = DECRYPTED_FOLDER + "/" + fileName;
            File decryptedFile = new File(decryptedFilePath);
            if (!decryptedFile.exists()) {
                logger.log(Level.WARNING, "Decrypted file not found in: {0}", decryptedFilePath);
                return;
            }
            logger.log(Level.INFO, "Decrypted file ready at: {0}", decryptedFilePath);

            // Now call the method to open it in Mousepad.
//            var result = req4.openFileInMousepad(decryptedFile.toString());
//            logger.info(result);

            // Optionally, once the user is done editing and the file is reprocessed, 
            // you can continue further processing or notify the main container.
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing update for file {0} - {1}",
                    new Object[]{fileName, e.getMessage()});
            e.printStackTrace();
        } finally {
            FileLockManager.releaseLock(fileName);
            logger.log(Level.INFO, "Lock released for update of file: {0}", fileName);
        }
    }
    

    private List<File> retrieveChunksFromStorage(String fileName) {
        List<File> chunks = new ArrayList<>();
        int numberOfChunks = 4;
        for (int i = 1; i <= numberOfChunks; i++) {
            // Build the chunk file name to match "mmmmm.txt-chunk1.zip"
            String chunkName = fileName + "-chunk" + i + ".zip";
            // Use Paths.get for safer path construction
            String chunkPath = Paths.get(BUFFER_FOLDER, "Chunk" + i, chunkName)
                    .toString();

            logger.log(Level.INFO, "Checking for chunk {0} at path: {1}",
                    new Object[]{i, chunkPath});
            File chunkFile = new File(chunkPath);
            if (chunkFile.exists()) {
                chunks.add(chunkFile);
                logger.log(Level.INFO, "Chunk {0} found for file: {1}",
                        new Object[]{i, fileName});
            } else {
                logger.log(Level.WARNING, "Chunk {0} missing for file: {1}",
                        new Object[]{i, fileName});
            }
        }
        logger.log(Level.INFO, "Total chunks retrieved for file {0}: {1}",
                new Object[]{fileName, chunks.size()});
        return chunks;
    }

    private File reassembleChunks(List<File> chunks) throws IOException {
        String reassembledFilename = "reassembled_" + System.currentTimeMillis() + ".zip";
        Path reassembledPath = Paths.get(BUFFER_FOLDER, reassembledFilename);
        logger.log(Level.INFO, "Starting reassembly of {0} chunk(s) into file: {1}",
                new Object[]{chunks.size(), reassembledPath.toAbsolutePath()});

        try (OutputStream os = Files.newOutputStream(reassembledPath)) {
            for (File chunk : chunks) {
                logger.log(Level.INFO, "Appending chunk from: {0}",
                        chunk.getAbsolutePath());
                Files.copy(chunk.toPath(), os);
            }
        }

        if (Files.exists(reassembledPath)) {
            logger.log(Level.INFO, "Reassembly complete. Reassembled file at: {0}",
                    reassembledPath.toAbsolutePath());
        } else {
            logger.log(Level.WARNING, "Reassembled file not found at expected path: {0}",
                    reassembledPath.toAbsolutePath());
        }

        return reassembledPath.toFile();
    }

    private void sendFileToMainContainer(File file) {
        try {
            Path sourcePath = file.toPath();
            Path destinationPath = Paths.get(MAIN_FOLDER, file.getName());

            Files.copy(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING);

            logger.log(Level.INFO, "File sent to Temp folder: {0}", destinationPath.toString());
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to send file to Temp folder: {0}", e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        try {
            Buffer buffer = new Buffer("tcp://mqtt-broker:1883", "BufferClient");
            logger.info("Buffer is listening for tasks...");
        } catch (MqttException e) {
            logger.log(Level.SEVERE, "Error initializing Buffer: {0}", e.getMessage());
            e.printStackTrace();
        }
    }
}
