package com.mycompany.requirements;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.json.JSONObject;
import java.util.*;
import java.util.Comparator;

public class TrafficSimulation {

    public enum TrafficType {
        HIGH(2),
        MEDIUM(1.6),
        LOW(1.2);

        private final double multiplier;
        TrafficType(double multiplier) {
            this.multiplier = multiplier;
        }
        public double getMultiplier() {
            return multiplier;
        }
    }

    private final Queue<Task> waitingQueue = new LinkedList<>();
    private final Random random = new Random();
    // Replace MQTTHelper with MQTTService.
    private final MQTTService mqttService;

    public TrafficSimulation() throws MqttException {
        this.mqttService = MQTTService.getInstance();
    }

   public void addTask(String taskName, int delayStart, int delayEnd) {
        // Set a default traffic level (you can adjust this as needed)
        TrafficSimulation.TrafficType trafficLevel = TrafficSimulation.TrafficType.MEDIUM;

        // Calculate delay based on traffic multiplier.
        int delay = (delayStart + random.nextInt(delayEnd - delayStart)) * (int) trafficLevel.getMultiplier();

        // Create a new Task with the CREATE task type.
        Task task = new Task(taskName, delay, trafficLevel, Task.TaskType.CREATE);

        // Add the task to the waiting queue.
        waitingQueue.offer(task);

        System.out.println("Added task: " + taskName + " with delay: " + delay / 1000 + "s");
    }


    public String determineBestSchedulingAlgorithm(long timeQuantum) {
        long fcfsTime = calculateFCFSTime();
        long sjnTime = calculateSJNTime();
        long rrTime = calculateRRTime(timeQuantum);

        if (fcfsTime <= sjnTime && fcfsTime <= rrTime) return "FCFS";
        else if (sjnTime <= fcfsTime && sjnTime <= rrTime) return "SJN";
        else return "RR";
    }

    // Change these from private to public
public long calculateFCFSTime() {
    return waitingQueue.stream().mapToLong(Task::getAdjustedDelay).sum();
}

public long calculateSJNTime() {
    return waitingQueue.stream().sorted(Comparator.comparingLong(Task::getAdjustedDelay))
            .mapToLong(Task::getAdjustedDelay).sum();
}

public long calculateRRTime(long timeQuantum) {
    Queue<Task> rrQueue = new LinkedList<>(waitingQueue);
    long totalTime = 0;
    while (!rrQueue.isEmpty()) {
        Task task = rrQueue.poll();
        long remainingTime = task.getAdjustedDelay();
        while (remainingTime > 0) {
            long timeSlice = Math.min(timeQuantum, remainingTime);
            remainingTime -= timeSlice;
            totalTime += timeSlice;
        }
    }
    return totalTime;
}


    public void executeBestAlgorithm(long timeQuantum) throws InterruptedException {
    String bestAlgorithm = determineBestSchedulingAlgorithm(timeQuantum);
    System.out.println("Selected scheduling algorithm: " + bestAlgorithm);
    System.out.println("Initial waiting queue size: " + waitingQueue.size());
    
    if (bestAlgorithm.equals("FCFS")) {
        while (!waitingQueue.isEmpty()) {
            System.out.println("Waiting queue size: " + waitingQueue.size());
            Task task = waitingQueue.poll();
            processTask(task);
        }
    } else if (bestAlgorithm.equals("SJN")) {
        List<Task> taskList = new ArrayList<>(waitingQueue);
        taskList.sort(Comparator.comparingLong(Task::getAdjustedDelay));
        for (Task task : taskList) {
            System.out.println("Processing task from sorted list: " + task.getName());
            processTask(task);
        }
    } else { // RR
        Queue<Task> roundRobinQueue = new LinkedList<>(waitingQueue);
        while (!roundRobinQueue.isEmpty()) {
            Task task = roundRobinQueue.poll();
            long taskTimeRemaining = task.getAdjustedDelay() - timeQuantum;
            System.out.println("RR processing " + task.getName() + "; remaining time: " + taskTimeRemaining + "ms");
            if (taskTimeRemaining > 0) {
                // In a more detailed simulation you might update the task delay and re-add it.
                processTask(task);
            } else {
                processTask(task);
            }
        }
    }
}


    public void fcfsSchedule() throws InterruptedException {
        while (!waitingQueue.isEmpty()) {
            Task task = waitingQueue.poll();
            processTask(task);
        }
    }

    public void sjnSchedule() throws InterruptedException {
        List<Task> taskList = new ArrayList<>(waitingQueue);
        taskList.sort(Comparator.comparingLong(Task::getAdjustedDelay));
        for (Task task : taskList) {
            processTask(task);
        }
    }

   

    public void rrSchedule(long timeQuantum) throws InterruptedException {
        Queue<Task> roundRobinQueue = new LinkedList<>(waitingQueue);
        while (!roundRobinQueue.isEmpty()) {
            Task task = roundRobinQueue.poll();
            long taskTimeRemaining = task.getAdjustedDelay() - timeQuantum;
            if (taskTimeRemaining > 0) {
                // Re-add the task to the queue with the remaining time and the same TaskType
                Task newTask = new Task(task.getName(), (int) taskTimeRemaining, task.getTrafficType(), task.getTaskType());
                roundRobinQueue.offer(newTask);
            } else {
                processTask(task);
            }
        }
    }



    private void processTask(Task task) throws InterruptedException {
        try {
            Thread.sleep(task.getAdjustedDelay()/1000);
            System.out.println("Processed task: " + task.getName());
            mqttService.publish("task/progress", "Task " + task.getName() + " processed.");
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void listenForTasks() throws MqttException {
        mqttService.subscribe("tasks/new", (topic, message) -> {
            String payload = new String(message.getPayload());
            System.out.println("Received task: " + payload);
            try {
                JSONObject json = new JSONObject(payload);
                String taskName = json.getString("taskName");
                int delayStart = json.getInt("delayStart");
                int delayEnd = json.getInt("delayEnd");
                addTask(taskName, delayStart, delayEnd);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void main(String[] args) {
        try {
            TrafficSimulation simulation = new TrafficSimulation();
            simulation.listenForTasks();
            System.out.println("TrafficSimulation is listening for tasks on MQTT...");
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}
