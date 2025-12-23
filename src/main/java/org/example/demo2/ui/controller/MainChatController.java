package org.example.demo2.ui.controller;

import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.media.AudioClip;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import javafx.stage.FileChooser;
import org.example.demo2.Session;
import org.example.demo2.client.AegisTalkClientService;
import org.example.demo2.model.ChatMessage;
import org.example.demo2.model.Conversation;
import org.example.demo2.model.ModerationDecision;
import org.example.demo2.model.ModerationResult;
import org.example.demo2.model.User;
import org.example.demo2.net.chat.ChatClient;
import org.example.demo2.net.moderation.ModerationClient;
import org.example.demo2.service.rmi.CallService;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;


public class MainChatController {
    
    // ========== Left Sidebar ==========
    @FXML private TextField txtSearch;
    @FXML private ListView<User> lstSearchResults;
    @FXML private VBox searchResultsContainer;
    @FXML private ToggleButton tabFriends;
    @FXML private ToggleButton tabGroups;
    @FXML private ToggleButton tabRequests;
    @FXML private Button btnCreateGroup;
    @FXML private ListView<ContactItem> lstContacts;
    @FXML private Label lblCurrentUser;
    @FXML private Circle currentUserAvatar;
    @FXML private Button btnEditProfile;
    @FXML private Button btnLogout;
    
    // ========== Center Chat ==========
    @FXML private Circle avatarCircle;
    @FXML private Circle statusIndicator;
    @FXML private Label lblChatName;
    @FXML private Label lblChatStatus;
    @FXML private Button btnVideoCall;
    @FXML private Button btnInfo;
    @FXML private ScrollPane scrollMessages;
    @FXML private VBox messagesContainer;
    @FXML private VBox emptyState;
    @FXML private TextField txtMessage;
    @FXML private Button btnSend;
    @FXML private Button btnAttach;
    @FXML private Button btnImage;
    
    // ========== Right Sidebar ==========
    @FXML private VBox infoPanel;
    @FXML private Circle infoAvatar;
    @FXML private Label lblInfoName;
    @FXML private Label lblInfoStatus;
    @FXML private Button btnAddMember;
    @FXML private Button btnToggleNotification;
    @FXML private Button btnMuteConversation;
    @FXML private Button btnUnfriend;
    @FXML private Button btnShowFiles;
    
    // ========== Search Messages ==========
    @FXML private Button btnSearchMessages;
    @FXML private VBox searchMessagesPanel;
    @FXML private TextField txtSearchMessages;
    @FXML private Button btnCloseSearch;
    @FXML private Label lblMessageSearchResults;
    @FXML private ListView<String> lstMessageSearchResults;
    
    // Store search results for click handling
    private List<ChatMessage> currentMessageSearchResults = new java.util.ArrayList<>();
    
    // ========== Services ==========
    private AegisTalkClientService clientService;
    private ChatClient chatClient;
    private ModerationClient moderationClient;
    private org.example.demo2.service.FileTransferService fileTransferService;
    
    // ========== Data ==========
    private final ObservableList<ContactItem> friendsList = FXCollections.observableArrayList();
    private final ObservableList<ContactItem> groupsList = FXCollections.observableArrayList();
    private final ObservableList<User> pendingRequests = FXCollections.observableArrayList();
    private ContactItem currentChat;
    private String currentRoomId; // Lưu room ID hiện tại để so sánh với incoming messages
    private final Set<Integer> shownCallDialogs = java.util.Collections.synchronizedSet(new HashSet<>()); // Track các call đã hiển thị dialog
    private final Set<Integer> activeCallDialogs = java.util.Collections.synchronizedSet(new HashSet<>()); // Track các dialog đang mở
    private final Map<Long, User> userCache = new HashMap<>();
    
    // ========== Notification ==========
    private AudioClip notificationSound;
    private final Map<String, Integer> unreadMessages = new HashMap<>(); // roomId -> unread count
    
    // ========== Constants ==========
    // Sử dụng ServerConfig để dễ thay đổi khi chạy trên mạng khác nhau
    private static final String CHAT_HOST = org.example.demo2.config.ServerConfig.SERVER_HOST;
    private static final int CHAT_PORT = org.example.demo2.config.ServerConfig.CHAT_PORT;
    private static final String MOD_HOST = org.example.demo2.config.ServerConfig.SERVER_HOST;
    private static final int MOD_PORT = org.example.demo2.config.ServerConfig.MODERATION_PORT;
    private static final String[] AVATAR_COLORS = new String[]{
            "#38bdf8", // blue
            "#22c55e", // green
            "#a855f7", // purple
            "#f97316", // orange
            "#ec4899", // pink
            "#14b8a6", // teal
            "#6366f1"  // indigo
    };
    private static final String ONLINE_COLOR = "#22c55e";
    private static final String OFFLINE_COLOR = "#6b7280";
    private static final String TYPING_COLOR = "#fbbf24";

    // Typing presence (UDP multicast)
    private static final String TYPING_GROUP = "239.1.1.1";
    private static final int TYPING_PORT = 4447;
    private MulticastSocket typingSocket;
    private Thread typingThread;
    private final AtomicBoolean typingRunning = new AtomicBoolean(false);
    private long lastTypingSent = 0L;
    private javafx.animation.Timeline typingTimeline;
    
    // Track avatar paths that have been logged to avoid spam
    private final java.util.Set<String> loggedAvatarPaths = new java.util.HashSet<>();
    
    // Track avatar URLs that have failed to load (to avoid spam error logs)
    private final java.util.Set<String> failedAvatarUrls = new java.util.HashSet<>();
    
    // Cache loaded images to avoid reloading
    private final java.util.Map<String, Image> imageCache = new java.util.concurrent.ConcurrentHashMap<>();
    
    @FXML
    private void initialize() {
        try {
            clientService = new AegisTalkClientService();
            fileTransferService = new org.example.demo2.service.FileTransferService();
            loadFriends();
            loadPendingRequests();
        } catch (Exception e) {
            showError("Lỗi kết nối: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Hiển thị tên user hiện tại
        if (lblCurrentUser != null) {
            lblCurrentUser.setText(Session.getDisplayName());
        }
        String currentAvatarPath = Session.getAvatarPath();
        applyAvatar(currentUserAvatar, currentAvatarPath, fallbackColorForUser(Session.getUserId()));
        
        // Tự động migrate avatar cũ (file path) sang URL nếu cần (chạy background)
        if (currentAvatarPath != null && !currentAvatarPath.isBlank() && 
            !currentAvatarPath.startsWith("http://") && !currentAvatarPath.startsWith("https://")) {
            // Avatar cũ là file path, thử migrate sang URL trong background
            javafx.concurrent.Task<Void> migrateTask = new javafx.concurrent.Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    File avatarFile = getAvatarFile(currentAvatarPath);
                    if (avatarFile != null && avatarFile.exists()) {
                        // File tồn tại, upload lên server
                        try {
                            org.example.demo2.service.FileTransferService.FileUploadResult result = 
                                fileTransferService.uploadFile(avatarFile, Session.getUserId(), null);
                            String avatarUrl = result.getDownloadUrl();
                            // Tự động cập nhật avatar URL vào database
                            try {
                                User updated = clientService.updateProfile(Session.getUserId(), Session.getDisplayName(), avatarUrl);
                                if (updated != null) {
                                    Session.setUser(updated.id(), updated.displayName(), updated.avatarPath());
                                    Platform.runLater(() -> {
                                        applyAvatar(currentUserAvatar, avatarUrl, fallbackColorForUser(Session.getUserId()));
                                        loadFriends(); // Refresh để cập nhật avatar cho bạn bè
                                    });
                                    System.out.println("[MainChatController] Auto-migrated avatar from file path to URL: " + avatarUrl);
                                }
                            } catch (Exception e) {
                                System.err.println("[MainChatController] Failed to update avatar URL in database: " + e.getMessage());
                            }
                        } catch (IOException e) {
                            System.err.println("[MainChatController] Failed to migrate avatar to server: " + e.getMessage());
                        }
                    } else {
                        System.out.println("[MainChatController] Avatar file not found (may be from another machine): " + currentAvatarPath);
                        System.out.println("[MainChatController] User needs to upload avatar again via profile dialog");
                    }
                    return null;
                }
            };
            new Thread(migrateTask).start();
        }
        
        // Setup search
        setupSearch();
        
        // Setup tabs
        setupTabs();
        
        // Setup contacts list
        setupContactsList();
        
        // Setup chat input
        txtMessage.setOnAction(e -> sendMessage());
        txtMessage.textProperty().addListener((obs, oldVal, newVal) -> sendTypingSignal());
        btnSend.setOnAction(e -> sendMessage());
        
        // Setup call buttons
        btnVideoCall.setOnAction(e -> handleVideoCall());
        if (btnInfo != null) {
            btnInfo.setOnAction(e -> toggleInfoPanel());
        }
        if (btnShowFiles != null) {
            btnShowFiles.setOnAction(e -> handleShowFiles());
        }
        
        // Setup add member button
        if (btnAddMember != null) {
            btnAddMember.setOnAction(e -> handleAddMember());
        }
        
        // Connect TCP chat
        connectChat();
        
        // Connect moderation
        connectModeration();
        
        // Load notification sound
        loadNotificationSound();
        
        // Start auto-refresh cho friend requests (real-time updates)
        startAutoRefresh();
        
        // Start checking incoming calls định kỳ
        startIncomingCallChecker();

        // Start typing presence (UDP multicast)
        startTypingPresence();
    }
    
    /**
     * Load notification sound for incoming messages.
     */
    private void loadNotificationSound() {
        try {
            // Thử tải từ resource nếu có (WAV hoặc MP3)
            var wavResource = getClass().getResource("/org/example/demo2/sounds/notification.wav");
            var mp3Resource = getClass().getResource("/org/example/demo2/sounds/notification.mp3");
            
            if (wavResource != null) {
                notificationSound = new AudioClip(wavResource.toExternalForm());
                notificationSound.setVolume(0.5);
                System.out.println("[MainChatController] Notification sound loaded (WAV)");
            } else if (mp3Resource != null) {
                notificationSound = new AudioClip(mp3Resource.toExternalForm());
                notificationSound.setVolume(0.5);
                System.out.println("[MainChatController] Notification sound loaded (MP3)");
            } else {
                // Không có file âm thanh, sử dụng system beep
                System.out.println("[MainChatController] No notification sound file found, using system beep");
                notificationSound = null;
            }
        } catch (Exception e) {
            System.err.println("[MainChatController] Could not load notification sound: " + e.getMessage());
            notificationSound = null;
        }
    }
    
    /**
     * Play notification sound when receiving a message.
     */
    private void playNotificationSound(String roomId) {
        // Kiểm tra nếu notification bị tắt
        if (!Session.isNotificationEnabled()) {
            return;
        }
        
        // Kiểm tra nếu conversation này bị mute
        if (Session.isConversationMuted(roomId)) {
            return;
        }
        
        // Play sound
        if (notificationSound != null) {
            notificationSound.play();
        } else {
            // Fallback: sử dụng system beep
            java.awt.Toolkit.getDefaultToolkit().beep();
        }
    }
    
    /**
     * Toggle global notification on/off.
     */
    @FXML
    private void handleToggleNotification() {
        Session.toggleNotification();
        updateNotificationUI();
        
        String status = Session.isNotificationEnabled() ? "bật" : "tắt";
        showInfo("Thông báo đã " + status);
    }

    @FXML
    private void handleEditProfile() {
        if (clientService == null) {
            showError("Chưa kết nối tới server");
            return;
        }
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Cập nhật hồ sơ");
        dialog.setHeaderText(null);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(760);
        dialog.getDialogPane().getStyleClass().add("profile-dialog");
        try {
            var css = getClass().getResource("/org/example/demo2/ui/css/theme.css");
            if (css != null && !dialog.getDialogPane().getStylesheets().contains(css.toExternalForm())) {
                dialog.getDialogPane().getStylesheets().add(css.toExternalForm());
            }
        } catch (Exception ignored) {}

        // Header
        HBox header = new HBox(10);
        header.getStyleClass().add("profile-header");
        VBox headerText = new VBox(4);
        Label title = new Label("Cập nhật hồ sơ");
        title.getStyleClass().add("profile-title");
        Label subTitle = new Label("Tên hiển thị và avatar sẽ xuất hiện với bạn bè");
        subTitle.getStyleClass().add("profile-subtitle");
        headerText.getChildren().addAll(title, subTitle);
        header.getChildren().addAll(headerText);

        TextField displayNameField = new TextField(Session.getDisplayName());
        displayNameField.setPromptText("Nhập tên hiển thị");
        displayNameField.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(displayNameField, Priority.ALWAYS);
        // Kiểm tra và migrate avatar cũ (file path) sang URL nếu cần
        String currentAvatarPath = Session.getAvatarPath();
        String avatarPathToUse = currentAvatarPath;
        if (currentAvatarPath != null && !currentAvatarPath.isBlank() && 
            !currentAvatarPath.startsWith("http://") && !currentAvatarPath.startsWith("https://")) {
            // Avatar cũ là file path, thử migrate sang URL
            File avatarFile = getAvatarFile(currentAvatarPath);
            if (avatarFile != null && avatarFile.exists()) {
                // File tồn tại, upload lên server
                try {
                    org.example.demo2.service.FileTransferService.FileUploadResult result = 
                        fileTransferService.uploadFile(avatarFile, Session.getUserId(), null);
                    avatarPathToUse = result.getDownloadUrl();
                    // Tự động cập nhật avatar URL vào database
                    try {
                        User updated = clientService.updateProfile(Session.getUserId(), Session.getDisplayName(), avatarPathToUse);
                        if (updated != null) {
                            Session.setUser(updated.id(), updated.displayName(), updated.avatarPath());
                            System.out.println("[MainChatController] Migrated avatar from file path to URL: " + avatarPathToUse);
                        }
                    } catch (Exception e) {
                        System.err.println("[MainChatController] Failed to update avatar URL in database: " + e.getMessage());
                    }
                } catch (IOException e) {
                    System.err.println("[MainChatController] Failed to migrate avatar to server: " + e.getMessage());
                    // Giữ nguyên file path nếu upload thất bại
                }
            }
        }
        
        TextField avatarPathField = new TextField(avatarPathToUse != null ? avatarPathToUse : "");
        avatarPathField.setPromptText("Đường dẫn file ảnh hoặc URL");
        avatarPathField.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(avatarPathField, Priority.ALWAYS);
        Button browseBtn = new Button("Chọn ảnh");
        browseBtn.getStyleClass().add("profile-browse-btn");
        browseBtn.setMinWidth(96);
        HBox.setHgrow(avatarPathField, Priority.ALWAYS);
        Circle preview = new Circle(28);
        applyAvatar(preview, avatarPathField.getText(), fallbackColorForUser(Session.getUserId()));
        avatarPathField.textProperty().addListener((obs, oldVal, newVal) ->
                applyAvatar(preview, newVal, fallbackColorForUser(Session.getUserId())));
        browseBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Ảnh", "*.png", "*.jpg", "*.jpeg", "*.gif"),
                    new FileChooser.ExtensionFilter("Tất cả", "*.*")
            );
            File file = chooser.showOpenDialog(btnEditProfile != null ? btnEditProfile.getScene().getWindow() : null);
            if (file != null) {
                // Upload avatar lên HTTP File Server và lưu URL
                try {
                    browseBtn.setDisable(true);
                    browseBtn.setText("Đang tải...");
                    org.example.demo2.service.FileTransferService.FileUploadResult result = 
                        fileTransferService.uploadFile(file, Session.getUserId(), progress -> {
                            // Có thể hiển thị progress nếu cần
                        });
                    // Lưu URL vào database
                    String avatarUrl = result.getDownloadUrl();
                    avatarPathField.setText(avatarUrl);
                    // Update preview ngay lập tức
                    applyAvatar(preview, avatarUrl, fallbackColorForUser(Session.getUserId()));
                    browseBtn.setDisable(false);
                    browseBtn.setText("Chọn ảnh");
                } catch (IOException ex) {
                    showError("Không thể upload avatar: " + ex.getMessage());
                    browseBtn.setDisable(false);
                    browseBtn.setText("Chọn ảnh");
                    ex.printStackTrace();
                }
            }
        });

        PasswordField currentPasswordField = new PasswordField();
        currentPasswordField.setPromptText("Bắt buộc nếu đổi mật khẩu");
        currentPasswordField.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(currentPasswordField, Priority.ALWAYS);
        PasswordField newPasswordField = new PasswordField();
        newPasswordField.setPromptText("Mật khẩu mới");
        newPasswordField.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(newPasswordField, Priority.ALWAYS);
        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Nhập lại mật khẩu mới");
        confirmPasswordField.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(confirmPasswordField, Priority.ALWAYS);

        GridPane publicGrid = new GridPane();
        publicGrid.setHgap(12);
        publicGrid.setVgap(12);
        publicGrid.setPadding(new Insets(8, 0, 0, 0));
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(30);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(70);
        publicGrid.getColumnConstraints().addAll(col1, col2);
        Label lblDisplayName = new Label("Tên hiển thị");
        lblDisplayName.getStyleClass().add("profile-label");
        publicGrid.add(lblDisplayName, 0, 0);
        publicGrid.add(displayNameField, 1, 0);
        Label lblAvatar = new Label("Avatar");
        lblAvatar.getStyleClass().add("profile-label");
        publicGrid.add(lblAvatar, 0, 1);
        HBox avatarRow = new HBox(10, avatarPathField, browseBtn, preview);
        avatarRow.setAlignment(Pos.CENTER_LEFT);
        publicGrid.add(avatarRow, 1, 1);
        publicGrid.setMaxWidth(Double.MAX_VALUE);
        TitledPane publicPane = new TitledPane("Thông tin công khai", publicGrid);
        publicPane.getStyleClass().add("profile-section");
        publicPane.setExpanded(true);
        publicPane.setCollapsible(false);

        GridPane securityGrid = new GridPane();
        securityGrid.setHgap(12);
        securityGrid.setVgap(12);
        securityGrid.setPadding(new Insets(8, 0, 0, 0));
        securityGrid.getColumnConstraints().addAll(col1, col2);
        Label lblCurrentPw = new Label("Mật khẩu hiện tại");
        lblCurrentPw.getStyleClass().add("profile-label");
        securityGrid.add(lblCurrentPw, 0, 0);
        securityGrid.add(currentPasswordField, 1, 0);
        Label lblNewPw = new Label("Mật khẩu mới");
        lblNewPw.getStyleClass().add("profile-label");
        securityGrid.add(lblNewPw, 0, 1);
        securityGrid.add(newPasswordField, 1, 1);
        Label lblConfirmPw = new Label("Nhập lại mật khẩu mới");
        lblConfirmPw.getStyleClass().add("profile-label");
        securityGrid.add(lblConfirmPw, 0, 2);
        securityGrid.add(confirmPasswordField, 1, 2);
        securityGrid.setMaxWidth(Double.MAX_VALUE);
        TitledPane securityPane = new TitledPane("Bảo mật (tuỳ chọn)", securityGrid);
        securityPane.getStyleClass().add("profile-section");
        securityPane.setExpanded(true);
        securityPane.setCollapsible(false);

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("profile-error");

        VBox content = new VBox(12, header, publicPane, securityPane, errorLabel);
        content.setPadding(new Insets(6, 0, 0, 0));
        dialog.getDialogPane().setContent(content);

        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.getStyleClass().add("profile-ok-btn");
        Button cancelButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        if (cancelButton != null) {
            cancelButton.getStyleClass().add("profile-cancel-btn");
        }

        BooleanBinding displayNameInvalid = displayNameField.textProperty().isEmpty();
        BooleanBinding passwordChangeRequested = currentPasswordField.textProperty().isNotEmpty()
                .or(newPasswordField.textProperty().isNotEmpty())
                .or(confirmPasswordField.textProperty().isNotEmpty());
        BooleanBinding passwordInvalid = passwordChangeRequested.and(
                currentPasswordField.textProperty().isEmpty()
                        .or(newPasswordField.textProperty().isEmpty())
                        .or(confirmPasswordField.textProperty().isEmpty())
                        .or(newPasswordField.textProperty().isNotEqualTo(confirmPasswordField.textProperty()))
        );
        okButton.disableProperty().bind(displayNameInvalid.or(passwordInvalid));

        okButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String newDisplayName = displayNameField.getText().trim();
            String avatarPath = avatarPathField.getText().trim();
            String currentPw = currentPasswordField.getText();
            String newPw = newPasswordField.getText();
            String confirmPw = confirmPasswordField.getText();

            errorLabel.setText("");
            if (newDisplayName.isEmpty()) {
                errorLabel.setText("Tên hiển thị không được để trống");
                event.consume();
                return;
            }

            try {
                boolean profileChanged = !newDisplayName.equals(Session.getDisplayName())
                        || !Objects.equals(avatarPath, Session.getAvatarPath());
                if (profileChanged) {
                    User updated = clientService.updateProfile(Session.getUserId(), newDisplayName,
                            avatarPath.isBlank() ? null : avatarPath);
                    if (updated != null) {
                        Session.setUser(updated.id(), updated.displayName(), updated.avatarPath());
                        lblCurrentUser.setText(updated.displayName());
                        applyAvatar(currentUserAvatar, updated.avatarPath(), fallbackColorForUser(updated.id()));
                        userCache.put(updated.id(), updated);
                        loadFriends();
                    }
                }

                if (!newPw.isBlank() || !currentPw.isBlank() || !confirmPw.isBlank()) {
                    if (currentPw.isBlank()) {
                        errorLabel.setText("Vui lòng nhập mật khẩu hiện tại");
                        event.consume();
                        return;
                    }
                    if (newPw.isBlank() || confirmPw.isBlank()) {
                        errorLabel.setText("Vui lòng nhập và xác nhận mật khẩu mới");
                        event.consume();
                        return;
                    }
                    if (!newPw.equals(confirmPw)) {
                        errorLabel.setText("Mật khẩu mới không khớp");
                        event.consume();
                        return;
                    }
                    boolean changed = clientService.changePassword(Session.getUserId(), currentPw, newPw);
                    if (!changed) {
                        errorLabel.setText("Mật khẩu hiện tại không đúng");
                        event.consume();
                        return;
                    }
                }
            } catch (RemoteException e) {
                errorLabel.setText("Lỗi cập nhật hồ sơ: " + e.getMessage());
                event.consume();
            }
        });

        dialog.showAndWait();
    }
    
    /**
     * Toggle mute for current conversation.
     */
    @FXML
    private void handleMuteCurrentConversation() {
        if (currentRoomId == null) {
            showError("Chưa chọn cuộc trò chuyện");
            return;
        }
        
        Session.toggleConversationMute(currentRoomId);
        updateMuteUI();
        
        String status = Session.isConversationMuted(currentRoomId) ? "đã tắt" : "đã bật";
        showInfo("Thông báo " + status + " cho cuộc trò chuyện này");
    }

    @FXML
    private void handleUnfriend() {
        if (currentChat == null) {
            return;
        }
        
        // Kiểm tra xem đang ở group chat hay direct chat
        if (currentChat.conversation != null && "GROUP".equals(currentChat.conversation.type())) {
            // Rời nhóm
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Rời nhóm");
            confirm.setHeaderText("Bạn chắc chắn muốn rời nhóm này?");
            confirm.setContentText("Bạn sẽ không nhận được tin nhắn từ nhóm này nữa.");
            confirm.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.OK) {
                    try {
                        Long userId = Session.getUserId();
                        Long groupId = currentChat.conversation.id();
                        String userName = Session.getDisplayName();
                        
                        // Gửi system message thông báo rời nhóm trước khi rời
                        String systemMsg = userName + " đã rời nhóm";
                        ChatMessage systemMessage = new ChatMessage(
                            groupId.toString(),
                            "System",
                            org.example.demo2.model.MessageType.SYSTEM,
                            systemMsg,
                            null,
                            System.currentTimeMillis()
                        );
                        
                        // Lưu vào database
                        clientService.saveMessage(systemMessage);
                        
                        // Broadcast qua TCP để tất cả thành viên trong nhóm nhận được
                        try {
                            if (chatClient != null) {
                                chatClient.send(systemMessage);
                            }
                        } catch (Exception e) {
                            System.err.println("Failed to broadcast leave group message: " + e.getMessage());
                        }
                        
                        // Rời nhóm
                        boolean success = clientService.removeMember(groupId, userId, userId);
                        if (!success) {
                            showError("Không thể rời nhóm");
                            return;
                        }
                        
                        // Cập nhật UI
                        messagesContainer.getChildren().clear();
                        lblChatName.setText("");
                        applyStatus(lblChatStatus, "", ONLINE_COLOR);
                        setStatusIndicator(false);
                        currentChat = null;
                        currentRoomId = null;
                        loadGroups();
                        showInfo("Đã rời nhóm");
                    } catch (RemoteException e) {
                        showError("Lỗi rời nhóm: " + e.getMessage());
                    }
                }
            });
        } else if (currentChat.user != null) {
            // Hủy kết bạn (chat 1-1)
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Huỷ kết bạn");
            confirm.setHeaderText("Bạn chắc chắn muốn huỷ kết bạn và xoá toàn bộ tin nhắn?");
            confirm.setContentText("Thao tác này sẽ xoá lịch sử trò chuyện giữa hai người.");
            confirm.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.OK) {
                    try {
                        Long me = Session.getUserId();
                        Long other = currentChat.user.id();
                        // Xoá tin nhắn cuộc trò chuyện hiện tại
                        if (currentRoomId != null) {
                            clientService.deleteConversationMessages(currentRoomId);
                        }
                        // Huỷ kết bạn 2 chiều
                        clientService.removeFriend(me, other);

                        // Cập nhật UI
                        messagesContainer.getChildren().clear();
                        lblChatName.setText("");
                        applyStatus(lblChatStatus, "", ONLINE_COLOR);
                        setStatusIndicator(false);
                        currentChat = null;
                        currentRoomId = null;
                        loadFriends();
                        loadPendingRequests();
                        showInfo("Đã huỷ kết bạn và xoá tin nhắn");
                    } catch (RemoteException e) {
                        showError("Lỗi huỷ kết bạn: " + e.getMessage());
                    }
                }
            });
        }
    }

    @FXML
    private void handleShowFiles() {
        if (currentRoomId == null) {
            showError("Chưa chọn cuộc trò chuyện");
            return;
        }
        List<ChatMessage> files;
        try {
            files = clientService.getMessageHistory(currentRoomId, 500).stream()
                    .filter(msg -> msg.type() == org.example.demo2.model.MessageType.FILE
                            || msg.type() == org.example.demo2.model.MessageType.IMAGE)
                    .collect(Collectors.toList());
        } catch (RemoteException e) {
            showError("Lỗi tải danh sách file: " + e.getMessage());
            return;
        }

        if (files.isEmpty()) {
            showInfo("Chưa có file nào trong cuộc trò chuyện này");
            return;
        }

        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle("");
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.getDialogPane().getStyleClass().add("file-dialog");
        dlg.getDialogPane().setPrefWidth(600);
        dlg.getDialogPane().setPrefHeight(500);

        // Custom header
        HBox headerBox = new HBox(12);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.setPadding(new Insets(20, 24, 16, 24));
        Label headerIcon = new Label("📁");
        headerIcon.getStyleClass().add("file-header-icon");
        VBox headerText = new VBox(2);
        Label headerTitle = new Label("File & phương tiện");
        headerTitle.getStyleClass().add("file-header-title");
        Label headerSubtitle = new Label(files.size() + " file trong cuộc trò chuyện");
        headerSubtitle.getStyleClass().add("file-header-subtitle");
        headerText.getChildren().addAll(headerTitle, headerSubtitle);
        headerBox.getChildren().addAll(headerIcon, headerText);

        // File list container
        VBox fileListContainer = new VBox(8);
        fileListContainer.setPadding(new Insets(0, 24, 24, 24));

        for (ChatMessage msg : files) {
            String payload = msg.payloadRef();
            String[] parts = payload != null ? payload.split("\\|") : new String[0];
            String filename = parts.length >= 2 ? parts[1] : "(không tên)";
            String size = parts.length >= 3 ? formatFileSizeSafe(parts[2]) : "";
            
            // File item container
            HBox fileItem = new HBox(16);
            fileItem.setAlignment(Pos.CENTER_LEFT);
            fileItem.setPadding(new Insets(14, 16, 14, 16));
            fileItem.getStyleClass().add("file-item");
            
            // File icon
            String icon = getFileIcon(filename);
            Label iconLabel = new Label(icon);
            iconLabel.setStyle("-fx-font-size: 32px;");
            
            // File info
            VBox fileInfo = new VBox(4);
            fileInfo.setAlignment(Pos.CENTER_LEFT);
            Label nameLabel = new Label(filename);
            nameLabel.setStyle("-fx-text-fill: #e2e8f0; -fx-font-size: 14px; -fx-font-weight: 600;");
            nameLabel.setWrapText(true);
            nameLabel.setMaxWidth(300);
            Label sizeLabel = new Label(size);
            sizeLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
            fileInfo.getChildren().addAll(nameLabel, sizeLabel);
            
            // Spacer
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            
            // Download button
            Button downloadBtn = new Button("⬇ Tải xuống");
            downloadBtn.getStyleClass().add("file-download-btn");
            downloadBtn.setOnAction(ev -> {
                try {
                    long fileId = Long.parseLong(parts[0]);
                    downloadFile(fileId, filename);
                } catch (Exception ex) {
                    showError("Không thể tải: " + ex.getMessage());
                }
            });
            
            fileItem.getChildren().addAll(iconLabel, fileInfo, spacer, downloadBtn);
            fileListContainer.getChildren().add(fileItem);
        }

        // ScrollPane for file list
        ScrollPane scrollPane = new ScrollPane(fileListContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        
        // Main content
        VBox mainContent = new VBox();
        mainContent.getChildren().addAll(headerBox, scrollPane);
        mainContent.setStyle("-fx-background-color: #0f172a;");
        
        dlg.getDialogPane().setContent(mainContent);
        
        // Style close button
        Button closeButton = (Button) dlg.getDialogPane().lookupButton(ButtonType.CLOSE);
        if (closeButton != null) {
            closeButton.getStyleClass().add("file-close-btn");
        }
        
        dlg.showAndWait();
    }

    private String formatFileSizeSafe(String sizeStr) {
        try {
            long size = Long.parseLong(sizeStr);
            return formatFileSize(size);
        } catch (Exception e) {
            return "";
        }
    }
    
    /**
     * Update notification button/label UI.
     */
    private void updateNotificationUI() {
        if (btnToggleNotification != null) {
            if (Session.isNotificationEnabled()) {
                btnToggleNotification.setText("🔔");
                btnToggleNotification.getStyleClass().removeAll("notification-disabled");
                if (!btnToggleNotification.getStyleClass().contains("notification-enabled")) {
                    btnToggleNotification.getStyleClass().add("notification-enabled");
                }
            } else {
                btnToggleNotification.setText("🔕");
                btnToggleNotification.getStyleClass().removeAll("notification-enabled");
                if (!btnToggleNotification.getStyleClass().contains("notification-disabled")) {
                    btnToggleNotification.getStyleClass().add("notification-disabled");
                }
            }
        }
    }
    
    /**
     * Update mute status UI for current conversation in info panel.
     */
    private void updateMuteUI() {
        if (btnMuteConversation != null && currentRoomId != null) {
            if (Session.isConversationMuted(currentRoomId)) {
                btnMuteConversation.setText("🔔  Bật thông báo");
            } else {
                btnMuteConversation.setText("🔕  Tắt thông báo");
            }
        }
    }
    
    // ==================== MESSAGE SEARCH ====================
    
    /**
     * Handle search messages button click - show/hide search panel.
     */
    @FXML
    private void handleSearchMessages() {
        if (currentRoomId == null) {
            showError("Chưa chọn cuộc trò chuyện");
            return;
        }
        
        if (searchMessagesPanel != null) {
            boolean isVisible = searchMessagesPanel.isVisible();
            searchMessagesPanel.setVisible(!isVisible);
            searchMessagesPanel.setManaged(!isVisible);
            
            if (!isVisible) {
                // Focus vào ô tìm kiếm khi mở
                txtSearchMessages.requestFocus();
                txtSearchMessages.clear();
                lblMessageSearchResults.setText("");
                lstMessageSearchResults.setVisible(false);
                lstMessageSearchResults.setManaged(false);
            }
        }
    }
    
    /**
     * Close message search panel.
     */
    @FXML
    private void closeMessageSearch() {
        if (searchMessagesPanel != null) {
            searchMessagesPanel.setVisible(false);
            searchMessagesPanel.setManaged(false);
            txtSearchMessages.clear();
            lblMessageSearchResults.setText("");
            lstMessageSearchResults.setVisible(false);
            lstMessageSearchResults.setManaged(false);
            
            // Clear highlight
            clearSearchHighlight();
        }
    }
    
    /**
     * Perform message search in current conversation.
     */
    @FXML
    private void performMessageSearch() {
        if (currentRoomId == null) {
            showError("Chưa chọn cuộc trò chuyện");
            return;
        }
        
        String keyword = txtSearchMessages.getText().trim();
        if (keyword.isEmpty()) {
            lblMessageSearchResults.setText("Nhập từ khóa để tìm kiếm");
            lstMessageSearchResults.setVisible(false);
            lstMessageSearchResults.setManaged(false);
            return;
        }
        
        try {
            // Lấy tất cả tin nhắn trong conversation
            List<ChatMessage> allMessages = clientService.getMessageHistory(currentRoomId, 500);
            
            // Lọc tin nhắn chứa từ khóa
            String keywordLower = keyword.toLowerCase();
            List<ChatMessage> matchedMessages = allMessages.stream()
                    .filter(msg -> msg.text() != null && msg.text().toLowerCase().contains(keywordLower))
                    .collect(Collectors.toList());
            
            if (matchedMessages.isEmpty()) {
                lblMessageSearchResults.setText("Không tìm thấy kết quả cho \"" + keyword + "\"");
                lstMessageSearchResults.setVisible(false);
                lstMessageSearchResults.setManaged(false);
                currentMessageSearchResults.clear();
            } else {
                lblMessageSearchResults.setText("Tìm thấy " + matchedMessages.size() + " kết quả");
                
                // Store results for click handling
                currentMessageSearchResults.clear();
                currentMessageSearchResults.addAll(matchedMessages);
                
                // Convert to display strings
                ObservableList<String> displayItems = FXCollections.observableArrayList();
                for (ChatMessage msg : matchedMessages) {
                    String senderName = msg.from();
                    try {
                        Long userId = Long.parseLong(msg.from());
                        senderName = clientService.getUserDisplayName(userId);
                    } catch (Exception e) {
                        // Use as-is
                    }
                    String text = msg.text();
                    if (text.length() > 50) {
                        text = text.substring(0, 50) + "...";
                    }
                    String time = formatTimestamp(msg.ts());
                    displayItems.add(senderName + " • " + time + "\n" + text);
                }
                
                // Setup ListView
                setupMessageSearchResultsList();
                lstMessageSearchResults.setItems(displayItems);
                lstMessageSearchResults.setVisible(true);
                lstMessageSearchResults.setManaged(true);
                
                // Highlight từ khóa trong messages container
                highlightSearchResults(keyword);
            }
        } catch (RemoteException e) {
            showError("Lỗi tìm kiếm: " + e.getMessage());
        }
    }
    
    /**
     * Setup message search results ListView with custom cell factory.
     */
    private void setupMessageSearchResultsList() {
        lstMessageSearchResults.setCellFactory(listView -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    setStyle("");
                } else {
                    VBox vbox = new VBox(4);
                    vbox.setPadding(new Insets(8, 12, 8, 12));
                    vbox.setStyle("-fx-background-color: rgba(99, 102, 241, 0.1); -fx-background-radius: 8px; -fx-cursor: hand;");
                    
                    String[] parts = item.split("\n", 2);
                    
                    // Header (name • time)
                    Label headerLabel = new Label(parts[0]);
                    headerLabel.setStyle("-fx-text-fill: #6366f1; -fx-font-size: 11px; -fx-font-weight: 600;");
                    
                    // Message text
                    Label textLabel = new Label(parts.length > 1 ? parts[1] : "");
                    textLabel.setStyle("-fx-text-fill: #f1f5f9; -fx-font-size: 13px;");
                    textLabel.setWrapText(true);
                    
                    vbox.getChildren().addAll(headerLabel, textLabel);
                    setGraphic(vbox);
                    setStyle("-fx-background-color: transparent; -fx-padding: 2px;");
                    
                    // Hover effect
                    setOnMouseEntered(e -> vbox.setStyle("-fx-background-color: rgba(99, 102, 241, 0.2); -fx-background-radius: 8px; -fx-cursor: hand;"));
                    setOnMouseExited(e -> vbox.setStyle("-fx-background-color: rgba(99, 102, 241, 0.1); -fx-background-radius: 8px; -fx-cursor: hand;"));
                }
            }
        });
        
        // Click to scroll to message
        lstMessageSearchResults.setOnMouseClicked(event -> {
            int selectedIndex = lstMessageSearchResults.getSelectionModel().getSelectedIndex();
            if (selectedIndex >= 0 && selectedIndex < currentMessageSearchResults.size()) {
                ChatMessage selected = currentMessageSearchResults.get(selectedIndex);
                scrollToMessage(selected);
            }
        });
    }
    
    /**
     * Format timestamp to readable string.
     */
    private String formatTimestamp(long timestamp) {
        java.time.Instant instant = java.time.Instant.ofEpochMilli(timestamp);
        java.time.LocalDateTime dateTime = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault());
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        return dateTime.format(formatter);
    }
    
    /**
     * Scroll to a specific message in the chat.
     */
    private void scrollToMessage(ChatMessage targetMsg) {
        // Tìm message trong container dựa trên timestamp
        for (javafx.scene.Node node : messagesContainer.getChildren()) {
            if (node.getUserData() != null && node.getUserData().equals(targetMsg.ts())) {
                // Scroll đến vị trí này
                double totalHeight = messagesContainer.getHeight();
                double nodeY = node.getBoundsInParent().getMinY();
                double scrollValue = nodeY / totalHeight;
                scrollMessages.setVvalue(Math.min(1.0, Math.max(0.0, scrollValue)));
                
                // Highlight message tạm thời
                String originalStyle = node.getStyle();
                node.setStyle(originalStyle + "-fx-background-color: rgba(99, 102, 241, 0.3); -fx-background-radius: 12px;");
                
                // Reset sau 2 giây
                javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(Duration.seconds(2));
                pause.setOnFinished(e -> node.setStyle(originalStyle));
                pause.play();
                
                break;
            }
        }
    }
    
    /**
     * Highlight search results in messages container.
     */
    private void highlightSearchResults(String keyword) {
        String keywordLower = keyword.toLowerCase();
        
        for (javafx.scene.Node node : messagesContainer.getChildren()) {
            if (node instanceof HBox) {
                highlightInNode(node, keywordLower);
            }
        }
    }
    
    /**
     * Recursively search and highlight text in nodes.
     */
    private void highlightInNode(javafx.scene.Node node, String keyword) {
        if (node instanceof Label) {
            Label label = (Label) node;
            String text = label.getText();
            if (text != null && text.toLowerCase().contains(keyword)) {
                // Thêm style highlight
                String currentStyle = label.getStyle();
                if (!currentStyle.contains("-fx-background-color: rgba(245, 158, 11")) {
                    label.setStyle(currentStyle + "-fx-background-color: rgba(245, 158, 11, 0.3); -fx-background-radius: 4px;");
                    label.setUserData("search-highlighted");
                }
            }
        } else if (node instanceof javafx.scene.Parent) {
            for (javafx.scene.Node child : ((javafx.scene.Parent) node).getChildrenUnmodifiable()) {
                highlightInNode(child, keyword);
            }
        }
    }
    
    /**
     * Clear search highlight from all messages.
     */
    private void clearSearchHighlight() {
        for (javafx.scene.Node node : messagesContainer.getChildren()) {
            clearHighlightInNode(node);
        }
    }
    
    /**
     * Recursively clear highlight from nodes.
     */
    private void clearHighlightInNode(javafx.scene.Node node) {
        if (node instanceof Label) {
            Label label = (Label) node;
            if ("search-highlighted".equals(label.getUserData())) {
                String style = label.getStyle();
                // Remove highlight style
                style = style.replace("-fx-background-color: rgba(245, 158, 11, 0.3); -fx-background-radius: 4px;", "");
                label.setStyle(style);
                label.setUserData(null);
            }
        } else if (node instanceof javafx.scene.Parent) {
            for (javafx.scene.Node child : ((javafx.scene.Parent) node).getChildrenUnmodifiable()) {
                clearHighlightInNode(child);
            }
        }
    }
    
    // ==================== END MESSAGE SEARCH ====================
    
    /**
     * Highlight contact item when receiving new message.
     */
    private void highlightContactItem(String roomId) {
        Platform.runLater(() -> {
            // Tăng unread count
            unreadMessages.merge(roomId, 1, Integer::sum);
            
            // Refresh contact list để cập nhật highlight
            lstContacts.refresh();
        });
    }
    
    /**
     * Clear unread messages for a room when opening chat.
     */
    private void clearUnreadMessages(String roomId) {
        unreadMessages.remove(roomId);
        lstContacts.refresh();
    }
    
    /**
     * Auto-refresh friend requests và friends list mỗi 3 giây.
     */
    private void startAutoRefresh() {
        ScheduledService<Void> refreshService = new ScheduledService<Void>() {
            @Override
            protected Task<Void> createTask() {
                return new Task<Void>() {
                    @Override
                    protected Void call() throws Exception {
                        // Reload friends và requests
                        Platform.runLater(() -> {
                            loadFriends();
                            loadPendingRequests();
                            
                            // Nếu đang ở tab Requests, refresh lại
                            if (tabRequests.isSelected()) {
                                showRequestsTab();
                            }
                        });
                        return null;
                    }
                };
            }
        };
        refreshService.setPeriod(Duration.seconds(3));
        refreshService.start();
    }
    
    /**
     * Kiểm tra incoming calls định kỳ mỗi 1 giây.
     */
    private void startIncomingCallChecker() {
        ScheduledService<Void> callCheckerService = new ScheduledService<Void>() {
            @Override
            protected Task<Void> createTask() {
                return new Task<Void>() {
                    @Override
                    protected Void call() throws Exception {
                        checkIncomingCalls();
                        return null;
                    }
                };
            }
        };
        callCheckerService.setPeriod(Duration.seconds(1));
        callCheckerService.start();
    }
    
    private void setupSearch() {
        txtSearch.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.trim().isEmpty()) {
                searchResultsContainer.setVisible(false);
                searchResultsContainer.setManaged(false);
                return;
            }
            
            // Tìm kiếm user
            searchUsers(newVal.trim());
        });
        
        // Custom cell factory cho search results
        lstSearchResults.setCellFactory(listView -> new ListCell<User>() {
            @Override
            protected void updateItem(User user, boolean empty) {
                super.updateItem(user, empty);
                if (empty || user == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    HBox hbox = new HBox(12);
                    hbox.setAlignment(Pos.CENTER_LEFT);
                    hbox.setPadding(new Insets(10, 14, 10, 14));
                    
                    // Avatar
                    Circle avatar = new Circle(20);
                    applyAvatar(avatar, user);
                    
                    // Name
                    VBox vbox = new VBox(2);
                    Label nameLabel = new Label(user.displayName());
                    nameLabel.setStyle("-fx-text-fill: #f1f5f9; -fx-font-size: 14px; -fx-font-weight: 600;");
                    Label usernameLabel = new Label("@" + user.username());
                    usernameLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");
                    vbox.getChildren().addAll(nameLabel, usernameLabel);
                    
                    hbox.getChildren().addAll(avatar, vbox);
                    
                    // Add friend button
                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);
                    Button addBtn = new Button("+ Kết bạn");
                    addBtn.getStyleClass().add("add-friend-button");
                    addBtn.setOnAction(e -> sendFriendRequest(user));
                    
                    hbox.getChildren().addAll(spacer, addBtn);
                    setGraphic(hbox);
                }
            }
        });
    }
    
    private void searchUsers(String keyword) {
        try {
            List<User> results = clientService.searchUsers(keyword);
            for (User u : results) {
                userCache.put(u.id(), u);
            }
            lstSearchResults.setItems(FXCollections.observableArrayList(results));
            searchResultsContainer.setVisible(true);
            searchResultsContainer.setManaged(true);
        } catch (RemoteException e) {
            showError("Lỗi tìm kiếm: " + e.getMessage());
        }
    }
    
    private void sendFriendRequest(User user) {
        try {
            Long currentUserId = Session.getUserId();
            boolean success = clientService.sendFriendRequest(currentUserId, user.id());
            if (success) {
                showInfo("Đã gửi lời mời kết bạn đến " + user.displayName());
            } else {
                showError("Không thể gửi lời mời kết bạn");
            }
        } catch (RemoteException e) {
            showError("Lỗi: " + e.getMessage());
        }
    }
    
    private void setupTabs() {
        ToggleGroup tabGroup = new ToggleGroup();
        tabFriends.setToggleGroup(tabGroup);
        tabGroups.setToggleGroup(tabGroup);
        tabRequests.setToggleGroup(tabGroup);
        
        tabFriends.setOnAction(e -> showFriendsTab());
        tabGroups.setOnAction(e -> showGroupsTab());
        tabRequests.setOnAction(e -> showRequestsTab());
        
        // Mặc định hiển thị Friends
        showFriendsTab();
    }
    
    private void showFriendsTab() {
        lstContacts.setItems(friendsList);
    }
    
    private void showGroupsTab() {
        lstContacts.setItems(groupsList);
        // Load groups từ RMI
        loadGroups();
    }
    
    private void showRequestsTab() {
        // Hiển thị pending friend requests
        ObservableList<ContactItem> requests = FXCollections.observableArrayList();
        for (User user : pendingRequests) {
            requests.add(new ContactItem(user, "Lời mời kết bạn", null, false));
        }
        lstContacts.setItems(requests);
    }
    
    private void setupContactsList() {
        lstContacts.setCellFactory(listView -> new ListCell<ContactItem>() {
            @Override
            protected void updateItem(ContactItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    // Lấy roomId để kiểm tra unread
                    String roomId = null;
                    if (item.conversation != null) {
                        roomId = item.conversation.id().toString();
                    }
                    
                    // Kiểm tra có tin nhắn chưa đọc không
                    int unreadCount = roomId != null ? unreadMessages.getOrDefault(roomId, 0) : 0;
                    boolean hasUnread = unreadCount > 0;
                    
                    HBox hbox = new HBox(14);
                    hbox.setAlignment(Pos.CENTER_LEFT);
                    hbox.setPadding(new Insets(12, 16, 12, 16));
                    
                    // Background: selected > unread > normal
                    String bgColor;
                    if (isSelected()) {
                        bgColor = "rgba(59, 130, 246, 0.15)";
                    } else if (hasUnread) {
                        bgColor = "rgba(34, 197, 94, 0.15)"; // Green highlight for unread
                    } else {
                        bgColor = "transparent";
                    }
                    hbox.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 12px;");
                    
                    // Avatar with online indicator
                    StackPane avatarStack = new StackPane();
                    Circle avatar = new Circle(24);
                    String fallback = item.user != null
                            ? fallbackColorForUser(item.user.id())
                            : fallbackColorForUser(item.conversation != null ? item.conversation.id() : null);
                    applyAvatar(avatar, item.user != null ? item.user.avatarPath() : null, fallback);
                    avatarStack.getChildren().add(avatar);
                    
                    // Online indicator
                    Circle onlineIndicator = new Circle(7);
                    onlineIndicator.setTranslateX(17);
                    onlineIndicator.setTranslateY(17);
                    boolean online = item.online != null && item.online;
                    onlineIndicator.setFill(Color.web(online ? ONLINE_COLOR : OFFLINE_COLOR));
                    onlineIndicator.setStroke(Color.web("#0f172a"));
                    onlineIndicator.setStrokeWidth(2);
                    onlineIndicator.setVisible(item.user != null);
                    avatarStack.getChildren().add(onlineIndicator);
                    
                    // Info
                    VBox vbox = new VBox(4);
                    vbox.setAlignment(Pos.CENTER_LEFT);
                    HBox.setHgrow(vbox, Priority.ALWAYS);
                    
                    String displayName = item.user != null ? item.user.displayName() : 
                                        (item.conversation != null && item.conversation.title() != null ? item.conversation.title() : 
                                        (item.name != null ? item.name : "Unknown"));
                    Label nameLabel = new Label(displayName);
                    // Bold name if has unread
                    String nameStyle = hasUnread ? 
                            "-fx-text-fill: #ffffff; -fx-font-size: 14px; -fx-font-weight: 800;" :
                            "-fx-text-fill: #f1f5f9; -fx-font-size: 14px; -fx-font-weight: 600;";
                    nameLabel.setStyle(nameStyle);
                    
                    Label lastMsgLabel = new Label(item.lastMessage != null ? item.lastMessage : "Bắt đầu cuộc trò chuyện");
                    String msgStyle = hasUnread ?
                            "-fx-text-fill: #22c55e; -fx-font-size: 12px; -fx-font-weight: 600;" :
                            "-fx-text-fill: #64748b; -fx-font-size: 12px;";
                    lastMsgLabel.setStyle(msgStyle);
                    lastMsgLabel.setMaxWidth(200);
                    
                    vbox.getChildren().addAll(nameLabel, lastMsgLabel);
                    
                    hbox.getChildren().addAll(avatarStack, vbox);
                    
                    // Unread badge
                    if (hasUnread) {
                        Label badge = new Label(String.valueOf(unreadCount));
                        badge.setStyle("-fx-background-color: #22c55e; -fx-text-fill: white; " +
                                "-fx-font-size: 11px; -fx-font-weight: 700; -fx-background-radius: 10px; " +
                                "-fx-padding: 2px 8px; -fx-min-width: 20px; -fx-alignment: center;");
                        hbox.getChildren().add(badge);
                    }
                    
                    // Action buttons cho requests
                    if (tabRequests.isSelected() && item.user != null) {
                        Button acceptBtn = new Button("✓");
                        acceptBtn.setStyle("-fx-background-color: #22c55e; " +
                                "-fx-text-fill: white; -fx-background-radius: 8px; -fx-padding: 8px 14px; " +
                                "-fx-font-weight: 600; -fx-cursor: hand;");
                        acceptBtn.setOnAction(e -> acceptFriendRequest(item.user));
                        hbox.getChildren().add(acceptBtn);
                    }
                    
                    setGraphic(hbox);
                }
            }
        });
        
        lstContacts.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                openChat(newVal);
            }
        });
    }
    
    private void loadFriends() {
        try {
            Long userId = Session.getUserId();
            List<User> friends = clientService.getFriends(userId);
            friendsList.clear();
            for (User friend : friends) {
                // Thử migrate avatar của bạn bè nếu là file path và file tồn tại
                User friendToUse = friend;
                String friendAvatarPath = friend.avatarPath();
                if (friendAvatarPath != null && !friendAvatarPath.isBlank() && 
                    !friendAvatarPath.startsWith("http://") && !friendAvatarPath.startsWith("https://")) {
                    // Avatar là file path, thử migrate (chạy background)
                    File avatarFile = getAvatarFile(friendAvatarPath);
                    if (avatarFile != null && avatarFile.exists()) {
                        // File tồn tại, thử migrate (chạy background, không chặn UI)
                        javafx.concurrent.Task<User> migrateTask = new javafx.concurrent.Task<User>() {
                            @Override
                            protected User call() throws Exception {
                                try {
                                    org.example.demo2.service.FileTransferService.FileUploadResult result = 
                                        fileTransferService.uploadFile(avatarFile, friend.id(), null);
                                    String avatarUrl = result.getDownloadUrl();
                                    // Cập nhật avatar URL vào database (cần quyền admin hoặc friend tự update)
                                    // Tạm thời chỉ update trong userCache để hiển thị
                                    User updated = new User(friend.id(), friend.username(), friend.displayName(), avatarUrl);
                                    Platform.runLater(() -> {
                                        userCache.put(updated.id(), updated);
                                        // Refresh UI nếu đang hiển thị friend này
                                        javafx.application.Platform.runLater(() -> {
                                            loadFriends(); // Refresh để cập nhật avatar
                                        });
                                    });
                                    System.out.println("[MainChatController] Migrated friend avatar: " + friend.displayName() + " -> " + avatarUrl);
                                    return updated;
                                } catch (Exception e) {
                                    System.err.println("[MainChatController] Failed to migrate friend avatar: " + e.getMessage());
                                    return friend;
                                }
                            }
                        };
                        new Thread(migrateTask).start();
                    }
                }
                
                userCache.put(friendToUse.id(), friendToUse);
                boolean online = isUserOnline(friendToUse.id());
                // Lấy conversation và tin nhắn cuối cùng
                Conversation conv = null;
                ChatMessage lastMsg = null;
                try {
                    conv = clientService.getOrCreateDirectConversation(userId, friendToUse.id());
                    if (conv != null) {
                        lastMsg = clientService.getLastMessage(conv.id().toString());
                    }
                } catch (Exception e) {
                    // Ignore errors when getting conversation
                }
                
                String lastMsgText = lastMsg != null ? lastMsg.text() : null;
                friendsList.add(new ContactItem(friendToUse, lastMsgText, conv, online));
            }
        } catch (RemoteException e) {
            showError("Lỗi tải danh sách bạn bè: " + e.getMessage());
        }
    }
    
    private void loadPendingRequests() {
        try {
            Long userId = Session.getUserId();
            List<User> requests = clientService.getPendingFriendRequests(userId);
            for (User u : requests) {
                userCache.put(u.id(), u);
            }
            pendingRequests.setAll(requests);
        } catch (RemoteException e) {
            showError("Lỗi tải lời mời: " + e.getMessage());
        }
    }
    
    private void loadGroups() {
        try {
            Long userId = Session.getUserId();
            List<Conversation> conversations = clientService.getUserConversations(userId);
            groupsList.clear();
            for (Conversation conv : conversations) {
                if ("GROUP".equals(conv.type())) {
                    // Lấy tin nhắn cuối cùng
                    ChatMessage lastMsg = null;
                    try {
                        lastMsg = clientService.getLastMessage(conv.id().toString());
                    } catch (Exception e) {
                        // Ignore errors
                    }
                    String lastMsgText = lastMsg != null ? lastMsg.text() : null;
                    groupsList.add(new ContactItem(null, conv.title(), lastMsgText, conv, null));
                }
            }
        } catch (RemoteException e) {
            showError("Lỗi tải nhóm: " + e.getMessage());
        }
    }
    
    private void acceptFriendRequest(User user) {
        try {
            Long userId = Session.getUserId();
            boolean success = clientService.acceptFriendRequest(userId, user.id());
            if (success) {
                showInfo("Đã chấp nhận lời mời từ " + user.displayName());
                loadFriends();
                loadPendingRequests();
                showRequestsTab();
            }
        } catch (RemoteException e) {
            showError("Lỗi: " + e.getMessage());
        }
    }
    
    private void openChat(ContactItem item) {
        currentChat = item;
        
        // Ẩn empty state
        if (emptyState != null) {
            emptyState.setVisible(false);
            emptyState.setManaged(false);
        }
        
        if (item.user != null) {
            // Chat với bạn bè
            lblChatName.setText(item.user.displayName());
            boolean online = isUserOnline(item.user.id());
            applyStatus(lblChatStatus, online ? "Đang hoạt động" : "Không hoạt động",
                    online ? ONLINE_COLOR : OFFLINE_COLOR);
            applyAvatar(avatarCircle, item.user.avatarPath(), fallbackColorForUser(item.user.id()));
            setStatusIndicator(online);
            
            // Lấy hoặc tạo direct conversation
            try {
                Long userId = Session.getUserId();
                Conversation conv = clientService.getOrCreateDirectConversation(userId, item.user.id());
                currentRoomId = conv.id().toString();
                System.out.println("[MainChatController] Opened chat with user " + item.user.id() + ", conversation ID: " + currentRoomId);
            } catch (RemoteException e) {
                showError("Lỗi tạo conversation: " + e.getMessage());
                currentRoomId = null;
                return;
            }
            
            // Load message history
            loadMessageHistory(currentRoomId);
        } else if (item.conversation != null) {
            // Chat nhóm
            lblChatName.setText(item.conversation.title() != null ? item.conversation.title() : "Nhóm");
            lblChatStatus.setText("Nhóm");
            applyAvatar(avatarCircle, null, fallbackColorForUser(item.conversation.id()));
            currentRoomId = item.conversation.id().toString();
            System.out.println("[MainChatController] Opened group chat, conversation ID: " + currentRoomId);
            
            // Update status indicator for group
            if (statusIndicator != null) {
                statusIndicator.setVisible(false);
            }
            
            // Load message history
            loadMessageHistory(currentRoomId);
        }
        
        // Clear unread messages khi mở chat
        if (currentRoomId != null) {
            clearUnreadMessages(currentRoomId);
        }
        
        // Update mute UI
        updateMuteUI();
        
        // Cập nhật info panel nếu đang mở
        if (infoPanel != null && infoPanel.isVisible()) {
            updateInfoPanel();
        }
    }
    
    private void loadMessageHistory(String roomId) {
        try {
            List<ChatMessage> messages = clientService.getMessageHistory(roomId, 50);
            messagesContainer.getChildren().clear();
            for (ChatMessage msg : messages) {
                addMessageToUI(msg);
            }
            scrollToBottom();
        } catch (RemoteException e) {
            showError("Lỗi tải lịch sử: " + e.getMessage());
        }
    }
    
    private void connectChat() {
        try {
            System.out.println("[MainChatController] Connecting to chat server: " + CHAT_HOST + ":" + CHAT_PORT);
            chatClient = new ChatClient(CHAT_HOST, CHAT_PORT, this::onIncomingMessage);
            chatClient.connect();
            System.out.println("[MainChatController] Chat client connected successfully");
        } catch (IOException e) {
            System.err.println("[MainChatController] Failed to connect chat: " + e.getMessage());
            e.printStackTrace();
            showError("Lỗi kết nối chat: " + e.getMessage());
        }
    }
    
    private void connectModeration() {
        try {
            moderationClient = new ModerationClient(MOD_HOST, MOD_PORT);
        } catch (Exception e) {
            System.err.println("Moderation không khả dụng: " + e.getMessage());
        }
    }
    
    private void sendMessage() {
        if (currentChat == null || chatClient == null) {
            showError("Chưa chọn cuộc trò chuyện");
            return;
        }
        
        String text = txtMessage.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        
        // Moderation
        if (moderationClient != null) {
            try {
                ModerationResult result = moderationClient.moderateText(text);
                if (result.getDecision() == ModerationDecision.BLOCK) {
                    showError("Tin nhắn bị chặn: " + result.getReason());
                    return;
                }
            } catch (Exception e) {
                // Continue even if moderation fails
            }
        }
        
        // Sử dụng currentRoomId đã lưu (được set khi openChat)
        if (currentRoomId == null) {
            showError("Chưa chọn cuộc trò chuyện hoặc room ID không hợp lệ");
            return;
        }
        
        // Send message - dùng user ID thay vì display name để lưu vào DB
        Long userId = Session.getUserId();
        ChatMessage msg = ChatMessage.text(currentRoomId, userId.toString(), text);
        System.out.println("[MainChatController] ===== SENDING MESSAGE =====");
        System.out.println("[MainChatController] Conversation ID: " + currentRoomId + " (type: " + currentRoomId.getClass().getSimpleName() + ")");
        System.out.println("[MainChatController] From User ID: " + userId);
        System.out.println("[MainChatController] Text: " + text);
        System.out.println("[MainChatController] ChatClient connected: " + (chatClient != null));
        
        try {
            // Hiển thị tin nhắn của mình ngay lập tức (optimistic update)
            Platform.runLater(() -> {
                System.out.println("[MainChatController] Adding own message to UI (optimistic update)");
                addMessageToUI(msg);
                scrollToBottom();
            });
            
            // Gửi qua TCP
            System.out.println("[MainChatController] Sending via TCP...");
            chatClient.send(msg);
            System.out.println("[MainChatController] Message sent via TCP successfully");
            
            // Lưu vào database
            try {
                System.out.println("[MainChatController] Saving to database...");
                clientService.saveMessage(msg);
                System.out.println("[MainChatController] Message saved to database");
            } catch (Exception e) {
                System.err.println("[MainChatController] Failed to save message: " + e.getMessage());
                e.printStackTrace();
            }
            
            txtMessage.clear();
        } catch (Exception e) {
            System.err.println("[MainChatController] Error sending message: " + e.getMessage());
            e.printStackTrace();
            showError("Lỗi gửi tin: " + e.getMessage());
        }
    }
    
    private void onIncomingMessage(ChatMessage msg) {
        System.out.println("[MainChatController] ===== INCOMING MESSAGE =====");
        System.out.println("[MainChatController] Message room: " + msg.room());
        System.out.println("[MainChatController] Message from: " + msg.from());
        System.out.println("[MainChatController] Message text: " + msg.text());
        System.out.println("[MainChatController] Current room ID: " + currentRoomId);
        System.out.println("[MainChatController] Current chat: " + (currentChat != null ? (currentChat.user != null ? "user=" + currentChat.user.id() : "conversation=" + currentChat.conversation.id()) : "null"));
        System.out.println("[MainChatController] Session display name: " + Session.getDisplayName());
        
        Platform.runLater(() -> {
            // Kiểm tra xem tin nhắn đã có trong UI chưa (tránh duplicate)
            // msg.from() giờ là user ID (String), so sánh với Session.getUserId()
            boolean isOwnMessage = msg.from().equals(String.valueOf(Session.getUserId()));
            System.out.println("[MainChatController] Is own message: " + isOwnMessage);
            
            // Nếu là tin nhắn của mình, bỏ qua (đã hiển thị qua optimistic update)
            if (isOwnMessage) {
                System.out.println("[MainChatController] Ignoring own message (already displayed via optimistic update)");
                return;
            }
            
            // Kiểm tra room ID
            String msgRoom = msg.room();
            
            // Nếu currentRoomId null, thử tìm conversation từ currentChat
            if (currentRoomId == null && currentChat != null) {
                System.out.println("[MainChatController] currentRoomId is null, trying to get conversation from currentChat");
                try {
                    if (currentChat.user != null) {
                        Long userId = Session.getUserId();
                        Conversation conv = clientService.getOrCreateDirectConversation(userId, currentChat.user.id());
                        currentRoomId = conv.id().toString();
                        System.out.println("[MainChatController] Got conversation ID from currentChat: " + currentRoomId);
                    } else if (currentChat.conversation != null) {
                        currentRoomId = currentChat.conversation.id().toString();
                        System.out.println("[MainChatController] Got conversation ID from currentChat.conversation: " + currentRoomId);
                    }
                } catch (Exception e) {
                    System.err.println("[MainChatController] Error getting conversation: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            // So sánh room ID
            boolean roomMatches = false;
            if (currentRoomId != null) {
                roomMatches = currentRoomId.equals(msgRoom);
                System.out.println("[MainChatController] Room match: " + roomMatches + " (current=" + currentRoomId + ", message=" + msgRoom + ")");
            } else {
                System.out.println("[MainChatController] WARNING: currentRoomId is still null after trying to get from currentChat");
            }
            
            if (roomMatches) {
                // Thêm tin nhắn vào UI
                System.out.println("[MainChatController] Adding message to UI");
                addMessageToUI(msg);
                scrollToBottom();
                
                // Phát âm thanh thông báo (vì đang trong cuộc trò chuyện này)
                playNotificationSound(msgRoom);
            } else {
                // Tin nhắn từ cuộc trò chuyện khác
                // Phát âm thanh và highlight contact
                System.out.println("[MainChatController] Message from different room, playing notification and highlighting");
                playNotificationSound(msgRoom);
                highlightContactItem(msgRoom);
            }
        });
    }
    
    private void addMessageToUI(ChatMessage msg) {
        if (msg == null) {
            return;
        }
        
        // Kiểm tra nếu là system message (thông báo thêm thành viên)
        if (msg.type() == org.example.demo2.model.MessageType.SYSTEM) {
            // Hiển thị system message ở giữa, màu xám nhạt
            HBox systemBox = new HBox();
            systemBox.setAlignment(Pos.CENTER);
            systemBox.setPadding(new Insets(12, 16, 12, 16));
            
            Label systemLabel = new Label(msg.text());
            systemLabel.getStyleClass().add("system-message");
            systemBox.getChildren().add(systemLabel);
            
            messagesContainer.getChildren().add(systemBox);
            return;
        }
        
        // Kiểm tra nếu là FILE hoặc IMAGE message
        if (msg.type() == org.example.demo2.model.MessageType.FILE || 
            msg.type() == org.example.demo2.model.MessageType.IMAGE) {
            System.out.println("[MainChatController] FILE/IMAGE message detected: type=" + msg.type() + ", payloadRef=" + msg.payloadRef() + ", text=" + msg.text());
            if (msg.payloadRef() != null) {
                boolean isOwnMessage = msg.from().equals(String.valueOf(Session.getUserId()));
                addFileMessageToUI(msg, isOwnMessage);
                return;
            } else {
                System.out.println("[MainChatController] WARNING: FILE/IMAGE message has null payloadRef!");
            }
        }
        
        // Text message - cần có text
        if (msg.text() == null) {
            return;
        }
        
        HBox messageBox = new HBox(12);
        messageBox.setPadding(new Insets(6, 16, 6, 16));
        
        // Kiểm tra xem có phải tin nhắn của mình không
        // msg.from() giờ là user ID (String), so sánh với Session.getUserId()
        boolean isOwnMessage = msg.from().equals(String.valueOf(Session.getUserId()));
        
        if (isOwnMessage) {
            // Tin nhắn của mình - căn phải
            messageBox.setAlignment(Pos.CENTER_RIGHT);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            messageBox.getChildren().add(spacer);
        } else {
            // Tin nhắn của người khác - căn trái
            messageBox.setAlignment(Pos.CENTER_LEFT);
        }
        
        // Avatar
        Circle avatar = new Circle(18);
        if (isOwnMessage) {
            applyAvatar(avatar, Session.getAvatarPath(), fallbackColorForUser(Session.getUserId()));
        } else {
            User sender = null;
            try {
                sender = getCachedUser(Long.parseLong(msg.from()));
            } catch (NumberFormatException ignored) {
            }
            if (sender == null && currentChat != null && currentChat.user != null
                    && msg.from().equals(String.valueOf(currentChat.user.id()))) {
                sender = currentChat.user;
            }
            applyAvatar(avatar, sender != null ? sender.avatarPath() : null,
                    fallbackColorForUser(sender != null ? sender.id() : null));
        }
        
        // Message content
        VBox contentBox = new VBox(4);
        contentBox.setMaxWidth(400);
        
        // Name label (chỉ hiển thị nếu không phải tin nhắn của mình)
        if (!isOwnMessage) {
            // msg.from() giờ luôn là user ID (String), lấy display name từ service
            String senderDisplayName = msg.from(); // Fallback: dùng ID nếu không lấy được display name
            try {
                Long userId = Long.parseLong(msg.from());
                senderDisplayName = clientService.getUserDisplayName(userId);
            } catch (NumberFormatException e) {
                // Không phải số, có thể là "System" hoặc display name cũ, dùng trực tiếp
            } catch (RemoteException e) {
                // Lỗi khi lấy display name, dùng ID làm fallback
                System.err.println("Could not get display name for user ID: " + msg.from() + ", using ID instead. Error: " + e.getMessage());
            }
            
            Label nameLabel = new Label(senderDisplayName);
            nameLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px; -fx-font-weight: 600; -fx-padding: 0 0 2px 12px;");
            contentBox.getChildren().add(nameLabel);
        }
        
        // Message bubble
        VBox bubble = new VBox(4);
        bubble.setMaxWidth(380);
        if (isOwnMessage) {
            // Tin nhắn của mình - màu xanh tím đậm
            bubble.getStyleClass().add("message-bubble-own");
        } else {
            // Tin nhắn của người khác - màu slate đậm
            bubble.getStyleClass().add("message-bubble-other");
        }
        
        Label textLabel = new Label(msg.text());
        textLabel.getStyleClass().add(isOwnMessage ? "message-text-own" : "message-text-other");
        textLabel.setWrapText(true);
        bubble.getChildren().add(textLabel);
        
        // Timestamp (optional - bạn có thể thêm nếu muốn)
        // Label timeLabel = new Label(formatTime(msg.timestamp()));
        // timeLabel.setStyle("-fx-text-fill: " + (isOwnMessage ? "rgba(255,255,255,0.7)" : "#64748b") + "; -fx-font-size: 10px;");
        // bubble.getChildren().add(timeLabel);
        
        contentBox.getChildren().add(bubble);
        
        if (isOwnMessage) {
            // Tin nhắn của mình: content trước, avatar sau
            messageBox.getChildren().addAll(contentBox, avatar);
        } else {
            // Tin nhắn của người khác: avatar trước, content sau
            messageBox.getChildren().addAll(avatar, contentBox);
        }
        
        // Lưu timestamp vào userData để có thể scroll đến khi search
        messageBox.setUserData(msg.ts());
        
        messagesContainer.getChildren().add(messageBox);
    }
    
    private void scrollToBottom() {
        Platform.runLater(() -> {
            scrollMessages.setVvalue(1.0);
        });
    }
    
    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Lỗi");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
    
    private void showInfo(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Thông báo");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
    
    @FXML
    private void handleCall() {
        if (currentChat == null) {
            showInfo("Chưa chọn cuộc trò chuyện");
            return;
        }
        showInfo("Tính năng gọi thoại sẽ được triển khai trong bài UDP/Video Call");
    }
    
    @FXML
    private void handleLogout() {
        // Xác nhận đăng xuất
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Đăng xuất");
        confirmAlert.setHeaderText("Bạn có chắc chắn muốn đăng xuất?");
        confirmAlert.setContentText("Bạn sẽ cần đăng nhập lại để tiếp tục sử dụng.");
        
        confirmAlert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    // Thông báo server hủy session để trạng thái online cập nhật ngay
                    if (clientService != null) {
                        try {
                            clientService.logout(Session.getUserId());
                        } catch (Exception e) {
                            System.err.println("[MainChatController] logout notify failed: " + e.getMessage());
                        }
                    }

                    // Đóng kết nối chat
                    if (chatClient != null) {
                        chatClient.close();
                    }
                    
                    // Xóa session
                    Session.clear();
                    userCache.clear();
                    
                    // Quay về màn hình login
                    javafx.stage.Stage currentStage = (javafx.stage.Stage) lblCurrentUser.getScene().getWindow();
                    
                    java.net.URL fxml = getClass().getResource("/org/example/demo2/ui/view/LoginView.fxml");
                    javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(fxml);
                    javafx.scene.Scene scene = new javafx.scene.Scene(loader.load());
                    
                    java.net.URL css = getClass().getResource("/org/example/demo2/ui/css/theme.css");
                    if (css != null) {
                        scene.getStylesheets().add(css.toExternalForm());
                    }
                    
                    currentStage.setTitle("AegisTalk – Login");
                    currentStage.setScene(scene);
                    currentStage.show();
                } catch (Exception e) {
                    showError("Lỗi đăng xuất: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
    }
    
    @FXML
    private void handleVideoCall() {
        if (currentChat == null || currentChat.user == null) {
            showInfo("Chỉ có thể gọi video với bạn bè (1-1)");
            return;
        }
        
        // Chỉ hỗ trợ video call 1-1, không hỗ trợ nhóm
        if (currentChat.conversation != null && "GROUP".equals(currentChat.conversation.type())) {
            showInfo("Video call nhóm chưa được hỗ trợ");
            return;
        }
        
        try {
            openVideoCallWindow(currentChat.user.id(), currentChat.user.displayName(), true, null);
        } catch (Exception e) {
            showError("Lỗi mở video call: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void openVideoCallWindow(Long otherUserId, String otherUserName, boolean isCaller, Integer existingSessionId) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader();
            loader.setLocation(getClass().getResource("/org/example/demo2/ui/view/CallView.fxml"));
            javafx.scene.Parent root = loader.load();
            
            VideoCallController controller = loader.getController();
            controller.setClientService(clientService);
            
            javafx.stage.Stage callStage = new javafx.stage.Stage();
            callStage.setTitle("Video Call - " + otherUserName);
            callStage.setScene(new javafx.scene.Scene(root, 800, 600));
            callStage.setOnCloseRequest(e -> {
                // Kết thúc cuộc gọi khi đóng window
                controller.handleLeaveCall();
            });
            
            // Set stage vào controller để có thể đóng cửa sổ
            controller.setCallStage(callStage);
            
            if (isCaller) {
                controller.startCall(otherUserId, otherUserName);
            } else if (existingSessionId != null) {
                // Nhận cuộc gọi và tự động accept (vì đã được chấp nhận từ dialog)
                controller.receiveCall(existingSessionId, otherUserId, otherUserName, true);
            }
            
            callStage.show();
        } catch (Exception e) {
            showError("Lỗi mở video call: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Kiểm tra và xử lý incoming calls.
     */
    private synchronized void checkIncomingCalls() {
        try {
            Long userId = Session.getUserId();
            List<CallService.CallInfo> pendingCalls = clientService.getPendingCalls(userId);
            
            // Xóa các call đã không còn PENDING khỏi shownCallDialogs
            Set<Integer> toRemove = new HashSet<>();
            for (Integer sessionId : shownCallDialogs) {
                try {
                    CallService.CallInfo callInfo = clientService.getCallInfo(sessionId);
                    if (callInfo == null || !"PENDING".equals(callInfo.status)) {
                        toRemove.add(sessionId);
                    }
                } catch (Exception e) {
                    // Call không tồn tại nữa, xóa khỏi set
                    toRemove.add(sessionId);
                }
            }
            shownCallDialogs.removeAll(toRemove);
            activeCallDialogs.removeAll(toRemove);
            
            for (CallService.CallInfo callInfo : pendingCalls) {
                // Chỉ hiển thị dialog cho các call chưa được hiển thị, vẫn còn PENDING, và chưa có dialog đang mở
                if (!shownCallDialogs.contains(callInfo.sessionId) 
                    && !activeCallDialogs.contains(callInfo.sessionId)
                    && "PENDING".equals(callInfo.status)) {
                    shownCallDialogs.add(callInfo.sessionId);
                    activeCallDialogs.add(callInfo.sessionId);
                    final CallService.CallInfo finalCallInfo = callInfo;
                    Platform.runLater(() -> {
                        showIncomingCallDialog(finalCallInfo);
                    });
                }
            }
        } catch (Exception e) {
            // Ignore errors - có thể log nếu cần debug
            // System.err.println("Error checking incoming calls: " + e.getMessage());
        }
    }
    
    private void showIncomingCallDialog(CallService.CallInfo callInfo) {
        try {
            // Kiểm tra lại call status trước khi hiển thị dialog
            CallService.CallInfo currentCallInfo = clientService.getCallInfo(callInfo.sessionId);
            if (currentCallInfo == null || !"PENDING".equals(currentCallInfo.status)) {
                // Call đã bị hủy, xóa khỏi tracking
                shownCallDialogs.remove(callInfo.sessionId);
                activeCallDialogs.remove(callInfo.sessionId);
                return;
            }
            
            // Lấy thông tin người gọi
            Long callerId = callInfo.callerId;
            String callerName = clientService.getUserDisplayName(callerId);
            
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.NONE);
            alert.setTitle("Cuộc gọi đến");
            alert.setHeaderText(callerName + " đang gọi video...");
            alert.setContentText("Bạn có muốn chấp nhận cuộc gọi?");
            
            javafx.scene.control.ButtonType acceptButton = new javafx.scene.control.ButtonType("Chấp nhận");
            javafx.scene.control.ButtonType rejectButton = new javafx.scene.control.ButtonType("Từ chối");
            alert.getButtonTypes().setAll(acceptButton, rejectButton);
            
            alert.showAndWait().ifPresent(buttonType -> {
                // Xóa khỏi active dialogs
                activeCallDialogs.remove(callInfo.sessionId);
                
                if (buttonType == acceptButton) {
                    // Xóa khỏi set vì đã xử lý
                    shownCallDialogs.remove(callInfo.sessionId);
                    
                    // Kiểm tra lại call status trước khi accept
                    try {
                        CallService.CallInfo checkCallInfo = clientService.getCallInfo(callInfo.sessionId);
                        if (checkCallInfo == null || !"PENDING".equals(checkCallInfo.status)) {
                            showError("Cuộc gọi không còn hợp lệ");
                            return;
                        }
                        
                        // Mở video call window và tự động accept
                        openVideoCallWindow(callerId, callerName, false, callInfo.sessionId);
                    } catch (RemoteException e) {
                        showError("Lỗi kiểm tra cuộc gọi: " + e.getMessage());
                    }
                } else if (buttonType == rejectButton) {
                    // Xóa khỏi set vì đã xử lý
                    shownCallDialogs.remove(callInfo.sessionId);
                    // Từ chối cuộc gọi
                    try {
                        clientService.rejectCall(callInfo.sessionId, Session.getUserId());
                    } catch (RemoteException e) {
                        System.err.println("Error rejecting call: " + e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            System.err.println("Error showing incoming call dialog: " + e.getMessage());
            // Xóa khỏi tracking nếu có lỗi
            shownCallDialogs.remove(callInfo.sessionId);
            activeCallDialogs.remove(callInfo.sessionId);
        }
    }
    
    @FXML
    private void toggleInfoPanel() {
        if (infoPanel != null) {
            boolean visible = infoPanel.isVisible();
            infoPanel.setVisible(!visible);
            infoPanel.setManaged(!visible);
            
            // Cập nhật info panel khi mở
            if (visible && currentChat != null) {
                updateInfoPanel();
            }
        }
    }
    
    private void updateInfoPanel() {
        if (currentChat == null || infoPanel == null) {
            return;
        }
        
        if (currentChat.conversation != null && "GROUP".equals(currentChat.conversation.type())) {
            // Đang chat nhóm
            if (lblInfoName != null) {
                lblInfoName.setText(currentChat.conversation.title() != null ? currentChat.conversation.title() : "Nhóm");
            }
            if (lblInfoStatus != null) {
                lblInfoStatus.setText("Nhóm chat");
            }
            applyAvatar(infoAvatar, null, fallbackColorForUser(currentChat.conversation.id()));
            if (btnAddMember != null) {
                btnAddMember.setVisible(true);
                btnAddMember.setManaged(true);
            }
            // Thay đổi nút "Hủy kết bạn" thành "Rời nhóm"
            if (btnUnfriend != null) {
                btnUnfriend.setText("Rời nhóm");
                btnUnfriend.setVisible(true);
                btnUnfriend.setManaged(true);
            }
        } else if (currentChat.user != null) {
            // Đang chat 1-1
            if (lblInfoName != null) {
                lblInfoName.setText(currentChat.user.displayName());
            }
            boolean online = isUserOnline(currentChat.user.id());
            applyStatus(lblInfoStatus, online ? "Đang hoạt động" : "Không hoạt động",
                    online ? ONLINE_COLOR : OFFLINE_COLOR);
            applyAvatar(infoAvatar, currentChat.user.avatarPath(), fallbackColorForUser(currentChat.user.id()));
            setStatusIndicator(online);
            if (btnAddMember != null) {
                btnAddMember.setVisible(false);
                btnAddMember.setManaged(false);
            }
            // Hiển thị nút "Hủy kết bạn" cho chat 1-1
            if (btnUnfriend != null) {
                btnUnfriend.setText("❌ Huỷ kết bạn");
                btnUnfriend.setVisible(true);
                btnUnfriend.setManaged(true);
            }
        }
    }
    
    @FXML
    private void handleAddMember() {
        if (currentChat == null || currentChat.conversation == null || !"GROUP".equals(currentChat.conversation.type())) {
            showError("Chỉ có thể mời thêm người vào nhóm");
            return;
        }
        
        try {
            Long userId = Session.getUserId();
            Long groupId = currentChat.conversation.id();
            
            // Lấy danh sách bạn bè
            List<User> friends = clientService.getFriends(userId);
            
            // Lấy danh sách thành viên hiện tại trong nhóm
            List<User> currentMembers = clientService.getGroupMembers(groupId);
            
            // Lọc ra những bạn bè chưa trong nhóm
            List<User> availableFriends = friends.stream()
                    .filter(friend -> currentMembers.stream()
                            .noneMatch(member -> member.id().equals(friend.id())))
                    .collect(Collectors.toList());
            
            if (availableFriends.isEmpty()) {
                showInfo("Tất cả bạn bè đã có trong nhóm");
                return;
            }
            
            // Tạo dialog
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Mời thêm người vào nhóm");
            dialog.setHeaderText("Chọn bạn bè để mời vào nhóm");
            
            // Form
            VBox form = new VBox(12);
            form.setPadding(new Insets(20));
            form.setPrefWidth(400);
            
            // Danh sách bạn bè để chọn
            Label lblMembers = new Label("Chọn thành viên:");
            ListView<User> lstMembers = new ListView<>();
            lstMembers.setItems(FXCollections.observableArrayList(availableFriends));
            lstMembers.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            lstMembers.setPrefHeight(300);
            
            // Cell factory để hiển thị tên
            lstMembers.setCellFactory(listView -> new ListCell<User>() {
                @Override
                protected void updateItem(User user, boolean empty) {
                    super.updateItem(user, empty);
                    if (empty || user == null) {
                        setText(null);
                    } else {
                        setText(user.displayName());
                    }
                }
            });
            
            form.getChildren().addAll(lblMembers, lstMembers);
            
            dialog.getDialogPane().setContent(form);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            
            // Validation
            Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
            okButton.setDisable(true);
            lstMembers.getSelectionModel().getSelectedItems().addListener((javafx.collections.ListChangeListener.Change<? extends User> c) -> {
                okButton.setDisable(lstMembers.getSelectionModel().getSelectedItems().isEmpty());
            });
            
            // Xử lý kết quả
            dialog.showAndWait().ifPresent(buttonType -> {
                if (buttonType == ButtonType.OK) {
                    ObservableList<User> selectedMembers = lstMembers.getSelectionModel().getSelectedItems();
                    if (selectedMembers.isEmpty()) {
                        showError("Vui lòng chọn ít nhất một người");
                        return;
                    }
                    
                    addMembersToGroup(groupId, selectedMembers);
                }
            });
        } catch (Exception e) {
            showError("Lỗi mời thêm người: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void addMembersToGroup(Long groupId, ObservableList<User> members) {
        try {
            Long userId = Session.getUserId();
            String inviterName = Session.getDisplayName();
            
            // Thêm từng thành viên
            for (User member : members) {
                boolean success = clientService.addMember(groupId, member.id(), userId);
                if (success) {
                    // Gửi system message thông báo thành viên mới
                    String systemMsg = inviterName + " đã thêm " + member.displayName() + " vào nhóm";
                    ChatMessage systemMessage = new ChatMessage(
                        groupId.toString(),
                        "System",
                        org.example.demo2.model.MessageType.SYSTEM,
                        systemMsg,
                        null,
                        System.currentTimeMillis()
                    );
                    
                    // Lưu vào database
                    clientService.saveMessage(systemMessage);
                    
                    // Broadcast qua TCP để tất cả thành viên trong nhóm nhận được
                    try {
                        if (chatClient != null) {
                            chatClient.send(systemMessage);
                        }
                    } catch (Exception e) {
                        System.err.println("Failed to broadcast system message: " + e.getMessage());
                    }
                }
            }
            
            showInfo("Đã thêm " + members.size() + " người vào nhóm!");
            
            // Refresh danh sách nhóm
            loadGroups();
            
            // Refresh message history để hiển thị system messages
            if (currentRoomId != null) {
                loadMessageHistory(currentRoomId);
            }
        } catch (RemoteException e) {
            showError("Lỗi thêm thành viên: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @FXML
    private void handleCreateGroup() {
        try {
            // Lấy danh sách bạn bè
            Long userId = Session.getUserId();
            List<User> friends = clientService.getFriends(userId);
            
            if (friends.isEmpty()) {
                showInfo("Bạn cần có ít nhất một bạn bè để tạo nhóm");
                return;
            }
            
            // Tạo dialog với dark theme
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Tạo nhóm mới");
            
            // Style dialog pane
            DialogPane dialogPane = dialog.getDialogPane();
            dialogPane.getStyleClass().add("group-dialog");
            
            // Form container
            VBox form = new VBox(16);
            form.setPadding(new Insets(24));
            form.setPrefWidth(420);
            form.getStyleClass().add("group-form");
            
            // Header
            Label headerLabel = new Label("👥 Tạo nhóm chat mới");
            headerLabel.getStyleClass().add("group-header-title");
            
            Label subHeaderLabel = new Label("Đặt tên và chọn thành viên cho nhóm");
            subHeaderLabel.getStyleClass().add("group-header-subtitle");
            
            VBox headerBox = new VBox(4);
            headerBox.getChildren().addAll(headerLabel, subHeaderLabel);
            
            // Tên nhóm
            Label lblGroupName = new Label("Tên nhóm");
            lblGroupName.getStyleClass().add("group-label");
            
            TextField txtGroupName = new TextField();
            txtGroupName.setPromptText("Nhập tên nhóm...");
            txtGroupName.getStyleClass().add("group-text-field");
            txtGroupName.setPrefHeight(44);
            
            VBox groupNameBox = new VBox(8);
            groupNameBox.getChildren().addAll(lblGroupName, txtGroupName);
            
            // Danh sách bạn bè để chọn
            Label lblMembers = new Label("Chọn thành viên");
            lblMembers.getStyleClass().add("group-label");
            
            ListView<User> lstMembers = new ListView<>();
            lstMembers.setItems(FXCollections.observableArrayList(friends));
            lstMembers.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            lstMembers.setPrefHeight(220);
            lstMembers.getStyleClass().add("group-list-view");
            
            // Cell factory để hiển thị tên đẹp hơn
            lstMembers.setCellFactory(listView -> new ListCell<User>() {
                @Override
                protected void updateItem(User user, boolean empty) {
                    super.updateItem(user, empty);
                    if (empty || user == null) {
                        setText(null);
                        setGraphic(null);
                        getStyleClass().setAll("group-list-cell");
                    } else {
                        HBox hbox = new HBox(12);
                        hbox.setAlignment(Pos.CENTER_LEFT);
                        hbox.setPadding(new Insets(8, 12, 8, 12));
                        
                        // Avatar
                        Circle avatar = new Circle(16);
                    applyAvatar(avatar, user);
                        
                        // Name
                        Label nameLabel = new Label(user.displayName());
                        nameLabel.getStyleClass().add("group-member-name");
                        
                        // Check icon when selected
                        Label checkIcon = new Label(isSelected() ? "✓" : "");
                        checkIcon.getStyleClass().add("group-check-icon");
                        
                        Region spacer = new Region();
                        HBox.setHgrow(spacer, Priority.ALWAYS);
                        
                        hbox.getChildren().addAll(avatar, nameLabel, spacer, checkIcon);
                        setGraphic(hbox);
                        setText(null);
                        
                        // Style based on selection
                        getStyleClass().setAll("group-list-cell");
                        if (isSelected()) {
                            getStyleClass().add("selected");
                        }
                    }
                }
            });
            
            VBox membersBox = new VBox(8);
            membersBox.getChildren().addAll(lblMembers, lstMembers);
            
            // Hint
            Label hintLabel = new Label("💡 Giữ Ctrl để chọn nhiều người");
            hintLabel.getStyleClass().add("group-hint");
            
            form.getChildren().addAll(headerBox, groupNameBox, membersBox, hintLabel);
            
            dialogPane.setContent(form);
            
            // Buttons với style
            ButtonType createButton = new ButtonType("Tạo nhóm", ButtonBar.ButtonData.OK_DONE);
            ButtonType cancelButton = new ButtonType("Hủy", ButtonBar.ButtonData.CANCEL_CLOSE);
            dialogPane.getButtonTypes().addAll(createButton, cancelButton);
            
            // Style buttons
            Button createBtn = (Button) dialogPane.lookupButton(createButton);
            createBtn.getStyleClass().add("group-create-button");
            createBtn.setDisable(true);
            
            Button cancelBtn = (Button) dialogPane.lookupButton(cancelButton);
            cancelBtn.getStyleClass().add("group-cancel-button");
            
            // Validation
            txtGroupName.textProperty().addListener((obs, oldVal, newVal) -> {
                createBtn.setDisable(newVal == null || newVal.trim().isEmpty());
            });
            
            // Xử lý kết quả
            dialog.showAndWait().ifPresent(buttonType -> {
                if (buttonType == createButton) {
                    String groupName = txtGroupName.getText().trim();
                    if (groupName.isEmpty()) {
                        showError("Vui lòng nhập tên nhóm");
                        return;
                    }
                    
                    ObservableList<User> selectedMembers = lstMembers.getSelectionModel().getSelectedItems();
                    if (selectedMembers.isEmpty()) {
                        showError("Vui lòng chọn ít nhất một thành viên");
                        return;
                    }
                    
                    createGroup(groupName, selectedMembers);
                }
            });
        } catch (Exception e) {
            showError("Lỗi tạo nhóm: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void createGroup(String groupName, ObservableList<User> members) {
        try {
            Long userId = Session.getUserId();
            
            // Tạo nhóm
            Conversation group = clientService.createGroup(groupName, userId);
            if (group == null) {
                showError("Không thể tạo nhóm");
                return;
            }
            
            // Thêm các thành viên đã chọn
            for (User member : members) {
                boolean success = clientService.addMember(group.id(), member.id(), userId);
                if (success) {
                    // Gửi system message thông báo thành viên mới
                    String systemMsg = Session.getDisplayName() + " đã thêm " + member.displayName() + " vào nhóm";
                    ChatMessage systemMessage = new ChatMessage(
                        group.id().toString(),
                        "System",
                        org.example.demo2.model.MessageType.SYSTEM,
                        systemMsg,
                        null,
                        System.currentTimeMillis()
                    );
                    
                    // Lưu vào database
                    clientService.saveMessage(systemMessage);
                    
                    // Broadcast qua TCP để tất cả thành viên trong nhóm nhận được
                    try {
                        chatClient.send(systemMessage);
                    } catch (Exception e) {
                        System.err.println("Failed to broadcast system message: " + e.getMessage());
                    }
                }
            }
            
            showInfo("Đã tạo nhóm " + groupName + " thành công!");
            
            // Refresh danh sách nhóm
            loadGroups();
            
            // Chuyển sang tab Nhóm và mở nhóm vừa tạo
            tabGroups.setSelected(true);
            showGroupsTab();
            
            // Tìm và mở nhóm vừa tạo
            for (ContactItem item : groupsList) {
                if (item.conversation != null && item.conversation.id().equals(group.id())) {
                    lstContacts.getSelectionModel().select(item);
                    openChat(item);
                    break;
                }
            }
        } catch (RemoteException e) {
            showError("Lỗi tạo nhóm: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Handle attach file button click.
     * Mở file chooser để chọn file và upload lên server.
     */
    @FXML
    private void handleAttachFile() {
        if (currentChat == null || currentRoomId == null) {
            showError("Vui lòng chọn cuộc trò chuyện trước");
            return;
        }
        
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Chọn file đính kèm");
        fileChooser.getExtensionFilters().addAll(
            new javafx.stage.FileChooser.ExtensionFilter("Tất cả file", "*.*"),
            new javafx.stage.FileChooser.ExtensionFilter("Documents", "*.pdf", "*.doc", "*.docx", "*.xls", "*.xlsx", "*.ppt", "*.pptx", "*.txt"),
            new javafx.stage.FileChooser.ExtensionFilter("Archives", "*.zip", "*.rar", "*.7z")
        );
        
        java.io.File file = fileChooser.showOpenDialog(txtMessage.getScene().getWindow());
        if (file != null) {
            uploadAndSendFile(file, org.example.demo2.model.MessageType.FILE);
        }
    }
    
    /**
     * Handle attach image button click.
     * Mở file chooser để chọn hình ảnh và upload lên server.
     */
    @FXML
    private void handleAttachImage() {
        if (currentChat == null || currentRoomId == null) {
            showError("Vui lòng chọn cuộc trò chuyện trước");
            return;
        }
        
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Chọn hình ảnh");
        fileChooser.getExtensionFilters().addAll(
            new javafx.stage.FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp", "*.bmp")
        );
        
        java.io.File file = fileChooser.showOpenDialog(txtMessage.getScene().getWindow());
        if (file != null) {
            uploadAndSendFile(file, org.example.demo2.model.MessageType.IMAGE);
        }
    }
    
    /**
     * Upload file và gửi tin nhắn chứa file.
     */
    private void uploadAndSendFile(java.io.File file, org.example.demo2.model.MessageType type) {
        // Hiển thị progress dialog
        javafx.stage.Stage progressStage = new javafx.stage.Stage();
        progressStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        progressStage.setTitle("Đang tải lên");
        progressStage.setResizable(false);
        
        javafx.scene.control.ProgressBar progressBar = new javafx.scene.control.ProgressBar(0);
        progressBar.setPrefWidth(300);
        
        javafx.scene.control.Label label = new javafx.scene.control.Label("Đang tải file: " + file.getName());
        label.setStyle("-fx-text-fill: #e2e8f0; -fx-font-size: 14px;");
        
        VBox vbox = new VBox(15, label, progressBar);
        vbox.setAlignment(Pos.CENTER);
        vbox.setPadding(new Insets(20));
        vbox.setStyle("-fx-background-color: #1e293b;");
        
        javafx.scene.Scene scene = new javafx.scene.Scene(vbox, 350, 100);
        progressStage.setScene(scene);
        progressStage.show();
        
        // Upload file trong background thread
        Long uploaderId = Session.getUserId();
        new Thread(() -> {
            try {
                org.example.demo2.service.FileTransferService.FileUploadResult result = 
                    fileTransferService.uploadFile(file, uploaderId, progress -> {
                        Platform.runLater(() -> progressBar.setProgress(progress));
                    });
                
                Platform.runLater(() -> {
                    progressStage.close();
                    
                    // Tạo message với file info
                    // payloadRef chứa: fileId|filename|size|contentType
                    String payloadRef = result.getId() + "|" + result.getFilename() + "|" + 
                                       result.getSize() + "|" + result.getContentType();
                    
                    Long userId = Session.getUserId();
                    ChatMessage msg = new ChatMessage(
                        currentRoomId,
                        userId.toString(),
                        type,
                        result.getFilename(), // text chứa filename
                        payloadRef,
                        System.currentTimeMillis()
                    );
                    
                    // Hiển thị tin nhắn ngay lập tức (optimistic update)
                    addFileMessageToUI(msg, true);
                    scrollToBottom();
                    
                    // Gửi qua TCP
                    try {
                        chatClient.send(msg);
                        // Lưu vào database
                        clientService.saveMessage(msg);
                        System.out.println("[MainChatController] File message sent: " + result.getFilename());
                    } catch (Exception e) {
                        showError("Lỗi gửi file: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
                
            } catch (Exception e) {
                Platform.runLater(() -> {
                    progressStage.close();
                    showError("Lỗi tải file: " + e.getMessage());
                    e.printStackTrace();
                });
            }
        }).start();
    }
    
    /**
     * Thêm file message vào UI.
     */
    private void addFileMessageToUI(ChatMessage msg, boolean isOwnMessage) {
        if (msg == null || msg.payloadRef() == null) {
            System.out.println("[MainChatController] addFileMessageToUI: msg=" + msg + ", payloadRef=" + (msg != null ? msg.payloadRef() : "null"));
            return;
        }
        
        System.out.println("[MainChatController] addFileMessageToUI: payloadRef=" + msg.payloadRef());
        
        // Parse payloadRef: fileId|filename|size|contentType
        String[] parts = msg.payloadRef().split("\\|");
        if (parts.length < 4) {
            System.out.println("[MainChatController] addFileMessageToUI: invalid parts length=" + parts.length);
            return;
        }
        
        long fileId = Long.parseLong(parts[0]);
        String filename = parts[1];
        long size = Long.parseLong(parts[2]);
        String contentType = parts[3];
        
        HBox messageBox = new HBox(12);
        messageBox.setPadding(new Insets(6, 16, 6, 16));
        
        if (isOwnMessage) {
            messageBox.setAlignment(Pos.CENTER_RIGHT);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            messageBox.getChildren().add(spacer);
        } else {
            messageBox.setAlignment(Pos.CENTER_LEFT);
        }
        
        // Avatar
        Circle avatar = new Circle(18);
        if (isOwnMessage) {
            applyAvatar(avatar, Session.getAvatarPath(), fallbackColorForUser(Session.getUserId()));
        } else {
            User sender = null;
            try {
                sender = getCachedUser(Long.parseLong(msg.from()));
            } catch (NumberFormatException ignored) {
            }
            applyAvatar(avatar, sender != null ? sender.avatarPath() : null,
                    fallbackColorForUser(sender != null ? sender.id() : null));
        }
        
        // Content box
        VBox contentBox = new VBox(4);
        contentBox.setMaxWidth(400);
        
        // Sender name (nếu không phải tin nhắn của mình)
        if (!isOwnMessage) {
            String senderName = msg.from();
            try {
                Long userId = Long.parseLong(msg.from());
                senderName = clientService.getUserDisplayName(userId);
            } catch (Exception ignored) {}
            
            Label nameLabel = new Label(senderName);
            nameLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px; -fx-font-weight: 600; -fx-padding: 0 0 2px 12px;");
            contentBox.getChildren().add(nameLabel);
        }
        
        // File bubble
        VBox bubble = new VBox(8);
        bubble.setMaxWidth(320);
        bubble.setStyle(isOwnMessage ? 
            "-fx-background-color: #3b82f6; -fx-background-radius: 18 18 4 18; -fx-padding: 12 16;" :
            "-fx-background-color: #374151; -fx-background-radius: 18 18 18 4; -fx-padding: 12 16;");
        
        // Kiểm tra nếu là hình ảnh
        if (msg.type() == org.example.demo2.model.MessageType.IMAGE && contentType.startsWith("image/")) {
            // Hiển thị hình ảnh preview
            try {
                String imageUrl = "http://" + org.example.demo2.config.ServerConfig.SERVER_HOST + ":" + 
                                 org.example.demo2.config.ServerConfig.FILE_SERVER_PORT + "/files/" + fileId;
                javafx.scene.image.Image image = new javafx.scene.image.Image(imageUrl, 280, 280, true, true, true);
                javafx.scene.image.ImageView imageView = new javafx.scene.image.ImageView(image);
                imageView.setFitWidth(280);
                imageView.setPreserveRatio(true);
                imageView.setStyle("-fx-cursor: hand;");
                
                // Click để xem full size hoặc download
                imageView.setOnMouseClicked(e -> downloadFile(fileId, filename));
                
                bubble.getChildren().add(imageView);
            } catch (Exception e) {
                // Fallback to file icon
                addFileIconAndInfo(bubble, filename, size, fileId, isOwnMessage);
            }
        } else {
            // Hiển thị file icon và thông tin
            addFileIconAndInfo(bubble, filename, size, fileId, isOwnMessage);
        }
        
        contentBox.getChildren().add(bubble);
        
        if (isOwnMessage) {
            messageBox.getChildren().addAll(contentBox, avatar);
        } else {
            messageBox.getChildren().addAll(avatar, contentBox);
        }
        
        messagesContainer.getChildren().add(messageBox);
    }
    
    /**
     * Thêm file icon và thông tin vào bubble.
     */
    private void addFileIconAndInfo(VBox bubble, String filename, long size, long fileId, boolean isOwnMessage) {
        HBox fileBox = new HBox(12);
        fileBox.setAlignment(Pos.CENTER_LEFT);
        fileBox.setStyle("-fx-cursor: hand;");
        
        // File icon based on extension
        String icon = getFileIcon(filename);
        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: 28px;");
        
        // File info
        VBox infoBox = new VBox(2);
        
        Label filenameLabel = new Label(filename);
        filenameLabel.setStyle("-fx-text-fill: " + (isOwnMessage ? "#ffffff" : "#e2e8f0") + "; -fx-font-size: 14px; -fx-font-weight: bold;");
        filenameLabel.setWrapText(true);
        filenameLabel.setMaxWidth(220);
        
        Label sizeLabel = new Label(formatFileSize(size));
        sizeLabel.setStyle("-fx-text-fill: " + (isOwnMessage ? "rgba(255,255,255,0.7)" : "#94a3b8") + "; -fx-font-size: 12px;");
        
        infoBox.getChildren().addAll(filenameLabel, sizeLabel);
        fileBox.getChildren().addAll(iconLabel, infoBox);
        
        // Click để download
        fileBox.setOnMouseClicked(e -> downloadFile(fileId, filename));
        
        // Download button
        Button downloadBtn = new Button("⬇ Tải xuống");
        downloadBtn.setStyle("-fx-background-color: " + (isOwnMessage ? "rgba(255,255,255,0.2)" : "rgba(99,102,241,0.3)") + "; " +
            "-fx-text-fill: " + (isOwnMessage ? "#ffffff" : "#a5b4fc") + "; " +
            "-fx-font-size: 12px; -fx-padding: 6 12; -fx-background-radius: 12; -fx-cursor: hand;");
        downloadBtn.setOnAction(e -> downloadFile(fileId, filename));
        
        bubble.getChildren().addAll(fileBox, downloadBtn);
    }
    
    /**
     * Download file từ server.
     */
    private void downloadFile(long fileId, String filename) {
        javafx.stage.DirectoryChooser dirChooser = new javafx.stage.DirectoryChooser();
        dirChooser.setTitle("Chọn thư mục lưu file");
        dirChooser.setInitialDirectory(new java.io.File(System.getProperty("user.home") + "/Downloads"));
        
        java.io.File selectedDir = dirChooser.showDialog(txtMessage.getScene().getWindow());
        if (selectedDir == null) return;
        
        // Show progress dialog
        javafx.stage.Stage progressStage = new javafx.stage.Stage();
        progressStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        progressStage.setTitle("Đang tải xuống");
        progressStage.setResizable(false);
        
        javafx.scene.control.ProgressBar progressBar = new javafx.scene.control.ProgressBar(0);
        progressBar.setPrefWidth(300);
        
        javafx.scene.control.Label label = new javafx.scene.control.Label("Đang tải: " + filename);
        label.setStyle("-fx-text-fill: #e2e8f0; -fx-font-size: 14px;");
        
        VBox vbox = new VBox(15, label, progressBar);
        vbox.setAlignment(Pos.CENTER);
        vbox.setPadding(new Insets(20));
        vbox.setStyle("-fx-background-color: #1e293b;");
        
        javafx.scene.Scene scene = new javafx.scene.Scene(vbox, 350, 100);
        progressStage.setScene(scene);
        progressStage.show();
        
        new Thread(() -> {
            try {
                java.io.File downloadedFile = fileTransferService.downloadFile(
                    fileId, 
                    selectedDir.toPath(),
                    filename, // Truyền filename để lưu đúng tên
                    progress -> Platform.runLater(() -> progressBar.setProgress(progress))
                );
                
                Platform.runLater(() -> {
                    progressStage.close();
                    
                    // Ask if user wants to open the file
                    javafx.scene.control.Alert successAlert = new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.CONFIRMATION);
                    successAlert.setTitle("Tải xuống thành công");
                    successAlert.setHeaderText("Đã tải: " + downloadedFile.getName());
                    successAlert.setContentText("Bạn có muốn mở file không?");
                    
                    successAlert.showAndWait().ifPresent(response -> {
                        if (response == javafx.scene.control.ButtonType.OK) {
                            try {
                                java.awt.Desktop.getDesktop().open(downloadedFile);
                            } catch (Exception e) {
                                showError("Không thể mở file: " + e.getMessage());
                            }
                        }
                    });
                });
                
            } catch (Exception e) {
                Platform.runLater(() -> {
                    progressStage.close();
                    showError("Lỗi tải file: " + e.getMessage());
                });
            }
        }).start();
    }
    
    /**
     * Lấy icon cho file dựa trên extension.
     */
    private String getFileIcon(String filename) {
        String name = filename.toLowerCase();
        if (name.endsWith(".pdf")) return "📕";
        if (name.endsWith(".doc") || name.endsWith(".docx")) return "📘";
        if (name.endsWith(".xls") || name.endsWith(".xlsx")) return "📗";
        if (name.endsWith(".ppt") || name.endsWith(".pptx")) return "📙";
        if (name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z")) return "📦";
        if (name.endsWith(".txt")) return "📄";
        if (name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".flac")) return "🎵";
        if (name.endsWith(".mp4") || name.endsWith(".avi") || name.endsWith(".mkv")) return "🎬";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".gif")) return "🖼️";
        if (name.endsWith(".java") || name.endsWith(".py") || name.endsWith(".js") || name.endsWith(".html") || name.endsWith(".css")) return "💻";
        return "📎";
    }
    
    /**
     * Format file size.
     */
    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024));
        return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
    }

    private void applyAvatar(Circle circle, User user) {
        if (user == null) {
            applyAvatar(circle, null, "#6366f1");
        } else {
            applyAvatar(circle, user.avatarPath(), fallbackColorForUser(user.id()));
        }
    }

    private void applyAvatar(Circle circle, String avatarPath, String fallbackColor) {
        if (circle == null) {
            return;
        }
        // Clear inline style so setFill wins over old -fx-fill
        circle.setStyle("");
        if (avatarPath != null && !avatarPath.isBlank()) {
            try {
                String source;
                if (avatarPath.startsWith("http://") || avatarPath.startsWith("https://")) {
                    // URL - normalize localhost thành SERVER_HOST
                    source = normalizeAvatarUrl(avatarPath);
                    // Chỉ log lần đầu tiên cho mỗi URL
                    if (!loggedAvatarPaths.contains(source)) {
                        loggedAvatarPaths.add(source);
                        System.out.println("[MainChatController] Loading avatar from URL: " + source);
                    }
                } else {
                    // File path - tìm file trong thư mục chung hoặc absolute path
                    File file = getAvatarFile(avatarPath);
                    if (file != null && file.exists()) {
                        source = file.toURI().toString();
                        System.out.println("[MainChatController] Loading avatar from local file: " + file.getAbsolutePath());
                    } else {
                        // File không tồn tại - có thể là absolute path từ máy khác
                        // Fallback về màu và log warning (chỉ 1 lần cho mỗi path)
                        if (!loggedAvatarPaths.contains(avatarPath)) {
                            loggedAvatarPaths.add(avatarPath);
                            System.err.println("[MainChatController] Avatar file not found (may be from another machine): " + avatarPath);
                            System.err.println("[MainChatController] This avatar needs to be migrated to URL. User should upload avatar again via profile dialog.");
                        }
                        circle.setFill(Color.web(fallbackColor));
                        return;
                    }
                }
                
                // Kiểm tra cache trước
                Image img = imageCache.get(source);
                final String finalSource = source; // Make final for lambda
                final Circle finalCircle = circle; // Make final for lambda
                final String finalFallbackColor = fallbackColor; // Make final for lambda
                
                if (img == null || img.isError()) {
                    // Load image với background loading
                    img = new Image(finalSource, finalCircle.getRadius() * 2, finalCircle.getRadius() * 2, true, true, true);
                    final Image finalImg = img; // Make final for lambda
                    // Cache image nếu load thành công
                    finalImg.progressProperty().addListener((obs, oldVal, newVal) -> {
                        if (newVal.doubleValue() >= 1.0 && !finalImg.isError()) {
                            imageCache.put(finalSource, finalImg);
                        }
                    });
                    // Cache ngay nếu đã load xong
                    if (finalImg.getProgress() >= 1.0 && !finalImg.isError()) {
                        imageCache.put(finalSource, finalImg);
                    }
                }
                
                final Image finalImg = img; // Make final for lambda
                
                // Thêm error listener để log chi tiết (chỉ log một lần cho mỗi URL)
                finalImg.errorProperty().addListener((obs, wasError, isError) -> {
                    if (isError && !failedAvatarUrls.contains(finalSource)) {
                        failedAvatarUrls.add(finalSource);
                        Exception exception = finalImg.getException();
                        System.err.println("[MainChatController] Avatar image loading error for: " + finalSource);
                        if (exception != null) {
                            System.err.println("[MainChatController] Exception: " + exception.getMessage());
                            // Chỉ print stack trace cho lần đầu tiên
                            if (exception.getMessage() != null && exception.getMessage().contains("Connection timed out")) {
                                System.err.println("[MainChatController] This avatar URL may be using an old IP address. Consider updating the avatar.");
                            } else {
                                exception.printStackTrace();
                            }
                        }
                        Platform.runLater(() -> {
                            finalCircle.setFill(Color.web(finalFallbackColor));
                        });
                    } else if (isError) {
                        // URL đã fail trước đó, chỉ set fallback màu không log
                        Platform.runLater(() -> {
                            finalCircle.setFill(Color.web(finalFallbackColor));
                        });
                    }
                });
                
                // Kiểm tra error ngay sau khi tạo Image
                if (finalImg.isError()) {
                    if (!failedAvatarUrls.contains(finalSource)) {
                        failedAvatarUrls.add(finalSource);
                        Exception exception = finalImg.getException();
                        System.err.println("[MainChatController] Avatar image has error immediately, using fallback. Path: " + avatarPath);
                        if (exception != null) {
                            System.err.println("[MainChatController] Exception: " + exception.getMessage());
                            if (exception.getMessage() != null && exception.getMessage().contains("Connection timed out")) {
                                System.err.println("[MainChatController] This avatar URL may be using an old IP address. Consider updating the avatar.");
                            }
                        }
                    }
                    finalCircle.setFill(Color.web(finalFallbackColor));
                    return;
                }
                
                // Nếu image đang load, đợi load xong
                if (finalImg.getProgress() < 1.0) {
                    finalImg.progressProperty().addListener((obs, oldVal, newVal) -> {
                        if (newVal.doubleValue() >= 1.0) {
                            Platform.runLater(() -> {
                                if (finalImg.isError()) {
                                    if (!failedAvatarUrls.contains(finalSource)) {
                                        failedAvatarUrls.add(finalSource);
                                        Exception exception = finalImg.getException();
                                        System.err.println("[MainChatController] Avatar image failed to load: " + finalSource);
                                        if (exception != null) {
                                            System.err.println("[MainChatController] Exception: " + exception.getMessage());
                                            if (exception.getMessage() != null && exception.getMessage().contains("Connection timed out")) {
                                                System.err.println("[MainChatController] This avatar URL may be using an old IP address. Consider updating the avatar.");
                                            }
                                        }
                                    }
                                    finalCircle.setFill(Color.web(finalFallbackColor));
                                } else {
                                    finalCircle.setFill(new ImagePattern(finalImg));
                                    System.out.println("[MainChatController] Avatar loaded successfully: " + finalSource);
                                }
                            });
                        }
                    });
                } else {
                    // Image đã load xong
                    if (finalImg.isError()) {
                        if (!failedAvatarUrls.contains(finalSource)) {
                            failedAvatarUrls.add(finalSource);
                            Exception exception = finalImg.getException();
                            System.err.println("[MainChatController] Avatar image has error after load, using fallback. Path: " + avatarPath);
                            if (exception != null) {
                                System.err.println("[MainChatController] Exception: " + exception.getMessage());
                                if (exception.getMessage() != null && exception.getMessage().contains("Connection timed out")) {
                                    System.err.println("[MainChatController] This avatar URL may be using an old IP address. Consider updating the avatar.");
                                }
                            }
                        }
                        finalCircle.setFill(Color.web(finalFallbackColor));
                    } else {
                        finalCircle.setFill(new ImagePattern(finalImg));
                        System.out.println("[MainChatController] Avatar loaded successfully (already loaded): " + finalSource);
                    }
                    return;
                }
            } catch (Exception e) {
                System.err.println("[MainChatController] Could not load avatar: " + e.getMessage() + ", path: " + avatarPath);
            }
        }
        // Fallback to color nếu không có avatarPath hoặc có lỗi
        circle.setFill(Color.web(fallbackColor));
    }

    /**
     * Copy avatar file vào thư mục chung data/avatars/ và trả về relative path.
     */
    private String copyAvatarToSharedFolder(File sourceFile, Long userId) throws IOException {
        // Tạo thư mục data/avatars nếu chưa có
        File avatarsDir = new File("data/avatars");
        if (!avatarsDir.exists()) {
            avatarsDir.mkdirs();
        }
        
        // Tạo tên file: userId_timestamp.extension
        String extension = "";
        String fileName = sourceFile.getName();
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            extension = fileName.substring(lastDot);
        }
        String newFileName = userId + "_" + System.currentTimeMillis() + extension;
        File destFile = new File(avatarsDir, newFileName);
        
        // Copy file
        java.nio.file.Files.copy(
            sourceFile.toPath(),
            destFile.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING
        );
        
        // Trả về relative path: avatars/filename
        return "avatars/" + newFileName;
    }
    
    /**
     * Normalize avatar URL: thay localhost và các IP cũ bằng SERVER_HOST để hoạt động trên máy khác.
     * Nếu URL chứa IP khác với SERVER_HOST hiện tại, sẽ thay thế để tránh lỗi connection timeout.
     */
    private String normalizeAvatarUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        String serverHost = org.example.demo2.config.ServerConfig.SERVER_HOST;
        String fileServerPort = String.valueOf(org.example.demo2.config.ServerConfig.FILE_SERVER_PORT);
        
        // Thay localhost bằng SERVER_HOST
        String normalized = url.replace("http://localhost:", "http://" + serverHost + ":");
        normalized = normalized.replace("https://localhost:", "https://" + serverHost + ":");
        
        // Thay thế các IP cũ (không phải localhost và không phải SERVER_HOST hiện tại) bằng SERVER_HOST
        // Pattern: http://IP:PORT/ hoặc https://IP:PORT/
        java.util.regex.Pattern ipPattern = java.util.regex.Pattern.compile(
            "(https?://)([0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3})(:[0-9]+)(/.*)?"
        );
        java.util.regex.Matcher matcher = ipPattern.matcher(normalized);
        if (matcher.find()) {
            String protocol = matcher.group(1);
            String ip = matcher.group(2);
            String port = matcher.group(3);
            String path = matcher.group(4) != null ? matcher.group(4) : "";
            
            // Nếu IP không phải localhost và không phải SERVER_HOST hiện tại, thay thế
            if (!ip.equals("127.0.0.1") && !ip.equals("localhost") && !ip.equals(serverHost)) {
                normalized = protocol + serverHost + port + path;
                System.out.println("[MainChatController] Normalized avatar URL from old IP " + ip + " to " + serverHost + ": " + normalized);
            }
        }
        
        return normalized;
    }
    
    /**
     * Lấy file path từ relative path hoặc absolute path.
     * (Giữ lại để backward compatibility với avatar cũ)
     */
    private File getAvatarFile(String avatarPath) {
        if (avatarPath == null || avatarPath.isBlank()) {
            return null;
        }
        
        // Nếu là URL, return null (sẽ xử lý riêng trong applyAvatar)
        if (avatarPath.startsWith("http://") || avatarPath.startsWith("https://")) {
            return null;
        }
        
        // Nếu là relative path (bắt đầu với "avatars/")
        if (avatarPath.startsWith("avatars/")) {
            File file = new File("data/" + avatarPath);
            if (file.exists() && file.isFile()) {
                return file;
            }
        }
        
        // Nếu là absolute path (fallback cho backward compatibility)
        File file = new File(avatarPath);
        if (file.exists() && file.isFile()) {
            return file;
        }
        
        return null;
    }
    
    private String fallbackColorForUser(Long userId) {
        if (userId == null || userId <= 0) {
            return "#6366f1";
        }
        int idx = (int) ((userId - 1) % AVATAR_COLORS.length);
        return AVATAR_COLORS[idx];
    }

    private boolean isUserOnline(Long userId) {
        try {
            return clientService != null && clientService.isOnline(userId);
        } catch (Exception e) {
            return false;
        }
    }

    private void setStatusIndicator(boolean online) {
        if (statusIndicator == null) return;
        statusIndicator.setVisible(true);
        statusIndicator.setFill(Color.web(online ? ONLINE_COLOR : OFFLINE_COLOR));
        statusIndicator.setStroke(Color.web("#0f172a"));
        statusIndicator.setStrokeWidth(1.5);
    }

    // ---------------- Typing presence (UDP multicast) ----------------
    private void startTypingPresence() {
        try {
            typingSocket = new MulticastSocket(TYPING_PORT);
            typingSocket.setReuseAddress(true);
            InetAddress group = InetAddress.getByName(TYPING_GROUP);
            typingSocket.joinGroup(new java.net.InetSocketAddress(group, TYPING_PORT), null);
            typingRunning.set(true);
            typingThread = new Thread(() -> listenTyping(group));
            typingThread.setDaemon(true);
            typingThread.start();
        } catch (Exception e) {
            System.err.println("[MainChatController] Typing presence init failed: " + e.getMessage());
            typingSocket = null;
        }
    }

    private void listenTyping(InetAddress group) {
        byte[] buf = new byte[512];
        while (typingRunning.get()) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                typingSocket.receive(packet);
                String payload = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                handleTypingMessage(payload);
            } catch (IOException e) {
                if (typingRunning.get()) {
                    System.err.println("[MainChatController] Typing listen error: " + e.getMessage());
                }
            }
        }
    }

    private void handleTypingMessage(String payload) {
        String[] parts = payload.split("\\|", 4);
        if (parts.length < 4) return;
        String type = parts[0];
        String room = parts[1];
        String userIdStr = parts[2];
        String displayName = parts[3];
        if (!"TYPING".equals(type)) return;
        if (currentRoomId == null || !currentRoomId.equals(room)) return;
        try {
            if (Long.parseLong(userIdStr) == Session.getUserId()) {
                return; // ignore self
            }
        } catch (NumberFormatException ignored) {}
        showTypingIndicator(displayName);
    }

    private void sendTypingSignal() {
        if (typingSocket == null || currentRoomId == null) return;
        long now = System.currentTimeMillis();
        if (now - lastTypingSent < 1200) {
            return; // throttle
        }
        lastTypingSent = now;
        try {
            String payload = "TYPING|" + currentRoomId + "|" + Session.getUserId() + "|" + Session.getDisplayName();
            byte[] data = payload.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(TYPING_GROUP), TYPING_PORT);
            typingSocket.send(packet);
        } catch (Exception e) {
            System.err.println("[MainChatController] sendTypingSignal error: " + e.getMessage());
        }
    }

    private void showTypingIndicator(String name) {
        Platform.runLater(() -> {
            applyStatus(lblChatStatus, name + " đang nhập...", TYPING_COLOR);
            if (typingTimeline != null) {
                typingTimeline.stop();
            }
            typingTimeline = new javafx.animation.Timeline(
                    new javafx.animation.KeyFrame(Duration.seconds(2.5), ev -> updateCurrentStatusLabel())
            );
            typingTimeline.setCycleCount(1);
            typingTimeline.play();
        });
    }

    private void updateCurrentStatusLabel() {
        if (currentChat == null) {
            return;
        }
        if (currentChat.user != null) {
            boolean online = isUserOnline(currentChat.user.id());
            applyStatus(lblChatStatus, online ? "Đang hoạt động" : "Không hoạt động",
                    online ? ONLINE_COLOR : OFFLINE_COLOR);
        } else if (currentChat.conversation != null && "GROUP".equals(currentChat.conversation.type())) {
            applyStatus(lblChatStatus, "Nhóm", ONLINE_COLOR);
        }
    }

    private User getCachedUser(Long userId) {
        if (userId == null) {
            return null;
        }
        User cached = userCache.get(userId);
        if (cached != null) {
            return cached;
        }
        if (clientService == null) {
            return null;
        }
        try {
            User remote = clientService.findUserById(userId);
            if (remote != null) {
                userCache.put(userId, remote);
            }
            return remote;
        } catch (Exception e) {
            System.err.println("[MainChatController] Could not fetch user " + userId + ": " + e.getMessage());
            return null;
        }
    }
    
    private void applyStatus(Label label, String text, String colorHex) {
        if (label == null) return;
        label.setText(text);
        label.setTextFill(Color.web(colorHex));
    }
    
    /**
     * Model cho contact item (friend hoặc group).
     */
    private static class ContactItem {
        final User user;
        final String name;
        final String lastMessage;
        final Conversation conversation;
        final Boolean online;
        
        // Constructor cho friend (có user)
        ContactItem(User user, String lastMessage, Conversation conversation, Boolean online) {
            this.user = user;
            this.name = user != null ? user.displayName() : null;
            this.lastMessage = lastMessage;
            this.conversation = conversation;
            this.online = online;
        }
        
        // Constructor cho group (có conversation) hoặc friend với conversation
        ContactItem(User user, String name, String lastMessage, Conversation conversation, Boolean online) {
            this.user = user;
            this.name = name;
            this.lastMessage = lastMessage;
            this.conversation = conversation;
            this.online = online;
        }
    }
}

