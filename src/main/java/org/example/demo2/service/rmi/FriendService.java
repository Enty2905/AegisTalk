package org.example.demo2.service.rmi;

import org.example.demo2.model.User;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * RMI Service cho quản lý bạn bè.
 * 
 * Áp dụng: RMI (Remote Method Invocation) - Bài RMI
 * - Client gọi method từ xa để quản lý bạn bè
 * - Trả về danh sách User qua network
 */
public interface FriendService extends Remote {
    
    /**
     * Tìm kiếm user theo username hoặc display name.
     * @param keyword Từ khóa tìm kiếm
     * @return Danh sách user khớp
     * @throws RemoteException Lỗi RMI
     */
    List<User> searchUsers(String keyword) throws RemoteException;
    
    /**
     * Gửi yêu cầu kết bạn.
     * @param fromUserId ID người gửi
     * @param toUserId ID người nhận
     * @return true nếu thành công
     * @throws RemoteException Lỗi RMI
     */
    boolean sendFriendRequest(Long fromUserId, Long toUserId) throws RemoteException;
    
    /**
     * Hủy yêu cầu kết bạn.
     * @param fromUserId ID người gửi
     * @param toUserId ID người nhận
     * @return true nếu thành công
     * @throws RemoteException Lỗi RMI
     */
    boolean cancelFriendRequest(Long fromUserId, Long toUserId) throws RemoteException;
    
    /**
     * Chấp nhận yêu cầu kết bạn.
     * @param userId ID người chấp nhận
     * @param friendId ID người gửi yêu cầu
     * @return true nếu thành công
     * @throws RemoteException Lỗi RMI
     */
    boolean acceptFriendRequest(Long userId, Long friendId) throws RemoteException;
    
    /**
     * Từ chối yêu cầu kết bạn.
     * @param userId ID người từ chối
     * @param friendId ID người gửi yêu cầu
     * @return true nếu thành công
     * @throws RemoteException Lỗi RMI
     */
    boolean rejectFriendRequest(Long userId, Long friendId) throws RemoteException;
    
    /**
     * Lấy danh sách bạn bè.
     * @param userId ID người dùng
     * @return Danh sách bạn bè
     * @throws RemoteException Lỗi RMI
     */
    List<User> getFriends(Long userId) throws RemoteException;
    
    /**
     * Lấy danh sách yêu cầu kết bạn đang chờ.
     * @param userId ID người dùng
     * @return Danh sách yêu cầu đang chờ
     * @throws RemoteException Lỗi RMI
     */
    List<User> getPendingFriendRequests(Long userId) throws RemoteException;
    
    /**
     * Kiểm tra 2 user có phải bạn bè không.
     * @param userId1 ID user 1
     * @param userId2 ID user 2
     * @return true nếu là bạn bè
     * @throws RemoteException Lỗi RMI
     */
    boolean areFriends(Long userId1, Long userId2) throws RemoteException;

    /**
     * Huỷ kết bạn (xoá cả 2 chiều).
     */
    boolean removeFriend(Long userId, Long friendId) throws RemoteException;
}





