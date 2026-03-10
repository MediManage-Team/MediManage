package org.example.MediManage.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.example.MediManage.dao.AttendanceDAO;
import org.example.MediManage.util.UserSession;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Controller for Employee Attendance view — check in/out + daily log.
 */
public class AttendanceController {

    @FXML
    private Label lblStatus;
    @FXML
    private Label lblSummary;
    @FXML
    private Button btnCheckIn;
    @FXML
    private Button btnCheckOut;
    @FXML
    private DatePicker datePicker;
    @FXML
    private TableView<Map<String, Object>> attendanceTable;
    @FXML
    private TableColumn<Map<String, Object>, String> colUser;
    @FXML
    private TableColumn<Map<String, Object>, String> colRole;
    @FXML
    private TableColumn<Map<String, Object>, String> colCheckIn;
    @FXML
    private TableColumn<Map<String, Object>, String> colCheckOut;
    @FXML
    private TableColumn<Map<String, Object>, String> colHours;

    private final AttendanceDAO attendanceDAO = new AttendanceDAO();
    private final ObservableList<Map<String, Object>> records = FXCollections.observableArrayList();
    private int activeCheckInId = -1;

    @FXML
    public void initialize() {
        colUser.setCellValueFactory(cd -> new SimpleStringProperty(str(cd.getValue().get("username"))));
        colRole.setCellValueFactory(cd -> new SimpleStringProperty(str(cd.getValue().get("role"))));
        colCheckIn.setCellValueFactory(cd -> new SimpleStringProperty(str(cd.getValue().get("checkIn"))));
        colCheckOut.setCellValueFactory(cd -> new SimpleStringProperty(str(cd.getValue().get("checkOut"))));
        colHours.setCellValueFactory(cd -> {
            Object h = cd.getValue().get("totalHours");
            return new SimpleStringProperty(h != null ? String.format("%.1f", ((Number) h).doubleValue()) : "-");
        });

        attendanceTable.setItems(records);
        datePicker.setValue(LocalDate.now());
        refreshStatus();
        loadDate(LocalDate.now().toString());
    }

    @FXML
    private void handleCheckIn() {
        try {
            int userId = UserSession.getInstance().getUser().getId();
            int existing = attendanceDAO.getActiveCheckIn(userId);
            if (existing > 0) {
                showAlert(Alert.AlertType.WARNING, "Already Checked In",
                        "You already have an active check-in for today.");
                return;
            }
            int id = attendanceDAO.checkIn(userId);
            showAlert(Alert.AlertType.INFORMATION, "Checked In", "Check-in recorded (ID: " + id + ").");
            refreshStatus();
            loadDate(LocalDate.now().toString());
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Check-In Error", e.getMessage());
        }
    }

    @FXML
    private void handleCheckOut() {
        try {
            int userId = UserSession.getInstance().getUser().getId();
            int checkInId = attendanceDAO.getActiveCheckIn(userId);
            if (checkInId < 0) {
                showAlert(Alert.AlertType.WARNING, "Not Checked In", "No active check-in found for today.");
                return;
            }
            boolean success = attendanceDAO.checkOut(checkInId);
            if (success) {
                showAlert(Alert.AlertType.INFORMATION, "Checked Out", "Check-out recorded. Hours calculated.");
            }
            refreshStatus();
            loadDate(LocalDate.now().toString());
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Check-Out Error", e.getMessage());
        }
    }

    @FXML
    private void handleLoadDate() {
        LocalDate date = datePicker.getValue();
        if (date == null)
            date = LocalDate.now();
        loadDate(date.toString());
    }

    private void loadDate(String date) {
        try {
            List<Map<String, Object>> rows = attendanceDAO.getAttendanceByDate(date);
            records.setAll(rows);
            double totalHrs = rows.stream()
                    .filter(r -> r.get("totalHours") != null)
                    .mapToDouble(r -> ((Number) r.get("totalHours")).doubleValue())
                    .sum();
            lblSummary.setText(String.format("%d records | %.1f total hours", rows.size(), totalHrs));
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Load Error", e.getMessage());
        }
    }

    private void refreshStatus() {
        try {
            int userId = UserSession.getInstance().getUser().getId();
            activeCheckInId = attendanceDAO.getActiveCheckIn(userId);
            if (activeCheckInId > 0) {
                lblStatus.setText("Status: ✅ Checked In");
                btnCheckIn.setDisable(true);
                btnCheckOut.setDisable(false);
            } else {
                lblStatus.setText("Status: Not checked in");
                btnCheckIn.setDisable(false);
                btnCheckOut.setDisable(true);
            }
        } catch (SQLException e) {
            lblStatus.setText("Status: Error");
        }
    }

    private String str(Object o) {
        return o != null ? o.toString() : "-";
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.show();
    }
}
