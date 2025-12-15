package org.example.demo2.ui.controller;

import javafx.fxml.FXML;
import org.example.demo2.ui.AppNavigator;
import org.example.demo2.ui.AppView;

public class SettingsController {

    @FXML
    public void initialize() {
        // Sau này: load config từ file .properties hoặc DB
    }

    @FXML
    private void handleBackToChat() {
        AppNavigator.switchTo(AppView.CHAT);
    }
}
