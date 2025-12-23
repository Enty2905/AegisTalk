# TÀI LIỆU GIẢI THÍCH VideoCallController.java VÀ LoginController.java

---

# PHẦN 1: VideoCallController.java

## 1. TỔNG QUAN (5 dòng)

**VideoCallController** là controller quản lý giao diện và logic video call trong ứng dụng AegisTalk. Module này được mở từ `MainChatController` khi user click nút video call hoặc nhận incoming call. Sử dụng **RMI** cho signaling (mời, chấp nhận, từ chối, kết thúc cuộc gọi) và **UDP** cho video/audio streaming real-time. Module được load trong window riêng (`CallView.fxml`) và quản lý toàn bộ quá trình video call từ lúc mời đến khi kết thúc.

---

## 2. FLOW RUNTIME: TỪ APP START → MODULE ĐƯỢC GỌI

### **2.1. Application Start → Login → MainChat**
```
HelloApplication.main() 
  → LoginController 
    → MainChatController (sau khi login thành công)
```

### **2.2. VideoCallController được gọi khi:**

**Trường hợp 1: User gọi (Caller)**
```
User click btnVideoCall trong MainChatController
  → handleVideoCall()
    → openVideoCallWindow(otherUserId, otherUserName, true, null)
      → Load CallView.fxml
        → VideoCallController được khởi tạo
          → controller.startCall(calleeId, calleeName)
            → RMI: clientService.inviteCall() → Tạo call session
              → startCallStatusPolling() → Polling status mỗi 1 giây
                → Khi status = "ACTIVE" → startVideoStreaming()
```

**Trường hợp 2: User nhận cuộc gọi (Callee)**
```
MainChatController.checkIncomingCalls() (polling mỗi 2 giây)
  → RMI: clientService.getPendingCalls() → Lấy danh sách incoming calls
    → Hiển thị dialog "X đang gọi..."
      → User click "Chấp nhận"
        → openVideoCallWindow(callerId, callerName, false, sessionId)
          → Load CallView.fxml
            → VideoCallController được khởi tạo
              → controller.receiveCall(sessionId, callerId, callerName, true)
                → handleJoinCall() → RMI: acceptCall()
                  → startVideoStreaming()
```

### **2.3. Video Streaming Flow**

**Gửi Video/Audio:**
```
startVideoStreaming()
  → startLocalVideo() → Mở webcam, capture frames
    → startSendingFrames() → Thread gửi video frames qua UDP
      → videoStreamClient.sendFrame(frameData)
  → startAudioStreaming()
    → startMicrophoneCapture() → Thread capture audio từ mic
      → Gửi audio với prefix "AUDIO:" qua UDP
```

**Nhận Video/Audio:**
```
VideoStreamClient.startReceiving()
  → UDP socket nhận packets
    → Callback được gọi với frameData
      → Kiểm tra prefix "AUDIO:" → Phát qua speakers
      → Không có prefix → displayRemoteVideo() → Hiển thị video
```

### **2.4. Các Luồng Dữ Liệu**

**RMI (Remote Method Invocation):**
- `clientService.inviteCall()` → Tạo call session, trả về sessionId
- `clientService.acceptCall()` → Chấp nhận cuộc gọi
- `clientService.rejectCall()` → Từ chối cuộc gọi
- `clientService.endCall()` → Kết thúc cuộc gọi
- `clientService.getCallInfo()` → Lấy thông tin call status
- `clientService.registerUdpEndpoint()` → Đăng ký UDP endpoint với server

**UDP (User Datagram Protocol):**
- `VideoStreamClient.sendFrame()` → Gửi video/audio frames
- `VideoStreamClient.startReceiving()` → Nhận video/audio frames
- Format: Video frame = raw bytes, Audio frame = "AUDIO:" + raw bytes

---

## 3. GIẢI THÍCH TỪNG METHOD

### **3.1. startCall(Long calleeId, String calleeName)**

**Method:** `public void startCall(Long calleeId, String calleeName)`

**Purpose:**
Khởi tạo cuộc gọi từ phía caller. Gửi lời mời qua RMI, mở camera preview, và bắt đầu polling call status.

**Input/Output:**
- Input: `calleeId` (Long) - ID của người được gọi, `calleeName` (String) - Tên người được gọi
- Output: Không (void), nhưng tạo call session và start polling

**Important lines:**
- Dòng 117: `isCaller = true` → Đánh dấu là người gọi
- Dòng 141: `startLocalVideo()` → Mở camera preview ngay
- Dòng 147: `clientService.inviteCall()` [RMI] → Tạo call session, nhận sessionId
- Dòng 152: `startCallStatusPolling()` → Bắt đầu polling status

**Corner cases:**
- `inviteCall()` trả về `null` → Hiển thị error, đóng window
- RMI exception → Hiển thị error, đóng window
- Camera không mở được → Fallback về placeholder video

---

### **3.2. receiveCall(Integer sessionId, Long callerId, String callerName, boolean autoAccept)**

**Method:** `public void receiveCall(Integer sessionId, Long callerId, String callerName, boolean autoAccept)`

**Purpose:**
Nhận cuộc gọi từ phía callee. Có thể tự động accept hoặc hiển thị UI để user chấp nhận.

**Input/Output:**
- Input: `sessionId` (Integer) - Call session ID, `callerId` (Long) - ID người gọi, `callerName` (String) - Tên người gọi, `autoAccept` (boolean) - Tự động accept hay không
- Output: Không (void), nhưng hiển thị UI và có thể start streaming

**Important lines:**
- Dòng 177-180: Set sessionId, callerId, callerName, `isCaller = false`
- Dòng 183-185: Nếu `autoAccept == true` → Gọi `handleJoinCall()` ngay
- Dòng 188-195: Nếu `autoAccept == false` → Hiển thị button "Chấp nhận"

**Corner cases:**
- `sessionId == null` → Không thể nhận cuộc gọi
- `autoAccept == true` → Tự động accept, không cần user click
- Camera không mở được → Vẫn có thể nhận cuộc gọi (chỉ không có video)

---

### **3.3. handleJoinCall()**

**Method:** `@FXML private void handleJoinCall()`

**Purpose:**
Xử lý khi callee chấp nhận cuộc gọi. Validate call status, gọi RMI acceptCall, và bắt đầu video streaming.

**Input/Output:**
- Input: Không (được gọi từ button click)
- Output: Không (void), nhưng accept call và start streaming

**Important lines:**
- Dòng 252: `clientService.getCallInfo()` [RMI] → Kiểm tra call còn hợp lệ không
- Dòng 260: Check status == "PENDING" → Chỉ accept nếu còn pending
- Dòng 267: Validate calleeId → Đảm bảo đúng người nhận
- Dòng 274: `clientService.acceptCall()` [RMI] → Accept call trên server
- Dòng 297: `startVideoStreaming()` → Bắt đầu video/audio streaming

**Corner cases:**
- `currentCallSessionId == null` → Hiển thị error, không làm gì
- Call status != "PENDING" → Hiển thị error "Cuộc gọi không còn hợp lệ"
- `calleeId` không match → Hiển thị error "Bạn không phải là người nhận"
- RMI acceptCall fail → Hiển thị error, không start streaming

---

### **3.4. handleLeaveCall()**

**Method:** `@FXML public void handleLeaveCall()`

**Purpose:**
Kết thúc cuộc gọi: gọi RMI endCall/rejectCall, dừng video/audio streaming, và đóng window.

**Input/Output:**
- Input: Không (được gọi từ button click hoặc window close)
- Output: Không (void), nhưng kết thúc call và đóng window

**Important lines:**
- Dòng 328-330: Nếu `isInCall == true` → `clientService.endCall()` [RMI]
- Dòng 331-333: Nếu `isCaller && !isInCall` → `clientService.endCall()` (hủy lời mời)
- Dòng 335-336: Nếu callee chưa accept → `clientService.rejectCall()` [RMI]
- Dòng 340-341: `stopCallDurationTimer()`, `stopVideoStreaming()` → Dừng tất cả
- Dòng 345: `closeCallWindow()` → Đóng window

**Corner cases:**
- `currentCallSessionId == null` → Chỉ đóng window, không gọi RMI
- RMI call fail → Vẫn đóng window (đảm bảo UI cleanup)
- Streaming không dừng được → Log error, nhưng vẫn đóng window

---

### **3.5. startVideoStreaming()**

**Method:** `private void startVideoStreaming()`

**Purpose:**
Bắt đầu video và audio streaming qua UDP. Tạo VideoStreamClient, đăng ký UDP endpoint với server, và start các threads gửi/nhận.

**Input/Output:**
- Input: Không (sử dụng `currentCallSessionId`)
- Output: Không (void), nhưng start UDP streaming

**Important lines:**
- Dòng 473-476: `startLocalVideo()` → Mở webcam nếu chưa mở
- Dòng 480: `new VideoStreamClient(sessionId)` → Tạo UDP client
- Dòng 494: `clientService.registerUdpEndpoint()` [RMI] → Đăng ký endpoint với server
- Dòng 499-525: `videoStreamClient.startReceiving()` → Callback nhận frames, phân biệt audio/video
- Dòng 531: `startSendingFrames()` → Thread gửi video frames
- Dòng 534: `startAudioStreaming()` → Start audio capture và playback

**Corner cases:**
- `currentCallSessionId == null` → Log error, return early
- Webcam không mở được → Vẫn start streaming (chỉ không có local video)
- UDP connection fail → Log error, streaming không hoạt động
- RMI registerUdpEndpoint fail → Server không biết endpoint, remote không gửi được

---

### **3.6. startLocalVideo()**

**Method:** `private void startLocalVideo()`

**Purpose:**
Mở webcam và hiển thị preview trong localVideo region. Xử lý webcam detection, set view size, và start capture thread.

**Input/Output:**
- Input: Không
- Output: Không (void), nhưng mở webcam và hiển thị preview

**Important lines:**
- Dòng 646: `Webcam.getWebcams()` → Kiểm tra có webcam không
- Dòng 654: `Webcam.getDefault()` → Lấy webcam mặc định
- Dòng 670-683: Set view size (tối đa 640x480) TRƯỚC KHI mở
- Dòng 687: `webcam.open()` → Mở webcam
- Dòng 691: `startWebcamDisplay()` → Start thread capture và hiển thị

**Corner cases:**
- Không có webcam → `startPlaceholderVideo()` → Hiển thị placeholder
- Webcam bị lock bởi app khác → Catch `WebcamLockException`, fallback placeholder
- Webcam open fail → Catch exception, fallback placeholder
- `localVideo == null` → Log error, return early

---

### **3.7. startSendingFrames()**

**Method:** `private void startSendingFrames()`

**Purpose:**
Thread gửi video frames từ webcam qua UDP. Capture frames từ webcam, convert sang byte array, và gửi qua VideoStreamClient.

**Input/Output:**
- Input: Không (sử dụng `webcam` và `videoStreamClient`)
- Output: Không (void), nhưng start background thread gửi frames

**Important lines:**
- Dòng 1236-1283: Thread loop capture và gửi frames
- Dòng 1245: `webcam.getImage()` → Capture frame từ webcam
- Dòng 1250-1255: Convert BufferedImage → byte array
- Dòng 1278: `videoStreamClient.sendFrame(frameData)` → Gửi qua UDP

**Corner cases:**
- `webcam == null` hoặc không mở → Thread kết thúc
- `videoStreamClient == null` → Không gửi được, nhưng thread vẫn chạy
- Webcam capture fail → Catch exception, tiếp tục loop
- UDP send fail → Frame bị mất, nhưng không crash

---

### **3.8. startMicrophoneCapture()**

**Method:** `private void startMicrophoneCapture()**

**Purpose:**
Thread capture audio từ microphone và gửi qua UDP. Sử dụng Java Sound API (TargetDataLine) để capture, thêm prefix "AUDIO:", và gửi qua cùng UDP channel với video.

**Input/Output:**
- Input: Không (sử dụng `audioFormat` và `videoStreamClient`)
- Output: Không (void), nhưng start background thread capture audio

**Important lines:**
- Dòng 1358: `AudioSystem.isLineSupported()` → Kiểm tra mic có sẵn không
- Dòng 1370-1372: `microphone.open()`, `microphone.start()` → Mở và start mic
- Dòng 1377: Buffer 640 bytes (20ms audio ở 16kHz, 16-bit, mono)
- Dòng 1398-1405: Thêm prefix "AUDIO:" vào audio packet
- Dòng 1408: `videoStreamClient.sendFrame(audioPacket)` → Gửi qua UDP

**Corner cases:**
- Mic không supported → Disable mute button, hiển thị warning icon
- `LineUnavailableException` → Mic bị chiếm, disable button
- `audioRunning == false` → Thread kết thúc
- Mic bị close giữa chừng → Catch exception, thread kết thúc

---

### **3.9. startAudioPlayback()**

**Method:** `private void startAudioPlayback()`

**Purpose:**
Thread nhận audio từ UDP và phát qua speakers. Sử dụng Java Sound API (SourceDataLine) để playback audio.

**Input/Output:**
- Input: Không (sử dụng `videoStreamClient` và callback)
- Output: Không (void), nhưng start background thread playback

**Important lines:**
- Dòng 1489-1550: Thread nhận và phát audio
- Dòng 1502: `speakers.open(audioFormat)` → Mở speakers
- Dòng 1503: `speakers.start()` → Start playback
- Dòng 1539: `playRemoteAudio(audioData)` → Phát audio data

**Corner cases:**
- Speakers không available → Log error, audio không phát được
- `audioRunning == false` → Thread kết thúc
- Audio data null/empty → Skip, không phát

---

### **3.10. displayRemoteVideo(byte[] frameData)**

**Method:** `private void displayRemoteVideo(byte[] frameData)`

**Purpose:**
Hiển thị remote video frame nhận được từ UDP. Decode byte array thành BufferedImage, convert sang JavaFX Image, và hiển thị trong remoteVideo region.

**Input/Output:**
- Input: `frameData` (byte[]) - Video frame data từ UDP
- Output: Không (void), nhưng update UI với remote video

**Important lines:**
- Dòng 1054-1153: Decode và hiển thị remote video
- Dòng 1080: `ImageIO.read()` → Decode JPEG bytes thành BufferedImage
- Dòng 1087: Update `lastRemoteFrameTime` → Track camera status
- Dòng 1130: `new ImageView(image)` → Tạo ImageView để hiển thị
- Dòng 1145: Update UI trên JavaFX thread

**Corner cases:**
- Decode fail → Log error, không hiển thị frame
- `frameData == null` hoặc empty → Return early
- Image decode exception → Frame bị bỏ qua, không crash

---

### **3.11. startCallStatusPolling()**

**Method:** `private void startCallStatusPolling()`

**Purpose:**
Background thread polling call status mỗi 1 giây. Kiểm tra khi call được accept (ACTIVE) để start streaming, và khi call kết thúc (ENDED) để cleanup.

**Input/Output:**
- Input: Không (sử dụng `currentCallSessionId`)
- Output: Không (void), nhưng start background thread

**Important lines:**
- Dòng 1610-1710: Polling loop
- Dòng 1634: `clientService.getCallInfo()` [RMI] → Lấy call status
- Dòng 1650: Check status == "ACTIVE" → Start streaming khi được accept
- Dòng 1685: Check status == "ENDED" → Stop streaming và đóng window

**Corner cases:**
- `currentCallSessionId == null` → Log error, không start polling
- `callInfo == null` → Call đã bị hủy, đóng window
- RMI call fail → Log error, tiếp tục polling
- Status không hợp lệ → Tiếp tục polling, không làm gì

---

### **3.12. handleToggleMute()**

**Method:** `@FXML private void handleToggleMute()`

**Purpose:**
Toggle microphone mute/unmute. Khi mute, dừng microphone capture. Khi unmute, restart microphone capture.

**Input/Output:**
- Input: Không (được gọi từ button click)
- Output: Không (void), nhưng toggle mic state

**Important lines:**
- Dòng 376: `isMuted = !isMuted` → Toggle state
- Dòng 379: Update button icon (🎤/🔇)
- Dòng 387-395: Hiển thị/ẩn mute indicator
- Dòng 397-400: Nếu mute → `stopMicrophoneCapture()`
- Dòng 401-403: Nếu unmute → `restartMicrophoneCapture()`

**Corner cases:**
- Mic không available → Button disabled, không toggle được
- `stopMicrophoneCapture()` fail → Log error, nhưng vẫn update UI
- `restartMicrophoneCapture()` fail → Mic không hoạt động, nhưng UI vẫn update

---

### **3.13. handleToggleCamera()**

**Method:** `@FXML private void handleToggleCamera()`

**Purpose:**
Toggle camera on/off. Khi tắt, dừng webcam capture và hiển thị placeholder. Khi bật, mở lại webcam.

**Input/Output:**
- Input: Không (được gọi từ button click)
- Output: Không (void), nhưng toggle camera state

**Important lines:**
- Dòng 405-460: Toggle camera logic
- Dòng 407: `isCameraOn = !isCameraOn` → Toggle state
- Dòng 410: Update button icon (📷/🚫)
- Dòng 420-425: Nếu tắt → `stopWebcamCapture()`, `startPlaceholderVideo()`
- Dòng 426-456: Nếu bật → `reopenWebcam()`, `startWebcamDisplay()`

**Corner cases:**
- Webcam không available → Button disabled, không toggle được
- `reopenWebcam()` fail → Camera không mở được, fallback placeholder
- Webcam bị lock → Hiển thị error, camera vẫn tắt

---

---

# PHẦN 2: LoginController.java

## 1. TỔNG QUAN (5 dòng)

**LoginController** là controller đầu tiên của ứng dụng AegisTalk, được load khi app start trong `HelloApplication`. Module này quản lý giao diện đăng nhập và đăng ký, sử dụng **RMI** để gọi `AuthService.login()` và `AuthService.register()` từ xa. Sau khi đăng nhập/đăng ký thành công, module chuyển sang `MainChatView` và lưu thông tin user vào `Session`. Module được sử dụng trong `LoginView.fxml` và là entry point của ứng dụng.

---

## 2. FLOW RUNTIME: TỪ APP START → MODULE ĐƯỢC GỌI

### **2.1. Application Start**
```
HelloApplication.main()
  → HelloApplication.start(Stage stage)
    → Load LoginView.fxml
      → LoginController được khởi tạo
        → initialize() được gọi tự động
```

### **2.2. LoginController.initialize() được gọi tự động**
```
1. Kiểm tra FXML controls không null
2. Khởi tạo AegisTalkClientService (RMI connection)
3. Setup event handlers:
   - btnLogin → doLogin()
   - btnRegister → doRegister()
   - toggleRegister → Toggle giữa login/register mode
   - txtPassword Enter key → doLogin() hoặc doRegister()
```

### **2.3. User Login Flow**
```
User nhập username/password → Click btnLogin hoặc Enter
  → doLogin()
    → Validate input (không rỗng)
      → clientService.login(username, password) [RMI call]
        → AuthServiceImpl.login() trên server
          → UserDao.findByUsername() → Tìm user trong DB
            → Verify password hash
              → Trả về User object
                → Session.setUser() → Lưu vào Session
                  → Load MainChatView.fxml
                    → MainChatController được khởi tạo
```

### **2.4. User Register Flow**
```
User toggle sang register mode → Nhập username/password/displayName
  → Click btnRegister hoặc Enter
    → doRegister()
      → Validate input
        → clientService.register(username, password, displayName) [RMI call]
          → AuthServiceImpl.register() trên server
            → UserDao.findByUsername() → Check username tồn tại
              → Hash password (SHA-256)
                → UserDao.create() → Tạo user mới trong DB
                  → Trả về User object
                    → Session.setUser() → Lưu vào Session
                      → Load MainChatView.fxml
                        → MainChatController được khởi tạo
```

### **2.5. Các Luồng Dữ Liệu**

**RMI (Remote Method Invocation):**
- `clientService.login()` → Xác thực user, trả về User object
- `clientService.register()` → Tạo user mới, trả về User object
- User object chứa: id, username, displayName, avatarPath

**Session Management:**
- `Session.setUser()` → Lưu user info vào static Session
- `Session.getUserId()`, `Session.getDisplayName()`, `Session.getAvatarPath()` → Lấy thông tin user

---

## 3. GIẢI THÍCH TỪNG METHOD

### **3.1. initialize()**

**Method:** `@FXML private void initialize()`

**Purpose:**
Khởi tạo controller khi FXML được load. Setup RMI connection, bind event handlers, và configure UI cho login/register toggle.

**Input/Output:**
- Input: Không (được gọi tự động bởi JavaFX)
- Output: Không (void)

**Important lines:**
- Dòng 38-41: Kiểm tra FXML controls không null
- Dòng 49: `clientService = new AegisTalkClientService()` → Khởi tạo RMI client
- Dòng 56-61: Bind button actions
- Dòng 65-103: Toggle register mode → Show/hide display name field và buttons

**Corner cases:**
- FXML controls null → Log warning, một số tính năng không hoạt động
- RMI connection fail → Hiển thị error "Lỗi kết nối", nhưng UI vẫn hiển thị
- `displayNameBox == null` → Fallback dùng `txtDisplayName` trực tiếp

---

### **3.2. doLogin()**

**Method:** `private void doLogin()`

**Purpose:**
Xử lý đăng nhập: validate input, gọi RMI login, lưu vào Session, và chuyển sang MainChatView.

**Input/Output:**
- Input: Lấy từ `txtUsername.getText()` và `txtPassword.getText()`
- Output: Không (void), nhưng chuyển scene nếu thành công

**Important lines:**
- Dòng 136-139: Check `clientService != null`
- Dòng 141-147: Validate input không rỗng
- Dòng 153: `clientService.login()` [RMI] → Xác thực và trả về User
- Dòng 156: `Session.setUser()` → Lưu user info
- Dòng 162-168: Load MainChatView.fxml và chuyển scene

**Corner cases:**
- `clientService == null` → Hiển thị error "Chưa kết nối đến server"
- Username/password rỗng → Hiển thị warning "Vui lòng nhập đủ thông tin"
- RMI login trả về `null` → Hiển thị error "Sai tên đăng nhập hoặc mật khẩu"
- RMI exception → Hiển thị error "Lỗi kết nối"
- Load MainChatView.fxml fail → Hiển thị error, không chuyển scene

---

### **3.3. doRegister()**

**Method:** `private void doRegister()`

**Purpose:**
Xử lý đăng ký: validate input, gọi RMI register, lưu vào Session, và chuyển sang MainChatView.

**Input/Output:**
- Input: Lấy từ `txtUsername.getText()`, `txtPassword.getText()`, và `txtDisplayName.getText()`
- Output: Không (void), nhưng chuyển scene nếu thành công

**Important lines:**
- Dòng 184-187: Check `clientService != null`
- Dòng 189-196: Validate username/password không rỗng
- Dòng 198-200: Display name mặc định = username nếu rỗng
- Dòng 206: `clientService.register()` [RMI] → Tạo user mới
- Dòng 209: `Session.setUser()` → Lưu user info
- Dòng 215-221: Load MainChatView.fxml và chuyển scene

**Corner cases:**
- `clientService == null` → Hiển thị error "Chưa kết nối đến server"
- Username/password rỗng → Hiển thị warning "Vui lòng nhập đủ thông tin"
- Display name rỗng → Dùng username làm display name
- RMI register trả về `null` → Hiển thị error "Đăng ký thất bại (username đã tồn tại)"
- RMI exception → Hiển thị error "Lỗi kết nối"
- Load MainChatView.fxml fail → Hiển thị error, không chuyển scene

---

### **3.4. showStatus(String message, boolean isSuccess)**

**Method:** `private void showStatus(String message, boolean isSuccess)`

**Purpose:**
Hiển thị status message với màu sắc phù hợp (xanh cho success, đỏ cho error).

**Input/Output:**
- Input: `message` (String) - Message cần hiển thị, `isSuccess` (boolean) - Success hay error
- Output: Không (void), nhưng update UI

**Important lines:**
- Dòng 120-122: Set text và visible cho label
- Dòng 125: Remove old style classes
- Dòng 127-128: Success → Màu xanh (#22c55e)
- Dòng 130: Error → Màu đỏ (#ef4444)

**Corner cases:**
- `lblStatus == null` → Không làm gì (tránh NPE)
- Message rỗng → Vẫn hiển thị (có thể là empty string)

---

---

## 4. 10 CÂU HỎI THẦY CÓ THỂ HỎI + CÂU TRẢ LỜI

### **VideoCallController:**

### **Câu 1: Tại sao video call sử dụng cả RMI và UDP?**

**Trả lời:** 
- **RMI**: Dùng cho signaling (mời, chấp nhận, từ chối, kết thúc) vì cần reliability và state management trên server
- **UDP**: Dùng cho video/audio streaming vì cần real-time, low latency, và không cần reliability (mất frame không sao)

Hai giao thức bổ sung nhau: RMI cho control, UDP cho data streaming.

---

### **Câu 2: Tại sao phải polling call status thay vì dùng callback?**

**Trả lời:** 
Polling (dòng 1610) được dùng vì RMI không hỗ trợ push notification tốt. Server không thể tự động notify client khi status thay đổi. Polling mỗi 1 giây là trade-off giữa responsiveness và server load. Có thể cải thiện bằng WebSocket hoặc long polling trong tương lai.

---

### **Câu 3: Làm sao phân biệt audio và video packets trong UDP?**

**Trả lời:** 
Audio packets có prefix "AUDIO:" (6 bytes) ở đầu (dòng 1398-1405). Khi nhận frame (dòng 503-506), check 6 bytes đầu. Nếu là "AUDIO:" thì phát qua speakers, nếu không thì hiển thị như video frame. Cách này đơn giản và hiệu quả.

---

### **Câu 4: Tại sao phải đăng ký UDP endpoint với server?**

**Trả lời:** 
Server cần biết IP:port của mỗi client để forward frames đúng. Khi client A gọi client B, server cần biết endpoint của B để forward frames từ A. `registerUdpEndpoint()` (dòng 494) gửi local IP:port lên server qua RMI.

---

### **Câu 5: Optimistic update trong video call là gì?**

**Trả lời:** 
Khi caller bắt đầu gọi (dòng 141), camera preview được mở ngay lập tức trước khi callee accept. Đây là optimistic update - hiển thị local video ngay để tăng UX, không đợi call được accept. Tương tự như optimistic update trong chat messages.

---

### **Câu 6: Xử lý khi webcam không available như thế nào?**

**Trả lời:** 
Trong `startLocalVideo()` (dòng 648-650), nếu không có webcam thì gọi `startPlaceholderVideo()` - hiển thị placeholder với icon và tên user. Video call vẫn có thể tiếp tục (chỉ không có local video), audio vẫn hoạt động bình thường.

---

### **Câu 7: Tại sao audio và video dùng chung UDP channel?**

**Trả lời:** 
Đơn giản hóa implementation - chỉ cần một UDP socket cho cả audio và video. Phân biệt bằng prefix "AUDIO:". Cách này đơn giản hơn việc dùng 2 socket riêng, và vẫn đảm bảo real-time cho cả hai.

---

### **LoginController:**

### **Câu 8: Tại sao LoginController dùng RMI thay vì HTTP REST API?**

**Trả lời:** 
RMI phù hợp cho internal service calls trong Java application. Có type safety (trả về User object), không cần serialize/deserialize JSON, và tích hợp tốt với Java. HTTP REST phù hợp hơn cho public APIs hoặc cross-platform, nhưng RMI đơn giản hơn cho Java-to-Java communication.

---

### **Câu 9: Session được quản lý như thế nào?**

**Trả lời:** 
`Session` là class với static fields lưu user info (id, displayName, avatarPath). Khi login thành công (dòng 156), `Session.setUser()` được gọi để lưu. Các module khác dùng `Session.getUserId()`, `Session.getDisplayName()` để lấy thông tin. Session tồn tại trong suốt app lifecycle, chỉ clear khi logout.

---

### **Câu 10: Toggle register mode hoạt động như thế nào?**

**Trả lời:** 
`toggleRegister` (dòng 65-103) là ToggleButton. Khi selected, hiển thị `displayNameBox` và `btnRegister`, ẩn `btnLogin`. Khi unselected, ngược lại. Listener trên `selectedProperty()` update UI động. Enter key trong password field (dòng 108-114) gọi `doLogin()` hoặc `doRegister()` tùy mode.

---

## 5. SCRIPT THUYẾT TRÌNH 4 PHÚT (CÓ CẢ PHẦN DEMO)

### **PHẦN 1: GIỚI THIỆU (30 giây)**

"Xin chào thầy và các bạn. Em sẽ trình bày về **LoginController** và **VideoCallController** - hai module quan trọng trong ứng dụng AegisTalk. **LoginController** là entry point của app, quản lý đăng nhập/đăng ký qua RMI. **VideoCallController** quản lý video call với signaling qua RMI và streaming qua UDP."

**[Demo: Mở app, hiển thị LoginView]**

---

### **PHẦN 2: LoginController (1 phút)**

"**LoginController** được load đầu tiên khi app start. Module này có 2 chức năng chính:

**Thứ nhất, đăng nhập** - User nhập username/password, gọi `clientService.login()` qua RMI. Server xác thực và trả về User object. Nếu thành công, lưu vào Session và chuyển sang MainChatView.

**Thứ hai, đăng ký** - User toggle sang register mode, nhập thông tin, gọi `clientService.register()` qua RMI. Server tạo user mới trong database và trả về User object.

Module sử dụng RMI để đảm bảo type safety và tích hợp tốt với Java."

**[Demo: Đăng nhập, đăng ký, toggle mode]**

---

### **PHẦN 3: VideoCallController - Signaling (1 phút)**

"**VideoCallController** quản lý video call với 2 giai đoạn:

**Giai đoạn 1: Signaling qua RMI** - Khi caller gọi, `startCall()` gửi lời mời qua `clientService.inviteCall()`. Server tạo call session và trả về sessionId. Callee nhận qua `receiveCall()`, có thể accept qua `handleJoinCall()` gọi `clientService.acceptCall()`. Status được polling mỗi 1 giây để biết khi call được accept.

**Giai đoạn 2: Streaming qua UDP** - Khi status = ACTIVE, `startVideoStreaming()` được gọi. Client đăng ký UDP endpoint với server, rồi bắt đầu gửi/nhận video và audio frames qua UDP."

**[Demo: Mở code, chỉ vào RMI calls và UDP setup]**

---

### **PHẦN 4: VideoCallController - Video/Audio Streaming (1 phút)**

"Video và audio streaming hoạt động như sau:

**Video:** Webcam được mở qua `startLocalVideo()`, capture frames mỗi 33ms (30fps). Frames được convert sang JPEG bytes và gửi qua UDP. Remote frames được nhận, decode, và hiển thị trong remoteVideo region.

**Audio:** Microphone capture qua `startMicrophoneCapture()`, audio được gửi với prefix 'AUDIO:' để phân biệt. Audio playback qua `startAudioPlayback()` phát audio nhận được qua speakers.

Cả video và audio dùng chung UDP channel, phân biệt bằng prefix."

**[Demo: Video call giữa 2 client, chỉ vào video/audio streaming]**

---

### **PHẦN 5: KẾT LUẬN (30 giây)**

"Tóm lại, **LoginController** là entry point sử dụng RMI cho authentication, còn **VideoCallController** kết hợp RMI cho signaling và UDP cho real-time streaming. Hai module này thể hiện việc chọn đúng giao thức cho đúng mục đích: RMI cho reliability, UDP cho real-time performance.

Em xin cảm ơn thầy và các bạn đã lắng nghe!"

---

## TỔNG KẾT

**LoginController** và **VideoCallController** là hai module quan trọng, sử dụng RMI và UDP một cách hiệu quả để đảm bảo authentication và real-time video communication.

