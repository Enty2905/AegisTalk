package org.example.demo2.service.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * RMI Service cho quản lý video call.
 * 
 * Áp dụng: RMI (Remote Method Invocation) - Bài RMI
 * - Client gọi method từ xa để mời gọi, chấp nhận, từ chối, kết thúc cuộc gọi
 * - Signaling cho video call (thiết lập cuộc gọi)
 * 
 * Áp dụng: SOA (Service-Oriented Architecture) - Bài SOA
 * - Service riêng biệt cho call management
 * - Contract rõ ràng (interface)
 */
public interface CallService extends Remote {
    
    /**
     * Mời gọi video (caller gọi).
     * @param callerId ID người gọi
     * @param calleeId ID người nhận
     * @return Call session ID nếu thành công, null nếu thất bại
     * @throws RemoteException Lỗi RMI
     */
    Integer inviteCall(Long callerId, Long calleeId) throws RemoteException;
    
    /**
     * Chấp nhận cuộc gọi.
     * @param callSessionId ID cuộc gọi
     * @param userId ID người chấp nhận
     * @return true nếu thành công
     * @throws RemoteException Lỗi RMI
     */
    boolean acceptCall(Integer callSessionId, Long userId) throws RemoteException;
    
    /**
     * Từ chối cuộc gọi.
     * @param callSessionId ID cuộc gọi
     * @param userId ID người từ chối
     * @return true nếu thành công
     * @throws RemoteException Lỗi RMI
     */
    boolean rejectCall(Integer callSessionId, Long userId) throws RemoteException;
    
    /**
     * Kết thúc cuộc gọi.
     * @param callSessionId ID cuộc gọi
     * @param userId ID người kết thúc
     * @return true nếu thành công
     * @throws RemoteException Lỗi RMI
     */
    boolean endCall(Integer callSessionId, Long userId) throws RemoteException;
    
    /**
     * Đăng ký UDP endpoint cho cuộc gọi.
     * @param callSessionId ID cuộc gọi
     * @param userId ID người dùng
     * @param address Địa chỉ IP
     * @param port Port UDP
     * @return true nếu thành công
     * @throws RemoteException Lỗi RMI
     */
    boolean registerUdpEndpoint(Integer callSessionId, Long userId, String address, int port) throws RemoteException;
    
    /**
     * Lấy thông tin cuộc gọi.
     * @param callSessionId ID cuộc gọi
     * @return CallInfo hoặc null
     * @throws RemoteException Lỗi RMI
     */
    CallInfo getCallInfo(Integer callSessionId) throws RemoteException;
    
    /**
     * Lấy danh sách cuộc gọi đang chờ của user.
     * @param userId ID người dùng
     * @return Danh sách CallInfo
     * @throws RemoteException Lỗi RMI
     */
    java.util.List<CallInfo> getPendingCalls(Long userId) throws RemoteException;
    
    /**
     * Model cho thông tin cuộc gọi.
     */
    class CallInfo implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        public final Integer sessionId;
        public final Long callerId;
        public final Long calleeId;
        public final String status; // "PENDING", "ACTIVE", "ENDED"
        public final Long createdAt;
        
        public CallInfo(Integer sessionId, Long callerId, Long calleeId, String status, Long createdAt) {
            this.sessionId = sessionId;
            this.callerId = callerId;
            this.calleeId = calleeId;
            this.status = status;
            this.createdAt = createdAt;
        }
    }
}



