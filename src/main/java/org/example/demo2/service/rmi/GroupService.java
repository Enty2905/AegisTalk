package org.example.demo2.service.rmi;

import org.example.demo2.model.Conversation;
import org.example.demo2.model.User;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * RMI Service cho quản lý nhóm chat.
 * 
 * Áp dụng: RMI (Remote Method Invocation) - Bài RMI
 * - Client gọi method từ xa để quản lý nhóm
 * - Trả về danh sách Conversation, User qua network
 */
public interface GroupService extends Remote {
    
    /**
     * Tạo nhóm chat mới.
     * @param name Tên nhóm
     * @param creatorId ID người tạo
     * @return Conversation đã tạo
     * @throws RemoteException Lỗi RMI
     */
    Conversation createGroup(String name, Long creatorId) throws RemoteException;
    
    /**
     * Thêm thành viên vào nhóm.
     * @param groupId ID nhóm
     * @param userId ID người dùng cần thêm
     * @param inviterId ID người mời
     * @return true nếu thành công
     * @throws RemoteException Lỗi RMI
     */
    boolean addMember(Long groupId, Long userId, Long inviterId) throws RemoteException;
    
    /**
     * Xóa thành viên khỏi nhóm.
     * @param groupId ID nhóm
     * @param userId ID người dùng cần xóa
     * @param removerId ID người thực hiện (phải là admin hoặc chính user đó)
     * @return true nếu thành công
     * @throws RemoteException Lỗi RMI
     */
    boolean removeMember(Long groupId, Long userId, Long removerId) throws RemoteException;
    
    /**
     * Lấy danh sách thành viên của nhóm.
     * @param groupId ID nhóm
     * @return Danh sách thành viên
     * @throws RemoteException Lỗi RMI
     */
    List<User> getGroupMembers(Long groupId) throws RemoteException;
    
    /**
     * Cập nhật tên nhóm.
     * @param groupId ID nhóm
     * @param newName Tên mới
     * @param updaterId ID người cập nhật (phải là admin)
     * @return true nếu thành công
     * @throws RemoteException Lỗi RMI
     */
    boolean updateGroupName(Long groupId, String newName, Long updaterId) throws RemoteException;
}



