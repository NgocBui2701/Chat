package com.mychat.core;

import com.mychat.db.DatabaseHelper;
import com.mychat.security.VigenereCipher;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class MessageHandler {
    private DatabaseHelper dbHelper;
    private DatagramSocket socket;
    private NotificationManager notificationManager;
    private static final String VIGENERE_KEY = "MYCHATKEY";

    // Constructor
    public MessageHandler(DatabaseHelper dbHelper, DatagramSocket socket, NotificationManager notificationManager) {
        this.dbHelper = dbHelper;
        this.socket = socket;
        this.notificationManager = notificationManager;
    }
    
    public void handleMessage(int senderId, int receiverId, String encryptedMessage) throws Exception {
        if (dbHelper.saveMessage(senderId, receiverId, encryptedMessage)) {
            try {
                notificationManager.createNotification("Tin nhan moi tu User " + senderId);
            } catch (Exception e) {
                System.err.println("Lỗi khi tạo thông báo: " + e.getMessage());
            }
        } else {
            throw new Exception("Không thể lưu tin nhắn vào cơ sở dữ liệu");
        }
    }
    
    public void sendMessage(int senderId, int receiverId, String message, InetAddress address, int port) throws Exception {
        String encryptedMessage = VigenereCipher.encrypt(message, VIGENERE_KEY);
        if (dbHelper.saveMessage(senderId, receiverId, encryptedMessage)) {
            String response = "MESSAGE:" + senderId + "," + encryptedMessage;
            byte[] responseBytes = response.getBytes();
            DatagramPacket packet = new DatagramPacket(responseBytes, responseBytes.length, address, port);
            socket.send(packet);
            
            try {
                notificationManager.createNotification("Tin nhan moi tu User " + senderId);
            } catch (Exception e) {
                System.err.println("Lỗi khi tạo thông báo: " + e.getMessage());
            }
        } else {
            throw new Exception("Không thể lưu tin nhắn vào cơ sở dữ liệu");
        }
    }
    
    public void sendNotification(int userId, String content, InetAddress address, int port) throws Exception {
        String response = "NOTIFICATION:" + content;
        byte[] responseBytes = response.getBytes("UTF-8");
        DatagramPacket packet = new DatagramPacket(responseBytes, responseBytes.length, address, port);
        socket.send(packet);
    }
}