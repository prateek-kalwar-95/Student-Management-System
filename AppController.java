import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.Color;
import java.sql.SQLException;
import java.util.List;

/**
 * CONTROLLER: Coordinates interactions between MainView and DatabaseModel.
 * Handles validation, button actions, live search, live grade calculations,
 * and refreshes UI data models.
 */
public class AppController {
    private final DatabaseModel model;
    private final MainView view;

    public AppController(DatabaseModel model, MainView view) {
        this.model = model;
        this.view = view;

        initListeners();
        loadAllData();
    }

    private void initListeners() {
        // --- STUDENT LISTENERS ---
        view.btnStudAdd.addActionListener(e -> handleAddStudent());
        view.btnStudUpdate.addActionListener(e -> handleUpdateStudent());
        view.btnStudDelete.addActionListener(e -> handleDeleteStudent());
        view.btnStudClear.addActionListener(e -> clearStudentForm());

        // When table row is clicked, populate fields
        view.studTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && view.studTable.getSelectedRow() != -1) {
                int modelRow = view.studTable.convertRowIndexToModel(view.studTable.getSelectedRow());
                view.txtStudRoll.setText(view.studTableModel.getValueAt(modelRow, 0).toString());
                view.txtStudRoll.setEditable(false); // primary key shouldn't be edited
                // during update
                view.txtStudName.setText(view.studTableModel.getValueAt(modelRow, 1).toString());
                view.txtStudEmail.setText(view.studTableModel.getValueAt(modelRow, 2).toString());
                view.txtStudDept.setText(view.studTableModel.getValueAt(modelRow, 3).toString());
                // view.txtStudDept.setEditable(false);
                view.txtStudPhone.setText(view.studTableModel.getValueAt(modelRow, 4).toString());
            }
        });

        // Live search filter for students
        setupSearchFilter(view.txtStudSearch, view.studSorter);

        // --- COURSE LISTENERS ---
        view.btnCourseAdd.addActionListener(e -> handleAddCourse());
        view.btnCourseUpdate.addActionListener(e -> handleUpdateCourse());
        view.btnCourseDelete.addActionListener(e -> handleDeleteCourse());
        view.btnCourseClear.addActionListener(e -> clearCourseForm());

        view.courseTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && view.courseTable.getSelectedRow() != -1) {
                int modelRow = view.courseTable.convertRowIndexToModel(view.courseTable.getSelectedRow());
                view.txtCourseCode.setText(view.courseTableModel.getValueAt(modelRow, 0).toString());
                view.txtCourseCode.setEditable(false);
                view.txtCourseName.setText(view.courseTableModel.getValueAt(modelRow, 1).toString());
                view.txtCourseCredits.setText(view.courseTableModel.getValueAt(modelRow, 2).toString());
                view.txtCourseInstructor.setText(view.courseTableModel.getValueAt(modelRow, 3).toString());
            }
        });

        setupSearchFilter(view.txtCourseSearch, view.courseSorter);

        // --- ENROLLMENT & GRADING LISTENERS ---
        view.btnEnrollSave.addActionListener(e -> handleSaveEnrollment());
        view.btnEnrollDelete.addActionListener(e -> handleDeleteEnrollment());
        view.btnEnrollClear.addActionListener(e -> clearEnrollmentForm());

        view.enrollTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && view.enrollTable.getSelectedRow() != -1) {
                int modelRow = view.enrollTable.convertRowIndexToModel(view.enrollTable.getSelectedRow());
                String rollStr = view.enrollTableModel.getValueAt(modelRow, 1).toString();
                String code = view.enrollTableModel.getValueAt(modelRow, 3).toString();

                selectStudentInCombo(rollStr);
                selectCourseInCombo(code);

                String attStr = view.enrollTableModel.getValueAt(modelRow, 5).toString().replace("%", "");
                String examStr = view.enrollTableModel.getValueAt(modelRow, 7).toString().replace(" / 90", "");

                view.txtAttendancePct.setText(attStr);
                view.txtExamMarks.setText(examStr);
                updateLiveGradePreview();
            }
        });

        // Live calculation preview when typing attendance or exam marks
        DocumentListener calcListener = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                updateLiveGradePreview();
            }

            public void removeUpdate(DocumentEvent e) {
                updateLiveGradePreview();
            }

            public void changedUpdate(DocumentEvent e) {
                updateLiveGradePreview();
            }
        };
        view.txtAttendancePct.getDocument().addDocumentListener(calcListener);
        view.txtExamMarks.getDocument().addDocumentListener(calcListener);

        // Restrict phone field: only digits allowed, maximum 10 digits (blocks any further input)
        ((AbstractDocument) view.txtStudPhone.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
                if (string == null) return;
                if (isAllowed(fb.getDocument().getLength(), string)) {
                    super.insertString(fb, offset, string, attr);
                }
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
                if (text == null) return;
                if (isAllowed(fb.getDocument().getLength() - length, text)) {
                    super.replace(fb, offset, length, text, attrs);
                }
            }

            private boolean isAllowed(int currentLengthWithoutReplaced, String text) {
                if (!text.matches("\\d*")) {
                    return false;
                }
                return (currentLengthWithoutReplaced + text.length()) <= 10;
            }
        });

        // Live phone validation preview as user types
        DocumentListener phoneListener = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { updatePhoneFeedback(); }
            public void removeUpdate(DocumentEvent e) { updatePhoneFeedback(); }
            public void changedUpdate(DocumentEvent e) { updatePhoneFeedback(); }
        };
        view.txtStudPhone.getDocument().addDocumentListener(phoneListener);

        setupSearchFilter(view.txtEnrollSearch, view.enrollSorter);

        // --- DASHBOARD LISTENER ---
        view.btnRefreshDashboard.addActionListener(e -> loadDashboardData());
    }

    private void updatePhoneFeedback() {
        String phone = view.txtStudPhone.getText().trim();
        if (phone.isEmpty()) {
            view.lblStudPhoneHint.setText("Must be a 10-digit number");
            view.lblStudPhoneHint.setForeground(new Color(110, 110, 110));
        } else if (!phone.matches("\\d+")) {
            view.lblStudPhoneHint.setText("Invalid: Numbers only (digits 0-9)!");
            view.lblStudPhoneHint.setForeground(new Color(211, 47, 47));
        } else if (phone.length() < 10) {
            view.lblStudPhoneHint.setText("Entering number: " + phone.length() + "/10 digits");
            view.lblStudPhoneHint.setForeground(new Color(230, 81, 0));
        } else {
            view.lblStudPhoneHint.setText("✓ 10 digits completed (Limit reached)");
            view.lblStudPhoneHint.setForeground(new Color(46, 125, 50));
        }
    }

    private void updateLiveGradePreview() {
        try {
            String attText = view.txtAttendancePct.getText().trim();
            String examText = view.txtExamMarks.getText().trim();

            if (attText.isEmpty() || examText.isEmpty()) {
                view.lblCalculatedPreview.setText("Preview: Attendance Marks: - | Total: - | Grade: -");
                return;
            }

            double attPct = Double.parseDouble(attText);
            double examMarks = Double.parseDouble(examText);

            if (attPct < 0 || attPct > 100 || examMarks < 0 || examMarks > 90) {
                view.lblCalculatedPreview.setText("Preview: [Values out of range: Att 0-100, Exam 0-90]");
                return;
            }

            double attMarks = model.calculateAttendanceMarks(attPct);
            double total = examMarks + attMarks;
            String grade = model.calculateGrade(total);

            view.lblCalculatedPreview.setText(String.format(
                    "Preview: Attendance Marks: %.1f/10 | Total: %.1f/100 | Grade: %s",
                    attMarks, total, grade));
        } catch (NumberFormatException ignored) {
            view.lblCalculatedPreview.setText("Preview: [Please enter valid numbers]");
        }
    }

    // ==========================================
    // DATA LOADING METHODS
    // ==========================================

    public void loadAllData() {
        loadStudents();
        loadCourses();
        loadEnrollments();
        loadDashboardData();
    }

    private void loadStudents() {
        try {
            view.studTableModel.setRowCount(0);
            List<Object[]> list = model.getAllStudents();
            for (Object[] row : list) {
                view.studTableModel.addRow(row);
            }
            refreshStudentDropdown(list);
        } catch (SQLException ex) {
            view.showMessage("Failed to load students: " + ex.getMessage(), "Database Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadCourses() {
        try {
            view.courseTableModel.setRowCount(0);
            List<Object[]> list = model.getAllCourses();
            for (Object[] row : list) {
                view.courseTableModel.addRow(row);
            }
            refreshCourseDropdown(list);
        } catch (SQLException ex) {
            view.showMessage("Failed to load courses: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadEnrollments() {
        try {
            view.enrollTableModel.setRowCount(0);
            List<Object[]> list = model.getAllEnrollments();
            for (Object[] row : list) {
                view.enrollTableModel.addRow(row);
            }
        } catch (SQLException ex) {
            view.showMessage("Failed to load enrollments: " + ex.getMessage(), "Database Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadDashboardData() {
        try {
            int students = model.getStudentCount();
            int courses = model.getCourseCount();
            int enrollments = model.getEnrollmentCount();
            double avgScore = model.getAverageTotalMarks();

            view.lblStatStudents.setText(String.valueOf(students));
            view.lblStatCourses.setText(String.valueOf(courses));
            view.lblStatEnrollments.setText(String.valueOf(enrollments));
            view.lblStatAvgScore.setText(String.format("%.1f", avgScore));

            view.shortageTableModel.setRowCount(0);
            List<Object[]> shortage = model.getLowAttendanceStudents();
            for (Object[] row : shortage) {
                view.shortageTableModel.addRow(row);
            }
        } catch (SQLException ex) {
            view.showMessage("Failed to load dashboard: " + ex.getMessage(), "Database Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refreshStudentDropdown(List<Object[]> students) {
        view.comboEnrollStudent.removeAllItems();
        for (Object[] s : students) {
            view.comboEnrollStudent.addItem(s[0] + " - " + s[1]);
        }
    }

    private void refreshCourseDropdown(List<Object[]> courses) {
        view.comboEnrollCourse.removeAllItems();
        for (Object[] c : courses) {
            view.comboEnrollCourse.addItem(c[0] + " - " + c[1]);
        }
    }

    private void selectStudentInCombo(String rollStr) {
        for (int i = 0; i < view.comboEnrollStudent.getItemCount(); i++) {
            if (view.comboEnrollStudent.getItemAt(i).startsWith(rollStr + " - ")) {
                view.comboEnrollStudent.setSelectedIndex(i);
                break;
            }
        }
    }

    private void selectCourseInCombo(String courseCode) {
        for (int i = 0; i < view.comboEnrollCourse.getItemCount(); i++) {
            if (view.comboEnrollCourse.getItemAt(i).startsWith(courseCode + " - ")) {
                view.comboEnrollCourse.setSelectedIndex(i);
                break;
            }
        }
    }

    // ==========================================
    // ACTION HANDLERS
    // ==========================================

    private void handleAddStudent() {
        try {
            String rollStr = view.txtStudRoll.getText().trim();
            String name = view.txtStudName.getText().trim();
            String email = view.txtStudEmail.getText().trim();
            String dept = view.txtStudDept.getText().trim();
            String phone = view.txtStudPhone.getText().trim();

            if (rollStr.isEmpty() || name.isEmpty()) {
                view.showMessage("Roll Number and Name are required!", "Validation Error", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (phone.isEmpty()) {
                view.showMessage("Phone number is required and must be a 10-digit number!", "Validation Error", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (!phone.matches("\\d+")) {
                view.showMessage("Phone number must contain only numbers (0-9). Letters or special characters are not allowed!", "Validation Error", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (phone.length() != 10) {
                view.showMessage("Phone number must be exactly 10 digits! (You entered " + phone.length() + " digits)", "Validation Error", JOptionPane.WARNING_MESSAGE);
                return;
            }

            long roll = Long.parseLong(rollStr);
            model.addStudent(roll, name, email, dept, phone);
            view.showMessage("Student added successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            clearStudentForm();
            loadStudents();
            loadDashboardData();
        } catch (NumberFormatException ex) {
            view.showMessage("Roll Number must be a valid number!", "Input Error", JOptionPane.ERROR_MESSAGE);
        } catch (SQLException ex) {
            view.showMessage("Database error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleUpdateStudent() {
        try {
            String rollStr = view.txtStudRoll.getText().trim();
            String name = view.txtStudName.getText().trim();
            String email = view.txtStudEmail.getText().trim();
            String dept = view.txtStudDept.getText().trim();
            String phone = view.txtStudPhone.getText().trim();

            if (rollStr.isEmpty() || name.isEmpty()) {
                view.showMessage("Select a student to update and provide valid fields.", "Validation Error",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (phone.isEmpty()) {
                view.showMessage("Phone number is required and must be a 10-digit number!", "Validation Error", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (!phone.matches("\\d+")) {
                view.showMessage("Phone number must contain only numbers (0-9). Letters or special characters are not allowed!", "Validation Error", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (phone.length() != 10) {
                view.showMessage("Phone number must be exactly 10 digits! (You entered " + phone.length() + " digits)", "Validation Error", JOptionPane.WARNING_MESSAGE);
                return;
            }

            long roll = Long.parseLong(rollStr);
            model.updateStudent(roll, name, email, dept, phone);
            view.showMessage("Student updated successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            clearStudentForm();
            loadStudents();
            loadEnrollments(); // names might have updated
        } catch (Exception ex) {
            view.showMessage("Update error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleDeleteStudent() {
        String rollStr = view.txtStudRoll.getText().trim();
        if (rollStr.isEmpty()) {
            view.showMessage("Select a student from the table to delete.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (view.confirmDialog("Deleting this student will also remove their enrollments. Proceed?",
                "Confirm Delete")) {
            try {
                long roll = Long.parseLong(rollStr);
                model.deleteStudent(roll);
                view.showMessage("Student removed successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
                clearStudentForm();
                loadStudents();
                loadEnrollments();
                loadDashboardData();
            } catch (Exception ex) {
                view.showMessage("Delete error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void handleAddCourse() {
        try {
            String code = view.txtCourseCode.getText().trim().toUpperCase();
            String name = view.txtCourseName.getText().trim();
            String creditsStr = view.txtCourseCredits.getText().trim();
            String instructor = view.txtCourseInstructor.getText().trim();

            if (code.isEmpty() || name.isEmpty() || creditsStr.isEmpty()) {
                view.showMessage("Course Code, Name, and Credits are required!", "Validation Error",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            int credits = Integer.parseInt(creditsStr);
            model.addCourse(code, name, credits, instructor);
            view.showMessage("Course added successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            clearCourseForm();
            loadCourses();
            loadDashboardData();
        } catch (NumberFormatException ex) {
            view.showMessage("Credits must be a valid integer!", "Input Error", JOptionPane.ERROR_MESSAGE);
        } catch (SQLException ex) {
            view.showMessage("Database error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleUpdateCourse() {
        try {
            String code = view.txtCourseCode.getText().trim().toUpperCase();
            String name = view.txtCourseName.getText().trim();
            String creditsStr = view.txtCourseCredits.getText().trim();
            String instructor = view.txtCourseInstructor.getText().trim();

            if (code.isEmpty() || name.isEmpty() || creditsStr.isEmpty()) {
                view.showMessage("Select a course to update and provide valid fields.", "Validation Error",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            int credits = Integer.parseInt(creditsStr);
            model.updateCourse(code, name, credits, instructor);
            view.showMessage("Course updated successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            clearCourseForm();
            loadCourses();
            loadEnrollments();
        } catch (Exception ex) {
            view.showMessage("Update error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleDeleteCourse() {
        String code = view.txtCourseCode.getText().trim().toUpperCase();
        if (code.isEmpty()) {
            view.showMessage("Select a course from the table to delete.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (view.confirmDialog("Deleting this course will also remove its enrollments. Proceed?", "Confirm Delete")) {
            try {
                model.deleteCourse(code);
                view.showMessage("Course removed successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
                clearCourseForm();
                loadCourses();
                loadEnrollments();
                loadDashboardData();
            } catch (Exception ex) {
                view.showMessage("Delete error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void handleSaveEnrollment() {
        try {
            if (view.comboEnrollStudent.getSelectedItem() == null || view.comboEnrollCourse.getSelectedItem() == null) {
                view.showMessage("Please make sure students and courses are available to select.", "Validation Error",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            String selectedStudent = (String) view.comboEnrollStudent.getSelectedItem();
            long roll = Long.parseLong(selectedStudent.split(" - ")[0]);

            String selectedCourse = (String) view.comboEnrollCourse.getSelectedItem();
            String courseCode = selectedCourse.split(" - ")[0];

            String attStr = view.txtAttendancePct.getText().trim();
            String examStr = view.txtExamMarks.getText().trim();

            if (attStr.isEmpty() || examStr.isEmpty()) {
                view.showMessage("Please enter attendance % and exam marks.", "Validation Error",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            double attPct = Double.parseDouble(attStr);
            double examMarks = Double.parseDouble(examStr);

            if (attPct < 0.0 || attPct > 100.0) {
                view.showMessage("Attendance percentage must be between 0 and 100.", "Range Error",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (examMarks < 0.0 || examMarks > 90.0) {
                view.showMessage("Exam marks must be between 0 and 90 (10 marks reserved for attendance).",
                        "Range Error", JOptionPane.WARNING_MESSAGE);
                return;
            }

            model.saveEnrollment(roll, courseCode, attPct, examMarks);
            view.showMessage("Enrollment and Grade saved successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            clearEnrollmentForm();
            loadEnrollments();
            loadDashboardData();
        } catch (NumberFormatException ex) {
            view.showMessage("Attendance and Exam marks must be numeric!", "Input Error", JOptionPane.ERROR_MESSAGE);
        } catch (SQLException ex) {
            view.showMessage("Database error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleDeleteEnrollment() {
        int selectedRow = view.enrollTable.getSelectedRow();
        if (selectedRow == -1) {
            view.showMessage("Select an enrollment from the table to delete.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (view.confirmDialog("Are you sure you want to delete this enrollment record?", "Confirm Delete")) {
            try {
                int modelRow = view.enrollTable.convertRowIndexToModel(selectedRow);
                int id = (int) view.enrollTableModel.getValueAt(modelRow, 0);
                model.deleteEnrollment(id);
                view.showMessage("Enrollment deleted successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
                clearEnrollmentForm();
                loadEnrollments();
                loadDashboardData();
            } catch (SQLException ex) {
                view.showMessage("Delete error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void clearStudentForm() {
        view.txtStudRoll.setText("");
        view.txtStudRoll.setEditable(true);
        view.txtStudName.setText("");
        view.txtStudEmail.setText("");
        view.txtStudDept.setText("");
        // view.txtStudDept.setEditable(true);
        view.txtStudPhone.setText("");
        view.lblStudPhoneHint.setText("Must be a 10-digit number");
        view.lblStudPhoneHint.setForeground(new Color(110, 110, 110));
        view.studTable.clearSelection();
    }

    private void clearCourseForm() {
        view.txtCourseCode.setText("");
        view.txtCourseCode.setEditable(true);
        view.txtCourseName.setText("");
        view.txtCourseCredits.setText("");
        view.txtCourseInstructor.setText("");
        view.courseTable.clearSelection();
    }

    private void clearEnrollmentForm() {
        view.txtAttendancePct.setText("");
        view.txtExamMarks.setText("");
        view.lblCalculatedPreview.setText("Preview: Attendance Marks: - | Total: - | Grade: -");
        view.enrollTable.clearSelection();
    }

    private void setupSearchFilter(JTextField searchField, TableRowSorter<DefaultTableModel> sorter) {
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                filter();
            }

            public void removeUpdate(DocumentEvent e) {
                filter();
            }

            public void changedUpdate(DocumentEvent e) {
                filter();
            }

            private void filter() {
                String text = searchField.getText().trim();
                if (text.isEmpty()) {
                    sorter.setRowFilter(null);
                } else {
                    sorter.setRowFilter(RowFilter.regexFilter("(?i)" + text));
                }
            }
        });
    }
}
