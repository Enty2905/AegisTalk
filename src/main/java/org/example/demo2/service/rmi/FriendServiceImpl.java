package org.example.demo2.service.rmi;

import org.example.demo2.dao.FriendDao;
import org.example.demo2.dao.UserDao;
import org.example.demo2.model.User;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.SQLException;
import java.util.List;

/**
 * Implementation của FriendService sử dụng RMI.
 * 
 * Áp dụng: RMI (Remote Method Invocation) - Bài RMI
 * - Kế thừa UnicastRemoteObject để export remote object
 * - Client gọi method từ xa để quản lý bạn bè
 */
public class FriendServiceImpl extends UnicastRemoteObject implements FriendService {
    
    private final FriendDao friendDao = new FriendDao();
    private final UserDao userDao = new UserDao();
    
    public FriendServiceImpl() throws RemoteException {
        super();
    }
    
    @Override
    public List<User> searchUsers(String keyword) throws RemoteException {
        try {
            return userDao.search(keyword);
        } catch (SQLException e) {
            throw new RemoteException("Database error: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean sendFriendRequest(Long fromUserId, Long toUserId) throws RemoteException {
        try {
            if (fromUserId.equals(toUserId)) {
                return false;
            }
            return friendDao.sendFriendRequest(fromUserId, toUserId);
        } catch (SQLException e) {
            throw new RemoteException("Database error: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean cancelFriendRequest(Long fromUserId, Long toUserId) throws RemoteException {
        try {
            return friendDao.cancelFriendRequest(fromUserId, toUserId);
        } catch (SQLException e) {
            throw new RemoteException("Database error: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean acceptFriendRequest(Long userId, Long friendId) throws RemoteException {
        try {
            return friendDao.acceptFriendRequest(userId, friendId);
        } catch (SQLException e) {
            throw new RemoteException("Database error: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean rejectFriendRequest(Long userId, Long friendId) throws RemoteException {
        try {
            return friendDao.rejectFriendRequest(userId, friendId);
        } catch (SQLException e) {
            throw new RemoteException("Database error: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<User> getFriends(Long userId) throws RemoteException {
        try {
            return friendDao.getFriends(userId);
        } catch (SQLException e) {
            throw new RemoteException("Database error: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<User> getPendingFriendRequests(Long userId) throws RemoteException {
        try {
            return friendDao.getPendingFriendRequests(userId);
        } catch (SQLException e) {
            throw new RemoteException("Database error: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean areFriends(Long userId1, Long userId2) throws RemoteException {
        try {
            return friendDao.areFriends(userId1, userId2);
        } catch (SQLException e) {
            throw new RemoteException("Database error: " + e.getMessage(), e);
        }
    }
}





