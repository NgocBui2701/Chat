package com.mychat.db;

import com.mychat.security.MD5;
import com.mychat.security.VigenereCipher;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper {
    private final DBConnection dbConnection;
    
    public DatabaseHelper(DBConnection dbConnection) {
        this.dbConnection = dbConnection;
    }
    
    public boolean registerUser(String username, String password, String role, String fullName, String email, String phoneNumber) throws SQLException {
        Connection conn = null;
        boolean autoCommit = true;
        
        try {
            conn = dbConnection.getConnection();
            autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            
            String userQuery = "INSERT INTO Users (Username, Password, Role) VALUES (?, ?, ?)";
            String profileQuery = "INSERT INTO UserProfiles (UserID, FullName, Email, Phone) VALUES (?, ?, ?, ?)";
            String userStatus = "INSERT INTO UserStatus (UserID, IsOnline, LastActive) VALUES (?, 0, GETDATE())";
            
            // Insert into Users table
            try (PreparedStatement userStmt = conn.prepareStatement(userQuery, PreparedStatement.RETURN_GENERATED_KEYS)) {
                userStmt.setString(1, username);
                userStmt.setString(2, MD5.getMD5(password)); // Giữ nguyên MD5 theo yêu cầu
                userStmt.setString(3, role);
                userStmt.executeUpdate();
                
                ResultSet rs = userStmt.getGeneratedKeys();
                if (!rs.next()) {
                    conn.rollback();
                    return false;
                }
                
                int userId = rs.getInt(1);
                
                // Insert into UserProfiles table
                try (PreparedStatement profileStmt = conn.prepareStatement(profileQuery)) {
                    profileStmt.setInt(1, userId);
                    profileStmt.setString(2, fullName);
                    profileStmt.setString(3, email);
                    profileStmt.setString(4, phoneNumber);
                    profileStmt.executeUpdate();
                }
                
                // Insert into UserStatus table
                try (PreparedStatement statusStmt = conn.prepareStatement(userStatus)) {
                    statusStmt.setInt(1, userId);
                    statusStmt.executeUpdate();
                }
                
                conn.commit();
                return true;
            }
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    System.err.println("Lỗi khi rollback: " + ex.getMessage());
                }
            }
            System.err.println("Lỗi khi đăng ký người dùng: " + e.getMessage());
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(autoCommit);
                } catch (SQLException e) {
                    System.err.println("Lỗi khi đặt lại autoCommit: " + e.getMessage());
                }
            }
        }
    }
    public int checkLogin(String username, String password, String ipAddress) throws SQLException {
        Connection conn = null;
        
        try {
            conn = dbConnection.getConnection();
            
            String query = "SELECT UserID FROM Users WHERE Username = ? AND Password = ?";
            String loginHistoryQuery = "INSERT INTO LoginHistory (UserID, Status, IPAddress) VALUES (?, ?, ?)";
            
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, username);
                stmt.setString(2, MD5.getMD5(password)); // Giữ nguyên MD5 theo yêu cầu
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int userId = rs.getInt("UserID");
                        
                        try (PreparedStatement historyStmt = conn.prepareStatement(loginHistoryQuery)) {
                            historyStmt.setInt(1, userId);
                            historyStmt.setString(2, "Success");
                            historyStmt.setString(3, ipAddress);
                            historyStmt.executeUpdate();
                        }
                        
                        return userId;
                    } else {
                        try (PreparedStatement historyStmt = conn.prepareStatement(loginHistoryQuery)) {
                            historyStmt.setInt(1, 0); // 0 cho đăng nhập thất bại
                            historyStmt.setString(2, "Failed");
                            historyStmt.setString(3, ipAddress);
                            historyStmt.executeUpdate();
                        }
                        
                        return -1;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi kiểm tra đăng nhập: " + e.getMessage());
            throw e;
        }
    }
    public boolean saveMessage(int senderId, int receiverId, String messageContent) throws SQLException {
        Connection conn = null;
        
        try {
            conn = dbConnection.getConnection();
            
            String contentToSave = messageContent;
            if (!isVigenereEncrypted(messageContent)) {
                contentToSave = VigenereCipher.encrypt(messageContent, "MYCHATKEY");
            }
            
            String query = "INSERT INTO ChatMessages (SenderID, ReceiverID, MessageContent) VALUES (?, ?, ?)";
            
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, senderId);
                stmt.setInt(2, receiverId);
                stmt.setString(3, contentToSave);
                return stmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi lưu tin nhắn: " + e.getMessage());
            throw e;
        }
    }
    
    public List<String> getChatHistory(int userId1, int userId2) throws SQLException {
        List<String> chatHistory = new ArrayList<>();
        Connection conn = null;
        
        try {
            conn = dbConnection.getConnection();
            
            String query = "SELECT SenderID, MessageContent, Timestamp FROM ChatMessages " +
                           "WHERE (SenderID = ? AND ReceiverID = ?) OR (SenderID = ? AND ReceiverID = ?) " +
                           "ORDER BY Timestamp ASC";
            
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, userId1);
                stmt.setInt(2, userId2);
                stmt.setInt(3, userId2);
                stmt.setInt(4, userId1);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String encryptedContent = rs.getString("MessageContent");
                        String decryptedContent;
                        
                        try {
                            decryptedContent = VigenereCipher.decrypt(encryptedContent, "MYCHATKEY");
                            System.out.println("Đã giải mã tin nhắn: " + encryptedContent + " -> " + decryptedContent);
                        } catch (Exception e) {
                            decryptedContent = encryptedContent;
                            System.err.println("Không thể giải mã tin nhắn từ lịch sử: " + e.getMessage());
                        }
                        
                        String message = String.format("User%d [%s]: %s",
                            rs.getInt("SenderID"),
                            rs.getTimestamp("Timestamp"),
                            decryptedContent);
                            
                        chatHistory.add(message);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy lịch sử chat: " + e.getMessage());
            throw e;
        }
        
        return chatHistory;
    }
    
    public List<String> getOnlineUsers() throws SQLException {
        List<String> onlineUsers = new ArrayList<>();
        Connection conn = null;
        
        try {
            conn = dbConnection.getConnection();
            
            String query = "SELECT u.Username FROM Users u JOIN UserStatus s ON u.UserID = s.UserID WHERE s.IsOnline = 1";
            
            try (PreparedStatement stmt = conn.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    onlineUsers.add(rs.getString("Username"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy danh sách người dùng Online: " + e.getMessage());
            throw e;
        }
        
        return onlineUsers;
    }
    public boolean saveFileTransfer(int senderId, int receiverId, String fileName, String fileType, int fileSize, byte[] fileData) throws SQLException {
        Connection conn = null;
        
        try {
            conn = dbConnection.getConnection();
            
            String query = "INSERT INTO FileTransfers (SenderID, ReceiverID, FileName, FileType, FileSize, FileData) VALUES (?, ?, ?, ?, ?, ?)";
            
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, senderId);
                stmt.setInt(2, receiverId);
                stmt.setString(3, fileName);
                stmt.setString(4, fileType);
                stmt.setInt(5, fileSize);
                stmt.setBytes(6, fileData);
                return stmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi lưu thông tin chuyển file: " + e.getMessage());
            throw e;
        }
    }
    public boolean updateFileData(int senderId, String fileName, byte[] fileData) throws SQLException {
        Connection conn = dbConnection.getConnection();
    
        String query = "UPDATE FileTransfers SET FileData = ? WHERE SenderID = ? AND FileName = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setBytes(1, fileData);
            stmt.setInt(2, senderId);
            stmt.setString(3, fileName);
            return stmt.executeUpdate() > 0;
        }
    }
    public byte[] getFileData(int senderId, String fileName) throws SQLException {
        Connection conn = null;
        
        try {
            conn = dbConnection.getConnection();
            
            String query = "SELECT FileData FROM FileTransfers WHERE SenderID = ? AND FileName = ?";
            
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, senderId);
                stmt.setString(2, fileName);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getBytes("FileData");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving file data: " + e.getMessage());
            throw e;
        }
        
        return null;
    }
    
    public List<String> getNotifications(int userId) throws SQLException {
        List<String> notifications = new ArrayList<>();
        Connection conn = null;
        
        try {
            conn = dbConnection.getConnection();
            
            String query = "SELECT Content, CreatedAt FROM Notifications WHERE UserID = ? AND IsRead = 0 ORDER BY CreatedAt DESC";
            
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, userId);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String notification = String.format("%s [%s]",
                            rs.getString("Content"),
                            rs.getTimestamp("CreatedAt"));
                        notifications.add(notification);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy thông báo: " + e.getMessage());
            throw e;
        }
        
        return notifications;
    }
    
    public boolean markNotificationAsRead(int notificationId) throws SQLException {
        Connection conn = null;
        
        try {
            conn = dbConnection.getConnection();
            
            String query = "UPDATE Notifications SET IsRead = 1 WHERE NotificationID = ?";
            
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, notificationId);
                return stmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi đánh dấu thông báo là đã đọc: " + e.getMessage());
            throw e;
        }
    }
    
    public boolean logAdminAction(int adminId, String action) throws SQLException {
        Connection conn = null;
        
        try {
            conn = dbConnection.getConnection();
            
            String query = "INSERT INTO AdminActions (AdminID, Action) VALUES (?, ?)";
            
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, adminId);
                stmt.setString(2, action);
                return stmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi ghi lại hành động của Admin: " + e.getMessage());
            throw e;
        }
    }
    public boolean setAllUsersOffline() throws SQLException {
        Connection conn = null;
        
        try {
            conn = dbConnection.getConnection();
            
            String query = "UPDATE UserStatus SET IsOnline = 0, LastActive = GETDATE()";
            
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                return stmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi cập nhật trạng thái offline cho tất cả người dùng: " + e.getMessage());
            throw e;
        }
    }
    public boolean setUserOnline(int userId) throws SQLException {
        Connection conn = null;
        
        try {
            conn = dbConnection.getConnection();
            
            String query = "UPDATE UserStatus SET IsOnline = 1, LastActive = GETDATE() WHERE UserID = ?";
            
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, userId);
                return stmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi cập nhật trạng thái online cho người dùng: " + e.getMessage());
            throw e;
        }
    }
    private boolean isVigenereEncrypted(String text) {
        try {
            String decrypted = VigenereCipher.decrypt(text, "MYCHATKEY");
            return !decrypted.equals(text);
        } catch (Exception e) {
            return false;
        }
    }
}