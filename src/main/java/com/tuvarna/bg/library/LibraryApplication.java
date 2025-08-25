package com.tuvarna.bg.library;

import javafx.application.Application;
import com.tuvarna.bg.library.util.DatabaseUtil;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.util.Objects;

public class LibraryApplication extends Application {
    private double xOffset = 0;
    private double yOffset = 0;

    @Override
    public void start(Stage stage) throws IOException {
        DatabaseUtil.initializeDatabase();
        Parent root = FXMLLoader.load(Objects.requireNonNull(getClass().getResource("/com/tuvarna/bg/library/view/LoginView.fxml")));

        Scene scene = new Scene(root);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/com/tuvarna/bg/library/css/styles.css")).toExternalForm());

        stage.setTitle("Library Management System");
        stage.setScene(scene);
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setResizable(false);

        root.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });

        root.setOnMouseDragged(event -> {
            stage.setX(event.getScreenX() - xOffset);
            stage.setY(event.getScreenY() - yOffset);
        });

        root.lookup("#closeButton").setOnMouseClicked(event -> System.exit(0));
        root.lookup("#minimizeButton").setOnMouseClicked(event -> stage.setIconified(true));

        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
