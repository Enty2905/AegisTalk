package org.example.demo2.service.rmi;

import org.example.demo2.dao.ConversationDao;
import org.example.demo2.dao.MessageDao;
import org.example.demo2.dao.UserDao;
import org.example.demo2.model.ChatMessage;
import org.example.demo2.model.Conversation;
import org.example.demo2.model.User;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.SQLException;
import java.util.List;

/**
 * Implementation của ChatService sử dụng RMI.
 * 
 * Áp dụng: RMI (Remote Method Invocation) - Bài RMI
 * - Kế thừa UnicastRemoteObject để export remote object
 * - Client gọi method từ xa để lấy lịch sử chat, tạo conversation
 */
public class ChatServiceImpl extends UnicastRemoteObject implements ChatService {
    
    private final MessageDao messageDao = new MessageDao();
    private final ConversationDao conversationDao = new ConversationDao();
    private final UserDao userDao = new UserDao();
    
    public ChatServiceImpl() throws RemoteException {
        super();
    }
    
    @Override
    public List<ChatMessage> getMessageHistory(String conversationId, int limit) throws RemoteException {
        try {
            return messageDao.getHistory(conversationId, limit);
        } catch (SQLException e) {
            throw new RemoteException("Database error: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<Conversation> getUserConversations(Long userId) throws RemoteException {
        try {
            return conversationDao.getUserConversations(userId);
        } catch (SQLException e) {
            throw new RemoteException("Database error: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Conversation getOrCreateDirectConversation(Long userId1, Long userId2) throws RemoteException {
        try {
            // Tìm conversation đã tồn tại
            Conversation existing = conversationDao.findDirectConversation(userId1, userId2);
            if (existing != null) {
                return existing;
            }
            
            // Tạo conversation mới (DIRECT, title = null)
            Conversation conv = conversationDao.create("DIRECT", null, userId1);
            if (conv != null) {
                // Thêm cả 2 user vào conversation với role MEMBER
                conversationDao.addParticipant(conv.id(), userId1, "MEMBER");
                conversationDao.addParticipant(conv.id(), userId2, "MEMBER");
            }
            return conv;
        } catch (SQLException e) {
            throw new RemoteException("Database error: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean saveMessage(ChatMessage message) throws RemoteException {
        try {
            Long messageId = messageDao.save(message);
            return messageId != null;
        } catch (SQLException e) {
            throw new RemoteException("Database error: " + e.getMessage(), e);
        }
    }
    
    @Override
    public ChatMessage getLastMessage(String conversationId) throws RemoteException {
        try {
            List<ChatMessage> messages = messageDao.getHistory(conversationId, 1);
            return messages.isEmpty() ? null : messages.get(messages.size() - 1);
        } catch (SQLException e) {
            throw new RemoteException("Database error: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String getUserDisplayName(Long userId) throws RemoteException {
        try {
            User user = userDao.findById(userId);
            return user != null ? user.displayName() : userId.toString();
        } catch (SQLException e) {
            throw new RemoteException("Database error: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean deleteConversationMessages(String conversationId) throws RemoteException {
        try {
            return messageDao.deleteByConversationId(conversationId);
        } catch (SQLException e) {
            throw new RemoteException("Database error: " + e.getMessage(), e);
        }
    }
}



