# TÀI LIỆU GIẢI THÍCH MainChatController.java

## 1. TỔNG QUAN (5 dòng)

**MainChatController** là controller chính của giao diện chat ứng dụng AegisTalk, được load sau khi user đăng nhập thành công. Module này quản lý toàn bộ giao diện chat (sidebar bạn bè, khung chat, info panel), xử lý gửi/nhận tin nhắn qua TCP, quản lý bạn bè/nhóm qua RMI, và tích hợp các tính năng như video call, moderation, typing indicator. Module được sử dụng trong `MainChatView.fxml` và là màn hình chính của ứng dụng sau khi login.

---

## 2. FLOW RUNTIME: TỪ APP START → MODULE ĐƯỢC GỌI

### **2.1. Application Start**
```
HelloApplication.main() 
  → HelloApplication.start() 
    → Load LoginView.fxml 
      → LoginController được khởi tạo
```

### **2.2. User Login**
```
User nhập username/password 
  → LoginController.doLogin()
    → clientService.login() [RMI call]
      → Session.setUser() [Lưu user info]
        → Load MainChatView.fxml
          → MainChatController được khởi tạo
```

### **2.3. MainChatController.initialize() được gọi tự động**
```
1. Khởi tạo services:
   - AegisTalkClientService (RMI) → Quản lý bạn bè, conversations
   - FileTransferService → Upload/download files
   
2. Load dữ liệu ban đầu:
   - loadFriends() [RMI] → Lấy danh sách bạn bè từ server
   - loadPendingRequests() [RMI] → Lấy lời mời kết bạn
   
3. Setup UI:
   - Hiển thị tên/avatar user hiện tại
   - Setup search, tabs, contacts list
   - Bind event handlers cho buttons
   
4. Kết nối real-time:
   - connectChat() [TCP] → Kết nối ChatServer để nhận tin nhắn
   - connectModeration() [HTTP] → Kết nối ModerationServer
   - startTypingPresence() [UDP Multicast] → Gửi/nhận typing indicator
   
5. Background tasks:
   - startAutoRefresh() → Refresh friend requests mỗi 3 giây
   - startIncomingCallChecker() → Kiểm tra incoming calls mỗi 2 giây
```

### **2.4. Runtime Flow - Gửi/Nhận Tin Nhắn**

**Gửi tin nhắn:**
```
User nhập text → txtMessage Enter/btnSend click
  → sendMessage()
    → ModerationClient.moderateText() [HTTP] → Kiểm tra nội dung
      → ChatMessage.text() → Tạo message object
        → addMessageToUI() [Optimistic update] → Hiển thị ngay
          → chatClient.send() [TCP] → Gửi qua TCP socket
            → clientService.saveMessage() [RMI] → Lưu vào database
```

**Nhận tin nhắn:**
```
ChatServer nhận message từ user khác
  → ChatServer broadcast qua TCP
    → ChatClient.onMessageReceived()
      → MainChatController.onIncomingMessage() [Callback]
        → Kiểm tra room ID match
          → addMessageToUI() → Hiển thị message
            → playNotificationSound() → Phát âm thanh
              → highlightContactItem() → Highlight nếu khác room
```

### **2.5. Các Luồng Dữ Liệu Chính**

**RMI (Remote Method Invocation):**
- `clientService.getFriends()` → Lấy danh sách bạn bè
- `clientService.sendFriendRequest()` → Gửi lời mời kết bạn
- `clientService.getOrCreateDirectConversation()` → Tạo/lấy conversation
- `clientService.saveMessage()` → Lưu message vào DB
- `clientService.startCall()` → Bắt đầu video call

**TCP (Socket):**
- `chatClient.send(msg)` → Gửi message real-time
- `chatClient.onMessageReceived()` → Nhận message real-time

**UDP Multicast:**
- `typingSocket.send()` → Gửi typing indicator
- `typingThread` → Lắng nghe typing events

**HTTP:**
- `moderationClient.moderateText()` → Kiểm tra nội dung tin nhắn
- `fileTransferService.uploadFile()` → Upload file

---

## 3. GIẢI THÍCH TỪNG METHOD

### **3.1. initialize()**

**Method:** `@FXML private void initialize()`

**Purpose:** 
Khởi tạo controller khi FXML được load. Setup tất cả services, load dữ liệu ban đầu, kết nối real-time, và start background tasks.

**Input/Output:**
- Input: Không (được gọi tự động bởi JavaFX)
- Output: Không (void)

**Important lines:**
- Dòng 153: `clientService = new AegisTalkClientService()` → Khởi tạo RMI client
- Dòng 155-156: `loadFriends()`, `loadPendingRequests()` → Load dữ liệu ban đầu
- Dòng 197: `connectChat()` → Kết nối TCP chat server
- Dòng 206: `startAutoRefresh()` → Start background refresh
- Dòng 211: `startTypingPresence()` → Start UDP typing indicator

**Corner cases:**
- Nếu RMI connection fail → Hiển thị error, nhưng app vẫn chạy
- Nếu TCP connection fail → Chat không hoạt động, nhưng UI vẫn hiển thị
- Nếu UDP multicast fail → Typing indicator không hoạt động, nhưng chat vẫn OK

---

### **3.2. sendMessage()**

**Method:** `private void sendMessage()`

**Purpose:**
Xử lý gửi tin nhắn text: kiểm tra moderation, tạo message object, hiển thị optimistic update, gửi qua TCP, và lưu vào database.

**Input/Output:**
- Input: Lấy text từ `txtMessage.getText()`
- Output: Không (void), nhưng gửi message qua TCP và lưu vào DB

**Important lines:**
- Dòng 1454-1464: Moderation check → Chặn nội dung không phù hợp
- Dòng 1474: `ChatMessage.text()` → Tạo message object với room ID và user ID
- Dòng 1483-1486: Optimistic update → Hiển thị message ngay không đợi server
- Dòng 1491: `chatClient.send(msg)` → Gửi qua TCP socket
- Dòng 1497: `clientService.saveMessage(msg)` → Lưu vào database qua RMI

**Corner cases:**
- `currentChat == null` → Hiển thị error "Chưa chọn cuộc trò chuyện"
- `currentRoomId == null` → Hiển thị error "room ID không hợp lệ"
- Moderation BLOCK → Không gửi, hiển thị error với lý do
- TCP send fail → Message đã hiển thị (optimistic) nhưng không gửi được → Có thể duplicate
- Database save fail → Message đã gửi qua TCP nhưng không lưu DB → Mất lịch sử

---

### **3.3. onIncomingMessage(ChatMessage msg)**

**Method:** `private void onIncomingMessage(ChatMessage msg)`

**Purpose:**
Callback được gọi khi nhận tin nhắn mới từ TCP ChatServer. Kiểm tra room ID, tránh duplicate, và hiển thị message vào UI.

**Input/Output:**
- Input: `ChatMessage msg` → Message nhận được từ server
- Output: Không (void), nhưng update UI

**Important lines:**
- Dòng 1524: `isOwnMessage` check → Tránh hiển thị lại message của mình (đã optimistic update)
- Dòng 1528-1530: Skip own message → Bỏ qua nếu là message của mình
- Dòng 1557-1558: Room ID matching → Chỉ hiển thị nếu đúng room hiện tại
- Dòng 1567: `addMessageToUI(msg)` → Thêm message vào UI
- Dòng 1576-1577: Highlight contact → Nếu message từ room khác, highlight contact item

**Corner cases:**
- `currentRoomId == null` → Thử lấy từ `currentChat.conversation`
- Room ID không match → Message từ room khác → Highlight contact, phát sound
- Duplicate message → Bỏ qua nếu là own message (đã hiển thị qua optimistic)
- `msg.from()` không parse được → Không hiển thị avatar sender

---

### **3.4. addMessageToUI(ChatMessage msg)**

**Method:** `private void addMessageToUI(ChatMessage msg)`

**Purpose:**
Thêm message vào UI (messagesContainer). Xử lý system messages, file messages, và text messages với styling khác nhau.

**Input/Output:**
- Input: `ChatMessage msg` → Message cần hiển thị
- Output: Không (void), nhưng thêm Node vào `messagesContainer`

**Important lines:**
- Dòng 1584-1596: System message → Hiển thị ở giữa với style đặc biệt
- Dòng 1599-1605: FILE/IMAGE message → Gọi `addFileMessageToUI()`
- Dòng 1625: `isOwnMessage` check → Xác định alignment (trái/phải)
- Dòng 1681-1686: Message bubble styling → Màu xanh cho own, xám cho other
- Dòng 1690: Text label styling → Màu trắng cho own, xám nhạt cho other

**Corner cases:**
- `msg == null` → Return early, không làm gì
- `msg.text() == null` → Return early cho text messages
- FILE/IMAGE với `payloadRef == null` → Warning log, không hiển thị
- Sender không tìm thấy trong cache → Dùng fallback, không hiển thị avatar

---

### **3.5. openChat(ContactItem item)**

**Method:** `private void openChat(ContactItem item)`

**Purpose:**
Mở cuộc trò chuyện với một contact (friend hoặc group). Load conversation, lấy message history, và hiển thị trong chat area.

**Input/Output:**
- Input: `ContactItem item` → Contact cần mở chat
- Output: Không (void), nhưng update UI và set `currentChat`, `currentRoomId`

**Important lines:**
- Dòng 1359-1361: Direct conversation → Lấy hoặc tạo conversation với friend
- Dòng 1377-1379: Group conversation → Dùng conversation ID trực tiếp
- Dòng 1360: `applyAvatar()` → Hiển thị avatar của contact
- Dòng 1370: `loadMessageHistory()` → Load tin nhắn cũ từ database
- Dòng 1372: `clearUnreadMessages()` → Xóa unread count

**Corner cases:**
- `item == null` → Không làm gì
- Conversation không tồn tại → Tạo mới qua `getOrCreateDirectConversation()`
- Load history fail → Hiển thị error, nhưng vẫn mở chat (có thể gửi tin mới)
- `currentRoomId` không set được → Incoming messages có thể không match

---

### **3.6. loadFriends()**

**Method:** `private void loadFriends()`

**Purpose:**
Load danh sách bạn bè từ server qua RMI, lấy conversation và online status, rồi hiển thị trong contacts list.

**Input/Output:**
- Input: Không (lấy từ `Session.getUserId()`)
- Output: Không (void), nhưng update `friendsList` và UI

**Important lines:**
- Dòng 1305: `clientService.getFriends()` [RMI] → Lấy danh sách bạn bè
- Dòng 1308-1315: Loop qua friends → Lấy conversation và online status
- Dòng 1317: `new ContactItem()` → Tạo contact item với đầy đủ thông tin
- Dòng 1320: `friendsList.addAll()` → Thêm vào ObservableList

**Corner cases:**
- RMI call fail → Hiển thị error, `friendsList` rỗng
- Friend không có conversation → `conversation = null`, không hiển thị last message
- Online status check fail → `online = null`, hiển thị offline

---

### **3.7. handleEditProfile()**

**Method:** `@FXML private void handleEditProfile()`

**Purpose:**
Mở dialog để chỉnh sửa profile: display name, avatar path, và đổi mật khẩu. Validate input và update qua RMI.

**Input/Output:**
- Input: Không (được gọi từ button click)
- Output: Không (void), nhưng update Session và UI nếu thành công

**Important lines:**
- Dòng 279-487: Tạo custom Dialog với GridPane layout
- Dòng 411-420: BooleanBinding validation → Disable OK button nếu invalid
- Dòng 430-435: Avatar preview → Hiển thị preview khi chọn file
- Dòng 442-445: `clientService.updateProfile()` [RMI] → Update display name và avatar
- Dòng 447-450: `clientService.changePassword()` [RMI] → Đổi mật khẩu (nếu có)

**Corner cases:**
- Display name rỗng → OK button disabled
- Password change: new password != confirm → OK button disabled
- Avatar file không tồn tại → Preview không hiển thị, nhưng vẫn lưu path
- RMI update fail → Hiển thị error, không update Session

---

### **3.8. handleVideoCall()**

**Method:** `@FXML private void handleVideoCall()`

**Purpose:**
Bắt đầu video call với contact hiện tại. Tạo call session qua RMI và mở VideoCallController window.

**Input/Output:**
- Input: Không (được gọi từ button click)
- Output: Không (void), nhưng mở video call window

**Important lines:**
- Dòng 1795: Check `currentChat.user != null` → Chỉ call được với friend, không phải group
- Dòng 1800: `clientService.startCall()` [RMI] → Tạo call session trên server
- Dòng 1803: `openVideoCallWindow()` → Mở window mới với VideoCallController

**Corner cases:**
- `currentChat == null` → Hiển thị error "Chưa chọn cuộc trò chuyện"
- `currentChat.user == null` → Hiển thị error "Chỉ có thể gọi với bạn bè"
- RMI startCall fail → Hiển thị error, không mở window
- VideoCallController load fail → Hiển thị error

---

### **3.9. startTypingPresence()**

**Method:** `private void startTypingPresence()`

**Purpose:**
Khởi động UDP multicast để gửi/nhận typing indicator. Tạo socket, join multicast group, và start listening thread.

**Input/Output:**
- Input: Không
- Output: Không (void), nhưng start background thread

**Important lines:**
- Dòng 2793-2798: Tạo MulticastSocket và join group `239.1.1.1:4447`
- Dòng 2800: `typingRunning.set(true)` → Set flag để thread chạy
- Dòng 2802-2826: `typingThread` → Thread lắng nghe typing events
- Dòng 2828-2865: `sendTypingSignal()` → Gửi typing event khi user đang gõ

**Corner cases:**
- Socket creation fail → Log warning, typing indicator không hoạt động
- Multicast group join fail → Typing không hoạt động, nhưng chat vẫn OK
- Network interface không hỗ trợ multicast → Fallback, không crash app

---

### **3.10. performMessageSearch()**

**Method:** `@FXML private void performMessageSearch()`

**Purpose:**
Tìm kiếm tin nhắn trong conversation hiện tại. Load message history, filter theo keyword, và hiển thị kết quả.

**Input/Output:**
- Input: Lấy keyword từ `txtSearchMessages.getText()`
- Output: Không (void), nhưng update search results UI

**Important lines:**
- Dòng 751: Lấy keyword và trim
- Dòng 755-758: Load message history từ database
- Dòng 760-765: Filter messages chứa keyword (case-insensitive)
- Dòng 801: Hiển thị kết quả trong ListView
- Dòng 853-870: Click vào kết quả → Scroll đến message đó

**Corner cases:**
- `currentRoomId == null` → Hiển thị error "Chưa chọn cuộc trò chuyện"
- Keyword rỗng → Hiển thị hint "Nhập từ khóa để tìm kiếm"
- Không tìm thấy → Hiển thị "Không tìm thấy kết quả"
- Load history fail → Hiển thị error

---

### **3.11. applyAvatar(Circle circle, String avatarPath, String fallbackColor)**

**Method:** `private void applyAvatar(Circle circle, String avatarPath, String fallbackColor)`

**Purpose:**
Áp dụng avatar cho Circle: load ảnh từ path hoặc dùng fallback color nếu không có ảnh.

**Input/Output:**
- Input: `circle` (Circle node), `avatarPath` (String path hoặc URL), `fallbackColor` (String hex color)
- Output: Không (void), nhưng set fill cho circle

**Important lines:**
- Dòng 2752: `circle.setStyle("")` → Clear inline style để setFill hoạt động
- Dòng 2753-2760: Load image từ path/URL → Set ImagePattern nếu thành công
- Dòng 2765: `circle.setFill(Color.web(fallbackColor))` → Dùng fallback color nếu load fail

**Corner cases:**
- `circle == null` → Return early
- `avatarPath == null` hoặc blank → Dùng fallback color ngay
- Image load fail (file không tồn tại, URL lỗi) → Catch exception, dùng fallback
- `img.isError() == true` → Dùng fallback color

---

### **3.12. fallbackColorForUser(Long userId)**

**Method:** `private String fallbackColorForUser(Long userId)`

**Purpose:**
Tính toán màu fallback cho avatar dựa trên user ID. Đảm bảo mỗi user có màu nhất quán.

**Input/Output:**
- Input: `Long userId` → ID của user
- Output: `String` → Hex color code

**Important lines:**
- Dòng 2769-2770: Check null/negative → Return default color `#6366f1`
- Dòng 2772: `(userId - 1) % AVATAR_COLORS.length` → Tính index trong mảng màu
- Dòng 2773: Return color từ `AVATAR_COLORS` array

**Corner cases:**
- `userId == null` → Return `#6366f1` (default)
- `userId <= 0` → Return `#6366f1` (default)
- Modulo operation đảm bảo index luôn trong range [0, 6]

---

## 4. 10 CÂU HỎI THẦY CÓ THỂ HỎI + CÂU TRẢ LỜI

### **Câu 1: MainChatController sử dụng những công nghệ/giao thức nào để giao tiếp với server?**

**Trả lời:** 
- **RMI (Remote Method Invocation)**: Qua `AegisTalkClientService` để quản lý bạn bè, conversations, messages, calls
- **TCP Socket**: Qua `ChatClient` để gửi/nhận tin nhắn real-time
- **UDP Multicast**: Qua `MulticastSocket` để gửi/nhận typing indicator
- **HTTP**: Qua `ModerationClient` và `FileTransferService` để moderation và upload/download files

---

### **Câu 2: Tại sao khi gửi tin nhắn lại có "optimistic update"?**

**Trả lời:** 
Optimistic update (dòng 1483-1486) hiển thị message ngay lập tức trước khi server xác nhận để tăng trải nghiệm người dùng. Message được hiển thị ngay, sau đó mới gửi qua TCP và lưu DB. Trong `onIncomingMessage()` có check để tránh duplicate nếu nhận lại message của chính mình.

---

### **Câu 3: Làm thế nào để tránh duplicate message khi nhận tin nhắn?**

**Trả lời:** 
Trong `onIncomingMessage()` (dòng 1524-1530), kiểm tra `isOwnMessage` bằng cách so sánh `msg.from()` với `Session.getUserId()`. Nếu là message của mình thì bỏ qua vì đã hiển thị qua optimistic update. Nếu là message của người khác thì mới hiển thị.

---

### **Câu 4: Typing indicator hoạt động như thế nào?**

**Trả lời:** 
Sử dụng UDP Multicast (group `239.1.1.1:4447`). Khi user gõ trong `txtMessage`, `sendTypingSignal()` (dòng 2828) gửi UDP packet với format `"TYPING|userId|conversationId"`. Thread `typingThread` (dòng 2802) lắng nghe và khi nhận được thì hiển thị "đang nhập..." trong `lblChatStatus` với màu vàng, tự ẩn sau 2.5 giây.

---

### **Câu 5: Làm sao để biết message thuộc conversation nào?**

**Trả lời:** 
Mỗi message có field `room()` chứa conversation ID (String). Khi mở chat, `currentRoomId` được set (dòng 1360, 1378). Khi nhận message, so sánh `msg.room()` với `currentRoomId` (dòng 1557-1558). Nếu match thì hiển thị, nếu không thì highlight contact item và phát sound.

---

### **Câu 6: Tại sao có cả RMI và TCP cho messages?**

**Trả lời:** 
- **RMI (`saveMessage`)**: Lưu message vào database để persist, có thể query lại lịch sử
- **TCP (`chatClient.send`)**: Gửi real-time để người nhận nhận được ngay, không cần polling

Hai cơ chế bổ sung nhau: TCP cho real-time, RMI cho persistence.

---

### **Câu 7: Moderation hoạt động như thế nào?**

**Trả lời:** 
Trong `sendMessage()` (dòng 1454-1464), trước khi gửi, gọi `moderationClient.moderateText(text)` qua HTTP. Nếu `result.getDecision() == ModerationDecision.BLOCK` thì không gửi và hiển thị error với lý do. Nếu moderation fail (exception) thì vẫn cho phép gửi để không block user.

---

### **Câu 8: Online/offline status được cập nhật như thế nào?**

**Trả lời:** 
- Khi load friends (dòng 1312): Gọi `clientService.isOnline(userId)` qua RMI để check status
- `startAutoRefresh()` (dòng 995): Refresh friend list mỗi 3 giây, cập nhật online status
- `isUserOnline()` (dòng 2776): Wrapper method gọi RMI `clientService.isOnline()`
- Server track active sessions trong `AuthServiceImpl.activeSessions` map

---

### **Câu 9: File messages được xử lý như thế nào?**

**Trả lời:** 
- Upload: `handleAttachFile()` (dòng 2376) dùng `FileTransferService.uploadFile()` qua HTTP, lưu `fileId|filename|size` vào `payloadRef`
- Hiển thị: `addFileMessageToUI()` (dòng 2498) parse `payloadRef`, hiển thị icon theo extension, có button download
- Download: `downloadFile()` (dòng 2604) tải từ HTTP server `/files/{fileId}`
- Download trong file dialog: `handleShowFiles()` (dòng 541) list tất cả files trong conversation

---

### **Câu 10: ContactItem là gì và tại sao cần nó?**

**Trả lời:** 
`ContactItem` (dòng 2918) là inner class model để đại diện cho một item trong contacts list. Có thể là friend (có `user`) hoặc group (có `conversation`). Chứa thông tin: user, name, lastMessage, conversation, online status. Dùng để hiển thị thống nhất trong `lstContacts` ListView, dù là friend hay group.

---

## 5. SCRIPT THUYẾT TRÌNH 4 PHÚT (CÓ DEMO)

### **PHẦN 1: GIỚI THIỆU (30 giây)**

"Xin chào thầy và các bạn. Hôm nay em sẽ trình bày về **MainChatController** - module chính quản lý giao diện chat của ứng dụng AegisTalk. Module này được load sau khi user đăng nhập thành công và là trung tâm xử lý tất cả tính năng chat, bao gồm gửi/nhận tin nhắn, quản lý bạn bè, video call, và nhiều tính năng khác."

**[Demo: Mở ứng dụng, đăng nhập, hiển thị MainChatView]**

---

### **PHẦN 2: KIẾN TRÚC VÀ CÔNG NGHỆ (1 phút)**

"MainChatController sử dụng **4 giao thức** chính:

**Thứ nhất, RMI** - Qua `AegisTalkClientService` để quản lý bạn bè, conversations, và lưu messages vào database. 

**Thứ hai, TCP Socket** - Qua `ChatClient` để gửi/nhận tin nhắn real-time. Khi user gửi message, nó được gửi qua TCP để người nhận nhận được ngay lập tức.

**Thứ ba, UDP Multicast** - Để gửi/nhận typing indicator. Khi một user đang gõ, ứng dụng gửi UDP packet và người kia thấy "đang nhập...".

**Cuối cùng, HTTP** - Cho moderation và file transfer."

**[Demo: Mở code, chỉ vào các service fields và constants]**

---

### **PHẦN 3: FLOW GỬI/NHẬN TIN NHẮN (1.5 phút)**

"Bây giờ em sẽ demo flow gửi/nhận tin nhắn:

**Khi gửi tin nhắn:**
1. User nhập text và nhấn Enter
2. Method `sendMessage()` được gọi
3. Đầu tiên, kiểm tra moderation qua HTTP - nếu nội dung không phù hợp thì bị chặn
4. Sau đó, hiển thị message ngay lập tức - đây gọi là optimistic update để tăng UX
5. Gửi qua TCP socket để người nhận nhận được real-time
6. Cuối cùng, lưu vào database qua RMI để persist

**Khi nhận tin nhắn:**
1. TCP ChatServer broadcast message
2. `onIncomingMessage()` callback được gọi
3. Kiểm tra room ID - chỉ hiển thị nếu đúng conversation đang mở
4. Tránh duplicate - nếu là message của mình thì bỏ qua vì đã hiển thị qua optimistic update
5. Hiển thị vào UI và phát âm thanh thông báo"

**[Demo: Gửi tin nhắn giữa 2 client, chỉ vào console logs, giải thích flow]**

---

### **PHẦN 4: CÁC TÍNH NĂNG NỔI BẬT (1 phút)**

"Module này còn có nhiều tính năng khác:

**Thứ nhất, Typing Indicator** - Sử dụng UDP Multicast. Khi user gõ, gửi UDP packet và người kia thấy "đang nhập..." màu vàng.

**Thứ hai, Online/Offline Status** - Refresh mỗi 3 giây qua RMI để cập nhật status real-time.

**Thứ ba, Message Search** - Tìm kiếm trong conversation hiện tại, có highlight và scroll đến message.

**Thứ tư, File Transfer** - Upload/download files qua HTTP, hiển thị icon theo extension.

**Cuối cùng, Video Call** - Tích hợp với VideoCallController để bắt đầu video call."

**[Demo: Typing indicator, online status, search messages, file upload]**

---

### **PHẦN 5: KẾT LUẬN (30 giây)**

"Tóm lại, MainChatController là module trung tâm quản lý toàn bộ giao diện và logic chat, sử dụng đa giao thức (RMI, TCP, UDP, HTTP) để đảm bảo real-time communication, data persistence, và các tính năng nâng cao. Module được thiết kế với separation of concerns rõ ràng, xử lý tốt các corner cases, và có optimistic update để tăng trải nghiệm người dùng.

Em xin cảm ơn thầy và các bạn đã lắng nghe!"

---

## TỔNG KẾT

**MainChatController** là một module phức tạp và quan trọng, tích hợp nhiều công nghệ để tạo ra một ứng dụng chat hoàn chỉnh với real-time communication, data persistence, và nhiều tính năng nâng cao.

