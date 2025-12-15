package org.example.demo2.net.moderation;

import org.example.demo2.model.ModerationResult;

import java.rmi.Naming;

/**
 * Client RMI nói chuyện với ModerationServer.
 */
public class ModerationClient {

    private final ModerationRemote remote;

    public ModerationClient(String host, int port) throws Exception {
        String url = String.format("rmi://%s:%d/ModerationService", host, port);
        System.out.println("[ModerationClient] Connecting to " + url);
        remote = (ModerationRemote) Naming.lookup(url);
        System.out.println("[ModerationClient] Connected OK");
    }

    public ModerationResult moderateText(String text) {
        try {
            return remote.moderateText(text);
        } catch (Exception e) {
            System.err.println("[ModerationClient] moderateText error: " + e);
            return new ModerationResult(
                    org.example.demo2.model.ModerationDecision.ALLOW,
                    java.util.EnumSet.noneOf(org.example.demo2.model.PolicyCategory.class),
                    0.0,
                    "Moderation error: " + e.getMessage()
            );
        }
    }

    public ModerationResult moderateImage(byte[] jpegBytes) {
        try {
            return remote.moderateImage(jpegBytes);
        } catch (Exception e) {
            System.err.println("[ModerationClient] moderateImage error: " + e);
            return new ModerationResult(
                    org.example.demo2.model.ModerationDecision.ALLOW,
                    java.util.EnumSet.noneOf(org.example.demo2.model.PolicyCategory.class),
                    0.0,
                    "Moderation error: " + e.getMessage()
            );
        }
    }

    // thêm cho ChatController gọi cho “đúng form”
    public void close() {
        // RMI stub không cần đóng gì, để trống
    }
}
