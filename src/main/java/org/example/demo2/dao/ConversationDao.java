package org.example.demo2.dao;

import org.example.demo2.model.Conversation;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO cho quản lý Conversation trong database.
 * Thay thế RoomDao với schema mới.
 */
public class ConversationDao {
    
    /**
     * Tìm conversation theo ID.
     */
    public Conversation findById(Long id) throws SQLException {
        try (Connection conn = org.example.demo2.ui.DBTest.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "SELECT id, type, title, created_by, last_message_id, extra_settings " +
                     "FROM conversations WHERE id=?")) {
            st.setLong(1, id);
            ResultSet rs = st.executeQuery();
            if (rs.next()) {
                return new Conversation(
                    rs.getLong("id"),
                    rs.getString("type"),
                    rs.getString("title"),
                    rs.getLong("created_by"),
                    rs.getObject("last_message_id", Long.class),
                    rs.getString("extra_settings")
                );
            }
            return null;
        }
    }
    
    /**
     * Tạo conversation mới.
     */
    public Conversation create(String type, String title, Long createdBy) throws SQLException {
        try (Connection conn = org.example.demo2.ui.DBTest.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "INSERT INTO conversations(type, title, created_by) VALUES (?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            st.setString(1, type);
            st.setString(2, title);
            st.setLong(3, createdBy);
            st.executeUpdate();
            ResultSet rs = st.getGeneratedKeys();
            if (rs.next()) {
                Long id = rs.getLong(1);
                // Lấy lại conversation với đầy đủ thông tin
                return findById(id);
            }
            return null;
        }
    }
    
    /**
     * Lấy danh sách conversation của user (qua conversation_participants).
     */
    public List<Conversation> getUserConversations(Long userId) throws SQLException {
        List<Conversation> conversations = new ArrayList<>();
        try (Connection conn = org.example.demo2.ui.DBTest.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "SELECT c.id, c.type, c.title, c.created_by, c.last_message_id, c.extra_settings, c.updated_at " +
                     "FROM conversations c " +
                     "JOIN conversation_participants cp ON c.id = cp.conversation_id " +
                     "WHERE cp.user_id=? " +
                     "GROUP BY c.id, c.type, c.title, c.created_by, c.last_message_id, c.extra_settings, c.updated_at " +
                     "ORDER BY c.updated_at DESC")) {
            st.setLong(1, userId);
            ResultSet rs = st.executeQuery();
            while (rs.next()) {
                // Đọc updated_at nhưng không dùng (chỉ để ORDER BY)
                rs.getTimestamp("updated_at");
                conversations.add(new Conversation(
                    rs.getLong("id"),
                    rs.getString("type"),
                    rs.getString("title"),
                    rs.getLong("created_by"),
                    rs.getObject("last_message_id", Long.class),
                    rs.getString("extra_settings")
                ));
            }
        }
        return conversations;
    }
    
    /**
     * Tìm DIRECT conversation giữa 2 user.
     */
    public Conversation findDirectConversation(Long userId1, Long userId2) throws SQLException {
        try (Connection conn = org.example.demo2.ui.DBTest.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "SELECT c.id, c.type, c.title, c.created_by, c.last_message_id, c.extra_settings " +
                     "FROM conversations c " +
                     "JOIN conversation_participants cp1 ON c.id = cp1.conversation_id AND cp1.user_id=? " +
                     "JOIN conversation_participants cp2 ON c.id = cp2.conversation_id AND cp2.user_id=? " +
                     "WHERE c.type='DIRECT' " +
                     "LIMIT 1")) {
            st.setLong(1, userId1);
            st.setLong(2, userId2);
            ResultSet rs = st.executeQuery();
            if (rs.next()) {
                return new Conversation(
                    rs.getLong("id"),
                    rs.getString("type"),
                    rs.getString("title"),
                    rs.getLong("created_by"),
                    rs.getObject("last_message_id", Long.class),
                    rs.getString("extra_settings")
                );
            }
            return null;
        }
    }
    
    /**
     * Thêm participant vào conversation.
     */
    public boolean addParticipant(Long conversationId, Long userId, String role) throws SQLException {
        try (Connection conn = org.example.demo2.ui.DBTest.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "INSERT INTO conversation_participants(conversation_id, user_id, role) " +
                     "VALUES (?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE role=VALUES(role)")) {
            st.setLong(1, conversationId);
            st.setLong(2, userId);
            st.setString(3, role != null ? role : "MEMBER");
            return st.executeUpdate() > 0;
        }
    }
    
    /**
     * Xóa participant khỏi conversation.
     */
    public boolean removeParticipant(Long conversationId, Long userId) throws SQLException {
        try (Connection conn = org.example.demo2.ui.DBTest.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "DELETE FROM conversation_participants WHERE conversation_id=? AND user_id=?")) {
            st.setLong(1, conversationId);
            st.setLong(2, userId);
            return st.executeUpdate() > 0;
        }
    }
    
    /**
     * Lấy danh sách participants của conversation.
     */
    public List<Long> getParticipantIds(Long conversationId) throws SQLException {
        List<Long> userIds = new ArrayList<>();
        try (Connection conn = org.example.demo2.ui.DBTest.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "SELECT user_id FROM conversation_participants WHERE conversation_id=?")) {
            st.setLong(1, conversationId);
            ResultSet rs = st.executeQuery();
            while (rs.next()) {
                userIds.add(rs.getLong("user_id"));
            }
        }
        return userIds;
    }
    
    /**
     * Cập nhật last_message_id và updated_at khi có tin nhắn mới.
     */
    public boolean updateLastMessage(Long conversationId, Long messageId) throws SQLException {
        try (Connection conn = org.example.demo2.ui.DBTest.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "UPDATE conversations SET last_message_id=?, updated_at=NOW() WHERE id=?")) {
            st.setLong(1, messageId);
            st.setLong(2, conversationId);
            return st.executeUpdate() > 0;
        }
    }
}

