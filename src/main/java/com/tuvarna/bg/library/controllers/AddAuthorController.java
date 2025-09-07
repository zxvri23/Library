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
import java.time.LocalDate;
import java.util.Objects;

public class AddAuthorController {

    @FXML private TextField fullNameField;
    @FXML private DatePicker birthDatePicker;
    @FXML private Label messageLabel;

    @FXML
    public void initialize() {
        messageLabel.setText("");
    }

    public void setPreFilledName(String name) {
        fullNameField.setText(name);
    }

    @FXML
    private void addAuthor() {
        String fullName = fullNameField.getText().trim();
        LocalDate birthDate = birthDatePicker.getValue();

        if (fullName.isEmpty()) {
            showMessage("Please enter the author's full name.", true);
            return;
        }

        try {
            // Check if author already exists
            if (authorExists(fullName, birthDate)) {
                showMessage("Author with this name and birth date already exists.", true);
                return;
            }

            // Insert new author
            String sql = "INSERT INTO authors (full_name, birth_date) VALUES (?, ?)";
            try (Connection conn = DatabaseUtil.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, fullName);
                if (birthDate != null) {
                    stmt.setDate(2, Date.valueOf(birthDate));
                } else {
                    stmt.setNull(2, Types.DATE);
                }

                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected > 0) {
                    showMessage("Author added successfully!", false);
                    clearForm();
                } else {
                    showMessage("Failed to add author.", true);
                }
            }

        } catch (SQLException e) {
            showMessage("Database error: " + e.getMessage(), true);
            e.printStackTrace();
        }
    }

    private boolean authorExists(String fullName, LocalDate birthDate) throws SQLException {
        String sql = "SELECT COUNT(*) FROM authors WHERE full_name = ? AND birth_date = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, fullName);
            if (birthDate != null) {
                stmt.setDate(2, Date.valueOf(birthDate));
            } else {
                stmt.setNull(2, Types.DATE);
            }

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
        fullNameField.clear();
        birthDatePicker.setValue(null);
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

            ((Stage) fullNameField.getScene().getWindow()).close();

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
