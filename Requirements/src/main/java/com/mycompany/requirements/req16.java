package com.mycompany.requirements;

import java.sql.*;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;



public class req16 extends TimerTask {

    private static final String SQLITE_CONNECTION_STRING = "jdbc:sqlite:/home/ntu-user/NetBeansProjects/Users";
    private static final String MYSQL_CONNECTION_STRING = "jdbc:mysql://172.17.0.9:3306/MysqlUsers"; 
    private static final String MYSQL_USERNAME = "admin";
    private static final String MYSQL_PASSWORD = "Friday14!";
    
    Connection mysqlConn;

    public req16() throws SQLException {
        this.mysqlConn = DriverManager.getConnection(MYSQL_CONNECTION_STRING, MYSQL_USERNAME, MYSQL_PASSWORD);
    }
       

    @Override
    public void run() {
        try (
            Connection sqliteConn = DriverManager.getConnection(SQLITE_CONNECTION_STRING);
            
        ) {
            System.out.println("Connected to SQLite");
            System.out.println("Connected to MySQL");

            // Synchronize userDetails table
            try (
                Statement sqliteStmtUserDetails = sqliteConn.createStatement();
                ResultSet sqliteRsUserDetails = sqliteStmtUserDetails.executeQuery("SELECT * FROM userDetails");
                Statement mysqlStmtUserDetails = mysqlConn.createStatement();
            ) {
                synchronizeTable(sqliteRsUserDetails, mysqlStmtUserDetails, mysqlConn, "MysqlUserDetails"); 
            }

            // Synchronize Files table
            try (
                Statement sqliteStmtFiles = sqliteConn.createStatement();
                ResultSet sqliteRsFiles = sqliteStmtFiles.executeQuery("SELECT * FROM Files"); 
                Statement mysqlStmtFiles = mysqlConn.createStatement();
            ) {
                synchronizeTable(sqliteRsFiles, mysqlStmtFiles, mysqlConn, "MysqlFiles"); 
            }

            // Synchronize SharedFiles table
            try (
                Statement sqliteStmtSharedFiles = sqliteConn.createStatement();
                ResultSet sqliteRsSharedFiles = sqliteStmtSharedFiles.executeQuery("SELECT * FROM SharedFiles"); 
                Statement mysqlStmtSharedFiles = mysqlConn.createStatement();
            ) {
                synchronizeTable(sqliteRsSharedFiles, mysqlStmtSharedFiles, mysqlConn, "MysqlSharedFiles"); 
            }

        } catch (SQLException ex) {
            Logger.getLogger(req16.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private static void synchronizeTable(ResultSet sqliteRs, Statement mysqlStmt, Connection mysqlConn, String mysqlTableName) throws SQLException {
        while (sqliteRs.next()) {
            if (mysqlTableName.equals("MysqlUserDetails")) {
                // Handle userDetails table
                int id = sqliteRs.getInt("id");
                String username = sqliteRs.getString("username");
                String password = sqliteRs.getString("password"); 
                String salt = sqliteRs.getString("userType"); 
                String userType = sqliteRs.getString("salt"); 

                try (PreparedStatement mysqlCheckStmt = mysqlConn.prepareStatement("SELECT * FROM " + mysqlTableName + " WHERE username = ?")) {
                    mysqlCheckStmt.setString(1, username);
                    ResultSet mysqlCheckRs = mysqlCheckStmt.executeQuery();
                    if (mysqlCheckRs.next()) { 
                        System.out.println("Duplicate username: " + username + " found. Skipping insertion.");
                        
                    } else {
                        try (PreparedStatement mysqlInsertStmt = mysqlConn.prepareStatement(
                                "INSERT INTO " + mysqlTableName + " (id, username, password, userType, salt) VALUES (?, ?, ?, ?, ?)")) {
                            mysqlInsertStmt.setInt(1, id); // This line was removed
                            mysqlInsertStmt.setString(1, username); 
                            mysqlInsertStmt.setString(2, password);
                            mysqlInsertStmt.setString(3, userType); 
                            mysqlInsertStmt.setString(4, salt); 
                            mysqlInsertStmt.executeUpdate();
                        }
                    }
                }
            } else if (mysqlTableName.equals("MysqlFiles")) {
                // Handle Files table
                String name = sqliteRs.getString("name"); 
                String location = sqliteRs.getString("location"); 
                int fileId = sqliteRs.getInt("fileId"); // Get fileId from the ResultSet
                int ownerId = sqliteRs.getInt("ownerId"); 

                try (PreparedStatement mysqlCheckStmt = mysqlConn.prepareStatement("SELECT * FROM " + mysqlTableName + " WHERE fileId = ?")) {
                    mysqlCheckStmt.setInt(1, fileId); // Use fileId for the check
                    ResultSet mysqlCheckRs = mysqlCheckStmt.executeQuery();
                    if (mysqlCheckRs.next()) { 
                        try (PreparedStatement mysqlUpdateStmt = mysqlConn.prepareStatement(
                                "UPDATE " + mysqlTableName + " SET name = ?, location = ?, ownerId = ? WHERE fileId = ?")) {
                            mysqlUpdateStmt.setString(1, name); 
                            mysqlUpdateStmt.setString(2, location); 
                            mysqlUpdateStmt.setInt(3, ownerId); 
                            mysqlUpdateStmt.setInt(4, fileId); // Use fileId for the update condition
                            mysqlUpdateStmt.executeUpdate();
                        }
                    } else {
                        try (PreparedStatement mysqlInsertStmt = mysqlConn.prepareStatement(
                                "INSERT INTO " + mysqlTableName + " (fileId, name, location, ownerId) VALUES (?, ?, ?, ?)")) {
                            mysqlInsertStmt.setInt(1, fileId);
                            mysqlInsertStmt.setString(2, name);
                            mysqlInsertStmt.setString(3, location); 
                            mysqlInsertStmt.setInt(4, ownerId); 
                            mysqlInsertStmt.executeUpdate();
                        }
                    }
                }
            } else if (mysqlTableName.equals("MysqlSharedFiles")) {
                // Handle SharedFiles table
                int sharedId = sqliteRs.getInt("sharedId"); 
                int fileId = sqliteRs.getInt("fileId"); 
                int sharedToId = sqliteRs.getInt("sharedToId"); 
                String permissions = sqliteRs.getString("permissions"); 

                try (PreparedStatement mysqlCheckStmt = mysqlConn.prepareStatement("SELECT * FROM " + mysqlTableName + " WHERE sharedId = ? AND fileId = ?")) {
                    mysqlCheckStmt.setInt(1, sharedId); 
                    mysqlCheckStmt.setInt(2, fileId); 
                    ResultSet mysqlCheckRs = mysqlCheckStmt.executeQuery();
                    if (mysqlCheckRs.next()) { 
                        try (PreparedStatement mysqlUpdateStmt = mysqlConn.prepareStatement(
                                "UPDATE " + mysqlTableName + " SET sharedToId = ?, permissions = ? WHERE sharedId = ? AND fileId = ?")) {
                            mysqlUpdateStmt.setInt(1, sharedToId); 
                            mysqlUpdateStmt.setString(2, permissions); 
                            mysqlUpdateStmt.setInt(3, sharedId); 
                            mysqlUpdateStmt.setInt(4, fileId);
                            mysqlUpdateStmt.executeUpdate();
                        }
                    } else {
                        try (PreparedStatement mysqlInsertStmt = mysqlConn.prepareStatement(
                                "INSERT INTO " + mysqlTableName + " (sharedId, fileId, sharedToId, permissions) VALUES (?, ?, ?, ?)")) {
                            mysqlInsertStmt.setInt(1, sharedId); 
                            mysqlInsertStmt.setInt(2, fileId); 
                            mysqlInsertStmt.setInt(3, sharedToId); 
                            mysqlInsertStmt.setString(4, permissions ); 
                            mysqlInsertStmt.executeUpdate();
                        }
                    }
                }
            }
        }
    }
    

    private void displayUserDetails() { 
        try (
            Statement stmt = mysqlConn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM MysqlUserDetails")
        ) {
            System.out.println("\nMysqlUserDetails:");
            while (rs.next()) {
                int id = rs.getInt("id");
                String username = rs.getString("username");
                String password = rs.getString("password"); 
                String salt = rs.getString("salt"); 
                String userType = rs.getString("userType");
                System.out.println("id: " + id + ", username: " + username + ", password: " + password + ", salt: " + salt + ", userType: " + userType);
            }
        } catch (SQLException ex) {
            Logger.getLogger(req16.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void displayFiles() { 
        try (
            Statement stmt = mysqlConn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM MysqlFiles")
        ) {
            System.out.println("\nMysqlFiles:");
            while (rs.next()) {
                int fileId = rs.getInt("fileId");
                String name = rs.getString("name");
                String location = rs.getString("location");
                int ownerId = rs.getInt("ownerId");
                System.out.println("fileId: " + fileId + ", name: " + name + ", location: " + location + ", ownerId: " + ownerId);
            }
        } catch (SQLException ex) {
            Logger.getLogger(req16.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void displaySharedFiles() { 
        try (
            Statement stmt = mysqlConn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM MysqlSharedFiles")
        ) {
            System.out.println("\nMysqlSharedFiles:");
            while (rs.next()) {
                int sharedId = rs.getInt("sharedId");
                int fileId = rs.getInt("fileId");
                int sharedToId = rs.getInt("sharedToId");
                String permissions = rs.getString("permissions");
                System.out.println("sharedId: " + sharedId + ", fileId: " + fileId + ", sharedToId: " + sharedToId + ", permissions: " + permissions);
            }
        } catch (SQLException ex) {
            Logger.getLogger(req16.class.getName()).log(Level.SEVERE, null, ex);
        }
    }


    public static void main(String[] argv) throws SQLException {
        req16 sync = new req16(); 
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(sync, 0, 180000); // Run every 3 mins 
        sync.displayUserDetails(); 
        sync.displayFiles();
        sync.displaySharedFiles();
    }
}