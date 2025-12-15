package org.example.demo2.service.rmi;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * RMI Server để đăng ký các service.
 * 
 * Áp dụng: RMI (Remote Method Invocation) - Bài RMI
 * - Tạo RMI Registry trên port 1099
 * - Đăng ký các remote service (AuthService, FriendService, ChatService, GroupService)
 * - Client có thể lookup và sử dụng các service này từ xa
 * 
 * Áp dụng: SOA (Service-Oriented Architecture) - Bài SOA
 * - Tổ chức kiến trúc theo kiểu hướng dịch vụ
 * - Mỗi service có contract rõ ràng (interface)
 * - Các service lỏng lẻo (loose coupling), dễ mở rộng
 */
public class RMIServiceServer {
    
    private static final int RMI_PORT = 1099;
    private static CallServiceImpl callServiceInstance;
    
    public static void main(String[] args) {
        try {
            // Lấy địa chỉ IP LAN để client từ xa có thể kết nối
            String serverHost = org.example.demo2.net.udp.VideoStreamClient.getLocalLanAddress();
            
            // QUAN TRỌNG: Set system property để RMI trả về đúng hostname cho client
            // Nếu không set, RMI có thể trả về localhost và client từ xa không thể kết nối
            System.setProperty("java.rmi.server.hostname", serverHost);
            System.out.println("[RMIServiceServer] Set java.rmi.server.hostname=" + serverHost);
            
            // Tạo RMI Registry
            Registry registry = LocateRegistry.createRegistry(RMI_PORT);
            System.out.println("[RMIServiceServer] RMI Registry created on port " + RMI_PORT);
            
            // Tạo và đăng ký các service
            AuthService authService = new AuthServiceImpl();
            registry.rebind("AuthService", authService);
            System.out.println("[RMIServiceServer] AuthService registered");
            
            FriendService friendService = new FriendServiceImpl();
            registry.rebind("FriendService", friendService);
            System.out.println("[RMIServiceServer] FriendService registered");
            
            ChatService chatService = new ChatServiceImpl();
            registry.rebind("ChatService", chatService);
            System.out.println("[RMIServiceServer] ChatService registered");
            
            GroupService groupService = new GroupServiceImpl();
            registry.rebind("GroupService", groupService);
            System.out.println("[RMIServiceServer] GroupService registered");
            
            callServiceInstance = new CallServiceImpl();
            registry.rebind("CallService", callServiceInstance);
            System.out.println("[RMIServiceServer] CallService registered");
            
            System.out.println("[RMIServiceServer] All services registered. Server ready.");
            System.out.println("[RMIServiceServer] Clients can connect using: rmi://" + serverHost + ":" + RMI_PORT + "/<ServiceName>");
            
        } catch (Exception e) {
            System.err.println("[RMIServiceServer] Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Lấy instance của CallServiceImpl để có thể set VideoStreamServer.
     * Chỉ dùng trong nội bộ server.
     */
    public static CallServiceImpl getCallServiceInstance() {
        return callServiceInstance;
    }
}



