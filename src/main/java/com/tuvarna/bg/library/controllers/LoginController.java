package com.tuvarna.bg.library.controllers;

import com.tuvarna.bg.library.dao.UserDAO;
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
import java.util.logging.Logger;

public class LoginController {
    private static final Logger LOGGER = Logger.getLogger(LoginController.class.getName());

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;
    @FXML private Label errorLabel;


    private UserDAO userDAO;

    @FXML
    public void initialize() {
        errorLabel.setText("");
        errorLabel.setVisible(false);
        userDAO = new UserDAO();
        LOGGER.info("LoginController initialized");

        // pressing Enter in password field triggers login
        passwordField.setOnAction(e -> handleLogin());

        if (!DatabaseUtil.testConnection()) {
            showError("Database connection failed. Please check your database settings.");
            loginButton.setDisable(true);
        }

        insertDemoData(); // ensures demo users exist
    }

    @FXML
    private void handleLogin() {
        LOGGER.info("handleLogin() fired");
        final String username = usernameField.getText().trim();
        final String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Please enter both username and password.");
            return;
        }

        if (!DatabaseUtil.testConnection()) {
            showError("Cannot connect to database. Please try again later.");
            return;
        }

        UserEntity user = userDAO.findByUsernameAndPassword(username, password);
        if (user == null) {
            LOGGER.warning("No user found or wrong password for username=" + username);
            showError("Invalid username or password.");
            usernameField.requestFocus();
            passwordField.clear();
            return;
        }

        if (user.getRole() == null || user.getRole().getName() == null) {
            LOGGER.severe("User has no role loaded. Check DAO JOIN for roles.");
            showError("Your account has no role assigned. Contact admin.");
            return;
        }

        final String role = user.getRole().getName();
        LOGGER.info("Authenticated user " + user.getUsername() + " with role=" + role);
        openDashboard(user);
    }


    private void openDashboard(UserEntity user) {
        try {
            String fxmlFile;
            String title;

            switch (user.getRole().getName()) {
                case "ADMIN":
                    fxmlFile = "/com/tuvarna/bg/library/view/admin-dashboard.fxml";
                    title = "Admin Dashboard - " + user.getFullName();
                    break;
                case "MANAGER":
                    fxmlFile = "/com/tuvarna/bg/library/view/manager-dashboard.fxml";
                    title = "Manager Dashboard - " + user.getFullName();
                    break;
                case "CLIENT":
                    fxmlFile = "/com/tuvarna/bg/library/view/client-dashboard.fxml";
                    title = "Client Dashboard - " + user.getFullName();
                    break;
                default:
                    showError("Unknown role: " + user.getRole().getName());
                    return;
            }

            java.net.URL url = getClass().getResource(fxmlFile);
            if (url == null) {
                String msg = "FXML not found on classpath: " + fxmlFile +
                        "\nExpected under: src/main/resources" + fxmlFile;
                LOGGER.severe(msg);
                showError(msg);
                return;
            }

            FXMLLoader loader = new FXMLLoader(url);
            Parent root = loader.load();

            Object controller = loader.getController();
            if (controller instanceof DashboardController) {
                ((DashboardController) controller).setCurrentUser(user);
            }

            Scene scene = new Scene(root);
            java.net.URL css = getClass().getResource("/com/tuvarna/bg/library/css/styles.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());

            Stage stage = new Stage();
            stage.setTitle(title);
            stage.setScene(scene);
            stage.initStyle(StageStyle.UNDECORATED);
            stage.setResizable(false);
            stage.setMaximized(true);

            // Close login window
            Stage loginStage = (Stage) loginButton.getScene().getWindow();
            if (loginStage != null) loginStage.close();

            stage.show();

        } catch (IOException e) {
            LOGGER.severe("Error loading dashboard: " + e.getMessage());
            showError("Error loading dashboard: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setStyle("-fx-text-fill: #c0392b; -fx-font-size: 14px;");
        errorLabel.setVisible(true);
        LOGGER.warning(message);
    }

    /**
     * Inserts demo accounts (admin, manager, client) if not already present.
     */
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
                    LOGGER.info("Demo data inserted successfully");
                }
            }
        } catch (SQLException e) {
            LOGGER.severe("Error inserting demo data: " + e.getMessage());
        }
    }
}