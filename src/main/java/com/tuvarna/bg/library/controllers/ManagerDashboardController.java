package com.tuvarna.bg.library.controllers;

import com.tuvarna.bg.library.entity.*;
import com.tuvarna.bg.library.util.DatabaseUtil;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.cell.PropertyValueFactory;

import java.io.IOException;
import java.net.URL;
import java.sql.*;
import java.time.LocalDate;
import java.util.Objects;

public class ManagerDashboardController implements DashboardController {

    @FXML
    private Label userLabel;

    @FXML
    private ComboBox<UserEntity> customerCombo;
    @FXML
    private ComboBox<BookCopyEntity> copyCombo;
    @FXML
    private DatePicker dueDatePicker;
    @FXML
    private TableView<LoanEntity> loansTable;
    @FXML
    private TableColumn<LoanEntity, Integer> loanIdColumn;
    @FXML
    private TableColumn<LoanEntity, String> loanCustomerColumn;
    @FXML
    private TableColumn<LoanEntity, String> loanBookColumn;
    @FXML
    private TableColumn<LoanEntity, String> loanBorrowedColumn;
    @FXML
    private TableColumn<LoanEntity, String> loanDueColumn;
    @FXML
    private TableColumn<LoanEntity, String> loanStatusColumn;
    @FXML
    private TableView<ReservationEntity> reservationsTable;
    @FXML
    private TableColumn<ReservationEntity, Integer> reservationIdColumn;
    @FXML
    private TableColumn<ReservationEntity, String> reservationCustomerColumn;
    @FXML
    private TableColumn<ReservationEntity, String> reservationBookColumn;
    @FXML
    private TableColumn<ReservationEntity, String> reservationCreatedColumn;
    @FXML
    private TableColumn<ReservationEntity, String> reservationStatusColumn;
    @FXML
    private TextField newUsernameField, newFirstNameField, newLastNameField, newEmailField;
    @FXML
    private PasswordField newPasswordField;

    private UserEntity currentUser;

    @FXML
    public void initialize() {
        setupTableColumns();
        setupComboBoxes();
        loadInitialData();
    }

    private void setupTableColumns() {
        loanIdColumn.setCellValueFactory(new PropertyValueFactory<>("loansId"));
        loanCustomerColumn.setCellValueFactory(new PropertyValueFactory<>("user"));
        loanBookColumn.setCellValueFactory(new PropertyValueFactory<>("copy"));
        loanBorrowedColumn.setCellValueFactory(new PropertyValueFactory<>("borrowedAt"));
        loanDueColumn.setCellValueFactory(new PropertyValueFactory<>("dueDate"));
        loanStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        reservationIdColumn.setCellValueFactory(new PropertyValueFactory<>("reservationsId"));
        reservationCustomerColumn.setCellValueFactory(new PropertyValueFactory<>("user"));
        reservationBookColumn.setCellValueFactory(new PropertyValueFactory<>("book"));
        reservationCreatedColumn.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        reservationStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
    }

    private void setupComboBoxes() {
        loadCustomers();
        loadAvailableCopies();
    }

    private void loadInitialData() {
        refreshLoans();
        refreshReservations();
    }

    @FXML
    private void createLoan() {
        if (!validateLoanForm()) {
            return;
        }

        try {
            String sql = "INSERT INTO loans (users_id, staff_id, copy_id, due_date) VALUES (?, ?, ?, ?)";

            try (Connection conn = DatabaseUtil.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, customerCombo.getValue().getUsersId());
                stmt.setInt(2, currentUser.getUsersId());
                stmt.setInt(3, copyCombo.getValue().getCopiesId());
                stmt.setDate(4, Date.valueOf(dueDatePicker.getValue()));

                stmt.executeUpdate();

                // Update copy status to LOANED
                updateCopyStatus(copyCombo.getValue().getCopiesId(), "LOANED");

                showAlert("Success", "Loan created successfully!", Alert.AlertType.INFORMATION);
                clearLoanForm();
                refreshLoans();
                loadAvailableCopies(); // Refresh available copies
            }

        } catch (SQLException e) {
            showAlert("Error", "Failed to create loan: " + e.getMessage(), Alert.AlertType.ERROR);
            e.printStackTrace();
        }
    }

    private boolean validateLoanForm() {
        if (customerCombo.getValue() == null) {
            showAlert("Validation Error", "Please select a customer", Alert.AlertType.WARNING);
            return false;
        }

        if (copyCombo.getValue() == null) {
            showAlert("Validation Error", "Please select a book copy", Alert.AlertType.WARNING);
            return false;
        }

        if (dueDatePicker.getValue() == null || dueDatePicker.getValue().isBefore(LocalDate.now())) {
            showAlert("Validation Error", "Please select a valid due date", Alert.AlertType.WARNING);
            return false;
        }

        return true;
    }

    @FXML
    private void clearLoanForm() {
        customerCombo.setValue(null);
        copyCombo.setValue(null);
        dueDatePicker.setValue(null);
    }

    @FXML
    private void processReservation() {
        ReservationEntity selectedReservation = reservationsTable.getSelectionModel().getSelectedItem();
        if (selectedReservation == null) {
            showAlert("Warning", "Please select a reservation to process", Alert.AlertType.WARNING);
            return;
        }

        try {
            String sql = "UPDATE reservations SET status = 'READY' WHERE reservations_id = ?";
            try (Connection conn = DatabaseUtil.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, selectedReservation.getReservationsId());
                stmt.executeUpdate();

                showAlert("Success", "Reservation marked as ready!", Alert.AlertType.INFORMATION);
                refreshReservations();
            }

        } catch (SQLException e) {
            showAlert("Error", "Failed to process reservation: " + e.getMessage(), Alert.AlertType.ERROR);
            e.printStackTrace();
        }
    }

    @FXML
    private void registerCustomer() {
        if (!validateCustomerForm()) {
            return;
        }

        try {
            String sql = "INSERT INTO users (username, password, first_name, last_name, email, roles_id) " +
                    "VALUES (?, ?, ?, ?, ?, (SELECT roles_id FROM roles WHERE name = 'CLIENT'))";

            try (Connection conn = DatabaseUtil.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, newUsernameField.getText().trim());
                stmt.setString(2, newPasswordField.getText());
                stmt.setString(3, newFirstNameField.getText().trim());
                stmt.setString(4, newLastNameField.getText().trim());
                stmt.setString(5, newEmailField.getText().trim());

                stmt.executeUpdate();

                showAlert("Success", "Customer registered successfully!", Alert.AlertType.INFORMATION);
                clearCustomerForm();
                loadCustomers(); // Refresh customer list
            }

        } catch (SQLException e) {
            showAlert("Error", "Failed to register customer: " + e.getMessage(), Alert.AlertType.ERROR);
            e.printStackTrace();
        }
    }

    private boolean validateCustomerForm() {
        if (newUsernameField.getText().trim().isEmpty()) {
            showAlert("Validation Error", "Please enter a username", Alert.AlertType.WARNING);
            return false;
        }

        if (newPasswordField.getText().isEmpty()) {
            showAlert("Validation Error", "Please enter a password", Alert.AlertType.WARNING);
            return false;
        }

        if (newFirstNameField.getText().trim().isEmpty()) {
            showAlert("Validation Error", "Please enter a first name", Alert.AlertType.WARNING);
            return false;
        }

        if (newLastNameField.getText().trim().isEmpty()) {
            showAlert("Validation Error", "Please enter a last name", Alert.AlertType.WARNING);
            return false;
        }

        if (newEmailField.getText().trim().isEmpty()) {
            showAlert("Validation Error", "Please enter an email", Alert.AlertType.WARNING);
            return false;
        }

        return true;
    }

    @FXML
    private void clearCustomerForm() {
        newUsernameField.clear();
        newPasswordField.clear();
        newFirstNameField.clear();
        newLastNameField.clear();
        newEmailField.clear();
    }

    // Data Loading Methods
    private void loadCustomers() {
        try (Connection conn = DatabaseUtil.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM users WHERE roles_id = (SELECT roles_id FROM roles WHERE name = 'CLIENT') ORDER BY first_name, last_name")) {

            ObservableList<UserEntity> customers = FXCollections.observableArrayList();
            while (rs.next()) {
                UserEntity customer = new UserEntity();
                customer.setUsersId(rs.getInt("users_id"));
                customer.setUsername(rs.getString("username"));
                customer.setFirstName(rs.getString("first_name"));
                customer.setLastName(rs.getString("last_name"));
                customer.setEmail(rs.getString("email"));
                customers.add(customer);
            }

            customerCombo.setItems(customers);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadAvailableCopies() {
        try (Connection conn = DatabaseUtil.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM book_copies WHERE status = 'AVAILABLE' ORDER BY copies_id")) {

            ObservableList<BookCopyEntity> copies = FXCollections.observableArrayList();
            while (rs.next()) {
                BookCopyEntity copy = new BookCopyEntity();
                copy.setCopiesId(rs.getInt("copies_id"));
                copy.setStatus(rs.getString("status"));
                copy.setAcquiredAt(rs.getDate("acquired_at") != null ?
                        rs.getDate("acquired_at").toLocalDate() : null);

                // Load book information
                BookEntity book = loadBook(rs.getInt("books_id"));
                copy.setBook(book);

                copies.add(copy);
            }

            copyCombo.setItems(copies);

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

    private void refreshLoans() {
        try (Connection conn = DatabaseUtil.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM loans WHERE returned_at IS NULL ORDER BY borrowed_at DESC")) {

            ObservableList<LoanEntity> loans = FXCollections.observableArrayList();
            while (rs.next()) {
                LoanEntity loan = new LoanEntity();
                loan.setLoansId(rs.getInt("loans_id"));
                loan.setBorrowedAt(rs.getTimestamp("borrowed_at") != null ?
                        rs.getTimestamp("borrowed_at").toLocalDateTime() : null);
                loan.setDueDate(rs.getDate("due_date") != null ?
                        rs.getDate("due_date").toLocalDate() : null);

                // Load user information
                UserEntity user = loadUser(rs.getInt("users_id"));
                loan.setUser(user);

                // Load book copy information
                BookCopyEntity copy = loadBookCopy(rs.getInt("copy_id"));
                loan.setCopy(copy);

                loans.add(loan);
            }

            loansTable.setItems(loans);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void refreshReservations() {
        try (Connection conn = DatabaseUtil.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM reservations WHERE status = 'PENDING' ORDER BY created_at DESC")) {

            ObservableList<ReservationEntity> reservations = FXCollections.observableArrayList();
            while (rs.next()) {
                ReservationEntity reservation = new ReservationEntity();
                reservation.setReservationsId(rs.getInt("reservations_id"));
                reservation.setCreatedAt(rs.getTimestamp("created_at") != null ?
                        rs.getTimestamp("created_at").toLocalDateTime() : null);
                reservation.setStatus(rs.getString("status"));

                // Load user information
                UserEntity user = loadUser(rs.getInt("user_id"));
                reservation.setUser(user);

                // Load book information
                BookEntity book = loadBook(rs.getInt("book_id"));
                reservation.setBook(book);

                reservations.add(reservation);
            }

            reservationsTable.setItems(reservations);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private UserEntity loadUser(int userId) throws SQLException {
        String sql = "SELECT * FROM users WHERE users_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    UserEntity user = new UserEntity();
                    user.setUsersId(rs.getInt("users_id"));
                    user.setUsername(rs.getString("username"));
                    user.setFirstName(rs.getString("first_name"));
                    user.setLastName(rs.getString("last_name"));
                    user.setEmail(rs.getString("email"));
                    return user;
                }
            }
        }
        return null;
    }

    private BookCopyEntity loadBookCopy(int copyId) throws SQLException {
        //
        return null;
    }

    private void updateCopyStatus(int copyId, String status) throws SQLException {
        String sql = "UPDATE book_copies SET status = ? WHERE copies_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, status);
            stmt.setInt(2, copyId);
            stmt.executeUpdate();
        }
    }

    private void initializeData() {

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
}