package com.tuvarna.bg.library;

import com.tuvarna.bg.library.util.DatabaseUtil;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.net.URL;

public class LibraryApplication extends Application {
    private double xOffset = 0;
    private double yOffset = 0;

    @Override
    public void start(Stage stage) throws Exception {
        // Initialize DB (will log success/failure)
        DatabaseUtil.initializeDatabase();

        // FXML must be under: src/main/resources/com/tuvarna/bg/library/view/login-view.fxml
        final String FXML_PATH = "/com/tuvarna/bg/library/view/login-view.fxml";
        URL fxmlUrl = getClass().getResource(FXML_PATH);
        if (fxmlUrl == null) {
            throw new IllegalStateException("FXML not found on classpath: " + FXML_PATH);
        }

        Parent root = FXMLLoader.load(fxmlUrl);
        Scene scene = new Scene(root);

        // Optional stylesheet under: src/main/resources/com/tuvarna/bg/library/css/styles.css
        final String CSS_PATH = "/com/tuvarna/bg/library/css/styles.css";
        URL cssUrl = getClass().getResource(CSS_PATH);
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }

        stage.setTitle("Library Management System");
        stage.setScene(scene);
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setFullScreen(true);
        stage.setResizable(false);

        // Drag to move window
        root.setOnMousePressed(e -> {
            xOffset = e.getSceneX();
            yOffset = e.getSceneY();
        });
        root.setOnMouseDragged(e -> {
            stage.setX(e.getScreenX() - xOffset);
            stage.setY(e.getScreenY() - yOffset);
        });

        // Wire window controls if present
        Button closeBtn = (Button) root.lookup("#closeButton");
        if (closeBtn != null) closeBtn.setOnAction(e -> System.exit(0));

        Button minimizeBtn = (Button) root.lookup("#minimizeButton");
        if (minimizeBtn != null) minimizeBtn.setOnAction(e -> stage.setIconified(true));

        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
