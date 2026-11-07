========================================================================
STUDENT MANAGEMENT SYSTEM (Java Swing GUI + SQLite JDBC)
MVC Architecture – Advanced Feature Integration
========================================================================

Class Group: A1
Team No.: 2
Project Title: Student Management System

------------------------------------------------------------------------
1. PROJECT OVERVIEW & FEATURES
------------------------------------------------------------------------
This project implements an enhanced, complete Student Management System 
following the Model-View-Controller (MVC) architectural pattern:

- MODEL (DatabaseModel.java):
  * Manages SQLite database connection to 'student_management.db'.
  * Automatic foreign key enforcement on every connection.
  * Composite UNIQUE(roll_no, course_code) constraint and index.
  * Handles full CRUD operations (Create, Read, Update, Delete) for:
      - Students (Roll No, Name, Email, Department, Phone)
      - Courses (Course Code, Course Name, Credits, Instructor)
      - Enrollments & Grades (Attendance %, Attendance Marks, Exam Marks, Total, Grade)
  * INTEGRATION OF ATTENDANCE INTO OVERALL MARKS:
      - Total Score (100) = Exam Marks (90) + Attendance Marks (10)
      - Attendance Criteria:
          * >= 90% attendance -> 10 Marks
          * 85% - 89%        -> 8 Marks
          * 80% - 84%        -> 6 Marks
          * 75% - 79%        -> 4 Marks
          * < 75%            -> 0 Marks (Attendance Shortage Flagged)
      - Grade assignment: A+ (>=90), A (>=80), B (>=70), C (>=60), D (>=50), F (<50)

- VIEW (MainView.java):
  * Modern Java Swing graphical user interface with System Look-and-Feel.
  * 4 Organized Tabbed Panels:
      1. Students Management: Form, live search filter, interactive JTable, and 'View Student Report' button (green with hover effect).
      2. Courses Management: Form, live search filter, and interactive JTable.
      3. Enrollments & Grading: Unique student/course dropdowns, enrolled courses summary, color-coded JTable.
      4. Dashboard & Reports: KPI Cards and Attendance Shortage Alert Table (<75%) with color coding.

- CONTROLLER (AppController.java):
  * Mediates between View events and Model database calls.
  * Asynchronous processing (SwingWorker) for PDF export and email dispatch.
  * Enforces duplicate enrollment validation.

------------------------------------------------------------------------
2. ADVANCED INTEGRATED FEATURES
------------------------------------------------------------------------
FEATURE 1: Attendance-Based Dynamic Color Coding (AttendanceCellRenderer.java)
- >= 75%: Soft Green (#D4EDDA) - Safe
- 50% - 74.9%: Pale Yellow (#FFF3CD) - Warning
- < 50%: Soft Red (#F8D7DA) - Danger
- Applied to Enrollments Table, Shortage Alert Table, and Individual Report Table.

FEATURE 2: Duplicate Prevention in Enrollments & Grading
- Populates unique students in the student dropdown.
- Displays enrolled courses for the selected student separately.
- Displays validation message: "This student is already enrolled in the selected course."
- Enforced at business logic and database level (composite UNIQUE constraint).

FEATURE 3: Comprehensive Individual Student Report (StudentReportView.java)
- Dedicated "View Student Report" buttons in Students Management (form and table).
- Styled in high-visibility green (#198754) with hover (#146c43) and bold white text.
- Displays personal details, academic KPIs, attendance shortage alerts, and course-wise breakdown.
- Export to PDF, Print, and Email buttons.

FEATURE 4: PDF Export & Email Delivery (PDFReportGenerator.java & EmailService.java)
- Generates high-resolution vector PDF reports (pure Java).
- Sends email via Jakarta Mail with PDF attachment after user confirmation.
- Send Report via Email button styled green with hover feedback.
- Configuration Priority:
  1. Environment variables: SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASS
  2. Configuration file: smtp.properties (template in smtp.properties.example)

------------------------------------------------------------------------
3. EXTERNAL DEPENDENCIES & VS CODE CLASSPATH
------------------------------------------------------------------------
Libraries are located in both the 'lib/' directory and project root:
- SQLite JDBC Driver: lib/sqlite-jdbc-3.53.2.1.jar
- Jakarta Mail: lib/jakarta.mail-2.0.1.jar
- Jakarta Activation: lib/jakarta.activation-2.0.1.jar

VS Code Configuration:
- '.vscode/settings.json' and '.classpath' are pre-configured so that VS Code
  instantly resolves all Jakarta Mail and SQLite imports without error.

------------------------------------------------------------------------
4. HOW TO COMPILE AND RUN (COMMAND LINE)
------------------------------------------------------------------------
Open Command Prompt (cmd), PowerShell, or Terminal in this folder:

A. COMPILATION COMMAND:
   • Windows:
     javac -cp ".;lib/*" *.java
     (or: javac -cp ".;*" *.java)

   • macOS / Linux:
     javac -cp ".:lib/*" *.java

B. EXECUTION COMMAND:
   • Windows:
     java -cp ".;lib/*" Main
     (or: java -cp ".;*" Main)

   • macOS / Linux:
     java -cp ".:lib/*" Main

========================================================================