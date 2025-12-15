package org.example.demo2.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class HelloController {

    @FXML
    private Label appTitleLabel;

    @FXML
    private Label appSubtitleLabel;

    @FXML
    public void initialize() {
        // Bạn có thể chỉnh text ở đây nếu muốn
        // appTitleLabel.setText("AegisTalk – Ready");
        // appSubtitleLabel.setText("Day 3: JavaFX + DB OK");
    }
}
