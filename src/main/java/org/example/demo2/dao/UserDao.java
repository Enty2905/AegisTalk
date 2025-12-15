package org.example.demo2.dao;

import org.example.demo2.model.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO cho quản lý User trong database.
 */
public class UserDao {
    
    /**
     * Tìm user theo ID.
     */
    public User findById(Long id) throws SQLException {
        try (Connection conn = org.example.demo2.ui.DBTest.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "SELECT id, username, display_name FROM users WHERE id=?")) {
            st.setLong(1, id);
            ResultSet rs = st.executeQuery();
            if (rs.next()) {
                return new User(rs.getLong("id"), rs.getString("username"), rs.getString("display_name"));
            }
            return null;
        }
    }
    
    /**
     * Tìm user theo username.
     */
    public User findByUsername(String username) throws SQLException {
        try (Connection conn = org.example.demo2.ui.DBTest.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "SELECT id, username, display_name FROM users WHERE username=?")) {
            st.setString(1, username);
            ResultSet rs = st.executeQuery();
            if (rs.next()) {
                return new User(rs.getLong("id"), rs.getString("username"), rs.getString("display_name"));
            }
            return null;
        }
    }
    
    /**
     * Tìm kiếm user theo keyword (username hoặc display_name).
     */
    public List<User> search(String keyword) throws SQLException {
        List<User> users = new ArrayList<>();
        String pattern = "%" + keyword + "%";
        try (Connection conn = org.example.demo2.ui.DBTest.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "SELECT id, username, display_name FROM users " +
                     "WHERE username LIKE ? OR display_name LIKE ? LIMIT 50")) {
            st.setString(1, pattern);
            st.setString(2, pattern);
            ResultSet rs = st.executeQuery();
            while (rs.next()) {
                users.add(new User(rs.getLong("id"), rs.getString("username"), rs.getString("display_name")));
            }
        }
        return users;
    }
    
    /**
     * Tạo user mới.
     */
    public User create(String username, String passwordHash, String displayName) throws SQLException {
        try (Connection conn = org.example.demo2.ui.DBTest.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "INSERT INTO users(username, password_hash, display_name) VALUES (?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            st.setString(1, username);
            st.setString(2, passwordHash);
            st.setString(3, displayName);
            st.executeUpdate();
            ResultSet rs = st.getGeneratedKeys();
            if (rs.next()) {
                return new User(rs.getLong(1), username, displayName);
            }
            return null;
        }
    }
    
    /**
     * Kiểm tra password hash.
     */
    public String getPasswordHash(Long userId) throws SQLException {
        try (Connection conn = org.example.demo2.ui.DBTest.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "SELECT password_hash FROM users WHERE id=?")) {
            st.setLong(1, userId);
            ResultSet rs = st.executeQuery();
            if (rs.next()) {
                return rs.getString("password_hash");
            }
            return null;
        }
    }
}





