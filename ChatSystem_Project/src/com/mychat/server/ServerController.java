package com.mychat.server;

  import com.mychat.core.ClientManager;
  import com.mychat.core.MessageHandler;
  import com.mychat.core.FileHandler;
  import com.mychat.core.NotificationManager;
  import com.mychat.db.DBConnection;
  import com.mychat.db.DatabaseHelper;
  import com.mychat.security.VigenereCipher;
  import com.mychat.server.gui.ChatServerFrame;

  import java.io.File;
  import java.net.DatagramPacket;
  import java.net.DatagramSocket;
  import java.net.InetAddress;
  import java.sql.Connection;
  import java.sql.SQLException;
  import java.util.concurrent.ExecutorService;
  import java.util.concurrent.Executors;
  import java.util.concurrent.ScheduledExecutorService;
  import java.util.concurrent.TimeUnit;
  import java.nio.charset.StandardCharsets;

  import javax.swing.SwingUtilities;

  public class ServerController {
      private DBConnection dbConnection;
      private DatabaseHelper dbHelper;
      private ChatServerFrame gui;
      private DatagramSocket socket;
      private ClientManager clientManager;
      private MessageHandler messageHandler;
      private FileHandler fileHandler;
      private NotificationManager notificationManager;
      private ExecutorService packetExecutor;
      private ScheduledExecutorService scheduler;
      private volatile boolean isRunning;
      private int adminId = 1;

      public ServerController() {
          dbConnection = new DBConnection();
          dbHelper = new DatabaseHelper(dbConnection);
          clientManager = new ClientManager(dbConnection);
          gui = new ChatServerFrame(this);
          packetExecutor = Executors.newFixedThreadPool(10);
          scheduler = Executors.newScheduledThreadPool(1);
          isRunning = false;
      }

      public void startServer() {
          if (!isRunning) {
              try {
                  if (dbConnection == null) {
                      dbConnection = new DBConnection();
                      dbHelper = new DatabaseHelper(dbConnection);
                      clientManager = new ClientManager(dbConnection);
                  }
                  Connection conn = dbConnection.connect();
                  if (conn == null) {
                      gui.addMessage("Không thể kết nối đến cơ sở dữ liệu");
                      return;
                  }
                  socket = new DatagramSocket(12345);
                  notificationManager = new NotificationManager(dbHelper, dbConnection, adminId);
                  messageHandler = new MessageHandler(dbHelper, socket, notificationManager);
                  fileHandler = new FileHandler(dbHelper, socket);
                  isRunning = true;
                  packetExecutor = Executors.newFixedThreadPool(10);
                  scheduler = Executors.newScheduledThreadPool(1);
                  new Thread(this::receivePackets).start();
                  scheduler.scheduleAtFixedRate(() -> {
                      try {
                          updateUserTable();
                      } catch (Exception e) {
                          gui.addMessage("Lỗi khi cập nhật bảng người dùng: " + e.getMessage());
                      }
                  }, 0, 5, TimeUnit.SECONDS);
                  try {
                      dbHelper.logAdminAction(adminId, "Khởi động server");
                  } catch (SQLException e) {
                      gui.addMessage("Lỗi khi ghi log: " + e.getMessage());
                  }
                  SwingUtilities.invokeLater(() -> {
                      gui.updateServerStatus(true);
                  });
              } catch (Exception e) {
                  gui.addMessage("Khởi động server thất bại: " + e.getMessage());
                  cleanupResources();
              }
          }
      }

      public void stopServer() throws Exception {
          if (!isRunning) {
              return;
          }
          try {
              isRunning = false;
              if (socket != null && !socket.isClosed()) {
                  socket.close();
              }
              if (scheduler != null) {
                  scheduler.shutdownNow();
                  try {
                      if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                          gui.addMessage("Scheduler không dừng kịp thời gian chờ.");
                      }
                  } catch (InterruptedException e) {
                      gui.addMessage("Scheduler bị gián đoạn: " + e.getMessage());
                  }
              }
              if (packetExecutor != null) {
                  packetExecutor.shutdownNow();
                  try {
                      if (!packetExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                          gui.addMessage("Packet executor không dừng kịp thời gian chờ.");
                      }
                  } catch (InterruptedException e) {
                      gui.addMessage("Packet executor bị gián đoạn: " + e.getMessage());
                  }
              }
              try {
                  dbHelper.logAdminAction(adminId, "Đóng server");
              } catch (Exception e) {
                  gui.addMessage("Lỗi khi ghi log: " + e.getMessage());
              }
              if (dbConnection != null) {
                  dbConnection.disconnect();
              }
              SwingUtilities.invokeLater(() -> {
                  gui.updateServerStatus(false);
              });
          } catch (Exception e) {
              gui.addMessage("Lỗi khi dừng server: " + e.getMessage());
          } finally {
              isRunning = false;
          }
      }

      private void cleanupResources() {
          try {
              isRunning = false;
              if (socket != null && !socket.isClosed()) {
                  socket.close();
              }
              if (scheduler != null && !scheduler.isShutdown()) {
                  scheduler.shutdownNow();
              }
              if (packetExecutor != null && !packetExecutor.isShutdown()) {
                  packetExecutor.shutdownNow();
              }
              if (dbConnection != null) {
                  dbConnection.disconnect();
              }
          } catch (Exception e) {
              gui.addMessage("Lỗi khi dọn dẹp tài nguyên: " + e.getMessage());
          }
      }

      private void receivePackets() {
          byte[] buffer = new byte[65507];
          DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
          while (isRunning) {
              try {
                  packet.setLength(buffer.length);
                  socket.receive(packet);
                  final byte[] data = new byte[packet.getLength()];
                  System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());
                  final InetAddress address = packet.getAddress();
                  final int port = packet.getPort();
                  packetExecutor.execute(() -> {
                      try {
                          processPacket(new DatagramPacket(data, data.length, address, port));
                      } catch (Exception e) {
                          gui.addMessage("Lỗi khi xử lý gói tin: " + e.getMessage());
                      }
                  });
              } catch (Exception e) {
                  if (isRunning) {
                      gui.addMessage("Lỗi khi nhận gói tin: " + e.getMessage());
                  }
              }
          }
      }

      private void processPacket(DatagramPacket packet) {
          String request = new String(packet.getData(), 0, packet.getLength());
          try {
              String[] parts = request.split(":", 2);
              if (parts.length < 2) {
                  gui.addMessage("Gói tin không hợp lệ từ " + packet.getAddress() + ":" + packet.getPort());
                  return;
              }
              String command = parts[0];
              String requestData = parts[1];
              switch (command) {
                  case "REGISTER":
                      handleRegister(requestData, packet);
                      break;
                  case "LOGIN":
                      handleLogin(requestData, packet);
                      break;
                  case "LOGOUT":
                      handleLogout(requestData);
                      break;
                  case "SEND_MESSAGE":
                      handleSendMessage(requestData, packet);
                      break;
                  case "SEND_FILE":
                      handleSendFile(requestData, packet);
                      break;
                  case "FILE_CHUNK":
                      handleFileChunk(packet);
                      break;
                  default:
                      gui.addMessage("Gói tin không hợp lệ từ " + packet.getAddress() + ":" + packet.getPort());
              }
          } catch (Exception e) {
              gui.addMessage("Lỗi khi xử lý gói tin: " + e.getMessage());
          }
      }

      private void handleRegister(String data, DatagramPacket packet) throws Exception {
          String[] registerData = data.split(",", 6);
          if (registerData.length < 6) {
              sendResponse("REGISTER_FAILED:Dữ liệu không đầy đủ", packet.getAddress(), packet.getPort());
              return;
          }
          boolean success = dbHelper.registerUser(
              registerData[0], registerData[1], registerData[2], registerData[3], registerData[4], registerData[5]
          );
          if (success) {
              sendResponse("REGISTER_SUCCESS", packet.getAddress(), packet.getPort());
              gui.addMessage("Người dùng " + registerData[0] + " đã đăng ký.");
          } else {
              sendResponse("REGISTER_FAILED:Đăng ký thất bại", packet.getAddress(), packet.getPort());
          }
      }

      private void handleLogin(String data, DatagramPacket packet) {
          try {
              System.out.println("Đang xử lý đăng nhập: " + data);
              String[] loginData = data.split(",");
              if (loginData.length < 2) {
                  System.out.println("Dữ liệu đăng nhập không đủ");
                  sendResponse("LOGIN_FAILED:Dữ liệu không đầy đủ", packet.getAddress(), packet.getPort());
                  return;
              }
              String username = loginData[0];
              String password = loginData[1];
              System.out.println("Kiểm tra đăng nhập cho: " + username);
              int userId = dbHelper.checkLogin(username, password, packet.getAddress().getHostAddress());
              if (userId > 0) {
                  System.out.println("Đăng nhập thành công cho: " + username + ", UserID: " + userId);
                  clientManager.addClient(userId, packet.getAddress(), packet.getPort());
                  String response = "LOGIN_SUCCESS:" + userId;
                  System.out.println("Gửi phản hồi: " + response + " đến " + packet.getAddress() + ":" + packet.getPort());
                  sendResponse(response, packet.getAddress(), packet.getPort());
                  gui.addMessage("Người dùng " + username + " đã đăng nhập.");
              } else {
                  System.out.println("Đăng nhập thất bại cho: " + username);
                  System.out.println("Gửi phản hồi thất bại đến " + packet.getAddress() + ":" + packet.getPort());
                  sendResponse("LOGIN_FAILED:Đăng nhập thất bại", packet.getAddress(), packet.getPort());
              }
          } catch (Exception e) {
              System.err.println("Lỗi khi xử lý đăng nhập: " + e.getMessage());
              e.printStackTrace();
              try {
                  sendResponse("LOGIN_FAILED:" + e.getMessage(), packet.getAddress(), packet.getPort());
              } catch (Exception ex) {
                  System.err.println("Không thể gửi phản hồi lỗi: " + ex.getMessage());
              }
          }
      }

      private void handleLogout(String data) throws Exception {
          int userId = Integer.parseInt(data);
          clientManager.removeClient(userId);
          gui.addMessage("Người dùng ID " + userId + " đã đăng xuất.");
      }

      private void handleSendMessage(String data, DatagramPacket packet) throws Exception {
          String[] msgData = data.split(",", 3);
          if (msgData.length < 3) {
              return;
          }
          int senderId = Integer.parseInt(msgData[0]);
          int receiverId = Integer.parseInt(msgData[1]);
          String encryptedMessage = msgData[2];
          String decryptedMessage;
          try {
              decryptedMessage = VigenereCipher.decrypt(encryptedMessage, "MYCHATKEY");
          } catch (Exception e) {
              decryptedMessage = encryptedMessage;
              System.err.println("Không thể giải mã tin nhắn: " + e.getMessage());
          }
          messageHandler.handleMessage(senderId, receiverId, encryptedMessage);
          if (receiverId != adminId) {
              InetAddress address = clientManager.getClientAddress(receiverId);
              Integer port = clientManager.getClientPort(receiverId);
              if (address != null && port != null) {
                  sendResponse("MESSAGE:" + senderId + "," + encryptedMessage, address, port);
                  messageHandler.sendNotification(receiverId, "Tin nhắn mới từ User " + senderId, address, port);
              } else {
                  gui.addMessage("User" + senderId + ": " + decryptedMessage);
              }
          } else {
              gui.addMessage("User" + senderId + ": " + decryptedMessage);
          }
      }

      private void handleSendFile(String data, DatagramPacket packet) throws Exception {
          String[] fileData = data.split(",", 5);
          if (fileData.length < 5) {
              return;
          }
          int senderId = Integer.parseInt(fileData[0]);
          int receiverId = Integer.parseInt(fileData[1]);
          String fileName = fileData[2];
          String fileType = fileData[3];
          int fileSize = Integer.parseInt(fileData[4]);
          fileHandler.handleFile(senderId, receiverId, fileName, fileType, fileSize);
          if (receiverId != adminId) {
              InetAddress address = clientManager.getClientAddress(receiverId);
              Integer port = clientManager.getClientPort(receiverId);
              if (address != null && port != null) {
                  sendResponse("FILE:" + fileData[0] + "," + fileData[2] + "," + fileData[3], address, port);
                  messageHandler.sendNotification(receiverId, "Tệp mới từ User" + senderId + ": " + fileData[2], address, port);
              } else {
                  gui.addMessage("User" + fileData[0] + ": " + fileData[2] + " đã gửi tệp tin: " + fileData[3]);
              }
          }
      }

      private void handleFileChunk(DatagramPacket packet) throws Exception {
          byte[] data = packet.getData();
          int length = packet.getLength();
          String header = new String(data, 0, Math.min(length, 512), StandardCharsets.UTF_8);
          
          if (!header.startsWith("FILE_CHUNK:")) {
              gui.addMessage("Invalid FILE_CHUNK format: incorrect command");
              return;
          }
          
          int firstColonIndex = header.indexOf(":");
          int secondColonIndex = header.indexOf(":", firstColonIndex + 1);
          if (secondColonIndex < 0) {
              gui.addMessage("Invalid FILE_CHUNK format: missing data separator");
              return;
          }
          
          String headerData = header.substring(firstColonIndex + 1, secondColonIndex);
          String[] chunkData = headerData.split(",", 4);
          if (chunkData.length < 4) {
              gui.addMessage("Invalid FILE_CHUNK header: insufficient fields");
              return;
          }
          
          int senderId = Integer.parseInt(chunkData[0]);
          String fileName = chunkData[1];
          int chunkIndex = Integer.parseInt(chunkData[2]);
          int totalChunks = Integer.parseInt(chunkData[3]);
          
          int binaryDataStart = secondColonIndex + 1;
          int binaryLength = length - binaryDataStart;
          if (binaryLength <= 0) {
              gui.addMessage("Warning: Empty chunk received for file " + fileName);
              return;
          }
          
          byte[] chunk = new byte[binaryLength];
          System.arraycopy(data, binaryDataStart, chunk, 0, binaryLength);
          
          gui.addMessage(String.format("Received chunk %d/%d for file %s from User%d (size: %d bytes)",
                         chunkIndex + 1, totalChunks, fileName, senderId, binaryLength));
          
          fileHandler.handleChunk(senderId, 0, fileName, chunkIndex, totalChunks, chunk);
          
          if (chunkIndex == totalChunks - 1) {
              InetAddress address = clientManager.getClientAddress(1);
              Integer port = clientManager.getClientPort(1);
              if (address != null && port != null) {
                  byte[] fileData = dbHelper.getFileData(senderId, fileName);
                  if (fileData != null && fileData.length > 0) {
                      fileHandler.sendFileContent(senderId, 0, fileData, fileName, address, port);
                  } else {
                      gui.addMessage("Warning: Couldn't retrieve complete file data for " + fileName);
                  }
              }
          }
      }

      private void sendResponse(String response, InetAddress address, int port) throws Exception {
          System.out.println("Đang gửi phản hồi: " + response + " đến " + address + ":" + port);
          if (socket == null || socket.isClosed()) {
              System.err.println("Socket đã đóng hoặc null, không thể gửi phản hồi");
              throw new IllegalStateException("Socket đã đóng");
          }
          byte[] responseBytes = response.getBytes();
          DatagramPacket responsePacket = new DatagramPacket(responseBytes, responseBytes.length, address, port);
          socket.send(responsePacket);
          System.out.println("Đã gửi phản hồi thành công");
      }

      public void sendMessage(int senderId, int receiverId, String message) {
          InetAddress address = clientManager.getClientAddress(receiverId);
          Integer port = clientManager.getClientPort(receiverId);
          if (address != null && port != null) {
              try {
                  String encryptedMessage = VigenereCipher.encrypt(message, "MYCHATKEY");
                  System.out.println("Tin nhắn đã mã hóa: " + message + " -> " + encryptedMessage);
                  if (dbHelper.saveMessage(senderId, receiverId, encryptedMessage)) {
                      String response = "MESSAGE:" + senderId + "," + encryptedMessage;
                      sendResponse(response, address, port);
                      dbHelper.logAdminAction(adminId, "Gửi tin nhắn đến " + receiverId + ": " + message);
                  } else {
                      gui.addMessage("Lỗi khi lưu tin nhắn vào cơ sở dữ liệu");
                  }
              } catch (Exception e) {
                  gui.addMessage("Lỗi khi gửi tin nhắn: " + e.getMessage());
              }
          } else {
              gui.addMessage("Người dùng không trực tuyến.");
          }
      }

      public void sendFile(int senderId, int receiverId, File file) {
          InetAddress address = clientManager.getClientAddress(receiverId);
          Integer port = clientManager.getClientPort(receiverId);
          if (address != null && port != null) {
              try {
                  fileHandler.sendFile(senderId, receiverId, file, address, port);
                  dbHelper.logAdminAction(adminId, "Gửi tệp tin đến " + receiverId + ": " + file.getName());
              } catch (Exception e) {
                  SwingUtilities.invokeLater(() -> 
                      gui.addMessage("Lỗi khi gửi tệp tin: " + e.getMessage())
                  );
              }
          } else {
              SwingUtilities.invokeLater(() -> 
                  gui.addMessage("Người dùng không trực tuyến.")
              );
          }
      }

      private void updateUserTable() throws Exception {
          gui.updateUserTable(clientManager.getUserStatus());
      }

      public static void main(String[] args) {
          SwingUtilities.invokeLater(ServerController::new);
      }
  }