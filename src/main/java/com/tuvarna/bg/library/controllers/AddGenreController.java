package com.tuvarna.bg.library.controllers;

import com.tuvarna.bg.library.util.DatabaseUtil;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.sql.*;
import java.util.Objects;

public class AddGenreController {

    @FXML private TextField genreNameField;
    @FXML private TextArea descriptionArea;
    @FXML private Label messageLabel;

    @FXML
    public void initialize() {
        messageLabel.setText("");
    }

    @FXML
    private void addGenre() {
        String genreName = genreNameField.getText().trim();
        String description = descriptionArea.getText().trim();

        if (genreName.isEmpty()) {
            showMessage("Please enter the genre name.", true);
            return;
        }

        try {
            // Check if genre already exists
            if (genreExists(genreName)) {
                showMessage("Genre with this name already exists.", true);
                return;
            }

            // Insert new genre
            String sql = "INSERT INTO genres (gen_name, genre_desc) VALUES (?, ?)";
            try (Connection conn = DatabaseUtil.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, genreName);
                stmt.setString(2, description.isEmpty() ? null : description);

                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected > 0) {
                    showMessage("Genre added successfully!", false);
                    clearForm();
                } else {
                    showMessage("Failed to add genre.", true);
                }
            }

        } catch (SQLException e) {
            showMessage("Database error: " + e.getMessage(), true);
            e.printStackTrace();
        }
    }

    private boolean genreExists(String genreName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM genres WHERE gen_name = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, genreName);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    @FXML
    private void clearForm() {
        genreNameField.clear();
        descriptionArea.clear();
        messageLabel.setText("");
    }

    @FXML
    private void handleBack() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/tuvarna/bg/library/view/admin-dashboard.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root);
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/com/tuvarna/bg/library/css/styles.css")).toExternalForm());

            Stage stage = new Stage();
            stage.setTitle("Admin Dashboard");
            stage.setScene(scene);
            stage.initStyle(StageStyle.UNDECORATED);
            stage.setResizable(false);
            stage.setMaximized(true);

            ((Stage) genreNameField.getScene().getWindow()).close();

            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showMessage(String message, boolean isError) {
        messageLabel.setText(message);
        if (isError) {
            messageLabel.setStyle("-fx-text-fill: #c0392b; -fx-font-size: 14px;");
        } else {
            messageLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-size: 14px;");
        }
    }
}
