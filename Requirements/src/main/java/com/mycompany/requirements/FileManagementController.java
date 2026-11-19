package com.mycompany.requirements;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ResourceBundle;
import javafx.geometry.Insets;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.eclipse.paho.client.mqttv3.MqttException;

public class FileManagementController implements Initializable {

    private req4 fileManager; // Injected instance of req4 for file operations
    private req15 userManager; // Injected instance for user management
    private MQTTHelper mqttHelper; // Integrate MQTT communication

    public void setFileManager(req4 fileManager) {
        this.fileManager = fileManager;
    }

    public void setUserManager(req15 userManager) {
        System.out.println("UserManagementController: setUserManager called");
        this.userManager = userManager;
        System.out.println("UserManagementController: userManager = " + userManager);
    }

    @FXML
    private Label writeFileLabel;

    @FXML
    private TextArea writeFileTextArea;

    @FXML
    private Button submitWriteButton;

    private String currentFilePath; // Store file path when accessing

    // FXML Elements for Create File Section
    @FXML private GridPane createFileGrid;
    @FXML private TextField createFileNameTextField;
    @FXML private TextArea createFileContentTextArea;
    @FXML private Button createFileButton, createFileCancelButton;

    // FXML Elements for Update File Section
    @FXML private GridPane updateFileGrid;
    @FXML private TextField updateFileNameTextField;
    @FXML private Button updateFileButton, updateFileCancelButton;

    // FXML Elements for Delete File Section
    @FXML private GridPane deleteFileGrid;
    @FXML private TextField deleteFileNameTextField;
    @FXML private Button deleteFileButton, deleteFileCancelButton;

    // FXML Elements for Set Permissions Section
    @FXML private GridPane setPermissionsGrid;
    @FXML private TextField setPermissionsFileNameTextField;
    @FXML private TextField shareWithTextField;
    @FXML private Button readPermissionButton, writePermissionButton, readAndWritePermissionButton;

    // FXML Elements for Access File Section
    @FXML private GridPane accessFileGrid;
    @FXML private TextField accessFileNameTextField;
    @FXML private Button accessFileButton, accessFileCancelButton;

    // Main VBox container
    @FXML private VBox mainVBox;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        Insets padding = new Insets(15.0, 15.0, 15.0, 15.0);  // Top, Right, Bottom, Left
        // Apply padding to each GridPane
        createFileGrid.setPadding(padding);
        updateFileGrid.setPadding(padding);
        deleteFileGrid.setPadding(padding);
        setPermissionsGrid.setPadding(padding);
        accessFileGrid.setPadding(padding);

        // Apply padding to the VBox
        mainVBox.setPadding(padding);
    }

    // Event handler for Create File Button
    @FXML
    private void onCreateFileButtonClicked(ActionEvent event) {
        if (fileManager == null) {
            showAlert(Alert.AlertType.ERROR, "Error", "File Manager is not initialized!");
            return;
        }

        String fileName = createFileNameTextField.getText();
        String fileContent = createFileContentTextArea.getText();

        String fileLocation = "/home/ntu-user/NetBeansProjects/FileTempFolder/";

        if (fileName.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "File Creation Failed", "Please enter a file name.");
            return;
        }

        try {
            fileManager.createFileWithContent(fileName, fileContent, fileLocation);
            showAlert(Alert.AlertType.INFORMATION, "File Created", "File " + fileName + " has been created.");
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "File Creation Failed", "There was an issue creating the file: " + e.getMessage());
        }
    }
    
    private void handleMqttResponse(String topic, String message) {
        showAlert(Alert.AlertType.INFORMATION, "Response Received", "Message: " + message);
    }

    // Event handler for Create File Cancel Button
    @FXML
    private void onCreateFileCancelButtonClicked(ActionEvent event) {
        createFileNameTextField.clear();
        createFileContentTextArea.clear();
    }

    // Event handler for Update File Button
    @FXML
    private void onUpdateFileButtonClicked(ActionEvent event) throws SQLException, MqttException {
        String fileName = updateFileNameTextField.getText();
        if (fileName.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "File Update Failed", "Please enter a file name.");
            return;
        }

        try {
            fileManager.updateFile(fileName);
            openFileInEditor(fileName);
            showAlert(Alert.AlertType.INFORMATION, "File Updated", "File " + fileName + " has been updated.");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "File Update Failed", "There was an issue updating the file: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            showAlert(Alert.AlertType.ERROR, "File Update Failed", e.getMessage());
        }
    }

    private void openFileInEditor(String fileName) throws IOException, SQLException {
        String fileLocation = fileManager.getFileLocation(fileName);
        Path filePath = Paths.get(fileLocation, fileName);
        if (Files.exists(filePath)) {
            String command = "mousepad " + filePath.toString();  // Adjust if necessary
            Process process = Runtime.getRuntime().exec(command);
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                throw new IOException("Error opening file in editor.");
            }
        } else {
            throw new IOException("File does not exist or invalid location.");
        }
    }

    // Event handler for Update File Cancel Button
    @FXML
    private void onUpdateFileCancelButtonClicked(ActionEvent event) {
        updateFileNameTextField.clear();
    }

    // Event handler for Delete File Button
    @FXML
    private void onDeleteFileButtonClicked(ActionEvent event) throws SQLException, IOException, MqttException {
        String fileName = deleteFileNameTextField.getText();
        if (fileName.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "File Deletion Failed", "Please enter a file name.");
        } else {
            fileManager.deleteFile(fileName);
            showAlert(Alert.AlertType.INFORMATION, "File Deleted", "File " + fileName + " has been deleted.");
        }
    }

    // Event handler for Delete File Cancel Button
    @FXML
    private void onDeleteFileCancelButtonClicked(ActionEvent event) {
        deleteFileNameTextField.clear();
    }

    // Event handler for Set Read Permission Button
    @FXML
    private void onReadPermissionButtonClicked(ActionEvent event) {
        setFilePermissions("Read Only");
    }

    // Event handler for Set Write Permission Button
    @FXML
    private void onWritePermissionButtonClicked(ActionEvent event) {
        setFilePermissions("Write Only");
    }

    // Event handler for Set Read & Write Permission Button
    @FXML
    private void onReadAndWritePermissionButtonClicked(ActionEvent event) {
        setFilePermissions("Read and Write");
    }

    private void setFilePermissions(String permission) {
        String fileName = setPermissionsFileNameTextField.getText();
        String sharedWith = shareWithTextField.getText();

        try {
            String result = fileManager.setFilePermissions(fileName, sharedWith, permission);
            showAlert(Alert.AlertType.INFORMATION, "Permissions Set", result);
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error", "An error occurred while setting permissions: " + e.getMessage());
        }
    }

    // Event handler for Set Permissions Cancel Button
    @FXML
    private void onSetPermissionsCancelButtonClicked(ActionEvent event) {
        setPermissionsFileNameTextField.clear();
        shareWithTextField.clear();
        showAlert(Alert.AlertType.INFORMATION, "Permissions Canceled", "Permissions changes have been canceled.");
    }

    // Event handler for Access File Button
    @FXML
    private void onAccessFileButtonClicked(ActionEvent event) {
        String fileName = accessFileNameTextField.getText();
        if (fileName.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "File Access Failed", "Please enter a file name.");
        } else {
            try {
                String result = fileManager.accessFile(fileName);
                showAlert(Alert.AlertType.INFORMATION, "File Access", result);
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "File Access Failed", "An error occurred: " + e.getMessage());
            }
        }
    }

    @FXML
private void onSubmitWriteButtonClicked(ActionEvent event) {
    // Get the new content from the TextArea
    String newContent = writeFileTextArea.getText();

    // Get the file name (you can also use this to identify the file)
    String fileName = accessFileNameTextField.getText();

    if (newContent.isEmpty()) {
        showAlert(Alert.AlertType.ERROR, "Error", "The content cannot be empty.");
        return;
    }

    if (fileName.isEmpty()) {
        showAlert(Alert.AlertType.ERROR, "Error", "Please specify a file name.");
        return;
    }

    try {
        // Call the updateFile method and just handle any exceptions directly
        fileManager.updateFile(fileName);
        showAlert(Alert.AlertType.INFORMATION, "File Updated", "The file has been successfully updated.");
    } catch (IOException e) {
        showAlert(Alert.AlertType.ERROR, "Error", "There was an issue updating the file: " + e.getMessage());
    } catch (Exception e) {
        showAlert(Alert.AlertType.ERROR, "Error", "An unexpected error occurred: " + e.getMessage());
    }

    // Reset the TextArea and hide the submit button (optional behavior)
    writeFileTextArea.clear();
    writeFileTextArea.setVisible(false);
    submitWriteButton.setVisible(false);
}


    // Event handler for Access File Cancel Button
    @FXML
    private void onAccessFileCancelButtonClicked(ActionEvent event) {
        accessFileNameTextField.clear();
    }

    // Method to show alerts
    private void showAlert(Alert.AlertType alertType, String title, String message) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Mouse Event Handler for Resetting UI Elements
    @FXML
    private void onMouseClickedResetFields(MouseEvent event) {
        createFileNameTextField.clear();
        updateFileNameTextField.clear();
        deleteFileNameTextField.clear();
        setPermissionsFileNameTextField.clear();
        accessFileNameTextField.clear();
    }
    
    
    @FXML
    private void onFileManagementLogoutButtonClicked(ActionEvent event) {
        // Log out the current user
        userManager.logout();
        System.out.println("Logging out user: " + userManager.getLoggedInUsername());

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

            System.out.println("Navigated back to the login screen.");
        } catch (IOException e) {
            System.err.println("Error loading login screen: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
