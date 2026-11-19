package com.mycompany.requirements;

public class Task {
    
    // Inner enum for task types.
    public static enum TaskType {
        CREATE,
        UPDATE,
        DELETE
    }
    
    // Inner enum for tracking task status.
    public static enum TaskStatus {
        WAITING,
        PROCESSING,
        COMPLETED
    }
    
    private final String name;
    private final long delay;
    private final TrafficSimulation.TrafficType trafficType;
    private final TaskType taskType;
    private final String newContent;  
    private TaskStatus status;        //will be WAITING, PROCESSING, or COMPLETED

    
    public Task(String name, long delay, TrafficSimulation.TrafficType trafficType, 
                TaskType taskType, String newContent) {
        this.name = name;
        this.delay = delay;
        this.trafficType = trafficType;
        this.taskType = taskType;
        this.newContent = newContent;                             //optional, for update. not needed for create and delete as no new contenet is added for these
        this.status = TaskStatus.WAITING;  // all tasks start as waiting
    }

    
    public Task(String name, long delay, TrafficSimulation.TrafficType trafficType, TaskType taskType) {
        this(name, delay, trafficType, taskType, null);
    }

    public String getName() {
        return name;
    }

    public long getDelay() {
        return delay;
    }

    
    public long getAdjustedDelay() {
        return delay;
    }

    public TrafficSimulation.TrafficType getTrafficType() {
        return trafficType;
    }

    public TaskType getTaskType() {
        return taskType;
    }

    public String getNewContent() {
        return newContent;
    }
    
    public TaskStatus getStatus() {
        return status;
    }
    
    public void setStatus(TaskStatus status) {
        this.status = status;
    }
}
