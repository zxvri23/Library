package com.tuvarna.bg.library;

import com.tuvarna.bg.library.util.DatabaseUtil;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.net.URL;

public class LibraryApplication extends Application {
    private double xOffset = 0, yOffset = 0;

    @Override
    public void start(Stage stage) throws Exception {
        DatabaseUtil.initializeDatabase();

        final String FXML_PATH = "/com/tuvarna/bg/library/view/login-view.fxml";
        URL fxmlUrl = getClass().getResource(FXML_PATH);
        if (fxmlUrl == null) throw new IllegalStateException("FXML not found: " + FXML_PATH);

        Parent root = FXMLLoader.load(fxmlUrl);
        Scene scene = new Scene(root);

        final String CSS_PATH = "/com/tuvarna/bg/library/css/styles.css";
        URL cssUrl = getClass().getResource(CSS_PATH);
        if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());

        stage.setTitle("Library Management System");
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setScene(scene);

        // Start maximized
        stage.setMaximized(true);
        stage.setResizable(false);

        // ESC on the login scene exits the app
        scene.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ESCAPE) Platform.exit(); });

        // Drag window when not maximized
        root.setOnMousePressed(e -> { xOffset = e.getSceneX(); yOffset = e.getSceneY(); });
        root.setOnMouseDragged(e -> {
            if (!stage.isMaximized()) {
                stage.setX(e.getScreenX() - xOffset);
                stage.setY(e.getScreenY() - yOffset);
            }
        });

        // Custom chrome
        Button closeBtn = (Button) root.lookup("#closeButton");
        if (closeBtn != null) closeBtn.setOnAction(e -> Platform.exit());
        Button minimizeBtn = (Button) root.lookup("#minimizeButton");
        if (minimizeBtn != null) minimizeBtn.setOnAction(e -> stage.setIconified(true));

        stage.show();
    }

    public static void main(String[] args) { launch(args); }
}
