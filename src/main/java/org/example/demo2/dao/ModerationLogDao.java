package org.example.demo2.dao;

import org.example.demo2.model.ModerationResult;
import org.example.demo2.model.PolicyCategory;
import org.example.demo2.ui.DBTest;

import java.sql.*;
import java.time.Instant;
import java.util.EnumSet;
import java.util.stream.Collectors;

public class ModerationLogDao {

    /**
     * Ghi log kết quả kiểm duyệt vào bảng moderation_logs.
     *
     * @param userId     id user (có thể null)
     * @param roomId     id room (có thể null)
     * @param messageId  id message (có thể null)
     * @param fileId     id file (có thể null)
     * @param targetType "TEXT" hoặc "IMAGE"
     * @param result     kết quả ModerationResult
     */
    public void insertLog(
            Long userId,
            Long roomId,
            Long messageId,
            Long fileId,
            String targetType,
            ModerationResult result
    ) {
        String sql = """
            INSERT INTO moderation_logs
                (user_id, room_id, message_id, file_id,
                 target_type, decision, categories, scores_json, hash, created_at)
            VALUES (?,?,?,?,?,?,?,?,?,?)
            """;

        try (Connection conn = DBTest.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            // 1) user_id, room_id, message_id, file_id
            if (userId == null) ps.setNull(1, Types.BIGINT); else ps.setLong(1, userId);
            if (roomId == null) ps.setNull(2, Types.BIGINT); else ps.setLong(2, roomId);
            if (messageId == null) ps.setNull(3, Types.BIGINT); else ps.setLong(3, messageId);
            if (fileId == null) ps.setNull(4, Types.BIGINT); else ps.setLong(4, fileId);

            // 2) target_type: TEXT / IMAGE
            ps.setString(5, targetType);

            // 3) decision: ALLOW / WARN / BLOCK
            ps.setString(6, result.getDecision().name());

            // 4) categories: lưu dạng "VIOLENCE,HARASSMENT"
            EnumSet<PolicyCategory> cats = result.getCategories();
            String catsStr = (cats == null || cats.isEmpty())
                    ? ""
                    : cats.stream().map(Enum::name)
                    .collect(Collectors.joining(","));
            ps.setString(7, catsStr);

            // 5) scores_json: vì không còn raw JSON, ta lưu tóm tắt maxScore + reason
            String scoresJson = String.format(
                    "{\"maxScore\": %.3f, \"reason\": \"%s\"}",
                    result.getMaxScore(),
                    escapeJson(result.getReason())
            );
            ps.setString(8, scoresJson);

            // 6) hash: hiện chưa dùng
            ps.setString(9, null);

            // 7) created_at
            ps.setTimestamp(10, Timestamp.from(Instant.now()));

            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[ModerationLogDao] Cannot insert log: " + e.getMessage());
        }
    }

    // Helper nhỏ tránh lỗi JSON khi reason có dấu " hoặc \
    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
