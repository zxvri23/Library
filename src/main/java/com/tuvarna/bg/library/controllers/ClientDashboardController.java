package com.tuvarna.bg.library.controllers;

import com.tuvarna.bg.library.entity.*;
import com.tuvarna.bg.library.util.DatabaseUtil;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.cell.PropertyValueFactory;

import java.io.File;
import java.io.IOException;
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
        userLabel.setText("Welcome, " + user.getFullName());
        initializeData();
    }

    @Override
    public UserEntity getCurrentUser() {
        return currentUser;
    }

    @FXML
    public void initialize() {
        setupTableColumns();
        setupComboBoxes();
        loadInitialData();
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
        refreshBooks();
        refreshLoans();
        refreshReservations();
    }

    @FXML
    private void searchBooks() {
        String searchTerm = searchField.getText().trim();
        GenreEntity selectedGenre = genreFilterCombo.getValue();
        String selectedLanguage = languageFilterCombo.getValue();

        try {
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT DISTINCT b.*, p.pub_name FROM books b ");
            sql.append("JOIN publishers p ON b.publishers_id = p.publishers_id ");
            sql.append("LEFT JOIN book_authors ba ON b.books_id = ba.books_id ");
            sql.append("LEFT JOIN authors a ON ba.authors_id = a.authors_id ");
            sql.append("LEFT JOIN book_genres bg ON b.books_id = bg.books_id ");
            sql.append("LEFT JOIN genres g ON bg.genres_id = g.genres_id ");
            sql.append("WHERE 1=1 ");

            List<Object> params = new java.util.ArrayList<>();
            int paramIndex = 1;

            if (!searchTerm.isEmpty()) {
                sql.append("AND (b.title ILIKE ? OR b.isbn ILIKE ? OR a.full_name ILIKE ?) ");
                String likePattern = "%" + searchTerm + "%";
                params.add(likePattern);
                params.add(likePattern);
                params.add(likePattern);
                paramIndex += 3;
            }

            if (selectedGenre != null) {
                sql.append("AND g.genres_id = ? ");
                params.add(selectedGenre.getGenresId());
                paramIndex++;
            }

            if (selectedLanguage != null && !selectedLanguage.equals("All languages")) {
                sql.append("AND b.language = ? ");
                params.add(selectedLanguage);
                paramIndex++;
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
        int row = 0;
        int col = 0;
        int maxCols = 4;

        while (rs.next()) {
            BookEntity book = new BookEntity();
            book.setBooksId(rs.getInt("books_id"));
            book.setTitle(rs.getString("title"));
            book.setIsbn(rs.getString("isbn"));
            book.setLanguage(rs.getString("language"));
            book.setPublicationYear(rs.getInt("publication_year"));
            book.setImagePath(rs.getString("image_path"));

            VBox bookCard = createBookCard(book);

            booksGrid.add(bookCard, col, row);

            col++;
            if (col >= maxCols) {
                col = 0;
                row++;
            }
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
                    Image image = new Image(imageFile.toURI().toString());
                    coverImage.setImage(image);
                }
            } catch (Exception e) {
            }
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
                    Image image = new Image(imageFile.toURI().toString());
                    bookCoverImage.setImage(image);
                }
            } catch (Exception e) {
            }
        }
        updateBookActionButtons(book);
    }

    private void updateBookActionButtons(BookEntity book) {
        boolean hasAvailableCopies = checkBookAvailability(book.getBooksId());
        boolean hasReservation = checkUserReservation(book.getBooksId());

        reserveButton.setDisable(hasReservation);
        borrowButton.setDisable(!hasAvailableCopies);

        if (hasReservation) {
            reserveButton.setText("Already Reserved");
        } else {
            reserveButton.setText("Reserve Book");
        }

        if (hasAvailableCopies) {
            borrowButton.setText("Borrow Available Copy");
        } else {
            borrowButton.setText("No Copies Available");
        }
    }

    private boolean checkBookAvailability(int bookId) {
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT COUNT(*) FROM book_copies WHERE books_id = ? AND status = 'AVAILABLE'")) {

            stmt.setInt(1, bookId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean checkUserReservation(int bookId) {
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT COUNT(*) FROM reservations WHERE user_id = ? AND book_id = ? AND status IN ('PENDING', 'READY')")) {

            stmt.setInt(1, currentUser.getUsersId());
            stmt.setInt(2, bookId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    @FXML
    private void reserveBook() {
        if (selectedBook == null) {
            showAlert("Warning", "Please select a book first", Alert.AlertType.WARNING);
            return;
        }

        try {
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
            }

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
        searchBooks(); // Refresh with no filters
    }

    private void loadGenres() {
        try (Connection conn = DatabaseUtil.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM genres ORDER BY gen_name")) {

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
        ObservableList<String> languages = FXCollections.observableArrayList("All languages", "English", "Spanish", "French", "German", "Italian");
        languageFilterCombo.setItems(languages);
        languageFilterCombo.setValue("All languages");
    }

    private void refreshBooks() {
        searchBooks(); // Initial load
    }

    private void refreshLoans() {
        loadActiveLoans();
        loadLoanHistory();
    }

    private void refreshReservations() {
        loadUserReservations();
    }

    private void loadActiveLoans() {
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT * FROM loans WHERE users_id = ? AND returned_at IS NULL ORDER BY borrowed_at DESC")) {

            stmt.setInt(1, currentUser.getUsersId());
            try (ResultSet rs = stmt.executeQuery()) {

                ObservableList<LoanEntity> loans = FXCollections.observableArrayList();
                while (rs.next()) {
                    LoanEntity loan = new LoanEntity();
                    loan.setLoansId(rs.getInt("loans_id"));
                    loan.setBorrowedAt(rs.getTimestamp("borrowed_at") != null ?
                            rs.getTimestamp("borrowed_at").toLocalDateTime() : null);
                    loan.setDueDate(rs.getDate("due_date") != null ?
                            rs.getDate("due_date").toLocalDate() : null);

                    // Load book copy information
                    BookCopyEntity copy = loadBookCopy(rs.getInt("copy_id"));
                    loan.setCopy(copy);

                    loans.add(loan);
                }

                activeLoansTable.setItems(loans);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadLoanHistory() {
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT * FROM loans WHERE users_id = ? AND returned_at IS NOT NULL ORDER BY returned_at DESC")) {

            stmt.setInt(1, currentUser.getUsersId());
            try (ResultSet rs = stmt.executeQuery()) {

                ObservableList<LoanEntity> loans = FXCollections.observableArrayList();
                while (rs.next()) {
                    LoanEntity loan = new LoanEntity();
                    loan.setLoansId(rs.getInt("loans_id"));
                    loan.setBorrowedAt(rs.getTimestamp("borrowed_at") != null ?
                            rs.getTimestamp("borrowed_at").toLocalDateTime() : null);
                    loan.setReturnedAt(rs.getTimestamp("returned_at") != null ?
                            rs.getTimestamp("returned_at").toLocalDateTime() : null);

                    // Load book copy information
                    BookCopyEntity copy = loadBookCopy(rs.getInt("copy_id"));
                    loan.setCopy(copy);

                    loans.add(loan);
                }

                loanHistoryTable.setItems(loans);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadUserReservations() {
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT * FROM reservations WHERE user_id = ? ORDER BY created_at DESC")) {

            stmt.setInt(1, currentUser.getUsersId());
            try (ResultSet rs = stmt.executeQuery()) {

                ObservableList<ReservationEntity> reservations = FXCollections.observableArrayList();
                while (rs.next()) {
                    ReservationEntity reservation = new ReservationEntity();
                    reservation.setReservationsId(rs.getInt("reservations_id"));
                    reservation.setCreatedAt(rs.getTimestamp("created_at") != null ?
                            rs.getTimestamp("created_at").toLocalDateTime() : null);
                    reservation.setStatus(rs.getString("status"));

                    // Load book information
                    BookEntity book = loadBook(rs.getInt("book_id"));
                    reservation.setBook(book);

                    reservations.add(reservation);
                }

                reservationsTable.setItems(reservations);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private BookEntity loadBook(int bookId) throws SQLException {
        String sql = "SELECT * FROM books WHERE books_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, bookId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    BookEntity book = new BookEntity();
                    book.setBooksId(rs.getInt("books_id"));
                    book.setTitle(rs.getString("title"));
                    book.setIsbn(rs.getString("isbn"));
                    return book;
                }
            }
        }
        return null;
    }

    private BookCopyEntity loadBookCopy(int copyId) throws SQLException {
        String sql = "SELECT * FROM book_copies WHERE copies_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, copyId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    BookCopyEntity copy = new BookCopyEntity();
                    copy.setCopiesId(rs.getInt("copies_id"));
                    copy.setStatus(rs.getString("status"));

                    // Load book information
                    BookEntity book = loadBook(rs.getInt("books_id"));
                    copy.setBook(book);

                    return copy;
                }
            }
        }
        return null;
    }

    private void initializeData() {
    }

    @FXML
    private void handleLogout() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/tuvarna/bg/library/view/LoginView.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root);
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/com/tuvarna/bg/library/css/styles.css")).toExternalForm());

            Stage stage = new Stage();
            stage.setTitle("Library Management System - Login");
            stage.setScene(scene);
            stage.initStyle(StageStyle.UNDECORATED);
            stage.setResizable(false);

            // Close current window
            ((Stage) userLabel.getScene().getWindow()).close();

            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showAlert(String title, String content, Alert.AlertType alertType) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}