package org.example.demo2.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
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
public class LoginControllerV2 {
    
    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;
    @FXML private TextField txtDisplayName; // Cho đăng ký
    @FXML private Label lblStatus;
    @FXML private Button btnLogin;
    @FXML private Button btnRegister;
    @FXML private ToggleButton toggleRegister;
    
    private AegisTalkClientService clientService;
    
    @FXML
    private void initialize() {
        try {
            clientService = new AegisTalkClientService();
            lblStatus.setText("Đã kết nối đến server");
        } catch (Exception e) {
            lblStatus.setText("Lỗi kết nối: " + e.getMessage());
            e.printStackTrace();
        }
        
        btnLogin.setOnAction(e -> doLogin());
        btnRegister.setOnAction(e -> doRegister());
        
        // Toggle hiển thị/ẩn trường display name
        toggleRegister.selectedProperty().addListener((obs, oldVal, newVal) -> {
            txtDisplayName.setVisible(newVal);
            if (newVal) {
                btnRegister.setVisible(true);
                btnLogin.setVisible(false);
            } else {
                btnRegister.setVisible(false);
                btnLogin.setVisible(true);
            }
        });
    }
    
    private void doLogin() {
        if (clientService == null) {
            lblStatus.setText("Chưa kết nối đến server");
            return;
        }
        
        String username = txtUsername.getText().trim();
        String password = txtPassword.getText().trim();
        
        if (username.isEmpty() || password.isEmpty()) {
            lblStatus.setText("Vui lòng nhập đủ thông tin");
            return;
        }
        
        try {
            // Gọi RMI AuthService.login()
            User user = clientService.login(username, password);
            
            if (user != null) {
                Session.setUser(user.id(), user.displayName());
                lblStatus.setText("Đăng nhập thành công: " + user.displayName());
                
                // Chuyển sang ChatView
                try {
                    javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                            getClass().getResource("/org/example/demo2/ui/view/ChatView.fxml"));
                    javafx.scene.Scene scene = new javafx.scene.Scene(loader.load());
                    Stage stage = (Stage) btnLogin.getScene().getWindow();
                    stage.setScene(scene);
                    stage.setTitle("AegisTalk - Chat (" + user.displayName() + ")");
                } catch (java.io.IOException e) {
                    lblStatus.setText("Lỗi tải giao diện: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                lblStatus.setText("Sai tên đăng nhập hoặc mật khẩu");
            }
        } catch (RemoteException e) {
            lblStatus.setText("Lỗi RMI: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void doRegister() {
        if (clientService == null) {
            lblStatus.setText("Chưa kết nối đến server");
            return;
        }
        
        String username = txtUsername.getText().trim();
        String password = txtPassword.getText().trim();
        String displayName = txtDisplayName.getText().trim();
        
        if (username.isEmpty() || password.isEmpty()) {
            lblStatus.setText("Vui lòng nhập đủ thông tin");
            return;
        }
        
        if (displayName.isEmpty()) {
            displayName = username; // Mặc định dùng username
        }
        
        try {
            // Gọi RMI AuthService.register()
            User user = clientService.register(username, password, displayName);
            
            if (user != null) {
                Session.setUser(user.id(), user.displayName());
                lblStatus.setText("Đăng ký thành công: " + user.displayName());
                
                // Chuyển sang ChatView
                try {
                    javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                            getClass().getResource("/org/example/demo2/ui/view/ChatView.fxml"));
                    javafx.scene.Scene scene = new javafx.scene.Scene(loader.load());
                    Stage stage = (Stage) btnRegister.getScene().getWindow();
                    stage.setScene(scene);
                    stage.setTitle("AegisTalk - Chat (" + user.displayName() + ")");
                } catch (java.io.IOException e) {
                    lblStatus.setText("Lỗi tải giao diện: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                lblStatus.setText("Đăng ký thất bại (username có thể đã tồn tại)");
            }
        } catch (RemoteException e) {
            lblStatus.setText("Lỗi RMI: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

