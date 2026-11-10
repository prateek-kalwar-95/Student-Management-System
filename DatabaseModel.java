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
        Connection conn = DriverManager.getConnection(url);
        Statement stmt = conn.createStatement();
        stmt.execute("PRAGMA foreign_keys = ON;");
        stmt.close();
        return conn;
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
                    + "phone TEXT,"
                    + "email_verified INTEGER NOT NULL DEFAULT 0"
                    + ");";
            stmt.execute(createStudentsTable);
            ensureColumnExists(conn, "students", "email_verified", "INTEGER NOT NULL DEFAULT 0");

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

            // Ensure composite unique index exists for duplicate prevention
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_enrollment_roll_course ON enrollments(roll_no, course_code);");
        }
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

    private void ensureColumnExists(Connection conn, String table, String column, String definition) throws SQLException {
        boolean exists = false;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ");")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    exists = true;
                    break;
                }
            }
        }
        if (!exists) {
            try (Statement alter = conn.createStatement()) {
                alter.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition + ";");
            }
        }
    }

    public void addStudent(long rollNo, String name, String email, String department, String phone) throws SQLException {
        addStudent(rollNo, name, email, department, phone, false);
    }

    public void addStudent(long rollNo, String name, String email, String department, String phone, boolean emailVerified) throws SQLException {
        String sql = "INSERT INTO students(roll_no, name, email, department, phone, email_verified) VALUES(?, ?, ?, ?, ?, ?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, rollNo);
            pstmt.setString(2, name);
            pstmt.setString(3, email);
            pstmt.setString(4, department);
            pstmt.setString(5, phone);
            pstmt.setInt(6, emailVerified ? 1 : 0);
            pstmt.executeUpdate();
        }
    }

    public void updateStudent(long rollNo, String name, String email, String department, String phone) throws SQLException {
        Object[] existing = getStudentByRollNo(rollNo);
        boolean keepVerified = false;
        if (existing != null && existing[2] != null && email != null
                && existing[2].toString().trim().equalsIgnoreCase(email.trim())) {
            keepVerified = isEmailVerified(rollNo);
        }
        updateStudent(rollNo, name, email, department, phone, keepVerified);
    }

    public void updateStudent(long rollNo, String name, String email, String department, String phone, boolean emailVerified) throws SQLException {
        String sql = "UPDATE students SET name = ?, email = ?, department = ?, phone = ?, email_verified = ? WHERE roll_no = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setString(2, email);
            pstmt.setString(3, department);
            pstmt.setString(4, phone);
            pstmt.setInt(5, emailVerified ? 1 : 0);
            pstmt.setLong(6, rollNo);
            pstmt.executeUpdate();
        }
    }

    public boolean isEmailInUse(String email, long excludeRollNo) throws SQLException {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        String sql = "SELECT COUNT(*) FROM students WHERE LOWER(TRIM(email)) = LOWER(TRIM(?)) AND roll_no != ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email.trim());
            pstmt.setLong(2, excludeRollNo);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    public boolean isEmailVerified(long rollNo) throws SQLException {
        String sql = "SELECT COALESCE(email_verified, 0) FROM students WHERE roll_no = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, rollNo);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        }
    }

    public void markEmailVerified(long rollNo, boolean verified) throws SQLException {
        String sql = "UPDATE students SET email_verified = ? WHERE roll_no = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, verified ? 1 : 0);
            pstmt.setLong(2, rollNo);
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
        String sql = "SELECT roll_no, name, email, department, phone, "
                   + "CASE WHEN COALESCE(email_verified, 0) = 1 THEN 'Verified' ELSE 'Unverified' END AS email_status "
                   + "FROM students ORDER BY roll_no ASC";
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Object[]{
                        rs.getLong("roll_no"),
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getString("department"),
                        rs.getString("phone"),
                        rs.getString("email_status")
                });
            }
        }
        return list;
    }

    /**
     * FEATURE 2: Returns unique student records directly from students master table.
     */
    public List<Object[]> getUniqueStudents() throws SQLException {
        return getAllStudents();
    }

    /**
     * Fetches a single student's details by roll number.
     */
    public Object[] getStudentByRollNo(long rollNo) throws SQLException {
        String sql = "SELECT roll_no, name, email, department, phone FROM students WHERE roll_no = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, rollNo);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new Object[]{
                            rs.getLong("roll_no"),
                            rs.getString("name"),
                            rs.getString("email"),
                            rs.getString("department"),
                            rs.getString("phone")
                    };
                }
            }
        }
        return null;
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

    /**
     * Checks if a student is already enrolled in a specific course.
     */
    public boolean isStudentEnrolledInCourse(long rollNo, String courseCode) throws SQLException {
        String sql = "SELECT COUNT(*) FROM enrollments WHERE roll_no = ? AND course_code = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, rollNo);
            pstmt.setString(2, courseCode);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Checks if updating an enrollment would create a duplicate with another record.
     */
    public boolean isDuplicateEnrollment(long rollNo, String courseCode, int excludeEnrollmentId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM enrollments WHERE roll_no = ? AND course_code = ? AND id <> ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, rollNo);
            pstmt.setString(2, courseCode);
            pstmt.setInt(3, excludeEnrollmentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Fetches all courses enrolled by a particular student.
     */
    public List<Object[]> getEnrolledCoursesForStudent(long rollNo) throws SQLException {
        List<Object[]> list = new ArrayList<>();
        String sql = "SELECT e.id, c.course_code, c.course_name, c.credits, "
                   + "e.attendance_pct, e.attendance_marks, e.exam_marks, e.total_marks, e.grade "
                   + "FROM enrollments e "
                   + "JOIN courses c ON e.course_code = c.course_code "
                   + "WHERE e.roll_no = ? "
                   + "ORDER BY c.course_code ASC";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, rollNo);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new Object[]{
                            rs.getInt("id"),
                            rs.getString("course_code"),
                            rs.getString("course_name"),
                            rs.getInt("credits"),
                            rs.getDouble("attendance_pct"),
                            rs.getDouble("attendance_marks"),
                            rs.getDouble("exam_marks"),
                            rs.getDouble("total_marks"),
                            rs.getString("grade")
                    });
                }
            }
        }
        return list;
    }

    /**
     * Inserts a new enrollment record.
     */
    public void addEnrollment(long rollNo, String courseCode, double attendancePct, double examMarks) throws SQLException {
        double attMarks = calculateAttendanceMarks(attendancePct);
        double total = examMarks + attMarks;
        String grade = calculateGrade(total);

        String sql = "INSERT INTO enrollments(roll_no, course_code, attendance_pct, attendance_marks, exam_marks, total_marks, grade) "
                   + "VALUES(?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
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

    /**
     * Updates an existing enrollment record by primary key ID.
     */
    public void updateEnrollmentById(int id, long rollNo, String courseCode, double attendancePct, double examMarks) throws SQLException {
        double attMarks = calculateAttendanceMarks(attendancePct);
        double total = examMarks + attMarks;
        String grade = calculateGrade(total);

        String sql = "UPDATE enrollments SET roll_no = ?, course_code = ?, attendance_pct = ?, "
                   + "attendance_marks = ?, exam_marks = ?, total_marks = ?, grade = ? WHERE id = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, rollNo);
            pstmt.setString(2, courseCode);
            pstmt.setDouble(3, attendancePct);
            pstmt.setDouble(4, attMarks);
            pstmt.setDouble(5, examMarks);
            pstmt.setDouble(6, total);
            pstmt.setString(7, grade);
            pstmt.setInt(8, id);
            pstmt.executeUpdate();
        }
    }

    /**
     * FEATURE 3: Fetches comprehensive data for an individual student report.
     */
    public StudentReportData getStudentReportData(long rollNo) throws SQLException {
        Object[] stud = getStudentByRollNo(rollNo);
        if (stud == null) {
            return null;
        }

        String name = (String) stud[1];
        String email = (String) stud[2];
        String dept = (String) stud[3];
        String phone = (String) stud[4];

        StudentReportData report = new StudentReportData(rollNo, name, email, dept, phone);

        String sql = "SELECT c.course_code, c.course_name, c.credits, "
                   + "e.attendance_pct, e.attendance_marks, e.exam_marks, e.total_marks, e.grade "
                   + "FROM enrollments e "
                   + "JOIN courses c ON e.course_code = c.course_code "
                   + "WHERE e.roll_no = ? "
                   + "ORDER BY c.course_code ASC";

        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, rollNo);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    StudentReportData.CoursePerformance cp = new StudentReportData.CoursePerformance(
                            rs.getString("course_code"),
                            rs.getString("course_name"),
                            rs.getInt("credits"),
                            rs.getDouble("attendance_pct"),
                            rs.getDouble("attendance_marks"),
                            rs.getDouble("exam_marks"),
                            rs.getDouble("total_marks"),
                            rs.getString("grade")
                    );
                    report.addCourse(cp);
                }
            }
        }

        report.recalculate();
        return report;
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

    public static class LowAttendanceRecipient {
        private final long rollNo;
        private final String name;
        private final String email;
        private final boolean emailVerified;
        private int shortageCourseCount;
        private int dangerCourseCount;
        private double lowestAttendancePct;
        private final StringBuilder coursesSummary = new StringBuilder();

        public LowAttendanceRecipient(long rollNo, String name, String email, boolean emailVerified, double initialPct) {
            this.rollNo = rollNo;
            this.name = name != null ? name : "";
            this.email = email != null ? email.trim() : "";
            this.emailVerified = emailVerified;
            this.lowestAttendancePct = initialPct;
        }

        public void addShortageCourse(String courseCode, double attendancePct) {
            shortageCourseCount++;
            if (attendancePct < 50.0) {
                dangerCourseCount++;
            }
            if (attendancePct < lowestAttendancePct) {
                lowestAttendancePct = attendancePct;
            }
            if (coursesSummary.length() > 0) {
                coursesSummary.append(", ");
            }
            coursesSummary.append(courseCode).append(" (").append(String.format("%.1f%%", attendancePct)).append(")");
        }

        public long getRollNo() { return rollNo; }
        public String getName() { return name; }
        public String getEmail() { return email; }
        public boolean isEmailVerified() { return emailVerified; }
        public int getShortageCourseCount() { return shortageCourseCount; }
        public int getDangerCourseCount() { return dangerCourseCount; }
        public double getLowestAttendancePct() { return lowestAttendancePct; }
        public String getCoursesSummary() { return coursesSummary.toString(); }
    }

    public List<Object[]> getLowAttendanceStudents() throws SQLException {
        List<Object[]> list = new ArrayList<>();
        String sql = "SELECT s.roll_no, s.name, c.course_code, c.course_name, e.attendance_pct, "
                   + "CASE WHEN COALESCE(s.email_verified, 0) = 1 THEN 'Verified' ELSE 'Unverified' END AS email_status "
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
                        String.format("%.1f%%", rs.getDouble("attendance_pct")),
                        rs.getString("email_status")
                });
            }
        }
        return list;
    }

    /**
     * Groups all enrollments below 75% by unique student (ordered most critical first)
     * so bulk attendance alerts send one consolidated email per student.
     */
    public List<LowAttendanceRecipient> getLowAttendanceRecipients() throws SQLException {
        java.util.Map<Long, LowAttendanceRecipient> grouped = new java.util.LinkedHashMap<>();
        String sql = "SELECT s.roll_no, s.name, s.email, COALESCE(s.email_verified, 0) AS email_verified, "
                   + "c.course_code, e.attendance_pct "
                   + "FROM enrollments e "
                   + "JOIN students s ON e.roll_no = s.roll_no "
                   + "JOIN courses c ON e.course_code = c.course_code "
                   + "WHERE e.attendance_pct < 75.0 "
                   + "ORDER BY e.attendance_pct ASC, s.roll_no ASC, c.course_code ASC";
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                long rollNo = rs.getLong("roll_no");
                double attPct = rs.getDouble("attendance_pct");
                LowAttendanceRecipient rec = grouped.get(rollNo);
                if (rec == null) {
                    rec = new LowAttendanceRecipient(
                            rollNo,
                            rs.getString("name"),
                            rs.getString("email"),
                            rs.getInt("email_verified") == 1,
                            attPct);
                    grouped.put(rollNo, rec);
                }
                rec.addShortageCourse(rs.getString("course_code"), attPct);
            }
        }
        return new ArrayList<>(grouped.values());
    }
}
