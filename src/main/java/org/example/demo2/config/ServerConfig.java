package org.example.demo2.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Cấu hình server endpoints.
 * Đọc từ file config.properties để dễ dàng thay đổi khi chạy trên máy khác.
 * 
 * THỨ TỰ ƯU TIÊN:
 * 1. System Property: -Dserver.host=192.168.0.102
 * 2. File config.properties trong thư mục hiện tại
 * 3. File config.properties trong resources
 * 4. Giá trị mặc định
 * 
 * CÁCH SỬ DỤNG:
 * - Máy Server: Giữ nguyên localhost
 * - Máy Client: Đổi server.host trong config.properties thành IP của Server
 */
public class ServerConfig {
    
    private static final Properties config = new Properties();
    
    static {
        loadConfig();
    }
    
    /**
     * Load cấu hình từ file config.properties
     */
    private static void loadConfig() {
        // 1. Thử load từ file trong thư mục hiện tại (ưu tiên cao nhất)
        Path localConfig = Paths.get("config.properties");
        if (Files.exists(localConfig)) {
            try (InputStream is = Files.newInputStream(localConfig)) {
                config.load(is);
                System.out.println("[ServerConfig] ✓ Loaded config from: " + localConfig.toAbsolutePath());
                return;
            } catch (IOException e) {
                System.err.println("[ServerConfig] Error loading local config: " + e.getMessage());
            }
        }
        
        // 2. Thử load từ resources (trong JAR/classpath)
        try (InputStream is = ServerConfig.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (is != null) {
                config.load(is);
                System.out.println("[ServerConfig] ✓ Loaded config from resources/config.properties");
                return;
            }
        } catch (IOException e) {
            System.err.println("[ServerConfig] Error loading resource config: " + e.getMessage());
        }
        
        System.out.println("[ServerConfig] No config.properties found, using defaults");
    }
    
    /**
     * Lấy giá trị cấu hình với thứ tự ưu tiên:
     * 1. System Property
     * 2. config.properties
     * 3. Default value
     */
    private static String getProperty(String key, String defaultValue) {
        // 1. System Property có ưu tiên cao nhất
        String value = System.getProperty(key);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        
        // 2. Từ config.properties
        value = config.getProperty(key);
        if (value != null && !value.isEmpty()) {
            return value.trim();
        }
        
        // 3. Default
        return defaultValue;
    }
    
    private static int getIntProperty(String key, int defaultValue) {
        try {
            return Integer.parseInt(getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * IP hoặc hostname của máy chạy server.
     * - localhost: chạy trên cùng máy
     * - IP address: chạy trên máy khác (ví dụ: 192.168.0.102)
     */
    public static final String SERVER_HOST = getProperty("server.host", "localhost");
    
    /**
     * Port cho RMI Registry (AuthService, FriendService, ChatService, etc.)
     */
    public static final int RMI_PORT = getIntProperty("rmi.port", 1099);
    
    /**
     * Port cho TCP Chat Server
     */
    public static final int CHAT_PORT = getIntProperty("chat.port", 5555);
    
    /**
     * Port cho UDP Video Stream Server
     */
    public static final int VIDEO_STREAM_PORT = getIntProperty("video.port", 8888);
    
    /**
     * Port cho RMI Moderation Service
     */
    public static final int MODERATION_PORT = getIntProperty("moderation.port", 5100);
    
    /**
     * Port cho HTTP File Server
     */
    public static final int FILE_SERVER_PORT = getIntProperty("file.port", 8081);
    
    /**
     * RMI URL cho Moderation Service
     */
    public static String getModerationServiceUrl() {
        return "rmi://" + SERVER_HOST + ":" + MODERATION_PORT + "/ModerationService";
    }
    
    /**
     * RMI URL cho các service khác (AuthService, FriendService, etc.)
     */
    public static String getRmiServiceUrl(String serviceName) {
        return "rmi://" + SERVER_HOST + ":" + RMI_PORT + "/" + serviceName;
    }
    
    /**
     * In ra cấu hình hiện tại (để debug)
     */
    public static void printConfig() {
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║     AegisTalk Server Configuration   ║");
        System.out.println("╠══════════════════════════════════════╣");
        System.out.println("║ Server Host     : " + padRight(SERVER_HOST, 19) + "║");
        System.out.println("║ RMI Port        : " + padRight(String.valueOf(RMI_PORT), 19) + "║");
        System.out.println("║ Chat Port       : " + padRight(String.valueOf(CHAT_PORT), 19) + "║");
        System.out.println("║ Video Port      : " + padRight(String.valueOf(VIDEO_STREAM_PORT), 19) + "║");
        System.out.println("║ Moderation Port : " + padRight(String.valueOf(MODERATION_PORT), 19) + "║");
        System.out.println("║ File Server Port: " + padRight(String.valueOf(FILE_SERVER_PORT), 19) + "║");
        System.out.println("╚══════════════════════════════════════╝");
    }
    
    private static String padRight(String s, int length) {
        if (s.length() >= length) return s;
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < length) sb.append(" ");
        return sb.toString();
    }
    
    /**
     * Reload cấu hình (nếu cần thay đổi runtime)
     */
    public static void reload() {
        config.clear();
        loadConfig();
        System.out.println("[ServerConfig] Configuration reloaded");
    }
}



