package com.mychat.client;

  import com.mychat.client.gui.LoginFrame;
  import com.mychat.client.gui.MainFrame;
  import com.mychat.security.MD5;
  import com.mychat.security.VigenereCipher;

  import javax.swing.*;
  import java.io.IOException;
  import java.net.DatagramPacket;
  import java.net.DatagramSocket;
  import java.net.InetAddress;
  import java.nio.charset.StandardCharsets;
  import java.util.HashMap;
  import java.util.Map;

  public class ChatClient {
      private DatagramSocket socket;
      private InetAddress serverAddress;
      private int serverPort = 12345;
      private int userId;
      private String username;
      private LoginFrame loginFrame;
      private MainFrame mainFrame;
      private static final String VIGENERE_KEY = "MYCHATKEY";
      private Map<String, FileChunkBuffer> chunkBuffers;

      public ChatClient() {
          try {
              socket = new DatagramSocket();
              serverAddress = InetAddress.getByName("localhost");
              chunkBuffers = new HashMap<>();
              loginFrame = new LoginFrame(this);
              new Thread(this::receivePackets).start();
          } catch (Exception e) {
              JOptionPane.showMessageDialog(null, "Lỗi khi khởi động client: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
          }
      }

      public void login(String username, String password) {
          try {
              this.username = username;
              String hashPassword = MD5.getMD5(password);
              String request = "LOGIN:" + username + "," + hashPassword;
              System.out.println("Gửi yêu cầu đăng nhập: " + request);
              sendRequest(request);
          } catch (Exception e) {
              loginFrame.showError("Lỗi khi đăng nhập: " + e.getMessage(), true);
          }
      }

      public void logout() {
          try {
              if (userId > 0) {
                  String request = "LOGOUT:" + userId;
                  sendRequest(request);
                  System.out.println("Đã gửi yêu cầu đăng xuất");
              }
          } catch (Exception e) {
              System.err.println("Lỗi khi đăng xuất: " + e.getMessage());
          } finally {
              try {
                  if (socket != null && !socket.isClosed()) {
                      socket.close();
                  }
              } catch (Exception ex) {
                  System.err.println("Lỗi khi đóng socket: " + ex.getMessage());
              }
          }
      }

      public void register(String username, String password, String role, String fullName, String email, String phone) {
          try {
              String hashedPassword = MD5.getMD5(password);
              String request = "REGISTER:" + username + "," + hashedPassword + "," + role + "," + fullName + "," + email + "," + phone;
              System.out.println("Gửi yêu cầu đăng ký: " + request);
              sendRequest(request);
          } catch (Exception e) {
              loginFrame.showError("Lỗi khi đăng ký: " + e.getMessage(), false);
          }
      }

      public void sendMessage(int receiverId, String message) {
          String encryptedMessage = VigenereCipher.encrypt(message, VIGENERE_KEY);
          String request = "SEND_MESSAGE:" + userId + "," + receiverId + "," + encryptedMessage;
          sendRequest(request);
      }

      public void sendFile(int receiverId, java.io.File file) {
          try {
              if (!file.exists() || !file.canRead()) {
                  throw new IOException("Tệp không tồn tại hoặc không thể đọc: " + file.getAbsolutePath());
              }
              
              byte[] fileData = java.nio.file.Files.readAllBytes(file.toPath());
              String base64Data = java.util.Base64.getEncoder().encodeToString(fileData);
              String encryptedData = VigenereCipher.encrypt(base64Data, VIGENERE_KEY);
              byte[] encryptedBytes = encryptedData.getBytes(StandardCharsets.UTF_8);
              
              String request = "SEND_FILE:" + userId + "," + receiverId + "," + file.getName() + "," + 
                              getFileExtension(file.getName()) + "," + encryptedBytes.length;
              sendRequest(request);
              
              sendFileContent(encryptedBytes, file.getName());
              
              SwingUtilities.invokeLater(() -> 
                  mainFrame.addMessage("Đã gửi tệp " + file.getName() + " đến User" + receiverId)
              );
          } catch (Exception e) {
              SwingUtilities.invokeLater(() -> 
                  mainFrame.addMessage("Lỗi khi gửi tệp: " + e.getMessage())
              );
          }
      }

      private void sendFileContent(byte[] fileData, String fileName) throws Exception {
          int chunkSize = 60000;
          int totalChunks = (int) Math.ceil((double) fileData.length / chunkSize);
          
          System.out.println("Sending file " + fileName + " in " + totalChunks + " chunks");
          
          for (int i = 0; i < totalChunks; i++) {
              int start = i * chunkSize;
              int length = Math.min(chunkSize, fileData.length - start);
              byte[] chunk = new byte[length];
              System.arraycopy(fileData, start, chunk, 0, length);
              
              // Chỉ bao gồm senderId, fileName, chunkIndex, totalChunks
              String header = "FILE_CHUNK:" + userId + "," + fileName + "," + i + "," + totalChunks + ":";
              byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
              byte[] packetData = new byte[headerBytes.length + length];
              System.arraycopy(headerBytes, 0, packetData, 0, headerBytes.length);
              System.arraycopy(chunk, 0, packetData, headerBytes.length, length);
              
              System.out.println("Sending chunk " + (i + 1) + "/" + totalChunks + " for " + fileName + " (" + length + " bytes)");
              socket.send(new DatagramPacket(packetData, packetData.length, serverAddress, serverPort));
              
              if (i % 10 == 0) {
                  Thread.sleep(10);
              }
          }
          
          System.out.println("All chunks for " + fileName + " have been sent");
      }

      private String getFileExtension(String fileName) {
          int lastIndex = fileName.lastIndexOf('.');
          return lastIndex > 0 ? fileName.substring(lastIndex + 1) : "unknown";
      }

      private void sendRequest(String request) {
          try {
              socket.send(new DatagramPacket(request.getBytes(), request.getBytes().length, serverAddress, serverPort));
          } catch (Exception e) {
              JOptionPane.showMessageDialog(null, "Lỗi khi gửi yêu cầu: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
          }
      }

      private void receivePackets() {
          byte[] buffer = new byte[65507];
          DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
          
          System.out.println("Bắt đầu lắng nghe phản hồi từ server...");
          
          while (true) {
              try {
                  packet.setLength(buffer.length);
                  socket.receive(packet);
                  String response = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                  System.out.println("Nhận phản hồi từ server: " + response);
                  processResponse(packet, response);
              } catch (Exception e) {
                  System.err.println("Lỗi khi nhận phản hồi từ server: " + e.getMessage());
                  e.printStackTrace();
              }
          }
      }

      private void processResponse(DatagramPacket packet, String response) throws Exception {
          System.out.println("Nhận phản hồi: " + response);
          
          if (response.startsWith("LOGIN_SUCCESS:")) {
              String userIdStr = response.substring("LOGIN_SUCCESS:".length());
              userId = Integer.parseInt(userIdStr.trim());
              SwingUtilities.invokeLater(() -> {
                  try {
                      loginFrame.showSuccess("Đăng nhập thành công!", true);
                      Thread.sleep(500);
                      mainFrame = new MainFrame(this, userId, username);
                      loginFrame.dispose();
                  } catch (Exception e) {
                      System.err.println("Lỗi khi tạo MainFrame: " + e.getMessage());
                      e.printStackTrace();
                  }
              });
              return;
          } else if (response.equals("LOGIN_FAILED")) {
              loginFrame.showError("Đăng nhập thất bại. Kiểm tra lại tên đăng nhập và mật khẩu.", true);
              return;
          } else if (response.equals("REGISTER_SUCCESS")) {
              loginFrame.showSuccess("Đăng ký thành công. Vui lòng đăng nhập.", false);
              return;
          } else if (response.equals("REGISTER_FAILED")) {
              loginFrame.showError("Đăng ký thất bại. Tên đăng nhập có thể đã tồn tại.", false);
              return;
          }
          
          if (mainFrame == null) {
              System.out.println("Bỏ qua phản hồi vì chưa đăng nhập: " + response);
              return;
          }
          
          String[] parts = response.split(":", 2);
          String command = parts[0];
          
          switch (command) {
              case "MESSAGE":
                  String[] msgData = parts[1].split(",", 2);
                  int senderId = Integer.parseInt(msgData[0]);
                  String message;
                  try {
                      message = VigenereCipher.decrypt(msgData[1], VIGENERE_KEY);
                  } catch (Exception e) {
                      message = msgData[1];
                      System.err.println("Cảnh báo: Không thể giải mã tin nhắn, sử dụng tin nhắn gốc: " + e.getMessage());
                  }
                  mainFrame.addMessage("User" + senderId + ": " + message);
                  break;
              case "FILE":
                  String[] fileMetaData = parts[1].split(",", 3);
                  int fileSenderId = Integer.parseInt(fileMetaData[0]);
                  String fileName = fileMetaData[1];
                  mainFrame.addMessage("Nhận tệp từ User" + fileSenderId + ": " + fileName);
                  break;
              case "FILE_CHUNK":
                  int secondColonIndex = response.indexOf(":", parts[0].length() + 1);
                  if (secondColonIndex == -1) {
                      System.err.println("Không tìm thấy dấu ':' phân cách dữ liệu FILE_CHUNK.");
                      return;
                  }
                  String header = response.substring(parts[0].length() + 1, secondColonIndex);
                  String[] headerParts = header.split(",", 4);
                  if (headerParts.length < 4) {
                      System.err.println("Header FILE_CHUNK không hợp lệ: " + header);
                      return;
                  }
                  int chunkSenderId = Integer.parseInt(headerParts[0]);
                  String chunkFileName = headerParts[1];
                  int chunkIndex = Integer.parseInt(headerParts[2]);
                  int totalChunks = Integer.parseInt(headerParts[3]);
                  int binaryStart = secondColonIndex + 1;
                  byte[] fileChunkData = new byte[packet.getLength() - binaryStart];
                  System.arraycopy(packet.getData(), binaryStart, fileChunkData, 0, fileChunkData.length);
                  String bufferKey = chunkSenderId + "_" + chunkFileName;
                  chunkBuffers.putIfAbsent(bufferKey, new FileChunkBuffer(chunkFileName, totalChunks));
                  FileChunkBuffer buffer = chunkBuffers.get(bufferKey);
                  buffer.addChunk(chunkIndex, fileChunkData);
                  if (buffer.isComplete(totalChunks)) {
                      byte[] encryptedFileData = buffer.getFileData();
                      try {
                          String encryptedStr = new String(encryptedFileData, StandardCharsets.UTF_8);
                          String decryptedBase64 = VigenereCipher.decrypt(encryptedStr, VIGENERE_KEY);
                          byte[] originalFileData = java.util.Base64.getDecoder().decode(decryptedBase64);
                          mainFrame.receiveFile(chunkSenderId, chunkFileName, originalFileData);
                      } catch (Exception e) {
                          System.err.println("Lỗi khi giải mã tệp: " + e.getMessage());
                          mainFrame.receiveFile(chunkSenderId, chunkFileName, encryptedFileData);
                      }
                      chunkBuffers.remove(bufferKey);
                  }
                  break;
              case "NOTIFICATION":
                  try {
                      String notificationContent = parts[1];
                      mainFrame.addNotification(notificationContent);
                  } catch (Exception e) {
                      System.err.println("Lỗi khi xử lý thông báo: " + e.getMessage());
                      mainFrame.addNotification(parts[1]);
                  }
                  break;
              default:
                  System.out.println("Phản hồi không xác định: " + response);
          }
      }

      public static void main(String[] args) {
          SwingUtilities.invokeLater(ChatClient::new);
      }

      private static class FileChunkBuffer {
          private final String fileName;
          private final byte[][] chunks;
          private final int totalChunks;

          public FileChunkBuffer(String fileName, int totalChunks) {
              this.fileName = fileName;
              this.totalChunks = totalChunks;
              this.chunks = new byte[totalChunks][];
          }

          public void addChunk(int index, byte[] chunkData) {
              if (index < chunks.length) {
                  chunks[index] = chunkData;
              }
          }

          public boolean isComplete(int expectedChunks) {
              if (chunks.length != expectedChunks) return false;
              for (byte[] chunk : chunks) {
                  if (chunk == null) return false;
              }
              return true;
          }

          public byte[] getFileData() {
              int totalLength = 0;
              for (byte[] chunk : chunks) {
                  if (chunk != null) {
                      totalLength += chunk.length;
                  }
              }
              byte[] fileData = new byte[totalLength];
              int offset = 0;
              for (byte[] chunk : chunks) {
                  if (chunk != null) {
                      System.arraycopy(chunk, 0, fileData, offset, chunk.length);
                      offset += chunk.length;
                  }
              }
              return fileData;
          }
      }
  }