package org.example.demo2.ui;

public enum AppView {
    LOGIN("/org/example/demo2/ui/view/LoginView.fxml", "AegisTalk – Login"),
    CHAT("/org/example/demo2/ui/view/ChatView.fxml", "AegisTalk – Chat"),
    CALL("/org/example/demo2/ui/view/CallView.fxml", "AegisTalk – Call"),
    SETTINGS("/org/example/demo2/ui/view/SettingsView.fxml", "AegisTalk – Settings");

    private final String fxmlPath;
    private final String title;

    AppView(String fxmlPath, String title) {
        this.fxmlPath = fxmlPath;
        this.title = title;
    }

    public String fxmlPath() {
        return fxmlPath;
    }

    public String title() {
        return title;
    }
}