package org.example.demo2.dao;

import org.example.demo2.model.ChatMessage;
import org.example.demo2.model.MessageType;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO cho quản lý ChatMessage trong database.
 * Cập nhật để dùng conversation_id thay vì room_id.
 */
public class MessageDao {
    
    /**
     * Lưu tin nhắn và trả về ID của message vừa tạo.
     */
    public Long save(ChatMessage message) throws SQLException {
        try (Connection conn = org.example.demo2.ui.DBTest.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "INSERT INTO messages(conversation_id, sender_id, type, content_text, content_payload, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            st.setLong(1, Long.parseLong(message.room())); // conversation_id
            
            // Xử lý sender_id: nếu là "System" thì dùng NULL hoặc user đặc biệt
            // Nếu là user ID (số) thì parse, nếu không thì tìm user từ display_name
            Long senderId = null;
            if ("System".equals(message.from())) {
                // System message: có thể dùng NULL hoặc user đặc biệt
                // Tạm thời dùng NULL (cần sửa schema để cho phép NULL)
                // Hoặc tìm user có username = "system"
                try {
                    org.example.demo2.dao.UserDao userDao = new org.example.demo2.dao.UserDao();
                    org.example.demo2.model.User systemUser = userDao.findByUsername("system");
                    if (systemUser != null) {
                        senderId = systemUser.id();
                    } else {
                        // Nếu không có system user, dùng user đầu tiên (tạm thời)
                        // Hoặc để NULL nếu schema cho phép
                        senderId = 1L; // Tạm thời
                    }
                } catch (Exception e) {
                    senderId = 1L; // Fallback
                }
            } else {
                try {
                    senderId = Long.parseLong(message.from());
                } catch (NumberFormatException e) {
                    // Nếu không phải số, tìm user từ display_name
                    org.example.demo2.dao.UserDao userDao = new org.example.demo2.dao.UserDao();
                    // Tìm user theo display_name (cần thêm method trong UserDao)
                    // Tạm thời: giả sử msg.from() luôn là user ID
                    throw new SQLException("Invalid sender ID: " + message.from());
                }
            }
            
            st.setLong(2, senderId);
            st.setString(3, message.type().name());
            st.setString(4, message.text()); // content_text
            // content_payload: nếu có payloadRef, lưu dưới dạng JSON
            String payload = message.payloadRef() != null ? "{\"ref\":\"" + message.payloadRef() + "\"}" : null;
            st.setString(5, payload);
            st.setTimestamp(6, new Timestamp(message.ts()));
            
            // Debug log
            if (message.type().name().equals("FILE") || message.type().name().equals("IMAGE")) {
                System.out.println("[MessageDao] Saving " + message.type().name() + " message: payloadRef=" + message.payloadRef() + ", payload=" + payload);
            }
            
            st.executeUpdate();
            
            // Lấy ID vừa tạo
            ResultSet rs = st.getGeneratedKeys();
            if (rs.next()) {
                Long messageId = rs.getLong(1);
                
                // Cập nhật last_message_id trong conversations
                ConversationDao convDao = new ConversationDao();
                convDao.updateLastMessage(Long.parseLong(message.room()), messageId);
                
                return messageId;
            }
            return null;
        }
    }
    
    /**
     * Lấy lịch sử tin nhắn của conversation.
     */
    public List<ChatMessage> getHistory(String conversationId, int limit) throws SQLException {
        List<ChatMessage> messages = new ArrayList<>();
        try (Connection conn = org.example.demo2.ui.DBTest.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "SELECT m.id, m.conversation_id, m.sender_id, m.type, m.content_text, m.content_payload, m.created_at, " +
                     "       u.display_name " +
                     "FROM messages m " +
                     "LEFT JOIN users u ON m.sender_id = u.id " +
                     "WHERE m.conversation_id=? AND m.is_deleted=FALSE " +
                     "ORDER BY m.created_at DESC LIMIT ?")) {
            st.setLong(1, Long.parseLong(conversationId));
            st.setInt(2, limit);
            ResultSet rs = st.executeQuery();
            while (rs.next()) {
                // Luôn dùng sender_id (user ID) trong from field để nhất quán với TCP messages
                String from = rs.getLong("sender_id") + "";
                
                // Parse content_payload nếu có
                String payloadRef = null;
                String payloadJson = rs.getString("content_payload");
                String msgType = rs.getString("type");
                if (payloadJson != null && payloadJson.contains("\"ref\"")) {
                    // Extract ref từ JSON - hỗ trợ cả có và không có space sau dấu :
                    // Format có thể là: {"ref":"value"} hoặc {"ref": "value"}
                    int refIndex = payloadJson.indexOf("\"ref\"");
                    if (refIndex >= 0) {
                        // Tìm dấu : sau "ref"
                        int colonIndex = payloadJson.indexOf(":", refIndex);
                        if (colonIndex >= 0) {
                            // Tìm dấu " mở đầu value (bỏ qua spaces)
                            int startQuote = payloadJson.indexOf("\"", colonIndex + 1);
                            if (startQuote >= 0) {
                                // Tìm dấu " kết thúc value
                                int endQuote = payloadJson.indexOf("\"", startQuote + 1);
                                if (endQuote > startQuote) {
                                    payloadRef = payloadJson.substring(startQuote + 1, endQuote);
                                }
                            }
                        }
                    }
                }
                
                // Debug log cho FILE/IMAGE messages
                if ("FILE".equals(msgType) || "IMAGE".equals(msgType)) {
                    System.out.println("[MessageDao] Loading " + msgType + " message: payloadJson=" + payloadJson + ", payloadRef=" + payloadRef);
                }
                
                messages.add(new ChatMessage(
                        rs.getLong("conversation_id") + "", // conversation_id
                        from, // user ID (sender_id)
                        MessageType.valueOf(rs.getString("type")),
                        rs.getString("content_text"),
                        payloadRef,
                        rs.getTimestamp("created_at").getTime()
                ));
            }
        }
        // Đảo ngược để có thứ tự từ cũ đến mới
        List<ChatMessage> reversed = new ArrayList<>();
        for (int i = messages.size() - 1; i >= 0; i--) {
            reversed.add(messages.get(i));
        }
        return reversed;
    }
    
    /**
     * Lấy message theo ID.
     */
    public ChatMessage findById(Long messageId) throws SQLException {
        try (Connection conn = org.example.demo2.ui.DBTest.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "SELECT m.id, m.conversation_id, m.sender_id, m.type, m.content_text, m.content_payload, m.created_at, " +
                     "       u.display_name " +
                     "FROM messages m " +
                     "LEFT JOIN users u ON m.sender_id = u.id " +
                     "WHERE m.id=?")) {
            st.setLong(1, messageId);
            ResultSet rs = st.executeQuery();
            if (rs.next()) {
                // Luôn dùng sender_id (user ID) trong from field
                String from = rs.getLong("sender_id") + "";
                
                String payloadRef = null;
                String payloadJson = rs.getString("content_payload");
                if (payloadJson != null && payloadJson.contains("\"ref\"")) {
                    // Extract ref từ JSON - hỗ trợ cả có và không có space sau dấu :
                    int refIndex = payloadJson.indexOf("\"ref\"");
                    if (refIndex >= 0) {
                        int colonIndex = payloadJson.indexOf(":", refIndex);
                        if (colonIndex >= 0) {
                            int startQuote = payloadJson.indexOf("\"", colonIndex + 1);
                            if (startQuote >= 0) {
                                int endQuote = payloadJson.indexOf("\"", startQuote + 1);
                                if (endQuote > startQuote) {
                                    payloadRef = payloadJson.substring(startQuote + 1, endQuote);
                                }
                            }
                        }
                    }
                }
                
                return new ChatMessage(
                        rs.getLong("conversation_id") + "",
                        from,
                        MessageType.valueOf(rs.getString("type")),
                        rs.getString("content_text"),
                        payloadRef,
                        rs.getTimestamp("created_at").getTime()
                );
            }
            return null;
        }
    }
}
