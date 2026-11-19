package com.mycompany.requirements;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.control.TextInputDialog;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.json.JSONObject;

public class req4 {

    private static final Logger logger = req19_Logging.getLogger(req4.class);

    private static final double HIGH_TRAFFIC_MULTIPLIER = 2.0;
    private static final double MEDIUM_TRAFFIC_MULTIPLIER = 1.6;
    private static final double LOW_TRAFFIC_MULTIPLIER = 1.2;
    private static final String MQTT_BROKER = "tcp://mqtt-broker:1883";
    private static final String LOAD_BALANCER_TOPIC = "loadBalancer/queue";
    
    private MQTTHelper mqttClient;  
    private static String trafficLevel = "medium"; // options: "high", "medium", "low"
    private Random random = new Random();
    public static final String DATABASE_URL = "jdbc:sqlite:/home/ntu-user/NetBeansProjects/Users";
    
    // The userManager field is stored as an instance field.
    private req15 userManager;

    // Constructor (inject the userManager)
    public req4(req15 userManager) {
        this.userManager = userManager;
        try {
            mqttClient = new MQTTHelper(MQTT_BROKER, "MainContainer");
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    // ----------------------------------------------------------------
    // FILE/DB CREATION METHODS
    // ----------------------------------------------------------------

    public void createFilesTable() throws SQLException {
        try (Connection connection = DriverManager.getConnection(DATABASE_URL)) {
            Statement statement = connection.createStatement();
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS Files ("
                    + "fileId INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "name VARCHAR(100) NOT NULL, "
                    + "location VARCHAR(100) NOT NULL, "
                    + "ownerId INTEGER REFERENCES userDetails(id) ON DELETE CASCADE)"
            );
            System.out.println("Table 'Files' created successfully (or already exists).");
        } catch (SQLException ex) {
            Logger.getLogger(req15.class.getName()).log(Level.SEVERE, null, ex);
            throw ex;
        }
    }

    public void createFileWithContent(String fileName, String fileContent, String fileLocation)
            throws SQLException, IOException {
        Lock lock = FileLockManager.acquireLock(fileName);
        try {
            logger.log(Level.INFO, "Attempting to create file: {0}", fileName);

            // 1. Database logic
            int ownerId = userManager.getUserId();
            // Force file storage in the temporary folder.
            fileLocation = "/home/ntu-user/NetBeansProjects/FileTempFolder/";
            int fileId = -1; 

            if (ownerId != -1) {
                try (Connection connection = DriverManager.getConnection(DATABASE_URL)) {
                    String sql = "INSERT INTO Files (name, location, ownerId) VALUES (?, ?, ?)";
                    try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                        statement.setString(1, fileName);
                        statement.setString(2, fileLocation);
                        statement.setInt(3, ownerId);
                        statement.executeUpdate();

                        // Retrieve the generated file ID.
                        ResultSet rs = statement.getGeneratedKeys();
                        if (rs.next()) {
                            fileId = rs.getInt(1);
                        }
                    }
                } catch (SQLException e) {
                    logger.log(Level.SEVERE, "Error interacting with the database: {0}", e.getMessage());
                    throw new SQLException("Error interacting with the database: " + e.getMessage(), e);
                }
            } else {
                logger.warning("User not logged in or user ID not found.");
                throw new SQLException("User not logged in or user ID not found.");
            }

            // 2. File System logic: write file content.
            String fullPath = fileLocation + fileName;
            try (FileWriter writer = new FileWriter(fullPath)) {
                writer.write(fileContent);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Error writing to file: {0}", e.getMessage());
                throw new IOException("Error writing to file: " + e.getMessage(), e);
            }

            logger.log(Level.INFO, "File created successfully: {0} at location: {1}", new Object[]{fileName, fileLocation});

            // Retrieve the actual file location from the database.
            fileLocation = getFileLocation(fileName);

            // 3. Adjust task delay based on traffic.
            long adjustedDelay = getAdjustedDelay(30000, 60000); // Base delay range of 30-60 seconds.

            // 4. Send Metadata via MQTT.
            sendFileMetadata(fileId, fileName, fileLocation, ownerId, adjustedDelay);
            logger.log(Level.INFO, "File metadata sent to load balancer: {0}", fileLocation);

            // 5. Simulate traffic on file creation with a real task and 10 dummy tasks.
            simulateTaskScheduling(fileName, adjustedDelay);

        } finally {
            FileLockManager.releaseLock(fileName);
            logger.log(Level.INFO, "Lock released for file: {0}", fileName);
        }
    }

    private long getAdjustedDelay(int delayStart, int delayEnd) {
        long baseDelay = delayStart + random.nextInt(delayEnd - delayStart); // Random delay in the base range.
        double multiplier;
        switch (trafficLevel.toLowerCase()) {
            case "high":
                multiplier = HIGH_TRAFFIC_MULTIPLIER;
                break;
            case "medium":
                multiplier = MEDIUM_TRAFFIC_MULTIPLIER;
                break;
            case "low":
                multiplier = LOW_TRAFFIC_MULTIPLIER;
                break;
            default:
                multiplier = 1.0;
                break;
        }
        return (long) (baseDelay * multiplier); // Return the adjusted delay.
    }

    private void sendFileMetadata(int fileId, String fileName, String fileLocation, int ownerId, long adjustedDelay) {
        try {
            JSONObject metadata = new JSONObject();
            metadata.put("action", "createFile");
            metadata.put("fileId", fileId);
            metadata.put("filename", fileName);
            metadata.put("location", fileLocation);
            metadata.put("ownerId", ownerId);
            metadata.put("adjustedDelay", adjustedDelay);
            mqttClient.publish(LOAD_BALANCER_TOPIC, metadata.toString());

            System.out.println("File metadata sent to Load Balancer: " + metadata);
        } catch (MqttException e) {
            System.err.println("Failed to send file metadata via MQTT: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Retrieves the file location (as stored in the database) for the given file.
    public String getFileLocation(String fileName) throws SQLException {
        String fileLocation = null;
        try (Connection connection = DriverManager.getConnection(DATABASE_URL)) {
            String query = "SELECT location FROM Files WHERE name = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setString(1, fileName);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        fileLocation = rs.getString("location");
                    } else {
                        throw new IllegalArgumentException("File not found.");
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new SQLException("Error retrieving file location.", e);
        }
        return fileLocation;
    }

    private void simulateTaskScheduling(String fileName, long realAdjustedDelay) {
        try {
            List<Task> tasks = new ArrayList<>();

            // Real file task with a fixed delay.
            tasks.add(new Task("Real File: " + fileName, realAdjustedDelay,
                    TrafficSimulation.TrafficType.MEDIUM, Task.TaskType.CREATE));

            // Add 10 dummy tasks.
            for (int i = 1; i <= 10; i++) {
                int dummyDelay = 30000 + random.nextInt(90000 - 60000);
                tasks.add(new Task("Dummy Task " + i, dummyDelay,
                        TrafficSimulation.TrafficType.MEDIUM, Task.TaskType.CREATE));
            }

            int numResources = 4;
            int timeSlice = 5000;

            // Simulate scheduling via various algorithms.
            long fcfs = SimpleSchedulingSimulator.simulateFCFS(tasks, numResources);
            long sjf = SimpleSchedulingSimulator.simulateSJF(tasks, numResources);
            long rr = SimpleSchedulingSimulator.simulateRR(tasks, numResources, timeSlice);
            System.out.println("Calculated Processing Times:");
            System.out.println("FCFS: " + fcfs + " ms");
            System.out.println("SJF:  " + sjf + " ms");
            System.out.println("RR:   " + rr + " ms");

            String chosenAlgorithm = SimpleSchedulingSimulator.determineBestAlgorithm(tasks, numResources, timeSlice);
            System.out.println("Chosen scheduling algorithm: " + chosenAlgorithm);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Common post-processing for a file (e.g. updating DB, re-sending metadata, re-scheduling tasks).
public void processFileTasks(String fileName) throws SQLException, IOException {
    int ownerId = userManager.getUserId();
    try (Connection connection = DriverManager.getConnection(DATABASE_URL)) {
        String sql = "UPDATE Files SET location = ? WHERE name = ? AND ownerId = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            String fileLocation = "/home/ntu-user/NetBeansProjects/FileTempFolder/";
            statement.setString(1, fileLocation);
            statement.setString(2, fileName);
            statement.setInt(3, ownerId);
            statement.executeUpdate();
        }
    }

    String fileLocation = getFileLocation(fileName);

    long adjustedDelay = getAdjustedDelay(30000, 60000);

    try (Connection connection = DriverManager.getConnection(DATABASE_URL)) {
        int fileId = getFileId(connection, fileName, fileLocation);
        sendFileMetadata(fileId, fileName, fileLocation, ownerId, adjustedDelay);
    }
    simulateTaskScheduling(fileName, adjustedDelay);

    logger.log(Level.INFO, "Processing complete for file: {0}", fileName);
}

// Remove static so that it becomes an instance method.
public String openFileInMousepad(String fileName) {
    String filePath = "/home/ntu-user/NetBeansProjects/FileTempFolder/" + fileName;
    File file = new File(filePath);
    if (!file.exists()) {
        return "Error: The file does not exist.";
    }
    try {
        // Launch Mousepad and wait for the user to finish editing.
        Process process = new ProcessBuilder("mousepad", file.getAbsolutePath()).start();
        process.waitFor();
        logger.log(Level.INFO, "User finished editing file: {0}", fileName);
        // Call the instance method processFileTasks.
        this.processFileTasks(fileName);
        return "File processed successfully after editing.";
    } catch (IOException | InterruptedException | SQLException e) {
        logger.log(Level.SEVERE, "Error during file editing and processing: {0}", e.getMessage());
        e.printStackTrace();
        return "Failed to process file: " + e.getMessage();
    }
}


    // Updates the file by sending an update request via MQTT.
    public void updateFile(String fileName) throws SQLException, IOException, MqttException {
        if (!userManager.isLoggedIn()) {
            throw new IOException("No user is logged in. Please log in first.");
        }
        int userId = userManager.getUserId();
        try (Connection connection = DriverManager.getConnection(DATABASE_URL)) {
            String sql = "SELECT location FROM Files WHERE name = ? AND ownerId = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, fileName);
                pstmt.setInt(2, userId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        JSONObject message = new JSONObject();
                        message.put("action", "updateFile");
                        message.put("fileName", fileName);
                        message.put("userId", userId);

                        mqttClient.publish(LOAD_BALANCER_TOPIC, message.toString());
                        logger.log(Level.INFO, "Update request sent to LoadBalancer for file: {0}", fileName);
                    } else {
                        throw new IllegalArgumentException("File not found or you don't have permission to update it.");
                    }
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "SQL Exception during file update: {0}", ex.getMessage());
            ex.printStackTrace();
            throw ex;
        }
    }

    // Deletes the file by sending a deletion request via MQTT.
    public void deleteFile(String fileName) throws SQLException, IOException, MqttException {
        if (!userManager.isLoggedIn()) {
            throw new IOException("No user is logged in. Please log in first.");
        }
        int userId = userManager.getUserId();
        try (Connection connection = DriverManager.getConnection(DATABASE_URL)) {
            String sql = "SELECT location FROM Files WHERE name = ? AND ownerId = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, fileName);
                pstmt.setInt(2, userId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        JSONObject message = new JSONObject();
                        message.put("action", "deleteFile");
                        message.put("fileName", fileName);
                        message.put("userId", userId);

                        mqttClient.publish("loadBalancer/queue", message.toString());
                        logger.log(Level.INFO, "Delete request sent to LoadBalancer for file: {0}", fileName);
                    } else {
                        throw new IllegalArgumentException("File not found or you don't have permission to delete it.");
                    }
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "SQL Exception during file deletion: {0}", ex.getMessage());
            ex.printStackTrace();
            throw ex;
        }
    }

    private int getFileId(Connection connection, String fileName, String fileLocation) throws SQLException {
        String sql = "SELECT fileId FROM Files WHERE name = ? AND location = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, fileName);
            stmt.setString(2, fileLocation);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("fileId");
                }
            }
        }
        return -1;
    }

    public void createSharedFilesTable() throws SQLException {
        try (Connection connection = DriverManager.getConnection(DATABASE_URL)) {
            Statement statement = connection.createStatement();
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS SharedFiles ("
                    + "sharedId INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "fileId INTEGER REFERENCES Files(fileId) ON DELETE CASCADE, "
                    + "sharedToId INTEGER REFERENCES userDetails(id) ON DELETE CASCADE, "
                    + "permissions INTEGER)"
            );
            System.out.println("Table 'SharedFiles' created successfully (or already exists).");
        } catch (SQLException ex) {
            Logger.getLogger(req15.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public String setFilePermissions(String fileName, String sharedWithUsername, String permissions)
            throws SQLException, IOException {
        if (!userManager.isLoggedIn()) {
            throw new IOException("No user is logged in. Please log in first.");
        }
        try (Connection connection = DriverManager.getConnection(DATABASE_URL)) {
            int ownerId = userManager.getUserId();
            if (ownerId != -1) {
                try (PreparedStatement checkOwnershipStmt = connection.prepareStatement(
                        "SELECT fileId, location FROM Files WHERE name = ? AND ownerId = ?")) {
                    checkOwnershipStmt.setString(1, fileName);
                    checkOwnershipStmt.setInt(2, ownerId);
                    try (ResultSet ownershipRs = checkOwnershipStmt.executeQuery()) {
                        if (ownershipRs.next()) {
                            int fileId = ownershipRs.getInt("fileId");
                            String fileLocation = ownershipRs.getString("location");
                            int sharedWithId = -1;
                            try (PreparedStatement getUserStmt = connection.prepareStatement(
                                    "SELECT id FROM userDetails WHERE username = ?")) {
                                getUserStmt.setString(1, sharedWithUsername);
                                try (ResultSet userRs = getUserStmt.executeQuery()) {
                                    if (userRs.next()) {
                                        sharedWithId = userRs.getInt("id");
                                    }
                                }
                            }
                            if (sharedWithId != -1) {
                                if (permissions.equals("Read Only")
                                        || permissions.equals("Write Only")
                                        || permissions.equals("Read and Write")) {
                                    if (fileId != -1) {
                                        try (PreparedStatement insertSharedFileStmt = connection.prepareStatement(
                                                "INSERT INTO SharedFiles (fileId, sharedToId, permissions) VALUES (?, ?, ?)")) {
                                            insertSharedFileStmt.setInt(1, fileId);
                                            insertSharedFileStmt.setInt(2, sharedWithId);
                                            insertSharedFileStmt.setString(3, permissions);
                                            insertSharedFileStmt.executeUpdate();
                                            return "File shared successfully with " + sharedWithUsername + " with " + permissions + " permissions.";
                                        }
                                    } else {
                                        throw new IOException("File not found.");
                                    }
                                } else {
                                    throw new IllegalArgumentException("Invalid permission value. Please enter Read Only, Write Only, or Read and Write.");
                                }
                            } else {
                                throw new IOException("User to share with not found.");
                            }
                        } else {
                            throw new IOException("File not found or you don't have permission to share it.");
                        }
                    }
                }
            } else {
                throw new IOException("Logged-in user not found.");
            }
        } catch (SQLException ex) {
            Logger.getLogger(req4.class.getName()).log(Level.SEVERE, null, ex);
            return "Error: " + ex.getMessage();
        }
    }

    public String accessFile(String fileName) throws SQLException {
        if (!userManager.isLoggedIn()) {
            return "Error: No user is logged in. Please log in first.";
        }
        try (Connection connection = DriverManager.getConnection(DATABASE_URL)) {
            int userId = userManager.getUserId();
            if (userId == -1) {
                return "Error: Logged-in user not found.";
            }
            String fileLocation = getFileLocation(connection, fileName, userId);
            if (fileLocation == null) {
                return "Error: File not found.";
            }
            int fileId = getFileId(connection, fileName, fileLocation);
            if (fileId == -1) {
                return "Error: Unable to retrieve file ID.";
            }
            String permissions = getUserFilePermissions(connection, fileId, userId);
            if (permissions == null) {
                return "Error: You do not have permissions to access this file.";
            }
            String fullFilePath = fileLocation + fileName;
            return handleFileAccess(fullFilePath, permissions);
        } catch (SQLException ex) {
            Logger.getLogger(req4.class.getName()).log(Level.SEVERE, null, ex);
            return "Error: " + ex.getMessage();
        }
    }

    private String getFileLocation(Connection connection, String fileName, int ownerId) throws SQLException {
        try (PreparedStatement checkFileStmt = connection.prepareStatement(
                "SELECT location FROM Files WHERE name = ? AND ownerId = ?")) {
            checkFileStmt.setString(1, fileName);
            checkFileStmt.setInt(2, ownerId);
            try (ResultSet fileRs = checkFileStmt.executeQuery()) {
                if (fileRs.next()) {
                    return fileRs.getString("location");
                }
            }
        }
        return null;
    }

    private String getUserFilePermissions(Connection connection, int fileId, int userId) throws SQLException {
        String sql = "SELECT permissions FROM SharedFiles WHERE fileId = ? AND sharedToId = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, fileId);
            stmt.setInt(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("permissions");
                }
            }
        }
        return null;
    }

    private String handleFileAccess(String filePath, String permissions) {
        switch (permissions) {
            case "Read Only":
                return readFile(filePath);
            case "Write Only":
                return writeFile(filePath);
            case "Read and Write":
                return readAndWriteFile(filePath);
            default:
                return "Error: Invalid permissions: " + permissions;
        }
    }

    private String readFile(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            return "Error: The file does not exist.";
        }
        try {
            File tempFile = File.createTempFile("readonly_", ".txt");
            copyFile(file, tempFile);
            new ProcessBuilder("mousepad", tempFile.getAbsolutePath()).start();
            return "File opened in Mousepad (Read Only). Note: Changes will not be saved.";
        } catch (IOException e) {
            return "Failed to open file in Mousepad: " + e.getMessage();
        }
    }

    private String writeFile(String filePath) {
        try {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Write to File");
            dialog.setHeaderText("Enter new content for the file:");
            dialog.setContentText("Content:");
            Optional<String> result = dialog.showAndWait();
            if (result.isPresent()) {
                String newFileContent = result.get();
                try (FileWriter writer = new FileWriter(filePath, false)) {
                    writer.write(newFileContent);
                }
                return "File updated successfully (Write Only).";
            } else {
                return "No content entered. File not modified.";
            }
        } catch (IOException e) {
            return "Error writing to file: " + e.getMessage();
        }
    }

    private String readAndWriteFile(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            return "Error: The file does not exist.";
        }
        try {
            new ProcessBuilder("mousepad", file.getAbsolutePath()).start();
            return "File opened successfully in Mousepad for reading and writing.";
        } catch (IOException e) {
            return "Failed to open file in Mousepad: " + e.getMessage();
        }
    }

    private void copyFile(File source, File dest) throws IOException {
        try (FileInputStream fis = new FileInputStream(source); 
             FileOutputStream fos = new FileOutputStream(dest)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
        }
    }

    public void disconnectMqtt() throws MqttException {
        mqttClient.disconnect();
    }
}
