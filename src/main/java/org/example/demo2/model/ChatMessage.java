package org.example.demo2.model;

import java.io.Serializable;

/**
 * ChatMessage model - phải implement Serializable để truyền qua RMI/TCP.
 */
public record ChatMessage(
        String room,          // room id hoặc name
        String from,          // username hoặc userId dưới dạng string
        MessageType type,
        String text,          // nội dung chính (văn bản, caption)
        String payloadRef,    // id file, url, hoặc callId
        long ts               // timestamp millis (System.currentTimeMillis)
) implements Serializable {
    private static final long serialVersionUID = 1L;
    /** Helper static: tạo nhanh 1 tin nhắn text bình thường */
    public static ChatMessage text(String room, String from, String text) {
        return new ChatMessage(
                room,
                from,
                MessageType.TEXT,
                text,
                null,
                System.currentTimeMillis()
        );
    }
}