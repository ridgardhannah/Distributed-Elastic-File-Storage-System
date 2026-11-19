package com.mycompany.requirements;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import org.eclipse.paho.client.mqttv3.MqttException;

public class MainMenuController implements Initializable {

    @FXML
    private Label loggedInUsernameLabel;
    private req15 userManager;
    private req4 fileManager;
    // Remove individual MQTTHelper – use centralized MQTTService instead.

    public void setUserManager(req15 userManager) {
        System.out.println("MainMenuController: setUserManager called");
        this.userManager = userManager;
        if (this.userManager != null) {
            System.out.println("MainMenuController: userManager set successfully!");
            loggedInUsernameLabel.setText("Logged in as: " + userManager.getLoggedInUsername());
        } else {
            System.err.println("MainMenuController: userManager is NULL!");
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("[DEBUG] MainMenuController initialized.");
        // No longer instantiating a separate MQTTHelper here.
        // Use MQTTService.getInstance() elsewhere when needed.
    }
    
    @FXML
    private void onUserMgmtClicked(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/mycompany/requirements/userManagement.fxml"));
            Parent root = loader.load();
            UserManagementController controller = loader.getController();
            controller.setUserManager(userManager); 
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.show();
        } catch (IOException e) {
            System.err.println("Error loading User Management screen: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
private void onFileManagementClicked(ActionEvent event) {
    try {
        if (userManager == null) {
            System.err.println("[ERROR] userManager is not set.");
            return;
        }

        System.out.println("[DEBUG] Reinitializing fileManager with centralized MQTT connection.");
        fileManager = new req4(userManager);

        URL fxmlLocation = getClass().getResource("/com/mycompany/requirements/FileManagement.fxml");
        if (fxmlLocation == null) {
            System.err.println("[ERROR] FileManagement.fxml NOT FOUND!");
            return;
        }

        FXMLLoader loader = new FXMLLoader(fxmlLocation);
        Parent root = loader.load();
        FileManagementController controller = loader.getController();
        controller.setUserManager(userManager); // Pass userManager to FileManagementController
        controller.setFileManager(fileManager); // Pass fileManager to FileManagementController

        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setScene(new Scene(root));
        stage.show();

    } catch (IOException e) {
        System.err.println("[ERROR] Error loading File Management screen: " + e.getMessage());
        e.printStackTrace();
    }
}


    @FXML
private void onLogoutClicked(ActionEvent event) {
    try {
        // Disconnect MQTT service (if applicable)
        MQTTService.getInstance().disconnect();
    } catch (MqttException e) {
        System.err.println("[ERROR] Failed to disconnect MQTT service: " + e.getMessage());
        e.printStackTrace();
    }

    // Log out the current user
    userManager.logout();

    try {
        // Load the login screen
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/mycompany/requirements/LoginOrRegister.fxml"));
        Parent root = loader.load();

        // Pass the userManager to the LoginOrRegisterController
        LoginOrRegisterController loginController = loader.getController();
        loginController.setUserManager(userManager);

        // Navigate to the login screen
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setScene(new Scene(root));
        stage.show();
    } catch (IOException e) {
        System.err.println("Error loading login screen: " + e.getMessage());
        e.printStackTrace();
    }
}

}
