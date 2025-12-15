# AegisTalk - Kiến trúc và Công nghệ Mạng

## Tổng quan

AegisTalk là ứng dụng desktop chat & video call được xây dựng bằng JavaFX (client) và Java (server), áp dụng các kiến thức từ môn Lập trình mạng (bài 0-10).

## Kiến trúc SOA (Service-Oriented Architecture)

Ứng dụng được tổ chức theo kiến trúc hướng dịch vụ với các service riêng biệt:

### Các RMI Service (Port 1099)

1. **AuthService** - Xác thực người dùng
   - `register()` - Đăng ký tài khoản mới
   - `login()` - Đăng nhập
   - `logout()` - Đăng xuất
   - `isValidSession()` - Kiểm tra session

2. **FriendService** - Quản lý bạn bè
   - `searchUsers()` - Tìm kiếm user
   - `sendFriendRequest()` - Gửi yêu cầu kết bạn
   - `acceptFriendRequest()` - Chấp nhận yêu cầu
   - `getFriends()` - Lấy danh sách bạn bè

3. **ChatService** - Quản lý chat
   - `getMessageHistory()` - Lấy lịch sử tin nhắn
   - `getUserRooms()` - Lấy danh sách phòng
   - `getOrCreateDirectRoom()` - Tạo phòng 1-1
   - `saveMessage()` - Lưu tin nhắn

4. **GroupService** - Quản lý nhóm
   - `createGroup()` - Tạo nhóm mới
   - `addMember()` - Thêm thành viên
   - `removeMember()` - Xóa thành viên
   - `getGroupMembers()` - Lấy danh sách thành viên

**Áp dụng: RMI (Remote Method Invocation) - Bài RMI**
- Client gọi method từ xa như gọi local method
- Trả về đối tượng Java qua network

**Áp dụng: SOA (Service-Oriented Architecture) - Bài SOA**
- Mỗi service có contract rõ ràng (interface)
- Các service lỏng lẻo (loose coupling), dễ mở rộng

## Các Server và Giao thức

### 1. TCP Server (Port 5555)

**File:** `AegisTalkTCPServer.java`

**Áp dụng: TCP Socket - Bài TCP**
- Sử dụng `ServerSocket` để lắng nghe kết nối
- Mỗi client là một thread riêng (multi-threaded server)
- Đảm bảo độ tin cậy và thứ tự dữ liệu (reliable, ordered)

**Chức năng:**
- Đăng nhập và quản lý session
- Gửi/nhận tin nhắn 1-1 và nhóm
- Signaling cho video call (CALL_OFFER, CALL_ANSWER, CALL_ICE, CALL_END)
- Typing indicator

**Protocol:**
- Mỗi dòng là một JSON message
- Message types: LOGIN, CHAT, JOIN_ROOM, LEAVE_ROOM, CALL_OFFER, CALL_ANSWER, CALL_ICE, CALL_END, TYPING

### 2. UDP Server (Port 8888)

**File:** `VideoStreamServer.java`

**Áp dụng: UDP Socket - Bài UDP**
- Sử dụng `DatagramSocket` để gửi/nhận gói tin UDP
- Tốc độ cao, độ trễ thấp (low latency)
- Chấp nhận mất gói (packet loss) phù hợp cho real-time streaming

**Chức năng:**
- Truyền frame audio/video trong cuộc gọi
- Forward packet đến các client khác trong session

**Packet Format:**
- Header (12 bytes): [sessionId(4)][sequence(4)][timestamp(4)]
- Payload: audio/video data

### 3. Multicast Service (Port 4446, Group 239.1.1.1)

**File:** `PresenceMulticastService.java`

**Áp dụng: Multicast - Bài Multicast**
- Sử dụng `MulticastSocket` để join/leave multicast group
- Dùng địa chỉ lớp D (239.x.x.x) cho multicast
- Gửi thông báo đến nhiều client đồng thời (one-to-many)

**Chức năng:**
- Presence discovery trong LAN
- Thông báo sự kiện nhóm (user join/leave)
- Typing indicator

**Protocol:**
- Format: `TYPE|room|userId|displayName`
- Types: JOIN, LEAVE, TYPING

### 4. HTTP File Server (Port 8081)

**File:** `FileHttpServerMain.java`

**Áp dụng: HTTP - Bài HTTP**
- Sử dụng `HttpServer` (JDK built-in) để tạo HTTP server
- Giao thức HTTP over TCP

**Chức năng:**
- Upload file (POST /files)
- Download file (GET /files/{id})
- Lưu file với SHA-256 hash để tránh trùng lặp
- Hỗ trợ ETag và If-None-Match cho cache

### 5. Gemini AI Moderation (RMI Port 5100)

**File:** `GeminiModerationService.java`, `ModerationServerMain.java`

**Áp dụng: HTTP - Bài HTTP**
- Sử dụng `HttpClient` để gọi Gemini API
- Đọc API key từ biến môi trường `GERMINI_API`

**Chức năng:**
- Kiểm duyệt nội dung tin nhắn bằng AI
- Quyết định: ALLOW, WARN, BLOCK
- Phân loại: SEXUAL, VIOLENCE, HARASSMENT, SELF_HARM, etc.

## Cấu trúc Database

Cần tạo các bảng sau trong MySQL:

```sql
CREATE DATABASE aegistalk;

USE aegistalk;

-- Bảng users
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(64) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Bảng friend_requests
CREATE TABLE friend_requests (
    from_user_id BIGINT NOT NULL,
    to_user_id BIGINT NOT NULL,
    status ENUM('PENDING', 'ACCEPTED', 'REJECTED') DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (from_user_id, to_user_id),
    FOREIGN KEY (from_user_id) REFERENCES users(id),
    FOREIGN KEY (to_user_id) REFERENCES users(id)
);

-- Bảng friends
CREATE TABLE friends (
    user_id BIGINT NOT NULL,
    friend_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, friend_id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (friend_id) REFERENCES users(id)
);

-- Bảng rooms
CREATE TABLE rooms (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    type ENUM('PUBLIC', 'PRIVATE', 'DIRECT') NOT NULL,
    created_by_user_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by_user_id) REFERENCES users(id)
);

-- Bảng room_members
CREATE TABLE room_members (
    room_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (room_id, user_id),
    FOREIGN KEY (room_id) REFERENCES rooms(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Bảng messages
CREATE TABLE messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id VARCHAR(100) NOT NULL,
    from_user_id VARCHAR(50) NOT NULL,
    type ENUM('TEXT', 'IMAGE', 'FILE', 'SYSTEM', 'CALL_EVENT') NOT NULL,
    text TEXT,
    payload_ref VARCHAR(255),
    timestamp TIMESTAMP NOT NULL,
    INDEX idx_room_timestamp (room_id, timestamp)
);
```

## Cách chạy

### 1. Cấu hình Database

Sửa thông tin kết nối trong `DBTest.java`:
```java
private static final String URL = "jdbc:mysql://localhost:3306/aegistalk?...";
private static final String USER = "root";
private static final String PASSWORD = "your_password";
```

### 2. Cấu hình Gemini API

Đặt biến môi trường:
```bash
# Windows
set GERMINI_API=your_api_key

# Linux/Mac
export GERMINI_API=your_api_key
```

### 3. Chạy Server

```bash
mvn clean compile
mvn exec:java -Dexec.mainClass="org.example.demo2.server.AegisTalkServerMain"
```

Hoặc chạy từng service riêng:
- `RMIServiceServer` - RMI services
- `ModerationServerMain` - Moderation service
- `AegisTalkTCPServer` - TCP chat server
- `VideoStreamServer` - UDP video server
- `FileHttpServerMain` - HTTP file server

### 4. Chạy Client

```bash
mvn javafx:run
```

## Tóm tắt các công nghệ áp dụng

| Công nghệ | Bài học | Ứng dụng trong AegisTalk |
|-----------|---------|-------------------------|
| **TCP Socket** | Bài TCP | Chat server, signaling video call |
| **UDP Socket** | Bài UDP | Video/audio streaming |
| **Multicast** | Bài Multicast | Presence discovery, group notifications |
| **HTTP** | Bài HTTP | File upload/download, Gemini API |
| **RMI** | Bài RMI | AuthService, FriendService, ChatService, GroupService |
| **SOA** | Bài SOA | Kiến trúc hướng dịch vụ với các service riêng biệt |

## Lưu ý

1. **Multicast chỉ hoạt động trong LAN** - TTL được set = 1
2. **UDP không đảm bảo độ tin cậy** - Có thể mất gói, phù hợp cho streaming
3. **RMI cần cả client và server cùng chạy** - Đảm bảo RMI registry đã khởi động
4. **Gemini API cần API key** - Đặt biến môi trường `GERMINI_API`





