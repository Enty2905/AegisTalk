package org.example.demo2.net.tcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.demo2.model.ChatMessage;
import org.example.demo2.model.MessageType;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * TCP Server đa luồng cho chat và signaling video call.
 * 
 * Áp dụng: TCP Socket - Bài TCP
 * - Sử dụng ServerSocket để lắng nghe kết nối
 * - Mỗi client là một thread riêng (multi-threaded server)
 * - Đảm bảo độ tin cậy và thứ tự dữ liệu (reliable, ordered)
 * - Dùng cho: đăng nhập, tin nhắn 1-1, tin nhắn nhóm, signaling video call
 * 
 * Protocol:
 * - Mỗi dòng là một JSON message
 * - Message types: CHAT, CALL_OFFER, CALL_ANSWER, CALL_ICE, CALL_END, TYPING
 */
public class AegisTalkTCPServer {
    
    private final int port;
    private final ObjectMapper mapper = new ObjectMapper();
    
    // Quản lý kết nối: userId -> ClientHandler
    private final Map<Long, ClientHandler> connectedClients = new ConcurrentHashMap<>();
    
    // Quản lý phòng: roomId -> Set<ClientHandler>
    private final Map<String, CopyOnWriteArraySet<ClientHandler>> roomClients = new ConcurrentHashMap<>();
    
    public AegisTalkTCPServer(int port) {
        this.port = port;
    }
    
    public void start() throws IOException {
        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("[AegisTalkTCPServer] TCP Server listening on port " + port);
            System.out.println("[AegisTalkTCPServer] Áp dụng: TCP Socket - Bài TCP");
            
            while (true) {
                Socket socket = server.accept();
                System.out.println("[AegisTalkTCPServer] New client from " + socket.getRemoteSocketAddress());
                
                ClientHandler handler = new ClientHandler(socket);
                Thread t = new Thread(handler, "TCPClient-" + socket.getPort());
                t.start();
            }
        }
    }
    
    /**
     * Gửi message đến một user cụ thể.
     */
    private void sendToUser(Long userId, ChatMessage message) {
        ClientHandler handler = connectedClients.get(userId);
        if (handler != null) {
            handler.send(message);
        }
    }
    
    /**
     * Broadcast message đến tất cả client trong room.
     */
    private void broadcastToRoom(String roomId, ChatMessage message, ClientHandler from) {
        CopyOnWriteArraySet<ClientHandler> clients = roomClients.get(roomId);
        if (clients != null) {
            try {
                String json = mapper.writeValueAsString(message);
                for (ClientHandler c : clients) {
                    if (c != from) {
                        c.sendRaw(json);
                    }
                }
            } catch (Exception e) {
                System.err.println("[AegisTalkTCPServer] Error broadcasting: " + e.getMessage());
            }
        }
    }
    
    /**
     * Thread xử lý từng client.
     */
    private class ClientHandler implements Runnable {
        private final Socket socket;
        private final BufferedReader in;
        private final BufferedWriter out;
        private Long userId;
        private String currentRoom;
        
        ClientHandler(Socket socket) throws IOException {
            this.socket = socket;
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
        }
        
        @Override
        public void run() {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    try {
                        TCPMessage tcpMsg = mapper.readValue(line, TCPMessage.class);
                        handleMessage(tcpMsg);
                    } catch (Exception e) {
                        System.err.println("[AegisTalkTCPServer] Error parsing message: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                System.err.println("[AegisTalkTCPServer] Client disconnected: " + e.getMessage());
            } finally {
                disconnect();
            }
        }
        
        private void handleMessage(TCPMessage msg) throws IOException {
            switch (msg.type()) {
                case "LOGIN":
                    handleLogin(msg);
                    break;
                case "CHAT":
                    handleChat(msg);
                    break;
                case "JOIN_ROOM":
                    handleJoinRoom(msg);
                    break;
                case "LEAVE_ROOM":
                    handleLeaveRoom();
                    break;
                case "CALL_OFFER":
                case "CALL_ANSWER":
                case "CALL_ICE":
                case "CALL_END":
                    handleCallSignaling(msg);
                    break;
                case "TYPING":
                    handleTyping(msg);
                    break;
                default:
                    System.err.println("[AegisTalkTCPServer] Unknown message type: " + msg.type());
            }
        }
        
        private void handleLogin(TCPMessage msg) {
            try {
                Long id = Long.parseLong(msg.data().get("userId"));
                this.userId = id;
                connectedClients.put(userId, this);
                sendResponse("LOGIN_OK", Map.of("userId", id.toString()));
                System.out.println("[AegisTalkTCPServer] User " + userId + " logged in");
            } catch (Exception e) {
                sendResponse("LOGIN_FAIL", Map.of("error", e.getMessage()));
            }
        }
        
        private void handleChat(TCPMessage msg) {
            try {
                String roomId = msg.data().get("roomId");
                String from = msg.data().get("from");
                String text = msg.data().get("text");
                
                ChatMessage chatMsg = ChatMessage.text(roomId, from, text);
                
                // Broadcast đến room
                broadcastToRoom(roomId, chatMsg, this);
                
                // Lưu vào database (có thể gọi qua RMI ChatService)
                System.out.println("[AegisTalkTCPServer] Chat message in room " + roomId + " from " + from);
            } catch (Exception e) {
                System.err.println("[AegisTalkTCPServer] Error handling chat: " + e.getMessage());
            }
        }
        
        private void handleJoinRoom(TCPMessage msg) {
            String roomId = msg.data().get("roomId");
            if (roomId != null) {
                roomClients.computeIfAbsent(roomId, k -> new CopyOnWriteArraySet<>()).add(this);
                this.currentRoom = roomId;
                System.out.println("[AegisTalkTCPServer] User " + userId + " joined room " + roomId);
            }
        }
        
        private void handleLeaveRoom() {
            if (currentRoom != null) {
                CopyOnWriteArraySet<ClientHandler> clients = roomClients.get(currentRoom);
                if (clients != null) {
                    clients.remove(this);
                }
                this.currentRoom = null;
            }
        }
        
        private void handleCallSignaling(TCPMessage msg) {
            // Forward signaling message đến user đích
            String toUserId = msg.data().get("toUserId");
            if (toUserId != null) {
                try {
                    Long toId = Long.parseLong(toUserId);
                    String payload = mapper.writeValueAsString(msg.data());
                    sendToUser(toId, new ChatMessage(
                            "call",
                            userId.toString(),
                            MessageType.CALL_EVENT,
                            msg.type(),
                            payload,
                            System.currentTimeMillis()
                    ));
                } catch (Exception e) {
                    System.err.println("[AegisTalkTCPServer] Error handling call signaling: " + e.getMessage());
                }
            }
        }
        
        private void handleTyping(TCPMessage msg) {
            String roomId = msg.data().get("roomId");
            String toUserId = msg.data().get("toUserId");
            if (toUserId != null) {
                Long toId = Long.parseLong(toUserId);
                sendToUser(toId, new ChatMessage(
                        roomId,
                        userId.toString(),
                        MessageType.SYSTEM,
                        "TYPING",
                        null,
                        System.currentTimeMillis()
                ));
            }
        }
        
        void send(ChatMessage message) {
            try {
                String json = mapper.writeValueAsString(message);
                sendRaw(json);
            } catch (Exception e) {
                System.err.println("[AegisTalkTCPServer] Error sending message: " + e.getMessage());
            }
        }
        
        void sendRaw(String json) {
            try {
                out.write(json);
                out.write("\n");
                out.flush();
            } catch (IOException e) {
                System.err.println("[AegisTalkTCPServer] Error sending raw: " + e.getMessage());
            }
        }
        
        void sendResponse(String type, Map<String, String> data) {
            try {
                TCPMessage response = new TCPMessage(type, data);
                String json = mapper.writeValueAsString(response);
                sendRaw(json);
            } catch (Exception e) {
                System.err.println("[AegisTalkTCPServer] Error sending response: " + e.getMessage());
            }
        }
        
        private void disconnect() {
            if (userId != null) {
                connectedClients.remove(userId);
            }
            handleLeaveRoom();
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }
    
    /**
     * Message format cho TCP communication.
     */
    public record TCPMessage(String type, Map<String, String> data) {}
    
    public static void main(String[] args) throws IOException {
        AegisTalkTCPServer server = new AegisTalkTCPServer(5555);
        server.start();
    }
}

