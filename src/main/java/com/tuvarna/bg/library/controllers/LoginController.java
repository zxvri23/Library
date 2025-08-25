package com.tuvarna.bg.library.controllers;

import com.tuvarna.bg.library.entity.RoleEntity;
import com.tuvarna.bg.library.entity.UserEntity;
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

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;
    @FXML private Label errorLabel;

    @FXML
    public void initialize() {
        passwordField.setOnAction(event -> handleLogin());
        insertDemoData();
    }

    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Please enter both username and password");
            return;
        }

        try {
            UserEntity user = authenticateUser(username, password);
            if (user != null) {
                openDashboard(user);
            } else {
                showError("Invalid username or password");
            }
        } catch (SQLException e) {
            showError("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private UserEntity authenticateUser(String username, String password) throws SQLException {
        String sql = "SELECT u.*, r.name as role_name FROM users u " +
                "JOIN roles r ON u.roles_id = r.roles_id " +
                "WHERE u.username = ? AND u.password = ?";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            stmt.setString(2, password);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    UserEntity user = new UserEntity();
                    user.setUsersId(rs.getInt("users_id"));
                    user.setUsername(rs.getString("username"));
                    user.setPassword(rs.getString("password"));
                    user.setFirstName(rs.getString("first_name"));
                    user.setLastName(rs.getString("last_name"));
                    user.setEmail(rs.getString("email"));

                    RoleEntity role = new RoleEntity();
                    role.setRolesId(rs.getInt("roles_id"));
                    role.setName(rs.getString("role_name"));
                    user.setRole(role);

                    return user;
                }
            }
        }
        return null;
    }

    private void openDashboard(UserEntity user) {
        try {
            String fxmlFile;
            String title;

            switch (user.getRole().getName()) {
                case "ADMIN":
                    fxmlFile = "/com/tuvarna/bg/library/view/AdminDashboard.fxml";
                    title = "Admin Dashboard - " + user.getFullName();
                    break;
                case "MANAGER":
                    fxmlFile = "/com/tuvarna/bg/library/view/ManagerDashboard.fxml";
                    title = "Manager Dashboard - " + user.getFullName();
                    break;
                case "CLIENT":
                    fxmlFile = "/com/tuvarna/bg/library/view/ClientDashboard.fxml";
                    title = "Client Dashboard - " + user.getFullName();
                    break;
                default:
                    showError("Unknown role: " + user.getRole().getName());
                    return;
            }

            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlFile));
            Parent root = loader.load();

            // Pass user data to dashboard controller
            Object controller = loader.getController();
            if (controller instanceof DashboardController) {
                ((DashboardController) controller).setCurrentUser(user);
            }

            Scene scene = new Scene(root);
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/com/tuvarna/bg/library/css/styles.css")).toExternalForm());

            Stage stage = new Stage();
            stage.setTitle(title);
            stage.setScene(scene);
            stage.initStyle(StageStyle.UNDECORATED);
            stage.setResizable(false);
            stage.setMaximized(true);
            ((Stage) loginButton.getScene().getWindow()).close();

            stage.show();

        } catch (IOException e) {
            showError("Error loading dashboard: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);

        // Auto-hide error after 5 seconds
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                javafx.application.Platform.runLater(() -> errorLabel.setVisible(false));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private void insertDemoData() {
        try (Connection conn = DatabaseUtil.getConnection()) {
            String checkSql = "SELECT COUNT(*) FROM users WHERE username IN ('admin', 'manager', 'client')";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(checkSql)) {

                if (rs.next() && rs.getInt(1) == 0) {
                    String[] demoUsers = {
                            "INSERT INTO users (username, password, first_name, last_name, email, roles_id) " +
                                    "VALUES ('admin', 'admin123', 'Admin', 'User', 'admin@library.com', " +
                                    "(SELECT roles_id FROM roles WHERE name = 'ADMIN'))",

                            "INSERT INTO users (username, password, first_name, last_name, email, roles_id) " +
                                    "VALUES ('manager', 'manager123', 'Manager', 'User', 'manager@library.com', " +
                                    "(SELECT roles_id FROM roles WHERE name = 'MANAGER'))",

                            "INSERT INTO users (username, password, first_name, last_name, email, roles_id) " +
                                    "VALUES ('client', 'client123', 'Client', 'User', 'client@library.com', " +
                                    "(SELECT roles_id FROM roles WHERE name = 'CLIENT'))"
                    };

                    for (String sql : demoUsers) {
                        try (Statement insertStmt = conn.createStatement()) {
                            insertStmt.execute(sql);
                        }
                    }

                    System.out.println("Demo data inserted successfully");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error inserting demo data: " + e.getMessage());
        }
    }
}