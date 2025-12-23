package org.example.demo2.dao;

import org.example.demo2.model.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO cho quản lý bạn bè trong database.
 */
public class FriendDao {
    
    /**
     * Gửi yêu cầu kết bạn.
     */
    public boolean sendFriendRequest(Long fromUserId, Long toUserId) throws SQLException {
        try (Connection conn = org.example.demo2.ui.DBTest.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "INSERT INTO friend_requests(from_user_id, to_user_id, status) VALUES (?, ?, 'PENDING') " +
                     "ON DUPLICATE KEY UPDATE status='PENDING'")) {
            st.setLong(1, fromUserId);
            st.setLong(2, toUserId);
            return st.executeUpdate() > 0;
        }
    }
    
    /**
     * Hủy yêu cầu kết bạn.
     */
    public boolean cancelFriendRequest(Long fromUserId, Long toUserId) throws SQLException {
        try (Connection conn = org.example.demo2.ui.DBTest.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "DELETE FROM friend_requests WHERE from_user_id=? AND to_user_id=?")) {
            st.setLong(1, fromUserId);
            st.setLong(2, toUserId);
            return st.executeUpdate() > 0;
        }
    }
    
    /**
     * Chấp nhận yêu cầu kết bạn.
     */
    public boolean acceptFriendRequest(Long userId, Long friendId) throws SQLException {
        try (Connection conn = org.example.demo2.ui.DBTest.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Xóa request
                try (PreparedStatement st1 = conn.prepareStatement(
                        "DELETE FROM friend_requests WHERE from_user_id=? AND to_user_id=?")) {
                    st1.setLong(1, friendId);
                    st1.setLong(2, userId);
                    st1.executeUpdate();
                }
                
                // Thêm vào bảng friends (2 chiều)
                try (PreparedStatement st2 = conn.prepareStatement(
                        "INSERT INTO friends(user_id, friend_id) VALUES (?, ?), (?, ?) " +
                        "ON DUPLICATE KEY UPDATE user_id=user_id")) {
                    st2.setLong(1, userId);
                    st2.setLong(2, friendId);
                    st2.setLong(3, friendId);
                    st2.setLong(4, userId);
                    st2.executeUpdate();
                }
                
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }
    
    /**
     * Từ chối yêu cầu kết bạn.
     */
    public boolean rejectFriendRequest(Long userId, Long friendId) throws SQLException {
        try (Connection conn = org.example.demo2.ui.DBTest.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "DELETE FROM friend_requests WHERE from_user_id=? AND to_user_id=?")) {
            st.setLong(1, friendId);
            st.setLong(2, userId);
            return st.executeUpdate() > 0;
        }
    }
    
    /**
     * Lấy danh sách bạn bè.
     */
    public List<User> getFriends(Long userId) throws SQLException {
        List<User> friends = new ArrayList<>();
        try (Connection conn = org.example.demo2.ui.DBTest.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "SELECT u.id, u.username, u.display_name, u.avatar_path FROM friends f " +
                     "JOIN users u ON f.friend_id = u.id " +
                     "WHERE f.user_id=?")) {
            st.setLong(1, userId);
            ResultSet rs = st.executeQuery();
            while (rs.next()) {
                friends.add(new User(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("display_name"),
                        rs.getString("avatar_path")));
            }
        }
        return friends;
    }
    
    /**
     * Lấy danh sách yêu cầu kết bạn đang chờ.
     */
    public List<User> getPendingFriendRequests(Long userId) throws SQLException {
        List<User> requests = new ArrayList<>();
        try (Connection conn = org.example.demo2.ui.DBTest.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "SELECT u.id, u.username, u.display_name, u.avatar_path FROM friend_requests fr " +
                     "JOIN users u ON fr.from_user_id = u.id " +
                     "WHERE fr.to_user_id=? AND fr.status='PENDING'")) {
            st.setLong(1, userId);
            ResultSet rs = st.executeQuery();
            while (rs.next()) {
                requests.add(new User(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("display_name"),
                        rs.getString("avatar_path")));
            }
        }
        return requests;
    }
    
    /**
     * Kiểm tra 2 user có phải bạn bè không.
     */
    public boolean areFriends(Long userId1, Long userId2) throws SQLException {
        try (Connection conn = org.example.demo2.ui.DBTest.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "SELECT COUNT(*) as cnt FROM friends " +
                     "WHERE (user_id=? AND friend_id=?) OR (user_id=? AND friend_id=?)")) {
            st.setLong(1, userId1);
            st.setLong(2, userId2);
            st.setLong(3, userId2);
            st.setLong(4, userId1);
            ResultSet rs = st.executeQuery();
            if (rs.next()) {
                return rs.getInt("cnt") > 0;
            }
            return false;
        }
    }

    /**
     * Huỷ kết bạn (xoá cả hai chiều và các request còn lại nếu có).
     */
    public boolean removeFriend(Long userId, Long friendId) throws SQLException {
        try (Connection conn = org.example.demo2.ui.DBTest.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Xoá quan hệ friends 2 chiều
                try (PreparedStatement st = conn.prepareStatement(
                        "DELETE FROM friends WHERE (user_id=? AND friend_id=?) OR (user_id=? AND friend_id=?)")) {
                    st.setLong(1, userId);
                    st.setLong(2, friendId);
                    st.setLong(3, friendId);
                    st.setLong(4, userId);
                    st.executeUpdate();
                }
                // Xoá các friend_requests còn lại giữa 2 người
                try (PreparedStatement st = conn.prepareStatement(
                        "DELETE FROM friend_requests WHERE (from_user_id=? AND to_user_id=?) OR (from_user_id=? AND to_user_id=?)")) {
                    st.setLong(1, userId);
                    st.setLong(2, friendId);
                    st.setLong(3, friendId);
                    st.setLong(4, userId);
                    st.executeUpdate();
                }
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }
}





