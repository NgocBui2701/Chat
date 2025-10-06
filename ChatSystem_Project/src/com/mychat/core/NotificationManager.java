package com.mychat.core;

import com.mychat.db.DatabaseHelper;
import com.mychat.db.DBConnection;

import java.util.List;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.Connection;
import java.sql.PreparedStatement;

public class NotificationManager {
    private final DatabaseHelper dbHelper;
    private final DBConnection dbConnection;
    private final int userId;

    // Constructor
    public NotificationManager(DatabaseHelper dbHelper, DBConnection dbConnection, int userId) {
        this.dbHelper = dbHelper;
        this.dbConnection = dbConnection;
        this.userId = userId;
    }
    
    public void createNotification(String content) throws SQLException {
        Connection conn = null;
        
        try {
            conn = dbConnection.getConnection();
            
            String query = "INSERT INTO Notifications (UserID, Content, IsRead) VALUES (?, ?, 0)";
            
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, userId);
                stmt.setNString(2, content);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi tạo thông báo: " + e.getMessage());
            throw e;
        }
    }
    
    public int getUnreadCount() throws SQLException {
        try {
            List<String> notifications = dbHelper.getNotifications(userId);
            return notifications.size();
        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy số lượng thông báo chưa đọc: " + e.getMessage());
            throw e;
        }
    }
    
    public List<String> getNotifications() throws SQLException {
        try {
            return dbHelper.getNotifications(userId);
        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy thông báo: " + e.getMessage());
            throw e;
        }
    }
    
    public void markAsRead(String notification) throws SQLException {
        Connection conn = null;
        
        try {
            conn = dbConnection.getConnection();
            
            // Parse notification content more carefully
            String content = notification;
            String timestamp = "";
            
            // Extract content and timestamp more reliably
            int bracketIndex = notification.lastIndexOf(" [");
            if (bracketIndex > 0) {
                content = notification.substring(0, bracketIndex);
                timestamp = notification.substring(bracketIndex + 2, notification.length() - 1);
            }
            
            // Find notification by content rather than exact timestamp match
            String query = "SELECT NotificationID FROM Notifications WHERE UserID = ? AND Content = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, userId);
                stmt.setString(2, content);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int notificationId = rs.getInt("NotificationID");
                        dbHelper.markNotificationAsRead(notificationId);
                    } else {
                        System.err.println("Không tìm thấy thông báo có nội dung: " + content);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Có lỗi khi đánh dấu thông báo đã đọc: " + e.getMessage());
            throw new SQLException("Lỗi đánh dấu thông báo: " + e.getMessage(), e);
        }
    }
}