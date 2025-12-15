package org.example.demo2.dao;

import org.example.demo2.model.Room;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO cho quản lý Room trong database.
 */
public class RoomDao {
    
    /**
     * Tìm room theo ID.
     */
    public Room findById(Long id) throws SQLException {
        try (Connection conn = org.example.demo2.ui.DBTest.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "SELECT id, name, type, created_by_user_id FROM rooms WHERE id=?")) {
            st.setLong(1, id);
            ResultSet rs = st.executeQuery();
            if (rs.next()) {
                return new Room(rs.getLong("id"), rs.getString("name"), 
                               rs.getString("type"), rs.getLong("created_by_user_id"));
            }
            return null;
        }
    }
    
    /**
     * Tạo room mới.
     */
    public Room create(String name, String type, Long createdByUserId) throws SQLException {
        try (Connection conn = org.example.demo2.ui.DBTest.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "INSERT INTO rooms(name, type, created_by_user_id) VALUES (?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            st.setString(1, name);
            st.setString(2, type);
            st.setLong(3, createdByUserId);
            st.executeUpdate();
            ResultSet rs = st.getGeneratedKeys();
            if (rs.next()) {
                return new Room(rs.getLong(1), name, type, createdByUserId);
            }
            return null;
        }
    }
    
    /**
     * Lấy danh sách room của user.
     */
    public List<Room> getUserRooms(Long userId) throws SQLException {
        List<Room> rooms = new ArrayList<>();
        try (Connection conn = org.example.demo2.ui.DBTest.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "SELECT DISTINCT r.id, r.name, r.type, r.created_by_user_id " +
                     "FROM rooms r " +
                     "LEFT JOIN room_members rm ON r.id = rm.room_id " +
                     "WHERE r.created_by_user_id=? OR rm.user_id=?")) {
            st.setLong(1, userId);
            st.setLong(2, userId);
            ResultSet rs = st.executeQuery();
            while (rs.next()) {
                rooms.add(new Room(rs.getLong("id"), rs.getString("name"), 
                                  rs.getString("type"), rs.getLong("created_by_user_id")));
            }
        }
        return rooms;
    }
    
    /**
     * Tìm direct room giữa 2 user.
     */
    public Room findDirectRoom(Long userId1, Long userId2) throws SQLException {
        try (Connection conn = org.example.demo2.ui.DBTest.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "SELECT r.id, r.name, r.type, r.created_by_user_id " +
                     "FROM rooms r " +
                     "JOIN room_members rm1 ON r.id = rm1.room_id AND rm1.user_id=? " +
                     "JOIN room_members rm2 ON r.id = rm2.room_id AND rm2.user_id=? " +
                     "WHERE r.type='DIRECT'")) {
            st.setLong(1, userId1);
            st.setLong(2, userId2);
            ResultSet rs = st.executeQuery();
            if (rs.next()) {
                return new Room(rs.getLong("id"), rs.getString("name"), 
                               rs.getString("type"), rs.getLong("created_by_user_id"));
            }
            return null;
        }
    }
    
    /**
     * Thêm member vào room.
     */
    public boolean addMember(Long roomId, Long userId) throws SQLException {
        try (Connection conn = org.example.demo2.ui.DBTest.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "INSERT INTO room_members(room_id, user_id) VALUES (?, ?) " +
                     "ON DUPLICATE KEY UPDATE room_id=room_id")) {
            st.setLong(1, roomId);
            st.setLong(2, userId);
            return st.executeUpdate() > 0;
        }
    }
    
    /**
     * Xóa member khỏi room.
     */
    public boolean removeMember(Long roomId, Long userId) throws SQLException {
        try (Connection conn = org.example.demo2.ui.DBTest.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "DELETE FROM room_members WHERE room_id=? AND user_id=?")) {
            st.setLong(1, roomId);
            st.setLong(2, userId);
            return st.executeUpdate() > 0;
        }
    }
}





