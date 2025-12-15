package org.example.demo2.net.udp;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Enumeration;

/**
 * UDP Client cho gửi/nhận video/audio streaming.
 * 
 * Áp dụng: UDP Socket - Bài UDP
 * - Sử dụng DatagramSocket để gửi/nhận gói tin UDP
 * - Tốc độ cao, độ trễ thấp cho real-time streaming
 * - Chấp nhận mất gói (packet loss)
 * 
 * Packet format:
 * - Header (16 bytes): [sessionId(4)][userId(4)][sequence(4)][timestamp(4)]
 * - Payload: audio/video data
 */
public class VideoStreamClient {
    
    private DatagramSocket socket;
    private InetAddress serverAddress;
    private int serverPort;
    private int sessionId;
    private int userId; // ID của user hiện tại
    private int sequenceNumber = 0;
    private boolean running = false;
    
    public VideoStreamClient(int sessionId) throws SocketException {
        this.sessionId = sessionId;
        this.userId = 0; // Default, sẽ được set sau
        this.socket = new DatagramSocket();
    }
    
    /**
     * Set userId cho client này (quan trọng để server phân biệt các clients).
     */
    public void setUserId(int userId) {
        this.userId = userId;
    }
    
    /**
     * Lấy địa chỉ IP LAN thực tế của máy (không phải localhost/127.0.0.1).
     * Quan trọng khi chạy trên nhiều máy khác nhau trong mạng LAN.
     */
    public static String getLocalLanAddress() {
        try {
            String bestAddress = null;
            java.util.List<String> allFoundAddresses = new java.util.ArrayList<>();
            
            // Cách 1: Tìm network interface có IPv4 không phải loopback
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                String niName = ni.getDisplayName().toLowerCase();
                
                // Bỏ qua các virtual adapters (VirtualBox, VMware, Docker, Hyper-V, etc.)
                boolean isVirtual = niName.contains("virtual") || niName.contains("vmware") || 
                    niName.contains("docker") || niName.contains("hyper-v") ||
                    niName.contains("vbox") || niName.contains("vpn") ||
                    niName.contains("tunnel") || niName.contains("loopback");
                
                if (ni.isUp() && !ni.isLoopback()) {
                    Enumeration<InetAddress> addresses = ni.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress addr = addresses.nextElement();
                        // Chỉ lấy IPv4 và không phải loopback
                        if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                            String ip = addr.getHostAddress();
                            allFoundAddresses.add(ip + " (" + ni.getDisplayName() + ")");
                            
                            // Bỏ qua 192.168.56.x (VirtualBox default range)
                            if (ip.startsWith("192.168.56.")) {
                                continue;
                            }
                            
                            // Bỏ qua nếu là virtual adapter
                            if (isVirtual) {
                                continue;
                            }
                            
                            if (ip.startsWith("192.168.") || ip.startsWith("10.") || 
                                (ip.startsWith("172.") && isPrivate172(ip))) {
                                System.out.println("[VideoStreamClient] Found LAN address: " + ip + " on " + ni.getDisplayName());
                                bestAddress = ip;
                                // Ưu tiên WiFi hoặc Ethernet adapters
                                if (niName.contains("wi-fi") || niName.contains("wifi") || 
                                    niName.contains("ethernet") || niName.contains("lan") ||
                                    niName.contains("wireless")) {
                                    return ip;
                                }
                            }
                        }
                    }
                }
            }
            
            System.out.println("[VideoStreamClient] All found addresses: " + allFoundAddresses);
            
            if (bestAddress != null) {
                return bestAddress;
            }
            
            // Cách 2: Fallback - dùng getLocalHost
            String fallback = InetAddress.getLocalHost().getHostAddress();
            System.out.println("[VideoStreamClient] Using fallback address: " + fallback);
            
            // Nếu fallback vẫn là 127.0.0.1 hoặc không tìm thấy, có thể chấp nhận 
            // vì khi chạy local cả 2 client sẽ cùng dùng localhost
            return fallback;
        } catch (Exception e) {
            System.err.println("[VideoStreamClient] Error getting local address: " + e.getMessage());
            return "127.0.0.1";
        }
    }
    
    private static boolean isPrivate172(String ip) {
        try {
            String[] parts = ip.split("\\.");
            int second = Integer.parseInt(parts[1]);
            return second >= 16 && second <= 31;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Kết nối đến server.
     */
    public void connect(String serverHost, int serverPort) throws UnknownHostException {
        this.serverAddress = InetAddress.getByName(serverHost);
        this.serverPort = serverPort;
        
        // Đăng ký endpoint với server (có thể dùng TCP để đăng ký)
        System.out.println("[VideoStreamClient] Connected to " + serverHost + ":" + serverPort);
    }
    
    /**
     * Gửi frame audio/video.
     * Header format: [sessionId(4)][userId(4)][sequence(4)][timestamp(4)]
     */
    public void sendFrame(byte[] frameData) throws IOException {
        if (serverAddress == null) {
            throw new IllegalStateException("Not connected to server");
        }
        
        // Tạo header (16 bytes)
        ByteBuffer header = ByteBuffer.allocate(16);
        header.putInt(sessionId);
        header.putInt(userId);  // Thêm userId vào header
        header.putInt(sequenceNumber++);
        header.putInt((int) (System.currentTimeMillis() & 0xFFFFFFFFL));
        byte[] headerBytes = header.array();
        
        // Tạo packet
        byte[] packetData = new byte[16 + frameData.length];
        System.arraycopy(headerBytes, 0, packetData, 0, 16);
        System.arraycopy(frameData, 0, packetData, 16, frameData.length);
        
        DatagramPacket packet = new DatagramPacket(
                packetData, packetData.length,
                serverAddress, serverPort);
        
        socket.send(packet);
    }
    
    /**
     * Bắt đầu nhận frame.
     */
    public void startReceiving(FrameReceiver receiver) {
        if (running) {
            return;
        }
        running = true;
        
        Thread receiveThread = new Thread(() -> {
            byte[] buffer = new byte[65507];
            
            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    
                    if (packet.getLength() < 16) {
                        continue; // Packet quá nhỏ
                    }
                    
                    // Parse header (16 bytes)
                    ByteBuffer header = ByteBuffer.wrap(packet.getData(), 0, 16);
                    int recvSessionId = header.getInt();
                    int senderId = header.getInt();  // userId của người gửi
                    int sequence = header.getInt();
                    long timestamp = header.getInt() & 0xFFFFFFFFL;
                    
                    // QUAN TRỌNG: Chỉ nhận frames từ session đúng
                    if (recvSessionId != sessionId) {
                        continue; // Session ID không khớp
                    }
                    
                    // Bỏ qua frames từ chính mình (dựa vào userId, không phải IP)
                    if (senderId == userId) {
                        continue; // Frame từ chính mình, bỏ qua
                    }
                    
                    // Lấy payload
                    byte[] payload = new byte[packet.getLength() - 16];
                    System.arraycopy(packet.getData(), 16, payload, 0, payload.length);
                    
                    // Gọi callback
                    if (receiver != null) {
                        receiver.onFrameReceived(recvSessionId, sequence, timestamp, payload);
                    }
                    
                } catch (IOException e) {
                    if (running) {
                        System.err.println("[VideoStreamClient] Error receiving: " + e.getMessage());
                    }
                }
            }
        }, "UDP-Client-Receive");
        
        receiveThread.start();
    }
    
    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
    
    /**
     * Callback interface cho nhận frame.
     */
    public interface FrameReceiver {
        void onFrameReceived(int sessionId, int sequence, long timestamp, byte[] frameData);
    }
    
    public int getLocalPort() {
        return socket != null ? socket.getLocalPort() : -1;
    }
}





