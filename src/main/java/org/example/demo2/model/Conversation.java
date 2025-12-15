package org.example.demo2.model;

import java.io.Serializable;

/**
 * Conversation model - thay thế Room.
 * Mỗi cuộc trò chuyện (1-1 hoặc nhóm) = 1 conversation.
 * Phải implement Serializable để truyền qua RMI.
 */
public record Conversation(
        Long id,
        String type,              // "DIRECT" hoặc "GROUP"
        String title,             // Tên nhóm, với DIRECT thì null
        Long createdBy,           // ID user tạo cuộc trò chuyện
        Long lastMessageId,       // ID tin nhắn cuối cùng
        String extraSettings      // JSON: quyền, emoji, màu sắc, theme,...
) implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * Helper: Lấy tên hiển thị của conversation.
     * Với DIRECT: trả về null (sẽ lấy từ participant)
     * Với GROUP: trả về title
     */
    public String getDisplayName() {
        if ("GROUP".equals(type)) {
            return title;
        }
        return null; // DIRECT conversation - tên sẽ lấy từ participant
    }
}



