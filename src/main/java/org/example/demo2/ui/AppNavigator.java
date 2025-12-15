package org.example.demo2.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.demo2.HelloApplication;

import java.io.IOException;
import java.net.URL;

/**
 * Quản lý Stage chính và chuyển giữa các màn hình (Login, Chat, Call, Settings).
 */
public final class AppNavigator {

    private static Stage primaryStage;

    private AppNavigator() {
    }

    public static void init(Stage stage) {
        primaryStage = stage;
    }

    /**
     * Chuyển sang view mới, trả về controller để controller khác có thể dùng nếu cần.
     */
    @SuppressWarnings("unchecked")
    public static <T> T switchTo(AppView view) {
        if (primaryStage == null) {
            throw new IllegalStateException("AppNavigator chưa được init(Stage). Gọi AppNavigator.init() trong Application.start().");
        }

        try {
            URL fxml = HelloApplication.class.getResource(view.fxmlPath());
            if (fxml == null) {
                throw new IllegalStateException("Không tìm thấy FXML: " + view.fxmlPath());
            }

            FXMLLoader loader = new FXMLLoader(fxml);
            Parent root = loader.load();

            Scene scene = primaryStage.getScene();
            if (scene == null) {
                scene = new Scene(root, 1000, 700);
                primaryStage.setScene(scene);
            } else {
                scene.setRoot(root);
            }

            // Gắn CSS theme (nếu chưa có)
            URL css = HelloApplication.class.getResource("/org/example/demo2/ui/css/theme.css");
            if (css != null) {
                String cssUrl = css.toExternalForm();
                if (!scene.getStylesheets().contains(cssUrl)) {
                    scene.getStylesheets().add(cssUrl);
                }
            }

            primaryStage.setTitle(view.title());
            return (T) loader.getController();
        } catch (IOException e) {
            throw new RuntimeException("Không thể load view: " + view, e);
        }
    }
}