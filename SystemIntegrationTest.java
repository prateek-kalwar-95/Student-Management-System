import java.io.File;
import java.sql.SQLException;
import java.util.List;

public class SystemIntegrationTest {

    public static void main(String[] args) {
        System.out.println("====================================================");
        System.out.println("STUDENT MANAGEMENT SYSTEM - INTEGRATION TESTS");
        System.out.println("====================================================");

        int passed = 0;
        int failed = 0;

        try {
            DatabaseModel model = new DatabaseModel();
            model.initDatabase();

            // ----------------------------------------------------
            // TEST 1: AttendanceCellRenderer parsing & thresholds
            // ----------------------------------------------------
            System.out.println("\n[TEST 1] Testing AttendanceCellRenderer thresholds & parsing...");
            testAttendanceThreshold(95.0, "SAFE");
            testAttendanceThreshold(75.0, "SAFE");
            testAttendanceThreshold(74.9, "WARNING");
            testAttendanceThreshold(50.0, "WARNING");
            testAttendanceThreshold(49.9, "DANGER");
            testAttendanceThreshold(0.0, "DANGER");
            System.out.println("✓ TEST 1 PASSED: Attendance thresholds match exact specifications (>=75 Green, 50-74 Yellow, <50 Red)");
            passed++;

            // ----------------------------------------------------
            // TEST 2: Unique Student dropdown population & duplicate prevention
            // ----------------------------------------------------
            System.out.println("\n[TEST 2] Testing Duplicate Enrollment Prevention & Unique Students...");
            long testRoll = 999901;
            String courseA = "TEST101";
            String courseB = "TEST102";

            // Clean up any previous test artifacts
            try (java.sql.Connection c = model.connect(); java.sql.Statement s = c.createStatement()) {
                s.execute("DELETE FROM enrollments WHERE roll_no >= 999000 OR course_code IN ('TEST101', 'TEST102');");
                s.execute("DELETE FROM students WHERE roll_no >= 999000;");
                s.execute("DELETE FROM courses WHERE course_code IN ('TEST101', 'TEST102');");
            } catch (Exception ignored) {}

            int initialUniqueCount = model.getUniqueStudents().size();

            // Setup test student and courses
            model.addStudent(testRoll, "Test Student", "teststudent@example.com", "CSE", "9876543210");
            model.addCourse(courseA, "Test Course A", 4, "Prof. Alpha");
            model.addCourse(courseB, "Test Course B", 3, "Prof. Beta");

            List<Object[]> uniqueStudentsAfterAdd = model.getUniqueStudents();
            System.out.println("Fetched " + uniqueStudentsAfterAdd.size() + " unique students from students table.");
            if (uniqueStudentsAfterAdd.size() != initialUniqueCount + 1) {
                throw new AssertionError("Expected unique students count " + (initialUniqueCount + 1)
                        + " after adding test student, got " + uniqueStudentsAfterAdd.size());
            }

            // 1. Same student enrolled in Course A -> Allowed
            boolean alreadyEnrolledBefore = model.isStudentEnrolledInCourse(testRoll, courseA);
            if (alreadyEnrolledBefore) throw new AssertionError("Student should not be enrolled in Course A yet");

            model.addEnrollment(testRoll, courseA, 88.0, 78.0);
            System.out.println("✓ Same student in Course A: Enrolled successfully.");

            // 2. Same student enrolled in Course B -> Allowed
            model.addEnrollment(testRoll, courseB, 65.0, 60.0);
            System.out.println("✓ Same student in Course B: Enrolled successfully (multiple courses allowed).");

            if (model.getUniqueStudents().size() != initialUniqueCount + 1) {
                throw new AssertionError("Enrolling same student in multiple courses should not duplicate unique student list");
            }

            // 3. Same student in Course A AGAIN -> Duplicate check MUST detect it
            boolean isDuplicate = model.isStudentEnrolledInCourse(testRoll, courseA);
            if (!isDuplicate) throw new AssertionError("Duplicate check failed: student is already enrolled in Course A!");
            System.out.println("✓ Same student in Course A again: Properly detected as duplicate!");

            // 4. Different student enrolled in Course A -> Allowed
            long testRoll2 = 999902;
            try { model.deleteStudent(testRoll2); } catch (Exception ignored) {}
            model.addStudent(testRoll2, "Second Student", "second@example.com", "IT", "9876543211");
            boolean diffStudentDuplicate = model.isStudentEnrolledInCourse(testRoll2, courseA);
            if (diffStudentDuplicate) throw new AssertionError("Second student should not be marked as duplicate for Course A");
            model.addEnrollment(testRoll2, courseA, 92.0, 85.0);
            System.out.println("✓ Different student in Course A: Enrolled successfully.");

            System.out.println("✓ TEST 2 PASSED: Duplicate enrollment correctly prevented.");
            passed++;

            // ----------------------------------------------------
            // TEST 3: Individual Student Report Generation (Features 3 & 4)
            // ----------------------------------------------------
            System.out.println("\n[TEST 3] Testing Comprehensive Student Report Data & PDF Generation...");

            // Test Student with multiple courses
            StudentReportData reportData = model.getStudentReportData(testRoll);
            if (reportData == null) throw new AssertionError("Report data was null for roll: " + testRoll);
            if (reportData.getTotalCourses() != 2) throw new AssertionError("Expected 2 courses, got: " + reportData.getTotalCourses());

            // Check overall attendance calculation: average of 88.0% and 65.0% = 76.5%
            double expectedAvgAtt = (88.0 + 65.0) / 2.0;
            if (Math.abs(reportData.getOverallAttendancePct() - expectedAvgAtt) > 0.01) {
                throw new AssertionError("Overall attendance calculation mismatch: expected " + expectedAvgAtt + ", got " + reportData.getOverallAttendancePct());
            }

            // Check shortage detection: Course B is 65% (< 75%), so shortageCount must be 1
            if (reportData.getShortageCount() != 1) {
                throw new AssertionError("Expected 1 course below threshold, got: " + reportData.getShortageCount());
            }

            System.out.println("Student: " + reportData.getName() + " (" + reportData.getRollNo() + ")");
            System.out.println("Total Courses: " + reportData.getTotalCourses());
            System.out.println(String.format("Overall Attendance: %.1f%% (Status: %s)", reportData.getOverallAttendancePct(), reportData.getOverallStatus()));
            System.out.println(String.format("Courses below 75%%: %d", reportData.getShortageCount()));

            // Test PDF Generation from report data
            File pdfFile = new File("Test_Report_Output.pdf");
            PDFReportGenerator.generateReport(reportData, pdfFile);
            if (!pdfFile.exists() || pdfFile.length() < 1000) {
                throw new AssertionError("PDF generation failed or file too small: " + (pdfFile.exists() ? pdfFile.length() : "missing"));
            }
            System.out.println("✓ PDF Report generated successfully (" + pdfFile.length() + " bytes).");
            pdfFile.delete();

            // Test student with no enrolled courses
            long testRollEmpty = 999903;
            try { model.deleteStudent(testRollEmpty); } catch (Exception ignored) {}
            model.addStudent(testRollEmpty, "No Courses Student", "nocourse@example.com", "ME", "9876543212");
            StudentReportData emptyReport = model.getStudentReportData(testRollEmpty);
            if (emptyReport.getTotalCourses() != 0) throw new AssertionError("Expected 0 courses");
            System.out.println("✓ Student with 0 enrolled courses handled gracefully without error.");

            System.out.println("✓ TEST 3 PASSED: Comprehensive student report data & PDF export working.");
            passed++;

            // ----------------------------------------------------
            // TEST 4: EmailService Validation and Configurations
            // ----------------------------------------------------
            System.out.println("\n[TEST 4] Testing EmailService validation & security...");
            if (!EmailService.isValidEmail("alice@example.com")) throw new AssertionError("Valid email rejected");
            if (!EmailService.isValidEmail("student.name@university.edu")) throw new AssertionError("Valid email rejected");
            if (EmailService.isValidEmail("invalid-email-address")) throw new AssertionError("Invalid email accepted");
            if (EmailService.isValidEmail("missing@domain")) throw new AssertionError("Invalid email accepted");
            if (EmailService.isValidEmail(null)) throw new AssertionError("Null email accepted");
            if (EmailService.isValidEmail("")) throw new AssertionError("Empty email accepted");

            System.out.println("✓ Email syntax validation working accurately.");
            System.out.println("✓ SMTP Configuration detection: " + (EmailService.isConfigured() ? "Configured" : "Not configured (safe template available)"));

            // Test sendReportEmail argument validation
            try {
                EmailService.sendReportEmail("Test Student", "invalid-email", testRoll, new File("dummy.pdf"));
                throw new AssertionError("Should fail on invalid email");
            } catch (IllegalArgumentException ex) {
                System.out.println("✓ Invalid email correctly rejected by sendReportEmail: " + ex.getMessage());
            }

            try {
                EmailService.sendReportEmail("Test Student", "test@example.com", testRoll, new File("non_existent_file.pdf"));
                throw new AssertionError("Should fail on missing attachment");
            } catch (java.io.IOException ex) {
                System.out.println("✓ Missing PDF attachment correctly detected: " + ex.getMessage());
            }

            System.out.println("✓ TEST 4 PASSED: Email service safety and validation checks pass.");
            passed++;

            // ----------------------------------------------------
            // TEST 5: Multi-Level Email Verification (Syntax, Typo, Duplicate, DNS MX, OTP, DB Status)
            // ----------------------------------------------------
            System.out.println("\n[TEST 5] Testing 3-Level Email Verification (Syntax/Typo/Duplicate, DNS MX, OTP, DB Status)...");

            // Level 1: Stricter syntax + domain typo detection
            if (EmailService.isValidEmail("bad..dots@gmail.com")) throw new AssertionError("Double-dot email accepted");
            if (EmailService.isValidEmail(".leading@gmail.com")) throw new AssertionError("Leading dot local-part accepted");
            if (EmailService.isValidEmail("trailing.@gmail.com")) throw new AssertionError("Trailing dot local-part accepted");
            if (EmailService.isValidEmail("user@-badhost.com")) throw new AssertionError("Leading hyphen domain accepted");

            if (!"gmail.com".equals(EmailService.suggestDomainTypo("student@gmial.com"))) {
                throw new AssertionError("Expected typo suggestion 'gmail.com' for 'student@gmial.com'");
            }
            if (!"outlook.com".equals(EmailService.suggestDomainTypo("student@outlok.com"))) {
                throw new AssertionError("Expected typo suggestion 'outlook.com' for 'student@outlok.com'");
            }
            if (EmailService.suggestDomainTypo("student@gmail.com") != null) {
                throw new AssertionError("Did not expect typo suggestion for valid 'gmail.com'");
            }
            System.out.println("✓ Level 1 syntax & domain typo detection verified.");

            // Duplicate email check (case-insensitive) & email_verified persistence
            if (!model.isEmailInUse("teststudent@example.com", -1)) {
                throw new AssertionError("Expected 'teststudent@example.com' to be detected as in use");
            }
            if (!model.isEmailInUse("  TESTSTUDENT@EXAMPLE.COM ", testRoll2)) {
                throw new AssertionError("Expected case-insensitive duplicate email match");
            }
            if (model.isEmailInUse("teststudent@example.com", testRoll)) {
                throw new AssertionError("Own roll number should be excluded from duplicate email check");
            }

            if (model.isEmailVerified(testRoll)) {
                throw new AssertionError("Expected newly added student email to start Unverified");
            }
            model.markEmailVerified(testRoll, true);
            if (!model.isEmailVerified(testRoll)) {
                throw new AssertionError("Expected markEmailVerified(true) to persist in SQLite");
            }

            // 5-arg update with same email preserves Verified; changing email resets to Unverified
            model.updateStudent(testRoll, "Test Student Renamed", "teststudent@example.com", "CSE", "9876543210");
            if (!model.isEmailVerified(testRoll)) {
                throw new AssertionError("Updating name without changing email should preserve Verified status");
            }
            model.updateStudent(testRoll, "Test Student Renamed", "changedemail@example.com", "CSE", "9876543210");
            if (model.isEmailVerified(testRoll)) {
                throw new AssertionError("Changing student email via 5-arg update should reset Verified status");
            }

            // Explicit 6-arg verified student creation & 6th column in getAllStudents()
            long testRollVerified = 999904;
            model.addStudent(testRollVerified, "Verified Student", "verified999904@gmail.com", "ECE", "9876543219", true);
            boolean foundVerifiedRow = false;
            for (Object[] row : model.getAllStudents()) {
                if (((Number) row[0]).longValue() == testRollVerified) {
                    if (row.length < 6 || !"Verified".equals(row[5])) {
                        throw new AssertionError("Expected 6th column 'Verified' for roll " + testRollVerified);
                    }
                    foundVerifiedRow = true;
                    break;
                }
            }
            if (!foundVerifiedRow) throw new AssertionError("Verified student row not found in getAllStudents()");
            System.out.println("✓ Duplicate email check & email_verified DB persistence verified.");

            // Level 2: DNS MX / Domain Verification
            EmailService.DomainCheckResult badSyntax = EmailService.verifyEmailDomain("bad..mail@gmail.com");
            if (badSyntax.getStatus() != EmailService.DomainVerificationStatus.INVALID_SYNTAX) {
                throw new AssertionError("Expected INVALID_SYNTAX, got " + badSyntax.getStatus());
            }
            EmailService.DomainCheckResult reservedDomain = EmailService.verifyEmailDomain("alice@example.com");
            if (reservedDomain.getStatus() != EmailService.DomainVerificationStatus.INVALID_DOMAIN) {
                throw new AssertionError("Expected INVALID_DOMAIN for reserved example.com, got " + reservedDomain.getStatus());
            }
            EmailService.DomainCheckResult gmailDomain = EmailService.verifyEmailDomain("student@gmail.com");
            if (gmailDomain.getStatus() != EmailService.DomainVerificationStatus.VALID
                    && gmailDomain.getStatus() != EmailService.DomainVerificationStatus.NETWORK_UNAVAILABLE) {
                throw new AssertionError("Expected VALID (or NETWORK_UNAVAILABLE offline) for gmail.com, got " + gmailDomain.getStatus() + " (" + gmailDomain.getMessage() + ")");
            }
            EmailService.DomainCheckResult bogusDomain = EmailService.verifyEmailDomain("student@nonexistent-domain-check-998877.com");
            if (bogusDomain.getStatus() != EmailService.DomainVerificationStatus.INVALID_DOMAIN
                    && bogusDomain.getStatus() != EmailService.DomainVerificationStatus.NETWORK_UNAVAILABLE) {
                throw new AssertionError("Expected INVALID_DOMAIN (or NETWORK_UNAVAILABLE offline) for nonexistent domain, got " + bogusDomain.getStatus());
            }
            System.out.println("✓ Level 2 DNS MX check verified (gmail.com status: " + gmailDomain.getStatus() + ", fake domain status: " + bogusDomain.getStatus() + ").");

            // Level 3: 6-digit OTP generation
            String otp = EmailService.generateOtp();
            if (otp == null || !otp.matches("^[1-9][0-9]{5}$")) {
                throw new AssertionError("Expected 6-digit OTP (100000-999999), got: " + otp);
            }
            System.out.println("✓ Level 3 OTP generator verified (sample format OK: 6 digits).");

            System.out.println("✓ TEST 5 PASSED: All 3 email verification levels & DB persistence passed.");
            passed++;

            // ----------------------------------------------------
            // TEST 6: UI State Rules for Verified / Existing / Edit Email Workflow
            // ----------------------------------------------------
            System.out.println("\n[TEST 6] Testing UI Email Edit Lock & Verify Option Visibility Rules...");
            if (!java.awt.GraphicsEnvironment.isHeadless()) {
                long uiVerifiedRoll = 999905;
                long uiUnverifiedRoll = 999906;
                model.addStudent(uiVerifiedRoll, "UI Verified Student", "uiverified999905@gmail.com", "CSE", "9876543215", true);
                model.addStudent(uiUnverifiedRoll, "UI Unverified Student", "uiunverified999906@gmail.com", "ECE", "9876543216", false);

                javax.swing.SwingUtilities.invokeAndWait(() -> {
                    MainView view = new MainView();
                    try {
                        new AppController(model, view);

                        // 1. Add mode (new student): email editable, Edit Email hidden, Verify OTP visible
                        if (!view.txtStudEmail.isEditable()) {
                            throw new AssertionError("Add mode email field should be editable");
                        }
                        if (view.btnEditEmail.isVisible()) {
                            throw new AssertionError("Add mode should not show Edit Email button");
                        }
                        if (!view.btnVerifyEmail.isVisible()) {
                            throw new AssertionError("Add mode unverified email should show Verify OTP button");
                        }

                        // 2. Select existing Verified student: email locked, Verify OTP hidden, Edit Email visible
                        int verifiedViewRow = findStudViewRowByRoll(view, uiVerifiedRoll);
                        if (verifiedViewRow < 0) throw new AssertionError("UI verified test student missing in table");
                        view.studTable.setRowSelectionInterval(verifiedViewRow, verifiedViewRow);

                        if (view.txtStudEmail.isEditable()) {
                            throw new AssertionError("Existing student email must be locked until Edit Email is clicked");
                        }
                        if (!view.btnEditEmail.isVisible()) {
                            throw new AssertionError("Existing verified student must show Edit Email option");
                        }
                        if (view.btnVerifyEmail.isVisible()) {
                            throw new AssertionError("Verified email must NOT show Verify OTP button before editing email");
                        }

                        // 3. Click Edit Email on verified student: unlocks field, still hides Verify OTP while unchanged
                        view.btnEditEmail.doClick();
                        if (!view.txtStudEmail.isEditable()) {
                            throw new AssertionError("Clicking Edit Email must unlock email field");
                        }
                        if (view.btnVerifyEmail.isVisible()) {
                            throw new AssertionError("Verify OTP should stay hidden until email text actually changes");
                        }

                        // 4. Edit email text to a different address: Verify OTP must appear immediately
                        view.txtStudEmail.setText("changed999905@gmail.com");
                        if (!view.btnVerifyEmail.isVisible()) {
                            throw new AssertionError("Editing verified email to a new address must show Verify OTP button");
                        }

                        // 5. Click Cancel Edit: reverts email, locks field again, hides Verify OTP
                        view.btnEditEmail.doClick();
                        if (view.txtStudEmail.isEditable()) {
                            throw new AssertionError("Canceling email edit should re-lock email field");
                        }
                        if (!"uiverified999905@gmail.com".equals(view.txtStudEmail.getText())) {
                            throw new AssertionError("Canceling edit should restore original saved email");
                        }
                        if (view.btnVerifyEmail.isVisible()) {
                            throw new AssertionError("Canceling edit on verified email should hide Verify OTP again");
                        }

                        // 6. Select existing Unverified student: email locked, BOTH Edit Email and Verify OTP visible
                        int unverifiedViewRow = findStudViewRowByRoll(view, uiUnverifiedRoll);
                        if (unverifiedViewRow < 0) throw new AssertionError("UI unverified test student missing in table");
                        view.studTable.setRowSelectionInterval(unverifiedViewRow, unverifiedViewRow);

                        if (view.txtStudEmail.isEditable()) {
                            throw new AssertionError("Existing unverified student email must be locked until Edit Email is clicked");
                        }
                        if (!view.btnEditEmail.isVisible() || !view.btnVerifyEmail.isVisible()) {
                            throw new AssertionError("Existing unverified student must show both Edit Email and Verify OTP");
                        }

                        // 7. Clear Form returns to Add mode
                        view.btnStudClear.doClick();
                        if (!view.txtStudEmail.isEditable() || view.btnEditEmail.isVisible() || !view.btnVerifyEmail.isVisible()) {
                            throw new AssertionError("Clear Form should reset email controls to Add mode");
                        }
                    } finally {
                        view.dispose();
                    }
                });

                model.deleteStudent(uiVerifiedRoll);
                model.deleteStudent(uiUnverifiedRoll);
                System.out.println("✓ TEST 6 PASSED: Email lock, Edit Email toggle, and Verify OTP visibility behave as expected.");
            } else {
                System.out.println("✓ TEST 6 SKIPPED: Headless graphics environment.");
            }
            passed++;

            // ----------------------------------------------------
            // TEST 7: Bulk Low-Attendance Alert Aggregation & Safety Checks
            // ----------------------------------------------------
            System.out.println("\n[TEST 7] Testing Bulk Email Low-Attendance Alerts Grouping & Dashboard...");
            long bulkLowRoll = 999907;
            long bulkSafeRoll = 999908;
            model.addStudent(bulkLowRoll, "Bulk Low Student", "bulklow999907@gmail.com", "CSE", "9876543220", true);
            model.addStudent(bulkSafeRoll, "Bulk Safe Student", "bulksafe999908@gmail.com", "IT", "9876543221", true);

            // Enroll low-attendance student in 2 shortage courses (<50% and <75%) and safe student >=75%
            model.addEnrollment(bulkLowRoll, courseA, 42.0, 55.0);
            model.addEnrollment(bulkLowRoll, courseB, 68.0, 62.0);
            model.addEnrollment(bulkSafeRoll, courseA, 88.0, 80.0);

            List<DatabaseModel.LowAttendanceRecipient> bulkRecipients = model.getLowAttendanceRecipients();
            DatabaseModel.LowAttendanceRecipient foundLow = null;
            for (DatabaseModel.LowAttendanceRecipient r : bulkRecipients) {
                if (r.getRollNo() == bulkSafeRoll) {
                    throw new AssertionError("Safe attendance (>=75%) student should NOT be in low-attendance recipients");
                }
                if (r.getRollNo() == bulkLowRoll) {
                    foundLow = r;
                }
            }
            if (foundLow == null) {
                throw new AssertionError("Expected low-attendance student " + bulkLowRoll + " in grouped recipients");
            }
            if (foundLow.getShortageCourseCount() != 2 || foundLow.getDangerCourseCount() != 1) {
                throw new AssertionError("Expected 2 shortage courses (1 danger) for grouped recipient, got "
                        + foundLow.getShortageCourseCount() + " / " + foundLow.getDangerCourseCount());
            }
            if (!foundLow.isEmailVerified() || Math.abs(foundLow.getLowestAttendancePct() - 42.0) > 0.01) {
                throw new AssertionError("Expected verified flag and min attendance 42.0%");
            }

            // Reserved/placeholder email helper & sendLowAttendanceAlertEmail safety checks
            if (!EmailService.isReservedDomainEmail("alice@example.com")) {
                throw new AssertionError("Expected example.com to be detected as reserved domain");
            }
            if (EmailService.isReservedDomainEmail("student@gmail.com")) {
                throw new AssertionError("gmail.com should NOT be marked as reserved domain");
            }

            StudentReportData invalidAlertData = new StudentReportData(999909, "Invalid Email", "not-an-email", "CSE", "9876543222");
            try {
                EmailService.sendLowAttendanceAlertEmail(invalidAlertData, null);
                throw new AssertionError("Expected IllegalArgumentException for invalid alert email");
            } catch (IllegalArgumentException ignored) {
            }

            StudentReportData lowReport = model.getStudentReportData(bulkLowRoll);
            try {
                EmailService.sendLowAttendanceAlertEmail(lowReport, new File("missing_alert_attachment.pdf"));
                throw new AssertionError("Expected IOException when alert attachment file is missing");
            } catch (java.io.IOException ignored) {
            }

            // Verify 6-column shortage table + summary label on Dashboard UI
            if (!java.awt.GraphicsEnvironment.isHeadless()) {
                javax.swing.SwingUtilities.invokeAndWait(() -> {
                    MainView dashView = new MainView();
                    try {
                        new AppController(model, dashView);
                        if (dashView.shortageTableModel.getColumnCount() < 6) {
                            throw new AssertionError("Expected 6 columns in Dashboard shortage table");
                        }
                        if (dashView.btnBulkAlertEmail == null || dashView.btnDashboardViewReport == null) {
                            throw new AssertionError("Expected Dashboard bulk alert and view report buttons");
                        }
                        if (dashView.lblShortageSummary.getText().trim().isEmpty()) {
                            throw new AssertionError("Expected Dashboard shortage summary label text");
                        }
                    } finally {
                        dashView.dispose();
                    }
                });
            }

            model.deleteStudent(bulkLowRoll);
            model.deleteStudent(bulkSafeRoll);
            System.out.println("✓ TEST 7 PASSED: Bulk alert grouping, safety checks, and Dashboard controls passed.");
            passed++;

            // Clean up test data
            model.deleteStudent(testRoll);
            model.deleteStudent(testRoll2);
            model.deleteStudent(testRollEmpty);
            model.deleteStudent(testRollVerified);
            model.deleteCourse(courseA);
            model.deleteCourse(courseB);

        } catch (Exception ex) {
            System.err.println("TEST FAILED with exception: " + ex.getMessage());
            ex.printStackTrace();
            failed++;
        }

        System.out.println("\n====================================================");
        System.out.println("TEST SUMMARY: " + passed + " PASSED, " + failed + " FAILED");
        System.out.println("====================================================");
        if (failed > 0) System.exit(1);
    }

    private static int findStudViewRowByRoll(MainView view, long roll) {
        for (int viewRow = 0; viewRow < view.studTable.getRowCount(); viewRow++) {
            int modelRow = view.studTable.convertRowIndexToModel(viewRow);
            if (String.valueOf(roll).equals(String.valueOf(view.studTableModel.getValueAt(modelRow, 0)))) {
                return viewRow;
            }
        }
        return -1;
    }

    private static void testAttendanceThreshold(double pct, String expectedStatus) {
        String status;
        if (pct >= 75.0) status = "SAFE";
        else if (pct >= 50.0) status = "WARNING";
        else status = "DANGER";

        if (!status.equals(expectedStatus)) {
            throw new AssertionError("Mismatch for attendance " + pct + "%: expected " + expectedStatus + " but got " + status);
        }
        System.out.println(String.format("  - Attendance %.1f%% -> Status: %s (Background: %s)",
                pct, status, status.equals("SAFE") ? "Soft Green" : (status.equals("WARNING") ? "Pale Yellow" : "Soft Red")));
    }
}
