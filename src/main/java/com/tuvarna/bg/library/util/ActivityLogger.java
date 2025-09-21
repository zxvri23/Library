package com.tuvarna.bg.library.util;

import com.tuvarna.bg.library.entity.UserEntity;
import com.tuvarna.bg.library.controllers.NotificationService;
import javafx.scene.Node;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class ActivityLogger {
    private ActivityLogger() {}

    public static void log(Node anchor, UserEntity user, String action, String details) {
        // DB row
        try (Connection c = DatabaseUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO activity_log (when_ts, action, who, details) VALUES (CURRENT_TIMESTAMP, ?, ?, ?)")) {
            ps.setString(1, action);
            ps.setString(2, user != null ? user.getUsername() : "system");
            ps.setString(3, details);
            ps.executeUpdate();
        } catch (SQLException e) {
            // show an error toast but don't crash the app
            if (anchor != null) NotificationService.get().error(anchor, "Failed to write activity_log");
        }

        // Toast (success/info based on action)
        if (anchor != null) {
            if (action.equalsIgnoreCase("ERROR")) {
                NotificationService.get().error(anchor, details);
            } else if (action.startsWith("Warn")) {
                NotificationService.get().warn(anchor, details);
            } else if (action.startsWith("Added") || action.startsWith("Deleted") ||
                    action.startsWith("Updated") || action.startsWith("Login") ||
                    action.startsWith("Logout") || action.startsWith("Reserved") ||
                    action.startsWith("Borrowed")) {
                NotificationService.get().ok(anchor, details);
            } else {
                NotificationService.get().info(anchor, details);
            }
        }
    }
}
