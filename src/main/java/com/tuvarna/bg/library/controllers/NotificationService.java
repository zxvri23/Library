package com.tuvarna.bg.library.controllers;

import javafx.animation.FadeTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

public final class NotificationService {

    public enum Type { INFO, SUCCESS, WARN, ERROR }

    private static final NotificationService INSTANCE = new NotificationService();
    public static NotificationService get() { return INSTANCE; }
    private NotificationService() {}

    public void show(Node anchor, String message, Type type) {
        Platform.runLater(() -> {
            StackPane root = (StackPane) anchor.getScene().getRoot();

            Label label = new Label(message);
            label.setStyle("""
                -fx-text-fill: white;
                -fx-font-size: 14px;
                -fx-padding: 10 14;
            """);

            HBox box = new HBox(label);
            box.setAlignment(Pos.CENTER_LEFT);
            box.setPadding(new Insets(8, 12, 8, 12));
            box.setStyle(switch (type) {
                case SUCCESS -> "-fx-background-color: #2ecc71; -fx-background-radius: 10;";
                case WARN    -> "-fx-background-color: #f39c12; -fx-background-radius: 10;";
                case ERROR   -> "-fx-background-color: #e74c3c; -fx-background-radius: 10;";
                default      -> "-fx-background-color: #3498db; -fx-background-radius: 10;";
            });

            StackPane.setAlignment(box, Pos.TOP_RIGHT);
            StackPane.setMargin(box, new Insets(20, 20, 0, 0));
            box.setOpacity(0);
            box.setTranslateY(-20);

            root.getChildren().add(box);

            FadeTransition fadeIn = new FadeTransition(Duration.millis(180), box);
            fadeIn.setFromValue(0); fadeIn.setToValue(1);

            TranslateTransition slideIn = new TranslateTransition(Duration.millis(180), box);
            slideIn.setFromY(-20); slideIn.setToY(0);

            FadeTransition stay = new FadeTransition(Duration.seconds(2.2), box);
            stay.setFromValue(1); stay.setToValue(1);

            FadeTransition fadeOut = new FadeTransition(Duration.millis(220), box);
            fadeOut.setFromValue(1); fadeOut.setToValue(0);

            fadeOut.setOnFinished(e -> root.getChildren().remove(box));

            new SequentialTransition(fadeIn, slideIn, stay, fadeOut).play();
        });
    }

    // Sugar
    public void info(Node anchor, String msg)    { show(anchor, msg, Type.INFO); }
    public void ok(Node anchor, String msg)      { show(anchor, msg, Type.SUCCESS); }
    public void warn(Node anchor, String msg)    { show(anchor, msg, Type.WARN); }
    public void error(Node anchor, String msg)   { show(anchor, msg, Type.ERROR); }
}
