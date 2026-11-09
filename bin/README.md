# Student Management System (Java Swing GUI + SQLite JDBC)
**MVC Architecture – Advanced Feature Integration**

- **Class Group:** A1
- **Team No.:** 2
- **Project Title:** Student Management System

---

## 1. Project Overview & Features
This project is an advanced, production-grade Student Management System following the **Model-View-Controller (MVC)** architectural pattern:

### • Model (`DatabaseModel.java`):
* Manages SQLite database connection to `student_management.db`.
* Automatic foreign key enforcement on every connection (`PRAGMA foreign_keys = ON;`).
* Implements relational schema with foreign key constraints and composite UNIQUE constraint on `(roll_no, course_code)`.
* Full CRUD operations for:
  - **Students** (Roll No, Name, Email, Department, Phone)
  - **Courses** (Course Code, Course Name, Credits, Instructor)
  - **Enrollments & Grades** (Attendance %, Attendance Marks, Exam Marks, Total, Grade)
* **Attendance Marks Weightage (Max 10 Marks)**:
  - $\ge 90\%$ attendance $\rightarrow$ 10 Marks
  - $85\% - 89\%$ $\rightarrow$ 8 Marks
  - $80\% - 84\%$ $\rightarrow$ 6 Marks
  - $75\% - 79\%$ $\rightarrow$ 4 Marks
  - $< 75\%$ $\rightarrow$ 0 Marks (Attendance Shortage Flagged)
* **Total Score (100)** = Exam Marks (90) + Attendance Marks (10)
* **Grade Assignment**: A+ ($\ge 90$), A ($\ge 80$), B ($\ge 70$), C ($\ge 60$), D ($\ge 50$), F ($< 50$)

### • View (`MainView.java`):
* Modern Java Swing graphical user interface with System Look-and-Feel.
* 4 Organized Tabbed Panels:
  1. **Students Management:** Form, live search filter, interactive JTable, and **View Student Report** button (styled green with hover effect).
  2. **Courses Management:** Form, live search filter, and interactive JTable.
  3. **Enrollments & Grading:** Unique Student and Course dropdowns, live enrolled courses summary, live preview, JTable with attendance color rendering.
  4. **Dashboard & Reports:** Metric KPI cards, Attendance Shortage Alert Table ($< 75\%$) with dynamic color-coding.

### • Controller (`AppController.java`):
* Mediates between View events and Model database calls.
* Asynchronous background execution (`SwingWorker`) for PDF generation and email dispatch to keep EDT responsive.
* Input validations and live error/success dialogs.
* Duplicate enrollment validation preventing duplicate student-course pairs.

---

## 2. Advanced Integrated Features

### FEATURE 1: Attendance-Based Dynamic Color Coding (`AttendanceCellRenderer.java`)
* Visual cell background representation based on attendance percentage:
  - **$\ge 75\%$**: Soft Green (`#D4EDDA`) – **SAFE**
  - **$50\% - 74.9\%$**: Pale Yellow (`#FFF3CD`) – **WARNING**
  - **$< 50\%$**: Soft Red (`#F8D7DA`) – **DANGER**
* High contrast: crisp, readable dark/black text across all shades.
* Seamless integration into:
  - Enrollments & Grading table (Attendance column)
  - Dashboard Attendance Shortage table
  - Individual Student Report course-wise table (Attendance % and Status columns)
* Preserves readability and provides a subtle selection tint when table rows are selected.

### FEATURE 2: Duplicate Prevention in Enrollments & Grading
* Student dropdown (`comboEnrollStudent`) displays each student **only once**, populated directly from unique student records.
* When a student is selected, their currently enrolled courses are displayed separately (`lblStudentEnrolledCourses`).
* Allows students to enroll in multiple distinct courses.
* Enforces duplicate prevention at both business and database levels:
  - Attempts to enroll a student in a course they are already taking displays the message:  
    `"This student is already enrolled in the selected course."`
  - Composite `UNIQUE(roll_no, course_code)` constraint and index in SQLite.
  - Safe editing of existing records without creating accidental duplicates.

### FEATURE 3: Comprehensive Individual Student Report (`StudentReportView.java` & `StudentReportData.java`)
* Dedicated **"View Student Report"** buttons (form panel and table header in Students Management).
* Styled with custom **`GradientButton`**: elegant Pistachio-to-Mint pastel gradient (`#C5DFCE` $\rightarrow$ `#A8D5BA`), dark forest-green text (`#244D36`), and smooth interactive states (Mint-Seafoam hover, deep Mint-Sage pressed, and soft light grey-green disabled).
* Displays a comprehensive dialog directly querying live SQLite records:
  - **A. Personal Information Card:** Full Name, Roll No, Email, Department, Phone, Registration No, Academic Group.
  - **B. Academic Performance KPIs:** Enrolled Courses, Overall Attendance %, Total Attendance Marks, Average Total Score & Letter Grade.
  - **C. Attendance Risk Banners:**
    - If overall attendance $< 50\%$ or any course $< 50\%$: **CRITICAL DANGER** alert.
    - If overall attendance $< 75\%$ or any course $< 75\%$: **ATTENDANCE SHORTAGE - STUDENT AT RISK** alert.
    - If attendance $\ge 75\%$: **SAFE** status banner.
  - **D. Course-Wise Performance Table:** Course Code, Name, Credits, Attendance %, Attendance Marks, Exam Marks, Total, Grade, Status with color-coding.
  - **E. Calculation Transparency:** Unweighted arithmetic average documented clearly.
  - **F. Actions:** Export Report as PDF, Print Report, Send Report via Email, Close.

### FEATURE 4: PDF Export & Email Delivery (`PDFReportGenerator.java` & `EmailService.java`)
* **Pure Java PDF Generator (`PDFReportGenerator.java`)**:
  - Generates standard, high-resolution vector PDF 1.4 documents.
  - Formatted layout matching GUI styling (header banner, student info card, KPI cards, colored course-wise performance table, attendance alert callout, footer).
  - Pure Java with **zero external PDF libraries required**.
* **Jakarta Mail Email Integration (`EmailService.java`)**:
  - Uses Jakarta Mail 2.0.1 with SMTP support (including Gmail).
  - Styled with custom **`GradientButton`**: **Send Report via Email** featuring the soft Pistachio-to-Mint gradient, dark forest-green text, and clear hover/click feedback.
  - Formats professional email with student name, roll number, attendance notice, and PDF attachment.
  - Requires user confirmation dialog showing student name, email, and attachment before sending.
  - Robust configuration priority:
    1. Checks environment variables (`SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASS`).
    2. Checks `smtp.properties` in project directory if environment variables are not set.
    3. Prompts helpful instructions dialog if unconfigured.
  - Handles invalid email syntax, connection errors, and missing credentials gracefully without crashing or leaking sensitive info.

---

## 3. External Dependencies & Libraries

All dependencies are included in the `lib/` directory and project root:

| Library | Version | File | Purpose |
|---|---|---|---|
| **SQLite JDBC Driver** | 3.53.2.1 | `lib/sqlite-jdbc-3.53.2.1.jar` | SQLite database connectivity |
| **Jakarta Mail** | 2.0.1 | `lib/jakarta.mail-2.0.1.jar` | SMTP email dispatch & MIME message building |
| **Jakarta Activation** | 2.0.1 | `lib/jakarta.activation-2.0.1.jar` | DataHandler & FileDataSource for PDF attachments |

### VS Code Java Classpath Configuration
To ensure VS Code's Java extension resolves all dependencies without import errors:
- The project includes `.vscode/settings.json` configured with `"java.project.referencedLibraries": ["lib/**/*.jar", "*.jar"]`.
- The project also includes Eclipse-compatible `.classpath` and `.project` files pointing to the libraries in `lib/`.

---

## 4. SMTP Configuration Instructions (Feature 4)

To enable email dispatch for academic reports, configure SMTP credentials:

### Priority 1: Environment Variables
Configure the following environment variables:
- `SMTP_HOST` (default: `smtp.gmail.com`)
- `SMTP_PORT` (default: `587`)
- `SMTP_USER` (e.g. `your_email@gmail.com`)
- `SMTP_PASS` (e.g. `your_16_digit_app_password`)

### Priority 2: Configuration File (`smtp.properties`)
If environment variables are not set, create a file named `smtp.properties` in the project root directory (a template `smtp.properties.example` is provided):

```properties
mail.smtp.host=smtp.gmail.com
mail.smtp.port=587
mail.smtp.auth=true
mail.smtp.starttls.enable=true
mail.smtp.user=your_email@gmail.com
mail.smtp.password=your_16_digit_app_password
mail.smtp.from=your_email@gmail.com
```

> **Security Note:** `smtp.properties` is listed in `.gitignore` to prevent committing credentials to version control.

### Generating a Gmail App Password
1. Go to your **Google Account Settings** $\rightarrow$ **Security** (enable 2-Step Verification if not already enabled).
2. Navigate to **App passwords** (`https://myaccount.google.com/apppasswords`).
3. Create a new app password (e.g. name: `Student Management System`).
4. Copy the generated 16-character code into `mail.smtp.password` or `SMTP_PASS`.

---

## 5. How to Compile and Run (Command Line)

Ensure JDK 17 or higher is installed and navigate to the project directory:

### A. Compilation Command:

* **Windows (CMD / PowerShell):**
  ```powershell
  javac -cp ".;lib/*" *.java
  ```
  *(Or with root JARs)*:
  ```powershell
  javac -cp ".;*" *.java
  ```

* **macOS / Linux:**
  ```bash
  javac -cp ".:lib/*" *.java
  ```
  *(Or with root JARs)*:
  ```bash
  javac -cp ".:*" *.java
  ```

### B. Execution Command:

* **Windows (CMD / PowerShell):**
  ```powershell
  java -cp ".;lib/*" Main
  ```
  *(Or with wildcard)*:
  ```powershell
  java -cp ".;*" Main
  ```

* **macOS / Linux:**
  ```bash
  java -cp ".:lib/*" Main
  ```
  *(Or with wildcard)*:
  ```bash
  java -cp ".:*" Main
  ```

### C. Running Automated Integration Tests:
To run the automated test suite verifying attendance coloring, duplicate prevention, and report generation:

* **Windows (CMD / PowerShell):**
  ```powershell
  java -cp ".;lib/*" SystemIntegrationTest
  ```
