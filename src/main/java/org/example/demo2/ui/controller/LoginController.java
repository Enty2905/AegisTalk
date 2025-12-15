package org.example.demo2.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.example.demo2.Session;
import org.example.demo2.client.AegisTalkClientService;
import org.example.demo2.model.User;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;

/**
 * Login Controller sử dụng RMI AuthService.
 * 
 * Áp dụng: RMI (Remote Method Invocation) - Bài RMI
 * - Gọi method từ xa (register/login) qua RMI
 * - Trả về đối tượng User qua network
 */
public class LoginController {
    
    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;
    @FXML private TextField txtDisplayName; // Cho đăng ký
    @FXML private VBox displayNameBox; // Container cho display name field
    @FXML private Label lblStatus;
    @FXML private Button btnLogin;
    @FXML private Button btnRegister;
    @FXML private ToggleButton toggleRegister;
    @FXML private CheckBox chkRememberMe;
    
    private AegisTalkClientService clientService;
    
    @FXML
    private void initialize() {
        // Kiểm tra các control có null không
        if (lblStatus == null || txtUsername == null || txtPassword == null) {
            System.err.println("[LoginController] Warning: Some FXML controls are null");
            return;
        }
        
        // Clear default status
        lblStatus.setText("");
        lblStatus.setVisible(false);
        lblStatus.setManaged(false);
        
        try {
            clientService = new AegisTalkClientService();
            showStatus("✓ Đã kết nối đến server", true);
        } catch (Exception e) {
            showStatus("✗ Lỗi kết nối: " + e.getMessage(), false);
            e.printStackTrace();
        }
        
        if (btnLogin != null) {
            btnLogin.setOnAction(e -> doLogin());
        }
        if (btnRegister != null) {
            btnRegister.setOnAction(e -> doRegister());
        }
        
        // Toggle hiển thị/ẩn trường display name
        if (toggleRegister != null) {
            toggleRegister.selectedProperty().addListener((obs, oldVal, newVal) -> {
                // Show/hide display name box
                if (displayNameBox != null) {
                    displayNameBox.setVisible(newVal);
                    displayNameBox.setManaged(newVal);
                } else if (txtDisplayName != null) {
                    // Fallback for old FXML structure
                    txtDisplayName.setVisible(newVal);
                    txtDisplayName.setManaged(newVal);
                }
                
                if (newVal) {
                    // Chế độ đăng ký
                    toggleRegister.setText("Đã có tài khoản? Đăng nhập");
                    if (btnRegister != null) {
                        btnRegister.setVisible(true);
                        btnRegister.setManaged(true);
                    }
                    if (btnLogin != null) {
                        btnLogin.setVisible(false);
                        btnLogin.setManaged(false);
                    }
                } else {
                    // Chế độ đăng nhập
                    toggleRegister.setText("Chưa có tài khoản? Đăng ký ngay");
                    if (btnRegister != null) {
                        btnRegister.setVisible(false);
                        btnRegister.setManaged(false);
                    }
                    if (btnLogin != null) {
                        btnLogin.setVisible(true);
                        btnLogin.setManaged(true);
                    }
                }
                
                // Clear status when switching modes
                lblStatus.setVisible(false);
                lblStatus.setManaged(false);
            });
        }
        
        // Enter key to login/register
        if (txtPassword != null) {
            txtPassword.setOnAction(e -> {
                if (toggleRegister != null && toggleRegister.isSelected()) {
                    doRegister();
                } else {
                    doLogin();
                }
            });
        }
    }
    
    private void showStatus(String message, boolean isSuccess) {
        if (lblStatus != null) {
            lblStatus.setText(message);
            lblStatus.setVisible(true);
            lblStatus.setManaged(true);
            
            // Update style based on success/failure
            lblStatus.getStyleClass().removeAll("success", "error");
            if (isSuccess) {
                lblStatus.getStyleClass().add("success");
                lblStatus.setStyle("-fx-text-fill: #22c55e; -fx-background-color: rgba(34, 197, 94, 0.1);");
            } else {
                lblStatus.setStyle("-fx-text-fill: #ef4444; -fx-background-color: rgba(239, 68, 68, 0.1);");
            }
        }
    }
    
    private void doLogin() {
        if (clientService == null) {
            showStatus("✗ Chưa kết nối đến server", false);
            return;
        }
        
        String username = txtUsername.getText().trim();
        String password = txtPassword.getText().trim();
        
        if (username.isEmpty() || password.isEmpty()) {
            showStatus("⚠ Vui lòng nhập đủ thông tin", false);
            return;
        }
        
        try {
            System.out.println("[LoginController] Login attempt: username=" + username + ", password length=" + password.length());
            
            // Gọi RMI AuthService.login()
            User user = clientService.login(username, password);
            
            if (user != null) {
                Session.setUser(user.id(), user.displayName());
                showStatus("✓ Đăng nhập thành công!", true);
                System.out.println("[LoginController] Login successful: user ID=" + user.id());
                
                // Chuyển sang MainChatView (giao diện Messenger)
                try {
                    javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                            getClass().getResource("/org/example/demo2/ui/view/MainChatView.fxml"));
                    javafx.scene.Scene scene = new javafx.scene.Scene(loader.load(), 1400, 900);
                    Stage stage = (Stage) btnLogin.getScene().getWindow();
                    stage.setScene(scene);
                    stage.setTitle("AegisTalk - " + user.displayName());
                    stage.centerOnScreen();
                } catch (java.io.IOException e) {
                    showStatus("✗ Lỗi tải giao diện: " + e.getMessage(), false);
                    e.printStackTrace();
                }
            } else {
                showStatus("✗ Sai tên đăng nhập hoặc mật khẩu", false);
                System.out.println("[LoginController] Login failed for username: " + username);
            }
        } catch (RemoteException e) {
            showStatus("✗ Lỗi kết nối: " + e.getMessage(), false);
            e.printStackTrace();
        }
    }
    
    private void doRegister() {
        if (clientService == null) {
            showStatus("✗ Chưa kết nối đến server", false);
            return;
        }
        
        String username = txtUsername.getText().trim();
        String password = txtPassword.getText().trim();
        String displayName = txtDisplayName != null ? txtDisplayName.getText().trim() : "";
        
        if (username.isEmpty() || password.isEmpty()) {
            showStatus("⚠ Vui lòng nhập đủ thông tin", false);
            return;
        }
        
        if (displayName.isEmpty()) {
            displayName = username; // Mặc định dùng username
        }
        
        try {
            System.out.println("[LoginController] Register attempt: username=" + username + ", password length=" + password.length() + ", displayName=" + displayName);
            
            // Gọi RMI AuthService.register()
            User user = clientService.register(username, password, displayName);
            
            if (user != null) {
                Session.setUser(user.id(), user.displayName());
                showStatus("✓ Đăng ký thành công!", true);
                System.out.println("[LoginController] Register successful: user ID=" + user.id());
                
                // Chuyển sang MainChatView (giao diện Messenger)
                try {
                    javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                            getClass().getResource("/org/example/demo2/ui/view/MainChatView.fxml"));
                    javafx.scene.Scene scene = new javafx.scene.Scene(loader.load(), 1400, 900);
                    Stage stage = (Stage) btnRegister.getScene().getWindow();
                    stage.setScene(scene);
                    stage.setTitle("AegisTalk - " + user.displayName());
                    stage.centerOnScreen();
                } catch (java.io.IOException e) {
                    showStatus("✗ Lỗi tải giao diện: " + e.getMessage(), false);
                    e.printStackTrace();
                }
            } else {
                showStatus("✗ Đăng ký thất bại (username đã tồn tại)", false);
                System.out.println("[LoginController] Register failed for username: " + username);
            }
        } catch (RemoteException e) {
            showStatus("✗ Lỗi kết nối: " + e.getMessage(), false);
            e.printStackTrace();
        }
    }
}
