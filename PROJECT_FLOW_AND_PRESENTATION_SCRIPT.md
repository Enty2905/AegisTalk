# LUỒNG DỰ ÁN VÀ SCRIPT THUYẾT TRÌNH DEMO AEGISTALK

---

## PHẦN 1: LUỒNG TỔNG THỂ CỦA DỰ ÁN

### 1. LUỒNG KHỞI ĐỘNG SERVER

```
AegisTalkServerMain.main()
  ├── 1. RMI Service Server (Port 1099)
  │     └── AuthService, FriendService, ChatService, GroupService, CallService
  │
  ├── 2. RMI Moderation Server (Port 5100)
  │     └── GeminiModerationService (AI kiểm duyệt)
  │
  ├── 3. TCP Chat Server (Port 5555)
  │     └── ChatServer - Broadcast messages real-time
  │
  ├── 4. UDP Video Stream Server (Port 8888)
  │     └── VideoStreamServer - Forward video/audio frames
  │
  └── 5. HTTP File Server (Port 8081)
        └── FileHttpServerMain - Upload/download files
```

**Các giao thức được sử dụng:**
- **RMI (Port 1099, 5100)**: Quản lý authentication, bạn bè, conversations, call signaling
- **TCP (Port 5555)**: Gửi/nhận tin nhắn real-time
- **UDP (Port 8888)**: Video/audio streaming
- **HTTP (Port 8081)**: File upload/download
- **UDP Multicast (Port 4446)**: Typing indicator, presence

---

### 2. LUỒNG KHỞI ĐỘNG CLIENT

```
HelloApplication.main()
  → HelloApplication.start()
    → Load LoginView.fxml
      → LoginController.initialize()
        ├── Khởi tạo AegisTalkClientService (RMI connection)
        └── Setup UI: username, password, register toggle
```

---

### 3. LUỒNG ĐĂNG NHẬP/ĐĂNG KÝ

```
User nhập thông tin
  → LoginController.doLogin() hoặc doRegister()
    → clientService.login() / register() [RMI call]
      → AuthServiceImpl.login() / register()
        → UserDao.findByUsername() / create()
          → Verify password hash (SHA-256)
            → Trả về User object
              → Session.setUser() [Lưu user info: id, displayName, avatarPath]
                → Load MainChatView.fxml
                  → MainChatController.initialize()
```

---

### 4. LUỒNG KHỞI TẠO MAINCHATCONTROLLER

```
MainChatController.initialize()
  ├── 1. Khởi tạo services:
  │     ├── AegisTalkClientService (RMI)
  │     └── FileTransferService (HTTP)
  │
  ├── 2. Load dữ liệu ban đầu:
  │     ├── loadFriends() [RMI] → Lấy danh sách bạn bè
  │     └── loadPendingRequests() [RMI] → Lấy lời mời kết bạn
  │
  ├── 3. Setup UI:
  │     ├── Hiển thị tên/avatar user hiện tại
  │     ├── Setup search, tabs (Friends, Groups, Requests)
  │     ├── Setup contacts list
  │     └── Bind event handlers
  │
  ├── 4. Kết nối real-time:
  │     ├── connectChat() [TCP] → Kết nối ChatServer
  │     ├── connectModeration() [HTTP] → Kết nối ModerationServer
  │     └── startTypingPresence() [UDP Multicast] → Typing indicator
  │
  └── 5. Background tasks:
        ├── startAutoRefresh() → Refresh friend requests mỗi 3 giây
        └── startIncomingCallChecker() → Check incoming calls mỗi 2 giây
```

---

### 5. LUỒNG GỬI/NHẬN TIN NHẮN

#### **5.1. Gửi tin nhắn:**

```
User nhập text → Nhấn Enter hoặc click Send
  → MainChatController.sendMessage()
    ├── 1. Kiểm tra moderation [HTTP]
    │     └── ModerationClient.moderate() → Gemini API
    │         → Nếu BLOCK → Hiển thị error, không gửi
    │         → Nếu WARN → Hiển thị warning, vẫn gửi
    │         → Nếu ALLOW → Tiếp tục
    │
    ├── 2. Optimistic update [UI]
    │     └── Hiển thị message ngay lập tức (màu xanh, bên phải)
    │
    ├── 3. Gửi qua TCP [TCP Socket]
    │     └── chatClient.send(ChatMessage) → ChatServer
    │         → ChatServer.broadcast() → Gửi đến tất cả clients
    │
    └── 4. Lưu vào database [RMI]
          └── clientService.saveMessage() → ChatServiceImpl.saveMessage()
              → MessageDao.create() → Lưu vào MySQL
```

#### **5.2. Nhận tin nhắn:**

```
ChatServer nhận message từ client A
  → ChatServer.broadcast()
    → Gửi đến tất cả clients (trừ sender)
      → Client B: ChatClient.readLoop()
        → onIncomingMessage() callback
          → MainChatController.onIncomingMessage()
            ├── 1. Kiểm tra room ID → Chỉ hiển thị nếu đúng conversation
            ├── 2. Tránh duplicate → Bỏ qua nếu là message của mình (đã hiển thị qua optimistic)
            ├── 3. Hiển thị vào UI (màu xám, bên trái)
            └── 4. Phát âm thanh thông báo
```

---

### 6. LUỒNG TÌM KIẾM VÀ KẾT BẠN

```
User nhập tên trong search box
  → MainChatController.handleSearch()
    → clientService.searchUsers() [RMI]
      → FriendServiceImpl.searchUsers()
        → UserDao.search() → Tìm trong database
          → Trả về List<User>
            → Hiển thị trong lstSearchResults
              → User click "Kết bạn"
                → handleAddFriend()
                  → clientService.sendFriendRequest() [RMI]
                    → FriendServiceImpl.sendFriendRequest()
                      → FriendDao.createRequest() → Lưu vào database
                        → Server gửi notification đến user được mời
```

---

### 7. LUỒNG CHẤP NHẬN LỜI MỜI KẾT BẠN

```
User click tab "Requests"
  → loadPendingRequests() [RMI]
    → Hiển thị danh sách lời mời
      → User click "Chấp nhận"
        → handleAcceptRequest()
          → clientService.acceptFriendRequest() [RMI]
            → FriendServiceImpl.acceptFriendRequest()
              → FriendDao.acceptRequest() → Cập nhật status = ACCEPTED
                → FriendDao.addFriendship() → Tạo quan hệ bạn bè
                  → loadFriends() → Refresh danh sách bạn bè
```

---

### 8. LUỒNG VIDEO CALL

#### **8.1. Caller (Người gọi):**

```
User click btnVideoCall
  → MainChatController.handleVideoCall()
    → openVideoCallWindow(otherUserId, otherUserName, true, null)
      → Load CallView.fxml
        → VideoCallController được khởi tạo
          → controller.startCall(calleeId, calleeName)
            ├── 1. RMI: clientService.inviteCall() → Tạo call session
            │     └── Trả về sessionId
            │
            ├── 2. Mở webcam preview (local video)
            │
            ├── 3. startCallStatusPolling() → Polling status mỗi 1 giây
            │     └── Khi status = "ACTIVE" → startVideoStreaming()
            │
            └── 4. startVideoStreaming()
                  ├── Đăng ký UDP endpoint với server
                  ├── startSendingFrames() → Gửi video frames qua UDP
                  └── startAudioStreaming() → Gửi/nhận audio qua UDP
```

#### **8.2. Callee (Người nhận):**

```
MainChatController.checkIncomingCalls() (polling mỗi 2 giây)
  → clientService.getPendingCalls() [RMI]
    → Trả về List<CallSession>
      → Hiển thị dialog "X đang gọi..."
        → User click "Chấp nhận"
          → openVideoCallWindow(callerId, callerName, false, sessionId)
            → Load CallView.fxml
              → VideoCallController được khởi tạo
                → controller.receiveCall(sessionId, callerId, callerName, true)
                  → handleJoinCall()
                    ├── 1. RMI: clientService.acceptCall() → Cập nhật status = ACTIVE
                    └── 2. startVideoStreaming() → Bắt đầu streaming
```

#### **8.3. Video/Audio Streaming:**

```
VideoCallController.startVideoStreaming()
  ├── 1. startLocalVideo() → Mở webcam, capture frames
  │     └── startSendingFrames() → Thread gửi video frames qua UDP
  │
  ├── 2. startAudioStreaming()
  │     ├── startMicrophoneCapture() → Capture audio từ mic
  │     └── startAudioPlayback() → Phát audio nhận được
  │
  └── 3. VideoStreamClient.startReceiving()
        └── Callback nhận frames → displayRemoteVideo() → Hiển thị video
```

---

### 9. LUỒNG GỬI FILE/ẢNH

```
User click btnAttach hoặc btnImage
  → MainChatController.handleAttachFile() hoặc handleAttachImage()
    → FileChooser.showOpenDialog()
      → User chọn file
        → fileTransferService.uploadFile() [HTTP POST]
          → FileHttpServerMain nhận file
            → Lưu file với SHA-256 hash
              → Trả về fileId
                → Tạo ChatMessage với type = FILE hoặc IMAGE
                  → Gửi qua TCP (giống gửi tin nhắn)
                    → Lưu vào database
```

---

### 10. LUỒNG TẠO NHÓM

```
User click btnCreateGroup
  → MainChatController.handleCreateGroup()
    → Hiển thị dialog
      → User nhập tên nhóm, chọn members
        → clientService.createGroup() [RMI]
          → GroupServiceImpl.createGroup()
            → RoomDao.create() → Tạo room trong database
              → RoomMemberDao.addMembers() → Thêm members
                → loadFriends() → Refresh UI
```

---

### 11. LUỒNG CHỈNH SỬA PROFILE

```
User click btnEditProfile
  → MainChatController.handleEditProfile()
    → Hiển thị dialog
      → User có thể:
        ├── Sửa display name
        ├── Upload avatar mới
        └── Đổi mật khẩu
          → clientService.updateProfile() [RMI]
            → AuthServiceImpl.updateProfile()
              → UserDao.updateProfile() → Cập nhật database
                → Session.setUser() → Cập nhật Session
                  → Refresh UI
```

---

### 12. LUỒNG TYPING INDICATOR

```
User gõ trong txtMessage
  → txtMessage.textProperty().addListener()
    → MainChatController.sendTypingSignal()
      → UDP Multicast: gửi packet "TYPING|roomId|userId|displayName"
        → Các clients khác nhận qua UDP Multicast
          → MainChatController.onTypingReceived()
            → Hiển thị "X đang nhập..." màu vàng
              → Tự động ẩn sau 3 giây
```

---

### 13. LUỒNG ONLINE/OFFLINE STATUS

```
User login
  → LoginController.doLogin()
    → clientService.login() [RMI]
      → AuthServiceImpl.login()
        → SessionManager.addSession() → Đánh dấu user online
          → Các clients khác: loadFriends() → isUserOnline() → Hiển thị chấm xanh

User logout
  → MainChatController.handleLogout()
    → clientService.logout() [RMI]
      → AuthServiceImpl.logout()
        → SessionManager.removeSession() → Đánh dấu user offline
          → Các clients khác: loadFriends() → isUserOnline() → Hiển thị chấm xám
```

---

## PHẦN 2: SCRIPT THUYẾT TRÌNH DEMO (10-15 PHÚT)

### **MỞ BÀI (30 giây):**

"Xin chào thầy và các bạn. Em sẽ trình bày về **AegisTalk** - một ứng dụng desktop chat và video call được xây dựng bằng JavaFX và Java, áp dụng các kiến thức từ môn Lập trình mạng.

AegisTalk sử dụng **5 giao thức mạng** chính: **RMI** cho quản lý dữ liệu, **TCP** cho chat real-time, **UDP** cho video streaming, **HTTP** cho file transfer, và **UDP Multicast** cho typing indicator. Ứng dụng có đầy đủ các tính năng của một ứng dụng chat hiện đại: kết bạn, chat 1-1, chat nhóm, video call, gửi file, và AI moderation.

Bây giờ em sẽ demo từng chức năng một."

**[Chuyển ý:]** "Đầu tiên, em sẽ khởi động server và client."

---

### **PHẦN 1: KHỞI ĐỘNG VÀ ĐĂNG NHẬP (1 phút)**

**[Demo: Mở terminal, chạy server]**

"Đầu tiên, em khởi động server. Server sẽ start 5 services:
- RMI Service Server trên port 1099
- RMI Moderation Server trên port 5100
- TCP Chat Server trên port 5555
- UDP Video Stream Server trên port 8888
- HTTP File Server trên port 8081

Như các bạn thấy, server đã khởi động thành công và hiển thị IP address của server."

**[Demo: Mở client, hiển thị LoginView]**

"Bây giờ em mở client. Ứng dụng sẽ hiển thị màn hình đăng nhập. Em có thể đăng nhập với tài khoản đã có hoặc đăng ký tài khoản mới."

**[Demo: Đăng nhập]**

"Em sẽ đăng nhập với username 'user1'. Khi đăng nhập thành công, ứng dụng sẽ gọi RMI `AuthService.login()` để xác thực, lưu thông tin user vào Session, và chuyển sang màn hình chat chính."

**[Demo: Hiển thị MainChatView]**

"Đây là màn hình chat chính. Bên trái là sidebar với danh sách bạn bè, nhóm, và lời mời kết bạn. Ở giữa là khung chat. Bên phải là info panel hiển thị thông tin của người đang chat."

**[Chuyển ý:]** "Bây giờ em sẽ demo chức năng tìm kiếm và kết bạn."

---

### **PHẦN 2: TÌM KIẾM VÀ KẾT BẠN (1 phút)**

**[Demo: Nhập tên trong search box]**

"Em sẽ tìm kiếm user 'user2'. Khi em nhập, ứng dụng sẽ gọi RMI `FriendService.searchUsers()` để tìm kiếm trong database."

**[Demo: Hiển thị kết quả tìm kiếm]**

"Như các bạn thấy, kết quả tìm kiếm hiển thị user 'user2' với avatar và nút 'Kết bạn'. Em sẽ click 'Kết bạn'."

**[Demo: Click "Kết bạn"]**

"Khi click 'Kết bạn', ứng dụng gọi RMI `FriendService.sendFriendRequest()` để gửi lời mời kết bạn. Lời mời được lưu vào database và user2 sẽ nhận được notification."

**[Demo: Mở tab "Requests" trên client 2]**

"Bây giờ em chuyển sang client 2 (user2). Em sẽ mở tab 'Requests' để xem lời mời kết bạn. Như các bạn thấy, có lời mời từ user1. Em sẽ click 'Chấp nhận'."

**[Demo: Chấp nhận lời mời]**

"Khi chấp nhận, ứng dụng gọi RMI `FriendService.acceptFriendRequest()` để cập nhật status và tạo quan hệ bạn bè. Bây giờ user1 và user2 đã là bạn bè."

**[Chuyển ý:]** "Bây giờ em sẽ demo chức năng chat."

---

### **PHẦN 3: CHAT 1-1 VÀ TYPING INDICATOR (2 phút)**

**[Demo: Click vào user2 trong danh sách bạn bè]**

"Em sẽ click vào user2 trong danh sách bạn bè để mở conversation. Khung chat sẽ hiển thị lịch sử tin nhắn (nếu có) được load từ database qua RMI."

**[Demo: Gửi tin nhắn]**

"Bây giờ em sẽ gửi một tin nhắn. Khi em nhấn Enter, ứng dụng sẽ:

**Bước 1:** Kiểm tra moderation qua HTTP - gọi Gemini API để kiểm duyệt nội dung. Nếu nội dung không phù hợp, tin nhắn sẽ bị chặn.

**Bước 2:** Hiển thị tin nhắn ngay lập tức - đây gọi là optimistic update để tăng trải nghiệm người dùng.

**Bước 3:** Gửi qua TCP socket để người nhận nhận được real-time.

**Bước 4:** Lưu vào database qua RMI để persist."

**[Demo: Nhận tin nhắn trên client 2]**

"Bây giờ em chuyển sang client 2. Như các bạn thấy, tin nhắn từ user1 đã được nhận và hiển thị. Tin nhắn được nhận qua TCP socket và hiển thị với màu xám, bên trái (khác với tin nhắn của mình là màu xanh, bên phải)."

**[Demo: Typing indicator]**

"Bây giờ em sẽ demo typing indicator. Khi em bắt đầu gõ trong ô nhập tin nhắn, ứng dụng sẽ gửi UDP Multicast packet với prefix 'TYPING'. Client 2 sẽ nhận được và hiển thị 'user1 đang nhập...' màu vàng."

**[Demo: Gõ trên client 1, xem indicator trên client 2]**

"Như các bạn thấy, khi em gõ trên client 1, client 2 hiển thị 'user1 đang nhập...'. Indicator sẽ tự động ẩn sau 3 giây nếu không có typing mới."

**[Chuyển ý:]** "Bây giờ em sẽ demo chức năng gửi file và ảnh."

---

### **PHẦN 4: GỬI FILE VÀ ẢNH (1 phút)**

**[Demo: Click nút "📎" (Attach)]**

"Em sẽ click nút 'Attach' để gửi file. FileChooser sẽ mở và em chọn một file."

**[Demo: Chọn file]**

"Khi chọn file, ứng dụng sẽ:
1. Upload file qua HTTP POST đến File Server
2. Server lưu file với SHA-256 hash để tránh trùng lặp
3. Trả về fileId
4. Tạo ChatMessage với type = FILE và gửi qua TCP"

**[Demo: Hiển thị file trong chat]**

"Như các bạn thấy, file đã được gửi và hiển thị trong chat với icon và tên file. Người nhận có thể click để download."

**[Demo: Gửi ảnh]**

"Tương tự, em có thể gửi ảnh bằng nút '🖼️'. Ảnh sẽ được upload và hiển thị trực tiếp trong chat."

**[Chuyển ý:]** "Bây giờ em sẽ demo chức năng video call."

---

### **PHẦN 5: VIDEO CALL (2 phút)**

**[Demo: Click nút "📹" (Video Call)]**

"Em sẽ click nút 'Video Call' để gọi user2. Khi click, ứng dụng sẽ:
1. Gọi RMI `CallService.inviteCall()` để tạo call session
2. Mở window video call
3. Mở webcam preview ngay lập tức (optimistic update)
4. Bắt đầu polling call status mỗi 1 giây"

**[Demo: Hiển thị video call window]**

"Đây là màn hình video call. Bên trái là local video (webcam của mình), bên phải là remote video (sẽ hiển thị khi người kia accept). Ở trên có đồng hồ đếm thời gian cuộc gọi."

**[Demo: Chấp nhận cuộc gọi trên client 2]**

"Bây giờ em chuyển sang client 2. Client 2 sẽ nhận được dialog 'user1 đang gọi...'. Em sẽ click 'Chấp nhận'."

**[Demo: Accept call]**

"Khi chấp nhận, ứng dụng sẽ:
1. Gọi RMI `CallService.acceptCall()` để cập nhật status = ACTIVE
2. Đăng ký UDP endpoint với server
3. Bắt đầu video/audio streaming qua UDP"

**[Demo: Video streaming]**

"Như các bạn thấy, video call đã bắt đầu. Video và audio được truyền qua UDP với độ trễ thấp. Em có thể:
- Toggle microphone (mute/unmute)
- Toggle camera (bật/tắt camera)
- End call để kết thúc cuộc gọi"

**[Demo: End call]**

"Khi click 'End call', ứng dụng sẽ gọi RMI `CallService.endCall()` để kết thúc cuộc gọi cho cả 2 phía và đóng window."

**[Chuyển ý:]** "Bây giờ em sẽ demo các chức năng khác."

---

### **PHẦN 6: CÁC CHỨC NĂNG KHÁC (2 phút)**

#### **6.1. Chỉnh sửa Profile (30 giây)**

**[Demo: Click nút "✏️" (Edit Profile)]**

"Em sẽ click nút 'Edit Profile' để chỉnh sửa thông tin cá nhân. Dialog sẽ hiển thị với 2 sections:
- Thông tin công khai: Sửa display name và upload avatar
- Bảo mật: Đổi mật khẩu"

**[Demo: Sửa display name và upload avatar]**

"Em sẽ sửa display name và upload avatar mới. Khi click 'Lưu', ứng dụng sẽ gọi RMI `AuthService.updateProfile()` để cập nhật database và refresh UI."

#### **6.2. Tạo Nhóm (30 giây)**

**[Demo: Click nút "Tạo nhóm"]**

"Em sẽ click nút 'Tạo nhóm' để tạo nhóm chat mới. Dialog sẽ hiển thị danh sách bạn bè để chọn members."

**[Demo: Tạo nhóm với tên và members]**

"Em sẽ nhập tên nhóm và chọn members. Khi click 'Tạo', ứng dụng sẽ gọi RMI `GroupService.createGroup()` để tạo nhóm trong database."

#### **6.3. Xem File Đã Gửi (30 giây)**

**[Demo: Click nút "ℹ️" (Info) → "📁" (Show Files)]**

"Em sẽ click nút 'Info' để mở info panel, sau đó click 'Show Files' để xem tất cả file đã gửi trong conversation."

**[Demo: Hiển thị danh sách file]**

"Dialog sẽ hiển thị danh sách tất cả file và ảnh đã gửi, với tên file, kích thước, và nút download."

#### **6.4. Hủy Kết Bạn (30 giây)**

**[Demo: Click nút "❌" (Unfriend)]**

"Em sẽ click nút 'Unfriend' để hủy kết bạn. Khi click, ứng dụng sẽ:
1. Xóa tất cả tin nhắn trong conversation
2. Xóa quan hệ bạn bè
3. Refresh danh sách bạn bè"

---

### **PHẦN 7: ONLINE/OFFLINE STATUS (30 giây)**

**[Demo: Logout trên client 1]**

"Em sẽ logout trên client 1. Khi logout, ứng dụng sẽ gọi RMI `AuthService.logout()` để đánh dấu user offline."

**[Demo: Xem status trên client 2]**

"Bây giờ em chuyển sang client 2. Như các bạn thấy, status của user1 đã chuyển từ 'Đang hoạt động' (chấm xanh) sang 'Không hoạt động' (chấm xám)."

**[Demo: Login lại]**

"Khi em login lại, status sẽ chuyển về 'Đang hoạt động'."

---

### **PHẦN 8: AI MODERATION (30 giây)**

**[Demo: Gửi tin nhắn không phù hợp]**

"Em sẽ demo chức năng AI moderation. Em sẽ gửi một tin nhắn có nội dung không phù hợp."

**[Demo: Tin nhắn bị chặn]**

"Như các bạn thấy, tin nhắn đã bị chặn bởi AI moderation. Ứng dụng gọi Gemini API qua HTTP để kiểm duyệt nội dung. Nếu nội dung không phù hợp, tin nhắn sẽ bị chặn và hiển thị thông báo lỗi."

---

### **KẾT LUẬN (30 giây):**

"Tóm lại, AegisTalk là một ứng dụng chat và video call hoàn chỉnh, áp dụng đầy đủ các kiến thức từ môn Lập trình mạng:

- **RMI** cho quản lý dữ liệu và authentication
- **TCP** cho chat real-time
- **UDP** cho video/audio streaming
- **HTTP** cho file transfer và AI moderation
- **UDP Multicast** cho typing indicator và presence

Ứng dụng có đầy đủ các tính năng: kết bạn, chat 1-1, chat nhóm, video call, gửi file, AI moderation, và quản lý profile.

**Hướng cải tiến:** Có thể thêm end-to-end encryption, voice call, screen sharing, và hỗ trợ mobile app.

Em xin cảm ơn thầy và các bạn đã lắng nghe!"

---

## TỔNG KẾT

Script thuyết trình này bao gồm:
- **Mở bài (30s)**: Giới thiệu dự án và các giao thức
- **8 phần demo (10-12 phút)**: Từng chức năng với giải thích chi tiết
- **Kết luận (30s)**: Tổng kết và hướng cải tiến

Mỗi phần demo có:
- **Giải thích flow**: Nói rõ các bước xảy ra
- **Chỉ vào UI**: Giải thích các elements trên màn hình
- **Chỉ vào code/logs**: Giải thích implementation (nếu cần)
- **Câu chuyển ý**: Chuyển sang phần tiếp theo một cách tự nhiên

