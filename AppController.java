import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * CONTROLLER: Coordinates interactions between MainView and DatabaseModel.
 * Handles validation, button actions, live search, live grade calculations,
 * and refreshes UI data models.
 */
public class AppController {
    private final DatabaseModel model;
    private final MainView view;
    private int currentEditingEnrollmentId = -1; // -1 indicates Add mode, >=0 indicates Update mode
    private long selectedStudentRoll = -1;       // -1 indicates Add mode, >=0 indicates existing student selected
    private String savedStudentEmail = "";       // Stored DB email for selected existing student
    private boolean savedStudentEmailVerified = false;
    private boolean emailEditUnlocked = true;    // True for new students; unlocked via Edit Email for existing students
    private String verifiedEmailInForm = null;   // Tracks email verified via OTP in current form session

    public AppController(DatabaseModel model, MainView view) {
        this.model = model;
        this.view = view;

        initListeners();
        loadAllData();
        updateEmailFeedback();
    }

    private void initListeners() {
        // --- STUDENT LISTENERS ---
        view.btnStudAdd.addActionListener(e -> handleAddStudent());
        view.btnStudUpdate.addActionListener(e -> handleUpdateStudent());
        view.btnStudDelete.addActionListener(e -> handleDeleteStudent());
        view.btnStudClear.addActionListener(e -> clearStudentForm());
        view.btnEditEmail.addActionListener(e -> handleToggleEditEmail());
        view.btnVerifyEmail.addActionListener(e -> handleVerifyEmailButton());

        // FEATURE 3: View Individual Student Report listeners
        view.btnStudViewReport.addActionListener(e -> handleOpenStudentReport());
        view.btnStudViewReportTable.addActionListener(e -> handleOpenStudentReport());

        // When table row is clicked, populate fields
        view.studTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && view.studTable.getSelectedRow() != -1) {
                int modelRow = view.studTable.convertRowIndexToModel(view.studTable.getSelectedRow());
                String rollText = cellString(view.studTableModel.getValueAt(modelRow, 0)).trim();
                long parsedRoll = -1;
                try {
                    parsedRoll = Long.parseLong(rollText);
                } catch (NumberFormatException ignored) {
                }

                String rowEmail = cellString(view.studTableModel.getValueAt(modelRow, 2)).trim();
                Object statusObj = view.studTableModel.getColumnCount() > 5
                        ? view.studTableModel.getValueAt(modelRow, 5)
                        : null;
                boolean rowVerified = "Verified".equalsIgnoreCase(cellString(statusObj)) && !rowEmail.isEmpty();

                selectedStudentRoll = parsedRoll;
                savedStudentEmail = rowEmail;
                savedStudentEmailVerified = rowVerified;
                emailEditUnlocked = false;
                verifiedEmailInForm = rowVerified ? rowEmail.toLowerCase() : null;

                view.txtStudRoll.setText(rollText);
                view.txtStudRoll.setEditable(false); // primary key shouldn't be edited during update
                view.txtStudName.setText(cellString(view.studTableModel.getValueAt(modelRow, 1)));
                view.txtStudEmail.setEditable(false); // existing student email can only be edited via Edit Email
                view.txtStudEmail.setText(rowEmail);
                view.txtStudDept.setText(cellString(view.studTableModel.getValueAt(modelRow, 3)));
                // view.txtStudDept.setEditable(false);
                view.txtStudPhone.setText(cellString(view.studTableModel.getValueAt(modelRow, 4)));
                updateEmailFeedback();
                updatePhoneFeedback();
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

        // FEATURE 2: Update enrolled courses summary whenever student dropdown selection changes
        view.comboEnrollStudent.addActionListener(e -> updateEnrolledCoursesSummaryForSelectedStudent());

        view.enrollTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && view.enrollTable.getSelectedRow() != -1) {
                int modelRow = view.enrollTable.convertRowIndexToModel(view.enrollTable.getSelectedRow());
                currentEditingEnrollmentId = (int) view.enrollTableModel.getValueAt(modelRow, 0);
                view.btnEnrollSave.setText("Update Selected Grade");

                String rollStr = view.enrollTableModel.getValueAt(modelRow, 1).toString();
                String code = view.enrollTableModel.getValueAt(modelRow, 3).toString();

                selectStudentInCombo(rollStr);
                selectCourseInCombo(code);

                String attStr = view.enrollTableModel.getValueAt(modelRow, 5).toString().replace("%", "");
                String examStr = view.enrollTableModel.getValueAt(modelRow, 7).toString().replace(" / 90", "");

                view.txtAttendancePct.setText(attStr);
                view.txtExamMarks.setText(examStr);
                updateLiveGradePreview();
                updateEnrolledCoursesSummaryForSelectedStudent();
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

        // Live email format & verification feedback as user types
        DocumentListener emailListener = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { updateEmailFeedback(); }
            public void removeUpdate(DocumentEvent e) { updateEmailFeedback(); }
            public void changedUpdate(DocumentEvent e) { updateEmailFeedback(); }
        };
        view.txtStudEmail.getDocument().addDocumentListener(emailListener);

        setupSearchFilter(view.txtEnrollSearch, view.enrollSorter);

        // --- DASHBOARD LISTENER ---
        view.btnRefreshDashboard.addActionListener(e -> loadDashboardData());
        view.btnDashboardViewReport.addActionListener(e -> handleOpenReportFromDashboard());
        view.btnBulkAlertEmail.addActionListener(e -> handleBulkLowAttendanceAlerts());
    }

    private void updateEmailFeedback() {
        String email = view.txtStudEmail.getText().trim();
        boolean isExisting = selectedStudentRoll != -1;
        boolean isVerifiedNow = !email.isEmpty()
                && verifiedEmailInForm != null
                && email.equalsIgnoreCase(verifiedEmailInForm);

        // Edit Email button is only for already-added students
        if (isExisting) {
            view.btnEditEmail.setVisible(true);
            view.btnEditEmail.setText(emailEditUnlocked ? "Cancel Edit" : "Edit Email");
            view.btnEditEmail.setToolTipText(emailEditUnlocked
                    ? "Revert to saved email and lock field"
                    : "Unlock email field to edit student's email");
        } else {
            view.btnEditEmail.setVisible(false);
        }

        // Verify OTP option is hidden whenever the current email is already verified
        view.btnVerifyEmail.setVisible(!isVerifiedNow);

        if (email.isEmpty()) {
            view.lblStudEmailHint.setText(isExisting && !emailEditUnlocked
                    ? "No email saved - Click 'Edit Email' to add one"
                    : "Required - Must be a valid email address");
            view.lblStudEmailHint.setForeground(new Color(110, 110, 110));
        } else if (isVerifiedNow) {
            if (isExisting && !emailEditUnlocked) {
                view.lblStudEmailHint.setText("Email verified (OTP confirmed) - Click 'Edit Email' to change");
            } else if (isExisting && !email.equalsIgnoreCase(savedStudentEmail)) {
                view.lblStudEmailHint.setText("New email verified (OTP confirmed) - Click 'Update Selected' to save");
            } else {
                view.lblStudEmailHint.setText("Email verified (OTP confirmed)");
            }
            view.lblStudEmailHint.setForeground(new Color(46, 125, 50));
        } else {
            String typoSuggestion = EmailService.suggestDomainTypo(email);
            if (typoSuggestion != null) {
                view.lblStudEmailHint.setText("Possible typo: did you mean @" + typoSuggestion + "?");
                view.lblStudEmailHint.setForeground(new Color(230, 81, 0));
            } else if (!EmailService.isValidEmail(email)) {
                view.lblStudEmailHint.setText("Invalid email format (e.g. name@gmail.com)");
                view.lblStudEmailHint.setForeground(new Color(211, 47, 47));
            } else if (isExisting && !emailEditUnlocked) {
                view.lblStudEmailHint.setText("Unverified - Click 'Verify OTP' or 'Edit Email' to change");
                view.lblStudEmailHint.setForeground(new Color(180, 100, 0));
            } else {
                view.lblStudEmailHint.setText("Valid format (Click 'Verify OTP' or save to verify)");
                view.lblStudEmailHint.setForeground(new Color(3, 105, 161));
            }
        }

        view.emailActionPanel.setVisible(view.btnEditEmail.isVisible() || view.btnVerifyEmail.isVisible());
        view.emailContainer.revalidate();
        view.emailContainer.repaint();
    }

    private void handleToggleEditEmail() {
        if (selectedStudentRoll == -1) {
            return;
        }
        if (!emailEditUnlocked) {
            emailEditUnlocked = true;
            view.txtStudEmail.setEditable(true);
            view.txtStudEmail.requestFocusInWindow();
            view.txtStudEmail.selectAll();
            updateEmailFeedback();
        } else {
            emailEditUnlocked = false;
            view.txtStudEmail.setEditable(false);
            verifiedEmailInForm = (savedStudentEmailVerified && !savedStudentEmail.isEmpty())
                    ? savedStudentEmail.toLowerCase()
                    : null;
            view.txtStudEmail.setText(savedStudentEmail);
            updateEmailFeedback();
        }
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
            view.lblStudPhoneHint.setText("10 digits completed (Limit reached)");
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
            List<Object[]> list = model.getUniqueStudents();
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
            updateEnrolledCoursesSummaryForSelectedStudent();
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
            Set<Long> uniqueRolls = new LinkedHashSet<>();
            for (Object[] row : shortage) {
                view.shortageTableModel.addRow(row);
                if (row.length > 0 && row[0] instanceof Number) {
                    uniqueRolls.add(((Number) row[0]).longValue());
                }
            }
            if (shortage.isEmpty()) {
                view.lblShortageSummary.setText("All enrolled students meet the 75% attendance threshold.");
            } else {
                view.lblShortageSummary.setText(shortage.size() + " shortage record(s) affecting "
                        + uniqueRolls.size() + " unique student(s).");
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
        updateEnrolledCoursesSummaryForSelectedStudent();
    }

    private void updateEnrolledCoursesSummaryForSelectedStudent() {
        if (view.comboEnrollStudent.getSelectedItem() == null) {
            view.lblStudentEnrolledCourses.setText("Enrolled Courses: (None selected)");
            return;
        }

        try {
            String selected = (String) view.comboEnrollStudent.getSelectedItem();
            long roll = Long.parseLong(selected.split(" - ")[0]);
            List<Object[]> courses = model.getEnrolledCoursesForStudent(roll);

            if (courses.isEmpty()) {
                view.lblStudentEnrolledCourses.setText("Enrolled Courses: (None enrolled yet)");
            } else {
                StringBuilder sb = new StringBuilder("Enrolled: ");
                for (int i = 0; i < courses.size(); i++) {
                    Object[] c = courses.get(i);
                    sb.append(c[1]).append(" (").append(String.format("%.1f%%", (double) c[4])).append(")");
                    if (i < courses.size() - 1) sb.append(", ");
                }
                sb.append(" [").append(courses.size()).append(" total]");
                view.lblStudentEnrolledCourses.setText(sb.toString());
            }
        } catch (Exception ex) {
            view.lblStudentEnrolledCourses.setText("Enrolled Courses: (Error loading)");
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
        validateAndSaveStudent(false);
    }

    private void handleUpdateStudent() {
        validateAndSaveStudent(true);
    }

    private void validateAndSaveStudent(boolean isUpdate) {
        try {
            String rollStr = view.txtStudRoll.getText().trim();
            String name = view.txtStudName.getText().trim();
            String email = view.txtStudEmail.getText().trim();
            String dept = view.txtStudDept.getText().trim();
            String phone = view.txtStudPhone.getText().trim();

            if (rollStr.isEmpty() || name.isEmpty()) {
                view.showMessage(isUpdate
                        ? "Select a student to update and provide valid fields."
                        : "Roll Number and Name are required!",
                        "Validation Error", JOptionPane.WARNING_MESSAGE);
                return;
            }

            long roll = Long.parseLong(rollStr);

            if (!isUpdate && model.getStudentByRollNo(roll) != null) {
                view.showMessage("A student with Roll Number " + roll + " already exists!",
                        "Duplicate Roll Number", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (isUpdate && model.getStudentByRollNo(roll) == null) {
                view.showMessage("Select an existing student from the table before clicking Update.",
                        "Student Not Found", JOptionPane.WARNING_MESSAGE);
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

            // LEVEL 1: Syntax, Typo Suggestion & Duplicate Email Check
            String checkedEmail = validateEmailSyntaxTypoAndDuplicate(email, isUpdate ? roll : -1);
            if (checkedEmail == null) {
                return;
            }

            // LEVEL 2: DNS MX Mail-Server Domain Check (Background SwingWorker)
            setStudentActionsEnabled(false);
            view.lblStudEmailHint.setText("Checking domain @" + EmailService.extractDomain(checkedEmail) + "...");
            view.lblStudEmailHint.setForeground(new Color(3, 105, 161));

            SwingWorker<EmailService.DomainCheckResult, Void> dnsWorker = new SwingWorker<>() {
                @Override
                protected EmailService.DomainCheckResult doInBackground() {
                    return EmailService.verifyEmailDomain(checkedEmail);
                }

                @Override
                protected void done() {
                    setStudentActionsEnabled(true);
                    updateEmailFeedback();
                    try {
                        EmailService.DomainCheckResult domainResult = get();
                        if (domainResult.getStatus() == EmailService.DomainVerificationStatus.INVALID_DOMAIN
                                || domainResult.getStatus() == EmailService.DomainVerificationStatus.INVALID_SYNTAX) {
                            view.showMessage("Email verification failed:\n" + domainResult.getMessage()
                                    + "\n\nPlease enter a real, active email address.",
                                    "Invalid Email Domain", JOptionPane.ERROR_MESSAGE);
                            return;
                        }

                        if (domainResult.getStatus() == EmailService.DomainVerificationStatus.NETWORK_UNAVAILABLE) {
                            boolean saveOffline = view.confirmDialog(
                                    domainResult.getMessage() + "\n\n"
                                    + "Format is valid, but online domain/OTP verification could not run.\n"
                                    + "Do you want to save this student with status 'Unverified'?",
                                    "Offline Mode - Save Unverified?");
                            if (saveOffline) {
                                performSaveStudent(isUpdate, roll, name, checkedEmail, dept, phone, false);
                            }
                            return;
                        }

                        // LEVEL 3: OTP Inbox Ownership Verification
                        if (verifiedEmailInForm != null && checkedEmail.equalsIgnoreCase(verifiedEmailInForm)) {
                            performSaveStudent(isUpdate, roll, name, checkedEmail, dept, phone, true);
                            return;
                        }

                        boolean smtpReady = EmailService.isConfigured();
                        Object[] options = {
                                smtpReady ? "Send OTP & Verify Inbox" : "Configure SMTP & Send OTP",
                                "Save as Unverified",
                                "Cancel"
                        };
                        int choice = JOptionPane.showOptionDialog(
                                view,
                                "Email domain '@" + domainResult.getDomain() + "' mail server verified!\n\n"
                                + "Email: " + checkedEmail + "\n"
                                + "Would you like to send a 6-digit OTP to confirm this inbox is active right now?",
                                "Verify Student Email (OTP)",
                                JOptionPane.YES_NO_CANCEL_OPTION,
                                JOptionPane.QUESTION_MESSAGE,
                                null,
                                options,
                                options[0]);

                        if (choice == 0) {
                            startOtpVerificationFlow(name, checkedEmail, () ->
                                    performSaveStudent(isUpdate, roll, name, checkedEmail, dept, phone, true));
                        } else if (choice == 1) {
                            performSaveStudent(isUpdate, roll, name, checkedEmail, dept, phone, false);
                        }
                    } catch (Exception ex) {
                        view.showMessage("Domain verification error: " + ex.getMessage(),
                                "Verification Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            };
            dnsWorker.execute();
        } catch (NumberFormatException ex) {
            view.showMessage("Roll Number must be a valid number!", "Input Error", JOptionPane.ERROR_MESSAGE);
        } catch (SQLException ex) {
            view.showMessage("Database error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleVerifyEmailButton() {
        String email = view.txtStudEmail.getText().trim();
        String name = view.txtStudName.getText().trim();
        long excludeRoll = selectedStudentRoll;

        try {
            String checkedEmail = validateEmailSyntaxTypoAndDuplicate(email, excludeRoll);
            if (checkedEmail == null) {
                return;
            }
            if (verifiedEmailInForm != null && checkedEmail.equalsIgnoreCase(verifiedEmailInForm)) {
                view.showMessage("Email '" + checkedEmail + "' is already verified!",
                        "Already Verified", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            final long targetRoll = excludeRoll;
            setStudentActionsEnabled(false);
            view.lblStudEmailHint.setText("Checking domain @" + EmailService.extractDomain(checkedEmail) + "...");
            view.lblStudEmailHint.setForeground(new Color(3, 105, 161));

            SwingWorker<EmailService.DomainCheckResult, Void> dnsWorker = new SwingWorker<>() {
                @Override
                protected EmailService.DomainCheckResult doInBackground() {
                    return EmailService.verifyEmailDomain(checkedEmail);
                }

                @Override
                protected void done() {
                    setStudentActionsEnabled(true);
                    updateEmailFeedback();
                    try {
                        EmailService.DomainCheckResult result = get();
                        if (result.getStatus() == EmailService.DomainVerificationStatus.INVALID_DOMAIN
                                || result.getStatus() == EmailService.DomainVerificationStatus.INVALID_SYNTAX) {
                            view.showMessage("Email domain verification failed:\n" + result.getMessage(),
                                    "Invalid Email Domain", JOptionPane.ERROR_MESSAGE);
                            return;
                        }
                        if (result.getStatus() == EmailService.DomainVerificationStatus.NETWORK_UNAVAILABLE) {
                            view.showMessage("Cannot run online domain / OTP verification:\n" + result.getMessage(),
                                    "Network Unavailable", JOptionPane.WARNING_MESSAGE);
                            return;
                        }

                        startOtpVerificationFlow(name, checkedEmail, () -> {
                            try {
                                if (targetRoll != -1) {
                                    Object[] existing = model.getStudentByRollNo(targetRoll);
                                    if (existing != null && existing[2] != null
                                            && checkedEmail.equalsIgnoreCase(existing[2].toString().trim())) {
                                        model.markEmailVerified(targetRoll, true);
                                        if (selectedStudentRoll == targetRoll) {
                                            savedStudentEmail = checkedEmail;
                                            savedStudentEmailVerified = true;
                                            emailEditUnlocked = false;
                                            view.txtStudEmail.setEditable(false);
                                        }
                                        loadStudents();
                                        updateEmailFeedback();
                                        view.showMessage("Email '" + checkedEmail + "' verified and saved to student record!",
                                                "Email Verified", JOptionPane.INFORMATION_MESSAGE);
                                        return;
                                    }
                                }
                                view.showMessage("Email '" + checkedEmail + "' verified via OTP!\n"
                                        + "Click 'Add Student' or 'Update Selected' to save the student record.",
                                        "Email Verified", JOptionPane.INFORMATION_MESSAGE);
                            } catch (SQLException ex) {
                                view.showMessage("Verified via OTP, but failed to update status: " + ex.getMessage(),
                                        "Database Error", JOptionPane.ERROR_MESSAGE);
                            }
                        });
                    } catch (Exception ex) {
                        view.showMessage("Verification error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            };
            dnsWorker.execute();
        } catch (SQLException ex) {
            view.showMessage("Database error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String validateEmailSyntaxTypoAndDuplicate(String rawEmail, long excludeRollNo) throws SQLException {
        String email = rawEmail == null ? "" : rawEmail.trim();
        if (email.isEmpty()) {
            view.showMessage("Email Address is required and must be valid!",
                    "Validation Error", JOptionPane.WARNING_MESSAGE);
            view.txtStudEmail.requestFocusInWindow();
            return null;
        }

        String typoSuggestion = EmailService.suggestDomainTypo(email);
        if (typoSuggestion != null) {
            int at = email.lastIndexOf('@');
            String corrected = email.substring(0, at + 1) + typoSuggestion;
            int fixChoice = JOptionPane.showConfirmDialog(
                    view,
                    "Possible typo detected in email domain!\n\n"
                    + "You entered: " + email + "\n"
                    + "Did you mean: " + corrected + "?\n\n"
                    + "Click Yes to fix automatically, or No to edit manually.",
                    "Email Domain Typo Check",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (fixChoice == JOptionPane.YES_OPTION) {
                email = corrected;
                view.txtStudEmail.setText(corrected);
            } else {
                view.txtStudEmail.requestFocusInWindow();
                return null;
            }
        }

        if (!EmailService.isValidEmail(email)) {
            view.showMessage("Please enter a valid Email Address (e.g. student@gmail.com).",
                    "Invalid Email Format", JOptionPane.WARNING_MESSAGE);
            view.txtStudEmail.requestFocusInWindow();
            return null;
        }

        if (model.isEmailInUse(email, excludeRollNo)) {
            view.showMessage("Email address '" + email + "' is already registered to another student!",
                    "Duplicate Email", JOptionPane.WARNING_MESSAGE);
            view.txtStudEmail.requestFocusInWindow();
            return null;
        }

        return email;
    }

    private void startOtpVerificationFlow(String studentName, String email, Runnable onVerified) {
        if (!EmailService.isConfigured()) {
            if (!showSmtpConfigDialog(view)) {
                return;
            }
        }

        final String otp = EmailService.generateOtp();
        setStudentActionsEnabled(false);
        view.lblStudEmailHint.setText("Sending 6-digit verification OTP to " + email + "...");
        view.lblStudEmailHint.setForeground(new Color(3, 105, 161));

        SwingWorker<Void, Void> otpWorker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                EmailService.sendVerificationOtpEmail(studentName, email, otp);
                return null;
            }

            @Override
            protected void done() {
                setStudentActionsEnabled(true);
                updateEmailFeedback();
                try {
                    get();
                    promptOtpEntry(studentName, email, otp, onVerified);
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    view.showMessage("Failed to send verification OTP to " + email + ":\n" + cause.getMessage(),
                            "OTP Delivery Failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        otpWorker.execute();
    }

    private void promptOtpEntry(String studentName, String email, String expectedOtp, Runnable onVerified) {
        JTextField txtOtp = new JTextField(10);
        txtOtp.setFont(new Font("Segoe UI", Font.BOLD, 16));
        txtOtp.setHorizontalAlignment(SwingConstants.CENTER);

        while (true) {
            JPanel panel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(4, 4, 4, 4);
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.gridx = 0;
            gbc.gridy = 0;

            JLabel lblMsg = new JLabel("<html>A 6-digit verification code (OTP) has been sent to:<br><b>"
                    + escapeHtmlText(email) + "</b><br><br>Enter the 6-digit OTP below:</html>");
            panel.add(lblMsg, gbc);

            gbc.gridy = 1;
            panel.add(txtOtp, gbc);

            Object[] options = {"Verify Code", "Resend OTP", "Cancel"};
            int choice = JOptionPane.showOptionDialog(
                    view,
                    panel,
                    "Verify Student Email (OTP)",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    options,
                    options[0]);

            if (choice == 1) {
                startOtpVerificationFlow(studentName, email, onVerified);
                return;
            }
            if (choice != 0) {
                return;
            }

            String entered = txtOtp.getText().trim();
            if (entered.equals(expectedOtp)) {
                verifiedEmailInForm = email.toLowerCase();
                updateEmailFeedback();
                if (onVerified != null) {
                    onVerified.run();
                }
                return;
            } else {
                view.showMessage("Incorrect OTP code entered. Please check your inbox and try again.",
                        "OTP Mismatch", JOptionPane.WARNING_MESSAGE);
                txtOtp.requestFocusInWindow();
            }
        }
    }

    private void performSaveStudent(boolean isUpdate, long roll, String name, String email,
                                    String dept, String phone, boolean emailVerified) {
        try {
            if (isUpdate) {
                model.updateStudent(roll, name, email, dept, phone, emailVerified);
                view.showMessage("Student updated successfully! (Email Status: "
                        + (emailVerified ? "Verified" : "Unverified") + ")",
                        "Success", JOptionPane.INFORMATION_MESSAGE);
            } else {
                model.addStudent(roll, name, email, dept, phone, emailVerified);
                view.showMessage("Student added successfully! (Email Status: "
                        + (emailVerified ? "Verified" : "Unverified") + ")",
                        "Success", JOptionPane.INFORMATION_MESSAGE);
            }
            clearStudentForm();
            loadStudents();
            if (isUpdate) {
                loadEnrollments();
            }
            loadDashboardData();
        } catch (SQLException ex) {
            view.showMessage("Database error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void setStudentActionsEnabled(boolean enabled) {
        view.btnStudAdd.setEnabled(enabled);
        view.btnStudUpdate.setEnabled(enabled);
        view.btnVerifyEmail.setEnabled(enabled);
        view.btnEditEmail.setEnabled(enabled);
    }

    private static String cellString(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String escapeHtmlText(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
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

            // FEATURE 2: Prevent duplicate enrollments with meaningful validation message
            if (currentEditingEnrollmentId == -1) {
                // Adding a new enrollment
                if (model.isStudentEnrolledInCourse(roll, courseCode)) {
                    view.showMessage("This student is already enrolled in the selected course.",
                            "Duplicate Enrollment", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                model.addEnrollment(roll, courseCode, attPct, examMarks);
            } else {
                // Updating an existing enrollment record
                if (model.isDuplicateEnrollment(roll, courseCode, currentEditingEnrollmentId)) {
                    view.showMessage("This student is already enrolled in the selected course.",
                            "Duplicate Enrollment", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                model.updateEnrollmentById(currentEditingEnrollmentId, roll, courseCode, attPct, examMarks);
            }

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
        selectedStudentRoll = -1;
        savedStudentEmail = "";
        savedStudentEmailVerified = false;
        emailEditUnlocked = true;
        verifiedEmailInForm = null;
        view.txtStudRoll.setText("");
        view.txtStudRoll.setEditable(true);
        view.txtStudName.setText("");
        view.txtStudEmail.setEditable(true);
        view.txtStudEmail.setText("");
        view.txtStudDept.setText("");
        // view.txtStudDept.setEditable(true);
        view.txtStudPhone.setText("");
        updateEmailFeedback();
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
        currentEditingEnrollmentId = -1;
        view.btnEnrollSave.setText("Save / Update Grade");
        view.txtAttendancePct.setText("");
        view.txtExamMarks.setText("");
        view.lblCalculatedPreview.setText("Preview: Attendance Marks: - | Total: - | Grade: -");
        view.enrollTable.clearSelection();
        updateEnrolledCoursesSummaryForSelectedStudent();
    }

    // ==========================================
    // FEATURE 3 & 4: STUDENT REPORT & EMAIL
    // ==========================================

    private void handleOpenStudentReport() {
        int selectedRow = view.studTable.getSelectedRow();
        long roll = -1;

        if (selectedRow != -1) {
            int modelRow = view.studTable.convertRowIndexToModel(selectedRow);
            roll = Long.parseLong(view.studTableModel.getValueAt(modelRow, 0).toString());
        } else {
            String rollText = view.txtStudRoll.getText().trim();
            if (!rollText.isEmpty()) {
                try {
                    roll = Long.parseLong(rollText);
                } catch (NumberFormatException ignored) {}
            }
        }

        if (roll == -1) {
            view.showMessage("Please select a student from the table first.", "Select Student",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        openStudentReportByRoll(roll);
    }

    private void handleOpenReportFromDashboard() {
        int selectedRow = view.shortageTable.getSelectedRow();
        if (selectedRow == -1) {
            view.showMessage("Please select a student row in the Attendance Shortage table first.",
                    "Select Student", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int modelRow = view.shortageTable.convertRowIndexToModel(selectedRow);
        long roll = Long.parseLong(view.shortageTableModel.getValueAt(modelRow, 0).toString());
        openStudentReportByRoll(roll);
    }

    private void openStudentReportByRoll(long roll) {
        try {
            StudentReportData data = model.getStudentReportData(roll);
            if (data == null) {
                view.showMessage("Student record not found in database.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            StudentReportView reportView = new StudentReportView(view, data);

            // Wire actions inside the report dialog
            reportView.btnExportPdf.addActionListener(e -> handleExportPdf(reportView, data));
            reportView.btnSendEmail.addActionListener(e -> handleSendEmail(reportView, data));

            reportView.setVisible(true);

        } catch (SQLException ex) {
            view.showMessage("Database error while loading student report: " + ex.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ==========================================
    // BULK EMAIL LOW-ATTENDANCE ALERTS
    // ==========================================

    private void handleBulkLowAttendanceAlerts() {
        if (!EmailService.isConfigured()) {
            if (!showSmtpConfigDialog(view)) {
                return;
            }
        }

        List<DatabaseModel.LowAttendanceRecipient> recipients;
        try {
            recipients = model.getLowAttendanceRecipients();
        } catch (SQLException ex) {
            view.showMessage("Failed to load low-attendance students: " + ex.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (recipients.isEmpty()) {
            view.showMessage("Great news! All enrolled students currently have >= 75% attendance.\nNo low-attendance alerts needed.",
                    "No Shortage Alerts", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        boolean[] deliverable = new boolean[recipients.size()];
        for (int i = 0; i < recipients.size(); i++) {
            String em = recipients.get(i).getEmail();
            deliverable[i] = EmailService.isValidEmail(em) && !EmailService.isReservedDomainEmail(em);
        }

        DefaultTableModel previewModel = new DefaultTableModel(new String[]{
                "Send", "Roll No", "Student Name", "Email", "Status", "Shortage Courses (<75%)", "Min Att."
        }, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 0 ? Boolean.class : Object.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0 && deliverable[row];
            }
        };

        for (int i = 0; i < recipients.size(); i++) {
            DatabaseModel.LowAttendanceRecipient r = recipients.get(i);
            String statusLabel;
            if (!deliverable[i]) {
                statusLabel = (r.getEmail().isEmpty() ? "Blocked (Missing)" : "Blocked (Invalid/Test)");
            } else {
                statusLabel = r.isEmailVerified() ? "Verified" : "Unverified";
            }
            boolean checkedByDefault = deliverable[i] && r.isEmailVerified();
            previewModel.addRow(new Object[]{
                    checkedByDefault,
                    r.getRollNo(),
                    r.getName(),
                    r.getEmail().isEmpty() ? "(None)" : r.getEmail(),
                    statusLabel,
                    r.getCoursesSummary(),
                    String.format("%.1f%%", r.getLowestAttendancePct())
            });
        }

        JTable previewTable = new JTable(previewModel);
        previewTable.setRowHeight(24);
        previewTable.getColumnModel().getColumn(0).setPreferredWidth(45);
        previewTable.getColumnModel().getColumn(0).setMaxWidth(55);
        previewTable.getColumnModel().getColumn(1).setPreferredWidth(70);
        previewTable.getColumnModel().getColumn(2).setPreferredWidth(125);
        previewTable.getColumnModel().getColumn(3).setPreferredWidth(175);
        previewTable.getColumnModel().getColumn(4).setPreferredWidth(115);
        previewTable.getColumnModel().getColumn(4).setCellRenderer(MainView.createEmailStatusCellRenderer());
        previewTable.getColumnModel().getColumn(5).setPreferredWidth(190);
        previewTable.getColumnModel().getColumn(6).setPreferredWidth(65);
        previewTable.getColumnModel().getColumn(6).setCellRenderer(new AttendanceCellRenderer());

        JLabel lblSummary = new JLabel();
        lblSummary.setFont(new Font("Segoe UI", Font.BOLD, 12));

        Runnable refreshCount = () -> {
            int selected = 0;
            int deliverableCount = 0;
            for (int i = 0; i < previewModel.getRowCount(); i++) {
                if (deliverable[i]) {
                    deliverableCount++;
                    if (Boolean.TRUE.equals(previewModel.getValueAt(i, 0))) {
                        selected++;
                    }
                }
            }
            lblSummary.setText("Selected: " + selected + " of " + previewModel.getRowCount()
                    + " low-attendance student(s) (" + deliverableCount + " deliverable)");
        };
        refreshCount.run();
        previewModel.addTableModelListener(e -> refreshCount.run());

        JButton btnVerifiedOnly = new JButton("Verified Only");
        btnVerifiedOnly.addActionListener(e -> {
            for (int i = 0; i < previewModel.getRowCount(); i++) {
                previewModel.setValueAt(deliverable[i] && recipients.get(i).isEmailVerified(), i, 0);
            }
        });

        JButton btnSelectAllDeliverable = new JButton("Include Unverified");
        btnSelectAllDeliverable.addActionListener(e -> {
            for (int i = 0; i < previewModel.getRowCount(); i++) {
                previewModel.setValueAt(deliverable[i], i, 0);
            }
        });

        JButton btnClearSelection = new JButton("Uncheck All");
        btnClearSelection.addActionListener(e -> {
            for (int i = 0; i < previewModel.getRowCount(); i++) {
                previewModel.setValueAt(false, i, 0);
            }
        });

        JCheckBox chkAttachPdf = new JCheckBox("Attach full Academic Report PDF (Academic_Report_<roll>.pdf)", true);
        chkAttachPdf.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        while (true) {
            JPanel dialogPanel = new JPanel(new BorderLayout(8, 8));
            dialogPanel.setPreferredSize(new Dimension(820, 360));

            JLabel lblHeader = new JLabel("<html><b>Send Low-Attendance Alert Emails (&lt; 75% Attendance)</b><br>"
                    + "<small>One consolidated alert email will be sent per selected student in the background. "
                    + "Verified emails are selected by default; placeholder/invalid addresses are blocked.</small></html>");
            dialogPanel.add(lblHeader, BorderLayout.NORTH);

            dialogPanel.add(new JScrollPane(previewTable), BorderLayout.CENTER);

            JPanel footerControls = new JPanel(new BorderLayout(8, 6));
            JPanel quickBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            quickBtns.add(btnVerifiedOnly);
            quickBtns.add(btnSelectAllDeliverable);
            quickBtns.add(btnClearSelection);

            JPanel topFooter = new JPanel(new BorderLayout(8, 0));
            topFooter.add(quickBtns, BorderLayout.WEST);
            topFooter.add(lblSummary, BorderLayout.EAST);

            footerControls.add(topFooter, BorderLayout.NORTH);
            footerControls.add(chkAttachPdf, BorderLayout.SOUTH);
            dialogPanel.add(footerControls, BorderLayout.SOUTH);

            Object[] options = {"Send Selected Alerts", "Cancel"};
            int choice = JOptionPane.showOptionDialog(
                    view,
                    dialogPanel,
                    "Bulk Low-Attendance Email Alerts",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    options,
                    options[0]);

            if (choice != 0) {
                return;
            }

            List<DatabaseModel.LowAttendanceRecipient> selectedRecipients = new ArrayList<>();
            for (int i = 0; i < previewModel.getRowCount(); i++) {
                if (deliverable[i] && Boolean.TRUE.equals(previewModel.getValueAt(i, 0))) {
                    selectedRecipients.add(recipients.get(i));
                }
            }

            if (selectedRecipients.isEmpty()) {
                view.showMessage("Please select at least one deliverable student email.\n"
                        + "(Tip: Click 'Include Unverified' if the student's email is valid but not yet OTP-verified.)",
                        "No Recipients Selected", JOptionPane.WARNING_MESSAGE);
                continue;
            }

            startBulkLowAttendanceWorker(selectedRecipients, chkAttachPdf.isSelected());
            return;
        }
    }

    private void startBulkLowAttendanceWorker(List<DatabaseModel.LowAttendanceRecipient> selectedRecipients,
                                              boolean attachPdf) {
        final int total = selectedRecipients.size();
        view.btnBulkAlertEmail.setEnabled(false);
        view.btnBulkAlertEmail.setText("Sending Alerts (0/" + total + ")...");

        JDialog progressDialog = new JDialog(view, "Sending Bulk Attendance Alerts", false);
        progressDialog.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        progressDialog.setSize(470, 175);
        progressDialog.setLocationRelativeTo(view);

        JLabel lblCurrent = new JLabel("Preparing background email dispatch...");
        lblCurrent.setFont(new Font("Segoe UI", Font.BOLD, 12));

        JProgressBar progressBar = new JProgressBar(0, total);
        progressBar.setStringPainted(true);
        progressBar.setValue(0);

        JLabel lblCounts = new JLabel("Sent: 0 | Failed: 0 | Total: " + total);
        lblCounts.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        JButton btnHide = new JButton("Run in Background");
        JButton btnCancel = new JButton("Cancel Remaining");

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnBar.add(btnHide);
        btnBar.add(btnCancel);

        JPanel content = new JPanel(new BorderLayout(8, 10));
        content.setBorder(BorderFactory.createEmptyBorder(14, 16, 12, 16));
        content.add(lblCurrent, BorderLayout.NORTH);
        content.add(progressBar, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout(8, 8));
        south.add(lblCounts, BorderLayout.WEST);
        south.add(btnBar, BorderLayout.EAST);
        content.add(south, BorderLayout.SOUTH);

        progressDialog.setContentPane(content);

        final AtomicBoolean stopRequested = new AtomicBoolean(false);

        SwingWorker<List<String>, int[]> worker = new SwingWorker<>() {
            private volatile String activeStudentLabel = "";
            private int sentCount = 0;
            private int failedCount = 0;

            @Override
            protected List<String> doInBackground() {
                List<String> logs = new ArrayList<>();
                for (int i = 0; i < selectedRecipients.size(); i++) {
                    DatabaseModel.LowAttendanceRecipient rec = selectedRecipients.get(i);
                    if (stopRequested.get()) {
                        logs.add(String.format("CANCELLED | Roll %-8d | %-20s | %s (Stopped by user)",
                                rec.getRollNo(), rec.getName(), rec.getEmail()));
                        continue;
                    }
                    activeStudentLabel = rec.getName() + " (" + rec.getEmail() + ")";
                    publish(new int[]{i, sentCount, failedCount});

                    File tempPdf = null;
                    try {
                        StudentReportData data = model.getStudentReportData(rec.getRollNo());
                        if (data == null) {
                            throw new IllegalStateException("Student record missing from database.");
                        }
                        if (attachPdf) {
                            tempPdf = File.createTempFile("Academic_Report_" + rec.getRollNo() + "_", ".pdf");
                            tempPdf.deleteOnExit();
                            PDFReportGenerator.generateReport(data, tempPdf);
                        }
                        EmailService.sendLowAttendanceAlertEmail(data, tempPdf);
                        sentCount++;
                        logs.add(String.format("SENT      | Roll %-8d | %-20s | %s (Min Att: %.1f%%)",
                                rec.getRollNo(), rec.getName(), rec.getEmail(), rec.getLowestAttendancePct()));
                    } catch (Exception ex) {
                        failedCount++;
                        String reason = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                        logs.add(String.format("FAILED    | Roll %-8d | %-20s | %s -> %s",
                                rec.getRollNo(), rec.getName(), rec.getEmail(), reason));
                    } finally {
                        if (tempPdf != null && tempPdf.exists()) {
                            tempPdf.delete();
                        }
                    }
                    publish(new int[]{i + 1, sentCount, failedCount});

                    if (i < selectedRecipients.size() - 1 && !stopRequested.get()) {
                        try {
                            Thread.sleep(150);
                        } catch (InterruptedException ignored) {
                            stopRequested.set(true);
                        }
                    }
                }
                return logs;
            }

            @Override
            protected void process(List<int[]> chunks) {
                int[] latest = chunks.get(chunks.size() - 1);
                int processed = latest[0];
                int sent = latest[1];
                int failed = latest[2];
                progressBar.setValue(processed);
                lblCounts.setText("Sent: " + sent + " | Failed: " + failed + " | Total: " + total);
                view.btnBulkAlertEmail.setText("Sending Alerts (" + processed + "/" + total + ")...");
                if (processed < total && !activeStudentLabel.isEmpty()) {
                    lblCurrent.setText("Sending " + (processed + 1) + " of " + total + ": " + activeStudentLabel);
                } else {
                    lblCurrent.setText("Finalizing dispatch (" + processed + "/" + total + ")...");
                }
            }

            @Override
            protected void done() {
                view.btnBulkAlertEmail.setEnabled(true);
                view.btnBulkAlertEmail.setText("Send Bulk Attendance Alerts");
                progressDialog.dispose();

                List<String> logs = new ArrayList<>();
                try {
                    logs = get();
                } catch (Exception ignored) {
                }

                int cancelled = Math.max(0, total - (sentCount + failedCount));

                JTextArea logArea = new JTextArea(String.join("\n", logs));
                logArea.setEditable(false);
                logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
                JScrollPane scroll = new JScrollPane(logArea);
                scroll.setPreferredSize(new Dimension(640, 200));

                JPanel summaryPanel = new JPanel(new BorderLayout(8, 8));
                String titleText = String.format(
                        "<html><b>Bulk Low-Attendance Alert Summary</b><br>"
                        + "Sent: <font color='#15803D'><b>%d</b></font> &nbsp;|&nbsp; "
                        + "Failed: <font color='#B91C1C'><b>%d</b></font> &nbsp;|&nbsp; "
                        + "Cancelled/Skipped: <b>%d</b></html>",
                        sentCount, failedCount, cancelled);
                summaryPanel.add(new JLabel(titleText), BorderLayout.NORTH);
                summaryPanel.add(scroll, BorderLayout.CENTER);

                int msgType = failedCount > 0 ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE;
                JOptionPane.showMessageDialog(view, summaryPanel, "Bulk Alert Dispatch Complete", msgType);
            }
        };

        btnHide.addActionListener(e -> progressDialog.setVisible(false));
        btnCancel.addActionListener(e -> {
            stopRequested.set(true);
            btnCancel.setEnabled(false);
            lblCurrent.setText("Cancelling remaining emails after current send...");
        });

        worker.execute();
        progressDialog.setVisible(true);
    }

    private void handleExportPdf(StudentReportView reportView, StudentReportData data) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Academic Report as PDF");
        String defaultName = "Academic_Report_" + data.getRollNo() + ".pdf";
        chooser.setSelectedFile(new File(defaultName));
        chooser.setFileFilter(new FileNameExtensionFilter("PDF Documents (*.pdf)", "pdf"));

        int userSelection = chooser.showSaveDialog(reportView);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = chooser.getSelectedFile();
            if (!fileToSave.getName().toLowerCase().endsWith(".pdf")) {
                fileToSave = new File(fileToSave.getParentFile(), fileToSave.getName() + ".pdf");
            }

            final File target = fileToSave;
            reportView.btnExportPdf.setEnabled(false);

            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() throws Exception {
                    PDFReportGenerator.generateReport(data, target);
                    return null;
                }

                @Override
                protected void done() {
                    reportView.btnExportPdf.setEnabled(true);
                    try {
                        get();
                        JOptionPane.showMessageDialog(reportView,
                                "PDF report successfully exported to:\n" + target.getAbsolutePath(),
                                "Export Successful", JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(reportView,
                                "Failed to generate PDF: " + ex.getMessage(),
                                "PDF Export Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            };
            worker.execute();
        }
    }

    private void handleSendEmail(StudentReportView reportView, StudentReportData data) {
        String email = data.getEmail();
        if (email == null || email.trim().isEmpty() || !EmailService.isValidEmail(email)) {
            JOptionPane.showMessageDialog(reportView,
                    "Student does not have a valid registered email address (" + (email == null || email.isEmpty() ? "None" : email) + ").\n"
                    + "Please update the student's email address in the Students tab first.",
                    "Invalid Email", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (!EmailService.isConfigured()) {
            if (!showSmtpConfigDialog(reportView)) {
                return;
            }
        }

        // Required Confirmation Dialog:
        // "Are you sure you want to send the academic report to [student email]?"
        int confirm = JOptionPane.showConfirmDialog(reportView,
                "Are you sure you want to send the academic report to " + email.trim() + "?\n\n"
                + "Student Name: " + data.getName() + "\n"
                + "Roll Number: " + data.getRollNo() + "\n"
                + "Recipient Email: " + email.trim() + "\n"
                + "Attachment: Academic_Report_" + data.getRollNo() + ".pdf",
                "Confirm Email Dispatch", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        // Asynchronous sending via SwingWorker to avoid freezing the EDT
        reportView.btnSendEmail.setEnabled(false);
        String originalText = reportView.btnSendEmail.getText();
        reportView.btnSendEmail.setText("Sending Email, Please Wait...");

        final boolean[] noticeOpen = {true};
        final Runnable[] pendingCompletionDialog = {null};

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                File tempPdf = File.createTempFile("Academic_Report_" + data.getRollNo() + "_", ".pdf");
                tempPdf.deleteOnExit();
                PDFReportGenerator.generateReport(data, tempPdf);
                EmailService.sendReportEmail(data, tempPdf);
                return null;
            }

            @Override
            protected void done() {
                reportView.btnSendEmail.setEnabled(true);
                reportView.btnSendEmail.setText(originalText);

                Runnable showResult = () -> {
                    Component dialogParent = reportView.isShowing() ? reportView : view;
                    try {
                        get();
                        JOptionPane.showMessageDialog(dialogParent,
                                "Academic report has been successfully sent!\n\n"
                                + "Student Name: " + data.getName() + "\n"
                                + "Roll Number: " + data.getRollNo() + "\n"
                                + "Department: " + data.getDepartment() + "\n"
                                + "Recipient Email: " + email.trim() + "\n"
                                + "Attachment: Academic_Report_" + data.getRollNo() + ".pdf",
                                "Email Dispatched Successfully", JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception ex) {
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        Object[] options = {"Reconfigure SMTP Settings", "Close"};
                        int choice = JOptionPane.showOptionDialog(dialogParent,
                                "Failed to send email for " + data.getName() + " (Roll No: " + data.getRollNo()
                                + ", " + email.trim() + "):\n" + cause.getMessage() + "\n\n"
                                + "Troubleshooting Tips:\n"
                                + "• Ensure internet connectivity is active.\n"
                                + "• For Gmail SMTP, use a 16-character Google App Password (not your normal Gmail password).\n"
                                + "• Verify your sender email and SMTP host/port in settings.",
                                "Email Delivery Error",
                                JOptionPane.YES_NO_OPTION,
                                JOptionPane.ERROR_MESSAGE,
                                null,
                                options,
                                options[0]);
                        if (choice == 0 && showSmtpConfigDialog(dialogParent)) {
                            if (reportView.isShowing()) {
                                handleSendEmail(reportView, data);
                            } else {
                                JOptionPane.showMessageDialog(view,
                                        "SMTP settings updated. Reopen the student's report to send the email.",
                                        "SMTP Updated", JOptionPane.INFORMATION_MESSAGE);
                            }
                        }
                    }
                };

                if (noticeOpen[0]) {
                    pendingCompletionDialog[0] = showResult;
                } else {
                    showResult.run();
                }
            }
        };
        worker.execute();

        JOptionPane.showMessageDialog(reportView,
                "Sending academic report in the background...\n\n"
                + "Student Name: " + data.getName() + "\n"
                + "Roll Number: " + data.getRollNo() + "\n"
                + "Recipient Email: " + email.trim() + "\n\n"
                + "Once the report is sent, a confirmation dialog box will be shown.\n"
                + "You can click OK and continue using the application.",
                "Sending Report in Background",
                JOptionPane.INFORMATION_MESSAGE);

        noticeOpen[0] = false;
        if (pendingCompletionDialog[0] != null) {
            pendingCompletionDialog[0].run();
        }
    }

    private boolean showSmtpConfigDialog(Component parent) {
        Properties current = EmailService.loadSmtpProperties();
        String existingHost = current.getProperty("mail.smtp.host", "smtp.gmail.com");
        String existingPort = current.getProperty("mail.smtp.port", "587");
        String existingUser = current.getProperty("mail.smtp.user", "");
        if (existingUser.toLowerCase().startsWith("your_")) {
            existingUser = "";
        }

        JTextField txtHost = new JTextField(existingHost, 24);
        JTextField txtPort = new JTextField(existingPort, 24);
        JTextField txtUser = new JTextField(existingUser, 24);
        JPasswordField txtPass = new JPasswordField(24);
        char defaultEchoChar = txtPass.getEchoChar();

        JCheckBox chkShowPass = new JCheckBox("Show App Password");
        chkShowPass.addActionListener(e ->
                txtPass.setEchoChar(chkShowPass.isSelected() ? (char) 0 : defaultEchoChar));

        while (true) {
            JPanel panel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(4, 6, 4, 6);
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.anchor = GridBagConstraints.WEST;

            JLabel lblIntro = new JLabel("<html><b>Configure SMTP Sender Settings</b><br>"
                    + "Settings will be saved locally to <code>smtp.properties</code> so you only need to set this once.</html>");
            lblIntro.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.gridwidth = 2;
            panel.add(lblIntro, gbc);

            gbc.gridwidth = 1;
            gbc.gridy = 1;
            gbc.gridx = 0;
            panel.add(new JLabel("SMTP Host:"), gbc);
            gbc.gridx = 1;
            panel.add(txtHost, gbc);

            gbc.gridy = 2;
            gbc.gridx = 0;
            panel.add(new JLabel("SMTP Port:"), gbc);
            gbc.gridx = 1;
            panel.add(txtPort, gbc);

            gbc.gridy = 3;
            gbc.gridx = 0;
            panel.add(new JLabel("Sender Email:"), gbc);
            gbc.gridx = 1;
            panel.add(txtUser, gbc);

            gbc.gridy = 4;
            gbc.gridx = 0;
            panel.add(new JLabel("App Password:"), gbc);
            gbc.gridx = 1;
            panel.add(txtPass, gbc);

            gbc.gridy = 5;
            gbc.gridx = 1;
            panel.add(chkShowPass, gbc);

            JLabel lblHint = new JLabel("<html><font color='#555555' size='2'>"
                    + "For Gmail: Enable 2-Step Verification and generate a 16-character App Password at<br>"
                    + "<b>myaccount.google.com/apppasswords</b> (do not use your regular Gmail password).</font></html>");
            gbc.gridy = 6;
            gbc.gridx = 0;
            gbc.gridwidth = 2;
            panel.add(lblHint, gbc);

            int result = JOptionPane.showConfirmDialog(
                    parent,
                    panel,
                    "Configure Email Settings (SMTP)",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);

            if (result != JOptionPane.OK_OPTION) {
                return false;
            }

            String host = txtHost.getText().trim();
            String port = txtPort.getText().trim();
            String user = txtUser.getText().trim();
            String pass = new String(txtPass.getPassword()).trim();

            if (host.isEmpty() || port.isEmpty()) {
                JOptionPane.showMessageDialog(parent, "SMTP Host and Port are required.",
                        "Validation Error", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            try {
                int portNum = Integer.parseInt(port);
                if (portNum < 1 || portNum > 65535) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(parent, "SMTP Port must be a valid number between 1 and 65535.",
                        "Validation Error", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            if (!EmailService.isValidEmail(user)) {
                JOptionPane.showMessageDialog(parent, "Please enter a valid Sender Email address.",
                        "Validation Error", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            if (pass.isEmpty()) {
                JOptionPane.showMessageDialog(parent, "Please enter your SMTP / Gmail App Password.",
                        "Validation Error", JOptionPane.WARNING_MESSAGE);
                continue;
            }

            try {
                File saved = EmailService.saveSmtpProperties(host, port, user, pass);
                JOptionPane.showMessageDialog(parent,
                        "SMTP settings saved to:\n" + saved.getAbsolutePath(),
                        "Configuration Saved", JOptionPane.INFORMATION_MESSAGE);
                return true;
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(parent,
                        "Failed to save smtp.properties: " + ex.getMessage(),
                        "Save Error", JOptionPane.ERROR_MESSAGE);
                return false;
            }
        }
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
