package com.mychat.core;

  import com.mychat.db.DatabaseHelper;
  import com.mychat.security.VigenereCipher;

  import java.io.File;
  import java.io.IOException;
  import java.net.DatagramPacket;
  import java.net.DatagramSocket;
  import java.net.InetAddress;
  import java.nio.charset.StandardCharsets;
  import java.sql.SQLException;
  import java.util.Base64;
  import java.util.Map;
  import java.util.concurrent.ConcurrentHashMap;

  public class FileHandler {
    private final DatabaseHelper dbHelper;
    private final DatagramSocket socket;
    private final ClientManager clientManager;
    private static final String VIGENERE_KEY = "MYCHATKEY";
    private final Map<String, FileChunkBuffer> chunkBuffers;
    private static final int MAX_CHUNK_SIZE = 60000;

    public FileHandler(DatabaseHelper dbHelper, DatagramSocket socket, ClientManager clientManager) {
    this.dbHelper = dbHelper;
    this.socket = socket;
    this.clientManager = clientManager;
    this.chunkBuffers = new ConcurrentHashMap<>();
}

    public void handleFile(int senderId, int receiverId, String fileName, String fileType, int fileSize) throws Exception {
        if (dbHelper.saveFileTransfer(senderId, receiverId, fileName, fileType, fileSize, new byte[0])) {
            String key = senderId + ":" + fileName;
            chunkBuffers.put(key, new FileChunkBuffer(fileName, fileSize));
            clientManager.addFileTransfer(senderId, receiverId, fileName);
        } else {
            throw new Exception("Không thể lưu thông tin tệp tin vào cơ sở dữ liệu");
        }
    }
    public void handleChunk(int senderId, int receiverId, String fileName, int chunkIndex, int totalChunks, byte[] chunkData) throws Exception {
        String key = senderId + ":" + fileName;
        FileChunkBuffer buffer = chunkBuffers.get(key);

        Integer actualReceiverId = receiverId != 0 ? receiverId : clientManager.getReceiverId(senderId, fileName);
        if (actualReceiverId == null) {
            throw new Exception("Không tìm thấy receiverId cho file: " + fileName);
        }

        if (buffer == null) {
            System.out.println("Creating buffer on-the-fly for file: " + fileName);
            int estimatedFileSize = chunkData.length * totalChunks;
            buffer = new FileChunkBuffer(fileName, estimatedFileSize);
            chunkBuffers.put(key, buffer);
            try {
                dbHelper.saveFileTransfer(senderId, actualReceiverId, fileName, getFileExtension(fileName), estimatedFileSize, new byte[0]);
                clientManager.addFileTransfer(senderId, actualReceiverId, fileName);
            } catch (Exception e) {
                System.err.println("Error registering file transfer: " + e.getMessage());
            }
        }

        System.out.println("Received chunk " + (chunkIndex + 1) + " of " + totalChunks + 
                          " for file " + fileName + " (chunk size: " + chunkData.length + " bytes)");
        
        buffer.addChunk(chunkIndex, chunkData);

        if (buffer.isComplete(totalChunks)) {
            byte[] fileData = buffer.getFileData();
            System.out.println("File complete: " + fileName + " - Size: " + fileData.length + " bytes");

            if (fileData.length > 2_000_000_000) {
                throw new IOException("File size exceeds 2GB limit: " + fileName);
            }

            String fileExtension = getFileExtension(fileName).toLowerCase();
            boolean isBinaryFormat = isBinaryFileExtension(fileExtension);
            boolean updateSuccess = false;

            if (isBinaryFormat) {
                System.out.println("Saving binary file: " + fileName);
                updateSuccess = dbHelper.updateFileData(senderId, fileName, fileData);
            } else {
                try {
                    String base64Data = Base64.getEncoder().encodeToString(fileData);
                    String encryptedData = VigenereCipher.encrypt(base64Data, VIGENERE_KEY);
                    byte[] encryptedBytes = encryptedData.getBytes(StandardCharsets.UTF_8);
                    System.out.println("Saving encrypted text file: " + fileName);
                    updateSuccess = dbHelper.updateFileData(senderId, fileName, encryptedBytes);
                } catch (Exception e) {
                    System.out.println("Encryption failed. Saving raw file: " + e.getMessage());
                    updateSuccess = dbHelper.updateFileData(senderId, fileName, fileData);
                }
            }

            if (!updateSuccess) {
                throw new SQLException("Failed to save file data to database: " + fileName);
            }
            System.out.println("File data successfully saved to database: " + fileName);

            chunkBuffers.remove(key);
            clientManager.removeFileTransfer(senderId, fileName); // Xóa thông tin chuyển file
        }
    }

      private boolean isBinaryFileExtension(String extension) {
          String[] binaryFormats = {
              "exe", "dll", "bin", "dat", "class", "obj", "so", "dylib", "iso",
              "zip", "rar", "7z", "tar", "gz", "bz2", "xz",
              "jpg", "jpeg", "png", "gif", "bmp", "tif", "tiff", "ico", "webp",
              "mp3", "mp4", "wav", "flac", "ogg", "avi", "mov", "wmv", "mkv",
              "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods"
          };
          extension = extension.toLowerCase();
          for (String format : binaryFormats) {
              if (format.equals(extension)) {
                  return true;
              }
          }
          return false;
      }

      public void sendFile(int senderId, int receiverId, File file, InetAddress address, int port) throws Exception {
          validateFile(file);
          byte[] fileData = java.nio.file.Files.readAllBytes(file.toPath());
          String base64Data = java.util.Base64.getEncoder().encodeToString(fileData);
          String encryptedData = VigenereCipher.encrypt(base64Data, VIGENERE_KEY);
          byte[] encryptedBytes = encryptedData.getBytes(StandardCharsets.UTF_8);

          String fileExtension = getFileExtension(file.getName());
          String response = "FILE:" + senderId + "," + file.getName() + "," + fileExtension + "," + encryptedBytes.length;
          byte[] responseBytes = response.getBytes();
          DatagramPacket packet = new DatagramPacket(responseBytes, responseBytes.length, address, port);
          socket.send(packet);

          int totalChunks = (int) Math.ceil((double) encryptedBytes.length / MAX_CHUNK_SIZE);
          for (int i = 0; i < totalChunks; i++) {
              int start = i * MAX_CHUNK_SIZE;
              int length = Math.min(MAX_CHUNK_SIZE, encryptedBytes.length - start);
              byte[] chunk = new byte[length];
              System.arraycopy(encryptedBytes, start, chunk, 0, length);
              String header = "FILE_CHUNK:" + senderId + "," + receiverId + "," + file.getName() + "," + i + "," + totalChunks + ":";
              byte[] headerBytes = header.getBytes();
              byte[] packetData = new byte[headerBytes.length + chunk.length];
              System.arraycopy(headerBytes, 0, packetData, 0, headerBytes.length);
              System.arraycopy(chunk, 0, packetData, headerBytes.length, chunk.length);
              packet = new DatagramPacket(packetData, packetData.length, address, port);
              socket.send(packet);
              Thread.sleep(10);
          }
      }

      public void sendFileContent(int senderId, int receiverId, byte[] fileData, String fileName, InetAddress address, int port) throws Exception {
          int totalChunks = (int) Math.ceil((double) fileData.length / MAX_CHUNK_SIZE);
          for (int i = 0; i < totalChunks; i++) {
              int start = i * MAX_CHUNK_SIZE;
              int length = Math.min(MAX_CHUNK_SIZE, fileData.length - start);
              byte[] chunk = new byte[length];
              System.arraycopy(fileData, start, chunk, 0, length);
              String header = "FILE_CHUNK:" + senderId + "," + fileName + "," + i + "," + totalChunks + ":";
              byte[] headerBytes = header.getBytes();
              byte[] packetData = new byte[headerBytes.length + length];
              System.arraycopy(headerBytes, 0, packetData, 0, headerBytes.length);
              System.arraycopy(chunk, 0, packetData, headerBytes.length, length);
              DatagramPacket packet = new DatagramPacket(packetData, packetData.length, address, port);
              socket.send(packet);
              Thread.sleep(10);
          }
      }

      private String getFileExtension(String fileName) {
          int lastIndex = fileName.lastIndexOf('.');
          return lastIndex > 0 ? fileName.substring(lastIndex + 1) : "unknown";
      }

      private void validateFile(File file) throws IOException {
          if (file == null) {
              throw new IllegalArgumentException("Tệp tin không được null");
          }
          if (!file.exists()) {
              throw new IOException("Tệp tin không tồn tại: " + file.getAbsolutePath());
          }
          if (!file.canRead()) {
              throw new IOException("Không thể đọc tệp tin: " + file.getAbsolutePath());
          }
          if (file.length() <= 0) {
              throw new IOException("Tệp tin trống: " + file.getAbsolutePath());
          }
      }

      private static class FileChunkBuffer {
          private final String fileName;
          private byte[][] chunks;
          private final int totalSize;
          private int receivedChunks;

          public FileChunkBuffer(String fileName, int totalSize) {
              this.fileName = fileName;
              this.totalSize = totalSize;
              int estimatedChunks = totalSize / MAX_CHUNK_SIZE + 1;
              this.chunks = new byte[estimatedChunks][];
              this.receivedChunks = 0;
          }

          public synchronized void addChunk(int index, byte[] chunkData) {
              if (index >= chunks.length) {
                  byte[][] newChunks = new byte[index + 1][];
                  System.arraycopy(chunks, 0, newChunks, 0, chunks.length);
                  chunks = newChunks;
              }
              chunks[index] = chunkData.clone();
              receivedChunks++;
          }

          public synchronized boolean isComplete(int totalChunks) {
              if (receivedChunks < totalChunks) {
                  return false;
              }
              for (int i = 0; i < totalChunks; i++) {
                  if (chunks[i] == null) {
                      return false;
                  }
              }
              return true;
          }

          public synchronized byte[] getFileData() {
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