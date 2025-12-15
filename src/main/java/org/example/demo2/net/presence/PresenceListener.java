package org.example.demo2.net.presence;

import org.example.demo2.model.PresenceEvent;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.function.Consumer;

/**
 * Lắng nghe group multicast và bắn PresenceEvent qua handler.
 */
public class PresenceListener implements AutoCloseable {

    public static final String DEFAULT_GROUP_IP = "239.1.1.1";
    public static final int DEFAULT_GROUP_PORT = 4446;

    private final String selfUserId;
    private final Consumer<PresenceEvent> handler;
    private final MulticastSocket socket;
    private final InetAddress group;
    private final int port;
    private volatile boolean running;
    private final Thread thread;

    public PresenceListener(String selfUserId,
                            Consumer<PresenceEvent> handler) throws IOException {
        this(DEFAULT_GROUP_IP, DEFAULT_GROUP_PORT, selfUserId, handler);
    }

    public PresenceListener(String groupIp, int port,
                            String selfUserId,
                            Consumer<PresenceEvent> handler) throws IOException {

        this.selfUserId = selfUserId;
        this.handler = handler;
        this.group = InetAddress.getByName(groupIp);
        this.port = port;

        this.socket = new MulticastSocket(port);
        this.socket.setReuseAddress(true);
        this.socket.setTimeToLive(1); // Chỉ trong LAN
        // join group với NetworkInterface
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            NetworkInterface netIf = null;
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isUp() && !ni.isLoopback()) {
                    netIf = ni;
                    break;
                }
            }
            if (netIf == null && NetworkInterface.getNetworkInterfaces().hasMoreElements()) {
                netIf = NetworkInterface.getNetworkInterfaces().nextElement();
            }
            if (netIf != null) {
                this.socket.joinGroup(new InetSocketAddress(group, port), netIf);
            }
        } catch (Exception e) {
            throw new IOException("Failed to join multicast group", e);
        }

        this.running = true;
        this.thread = new Thread(this::runLoop, "PresenceListener");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    private void runLoop() {
        byte[] buf = new byte[512];

        while (running) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                socket.receive(p);

                String line = new String(p.getData(), p.getOffset(), p.getLength(),
                        StandardCharsets.UTF_8).trim();

                // TYPE|room|userId|displayName
                String[] parts = line.split("\\|", 4);
                if (parts.length < 4) continue;

                String typeStr = parts[0];
                String room = parts[1];
                String userId = parts[2];
                String displayName = parts[3];

                // bỏ qua sự kiện do chính mình gửi
                if (userId.equals(selfUserId)) {
                    continue;
                }

                PresenceEvent.Type type;
                try {
                    type = PresenceEvent.Type.valueOf(typeStr);
                } catch (IllegalArgumentException ex) {
                    // string không hợp lệ
                    continue;
                }

                PresenceEvent ev = new PresenceEvent(type, room, userId, displayName);
                handler.accept(ev);

            } catch (IOException e) {
                if (running) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        running = false;
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            NetworkInterface netIf = null;
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isUp() && !ni.isLoopback()) {
                    netIf = ni;
                    break;
                }
            }
            if (netIf == null && NetworkInterface.getNetworkInterfaces().hasMoreElements()) {
                netIf = NetworkInterface.getNetworkInterfaces().nextElement();
            }
            if (netIf != null) {
                socket.leaveGroup(new InetSocketAddress(group, port), netIf);
            }
        } catch (Exception e) {
            // Ignore errors when leaving group
        } finally {
            socket.close();
        }
    }
}
