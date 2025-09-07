package com.tuvarna.bg.library.dao;

import com.tuvarna.bg.library.entity.UserEntity;
import com.tuvarna.bg.library.entity.RoleEntity;
import com.tuvarna.bg.library.util.DatabaseUtil;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

public class UserDAO {
    private static final Logger LOGGER = Logger.getLogger(UserDAO.class.getName());

    public UserEntity findByUsernameAndPassword(String username, String password) {
        String sql =
                "SELECT u.users_id, u.username, u.password, u.first_name, u.last_name, u.email, " +
                        "       r.roles_id, r.name AS role_name " +
                        "FROM users u " +
                        "JOIN roles r ON r.roles_id = u.roles_id " +
                        "WHERE u.username = ? AND u.password = ?";

        try (Connection c = DatabaseUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, password); // if you hash, pass the hash here
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

                RoleEntity role = new RoleEntity();
                role.setRolesId(rs.getInt("roles_id"));
                role.setName(rs.getString("role_name")); // must be 'ADMIN' | 'MANAGER' | 'CLIENT'

                UserEntity u = new UserEntity();
                u.setUsersId(rs.getInt("users_id"));
                u.setUsername(rs.getString("username"));
                u.setPassword(rs.getString("password"));
                u.setFirstName(rs.getString("first_name"));
                u.setLastName(rs.getString("last_name"));
                u.setEmail(rs.getString("email"));
                u.setRole(role);
                return u;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }


    public UserEntity findById(int userId) {
        String sql = "SELECT u.*, r.name as role_name FROM users u " +
                "JOIN roles r ON u.roles_id = r.roles_id " +
                "WHERE u.users_id = ?";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToUser(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding user by ID: " + e.getMessage());
        }

        return null;
    }

    public List<UserEntity> findAll() {
        String sql = "SELECT u.*, r.name as role_name FROM users u " +
                "JOIN roles r ON u.roles_id = r.roles_id " +
                "ORDER BY u.users_id";
        List<UserEntity> users = new ArrayList<>();

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                users.add(mapResultSetToUser(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error retrieving all users: " + e.getMessage());
        }

        return users;
    }

    public List<UserEntity> findByRole(String roleName) {
        String sql = "SELECT u.*, r.name as role_name FROM users u " +
                "JOIN roles r ON u.roles_id = r.roles_id " +
                "WHERE r.name = ? " +
                "ORDER BY u.users_id";
        List<UserEntity> users = new ArrayList<>();

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, roleName);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    users.add(mapResultSetToUser(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error retrieving users by role: " + e.getMessage());
        }

        return users;
    }

    public boolean insert(UserEntity user) {
        String sql = "INSERT INTO users (username, password, first_name, last_name, email, roles_id) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, user.getUsername());
            stmt.setString(2, user.getPassword());
            stmt.setString(3, user.getFirstName());
            stmt.setString(4, user.getLastName());
            stmt.setString(5, user.getEmail());
            stmt.setInt(6, user.getRole().getRolesId());

            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error inserting user: " + e.getMessage());
            return false;
        }
    }

    public boolean update(UserEntity user) {
        String sql = "UPDATE users SET username = ?, password = ?, first_name = ?, " +
                "last_name = ?, email = ?, roles_id = ? WHERE users_id = ?";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, user.getUsername());
            stmt.setString(2, user.getPassword());
            stmt.setString(3, user.getFirstName());
            stmt.setString(4, user.getLastName());
            stmt.setString(5, user.getEmail());
            stmt.setInt(6, user.getRole().getRolesId());
            stmt.setInt(7, user.getUsersId());

            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error updating user: " + e.getMessage());
            return false;
        }
    }

    public boolean delete(int userId) {
        String sql = "DELETE FROM users WHERE users_id = ?";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);

            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error deleting user: " + e.getMessage());
            return false;
        }
    }

    public UserEntity findByUsername(String username) {
        String sql = "SELECT u.*, r.name as role_name FROM users u " +
                "JOIN roles r ON u.roles_id = r.roles_id " +
                "WHERE u.username = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToUser(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding user by username: " + e.getMessage());
        }
        return null;
    }

    private UserEntity mapResultSetToUser(ResultSet rs) throws SQLException {
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