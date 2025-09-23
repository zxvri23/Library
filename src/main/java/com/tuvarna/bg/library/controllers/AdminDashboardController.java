package com.tuvarna.bg.library.controllers;

import com.tuvarna.bg.library.dao.UserDAO;
import com.tuvarna.bg.library.entity.*;
import com.tuvarna.bg.library.util.DatabaseUtil;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.geometry.Pos;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

public class AdminDashboardController implements DashboardController {

    @FXML private Label userLabel;
    @FXML private TextField titleField, isbnField, languageField, yearField, copiesField;
    @FXML private TextArea summaryArea;
    @FXML private ComboBox<PublisherEntity> publisherCombo;
    @FXML private ComboBox<GenreEntity> genreCombo;
    @FXML private Label imageLabel;
    @FXML private TextField searchField;
    @FXML private TableView<BookEntity> booksTable;
    @FXML private TableColumn<BookEntity, Integer> idColumn;
    @FXML private TableColumn<BookEntity, String> titleColumn, isbnColumn, publisherColumn;
    @FXML private TableColumn<BookEntity, Integer> yearColumn, copiesColumn;
    @FXML private TableColumn<BookEntity, BookEntity> actionsColumn;

    @FXML private TextField userUsernameField, userFirstNameField, userLastNameField, userEmailField;
    @FXML private PasswordField userPasswordField;
    @FXML private ComboBox<RoleEntity> userRoleCombo;
    @FXML private TableView<UserEntity> usersTable;
    @FXML private TableColumn<UserEntity, Integer> userIdColumn;
    @FXML private TableColumn<UserEntity, String> userUsernameColumn, userNameColumn, userEmailColumn, userRoleColumn;
    @FXML private TableColumn<UserEntity, UserEntity> userActionsColumn; // bind the whole row

    // Keeps the copy count for each book shown in the table (populated by queries)
    private final java.util.Map<Integer, Integer> copyCountByBookId = new java.util.HashMap<>();


    // ---- Reports & Analytics KPI labels ----
    @FXML private Label totalBooksLabel, totalUsersLabel, activeLoansLabel, overdueLabel;

    @FXML private VBox requestCardsContainer;
    @FXML private VBox addAuthorForm, addGenreForm;
    @FXML private TextField authorNameField, authorBirthDateField;
    @FXML private TextField genreNameField;
    @FXML private TextArea genreDescriptionArea;
    @FXML private Label authorMessageLabel, genreMessageLabel;

    private UserEntity currentUser;
    private File selectedImageFile;
    private final List<AuthorEntity> selectedAuthors = new ArrayList<>();
    private final List<GenreEntity> selectedGenres = new ArrayList<>();
    private UserDAO userDAO;

    // ===== Validation helpers =====
    private static final Set<String> ISO_LANGUAGE_CODES = new HashSet<>();
    private static final Map<String, String> LANGUAGE_NAME_CANON = new HashMap<>();
    private static final Set<String> SMALL_TITLE_WORDS = new HashSet<>(Arrays.asList(
            "a","an","and","the","to","of","in","on","for","at","by","with","from",
            "into","over","nor","but","or","so","yet","per","vs","via"));

    static {
        for (String code : Locale.getISOLanguages()) {
            ISO_LANGUAGE_CODES.add(code.toLowerCase(Locale.ENGLISH));
            Locale loc = new Locale(code);
            String name = loc.getDisplayLanguage(Locale.ENGLISH);
            if (name != null && !name.isEmpty()) {
                LANGUAGE_NAME_CANON.put(name.toLowerCase(Locale.ENGLISH), name);
            }
        }
    }

    private static void markError(Control c) { if (c != null) c.setStyle("-fx-border-color:#e74c3c;-fx-border-radius:6;"); }
    private static void clearError(Control c) { if (c != null) c.setStyle(""); }

    private static boolean startsWithUpper(String s) {
        if (s == null) return false;
        String t = s.trim();
        if (t.isEmpty()) return false;
        char ch = t.charAt(0);
        return Character.isLetter(ch) && Character.isUpperCase(ch);
    }

    private static String toDigits(String s) { return s == null ? "" : s.replaceAll("[^0-9]", ""); }
    private static boolean isIsbn13(String s) { return toDigits(s).length() == 13; }

    private static String canonicalizeLanguage(String input) {
        if (input == null) return null;
        String k = input.trim().toLowerCase(Locale.ENGLISH);
        if (k.isEmpty()) return null;

        // ISO code (en, fr, ru, …)
        if (ISO_LANGUAGE_CODES.contains(k)) return new Locale(k).getDisplayLanguage(Locale.ENGLISH);

        // Display name (English)
        String name = LANGUAGE_NAME_CANON.get(k);
        if (name != null) return name;

        return null; // unknown language
    }

    private static String titleCase(String raw) {
        if (raw == null) return null;
        String s = raw.trim().replaceAll("\\s+", " ");
        if (s.isEmpty()) return s;

        String[] words = s.split(" ");
        for (int i = 0; i < words.length; i++) {
            String w = words[i];

            // handle hyphenated parts, e.g. "post-war"
            String[] parts = w.split("(-)");
            StringBuilder rebuilt = new StringBuilder();
            for (int p = 0; p < parts.length; p++) {
                String part = parts[p];
                if ("-".equals(part)) { rebuilt.append("-"); continue; }

                String lower = part.toLowerCase(Locale.ENGLISH);
                boolean small = SMALL_TITLE_WORDS.contains(lower);
                boolean forceCap = (i == 0) || (i == words.length - 1); // always cap first/last

                if (!small || forceCap) {
                    if (part.length() > 0) {
                        // keep inner apostrophes: Hitchhiker's -> Hitchhiker's
                        String head = part.substring(0, 1).toUpperCase(Locale.ENGLISH);
                        String tail = part.substring(1);
                        rebuilt.append(head).append(tail);
                    } else rebuilt.append(part);
                } else {
                    rebuilt.append(lower);
                }
            }
            words[i] = rebuilt.toString();
        }
        return String.join(" ", words);
    }


    @FXML private ComboBox<AuthorEntity> authorCombo;
    // NEW: analytics service instance
    private final AnalyticsService analyticsService = new AnalyticsService();

    @Override
    public void setCurrentUser(UserEntity user) {
        this.currentUser = user;
        userLabel.setText("Welcome, " + user.getFullName());
        initializeData();
    }

    @Override
    public UserEntity getCurrentUser() { return currentUser; }

    @FXML
    public void initialize() {
        userDAO = new UserDAO();
        setupTableColumns();
        setupComboBoxes();
        loadInitialData();

        if (booksTable != null)   VBox.setVgrow(booksTable, Priority.ALWAYS);
        if (usersTable != null)   VBox.setVgrow(usersTable, Priority.ALWAYS);
    }

    private void setupTableColumns() {
        /* ---------------- Books table ---------------- */

        // Make columns fill the full width of the table
        booksTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // "weights" so the constrained policy distributes space nicely
        titleColumn.setMaxWidth(1f * Integer.MAX_VALUE * 3);     // ~30%
        isbnColumn.setMaxWidth(1f * Integer.MAX_VALUE * 2);      // ~20%
        publisherColumn.setMaxWidth(1f * Integer.MAX_VALUE * 2); // ~20%
        yearColumn.setMaxWidth(1f * Integer.MAX_VALUE * 1);      // ~10%
        copiesColumn.setMaxWidth(1f * Integer.MAX_VALUE * 1);    // ~10%
        actionsColumn.setMaxWidth(1f * Integer.MAX_VALUE * 1);   // ~10%

        // Standard value factories
        idColumn.setCellValueFactory(new PropertyValueFactory<>("booksId"));
        titleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        isbnColumn.setCellValueFactory(new PropertyValueFactory<>("isbn"));

        // Publisher is an object; show its name safely
        publisherColumn.setCellValueFactory(cd ->
                new SimpleStringProperty(
                        cd.getValue().getPublisher() != null ? cd.getValue().getPublisher().getPubName() : ""
                )
        );

        // Year as integer (renders/sorts numerically)
        yearColumn.setCellValueFactory(cd ->
                new SimpleIntegerProperty(cd.getValue().getPublicationYear()).asObject()
        );

        // Copies: read from our SQL-backed map; fallback to entity list size if present
        copiesColumn.setCellValueFactory(cd -> {
            int bookId = cd.getValue().getBooksId();
            int count = copyCountByBookId.getOrDefault(bookId, 0);
            if (count == 0) {
                // gentle fallback if entity carries a list of copies
                try {
                    Object copiesObj = cd.getValue().getCopies();
                    if (copiesObj instanceof java.util.List) {
                        count = ((java.util.List<?>) copiesObj).size();
                    } else if (copiesObj instanceof Number) {
                        count = ((Number) copiesObj).intValue();
                    }
                } catch (Throwable ignored) {
                }
            }
            return new SimpleIntegerProperty(count).asObject();
        });

        // Actions column — bind the whole BookEntity and build buttons with your colors
        actionsColumn.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(param.getValue()));
        actionsColumn.setCellFactory(col -> new TableCell<BookEntity, BookEntity>() {
            private final Button editBtn = new Button("Edit");
            private final Button deleteBtn = new Button("Delete");
            private final HBox box = new HBox(5, editBtn, deleteBtn);

            {
                box.setAlignment(Pos.CENTER_LEFT);
                // keep your original colors/styles
                editBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-padding: 5 10;");
                deleteBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-padding: 5 10;");
            }

            @Override
            protected void updateItem(BookEntity book, boolean empty) {
                super.updateItem(book, empty);
                if (empty || book == null) {
                    setGraphic(null);
                    return;
                }
                editBtn.setOnAction(e -> editBook(book));
                deleteBtn.setOnAction(e -> deleteBook(book));
                setGraphic(box);
            }
        });
        actionsColumn.setSortable(false);
        actionsColumn.setStyle("-fx-alignment: CENTER;");

        /* ---------------- Users table (fills width + button colors kept) ---------------- */
        usersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

// weights so constrained policy distributes space nicely
        userUsernameColumn.setMaxWidth(1f * Integer.MAX_VALUE * 2); // ~25%
        userNameColumn.setMaxWidth(1f * Integer.MAX_VALUE * 2);     // ~25%
        userEmailColumn.setMaxWidth(1f * Integer.MAX_VALUE * 3);    // ~30%
        userRoleColumn.setMaxWidth(1f * Integer.MAX_VALUE * 1);     // ~10%
        userActionsColumn.setMaxWidth(1f * Integer.MAX_VALUE * 1);  // ~10%

        userIdColumn.setCellValueFactory(new PropertyValueFactory<>("usersId"));
        userUsernameColumn.setCellValueFactory(new PropertyValueFactory<>("username"));
        userNameColumn.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        userEmailColumn.setCellValueFactory(new PropertyValueFactory<>("email"));

// Role is an object; show its name safely
        userRoleColumn.setCellValueFactory(cd ->
                new SimpleStringProperty(
                        cd.getValue().getRole() != null ? cd.getValue().getRole().getName() : ""
                )
        );

// Actions column bound to the row item so every row renders buttons reliably
        userActionsColumn.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(param.getValue()));
        userActionsColumn.setCellFactory(col -> new TableCell<UserEntity, UserEntity>() {
            private final Button editBtn = new Button("Edit");
            private final Button deleteBtn = new Button("Delete");
            private final HBox box = new HBox(5, editBtn, deleteBtn);

            {
                box.setAlignment(Pos.CENTER_LEFT);
                // keep your original colors
                editBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-padding: 5 10;");
                deleteBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-padding: 5 10;");
            }

            @Override
            protected void updateItem(UserEntity user, boolean empty) {
                super.updateItem(user, empty);
                if (empty || user == null) {
                    setGraphic(null);
                    return;
                }
                editBtn.setOnAction(e -> editUser(user));
                deleteBtn.setOnAction(e -> deleteUser(user));
                setGraphic(box);
            }
        });
        userActionsColumn.setSortable(false);
        userActionsColumn.setStyle("-fx-alignment: CENTER;");
    }

    private boolean containsAuthorId(int id) {
        for (AuthorEntity a : selectedAuthors) if (a.getAuthorsId() == id) return true;
        return false;
    }


    private void loadAuthors() {
        if (authorCombo == null) return;
        try (Connection conn = DatabaseUtil.getConnection();
             Statement stmt = conn.createStatement();
             // DISTINCT ON de-dupes same-name rows (PostgreSQL)
             ResultSet rs = stmt.executeQuery("""
             SELECT DISTINCT ON (LOWER(full_name))
                    authors_id, full_name, birth_date
             FROM authors
             ORDER BY LOWER(full_name), authors_id
         """)) {
            ObservableList<AuthorEntity> authors = FXCollections.observableArrayList();
            while (rs.next()) {
                AuthorEntity a = new AuthorEntity();
                a.setAuthorsId(rs.getInt("authors_id"));
                a.setFullName(rs.getString("full_name"));
                Date bd = rs.getDate("birth_date");
                if (bd != null) a.setBirthDate(bd.toLocalDate());
                authors.add(a);
            }
            authorCombo.setItems(authors);
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void setupComboBoxes() {
        // Load data
        loadPublishers();
        loadGenres();
        loadRoles();
        loadAuthors(); // populate authors dropdown

        // ===== Authors dropdown =====
        if (authorCombo != null) {
            // list cells in the popup
            authorCombo.setCellFactory(lv -> new ListCell<AuthorEntity>() {
                @Override protected void updateItem(AuthorEntity item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getFullName());
                }
            });
            // button cell: show prompt when no selection (so it doesn't look blank/white)
            authorCombo.setButtonCell(new ListCell<AuthorEntity>() {
                @Override protected void updateItem(AuthorEntity item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(authorCombo.getPromptText() != null ? authorCombo.getPromptText() : "Select author");
                        setStyle("-fx-text-fill: #9aa0a6;"); // subtle placeholder color
                    } else {
                        setText(item.getFullName());
                        setStyle(""); // reset any placeholder style
                    }
                }
            });

            // auto-add selected author, then clear selection so user can pick another
            authorCombo.setOnAction(e -> {
                AuthorEntity sel = authorCombo.getValue();
                if (sel != null && !containsAuthorId(sel.getAuthorsId())) {
                    selectedAuthors.add(sel);
                    showAlert("Author Added", "Added author: " + sel.getFullName(), Alert.AlertType.INFORMATION);
                }
                authorCombo.getSelectionModel().clearSelection(); // triggers prompt text in button cell
            });
        }

        // ===== Genres dropdown =====
        if (genreCombo != null) {
            // list cells in the popup
            genreCombo.setCellFactory(lv -> new ListCell<GenreEntity>() {
                @Override protected void updateItem(GenreEntity item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getGenreName());
                }
            });
            // button cell: show prompt when no selection
            genreCombo.setButtonCell(new ListCell<GenreEntity>() {
                @Override protected void updateItem(GenreEntity item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(genreCombo.getPromptText() != null ? genreCombo.getPromptText() : "Select genre");
                        setStyle("-fx-text-fill: #9aa0a6;");
                    } else {
                        setText(item.getGenreName());
                        setStyle("");
                    }
                }
            });

            // auto-add selected genre, then clear
            genreCombo.setOnAction(e -> {
                GenreEntity g = genreCombo.getValue();
                if (g != null && !selectedGenres.contains(g)) {
                    selectedGenres.add(g);
                    showAlert("Genre Added", "Added genre: " + g.getGenreName(), Alert.AlertType.INFORMATION);
                }
                genreCombo.getSelectionModel().clearSelection(); // shows prompt again
            });
        }
    }


    private void loadInitialData() {
        refreshBooks();
        refreshUsers();
        loadStatistics();   // uses AnalyticsService now
        loadRequestCards(); // dynamic analytics cards
    }

    @FXML
    private void chooseImage() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Choose Book Cover Image");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
        selectedImageFile = fc.showOpenDialog(userLabel.getScene().getWindow());
        if (selectedImageFile != null) imageLabel.setText(selectedImageFile.getName());
    }

    private String saveImageToLibrary() {
        if (selectedImageFile == null) return null;
        try {
            Path imagesDir = Paths.get("library_images");
            if (!Files.exists(imagesDir)) Files.createDirectories(imagesDir);
            String fileName = System.currentTimeMillis() + "_" + selectedImageFile.getName();
            Path targetPath = imagesDir.resolve(fileName);
            Files.copy(selectedImageFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            return targetPath.toString();
        } catch (IOException e) {
            showAlert("Error", "Failed to save image: " + e.getMessage(), Alert.AlertType.ERROR);
            return null;
        }
    }

    @FXML
    private void addBook() {
        if (!validateBookForm()) return;

        try {
            String imagePath = saveImageToLibrary();
            int bookId = insertBook(imagePath);

            if (bookId > 0) {
                insertBookAuthors(bookId);
                insertBookGenres(bookId);
                insertBookCopies(bookId);

                showAlert("Success", "Book added successfully!", Alert.AlertType.INFORMATION);
                clearBookForm();
                refreshBooks();
                loadStatistics(); // refresh KPIs/cards
            }

        } catch (SQLException e) {
            showAlert("Error", "Failed to add book: " + e.getMessage(), Alert.AlertType.ERROR);
            e.printStackTrace();
        }
    }

    private boolean validateBookForm() {
        // clear previous error styles
        clearError(titleField);
        clearError(isbnField);
        clearError(languageField);
        clearError(summaryArea);
        clearError(copiesField);
        clearError(yearField);

        // ----- Title -----
        String title = titleField.getText() == null ? "" : titleField.getText().trim();
        if (title.isEmpty()) {
            markError(titleField);
            showAlert("Validation Error", "Please enter a book title.", Alert.AlertType.WARNING);
            return false;
        }
        if (!startsWithUpper(title)) {
            markError(titleField);
            showAlert("Validation Error", "Title must start with an uppercase letter.", Alert.AlertType.WARNING);
            return false;
        }
        // normalize to Title Case (like your screenshot)
        String normalizedTitle = titleCase(title);
        if (!normalizedTitle.equals(title)) titleField.setText(normalizedTitle);

        // ----- ISBN (13 digits) -----
        String isbnRaw = isbnField.getText() == null ? "" : isbnField.getText().trim();
        if (!isIsbn13(isbnRaw)) {
            markError(isbnField);
            showAlert("Validation Error", "ISBN must be exactly 13 digits (you can omit hyphens).", Alert.AlertType.WARNING);
            return false;
        }
        // show the cleaned digits back to the user
        isbnField.setText(toDigits(isbnRaw));

        // ----- Language (must exist) -----
        String langInput = languageField.getText() == null ? "" : languageField.getText().trim();
        String canonicalLang = canonicalizeLanguage(langInput);
        if (canonicalLang == null) {
            markError(languageField);
            showAlert("Validation Error", "Language must be a real world language (e.g., English, Spanish, French).", Alert.AlertType.WARNING);
            return false;
        }
        languageField.setText(canonicalLang);

        // ----- Summary (starts with uppercase) -----
        String summary = summaryArea.getText() == null ? "" : summaryArea.getText().trim();
        if (summary.isEmpty()) {
            markError(summaryArea);
            showAlert("Validation Error", "Please enter a book summary.", Alert.AlertType.WARNING);
            return false;
        }
        if (!startsWithUpper(summary)) {
            markError(summaryArea);
            showAlert("Validation Error", "Summary must start with an uppercase letter.", Alert.AlertType.WARNING);
            return false;
        }

        // ----- Copies (> 0 integer) -----
        String copiesText = copiesField.getText() == null ? "" : copiesField.getText().trim();
        int copies;
        try {
            copies = Integer.parseInt(copiesText);
            if (copies <= 0) throw new NumberFormatException("non-positive");
        } catch (NumberFormatException ex) {
            markError(copiesField);
            showAlert("Validation Error", "Copies must be a positive whole number (at least 1).", Alert.AlertType.WARNING);
            return false;
        }

        // ----- Publisher -----
        if (publisherCombo.getValue() == null) {
            markError(publisherCombo);
            showAlert("Validation Error", "Please select a publisher.", Alert.AlertType.WARNING);
            return false;
        }

        // ----- Year (optional; keep in DB constraint range if provided) -----
        String yearText = yearField.getText() == null ? "" : yearField.getText().trim();
        if (!yearText.isEmpty()) {
            try {
                int y = Integer.parseInt(yearText);
                if (y < 1000 || y > 2030) {
                    markError(yearField);
                    showAlert("Validation Error", "Publication year must be between 1000 and 2030.", Alert.AlertType.WARNING);
                    return false;
                }
            } catch (NumberFormatException ex) {
                markError(yearField);
                showAlert("Validation Error", "Publication year must be a valid number.", Alert.AlertType.WARNING);
                return false;
            }
        }

        // ----- At least one author & one genre -----
        if (selectedAuthors.isEmpty()) {
            showAlert("Validation Error", "Please add at least one author.", Alert.AlertType.WARNING);
            return false;
        }
        if (selectedGenres.isEmpty()) {
            showAlert("Validation Error", "Please add at least one genre.", Alert.AlertType.WARNING);
            return false;
        }

        return true;
    }


    private int insertBook(String imagePath) throws SQLException {
        String sql = "INSERT INTO books (title, summary, isbn, language, publication_year, publishers_id, image_path) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING books_id";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            String title = titleCase(titleField.getText().trim());
            String isbnDigits = toDigits(isbnField.getText().trim());
            String lang = canonicalizeLanguage(languageField.getText().trim()); // already validated

            stmt.setString(1, title);
            stmt.setString(2, summaryArea.getText().trim());
            stmt.setString(3, isbnDigits);
            stmt.setString(4, lang);

            String yearText = yearField.getText().trim();
            if (!yearText.isEmpty()) stmt.setInt(5, Integer.parseInt(yearText));
            else stmt.setNull(5, Types.INTEGER);

            stmt.setInt(6, publisherCombo.getValue().getPublishersId());
            stmt.setString(7, imagePath);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return -1;
    }


    private void insertBookAuthors(int bookId) throws SQLException {
        for (AuthorEntity author : selectedAuthors) {
            int authorId = getOrCreateAuthor(author);
            try (Connection conn = DatabaseUtil.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("INSERT INTO book_authors (books_id, authors_id) VALUES (?, ?)")) {
                stmt.setInt(1, bookId);
                stmt.setInt(2, authorId);
                stmt.executeUpdate();
            }
        }
    }

    private void insertBookGenres(int bookId) throws SQLException {
        for (GenreEntity genre : selectedGenres) {
            int genreId = getOrCreateGenre(genre);
            try (Connection conn = DatabaseUtil.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("INSERT INTO book_genres (books_id, genres_id) VALUES (?, ?)")) {
                stmt.setInt(1, bookId);
                stmt.setInt(2, genreId);
                stmt.executeUpdate();
            }
        }
    }

    private void insertBookCopies(int bookId) throws SQLException {
        int copies = Integer.parseInt(copiesField.getText().trim());
        String sql = "INSERT INTO book_copies (books_id, status, acquired_at) VALUES (?, 'AVAILABLE', ?)";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, bookId);
            stmt.setDate(2, Date.valueOf(LocalDate.now()));
            for (int i = 0; i < copies; i++) stmt.executeUpdate();
        }
    }

    private int getOrCreateAuthor(AuthorEntity author) throws SQLException {
        String checkSql = "SELECT authors_id FROM authors WHERE full_name = ? AND birth_date = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(checkSql)) {
            stmt.setString(1, author.getFullName());
            if (author.getBirthDate() != null) stmt.setDate(2, Date.valueOf(author.getBirthDate()));
            else stmt.setNull(2, Types.DATE);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt("authors_id");
            }
        }
        String insertSql = "INSERT INTO authors (full_name, birth_date) VALUES (?, ?) RETURNING authors_id";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            stmt.setString(1, author.getFullName());
            if (author.getBirthDate() != null) stmt.setDate(2, Date.valueOf(author.getBirthDate()));
            else stmt.setNull(2, Types.DATE);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt("authors_id");
            }
        }
        return -1;
    }

    private int getOrCreateGenre(GenreEntity genre) throws SQLException {
        String checkSql = "SELECT genres_id FROM genres WHERE gen_name = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(checkSql)) {
            stmt.setString(1, genre.getGenreName());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt("genres_id");
            }
        }
        String insertSql = "INSERT INTO genres (gen_name, genre_desc) VALUES (?, ?) RETURNING genres_id";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            stmt.setString(1, genre.getGenreName());
            stmt.setString(2, genre.getGenreDescription());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt("genres_id");
            }
        }
        return -1;
    }



    private void openAddAuthorScreen(String authorName) {
        if (addAuthorForm != null) {
            authorNameField.setText(authorName);
            addAuthorForm.setVisible(true);
            addGenreForm.setVisible(false);
        }
    }
    @FXML
    private void addAuthor() {
        // Always open the mini form to create a new author
        openAddAuthorScreen("");
    }


    @FXML
    private void addGenre() {
        if (genreCombo != null) {
            GenreEntity selectedGenre = genreCombo.getValue();
            if (selectedGenre != null) {
                if (!selectedGenres.contains(selectedGenre)) {
                    selectedGenres.add(selectedGenre);
                    showAlert("Success", "Genre added: " + selectedGenre.getGenreName(), Alert.AlertType.INFORMATION);
                }
                genreCombo.getSelectionModel().clearSelection();
                return;
            }
        }
        // No selection -> open mini form to add a new one
        openAddGenreScreen();
    }


    private void openAddGenreScreen() {
        if (addGenreForm != null) {
            addGenreForm.setVisible(true);
            addAuthorForm.setVisible(false);
        }
    }

    @FXML
    private void clearBookForm() {
        // simple text fields
        titleField.clear();
        isbnField.clear();
        languageField.clear();
        yearField.clear();
        copiesField.clear();                   // ✅ clear copies
        summaryArea.clear();

        // combos back to prompt
        if (publisherCombo != null) publisherCombo.getSelectionModel().clearSelection();
        if (authorCombo != null)    authorCombo.getSelectionModel().clearSelection();
        if (genreCombo != null)     genreCombo.getSelectionModel().clearSelection();

        // image
        selectedImageFile = null;
        imageLabel.setText("No image selected");

        // selections
        selectedAuthors.clear();
        selectedGenres.clear();
    }


    @FXML
    private void addUser() {
        if (!validateUserForm()) return;

        try {
            String sql = "INSERT INTO users (username, password, first_name, last_name, email, roles_id) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";
            try (Connection conn = DatabaseUtil.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, userUsernameField.getText().trim());
                stmt.setString(2, userPasswordField.getText());
                stmt.setString(3, userFirstNameField.getText().trim());
                stmt.setString(4, userLastNameField.getText().trim());
                stmt.setString(5, userEmailField.getText().trim());
                stmt.setInt(6, userRoleCombo.getValue().getRolesId());
                stmt.executeUpdate();

                showAlert("Success", "User added successfully!", Alert.AlertType.INFORMATION);
                clearUserForm();
                refreshUsers();
                loadStatistics(); // refresh KPIs/cards
            }
        } catch (SQLException e) {
            showAlert("Error", "Failed to add user: " + e.getMessage(), Alert.AlertType.ERROR);
            e.printStackTrace();
        }
    }

    private boolean validateUserForm() {
        if (userUsernameField.getText().trim().isEmpty()) {
            showAlert("Validation Error", "Please enter a username", Alert.AlertType.WARNING); return false;
        }
        if (userPasswordField.getText().isEmpty()) {
            showAlert("Validation Error", "Please enter a password", Alert.AlertType.WARNING); return false;
        }
        if (userRoleCombo.getValue() == null) {
            showAlert("Validation Error", "Please select a role", Alert.AlertType.WARNING); return false;
        }
        return true;
    }

    @FXML
    private void clearUserForm() {
        userUsernameField.clear();
        userPasswordField.clear();
        userFirstNameField.clear();
        userLastNameField.clear();
        userEmailField.clear();
        userRoleCombo.setValue(null);
    }

    private void loadPublishers() {
        try (Connection conn = DatabaseUtil.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM publishers ORDER BY pub_name")) {
            ObservableList<PublisherEntity> publishers = FXCollections.observableArrayList();
            while (rs.next()) {
                PublisherEntity p = new PublisherEntity();
                p.setPublishersId(rs.getInt("publishers_id"));
                p.setPubName(rs.getString("pub_name"));
                p.setEstablishedOn(rs.getDate("established_on") != null ? rs.getDate("established_on").toLocalDate() : null);
                publishers.add(p);
            }
            publisherCombo.setItems(publishers);
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void loadGenres() {
        try (Connection conn = DatabaseUtil.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM genres ORDER BY gen_name")) {
            ObservableList<GenreEntity> genres = FXCollections.observableArrayList();
            while (rs.next()) {
                GenreEntity g = new GenreEntity();
                g.setGenresId(rs.getInt("genres_id"));
                g.setGenreName(rs.getString("gen_name"));
                g.setGenreDescription(rs.getString("genre_desc"));
                genres.add(g);
            }
            genreCombo.setItems(genres);
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void loadRoles() {
        try (Connection conn = DatabaseUtil.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM roles WHERE name = 'MANAGER' ORDER BY name")) {
            ObservableList<RoleEntity> roles = FXCollections.observableArrayList();
            while (rs.next()) {
                RoleEntity r = new RoleEntity();
                r.setRolesId(rs.getInt("roles_id"));
                r.setName(rs.getString("name"));
                roles.add(r);
            }
            userRoleCombo.setItems(roles);
        } catch (SQLException e) { e.printStackTrace(); }
    }

    @FXML
    private void refreshBooks() {
        copyCountByBookId.clear();

        try (Connection conn = DatabaseUtil.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("""
             SELECT b.*, p.pub_name, COUNT(bc.copies_id) AS copy_count
             FROM books b
             LEFT JOIN publishers p ON b.publishers_id = p.publishers_id
             LEFT JOIN book_copies bc ON b.books_id = bc.books_id
             GROUP BY b.books_id, p.pub_name
             ORDER BY b.title
         """)) {

            ObservableList<BookEntity> books = FXCollections.observableArrayList();
            while (rs.next()) {
                BookEntity book = new BookEntity();
                int bookId = rs.getInt("books_id");
                book.setBooksId(bookId);
                book.setTitle(rs.getString("title"));
                book.setIsbn(rs.getString("isbn"));
                book.setLanguage(rs.getString("language"));

                try {
                    int yr = rs.getInt("publication_year");
                    if (!rs.wasNull()) book.setPublicationYear(yr);
                } catch (SQLException ignore) {}

                book.setSummary(rs.getString("summary"));
                book.setImagePath(rs.getString("image_path"));

                // put the COUNT into the controller map
                int cnt = 0;
                try { cnt = rs.getInt("copy_count"); if (rs.wasNull()) cnt = 0; } catch (SQLException ignore) {}
                copyCountByBookId.put(bookId, cnt);

                PublisherEntity publisher = new PublisherEntity();
                publisher.setPubName(rs.getString("pub_name"));
                book.setPublisher(publisher);

                books.add(book);
            }
            booksTable.setItems(books);
            booksTable.refresh(); // repaint actions & copies cells
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void refreshUsers() {
        List<UserEntity> users = userDAO.findByRole("MANAGER");
        usersTable.setItems(FXCollections.observableArrayList(users));
        usersTable.refresh(); // ensure action cells repaint on data swap
    }


    // ======= uses AnalyticsService (single round-trip & updates cards) =======
    private void loadStatistics() {
        try {
            DashboardStats s = analyticsService.loadStats();

            if (totalBooksLabel != null)  totalBooksLabel.setText("Total Books: " + s.totalBooks);
            if (totalUsersLabel != null)  totalUsersLabel.setText("Total Users: " + s.totalUsers);
            if (activeLoansLabel != null) activeLoansLabel.setText("Active Loans: " + s.activeLoans);
            if (overdueLabel != null)     overdueLabel.setText("Overdue: " + s.overdueLoans);

            loadAnalyticsCards(s);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    // ========================================================================

    private void editBook(BookEntity book) {
        titleField.setText(book.getTitle());
        isbnField.setText(book.getIsbn());
        languageField.setText(book.getLanguage());
        yearField.setText(String.valueOf(book.getPublicationYear()));
        summaryArea.setText(book.getSummary());
        for (PublisherEntity publisher : publisherCombo.getItems()) {
            if (publisher.getPubName().equals(book.getPublisher().getPubName())) {
                publisherCombo.setValue(publisher); break;
            }
        }
        showAlert("Edit Book", "Book data loaded for editing. Make changes and click 'Add Book' to update.", Alert.AlertType.INFORMATION);
    }

    private void deleteBook(BookEntity book) {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Confirm Delete");
        confirmAlert.setHeaderText("Delete Book");
        confirmAlert.setContentText("Are you sure you want to delete the book: " + book.getTitle() + "?");

        if (confirmAlert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        try (Connection conn = DatabaseUtil.getConnection()) {
            conn.setAutoCommit(false);

            // 1) Block deletion if there are ACTIVE loans for any copy of this book
            String activeLoansSql =
                    "SELECT COUNT(*) " +
                            "FROM loans l " +
                            "JOIN book_copies bc ON bc.copies_id = l.copy_id " +
                            "WHERE bc.books_id = ? AND l.returned_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(activeLoansSql)) {
                ps.setInt(1, book.getBooksId());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) > 0) {
                        conn.rollback();
                        showAlert("Cannot Delete",
                                "This book has active loans. Please return all copies first.",
                                Alert.AlertType.WARNING);
                        return;
                    }
                }
            }

            // 2) Delete reservations for the book
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM reservations WHERE book_id = ?")) {
                ps.setInt(1, book.getBooksId());
                ps.executeUpdate();
            }

            // 3) Delete loan history for copies of this book (if you want to keep history, skip this)
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM loans WHERE copy_id IN (SELECT copies_id FROM book_copies WHERE books_id = ?)")) {
                ps.setInt(1, book.getBooksId());
                ps.executeUpdate();
            }

            // 4) Delete copies
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM book_copies WHERE books_id = ?")) {
                ps.setInt(1, book.getBooksId());
                ps.executeUpdate();
            }

            // 5) Delete join rows
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM book_authors WHERE books_id = ?")) {
                ps.setInt(1, book.getBooksId());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM book_genres WHERE books_id = ?")) {
                ps.setInt(1, book.getBooksId());
                ps.executeUpdate();
            }

            // 6) Finally delete the book
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM books WHERE books_id = ?")) {
                ps.setInt(1, book.getBooksId());
                int rows = ps.executeUpdate();
                if (rows == 0) throw new SQLException("Book not found.");
            }

            conn.commit();
            showAlert("Success", "Book deleted successfully!", Alert.AlertType.INFORMATION);
            refreshBooks();
            loadStatistics();

        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Error", "Failed to delete book: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }


    private void editUser(UserEntity user) {
        userUsernameField.setText(user.getUsername());
        userPasswordField.setText(user.getPassword());
        userFirstNameField.setText(user.getFirstName());
        userLastNameField.setText(user.getLastName());
        userEmailField.setText(user.getEmail());
        for (RoleEntity role : userRoleCombo.getItems()) {
            if (role.getName().equals(user.getRole().getName())) {
                userRoleCombo.setValue(role); break;
            }
        }
        showAlert("Edit User", "User data loaded for editing. Make changes and click 'Add User' to update.", Alert.AlertType.INFORMATION);
    }

    private void deleteUser(UserEntity user) {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Confirm Delete");
        confirmAlert.setHeaderText("Delete User");
        confirmAlert.setContentText("Are you sure you want to delete the user: " + user.getFullName() + "?");

        if (confirmAlert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            if (userDAO.delete(user.getUsersId())) {
                showAlert("Success", "User deleted successfully!", Alert.AlertType.INFORMATION);
                refreshUsers();
                loadStatistics();
            } else {
                showAlert("Error", "Failed to delete user.", Alert.AlertType.ERROR);
            }
        }
    }

    @FXML
    private void searchBooks() {
        String q = (searchField != null && searchField.getText() != null)
                ? searchField.getText().trim()
                : "";

        if (q.isEmpty()) {
            refreshBooks();
            return;
        }

        copyCountByBookId.clear();

        String like = "%" + q + "%";

        String sql = """
        SELECT b.*, p.pub_name, COUNT(bc.copies_id) AS copy_count
        FROM books b
        LEFT JOIN publishers p   ON b.publishers_id = p.publishers_id
        LEFT JOIN book_copies bc ON b.books_id      = bc.books_id
        LEFT JOIN book_authors ba ON ba.books_id    = b.books_id
        LEFT JOIN authors a       ON a.authors_id   = ba.authors_id
        LEFT JOIN book_genres bg  ON bg.books_id    = b.books_id
        LEFT JOIN genres g        ON g.genres_id    = bg.genres_id
        WHERE
            b.title ILIKE ? OR
            b.isbn ILIKE ? OR
            COALESCE(p.pub_name, '') ILIKE ? OR
            COALESCE(a.full_name, '') ILIKE ? OR
            COALESCE(g.gen_name, '') ILIKE ? OR
            COALESCE(b.language, '') ILIKE ? OR
            COALESCE(CAST(b.publication_year AS TEXT), '') ILIKE ?
        GROUP BY b.books_id, p.pub_name
        ORDER BY b.title
    """;

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            for (int i = 1; i <= 7; i++) ps.setString(i, like);

            try (ResultSet rs = ps.executeQuery()) {
                ObservableList<BookEntity> books = FXCollections.observableArrayList();
                while (rs.next()) {
                    BookEntity book = new BookEntity();
                    int bookId = rs.getInt("books_id");
                    book.setBooksId(bookId);
                    book.setTitle(rs.getString("title"));
                    book.setIsbn(rs.getString("isbn"));
                    book.setLanguage(rs.getString("language"));

                    try {
                        int yr = rs.getInt("publication_year");
                        if (!rs.wasNull()) book.setPublicationYear(yr);
                    } catch (SQLException ignore) {}

                    book.setSummary(rs.getString("summary"));
                    book.setImagePath(rs.getString("image_path"));

                    int cnt = 0;
                    try { cnt = rs.getInt("copy_count"); if (rs.wasNull()) cnt = 0; } catch (SQLException ignore) {}
                    copyCountByBookId.put(bookId, cnt);

                    PublisherEntity publisher = new PublisherEntity();
                    publisher.setPubName(rs.getString("pub_name"));
                    book.setPublisher(publisher);

                    books.add(book);
                }
                booksTable.setItems(books);
                booksTable.refresh();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Search Error", "Failed to search books: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }



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

    private void initializeData() { /* future stats/boot logic */ }

    // ======== Dynamic analytics cards in the "Reports & Analytics" tab ========
    @FXML
    private void loadRequestCards() {
        try {
            loadAnalyticsCards(analyticsService.loadStats());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadAnalyticsCards(DashboardStats stats) {
        if (requestCardsContainer == null) return;
        requestCardsContainer.getChildren().clear();

        // Inventory Snapshot
        String inventoryText = String.format(
                "Total Copies: %d%nAvailable: %d%nChecked Out: %d",
                stats.totalCopies, stats.availableCopies, stats.checkedOutCopies
        );
        requestCardsContainer.getChildren().add(createAnalyticsCard("Inventory Snapshot", inventoryText, "📚"));

        // Overdue Snapshot
        long maxOver = 0;
        try { maxOver = analyticsService.maxOverdueDays(); } catch (SQLException ignore) {}
        String overdueText = String.format(
                "Overdue Loans: %d%nMax Overdue: %d day(s)",
                stats.overdueLoans, maxOver
        );
        requestCardsContainer.getChildren().add(createAnalyticsCard("Overdue Snapshot", overdueText, "⏰"));

        // Top Borrowed Books
        StringBuilder topBooks = new StringBuilder();
        try {
            List<TopBook> books = analyticsService.topBorrowedBooks(5);
            if (books.isEmpty()) topBooks.append("No borrowing history yet.");
            else {
                int i = 1;
                for (TopBook b : books) {
                    topBooks.append(i++).append(". ")
                            .append(b.title).append(" — ")
                            .append(b.timesBorrowed).append(" loan(s)\n");
                }
            }
        } catch (SQLException ignore) { topBooks.append("Failed to load."); }
        requestCardsContainer.getChildren().add(createAnalyticsCard("Top Borrowed Books", topBooks.toString().trim(), "🏆"));

        // Most Active Borrowers
        StringBuilder topUsers = new StringBuilder();
        try {
            List<TopBorrower> users = analyticsService.topActiveBorrowers(5);
            if (users.isEmpty()) topUsers.append("No borrowing history yet.");
            else {
                int i = 1;
                for (TopBorrower u : users) {
                    topUsers.append(i++).append(". ")
                            .append(u.name).append(" — ")
                            .append(u.loansCount).append(" loan(s)\n");
                }
            }
        } catch (SQLException ignore) { topUsers.append("Failed to load."); }
        requestCardsContainer.getChildren().add(createAnalyticsCard("Most Active Borrowers", topUsers.toString().trim(), "👤"));
    }

    private VBox createAnalyticsCard(String title, String description, String icon) {
        VBox card = new VBox(10);
        card.getStyleClass().add("request-card");
        card.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-padding: 15; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 3, 0, 0, 0);");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: 24px;");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        header.getChildren().addAll(iconLabel, titleLabel);

        Label descLabel = new Label(description);
        descLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #2c3e50;");
        descLabel.setWrapText(true);

        card.getChildren().addAll(header, descLabel);
        return card;
    }
    // ==========================================================================

    @FXML
    private void submitAuthor() {
        String fullName = authorNameField.getText().trim();
        String birthDateStr = authorBirthDateField.getText().trim();

        if (fullName.isEmpty()) {
            showAuthorMessage("Please enter the author's full name.", true);
            return;
        }

        try {
            LocalDate birthDate = null;
            if (!birthDateStr.isEmpty()) {
                birthDate = LocalDate.parse(birthDateStr);
            }

            if (authorExists(fullName, birthDate)) {
                showAuthorMessage("Author with this name and birth date already exists.", true);
                return;
            }

            String sql = "INSERT INTO authors (full_name, birth_date) VALUES (?, ?) RETURNING authors_id";
            try (Connection conn = DatabaseUtil.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, fullName);
                if (birthDate != null) stmt.setDate(2, Date.valueOf(birthDate));
                else stmt.setNull(2, Types.DATE);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int newId = rs.getInt(1);

                        // Add to the selectedAuthors list immediately
                        AuthorEntity newAuthor = new AuthorEntity();
                        newAuthor.setAuthorsId(newId);
                        newAuthor.setFullName(fullName);
                        newAuthor.setBirthDate(birthDate);
                        if (!containsAuthorId(newId)) selectedAuthors.add(newAuthor);

                        // Refresh the dropdown
                        loadAuthors();
                        if (authorCombo != null) authorCombo.getSelectionModel().clearSelection();

                        showAuthorMessage("Author added successfully!", false);
                        clearAuthorForm();
                        addAuthorForm.setVisible(false);
                    } else {
                        showAuthorMessage("Failed to add author.", true);
                    }
                }
            }

        } catch (Exception e) {
            showAuthorMessage("Error: " + e.getMessage(), true);
            e.printStackTrace();
        }
    }

    @FXML
    private void submitGenre() {
        String genreName = genreNameField.getText().trim();
        String description = genreDescriptionArea.getText().trim();

        if (genreName.isEmpty()) {
            showGenreMessage("Please enter the genre name.", true);
            return;
        }

        try {
            if (genreExists(genreName)) {
                showGenreMessage("Genre with this name already exists.", true);
                return;
            }

            String sql = "INSERT INTO genres (gen_name, genre_desc) VALUES (?, ?)";
            try (Connection conn = DatabaseUtil.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, genreName);
                stmt.setString(2, description.isEmpty() ? null : description);

                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected > 0) {
                    showGenreMessage("Genre added successfully!", false);
                    clearGenreForm();
                    addGenreForm.setVisible(false);
                    loadGenres();
                } else {
                    showGenreMessage("Failed to add genre.", true);
                }
            }

        } catch (SQLException e) {
            showGenreMessage("Database error: " + e.getMessage(), true);
            e.printStackTrace();
        }
    }

    @FXML
    private void cancelAuthor() {
        addAuthorForm.setVisible(false);
        clearAuthorForm();
    }

    @FXML
    private void cancelGenre() {
        addGenreForm.setVisible(false);
        clearGenreForm();
    }

    private void clearAuthorForm() {
        authorNameField.clear();
        authorBirthDateField.clear();
        authorMessageLabel.setText("");
    }

    private void clearGenreForm() {
        genreNameField.clear();
        genreDescriptionArea.clear();
        genreMessageLabel.setText("");
    }

    private void showAuthorMessage(String message, boolean isError) {
        authorMessageLabel.setText(message);
        if (isError) {
            authorMessageLabel.setStyle("-fx-text-fill: #c0392b; -fx-font-size: 14px;");
        } else {
            authorMessageLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-size: 14px;");
        }
    }

    private void showGenreMessage(String message, boolean isError) {
        genreMessageLabel.setText(message);
        if (isError) {
            genreMessageLabel.setStyle("-fx-text-fill: #c0392b; -fx-font-size: 14px;");
        } else {
            genreMessageLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-size: 14px;");
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

    private boolean genreExists(String genreName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM genres WHERE gen_name = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, genreName);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    // ========= INNER ANALYTICS TYPES (Java 8 friendly) =========
    private static final class TopBook {
        long id; String title; long timesBorrowed;
        TopBook(long id, String title, long timesBorrowed) { this.id=id; this.title=title; this.timesBorrowed=timesBorrowed; }
    }
    private static final class TopBorrower {
        long id; String name; long loansCount;
        TopBorrower(long id, String name, long loansCount) { this.id=id; this.name=name; this.loansCount=loansCount; }
    }
    private static final class DashboardStats {
        long totalBooks, totalUsers, activeLoans, overdueLoans;
        long totalCopies, availableCopies, checkedOutCopies;
        DashboardStats(long totalBooks, long totalUsers, long activeLoans, long overdueLoans,
                       long totalCopies, long availableCopies) {
            this.totalBooks = totalBooks;
            this.totalUsers = totalUsers;
            this.activeLoans = activeLoans;
            this.overdueLoans = overdueLoans;
            this.totalCopies = totalCopies;
            this.availableCopies = availableCopies;
            this.checkedOutCopies = Math.max(0, totalCopies - availableCopies);
        }
    }

    // ========= ANALYTICS SERVICE (uses DatabaseUtil; auto-detects schema) =========
    private static final class AnalyticsService {

        DashboardStats loadStats() throws SQLException {
            try (Connection conn = DatabaseUtil.getConnection()) {
                long totalBooks   = scalarLong(conn, "SELECT COUNT(*) FROM books");
                long totalUsers   = scalarLong(conn, "SELECT COUNT(*) FROM users");
                long activeLoans  = scalarLong(conn, "SELECT COUNT(*) FROM loans WHERE returned_at IS NULL");
                long overdueLoans = scalarLong(conn, "SELECT COUNT(*) FROM loans WHERE returned_at IS NULL AND due_date < CURRENT_DATE");

                long totalCopies     = hasTable(conn, "book_copies") ? scalarLong(conn, "SELECT COUNT(*) FROM book_copies") : 0L;
                long availableCopies = hasTable(conn, "book_copies") ? scalarLong(conn, "SELECT COUNT(*) FROM book_copies WHERE status = 'AVAILABLE'") : 0L;

                return new DashboardStats(totalBooks, totalUsers, activeLoans, overdueLoans, totalCopies, availableCopies);
            }
        }

        List<TopBook> topBorrowedBooks(int limit) throws SQLException {
            if (limit <= 0) limit = 5;

            try (Connection conn = DatabaseUtil.getConnection()) {

                // Print schema info once to the console so we see what's available
                debugTable(conn, "loans");
                debugTable(conn, "loan_items");
                debugTable(conn, "book_copies");

                // 1) loans.copies_id -> book_copies -> books
                if (hasColumn(conn, "loans", "copies_id") &&
                        hasTable(conn, "book_copies") && hasColumn(conn, "book_copies", "books_id")) {
                    System.out.println("[TopBooks] Path: loans.copies_id -> book_copies -> books");
                    String sql = "SELECT b.books_id, b.title, COUNT(*) AS times " +
                            "FROM loans l " +
                            "JOIN book_copies bc ON bc.copies_id = l.copies_id " +
                            "JOIN books b ON b.books_id = bc.books_id " +
                            "GROUP BY b.books_id, b.title " +
                            "ORDER BY times DESC, b.title ASC " +
                            "LIMIT ?";
                    return runTopBooksQuery(conn, sql, limit);
                }

                // 1b) loans.copy_id (singular) -> book_copies -> books
                if (hasColumn(conn, "loans", "copy_id") &&
                        hasTable(conn, "book_copies") && hasColumn(conn, "book_copies", "books_id")) {
                    System.out.println("[TopBooks] Path: loans.copy_id -> book_copies -> books");
                    String sql = "SELECT b.books_id, b.title, COUNT(*) AS times " +
                            "FROM loans l " +
                            "JOIN book_copies bc ON bc.copies_id = l.copy_id " +
                            "JOIN books b ON b.books_id = bc.books_id " +
                            "GROUP BY b.books_id, b.title " +
                            "ORDER BY times DESC, b.title ASC " +
                            "LIMIT ?";
                    return runTopBooksQuery(conn, sql, limit);
                }

                // 2) loans.book_copies_id -> book_copies -> books
                if (hasColumn(conn, "loans", "book_copies_id") &&
                        hasTable(conn, "book_copies") && hasColumn(conn, "book_copies", "books_id")) {
                    System.out.println("[TopBooks] Path: loans.book_copies_id -> book_copies -> books");
                    String sql = "SELECT b.books_id, b.title, COUNT(*) AS times " +
                            "FROM loans l " +
                            "JOIN book_copies bc ON bc.copies_id = l.book_copies_id " +
                            "JOIN books b ON b.books_id = bc.books_id " +
                            "GROUP BY b.books_id, b.title " +
                            "ORDER BY times DESC, b.title ASC " +
                            "LIMIT ?";
                    return runTopBooksQuery(conn, sql, limit);
                }

                // 3) loans.books_id -> books
                if (hasColumn(conn, "loans", "books_id")) {
                    System.out.println("[TopBooks] Path: loans.books_id -> books");
                    String sql = "SELECT b.books_id, b.title, COUNT(*) AS times " +
                            "FROM loans l " +
                            "JOIN books b ON b.books_id = l.books_id " +
                            "GROUP BY b.books_id, b.title " +
                            "ORDER BY times DESC, b.title ASC " +
                            "LIMIT ?";
                    return runTopBooksQuery(conn, sql, limit);
                }

                // 3b) loans.book_id (singular) -> books
                if (hasColumn(conn, "loans", "book_id")) {
                    System.out.println("[TopBooks] Path: loans.book_id -> books");
                    String sql = "SELECT b.books_id, b.title, COUNT(*) AS times " +
                            "FROM loans l " +
                            "JOIN books b ON b.books_id = l.book_id " +
                            "GROUP BY b.books_id, b.title " +
                            "ORDER BY times DESC, b.title ASC " +
                            "LIMIT ?";
                    return runTopBooksQuery(conn, sql, limit);
                }

                // 4) loan_items.copies_id -> book_copies -> books
                if (hasTable(conn, "loan_items")) {
                    if (hasColumn(conn, "loan_items", "copies_id") &&
                            hasTable(conn, "book_copies") && hasColumn(conn, "book_copies", "books_id")) {
                        System.out.println("[TopBooks] Path: loan_items.copies_id -> book_copies -> books");
                        String sql = "SELECT b.books_id, b.title, COUNT(*) AS times " +
                                "FROM loan_items li " +
                                "JOIN book_copies bc ON bc.copies_id = li.copies_id " +
                                "JOIN books b ON b.books_id = bc.books_id " +
                                "GROUP BY b.books_id, b.title " +
                                "ORDER BY times DESC, b.title ASC " +
                                "LIMIT ?";
                        return runTopBooksQuery(conn, sql, limit);
                    }

                    // 4b) loan_items.book_id / books_id -> books
                    if (hasColumn(conn, "loan_items", "books_id") || hasColumn(conn, "loan_items", "book_id")) {
                        String bookFk = hasColumn(conn, "loan_items", "books_id") ? "books_id" : "book_id";
                        System.out.println("[TopBooks] Path: loan_items." + bookFk + " -> books");
                        String sql = "SELECT b.books_id, b.title, COUNT(*) AS times " +
                                "FROM loan_items li " +
                                "JOIN books b ON b.books_id = li." + bookFk + " " +
                                "GROUP BY b.books_id, b.title " +
                                "ORDER BY times DESC, b.title ASC " +
                                "LIMIT ?";
                        return runTopBooksQuery(conn, sql, limit);
                    }
                }

                System.out.println("[TopBooks] No compatible path found. Returning empty list.");
                return new ArrayList<>();
            }
        }

        // add this helper inside AnalyticsService (used above)
        private static void debugTable(Connection conn, String table) {
            try {
                if (!hasTable(conn, table)) {
                    System.out.println("[Schema] Table not found: " + table);
                    return;
                }
                System.out.print("[Schema] " + table + " columns:");
                DatabaseMetaData md = conn.getMetaData();
                try (ResultSet rs = md.getColumns(null, null, table, null)) {
                    boolean any = false;
                    while (rs.next()) {
                        any = true;
                        System.out.print(" " + rs.getString("COLUMN_NAME"));
                    }
                    if (!any) {
                        try (ResultSet rs2 = md.getColumns(null, null, table.toUpperCase(), null)) {
                            while (rs2.next()) System.out.print(" " + rs2.getString("COLUMN_NAME"));
                        }
                    }
                }
                System.out.println();
            } catch (SQLException ignore) {}
        }

        List<TopBorrower> topActiveBorrowers(int limit) throws SQLException {
            if (limit <= 0) limit = 5;
            String sql = "SELECT u.users_id, COALESCE(TRIM(u.first_name || ' ' || u.last_name), u.username) AS name, COUNT(l.loans_id) AS cnt " +
                    "FROM loans l JOIN users u ON u.users_id = l.users_id " +
                    "GROUP BY u.users_id, name " +
                    "ORDER BY cnt DESC, name ASC LIMIT ?";
            try (Connection conn = DatabaseUtil.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    List<TopBorrower> out = new ArrayList<>();
                    while (rs.next()) out.add(new TopBorrower(rs.getLong("users_id"), rs.getString("name"), rs.getLong("cnt")));
                    return out;
                }
            }
        }

        long maxOverdueDays() throws SQLException {
            String sql = "SELECT COALESCE(MAX((CURRENT_DATE - due_date)), 0) FROM loans WHERE returned_at IS NULL AND due_date < CURRENT_DATE";
            try (Connection conn = DatabaseUtil.getConnection();
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                rs.next(); return rs.getLong(1);
            }
        }

        /* ------------------------- helpers ------------------------- */
        private List<TopBook> runTopBooksQuery(Connection conn, String sql, int limit) throws SQLException {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, Math.max(1, limit));
                try (ResultSet rs = ps.executeQuery()) {
                    List<TopBook> out = new ArrayList<>();
                    while (rs.next()) {
                        out.add(new TopBook(
                                rs.getLong("books_id"),
                                rs.getString("title"),
                                rs.getLong("times")
                        ));
                    }
                    return out;
                }
            }
        }

        private static long scalarLong(Connection conn, String sql) throws SQLException {
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) { rs.next(); return rs.getLong(1); }
        }

        private static boolean hasTable(Connection conn, String table) throws SQLException {
            DatabaseMetaData md = conn.getMetaData();
            try (ResultSet rs = md.getTables(null, null, table, null)) { if (rs.next()) return true; }
            try (ResultSet rs = md.getTables(null, null, table.toLowerCase(), null)) { if (rs.next()) return true; }
            try (ResultSet rs = md.getTables(null, null, table.toUpperCase(), null)) { return rs.next(); }
        }

        private static boolean hasColumn(Connection conn, String table, String column) throws SQLException {
            DatabaseMetaData md = conn.getMetaData();
            try (ResultSet rs = md.getColumns(null, null, table, column)) { if (rs.next()) return true; }
            try (ResultSet rs = md.getColumns(null, null, table.toLowerCase(), column.toLowerCase())) { if (rs.next()) return true; }
            try (ResultSet rs = md.getColumns(null, null, table.toUpperCase(), column.toUpperCase())) { return rs.next(); }
        }
    }


}
