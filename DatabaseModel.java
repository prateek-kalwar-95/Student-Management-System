import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * MODEL: Handles SQLite database connection, table creation,
 * and all CRUD operations for Students, Courses, and Enrollments.
 */
public class DatabaseModel {
    private final String url = "jdbc:sqlite:student_management.db";

    public Connection connect() throws SQLException {
        return DriverManager.getConnection(url);
    }

    public void initDatabase() throws SQLException {
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            // Enable Foreign Keys in SQLite
            stmt.execute("PRAGMA foreign_keys = ON;");

            // 1. Students Table
            String createStudentsTable = "CREATE TABLE IF NOT EXISTS students ("
                    + "roll_no INTEGER PRIMARY KEY,"
                    + "name TEXT NOT NULL,"
                    + "email TEXT,"
                    + "department TEXT,"
                    + "phone TEXT"
                    + ");";
            stmt.execute(createStudentsTable);

            // 2. Courses Table
            String createCoursesTable = "CREATE TABLE IF NOT EXISTS courses ("
                    + "course_code TEXT PRIMARY KEY,"
                    + "course_name TEXT NOT NULL,"
                    + "credits INTEGER NOT NULL,"
                    + "instructor TEXT"
                    + ");";
            stmt.execute(createCoursesTable);

            // 3. Enrollments Table with Attendance & Grades
            String createEnrollmentsTable = "CREATE TABLE IF NOT EXISTS enrollments ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "roll_no INTEGER NOT NULL,"
                    + "course_code TEXT NOT NULL,"
                    + "attendance_pct REAL NOT NULL,"
                    + "attendance_marks REAL NOT NULL,"
                    + "exam_marks REAL NOT NULL,"
                    + "total_marks REAL NOT NULL,"
                    + "grade TEXT NOT NULL,"
                    + "FOREIGN KEY(roll_no) REFERENCES students(roll_no) ON DELETE CASCADE,"
                    + "FOREIGN KEY(course_code) REFERENCES courses(course_code) ON DELETE CASCADE,"
                    + "UNIQUE(roll_no, course_code)"
                    + ");";
            stmt.execute(createEnrollmentsTable);

            // Insert initial sample data if empty
            insertSampleDataIfEmpty(conn);
        }
    }

    private void insertSampleDataIfEmpty(Connection conn) {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM students")) {
            if (rs.next() && rs.getInt(1) == 0) {
                // Students
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO students(roll_no, name, email, department, phone) VALUES(?, ?, ?, ?, ?)")) {
                    insertStudentRow(ps, 101, "Alice Johnson", "alice@example.com", "Computer Science", "9876543210");
                    insertStudentRow(ps, 102, "Bob Smith", "bob@example.com", "Information Tech", "9876543211");
                    insertStudentRow(ps, 103, "Charlie Davis", "charlie@example.com", "Electronics", "9876543212");
                }

                // Courses
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO courses(course_code, course_name, credits, instructor) VALUES(?, ?, ?, ?)")) {
                    insertCourseRow(ps, "CS101", "Data Structures & Algorithms", 4, "Dr. Robert Adams");
                    insertCourseRow(ps, "CS102", "Object Oriented Programming (Java)", 4, "Prof. Sarah Miller");
                    insertCourseRow(ps, "CS103", "Database Management Systems", 3, "Dr. Kevin Lee");
                }

                // Check and insert sample enrollments
                try (Statement stmt2 = conn.createStatement();
                     ResultSet rs2 = stmt2.executeQuery("SELECT COUNT(*) FROM enrollments")) {
                    if (rs2.next() && rs2.getInt(1) == 0) {
                        saveEnrollmentWithConn(conn, 101, "CS101", 92.0, 82.0);
                        saveEnrollmentWithConn(conn, 101, "CS102", 86.0, 75.0);
                        saveEnrollmentWithConn(conn, 102, "CS101", 76.0, 68.0);
                        saveEnrollmentWithConn(conn, 103, "CS103", 68.0, 52.0);
                    }
                }
            } else {
                // If students exist but enrollments table is empty, seed enrollments
                try (Statement stmt2 = conn.createStatement();
                     ResultSet rs2 = stmt2.executeQuery("SELECT COUNT(*) FROM enrollments")) {
                    if (rs2.next() && rs2.getInt(1) == 0) {
                        saveEnrollmentWithConn(conn, 101, "CS101", 92.0, 82.0);
                        saveEnrollmentWithConn(conn, 101, "CS102", 86.0, 75.0);
                        saveEnrollmentWithConn(conn, 102, "CS101", 76.0, 68.0);
                        saveEnrollmentWithConn(conn, 103, "CS103", 68.0, 52.0);
                    }
                }
            }
        } catch (SQLException ex) {
            System.err.println("Sample data note: " + ex.getMessage());
        }
    }

    private void insertStudentRow(PreparedStatement ps, long roll, String name, String email, String dept, String phone) throws SQLException {
        ps.setLong(1, roll);
        ps.setString(2, name);
        ps.setString(3, email);
        ps.setString(4, dept);
        ps.setString(5, phone);
        ps.executeUpdate();
    }

    private void insertCourseRow(PreparedStatement ps, String code, String name, int credits, String inst) throws SQLException {
        ps.setString(1, code);
        ps.setString(2, name);
        ps.setInt(3, credits);
        ps.setString(4, inst);
        ps.executeUpdate();
    }

    // ==========================================
    // ATTENDANCE & GRADE CALCULATION LOGIC
    // ==========================================

    /**
     * Requirement: Integrate class attendance into overall marks.
     * Attendance weightage: max 10 marks.
     * >= 90% -> 10 marks
     * 85-89% -> 8 marks
     * 80-84% -> 6 marks
     * 75-79% -> 4 marks
     * < 75%  -> 0 marks (attendance shortage)
     */
    public double calculateAttendanceMarks(double attendancePct) {
        if (attendancePct >= 90.0) return 10.0;
        if (attendancePct >= 85.0) return 8.0;
        if (attendancePct >= 80.0) return 6.0;
        if (attendancePct >= 75.0) return 4.0;
        return 0.0;
    }

    /**
     * Calculates letter grade based on total marks (max 100):
     * >= 90 -> A+
     * >= 80 -> A
     * >= 70 -> B
     * >= 60 -> C
     * >= 50 -> D
     * < 50  -> F
     */
    public String calculateGrade(double totalMarks) {
        if (totalMarks >= 90.0) return "A+";
        if (totalMarks >= 80.0) return "A";
        if (totalMarks >= 70.0) return "B";
        if (totalMarks >= 60.0) return "C";
        if (totalMarks >= 50.0) return "D";
        return "F";
    }

    // ==========================================
    // STUDENT CRUD OPERATIONS
    // ==========================================

    public void addStudent(long rollNo, String name, String email, String department, String phone) throws SQLException {
        String sql = "INSERT INTO students(roll_no, name, email, department, phone) VALUES(?, ?, ?, ?, ?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, rollNo);
            pstmt.setString(2, name);
            pstmt.setString(3, email);
            pstmt.setString(4, department);
            pstmt.setString(5, phone);
            pstmt.executeUpdate();
        }
    }

    public void updateStudent(long rollNo, String name, String email, String department, String phone) throws SQLException {
        String sql = "UPDATE students SET name = ?, email = ?, department = ?, phone = ? WHERE roll_no = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setString(2, email);
            pstmt.setString(3, department);
            pstmt.setString(4, phone);
            pstmt.setLong(5, rollNo);
            pstmt.executeUpdate();
        }
    }

    public void deleteStudent(long rollNo) throws SQLException {
        String sql = "DELETE FROM students WHERE roll_no = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, rollNo);
            pstmt.executeUpdate();
        }
    }

    public List<Object[]> getAllStudents() throws SQLException {
        List<Object[]> list = new ArrayList<>();
        String sql = "SELECT roll_no, name, email, department, phone FROM students ORDER BY roll_no ASC";
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Object[]{
                        rs.getLong("roll_no"),
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getString("department"),
                        rs.getString("phone")
                });
            }
        }
        return list;
    }

    // ==========================================
    // COURSE CRUD OPERATIONS
    // ==========================================

    public void addCourse(String code, String name, int credits, String instructor) throws SQLException {
        String sql = "INSERT INTO courses(course_code, course_name, credits, instructor) VALUES(?, ?, ?, ?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, code);
            pstmt.setString(2, name);
            pstmt.setInt(3, credits);
            pstmt.setString(4, instructor);
            pstmt.executeUpdate();
        }
    }

    public void updateCourse(String code, String name, int credits, String instructor) throws SQLException {
        String sql = "UPDATE courses SET course_name = ?, credits = ?, instructor = ? WHERE course_code = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setInt(2, credits);
            pstmt.setString(3, instructor);
            pstmt.setString(4, code);
            pstmt.executeUpdate();
        }
    }

    public void deleteCourse(String code) throws SQLException {
        String sql = "DELETE FROM courses WHERE course_code = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, code);
            pstmt.executeUpdate();
        }
    }

    public List<Object[]> getAllCourses() throws SQLException {
        List<Object[]> list = new ArrayList<>();
        String sql = "SELECT course_code, course_name, credits, instructor FROM courses ORDER BY course_code ASC";
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Object[]{
                        rs.getString("course_code"),
                        rs.getString("course_name"),
                        rs.getInt("credits"),
                        rs.getString("instructor")
                });
            }
        }
        return list;
    }

    // ==========================================
    // ENROLLMENT & GRADING CRUD OPERATIONS
    // ==========================================

    public void saveEnrollment(long rollNo, String courseCode, double attendancePct, double examMarks) throws SQLException {
        try (Connection conn = connect()) {
            saveEnrollmentWithConn(conn, rollNo, courseCode, attendancePct, examMarks);
        }
    }

    private void saveEnrollmentWithConn(Connection conn, long rollNo, String courseCode, double attendancePct, double examMarks) throws SQLException {
        double attMarks = calculateAttendanceMarks(attendancePct);
        double total = examMarks + attMarks;
        String grade = calculateGrade(total);

        String sql = "INSERT INTO enrollments(roll_no, course_code, attendance_pct, attendance_marks, exam_marks, total_marks, grade) "
                   + "VALUES(?, ?, ?, ?, ?, ?, ?) "
                   + "ON CONFLICT(roll_no, course_code) DO UPDATE SET "
                   + "attendance_pct = excluded.attendance_pct, "
                   + "attendance_marks = excluded.attendance_marks, "
                   + "exam_marks = excluded.exam_marks, "
                   + "total_marks = excluded.total_marks, "
                   + "grade = excluded.grade";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, rollNo);
            pstmt.setString(2, courseCode);
            pstmt.setDouble(3, attendancePct);
            pstmt.setDouble(4, attMarks);
            pstmt.setDouble(5, examMarks);
            pstmt.setDouble(6, total);
            pstmt.setString(7, grade);
            pstmt.executeUpdate();
        }
    }

    public void deleteEnrollment(int id) throws SQLException {
        String sql = "DELETE FROM enrollments WHERE id = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        }
    }

    public List<Object[]> getAllEnrollments() throws SQLException {
        List<Object[]> list = new ArrayList<>();
        String sql = "SELECT e.id, e.roll_no, s.name, e.course_code, c.course_name, "
                   + "e.attendance_pct, e.attendance_marks, e.exam_marks, e.total_marks, e.grade "
                   + "FROM enrollments e "
                   + "JOIN students s ON e.roll_no = s.roll_no "
                   + "JOIN courses c ON e.course_code = c.course_code "
                   + "ORDER BY e.id DESC";

        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Object[]{
                        rs.getInt("id"),
                        rs.getLong("roll_no"),
                        rs.getString("name"),
                        rs.getString("course_code"),
                        rs.getString("course_name"),
                        String.format("%.1f%%", rs.getDouble("attendance_pct")),
                        String.format("%.1f / 10", rs.getDouble("attendance_marks")),
                        String.format("%.1f / 90", rs.getDouble("exam_marks")),
                        String.format("%.1f / 100", rs.getDouble("total_marks")),
                        rs.getString("grade")
                });
            }
        }
        return list;
    }

    // ==========================================
    // STATS & DASHBOARD DATA
    // ==========================================

    public int getStudentCount() throws SQLException {
        try (Connection conn = connect(); Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM students")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public int getCourseCount() throws SQLException {
        try (Connection conn = connect(); Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM courses")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public int getEnrollmentCount() throws SQLException {
        try (Connection conn = connect(); Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM enrollments")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public double getAverageTotalMarks() throws SQLException {
        try (Connection conn = connect(); Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT AVG(total_marks) FROM enrollments")) {
            return rs.next() ? rs.getDouble(1) : 0.0;
        }
    }

    public List<Object[]> getLowAttendanceStudents() throws SQLException {
        List<Object[]> list = new ArrayList<>();
        String sql = "SELECT s.roll_no, s.name, c.course_code, c.course_name, e.attendance_pct "
                   + "FROM enrollments e "
                   + "JOIN students s ON e.roll_no = s.roll_no "
                   + "JOIN courses c ON e.course_code = c.course_code "
                   + "WHERE e.attendance_pct < 75.0 "
                   + "ORDER BY e.attendance_pct ASC";
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Object[]{
                        rs.getLong("roll_no"),
                        rs.getString("name"),
                        rs.getString("course_code"),
                        rs.getString("course_name"),
                        String.format("%.1f%%", rs.getDouble("attendance_pct"))
                });
            }
        }
        return list;
    }
}
