# TÀI LIỆU GIẢI THÍCH MODULE UDP - VideoStreamClient & VideoStreamServer

---

## PHẦN 1: GIẢI THÍCH CODE

### 1. MỤC TIÊU CỦA MODULE

**VideoStreamClient** và **VideoStreamServer** là module UDP streaming cho video call trong ứng dụng AegisTalk. Module này sử dụng **UDP (User Datagram Protocol)** để truyền video/audio frames real-time giữa các clients với độ trễ thấp, chấp nhận mất gói tin (packet loss) để đảm bảo tốc độ cao cho streaming.

**VideoStreamClient** đóng vai trò client: gửi frames từ webcam/microphone và nhận frames từ remote clients. **VideoStreamServer** đóng vai trò server trung gian: nhận frames từ một client và forward đến các clients khác trong cùng session.

---

### 2. LUỒNG CHẠY CHÍNH (STEP-BY-STEP)

#### **2.1. Luồng khởi tạo và kết nối:**

```
1. Server khởi động:
   VideoStreamServer server = new VideoStreamServer(8888)
   → server.start()
     → Tạo DatagramSocket trên port 8888
     → Start thread receiveLoop() để nhận packets

2. Client khởi tạo:
   VideoStreamClient client = new VideoStreamClient(sessionId)
   → Tạo DatagramSocket (port tự động)
   → client.connect(serverHost, serverPort)
     → Lưu serverAddress và serverPort

3. Client đăng ký endpoint:
   client.setUserId(userId)
   → Server.registerEndpoint(sessionId, userId, localIP, localPort)
     → Tạo StreamSession nếu chưa có
     → Lưu UserEndpoint vào session
```

#### **2.2. Luồng gửi frames (Client → Server → Other Clients):**

```
1. Client A capture frame từ webcam/mic:
   → client.sendFrame(frameData)
     → Tạo header (16 bytes): [sessionId][userId][sequence][timestamp]
     → Ghép header + payload thành packet
     → socket.send(packet) → Gửi đến server

2. Server nhận packet:
   → receiveLoop() nhận DatagramPacket
     → processPacket(packet)
       → Parse header: sessionId, userId, sequence, timestamp
       → Lấy payload (frameData)
       → Tìm StreamSession theo sessionId
       → updateUserEndpoint() → Cập nhật actual IP:port của user
       → forwardToOthers() → Forward đến các clients khác

3. Server forward packet:
   → Duyệt qua tất cả UserEndpoint trong session
     → Bỏ qua sender (userId == senderId)
     → Tạo DatagramPacket mới với header giữ nguyên
     → socket.send(forwardPacket) → Gửi đến client B, C, ...

4. Client B nhận frame:
   → startReceiving() thread nhận DatagramPacket
     → Parse header: sessionId, senderId, sequence, timestamp
     → Kiểm tra sessionId khớp
     → Kiểm tra senderId != userId (bỏ qua frames từ chính mình)
     → Lấy payload
     → receiver.onFrameReceived() → Callback để xử lý frame
```

#### **2.3. Luồng phát hiện IP LAN:**

```
Client cần biết IP LAN thực tế để đăng ký với server:
→ getLocalLanAddress()
  → Enumeration<NetworkInterface> → Duyệt tất cả network interfaces
    → Bỏ qua loopback, virtual adapters (VirtualBox, VMware, Docker)
    → Lấy IPv4 addresses (Inet4Address)
    → Ưu tiên: WiFi/Ethernet adapters
    → Fallback: InetAddress.getLocalHost()
```

---

### 3. GIẢI THÍCH TỪNG HÀM

#### **3.1. VideoStreamClient.java**

##### **3.1.1. VideoStreamClient(int sessionId)**

**Input:** `sessionId` (int) - ID của call session

**Output:** Không (constructor)

**Ý nghĩa biến:**
- `this.sessionId` - Lưu session ID để đính kèm vào mỗi packet
- `this.userId` - Mặc định = 0, sẽ được set sau bằng `setUserId()`
- `this.socket` - DatagramSocket để gửi/nhận UDP packets
- `this.sequenceNumber` - Bắt đầu từ 0, tăng dần cho mỗi frame

**Vì sao viết vậy:**
- Tạo DatagramSocket không bind port cụ thể → Hệ thống tự chọn port tự do
- `userId = 0` mặc định vì sẽ được set sau khi login

---

##### **3.1.2. getLocalLanAddress()**

**Input:** Không (static method)

**Output:** `String` - IP address dạng "192.168.1.100"

**Ý nghĩa biến:**
- `bestAddress` - IP tốt nhất tìm được
- `allFoundAddresses` - Danh sách tất cả IPs tìm được (để debug)
- `interfaces` - Enumeration tất cả network interfaces
- `isVirtual` - Check xem adapter có phải virtual không

**Vì sao viết vậy:**
- **Bỏ qua virtual adapters** (dòng 59-62): VirtualBox, VMware, Docker tạo virtual adapters với IP riêng, không phải IP thực tế của máy
- **Bỏ qua 192.168.56.x** (dòng 74-76): Đây là default range của VirtualBox, không phải LAN thực
- **Ưu tiên WiFi/Ethernet** (dòng 88-92): Các adapter này thường là adapter thực tế
- **Fallback getLocalHost()** (dòng 106): Nếu không tìm thấy, dùng localhost (OK khi chạy local)

**Điểm dễ lỗi:**
- Nếu có nhiều network adapters, có thể chọn sai adapter
- Virtual adapters có thể được chọn nếu không filter đúng

**Cách debug:**
- Log `allFoundAddresses` (dòng 99) để xem tất cả IPs tìm được
- Log `bestAddress` (dòng 85-86) để xem IP được chọn
- Test với nhiều network adapters khác nhau

---

##### **3.1.3. connect(String serverHost, int serverPort)**

**Input:** 
- `serverHost` (String) - Hostname hoặc IP của server
- `serverPort` (int) - Port của server

**Output:** Không (void), nhưng lưu `serverAddress` và `serverPort`

**Ý nghĩa biến:**
- `this.serverAddress` - InetAddress của server (để gửi packets)
- `this.serverPort` - Port của server

**Vì sao viết vậy:**
- Chỉ lưu thông tin server, không tạo connection thực sự (UDP là connectionless)
- `InetAddress.getByName()` resolve hostname thành IP

---

##### **3.1.4. sendFrame(byte[] frameData)**

**Input:** `frameData` (byte[]) - Video/audio frame data (JPEG bytes hoặc audio bytes)

**Output:** Không (void), nhưng gửi UDP packet

**Ý nghĩa biến:**
- `header` (ByteBuffer) - Header 16 bytes: [sessionId(4)][userId(4)][sequence(4)][timestamp(4)]
- `packetData` - Ghép header + payload
- `packet` (DatagramPacket) - UDP packet để gửi

**Vì sao viết vậy:**
- **Header format cố định 16 bytes** (dòng 149-154):
  - `sessionId` (4 bytes): Để server biết frame thuộc session nào
  - `userId` (4 bytes): Để server biết ai gửi (quan trọng khi 2 clients cùng IP)
  - `sequence` (4 bytes): Số thứ tự frame (để detect mất gói)
  - `timestamp` (4 bytes): Thời gian gửi (để tính latency)
- **Ghép header + payload** (dòng 157-159): `System.arraycopy()` để ghép nhanh
- **Gửi qua DatagramSocket** (dòng 161-165): UDP không đảm bảo delivery

**Điểm dễ lỗi:**
- `frameData` quá lớn (> 65507 bytes) → UDP packet quá lớn, sẽ bị split hoặc fail
- `serverAddress == null` → `IllegalStateException` (chưa connect)
- Packet loss → Frame bị mất, không có retry

**Cách debug:**
- Log `sequenceNumber` để xem frames có bị skip không
- Log packet size để đảm bảo < 65507 bytes
- Monitor network để xem packets có đến server không

---

##### **3.1.5. startReceiving(FrameReceiver receiver)**

**Input:** `receiver` (FrameReceiver) - Callback interface để xử lý frames nhận được

**Output:** Không (void), nhưng start background thread

**Ý nghĩa biến:**
- `running` - Flag để dừng thread
- `buffer` (byte[65507]) - Buffer để nhận packets (max UDP size)
- `receiveThread` - Thread nhận packets

**Vì sao viết vậy:**
- **Background thread** (dòng 177): Không block main thread
- **Buffer size 65507** (dòng 178): Max UDP packet size
- **Parse header** (dòng 190-194): Đọc 16 bytes đầu để lấy sessionId, senderId, sequence, timestamp
- **Kiểm tra sessionId** (dòng 197-199): Chỉ nhận frames từ session đúng
- **Bỏ qua frames từ chính mình** (dòng 202-204): Dựa vào `senderId`, không phải IP (vì có thể cùng IP)

**Điểm dễ lỗi:**
- Packet < 16 bytes → Bỏ qua (dòng 185-187)
- `sessionId` không khớp → Bỏ qua (có thể là packet từ session khác)
- `senderId == userId` → Bỏ qua (frame từ chính mình, tránh loopback)
- `receiver == null` → Không xử lý frame (dòng 211)

**Cách debug:**
- Log `recvSessionId` và `sessionId` để xem có mismatch không
- Log `senderId` và `userId` để xem có loopback không
- Log `sequence` để xem frames có bị mất không

---

##### **3.1.6. stop()**

**Input:** Không

**Output:** Không (void), nhưng dừng receiving và đóng socket

**Ý nghĩa biến:**
- `running = false` → Thread `startReceiving()` sẽ dừng
- `socket.close()` → Đóng UDP socket

**Vì sao viết vậy:**
- Set `running = false` trước → Thread sẽ exit khi check `while (running)`
- Đóng socket để giải phóng tài nguyên

---

#### **3.2. VideoStreamServer.java**

##### **3.2.1. VideoStreamServer(int port)**

**Input:** `port` (int) - Port để bind UDP socket

**Output:** Không (constructor)

**Ý nghĩa biến:**
- `this.port` - Port của server
- `this.socket` - DatagramSocket để nhận packets
- `this.sessions` - Map<sessionId, StreamSession> để quản lý các sessions

**Vì sao viết vậy:**
- Lưu port để dùng khi `start()`
- `sessions` dùng `ConcurrentHashMap` để thread-safe

---

##### **3.2.2. start()**

**Input:** Không

**Output:** Không (void), nhưng start server và receiving thread

**Ý nghĩa biến:**
- `socket` - DatagramSocket bind trên port
- `running = true` - Flag để keep server running
- `receiveThread` - Thread nhận packets

**Vì sao viết vậy:**
- Bind socket trên port cụ thể (dòng 42)
- Start background thread `receiveLoop()` (dòng 48-49) để không block

**Điểm dễ lỗi:**
- Port đã được sử dụng → `SocketException`
- Firewall block port → Không nhận được packets

**Cách debug:**
- Check port có đang được dùng không: `netstat -an | grep 8888`
- Check firewall rules
- Log khi start để confirm server đã bind

---

##### **3.2.3. receiveLoop()**

**Input:** Không (private method, chạy trong thread)

**Output:** Không (void), nhưng nhận packets liên tục

**Ý nghĩa biến:**
- `buffer` (byte[65507]) - Buffer để nhận packets
- `packet` (DatagramPacket) - Packet nhận được

**Vì sao viết vậy:**
- **Loop vô hạn** (dòng 62): Nhận packets liên tục
- **Buffer size 65507** (dòng 60): Max UDP packet size
- **Check `running`** (dòng 62, 70): Để có thể dừng thread
- **Catch IOException** (dòng 69-73): Nếu socket bị đóng, không crash

**Điểm dễ lỗi:**
- Socket bị đóng giữa chừng → IOException, nhưng đã handle
- Buffer overflow → Không xảy ra vì buffer = max size

**Cách debug:**
- Log số packets nhận được mỗi giây
- Log khi có IOException để biết socket có vấn đề không

---

##### **3.2.4. processPacket(DatagramPacket packet)**

**Input:** `packet` (DatagramPacket) - UDP packet nhận được

**Output:** Không (void), nhưng parse và forward packet

**Ý nghĩa biến:**
- `data` - Byte array của packet
- `length` - Độ dài thực tế của packet
- `header` (ByteBuffer) - Header 16 bytes
- `sessionId`, `userId`, `sequence`, `timestamp` - Parse từ header
- `payload` - Data sau header (16 bytes)
- `isAudio` - Check xem có phải audio packet không (prefix "AUDIO:")

**Vì sao viết vậy:**
- **Check packet size** (dòng 81-83): Packet < 16 bytes → Bỏ qua (không đủ header)
- **Parse header** (dòng 86-90): Đọc 4 ints từ 16 bytes đầu
- **Detect audio** (dòng 97-100): Check 6 bytes đầu payload có phải "AUDIO:" không
- **Tìm session** (dòng 102): Lấy StreamSession từ `sessions` map
- **Update endpoint** (dòng 105): Khi nhận packet đầu tiên, cập nhật actual IP:port (có thể khác registered)
- **Forward to others** (dòng 114): Gửi đến các clients khác trong session

**Điểm dễ lỗi:**
- Packet < 16 bytes → Bỏ qua (có thể là corrupted packet)
- `session == null` → Không forward (session chưa được register hoặc đã bị xóa)
- `userId` không match → Vẫn forward (có thể là user mới join)

**Cách debug:**
- Log `sessionId` và `userId` khi nhận packet
- Log khi `session == null` để biết session có vấn đề không
- Log `isAudio` để phân biệt audio/video packets

---

##### **3.2.5. forwardToOthers(...)**

**Input:**
- `session` (StreamSession) - Session chứa các endpoints
- `sessionId` (int) - Session ID
- `senderId` (int) - User ID của người gửi
- `sequence` (int) - Sequence number
- `timestamp` (long) - Timestamp
- `payload` (byte[]) - Frame data

**Output:** Không (void), nhưng gửi packets đến các clients khác

**Ý nghĩa biến:**
- `header` (ByteBuffer) - Tạo header mới (giữ nguyên senderId)
- `packetData` - Ghép header + payload
- `targetUserId` - User ID của client nhận
- `endpoint` (UserEndpoint) - IP:port của client nhận

**Vì sao viết vậy:**
- **Tạo header mới** (dòng 126-131): Giữ nguyên `senderId` để receiver biết ai gửi
- **Duyệt tất cả endpoints** (dòng 141): Forward đến tất cả users trong session
- **Bỏ qua sender** (dòng 146): `targetUserId != senderId` → Không gửi lại cho người gửi
- **Check actual address** (dòng 146): Chỉ gửi nếu endpoint đã có actual address (đã nhận packet từ user đó)
- **Gửi qua socket** (dòng 148-151): Tạo DatagramPacket và gửi

**Điểm dễ lỗi:**
- `endpoint.hasActualAddress() == false` → Không gửi (user chưa gửi packet nào, chưa biết actual IP)
- `socket.send()` fail → IOException (network issue), nhưng không crash
- Gửi đến endpoint sai → Packet bị mất, nhưng không có error

**Cách debug:**
- Log `targetUserId` và `endpoint` khi forward
- Log khi `hasActualAddress() == false` để biết endpoint chưa ready
- Log khi `send()` fail để biết network issue

---

##### **3.2.6. registerEndpoint(int sessionId, int userId, InetAddress address, int port)**

**Input:**
- `sessionId` (int) - Session ID
- `userId` (int) - User ID
- `address` (InetAddress) - IP address (registered, có thể là localhost)
- `port` (int) - Port

**Output:** Không (void), nhưng thêm endpoint vào session

**Ý nghĩa biến:**
- `session` - StreamSession (tạo mới nếu chưa có)
- `UserEndpoint` - Lưu registered address và port (actual sẽ được update sau)

**Vì sao viết vậy:**
- **computeIfAbsent** (dòng 164): Tạo session mới nếu chưa có (thread-safe)
- **addUserEndpoint** (dòng 165): Thêm endpoint với registered address (actual sẽ được update khi nhận packet đầu tiên)

**Điểm dễ lỗi:**
- `address` là localhost → Actual address sẽ được update khi nhận packet đầu tiên
- `userId` trùng → Overwrite endpoint cũ (có thể là reconnect)

**Cách debug:**
- Log khi register để xem endpoint có được thêm không
- Log actual address khi được update

---

##### **3.2.7. StreamSession (Inner Class)**

**Ý nghĩa:**
- Quản lý các UserEndpoint trong một session
- Mỗi session có nhiều users (call có thể có > 2 người)

**Các methods:**
- `addUserEndpoint()` - Thêm user vào session
- `removeUserEndpoint()` - Xóa user khỏi session
- `updateUserEndpoint()` - Cập nhật actual IP:port khi nhận packet đầu tiên
- `getUserEndpoints()` - Lấy map userId -> UserEndpoint

**Vì sao viết vậy:**
- **ConcurrentHashMap** (dòng 196): Thread-safe cho multi-thread access
- **Registered vs Actual address** (dòng 215-222): 
  - Registered: IP:port đăng ký ban đầu (có thể là localhost)
  - Actual: IP:port thực tế khi nhận packet (có thể khác do NAT/firewall)

---

##### **3.2.8. UserEndpoint (Inner Class)**

**Ý nghĩa:**
- Lưu thông tin endpoint của một user: registered address và actual address

**Các fields:**
- `userId` - User ID
- `registeredAddress`, `registeredPort` - IP:port đăng ký ban đầu
- `actualAddress`, `actualPort` - IP:port thực tế (update khi nhận packet)

**Vì sao viết vậy:**
- **Registered address** có thể là localhost hoặc IP không chính xác
- **Actual address** được update khi nhận packet đầu tiên từ user đó (dòng 215-222)
- **Fallback** (dòng 278): Nếu chưa có actual, dùng registered

---

### 4. ĐIỂM DỄ LỖI + CÁCH DEBUG

#### **4.1. IP Detection Issues**

**Vấn đề:**
- `getLocalLanAddress()` có thể chọn sai adapter (virtual adapter, wrong network)
- Khi chạy trên cùng máy, có thể dùng localhost thay vì LAN IP

**Cách debug:**
- Log `allFoundAddresses` (dòng 99 VideoStreamClient) để xem tất cả IPs
- Log `bestAddress` để xem IP được chọn
- Test với nhiều network adapters
- Manually set IP nếu cần

**Fix:**
- Filter virtual adapters tốt hơn
- Ưu tiên adapter có traffic
- Cho phép user chọn IP manually

---

#### **4.2. Packet Loss**

**Vấn đề:**
- UDP không đảm bảo delivery → Frames có thể bị mất
- Network congestion → Nhiều packets bị drop

**Cách debug:**
- Log `sequence` number → Xem có gaps không (frames bị mất)
- Monitor network với Wireshark
- Log số packets gửi/nhận mỗi giây

**Fix:**
- Giảm frame rate nếu packet loss cao
- Giảm frame size (compress tốt hơn)
- Implement FEC (Forward Error Correction)
- Fallback sang TCP nếu UDP quá unreliable

---

#### **4.3. Session Mismatch**

**Vấn đề:**
- Client nhận frames từ session khác (sessionId không khớp)
- Server không tìm thấy session → Không forward

**Cách debug:**
- Log `sessionId` khi gửi/nhận
- Log khi `session == null` trong `processPacket()`
- Check `registerEndpoint()` có được gọi không

**Fix:**
- Đảm bảo `sessionId` được set đúng
- Đảm bảo `registerEndpoint()` được gọi trước khi gửi frames
- Validate sessionId trước khi forward

---

#### **4.4. Endpoint Not Ready**

**Vấn đề:**
- Client A gửi frames nhưng client B chưa có actual address → Không forward được
- `hasActualAddress() == false` → Bỏ qua

**Cách debug:**
- Log khi `hasActualAddress() == false`
- Log khi `updateUserEndpoint()` được gọi
- Check registered address có đúng không

**Fix:**
- Đảm bảo client gửi ít nhất 1 packet để update actual address
- Dùng registered address nếu actual chưa có (có thể không chính xác)
- Implement heartbeat để keep endpoint alive

---

#### **4.5. Loopback (Gửi lại cho chính mình)**

**Vấn đề:**
- Server forward frame về chính người gửi → Client nhận frame từ chính mình

**Cách debug:**
- Log `senderId` và `targetUserId` khi forward
- Log khi client bỏ qua frame (dòng 202 VideoStreamClient)

**Fix:**
- Check `targetUserId != senderId` trước khi forward (đã có ở dòng 146)
- Client check `senderId != userId` trước khi xử lý (đã có ở dòng 202)

---

#### **4.6. Packet Size Too Large**

**Vấn đề:**
- Frame quá lớn (> 65507 bytes) → UDP packet quá lớn, bị split hoặc fail

**Cách debug:**
- Log packet size trước khi gửi
- Check frame size từ webcam/audio

**Fix:**
- Compress frames tốt hơn (JPEG quality thấp hơn)
- Split frame thành nhiều packets (cần thêm sequence number cho fragments)
- Giảm resolution/frame rate

---

### 5. KIẾN TRÚC: CLASS/INTERFACE VÀ QUAN HỆ

#### **5.1. Class Diagram:**

```
VideoStreamClient
├── DatagramSocket socket
├── InetAddress serverAddress
├── int serverPort
├── int sessionId
├── int userId
├── int sequenceNumber
├── boolean running
│
├── connect(String, int)
├── sendFrame(byte[])
├── startReceiving(FrameReceiver)
├── stop()
└── getLocalLanAddress() [static]

FrameReceiver [interface]
└── onFrameReceived(int, int, long, byte[])

---

VideoStreamServer
├── DatagramSocket socket
├── int port
├── boolean running
├── Map<Integer, StreamSession> sessions
│
├── start()
├── stop()
├── receiveLoop() [private]
├── processPacket(DatagramPacket) [private]
├── forwardToOthers(...) [private]
├── registerEndpoint(int, int, InetAddress, int)
└── unregisterEndpoint(int, int)

StreamSession [inner class]
├── Map<Integer, UserEndpoint> userEndpoints
│
├── addUserEndpoint(int, InetAddress, int)
├── removeUserEndpoint(int)
├── updateUserEndpoint(int, InetAddress, int)
└── getUserEndpoints()

UserEndpoint [inner class]
├── int userId
├── InetAddress registeredAddress
├── int registeredPort
├── InetAddress actualAddress
├── int actualPort
│
├── hasActualAddress()
├── setActualAddress(InetAddress, int)
├── getActualAddress()
└── getActualPort()
```

#### **5.2. Quan hệ giữa các classes:**

1. **VideoStreamClient ↔ VideoStreamServer:**
   - Client gửi frames đến Server qua UDP
   - Server forward frames đến các Clients khác
   - Communication: UDP packets với header format cố định

2. **VideoStreamServer ↔ StreamSession:**
   - Server quản lý nhiều StreamSessions (mỗi session = một call)
   - Map: `sessionId → StreamSession`

3. **StreamSession ↔ UserEndpoint:**
   - Mỗi StreamSession chứa nhiều UserEndpoints (mỗi endpoint = một user trong call)
   - Map: `userId → UserEndpoint`

4. **VideoStreamClient ↔ FrameReceiver:**
   - Client sử dụng callback interface `FrameReceiver` để xử lý frames nhận được
   - Pattern: Observer/Callback

#### **5.3. Packet Format:**

```
UDP Packet Structure:
┌─────────────────────────────────────────┐
│ Header (16 bytes)                      │
├─────────────────────────────────────────┤
│ sessionId (4 bytes) - int               │
│ userId (4 bytes) - int                  │
│ sequence (4 bytes) - int                │
│ timestamp (4 bytes) - int (unsigned)    │
├─────────────────────────────────────────┤
│ Payload (variable length)               │
│ - Video: JPEG bytes                     │
│ - Audio: "AUDIO:" + PCM bytes           │
└─────────────────────────────────────────┘
```

---

## PHẦN 2: SCRIPT THUYẾT TRÌNH 3-5 PHÚT

### **MỞ BÀI (15 giây):**

"Xin chào thầy và các bạn. Em sẽ trình bày về **module UDP streaming** trong ứng dụng AegisTalk. Module này giải quyết vấn đề **truyền video/audio real-time** giữa các clients với **độ trễ thấp** và **tốc độ cao**. Mục tiêu là sử dụng **UDP (User Datagram Protocol)** để streaming, chấp nhận mất gói tin để đảm bảo performance tốt nhất cho video call."

**[Chuyển ý:]** "Bây giờ em sẽ giải thích luồng hoạt động và các điểm nổi bật của module."

---

### **THÂN BÀI (3 phút):**

#### **Phần 1: Kiến trúc tổng quan (30 giây)**

"Module gồm 2 components chính: **VideoStreamClient** và **VideoStreamServer**.

**VideoStreamClient** đóng vai trò client: gửi frames từ webcam/microphone và nhận frames từ remote clients. **VideoStreamServer** đóng vai trò server trung gian: nhận frames từ một client và forward đến các clients khác trong cùng session.

Tại sao dùng server trung gian? Vì các clients có thể ở sau NAT/firewall, không thể kết nối trực tiếp peer-to-peer. Server đóng vai trò relay để forward packets."

**[Chuyển ý:]** "Bây giờ em sẽ giải thích luồng gửi và nhận frames."

---

#### **Phần 2: Luồng gửi frames (1 phút)**

"Luồng gửi frames hoạt động như sau:

**Bước 1:** Client A capture frame từ webcam, gọi `sendFrame()`. Method này tạo **header 16 bytes** gồm: sessionId, userId, sequence number, và timestamp. Sau đó ghép header với payload (frame data) thành UDP packet và gửi đến server.

**Bước 2:** Server nhận packet trong `receiveLoop()`, parse header để lấy sessionId và userId. Server tìm StreamSession tương ứng, cập nhật actual IP:port của user (vì có thể khác với registered address do NAT), rồi gọi `forwardToOthers()`.

**Bước 3:** `forwardToOthers()` duyệt qua tất cả UserEndpoints trong session, bỏ qua người gửi (dựa vào userId), và forward packet đến các clients khác. Điểm quan trọng là server **giữ nguyên senderId** trong header để receiver biết ai gửi frame này."

**[Chuyển ý:]** "Bây giờ em sẽ giải thích luồng nhận frames và xử lý."

---

#### **Phần 3: Luồng nhận frames (1 phút)**

"Luồng nhận frames hoạt động như sau:

**Bước 1:** Client B gọi `startReceiving()` với callback `FrameReceiver`. Method này start một background thread để nhận UDP packets liên tục.

**Bước 2:** Khi nhận được packet, thread parse header để lấy sessionId, senderId, sequence, và timestamp. Sau đó có 2 checks quan trọng:

- **Check 1:** `sessionId` phải khớp với session hiện tại. Nếu không, bỏ qua packet (có thể là packet từ session khác).

- **Check 2:** `senderId` phải khác `userId` của client. Nếu bằng, bỏ qua (tránh nhận frame từ chính mình, có thể do server forward nhầm hoặc loopback).

**Bước 3:** Nếu pass cả 2 checks, lấy payload và gọi callback `onFrameReceived()`. Callback này sẽ xử lý frame: nếu là audio (có prefix "AUDIO:") thì phát qua speakers, nếu là video thì decode và hiển thị."

**[Chuyển ý:]** "Bây giờ em sẽ giải thích các điểm nổi bật và xử lý đặc biệt."

---

#### **Phần 4: Các điểm nổi bật (30 giây)**

"Module có một số điểm nổi bật:

**Thứ nhất, phát hiện IP LAN thực tế:** Method `getLocalLanAddress()` tự động tìm IP LAN thực tế của máy, bỏ qua virtual adapters (VirtualBox, VMware, Docker) và ưu tiên WiFi/Ethernet adapters. Điều này quan trọng khi chạy trên nhiều máy khác nhau trong mạng LAN.

**Thứ hai, phân biệt users bằng userId thay vì IP:** Khi 2 clients chạy trên cùng máy (cùng IP), server và client dùng `userId` để phân biệt, không phải IP:port. Điều này cho phép test local dễ dàng.

**Thứ ba, registered vs actual address:** Server lưu 2 loại address: registered (đăng ký ban đầu, có thể là localhost) và actual (cập nhật khi nhận packet đầu tiên, là IP thực tế). Điều này xử lý trường hợp NAT/firewall."

**[Chuyển ý:]** "Bây giờ em sẽ demo module và giải thích expected output."

---

### **DEMO NÓI GÌ KHI CHẠY (30-60 giây):**

"Khi chạy module, em sẽ chỉ vào các log messages:

**Khi server start:**
```
[VideoStreamServer] UDP Server listening on port 8888
[VideoStreamServer] Áp dụng: UDP Socket - Bài UDP
```

**Khi client đăng ký endpoint:**
```
[VideoStreamServer] Endpoint registered: session=1, userId=1, 192.168.1.100:54321
[StreamSession] Added user endpoint: userId=1, registered=192.168.1.100:54321
```

**Khi nhận packet đầu tiên:**
```
[StreamSession] Updated actual endpoint for userId=1: 192.168.1.100:54321
[VideoStreamServer] AUDIO from userId=1 IP=192.168.1.100 -> forwarding to others
```

**Khi client gửi frames:**
- Sequence number tăng dần: 0, 1, 2, 3, ...
- Timestamp được set theo thời gian gửi
- Packet size thường < 65507 bytes (max UDP size)

**Khi có lỗi:**
- `WARNING: No session found for sessionId=X` → Session chưa được register
- `Error forwarding to userId=X` → Network issue khi forward

Các log này giúp debug và monitor hoạt động của module."

---

### **KẾT LUẬN (15 giây):**

"Tóm lại, module UDP streaming sử dụng **UDP protocol** để truyền video/audio real-time với **độ trễ thấp** và **tốc độ cao**. Module có kiến trúc rõ ràng với **VideoStreamClient** và **VideoStreamServer**, sử dụng **header format cố định** và **userId-based routing** để xử lý các trường hợp đặc biệt.

**Hướng cải tiến:** Có thể thêm FEC (Forward Error Correction) để giảm packet loss, implement adaptive bitrate để tự động điều chỉnh quality theo network, và thêm encryption để bảo mật frames.

Em xin cảm ơn thầy và các bạn đã lắng nghe!"

---

## TỔNG KẾT

Module UDP streaming là một phần quan trọng của ứng dụng video call, sử dụng UDP để đảm bảo real-time performance. Module có kiến trúc rõ ràng, xử lý tốt các edge cases, và có logging đầy đủ để debug.

