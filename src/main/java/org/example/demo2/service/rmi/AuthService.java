package org.example.demo2.service.rmi;

import org.example.demo2.model.User;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * RMI Service cho xác thực người dùng (đăng nhập/đăng ký).
 * 
 * Áp dụng: RMI (Remote Method Invocation) - Bài RMI
 * - Client gọi method từ xa như gọi local method
 * - Trả về đối tượng Java (User) qua network
 */
public interface AuthService extends Remote {
    
    /**
     * Đăng ký tài khoản mới.
     * @param username Tên đăng nhập
     * @param password Mật khẩu (sẽ được hash ở server)
     * @param displayName Tên hiển thị
     * @return User nếu thành công, null nếu thất bại
     * @throws RemoteException Lỗi RMI
     */
    User register(String username, String password, String displayName) throws RemoteException;
    
    /**
     * Đăng nhập.
     * @param username Tên đăng nhập
     * @param password Mật khẩu
     * @return User nếu thành công, null nếu sai thông tin
     * @throws RemoteException Lỗi RMI
     */
    User login(String username, String password) throws RemoteException;
    
    /**
     * Đăng xuất.
     * @param userId ID người dùng
     * @throws RemoteException Lỗi RMI
     */
    void logout(Long userId) throws RemoteException;
    
    /**
     * Kiểm tra session còn hợp lệ không.
     * @param userId ID người dùng
     * @return true nếu session hợp lệ
     * @throws RemoteException Lỗi RMI
     */
    boolean isValidSession(Long userId) throws RemoteException;
}





