CREATE DATABASE ChatSystem;
GO
USE ChatSystem;
GO
CREATE USER my_user FOR LOGIN my_user;
GO
ALTER ROLE db_owner ADD MEMBER my_user;
--Quản lý thông tin người dùng
CREATE TABLE Users (
    UserID INT IDENTITY(1,1) PRIMARY KEY,
    Username NVARCHAR(50) UNIQUE NOT NULL,
    Password NVARCHAR(100) NOT NULL, -- MD5
    Role NVARCHAR(20) NOT NULL CHECK (Role IN ('user', 'admin')),
    CreatedAt DATETIME DEFAULT GETDATE()
);
GO
INSERT INTO Users (Username, Password, Role) 
			VALUES ('Admin', 'Admin', 'admin');
GO
CREATE TABLE UserProfiles (
    ProfileID INT IDENTITY(1,1) PRIMARY KEY,
    UserID INT FOREIGN KEY REFERENCES Users(UserID),
    FullName NVARCHAR(100),
    Email NVARCHAR(100),
    Phone NVARCHAR(20),
    Avatar NVARCHAR(255)
);
GO
CREATE TABLE UserStatus (
    UserID INT PRIMARY KEY FOREIGN KEY REFERENCES Users(UserID),
    IsOnline BIT DEFAULT 0,
    LastActive DATETIME DEFAULT GETDATE()
);
GO
INSERT INTO UserStatus (UserID, IsOnline) 
			VALUES (1, 1);
GO
-- Quản lý tin nhắn
CREATE TABLE ChatMessages (
    MessageID INT IDENTITY(1,1) PRIMARY KEY,
    SenderID INT FOREIGN KEY REFERENCES Users(UserID),
    ReceiverID INT FOREIGN KEY REFERENCES Users(UserID),
    MessageContent NVARCHAR(MAX),
    Timestamp DATETIME DEFAULT GETDATE()
);
GO
CREATE TABLE FileTransfers (
    TransferID INT IDENTITY(1,1) PRIMARY KEY,
    SenderID INT FOREIGN KEY REFERENCES Users(UserID),
    ReceiverID INT FOREIGN KEY REFERENCES Users(UserID),
    FileName NVARCHAR(255),
    FileType NVARCHAR(50),
    FileSize INT,
	FileData VARBINARY(MAX),
    Timestamp DATETIME DEFAULT GETDATE()
);
GO
-- Thông báo
CREATE TABLE Notifications (
    NotificationID INT IDENTITY(1,1) PRIMARY KEY,
    UserID INT FOREIGN KEY REFERENCES Users(UserID),
    Content NVARCHAR(255),
    IsRead BIT DEFAULT 0,
    CreatedAt DATETIME DEFAULT GETDATE()
);
GO
-- Phiên đăng nhập, lịch sử đăng nhập
CREATE TABLE SessionTokens (
    TokenID INT IDENTITY(1,1) PRIMARY KEY,
    UserID INT FOREIGN KEY REFERENCES Users(UserID),
    Token NVARCHAR(255),
    IssuedAt DATETIME DEFAULT GETDATE(),
    ExpiredAt DATETIME
);
GO
CREATE TABLE LoginHistory (
    HistoryID INT IDENTITY(1,1) PRIMARY KEY,
    UserID INT FOREIGN KEY REFERENCES Users(UserID),
    LoginTime DATETIME DEFAULT GETDATE(),
    Status NVARCHAR(20) CHECK (Status IN ('Success', 'Failed')),
    IPAddress NVARCHAR(50)
);
GO
-- Admin
CREATE TABLE AdminActions (
    ActionID INT IDENTITY(1,1) PRIMARY KEY,
    AdminID INT FOREIGN KEY REFERENCES Users(UserID),
    Action NVARCHAR(255),
    ActionTime DATETIME DEFAULT GETDATE()
);
GO
-- Cập nhật trạng thái
CREATE TRIGGER trg_UpdateOnlineStatus
ON LoginHistory
AFTER INSERT
AS
BEGIN
    UPDATE UserStatus
    SET IsOnline = 1, LastActive = GETDATE()
    FROM inserted i
    WHERE i.Status = 'Success' AND UserStatus.UserID = i.UserID;

    INSERT INTO UserStatus (UserID, IsOnline, LastActive)
    SELECT i.UserID, 1, GETDATE()
    FROM inserted i
    WHERE i.Status = 'Success'
    AND NOT EXISTS (SELECT 1 FROM UserStatus WHERE UserID = i.UserID);
END;
GO
-- Gửi thông báo khi có tin nhắn cá nhân
CREATE TRIGGER trg_NewMessageNotification
ON ChatMessages
AFTER INSERT
AS
BEGIN
    INSERT INTO Notifications (UserID, Content)
    SELECT ReceiverID, CONCAT('Bạn có tin nhắn mới từ user ', SenderID)
    FROM inserted;
END;
GO
-- Stored Procedure dọn phiên hết hạn
CREATE PROCEDURE USP_CleanupExpiredTokens
AS
BEGIN
    DELETE FROM SessionTokens
    WHERE ExpiredAt < GETDATE();
END;
GO