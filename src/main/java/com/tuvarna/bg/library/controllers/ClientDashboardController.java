    package com.tuvarna.bg.library.controllers;

    import com.tuvarna.bg.library.entity.*;
    import com.tuvarna.bg.library.util.DatabaseUtil;
    import javafx.beans.property.SimpleStringProperty;
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
    import java.sql.Date;
    import java.time.LocalDate;
    import java.time.format.DateTimeFormatter;
    import java.util.*;

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

        private final List<BookEntity> gridBooks = new ArrayList<>();
        private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        @Override
        public void setCurrentUser(UserEntity user) {
            this.currentUser = user;
            if (userLabel != null && user != null) {
                userLabel.setText("Welcome, " + user.getFullName());
            }
            loadInitialData();
        }

        @Override public UserEntity getCurrentUser() { return currentUser; }

        @FXML
        public void initialize() {
            setupTables();       // fit columns + status
            setupComboBoxes();   // dynamic filters
            // Responsive grid: recalc when width changes
            booksGrid.widthProperty().addListener((obs, ov, nv) -> layoutBooksGrid());
        }

        /* ---------- TABLES: fill width, remove actions, format/status ---------- */
        private void setupTables() {
            // Loans table fits width
            activeLoansTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
            loanBookColumn.setMaxWidth(1f * Integer.MAX_VALUE * 3);
            loanBorrowedColumn.setMaxWidth(1f * Integer.MAX_VALUE * 2);
            loanDueColumn.setMaxWidth(1f * Integer.MAX_VALUE * 1);
            loanStatusColumn.setMaxWidth(1f * Integer.MAX_VALUE * 1);

            // Remove any Actions column if present
            removeActionsColumn(activeLoansTable);
            removeActionsColumn(reservationsTable);

            // Friendly value factories for loans
            loanBookColumn.setCellValueFactory(cd -> {
                BookCopyEntity copy = cd.getValue().getCopy();
                String s = "";
                if (copy != null) {
                    BookEntity b = copy.getBook();
                    if (b != null && b.getTitle() != null && !b.getTitle().isEmpty()) s = b.getTitle();
                    else s = "Copy #" + copy.getCopiesId();
                }
                return new SimpleStringProperty(s);
            });
            loanBorrowedColumn.setCellValueFactory(cd ->
                    new SimpleStringProperty(cd.getValue().getBorrowedAt() == null ? "" : DATETIME_FMT.format(cd.getValue().getBorrowedAt())));
            loanDueColumn.setCellValueFactory(cd ->
                    new SimpleStringProperty(cd.getValue().getDueDate() == null ? "" : DATE_FMT.format(cd.getValue().getDueDate())));
            loanStatusColumn.setCellValueFactory(cd -> {
                LoanEntity l = cd.getValue();
                String status;
                if (l.getReturnedAt() != null) status = "Returned";
                else {
                    LocalDate d = l.getDueDate();
                    status = (d != null && d.isBefore(LocalDate.now())) ? "Overdue" : "Active";
                }
                return new SimpleStringProperty(status);
            });
            loanStatusColumn.setCellFactory(col -> new TableCell<LoanEntity, String>() {
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) { setText(null); setStyle(""); return; }
                    setText(item);
                    switch (item) {
                        case "Overdue": setStyle("-fx-text-fill:#e74c3c;-fx-font-weight:bold;"); break;
                        case "Returned": setStyle("-fx-text-fill:#7f8c8d;"); break;
                        default: setStyle("-fx-text-fill:#27ae60;");
                    }
                }
            });

            // Reservations table fits width
            reservationsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
            reservationBookColumn.setMaxWidth(1f * Integer.MAX_VALUE * 3);
            reservationCreatedColumn.setMaxWidth(1f * Integer.MAX_VALUE * 2);
            reservationStatusColumn.setMaxWidth(1f * Integer.MAX_VALUE * 1);

            reservationBookColumn.setCellValueFactory(cd -> {
                BookEntity b = cd.getValue().getBook();
                return new SimpleStringProperty(b == null ? "" : (b.getTitle() != null ? b.getTitle() : ("Book #" + b.getBooksId())));
            });
            reservationCreatedColumn.setCellValueFactory(cd ->
                    new SimpleStringProperty(cd.getValue().getCreatedAt() == null ? "" : DATETIME_FMT.format(cd.getValue().getCreatedAt())));
            reservationStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        }

        private static void removeActionsColumn(TableView<?> table) {
            if (table == null || table.getColumns() == null) return;
            table.getColumns().removeIf(c -> {
                String t = c.getText();
                return t != null && t.trim().toLowerCase().startsWith("acti"); // "Action", "Actions", truncated header, etc.
            });
        }

        /* ---------- FILTERS: genres from DB; languages dynamic & case-insensitive ---------- */
        private void setupComboBoxes() {
            loadGenres();
            loadLanguages(); // dynamic
        }

        private void loadGenres() {
            try (Connection conn = DatabaseUtil.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT genres_id, gen_name FROM genres ORDER BY gen_name")) {

                ObservableList<GenreEntity> genres = FXCollections.observableArrayList();
                while (rs.next()) {
                    GenreEntity g = new GenreEntity();
                    g.setGenresId(rs.getInt("genres_id"));
                    g.setGenreName(rs.getString("gen_name"));
                    genres.add(g);
                }
                genreFilterCombo.setItems(genres);
            } catch (SQLException e) { e.printStackTrace(); }
        }

        private void loadLanguages() {
            // Keep current selection if possible
            String current = languageFilterCombo.getValue();

            ObservableList<String> langs = FXCollections.observableArrayList();
            langs.add("All languages");

            String sql =
                    "SELECT MIN(TRIM(language)) AS lang " +
                            "FROM books " +
                            "WHERE language IS NOT NULL AND TRIM(language) <> '' " +
                            "GROUP BY LOWER(TRIM(language)) " +          // case-insensitive distinct
                            "ORDER BY MIN(TRIM(language)) ASC";

            try (Connection conn = DatabaseUtil.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                while (rs.next()) {
                    String lang = rs.getString("lang");
                    if (lang != null && !lang.isBlank()) {
                        langs.add(lang.trim());
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            languageFilterCombo.setItems(langs);

            // Restore selection if still present; otherwise default to "All languages"
            if (current != null && langs.stream().anyMatch(s -> s.equalsIgnoreCase(current))) {
                // re-select with the canonical value from list
                languageFilterCombo.setValue(
                        langs.stream().filter(s -> s.equalsIgnoreCase(current)).findFirst().orElse("All languages"));
            } else {
                languageFilterCombo.setValue("All languages");
            }
        }

        /* ---------- INITIAL LOAD ---------- */
        private void loadInitialData() {
            refreshBooks();
            refreshLoans();
            refreshReservations();
        }

        /* ---------- SEARCH + RESPONSIVE GRID ---------- */
        @FXML
        private void searchBooks() {
            String searchTerm = searchField.getText() == null ? "" : searchField.getText().trim();
            GenreEntity selectedGenre = genreFilterCombo.getValue();
            String selectedLanguage = languageFilterCombo.getValue();

            try {
                StringBuilder sql = new StringBuilder();
                sql.append("SELECT DISTINCT ")
                        .append("b.books_id, b.title, b.summary, b.isbn, b.language, b.publication_year, ")
                        .append("b.publishers_id, b.image_path ")
                        .append("FROM books b ")
                        .append("LEFT JOIN book_authors ba ON b.books_id = ba.books_id ")
                        .append("LEFT JOIN authors a ON ba.authors_id = a.authors_id ")
                        .append("LEFT JOIN book_genres bg ON b.books_id = bg.books_id ")
                        .append("LEFT JOIN genres g ON bg.genres_id = g.genres_id ")
                        .append("WHERE 1=1 ");

                List<Object> params = new ArrayList<>();

                if (!searchTerm.isEmpty()) {
                    sql.append("AND (b.title ILIKE ? OR b.isbn ILIKE ? OR a.full_name ILIKE ?) ");
                    String like = "%" + searchTerm + "%";
                    params.add(like); params.add(like); params.add(like);
                }

                if (selectedGenre != null) {
                    sql.append("AND g.genres_id = ? ");
                    params.add(selectedGenre.getGenresId());
                }

                if (selectedLanguage != null && !"All languages".equals(selectedLanguage)) {
                    sql.append("AND LOWER(b.language) = LOWER(?) "); // case-insensitive match
                    params.add(selectedLanguage);
                }

                sql.append("ORDER BY b.title");

                try (Connection conn = DatabaseUtil.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

                    for (int i = 0; i < params.size(); i++) stmt.setObject(i + 1, params.get(i));

                    try (ResultSet rs = stmt.executeQuery()) {
                        // Build list then layout (for responsive re-render)
                        gridBooks.clear();
                        while (rs.next()) {
                            BookEntity b = new BookEntity();
                            b.setBooksId(rs.getInt("books_id"));
                            b.setTitle(rs.getString("title"));
                            b.setSummary(safeGetString(rs, "summary"));
                            b.setIsbn(rs.getString("isbn"));
                            b.setLanguage(safeGetString(rs, "language"));
                            b.setPublicationYear(rs.getInt("publication_year"));
                            if (hasColumn(rs, "image_path")) b.setImagePath(rs.getString("image_path"));
                            gridBooks.add(b);
                        }
                        layoutBooksGrid();
                    }
                }

            } catch (SQLException e) {
                showAlert("Error", "Failed to search books: " + e.getMessage(), Alert.AlertType.ERROR);
                e.printStackTrace();
            }
        }

        private void layoutBooksGrid() {
            if (booksGrid == null) return;
            booksGrid.getChildren().clear();

            // Card metrics
            double cardW = 180;     // preferred card width
            double gaps  = 16;      // assumed HGap
            double pad   = 32;      // approximate padding inside container

            double available = Math.max(booksGrid.getWidth() - pad, 600); // fallback if width not yet measured
            int maxCols = (int) Math.floor(available / (cardW + gaps));
            if (maxCols < 3) maxCols = 3;     // minimum 3 per row
            if (maxCols > 8) maxCols = 8;     // cap for readability

            int row = 0, col = 0;
            for (BookEntity b : gridBooks) {
                VBox card = createBookCard(b);
                booksGrid.add(card, col, row);
                col++;
                if (col >= maxCols) { col = 0; row++; }
            }
        }

        private VBox createBookCard(BookEntity book) {
            VBox card = new VBox(10);
            card.getStyleClass().add("book-card");
            card.setPrefWidth(180);
            card.setPrefHeight(260);

            ImageView coverImage = new ImageView();
            coverImage.getStyleClass().add("book-image");
            coverImage.setFitWidth(140);
            coverImage.setFitHeight(190);
            coverImage.setPreserveRatio(true);

            // ✅ load robustly
            Image cover = findCover(book, 140);
            if (cover != null) coverImage.setImage(cover);

            Label titleLabel = new Label(book.getTitle());
            titleLabel.getStyleClass().add("book-title");
            titleLabel.setWrapText(true);
            titleLabel.setMaxWidth(160);

            Label authorLbl = new Label(authorsLine(book.getBooksId()));
            authorLbl.getStyleClass().add("book-author");

            card.getChildren().addAll(coverImage, titleLabel, authorLbl);
            card.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) showBookDetailsPopup(book);
                else if (event.getClickCount() == 1) showBookDetails(book);
            });
            return card;
        }

        /* ---------- DETAILS & ACTIONS ---------- */
        private void showBookDetails(BookEntity book) {
            this.selectedBook = book;

            bookTitleLabel.setText(book.getTitle());
            authorLabel.setText(authorsLine(book.getBooksId()));             publisherLabel.setText("Publisher Name");
            isbnLabel.setText(book.getIsbn());
            languageLabel.setText(book.getLanguage());
            yearLabel.setText(book.getPublicationYear() != null ? book.getPublicationYear().toString() : "N/A");
            genresLabel.setText("Genre List");
            summaryArea.setText(book.getSummary());

            if (book.getImagePath() != null && !book.getImagePath().isEmpty()) {
                try {
                    File imageFile = new File(book.getImagePath());
                    bookCoverImage.setImage(imageFile.exists() ? new Image(imageFile.toURI().toString()) : null);
                } catch (Exception ignored) { bookCoverImage.setImage(null); }
            } else bookCoverImage.setImage(null);

            updateBookActionButtons(book);
        }

        private void showBookDetailsPopup(BookEntity book) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/tuvarna/bg/library/view/book-details-popup.fxml"));
                Parent root = loader.load();

                BookDetailsPopUpController controller = loader.getController();
                controller.setBook(book);
                controller.setCurrentUser(currentUser);

                Scene scene = new Scene(root);
                scene.getStylesheets().add(Objects.requireNonNull(
                        getClass().getResource("/com/tuvarna/bg/library/css/styles.css")).toExternalForm());

                Stage popupStage = new Stage();
                popupStage.setTitle("Book Details - " + book.getTitle());
                popupStage.setScene(scene);
                popupStage.initStyle(StageStyle.UNDECORATED);
                popupStage.setResizable(false);
                popupStage.centerOnScreen();

                // A bit bigger
                popupStage.setWidth(1000);
                popupStage.setHeight(700);

                popupStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
                popupStage.showAndWait();

            } catch (IOException e) {
                showAlert("Error", "Failed to open book details: " + e.getMessage(), Alert.AlertType.ERROR);
                e.printStackTrace();
            }
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
                try (ResultSet rs = stmt.executeQuery()) { return rs.next() && rs.getInt(1) > 0; }
            } catch (SQLException e) { e.printStackTrace(); return false; }
        }

        private boolean checkUserReservation(int bookId) {
            if (currentUser == null) return false;
            try (Connection conn = DatabaseUtil.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT COUNT(*) FROM reservations WHERE user_id = ? AND book_id = ? AND status IN ('PENDING', 'READY')")) {
                stmt.setInt(1, currentUser.getUsersId());
                stmt.setInt(2, bookId);
                try (ResultSet rs = stmt.executeQuery()) { return rs.next() && rs.getInt(1) > 0; }
            } catch (SQLException e) { e.printStackTrace(); return false; }
        }

        @FXML
        private void clearFilters() {
            searchField.clear();
            genreFilterCombo.setValue(null);
            languageFilterCombo.setValue("All languages");
            searchBooks();
        }

        /* ---------- REFRESH HELPERS ---------- */
        private void refreshBooks() {
            loadLanguages();   // <-- ensure combo reflects DB now
            searchBooks();
        }
        // at top of ClientDashboardController
        private final Map<Integer, String> authorsCache = new HashMap<>();

        private String authorsLine(int bookId) {
            if (authorsCache.containsKey(bookId)) return authorsCache.get(bookId);
            String sql = """
        SELECT COALESCE(
                 STRING_AGG(DISTINCT a.full_name, ', ' ORDER BY a.full_name),
                 'Unknown Author'
               ) AS authors
        FROM book_authors ba
        LEFT JOIN authors a ON a.authors_id = ba.authors_id
        WHERE ba.books_id = ?
    """;
            try (Connection c = DatabaseUtil.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, bookId);
                try (ResultSet rs = ps.executeQuery()) {
                    String s = (rs.next() ? rs.getString("authors") : "Unknown Author");
                    if (s == null || s.isBlank()) s = "Unknown Author";
                    authorsCache.put(bookId, s);
                    return s;
                }
            } catch (SQLException e) {
                e.printStackTrace();
                return "Unknown Author";
            }
        }

        private void refreshLoans() {
            if (currentUser == null) return;
            loadActiveLoans();
        }

        private void refreshReservations() {
            if (currentUser == null) return;
            loadUserReservations();
        }

        private void loadActiveLoans() {
            if (currentUser == null) return;
            try (Connection conn = DatabaseUtil.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT loans_id, users_id, copy_id, borrowed_at, due_date, returned_at " +
                                 "FROM loans WHERE users_id = ? ORDER BY borrowed_at DESC")) {

                stmt.setInt(1, currentUser.getUsersId());
                try (ResultSet rs = stmt.executeQuery()) {
                    ObservableList<LoanEntity> loans = FXCollections.observableArrayList();
                    while (rs.next()) {
                        LoanEntity loan = new LoanEntity();
                        loan.setLoansId(rs.getInt("loans_id"));
                        Timestamp bAt = rs.getTimestamp("borrowed_at");
                        Date dDate = rs.getDate("due_date");
                        Timestamp rAt = rs.getTimestamp("returned_at");
                        loan.setBorrowedAt(bAt != null ? bAt.toLocalDateTime() : null);
                        loan.setDueDate(dDate != null ? dDate.toLocalDate() : null);
                        loan.setReturnedAt(rAt != null ? rAt.toLocalDateTime() : null);

                        BookCopyEntity copy = loadBookCopy(rs.getInt("copy_id"));
                        loan.setCopy(copy);
                        loans.add(loan);
                    }
                    activeLoansTable.setItems(loans);
                }
            } catch (SQLException e) { e.printStackTrace(); }
        }

        private void loadUserReservations() {
            if (currentUser == null) return;
            try (Connection conn = DatabaseUtil.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT reservations_id, user_id, book_id, created_at, status " +
                                 "FROM reservations WHERE user_id = ? ORDER BY created_at DESC")) {

                stmt.setInt(1, currentUser.getUsersId());
                try (ResultSet rs = stmt.executeQuery()) {
                    ObservableList<ReservationEntity> reservations = FXCollections.observableArrayList();
                    while (rs.next()) {
                        ReservationEntity r = new ReservationEntity();
                        r.setReservationsId(rs.getInt("reservations_id"));
                        Timestamp cAt = rs.getTimestamp("created_at");
                        r.setCreatedAt(cAt != null ? cAt.toLocalDateTime() : null);
                        r.setStatus(rs.getString("status"));

                        BookEntity book = loadBook(rs.getInt("book_id"));
                        r.setBook(book);
                        reservations.add(r);
                    }
                    reservationsTable.setItems(reservations);
                }
            } catch (SQLException e) { e.printStackTrace(); }
        }

        /* ---------- DB helpers ---------- */
        private BookEntity loadBook(int bookId) throws SQLException {
            String sql = "SELECT books_id, title, summary, isbn, language, publication_year, image_path " +
                    "FROM books WHERE books_id = ?";
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
                        if (hasColumn(rs, "image_path")) book.setImagePath(rs.getString("image_path"));
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

        /* ---------- Logout & utils ---------- */
        @FXML
        private void handleLogout() {
            javafx.application.Platform.runLater(() -> {
                Stage oldStage = (Stage) userLabel.getScene().getWindow();
                oldStage.hide();

                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/tuvarna/bg/library/view/login-view.fxml"));
                    Parent root = loader.load();

                    Scene scene = new Scene(root);
                    URL css = getClass().getResource("/com/tuvarna/bg/library/css/styles.css");
                    if (css != null) scene.getStylesheets().add(css.toExternalForm());

                    Stage loginStage = new Stage(StageStyle.UNDECORATED);
                    loginStage.setTitle("Library Management System");
                    loginStage.setScene(scene);
                    loginStage.setResizable(false);
                    loginStage.setMaximized(true);
                    loginStage.centerOnScreen();

                    scene.setOnKeyPressed(e -> {
                        if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) javafx.application.Platform.exit();
                    });

                    Button closeBtn = (Button) root.lookup("#closeButton");
                    if (closeBtn != null) closeBtn.setOnAction(e -> javafx.application.Platform.exit());
                    Button minimizeBtn = (Button) root.lookup("#minimizeButton");
                    if (minimizeBtn != null) minimizeBtn.setOnAction(e -> loginStage.setIconified(true));

                    loginStage.show();
                    oldStage.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                    new Alert(Alert.AlertType.ERROR, "Failed to load login screen:\n" + ex.getMessage()).showAndWait();
                    oldStage.show();
                }
            });
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

        // --- Robust cover loader (cards + left panel) ---
        private Image findCover(BookEntity b, double targetWidth) {
            try {
                String p = b.getImagePath();
                java.util.List<String> candidates = new java.util.ArrayList<>();

                if (p != null && !p.isBlank()) {
                    // already a URL?
                    if (p.startsWith("http://") || p.startsWith("https://") || p.startsWith("file:")) {
                        candidates.add(p);
                    } else {
                        // as absolute file
                        candidates.add("file:" + java.nio.file.Paths.get(p).toAbsolutePath());
                        // also try library_images/<filename>
                        java.nio.file.Path fn = java.nio.file.Paths.get(p).getFileName();
                        if (fn != null) {
                            candidates.add("file:" + java.nio.file.Paths.get("library_images", fn.toString()).toAbsolutePath());
                        }
                    }
                }

                // optional: try classpath by ISBN (e.g. /covers/978...jpg)
                if (b.getIsbn() != null) {
                    java.net.URL byIsbn = getClass().getResource("/covers/" + b.getIsbn() + ".jpg");
                    if (byIsbn != null) candidates.add(byIsbn.toExternalForm());
                }

                // try all candidates
                for (String url : candidates) {
                    try {
                        Image img = new Image(url, targetWidth, 0, true, true);
                        if (!img.isError()) return img;
                    } catch (Exception ignore) {}
                }

                // final placeholder (put a small png in resources if you like)
                java.net.URL ph = getClass().getResource("/com/tuvarna/bg/library/images/placeholder.png");
                if (ph != null) return new Image(ph.toExternalForm(), targetWidth, 0, true, true);
            } catch (Exception ignore) {}
            return null;
        }


    }
