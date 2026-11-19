/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.requirements;



import java.util.*;
import java.util.Comparator;

public class SimpleSchedulingSimulator {

    
    public static long simulateFCFS(List<Task> tasks, int numResources) {
        long[] resourceLoads = new long[numResources];
        Arrays.fill(resourceLoads, 0L);
        for (Task t : tasks) {
            // Find the resource with the minimum current load.
            int minRes = 0;
            for (int i = 1; i < numResources; i++) {
                if (resourceLoads[i] < resourceLoads[minRes]) {
                    minRes = i;
                }
            }
            resourceLoads[minRes] += t.getAdjustedDelay();
        }
        long makespan = 0;
        for (long load : resourceLoads) {
            makespan = Math.max(makespan, load);
        }
        return makespan;
    }

    
    public static long simulateSJF(List<Task> tasks, int numResources) {
        List<Task> sortedTasks = new ArrayList<>(tasks);
        sortedTasks.sort(Comparator.comparingLong(Task::getAdjustedDelay));
        return simulateFCFS(sortedTasks, numResources);
    }

    
    public static long simulateRR(List<Task> tasks, int numResources, int timeSlice) {
        // Create a mutable copy for each task with remaining time in ms.
        class RRTask {
            String name;
            long remaining;
            RRTask(Task t) {
                this.name = t.getName();
                this.remaining = t.getAdjustedDelay();
            }
        }
        
        Queue<RRTask> rrQueue = new LinkedList<>();
        for (Task t : tasks) {
            rrQueue.offer(new RRTask(t));
        }
        
        long[] resourceLoads = new long[numResources];
        Arrays.fill(resourceLoads, 0L);
        int resIndex = 0;  // We'll simply round-robin among resources

        while (!rrQueue.isEmpty()) {
            RRTask task = rrQueue.poll();
            long slice = Math.min(timeSlice, task.remaining);
            // Assign this slice to the current resource.
            resourceLoads[resIndex] += slice;
            task.remaining -= slice;
            // Print simulation details.
            System.out.println("Resource " + (resIndex + 1) + " processed " 
                    + task.name + " for " + slice + " ms, remaining " 
                    + task.remaining + " ms");
            resIndex = (resIndex + 1) % numResources;  // Move to next resource in round robin
            if (task.remaining > 0) {
                rrQueue.offer(task);  // Requeue the task if not finished.
            }
        }
        
        long makespan = 0;
        for (long load : resourceLoads) {
            makespan = Math.max(makespan, load);
        }
        return makespan;
    }

    /**
     * Determines which scheduling algorithm gives the smallest makespan.
     * Returns one of "FCFS", "SJF", or "RR".
     * @param tasks
     * @param numResources
     * @param timeSlice
     * @return 
     */
    public static String determineBestAlgorithm(List<Task> tasks, int numResources, int timeSlice) {
        long fcfs = simulateFCFS(tasks, numResources);
        long sjf  = simulateSJF(tasks, numResources);
        long rr   = simulateRR(tasks, numResources, timeSlice);
        System.out.println("Calculated Processing Times:");
        System.out.println("FCFS: " + fcfs + " ms");
        System.out.println("SJF:  " + sjf  + " ms");
        System.out.println("RR:   " + rr   + " ms");
        if (fcfs <= sjf && fcfs <= rr) {
            return "FCFS";
        } else if (sjf <= fcfs && sjf <= rr) {
            return "SJF";
        } else {
            return "RR";
        }
    }
    
    
}

