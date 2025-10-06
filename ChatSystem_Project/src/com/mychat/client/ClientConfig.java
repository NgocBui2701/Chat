package com.mychat.client;

import java.io.*;

public class ClientConfig {
    private static final String CONFIG_FILE = "client_config.txt";

    // Save credentials to a file
    public void saveCredentials(String username, String password) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(CONFIG_FILE))) {
            writer.write(username + "\n" + password);
        } catch (Exception e) {
            System.err.println("Lỗi khi lưu thông tin đăng nhập: " + e.getMessage());
        }
    }
    // Load credentials from a file
    public String[] loadCredentials() {
        String[] credentials = new String[2];
        try (BufferedReader reader = new BufferedReader(new FileReader(CONFIG_FILE))) {
            credentials[0] = reader.readLine();
            credentials[1] = reader.readLine();
        } catch (Exception e) {
            System.err.println("Lỗi khi tải thông tin đăng nhập: " + e.getMessage());
        }
        return credentials;
    }
    // Clear saved credentials
    public void clearCredentials() {
        new File(CONFIG_FILE).delete();
    }
}
