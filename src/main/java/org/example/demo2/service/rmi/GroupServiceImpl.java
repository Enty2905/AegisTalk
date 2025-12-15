package org.example.demo2.service.rmi;

import org.example.demo2.dao.ConversationDao;
import org.example.demo2.dao.UserDao;
import org.example.demo2.model.Conversation;
import org.example.demo2.model.User;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation của GroupService sử dụng RMI.
 * 
 * Áp dụng: RMI (Remote Method Invocation) - Bài RMI
 * - Kế thừa UnicastRemoteObject để export remote object
 * - Client gọi method từ xa để quản lý nhóm
 */
public class GroupServiceImpl extends UnicastRemoteObject implements GroupService {
    
    private final ConversationDao conversationDao = new ConversationDao();
    private final UserDao userDao = new UserDao();
    
    public GroupServiceImpl() throws RemoteException {
        super();
    }
    
    @Override
    public Conversation createGroup(String name, Long creatorId) throws RemoteException {
        try {
            // Tạo conversation với type GROUP
            Conversation conv = conversationDao.create("GROUP", name, creatorId);
            if (conv != null) {
                // Thêm creator vào nhóm với role OWNER
                conversationDao.addParticipant(conv.id(), creatorId, "OWNER");
            }
            return conv;
        } catch (SQLException e) {
            throw new RemoteException("Database error: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean addMember(Long groupId, Long userId, Long inviterId) throws RemoteException {
        try {
            // Kiểm tra conversation có tồn tại và là GROUP không
            Conversation conv = conversationDao.findById(groupId);
            if (conv == null || !"GROUP".equals(conv.type())) {
                return false;
            }
            // TODO: Kiểm tra inviter có quyền mời không (có thể kiểm tra role trong conversation_participants)
            return conversationDao.addParticipant(groupId, userId, "MEMBER");
        } catch (SQLException e) {
            throw new RemoteException("Database error: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean removeMember(Long groupId, Long userId, Long removerId) throws RemoteException {
        try {
            Conversation conv = conversationDao.findById(groupId);
            if (conv == null) {
                return false;
            }
            // Chỉ cho phép xóa nếu là owner/admin hoặc chính user đó
            if (!removerId.equals(conv.createdBy()) && !removerId.equals(userId)) {
                // TODO: Kiểm tra role của remover trong conversation_participants
                return false;
            }
            return conversationDao.removeParticipant(groupId, userId);
        } catch (SQLException e) {
            throw new RemoteException("Database error: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<User> getGroupMembers(Long groupId) throws RemoteException {
        try {
            List<Long> participantIds = conversationDao.getParticipantIds(groupId);
            List<User> members = new ArrayList<>();
            for (Long userId : participantIds) {
                User user = userDao.findById(userId);
                if (user != null) {
                    members.add(user);
                }
            }
            return members;
        } catch (SQLException e) {
            throw new RemoteException("Database error: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean updateGroupName(Long groupId, String newName, Long updaterId) throws RemoteException {
        try {
            Conversation conv = conversationDao.findById(groupId);
            if (conv == null || !"GROUP".equals(conv.type()) || !updaterId.equals(conv.createdBy())) {
                return false;
            }
            // TODO: Cần thêm method update trong ConversationDao
            // Tạm thời return false
            return false;
        } catch (SQLException e) {
            throw new RemoteException("Database error: " + e.getMessage(), e);
        }
    }
}



