# Student Management System (Java Swing GUI + SQLite JDBC)
**MVC Architecture**

- **Class Group:** A1
- **Team No.:** 2
- **Project Title:** Student Management System

---

## 1. Project Overview & Features
This project implements a complete Student Management System following the **Model-View-Controller (MVC)** architectural pattern:

### • Model (`DatabaseModel.java`):
* Manages SQLite database connection to `student_management.db`.
* Implements relational schema with foreign key constraints.
* Handles full CRUD operations (Create, Read, Update, Delete) for:
  - **Students** (Roll No, Name, Email, Department, Phone)
  - **Courses** (Course Code, Course Name, Credits, Instructor)
  - **Enrollments & Grades** (Attendance %, Attendance Marks, Exam Marks, Total, Grade)
* **Integration of Attendance into Overall Marks**:
  - Total Score (100) = Exam Marks (90) + Attendance Marks (10)
  - Attendance Criteria:
    - $\ge 90\%$ attendance $\rightarrow$ 10 Marks
    - $85\% - 89\%$ $\rightarrow$ 8 Marks
    - $80\% - 84\%$ $\rightarrow$ 6 Marks
    - $75\% - 79\%$ $\rightarrow$ 4 Marks
    - $< 75\%$ $\rightarrow$ 0 Marks (Attendance Shortage Flagged)
  - Grade assignment: A+ ($\ge 90$), A ($\ge 80$), B ($\ge 70$), C ($\ge 60$), D ($\ge 50$), F ($< 50$)

### • View (`MainView.java`):
* Modern Java Swing graphical user interface with System Look-and-Feel.
* 4 Organized Tabbed Panels:
  1. **Students Management:** Form, live search filter, and interactive JTable.
  2. **Courses Management:** Form, live search filter, and interactive JTable.
  3. **Enrollments & Grading:** Student/Course dropdowns, live grade preview, JTable.
  4. **Dashboard & Reports:** KPI Cards and Attendance Shortage Alert Table ($< 75\%$).
* Clicking any row in a table automatically loads its data into the form.

### • Controller (`AppController.java`):
* Mediates between View events and Model database calls.
* Input validations and live error/success dialogs.
* Live dynamic search filtering across tables.
* Live calculation preview as user types attendance and exam scores.

### • Entry Point (`Main.java`):
* Launches the application on the Event Dispatch Thread (`SwingUtilities.invokeLater`).

---

## 2. Prerequisites
- **Java Development Kit (JDK 17 or higher)**
- **SQLite JDBC Driver JAR:** `sqlite-jdbc-3.53.2.1.jar` (included in project)

---

## 3. How to Compile and Run (Command Line)

Open Command Prompt, PowerShell, or Terminal in the project root:

### A. Compilation Command:
- **Windows (CMD / PowerShell):**
  ```bash
  javac -cp ".;sqlite-jdbc-3.53.2.1.jar" DatabaseModel.java MainView.java AppController.java Main.java
  ```
- **macOS / Linux:**
  ```bash
  javac -cp ".:sqlite-jdbc-3.53.2.1.jar" DatabaseModel.java MainView.java AppController.java Main.java
  ```

### B. Execution Command:
- **Windows (CMD / PowerShell):**
  ```bash
  java -cp ".;sqlite-jdbc-3.53.2.1.jar" Main
  ```
- **macOS / Linux:**
  ```bash
  java -cp ".:sqlite-jdbc-3.53.2.1.jar" Main
  ```

