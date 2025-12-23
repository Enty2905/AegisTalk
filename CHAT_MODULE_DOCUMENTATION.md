# TÀI LIỆU GIẢI THÍCH MODULE CHAT

## 1. TỔNG QUAN (5 dòng)

Module **chat** là hệ thống giao tiếp real-time sử dụng TCP socket, bao gồm ChatServer (lắng nghe trên port 5555) và ChatClient (kết nối đến server). Module này xử lý việc gửi/nhận tin nhắn dưới dạng JSON (ChatMessage) giữa các client thông qua cơ chế broadcast. ChatServer được khởi động trong AegisTalkServerMain như một daemon thread, còn ChatClient được sử dụng trong MainChatController để kết nối và nhận tin nhắn real-time sau khi user đăng nhập thành công. Module này là thành phần cốt lõi cho tính năng chat của ứng dụng AegisTalk.

---

## 2. FLOW RUNTIME: TỪ APP START → MODULE ĐƯỢC GỌI

### **2.1. Server Side - Khi AegisTalkServerMain khởi động:**

```
AegisTalkServerMain.main()
  → Tạo daemon thread "TCP-Chat-Server"
    → new ChatServer(5555)
      → ChatServer.start()
        → ServerSocket.accept() [Lắng nghe kết nối mới]
          → Mỗi client kết nối → tạo ClientHandler thread riêng
            → ClientHandler.run() [Đọc tin nhắn từ client]
              → Parse JSON → ChatMessage
                → broadcast() [Gửi đến tất cả client khác]
```

**Input:** Không có input ban đầu, server chỉ lắng nghe trên port 5555  
**Output:** Server sẵn sàng nhận kết nối từ các client

### **2.2. Client Side - Khi MainChatController được khởi tạo:**

```
User đăng nhập thành công
  → Load MainChatView.fxml
    → MainChatController.initialize()
      → connectChat()
        → new ChatClient("localhost", 5555, this::onIncomingMessage)
          → ChatClient.connect()
            → new Socket("localhost", 5555) [Kết nối TCP]
              → Tạo BufferedReader/BufferedWriter
                → Tạo daemon thread "ChatClient-Reader"
                  → readLoop() [Đọc tin nhắn từ server liên tục]
                    → Parse JSON → ChatMessage
                      → onMessage.accept(msg) [Callback đến MainChatController]
                        → MainChatController.onIncomingMessage()
                          → Hiển thị tin nhắn lên UI
```

**Input:** Host "localhost", port 5555, callback function `onIncomingMessage`  
**Output:** Kết nối TCP đã thiết lập, sẵn sàng gửi/nhận tin nhắn

### **2.3. Flow Gửi Tin Nhắn:**

```
User gõ tin nhắn trong UI
  → MainChatController.sendMessage()
    → Tạo ChatMessage object
      → chatClient.send(msg)
        → ObjectMapper.writeValueAsString() [Serialize thành JSON]
          → out.write(json + "\n") [Gửi qua TCP socket]
            → Server nhận được
              → ClientHandler.run() parse JSON
                → broadcast(msg, this) [Gửi đến tất cả client khác]
                  → Mỗi client nhận được
                    → readLoop() parse JSON
                      → onMessage callback
                        → MainChatController.onIncomingMessage()
                          → Hiển thị tin nhắn trên UI của các client khác
```

**Input:** ChatMessage object từ UI  
**Output:** Tin nhắn được broadcast đến tất cả client đang kết nối (trừ người gửi)

### **2.4. Flow Nhận Tin Nhắn:**

```
Server nhận tin nhắn từ client A
  → broadcast() gửi đến client B, C, D...
    → Client B: readLoop() đọc được dòng JSON
      → Parse thành ChatMessage
        → onMessage.accept(msg)
          → MainChatController.onIncomingMessage(msg)
            → Kiểm tra room, conversation
              → Thêm vào danh sách tin nhắn
                → Cập nhật UI (nếu đang mở conversation đó)
```

**Input:** JSON string từ server qua TCP socket  
**Output:** ChatMessage object được xử lý và hiển thị trên UI

---

## 3. GIẢI THÍCH TỪNG METHOD

### **3.1. ChatServerMain**

#### **Method:** `main(String[] args)`

**Purpose:** Entry point để chạy ChatServer độc lập (standalone), không cần AegisTalkServerMain.

**Input/Output:**
- **Input:** `args` - không sử dụng
- **Output:** Không có return value, tạo và khởi động ChatServer trên port 5555

**Important lines:**
- Line 7: `new ChatServer(5555)` - Tạo server instance với port 5555
- Line 8: `server.start()` - Bắt đầu lắng nghe kết nối

**Corner cases:**
- Nếu port 5555 đã bị chiếm → IOException, in stack trace
- Nếu có lỗi khác → catch Exception và in stack trace

---

### **3.2. ChatServer**

#### **Method:** `ChatServer(int port)`

**Purpose:** Constructor khởi tạo ChatServer với port chỉ định.

**Input/Output:**
- **Input:** `port` - số port để lắng nghe (thường là 5555)
- **Output:** ChatServer instance đã được khởi tạo

**Important lines:**
- Line 25-27: Lưu port vào field, khởi tạo ObjectMapper và CopyOnWriteArraySet cho clients

**Corner cases:**
- Port < 0 hoặc > 65535 → sẽ lỗi khi gọi start()
- Không có validation ở đây, lỗi sẽ xảy ra ở start()

---

#### **Method:** `start() throws IOException`

**Purpose:** Khởi động server, lắng nghe kết nối mới và tạo thread riêng cho mỗi client.

**Input/Output:**
- **Input:** Không có
- **Output:** Không có return value, method chạy vô hạn (blocking)

**Important lines:**
- Line 30: `ServerSocket server = new ServerSocket(port)` - Tạo server socket
- Line 34: `Socket socket = server.accept()` - Chờ kết nối mới (blocking)
- Line 37: `new ClientHandler(socket)` - Tạo handler cho client mới
- Line 38: `clients.add(handler)` - Thêm vào danh sách clients
- Line 40-41: Tạo và start thread riêng cho mỗi client

**Corner cases:**
- Port đã bị chiếm → IOException khi tạo ServerSocket
- ServerSocket bị đóng → vòng lặp while(true) sẽ dừng
- Nhiều client kết nối đồng thời → mỗi client có thread riêng, thread-safe nhờ CopyOnWriteArraySet

---

#### **Method:** `broadcast(ChatMessage msg, ClientHandler from)`

**Purpose:** Gửi tin nhắn đến tất cả client đang kết nối, trừ client gửi tin nhắn.

**Input/Output:**
- **Input:** 
  - `msg` - ChatMessage cần broadcast
  - `from` - ClientHandler của người gửi (để không gửi lại cho họ)
- **Output:** Không có return value

**Important lines:**
- Line 49: `mapper.writeValueAsString(msg)` - Serialize ChatMessage thành JSON
- Line 52-58: Duyệt qua tất cả clients, gửi đến client khác (không phải `from`)
- Line 55: `c.send(json)` - Gửi JSON đến từng client

**Corner cases:**
- Serialize lỗi → catch Exception, in lỗi nhưng không crash server
- Client đã disconnect nhưng chưa remove khỏi set → send() sẽ throw IOException, được catch trong ClientHandler.send()
- `from` là null → sẽ gửi đến tất cả clients (kể cả người gửi)
- Không có client nào kết nối → vòng lặp không chạy, sentCount = 0

---

#### **Method:** `ClientHandler.ClientHandler(Socket socket) throws IOException`

**Purpose:** Constructor của ClientHandler, khởi tạo BufferedReader và BufferedWriter cho socket.

**Input/Output:**
- **Input:** `socket` - Socket đã được accept từ server
- **Output:** ClientHandler instance đã được khởi tạo với input/output streams

**Important lines:**
- Line 75: `new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"))` - Tạo reader với encoding UTF-8
- Line 76: `new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"))` - Tạo writer với encoding UTF-8

**Corner cases:**
- Socket đã bị đóng → IOException khi tạo streams
- Encoding không hỗ trợ → sẽ fallback về default encoding

---

#### **Method:** `ClientHandler.run()`

**Purpose:** Thread chạy cho mỗi client, đọc tin nhắn liên tục từ client và broadcast đến các client khác.

**Input/Output:**
- **Input:** Không có (implement Runnable)
- **Output:** Không có return value, chạy cho đến khi client disconnect

**Important lines:**
- Line 83: `while ((line = in.readLine()) != null)` - Đọc từng dòng JSON từ client
- Line 86: `mapper.readValue(line, ChatMessage.class)` - Parse JSON thành ChatMessage
- Line 89: `broadcast(msg, this)` - Broadcast tin nhắn đến các client khác
- Line 100: `clients.remove(this)` - Remove client khỏi danh sách khi disconnect
- Line 102: `socket.close()` - Đóng socket

**Corner cases:**
- Client disconnect đột ngột → `in.readLine()` trả về null hoặc throw IOException
- JSON không hợp lệ → catch Exception, in lỗi nhưng tiếp tục đọc dòng tiếp theo
- Client gửi tin nhắn rỗng → vẫn parse được nhưng có thể không có nội dung
- Nhiều tin nhắn gửi liên tiếp → mỗi tin nhắn được xử lý tuần tự trong thread này

---

#### **Method:** `ClientHandler.send(String jsonLine)`

**Purpose:** Gửi một dòng JSON đến client qua socket.

**Input/Output:**
- **Input:** `jsonLine` - Chuỗi JSON cần gửi
- **Output:** Không có return value

**Important lines:**
- Line 108: `out.write(jsonLine)` - Ghi JSON vào output stream
- Line 109: `out.write("\n")` - Ghi newline để đánh dấu kết thúc message
- Line 110: `out.flush()` - Đảm bảo dữ liệu được gửi ngay lập tức

**Corner cases:**
- Socket đã bị đóng → IOException, in lỗi nhưng không crash
- Client đã disconnect → IOException khi write, được catch và in lỗi
- `jsonLine` là null → NullPointerException (không được handle)
- Buffer đầy → flush() sẽ block cho đến khi có chỗ trống

---

### **3.3. ChatClient**

#### **Method:** `ChatClient(String host, int port, Consumer<ChatMessage> onMessage)`

**Purpose:** Constructor khởi tạo ChatClient với thông tin server và callback function.

**Input/Output:**
- **Input:**
  - `host` - Địa chỉ server (thường là "localhost")
  - `port` - Port của server (thường là 5555)
  - `onMessage` - Callback function được gọi khi nhận được tin nhắn
- **Output:** ChatClient instance đã được khởi tạo (chưa kết nối)

**Important lines:**
- Line 27-31: Lưu các tham số vào fields, khởi tạo ObjectMapper

**Corner cases:**
- `onMessage` là null → vẫn tạo được client nhưng sẽ không xử lý tin nhắn nhận được (có check null trong readLoop)
- `host` là null → sẽ lỗi khi gọi connect()
- `port` không hợp lệ → sẽ lỗi khi gọi connect()

---

#### **Method:** `connect() throws IOException`

**Purpose:** Kết nối đến ChatServer và bắt đầu thread đọc tin nhắn.

**Input/Output:**
- **Input:** Không có
- **Output:** Không có return value, kết nối đã được thiết lập

**Important lines:**
- Line 34: `socket = new Socket(host, port)` - Tạo TCP connection đến server
- Line 35-36: Tạo BufferedReader và BufferedWriter với encoding UTF-8
- Line 38-40: Tạo và start daemon thread để đọc tin nhắn liên tục

**Corner cases:**
- Server chưa chạy → ConnectException khi tạo Socket
- Host không tồn tại → UnknownHostException
- Port không đúng → ConnectException
- Đã kết nối rồi mà gọi lại connect() → tạo socket mới, socket cũ bị leak (nên check socket != null trước)
- Thread đọc tin nhắn là daemon → sẽ tự động kết thúc khi main thread kết thúc

---

#### **Method:** `readLoop()`

**Purpose:** Vòng lặp đọc tin nhắn từ server liên tục, parse JSON và gọi callback.

**Input/Output:**
- **Input:** Không có
- **Output:** Không có return value, chạy cho đến khi socket đóng

**Important lines:**
- Line 48: `while ((line = in.readLine()) != null)` - Đọc từng dòng JSON
- Line 50: `mapper.readValue(line, ChatMessage.class)` - Parse JSON thành ChatMessage
- Line 57-60: Gọi callback `onMessage.accept(msg)` nếu callback không null
- Line 64-67: Catch lỗi parse JSON, in lỗi nhưng tiếp tục đọc dòng tiếp theo

**Corner cases:**
- Server đóng connection → `in.readLine()` trả về null, vòng lặp kết thúc
- JSON không hợp lệ → catch Exception, in lỗi và raw line để debug, tiếp tục đọc
- `onMessage` là null → in warning nhưng không crash
- Socket bị đóng đột ngột → IOException, in lỗi và kết thúc vòng lặp
- Server gửi tin nhắn rất nhanh → mỗi tin nhắn được xử lý tuần tự

---

#### **Method:** `send(ChatMessage msg) throws IOException`

**Purpose:** Gửi ChatMessage đến server dưới dạng JSON.

**Input/Output:**
- **Input:** `msg` - ChatMessage cần gửi
- **Output:** Không có return value

**Important lines:**
- Line 77: Check `out == null` → throw IllegalStateException nếu chưa connect
- Line 78: `mapper.writeValueAsString(msg)` - Serialize ChatMessage thành JSON
- Line 79-81: Ghi JSON + newline và flush

**Corner cases:**
- Chưa gọi connect() → `out` là null, throw IllegalStateException
- Socket đã bị đóng → IOException khi write
- Serialize lỗi → IOException từ ObjectMapper
- `msg` là null → NullPointerException từ ObjectMapper (không được handle)

---

#### **Method:** `close() throws IOException`

**Purpose:** Đóng kết nối socket với server.

**Input/Output:**
- **Input:** Không có
- **Output:** Không có return value

**Important lines:**
- Line 86: `if (socket != null) socket.close()` - Đóng socket nếu đã kết nối

**Corner cases:**
- Socket đã bị đóng → close() không làm gì (idempotent)
- Socket là null → không làm gì
- Đóng socket sẽ tự động đóng BufferedReader/BufferedWriter

---

### **3.4. ChatClientConsoleMain**

#### **Method:** `main(String[] args) throws Exception`

**Purpose:** Entry point cho console-based test client, cho phép test chat system từ command line.

**Input/Output:**
- **Input:** `args` - không sử dụng
- **Output:** Không có return value

**Important lines:**
- Line 16-20: Đọc tên user từ console, default là "ConsoleUser"
- Line 22-26: Đọc room từ console, default là "general"
- Line 29-33: Tạo ChatClient với callback in ra console
- Line 35: `client.connect()` - Kết nối đến server
- Line 39-60: Vòng lặp đọc tin nhắn từ console và gửi đi
- Line 41-42: `/quit` để thoát
- Line 51-58: Tạo ChatMessage và gửi qua client

**Corner cases:**
- User nhập rỗng → dùng giá trị default
- User nhập `/quit` → break vòng lặp, đóng client
- Server không chạy → IOException khi connect, in stack trace
- Console input bị đóng → `readLine()` trả về null, vòng lặp kết thúc

---

## 4. 10 CÂU HỎI THẦY CÓ THỂ HỎI + CÂU TRẢ LỜI NGẮN

### **Câu 1: Tại sao dùng CopyOnWriteArraySet cho clients thay vì HashSet thông thường?**

**Trả lời:** CopyOnWriteArraySet là thread-safe, cho phép đọc và ghi đồng thời mà không cần synchronized. Khi broadcast, có thể có client mới kết nối/ngắt kết nối đồng thời, CopyOnWriteArraySet đảm bảo không có ConcurrentModificationException khi iterate.

---

### **Câu 2: Tại sao mỗi client cần một thread riêng trong ChatServer?**

**Trả lời:** Vì `server.accept()` và `in.readLine()` là blocking operations. Nếu dùng single thread, server chỉ có thể xử lý một client tại một thời điểm. Mỗi client một thread cho phép server xử lý nhiều client đồng thời, đảm bảo tính real-time.

---

### **Câu 3: Tại sao dùng daemon thread cho readLoop trong ChatClient?**

**Trả lời:** Daemon thread tự động kết thúc khi main thread kết thúc, không cần phải đóng thủ công. Khi ứng dụng đóng, readLoop sẽ tự động dừng, tránh thread "zombie" chạy nền.

---

### **Câu 4: Điều gì xảy ra nếu server nhận được JSON không hợp lệ?**

**Trả lời:** Server catch Exception trong ClientHandler.run(), in lỗi và raw line để debug, nhưng tiếp tục đọc dòng tiếp theo. Server không crash, chỉ bỏ qua tin nhắn lỗi đó.

---

### **Câu 5: Tại sao phải thêm "\n" sau mỗi JSON message?**

**Trả lời:** Vì `readLine()` đọc đến khi gặp newline character. Nếu không có "\n", `readLine()` sẽ block chờ newline hoặc EOF, không thể parse được message. "\n" là delimiter để phân biệt các message.

---

### **Câu 6: Broadcast có gửi lại tin nhắn cho người gửi không?**

**Trả lời:** Không. Trong method `broadcast()`, có check `if (c != from)` để bỏ qua client gửi tin nhắn. Chỉ gửi đến các client khác để tránh echo (tin nhắn hiện 2 lần trên UI của người gửi).

---

### **Câu 7: Nếu client disconnect đột ngột, server xử lý như thế nào?**

**Trả lời:** Khi client disconnect, `in.readLine()` trong ClientHandler.run() sẽ trả về null hoặc throw IOException. Server catch IOException, in log, remove client khỏi set trong finally block, và đóng socket. Server tiếp tục hoạt động bình thường.

---

### **Câu 8: ChatClient có thể gửi tin nhắn trước khi connect() không?**

**Trả lời:** Không. Method `send()` check `if (out == null) throw new IllegalStateException("Not connected")`. Phải gọi `connect()` trước để khởi tạo `out`, nếu không sẽ throw exception.

---

### **Câu 9: Encoding UTF-8 được dùng ở đâu và tại sao?**

**Trả lời:** UTF-8 được dùng khi tạo InputStreamReader và OutputStreamWriter trong cả ChatServer và ChatClient. UTF-8 hỗ trợ đầy đủ Unicode, đảm bảo tin nhắn tiếng Việt và emoji được truyền đúng, không bị lỗi encoding.

---

### **Câu 10: Module này có lưu tin nhắn vào database không?**

**Trả lời:** Không. Module chat chỉ xử lý real-time messaging qua TCP, không có logic lưu vào database. Việc lưu tin nhắn vào database được xử lý ở layer khác (có thể là MainChatController hoặc service layer) sau khi nhận được tin nhắn từ ChatClient callback.

---

## 5. SCRIPT THUYẾT TRÌNH 4 PHÚT (CÓ CẢ PHẦN DEMO)

### **[0:00 - 0:30] Giới thiệu tổng quan**

"Xin chào thầy và các bạn. Hôm nay em sẽ trình bày về **module Chat** trong ứng dụng AegisTalk. Module này là hệ thống giao tiếp real-time sử dụng TCP socket, bao gồm ChatServer lắng nghe trên port 5555 và ChatClient kết nối đến server để gửi/nhận tin nhắn. Module được sử dụng trong MainChatController sau khi user đăng nhập, và ChatServer được khởi động trong AegisTalkServerMain như một daemon thread."

**[Demo: Mở code, chỉ vào package org.example.demo2.net.chat]**

---

### **[0:30 - 1:30] Flow runtime và kiến trúc**

"Bây giờ em sẽ giải thích flow runtime. Khi AegisTalkServerMain khởi động, nó tạo một daemon thread để chạy ChatServer trên port 5555. Server lắng nghe kết nối mới, và mỗi client kết nối sẽ được xử lý bởi một thread riêng - ClientHandler."

"Ở phía client, khi MainChatController được khởi tạo sau khi user đăng nhập, nó gọi `connectChat()` để tạo ChatClient và kết nối đến server. ChatClient tạo một daemon thread để đọc tin nhắn liên tục từ server."

"Khi user gửi tin nhắn, MainChatController tạo ChatMessage object, serialize thành JSON, và gửi qua TCP socket. Server nhận được, parse JSON, và broadcast đến tất cả client khác. Các client nhận được, parse JSON, và gọi callback để hiển thị lên UI."

**[Demo: Chỉ vào code ChatServer.start(), ClientHandler.run(), ChatClient.readLoop()]**

---

### **[1:30 - 2:30] Giải thích các method quan trọng**

"Em sẽ giải thích một số method quan trọng. Đầu tiên là `ChatServer.start()` - method này tạo ServerSocket, chờ kết nối mới bằng `accept()`, và với mỗi client, tạo một ClientHandler thread riêng."

"Method `broadcast()` trong ChatServer nhận ChatMessage, serialize thành JSON, và gửi đến tất cả client khác - lưu ý là không gửi lại cho người gửi để tránh echo."

"Trong ClientHandler, method `run()` đọc từng dòng JSON từ client bằng `readLine()`, parse thành ChatMessage, và gọi `broadcast()`. Nếu client disconnect, nó được remove khỏi set và socket được đóng."

"Ở phía client, `ChatClient.connect()` tạo Socket, khởi tạo BufferedReader/BufferedWriter, và start thread đọc tin nhắn. Method `readLoop()` đọc liên tục, parse JSON, và gọi callback `onMessage`."

**[Demo: Chỉ vào các method trong code, giải thích logic]**

---

### **[2:30 - 3:30] Demo thực tế**

"Bây giờ em sẽ demo module này hoạt động. Đầu tiên, em sẽ chạy ChatServer."

**[Demo: Chạy ChatServerMain, show console output "Listening on port 5555"]**

"Tiếp theo, em sẽ chạy 2 ChatClientConsoleMain để mô phỏng 2 user chat với nhau."

**[Demo: Mở 2 terminal, chạy ChatClientConsoleMain, nhập tên và room]**

"Bây giờ em sẽ gửi tin nhắn từ client 1."

**[Demo: Gõ tin nhắn "Hello" trong client 1, show tin nhắn xuất hiện ở client 2]**

"Như các bạn thấy, tin nhắn từ client 1 đã được broadcast đến client 2. Server log cho thấy nó nhận được tin nhắn và gửi đến client khác."

**[Demo: Show server console log]**

"Em sẽ gửi thêm một tin nhắn từ client 2."

**[Demo: Gõ tin nhắn "Hi there" trong client 2, show tin nhắn xuất hiện ở client 1]**

"Hoàn hảo! Module chat hoạt động đúng như mong đợi - tin nhắn được broadcast real-time giữa các client."

---

### **[3:30 - 4:00] Tóm tắt và kết luận**

"Tóm lại, module Chat là một hệ thống TCP-based messaging đơn giản nhưng hiệu quả. Nó sử dụng multi-threading để xử lý nhiều client đồng thời, JSON để serialize/deserialize messages, và callback pattern để xử lý tin nhắn nhận được. Module này là nền tảng cho tính năng chat real-time của ứng dụng AegisTalk."

"Cảm ơn thầy và các bạn đã lắng nghe. Em sẵn sàng trả lời câu hỏi."

---

### **Ghi chú cho demo:**

1. **Chuẩn bị trước:**
   - Đảm bảo port 5555 không bị chiếm
   - Có sẵn 2-3 terminal windows để chạy client
   - Test trước để đảm bảo không có lỗi

2. **Khi demo:**
   - Nói rõ ràng từng bước đang làm
   - Chỉ vào code khi giải thích
   - Show console output để chứng minh hoạt động
   - Nếu có lỗi, giải thích và xử lý bình tĩnh

3. **Backup plan:**
   - Nếu không chạy được live demo, có thể show screenshots hoặc video đã quay trước
   - Hoặc giải thích flow bằng cách trace code

