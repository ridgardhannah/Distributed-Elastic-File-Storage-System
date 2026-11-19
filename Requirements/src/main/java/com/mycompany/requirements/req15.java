package com.mycompany.requirements;

/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */





import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;



public class req15 {
    public static String fileName = "jdbc:sqlite:/home/ntu-user/NetBeansProjects/Users";
    private int timeout = 30;
    private String dataBaseName = "Users";
    private String dataBaseTableName = "userDetails";
    Connection connection = null;
    private Random random = new SecureRandom();
    private String characters = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private int iterations = 10000;
    private int keylength = 256;
    private String saltValue;
    public static String loggedInUsername;
    private int userId;
    private String userType;
    

    public req15() {
        try {
            File fp = new File(".salt");
            if (!fp.exists()) {
                saltValue = getSaltvalue(30);
                try (FileWriter myWriter = new FileWriter(fp)) {
                    myWriter.write(saltValue);
                }
            } else {
                try (Scanner myReader = new Scanner(fp)) {
                    if (myReader.hasNextLine()) {
                        saltValue = myReader.nextLine();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace(); // Keep for debugging, consider logging
        }

        this.loggedInUsername = null; // Initialize user session as null
    }


    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void setDataBaseTableName(String dataBaseTableName) {
        this.dataBaseTableName = dataBaseTableName;
    }

    public void setLoggedInUser(String username) {
        this.loggedInUsername = username;
        System.out.println("User logged in: " + username);
    }

    public String getLoggedInUsername() {
        return loggedInUsername;
    }

  
    
public int loginUser(String username, String password) throws SQLException, InvalidKeySpecException, IOException {
    try (Connection connection = DriverManager.getConnection(fileName);
         PreparedStatement statement = connection.prepareStatement(
                 "SELECT id, password, salt, userType FROM userDetails WHERE username = ?")) {  // Ensure you're getting user_type from the DB
        statement.setString(1, username);

        try (ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                int userId = resultSet.getInt("id");
                String storedHashedPassword = resultSet.getString("password");
                String storedSalt = resultSet.getString("salt");

                // Check user type from the DB
                String userType = resultSet.getString("userType");  // Assuming you store this as 'user_type'
                System.out.println("User Type: " + userType);

                String enteredHashedPassword = generateSecurePassword(password, storedSalt);

                if (storedHashedPassword.equals(enteredHashedPassword)) {
                    loggedInUsername = username;
                    this.userId = userId;

                    // Set additional user info if necessary, like role
                    this.userType = userType;

                    return userId;
                } else {
                    throw new IOException("Invalid password.");
                }
            } else {
                throw new IOException("Invalid username.");
            }
        }
    }
}




public boolean checkLogin(String username, String password) {
        try (Connection connection = DriverManager.getConnection(fileName);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT password, salt FROM userDetails WHERE username = ?")) {

            statement.setString(1, username);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    String storedHashedPassword = resultSet.getString("password");
                    String storedSalt = resultSet.getString("salt");

                    String enteredHashedPassword = generateSecurePassword(password, storedSalt);

                    return storedHashedPassword.equals(enteredHashedPassword);
                } else {
                    return false;
                }
            }
        } catch (SQLException | InvalidKeySpecException  e) {
            e.printStackTrace(); // Handle exceptions appropriately (log them)
            return false;
        }
    }
 
 public boolean checkUserExists(String username) {
      System.out.println("Checking if user exists: " + username);
        try (Connection connection = DriverManager.getConnection(fileName);
             PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM userDetails WHERE username = ?")) {

            statement.setString(1, username);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next(); // Returns true if a row is found (user exists)
                
                
                
            }
        } catch (SQLException e) {
            System.err.println("Error checking user existence: " + e.getMessage()); // Print error to console
            e.printStackTrace(); // Print stack trace for debugging
            return false; // Assume user doesn't exist in case of an error
        }
    }


    public void createTable(String tableName) throws ClassNotFoundException, InvalidKeySpecException, SQLException {
        try (Connection connection = DriverManager.getConnection(fileName);
             Statement statement = connection.createStatement()) {

            statement.setQueryTimeout(timeout);

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + tableName +
                    "(id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    " username VARCHAR(100) NOT NULL, " +
                    " password VARCHAR(100) NOT NULL, " +
                    " userType VARCHAR(20) DEFAULT 'standard', " +
                    " salt VARCHAR(100))");

            ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + tableName);
            rs.next();
            int rowCount = rs.getInt(1);

            if (rowCount == 0) {
                String defaultUsername = "Admin1";
                String defaultPassword = "Admin1"; // **Replace with a more secure default password or remove**
                String salt = generateSalt();
                String hashedPassword = generateSecurePassword(defaultPassword, salt);

                try (PreparedStatement pstmt = connection.prepareStatement("INSERT INTO " + tableName +
                        "(username, password, userType, salt) VALUES (?, ?, ?, ?)")) {
                    pstmt.setString(1, defaultUsername);
                    pstmt.setString(2, hashedPassword);
                    pstmt.setString(3, "admin");
                    pstmt.setString(4, salt);
                    pstmt.executeUpdate();
                }

                System.out.println("Default admin user 'Admin1' created.");
            }
        } catch (SQLException ex) {
            Logger.getLogger(req15.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }


    public User registerNewUserAnyone(String username, String password) throws InvalidKeySpecException, ClassNotFoundException, SQLException {
    System.out.println("registerNewUserAnyone called for: " + username);  // Verify method entry
    String salt = generateSalt();
    String hashedPassword = generateSecurePassword(password, salt);

    System.out.println("Generated salt: " + salt); // Check the generated salt
    System.out.println("Generated hashedPassword: " + hashedPassword); // Check the generated hash

    try (Connection connection = DriverManager.getConnection(fileName);
         PreparedStatement pstmt = connection.prepareStatement("INSERT INTO userDetails (username, password, userType, salt) VALUES (?, ?, ?, ?)")) {

        System.out.println("Database connection established."); // Verify connection

        pstmt.setString(1, username);
        pstmt.setString(2, hashedPassword);
        pstmt.setString(3, "standard");
        pstmt.setString(4, salt);

        System.out.println("Executing SQL: " + pstmt); // Print the SQL (for debugging)

        int rowsInserted = pstmt.executeUpdate();

        System.out.println("Rows inserted: " + rowsInserted); // Check how many rows were inserted

        ResultSet generatedKeys = pstmt.getGeneratedKeys();
        int newUserId = 0;
        if (generatedKeys.next()) {
            newUserId = generatedKeys.getInt(1);
            System.out.println("Generated user ID: " + newUserId); // Check the generated ID
        } else {
            System.out.println("No generated keys found."); // Check if keys were returned
        }

        return new User(newUserId, username, hashedPassword);

    } catch (SQLException ex) {
        System.err.println("SQL Exception during registration: " + ex.getMessage()); // Print the exception message
        ex.printStackTrace(); // VERY IMPORTANT: Print the full stack trace
        throw ex; // Re-throw the exception
    }
}

    public void createUserAsAnAdmin(String newUsername, String newPassword, String userType) throws InvalidKeySpecException, ClassNotFoundException, SQLException, IllegalArgumentException, SecurityException {
        System.out.println("Logged in as: " + loggedInUsername);
        if (!isAdmin(loggedInUsername)) {
            throw new SecurityException("You are not authorized to create users.");
        }

        if (isUsernameTaken(newUsername)) {
            throw new IllegalArgumentException("Username '" + newUsername + "' is already taken.");
        }

        String salt = generateSalt();
        String hashedPassword = generateSecurePassword(newPassword, salt);

        try (Connection connection = DriverManager.getConnection(fileName);
             PreparedStatement pstmt = connection.prepareStatement("INSERT INTO " + dataBaseTableName + " (username, password, userType, salt) VALUES (?, ?, ?, ?)")) {
            pstmt.setString(1, newUsername);
            pstmt.setString(2, hashedPassword);
            pstmt.setString(3, userType);
            pstmt.setString(4, salt);
            pstmt.executeUpdate();
        } catch (SQLException ex) {
            Logger.getLogger(req15.class.getName()).log(Level.SEVERE, null, ex);
            throw ex;
        }
    }

    private boolean isUsernameTaken(String username) throws SQLException, ClassNotFoundException {
        try (Connection connection = DriverManager.getConnection(fileName);
             PreparedStatement pstmt = connection.prepareStatement("SELECT 1 FROM " + dataBaseTableName + " WHERE username = ?")) {
            pstmt.setString(1, username);
            try (var rs = pstmt.executeQuery()) {
                return rs.next();
            }
        }
    }
  
public boolean isAdmin(String username) throws SQLException, ClassNotFoundException {
        String userType = getUserType(username);
        return userType != null && "admin".equals(userType);
    }

    public String getUserType(String username) throws SQLException, ClassNotFoundException {
        try (Connection connection = DriverManager.getConnection(fileName);
             PreparedStatement pstmt = connection.prepareStatement("SELECT userType FROM " + dataBaseTableName + " WHERE username = ?")) {
            pstmt.setString(1, username);
            try (var rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("userType");
                }
            }
        }
        return null;
    }
    

public void updateUser(String loggedInUsername, String usernameToUpdate, String newPassword) throws InvalidKeySpecException, ClassNotFoundException, SQLException, IllegalArgumentException, SecurityException {
    int loggedInUserId = getUserId(loggedInUsername);
    int userIdToUpdate = getUserId(usernameToUpdate);

    if (loggedInUserId == -1) {
        throw new IllegalArgumentException("Logged-in user not found.");
    }

    if (userIdToUpdate == -1) {  // User to update must exist
        throw new IllegalArgumentException("User to update not found.");
    }


    if (loggedInUsername.equals(usernameToUpdate) || isAdmin(loggedInUsername)) { // Self-update or admin-update
        try (Connection connection = DriverManager.getConnection(fileName)) {
            String sql = "UPDATE " + dataBaseTableName + " SET ";

            List<String> updates = new ArrayList<>();
            List<Object> values = new ArrayList<>();

            if (newPassword != null && !newPassword.isEmpty()) {
                String salt = generateSalt();
                String hashedPassword = generateSecurePassword(newPassword, salt);
                updates.add("password = ?");
                updates.add("salt = ?");
                values.add(hashedPassword);
                values.add(salt);
            }

            if (usernameToUpdate != null && !usernameToUpdate.isEmpty() && !usernameToUpdate.equals(loggedInUsername)) {
                updates.add("username = ?");
                values.add(usernameToUpdate);
            }

            if (!updates.isEmpty()) {
                sql += String.join(", ", updates);
                sql += " WHERE id = ?";
                if(loggedInUsername.equals(usernameToUpdate)) {
                    values.add(loggedInUserId);
                } else {
                    values.add(userIdToUpdate);
                }


                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    for (int i = 0; i < values.size(); i++) {
                        pstmt.setObject(i + 1, values.get(i));
                    }
                    pstmt.executeUpdate();
                }
            }
        }
    } else {
        throw new SecurityException("You are not authorized to update this account.");
    }
}

    private int getUserId(String username) throws SQLException {
        try (Connection connection = DriverManager.getConnection(fileName);
             PreparedStatement stmt = connection.prepareStatement(
                     "SELECT id FROM userDetails WHERE username = ?")) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        return -1;
    }

    public boolean updateUserType(String loggedInUsername, String usernameToUpdate, String newUserType) throws SQLException, ClassNotFoundException, SecurityException, IllegalArgumentException {
    if (!isAdmin(loggedInUsername)) {
        throw new SecurityException("You are not authorized to update user types.");
    }

    try (Connection connection = DriverManager.getConnection(fileName);
         PreparedStatement statement = connection.prepareStatement("UPDATE " + dataBaseTableName + " SET userType = ? WHERE username = ?")) {

        statement.setString(1, newUserType);
        statement.setString(2, usernameToUpdate);

        int rowsAffected = statement.executeUpdate();

        if (rowsAffected > 0) {
            return true; // Return true on success
        } else {
            throw new IllegalArgumentException("User type update failed. No matching user found for: " + usernameToUpdate);
        }

    } catch (SQLException ex) {
        Logger.getLogger(req15.class.getName()).log(Level.SEVERE, null, ex);
        throw ex; // Re-throw the exception after logging
    }
}

    public boolean validateUser(String user, String pass) throws InvalidKeySpecException, ClassNotFoundException, SQLException {
        boolean isValid = false;

        try (Connection connection = DriverManager.getConnection(fileName);
             PreparedStatement statement = connection.prepareStatement("SELECT password, salt FROM " + dataBaseTableName + " WHERE username = ?")) {

            statement.setString(1, user);

            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    String storedHashedPassword = rs.getString("password");
                    String storedSalt = rs.getString("salt");

                    String enteredHashedPassword = generateSecurePassword(pass, storedSalt);

                    if (storedHashedPassword.equals(enteredHashedPassword)) {
                        isValid = true;
                    }
                }
            }
        }

        return isValid;
    }

   public void deleteUser(String loggedInUsername, String usernameToDelete) throws ClassNotFoundException, SQLException, SecurityException, IllegalArgumentException {
    if (!isAdmin(loggedInUsername)) { // Use loggedInUsername here
        throw new SecurityException("You are not authorized to delete users.");
    }

    try (Connection connection = DriverManager.getConnection(fileName);
         PreparedStatement pstmt = connection.prepareStatement("DELETE FROM " + dataBaseTableName + " WHERE username = ?")) {

        pstmt.setString(1, usernameToDelete);
        int rowsDeleted = pstmt.executeUpdate();

        if (rowsDeleted > 0) {
            System.out.println("User deleted successfully."); // Or return a boolean
        } else {
            throw new IllegalArgumentException("User not found.");
        }
    } catch (SQLException ex) {
        Logger.getLogger(req15.class.getName()).log(Level.SEVERE, null, ex);
        throw ex;
    }
}

    private String getSaltvalue(int length) {
        // Add logic to generate salt value
        return "randomSaltValue"; // Placeholder, replace with actual generation logic
    }

    public boolean isLoggedIn() {
        return loggedInUsername != null;
    }

    public void logout() {
        System.out.println("Logging out user: " + loggedInUsername);
        this.loggedInUsername = null;
    }

    public String generateSecurePassword(String password, String salt) throws InvalidKeySpecException {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt.getBytes(), iterations, keylength);
        SecretKeyFactory skf = null;
        try {
            skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(req15.class.getName()).log(Level.SEVERE, null, ex);
        }
        var securePassword = skf.generateSecret(spec).getEncoded();
        return Base64.getEncoder().encodeToString(securePassword);
    }

    public String getTableName() {
        return this.dataBaseTableName;
    }

    public void log(String message) {
        System.out.println(message);
    }

    public int getUserId() throws SQLException {
        try (Connection connection = DriverManager.getConnection(fileName);
             PreparedStatement stmt = connection.prepareStatement(
                     "SELECT id FROM userDetails WHERE username = ?")) {
            stmt.setString(1, loggedInUsername);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        System.err.println("User '" + loggedInUsername + "' not found in the database.");
        return -1;
    }

    private byte[] hash(char[] password, byte[] salt) throws InvalidKeySpecException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keylength);
        Arrays.fill(password, Character.MIN_VALUE);
        try {
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            return skf.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new AssertionError("Error while hashing a password: " + e.getMessage(), e);
        } finally {
            spec.clearPassword();
        }
    }

    void setUserId(int userId) {
        this.userId = userId;
    }

}
