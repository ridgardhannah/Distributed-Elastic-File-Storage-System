package com.mycompany.requirements;

import com.jcraft.jsch.JSchException;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.security.spec.InvalidKeySpecException;
import java.sql.SQLException;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.scene.control.Alert.AlertType;

public class LoginOrRegisterController implements Initializable {
    
    private static final Logger logger = req19_Logging.getLogger(LoginOrRegisterController.class);


    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label loginErrorMessage;

    @FXML
    private Label registrationErrorMessage;

    @FXML
    private Button loginButton;

    @FXML
    private Button registerButton;

    @FXML
    private TextArea commandInputArea; // Combined input area

    @FXML
    private TextArea terminalOutputArea; // Combined output area

    @FXML
    private Button executeLocalButton;

    @FXML
    private Button executeRemoteButton;

    private boolean isRemoteCommand = false;

    private RemoteTerminal remoteTerminal;

    private req15 userManager;

    public void setUserManager(req15 userManager) {
        System.out.println("MainMenuController: setUserManager called");
        this.userManager = userManager;
        if (this.userManager != null) {
            System.out.println("MainMenuController: userManager set successfully!");

        } else {
            System.err.println("MainMenuController: userManager is NULL!");
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        loginErrorMessage.setText("");
        registrationErrorMessage.setText("");
        remoteTerminal = new RemoteTerminal();
        req23 terminal = new req23(); // Create an instance of req23
        String allowedCommands = String.join(", ", terminal.getAllowedCommands()); // Get the commands as a comma-separated string
        commandInputArea.setPromptText("Enter command (allowed: " + allowedCommands + ")");
    }

@FXML
private void onLoginButtonClicked(ActionEvent event) {
    String username = usernameField.getText();
    String password = passwordField.getText();

    logger.log(Level.INFO, "User attempted to log in with username: {0}", username);

    if (userManager == null) {
        logger.severe("userManager is NULL in onLoginButtonClicked!");
        showAlert(Alert.AlertType.ERROR, "System error: userManager is not initialized. Please restart the application.");
        return;
    }

    try {
        int userId = userManager.loginUser(username, password); // Use injected userManager

        if (userId >= 0) {
            userManager.setLoggedInUser(username); // Store logged-in username
            logger.log(Level.INFO, "User logged in successfully: {0}", username);

            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/mycompany/requirements/MainMenu.fxml"));
                Parent root = loader.load();

                MainMenuController mainMenuController = loader.getController(); // Get the controller
                mainMenuController.setUserManager(userManager); // Set userManager on MainMenuController

                Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
                stage.setScene(new Scene(root));
                stage.show();

                logger.log(Level.INFO, "Main menu loaded successfully for user: {0}", username);

            } catch (IOException e) {
                logger.log(Level.SEVERE, "Error loading main menu for user: {0} - {1}", new Object[]{username, e.getMessage()});
                e.printStackTrace();
                showAlert(Alert.AlertType.ERROR, "Error loading main menu: " + e.getMessage());
            }
        } else {
            logger.log(Level.WARNING, "Login failed for username: {0}", username);
            showAlert(Alert.AlertType.ERROR, "Invalid username or password.");
        }
    } catch (SQLException | InvalidKeySpecException | IOException e) {
        logger.log(Level.SEVERE, "Login error for username: {0} - {1}", new Object[]{username, e.getMessage()});
        e.printStackTrace();
        showAlert(Alert.AlertType.ERROR, "Login error: " + e.getMessage());
    }
}




@FXML
private void onRegisterButtonClicked(ActionEvent event) throws ClassNotFoundException, InvalidKeySpecException, SQLException {
    if (userManager == null) {
        logger.severe("userManager is NULL in onRegisterButtonClicked!");
        registrationErrorMessage.setText("userManager is not initialized. Please restart the application.");
        return;
    }

    String username = usernameField.getText();
    String password = passwordField.getText();

    if (username.isEmpty() || password.isEmpty()) {
        logger.warning("Registration failed: Username or password is empty.");
        registrationErrorMessage.setText("Username and password cannot be empty.");
    } else if (userManager.checkUserExists(username)) {
        logger.log(Level.WARNING, "Registration failed: Username already exists - {0}", username);
        registrationErrorMessage.setText("Username already exists.");
    } else {
        logger.log(Level.INFO, "Attempting to register user: {0}", username);
        try {
            // Register the new user
            userManager.registerNewUserAnyone(username, password);

            logger.log(Level.INFO, "User registered successfully: {0}", username);

            // Display success alert to the user
            showAlert(Alert.AlertType.INFORMATION, "Registration successful!");

            // Clear the fields
            usernameField.clear();
            passwordField.clear();

            registrationErrorMessage.setText("You can now log in with your new account.");

        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Registration failed for user: {0} - {1}", new Object[]{username, e.getMessage()});
            registrationErrorMessage.setText("Registration failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}



    @FXML
    private void onExecuteButtonClicked(ActionEvent event) {       //will check if remote terminal or local teminal
        if (event.getSource() == executeRemoteButton) {
            isRemoteCommand = true;
        } else if (event.getSource() == executeLocalButton) {
            isRemoteCommand = false;
        }

        String command = commandInputArea.getText();
        terminalOutputArea.appendText("> " + command + "\n"); // Append to output area
        commandInputArea.clear(); // Clear input

        Task<String> task = new Task<String>() {
            @Override
            protected String call() throws Exception {
                if (isRemoteCommand) {
                    try {
                        return remoteTerminal.executeRemoteCommand(command, "your_remote_host", "your_remote_user", "your_remote_password", 2222); // Replace placeholders
                    } catch (JSchException | IOException ex) {
                        throw ex;
                    }
                } else {
                    req23 terminal = new req23();
                    return terminal.runCommand(command);
                }
            }
        };

        task.setOnSucceeded(e -> {
            String output = task.getValue();
            terminalOutputArea.appendText(output);
        });

        task.setOnFailed(e -> {
            terminalOutputArea.appendText("Error: " + task.getException().getMessage() + "\n"); // Append to output
            task.getException().printStackTrace();
        });

        new Thread(task).start();
    }

    private void showAlert(Alert.AlertType alertType, String message) {
        Alert alert = new Alert(alertType);
        alert.setContentText(message);
        alert.showAndWait();
    }

}