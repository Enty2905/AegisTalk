package org.example.demo2.dao;

import org.example.demo2.model.FileInfo;

import java.sql.*;
import java.time.LocalDateTime;

public class FileDao {

    // Dùng DBTest.getConnection() giống ModerationLogDao
    private Connection getConnection() throws SQLException {
        return org.example.demo2.ui.DBTest.getConnection();
    }

    public FileInfo findById(long id) throws SQLException {
        String sql = """
                SELECT id, uploader_id, filename, content_type,
                       size_bytes, sha256, storage_path, etag, created_at
                FROM files
                WHERE id = ?
                """;
        try (Connection c = getConnection();
             PreparedStatement st = c.prepareStatement(sql)) {
            st.setLong(1, id);
            try (ResultSet rs = st.executeQuery()) {
                if (!rs.next()) return null;
                return mapRow(rs);
            }
        }
    }

    public FileInfo findBySha256(String sha256) throws SQLException {
        String sql = """
                SELECT id, uploader_id, filename, content_type,
                       size_bytes, sha256, storage_path, etag, created_at
                FROM files
                WHERE sha256 = ?
                """;
        try (Connection c = getConnection();
             PreparedStatement st = c.prepareStatement(sql)) {
            st.setString(1, sha256);
            try (ResultSet rs = st.executeQuery()) {
                if (!rs.next()) return null;
                return mapRow(rs);
            }
        }
    }

    public long insert(Long uploaderId,
                       String filename,
                       String contentType,
                       long sizeBytes,
                       String sha256,
                       String storagePath,
                       String etag) throws SQLException {
        String sql = """
                INSERT INTO files
                    (uploader_id, filename, content_type, size_bytes,
                     sha256, storage_path, etag, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
                """;
        try (Connection c = getConnection();
             PreparedStatement st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            // uploader_id bắt buộc phải có giá trị (NOT NULL trong DB)
            st.setLong(1, uploaderId != null ? uploaderId : 1L);

            st.setString(2, filename);
            st.setString(3, contentType);
            st.setLong(4, sizeBytes);
            st.setString(5, sha256);
            st.setString(6, storagePath);
            st.setString(7, etag);

            st.executeUpdate();
            try (ResultSet rs = st.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("Insert file failed, no ID returned");
    }

    private FileInfo mapRow(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        long uploaderIdVal = rs.getLong("uploader_id");
        Long uploaderId = rs.wasNull() ? null : uploaderIdVal;

        String filename = rs.getString("filename");
        String contentType = rs.getString("content_type");
        long sizeBytes = rs.getLong("size_bytes");
        String sha256 = rs.getString("sha256");
        String storagePath = rs.getString("storage_path");
        String etag = rs.getString("etag");

        return new FileInfo(id, uploaderId, filename, contentType,
                sizeBytes, sha256, storagePath, etag);
    }
}
