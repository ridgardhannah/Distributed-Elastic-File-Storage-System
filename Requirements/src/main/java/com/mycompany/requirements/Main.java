package com.mycompany.requirements;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.paho.client.mqttv3.MqttException;

public class Main extends Application {

    // Correctly initialize the logger with the class reference
    private static final Logger logger = req19_Logging.getLogger(Main.class);
    private static req15 userManager = new req15(); // Create req15 ONCE

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Configure logging globally
        req19_Logging.configureLogger();
        logger.info("Application started. Logging is configured.");

        try {
            // Load the JavaFX UI
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/mycompany/requirements/LoginOrRegister.fxml"));
            Parent root = loader.load();

            // Pass the userManager to the LoginOrRegisterController
            LoginOrRegisterController controller = loader.getController();
            controller.setUserManager(userManager);

            // Set up the primary stage
            primaryStage.setScene(new Scene(root));
            primaryStage.show();
            logger.info("JavaFX UI loaded successfully.");

            // Start the MainContainer logic in a separate thread
            new Thread(() -> {
                try {
                    // Create instances of MQTTService and TaskScheduler
                    MQTTService mqttService = MQTTService.getInstance();
                    TaskScheduler scheduler = new TaskScheduler(4, "tcp://mqtt-broker:1883", "MainContainerScheduler");

                    // Create an instance of MainContainer
                    MainContainer mainContainer = new MainContainer(mqttService, scheduler);

                    // Start the MainContainer logic
                    mainContainer.startMainContainer();
                    logger.info("MainContainer started successfully.");
                } catch (MqttException e) {
                    logger.log(Level.SEVERE, "Error starting MainContainer: {0}", e.getMessage());
                    e.printStackTrace();
                }
            }).start();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error initializing application: {0}", e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args); // Launch the JavaFX application
    }
}
