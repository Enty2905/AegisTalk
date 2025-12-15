package org.example.demo2.service.rmi;

import org.example.demo2.net.udp.VideoStreamServer;

import java.net.InetAddress;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementation của CallService sử dụng RMI.
 * 
 * Áp dụng: RMI (Remote Method Invocation) - Bài RMI
 * - Kế thừa UnicastRemoteObject để export remote object
 * - Client gọi method từ xa để quản lý cuộc gọi
 */
public class CallServiceImpl extends UnicastRemoteObject implements CallService {
    
    private static final AtomicInteger sessionIdGenerator = new AtomicInteger(1);
    
    // Quản lý call sessions: sessionId -> CallInfo
    private final Map<Integer, CallInfo> callSessions = new ConcurrentHashMap<>();
    
    // Quản lý UDP endpoints: sessionId -> Map<userId, EndpointInfo>
    private final Map<Integer, Map<Long, EndpointInfo>> udpEndpoints = new ConcurrentHashMap<>();
    
    // Reference đến VideoStreamServer để đăng ký endpoint
    private VideoStreamServer videoStreamServer;
    
    public CallServiceImpl() throws RemoteException {
        super();
    }
    
    public void setVideoStreamServer(VideoStreamServer server) {
        this.videoStreamServer = server;
    }
    
    @Override
    public Integer inviteCall(Long callerId, Long calleeId) throws RemoteException {
        if (callerId == null || calleeId == null || callerId.equals(calleeId)) {
            return null;
        }
        
        Integer sessionId = sessionIdGenerator.getAndIncrement();
        CallInfo callInfo = new CallInfo(
            sessionId,
            callerId,
            calleeId,
            "PENDING",
            System.currentTimeMillis()
        );
        
        callSessions.put(sessionId, callInfo);
        System.out.println("[CallService] Call invited: session=" + sessionId + ", caller=" + callerId + ", callee=" + calleeId);
        
        return sessionId;
    }
    
    @Override
    public boolean acceptCall(Integer callSessionId, Long userId) throws RemoteException {
        CallInfo callInfo = callSessions.get(callSessionId);
        if (callInfo == null || !callInfo.calleeId.equals(userId)) {
            return false;
        }
        
        if (!"PENDING".equals(callInfo.status)) {
            return false;
        }
        
        // Cập nhật status
        CallInfo updated = new CallInfo(
            callInfo.sessionId,
            callInfo.callerId,
            callInfo.calleeId,
            "ACTIVE",
            callInfo.createdAt
        );
        callSessions.put(callSessionId, updated);
        
        System.out.println("[CallService] Call accepted: session=" + callSessionId + ", user=" + userId);
        return true;
    }
    
    @Override
    public boolean rejectCall(Integer callSessionId, Long userId) throws RemoteException {
        CallInfo callInfo = callSessions.get(callSessionId);
        if (callInfo == null || !callInfo.calleeId.equals(userId)) {
            return false;
        }
        
        // Xóa session
        callSessions.remove(callSessionId);
        udpEndpoints.remove(callSessionId);
        
        System.out.println("[CallService] Call rejected: session=" + callSessionId + ", user=" + userId);
        return true;
    }
    
    @Override
    public boolean endCall(Integer callSessionId, Long userId) throws RemoteException {
        CallInfo callInfo = callSessions.get(callSessionId);
        if (callInfo == null) {
            return false;
        }
        
        if (!callInfo.callerId.equals(userId) && !callInfo.calleeId.equals(userId)) {
            return false;
        }
        
        // Cập nhật status
        CallInfo updated = new CallInfo(
            callInfo.sessionId,
            callInfo.callerId,
            callInfo.calleeId,
            "ENDED",
            callInfo.createdAt
        );
        callSessions.put(callSessionId, updated);
        
        // Xóa UDP endpoints
        udpEndpoints.remove(callSessionId);
        
        System.out.println("[CallService] Call ended: session=" + callSessionId + ", user=" + userId);
        return true;
    }
    
    @Override
    public boolean registerUdpEndpoint(Integer callSessionId, Long userId, String address, int port) throws RemoteException {
        CallInfo callInfo = callSessions.get(callSessionId);
        if (callInfo == null) {
            System.err.println("[CallService] registerUdpEndpoint failed: call session " + callSessionId + " not found");
            return false;
        }
        
        if (!callInfo.callerId.equals(userId) && !callInfo.calleeId.equals(userId)) {
            System.err.println("[CallService] registerUdpEndpoint failed: user " + userId + " is not part of call " + callSessionId);
            return false;
        }
        
        // Validate address
        if (address == null || address.isEmpty() || "127.0.0.1".equals(address) || "localhost".equalsIgnoreCase(address)) {
            System.err.println("[CallService] WARNING: Invalid or localhost address: " + address + ". This may cause issues with remote clients.");
        }
        
        // Đăng ký endpoint
        Map<Long, EndpointInfo> endpoints = udpEndpoints.computeIfAbsent(callSessionId, k -> new ConcurrentHashMap<>());
        endpoints.put(userId, new EndpointInfo(address, port));
        
        // Đăng ký với VideoStreamServer
        if (videoStreamServer != null) {
            try {
                InetAddress inetAddress = InetAddress.getByName(address);
                // QUAN TRỌNG: Truyền userId để server phân biệt được users (đặc biệt khi chạy trên cùng 1 máy)
                videoStreamServer.registerEndpoint(callSessionId, userId.intValue(), inetAddress, port);
                System.out.println("[CallService] ✓ UDP endpoint registered with VideoStreamServer: session=" + callSessionId + ", userId=" + userId + ", " + address + ":" + port);
                
                // Log tổng số endpoints trong session
                int totalEndpoints = endpoints.size();
                System.out.println("[CallService] Session " + callSessionId + " now has " + totalEndpoints + " registered endpoints");
            } catch (Exception e) {
                System.err.println("[CallService] Error registering endpoint with VideoStreamServer: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.err.println("[CallService] WARNING: VideoStreamServer is null, cannot register endpoint");
        }
        
        return true;
    }
    
    @Override
    public CallInfo getCallInfo(Integer callSessionId) throws RemoteException {
        return callSessions.get(callSessionId);
    }
    
    @Override
    public List<CallInfo> getPendingCalls(Long userId) throws RemoteException {
        List<CallInfo> pending = new ArrayList<>();
        for (CallInfo callInfo : callSessions.values()) {
            if ("PENDING".equals(callInfo.status) && callInfo.calleeId.equals(userId)) {
                pending.add(callInfo);
            }
        }
        return pending;
    }
    
    /**
     * Model cho UDP endpoint info.
     */
    private static class EndpointInfo {
        final String address;
        final int port;
        
        EndpointInfo(String address, int port) {
            this.address = address;
            this.port = port;
        }
    }
}



