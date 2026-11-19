package com.mycompany.requirements;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.security.spec.InvalidKeySpecException;
import java.sql.SQLException;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.layout.VBox;

public class UserManagementController implements Initializable {

    private req15 userManager;

    public void setUserManager(req15 userManager) {
        System.out.println("UserManagementController: setUserManager called");
        this.userManager = userManager;
        System.out.println("UserManagementController: userManager = " + userManager);
        checkAdminStatus(); // Call checkAdminStatus *after* setting the userManager
    }

    @FXML
    private ChoiceBox<String> createAccountTypeChoiceBox; // Make sure the type is String

    @FXML
    private GridPane updateOwnAccountGrid, createAccountGrid, updateAccountGridAdmin, updateTypeGrid, deleteAccountGrid;

    @FXML
    private TextField updateUsernameField, createUsernameField, updateAccountAdminUsernameField, updateTypeUsernameField, deleteUsernameField;

    @FXML
    private PasswordField updatePasswordField, createPasswordField, updateAccountAdminPasswordField;

    @FXML
    private ChoiceBox<String> updateAccountTypeChoiceBox; // Add this field

    @FXML
    private Button updateAccountButton, updateCancelButton, createUserButton, createCancelButton,
            updateAccountAdminButton, updateAccountAdminCancelButton, updateTypeButton, updateTypeCancelButton,
            deleteUserButton, deleteCancelButton, UserManagementLogoutButton;

    @FXML
    private Label standardUserMessageLabel;

    @FXML
    private VBox userManagementVBox; // Add this if you want to replace content

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        ObservableList<String> userTypeOptions = FXCollections.observableArrayList("Standard", "Admin");
        createAccountTypeChoiceBox.setItems(userTypeOptions);
        createAccountTypeChoiceBox.getSelectionModel().select(0); // Default to "Standard"
        updateAccountTypeChoiceBox.setItems(userTypeOptions);
        updateAccountTypeChoiceBox.getSelectionModel().select(0);
    }

    private void checkAdminStatus() {
        if (userManager != null) {
            String loggedInUser = userManager.getLoggedInUsername();

            if (loggedInUser != null) {
                try {
                    Boolean isAdmin = userManager.isAdmin(loggedInUser);

                    if (isAdmin != null && isAdmin) { // Admin user
                        createAccountGrid.setVisible(true);
                        updateAccountGridAdmin.setVisible(true);
                        updateTypeGrid.setVisible(true);
                        deleteAccountGrid.setVisible(true);
                        updateOwnAccountGrid.setVisible(true); // Admin can update own account too
                        standardUserMessageLabel.setVisible(false);
                    } else { // Standard user or isAdmin is null
                        createAccountGrid.setVisible(false);  // Hide admin grids
                        updateAccountGridAdmin.setVisible(false);
                        updateTypeGrid.setVisible(false);
                        deleteAccountGrid.setVisible(false);

                        updateOwnAccountGrid.setVisible(true); // Show standard user's own account grid ONLY
                        standardUserMessageLabel.setText("You do not have administrative privileges.");
                        standardUserMessageLabel.setVisible(true);
                    }

                } catch (SQLException | ClassNotFoundException e) {
                    e.printStackTrace(); // Use logging in production!
                    showAlert(Alert.AlertType.ERROR, "Error", "Error checking admin status: " + e.getMessage());
                    // Hide ALL grids in case of error
                    createAccountGrid.setVisible(false);
                    updateAccountGridAdmin.setVisible(false);
                    updateTypeGrid.setVisible(false);
                    deleteAccountGrid.setVisible(false);
                    updateOwnAccountGrid.setVisible(false);
                    standardUserMessageLabel.setVisible(false);
                }
            }
        } else {
            System.err.println("userManager is null in checkAdminStatus()!");
            return;
        }
    }

    // Update Own Account
    @FXML
    private void onUpdateAccountButtonClicked(ActionEvent event) throws ClassNotFoundException {
        String newUsername = updateUsernameField.getText();
        String newPassword = updatePasswordField.getText();
        String loggedInUsername = userManager.getLoggedInUsername();

        try {
            userManager.updateUser(loggedInUsername, loggedInUsername, newPassword); // Self-update: usernameToUpdate is loggedInUsername
            showAlert(Alert.AlertType.INFORMATION, "Success", "Your account has been updated successfully.");
        } catch (IllegalArgumentException | SecurityException | SQLException | InvalidKeySpecException e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to update your account: " + e.getMessage());
            e.printStackTrace(); // Debugging only. Use logging in production!
        }
    }

    @FXML
    private void onUpdateCancelButtonClicked(ActionEvent event) {
        updateUsernameField.clear();
        updatePasswordField.clear();
    }

    // Create Account (Admin)
    @FXML
    private void onCreateUserButtonClicked(ActionEvent event) {
        // Get the currently logged-in user's username
        String loggedInUser = userManager.getLoggedInUsername();

        // Get the new user details from the UI
        String newUsername = createUsernameField.getText();
        String newPassword = createPasswordField.getText();

        // Get selected account type from the ChoiceBox
        String userType = createAccountTypeChoiceBox.getValue();

        // Check if the necessary fields are not empty
        if (newUsername.isEmpty() || newPassword.isEmpty() || userType == null) {
            showAlert(Alert.AlertType.ERROR, "Error", "Username, password, and account type cannot be empty.");
            return;
        }

        try {
            // You can optionally log or audit the creation action using loggedInUser
            System.out.println("Admin " + loggedInUser + " is creating a user: " + newUsername);

            try {
                // Call the method that handles creating the new user
                userManager.createUserAsAnAdmin(newUsername, newPassword, userType.toLowerCase());
            } catch (ClassNotFoundException ex) {
                Logger.getLogger(UserManagementController.class.getName()).log(Level.SEVERE, null, ex);
            }

            // Clear input fields
            createUsernameField.clear();
            createPasswordField.clear();

            // Show success message to user
            showAlert(Alert.AlertType.INFORMATION, "Success", "User created successfully.");

        } catch (IllegalArgumentException | SecurityException | SQLException | InvalidKeySpecException e) {
            // Show error message to user if something goes wrong
            showAlert(Alert.AlertType.ERROR, "Error", e.getMessage());
            e.printStackTrace(); // For debugging purposes, remove in production
        }
    }

    @FXML
    private void onCreateCancelButtonClicked(ActionEvent event) {
        createUsernameField.clear();
        createPasswordField.clear();
    }

    // Update Account (Admin)
    @FXML
    private void onUpdateAccountAdminButtonClicked(ActionEvent event) throws ClassNotFoundException {
        String usernameToUpdate = updateAccountAdminUsernameField.getText();
        String newPassword = updateAccountAdminPasswordField.getText();
        String loggedInUsername = userManager.getLoggedInUsername();

        try {
            userManager.updateUser(loggedInUsername, usernameToUpdate, newPassword); // Admin-update: usernameToUpdate is different
            showAlert(Alert.AlertType.INFORMATION, "Success", "Account updated successfully.");
        } catch (IllegalArgumentException | SecurityException | SQLException | InvalidKeySpecException e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to update account: " + e.getMessage());
            e.printStackTrace(); // Debugging only. Use logging in production!
        }
    }

    @FXML
    private void onUpdateAccountAdminCancelButtonClicked(ActionEvent event) {
        updateAccountAdminUsernameField.clear();
        updateAccountAdminPasswordField.clear();
    }

    // Update Type (Admin)
    @FXML
    private void onUpdateTypeButtonClicked(ActionEvent event) throws SQLException, InvalidKeySpecException, ClassNotFoundException {
        String usernameToUpdate = updateTypeUsernameField.getText();
        String newType = updateAccountTypeChoiceBox.getValue(); // Get selected value from ChoiceBox
        String loggedInUsername = userManager.getLoggedInUsername();

        if (usernameToUpdate.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Error", "Username to update cannot be empty.");
            return;
        }

        if (newType == null || newType.isEmpty()) { // Check for null or empty selection
            showAlert(Alert.AlertType.ERROR, "Error", "An account type must be selected.");
            return;
        }

        try {
            userManager.updateUserType(loggedInUsername, usernameToUpdate, newType);
            updateTypeUsernameField.clear();
            updateAccountTypeChoiceBox.setValue(null); // Clear selection
            showAlert(Alert.AlertType.INFORMATION, "Success", "User type updated successfully.");
        } catch (IllegalArgumentException | SecurityException e) {
            showAlert(Alert.AlertType.ERROR, "Error", e.getMessage());
        }
    }

    @FXML
    private void onUpdateTypeCancelButtonClicked(ActionEvent event) {
        updateTypeUsernameField.clear();
    }

    // Delete Account (Admin)
    @FXML
    private void onDeleteUserButtonClicked(ActionEvent event) throws SQLException, InvalidKeySpecException, ClassNotFoundException {
        String usernameToDelete = deleteUsernameField.getText();
        String loggedInUsername = userManager.getLoggedInUsername(); // Get logged-in username

        if (loggedInUsername == null) {
            return;
        }
        if (usernameToDelete.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Error", "Username to delete cannot be empty.");
            return;
        }

        try {
            userManager.deleteUser(loggedInUsername, usernameToDelete); // Pass loggedInUsername!
            deleteUsernameField.clear();
            showAlert(Alert.AlertType.INFORMATION, "Success", "User deleted successfully.");
        } catch (IllegalArgumentException | SecurityException e) {
            showAlert(Alert.AlertType.ERROR, "Error", e.getMessage());
        }
    }

    @FXML
    private void onDeleteCancelButtonClicked(ActionEvent event) {
        deleteUsernameField.clear();
    }

    @FXML
    private void onUserManagementLogoutButtonClicked(ActionEvent event) {
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

    private void showAlert(Alert.AlertType alertType, String title, String message) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null); // Optional: You can set a header if needed
        alert.setContentText(message);
        alert.showAndWait();
    }
}
