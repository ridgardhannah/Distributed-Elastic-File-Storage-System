package com.mycompany.requirements;

import com.jcraft.jsch.*;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MainContainer {

    private static final Logger logger = req19_Logging.getLogger(MainContainer.class);
    private static final String TEMP_FOLDER = "/home/ntu-user/NetBeansProjects/FileTempFolder/";
    private static final String BUFFER_FOLDER = "/home/ntu-user/NetBeansProjects/BufferFolder/";
    private static final String SFTP_HOST = "localhost";  // Use localhost if same machine
    private static final int SFTP_PORT = 22;
    private static final String SFTP_USER = "ntu-user";
    private static final String SFTP_PASSWORD = "ntu-user";
    private static final String REQUEST_TOPIC = "maincontainer/sendfile";
    private static final String DELETE_TOPIC = "maincontainer/delete";
    private static final String UPDATE_TOPIC = "maincontainer/update";
    private static final String FILE_READY_TOPIC = "maincontainer/ready"; // New topic for file ready notifications
    private MQTTService mqttService;
    private TaskScheduler scheduler;

    public MainContainer(MQTTService mqttService, TaskScheduler scheduler) {
        this.mqttService = mqttService;
        this.scheduler = scheduler;
    }

    public void startMainContainer() {
        try {
            // Obtain the centralized MQTT service
            System.out.println("MainContainer using central MQTT connection.");

            // Subscribe to the request topic with a dedicated listener.
            mqttService.subscribe(REQUEST_TOPIC, (topic, message) -> {
                System.out.println("Message arrived on topic: " + topic);
                System.out.println("Message payload: " + new String(message.getPayload()));

                String receivedMessage = new String(message.getPayload());
                System.out.println("Received file request: " + receivedMessage);

                // Extract filename from received JSON message
                String filename = extractFileNameFromMessage(receivedMessage);
                if (filename == null) {
                    System.err.println("Filename extraction failed.");
                    return;
                }

                // Construct full path to file
                String localFilePath = TEMP_FOLDER + filename;
                File file = new File(localFilePath);
                if (!file.exists()) {
                    System.err.println("File not found in TEMP_FOLDER: " + localFilePath);
                    return;
                }

                // Attempt SFTP transfer
                boolean success = sendFileViaSFTP(filename);
                if (success) {
                    System.out.println("File transfer completed successfully for: " + filename);
                } else {
                    System.err.println("File transfer failed for: " + filename);
                }
            });

            // Subscribe to deletion instructions.
            mqttService.subscribe(DELETE_TOPIC, new IMqttMessageListener() {
                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    System.out.println("Received deletion message on topic: " + topic);
                    String payload = new String(message.getPayload());
                    System.out.println("Deletion payload: " + payload);
                    JSONObject json = new JSONObject(payload);
                    String filename = json.optString("filename");
                    // Optionally allow a custom path; default to TEMP_FOLDER.
                    String path = json.optString("path", TEMP_FOLDER + filename);
                    File fileToDelete = new File(path);
                    if (fileToDelete.exists()) {
                        if (fileToDelete.delete()) {
                            System.out.println("Deleted file from FileTempFolder: " + path);
                        } else {
                            System.err.println("Failed to delete file: " + path);
                        }
                    } else {
                        System.out.println("File not found for deletion: " + path);
                    }
                }
            });

            // Subscribe to update notifications from the Buffer
            mqttService.subscribe(FILE_READY_TOPIC, new IMqttMessageListener() {
                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    System.out.println("Update notification received on topic: " + topic);
                    String payload = new String(message.getPayload());
                    System.out.println("Update payload: " + payload);
                    // Handle the file update notification (e.g., refresh the UI)
                }
            });

            System.out.println("Subscribed to topics: " + REQUEST_TOPIC + ", " + DELETE_TOPIC + ", and " + UPDATE_TOPIC + ".");

            // Keep the thread alive indefinitely.
            synchronized (MainContainer.class) {
                MainContainer.class.wait();
            }
        } catch (MqttException | InterruptedException e) {
            System.err.println("Error in MainContainer: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String extractFileNameFromMessage(String message) {
        System.out.println("Parsing message: " + message);
        try {
            JSONObject json = new JSONObject(message);
            return json.getString("filename");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean sendFileViaSFTP(String filename) {
        Session session = null;
        ChannelSftp channelSftp = null;

        try {
            System.out.println("Initiating SFTP transfer for file: " + filename);

            com.jcraft.jsch.JSch jsch = new com.jcraft.jsch.JSch();
            session = jsch.getSession(SFTP_USER, SFTP_HOST, SFTP_PORT);
            session.setPassword(SFTP_PASSWORD);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();
            System.out.println("SFTP session connected.");

            Channel channel = session.openChannel("sftp");
            channel.connect();
            channelSftp = (ChannelSftp) channel;
            System.out.println("SFTP channel connected.");

            String localFilePath = TEMP_FOLDER + filename;
            String remoteFilePath = BUFFER_FOLDER + filename;

            File file = new File(localFilePath);
            if (!file.exists()) {
                System.err.println("File not found: " + localFilePath);
                return false;
            }

            System.out.println("Transferring file: " + localFilePath + " to " + remoteFilePath);
            try (FileInputStream fileInputStream = new FileInputStream(file)) {
                channelSftp.put(fileInputStream, remoteFilePath);
            }

            System.out.println("File sent successfully: " + filename);
            return true;
        } catch (JSchException | SftpException | java.io.IOException e) {
            System.err.println("SFTP transfer failed for file: " + filename);
            e.printStackTrace();
            return false;
        } finally {
            if (channelSftp != null) {
                channelSftp.exit();
            }
            if (session != null) {
                session.disconnect();
            }
        }
    }

    public static void main(String[] args) {
        try {
            // Initialize MQTT service and TaskScheduler
            MQTTService mqttService = MQTTService.getInstance();
            TaskScheduler scheduler = new TaskScheduler(4, "tcp://mqtt-broker:1883", "MainContainerScheduler");

            // Create an instance of MainContainer
            MainContainer mainContainer = new MainContainer(mqttService, scheduler);

            // Start the MainContainer logic
            mainContainer.startMainContainer(); // Call startMainContainer on the instance

        } catch (MqttException e) {
            System.err.println("Error initializing MainContainer: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
