========================================================================
STUDENT MANAGEMENT SYSTEM (Java Swing GUI + SQLite JDBC)
MVC Architecture
========================================================================

Class Group: A1
Team No.: 2
Project Title: Student Management System

------------------------------------------------------------------------
1. PROJECT OVERVIEW & FEATURES
------------------------------------------------------------------------
This project implements a complete Student Management System following 
the Model-View-Controller (MVC) architectural pattern:

- MODEL (DatabaseModel.java):
  * Manages SQLite database connection to 'student_management.db'.
  * Implements relational schema with foreign key constraints.
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
      1. Students Management: Form, live search filter, and interactive JTable.
      2. Courses Management: Form, live search filter, and interactive JTable.
      3. Enrollments & Grading: Student/Course dropdowns, live grade preview, JTable.
      4. Dashboard & Reports: KPI Cards and Attendance Shortage Alert Table (<75%).
  * Clicking any row in a table automatically loads its data into the form.

- CONTROLLER (AppController.java):
  * Mediates between View events and Model database calls.
  * Input validations and live error/success dialogs.
  * Live dynamic search filtering across tables.
  * Live calculation preview as user types attendance and exam scores.

- ENTRY POINT (Main.java):
  * Launches the application on the Event Dispatch Thread (SwingUtilities).

------------------------------------------------------------------------
2. PREREQUISITES
------------------------------------------------------------------------
- Java Development Kit (JDK 17 or higher, tested on JDK 25)
- SQLite JDBC Driver JAR: sqlite-jdbc-3.53.2.1.jar (or 3.x)
  (Keep the JAR in the same folder as the .java source files)

------------------------------------------------------------------------
3. HOW TO COMPILE AND RUN (COMMAND LINE)
------------------------------------------------------------------------
Open Command Prompt (cmd) or PowerShell in this project folder:

A. COMPILATION COMMAND:
   javac -cp ".;sqlite-jdbc-3.53.2.1.jar" DatabaseModel.java MainView.java AppController.java Main.java

B. EXECUTION COMMAND:
   java -cp ".;sqlite-jdbc-3.53.2.1.jar" Main

(Note: The '.;' ensures Java looks in both current directory and the SQLite jar)

========================================================================
