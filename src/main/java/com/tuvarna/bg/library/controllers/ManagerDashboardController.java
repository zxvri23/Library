package com.tuvarna.bg.library.controllers;

import com.tuvarna.bg.library.entity.*;
import com.tuvarna.bg.library.util.DatabaseUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.net.URL;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ManagerDashboardController implements DashboardController {

    @FXML private Label userLabel;

    @FXML private ComboBox<UserEntity> customerCombo;
    @FXML private ComboBox<BookCopyEntity> copyCombo;
    @FXML private DatePicker dueDatePicker;

    @FXML private TableView<LoanEntity> loansTable;
    @FXML private TableColumn<LoanEntity, Integer> loanIdColumn;
    @FXML private TableColumn<LoanEntity, String> loanCustomerColumn;
    @FXML private TableColumn<LoanEntity, String> loanBookColumn;
    @FXML private TableColumn<LoanEntity, String> loanBorrowedColumn;
    @FXML private TableColumn<LoanEntity, String> loanDueColumn;
    @FXML private TableColumn<LoanEntity, String> loanStatusColumn;

    @FXML private TableView<ReservationEntity> reservationsTable;
    @FXML private TableColumn<ReservationEntity, Integer> reservationIdColumn;
    @FXML private TableColumn<ReservationEntity, String> reservationCustomerColumn;
    @FXML private TableColumn<ReservationEntity, String> reservationBookColumn;
    @FXML private TableColumn<ReservationEntity, String> reservationCreatedColumn;
    @FXML private TableColumn<ReservationEntity, String> reservationStatusColumn;

    @FXML private TextField newUsernameField, newFirstNameField, newLastNameField, newEmailField;
    @FXML private PasswordField newPasswordField;

    private UserEntity currentUser;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    // ---------- Validation helpers ----------
    private static void markError(Control c) { if (c != null) c.setStyle("-fx-border-color:#e74c3c;-fx-border-radius:6;"); }
    private static void clearError(Control c) { if (c != null) c.setStyle(""); }

    // first/last name: must start with uppercase letter, only letters after that (Unicode aware)
    private static boolean isValidName(String s) {
        return s != null && s.trim().matches("\\p{Lu}[\\p{L}]*");
    }

    // username must NOT be numbers only
    private static boolean isNumbersOnly(String s) {
        return s != null && s.trim().matches("\\d+");
    }

    // email must end with @gmail.com (case-insensitive)
    private static boolean isGmail(String s) {
        return s != null && s.trim().toLowerCase().endsWith("@gmail.com");
    }

    // DB check for username uniqueness (case-insensitive)
    private boolean usernameExists(String username) {
        if (username == null || username.trim().isEmpty()) return false;
        String sql = "SELECT 1 FROM users WHERE LOWER(username) = LOWER(?) LIMIT 1";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement st = conn.prepareStatement(sql)) {
            st.setString(1, username.trim());
            try (ResultSet rs = st.executeQuery()) { return rs.next(); }
        } catch (SQLException e) {
            // If DB fails, be conservative and say it exists to prevent duplicates.
            e.printStackTrace();
            return true;
        }
    }
    @FXML
    public void initialize() {
        setupTableColumns();
        setupComboBoxes();
        loadInitialData();
    }

    /* --------------------------------------------------------
     * Columns: fit width; remove Actions; friendly cell content
     * -------------------------------------------------------- */
    private void setupTableColumns() {
        /* ===== Loans table: fill width ===== */
        loansTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // "weights" so constrained policy distributes space nicely
        loanIdColumn.setMaxWidth(1f * Integer.MAX_VALUE * 1);          // ~8-10%
        loanCustomerColumn.setMaxWidth(1f * Integer.MAX_VALUE * 2);     // ~18-20%
        loanBookColumn.setMaxWidth(1f * Integer.MAX_VALUE * 2);         // ~18-20%
        loanBorrowedColumn.setMaxWidth(1f * Integer.MAX_VALUE * 2);     // ~18-20%
        loanDueColumn.setMaxWidth(1f * Integer.MAX_VALUE * 1);          // ~8-10%
        loanStatusColumn.setMaxWidth(1f * Integer.MAX_VALUE * 1);       // ~8-10%

        // Remove any "Actions" column if present in FXML
        removeActionsColumn(loansTable);

        // Value factories (friendly strings)
        loanIdColumn.setCellValueFactory(new PropertyValueFactory<>("loansId"));

        loanCustomerColumn.setCellValueFactory(cd -> {
            UserEntity u = cd.getValue().getUser();
            String s = (u == null) ? "" : (u.getFullName() != null ? u.getFullName() : u.getUsername());
            return new SimpleStringProperty(s);
        });

        loanBookColumn.setCellValueFactory(cd -> {
            BookCopyEntity copy = cd.getValue().getCopy();
            String s = "";
            if (copy != null) {
                BookEntity b = copy.getBook();
                if (b != null && b.getTitle() != null && !b.getTitle().isEmpty()) {
                    s = b.getTitle();
                } else {
                    s = "Copy #" + copy.getCopiesId();
                }
            }
            return new SimpleStringProperty(s);
        });

        loanBorrowedColumn.setCellValueFactory(cd -> {
            LocalDateTime dt = cd.getValue().getBorrowedAt();
            return new SimpleStringProperty(dt == null ? "" : DATETIME_FMT.format(dt));
        });

        loanDueColumn.setCellValueFactory(cd -> {
            LocalDate d = cd.getValue().getDueDate();
            return new SimpleStringProperty(d == null ? "" : DATE_FMT.format(d));
        });

        // Computed status: Returned / Overdue / Active (with color)
        loanStatusColumn.setCellValueFactory(cd -> {
            LoanEntity l = cd.getValue();
            String status;
            if (l == null) {
                status = "";
            } else if (l.getReturnedAt() != null) {
                status = "Returned";
            } else {
                LocalDate due = l.getDueDate();
                status = (due != null && due.isBefore(LocalDate.now())) ? "Overdue" : "Active";
            }
            return new SimpleStringProperty(status);
        });

        loanStatusColumn.setCellFactory(col -> new TableCell<LoanEntity, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(item);
                switch (item) {
                    case "Overdue":
                        setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                        break;
                    case "Returned":
                        setStyle("-fx-text-fill: #7f8c8d;");
                        break;
                    default: // Active
                        setStyle("-fx-text-fill: #27ae60;");
                }
            }
        });

        /* ===== Reservations table: fill width ===== */
        reservationsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        reservationIdColumn.setMaxWidth(1f * Integer.MAX_VALUE * 1);
        reservationCustomerColumn.setMaxWidth(1f * Integer.MAX_VALUE * 2);
        reservationBookColumn.setMaxWidth(1f * Integer.MAX_VALUE * 2);
        reservationCreatedColumn.setMaxWidth(1f * Integer.MAX_VALUE * 2);
        reservationStatusColumn.setMaxWidth(1f * Integer.MAX_VALUE * 1);

        removeActionsColumn(reservationsTable);

        // Value factories (friendly strings)
        reservationIdColumn.setCellValueFactory(new PropertyValueFactory<>("reservationsId"));

        reservationCustomerColumn.setCellValueFactory(cd -> {
            UserEntity u = cd.getValue().getUser();
            String s = (u == null) ? "" : (u.getFullName() != null ? u.getFullName() : u.getUsername());
            return new SimpleStringProperty(s);
        });

        reservationBookColumn.setCellValueFactory(cd -> {
            BookEntity b = cd.getValue().getBook();
            String s = (b == null) ? "" : (b.getTitle() != null ? b.getTitle() : ("Book #" + b.getBooksId()));
            return new SimpleStringProperty(s);
        });

        reservationCreatedColumn.setCellValueFactory(cd -> {
            LocalDateTime dt = cd.getValue().getCreatedAt();
            return new SimpleStringProperty(dt == null ? "" : DATETIME_FMT.format(dt));
        });

        reservationStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        reservationStatusColumn.setCellFactory(col -> new TableCell<ReservationEntity, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(item);
                switch (item) {
                    case "PENDING":
                        setStyle("-fx-text-fill: #f39c12;");
                        break;
                    case "READY":
                        setStyle("-fx-text-fill: #27ae60;");
                        break;
                    case "COMPLETED":
                        setStyle("-fx-text-fill: #7f8c8d;");
                        break;
                    case "CANCELLED":
                        setStyle("-fx-text-fill: #e74c3c;");
                        break;
                    default:
                        setStyle("");
                }
            }
        });

        // Double-click to toggle reservation status
        reservationsTable.setRowFactory(tv -> {
            TableRow<ReservationEntity> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    ReservationEntity reservation = row.getItem();
                    toggleReservationStatus(reservation);
                }
            });
            return row;
        });
    }

    private static void removeActionsColumn(TableView<?> table) {
        // Remove any column with header text exactly "Actions"
        List<TableColumn<?, ?>> toRemove = new ArrayList<>();
        for (TableColumn<?, ?> c : table.getColumns()) {
            if ("Actions".equalsIgnoreCase(c.getText())) {
                toRemove.add(c);
            }
        }
        table.getColumns().removeAll(toRemove);
    }

    /* ------------------------- Combo boxes ------------------------- */
    private void setupComboBoxes() {
        loadCustomers();
        loadAvailableCopies();
    }

    private void loadInitialData() {
        refreshLoans();
        refreshReservations();
    }

    /* ------------------------- Create Loan ------------------------- */
    @FXML
    private void createLoan() {
        if (!validateLoanForm()) return;

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
        customerCombo.getSelectionModel().clearSelection();
        copyCombo.getSelectionModel().clearSelection();
        dueDatePicker.setValue(null);
    }

    /* -------------------- Customer registration -------------------- */
    @FXML
    private void registerCustomer() {
        if (!validateCustomerForm()) return;

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
        // clear previous red borders
        clearError(newUsernameField);
        clearError(newPasswordField);
        clearError(newFirstNameField);
        clearError(newLastNameField);
        clearError(newEmailField);

        // Username
        String username = newUsernameField.getText() == null ? "" : newUsernameField.getText().trim();
        if (username.isEmpty()) {
            markError(newUsernameField);
            showAlert("Validation Error", "Please enter a username.", Alert.AlertType.WARNING);
            return false;
        }
        if (isNumbersOnly(username)) {
            markError(newUsernameField);
            showAlert("Validation Error", "Username cannot be numbers only.", Alert.AlertType.WARNING);
            return false;
        }
        if (usernameExists(username)) {
            markError(newUsernameField);
            showAlert("Validation Error", "This username is already taken.", Alert.AlertType.WARNING);
            return false;
        }

        // Password (just non-empty per your spec)
        if (newPasswordField.getText() == null || newPasswordField.getText().isEmpty()) {
            markError(newPasswordField);
            showAlert("Validation Error", "Please enter a password.", Alert.AlertType.WARNING);
            return false;
        }

        // First name
        String firstName = newFirstNameField.getText() == null ? "" : newFirstNameField.getText().trim();
        if (!isValidName(firstName)) {
            markError(newFirstNameField);
            showAlert("Validation Error", "First name must start with an uppercase letter and contain letters only.", Alert.AlertType.WARNING);
            return false;
        }

        // Last name
        String lastName = newLastNameField.getText() == null ? "" : newLastNameField.getText().trim();
        if (!isValidName(lastName)) {
            markError(newLastNameField);
            showAlert("Validation Error", "Last name must start with an uppercase letter and contain letters only.", Alert.AlertType.WARNING);
            return false;
        }

        // Email
        String email = newEmailField.getText() == null ? "" : newEmailField.getText().trim();
        if (!isGmail(email)) {
            markError(newEmailField);
            showAlert("Validation Error", "Email must end with @gmail.com.", Alert.AlertType.WARNING);
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

    /* --------------------------- Data load ------------------------- */
    private void loadCustomers() {
        try (Connection conn = DatabaseUtil.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT * FROM users " +
                             "WHERE roles_id = (SELECT roles_id FROM roles WHERE name = 'CLIENT') " +
                             "ORDER BY first_name, last_name")) {

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

        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void loadAvailableCopies() {
        try (Connection conn = DatabaseUtil.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT copies_id, books_id, status, acquired_at FROM book_copies " +
                             "WHERE status = 'AVAILABLE' ORDER BY copies_id")) {

            ObservableList<BookCopyEntity> copies = FXCollections.observableArrayList();
            while (rs.next()) {
                BookCopyEntity copy = new BookCopyEntity();
                copy.setCopiesId(rs.getInt("copies_id"));
                copy.setStatus(rs.getString("status"));
                Date acq = rs.getDate("acquired_at");
                copy.setAcquiredAt(acq != null ? acq.toLocalDate() : null);

                // Load book information
                BookEntity book = loadBook(rs.getInt("books_id"));
                copy.setBook(book);

                copies.add(copy);
            }
            copyCombo.setItems(copies);

        } catch (SQLException e) { e.printStackTrace(); }
    }

    private BookEntity loadBook(int bookId) throws SQLException {
        String sql = "SELECT books_id, title, isbn FROM books WHERE books_id = ?";
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
             // Shows active loans (not returned). If you also want returned, remove the WHERE clause.
             ResultSet rs = stmt.executeQuery(
                     "SELECT loans_id, users_id, staff_id, copy_id, borrowed_at, due_date, returned_at " +
                             "FROM loans " +
                             "ORDER BY borrowed_at DESC")) {

            ObservableList<LoanEntity> loans = FXCollections.observableArrayList();
            while (rs.next()) {
                LoanEntity loan = new LoanEntity();
                loan.setLoansId(rs.getInt("loans_id"));
                Timestamp bt = rs.getTimestamp("borrowed_at");
                loan.setBorrowedAt(bt != null ? bt.toLocalDateTime() : null);
                Date dd = rs.getDate("due_date");
                loan.setDueDate(dd != null ? dd.toLocalDate() : null);
                Timestamp rt = rs.getTimestamp("returned_at");
                loan.setReturnedAt(rt != null ? rt.toLocalDateTime() : null);

                // Load user & copy
                UserEntity user = loadUser(rs.getInt("users_id"));
                loan.setUser(user);
                BookCopyEntity copy = loadBookCopy(rs.getInt("copy_id"));
                loan.setCopy(copy);

                loans.add(loan);
            }
            loansTable.setItems(loans);

        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void refreshReservations() {
        try (Connection conn = DatabaseUtil.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT reservations_id, user_id, book_id, created_at, status " +
                             "FROM reservations " +
                             "ORDER BY created_at DESC")) {

            ObservableList<ReservationEntity> reservations = FXCollections.observableArrayList();
            while (rs.next()) {
                ReservationEntity reservation = new ReservationEntity();
                reservation.setReservationsId(rs.getInt("reservations_id"));
                Timestamp ct = rs.getTimestamp("created_at");
                reservation.setCreatedAt(ct != null ? ct.toLocalDateTime() : null);
                reservation.setStatus(rs.getString("status"));

                UserEntity user = loadUser(rs.getInt("user_id"));
                reservation.setUser(user);

                BookEntity book = loadBook(rs.getInt("book_id"));
                reservation.setBook(book);

                reservations.add(reservation);
            }
            reservationsTable.setItems(reservations);

        } catch (SQLException e) { e.printStackTrace(); }
    }

    private UserEntity loadUser(int userId) throws SQLException {
        String sql = "SELECT users_id, username, first_name, last_name, email FROM users WHERE users_id = ?";
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
        String sql = "SELECT copies_id, books_id, status, acquired_at FROM book_copies WHERE copies_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, copyId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    BookCopyEntity copy = new BookCopyEntity();
                    copy.setCopiesId(rs.getInt("copies_id"));
                    copy.setStatus(rs.getString("status"));
                    Date acq = rs.getDate("acquired_at");
                    copy.setAcquiredAt(acq != null ? acq.toLocalDate() : null);

                    BookEntity book = loadBook(rs.getInt("books_id"));
                    copy.setBook(book);
                    return copy;
                }
            }
        }
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

    /* --------------------------- misc --------------------------- */
    private void initializeData() { }

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

    private void toggleReservationStatus(ReservationEntity reservation) {
        String currentStatus = reservation.getStatus();
        String newStatus;
        switch (currentStatus) {
            case "PENDING":   newStatus = "READY";      break;
            case "READY":     newStatus = "COMPLETED";  break;
            case "COMPLETED": newStatus = "CANCELLED";  break;
            case "CANCELLED": newStatus = "PENDING";    break;
            default:          newStatus = "PENDING";
        }

        try {
            String sql = "UPDATE reservations SET status = ? WHERE reservations_id = ?";
            try (Connection conn = DatabaseUtil.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, newStatus);
                stmt.setInt(2, reservation.getReservationsId());

                int rows = stmt.executeUpdate();
                if (rows > 0) {
                    reservation.setStatus(newStatus);
                    reservationsTable.refresh();
                    showAlert("Success",
                            "Reservation status changed from " + currentStatus + " to " + newStatus,
                            Alert.AlertType.INFORMATION);
                } else {
                    showAlert("Error", "Failed to update reservation status", Alert.AlertType.ERROR);
                }
            }
        } catch (SQLException e) {
            showAlert("Error", "Database error: " + e.getMessage(), Alert.AlertType.ERROR);
            e.printStackTrace();
        }
    }

    @Override
    public void setCurrentUser(UserEntity user) {
        this.currentUser = user;
        userLabel.setText("Welcome, " + user.getFullName());
        initializeData();
    }

    @Override
    public UserEntity getCurrentUser() { return currentUser; }
}
