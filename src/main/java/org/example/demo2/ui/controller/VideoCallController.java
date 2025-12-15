package org.example.demo2.ui.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.image.PixelWriter;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import org.example.demo2.Session;
import org.example.demo2.client.AegisTalkClientService;
import org.example.demo2.net.udp.VideoStreamClient;
import org.example.demo2.service.rmi.CallService;

import java.awt.image.BufferedImage;
import java.awt.Dimension;
import java.io.ByteArrayOutputStream;
import java.rmi.RemoteException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import javafx.embed.swing.SwingFXUtils;

/**
 * Controller cho Video Call UI.
 * 
 * Áp dụng:
 * - RMI: Signaling (mời, chấp nhận, từ chối, kết thúc cuộc gọi)
 * - UDP: Video/audio streaming (real-time)
 */
public class VideoCallController {
    
    @FXML private Label lblCallStatus;
    @FXML private Label lblCallDuration;
    @FXML private Label lblRemoteName;
    @FXML private VBox callStatusOverlay;
    @FXML private HBox muteIndicator;
    @FXML private Region remoteVideo;
    @FXML private Region localVideo;
    @FXML private StackPane remoteVideoStack;
    @FXML private StackPane localVideoStack;
    @FXML private Button btnJoin;
    @FXML private Button btnLeave;
    @FXML private Button btnCamera;
    @FXML private Button btnMute;
    
    private AegisTalkClientService clientService;
    private VideoStreamClient videoStreamClient;
    private Integer currentCallSessionId;
    private Long otherUserId;
    private String otherUserName;
    private boolean isCaller; // true nếu là người gọi, false nếu là người nhận
    private boolean isMuted = false;
    private boolean isCameraOn = true; // Camera mặc định bật
    private boolean isInCall = false;
    private boolean remoteCameraOn = true; // Track remote camera status
    private long lastRemoteFrameTime = 0; // Track khi nhận frame cuối cùng
    
    // Track video canvases
    private Canvas localVideoCanvas;
    private Canvas remoteVideoCanvas;
    private ImageView localVideoImageView; // Dùng ImageView để hiển thị webcam thật
    private ImageView remoteVideoImageView; // Dùng ImageView để hiển thị remote video thật
    
    // Webcam capture
    private com.github.sarxos.webcam.Webcam webcam;
    private Thread webcamThread;
    
    // Audio capture and playback
    private TargetDataLine microphone;
    private SourceDataLine speakers;
    private Thread audioSendThread;
    private Thread audioReceiveThread;
    private AudioFormat audioFormat;
    private volatile boolean audioRunning = false;
    
    // Callback để đóng video call window
    private Runnable onCloseCallback;
    
    public void setClientService(AegisTalkClientService clientService) {
        this.clientService = clientService;
    }
    
    public void setOnCloseCallback(Runnable callback) {
        this.onCloseCallback = callback;
    }
    
    /**
     * Khởi tạo cuộc gọi (caller).
     */
    public void startCall(Long calleeId, String calleeName) {
        this.otherUserId = calleeId;
        this.otherUserName = calleeName;
        this.isCaller = true;
        
        Platform.runLater(() -> {
            lblCallStatus.setText("Đang gọi " + calleeName + "...");
            if (btnJoin != null) {
                btnJoin.setVisible(false);
                btnJoin.setManaged(false);
            }
            if (btnLeave != null) {
                btnLeave.setVisible(true);
            }
            // Hiển thị nút camera và mic ngay khi bắt đầu gọi
            if (btnCamera != null) {
                btnCamera.setVisible(true);
                btnCamera.setText("📷");
            }
            if (btnMute != null) {
                btnMute.setVisible(true);
                btnMute.setText("🎤");
            }
        });
        
        // Mở camera preview ngay lập tức (chưa streaming, chỉ preview)
        System.out.println("[VideoCallController] Starting camera preview for caller");
        isInCall = true; // Set để camera có thể hiển thị
        startLocalVideo();
        
        // Gửi lời mời qua RMI và đợi sessionId trước khi start polling
        new Thread(() -> {
            try {
                Long callerId = Session.getUserId();
                Integer sessionId = clientService.inviteCall(callerId, calleeId);
                if (sessionId != null) {
                    this.currentCallSessionId = sessionId;
                    System.out.println("[VideoCallController] Call invited: session=" + sessionId);
                    
                    // Start polling SAU KHI sessionId đã được set
                    startCallStatusPolling();
                } else {
                    Platform.runLater(() -> {
                        showError("Không thể tạo cuộc gọi");
                        if (onCloseCallback != null) {
                            onCloseCallback.run();
                        }
                    });
                }
            } catch (RemoteException e) {
                Platform.runLater(() -> {
                    showError("Lỗi gửi lời mời: " + e.getMessage());
                    if (onCloseCallback != null) {
                        onCloseCallback.run();
                    }
                });
            }
        }).start();
    }
    
    /**
     * Nhận cuộc gọi (callee).
     * @param autoAccept Nếu true, tự động accept call ngay lập tức
     */
    public void receiveCall(Integer sessionId, Long callerId, String callerName, boolean autoAccept) {
        this.currentCallSessionId = sessionId;
        this.otherUserId = callerId;
        this.otherUserName = callerName;
        this.isCaller = false;
        
        Platform.runLater(() -> {
            if (autoAccept) {
                // Tự động accept call
                handleJoinCall();
            } else {
                // Hiển thị UI để user chấp nhận
                lblCallStatus.setText(callerName + " đang gọi...");
                if (btnJoin != null) {
                    btnJoin.setVisible(true);
                    btnJoin.setManaged(true);
                }
                if (btnLeave != null) {
                    btnLeave.setVisible(true);
                }
                // Hiển thị nút camera và mic
                if (btnCamera != null) {
                    btnCamera.setVisible(true);
                    btnCamera.setText("📷");
                }
                if (btnMute != null) {
                    btnMute.setVisible(true);
                    btnMute.setText("🎤");
                }
            }
        });
    }
    
    /**
     * Nhận cuộc gọi (callee) - không auto accept (backward compatibility).
     */
    public void receiveCall(Integer sessionId, Long callerId, String callerName) {
        receiveCall(sessionId, callerId, callerName, false);
    }
    
    @FXML
    private void initialize() {
        // Setup UI - ban đầu ẩn các nút
        if (btnJoin != null) {
            btnJoin.setVisible(false);
            btnJoin.setManaged(false);
        }
        if (btnLeave != null) {
            btnLeave.setVisible(false);
        }
        if (btnCamera != null) {
            btnCamera.setVisible(false);
            // Icon camera bật
            btnCamera.setText("📷");
        }
        if (btnMute != null) {
            btnMute.setVisible(false);
            // Icon mic bật
            btnMute.setText("🎤");
        }
    }
    
    @FXML
    private void handleJoinCall() {
        if (currentCallSessionId == null) {
            System.err.println("[VideoCallController] Cannot accept call: sessionId is null");
            showError("Lỗi: Không có session ID");
            return;
        }
        
        // Chấp nhận cuộc gọi
        new Thread(() -> {
            try {
                Long userId = Session.getUserId();
                System.out.println("[VideoCallController] Attempting to accept call: session=" + currentCallSessionId + ", user=" + userId);
                
                // Kiểm tra call info trước khi accept
                CallService.CallInfo callInfo = clientService.getCallInfo(currentCallSessionId);
                if (callInfo == null) {
                    Platform.runLater(() -> {
                        showError("Cuộc gọi không tồn tại hoặc đã bị hủy");
                    });
                    return;
                }
                
                if (!"PENDING".equals(callInfo.status)) {
                    Platform.runLater(() -> {
                        showError("Cuộc gọi không còn ở trạng thái PENDING (status: " + callInfo.status + ")");
                    });
                    return;
                }
                
                if (!callInfo.calleeId.equals(userId)) {
                    Platform.runLater(() -> {
                        showError("Bạn không phải là người nhận cuộc gọi này");
                    });
                    return;
                }
                
                boolean success = clientService.acceptCall(currentCallSessionId, userId);
                System.out.println("[VideoCallController] Accept call result: " + success);
                
                if (success) {
                    // Set isInCall trước để startLocalVideo có thể hoạt động
                    isInCall = true;
                        Platform.runLater(() -> {
                            lblCallStatus.setText("Đã kết nối với " + otherUserName);
                            if (btnJoin != null) {
                                btnJoin.setVisible(false);
                                btnJoin.setManaged(false);
                            }
                            if (btnLeave != null) {
                                btnLeave.setVisible(true);
                            }
                            if (btnCamera != null) {
                                btnCamera.setVisible(true);
                                btnCamera.setText("📷");
                            }
                            if (btnMute != null) {
                                btnMute.setVisible(true);
                                btnMute.setText("🎤");
                            }
                            // Bắt đầu video streaming sau khi UI đã được cập nhật
                            startVideoStreaming();
                        });
                } else {
                    Platform.runLater(() -> {
                        showError("Không thể chấp nhận cuộc gọi. Có thể cuộc gọi đã bị hủy.");
                    });
                }
            } catch (RemoteException e) {
                System.err.println("[VideoCallController] Error accepting call: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> {
                    showError("Lỗi chấp nhận cuộc gọi: " + e.getMessage());
                });
            }
        }).start();
    }
    
    @FXML
    public void handleLeaveCall() {
        if (currentCallSessionId == null) {
            if (onCloseCallback != null) {
                onCloseCallback.run();
            }
            return;
        }
        
        new Thread(() -> {
            try {
                Long userId = Session.getUserId();
                
                if (isInCall) {
                    // Kết thúc cuộc gọi
                    clientService.endCall(currentCallSessionId, userId);
                } else if (isCaller) {
                    // Hủy lời mời
                    clientService.endCall(currentCallSessionId, userId);
                } else {
                    // Từ chối cuộc gọi
                    clientService.rejectCall(currentCallSessionId, userId);
                }
                
                // Dừng streaming
                stopVideoStreaming();
                
                Platform.runLater(() -> {
                    if (onCloseCallback != null) {
                        onCloseCallback.run();
                    }
                });
            } catch (RemoteException e) {
                Platform.runLater(() -> {
                    showError("Lỗi: " + e.getMessage());
                });
            }
        }).start();
    }
    
    @FXML
    private void handleToggleMute() {
        isMuted = !isMuted;
        if (btnMute != null) {
            // Icon mic: 🎤 khi bật, 🔇 khi tắt
            btnMute.setText(isMuted ? "🔇" : "🎤");
            // Thêm/xóa class để thay đổi màu
            if (isMuted) {
                btnMute.getStyleClass().add("muted");
            } else {
                btnMute.getStyleClass().remove("muted");
            }
        }
        // Hiển thị mute indicator
        if (muteIndicator != null) {
            muteIndicator.setVisible(isMuted);
        }
        System.out.println("[VideoCallController] Mic " + (isMuted ? "muted" : "unmuted"));
    }
    
    @FXML
    private void handleToggleCamera() {
        isCameraOn = !isCameraOn;
        if (btnCamera != null) {
            // Icon camera: 📷 khi bật, 📷❌ khi tắt
            btnCamera.setText(isCameraOn ? "📷" : "🚫");
            // Thêm/xóa class để thay đổi màu
            if (!isCameraOn) {
                btnCamera.getStyleClass().add("camera-off");
            } else {
                btnCamera.getStyleClass().remove("camera-off");
            }
        }
        System.out.println("[VideoCallController] Camera " + (isCameraOn ? "on" : "off"));
        
        if (!isCameraOn) {
            // TẮT CAMERA
            // Chỉ đóng webcam hoàn toàn nếu đang trong cuộc gọi active (streaming)
            // Nếu chỉ đang preview (chưa streaming), chỉ ẩn preview mà không đóng webcam
            boolean isStreaming = (videoStreamClient != null);
            
            if (isStreaming) {
                // Đang streaming - đóng webcam để release quyền
                System.out.println("[VideoCallController] Camera off during streaming - stopping webcam capture");
                stopWebcamCapture();
            } else {
                // Chưa streaming (đang preview) - chỉ dừng capture thread, KHÔNG đóng webcam
                System.out.println("[VideoCallController] Camera off during preview - pausing capture only");
                // Flag isCameraOn = false sẽ khiến capture thread tự tạm dừng
            }
            
            Platform.runLater(() -> {
                // Hiển thị màn hình đen trên local video
                if (localVideoImageView != null) {
                    WritableImage blackImage = new WritableImage(
                        (int)Math.max(1, localVideoImageView.getFitWidth()),
                        (int)Math.max(1, localVideoImageView.getFitHeight())
                    );
                    GraphicsContext gc = new Canvas(blackImage.getWidth(), blackImage.getHeight()).getGraphicsContext2D();
                    gc.setFill(Color.BLACK);
                    gc.fillRect(0, 0, blackImage.getWidth(), blackImage.getHeight());
                    gc.setFill(Color.WHITE);
                    gc.setFont(javafx.scene.text.Font.font(12));
                    gc.fillText("Camera đã tắt", blackImage.getWidth() / 2 - 50, blackImage.getHeight() / 2);
                    localVideoImageView.setImage(blackImage);
                } else if (localVideoCanvas != null) {
                    GraphicsContext gc = localVideoCanvas.getGraphicsContext2D();
                    double width = localVideoCanvas.getWidth();
                    double height = localVideoCanvas.getHeight();
                    if (width > 0 && height > 0) {
                        gc.setFill(Color.BLACK);
                        gc.fillRect(0, 0, width, height);
                        gc.setFill(Color.WHITE);
                        gc.setFont(javafx.scene.text.Font.font(12));
                        gc.fillText("Camera đã tắt", width / 2 - 50, height / 2);
                    }
                }
            });
        } else {
            // BẬT CAMERA - Mở lại webcam nếu đang trong cuộc gọi
            if (isInCall && currentCallSessionId != null) {
                System.out.println("[VideoCallController] Reopening webcam for streaming...");
                try {
                    reopenWebcam();
                } catch (Exception e) {
                    System.err.println("[VideoCallController] Error reopening webcam: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }
    
    private void startVideoStreaming() {
        if (currentCallSessionId == null) {
            System.err.println("[VideoCallController] ERROR: Cannot start streaming - currentCallSessionId is null!");
            return;
        }
        
        System.out.println("[VideoCallController] ===== Starting video streaming for session: " + currentCallSessionId + " (isCaller=" + isCaller + ") =====");
        
        try {
            // Bắt đầu local video TRƯỚC để đảm bảo UI được setup
            // Nếu đã có webcam mở từ preview (caller), không cần mở lại
            if (webcam == null || !webcam.isOpen()) {
                System.out.println("[VideoCallController] Starting local video first...");
                startLocalVideo();
                // Đợi một chút để canvas được thêm vào UI
                Thread.sleep(200);
            } else {
                System.out.println("[VideoCallController] Webcam already open from preview, reusing for streaming");
            }
            
            // Tạo UDP client
            videoStreamClient = new VideoStreamClient(currentCallSessionId);
            
            // QUAN TRỌNG: Set userId cho VideoStreamClient để server phân biệt được users
            Long userId = Session.getUserId();
            videoStreamClient.setUserId(userId.intValue());
            System.out.println("[VideoCallController] Set userId=" + userId + " for VideoStreamClient");
            
            videoStreamClient.connect(org.example.demo2.config.ServerConfig.SERVER_HOST, org.example.demo2.config.ServerConfig.VIDEO_STREAM_PORT);
            System.out.println("[VideoCallController] UDP client connected");
            
            // Đăng ký UDP endpoint với server
            // Sử dụng phương thức getLocalLanAddress để lấy đúng IP LAN (không phải localhost)
            String localAddress = VideoStreamClient.getLocalLanAddress();
            int localPort = videoStreamClient.getLocalPort();
            
            System.out.println("[VideoCallController] Registering UDP endpoint: " + localAddress + ":" + localPort + " for user " + userId);
            clientService.registerUdpEndpoint(currentCallSessionId, userId, localAddress, localPort);
            System.out.println("[VideoCallController] UDP endpoint registered: " + localAddress + ":" + localPort);
            
            // Bắt đầu nhận frame (video + audio)
            final int[] audioReceivedCount = {0};
            videoStreamClient.startReceiving((sessionId, sequence, timestamp, frameData) -> {
                // QUAN TRỌNG: Chỉ xử lý frames từ đúng session
                if (sessionId == currentCallSessionId) {
                    // Kiểm tra xem đây là audio hay video packet
                    if (frameData.length > 6 && 
                        frameData[0] == 'A' && frameData[1] == 'U' && 
                        frameData[2] == 'D' && frameData[3] == 'I' && 
                        frameData[4] == 'O' && frameData[5] == ':') {
                        // Audio packet - phát qua speakers
                        byte[] audioData = new byte[frameData.length - 6];
                        System.arraycopy(frameData, 6, audioData, 0, audioData.length);
                        audioReceivedCount[0]++;
                        // Log mỗi 50 packets để debug
                        if (audioReceivedCount[0] % 50 == 0) {
                            System.out.println("[VideoCallController] ✓ Received " + audioReceivedCount[0] + " AUDIO packets, latest: " + audioData.length + " bytes");
                        }
                        playRemoteAudio(audioData);
                    } else {
                        // Video packet - hiển thị
                        Platform.runLater(() -> {
                            displayRemoteVideo(frameData);
                        });
                    }
                } else {
                    System.out.println("[VideoCallController] Ignoring frame from wrong session: " + sessionId + " (expected: " + currentCallSessionId + ")");
                }
            });
            System.out.println("[VideoCallController] Started receiving frames (video + audio)");
            
            // Start monitoring remote camera status (check if no frames received for a while)
            startRemoteCameraMonitoring();
            
            // Status sẽ được update bởi polling khi ACTIVE
            
            // Bắt đầu gửi video frame
            startSendingFrames();
            System.out.println("[VideoCallController] Started sending frames");
            
            // Bắt đầu audio streaming
            startAudioStreaming();
            System.out.println("[VideoCallController] Started audio streaming");
            
        } catch (Exception e) {
            System.err.println("[VideoCallController] Error starting video streaming: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Mở lại webcam sau khi đã đóng (dùng khi bật lại camera).
     */
    private void reopenWebcam() {
        System.out.println("[VideoCallController] Reopening webcam...");
        
        // Đảm bảo webcam đã được đóng hoàn toàn
        if (webcam != null && webcam.isOpen()) {
            try {
                webcam.close();
                System.out.println("[VideoCallController] Closed existing webcam before reopening");
            } catch (Exception e) {
                System.err.println("[VideoCallController] Error closing webcam before reopen: " + e.getMessage());
            }
            webcam = null;
        }
        
        // Đợi lâu hơn để webcam được release hoàn toàn (webcam-capture cần thời gian)
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Mở lại webcam
        try {
            List<com.github.sarxos.webcam.Webcam> webcams = com.github.sarxos.webcam.Webcam.getWebcams();
            if (webcams.isEmpty()) {
                System.out.println("[VideoCallController] No webcam available for reopening");
                return;
            }
            
            webcam = com.github.sarxos.webcam.Webcam.getDefault();
            if (webcam == null) {
                System.out.println("[VideoCallController] No default webcam available - auto disable camera");
                isCameraOn = false;
                Platform.runLater(() -> {
                    if (btnCamera != null) {
                        btnCamera.setText("🚫");
                        btnCamera.getStyleClass().add("camera-off");
                    }
                });
                return;
            }
            
            // Kiểm tra webcam đã mở chưa - nếu đã mở thì không cần set view size
            if (!webcam.isOpen()) {
                // Set view size trước khi mở
                Dimension[] sizes = webcam.getViewSizes();
                if (sizes.length > 0) {
                    Dimension targetSize = sizes[sizes.length - 1];
                    if (targetSize.width > 640) {
                        for (Dimension size : sizes) {
                            if (size.width <= 640) {
                                targetSize = size;
                                break;
                            }
                        }
                    }
                    webcam.setViewSize(targetSize);
                }
                
                // Mở webcam
                webcam.open();
            }
            System.out.println("[VideoCallController] ✓ Webcam reopened successfully!");
            
            // Restart webcam display (sẽ reuse ImageView nếu đã tồn tại)
            // Nếu ImageView đã tồn tại, chỉ restart capture thread
            // Nếu chưa có ImageView, tạo mới
            startWebcamDisplay(false); // false = reuse existing ImageView
        } catch (com.github.sarxos.webcam.WebcamLockException e) {
            System.err.println("[VideoCallController] Webcam is locked, auto disable camera: " + e.getMessage());
            webcam = null;
            isCameraOn = false;
            Platform.runLater(() -> {
                if (btnCamera != null) {
                    btnCamera.setText("🚫");
                    btnCamera.getStyleClass().add("camera-off");
                }
            });
        } catch (Exception e) {
            System.err.println("[VideoCallController] Error reopening webcam: " + e.getMessage());
            e.printStackTrace();
            webcam = null;
            isCameraOn = false;
            Platform.runLater(() -> {
                if (btnCamera != null) {
                    btnCamera.setText("🚫");
                    btnCamera.getStyleClass().add("camera-off");
                }
            });
        }
    }
    
    /**
     * Bắt đầu hiển thị local video (webcam preview).
     * Thử mở webcam thật trước, nếu không có thì dùng placeholder.
     */
    private void startLocalVideo() {
        if (localVideo == null) {
            System.err.println("[VideoCallController] localVideo is null, cannot start local video");
            return;
        }
        
        // Nếu đã có canvas hoặc imageview, không tạo lại
        if (localVideoCanvas != null || localVideoImageView != null) {
            System.out.println("[VideoCallController] Local video already exists");
            return;
        }
        
        System.out.println("[VideoCallController] Starting local video display");
        System.out.println("[VideoCallController] localVideo parent: " + (localVideo.getParent() != null ? localVideo.getParent().getClass().getName() : "null"));
        
        // Thử mở webcam thật
        System.out.println("[VideoCallController] Attempting to open real webcam...");
        try {
            // Kiểm tra webcam có sẵn không
            System.out.println("[VideoCallController] Checking for available webcams...");
            List<com.github.sarxos.webcam.Webcam> webcams = com.github.sarxos.webcam.Webcam.getWebcams();
            System.out.println("[VideoCallController] Found " + webcams.size() + " webcam(s)");
            
            if (webcams.isEmpty()) {
                System.out.println("[VideoCallController] No webcam found, using placeholder");
                startPlaceholderVideo();
                return;
            }
            
            // Lấy webcam mặc định
            System.out.println("[VideoCallController] Getting default webcam...");
            webcam = com.github.sarxos.webcam.Webcam.getDefault();
            if (webcam == null) {
                System.out.println("[VideoCallController] Cannot get default webcam, using placeholder");
                startPlaceholderVideo();
                return;
            }
            
            System.out.println("[VideoCallController] Default webcam: " + webcam.getName());
            
            // Kiểm tra webcam đã mở chưa
            if (webcam.isOpen()) {
                System.out.println("[VideoCallController] Webcam already open, reusing existing instance");
                // Kiểm tra xem webcam có thực sự hoạt động không
                BufferedImage testImage = webcam.getImage();
                if (testImage == null) {
                    System.out.println("[VideoCallController] Warning: Webcam is open but getImage() returns null - may be locked by another process");
                    // Fallback về placeholder
                    throw new com.github.sarxos.webcam.WebcamLockException("Webcam is locked");
                }
            } else {
                // Thiết lập kích thước TRƯỚC KHI mở webcam (quan trọng!)
                Dimension[] sizes = webcam.getViewSizes();
                System.out.println("[VideoCallController] Available view sizes: " + sizes.length);
                if (sizes.length > 0) {
                    // Dùng kích thước vừa phải (không quá lớn để tránh lag)
                    Dimension targetSize = sizes[sizes.length - 1];
                    // Giới hạn tối đa 640x480 để performance tốt
                    if (targetSize.width > 640) {
                        for (Dimension size : sizes) {
                            if (size.width <= 640) {
                                targetSize = size;
                                break;
                            }
                        }
                    }
                    webcam.setViewSize(targetSize);
                    System.out.println("[VideoCallController] Webcam view size set to: " + targetSize.width + "x" + targetSize.height);
                }
                
                // Mở webcam SAU KHI set view size
                System.out.println("[VideoCallController] Opening webcam...");
                webcam.open();
                System.out.println("[VideoCallController] ✓ Webcam opened successfully!");
            }
            System.out.println("[VideoCallController] Webcam is open: " + webcam.isOpen());
            
            // Tạo ImageView để hiển thị webcam
            startWebcamDisplay();
            
        } catch (com.github.sarxos.webcam.WebcamLockException e) {
            // Webcam đã bị lock bởi instance khác (có thể do nhiều người dùng trên cùng máy)
            System.err.println("[VideoCallController] ✗ Webcam is locked by another instance");
            System.err.println("[VideoCallController] This usually happens when multiple users are on the same machine");
            System.err.println("[VideoCallController] Falling back to placeholder video with camera OFF");
            // Đảm bảo webcam reference được clear
            webcam = null;
            // TỰ ĐỘNG TẮT CAMERA khi bị lock
            isCameraOn = false;
            Platform.runLater(() -> {
                if (btnCamera != null) {
                    btnCamera.setText("🚫");
                    btnCamera.getStyleClass().add("camera-off");
                }
            });
            startPlaceholderVideo();
        } catch (Exception e) {
            System.err.println("[VideoCallController] ✗ Error opening webcam: " + e.getMessage());
            System.err.println("[VideoCallController] Exception type: " + e.getClass().getName());
            e.printStackTrace();
            // Fallback về placeholder với camera TẮT
            System.out.println("[VideoCallController] Falling back to placeholder video with camera OFF");
            webcam = null;
            // TỰ ĐỘNG TẮT CAMERA khi có lỗi
            isCameraOn = false;
            Platform.runLater(() -> {
                if (btnCamera != null) {
                    btnCamera.setText("🚫");
                    btnCamera.getStyleClass().add("camera-off");
                }
            });
            startPlaceholderVideo();
        }
    }
    
    /**
     * Hiển thị webcam thật bằng ImageView.
     * @param forceRecreate Nếu true, tạo lại ImageView ngay cả khi đã tồn tại
     */
    private void startWebcamDisplay(boolean forceRecreate) {
        if (webcam == null || !webcam.isOpen()) {
            System.err.println("[VideoCallController] Webcam is not open");
            return;
        }
        
        // Nếu ImageView đã tồn tại và không force recreate, chỉ restart thread
        if (localVideoImageView != null && !forceRecreate) {
            System.out.println("[VideoCallController] ImageView already exists, restarting webcam capture thread");
            // Dừng thread cũ nếu có
            if (webcamThread != null && webcamThread.isAlive()) {
                webcamThread.interrupt();
                try {
                    webcamThread.join(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            // Start lại capture thread
            startWebcamCaptureThread();
            return;
        }
        
        // Sử dụng CountDownLatch để đợi ImageView được tạo
        CountDownLatch imageViewReady = new CountDownLatch(1);
        
        Platform.runLater(() -> {
            // Ưu tiên sử dụng localVideoStack nếu có
            StackPane stackPane = localVideoStack;
            if (stackPane == null) {
                javafx.scene.Node parent = localVideo.getParent();
                if (parent instanceof StackPane) {
                    stackPane = (StackPane) parent;
                }
            }
            
            if (stackPane != null) {
                // Tạo ImageView mới hoặc reuse existing
                if (localVideoImageView == null || forceRecreate) {
                    if (localVideoImageView != null) {
                        // Xóa ImageView cũ
                        stackPane.getChildren().remove(localVideoImageView);
                    }
                    localVideoImageView = new ImageView();
                    localVideoImageView.setPreserveRatio(true);
                    localVideoImageView.setSmooth(true);
                    localVideoImageView.setCache(false); // Tắt cache để đảm bảo update real-time
                    
                    // Kích thước cố định cho local video (nhỏ ở góc)
                    localVideoImageView.setFitWidth(200);
                    localVideoImageView.setFitHeight(150);
                    System.out.println("[VideoCallController] ImageView size: 200x150 (fixed for local video)");
                    
                    // Đảm bảo ImageView hiển thị
                    localVideoImageView.setVisible(true);
                    localVideoImageView.setManaged(true);
                    
                    // Ẩn Region và thêm ImageView
                    localVideo.setVisible(false);
                    localVideo.setManaged(false);
                    stackPane.getChildren().add(0, localVideoImageView);
                    System.out.println("[VideoCallController] Added ImageView to StackPane for webcam");
                    System.out.println("[VideoCallController] StackPane children count: " + stackPane.getChildren().size());
                } else {
                    // Reuse existing ImageView
                    localVideoImageView.setVisible(true);
                    localVideoImageView.setManaged(true);
                }
                imageViewReady.countDown(); // Báo hiệu ImageView đã sẵn sàng
            } else {
                System.err.println("[VideoCallController] Cannot add ImageView, parent is not StackPane");
                startPlaceholderVideo();
                imageViewReady.countDown(); // Vẫn countDown để thread không bị block
                return;
            }
        });
        
        // Đợi ImageView được tạo xong (tối đa 5 giây)
        try {
            boolean ready = imageViewReady.await(5, TimeUnit.SECONDS);
            if (!ready) {
                System.err.println("[VideoCallController] Timeout waiting for ImageView to be created (5s)");
                System.err.println("[VideoCallController] Will retry in capture thread");
            } else {
                System.out.println("[VideoCallController] ImageView is ready!");
            }
        } catch (InterruptedException e) {
            System.err.println("[VideoCallController] Interrupted while waiting for ImageView");
        }
        
        // Bắt đầu capture frame từ webcam (sau khi ImageView đã sẵn sàng)
        startWebcamCaptureThread();
    }
    
    /**
     * Overload method - mặc định không force recreate
     */
    private void startWebcamDisplay() {
        startWebcamDisplay(false);
    }
    
    /**
     * Bắt đầu thread capture frames từ webcam.
     */
    private void startWebcamCaptureThread() {
        if (webcam == null || !webcam.isOpen()) {
            System.err.println("[VideoCallController] Cannot start capture thread - webcam is not open");
            return;
        }
        
        // Dừng thread cũ nếu có
        if (webcamThread != null && webcamThread.isAlive()) {
            webcamThread.interrupt();
            try {
                webcamThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // Bắt đầu capture frame từ webcam
        webcamThread = new Thread(() -> {
            System.out.println("[VideoCallController] Starting webcam capture thread");
            System.out.println("[VideoCallController] isInCall=" + isInCall + ", webcam.isOpen()=" + (webcam != null && webcam.isOpen()) + ", imageView=" + (localVideoImageView != null));
            
            // Đợi ImageView được tạo nếu chưa sẵn sàng (retry mechanism)
            int retryCount = 0;
            while (localVideoImageView == null && retryCount < 50 && isInCall) {
                try {
                    Thread.sleep(100); // Đợi 100ms mỗi lần
                    retryCount++;
                } catch (InterruptedException e) {
                    break;
                }
            }
            
            if (localVideoImageView == null) {
                System.err.println("[VideoCallController] ImageView still not ready after " + (retryCount * 100) + "ms, cannot start capture");
                return;
            }
            
            System.out.println("[VideoCallController] ImageView ready, starting capture loop");
            int frameCount = 0;
            int nullFrameCount = 0; // Đếm số lần liên tiếp webcam trả về null
            while (isInCall && !webcamCaptureStopping && localVideoImageView != null) {
                try {
                    // Kiểm tra flag dừng trước mỗi iteration
                    if (webcamCaptureStopping) {
                        System.out.println("[VideoCallController] Webcam capture stopping flag detected");
                        break;
                    }
                    
                    if (!isCameraOn) {
                        Thread.sleep(100);
                        continue;
                    }
                    
                    // Lấy reference local để tránh null pointer khi webcam bị close giữa chừng
                    com.github.sarxos.webcam.Webcam localWebcam = webcam;
                    if (localWebcam == null || !localWebcam.isOpen()) {
                        System.out.println("[VideoCallController] Webcam is not available, stopping capture");
                        break;
                    }
                    
                    BufferedImage image = localWebcam.getImage();
                    if (image != null) {
                        // Convert BufferedImage sang JavaFX Image
                        javafx.scene.image.Image fxImage = SwingFXUtils.toFXImage(image, null);
                        
                        Platform.runLater(() -> {
                            if (localVideoImageView != null && isInCall && isCameraOn) {
                                localVideoImageView.setImage(fxImage);
                                // Đảm bảo ImageView luôn visible
                                if (!localVideoImageView.isVisible()) {
                                    localVideoImageView.setVisible(true);
                                }
                            }
                        });
                        
                        frameCount++;
                        nullFrameCount = 0; // Reset null frame counter
                        if (frameCount % 30 == 0) {
                            System.out.println("[VideoCallController] Captured " + frameCount + " frames from webcam");
                        }
                    } else {
                        // Nếu getImage() trả về null liên tục, có thể webcam bị lock
                        nullFrameCount++;
                        
                        if (frameCount == 0) {
                            // Chưa capture được frame nào - webcam có thể bị lock bởi app khác
                            if (nullFrameCount == 1 || nullFrameCount % 50 == 0) {
                                System.out.println("[VideoCallController] Warning: webcam.getImage() returned null - webcam may be locked or unavailable (attempt " + nullFrameCount + ")");
                            }
                            
                            // Sau 100 lần thử (khoảng 10 giây), hiển thị thông báo cho user
                            if (nullFrameCount == 100) {
                                System.out.println("[VideoCallController] Webcam appears to be locked by another application");
                                Platform.runLater(() -> {
                                    if (localVideoImageView != null) {
                                        // Hiển thị thông báo webcam bị lock
                                        WritableImage lockedImage = new WritableImage(200, 150);
                                        Canvas tempCanvas = new Canvas(200, 150);
                                        GraphicsContext gc = tempCanvas.getGraphicsContext2D();
                                        gc.setFill(Color.rgb(50, 50, 50));
                                        gc.fillRect(0, 0, 200, 150);
                                        gc.setFill(Color.ORANGE);
                                        gc.setFont(javafx.scene.text.Font.font(11));
                                        gc.fillText("⚠ Webcam đang được", 30, 60);
                                        gc.fillText("sử dụng bởi app khác", 25, 80);
                                        gc.setFill(Color.GRAY);
                                        gc.setFont(javafx.scene.text.Font.font(9));
                                        gc.fillText("(Test trên 1 máy)", 55, 110);
                                        tempCanvas.snapshot(null, lockedImage);
                                        localVideoImageView.setImage(lockedImage);
                                    }
                                });
                            }
                            
                            // Đợi một chút rồi thử lại
                            Thread.sleep(100);
                        } else {
                            // Nếu đã capture được frames trước đó nhưng giờ null, có thể webcam bị release
                            System.out.println("[VideoCallController] Webcam.getImage() returned null after " + frameCount + " frames - stopping capture");
                            break;
                        }
                    }
                    Thread.sleep(33); // ~30 FPS
                } catch (Exception e) {
                    if (isInCall) {
                        System.err.println("[VideoCallController] Error capturing webcam frame: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
            System.out.println("[VideoCallController] Webcam capture thread ended (total frames: " + frameCount + ")");
        }, "Webcam-Capture");
        webcamThread.setDaemon(true);
        webcamThread.start();
    }
    
    /**
     * Hiển thị placeholder video (khi không có webcam hoặc webcam lỗi).
     */
    private void startPlaceholderVideo() {
        System.out.println("[VideoCallController] Starting placeholder video");
        
        // Tạo Canvas để vẽ video
        localVideoCanvas = new Canvas();
        
        // Kích thước cố định cho local video (nhỏ ở góc)
        localVideoCanvas.setWidth(200);
        localVideoCanvas.setHeight(150);
        System.out.println("[VideoCallController] Canvas size: 200x150 (fixed for local video)");
        
        final GraphicsContext gc = localVideoCanvas.getGraphicsContext2D();
        
        // Thêm Canvas vào UI
        Platform.runLater(() -> {
            // Ưu tiên sử dụng localVideoStack nếu có
            StackPane stackPane = localVideoStack;
            if (stackPane == null) {
                javafx.scene.Node parent = localVideo.getParent();
                if (parent instanceof StackPane) {
                    stackPane = (StackPane) parent;
                }
            }
            
            if (stackPane != null) {
                // Ẩn Region
                localVideo.setVisible(false);
                localVideo.setManaged(false);
                // Thêm Canvas vào đầu danh sách
                stackPane.getChildren().add(0, localVideoCanvas);
                System.out.println("[VideoCallController] Added canvas to StackPane");
            } else {
                javafx.scene.Node parent = localVideo.getParent();
                if (parent instanceof VBox) {
                    VBox vbox = (VBox) parent;
                    localVideo.setVisible(false);
                    localVideo.setManaged(false);
                    vbox.getChildren().add(0, localVideoCanvas);
                    System.out.println("[VideoCallController] Added canvas to VBox");
                }
            }
            
            // Vẽ ngay lập tức
            double width = localVideoCanvas.getWidth();
            double height = localVideoCanvas.getHeight();
            if (width > 0 && height > 0) {
                gc.setFill(Color.rgb(30, 30, 50));
                gc.fillRect(0, 0, width, height);
                gc.setFill(Color.WHITE);
                gc.fillText("Local Camera (Placeholder)", 10, 20);
            }
        });
        
        // Vẽ placeholder pattern
        new Thread(() -> {
            final int[] frameCount = {0};
            while (isInCall && localVideoCanvas != null) {
                try {
                    if (!isCameraOn) {
                        Thread.sleep(100);
                        continue;
                    }
                    
                    final int currentFrame = frameCount[0];
                    Platform.runLater(() -> {
                        if (localVideoCanvas == null || !isInCall || !isCameraOn) {
                            return;
                        }
                        
                        double width = localVideoCanvas.getWidth();
                        double height = localVideoCanvas.getHeight();
                        
                        if (width <= 0 || height <= 0) {
                            return;
                        }
                        
                        // Clear canvas
                        gc.clearRect(0, 0, width, height);
                        
                        // Background
                        gc.setFill(Color.rgb(30, 30, 50));
                        gc.fillRect(0, 0, width, height);
                        
                        // Vẽ pattern động
                        gc.setFill(Color.rgb(100, 100, 150));
                        int gridSize = 15;
                        for (int i = 0; i < gridSize; i++) {
                            for (int j = 0; j < gridSize; j++) {
                                double x = (i * width / gridSize) + (currentFrame % 40) * 1.5;
                                double y = (j * height / gridSize) + (currentFrame % 40) * 1.5;
                                double size = 8 + (currentFrame % 10);
                                gc.fillOval(x % width, y % height, size, size);
                            }
                        }
                        
                        // Hiển thị text
                        gc.setFill(Color.WHITE);
                        gc.setFont(javafx.scene.text.Font.font(14));
                        gc.fillText("Local Camera (Placeholder)", 10, 25);
                        gc.setFont(javafx.scene.text.Font.font(10));
                        gc.setFill(Color.rgb(200, 200, 200));
                        gc.fillText("Frame: " + currentFrame, 10, height - 10);
                    });
                    
                    frameCount[0]++;
                    Thread.sleep(50); // ~20 FPS
                } catch (Exception e) {
                    if (isInCall) {
                        System.err.println("[VideoCallController] Error drawing placeholder: " + e.getMessage());
                    }
                }
            }
            System.out.println("[VideoCallController] Placeholder video thread ended");
        }, "Placeholder-Video").start();
    }
    
    /**
     * Hiển thị remote video frame.
     */
    private void displayRemoteVideo(byte[] frameData) {
        if (remoteVideo == null) {
            return;
        }
        
        // Kiểm tra nếu frame rỗng hoặc quá nhỏ (có thể là signal camera tắt)
        if (frameData == null || frameData.length < 100) {
            // Frame quá nhỏ - không update status ngay, để monitoring thread quyết định
            return;
        }
        
        // Update lastRemoteFrameTime ngay khi nhận được frame hợp lệ
        lastRemoteFrameTime = System.currentTimeMillis();
        
        System.out.println("[VideoCallController] Displaying REMOTE video frame from " + otherUserName + ": " + frameData.length + " bytes");
        
        try {
            // Decode JPEG bytes thành BufferedImage
            java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(frameData);
            BufferedImage bufferedImage = ImageIO.read(bais);
            
            if (bufferedImage == null) {
                System.err.println("[VideoCallController] Failed to decode remote video frame");
                return;
            }
            
            // Update remote camera status - camera đang bật vì nhận được frame hợp lệ
            if (!remoteCameraOn) {
                remoteCameraOn = true;
                System.out.println("[VideoCallController] Remote camera is ON - received valid frame");
                Platform.runLater(() -> {
                    showRemoteCameraStatus(true);
                });
            }
            
            // Convert BufferedImage sang JavaFX Image
            javafx.scene.image.Image fxImage = SwingFXUtils.toFXImage(bufferedImage, null);
            
            Platform.runLater(() -> {
                // Tạo ImageView nếu chưa có
                if (remoteVideoImageView == null) {
                    // Ưu tiên sử dụng remoteVideoStack nếu có
                    StackPane stackPane = remoteVideoStack;
                    if (stackPane == null) {
                        javafx.scene.Node parent = remoteVideo.getParent();
                        if (parent instanceof StackPane) {
                            stackPane = (StackPane) parent;
                        }
                    }
                    
                    if (stackPane != null) {
                        remoteVideoImageView = new ImageView();
                        remoteVideoImageView.setPreserveRatio(true);
                        remoteVideoImageView.setSmooth(true);
                        remoteVideoImageView.setCache(false);
                        
                        // Bind kích thước theo stackPane để full size
                        remoteVideoImageView.fitWidthProperty().bind(stackPane.widthProperty().subtract(20));
                        remoteVideoImageView.fitHeightProperty().bind(stackPane.heightProperty().subtract(20));
                        
                        // Ẩn Region và Canvas (nếu có), thêm ImageView
                        remoteVideo.setVisible(false);
                        remoteVideo.setManaged(false);
                        if (remoteVideoCanvas != null) {
                            remoteVideoCanvas.setVisible(false);
                            remoteVideoCanvas.setManaged(false);
                        }
                        // Ẩn overlay khi có video
                        if (callStatusOverlay != null) {
                            callStatusOverlay.setVisible(false);
                        }
                        stackPane.getChildren().add(0, remoteVideoImageView);
                        System.out.println("[VideoCallController] Created remote video ImageView");
                    }
                }
                
                // Hiển thị frame
                if (remoteVideoImageView != null) {
                    remoteVideoImageView.setImage(fxImage);
                    remoteVideoImageView.setVisible(true);
                    // Đảm bảo overlay ẩn khi có video
                    if (callStatusOverlay != null) {
                        callStatusOverlay.setVisible(false);
                    }
                }
            });
        } catch (Exception e) {
            System.err.println("[VideoCallController] Error displaying remote video: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Hiển thị trạng thái camera của remote user.
     */
    private void showRemoteCameraStatus(boolean cameraOn) {
        if (cameraOn) {
            // Camera đã bật lại - ẩn thông báo trên remote video
            Platform.runLater(() -> {
                if (remoteVideoCanvas != null) {
                    remoteVideoCanvas.setVisible(false);
                }
                // Cập nhật status label
                if (lblCallStatus != null) {
                    String currentText = lblCallStatus.getText();
                    if (currentText.contains("đang tắt cam")) {
                        lblCallStatus.setText("Đã kết nối với " + otherUserName);
                    }
                }
            });
        } else {
            // Camera đã tắt - hiển thị thông báo trên remote video area
            Platform.runLater(() -> {
                // Hiển thị thông báo trên remote video area
                showRemoteCameraOffMessage();
                // Cập nhật status label
                if (lblCallStatus != null) {
                    lblCallStatus.setText(otherUserName + " đang tắt cam");
                }
            });
        }
    }
    
    /**
     * Hiển thị thông báo "Camera đã tắt" trên remote video area.
     */
    private void showRemoteCameraOffMessage() {
        if (remoteVideo == null) {
            return;
        }
        
        // Tạo canvas để hiển thị thông báo nếu chưa có
        if (remoteVideoCanvas == null) {
            javafx.scene.Node parent = remoteVideo.getParent();
            if (parent instanceof StackPane) {
                StackPane stackPane = (StackPane) parent;
                remoteVideoCanvas = new Canvas();
                if (remoteVideo instanceof javafx.scene.layout.Region) {
                    javafx.scene.layout.Region region = (javafx.scene.layout.Region) remoteVideo;
                    remoteVideoCanvas.widthProperty().bind(region.widthProperty());
                    remoteVideoCanvas.heightProperty().bind(region.heightProperty());
                }
                remoteVideo.setVisible(false);
                remoteVideo.setManaged(false);
                stackPane.getChildren().add(0, remoteVideoCanvas);
            }
        }
        
        if (remoteVideoCanvas != null) {
            remoteVideoCanvas.setVisible(true);
            remoteVideoCanvas.setManaged(true);
            
            // Ẩn ImageView nếu có
            if (remoteVideoImageView != null) {
                remoteVideoImageView.setVisible(false);
            }
            
            // Vẽ thông báo
            GraphicsContext gc = remoteVideoCanvas.getGraphicsContext2D();
            double width = remoteVideoCanvas.getWidth();
            double height = remoteVideoCanvas.getHeight();
            
            if (width > 0 && height > 0) {
                // Background đen
                gc.setFill(Color.BLACK);
                gc.fillRect(0, 0, width, height);
                
                // Text thông báo
                gc.setFill(Color.WHITE);
                gc.setFont(javafx.scene.text.Font.font(16));
                String message = otherUserName + " đang tắt cam";
                // Tính toán vị trí text (ước lượng)
                double textWidth = message.length() * 10; // Ước lượng ~10 pixels mỗi ký tự
                gc.fillText(message, (width - textWidth) / 2, height / 2);
            }
        }
    }
    
    private void startSendingFrames() {
        // Gửi video thật từ webcam qua UDP
        new Thread(() -> {
            int frameCount = 0;
            int senderNullCount = 0; // Đếm số lần webcam.getImage() trả về null
            System.out.println("[VideoCallController] Starting video sender thread");
            System.out.println("[VideoCallController] isCameraOn=" + isCameraOn + ", webcam=" + (webcam != null) + ", webcam.isOpen()=" + (webcam != null && webcam.isOpen()) + ", videoStreamClient=" + (videoStreamClient != null));
            
            while (isInCall && videoStreamClient != null) {
                try {
                    if (!isCameraOn) {
                        // Camera tắt - không gửi frame
                        Thread.sleep(100);
                        continue;
                    }
                    
                    // Kiểm tra webcam
                    if (webcam == null || !webcam.isOpen()) {
                        if (frameCount == 0) {
                            System.out.println("[VideoCallController] Webcam not available, waiting...");
                        }
                        Thread.sleep(500);
                        continue;
                    }
                    
                    // Kiểm tra webcam có available không
                    if (webcam == null || !webcam.isOpen()) {
                        // Webcam không available - không gửi frame
                        Thread.sleep(100);
                        continue;
                    }
                    
                    // Capture frame từ webcam
                    BufferedImage image = webcam.getImage();
                    if (image != null) {
                        // Encode BufferedImage thành JPEG bytes
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ImageIO.write(image, "jpg", baos);
                        byte[] frameData = baos.toByteArray();
                        
                        // Gửi frame qua UDP (LOCAL frame - gửi đi)
                        videoStreamClient.sendFrame(frameData);
                        
                        frameCount++;
                        senderNullCount = 0; // Reset null counter khi gửi thành công
                        if (frameCount == 1 || frameCount % 30 == 0) {
                            System.out.println("[VideoCallController] ✓ Sent LOCAL frame #" + frameCount + " (" + frameData.length + " bytes) to remote user");
                        }
                    } else {
                        // Nếu getImage() trả về null, có thể webcam bị lock hoặc không available
                        senderNullCount++;
                        if (senderNullCount == 1 || senderNullCount % 100 == 0) {
                            // Log warning chỉ 1 lần đầu và mỗi 100 lần sau đó
                            System.out.println("[VideoCallController] Warning: webcam.getImage() returned null for video sender (count: " + senderNullCount + ")");
                        }
                        // Không spam log - chỉ đợi và thử lại
                        Thread.sleep(100);
                    }
                    Thread.sleep(33); // ~30 FPS
                } catch (Exception e) {
                    if (isInCall) {
                        System.err.println("[VideoCallController] Error sending frame: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
            System.out.println("[VideoCallController] Video sender thread ended (total frames sent: " + frameCount + ")");
        }, "Video-Sender").start();
    }
    
    // Flag để báo hiệu thread capture nên dừng
    private volatile boolean webcamCaptureStopping = false;
    
    /**
     * Đóng webcam hoàn toàn để release quyền (dùng khi tắt camera).
     */
    private void stopWebcamCapture() {
        System.out.println("[VideoCallController] Stopping webcam capture and releasing webcam...");
        
        // Đánh dấu là đang dừng để thread capture biết mà thoát
        webcamCaptureStopping = true;
        
        // Dừng webcam thread trước
        Thread threadToStop = webcamThread;
        webcamThread = null; // Clear reference trước
        
        if (threadToStop != null && threadToStop.isAlive()) {
            threadToStop.interrupt();
            try {
                threadToStop.join(2000); // Đợi tối đa 2 giây
            } catch (InterruptedException e) {
                // Ignore
            }
        }
        
        // Sau khi thread đã dừng, mới đóng webcam
        com.github.sarxos.webcam.Webcam webcamToClose = webcam;
        webcam = null; // Clear reference trước khi đóng
        
        if (webcamToClose != null) {
            try {
                if (webcamToClose.isOpen()) {
                    webcamToClose.close();
                    System.out.println("[VideoCallController] ✓ Webcam closed and released");
                }
            } catch (Exception e) {
                System.err.println("[VideoCallController] Error closing webcam: " + e.getMessage());
            }
        }
        
        // Reset flag
        webcamCaptureStopping = false;
    }
    
    /**
     * Khởi tạo và bắt đầu audio streaming (microphone + speaker).
     * Audio sử dụng chung UDP channel với video, phân biệt bằng prefix trong payload.
     */
    private void startAudioStreaming() {
        try {
            // Audio format: 16-bit PCM, 16kHz, mono (phù hợp cho voice)
            audioFormat = new AudioFormat(16000, 16, 1, true, false);
            
            audioRunning = true;
            
            // Bắt đầu capture và gửi audio từ microphone
            startMicrophoneCapture();
            
            // Bắt đầu nhận và phát audio từ remote
            startAudioPlayback();
            
            System.out.println("[VideoCallController] Audio streaming initialized");
            
        } catch (Exception e) {
            System.err.println("[VideoCallController] Error starting audio streaming: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Capture audio từ microphone và gửi qua UDP (dùng chung video stream).
     */
    private void startMicrophoneCapture() {
        audioSendThread = new Thread(() -> {
            try {
                DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, audioFormat);
                
                if (!AudioSystem.isLineSupported(micInfo)) {
                    System.err.println("[VideoCallController] Microphone not supported on this system!");
                    Platform.runLater(() -> {
                        if (btnMute != null) {
                            btnMute.setText("⚠️");
                            btnMute.setDisable(true);
                        }
                    });
                    return;
                }
                
                microphone = (TargetDataLine) AudioSystem.getLine(micInfo);
                microphone.open(audioFormat);
                microphone.start();
                
                System.out.println("[VideoCallController] ✓ Microphone started - ready for voice chat");
                
                // Buffer cho audio data (20ms của audio = 640 bytes ở 16kHz, 16-bit, mono)
                byte[] buffer = new byte[640];
                int packetCount = 0;
                
                while (audioRunning && microphone != null && microphone.isOpen()) {
                    if (isMuted) {
                        // Nếu mute, đọc và bỏ đi data từ mic để tránh buffer overflow
                        microphone.read(buffer, 0, buffer.length);
                        Thread.sleep(5);
                        continue;
                    }
                    
                    int bytesRead = microphone.read(buffer, 0, buffer.length);
                    if (bytesRead > 0 && videoStreamClient != null) {
                        // Tính audio level để debug (check xem mic có capture được sound không)
                        int maxAmplitude = 0;
                        for (int i = 0; i < bytesRead; i += 2) {
                            if (i + 1 < bytesRead) {
                                // 16-bit signed little-endian
                                int sample = (buffer[i] & 0xFF) | (buffer[i + 1] << 8);
                                maxAmplitude = Math.max(maxAmplitude, Math.abs(sample));
                            }
                        }
                        
                        // Thêm prefix "AUDIO:" để server/client phân biệt với video
                        byte[] audioPacket = new byte[bytesRead + 6];
                        audioPacket[0] = 'A';
                        audioPacket[1] = 'U';
                        audioPacket[2] = 'D';
                        audioPacket[3] = 'I';
                        audioPacket[4] = 'O';
                        audioPacket[5] = ':';
                        System.arraycopy(buffer, 0, audioPacket, 6, bytesRead);
                        
                        // Gửi qua cùng UDP channel với video
                        videoStreamClient.sendFrame(audioPacket);
                        
                        packetCount++;
                        if (packetCount % 100 == 0) {
                            // Log audio level để biết mic có hoạt động không
                            System.out.println("[VideoCallController] Sent " + packetCount + " audio packets, level: " + maxAmplitude + "/32768");
                        }
                    }
                }
            } catch (LineUnavailableException e) {
                System.err.println("[VideoCallController] Microphone unavailable: " + e.getMessage());
                Platform.runLater(() -> {
                    if (btnMute != null) {
                        btnMute.setText("⚠️");
                        btnMute.setDisable(true);
                    }
                });
            } catch (Exception e) {
                if (audioRunning) {
                    System.err.println("[VideoCallController] Error in microphone capture: " + e.getMessage());
                    e.printStackTrace();
                }
            } finally {
                if (microphone != null) {
                    try {
                        microphone.stop();
                        microphone.close();
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
        }, "Audio-Send");
        audioSendThread.setDaemon(true);
        audioSendThread.start();
    }
    
    /**
     * Phát audio nhận được từ remote.
     * Audio packets được nhận qua video stream callback và xử lý riêng.
     */
    private void startAudioPlayback() {
        audioReceiveThread = new Thread(() -> {
            try {
                DataLine.Info speakerInfo = new DataLine.Info(SourceDataLine.class, audioFormat);
                
                if (!AudioSystem.isLineSupported(speakerInfo)) {
                    System.err.println("[VideoCallController] Speakers not supported on this system!");
                    return;
                }
                
                speakers = (SourceDataLine) AudioSystem.getLine(speakerInfo);
                speakers.open(audioFormat);
                speakers.start();
                
                System.out.println("[VideoCallController] ✓ Speakers started - ready to receive audio");
                
                // Keep thread alive để speakers không bị đóng
                while (audioRunning) {
                    Thread.sleep(100);
                }
            } catch (LineUnavailableException e) {
                System.err.println("[VideoCallController] Speakers unavailable: " + e.getMessage());
            } catch (Exception e) {
                if (audioRunning) {
                    System.err.println("[VideoCallController] Error in audio playback setup: " + e.getMessage());
                    e.printStackTrace();
                }
            } finally {
                if (speakers != null) {
                    try {
                        speakers.drain();
                        speakers.stop();
                        speakers.close();
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
        }, "Audio-Receive");
        audioReceiveThread.setDaemon(true);
        audioReceiveThread.start();
    }
    
    /**
     * Xử lý audio data nhận được từ remote (được gọi từ video receive callback).
     */
    private int audioPlayCount = 0;
    private void playRemoteAudio(byte[] audioData) {
        if (speakers != null && speakers.isOpen() && audioData != null && audioData.length > 0) {
            try {
                speakers.write(audioData, 0, audioData.length);
                audioPlayCount++;
                // Log mỗi 50 packets để debug
                if (audioPlayCount % 50 == 0) {
                    System.out.println("[VideoCallController] ✓ Played " + audioPlayCount + " audio packets, latest: " + audioData.length + " bytes");
                }
            } catch (Exception e) {
                System.err.println("[VideoCallController] Error playing audio: " + e.getMessage());
            }
        } else {
            // Debug khi không thể phát audio
            if (speakers == null) {
                System.err.println("[VideoCallController] ✗ Cannot play audio: speakers is null");
            } else if (!speakers.isOpen()) {
                System.err.println("[VideoCallController] ✗ Cannot play audio: speakers not open");
            }
        }
    }
    
    /**
     * Dừng audio streaming.
     */
    private void stopAudioStreaming() {
        audioRunning = false;
        
        // Dừng microphone
        if (microphone != null) {
            try {
                microphone.stop();
                microphone.close();
            } catch (Exception e) {
                // Ignore
            }
            microphone = null;
        }
        
        // Dừng speakers
        if (speakers != null) {
            try {
                speakers.stop();
                speakers.close();
            } catch (Exception e) {
                // Ignore
            }
            speakers = null;
        }
        
        // Dừng threads
        if (audioSendThread != null) {
            audioSendThread.interrupt();
            audioSendThread = null;
        }
        if (audioReceiveThread != null) {
            audioReceiveThread.interrupt();
            audioReceiveThread = null;
        }
        
        System.out.println("[VideoCallController] Audio streaming stopped");
    }
    
    private void stopVideoStreaming() {
        isInCall = false;
        if (videoStreamClient != null) {
            videoStreamClient.stop();
            videoStreamClient = null;
        }
        
        // Dừng audio streaming
        stopAudioStreaming();
        
        // Đóng webcam nếu đang mở
        stopWebcamCapture();
        
        // Xóa canvas và imageview
        localVideoCanvas = null;
        remoteVideoCanvas = null;
        localVideoImageView = null;
        remoteVideoImageView = null;
    }
    
    private void startCallStatusPolling() {
        // Polling để kiểm tra trạng thái cuộc gọi (chấp nhận, từ chối, kết thúc)
        new Thread(() -> {
            boolean streamingStarted = false; // Track xem đã start streaming chưa
            System.out.println("[VideoCallController] Starting call status polling (sessionId=" + currentCallSessionId + ", isCaller=" + isCaller + ")");
            
            // Đợi một chút để đảm bảo sessionId đã được set
            int waitCount = 0;
            while (currentCallSessionId == null && waitCount < 10) {
                try {
                    Thread.sleep(100);
                    waitCount++;
                } catch (InterruptedException e) {
                    break;
                }
            }
            
            if (currentCallSessionId == null) {
                System.err.println("[VideoCallController] ERROR: Cannot start polling - currentCallSessionId is null!");
                return;
            }
            
            while (currentCallSessionId != null) {
                try {
                    Thread.sleep(1000); // Poll mỗi giây
                    
                    CallService.CallInfo callInfo = clientService.getCallInfo(currentCallSessionId);
                    if (callInfo == null) {
                        // Cuộc gọi đã bị hủy
                        System.out.println("[VideoCallController] Call info is null, ending polling");
                        Platform.runLater(() -> {
                            lblCallStatus.setText("Cuộc gọi đã kết thúc");
                            if (onCloseCallback != null) {
                                onCloseCallback.run();
                            }
                        });
                        break;
                    }
                    
                    System.out.println("[VideoCallController] Call status: " + callInfo.status + ", streamingStarted: " + streamingStarted + ", isCaller: " + isCaller);
                    
                    if ("ACTIVE".equals(callInfo.status)) {
                        if (!streamingStarted) {
                            // Cuộc gọi đã được chấp nhận - bắt đầu streaming
                            streamingStarted = true;
                            System.out.println("[VideoCallController] ✓ Call accepted (ACTIVE), starting video streaming (isCaller=" + isCaller + ")");
                            
                            if (isCaller) {
                                // Caller: start streaming khi callee accept
                                System.out.println("[VideoCallController] Caller: Starting video streaming now...");
                                Platform.runLater(() -> {
                                    isInCall = true;
                                    startVideoStreaming();
                                    lblCallStatus.setText("Đã kết nối với " + otherUserName);
                                    System.out.println("[VideoCallController] Caller: Status updated to 'Đã kết nối với " + otherUserName + "'");
                                });
                            } else {
                                // Callee: start streaming khi accept
                                Platform.runLater(() -> {
                                    isInCall = true;
                                    startVideoStreaming();
                                    lblCallStatus.setText("Đã kết nối với " + otherUserName);
                                    if (btnJoin != null) {
                                        btnJoin.setVisible(false);
                                        btnJoin.setManaged(false);
                                    }
                                    if (btnLeave != null) {
                                        btnLeave.setVisible(true);
                                    }
                                    if (btnMute != null) {
                                        btnMute.setVisible(true);
                                    }
                                });
                            }
                        }
                        // Tiếp tục polling để theo dõi trạng thái
                    } else if ("ENDED".equals(callInfo.status)) {
                        Platform.runLater(() -> {
                            lblCallStatus.setText("Cuộc gọi đã kết thúc");
                            if (onCloseCallback != null) {
                                onCloseCallback.run();
                            }
                        });
                        break;
                    }
                } catch (Exception e) {
                    System.err.println("[VideoCallController] Error polling call status: " + e.getMessage());
                }
            }
        }, "Call-Status-Polling").start();
    }
    
    @FXML
    private void handleBackToChat() {
        // Kết thúc cuộc gọi nếu đang trong cuộc gọi
        if (isInCall && currentCallSessionId != null) {
            handleLeaveCall();
        }
        
        // Đóng window
        if (onCloseCallback != null) {
            onCloseCallback.run();
        } else {
            // Lấy stage và đóng
            if (lblCallStatus != null) {
                javafx.stage.Stage stage = (javafx.stage.Stage) lblCallStatus.getScene().getWindow();
                if (stage != null) {
                    stage.close();
                }
            }
        }
    }
    
    /**
     * Monitor remote camera status - nếu không nhận được frame trong 2 giây, coi như camera tắt.
     */
    private void startRemoteCameraMonitoring() {
        new Thread(() -> {
            // Khởi tạo lastRemoteFrameTime khi bắt đầu monitoring
            lastRemoteFrameTime = System.currentTimeMillis();
            
            while (isInCall) {
                try {
                    Thread.sleep(2000); // Check mỗi 2 giây
                    
                    // Chỉ check nếu đã nhận được ít nhất 1 frame trước đó
                    if (lastRemoteFrameTime > 0) {
                        long timeSinceLastFrame = System.currentTimeMillis() - lastRemoteFrameTime;
                        if (timeSinceLastFrame > 2000 && remoteCameraOn) {
                            // Không nhận được frame trong 2 giây - có thể camera đã tắt
                            System.out.println("[VideoCallController] No remote frames for " + timeSinceLastFrame + "ms - showing camera off");
                            remoteCameraOn = false;
                            Platform.runLater(() -> {
                                showRemoteCameraStatus(false);
                            });
                        }
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "Remote-Camera-Monitor").start();
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
}

