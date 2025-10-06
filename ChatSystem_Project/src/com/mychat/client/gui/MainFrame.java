package com.mychat.client.gui;

import com.mychat.client.ChatClient;
import com.mychat.core.NotificationManager;
import com.mychat.db.DBConnection;
import com.mychat.db.DatabaseHelper;
import com.mychat.security.VigenereCipher;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

// Reusing the same UI components from ChatServerFrame
class RoundedBorder extends AbstractBorder {
    private int radius;
    private Color color;

    public RoundedBorder(int radius, Color color) {
        this.radius = radius;
        this.color = color;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(color);
        g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
        g2.dispose();
    }

    @Override
    public Insets getBorderInsets(Component c) {
        return new Insets(radius, radius, radius, radius);
    }

    @Override
    public Insets getBorderInsets(Component c, Insets insets) {
        insets.left = insets.top = insets.right = insets.bottom = radius;
        return insets;
    }
}

class GradientPanel extends JPanel {
    private Color color1;
    private Color color2;

    public GradientPanel(Color color1, Color color2) {
        this.color1 = color1;
        this.color2 = color2;
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setPaint(new GradientPaint(0, 0, color1, 0, getHeight(), color2));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.dispose();
        super.paintComponent(g);
    }
}

class ModernButton extends JButton {
    private Color defaultColor = new Color(70, 130, 180);
    private Color hoverColor = new Color(100, 149, 237);

    public ModernButton(String text) {
        super(text);
        setFont(new Font("Segoe UI", Font.PLAIN, 14));
        setForeground(Color.WHITE);
        setBackground(defaultColor);
        setBorder(new RoundedBorder(15, defaultColor));
        setOpaque(false);
        setContentAreaFilled(false);
        setFocusPainted(false);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                setBackground(hoverColor);
                setBorder(new RoundedBorder(15, hoverColor));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setBackground(defaultColor);
                setBorder(new RoundedBorder(15, defaultColor));
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(getBackground());
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
        super.paintComponent(g2);
        g2.dispose();
    }
}

public class MainFrame extends JFrame {
    private ChatClient client;
    private int userId;
    private String username;
    private DatabaseHelper dbHelper;
    private DBConnection dbConnection;
    private JTable userTable;
    private DefaultListModel<String> messageModel;
    private JList<String> messageList;
    private JTextField messageInput;
    private JButton sendMessageButton;
    private JButton sendFileButton;
    private JButton downloadFileButton;
    private JLabel notificationLabel;
    private NotificationManager notificationManager;
    private DefaultListModel<String> notificationModel;

    // Constructor
    public MainFrame(ChatClient client, int userId, String username) {
        super("Chat Client - " + username);
        this.client = client;
        this.userId = userId;
        this.username = username;
        
        try {
            this.dbConnection = new DBConnection();
            this.dbConnection.connect();
            this.dbHelper = new DatabaseHelper(dbConnection);
            this.notificationManager = new NotificationManager(dbHelper, dbConnection, userId);
            
            initializeUI();
            updateUserTable();
            updateNotifications();
            
            addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                    handleLogout();
                }
            });
            
            setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            setVisible(true);
            System.out.println("MainFrame đã được khởi tạo cho user: " + username + " (ID: " + userId + ")");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, 
                "Lỗi khi khởi tạo giao diện: " + e.getMessage(), 
                "Lỗi", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }
    
    // Method to initialize the UI components
    private void initializeUI() {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // Background with gradient
        GradientPanel mainPanel = new GradientPanel(new Color(220, 240, 255), new Color(180, 210, 240));
        mainPanel.setLayout(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Panel thông báo
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        topPanel.setOpaque(false);
        notificationLabel = new JLabel("Thông báo: 0");
        notificationLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        notificationLabel.setForeground(new Color(50, 50, 50));
        topPanel.add(notificationLabel);
        
        JButton viewNotificationsButton = new ModernButton("Xem thông báo");
        topPanel.add(viewNotificationsButton);
        
        JButton refreshButton = new ModernButton("Làm mới danh sách");
        topPanel.add(refreshButton);
        
        JButton logoutButton = new ModernButton("Đăng xuất");
        topPanel.add(logoutButton);
        
        notificationModel = new DefaultListModel<>();

        // Panel danh sách người dùng
        JPanel userPanel = new JPanel(new BorderLayout());
        userPanel.setOpaque(false);
        userPanel.setBorder(new RoundedBorder(10, new Color(150, 150, 150)));
        userPanel.setPreferredSize(new Dimension(250, 0)); // Fixed width for user panel
        String[] columns = {"UserID", "Username", "Status"};
        Object[][] data = {};
        userTable = new JTable(data, columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; 
            }
        };
        userTable.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        userTable.setRowHeight(30);
        userTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userTable.setBackground(new Color(255, 255, 255));
        userTable.setForeground(new Color(50, 50, 50));
        JScrollPane userScroll = new JScrollPane(userTable);
        userScroll.setBorder(null);
        userScroll.getViewport().setBackground(new Color(245, 245, 245));
        customizeScrollBar(userScroll);
        userPanel.add(userScroll, BorderLayout.CENTER);

        // Panel chat
        JPanel chatPanel = new JPanel(new BorderLayout(5, 5));
        chatPanel.setOpaque(false);
        chatPanel.setBorder(new RoundedBorder(10, new Color(150, 150, 150)));
        messageModel = new DefaultListModel<>();
        messageList = new JList<>(messageModel);
        messageList.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        messageList.setBackground(new Color(255, 255, 255));
        messageList.setForeground(new Color(50, 50, 50));
        JScrollPane messageScroll = new JScrollPane(messageList);
        messageScroll.setBorder(null);
        messageScroll.getViewport().setBackground(new Color(245, 245, 245));
        customizeScrollBar(messageScroll);
        chatPanel.add(messageScroll, BorderLayout.CENTER);

        // Panel nhập tin nhắn
        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
        inputPanel.setOpaque(false);
        
        JPanel textPanel = new JPanel(new BorderLayout(5, 0));
        textPanel.setOpaque(false);
        JLabel messageLabel = new JLabel("Tin nhắn:");
        messageLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        messageLabel.setForeground(new Color(50, 50, 50));
        textPanel.add(messageLabel, BorderLayout.WEST);
        
        messageInput = new JTextField();
        messageInput.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        messageInput.setBorder(new RoundedBorder(10, new Color(150, 150, 150)));
        textPanel.add(messageInput, BorderLayout.CENTER);
        
        inputPanel.add(textPanel, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonPanel.setOpaque(false);
        sendMessageButton = new ModernButton("Gửi tin nhắn");
        sendFileButton = new ModernButton("Gửi tệp tin");
        downloadFileButton = new ModernButton("Tải tệp tin");
        buttonPanel.add(sendMessageButton);
        buttonPanel.add(sendFileButton);
        buttonPanel.add(downloadFileButton);
        
        inputPanel.add(buttonPanel, BorderLayout.EAST);
        chatPanel.add(inputPanel, BorderLayout.SOUTH);

        // Add components to main panel
        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(userPanel, BorderLayout.WEST);
        mainPanel.add(chatPanel, BorderLayout.CENTER);

        add(mainPanel);

        refreshButton.addActionListener(e -> updateUserTable());
        sendMessageButton.addActionListener(e -> sendMessage());
        sendFileButton.addActionListener(e -> sendFile());
        downloadFileButton.addActionListener(e -> downloadFile());
        logoutButton.addActionListener(e -> handleLogout());
        viewNotificationsButton.addActionListener(e -> {
            try {
                showNotifications();
            } catch (Exception ex) {
                addMessage("Lỗi khi hiển thị thông báo: " + ex.getMessage());
            }
        });
        userTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                try {
                    loadChatHistory();
                } catch (Exception ex) {
                    addMessage("Lỗi khi tải lịch sử trò chuyện: " + ex.getMessage());
                }
            }
        });
        addMessage("Chào mừng " + username + " đến với hệ thống chat!");
    }
    
    // Method to customize the scroll bar of a JScrollPane
    private void customizeScrollBar(JScrollPane scrollPane) {
        scrollPane.getVerticalScrollBar().setUI(new BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
                this.thumbColor = new Color(150, 150, 150);
                this.trackColor = new Color(220, 220, 220);
            }

            @Override
            protected JButton createDecreaseButton(int orientation) {
                return createZeroButton();
            }

            @Override
            protected JButton createIncreaseButton(int orientation) {
                return createZeroButton();
            }

            private JButton createZeroButton() {
                JButton button = new JButton();
                button.setPreferredSize(new Dimension(0, 0));
                button.setMinimumSize(new Dimension(0, 0));
                button.setMaximumSize(new Dimension(0, 0));
                return button;
            }
        });
    }
    
    // Update user table
    private void updateUserTable() {
        List<Object[]> users = new ArrayList<>();
        String query = "SELECT u.UserID, u.Username, CASE WHEN s.IsOnline = 1 THEN 'Online' ELSE 'Offline' END AS Status " +
                      "FROM Users u LEFT JOIN UserStatus s ON u.UserID = s.UserID";
        try {
            Connection conn = dbConnection.getConnection();
            if (conn == null) {
                addMessage("Không thể kết nối đến cơ sở dữ liệu");
                return;
            }
            
            try (PreparedStatement stmt = conn.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    users.add(new Object[]{rs.getInt("UserID"), rs.getString("Username"), rs.getString("Status")});
                }
                Object[][] data = users.toArray(new Object[0][]);
                String[] columns = {"UserID", "Username", "Status"};
                SwingUtilities.invokeLater(() -> userTable.setModel(new javax.swing.table.DefaultTableModel(data, columns) {
                    @Override
                    public boolean isCellEditable(int row, int column) {
                        return false; // Make all cells non-editable
                    }
                }));
            }
        } catch (Exception e) {
            addMessage("Lỗi khi cập nhật bảng người dùng: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Send message
    private void sendMessage() {
        String message = messageInput.getText().trim();
        if (!message.isEmpty()) {
            int selectedRow = userTable.getSelectedRow();
            if (selectedRow >= 0) {
                int receiverId = (int) userTable.getValueAt(selectedRow, 0);
                client.sendMessage(receiverId, message);
                messageModel.addElement("Bạn đến User" + receiverId + ": " + message);
                messageInput.setText("");
            } else {
                addMessage("Vui lòng chọn người dùng");
            }
        }
    }
    
    // Send file
    private void sendFile() {
        int selectedRow = userTable.getSelectedRow();
        if (selectedRow >= 0) {
            int receiverId = (int) userTable.getValueAt(selectedRow, 0);
            JFileChooser fileChooser = new JFileChooser();
            if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    client.sendFile(receiverId, fileChooser.getSelectedFile());
                    addMessage("Đang gửi tệp tin: " + fileChooser.getSelectedFile().getName());
                } catch (Exception e) {
                    addMessage("Lỗi khi gửi tệp tin: " + e.getMessage());
                }
            }
        } else {
            addMessage("Vui lòng chọn người dùng");
        }
    }
    
    // Receive file
    public void receiveFile(int senderId, String fileName, byte[] fileData) {
        try {
            // For binary files, avoid string conversion
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setSelectedFile(new File(fileName));
            if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    // Write binary data directly
                    fos.write(fileData);
                    addMessage("Đã nhận và lưu tệp: " + file.getName());
                }
            }
        } catch (Exception e) {
            addMessage("Lỗi khi nhận tệp: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Download file
    private void downloadFile() {
        int selectedRow = userTable.getSelectedRow();
        if (selectedRow >= 0) {
            int senderId = (int) userTable.getValueAt(selectedRow, 0);
            String query = "SELECT TransferID, FileName, FileData, FileType FROM FileTransfers WHERE ReceiverID = ? AND SenderID = ?";
            try {
                Connection conn = dbConnection.getConnection();
                if (conn == null) {
                    addMessage("Không thể kết nối đến cơ sở dữ liệu");
                    return;
                }
                
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setInt(1, userId);
                    stmt.setInt(2, senderId);
                    ResultSet rs = stmt.executeQuery();
                    List<String> fileNames = new ArrayList<>();
                    List<byte[]> fileDataList = new ArrayList<>();
                    
                    while (rs.next()) {
                        fileNames.add(rs.getString("FileName"));
                        fileDataList.add(rs.getBytes("FileData"));
                    }
                    
                    if (fileNames.isEmpty()) {
                        addMessage("Không có tệp nào để tải từ User" + senderId);
                        return;
                    }
                    
                    String selectedFile = (String) JOptionPane.showInputDialog(this, 
                            "Chọn tệp để tải:", 
                            "Tải tệp",
                            JOptionPane.QUESTION_MESSAGE, 
                            null, 
                            fileNames.toArray(), 
                            fileNames.get(0));
                    
                    if (selectedFile != null) {
                        int index = fileNames.indexOf(selectedFile);
                        byte[] fileData = fileDataList.get(index);
                        
                        // Thêm logging để debug
                        System.out.println("Đã tìm thấy file: " + selectedFile);
                        System.out.println("Kích thước dữ liệu: " + fileData.length + " bytes");
                        
                        byte[] originalFileData = fileData;
                        boolean decryptionSuccessful = false;
                        
                        try {
                            // Thử các phương pháp giải mã khác nhau
                            
                            // Phương pháp 1: Giải mã trực tiếp Base64 (nếu dữ liệu được lưu dạng Base64)
                            try {
                                originalFileData = Base64.getDecoder().decode(new String(fileData, StandardCharsets.UTF_8));
                                System.out.println("Giải mã Base64 thành công. Kích thước mới: " + originalFileData.length);
                                decryptionSuccessful = true;
                            } catch (IllegalArgumentException e) {
                                System.out.println("Không phải dữ liệu Base64 thuần túy: " + e.getMessage());
                            }
                            
                            if (!decryptionSuccessful) {
                                try {
                                    String encryptedStr = new String(fileData, StandardCharsets.UTF_8);
                                    String decryptedBase64 = VigenereCipher.decrypt(encryptedStr, "MYCHATKEY");
                                    originalFileData = Base64.getDecoder().decode(decryptedBase64);
                                    System.out.println("Giải mã Vigenere + Base64 thành công. Kích thước mới: " + originalFileData.length);
                                    decryptionSuccessful = true;
                                } catch (Exception e) {
                                    System.out.println("Không thể giải mã Vigenere + Base64: " + e.getMessage());
                                }
                            }
                            
                            if (!decryptionSuccessful) {
                                System.out.println("Sử dụng dữ liệu gốc không giải mã");
                                originalFileData = fileData;
                            }
                            
                        } catch (Exception e) {
                            System.err.println("Lỗi khi giải mã tệp: " + e.getMessage());
                            e.printStackTrace();
                            originalFileData = fileData;
                        }
                        
                        JFileChooser fileChooser = new JFileChooser();
                        fileChooser.setSelectedFile(new File(selectedFile));
                        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                            File file = fileChooser.getSelectedFile();
                            try (FileOutputStream fos = new FileOutputStream(file)) {
                                fos.write(originalFileData);
                                addMessage("Đã tải tệp: " + file.getName());
                            } catch (Exception e) {
                                addMessage("Lỗi khi lưu tệp: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }
                    }
                }
            } catch (Exception e) {
                addMessage("Lỗi khi tải tệp: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            addMessage("Vui lòng chọn người dùng");
        }
    }
    
    // Load chat history
    private void loadChatHistory() throws Exception {
        int selectedRow = userTable.getSelectedRow();
        if (selectedRow >= 0) {
            int otherUserId = (int) userTable.getValueAt(selectedRow, 0);
            List<String> history = dbHelper.getChatHistory(userId, otherUserId);
            messageModel.clear();
            history.forEach(messageModel::addElement);
        }
    }
    
    // Update notifications
    private void updateNotifications() {
        try {
            int unreadCount = notificationManager.getUnreadCount();
            SwingUtilities.invokeLater(() -> notificationLabel.setText("Thông báo: " + unreadCount));
        } catch (Exception e) {
            System.err.println("Lỗi khi cập nhật thông báo: " + e.getMessage());
        }
    }
    
    // Add notification
    public void addNotification(String content) {
        SwingUtilities.invokeLater(() -> {
            notificationModel.addElement(content);
            try {
                updateNotifications();
            } catch (Exception e) {
                addMessage("Lỗi khi cập nhật thông báo: " + e.getMessage());
            }
        });
    }
    
    // Show notifications
    private void showNotifications() {
        try {
            List<String> notifications = notificationManager.getNotifications();
            if (notifications.isEmpty()) {
                JOptionPane optionPane = new JOptionPane(
                    "Không có thông báo mới",
                    JOptionPane.INFORMATION_MESSAGE
                );
                JDialog dialog = optionPane.createDialog(this, "Thông báo");
                configureDialogForUnicode(dialog);
                dialog.setVisible(true);
                return;
            }

            JList<String> notificationList = new JList<>(notifications.toArray(new String[0]));
            notificationList.setFont(new Font("Arial", Font.PLAIN, 14));
            notificationList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            
            JScrollPane scrollPane = new JScrollPane(notificationList);
            scrollPane.setPreferredSize(new Dimension(400, 250));
            
            JOptionPane optionPane = new JOptionPane(
                scrollPane,
                JOptionPane.INFORMATION_MESSAGE,
                JOptionPane.OK_CANCEL_OPTION
            );
            
            JDialog dialog = optionPane.createDialog(this, "Thông báo");
            configureDialogForUnicode(dialog);
            dialog.setVisible(true);
            
            Object selectedValue = optionPane.getValue();
            if (selectedValue != null && (Integer)selectedValue == JOptionPane.OK_OPTION) {
                String selected = notificationList.getSelectedValue();
                if (selected != null) {
                    notificationManager.markAsRead(selected);
                    updateNotifications();
                }
            }
        } catch (Exception e) {
            addMessage("Lỗi khi hiển thị thông báo: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Add message to chat
    public void addMessage(String message) {
        SwingUtilities.invokeLater(() -> messageModel.addElement(message));
    }
    
    // Logout method
    private void handleLogout() {
        try {
            String updateQuery = "UPDATE UserStatus SET IsOnline = 0, LastActive = GETDATE() WHERE UserID = ?";
            Connection conn = dbConnection.getConnection();
            if (conn != null) {
                try (PreparedStatement stmt = conn.prepareStatement(updateQuery)) {
                    stmt.setInt(1, userId);
                    stmt.executeUpdate();
                }
            }
            client.logout();
            if (dbConnection != null) {
                dbConnection.disconnect();
            }
            dispose();
            System.exit(0);
        } catch (Exception e) {
            addMessage("Lỗi khi đăng xuất: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private void configureDialogForUnicode(JDialog dialog) {
        dialog.setFont(new Font("Arial", Font.PLAIN, 14));
        setUnicodeFont(dialog.getComponents());
    }
    
    private void setUnicodeFont(Component[] components) {
        for (Component component : components) {
            if (component instanceof Container) {
                setUnicodeFont(((Container) component).getComponents());
            }
            component.setFont(new Font("Arial", Font.PLAIN, 14));
        }
    }
}
