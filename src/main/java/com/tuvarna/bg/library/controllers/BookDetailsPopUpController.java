package com.tuvarna.bg.library.controllers;

import com.tuvarna.bg.library.entity.BookEntity;
import com.tuvarna.bg.library.entity.UserEntity;
import com.tuvarna.bg.library.util.DatabaseUtil;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

import java.sql.*;
import java.time.LocalDateTime;

public class BookDetailsPopUpController {
    @FXML private ImageView bookCoverImage;
    @FXML private Label bookTitleLabel, authorLabel, publisherLabel, isbnLabel, languageLabel, yearLabel, genresLabel;
    @FXML private TextArea summaryArea;
    @FXML private Button reserveButton, borrowButton, closeButton;

    private BookEntity book;
    private UserEntity currentUser;

    public void setBook(BookEntity book) {
        this.book = book;
        loadBookDetails();
    }

    public void setCurrentUser(UserEntity currentUser) {
        this.currentUser = currentUser;
    }

    private void loadBookDetails() {
        if (book == null) return;

        // Basic fields
        bookTitleLabel.setText(book.getTitle());
        isbnLabel.setText(book.getIsbn());
        languageLabel.setText(book.getLanguage());
        yearLabel.setText(book.getPublicationYear() != null ? book.getPublicationYear().toString() : "N/A");
        summaryArea.setText(book.getSummary());

        // --- Robust cover loading ---
        Image cover = null;
        try {
            String p = book.getImagePath();
            java.util.List<String> candidates = new java.util.ArrayList<>();

            if (p != null && !p.isBlank()) {
                if (p.startsWith("http://") || p.startsWith("https://") || p.startsWith("file:")) {
                    // already a URL
                    candidates.add(p);
                } else {
                    // try as absolute/local file
                    java.nio.file.Path abs = java.nio.file.Paths.get(p);
                    candidates.add("file:" + (abs.isAbsolute() ? abs : abs.toAbsolutePath()));

                    // also try library_images/<filename>
                    java.nio.file.Path fn = abs.getFileName();
                    if (fn != null) {
                        candidates.add("file:" + java.nio.file.Paths.get("library_images", fn.toString()).toAbsolutePath());
                    }
                }
            }

            // try packaged resource by ISBN (optional)
            if (book.getIsbn() != null && !book.getIsbn().isBlank()) {
                java.net.URL byIsbn = getClass().getResource("/covers/" + book.getIsbn() + ".jpg");
                if (byIsbn != null) candidates.add(byIsbn.toExternalForm());
            }

            // try each candidate until one works
            for (String url : candidates) {
                try {
                    Image img = new Image(url, 240, 0, true, true);
                    if (!img.isError()) { cover = img; break; }
                } catch (Exception ignored) {}
            }

            // final placeholder (if you add one to resources)
            if (cover == null) {
                java.net.URL ph = getClass().getResource("/com/tuvarna/bg/library/images/placeholder.png");
                if (ph != null) cover = new Image(ph.toExternalForm(), 240, 0, true, true);
            }
        } catch (Exception ignored) {}

        bookCoverImage.setPreserveRatio(true);
        bookCoverImage.setFitWidth(220);
        bookCoverImage.setFitHeight(300);
        bookCoverImage.setImage(cover);

        // Load additional details from database
        loadAuthorAndPublisher();
        loadGenres();
        updateActionButtons();
    }


    private void loadAuthorAndPublisher() {
        String sql = """
        SELECT
            COALESCE(
                STRING_AGG(DISTINCT a.full_name, ', ' ORDER BY a.full_name),
                'Unknown Author'
            ) AS authors,
            COALESCE(p.pub_name, 'Unknown Publisher') AS publisher
        FROM books b
        LEFT JOIN publishers p ON p.publishers_id = b.publishers_id
        LEFT JOIN book_authors ba ON ba.books_id = b.books_id
        LEFT JOIN authors a ON a.authors_id = ba.authors_id
        WHERE b.books_id = ?
        GROUP BY p.pub_name
    """;
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, book.getBooksId());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    authorLabel.setText(rs.getString("authors"));
                    publisherLabel.setText(rs.getString("publisher"));
                } else {
                    authorLabel.setText("Unknown Author");
                    publisherLabel.setText("Unknown Publisher");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            authorLabel.setText("Unknown Author");
            publisherLabel.setText("Unknown Publisher");
        }
    }

    private void loadGenres() {
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT g.gen_name " +
                             "FROM books b " +
                             "LEFT JOIN book_genres bg ON b.books_id = bg.books_id " +
                             "LEFT JOIN genres g ON bg.genres_id = g.genres_id " +
                             "WHERE b.books_id = ?")) {

            stmt.setInt(1, book.getBooksId());
            StringBuilder genres = new StringBuilder();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    if (genres.length() > 0) genres.append(", ");
                    genres.append(rs.getString("gen_name"));
                }
            }
            genresLabel.setText(genres.length() > 0 ? genres.toString() : "No genres");
        } catch (SQLException e) {
            e.printStackTrace();
            genresLabel.setText("No genres");
        }
    }

    private void updateActionButtons() {
        boolean hasAvailableCopies = checkBookAvailability(book.getBooksId());
        boolean hasReservation = checkUserReservation(book.getBooksId());

        reserveButton.setDisable(hasReservation);
        borrowButton.setDisable(!hasAvailableCopies);

        reserveButton.setText(hasReservation ? "Already Reserved" : "Reserve Book");
        borrowButton.setText(hasAvailableCopies ? "Borrow Available Copy" : "No Copies Available");
    }

    private boolean checkBookAvailability(int bookId) {
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT COUNT(*) FROM book_copies WHERE books_id = ? AND status = 'AVAILABLE'")) {
            stmt.setInt(1, bookId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean checkUserReservation(int bookId) {
        if (currentUser == null) return false;
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT COUNT(*) FROM reservations WHERE user_id = ? AND book_id = ? AND status IN ('PENDING', 'READY')")) {
            stmt.setInt(1, currentUser.getUsersId());
            stmt.setInt(2, bookId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @FXML
    private void reserveBook() {
        if (book == null || currentUser == null) {
            showAlert("Error", "Unable to process reservation", Alert.AlertType.ERROR);
            return;
        }

        String sql = "INSERT INTO reservations (user_id, book_id, created_at, status) VALUES (?, ?, ?, 'PENDING')";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, currentUser.getUsersId());
            stmt.setInt(2, book.getBooksId());
            stmt.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            stmt.executeUpdate();

            showAlert("Success", "Book reserved successfully!", Alert.AlertType.INFORMATION);
            updateActionButtons();

        } catch (SQLException e) {
            showAlert("Error", "Failed to reserve book: " + e.getMessage(), Alert.AlertType.ERROR);
            e.printStackTrace();
        }
    }

    @FXML
    private void borrowBook() {
        showAlert("Info", "Please contact a library staff member to borrow this book", Alert.AlertType.INFORMATION);
    }

    @FXML
    private void closePopup() {
        Stage stage = (Stage) closeButton.getScene().getWindow();
        stage.close();
    }

    private void showAlert(String title, String content, Alert.AlertType alertType) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
