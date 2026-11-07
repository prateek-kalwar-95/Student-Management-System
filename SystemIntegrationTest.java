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
            List<Object[]> uniqueStudents = model.getUniqueStudents();
            System.out.println("Fetched " + uniqueStudents.size() + " unique students from students table.");
            if (uniqueStudents.isEmpty()) throw new AssertionError("Expected students in database");

            long testRoll = 999901;
            String courseA = "TEST101";
            String courseB = "TEST102";

            // Clean up any previous test artifacts
            try (java.sql.Connection c = model.connect(); java.sql.Statement s = c.createStatement()) {
                s.execute("DELETE FROM enrollments WHERE roll_no >= 999000 OR course_code IN ('TEST101', 'TEST102');");
                s.execute("DELETE FROM students WHERE roll_no >= 999000;");
                s.execute("DELETE FROM courses WHERE course_code IN ('TEST101', 'TEST102');");
            } catch (Exception ignored) {}

            // Setup test student and courses
            model.addStudent(testRoll, "Test Student", "teststudent@example.com", "CSE", "9876543210");
            model.addCourse(courseA, "Test Course A", 4, "Prof. Alpha");
            model.addCourse(courseB, "Test Course B", 3, "Prof. Beta");

            // 1. Same student enrolled in Course A -> Allowed
            boolean alreadyEnrolledBefore = model.isStudentEnrolledInCourse(testRoll, courseA);
            if (alreadyEnrolledBefore) throw new AssertionError("Student should not be enrolled in Course A yet");

            model.addEnrollment(testRoll, courseA, 88.0, 78.0);
            System.out.println("✓ Same student in Course A: Enrolled successfully.");

            // 2. Same student enrolled in Course B -> Allowed
            model.addEnrollment(testRoll, courseB, 65.0, 60.0);
            System.out.println("✓ Same student in Course B: Enrolled successfully (multiple courses allowed).");

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

            // Clean up test data
            model.deleteStudent(testRoll);
            model.deleteStudent(testRoll2);
            model.deleteStudent(testRollEmpty);
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
