package org.example.demo2.ui.controller;

import javafx.fxml.FXML;
import org.example.demo2.ui.AppNavigator;
import org.example.demo2.ui.AppView;

public class CallController {

    @FXML
    public void initialize() {
        // Sau này: init audio/video, UDP, v.v.
    }

    @FXML
    private void handleJoinCall() {
        System.out.println("Join call (demo)");
    }

    @FXML
    private void handleLeaveCall() {
        System.out.println("Leave call (demo)");
    }

    @FXML
    private void handleToggleMute() {
        System.out.println("Toggle mute (demo)");
    }

    @FXML
    private void handleBackToChat() {
        AppNavigator.switchTo(AppView.CHAT);
    }
}