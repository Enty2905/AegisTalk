package org.example.demo2.client;

import org.example.demo2.config.ServerConfig;
import org.example.demo2.model.ChatMessage;
import org.example.demo2.model.Conversation;
import org.example.demo2.model.User;
import org.example.demo2.service.rmi.*;
import org.example.demo2.service.rmi.CallService;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;

/**
 * Client Service wrapper để tích hợp các RMI service vào UI.
 * 
 * Áp dụng: RMI (Remote Method Invocation) - Bài RMI
 * - Client lookup các remote service từ RMI registry
 * - Gọi method từ xa như gọi local method
 * 
 * Áp dụng: SOA (Service-Oriented Architecture) - Bài SOA
 * - Sử dụng các service đã được tổ chức theo kiến trúc hướng dịch vụ
 * - Mỗi service có contract rõ ràng (interface)
 */
public class AegisTalkClientService {
    
    private Registry registry;
    private AuthService authService;
    private FriendService friendService;
    private ChatService chatService;
    private GroupService groupService;
    private CallService callService;
    
    public AegisTalkClientService() throws RemoteException, NotBoundException {
        connect();
    }
    
    private void connect() throws RemoteException, NotBoundException {
        registry = LocateRegistry.getRegistry(ServerConfig.SERVER_HOST, ServerConfig.RMI_PORT);
        
        authService = (AuthService) registry.lookup("AuthService");
        friendService = (FriendService) registry.lookup("FriendService");
        chatService = (ChatService) registry.lookup("ChatService");
        groupService = (GroupService) registry.lookup("GroupService");
        callService = (CallService) registry.lookup("CallService");
        
        System.out.println("[AegisTalkClientService] Connected to RMI services at " + ServerConfig.SERVER_HOST + ":" + ServerConfig.RMI_PORT);
    }
    
    // ========== Auth Service ==========
    
    public User register(String username, String password, String displayName) throws RemoteException {
        return authService.register(username, password, displayName);
    }
    
    public User login(String username, String password) throws RemoteException {
        return authService.login(username, password);
    }
    
    public void logout(Long userId) throws RemoteException {
        authService.logout(userId);
    }
    
    public boolean isValidSession(Long userId) throws RemoteException {
        return authService.isValidSession(userId);
    }

    public User updateProfile(Long userId, String displayName, String avatarPath) throws RemoteException {
        return authService.updateProfile(userId, displayName, avatarPath);
    }

    public boolean changePassword(Long userId, String oldPassword, String newPassword) throws RemoteException {
        return authService.changePassword(userId, oldPassword, newPassword);
    }

    public User findUserById(Long userId) throws RemoteException {
        return authService.findById(userId);
    }

    public boolean isOnline(Long userId) throws RemoteException {
        return authService.isOnline(userId);
    }
    
    // ========== Friend Service ==========
    
    public List<User> searchUsers(String keyword) throws RemoteException {
        return friendService.searchUsers(keyword);
    }
    
    public boolean sendFriendRequest(Long fromUserId, Long toUserId) throws RemoteException {
        return friendService.sendFriendRequest(fromUserId, toUserId);
    }
    
    public boolean acceptFriendRequest(Long userId, Long friendId) throws RemoteException {
        return friendService.acceptFriendRequest(userId, friendId);
    }
    
    public List<User> getFriends(Long userId) throws RemoteException {
        return friendService.getFriends(userId);
    }
    
    public List<User> getPendingFriendRequests(Long userId) throws RemoteException {
        return friendService.getPendingFriendRequests(userId);
    }
    
    public boolean areFriends(Long userId1, Long userId2) throws RemoteException {
        return friendService.areFriends(userId1, userId2);
    }

    public boolean removeFriend(Long userId, Long friendId) throws RemoteException {
        return friendService.removeFriend(userId, friendId);
    }
    
    // ========== Chat Service ==========
    
    public List<ChatMessage> getMessageHistory(String conversationId, int limit) throws RemoteException {
        return chatService.getMessageHistory(conversationId, limit);
    }
    
    public List<Conversation> getUserConversations(Long userId) throws RemoteException {
        return chatService.getUserConversations(userId);
    }
    
    public Conversation getOrCreateDirectConversation(Long userId1, Long userId2) throws RemoteException {
        return chatService.getOrCreateDirectConversation(userId1, userId2);
    }
    
    public boolean saveMessage(ChatMessage message) throws RemoteException {
        return chatService.saveMessage(message);
    }
    
    public ChatMessage getLastMessage(String conversationId) throws RemoteException {
        return chatService.getLastMessage(conversationId);
    }
    
    public String getUserDisplayName(Long userId) throws RemoteException {
        return chatService.getUserDisplayName(userId);
    }

    public boolean deleteConversationMessages(String conversationId) throws RemoteException {
        return chatService.deleteConversationMessages(conversationId);
    }

    // ========== Group Service ==========
    
    public Conversation createGroup(String name, Long creatorId) throws RemoteException {
        return groupService.createGroup(name, creatorId);
    }
    
    public boolean addMember(Long groupId, Long userId, Long inviterId) throws RemoteException {
        return groupService.addMember(groupId, userId, inviterId);
    }
    
    public boolean removeMember(Long groupId, Long userId, Long removerId) throws RemoteException {
        return groupService.removeMember(groupId, userId, removerId);
    }
    
    public List<User> getGroupMembers(Long groupId) throws RemoteException {
        return groupService.getGroupMembers(groupId);
    }
    
    // ========== Call Service ==========
    
    public Integer inviteCall(Long callerId, Long calleeId) throws RemoteException {
        return callService.inviteCall(callerId, calleeId);
    }
    
    public boolean acceptCall(Integer callSessionId, Long userId) throws RemoteException {
        return callService.acceptCall(callSessionId, userId);
    }
    
    public boolean rejectCall(Integer callSessionId, Long userId) throws RemoteException {
        return callService.rejectCall(callSessionId, userId);
    }
    
    public boolean endCall(Integer callSessionId, Long userId) throws RemoteException {
        return callService.endCall(callSessionId, userId);
    }
    
    public boolean registerUdpEndpoint(Integer callSessionId, Long userId, String address, int port) throws RemoteException {
        return callService.registerUdpEndpoint(callSessionId, userId, address, port);
    }
    
    public CallService.CallInfo getCallInfo(Integer callSessionId) throws RemoteException {
        return callService.getCallInfo(callSessionId);
    }
    
    public List<CallService.CallInfo> getPendingCalls(Long userId) throws RemoteException {
        return callService.getPendingCalls(userId);
    }
}



