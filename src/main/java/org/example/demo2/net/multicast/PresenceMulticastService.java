package org.example.demo2.net.multicast;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.demo2.model.PresenceEvent;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.function.Consumer;

/**
 * Multicast Service cho presence discovery và group notifications.
 * 
 * Áp dụng: Multicast - Bài Multicast
 * - Sử dụng MulticastSocket để join/leave multicast group
 * - Dùng địa chỉ lớp D (239.x.x.x) cho multicast
 * - Gửi thông báo đến nhiều client đồng thời (one-to-many)
 * - Dùng cho: presence discovery trong LAN, thông báo sự kiện nhóm (user join/leave)
 * 
 * Protocol:
 * - Format: JSON hoặc pipe-separated: TYPE|room|userId|displayName|data
 */
public class PresenceMulticastService {
    
    public static final String DEFAULT_GROUP_IP = "239.1.1.1";
    public static final int DEFAULT_GROUP_PORT = 4446;
    
    private final InetAddress group;
    private final int port;
    private MulticastSocket socket;
    private boolean running = false;
    private Thread receiveThread;
    private Consumer<PresenceEvent> eventHandler;
    private String selfUserId;
    
    public PresenceMulticastService(String groupIp, int port) throws IOException {
        this.group = InetAddress.getByName(groupIp);
        this.port = port;
    }
    
    /**
     * Khởi động service và join multicast group.
     */
    public void start(String selfUserId, Consumer<PresenceEvent> handler) throws IOException {
        if (running) {
            return;
        }
        
        this.selfUserId = selfUserId;
        this.eventHandler = handler;
        
        socket = new MulticastSocket(port);
        socket.setReuseAddress(true);
        socket.setTimeToLive(1); // Chỉ trong LAN (TTL=1)
        
        // Join multicast group
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            NetworkInterface netIf = null;
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isUp() && !ni.isLoopback()) {
                    netIf = ni;
                    break;
                }
            }
            if (netIf == null) {
                // Fallback: lấy interface đầu tiên
                netIf = NetworkInterface.getNetworkInterfaces().nextElement();
            }
            if (netIf != null) {
                socket.joinGroup(new InetSocketAddress(group, port), netIf);
            }
        } catch (Exception e) {
            System.err.println("[PresenceMulticastService] Warning: Could not join multicast group with NetworkInterface: " + e.getMessage());
            throw new IOException("Failed to join multicast group", e);
        }
        
        running = true;
        receiveThread = new Thread(this::receiveLoop, "Multicast-Receive");
        receiveThread.setDaemon(true);
        receiveThread.start();
        
        System.out.println("[PresenceMulticastService] Joined multicast group " + group + ":" + port);
        System.out.println("[PresenceMulticastService] Áp dụng: Multicast - Bài Multicast");
    }
    
    /**
     * Dừng service và leave group.
     */
    public void stop() throws IOException {
        if (!running) {
            return;
        }
        
        running = false;
        
        if (socket != null) {
            try {
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                NetworkInterface netIf = null;
                while (interfaces.hasMoreElements()) {
                    NetworkInterface ni = interfaces.nextElement();
                    if (ni.isUp() && !ni.isLoopback()) {
                        netIf = ni;
                        break;
                    }
                }
                if (netIf == null && NetworkInterface.getNetworkInterfaces().hasMoreElements()) {
                    netIf = NetworkInterface.getNetworkInterfaces().nextElement();
                }
                if (netIf != null) {
                    socket.leaveGroup(new InetSocketAddress(group, port), netIf);
                }
            } catch (Exception e) {
                System.err.println("[PresenceMulticastService] Error leaving group: " + e.getMessage());
            }
            socket.close();
        }
    }
    
    /**
     * Gửi presence event đến multicast group.
     */
    public void sendEvent(PresenceEvent event) throws IOException {
        if (!running || socket == null) {
            return;
        }
        
        // Format: TYPE|room|userId|displayName
        String message = String.format("%s|%s|%s|%s",
                event.getType().name(),
                event.getRoom(),
                event.getUserId(),
                event.getDisplayName());
        
        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(data, data.length, group, port);
        socket.send(packet);
    }
    
    /**
     * Gửi group notification (ví dụ: user join/leave group).
     */
    public void sendGroupNotification(String groupId, String eventType, String userId, String displayName) throws IOException {
        PresenceEvent event = new PresenceEvent(
                PresenceEvent.Type.valueOf(eventType),
                groupId,
                userId,
                displayName
        );
        sendEvent(event);
    }
    
    private void receiveLoop() {
        byte[] buffer = new byte[512];
        
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                
                String message = new String(packet.getData(), packet.getOffset(), packet.getLength(),
                        StandardCharsets.UTF_8).trim();
                
                parseAndHandle(message);
                
            } catch (IOException e) {
                if (running) {
                    System.err.println("[PresenceMulticastService] Error receiving: " + e.getMessage());
                }
            }
        }
    }
    
    private void parseAndHandle(String message) {
        // Format: TYPE|room|userId|displayName
        String[] parts = message.split("\\|", 4);
        if (parts.length < 4) {
            return;
        }
        
        String typeStr = parts[0];
        String room = parts[1];
        String userId = parts[2];
        String displayName = parts[3];
        
        // Bỏ qua event từ chính mình
        if (userId.equals(selfUserId)) {
            return;
        }
        
        try {
            PresenceEvent.Type type = PresenceEvent.Type.valueOf(typeStr);
            PresenceEvent event = new PresenceEvent(type, room, userId, displayName);
            
            if (eventHandler != null) {
                eventHandler.accept(event);
            }
        } catch (IllegalArgumentException e) {
            System.err.println("[PresenceMulticastService] Unknown event type: " + typeStr);
        }
    }
    
    /**
     * Publisher riêng để gửi events (tách biệt với listener).
     */
    public static class Publisher {
        private final DatagramSocket socket;
        private final InetAddress group;
        private final int port;
        
        public Publisher(String groupIp, int port) throws IOException {
            this.group = InetAddress.getByName(groupIp);
            this.port = port;
            this.socket = new DatagramSocket();
            // Note: setTimeToLive chỉ có trong MulticastSocket, không có trong DatagramSocket
            // TTL được set khi gửi packet qua MulticastSocket
        }
        
        public void publish(PresenceEvent event) throws IOException {
            String message = String.format("%s|%s|%s|%s",
                    event.getType().name(),
                    event.getRoom(),
                    event.getUserId(),
                    event.getDisplayName());
            
            byte[] data = message.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(data, data.length, group, port);
            socket.send(packet);
        }
        
        public void close() {
            socket.close();
        }
    }
}

