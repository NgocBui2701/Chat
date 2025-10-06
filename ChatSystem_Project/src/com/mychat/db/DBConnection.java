package com.mychat.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {
    private static final String URL = "jdbc:sqlserver://localhost:1433;databaseName=ChatSystem;encrypt=true;trustServerCertificate=true;characterEncoding=UTF-8;useUnicode=true;sendStringParametersAsUnicode=true";
    private static final String USER = "my_user";
    private static final String PASSWORD = "ngocbui";
    
    private Connection connection;
	@SuppressWarnings("unused")
    private boolean isConnected;

    public DBConnection() {
        this.connection = null;
        this.isConnected = false;
    }
    
    public synchronized Connection connect() {
        try {
            if (connection == null || connection.isClosed()) {
                Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
                connection = DriverManager.getConnection(URL, USER, PASSWORD);
                connection.setAutoCommit(true);
                isConnected = true;
                System.out.println("Ket noi SQL Server thanh cong!");
            }
            
            return connection;
        } catch (ClassNotFoundException e) {
            System.err.println("Không tìm thấy driver SQL Server: " + e.getMessage());
            isConnected = false;
            return null;
        } catch (SQLException e) {
            System.err.println("Lỗi kết nối SQL Server: " + e.getMessage());
            isConnected = false;
            return null;
        }
    }
    public synchronized void disconnect() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                    System.out.println("Ngat ket noi SQL Server thanh cong!");
                }
            } catch (SQLException e) {
                System.err.println("Lỗi ngắt kết nối: " + e.getMessage());
            } finally {
                connection = null;
                isConnected = false;
            }
        }
    }
    
    public synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = connect();
            
            if (connection == null) {
                throw new SQLException("Không thể tạo kết nối đến cơ sở dữ liệu");
            }
        }
        
        try {
            if (!connection.isValid(3)) {
                System.out.println("Kết nối hiện tại không hợp lệ, đang tạo lại...");
                disconnect();
                connection = connect();
                
                if (connection == null) {
                    throw new SQLException("Không thể tạo lại kết nối đến cơ sở dữ liệu");
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi kiểm tra tính hợp lệ: " + e.getMessage());
            disconnect();
            connection = connect();
            
            if (connection == null) {
                throw new SQLException("Không thể tạo lại kết nối đến cơ sở dữ liệu");
            }
        }
        
        return connection;
    }
    
    public synchronized boolean isConnected() {
        try {
            return connection != null && !connection.isClosed() && connection.isValid(3);
        } catch (SQLException e) {
            return false;
        }
    }
}