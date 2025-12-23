package org.example.demo2;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;

public class HelloApplication extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        // Mở LOGIN trước, không mở ChatView nữa
        URL fxml = HelloApplication.class.getResource(
                "/org/example/demo2/ui/view/LoginView.fxml"
        );
        if (fxml == null) {
            throw new IllegalStateException("Không tìm thấy LoginView.fxml");
        }

        FXMLLoader loader = new FXMLLoader(fxml);
        Scene scene = new Scene(loader.load());

        // Theme chung
        URL css = HelloApplication.class.getResource(
                "/org/example/demo2/ui/css/theme.css"
        );
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }

        stage.setTitle("AegisTalk – Login");
        stage.setScene(scene);
        // Set minimum size để responsive trên màn hình nhỏ
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
