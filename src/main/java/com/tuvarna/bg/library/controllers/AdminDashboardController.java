package com.tuvarna.bg.library.controllers;

import com.tuvarna.bg.library.entity.*;
import com.tuvarna.bg.library.util.DatabaseUtil;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.cell.PropertyValueFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AdminDashboardController implements DashboardController {

    @FXML private Label userLabel;
    @FXML private TextField titleField, isbnField, languageField, yearField, copiesField;
    @FXML private TextArea summaryArea;
    @FXML private ComboBox<PublisherEntity> publisherCombo;
    @FXML private TextField authorField;
    @FXML private ComboBox<GenreEntity> genreCombo;
    @FXML private Label imageLabel;
    @FXML private TextField searchField;
    @FXML private TableView<BookEntity> booksTable;
    @FXML private TableColumn<BookEntity, Integer> idColumn;
    @FXML private TableColumn<BookEntity, String> titleColumn, isbnColumn, publisherColumn, yearColumn, copiesColumn;
    @FXML private TextField userUsernameField, userFirstNameField, userLastNameField, userEmailField;
    @FXML private PasswordField userPasswordField;
    @FXML private ComboBox<RoleEntity> userRoleCombo;
    @FXML private TableView<UserEntity> usersTable;
    @FXML private Label totalBooksLabel, totalUsersLabel, activeLoansLabel, overdueLabel;
    @FXML private TableView<Object> activityTable;

    private UserEntity currentUser;
    private File selectedImageFile;
    private List<AuthorEntity> selectedAuthors = new ArrayList<>();
    private List<GenreEntity> selectedGenres = new ArrayList<>();

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
        idColumn.setCellValueFactory(new PropertyValueFactory<>("booksId"));
        titleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        isbnColumn.setCellValueFactory(new PropertyValueFactory<>("isbn"));
        publisherColumn.setCellValueFactory(new PropertyValueFactory<>("publisher"));
        yearColumn.setCellValueFactory(new PropertyValueFactory<>("publicationYear"));

        // Add user table column setup here
    }

    private void setupComboBoxes() {
        loadPublishers();
        loadGenres();
        loadRoles();
    }

    private void loadInitialData() {
        refreshBooks();
        refreshUsers();
        loadStatistics();
    }

    @FXML
    private void chooseImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choose Book Cover Image");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp")
        );

        selectedImageFile = fileChooser.showOpenDialog(userLabel.getScene().getWindow());

        if (selectedImageFile != null) {
            imageLabel.setText(selectedImageFile.getName());
        }
    }

    private String saveImageToLibrary() {
        if (selectedImageFile == null) {
            return null;
        }

        try {
            Path imagesDir = Paths.get("library_images");
            if (!Files.exists(imagesDir)) {
                Files.createDirectories(imagesDir);
            }

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
        if (!validateBookForm()) {
            return;
        }

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
                loadStatistics();
            }

        } catch (SQLException e) {
            showAlert("Error", "Failed to add book: " + e.getMessage(), Alert.AlertType.ERROR);
            e.printStackTrace();
        }
    }

    private boolean validateBookForm() {
        if (titleField.getText().trim().isEmpty()) {
            showAlert("Validation Error", "Please enter a book title", Alert.AlertType.WARNING);
            return false;
        }

        if (isbnField.getText().trim().isEmpty()) {
            showAlert("Validation Error", "Please enter an ISBN", Alert.AlertType.WARNING);
            return false;
        }

        if (publisherCombo.getValue() == null) {
            showAlert("Validation Error", "Please select a publisher", Alert.AlertType.WARNING);
            return false;
        }

        if (selectedAuthors.isEmpty()) {
            showAlert("Validation Error", "Please add at least one author", Alert.AlertType.WARNING);
            return false;
        }

        if (selectedGenres.isEmpty()) {
            showAlert("Validation Error", "Please add at least one genre", Alert.AlertType.WARNING);
            return false;
        }

        return true;
    }

    private int insertBook(String imagePath) throws SQLException {
        String sql = "INSERT INTO books (title, summary, isbn, language, publication_year, publishers_id, image_path) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING books_id";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, titleField.getText().trim());
            stmt.setString(2, summaryArea.getText().trim());
            stmt.setString(3, isbnField.getText().trim());
            stmt.setString(4, languageField.getText().trim());

            String yearText = yearField.getText().trim();
            if (!yearText.isEmpty()) {
                stmt.setInt(5, Integer.parseInt(yearText));
            } else {
                stmt.setNull(5, Types.INTEGER);
            }

            stmt.setInt(6, publisherCombo.getValue().getPublishersId());
            stmt.setString(7, imagePath);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return -1;
    }

    private void insertBookAuthors(int bookId) throws SQLException {
        for (AuthorEntity author : selectedAuthors) {
            int authorId = getOrCreateAuthor(author);

            String sql = "INSERT INTO book_authors (books_id, authors_id) VALUES (?, ?)";
            try (Connection conn = DatabaseUtil.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, bookId);
                stmt.setInt(2, authorId);
                stmt.executeUpdate();
            }
        }
    }

    private void insertBookGenres(int bookId) throws SQLException {
        for (GenreEntity genre : selectedGenres) {
            int genreId = getOrCreateGenre(genre);

            String sql = "INSERT INTO book_genres (books_id, genres_id) VALUES (?, ?)";
            try (Connection conn = DatabaseUtil.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
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

            for (int i = 0; i < copies; i++) {
                stmt.executeUpdate();
            }
        }
    }

    private int getOrCreateAuthor(AuthorEntity author) throws SQLException {
        String checkSql = "SELECT authors_id FROM authors WHERE full_name = ? AND birth_date = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(checkSql)) {

            stmt.setString(1, author.getFullName());
            if (author.getBirthDate() != null) {
                stmt.setDate(2, Date.valueOf(author.getBirthDate()));
            } else {
                stmt.setNull(2, Types.DATE);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("authors_id");
                }
            }
        }

        String insertSql = "INSERT INTO authors (full_name, birth_date) VALUES (?, ?) RETURNING authors_id";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql)) {

            stmt.setString(1, author.getFullName());
            if (author.getBirthDate() != null) {
                stmt.setDate(2, Date.valueOf(author.getBirthDate()));
            } else {
                stmt.setNull(2, Types.DATE);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("authors_id");
                }
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
                if (rs.next()) {
                    return rs.getInt("genres_id");
                }
            }
        }

        String insertSql = "INSERT INTO genres (gen_name, genre_desc) VALUES (?, ?) RETURNING genres_id";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql)) {

            stmt.setString(1, genre.getGenreName());
            stmt.setString(2, genre.getGenreDescription());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("genres_id");
                }
            }
        }
        return -1;
    }

    @FXML
    private void addAuthor() {
        String authorName = authorField.getText().trim();
        if (!authorName.isEmpty()) {
            AuthorEntity author = new AuthorEntity(authorName, null);
            selectedAuthors.add(author);
            authorField.clear();
            showAlert("Success", "Author added: " + authorName, Alert.AlertType.INFORMATION);
        }
    }

    @FXML
    private void addGenre() {
        GenreEntity selectedGenre = genreCombo.getValue();
        if (selectedGenre != null && !selectedGenres.contains(selectedGenre)) {
            selectedGenres.add(selectedGenre);
            showAlert("Success", "Genre added: " + selectedGenre.getGenreName(), Alert.AlertType.INFORMATION);
        }
    }

    @FXML
    private void clearBookForm() {
        titleField.clear();
        isbnField.clear();
        languageField.clear();
        yearField.clear();
        summaryArea.clear();
        publisherCombo.setValue(null);
        authorField.clear();
        genreCombo.setValue(null);
        imageLabel.setText("No image selected");
        selectedImageFile = null;
        selectedAuthors.clear();
        selectedGenres.clear();
    }

    @FXML
    private void addUser() {
        if (!validateUserForm()) {
            return;
        }

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
                loadStatistics();
            }

        } catch (SQLException e) {
            showAlert("Error", "Failed to add user: " + e.getMessage(), Alert.AlertType.ERROR);
            e.printStackTrace();
        }
    }

    private boolean validateUserForm() {
        if (userUsernameField.getText().trim().isEmpty()) {
            showAlert("Validation Error", "Please enter a username", Alert.AlertType.WARNING);
            return false;
        }

        if (userPasswordField.getText().isEmpty()) {
            showAlert("Validation Error", "Please enter a password", Alert.AlertType.WARNING);
            return false;
        }

        if (userRoleCombo.getValue() == null) {
            showAlert("Validation Error", "Please select a role", Alert.AlertType.WARNING);
            return false;
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
                PublisherEntity publisher = new PublisherEntity();
                publisher.setPublishersId(rs.getInt("publishers_id"));
                publisher.setPubName(rs.getString("pub_name"));
                publisher.setEstablishedOn(rs.getDate("established_on") != null ?
                        rs.getDate("established_on").toLocalDate() : null);
                publishers.add(publisher);
            }

            publisherCombo.setItems(publishers);

        } catch (SQLException e) {
            e.printStackTrace();
        }
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
                genre.setGenreDescription(rs.getString("genre_desc"));
                genres.add(genre);
            }

            genreCombo.setItems(genres);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadRoles() {
        try (Connection conn = DatabaseUtil.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM roles ORDER BY name")) {

            ObservableList<RoleEntity> roles = FXCollections.observableArrayList();
            while (rs.next()) {
                RoleEntity role = new RoleEntity();
                role.setRolesId(rs.getInt("roles_id"));
                role.setName(rs.getString("name"));
                roles.add(role);
            }

            userRoleCombo.setItems(roles);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void refreshBooks() {
    }

    private void refreshUsers() {
    }

    private void loadStatistics() {
    }

    @FXML
    private void searchBooks() {
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

    private void initializeData() {

    }
}
