package org.example.demo2.service.rmi;

import org.example.demo2.dao.UserDao;
import org.example.demo2.model.User;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Implementation của AuthService sử dụng RMI.
 * 
 * Áp dụng: RMI (Remote Method Invocation) - Bài RMI
 * - Kế thừa UnicastRemoteObject để export remote object
 * - Client có thể gọi method từ xa qua network
 */
public class AuthServiceImpl extends UnicastRemoteObject implements AuthService {
    
    private final UserDao userDao = new UserDao();
    private final ConcurrentMap<Long, Long> activeSessions = new ConcurrentHashMap<>(); // userId -> lastAccessTime
    
    public AuthServiceImpl() throws RemoteException {
        super();
    }
    
    @Override
    public User register(String username, String password, String displayName) throws RemoteException {
        try {
            // Kiểm tra username đã tồn tại
            if (userDao.findByUsername(username) != null) {
                System.out.println("[AuthService] Register failed: Username already exists: " + username);
                return null;
            }
            
            // Hash password
            String passwordHash = hashPassword(password);
            System.out.println("[AuthService] Register: username=" + username + ", hash=" + passwordHash.substring(0, Math.min(32, passwordHash.length())) + "...");
            
            // Tạo user mới
            User user = userDao.create(username, passwordHash, displayName);
            if (user != null) {
                activeSessions.put(user.id(), System.currentTimeMillis());
                System.out.println("[AuthService] Register successful: user ID=" + user.id() + ", username=" + username);
            }
            return user;
        } catch (SQLException e) {
            throw new RemoteException("Database error: " + e.getMessage(), e);
        }
    }
    
    @Override
    public User login(String username, String password) throws RemoteException {
        try {
            User user = userDao.findByUsername(username);
            if (user == null) {
                System.out.println("[AuthService] User not found: " + username);
                return null;
            }
            
            // Kiểm tra password
            String storedHash = userDao.getPasswordHash(user.id());
            String inputHash = hashPassword(password);
            
            // Debug: in ra để kiểm tra
            System.out.println("[AuthService] Login attempt for: " + username);
            System.out.println("[AuthService] User ID: " + user.id());
            System.out.println("[AuthService] Stored hash length: " + (storedHash != null ? storedHash.length() : 0));
            System.out.println("[AuthService] Input hash length: " + (inputHash != null ? inputHash.length() : 0));
            if (storedHash != null && inputHash != null) {
                System.out.println("[AuthService] Stored hash (first 32): " + storedHash.substring(0, Math.min(32, storedHash.length())));
                System.out.println("[AuthService] Input hash (first 32): " + inputHash.substring(0, Math.min(32, inputHash.length())));
                System.out.println("[AuthService] Hashes match: " + storedHash.equals(inputHash));
            }
            
            if (storedHash == null) {
                System.out.println("[AuthService] ERROR: Stored hash is NULL for user: " + username);
                return null;
            }
            
            if (!storedHash.equals(inputHash)) {
                System.out.println("[AuthService] Password mismatch for: " + username);
                System.out.println("[AuthService] Full stored hash: " + storedHash);
                System.out.println("[AuthService] Full input hash: " + inputHash);
                return null;
            }
            
            // Tạo session
            activeSessions.put(user.id(), System.currentTimeMillis());
            System.out.println("[AuthService] Login successful for: " + username);
            return user;
        } catch (SQLException e) {
            throw new RemoteException("Database error: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void logout(Long userId) throws RemoteException {
        activeSessions.remove(userId);
    }
    
    @Override
    public boolean isValidSession(Long userId) throws RemoteException {
        if (!activeSessions.containsKey(userId)) {
            return false;
        }
        // Kiểm tra session timeout (30 phút)
        long lastAccess = activeSessions.get(userId);
        if (System.currentTimeMillis() - lastAccess > 30 * 60 * 1000) {
            activeSessions.remove(userId);
            return false;
        }
        // Cập nhật last access time
        activeSessions.put(userId, System.currentTimeMillis());
        return true;
    }
    
    private String hashPassword(String password) throws RemoteException {
        try {
            if (password == null || password.isEmpty()) {
                throw new IllegalArgumentException("Password cannot be null or empty");
            }
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            // Đảm bảo dùng UTF-8 encoding để hash nhất quán
            byte[] passwordBytes = password.getBytes("UTF-8");
            byte[] digest = md.digest(passwordBytes);
            String hash = HexFormat.of().formatHex(digest);
            System.out.println("[AuthService] Hash password: input length=" + password.length() + ", hash length=" + hash.length() + ", hash (first 32)=" + hash.substring(0, Math.min(32, hash.length())) + "...");
            return hash;
        } catch (Exception e) {
            System.err.println("[AuthService] Password hashing error: " + e.getMessage());
            e.printStackTrace();
            throw new RemoteException("Password hashing error", e);
        }
    }
}

