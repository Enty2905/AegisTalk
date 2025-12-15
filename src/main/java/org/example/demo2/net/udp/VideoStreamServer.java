package org.example.demo2.net.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UDP Server cho truyền video/audio streaming.
 * 
 * Áp dụng: UDP Socket - Bài UDP
 * - Sử dụng DatagramSocket để gửi/nhận gói tin UDP
 * - Không đảm bảo độ tin cậy nhưng tốc độ cao (low latency)
 * - Chấp nhận mất gói (packet loss) phù hợp cho real-time streaming
 * - Dùng cho: truyền frame audio/video trong cuộc gọi
 * 
 * Packet format:
 * - Header (16 bytes): [sessionId(4)][userId(4)][sequence(4)][timestamp(4)]
 * - Payload: audio/video data
 * 
 * FIX: Khi 2 client chạy trên cùng 1 máy, server dùng userId để phân biệt
 *      thay vì chỉ dựa vào IP:port (vì cả 2 đều là localhost)
 */
public class VideoStreamServer {
    
    private final int port;
    private DatagramSocket socket;
    private boolean running = false;
    
    // Quản lý session: sessionId -> StreamSession
    private final Map<Integer, StreamSession> sessions = new ConcurrentHashMap<>();
    
    public VideoStreamServer(int port) {
        this.port = port;
    }
    
    public void start() throws SocketException {
        socket = new DatagramSocket(port);
        running = true;
        System.out.println("[VideoStreamServer] UDP Server listening on port " + port);
        System.out.println("[VideoStreamServer] Áp dụng: UDP Socket - Bài UDP");
        
        // Thread nhận packet
        Thread receiveThread = new Thread(this::receiveLoop, "UDP-Receive");
        receiveThread.start();
    }
    
    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
    
    private void receiveLoop() {
        byte[] buffer = new byte[65507]; // Max UDP packet size
        
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                
                processPacket(packet);
                
            } catch (IOException e) {
                if (running) {
                    System.err.println("[VideoStreamServer] Error receiving packet: " + e.getMessage());
                }
            }
        }
    }
    
    private void processPacket(DatagramPacket packet) {
        byte[] data = packet.getData();
        int length = packet.getLength();
        
        if (length < 16) {
            return; // Packet quá nhỏ, bỏ qua
        }
        
        // Parse header (16 bytes): sessionId(4) + userId(4) + sequence(4) + timestamp(4)
        ByteBuffer header = ByteBuffer.wrap(data, 0, 16);
        int sessionId = header.getInt();
        int userId = header.getInt();  // userId của người gửi
        int sequence = header.getInt();
        long timestamp = header.getInt() & 0xFFFFFFFFL; // unsigned int
        
        // Lấy payload
        byte[] payload = new byte[length - 16];
        System.arraycopy(data, 16, payload, 0, payload.length);
        
        StreamSession session = sessions.get(sessionId);
        if (session != null) {
            // Cập nhật endpoint thực tế cho user này
            session.updateUserEndpoint(userId, packet.getAddress(), packet.getPort());
            
            // Forward đến các client khác trong session (dựa vào userId, không phải IP:port)
            forwardToOthers(session, sessionId, userId, sequence, timestamp, payload);
        } else {
            // Log khi không tìm thấy session
            if (sequence % 100 == 0) {
                System.out.println("[VideoStreamServer] WARNING: No session found for sessionId=" + sessionId + " (userId=" + userId + ")");
                System.out.println("[VideoStreamServer] Available sessions: " + sessions.keySet());
            }
        }
    }
    
    private void forwardToOthers(StreamSession session, int sessionId, int senderId, 
                                 int sequence, long timestamp, byte[] payload) {
        // Tạo packet mới với header (giữ nguyên senderId để receiver biết ai gửi)
        ByteBuffer header = ByteBuffer.allocate(16);
        header.putInt(sessionId);
        header.putInt(senderId);
        header.putInt(sequence);
        header.putInt((int) timestamp);
        byte[] headerBytes = header.array();
        
        byte[] packetData = new byte[16 + payload.length];
        System.arraycopy(headerBytes, 0, packetData, 0, 16);
        System.arraycopy(payload, 0, packetData, 16, payload.length);
        
        // Debug log
        if (sequence % 30 == 0) {
            System.out.println("[VideoStreamServer] Forwarding frame #" + sequence + " from userId=" + senderId);
            System.out.println("[VideoStreamServer] Session " + sessionId + " has " + session.getUserEndpoints().size() + " users");
        }
        
        // Gửi đến tất cả users khác trong session (dựa vào userId)
        for (Map.Entry<Integer, UserEndpoint> entry : session.getUserEndpoints().entrySet()) {
            int targetUserId = entry.getKey();
            UserEndpoint endpoint = entry.getValue();
            
            // Không gửi lại cho người gửi (dựa vào userId, không phải IP:port)
            if (targetUserId != senderId && endpoint.hasActualAddress()) {
                try {
                    DatagramPacket forwardPacket = new DatagramPacket(
                            packetData, packetData.length,
                            endpoint.getActualAddress(), endpoint.getActualPort());
                    socket.send(forwardPacket);
                    
                    if (sequence % 30 == 0) {
                        System.out.println("[VideoStreamServer] -> Forwarded to userId=" + targetUserId + 
                                          " at " + endpoint.getActualAddress().getHostAddress() + ":" + endpoint.getActualPort());
                    }
                } catch (IOException e) {
                    System.err.println("[VideoStreamServer] Error forwarding to userId=" + targetUserId + ": " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Đăng ký endpoint vào session với userId.
     */
    public void registerEndpoint(int sessionId, int userId, InetAddress address, int port) {
        StreamSession session = sessions.computeIfAbsent(sessionId, k -> new StreamSession());
        session.addUserEndpoint(userId, address, port);
        System.out.println("[VideoStreamServer] Endpoint registered: session=" + sessionId + 
                          ", userId=" + userId + ", " + address.getHostAddress() + ":" + port);
    }
    
    /**
     * Đăng ký endpoint vào session (legacy - không có userId).
     */
    public void registerEndpoint(int sessionId, InetAddress address, int port) {
        // Legacy method - tạo userId giả từ port
        registerEndpoint(sessionId, port, address, port);
    }
    
    /**
     * Hủy đăng ký endpoint.
     */
    public void unregisterEndpoint(int sessionId, int userId) {
        StreamSession session = sessions.get(sessionId);
        if (session != null) {
            session.removeUserEndpoint(userId);
            if (session.getUserEndpoints().isEmpty()) {
                sessions.remove(sessionId);
            }
        }
    }
    
    /**
     * Stream session chứa các user endpoint.
     */
    private static class StreamSession {
        // userId -> UserEndpoint
        private final Map<Integer, UserEndpoint> userEndpoints = new ConcurrentHashMap<>();
        
        public Map<Integer, UserEndpoint> getUserEndpoints() {
            return userEndpoints;
        }
        
        public void addUserEndpoint(int userId, InetAddress registeredAddr, int registeredPort) {
            userEndpoints.put(userId, new UserEndpoint(userId, registeredAddr, registeredPort));
            System.out.println("[StreamSession] Added user endpoint: userId=" + userId + 
                              ", registered=" + registeredAddr.getHostAddress() + ":" + registeredPort);
        }
        
        public void removeUserEndpoint(int userId) {
            userEndpoints.remove(userId);
        }
        
        /**
         * Cập nhật địa chỉ thực tế của user khi nhận được packet đầu tiên.
         */
        public void updateUserEndpoint(int userId, InetAddress actualAddr, int actualPort) {
            UserEndpoint endpoint = userEndpoints.get(userId);
            if (endpoint != null && !endpoint.hasActualAddress()) {
                endpoint.setActualAddress(actualAddr, actualPort);
                System.out.println("[StreamSession] Updated actual endpoint for userId=" + userId + 
                                  ": " + actualAddr.getHostAddress() + ":" + actualPort);
            }
        }
        
        // Legacy methods
        public java.util.Set<StreamEndpoint> getEndpoints() {
            java.util.Set<StreamEndpoint> endpoints = new java.util.HashSet<>();
            for (UserEndpoint ue : userEndpoints.values()) {
                if (ue.hasActualAddress()) {
                    endpoints.add(new StreamEndpoint(ue.getActualAddress(), ue.getActualPort()));
                }
            }
            return endpoints;
        }
        
        public void addEndpoint(StreamEndpoint endpoint) {
            // Legacy - không làm gì
        }
        
        public void removeEndpoint(StreamEndpoint endpoint) {
            // Legacy - không làm gì
        }
        
        public void updateEndpointAddress(String actualAddr, int actualPort) {
            // Legacy - không làm gì
        }
    }
    
    /**
     * User endpoint với địa chỉ đăng ký và địa chỉ thực tế.
     */
    private static class UserEndpoint {
        private final int userId;
        private final InetAddress registeredAddress;
        private final int registeredPort;
        private InetAddress actualAddress;
        private int actualPort;
        
        public UserEndpoint(int userId, InetAddress registeredAddr, int registeredPort) {
            this.userId = userId;
            this.registeredAddress = registeredAddr;
            this.registeredPort = registeredPort;
        }
        
        public int getUserId() {
            return userId;
        }
        
        public boolean hasActualAddress() {
            return actualAddress != null;
        }
        
        public void setActualAddress(InetAddress addr, int port) {
            this.actualAddress = addr;
            this.actualPort = port;
        }
        
        public InetAddress getActualAddress() {
            return actualAddress != null ? actualAddress : registeredAddress;
        }
        
        public int getActualPort() {
            return actualAddress != null ? actualPort : registeredPort;
        }
    }
    
    /**
     * Endpoint (address + port) - Legacy class.
     */
    public static class StreamEndpoint {
        private final InetAddress address;
        private final int port;
        
        public StreamEndpoint(InetAddress address, int port) {
            this.address = address;
            this.port = port;
        }
        
        public InetAddress getAddress() {
            return address;
        }
        
        public int getPort() {
            return port;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StreamEndpoint that = (StreamEndpoint) o;
            return port == that.port && address.equals(that.address);
        }
        
        @Override
        public int hashCode() {
            return address.hashCode() * 31 + port;
        }
    }
    
    public static void main(String[] args) throws SocketException {
        VideoStreamServer server = new VideoStreamServer(8888);
        server.start();
        
        // Giữ server chạy
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            server.stop();
        }
    }
}





