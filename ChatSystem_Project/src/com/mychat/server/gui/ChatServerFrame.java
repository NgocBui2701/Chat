package com.mychat.server.gui;

import com.mychat.server.ServerController;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

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

public class ChatServerFrame extends JFrame {
    private JFrame frame;
    private JButton startButton;
    private JButton stopButton;
    private JTable userTable;
    private DefaultListModel<String> messageModel;
    private JList<String> messageList;
    private JTextField messageInput;
    private JButton sendMessageButton;
    private JButton sendFileButton;
    private ServerController controller;

    // Constructor
    public ChatServerFrame(ServerController controller) {
        this.controller = controller;
        initializeGUI();
    }

    // Method to initialize the GUI components
    private void initializeGUI() {
        frame = new JFrame("Chat Server - Admin");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(900, 650);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout(10, 10));

        // Background with gradient
        GradientPanel mainPanel = new GradientPanel(new Color(220, 240, 255), new Color(180, 210, 240));
        mainPanel.setLayout(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Control Panel
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        controlPanel.setOpaque(false);
        startButton = new ModernButton("Khởi động Server");
        stopButton = new ModernButton("Đóng Server");
        stopButton.setEnabled(false);
        controlPanel.add(startButton);
        controlPanel.add(stopButton);

        // User Table
        JPanel userPanel = new JPanel(new BorderLayout());
        userPanel.setOpaque(false);
        userPanel.setBorder(new RoundedBorder(10, new Color(150, 150, 150)));
        String[] columns = {"UserID", "Username", "Status"};
        Object[][] data = {};
        userTable = new JTable(data, columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Make all cells non-editable
            }
        };
        userTable.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        userTable.setRowHeight(30);
        userTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userTable.setBackground(new Color(255, 255, 255));
        userTable.setForeground(new Color(50, 50, 50));
        JScrollPane tableScroll = new JScrollPane(userTable);
        tableScroll.setBorder(null);
        tableScroll.getViewport().setBackground(new Color(245, 245, 245));
        customizeScrollBar(tableScroll);
        userPanel.add(tableScroll, BorderLayout.CENTER);

        // Chat Area
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

        // Input Panel
        JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        inputPanel.setOpaque(false);
        JLabel messageLabel = new JLabel("Tin nhắn:");
        messageLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        messageLabel.setForeground(new Color(50, 50, 50));
        messageInput = new JTextField(30);
        messageInput.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        messageInput.setBorder(new RoundedBorder(10, new Color(150, 150, 150)));
        sendMessageButton = new ModernButton("Gửi tin nhắn");
        sendFileButton = new ModernButton("Gửi tệp tin");
        inputPanel.add(messageLabel);
        inputPanel.add(messageInput);
        inputPanel.add(sendMessageButton);
        inputPanel.add(sendFileButton);
        chatPanel.add(inputPanel, BorderLayout.SOUTH);

        // Add components to main panel
        mainPanel.add(controlPanel, BorderLayout.NORTH);
        mainPanel.add(userPanel, BorderLayout.WEST);
        mainPanel.add(chatPanel, BorderLayout.CENTER);

        frame.add(mainPanel);

        // Event Listeners
        startButton.addActionListener(e -> {
            controller.startServer();
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
        });

        stopButton.addActionListener(e -> {
            try {
                controller.stopServer();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Lỗi khi dừng server: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
        });

        sendMessageButton.addActionListener(e -> sendMessage());
        sendFileButton.addActionListener(e -> sendFile());

        frame.setVisible(true);
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

    // Method to send a message to the selected user
    private void sendMessage() {
        String message = messageInput.getText().trim();
        if (!message.isEmpty()) {
            int selectedRow = userTable.getSelectedRow();
            if (selectedRow >= 0) {
                int receiverId = (int) userTable.getValueAt(selectedRow, 0);
                controller.sendMessage(1, receiverId, message);
                messageModel.addElement("Gửi tin nhắn đến User " + receiverId + ": " + message);
                messageInput.setText("");
            } else {
                messageModel.addElement("Vui lòng chọn người dùng để gửi tin nhắn.");
            }
        }
    }

    // Method to send a file to the selected user
    private void sendFile() {
        int selectedRow = userTable.getSelectedRow();
        if (selectedRow >= 0) {
            int receiverId = (int) userTable.getValueAt(selectedRow, 0);
            JFileChooser fileChooser = new JFileChooser();
            if (fileChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                try {
                    controller.sendFile(1, receiverId, fileChooser.getSelectedFile());
                    SwingUtilities.invokeLater(() -> 
                        messageModel.addElement("Gửi tệp tin đến User " + receiverId)
                    );
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> 
                        messageModel.addElement("Lỗi khi gửi tệp tin: " + e.getMessage())
                    );
                }
            }
        } else {
            SwingUtilities.invokeLater(() -> 
                messageModel.addElement("Vui lòng chọn người dùng để gửi tệp tin.")
            );
        }
    }

    // Method to update the user table with the list of users
    public void updateUserTable(List<Object[]> users) {
        String[] columns = {"UserID", "Username", "Status"};
        Object[][] data = users.toArray(new Object[0][]);
        SwingUtilities.invokeLater(() -> {
            userTable.setModel(new javax.swing.table.DefaultTableModel(data, columns));
        });
    }

    // Method to add messages to the message list
    public void addMessage(String message) {
        SwingUtilities.invokeLater(() -> messageModel.addElement(message));
    }

    // Update server status
    public void updateServerStatus(boolean isRunning) {
        startButton.setEnabled(!isRunning);
        stopButton.setEnabled(isRunning);
        
        if (isRunning) {
            addMessage("Server đang chạy...");
        } else {
            addMessage("Server đã dừng.");
        }
        
        if (isRunning) {
            frame.setTitle("Chat Server - Admin (Running)");
        } else {
            frame.setTitle("Chat Server - Admin (Stopped)");
        }
    }
}