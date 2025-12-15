# Hướng Dẫn Chạy AegisTalk Trên 2 Máy Khác Nhau

## Yêu Cầu

1. Cả 2 máy phải cùng mạng LAN (cùng WiFi hoặc cùng mạng Ethernet)
2. Tắt Firewall hoặc mở các port sau:
   - **1099** - RMI Service
   - **5100** - Moderation Service
   - **5555** - TCP Chat Server
   - **8081** - HTTP File Server
   - **8888** - UDP Video Stream (quan trọng nhất cho video call)

## Các Bước Thực Hiện

### Bước 1: Xác Định IP của Máy Server

Trên máy sẽ chạy server, mở Command Prompt và chạy:

```bash
# Windows
ipconfig

# Linux/Mac
ifconfig
```

Tìm địa chỉ IPv4 của adapter mạng (thường có dạng `192.168.x.x` hoặc `10.x.x.x`).

**Ví dụ:** `192.168.1.100`

### Bước 2: Chạy Server

Trên máy server:

```bash
cd <đường-dẫn-dự-án>
mvn clean compile
mvn exec:java -Dexec.mainClass="org.example.demo2.server.AegisTalkServerMain"
```

Server sẽ hiển thị IP của máy và các port đang lắng nghe.

### Bước 3: Chạy Client Trên Máy Server (Client 1)

Mở terminal mới trên máy server:

```bash
mvn javafx:run
```

Hoặc nếu muốn chỉ định IP server rõ ràng:

```bash
mvn javafx:run -Dserver.host=192.168.1.100
```

### Bước 4: Chạy Client Trên Máy Khác (Client 2)

Copy dự án sang máy client hoặc clone từ git, sau đó:

**Cách 1: Dùng System Property**
```bash
mvn javafx:run -Dserver.host=192.168.1.100
```

**Cách 2: Dùng Environment Variable**
```bash
# Windows PowerShell
$env:SERVER_HOST="192.168.1.100"
mvn javafx:run

# Windows CMD
set SERVER_HOST=192.168.1.100
mvn javafx:run

# Linux/Mac
export SERVER_HOST=192.168.1.100
mvn javafx:run
```

## Kiểm Tra Kết Nối

### 1. Test TCP Connection (Chat)

Trên máy client, mở PowerShell:
```powershell
Test-NetConnection -ComputerName 192.168.1.100 -Port 5555
```

### 2. Test RMI Connection

Trên máy client:
```powershell
Test-NetConnection -ComputerName 192.168.1.100 -Port 1099
```

### 3. Test UDP (Video Call)

UDP khó test trực tiếp, nhưng nếu TCP hoạt động và firewall đã mở port 8888, UDP thường cũng hoạt động.

## Xử Lý Lỗi Thường Gặp

### Lỗi: "Connection refused" hoặc "Cannot connect"

1. **Kiểm tra firewall:**
   ```powershell
   # Windows - Mở port cho Java
   netsh advfirewall firewall add rule name="AegisTalk" dir=in action=allow protocol=TCP localport=1099,5100,5555,8081
   netsh advfirewall firewall add rule name="AegisTalk UDP" dir=in action=allow protocol=UDP localport=8888
   ```

2. **Kiểm tra server đang chạy:**
   ```powershell
   netstat -an | findstr "1099\|5555\|8888"
   ```

3. **Ping máy server:**
   ```powershell
   ping 192.168.1.100
   ```

### Lỗi: Video Call không thấy hình của người kia

1. **Kiểm tra UDP port 8888 đã mở trên cả 2 máy**

2. **Kiểm tra firewall cho UDP:**
   ```powershell
   netsh advfirewall firewall add rule name="AegisTalk UDP Inbound" dir=in action=allow protocol=UDP localport=8888
   netsh advfirewall firewall add rule name="AegisTalk UDP Outbound" dir=out action=allow protocol=UDP localport=8888
   ```

3. **Xem log trên server để debug:**
   - Server sẽ in log khi nhận được UDP packet
   - Kiểm tra xem có "Forwarding frame" message không

### Lỗi: RMI "Cannot bind to registry"

Server cần chạy trên IP có thể truy cập được, không chỉ localhost. Đảm bảo:

```bash
# Trên máy server, chạy với IP binding
java -Djava.rmi.server.hostname=192.168.1.100 ...
```

## Cấu Trúc Mạng

```
┌─────────────────────────────────────────────────────────────────┐
│                         LAN Network                             │
│                                                                 │
│  ┌───────────────────┐           ┌───────────────────┐          │
│  │   MÁY SERVER      │           │   MÁY CLIENT      │          │
│  │   192.168.1.100   │           │   192.168.1.101   │          │
│  │                   │           │                   │          │
│  │  ┌─────────────┐  │    TCP    │  ┌─────────────┐  │          │
│  │  │ AegisTalk   │◄─┼───────────┼──│ AegisTalk   │  │          │
│  │  │ Server      │  │   :5555   │  │ Client 2    │  │          │
│  │  └─────────────┘  │           │  └─────────────┘  │          │
│  │        │         │    UDP    │        │          │          │
│  │        ▼         │◄─────────►│        ▼          │          │
│  │  ┌─────────────┐  │   :8888   │  Video/Audio     │          │
│  │  │ UDP Video   │  │           │  Streaming       │          │
│  │  │ Server      │  │           │                   │          │
│  │  └─────────────┘  │           │                   │          │
│  │                   │           │                   │          │
│  │  ┌─────────────┐  │    RMI    │                   │          │
│  │  │ RMI Services│◄─┼───────────┼─── Auth, Chat,   │          │
│  │  │ :1099       │  │           │    Friend, Group  │          │
│  │  └─────────────┘  │           │                   │          │
│  │                   │           │                   │          │
│  │  ┌─────────────┐  │           │                   │          │
│  │  │ AegisTalk   │  │           │                   │          │
│  │  │ Client 1    │  │           │                   │          │
│  │  └─────────────┘  │           │                   │          │
│  └───────────────────┘           └───────────────────┘          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Port Summary

| Port | Protocol | Service | Mô tả |
|------|----------|---------|-------|
| 1099 | TCP | RMI Registry | Auth, Friend, Chat, Group services |
| 5100 | TCP | RMI Moderation | AI content moderation |
| 5555 | TCP | Chat Server | Real-time messaging |
| 8081 | TCP/HTTP | File Server | Upload/download files |
| 8888 | UDP | Video Stream | Video/audio streaming |
| 4446 | UDP Multicast | Presence | Online status (LAN only) |

## Tips

1. **Luôn chạy Server trước, Client sau**

2. **Kiểm tra IP đã đúng chưa:**
   - Server console sẽ hiển thị IP khi khởi động
   - Client sẽ log IP server khi kết nối

3. **Nếu video call không hoạt động:**
   - Kiểm tra webcam có được phép truy cập không
   - Thử tắt và bật lại camera trong video call
   - Xem log trên cả client và server

4. **Database phải chạy trên máy server:**
   - MySQL phải accessible từ localhost trên máy server
   - Clients không cần truy cập trực tiếp database (qua RMI)
