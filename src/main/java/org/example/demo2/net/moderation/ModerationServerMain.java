package org.example.demo2.net.moderation;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Chạy RMI Moderation Service mock.
 * Sau này demo: chạy class này ở 1 JVM riêng.
 */
public class ModerationServerMain {

    public static final String BIND_NAME = "ModerationService";
    public static final int PORT = 5100; // tuỳ chọn, tránh đụng 1099 nếu dùng RMI khác

    public static void main(String[] args) {
        try {
            System.out.println("[ModerationServer] Starting RMI registry on port " + PORT + "...");
            Registry registry = LocateRegistry.createRegistry(PORT);

            ModerationRemoteImpl svc = new ModerationRemoteImpl();

            registry.rebind(BIND_NAME, svc);
            System.out.println("[ModerationServer] Bound as '" + BIND_NAME + "'. Server is ready.");

            // Để server sống, không thoát main
            Thread.currentThread().join();
        } catch (Exception e) {
            System.err.println("[ModerationServer] ERROR:");
            e.printStackTrace();
        }
    }
}