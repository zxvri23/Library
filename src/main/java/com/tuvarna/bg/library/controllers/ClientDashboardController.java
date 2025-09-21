package com.tuvarna.bg.library.controllers;

import com.tuvarna.bg.library.entity.*;
import com.tuvarna.bg.library.util.DatabaseUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public class ClientDashboardController implements DashboardController {

    @FXML private Label userLabel;
    @FXML private TextField searchField;
    @FXML private ComboBox<GenreEntity> genreFilterCombo;
    @FXML private ComboBox<String> languageFilterCombo;
    @FXML private GridPane booksGrid;

    @FXML private TableView<LoanEntity> activeLoansTable;
    @FXML private TableColumn<LoanEntity, String> loanBookColumn;
    @FXML private TableColumn<LoanEntity, String> loanBorrowedColumn;
    @FXML private TableColumn<LoanEntity, String> loanDueColumn;
    @FXML private TableColumn<LoanEntity, String> loanStatusColumn;

    @FXML private TableView<LoanEntity> loanHistoryTable;
    @FXML private TableColumn<LoanEntity, String> historyBookColumn;
    @FXML private TableColumn<LoanEntity, String> historyBorrowedColumn;
    @FXML private TableColumn<LoanEntity, String> historyReturnedColumn;
    @FXML private TableColumn<LoanEntity, String> historyStatusColumn;

    @FXML private TableView<ReservationEntity> reservationsTable;
    @FXML private TableColumn<ReservationEntity, String> reservationBookColumn;
    @FXML private TableColumn<ReservationEntity, String> reservationCreatedColumn;
    @FXML private TableColumn<ReservationEntity, String> reservationStatusColumn;

    @FXML private ImageView bookCoverImage;
    @FXML private Label bookTitleLabel, authorLabel, publisherLabel, isbnLabel, languageLabel, yearLabel, genresLabel;
    @FXML private TextArea summaryArea;
    @FXML private Button reserveButton, borrowButton;

    private UserEntity currentUser;
    private BookEntity selectedBook;

    @Override
    public void setCurrentUser(UserEntity user) {
        this.currentUser = user;
        if (userLabel != null && user != null) {
            userLabel.setText("Welcome, " + user.getFullName());
        }
        // Load data only after the user is set
        loadInitialData();
    }

    @Override
    public UserEntity getCurrentUser() {
        return currentUser;
    }

    @FXML
    public void initialize() {
        setupTableColumns();
        setupComboBoxes();
        // DO NOT load data here — currentUser is still null during initialize()
        // loadInitialData();
    }

    private void setupTableColumns() {
        loanBookColumn.setCellValueFactory(new PropertyValueFactory<>("copy"));
        loanBorrowedColumn.setCellValueFactory(new PropertyValueFactory<>("borrowedAt"));
        loanDueColumn.setCellValueFactory(new PropertyValueFactory<>("dueDate"));
        loanStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        historyBookColumn.setCellValueFactory(new PropertyValueFactory<>("copy"));
        historyBorrowedColumn.setCellValueFactory(new PropertyValueFactory<>("borrowedAt"));
        historyReturnedColumn.setCellValueFactory(new PropertyValueFactory<>("returnedAt"));
        historyStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        reservationBookColumn.setCellValueFactory(new PropertyValueFactory<>("book"));
        reservationCreatedColumn.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        reservationStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
    }

    private void setupComboBoxes() {
        loadGenres();
        loadLanguages();
    }

    private void loadInitialData() {
        // Only run once user is set
        refreshBooks();
        refreshLoans();
        refreshReservations();
    }

    @FXML
    private void searchBooks() {
        String searchTerm = searchField.getText() == null ? "" : searchField.getText().trim();
        GenreEntity selectedGenre = genreFilterCombo.getValue();
        String selectedLanguage = languageFilterCombo.getValue();

        try {
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT DISTINCT ")
                    .append("b.books_id, b.title, b.summary, b.isbn, b.language, b.publication_year, ")
                    .append("b.publishers_id, b.image_path, ")
                    .append("p.pub_name ")
                    .append("FROM books b ")
                    .append("JOIN publishers p ON b.publishers_id = p.publishers_id ")
                    .append("LEFT JOIN book_authors ba ON b.books_id = ba.books_id ")
                    .append("LEFT JOIN authors a ON ba.authors_id = a.authors_id ")
                    .append("LEFT JOIN book_genres bg ON b.books_id = bg.books_id ")
                    .append("LEFT JOIN genres g ON bg.genres_id = g.genres_id ")
                    .append("WHERE 1=1 ");

            List<Object> params = new java.util.ArrayList<>();

            if (!searchTerm.isEmpty()) {
                sql.append("AND (b.title ILIKE ? OR b.isbn ILIKE ? OR a.full_name ILIKE ?) ");
                String like = "%" + searchTerm + "%";
                params.add(like);
                params.add(like);
                params.add(like);
            }

            if (selectedGenre != null) {
                sql.append("AND g.genres_id = ? ");
                params.add(selectedGenre.getGenresId());
            }

            if (selectedLanguage != null && !"All languages".equals(selectedLanguage)) {
                sql.append("AND b.language = ? ");
                params.add(selectedLanguage);
            }

            sql.append("ORDER BY b.title");

            try (Connection conn = DatabaseUtil.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

                for (int i = 0; i < params.size(); i++) {
                    stmt.setObject(i + 1, params.get(i));
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    displayBooksInGrid(rs);
                }
            }

        } catch (SQLException e) {
            showAlert("Error", "Failed to search books: " + e.getMessage(), Alert.AlertType.ERROR);
            e.printStackTrace();
        }
    }

    private void displayBooksInGrid(ResultSet rs) throws SQLException {
        booksGrid.getChildren().clear();
        int row = 0, col = 0, maxCols = 4;

        while (rs.next()) {
            BookEntity book = new BookEntity();
            book.setBooksId(rs.getInt("books_id"));
            book.setTitle(rs.getString("title"));
            book.setSummary(safeGetString(rs, "summary"));
            book.setIsbn(rs.getString("isbn"));
            book.setLanguage(safeGetString(rs, "language"));
            book.setPublicationYear(rs.getInt("publication_year"));

            // image_path is optional (guarded)
            String imagePath = null;
            if (hasColumn(rs, "image_path")) {
                imagePath = rs.getString("image_path");
            }
            book.setImagePath(imagePath);

            VBox bookCard = createBookCard(book);
            booksGrid.add(bookCard, col, row);

            col++;
            if (col >= maxCols) { col = 0; row++; }
        }
    }

    private VBox createBookCard(BookEntity book) {
        VBox card = new VBox(10);
        card.getStyleClass().add("book-card");
        card.setPrefWidth(150);
        card.setPrefHeight(200);

        ImageView coverImage = new ImageView();
        coverImage.getStyleClass().add("book-image");
        coverImage.setFitWidth(120);
        coverImage.setFitHeight(160);

        if (book.getImagePath() != null && !book.getImagePath().isEmpty()) {
            try {
                File imageFile = new File(book.getImagePath());
                if (imageFile.exists()) {
                    coverImage.setImage(new Image(imageFile.toURI().toString()));
                }
            } catch (Exception ignored) {}
        }

        Label titleLabel = new Label(book.getTitle());
        titleLabel.getStyleClass().add("book-title");
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(120);

        Label authorLabel = new Label("Author");
        authorLabel.getStyleClass().add("book-author");

        card.getChildren().addAll(coverImage, titleLabel, authorLabel);
        card.setOnMouseClicked(event -> showBookDetails(book));
        return card;
    }

    private void showBookDetails(BookEntity book) {
        this.selectedBook = book;

        bookTitleLabel.setText(book.getTitle());
        authorLabel.setText("Author Name");
        publisherLabel.setText("Publisher Name");
        isbnLabel.setText(book.getIsbn());
        languageLabel.setText(book.getLanguage());
        yearLabel.setText(book.getPublicationYear() != null ? book.getPublicationYear().toString() : "N/A");
        genresLabel.setText("Genre List");
        summaryArea.setText(book.getSummary());

        if (book.getImagePath() != null && !book.getImagePath().isEmpty()) {
            try {
                File imageFile = new File(book.getImagePath());
                if (imageFile.exists()) {
                    bookCoverImage.setImage(new Image(imageFile.toURI().toString()));
                } else {
                    bookCoverImage.setImage(null);
                }
            } catch (Exception ignored) {
                bookCoverImage.setImage(null);
            }
        } else {
            bookCoverImage.setImage(null);
        }
        updateBookActionButtons(book);
    }

    private void updateBookActionButtons(BookEntity book) {
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
        if (selectedBook == null) {
            showAlert("Warning", "Please select a book first", Alert.AlertType.WARNING);
            return;
        }
        if (currentUser == null) {
            showAlert("Error", "No current user in session.", Alert.AlertType.ERROR);
            return;
        }

        String sql = "INSERT INTO reservations (user_id, book_id, created_at, status) VALUES (?, ?, ?, 'PENDING')";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, currentUser.getUsersId());
            stmt.setInt(2, selectedBook.getBooksId());
            stmt.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            stmt.executeUpdate();

            showAlert("Success", "Book reserved successfully!", Alert.AlertType.INFORMATION);
            refreshReservations();
            updateBookActionButtons(selectedBook);

        } catch (SQLException e) {
            showAlert("Error", "Failed to reserve book: " + e.getMessage(), Alert.AlertType.ERROR);
            e.printStackTrace();
        }
    }

    @FXML
    private void borrowBook() {
        if (selectedBook == null) {
            showAlert("Warning", "Please select a book first", Alert.AlertType.WARNING);
            return;
        }
        showAlert("Info", "Please contact a library staff member to borrow this book", Alert.AlertType.INFORMATION);
    }

    @FXML
    private void clearFilters() {
        searchField.clear();
        genreFilterCombo.setValue(null);
        languageFilterCombo.setValue(null);
        searchBooks();
    }

    private void loadGenres() {
        try (Connection conn = DatabaseUtil.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT genres_id, gen_name FROM genres ORDER BY gen_name")) {

            ObservableList<GenreEntity> genres = FXCollections.observableArrayList();
            while (rs.next()) {
                GenreEntity genre = new GenreEntity();
                genre.setGenresId(rs.getInt("genres_id"));
                genre.setGenreName(rs.getString("gen_name"));
                genres.add(genre);
            }
            genreFilterCombo.setItems(genres);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadLanguages() {
        ObservableList<String> languages = FXCollections.observableArrayList(
                "All languages", "English", "Spanish", "French", "German", "Italian"
        );
        languageFilterCombo.setItems(languages);
        languageFilterCombo.setValue("All languages");
    }

    private void refreshBooks() { searchBooks(); }

    private void refreshLoans() {
        if (currentUser == null) return;
        loadActiveLoans();
        loadLoanHistory();
    }

    private void refreshReservations() {
        if (currentUser == null) return;
        loadUserReservations();
    }

    private void loadActiveLoans() {
        if (currentUser == null) return;
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT * FROM loans WHERE users_id = ? AND returned_at IS NULL ORDER BY borrowed_at DESC")) {

            stmt.setInt(1, currentUser.getUsersId());
            try (ResultSet rs = stmt.executeQuery()) {
                ObservableList<LoanEntity> loans = FXCollections.observableArrayList();
                while (rs.next()) {
                    LoanEntity loan = new LoanEntity();
                    loan.setLoansId(rs.getInt("loans_id"));
                    Timestamp bAt = rs.getTimestamp("borrowed_at");
                    Date dDate = rs.getDate("due_date");
                    loan.setBorrowedAt(bAt != null ? bAt.toLocalDateTime() : null);
                    loan.setDueDate(dDate != null ? dDate.toLocalDate() : null);

                    BookCopyEntity copy = loadBookCopy(rs.getInt("copy_id"));
                    loan.setCopy(copy);
                    loans.add(loan);
                }
                activeLoansTable.setItems(loans);
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void loadLoanHistory() {
        if (currentUser == null) return;
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT * FROM loans WHERE users_id = ? AND returned_at IS NOT NULL ORDER BY returned_at DESC")) {

            stmt.setInt(1, currentUser.getUsersId());
            try (ResultSet rs = stmt.executeQuery()) {
                ObservableList<LoanEntity> loans = FXCollections.observableArrayList();
                while (rs.next()) {
                    LoanEntity loan = new LoanEntity();
                    loan.setLoansId(rs.getInt("loans_id"));
                    Timestamp bAt = rs.getTimestamp("borrowed_at");
                    Timestamp rAt = rs.getTimestamp("returned_at");
                    loan.setBorrowedAt(bAt != null ? bAt.toLocalDateTime() : null);
                    loan.setReturnedAt(rAt != null ? rAt.toLocalDateTime() : null);

                    BookCopyEntity copy = loadBookCopy(rs.getInt("copy_id"));
                    loan.setCopy(copy);
                    loans.add(loan);
                }
                loanHistoryTable.setItems(loans);
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void loadUserReservations() {
        if (currentUser == null) return;
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT * FROM reservations WHERE user_id = ? ORDER BY created_at DESC")) {

            stmt.setInt(1, currentUser.getUsersId());
            try (ResultSet rs = stmt.executeQuery()) {
                ObservableList<ReservationEntity> reservations = FXCollections.observableArrayList();
                while (rs.next()) {
                    ReservationEntity reservation = new ReservationEntity();
                    reservation.setReservationsId(rs.getInt("reservations_id"));
                    Timestamp cAt = rs.getTimestamp("created_at");
                    reservation.setCreatedAt(cAt != null ? cAt.toLocalDateTime() : null);
                    reservation.setStatus(rs.getString("status"));

                    BookEntity book = loadBook(rs.getInt("book_id"));
                    reservation.setBook(book);
                    reservations.add(reservation);
                }
                reservationsTable.setItems(reservations);
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private BookEntity loadBook(int bookId) throws SQLException {
        String sql = "SELECT books_id, title, summary, isbn, language, publication_year, image_path FROM books WHERE books_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, bookId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    BookEntity book = new BookEntity();
                    book.setBooksId(rs.getInt("books_id"));
                    book.setTitle(rs.getString("title"));
                    book.setSummary(safeGetString(rs, "summary"));
                    book.setIsbn(rs.getString("isbn"));
                    book.setLanguage(safeGetString(rs, "language"));
                    book.setPublicationYear(rs.getInt("publication_year"));
                    if (hasColumn(rs, "image_path")) {
                        book.setImagePath(rs.getString("image_path"));
                    }
                    return book;
                }
            }
        }
        return null;
    }

    private BookCopyEntity loadBookCopy(int copyId) throws SQLException {
        String sql = "SELECT copies_id, books_id, status FROM book_copies WHERE copies_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, copyId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    BookCopyEntity copy = new BookCopyEntity();
                    copy.setCopiesId(rs.getInt("copies_id"));
                    copy.setStatus(rs.getString("status"));

                    BookEntity book = loadBook(rs.getInt("books_id"));
                    copy.setBook(book);
                    return copy;
                }
            }
        }
        return null;
    }

    @FXML
    private void handleLogout() {
        try {
            // Reuse the same window
            Stage stage = (Stage) userLabel.getScene().getWindow();

            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/tuvarna/bg/library/view/login-view.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root);
            URL css = getClass().getResource("/com/tuvarna/bg/library/css/styles.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());

            stage.setScene(scene);

            // Force maximized login
            stage.setMaximized(true);
            stage.setResizable(false);
            stage.centerOnScreen();

            // ESC on login ⇒ exit app
            scene.setOnKeyPressed(e -> {
                if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                    javafx.application.Platform.exit();
                }
            });

            // Re-wire custom chrome on the new scene
            Button closeBtn = (Button) root.lookup("#closeButton");
            if (closeBtn != null) closeBtn.setOnAction(e -> javafx.application.Platform.exit());
            Button minimizeBtn = (Button) root.lookup("#minimizeButton");
            if (minimizeBtn != null) minimizeBtn.setOnAction(e -> stage.setIconified(true));

        } catch (Exception ex) {
            ex.printStackTrace();
            // Optional: show an alert to the user
            // new Alert(Alert.AlertType.ERROR, "Failed to load login screen:\n" + ex.getMessage()).showAndWait();
        }
    }


    private void showAlert(String title, String content, Alert.AlertType alertType) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    // ---------- small helpers ----------

    private static boolean hasColumn(ResultSet rs, String col) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        for (int i = 1; i <= md.getColumnCount(); i++) {
            if (col.equalsIgnoreCase(md.getColumnName(i))) return true;
        }
        return false;
    }

    private static String safeGetString(ResultSet rs, String col) throws SQLException {
        return hasColumn(rs, col) ? rs.getString(col) : null;
    }
}
