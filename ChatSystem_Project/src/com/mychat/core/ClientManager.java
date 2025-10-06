package com.mychat.core;

import com.mychat.db.DBConnection;

import java.net.InetAddress;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientManager {
    private final DBConnection dbConnection;
    private final Map<Integer, InetAddress> clientAddresses;
    private final Map<Integer, Integer> clientPorts;
    private final Map<String, Integer> fileTransfers = new ConcurrentHashMap<>();

    // Constructor
    public ClientManager(DBConnection dbConnection) {
        this.dbConnection = dbConnection;
        this.clientAddresses = new ConcurrentHashMap<>();
        this.clientPorts = new ConcurrentHashMap<>();
    }
    
    public synchronized void addClient(int userId, InetAddress address, int port) throws SQLException {
        clientAddresses.put(userId, address);
        clientPorts.put(userId, port);
        updateStatus(userId, true);
    }
    
    public synchronized void removeClient(int userId) throws SQLException {
        clientAddresses.remove(userId);
        clientPorts.remove(userId);
        updateStatus(userId, false);
    }
    
    public synchronized InetAddress getClientAddress(int userId) {
        return clientAddresses.get(userId);
    }
    
    public synchronized Integer getClientPort(int userId) {
        return clientPorts.get(userId);
    }
    public synchronized void addFileTransfer(int senderId, int receiverId, String fileName) {
        String key = senderId + ":" + fileName;
        fileTransfers.put(key, receiverId);
    }

    public synchronized Integer getReceiverId(int senderId, String fileName) {
        String key = senderId + ":" + fileName;
        Integer receiverId = fileTransfers.get(key);
        if (receiverId == null) {
            System.err.println("No receiver found for key: " + key);
        }
        return receiverId;
    }
    public synchronized void removeFileTransfer(int senderId, String fileName) {
        String key = senderId + ":" + fileName;
        fileTransfers.remove(key);
    }
    public List<Object[]> getUserStatus() throws SQLException {
        List<Object[]> users = new ArrayList<>();
        Connection conn = null;
        
        try {
            conn = dbConnection.getConnection();
            
            String query = "SELECT u.UserID, u.Username, CASE WHEN s.IsOnline = 1 THEN 'Online' ELSE 'Offline' END AS Status " +
                          "FROM Users u LEFT JOIN UserStatus s ON u.UserID = s.UserID";
            
            try (PreparedStatement stmt = conn.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    users.add(new Object[] {
                        rs.getInt("UserID"),
                        rs.getString("Username"),
                        rs.getString("Status")
                    });
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy trạng thái người dùng: " + e.getMessage());
            throw e;
        }
        return users;
    }
    
    private void updateStatus(int userId, boolean isOnline) throws SQLException {
        Connection conn = null;
        
        try {
            conn = dbConnection.getConnection();
            
            String query = "UPDATE UserStatus SET IsOnline = ?, LastActive = GETDATE() WHERE UserID = ?";
            
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setBoolean(1, isOnline);
                stmt.setInt(2, userId);
                
                int rows = stmt.executeUpdate();
                
                if (rows == 0) {
                    String insertQuery = "INSERT INTO UserStatus (UserID, IsOnline, LastActive) VALUES (?, ?, GETDATE())";
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertQuery)) {
                        insertStmt.setInt(1, userId);
                        insertStmt.setBoolean(2, isOnline);
                        insertStmt.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi cập nhật trạng thái người dùng: " + e.getMessage());
            throw e;
        }
    }
    
    public boolean isUserOnline(int userId) {
        return clientAddresses.containsKey(userId) && clientPorts.containsKey(userId);
    }
}