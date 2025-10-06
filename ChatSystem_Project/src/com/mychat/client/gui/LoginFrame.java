package com.mychat.client.gui;

import com.mychat.client.ChatClient;
import com.mychat.client.ClientConfig;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

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

public class LoginFrame extends JFrame {
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JCheckBox rememberMeCheckBox;
    private JCheckBox showPasswordCheckBox;
    private JLabel loginMessageLabel;
    private JLabel registerMessageLabel;
    @SuppressWarnings("unused")
    private ChatClient client;
    private ClientConfig config;

    // Constructor
    public LoginFrame(ChatClient client) {
        super();
        this.client = client;
        this.config = new ClientConfig();
        setTitle("Đăng nhập / Đăng ký");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(450, 450);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // Background with gradient
        GradientPanel mainPanel = new GradientPanel(new Color(220, 240, 255), new Color(180, 210, 240));
        mainPanel.setLayout(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        // Login Panel
        JPanel loginPanel = new JPanel(new GridBagLayout());
        loginPanel.setOpaque(false);
        loginPanel.setBorder(new RoundedBorder(10, new Color(150, 150, 150)));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        JLabel usernameLabel = new JLabel("Tên đăng nhập:");
        usernameLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        loginPanel.add(usernameLabel, gbc);

        gbc.gridx = 1;
        usernameField = new JTextField(15);
        usernameField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        usernameField.setBorder(new RoundedBorder(10, new Color(150, 150, 150)));
        loginPanel.add(usernameField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        JLabel passwordLabel = new JLabel("Mật khẩu:");
        passwordLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        loginPanel.add(passwordLabel, gbc);

        gbc.gridx = 1;
        passwordField = new JPasswordField(15);
        passwordField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        passwordField.setBorder(new RoundedBorder(10, new Color(150, 150, 150)));
        loginPanel.add(passwordField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        rememberMeCheckBox = new JCheckBox("Ghi nhớ đăng nhập");
        rememberMeCheckBox.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        rememberMeCheckBox.setOpaque(false);
        loginPanel.add(rememberMeCheckBox, gbc);

        gbc.gridx = 1;
        showPasswordCheckBox = new JCheckBox("Hiển thị mật khẩu");
        showPasswordCheckBox.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        showPasswordCheckBox.setOpaque(false);
        loginPanel.add(showPasswordCheckBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        JButton loginButton = new ModernButton("Đăng nhập");
        loginPanel.add(loginButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        loginMessageLabel = new JLabel("");
        loginMessageLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        loginMessageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        loginPanel.add(loginMessageLabel, gbc);

        loadSavedCredentials();

        // Register Panel
        JPanel registerPanel = new JPanel(new GridBagLayout());
        registerPanel.setOpaque(false);
        registerPanel.setBorder(new RoundedBorder(10, new Color(150, 150, 150)));
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10); // Reduced padding
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        JLabel regUsernameLabel = new JLabel("Tên đăng nhập:");
        regUsernameLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        registerPanel.add(regUsernameLabel, gbc);

        gbc.gridx = 1;
        JTextField regUsernameField = new JTextField(15);
        regUsernameField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        regUsernameField.setBorder(new RoundedBorder(10, new Color(150, 150, 150)));
        registerPanel.add(regUsernameField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        JLabel regPasswordLabel = new JLabel("Mật khẩu:");
        regPasswordLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        registerPanel.add(regPasswordLabel, gbc);

        gbc.gridx = 1;
        JPasswordField regPasswordField = new JPasswordField(15);
        regPasswordField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        regPasswordField.setBorder(new RoundedBorder(10, new Color(150, 150, 150)));
        registerPanel.add(regPasswordField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        JLabel fullNameLabel = new JLabel("Họ tên:");
        fullNameLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        registerPanel.add(fullNameLabel, gbc);

        gbc.gridx = 1;
        JTextField fullNameField = new JTextField(15);
        fullNameField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        fullNameField.setBorder(new RoundedBorder(10, new Color(150, 150, 150)));
        registerPanel.add(fullNameField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        JLabel emailLabel = new JLabel("Email:");
        emailLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        registerPanel.add(emailLabel, gbc);

        gbc.gridx = 1;
        JTextField emailField = new JTextField(15);
        emailField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        emailField.setBorder(new RoundedBorder(10, new Color(150, 150, 150)));
        registerPanel.add(emailField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        JLabel phoneLabel = new JLabel("Số điện thoại:");
        phoneLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        registerPanel.add(phoneLabel, gbc);

        gbc.gridx = 1;
        JTextField phoneField = new JTextField(15);
        phoneField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        phoneField.setBorder(new RoundedBorder(10, new Color(150, 150, 150)));
        registerPanel.add(phoneField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        JButton registerButton = new ModernButton("Đăng ký");
        registerPanel.add(registerButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 2;
        registerMessageLabel = new JLabel("");
        registerMessageLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        registerMessageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        registerPanel.add(registerMessageLabel, gbc);

        tabbedPane.addTab("Đăng nhập", loginPanel);
        tabbedPane.addTab("Đăng ký", registerPanel);
        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        add(mainPanel);

        showPasswordCheckBox.addActionListener(e -> {
            if (showPasswordCheckBox.isSelected()) {
                passwordField.setEchoChar((char) 0);
            } else {
                passwordField.setEchoChar('*');
            }
        });

        loginButton.addActionListener(e -> {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword());
            if (username.isEmpty() || password.isEmpty()) {
                showError("Vui lòng điền đầy đủ các trường", true);
                return;
            }
            client.login(username, password);
            if (rememberMeCheckBox.isSelected()) {
                config.saveCredentials(username, password);
            } else {
                config.clearCredentials();
            }
        });

        registerButton.addActionListener(e -> {
            String username = regUsernameField.getText().trim();
            String password = new String(regPasswordField.getPassword());
            String role = "user";
            String fullName = fullNameField.getText().trim();
            String email = emailField.getText().trim();
            String phone = phoneField.getText().trim();
            if (username.isEmpty() || password.isEmpty() || fullName.isEmpty() || email.isEmpty() || phone.isEmpty()) {
                showError("Vui lòng điền đầy đủ các trường", false);
                return;
            }
            client.register(username, password, role, fullName, email, phone);
        });

        setVisible(true);
    }

    // Method to load saved credentials from config
    private void loadSavedCredentials() {
        String[] credentials = config.loadCredentials();
        if (credentials[0] != null && credentials[1] != null) {
            usernameField.setText(credentials[0]);
            passwordField.setText(credentials[1]);
            rememberMeCheckBox.setSelected(true);
        }
    }

    // Method to show error message in the appropriate tab
    public void showError(String message, boolean isLoginTab) {
        SwingUtilities.invokeLater(() -> {
            JLabel targetLabel = isLoginTab ? loginMessageLabel : registerMessageLabel;
            targetLabel.setForeground(new Color(200, 0, 0));
            targetLabel.setText(message);
        });
    }

    // Method to show success message in the appropriate tab
    public void showSuccess(String message, boolean isLoginTab) {
        SwingUtilities.invokeLater(() -> {
            JLabel targetLabel = isLoginTab ? loginMessageLabel : registerMessageLabel;
            targetLabel.setForeground(new Color(0, 150, 0));
            targetLabel.setText(message);
        });
    }
}