package com.mycompany.requirements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONObject;

public class TaskScheduler {

    private final Queue<Task> waitingQueue = new LinkedList<>();
    private final Queue<Task> processingQueue = new LinkedList<>();
    private final Queue<Task> completedQueue = new LinkedList<>();
    private final List<Long> resources;  // Simulated resource load times
    private final MqttClient mqttClient;
    private static final Logger logger = req19_Logging.getLogger(TaskScheduler.class);

    public TaskScheduler(int resourceCount, String broker, String clientId) throws MqttException {
        // Initialize resource list with zeros.
        resources = new ArrayList<>(Collections.nCopies(resourceCount, 0L));
        mqttClient = new MqttClient(broker, clientId);
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        mqttClient.connect(options);
        logger.info("TaskScheduler connected to MQTT broker: " + broker);
    }

    // Add a task to the waiting queue.
    public void addTask(Task task) {
        waitingQueue.offer(task);
        logger.info("Task addedto waiting queue: " + task.getName());
    }

    /**
     * Process tasks: move tasks from waitingQueue to processingQueue if a resource is free.
     * Then check if tasks in processingQueue are complete (if their assigned resource time is up);
     * if so, move them to completedQueue and free the resource.
     */
    public void processTasks() {
        // For each resource, if free and waitingQueue not empty, assign a task
        for (int i = 0; i < resources.size(); i++) {
            if (resources.get(i) == 0L && !waitingQueue.isEmpty()) {
                Task task = waitingQueue.poll();
                task.setStatus(Task.TaskStatus.PROCESSING);
                processingQueue.offer(task);
                // Simulate processing by setting a future time (current time + delay)
                resources.set(i, System.currentTimeMillis() + task.getAdjustedDelay());
                logger.info("Processing task: " + task.getName());
            }
        }

        // Now, check processingQueue to see if any tasks are completed.
        Iterator<Task> it = processingQueue.iterator();
        int resourceIndex = 0;
        while (it.hasNext() && resourceIndex < resources.size()) {
            Task task = it.next();
            if (resources.get(resourceIndex) > 0 && resources.get(resourceIndex) <= System.currentTimeMillis()) {
                it.remove();
                task.setStatus(Task.TaskStatus.COMPLETED);
                completedQueue.offer(task);
                resources.set(resourceIndex, 0L); // Free the resource.
                logger.info("Task completed: " + task.getName());
                publishProgress("Task completed: " + task.getName());
            }
            resourceIndex++;
        }
    }

    /**
     * Returns true if the task's status is COMPLETED.
     * @param task The task to check.
     * @return true if completed, false otherwise.
     */
    public boolean isTaskCompleted(Task task) {
        return task.getStatus() == Task.TaskStatus.COMPLETED;
    }

   
    public void publishProgress(String status) {
        try {
            JSONObject jsonMessage = new JSONObject();
            jsonMessage.put("action", "taskProgress");
            jsonMessage.put("status", status);
            jsonMessage.put("timestamp", System.currentTimeMillis());
            MqttMessage mqttMessage = new MqttMessage(jsonMessage.toString().getBytes());
            mqttClient.publish("loadBalancer/queue", mqttMessage);
            logger.info("Published progress: " + jsonMessage.toString());
        } catch (MqttException ex) {
            logger.log(Level.SEVERE, "Error publishing progress: {0}", ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * Disconnects the MQTT client.
     */
    public void disconnect() {
        try {
            mqttClient.disconnect();
            logger.info("TaskScheduler disconnected from MQTT broker.");
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}
