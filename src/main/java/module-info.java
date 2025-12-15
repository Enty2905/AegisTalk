module org.example.aegistalk {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires java.sql;
    requires javafx.graphics;
    requires javafx.media;
    requires javafx.swing; // Cần cho SwingFXUtils để convert BufferedImage sang JavaFX Image
    requires java.rmi;
    requires java.net.http;
    requires com.fasterxml.jackson.databind;
    requires java.desktop; // Cần cho BufferedImage
    requires webcam.capture; // Automatic module name từ webcam-capture JAR

    opens org.example.demo2 to javafx.fxml;
    opens org.example.demo2.ui.controller to javafx.fxml;

    exports org.example.demo2;
    exports org.example.demo2.ui.controller;
    exports org.example.demo2.model;
    exports org.example.demo2.net.moderation;
    exports org.example.demo2.service.rmi; // Export RMI services để RMI có thể truy cập

    opens org.example.demo2.model to com.fasterxml.jackson.databind;
    exports org.example.demo2.ui;
    opens org.example.demo2.ui to javafx.fxml;
    exports org.example.demo2.net.files;
    requires jdk.httpserver;
}
