import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;

/**
 * VIEW: Handles the graphical user interface (Java Swing)
 * Displays forms, tables, buttons, tabs, and user dialogs.
 */
public class MainView extends JFrame {

    // --- TAB 1: STUDENTS ---
    public JTextField txtStudRoll = new JTextField(15);
    public JTextField txtStudName = new JTextField(15);
    public JTextField txtStudEmail = new JTextField(15);
    public JTextField txtStudDept = new JTextField(15);
    public JTextField txtStudPhone = new JTextField(15);
    public JLabel lblStudPhoneHint = new JLabel("Must be a 10-digit number");
    public JTextField txtStudSearch = new JTextField(15);

    public JButton btnStudAdd = new JButton("Add Student");
    public JButton btnStudUpdate = new JButton("Update Selected");
    public JButton btnStudDelete = new JButton("Delete Selected");
    public JButton btnStudClear = new JButton("Clear Form");

    public DefaultTableModel studTableModel;
    public JTable studTable;
    public TableRowSorter<DefaultTableModel> studSorter;

    // --- TAB 2: COURSES ---
    public JTextField txtCourseCode = new JTextField(15);
    public JTextField txtCourseName = new JTextField(15);
    public JTextField txtCourseCredits = new JTextField(15);
    public JTextField txtCourseInstructor = new JTextField(15);
    public JTextField txtCourseSearch = new JTextField(15);

    public JButton btnCourseAdd = new JButton("Add Course");
    public JButton btnCourseUpdate = new JButton("Update Selected");
    public JButton btnCourseDelete = new JButton("Delete Selected");
    public JButton btnCourseClear = new JButton("Clear Form");

    public DefaultTableModel courseTableModel;
    public JTable courseTable;
    public TableRowSorter<DefaultTableModel> courseSorter;

    // --- TAB 3: ENROLLMENTS & GRADES ---
    public JComboBox<String> comboEnrollStudent = new JComboBox<>();
    public JComboBox<String> comboEnrollCourse = new JComboBox<>();
    public JTextField txtAttendancePct = new JTextField(10);
    public JTextField txtExamMarks = new JTextField(10);
    public JLabel lblCalculatedPreview = new JLabel("Preview: Attendance Marks: - | Total: - | Grade: -");
    public JTextField txtEnrollSearch = new JTextField(15);

    public JButton btnEnrollSave = new JButton("Save / Update Grade");
    public JButton btnEnrollDelete = new JButton("Delete Enrollment");
    public JButton btnEnrollClear = new JButton("Clear Form");

    public DefaultTableModel enrollTableModel;
    public JTable enrollTable;
    public TableRowSorter<DefaultTableModel> enrollSorter;

    // --- TAB 4: DASHBOARD & ALERTS ---
    public JLabel lblStatStudents = new JLabel("0", SwingConstants.CENTER);
    public JLabel lblStatCourses = new JLabel("0", SwingConstants.CENTER);
    public JLabel lblStatEnrollments = new JLabel("0", SwingConstants.CENTER);
    public JLabel lblStatAvgScore = new JLabel("0.0", SwingConstants.CENTER);
    public DefaultTableModel shortageTableModel;
    public JTable shortageTable;
    public JButton btnRefreshDashboard = new JButton("Refresh Dashboard");

    public MainView() {
        super("Student Management System (Java Swing + SQLite JDBC)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 720);
        setLocationRelativeTo(null);

        // Main Layout
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(12, 12, 12, 12));

        // Top Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(24, 43, 73));
        headerPanel.setBorder(new EmptyBorder(14, 18, 14, 18));

        JLabel titleLabel = new JLabel("Student Management System");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titleLabel.setForeground(Color.WHITE);

        JLabel subtitleLabel = new JLabel("MVC Architecture | Relational SQLite Database | Attendance-Weighted Grading");
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitleLabel.setForeground(new Color(200, 220, 240));

        headerPanel.add(titleLabel, BorderLayout.NORTH);
        headerPanel.add(subtitleLabel, BorderLayout.SOUTH);
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Tabbed Pane
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 13));
        tabbedPane.addTab("  Students  ", createStudentsTab());
        tabbedPane.addTab("  Courses  ", createCoursesTab());
        tabbedPane.addTab("  Enrollments & Grading  ", createEnrollmentsTab());
        tabbedPane.addTab("  Dashboard & Reports  ", createDashboardTab());

        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        add(mainPanel);
    }

    // ==========================================
    // STUDENTS TAB
    // ==========================================
    private JPanel createStudentsTab() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Form Panel (Left)
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Student Information",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 14)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        addFormRow(formPanel, gbc, row++, "Roll Number *:", txtStudRoll);
        addFormRow(formPanel, gbc, row++, "Full Name *:", txtStudName);
        addFormRow(formPanel, gbc, row++, "Email Address:", txtStudEmail);
        addFormRow(formPanel, gbc, row++, "Department:", txtStudDept);
        txtStudPhone.setToolTipText("Enter a 10-digit phone number (numbers only)");
        lblStudPhoneHint.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        lblStudPhoneHint.setForeground(new Color(110, 110, 110));

        JPanel phoneContainer = new JPanel(new BorderLayout(0, 3));
        phoneContainer.setOpaque(false);
        phoneContainer.add(txtStudPhone, BorderLayout.CENTER);
        phoneContainer.add(lblStudPhoneHint, BorderLayout.SOUTH);

        addFormRow(formPanel, gbc, row++, "Phone (10 digits) *:", phoneContainer);

        // Buttons
        JPanel btnPanel = new JPanel(new GridLayout(2, 2, 8, 8));
        btnPanel.add(btnStudAdd);
        btnPanel.add(btnStudUpdate);
        btnPanel.add(btnStudDelete);
        btnPanel.add(btnStudClear);

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(16, 6, 6, 6);
        formPanel.add(btnPanel, gbc);

        // Table Panel (Right)
        JPanel tablePanel = new JPanel(new BorderLayout(8, 8));
        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        searchBar.add(new JLabel("Search Students:"));
        searchBar.add(txtStudSearch);

        studTableModel = new DefaultTableModel(new String[]{"Roll No", "Name", "Email", "Department", "Phone"}, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        studTable = new JTable(studTableModel);
        studTable.setRowHeight(24);
        studTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        studSorter = new TableRowSorter<>(studTableModel);
        studTable.setRowSorter(studSorter);

        tablePanel.add(searchBar, BorderLayout.NORTH);
        tablePanel.add(new JScrollPane(studTable), BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, formPanel, tablePanel);
        splitPane.setDividerLocation(340);
        panel.add(splitPane, BorderLayout.CENTER);
        return panel;
    }

    // ==========================================
    // COURSES TAB
    // ==========================================
    private JPanel createCoursesTab() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Course Details",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 14)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        addFormRow(formPanel, gbc, row++, "Course Code *:", txtCourseCode);
        addFormRow(formPanel, gbc, row++, "Course Name *:", txtCourseName);
        addFormRow(formPanel, gbc, row++, "Credits (1-5) *:", txtCourseCredits);
        addFormRow(formPanel, gbc, row++, "Instructor:", txtCourseInstructor);

        JPanel buttonContainer = new JPanel(new GridLayout(2, 2, 8, 8));
        buttonContainer.add(btnCourseAdd);
        buttonContainer.add(btnCourseUpdate);
        buttonContainer.add(btnCourseDelete);
        buttonContainer.add(btnCourseClear);

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(16, 6, 6, 6);
        formPanel.add(buttonContainer, gbc);

        // Table
        JPanel tablePanel = new JPanel(new BorderLayout(8, 8));
        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        searchBar.add(new JLabel("Search Courses:"));
        searchBar.add(txtCourseSearch);

        courseTableModel = new DefaultTableModel(new String[]{"Course Code", "Course Name", "Credits", "Instructor"}, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        courseTable = new JTable(courseTableModel);
        courseTable.setRowHeight(24);
        courseTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        courseSorter = new TableRowSorter<>(courseTableModel);
        courseTable.setRowSorter(courseSorter);

        tablePanel.add(searchBar, BorderLayout.NORTH);
        tablePanel.add(new JScrollPane(courseTable), BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, formPanel, tablePanel);
        splitPane.setDividerLocation(340);
        panel.add(splitPane, BorderLayout.CENTER);
        return panel;
    }

    // ==========================================
    // ENROLLMENTS & GRADING TAB
    // ==========================================
    private JPanel createEnrollmentsTab() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Enrollment & Marks Entry",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 14)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        addFormRow(formPanel, gbc, row++, "Select Student *:", comboEnrollStudent);
        addFormRow(formPanel, gbc, row++, "Select Course *:", comboEnrollCourse);
        addFormRow(formPanel, gbc, row++, "Attendance % (0-100) *:", txtAttendancePct);
        addFormRow(formPanel, gbc, row++, "Exam Marks (0-90) *:", txtExamMarks);

        // Attendance Info Notice
        gbc.gridx = 0;
        gbc.gridy = row++;
        gbc.gridwidth = 2;
        JLabel note = new JLabel("<html><small style='color: #444;'>"
                + "ℹ <b>Grading Formula:</b> Total (100) = Exam (90) + Attendance (10)<br>"
                + "Attendance Criteria: &ge;90%=10m, 85%=8m, 80%=6m, 75%=4m, &lt;75%=0m</small></html>");
        formPanel.add(note, gbc);

        // Live calculation preview
        gbc.gridx = 0;
        gbc.gridy = row++;
        gbc.gridwidth = 2;
        lblCalculatedPreview.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblCalculatedPreview.setForeground(new Color(20, 90, 160));
        formPanel.add(lblCalculatedPreview, gbc);

        // Buttons
        JPanel btnPanel = new JPanel(new GridLayout(2, 2, 8, 8));
        btnPanel.add(btnEnrollSave);
        btnPanel.add(btnEnrollDelete);
        btnPanel.add(btnEnrollClear);

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(12, 6, 6, 6);
        formPanel.add(btnPanel, gbc);

        // Table
        JPanel tablePanel = new JPanel(new BorderLayout(8, 8));
        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        searchBar.add(new JLabel("Search Enrollments:"));
        searchBar.add(txtEnrollSearch);

        enrollTableModel = new DefaultTableModel(new String[]{
                "ID", "Roll No", "Student Name", "Course Code", "Course Name",
                "Attendance", "Att. Marks", "Exam Marks", "Total", "Grade"
        }, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        enrollTable = new JTable(enrollTableModel);
        enrollTable.setRowHeight(24);
        enrollTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        enrollSorter = new TableRowSorter<>(enrollTableModel);
        enrollTable.setRowSorter(enrollSorter);

        tablePanel.add(searchBar, BorderLayout.NORTH);
        tablePanel.add(new JScrollPane(enrollTable), BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, formPanel, tablePanel);
        splitPane.setDividerLocation(380);
        panel.add(splitPane, BorderLayout.CENTER);
        return panel;
    }

    // ==========================================
    // DASHBOARD & REPORTS TAB
    // ==========================================
    private JPanel createDashboardTab() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));

        // Metric Cards Panel
        JPanel metricsPanel = new JPanel(new GridLayout(1, 4, 14, 14));
        metricsPanel.add(createStatCard("Total Students", lblStatStudents, new Color(41, 128, 185)));
        metricsPanel.add(createStatCard("Total Courses", lblStatCourses, new Color(39, 174, 96)));
        metricsPanel.add(createStatCard("Total Enrollments", lblStatEnrollments, new Color(142, 68, 173)));
        metricsPanel.add(createStatCard("Avg Total Score", lblStatAvgScore, new Color(211, 84, 0)));

        panel.add(metricsPanel, BorderLayout.NORTH);

        // Lower: Attendance shortage alert table (<75%)
        JPanel alertPanel = new JPanel(new BorderLayout(8, 8));
        alertPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "⚠ Attendance Shortage Alert (< 75% Attendance)",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 14), new Color(180, 40, 40)));

        shortageTableModel = new DefaultTableModel(new String[]{
                "Roll No", "Student Name", "Course Code", "Course Name", "Attendance %"
        }, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        shortageTable = new JTable(shortageTableModel);
        shortageTable.setRowHeight(24);

        JPanel bottomBtnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomBtnBar.add(btnRefreshDashboard);

        alertPanel.add(new JScrollPane(shortageTable), BorderLayout.CENTER);
        alertPanel.add(bottomBtnBar, BorderLayout.SOUTH);

        panel.add(alertPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createStatCard(String title, JLabel valueLabel, Color barColor) {
        JPanel card = new JPanel(new BorderLayout(6, 6));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 220, 220), 1),
                new EmptyBorder(14, 16, 14, 16)
        ));

        JLabel titleLbl = new JLabel(title, SwingConstants.CENTER);
        titleLbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        titleLbl.setForeground(Color.DARK_GRAY);

        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        valueLabel.setForeground(barColor);

        card.add(titleLbl, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private void addFormRow(JPanel panel, GridBagConstraints gbc, int row, String label, JComponent field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0.3;
        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        panel.add(lbl, gbc);

        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.weightx = 0.7;
        panel.add(field, gbc);
    }

    public void showMessage(String message, String title, int messageType) {
        JOptionPane.showMessageDialog(this, message, title, messageType);
    }

    public boolean confirmDialog(String message, String title) {
        int result = JOptionPane.showConfirmDialog(this, message, title, JOptionPane.YES_NO_OPTION);
        return result == JOptionPane.YES_OPTION;
    }
}
