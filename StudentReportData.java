import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Transfer Object containing complete academic and personal information
 * required to render and export an individual student's report.
 */
public class StudentReportData {

    public static class CoursePerformance {
        private final String courseCode;
        private final String courseName;
        private final int credits;
        private final double attendancePct;
        private final double attendanceMarks;
        private final double examMarks;
        private final double totalMarks;
        private final String grade;
        private final String status; // "SAFE", "WARNING", "DANGER"

        public CoursePerformance(String courseCode, String courseName, int credits,
                                 double attendancePct, double attendanceMarks,
                                 double examMarks, double totalMarks, String grade) {
            this.courseCode = courseCode;
            this.courseName = courseName;
            this.credits = credits;
            this.attendancePct = attendancePct;
            this.attendanceMarks = attendanceMarks;
            this.examMarks = examMarks;
            this.totalMarks = totalMarks;
            this.grade = grade;

            if (attendancePct >= 75.0) {
                this.status = "SAFE";
            } else if (attendancePct >= 50.0) {
                this.status = "WARNING";
            } else {
                this.status = "DANGER";
            }
        }

        public String getCourseCode() { return courseCode; }
        public String getCourseName() { return courseName; }
        public int getCredits() { return credits; }
        public double getAttendancePct() { return attendancePct; }
        public double getAttendanceMarks() { return attendanceMarks; }
        public double getExamMarks() { return examMarks; }
        public double getTotalMarks() { return totalMarks; }
        public String getGrade() { return grade; }
        public String getStatus() { return status; }
    }

    // Student Personal Information
    private final long rollNo;
    private final String name;
    private final String email;
    private final String department;
    private final String phone;
    private final String registrationNo;
    private final String groupSection;

    // Course records
    private final List<CoursePerformance> courses = new ArrayList<>();

    // Aggregates
    private double overallAttendancePct = 0.0;
    private double totalAttendanceMarks = 0.0;
    private double totalMaxAttendanceMarks = 0.0;
    private double overallAverageScore = 0.0;
    private String overallGrade = "N/A";
    private String overallStatus = "SAFE"; // SAFE, WARNING, DANGER
    private int shortageCount = 0; // < 75%
    private int dangerCount = 0;   // < 50%
    private final String generatedTimestamp;

    public StudentReportData(long rollNo, String name, String email, String department, String phone) {
        this.rollNo = rollNo;
        this.name = name != null ? name : "";
        this.email = email != null ? email : "";
        this.department = department != null ? department : "General";
        this.phone = phone != null ? phone : "";
        this.registrationNo = "REG-" + rollNo;
        this.groupSection = "Class Group A1 - Team 2";
        this.generatedTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss"));
    }

    public void addCourse(CoursePerformance course) {
        courses.add(course);
        recalculate();
    }

    public void recalculate() {
        if (courses.isEmpty()) {
            overallAttendancePct = 0.0;
            totalAttendanceMarks = 0.0;
            totalMaxAttendanceMarks = 0.0;
            overallAverageScore = 0.0;
            overallGrade = "N/A";
            overallStatus = "SAFE";
            shortageCount = 0;
            dangerCount = 0;
            return;
        }

        double sumAtt = 0.0;
        double sumAttMarks = 0.0;
        double sumTotalMarks = 0.0;
        shortageCount = 0;
        dangerCount = 0;

        for (CoursePerformance c : courses) {
            sumAtt += c.getAttendancePct();
            sumAttMarks += c.getAttendanceMarks();
            sumTotalMarks += c.getTotalMarks();

            if (c.getAttendancePct() < 50.0) {
                dangerCount++;
                shortageCount++;
            } else if (c.getAttendancePct() < 75.0) {
                shortageCount++;
            }
        }

        // Attendance stored course-wise percentage: document clearly as unweighted arithmetic average
        overallAttendancePct = sumAtt / courses.size();
        totalAttendanceMarks = sumAttMarks;
        totalMaxAttendanceMarks = courses.size() * 10.0;
        overallAverageScore = sumTotalMarks / courses.size();

        // Calculate overall letter grade from average total score
        if (overallAverageScore >= 90.0) overallGrade = "A+";
        else if (overallAverageScore >= 80.0) overallGrade = "A";
        else if (overallAverageScore >= 70.0) overallGrade = "B";
        else if (overallAverageScore >= 60.0) overallGrade = "C";
        else if (overallAverageScore >= 50.0) overallGrade = "D";
        else overallGrade = "F";

        // Attendance Status:
        // Color/status conditions: >= 75% SAFE, 50%-74% WARNING, <50% DANGER
        if (overallAttendancePct >= 75.0 && shortageCount == 0) {
            overallStatus = "SAFE";
        } else if (overallAttendancePct < 50.0 || dangerCount > 0) {
            overallStatus = "DANGER";
        } else {
            overallStatus = "WARNING";
        }
    }

    // Getters
    public long getRollNo() { return rollNo; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getDepartment() { return department; }
    public String getPhone() { return phone; }
    public String getRegistrationNo() { return registrationNo; }
    public String getGroupSection() { return groupSection; }
    public List<CoursePerformance> getCourses() { return courses; }
    public int getTotalCourses() { return courses.size(); }
    public double getOverallAttendancePct() { return overallAttendancePct; }
    public double getTotalAttendanceMarks() { return totalAttendanceMarks; }
    public double getTotalMaxAttendanceMarks() { return totalMaxAttendanceMarks; }
    public double getOverallAverageScore() { return overallAverageScore; }
    public String getOverallGrade() { return overallGrade; }
    public String getOverallStatus() { return overallStatus; }
    public int getShortageCount() { return shortageCount; }
    public int getDangerCount() { return dangerCount; }
    public String getGeneratedTimestamp() { return generatedTimestamp; }
}

