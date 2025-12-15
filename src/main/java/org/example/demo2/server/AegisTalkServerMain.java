package org.example.demo2.server;

import org.example.demo2.net.files.FileHttpServerMain;
import org.example.demo2.net.moderation.ModerationServerMain;
import org.example.demo2.net.udp.VideoStreamServer;
import org.example.demo2.service.rmi.RMIServiceServer;

import java.io.IOException;

public class AegisTalkServerMain {
     
    public static void main(String[] args) {
        System.out.println("   AegisTalk Server - Starting...");
        System.out.println();
        
        // 1. RMI Service Server (Auth, Friend, Chat, Group)
        System.out.println("[Main] Starting RMI Service Server...");
        Thread rmiThread = new Thread(() -> {
            try {
                RMIServiceServer.main(new String[0]);
            } catch (Exception e) {
                System.err.println("[Main] RMI Service Server error: " + e.getMessage());
            }
        }, "RMI-Service-Server");
        rmiThread.setDaemon(true);
        rmiThread.start();
        
        // Đợi một chút để RMI registry khởi động
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 2. RMI Moderation Server
        System.out.println("[Main] Starting RMI Moderation Server...");
        Thread moderationThread = new Thread(() -> {
            try {
                ModerationServerMain.main(new String[0]);
            } catch (Exception e) {
                System.err.println("[Main] Moderation Server error: " + e.getMessage());
            }
        }, "RMI-Moderation-Server");
        moderationThread.setDaemon(true);
        moderationThread.start();
        
        // 3. TCP Chat Server (ChatServer - đơn giản hơn, chỉ broadcast messages)
        System.out.println("[Main] Starting TCP Chat Server...");
        Thread tcpThread = new Thread(() -> {
            try {
                org.example.demo2.net.chat.ChatServer chatServer = new org.example.demo2.net.chat.ChatServer(5555);
                chatServer.start();
            } catch (IOException e) {
                System.err.println("[Main] TCP Chat Server error: " + e.getMessage());
                e.printStackTrace();
            }
        }, "TCP-Chat-Server");
        tcpThread.setDaemon(true);
        tcpThread.start();
        
        // 4. UDP Video Stream Server
        System.out.println("[Main] Starting UDP Video Stream Server...");
        VideoStreamServer[] udpServerRef = new VideoStreamServer[1];
        Thread udpThread = new Thread(() -> {
            try {
                VideoStreamServer udpServer = new VideoStreamServer(8888);
                udpServerRef[0] = udpServer;
                udpServer.start();
                
                // Link với CallService - đợi một chút để CallService được đăng ký
                try {
                    Thread.sleep(2000); // Đợi CallService được đăng ký
                    org.example.demo2.service.rmi.CallServiceImpl callService = 
                        org.example.demo2.service.rmi.RMIServiceServer.getCallServiceInstance();
                    if (callService != null) {
                        callService.setVideoStreamServer(udpServer);
                        System.out.println("[Main] CallService linked with VideoStreamServer");
                    } else {
                        System.err.println("[Main] Warning: CallService instance not available yet");
                    }
                } catch (Exception e) {
                    System.err.println("[Main] Warning: Could not link CallService with VideoStreamServer: " + e.getMessage());
                }
                
                // Giữ thread sống
                try {
                    Thread.sleep(Long.MAX_VALUE);
                } catch (InterruptedException e) {
                    udpServer.stop();
                }
            } catch (Exception e) {
                System.err.println("[Main] UDP Server error: " + e.getMessage());
            }
        }, "UDP-Video-Server");
        udpThread.setDaemon(true);
        udpThread.start();
        
        // 5. HTTP File Server
        System.out.println("[Main] Starting HTTP File Server...");
        Thread httpThread = new Thread(() -> {
            try {
                FileHttpServerMain.main(new String[0]);
            } catch (Exception e) {
                System.err.println("[Main] HTTP File Server error: " + e.getMessage());
            }
        }, "HTTP-File-Server");
        httpThread.setDaemon(true);
        httpThread.start();
        
        System.out.println();
        System.out.println("========================================");
        System.out.println("   All services started successfully!");
        System.out.println("========================================");
        
        // Lấy địa chỉ IP LAN của server
        String serverIp = org.example.demo2.net.udp.VideoStreamClient.getLocalLanAddress();
        System.out.println();
        System.out.println("Server IP Address: " + serverIp);
        System.out.println();
        System.out.println("RMI Service Server:    rmi://" + serverIp + ":1099");
        System.out.println("RMI Moderation Server: rmi://" + serverIp + ":5100");
        System.out.println("TCP Chat Server:       " + serverIp + ":5555");
        System.out.println("UDP Video Server:      " + serverIp + ":8888");
        System.out.println("HTTP File Server:      http://" + serverIp + ":8081");
        System.out.println();
        System.out.println("===== HƯỚNG DẪN CHẠY TRÊN 2 MÁY KHÁC NHAU =====");
        System.out.println("1. Trên máy Client, chạy ứng dụng với tham số:");
        System.out.println("   mvn javafx:run -Dserver.host=" + serverIp);
        System.out.println();
        System.out.println("   Hoặc set biến môi trường trước khi chạy:");
        System.out.println("   set SERVER_HOST=" + serverIp + "  (Windows)");
        System.out.println("   export SERVER_HOST=" + serverIp + " (Linux/Mac)");
        System.out.println("================================================");
        System.out.println();
        System.out.println("Press Ctrl+C to stop all services...");
        System.out.println();
        
        // Giữ main thread sống
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.out.println("[Main] Shutting down...");
        }
    }
}



