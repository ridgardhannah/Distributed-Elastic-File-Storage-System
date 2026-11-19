/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.requirements;


import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class FileLockManager {
    private static final ConcurrentHashMap<String, Lock> fileLocks = new ConcurrentHashMap<>();

    // Acquire a lock for a file
    public static Lock acquireLock(String fileName) {
        Lock lock = fileLocks.computeIfAbsent(fileName, k -> new ReentrantLock());
        lock.lock(); // Acquire the lock
        return lock;
    }

    // Release a lock for a file
    public static void releaseLock(String fileName) {
        Lock lock = fileLocks.get(fileName);
        if (lock != null) {
            lock.unlock(); // Release the lock
            // Remove the lock if no other threads are waiting
            if (!lock.tryLock()) {
                fileLocks.remove(fileName);
            } else {
                lock.unlock(); // Re-lock and unlock to maintain state
            }
        }
    }
}

