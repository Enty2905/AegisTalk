package org.example.demo2.dao;

import java.sql.*;

/**
 * DAO cho quản lý MessageStatus trong database.
 * Track trạng thái gửi/đọc (SENT, DELIVERED, READ) của tin nhắn.
 */
public class MessageStatusDao {
    
    /**
     * Tạo hoặc cập nhật status của message cho user.
     */
    public boolean setStatus(Long messageId, Long userId, String status) throws SQLException {
        try (Connection conn = org.example.demo2.ui.DBTest.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "INSERT INTO message_status(message_id, user_id, status) " +
                     "VALUES (?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE status=VALUES(status), updated_at=NOW()")) {
            st.setLong(1, messageId);
            st.setLong(2, userId);
            st.setString(3, status);
            return st.executeUpdate() > 0;
        }
    }
    
    /**
     * Lấy status của message cho user.
     */
    public String getStatus(Long messageId, Long userId) throws SQLException {
        try (Connection conn = org.example.demo2.ui.DBTest.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "SELECT status FROM message_status WHERE message_id=? AND user_id=?")) {
            st.setLong(1, messageId);
            st.setLong(2, userId);
            ResultSet rs = st.executeQuery();
            if (rs.next()) {
                return rs.getString("status");
            }
            return "SENT"; // Mặc định
        }
    }
    
    /**
     * Đánh dấu message là DELIVERED cho user.
     */
    public boolean markDelivered(Long messageId, Long userId) throws SQLException {
        return setStatus(messageId, userId, "DELIVERED");
    }
    
    /**
     * Đánh dấu message là READ cho user.
     */
    public boolean markRead(Long messageId, Long userId) throws SQLException {
        return setStatus(messageId, userId, "READ");
    }
    
    /**
     * Lấy số lượng user đã đọc message.
     */
    public int getReadCount(Long messageId) throws SQLException {
        try (Connection conn = org.example.demo2.ui.DBTest.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "SELECT COUNT(*) FROM message_status WHERE message_id=? AND status='READ'")) {
            st.setLong(1, messageId);
            ResultSet rs = st.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        }
    }
}



