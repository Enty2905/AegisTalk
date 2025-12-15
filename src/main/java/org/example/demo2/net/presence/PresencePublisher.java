package org.example.demo2.net.presence;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

/**
 * Gửi thông tin hiện diện (JOIN / LEAVE / TYPING) lên multicast group.
 */
public class PresencePublisher implements AutoCloseable {

    public static final String DEFAULT_GROUP_IP = "239.1.1.1";
    public static final int DEFAULT_GROUP_PORT = 4446;

    private final InetAddress group;
    private final int port;
    private final String userId;
    private final String displayName;
    private final DatagramSocket socket;

    public PresencePublisher(String userId, String displayName) throws IOException {
        this(DEFAULT_GROUP_IP, DEFAULT_GROUP_PORT, userId, displayName);
    }

    public PresencePublisher(String groupIp, int port,
                             String userId, String displayName) throws IOException {
        this.group = InetAddress.getByName(groupIp);
        this.port = port;
        this.userId = userId;
        this.displayName = displayName;
        this.socket = new DatagramSocket();   // dùng port ngẫu nhiên
    }

    private void sendRaw(String payload) throws IOException {
        byte[] data = payload.getBytes(StandardCharsets.UTF_8);
        DatagramPacket p = new DatagramPacket(data, data.length, group, port);
        socket.send(p);
    }

    // FORMAT: TYPE|room|userId|displayName
    public void sendJoin(String room) throws IOException {
        sendRaw("JOIN|" + room + "|" + userId + "|" + displayName);
    }

    public void sendLeave(String room) throws IOException {
        sendRaw("LEAVE|" + room + "|" + userId + "|" + displayName);
    }

    public void sendTyping(String room) throws IOException {
        sendRaw("TYPING|" + room + "|" + userId + "|" + displayName);
    }

    @Override
    public void close() {
        socket.close();
    }
}
