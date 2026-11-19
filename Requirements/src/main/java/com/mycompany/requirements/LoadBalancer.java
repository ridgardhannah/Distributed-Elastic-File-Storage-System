package com.mycompany.requirements;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.paho.client.mqttv3.*;
import org.json.JSONObject;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Random;

public class LoadBalancer {

    private final MqttClient mqttClient;
    private static final Logger logger = req19_Logging.getLogger(LoadBalancer.class);
    private Random random = new Random();

    private final String taskQueueTopic = "loadBalancer/queue";
    private final String ackTopic = "loadBalancer/ack";
    private final String updateTopic = "loadBalancer/update";

    // Real-time scheduling: a TaskScheduler instance
    private TaskScheduler scheduler;

    // An instance of MQTTHelper (if you decide to use it instead of mqttClient directly)
    private MQTTHelper mqttHelper;

    public LoadBalancer(String broker, String clientId) throws MqttException {
        mqttClient = new MqttClient(broker, clientId);
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        mqttClient.connect(options);
        logger.log(Level.INFO, "LoadBalancer connected to broker: {0}", broker);

        // For real-time scheduling, initialize TaskScheduler with 4 resources.
        scheduler = new TaskScheduler(4, broker, clientId + "-scheduler");

        // Subscribe to the task queue topic with a dedicated listener.
        mqttClient.subscribe(taskQueueTopic, new IMqttMessageListener() {
            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                String payload = new String(message.getPayload());
                try {
                    JSONObject jsonMessage = new JSONObject(payload);
                    String action = jsonMessage.getString("action");

                    if ("createFile".equals(action)) {
                        processFileCreationTask(jsonMessage);
                    } else if ("updateFile".equals(action)) {
                        handleUpdateRequest(jsonMessage);
                    } else if ("taskProgress".equals(action)) {
                        logger.info("Task progress update received: " + jsonMessage.toString());
                    } else if ("deleteFile".equals(action)) {

                        mqttClient.publish("buffer/delete", new MqttMessage(jsonMessage.toString().getBytes()));
                        logger.log(Level.INFO, "Delete task forwarded for file: {0}", jsonMessage.getString("fileName"));
                    } else {
                        logger.warning("Unknown action received on taskQueueTopic: " + action);
                    }
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error processing message on taskQueueTopic: {0}", e.getMessage());
                    e.printStackTrace();
                }
            }
        });
        logger.info("LoadBalancer subscribed to topic: " + taskQueueTopic);

        // Subscribe to the ACK topic with a dedicated listener.
        mqttClient.subscribe(ackTopic, new IMqttMessageListener() {
            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                String payload = new String(message.getPayload());
                logger.info("LoadBalancer received ACK: " + payload);
            }
        });
        logger.info("LoadBalancer subscribed to topic: " + ackTopic);

        // Subscribe to update requests.
        mqttClient.subscribe(updateTopic, new IMqttMessageListener() {
            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                // When an update request is received, handle it.
                String payload = new String(message.getPayload());
                try {
                    JSONObject jsonMessage = new JSONObject(payload);
                    handleUpdateRequest(jsonMessage);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error processing update request: {0}", e.getMessage());
                    e.printStackTrace();
                }
            }
        });
        logger.info("LoadBalancer subscribed to topic: " + updateTopic);

        // Set a global callback for any messages not caught by the dedicated subscriptions.
        mqttClient.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                logger.log(Level.WARNING, "Connection lost: {0}", cause.getMessage());
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // Not used in this implementation.
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                // This callback should not be called anymore since we have dedicated listeners for each topic.
                logger.warning("Message received on unexpected topic: " + topic + ", payload: " + new String(message.getPayload()));
            }
        });
    }

    private void processFileCreationTask(JSONObject jsonMessage) {
        try {
            // Retrieve necessary file metadata:
            int fileId = jsonMessage.getInt("fileId");
            String fileName = jsonMessage.getString("filename");
            String fileLocation = jsonMessage.getString("location");
            long adjustedDelay = jsonMessage.getLong("adjustedDelay");

            // Create a new task with the CREATE task type
            Task creationTask = new Task(fileName, adjustedDelay, TrafficSimulation.TrafficType.MEDIUM, Task.TaskType.CREATE);
            scheduler.addTask(creationTask);
            logger.info("Enqueued file creation task: " + fileName);

            // Simulate task scheduling to determine the best algorithm
            String chosenAlgorithm = simulateTaskScheduling(creationTask);
            logger.info("Chosen scheduling algorithm: " + chosenAlgorithm);

            // Process tasks in real time until the creation task is completed
            while (!scheduler.isTaskCompleted(creationTask)) {
                scheduler.processTasks();
                Thread.sleep(1000); // Check every second
            }
            logger.info("File creation task completed: " + fileName);

            // After the task is completed, send a message to the Buffer to start processing the file
            JSONObject bufferMessage = new JSONObject();
            bufferMessage.put("action", "processNewFile");
            bufferMessage.put("fileName", fileName);
            bufferMessage.put("fileId", fileId);

            mqttClient.publish("buffer/retrieve", new MqttMessage(bufferMessage.toString().getBytes()));
            logger.info("Sent message to Buffer to process new file: " + fileName);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing file creation task: {0}", e.getMessage());
            e.printStackTrace();
        }
    }

    private String simulateTaskScheduling(Task realTask) {
        try {
            List<Task> tasks = new ArrayList<>();
            // Add the real file task with a fixed delay.
            tasks.add(realTask);

            // Add 10 dummy tasks (you can use fixed or random delays).
            for (int i = 1; i <= 10; i++) {
                int dummyDelay = 30000 + random.nextInt(30000);
                tasks.add(new Task("Dummy Task " + i, dummyDelay, TrafficSimulation.TrafficType.MEDIUM, Task.TaskType.CREATE));
            }

            int numResources = 4;
            int timeSlice = 5000;

            // Use our simulator methods:
            long fcfs = SimpleSchedulingSimulator.simulateFCFS(tasks, numResources);
            long sjf = SimpleSchedulingSimulator.simulateSJF(tasks, numResources);
            long rr = SimpleSchedulingSimulator.simulateRR(tasks, numResources, timeSlice);
            logger.info("Calculated Processing Times:");
            logger.log(Level.INFO, "FCFS: {0} ms", fcfs);
            logger.log(Level.INFO, "SJF:  {0} ms", sjf);
            logger.log(Level.INFO, "RR:   {0} ms", rr);

            String chosenAlgorithm = SimpleSchedulingSimulator.determineBestAlgorithm(tasks, numResources, timeSlice);
            logger.info("Chosen scheduling algorithm: " + chosenAlgorithm);
            return chosenAlgorithm;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error simulating task scheduling: {0}", e.getMessage());
            e.printStackTrace();
            return "FCFS"; // Default to FCFS in case of error
        }
    }

    public void handleUpdateRequest(JSONObject jsonMessage) {
        try {
            String action = jsonMessage.getString("action");
            if (!action.equals("updateFile")) {
                return; // Ignore unrelated actions.
            }
            String fileName = jsonMessage.getString("fileName");
            int userId = jsonMessage.getInt("userId");
            String newContent = jsonMessage.optString("newContent"); // Get new content from the message
            logger.log(Level.INFO, "Received update request for file: {0}", fileName);

            // Create an update task.
            // Here, we assume the update delay is set to 3000 ms, traffic type is MEDIUM.
            Task updateTask = new Task(fileName, 3000, TrafficSimulation.TrafficType.MEDIUM, Task.TaskType.UPDATE, newContent);
            scheduler.addTask(updateTask);
            logger.log(Level.INFO, "Update task enqueued for file: {0}", fileName);

            // Process tasks in real time until our update task is completed.
            while (!scheduler.isTaskCompleted(updateTask)) {
                scheduler.processTasks();
                Thread.sleep(1000); // Check every second to see if completeed or not yet. when its done its status should change to COMPLETED
            }
            logger.log(Level.INFO, "Update task completed for file: {0}", fileName);

            // Once the update is complete, instruct the Buffer to retrieve and process file chunks.
            JSONObject bufferMessage = new JSONObject();
            bufferMessage.put("action", "retrieveChunksForUpdate");
            bufferMessage.put("fileName", fileName);
            bufferMessage.put("userId", userId);
            // Publish using mqttHelper if available; otherwise, mqttClient.
            mqttClient.publish("buffer/retrieve", new MqttMessage(bufferMessage.toString().getBytes()));
            logger.info("Update retrieval instruction sent to Buffer for file: " + fileName);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error handling update request: {0}", e.getMessage());
            e.printStackTrace();
        }
    }

    public void disconnect() {
        try {
            mqttClient.disconnect();
            logger.info("LoadBalancer disconnected from MQTT broker.");
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        try {
            LoadBalancer loadBalancer = new LoadBalancer("tcp://mqtt-broker:1883", "LoadBalancer");
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}
