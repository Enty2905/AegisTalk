package org.example.demo2.service.rmi;

import org.example.demo2.model.ChatMessage;
import org.example.demo2.model.Conversation;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * RMI Service cho quản lý chat và conversation.
 * 
 * Áp dụng: RMI (Remote Method Invocation) - Bài RMI
 * - Client gọi method từ xa để lấy lịch sử chat, tạo conversation
 * - Trả về danh sách ChatMessage, Conversation qua network
 */
public interface ChatService extends Remote {
    
    /**
     * Lấy lịch sử tin nhắn của một conversation.
     * @param conversationId ID conversation
     * @param limit Số lượng tin nhắn tối đa
     * @return Danh sách tin nhắn
     * @throws RemoteException Lỗi RMI
     */
    List<ChatMessage> getMessageHistory(String conversationId, int limit) throws RemoteException;
    
    /**
     * Lấy danh sách conversation của user (bao gồm direct chat và group).
     * @param userId ID người dùng
     * @return Danh sách conversation
     * @throws RemoteException Lỗi RMI
     */
    List<Conversation> getUserConversations(Long userId) throws RemoteException;
    
    /**
     * Tạo conversation direct (1-1) giữa 2 user.
     * @param userId1 ID user 1
     * @param userId2 ID user 2
     * @return Conversation đã tạo hoặc đã tồn tại
     * @throws RemoteException Lỗi RMI
     */
    Conversation getOrCreateDirectConversation(Long userId1, Long userId2) throws RemoteException;
    
    /**
     * Lưu tin nhắn vào database.
     * @param message Tin nhắn cần lưu
     * @return true nếu thành công
     * @throws RemoteException Lỗi RMI
     */
    boolean saveMessage(ChatMessage message) throws RemoteException;
    
    /**
     * Lấy tin nhắn cuối cùng của conversation.
     * @param conversationId ID conversation
     * @return Tin nhắn cuối cùng hoặc null
     * @throws RemoteException Lỗi RMI
     */
    ChatMessage getLastMessage(String conversationId) throws RemoteException;
    
    /**
     * Lấy display name của user từ user ID.
     * @param userId ID người dùng
     * @return Display name hoặc user ID nếu không tìm thấy
     * @throws RemoteException Lỗi RMI
     */
    String getUserDisplayName(Long userId) throws RemoteException;

    /**
     * Xoá toàn bộ tin nhắn trong một conversation.
     */
    boolean deleteConversationMessages(String conversationId) throws RemoteException;
}



