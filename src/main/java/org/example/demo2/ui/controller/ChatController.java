package org.example.demo2.ui.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.util.Duration;
import org.example.demo2.model.ChatMessage;
import org.example.demo2.model.ModerationDecision;
import org.example.demo2.model.ModerationResult;
import org.example.demo2.net.chat.ChatClient;
import org.example.demo2.net.moderation.ModerationClient;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

public class ChatController {

    @FXML private TextField txtRoom;
    @FXML private ListView<String> lstMessages;
    @FXML private TextField txtMessage;
    @FXML private Button btnSend;
    @FXML private Label  lblStatus;
    @FXML private Label  lblModeration;

    // --- UI presence ---
    @FXML private ListView<String> lstOnline;
    @FXML private Label lblTyping;

    private final ObservableList<String> onlineUsers =
            FXCollections.observableArrayList();

    private ChatClient chatClient;
    private ModerationClient moderationClient;

    // cấu hình tạm
    private static final String CHAT_HOST = "localhost";
    private static final int    CHAT_PORT = 5555;

    private static final String MOD_HOST  = "localhost";
    private static final int    MOD_PORT  = 5100;

    // Presence (UDP multicast)
    private static final String PRESENCE_GROUP = "239.1.1.1";
    private static final int    PRESENCE_PORT  = 4446;

    private MulticastSocket presenceSocket;
    private Thread presenceThread;
    private volatile boolean presenceRunning = false;

    // user hiện tại (demo)
    private static final String CURRENT_USER =
            System.getProperty("user.displayName", "Me");

    @FXML
    private void initialize() {
        // room mặc định
        txtRoom.setText("room1");

        // danh sách online
        lstOnline.setItems(onlineUsers);

        // Kết nối Moderation RMI (Gemini)
        try {
            moderationClient = new ModerationClient(MOD_HOST, MOD_PORT);
            if (lblModeration != null) {
                lblModeration.setText("Moderation: OK");
                lblModeration.getStyleClass().add("connected");
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (lblModeration != null) {
                lblModeration.setText("Moderation: OFF");
                lblModeration.getStyleClass().removeAll("connected");
            }
        }

        // Kết nối TCP Chat
        try {
            chatClient = new ChatClient(CHAT_HOST, CHAT_PORT, this::onIncomingMessage);
            chatClient.connect();
            if (lblStatus != null) {
                lblStatus.setText("Chat: connected");
                lblStatus.getStyleClass().removeAll("disconnected");
                lblStatus.getStyleClass().add("connected");
            }
        } catch (IOException e) {
            e.printStackTrace();
            if (lblStatus != null) {
                lblStatus.setText("Chat: error – " + e.getMessage());
                lblStatus.getStyleClass().removeAll("connected");
                lblStatus.getStyleClass().add("disconnected");
            }
        }

        // Bắt đầu presence (JOIN + listener)
        startPresence();

        // Enter để gửi
        txtMessage.setOnAction(e -> onSendClicked());
        btnSend.setOnAction(e -> onSendClicked());

        // Khi gõ phím -> gửi TYPING
        txtMessage.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isEmpty()) {
                sendPresence("TYPING", getCurrentRoom(), CURRENT_USER);
            }
        });
    }

    // ----------------- Gửi tin nhắn (có moderation) -----------------
    @FXML
    private void onSendClicked() {
        if (chatClient == null) {
            lblStatus.setText("Chat client chưa kết nối");
            return;
        }

        String room = getCurrentRoom();
        txtRoom.setText(room);

        String text = txtMessage.getText().trim();
        if (text.isEmpty()) {
            return;
        }

        // ---- Bước 1: gọi Moderation trước khi gửi ----
        ModerationResult modResult = null;
        try {
            if (moderationClient != null) {
                modResult = moderationClient.moderateText(text);
            }
        } catch (Exception e) {
            e.printStackTrace();
            lblModeration.setText("Moderation error: " + e.getMessage());
        }

        if (modResult != null) {
            // dùng getter thay vì field trực tiếp
            lblModeration.setText("Decision: " + modResult.getDecision()
                    + " – " + modResult.getReason());

            if (modResult.getDecision() == ModerationDecision.BLOCK) {
                // Chặn gửi
                lstMessages.getItems().add("[BLOCKED] Tôi: " + text);
                txtMessage.clear();
                return;
            } else if (modResult.getDecision() == ModerationDecision.WARN) {
                lstMessages.getItems().add("[WARN] Tôi: " + text);
            }
        }

        // ---- Bước 2: tạo ChatMessage và gửi qua TCP ----
        ChatMessage msg = ChatMessage.text(room, CURRENT_USER, text);

        try {
            // dùng API thật của ChatClient: send(ChatMessage)
            chatClient.send(msg);
            txtMessage.clear();
        } catch (IOException e) {
            e.printStackTrace();
            lstMessages.getItems().add("[ERROR gửi tin] " + e.getMessage());
        }
    }

    // Callback khi nhận tin từ ChatClient
    private void onIncomingMessage(ChatMessage msg) {
        Platform.runLater(() -> {
            String line = String.format("[%s] %s: %s",
                    msg.room(),
                    msg.from(),
                    msg.text());
            lstMessages.getItems().add(line);
        });
    }

    // ----------------- Presence (JOIN / LEAVE / TYPING) -----------------

    private String getCurrentRoom() {
        String room = txtRoom.getText().trim();
        if (room.isEmpty()) {
            room = "room1";
        }
        return room;
    }

    private void startPresence() {
        try {
            presenceSocket = new MulticastSocket(PRESENCE_PORT);
            InetAddress group = InetAddress.getByName(PRESENCE_GROUP);
            presenceSocket.setReuseAddress(true);
            presenceSocket.setTimeToLive(1);
            
            // join group với NetworkInterface - xử lý lỗi gracefully
            boolean joined = false;
            try {
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                NetworkInterface netIf = null;
                while (interfaces.hasMoreElements()) {
                    NetworkInterface ni = interfaces.nextElement();
                    if (ni.isUp() && !ni.isLoopback() && ni.supportsMulticast()) {
                        // Kiểm tra có IPv4 address không
                        Enumeration<InetAddress> addresses = ni.getInetAddresses();
                        while (addresses.hasMoreElements()) {
                            InetAddress addr = addresses.nextElement();
                            if (addr instanceof java.net.Inet4Address) {
                                netIf = ni;
                                break;
                            }
                        }
                        if (netIf != null) break;
                    }
                }
                if (netIf != null) {
                    presenceSocket.joinGroup(new InetSocketAddress(group, PRESENCE_PORT), netIf);
                    joined = true;
                    System.out.println("[ChatController] Joined multicast group successfully");
                }
            } catch (Exception e) {
                System.err.println("[ChatController] Warning: Could not join multicast group: " + e.getMessage());
                System.err.println("[ChatController] Presence features will be disabled. Chat will still work.");
                // Đóng socket nếu không join được
                try {
                    presenceSocket.close();
                } catch (Exception ignored) {}
                presenceSocket = null;
                return; // Thoát sớm, không khởi động presence
            }
            
            if (!joined) {
                System.err.println("[ChatController] Warning: No suitable network interface for multicast");
                try {
                    presenceSocket.close();
                } catch (Exception ignored) {}
                presenceSocket = null;
                return;
            }
            
            presenceRunning = true;

            // gửi JOIN cho room hiện tại
            sendPresence("JOIN", getCurrentRoom(), CURRENT_USER);

            // Thread listen
            presenceThread = new Thread(() -> listenPresence(group));
            presenceThread.setDaemon(true);
            presenceThread.start();
        } catch (IOException e) {
            System.err.println("[ChatController] Warning: Presence initialization failed: " + e.getMessage());
            System.err.println("[ChatController] Chat will continue without presence features.");
            // Presence fail -> bỏ qua, không làm hỏng chat chính
            if (presenceSocket != null) {
                try {
                    presenceSocket.close();
                } catch (Exception ignored) {}
                presenceSocket = null;
            }
        }
    }

    private void listenPresence(InetAddress group) {
        byte[] buf = new byte[512];

        while (presenceRunning) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                presenceSocket.receive(packet);

                String payload = new String(
                        packet.getData(), 0, packet.getLength(),
                        StandardCharsets.UTF_8);

                handlePresenceMessage(payload);
            } catch (IOException e) {
                if (presenceRunning) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void sendPresence(String type, String room, String user) {
        if (presenceSocket == null || presenceSocket.isClosed()) return;

        try {
            String payload = type + "|" + room + "|" + user;
            byte[] data = payload.getBytes(StandardCharsets.UTF_8);

            DatagramPacket packet = new DatagramPacket(
                    data,
                    data.length,
                    InetAddress.getByName(PRESENCE_GROUP),
                    PRESENCE_PORT
            );

            presenceSocket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handlePresenceMessage(String payload) {
        String[] parts = payload.split("\\|", 3);
        if (parts.length < 3) return;

        String type = parts[0];
        String room = parts[1];
        String user = parts[2];

        // chỉ quan tâm room hiện tại
        String currentRoom = getCurrentRoom();
        if (!room.equals(currentRoom)) return;

        switch (type) {
            case "JOIN":
                Platform.runLater(() -> addOnlineUser(user));
                break;
            case "LEAVE":
                Platform.runLater(() -> removeOnlineUser(user));
                break;
            case "TYPING":
                if (!user.equals(CURRENT_USER)) {
                    Platform.runLater(() -> showTyping(user));
                }
                break;
            default:
                // bỏ qua
        }
    }

    private void addOnlineUser(String user) {
        if (!onlineUsers.contains(user)) {
            onlineUsers.add(user);
        }
    }

    private void removeOnlineUser(String user) {
        onlineUsers.remove(user);
    }

    // hiển thị "xxx đang nhập..." trong 2 giây
    private void showTyping(String user) {
        lblTyping.setText(user + " đang nhập...");
        Timeline t = new Timeline(
                new KeyFrame(Duration.seconds(2),
                        e -> {
                            // chỉ xóa nếu chưa bị user khác overwrite
                            if (lblTyping.getText().startsWith(user + " ")) {
                                lblTyping.setText("");
                            }
                        })
        );
        t.setCycleCount(1);
        t.play();
    }

    private void stopPresence() {
        if (presenceSocket != null && !presenceSocket.isClosed()) {
            // gửi LEAVE trước khi rời nhóm
            sendPresence("LEAVE", getCurrentRoom(), CURRENT_USER);
            presenceRunning = false;
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
                    presenceSocket.leaveGroup(new InetSocketAddress(
                            InetAddress.getByName(PRESENCE_GROUP), PRESENCE_PORT), netIf);
                }
            } catch (Exception e) {
                // Ignore errors when leaving group
            }
            try {
                presenceSocket.close();
            } catch (Exception e) {
                // Ignore errors when closing socket
            }
        }
    }

    // Gọi khi đóng app (nếu bạn muốn)
    public void shutdown() {
        try {
            if (chatClient != null) {
                chatClient.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (moderationClient != null) {
            moderationClient.close();
        }

        stopPresence();
    }
}
