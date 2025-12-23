# SERVER PORTS, THỨ TỰ CHẠY VÀ DEMO DỰ ÁN

---

## PHẦN 1: DANH SÁCH CÁC PORT SERVER SỬ DỤNG

### **Bảng tổng hợp các Port:**

| **STT** | **Service** | **Port** | **Giao thức** | **Mục đích** |
|---------|-------------|----------|---------------|--------------|
| 1 | **RMI Service Server** | **1099** | RMI | AuthService, FriendService, ChatService, GroupService, CallService |
| 2 | **RMI Moderation Server** | **5100** | RMI | GeminiModerationService (AI kiểm duyệt) |
| 3 | **TCP Chat Server** | **5555** | TCP | Gửi/nhận tin nhắn real-time |
| 4 | **UDP Video Stream Server** | **8888** | UDP | Video/audio streaming trong video call |
| 5 | **HTTP File Server** | **8081** | HTTP | Upload/download files |
| 6 | **UDP Multicast (Typing)** | **4447** | UDP Multicast | Typing indicator (client-side) |

### **Chi tiết từng Port:**

#### **1. Port 1099 - RMI Service Server**
- **Giao thức:** RMI (Remote Method Invocation)
- **Services:**
  - `AuthService` - Xác thực người dùng (login, register, logout)
  - `FriendService` - Quản lý bạn bè (search, send request, accept)
  - `ChatService` - Quản lý chat (get messages, save message)
  - `GroupService` - Quản lý nhóm (create group, add member)
  - `CallService` - Quản lý video call (invite, accept, end)
- **File:** `RMIServiceServer.java`
- **URL:** `rmi://<server_ip>:1099/<ServiceName>`

#### **2. Port 5100 - RMI Moderation Server**
- **Giao thức:** RMI
- **Service:** `ModerationService` - AI kiểm duyệt nội dung qua Gemini API
- **File:** `ModerationServerMain.java`
- **URL:** `rmi://<server_ip>:5100/ModerationService`

#### **3. Port 5555 - TCP Chat Server**
- **Giao thức:** TCP Socket
- **Mục đích:** Gửi/nhận tin nhắn real-time giữa các clients
- **File:** `ChatServer.java`
- **Format:** Mỗi dòng là JSON `ChatMessage`

#### **4. Port 8888 - UDP Video Stream Server**
- **Giao thức:** UDP Socket
- **Mục đích:** Forward video/audio frames giữa các clients trong video call
- **File:** `VideoStreamServer.java`
- **Packet Format:** Header (16 bytes) + Payload (video/audio data)

#### **5. Port 8081 - HTTP File Server**
- **Giao thức:** HTTP
- **Mục đích:** Upload/download files
- **File:** `FileHttpServerMain.java`
- **Endpoints:**
  - `POST /files` - Upload file
  - `GET /files/{id}` - Download file

#### **6. Port 4447 - UDP Multicast (Typing Indicator)**
- **Giao thức:** UDP Multicast
- **Mục đích:** Gửi/nhận typing indicator (client-side, không qua server)
- **Group:** `239.1.1.1`
- **File:** `MainChatController.java` (client-side)

---

## PHẦN 2: THỨ TỰ CHẠY CÁC SERVICES

### **Thứ tự khởi động trong `AegisTalkServerMain.java`:**

```
1. RMI Service Server (Port 1099)
   ↓ (Đợi 1 giây để RMI registry khởi động)
2. RMI Moderation Server (Port 5100)
   ↓
3. TCP Chat Server (Port 5555)
   ↓
4. UDP Video Stream Server (Port 8888)
   ↓ (Đợi 2 giây để CallService được đăng ký, sau đó link với VideoStreamServer)
5. HTTP File Server (Port 8081)
```

### **Lý do thứ tự:**

1. **RMI Service Server phải chạy đầu tiên** vì:
   - Tạo RMI Registry (cần thiết cho các RMI services khác)
   - Các services khác có thể cần gọi RMI services

2. **Đợi 1 giây** sau khi start RMI Service Server để:
   - RMI Registry có thời gian khởi động hoàn toàn
   - Đảm bảo các services được đăng ký thành công

3. **RMI Moderation Server** chạy độc lập, không phụ thuộc vào RMI Service Server

4. **TCP Chat Server** chạy độc lập, không phụ thuộc

5. **UDP Video Stream Server** cần:
   - Đợi 2 giây để `CallService` được đăng ký trong RMI Registry
   - Sau đó link `VideoStreamServer` với `CallService` để quản lý call sessions

6. **HTTP File Server** chạy độc lập, không phụ thuộc

---

## PHẦN 3: DEMO KHI CHẠY DỰ ÁN

### **BƯỚC 1: KHỞI ĐỘNG SERVER**

#### **3.1. Chạy Server:**

```bash
# Cách 1: Dùng Maven
mvn clean compile
mvn exec:java -Dexec.mainClass="org.example.demo2.server.AegisTalkServerMain"

# Cách 2: Dùng IDE
Run: AegisTalkServerMain.main()
```

#### **3.2. Expected Output khi Server khởi động:**

```
   AegisTalk Server - Starting...

[Main] Starting RMI Service Server...
[ServerConfig] ✓ Loaded config from resources/config.properties
[RMIServiceServer] RMI Registry created on port 1099
[RMIServiceServer] Binding AuthService...
[RMIServiceServer] Binding FriendService...
[RMIServiceServer] Binding ChatService...
[RMIServiceServer] Binding GroupService...
[RMIServiceServer] Binding CallService...
[RMIServiceServer] All services bound successfully
[RMIServiceServer] Server IP: 192.168.1.100
[RMIServiceServer] Clients can connect using: rmi://192.168.1.100:1099/<ServiceName>

[Main] Starting RMI Moderation Server...
[ModerationServerMain] Starting Moderation Server on port 5100...
[ModerationServerMain] ModerationService bound successfully
[ModerationServerMain] Server IP: 192.168.1.100
[ModerationServerMain] Clients can connect using: rmi://192.168.1.100:5100/ModerationService

[Main] Starting TCP Chat Server...
[ChatServer] Listening on port 5555

[Main] Starting UDP Video Stream Server...
[VideoStreamServer] UDP Server listening on port 8888
[VideoStreamServer] Áp dụng: UDP Socket - Bài UDP
[Main] CallService linked with VideoStreamServer

[Main] Starting HTTP File Server...
[FileHttpServerMain] HTTP File Server started on port 8081
[FileHttpServerMain] Upload endpoint: POST http://192.168.1.100:8081/files
[FileHttpServerMain] Download endpoint: GET http://192.168.1.100:8081/files/{id}

   All services started successfully!

Server IP Address: 192.168.1.100

RMI Service Server:    rmi://192.168.1.100:1099
RMI Moderation Server: rmi://192.168.1.100:5100
TCP Chat Server:       192.168.1.100:5555
UDP Video Server:      192.168.1.100:8888
HTTP File Server:      http://192.168.1.100:8081
```

#### **3.3. Giải thích Output:**

- **RMI Service Server:** Tạo RMI Registry và bind 5 services (Auth, Friend, Chat, Group, Call)
- **RMI Moderation Server:** Bind ModerationService để kiểm duyệt nội dung
- **TCP Chat Server:** Lắng nghe kết nối TCP trên port 5555
- **UDP Video Stream Server:** Lắng nghe UDP packets trên port 8888
- **HTTP File Server:** Lắng nghe HTTP requests trên port 8081
- **Server IP:** Hiển thị IP LAN của server (dùng để client kết nối)

---

### **BƯỚC 2: KHỞI ĐỘNG CLIENT**

#### **3.4. Chạy Client:**

```bash
# Cách 1: Dùng Maven
mvn javafx:run

# Cách 2: Dùng IDE
Run: HelloApplication.main()
```

#### **3.5. Expected Output khi Client khởi động:**

```
[ServerConfig] ✓ Loaded config from resources/config.properties
[ServerConfig] Server Host: localhost
[ServerConfig] RMI Port: 1099
[ServerConfig] Chat Port: 5555
[ServerConfig] Video Port: 8888
[ServerConfig] Moderation Port: 5100
[ServerConfig] File Server Port: 8081
```

**Lưu ý:** Client sẽ hiển thị màn hình LoginView (không có log gì thêm cho đến khi user đăng nhập).

---

### **BƯỚC 3: DEMO ĐĂNG NHẬP**

#### **3.6. User đăng nhập:**

**Trên Client:**
- User nhập username và password
- Click "Đăng nhập"

**Expected Output trên Server:**

```
[ChatServer] New client from /192.168.1.100:54321
[ChatServer] Total connected clients: 1
```

**Expected Output trên Client (Console):**

```
[AegisTalkClientService] Connected to RMI services at localhost:1099
[MainChatController] Loading friends...
[MainChatController] Loading pending requests...
[MainChatController] Connecting to chat server: localhost:5555
[ChatClient] Connected to localhost:5555
[MainChatController] Connecting to moderation server: localhost:5100
[ModerationClient] Connected to ModerationService at rmi://localhost:5100/ModerationService
[MainChatController] Typing presence started on 239.1.1.1:4447
```

**Giải thích:**
- Client kết nối RMI để lấy danh sách bạn bè
- Client kết nối TCP Chat Server để nhận tin nhắn
- Client kết nối RMI Moderation Server để kiểm duyệt
- Client start UDP Multicast cho typing indicator

---

### **BƯỚC 4: DEMO GỬI TIN NHẮN**

#### **3.7. User 1 gửi tin nhắn:**

**Trên Client 1:**
- User nhập "Hello, how are you?"
- Nhấn Enter

**Expected Output trên Server:**

```
[ChatServer] Received message from /192.168.1.100:54321: room=direct_1_2, from=user1, text=Hello, how are you?
[ChatServer] Total connected clients: 2
[ChatServer] Broadcasting message: room=direct_1_2, from=user1, to 2 clients
[ChatServer] Sent to client /192.168.1.100:54322
[ChatServer] Broadcast complete: sent to 1 clients
```

**Expected Output trên Client 1 (Console):**

```
[MainChatController] Sending message: Hello, how are you?
[ModerationClient] Moderation result: ALLOW
[MainChatController] Message sent via TCP
[MainChatController] Message saved to database
```

**Expected Output trên Client 2 (Console):**

```
[ChatClient] ===== RECEIVED MESSAGE =====
[ChatClient] Room: direct_1_2
[ChatClient] From: user1
[ChatClient] Text: Hello, how are you?
[ChatClient] Callback is null: false
[ChatClient] Calling onMessage callback...
[ChatClient] Callback executed
[MainChatController] Received message from user1: Hello, how are you?
[MainChatController] Playing notification sound
```

**Giải thích:**
1. Client 1 gửi message qua TCP
2. Server nhận và broadcast đến tất cả clients (trừ sender)
3. Client 2 nhận message và hiển thị
4. Moderation check được thực hiện (ALLOW = cho phép)

---

### **BƯỚC 5: DEMO TYPING INDICATOR**

#### **3.8. User 1 bắt đầu gõ:**

**Trên Client 1:**
- User bắt đầu gõ trong ô nhập tin nhắn

**Expected Output trên Client 1 (Console):**

```
[MainChatController] Sending typing signal: TYPING|direct_1_2|1|User1
```

**Expected Output trên Client 2 (Console):**

```
[MainChatController] Received typing signal from User1
[MainChatController] Showing typing indicator: User1 đang nhập...
```

**Giải thích:**
- Client 1 gửi UDP Multicast packet với prefix "TYPING"
- Client 2 nhận và hiển thị "User1 đang nhập..." màu vàng
- Indicator tự động ẩn sau 3 giây

---

### **BƯỚC 6: DEMO VIDEO CALL**

#### **3.9. User 1 gọi User 2:**

**Trên Client 1:**
- User click nút "📹" (Video Call)

**Expected Output trên Server:**

```
[CallService] Call invited: sessionId=1, callerId=1, calleeId=2
[VideoStreamServer] Endpoint registered: session=1, userId=1, 192.168.1.100:54323
[StreamSession] Added user endpoint: userId=1, registered=192.168.1.100:54323
```

**Expected Output trên Client 1 (Console):**

```
[VideoCallController] Starting call to user2 (ID: 2)
[VideoCallController] Call session created: sessionId=1
[VideoCallController] Opening webcam...
[VideoStreamClient] Found LAN address: 192.168.1.100 on Wi-Fi
[VideoStreamClient] Connected to localhost:8888
[VideoCallController] UDP endpoint: 192.168.1.100:54323 (userId=1)
[VideoCallController] Polling call status...
[VideoCallController] Call status: PENDING
```

#### **3.10. User 2 chấp nhận:**

**Trên Client 2:**
- Hiển thị dialog "user1 đang gọi..."
- Click "Chấp nhận"

**Expected Output trên Server:**

```
[CallService] Call accepted: sessionId=1, userId=2
[VideoStreamServer] Endpoint registered: session=1, userId=2, 192.168.1.100:54324
[StreamSession] Added user endpoint: userId=2, registered=192.168.1.100:54324
[StreamSession] Updated actual endpoint for userId=1: 192.168.1.100:54323
[StreamSession] Updated actual endpoint for userId=2: 192.168.1.100:54324
[VideoStreamServer] AUDIO from userId=1 IP=192.168.1.100 -> forwarding to others
[VideoStreamServer] AUDIO from userId=2 IP=192.168.1.100 -> forwarding to others
```

**Expected Output trên Client 1 (Console):**

```
[VideoCallController] Call status: ACTIVE
[VideoCallController] Starting video streaming...
[VideoCallController] Webcam opened: 640x480
[VideoCallController] Starting to send frames...
[VideoCallController] Starting audio streaming...
[VideoCallController] Remote video frame received
```

**Expected Output trên Client 2 (Console):**

```
[VideoCallController] Receiving call from user1 (ID: 1)
[VideoCallController] Accepting call...
[VideoCallController] Starting video streaming...
[VideoStreamClient] Found LAN address: 192.168.1.100 on Wi-Fi
[VideoStreamClient] Connected to localhost:8888
[VideoCallController] UDP endpoint: 192.168.1.100:54324 (userId=2)
[VideoCallController] Remote video frame received
```

**Giải thích:**
1. Client 1 tạo call session qua RMI
2. Client 1 đăng ký UDP endpoint với server
3. Client 2 accept call và đăng ký UDP endpoint
4. Server forward video/audio frames giữa 2 clients
5. Video call bắt đầu streaming

---

### **BƯỚC 7: DEMO GỬI FILE**

#### **3.11. User 1 gửi file:**

**Trên Client 1:**
- User click nút "📎" (Attach)
- Chọn file (ví dụ: document.pdf)

**Expected Output trên Server:**

```
[FileHttpServerMain] Received file upload request
[FileHttpServerMain] File size: 102400 bytes
[FileHttpServerMain] File hash: a552b49d5fef5d83df65d1b7962382b9f2a560bb58f5e35b0ff4eacc44b4f16a
[FileHttpServerMain] File saved: data/a552b49d5fef5d83df65d1b7962382b9f2a560bb58f5e35b0ff4eacc44b4f16a
[FileHttpServerMain] File ID: a552b49d5fef5d83df65d1b7962382b9f2a560bb58f5e35b0ff4eacc44b4f16a
```

**Expected Output trên Client 1 (Console):**

```
[MainChatController] Uploading file: document.pdf
[FileTransferService] Uploading file to http://localhost:8081/files
[FileTransferService] File uploaded successfully: fileId=a552b49d5fef5d83df65d1b7962382b9f2a560bb58f5e35b0ff4eacc44b4f16a
[MainChatController] Sending file message via TCP
```

**Expected Output trên Client 2 (Console):**

```
[ChatClient] ===== RECEIVED MESSAGE =====
[ChatClient] Room: direct_1_2
[ChatClient] From: user1
[ChatClient] Type: FILE
[ChatClient] PayloadRef: a552b49d5fef5d83df65d1b7962382b9f2a560bb58f5e35b0ff4eacc44b4f16a
[MainChatController] Received file message: document.pdf
```

**Giải thích:**
1. Client upload file qua HTTP POST
2. Server lưu file với SHA-256 hash
3. Server trả về fileId
4. Client gửi ChatMessage với type=FILE và payloadRef=fileId
5. Client 2 nhận message và có thể download file qua HTTP GET

---

### **BƯỚC 8: DEMO AI MODERATION**

#### **3.12. User 1 gửi tin nhắn không phù hợp:**

**Trên Client 1:**
- User nhập tin nhắn có nội dung không phù hợp
- Nhấn Enter

**Expected Output trên Client 1 (Console):**

```
[MainChatController] Sending message: [nội dung không phù hợp]
[ModerationClient] Calling moderation service...
[ModerationClient] Moderation result: BLOCK
[ModerationClient] Category: VIOLENCE
[ModerationClient] Reason: Content contains violent language
[MainChatController] Message blocked by moderation
[MainChatController] Showing error: Tin nhắn bị chặn bởi kiểm duyệt nội dung
```

**Expected Output trên Server (Moderation Server):**

```
[GeminiModerationService] Received moderation request
[GeminiModerationService] Calling Gemini API...
[GeminiModerationService] Gemini API response: BLOCK
[GeminiModerationService] Category: VIOLENCE
```

**Giải thích:**
1. Client gọi RMI ModerationService
2. ModerationService gọi Gemini API qua HTTP
3. Gemini API trả về quyết định (ALLOW/WARN/BLOCK)
4. Nếu BLOCK, tin nhắn không được gửi và hiển thị error

---

## PHẦN 4: CÁC LỖI THƯỜNG GẶP VÀ CÁCH XỬ LÝ

### **4.1. Port đã được sử dụng:**

**Lỗi:**
```
java.net.BindException: Address already in use: bind
```

**Cách xử lý:**
- Tìm process đang dùng port: `netstat -ano | findstr :1099` (Windows) hoặc `lsof -i :1099` (Linux/Mac)
- Kill process hoặc đổi port trong `config.properties`

### **4.2. RMI Connection refused:**

**Lỗi:**
```
java.rmi.ConnectException: Connection refused to host: localhost
```

**Cách xử lý:**
- Đảm bảo server đã khởi động
- Kiểm tra `server.host` trong `config.properties` (phải là IP của server, không phải localhost nếu chạy trên máy khác)

### **4.3. Database connection error:**

**Lỗi:**
```
java.sql.SQLException: Access denied for user 'root'@'localhost'
```

**Cách xử lý:**
- Kiểm tra MySQL đã chạy chưa
- Kiểm tra username/password trong `UserDao.java` hoặc `DBTest.java`
- Đảm bảo database `aegistalk` đã được tạo

### **4.4. Gemini API key missing:**

**Lỗi:**
```
[ModerationClient] Error: GEMINI_API environment variable not set
```

**Cách xử lý:**
- Set biến môi trường: `set GERMINI_API=your_api_key` (Windows) hoặc `export GERMINI_API=your_api_key` (Linux/Mac)

---

## TỔNG KẾT

**Các Port Server sử dụng:**
- **1099** - RMI Service Server
- **5100** - RMI Moderation Server
- **5555** - TCP Chat Server
- **8888** - UDP Video Stream Server
- **8081** - HTTP File Server
- **4447** - UDP Multicast (client-side)

**Thứ tự chạy:**
1. RMI Service Server (đợi 1 giây)
2. RMI Moderation Server
3. TCP Chat Server
4. UDP Video Stream Server (đợi 2 giây để link với CallService)
5. HTTP File Server

**Demo output:** Tài liệu này cung cấp expected output/logs cho từng bước demo, giúp bạn hiểu rõ luồng hoạt động của ứng dụng.

