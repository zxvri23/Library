package com.tuvarna.bg.library.util;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

public class DatabaseUtil {
    private static final Logger LOGGER = Logger.getLogger(DatabaseUtil.class.getName());
    private static final String URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String USERNAME = "postgres";
    private static final String PASSWORD = "zeri";

    static {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            LOGGER.log(Level.SEVERE, "PostgreSQL driver not found", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USERNAME, PASSWORD);
    }

    public static void closeConnection(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing connection", e);
            }
        }
    }

    public static void closeStatement(Statement statement) {
        if (statement != null) {
            try {
                statement.close();
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing statement", e);
            }
        }
    }

    public static void closeResultSet(ResultSet resultSet) {
        if (resultSet != null) {
            try {
                resultSet.close();
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing result set", e);
            }
        }
    }

    public static void closeResources(Connection connection, Statement statement, ResultSet resultSet) {
        closeResultSet(resultSet);
        closeStatement(statement);
        closeConnection(connection);
    }

    public static boolean testConnection() {
        try (Connection connection = getConnection()) {
            return connection.isValid(5);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database connection test failed", e);
            return false;
        }
    }

    public static void initializeDatabase() {
        String[] createTables = {
                "CREATE TABLE IF NOT EXISTS roles (" +
                        "roles_id SERIAL PRIMARY KEY, " +
                        "name TEXT NOT NULL UNIQUE)",

                "CREATE TABLE IF NOT EXISTS authors (" +
                        "authors_id SERIAL PRIMARY KEY, " +
                        "full_name TEXT NOT NULL, " +
                        "birth_date DATE, " +
                        "UNIQUE(full_name, birth_date))",

                "CREATE TABLE IF NOT EXISTS publishers (" +
                        "publishers_id SERIAL PRIMARY KEY, " +
                        "pub_name TEXT NOT NULL UNIQUE, " +
                        "established_on DATE)",

                "CREATE TABLE IF NOT EXISTS genres (" +
                        "genres_id SERIAL PRIMARY KEY, " +
                        "gen_name TEXT NOT NULL UNIQUE, " +
                        "genre_desc TEXT)",

                "CREATE TABLE IF NOT EXISTS users (" +
                        "users_id SERIAL PRIMARY KEY, " +
                        "username TEXT NOT NULL UNIQUE, " +
                        "password TEXT NOT NULL, " +
                        "first_name TEXT, " +
                        "last_name TEXT, " +
                        "email TEXT UNIQUE, " +
                        "roles_id INTEGER NOT NULL REFERENCES roles(roles_id))",

                "CREATE TABLE IF NOT EXISTS books (" +
                        "books_id SERIAL PRIMARY KEY, " +
                        "title TEXT NOT NULL, " +
                        "summary TEXT, " +
                        "isbn TEXT UNIQUE, " +
                        "language TEXT, " +
                        "publication_year SMALLINT CHECK(publication_year BETWEEN 1000 AND 2030), " +
                        "publishers_id INTEGER NOT NULL REFERENCES publishers(publishers_id), " +
                        "image_path TEXT)",

                "CREATE TABLE IF NOT EXISTS book_authors (" +
                        "books_id INTEGER NOT NULL REFERENCES books(books_id) ON DELETE CASCADE, " +
                        "authors_id INTEGER NOT NULL REFERENCES authors(authors_id) ON DELETE CASCADE, " +
                        "PRIMARY KEY (books_id, authors_id))",

                "CREATE TABLE IF NOT EXISTS book_genres (" +
                        "books_id INTEGER NOT NULL REFERENCES books(books_id) ON DELETE CASCADE, " +
                        "genres_id INTEGER NOT NULL REFERENCES genres(genres_id) ON DELETE CASCADE, " +
                        "PRIMARY KEY (books_id, genres_id))",

                "CREATE TABLE IF NOT EXISTS book_copies (" +
                        "copies_id SERIAL PRIMARY KEY, " +
                        "books_id INTEGER NOT NULL REFERENCES books(books_id) ON DELETE CASCADE, " +
                        "status TEXT NOT NULL CHECK (status IN ('AVAILABLE','LOANED','RESERVED')) DEFAULT 'AVAILABLE', " +
                        "acquired_at DATE)",

                "CREATE TABLE IF NOT EXISTS loans (" +
                        "loans_id SERIAL PRIMARY KEY, " +
                        "users_id INTEGER NOT NULL REFERENCES users(users_id), " +
                        "staff_id INTEGER REFERENCES users(users_id), " +
                        "copy_id INTEGER NOT NULL REFERENCES book_copies(copies_id), " +
                        "borrowed_at TIMESTAMPTZ NOT NULL DEFAULT now(), " +
                        "due_date DATE NOT NULL, " +
                        "returned_at TIMESTAMPTZ, " +
                        "CHECK (due_date >= borrowed_at::date), " +
                        "CHECK (returned_at IS NULL OR returned_at >= borrowed_at))",

                "CREATE TABLE IF NOT EXISTS reservations (" +
                        "reservations_id SERIAL PRIMARY KEY, " +
                        "user_id INTEGER NOT NULL REFERENCES users(users_id), " +
                        "book_id INTEGER NOT NULL REFERENCES books(books_id), " +
                        "created_at TIMESTAMPTZ NOT NULL DEFAULT now(), " +
                        "expires_at TIMESTAMPTZ, " +
                        "status TEXT NOT NULL CHECK (status IN ('PENDING','READY','CANCELLED')))"
        };

        try (Connection connection = getConnection()) {
            for (String sql : createTables) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(sql);
                }
            }

            // Insert default roles if they don't exist
            insertDefaultRoles(connection);

            LOGGER.info("Database initialized successfully");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error initializing database", e);
        }
    }

    private static void insertDefaultRoles(Connection connection) throws SQLException {
        String checkRoles = "SELECT COUNT(*) FROM roles";
        String insertRoles = "INSERT INTO roles (name) VALUES ('ADMIN'), ('MANAGER'), ('CLIENT')";

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(checkRoles)) {

            if (resultSet.next() && resultSet.getInt(1) == 0) {
                statement.execute(insertRoles);
                LOGGER.info("Default roles inserted");
            }
        }
    }
}
