package com.mycompany.requirements;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class req19_Logging {
    private static boolean isConfigured = false;

    // Configure the global logger
    public static void configureLogger() {
        if (isConfigured) {
            return; // Prevent reconfiguration
        }

        try {
            // Create a FileHandler to write logs to a file
            FileHandler fileHandler = new FileHandler("system_logs.txt", true); // Append to the file
            fileHandler.setFormatter(new SimpleFormatter()); // Use a simple text format

            // Get the global logger and add the file handler
            Logger globalLogger = Logger.getLogger("");
            globalLogger.addHandler(fileHandler);

            isConfigured = true;
            System.out.println("Logging configured. Logs will be written to system_logs.txt");
        } catch (IOException e) {
            System.err.println("Failed to configure logging: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Utility method to get a logger for a specific class
    public static Logger getLogger(Class<?> clazz) {
        return Logger.getLogger(clazz.getName());
    }
}
