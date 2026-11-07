import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.print.PrinterException;

/**
 * VIEW: Professional window displaying the comprehensive individual student
 * report.
 * Includes student personal information, academic KPIs, attendance danger
 * alerts,
 * course-wise performance table with color coding, and export/print/email
 * actions.
 */
public class StudentReportView extends JDialog {

    private final StudentReportData reportData;

    // Action Buttons
    public final JButton btnExportPdf = new JButton("Export Report as PDF");
    public final JButton btnPrint = new JButton("Print Report");
    public final JButton btnSendEmail = new JButton("Send Report via Email");
    public final JButton btnClose = new JButton("Close Report");

    public JTable tableCourses;
    public DefaultTableModel modelCourses;

    public StudentReportView(Frame parent, StudentReportData data) {
        super(parent, "Comprehensive Academic Performance Report - " + data.getName() + " (" + data.getRollNo() + ")",
                true);
        this.reportData = data;

        setSize(1000, 750);
        setMinimumSize(new Dimension(850, 600));
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        initUI();
    }

    private void initUI() {
        JPanel contentPanel = new JPanel(new BorderLayout(0, 10));
        contentPanel.setBackground(new Color(245, 247, 250));

        // 1. Header Banner
        contentPanel.add(createHeaderPanel(), BorderLayout.NORTH);

        // 2. Scrollable Body
        JPanel bodyPanel = new JPanel();
        bodyPanel.setLayout(new BoxLayout(bodyPanel, BoxLayout.Y_AXIS));
        bodyPanel.setBackground(new Color(245, 247, 250));
        bodyPanel.setBorder(new EmptyBorder(12, 16, 12, 16));

        // Section A: Personal Information
        bodyPanel.add(createPersonalInfoPanel());
        bodyPanel.add(Box.createVerticalStrut(12));

        // Section B: Academic Performance Overview KPIs
        bodyPanel.add(createKpiPanel());
        bodyPanel.add(Box.createVerticalStrut(12));

        // Section E: Attendance Danger / Shortage Alert
        bodyPanel.add(createAttendanceAlertPanel());
        bodyPanel.add(Box.createVerticalStrut(12));

        // Section C: Course-Wise Performance Details Table
        bodyPanel.add(createCourseTablePanel());
        bodyPanel.add(Box.createVerticalStrut(12));

        // Section D: Policy Footnote
        bodyPanel.add(createPolicyNotePanel());

        JScrollPane scrollPane = new JScrollPane(bodyPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        contentPanel.add(scrollPane, BorderLayout.CENTER);

        // 3. Bottom Button Bar
        contentPanel.add(createBottomButtonBar(), BorderLayout.SOUTH);

        setContentPane(contentPanel);

        // Wire Close Button
        btnClose.addActionListener(e -> dispose());

        // Wire Print Button
        btnPrint.addActionListener(e -> handlePrint());
    }

    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(24, 43, 73));
        header.setBorder(new EmptyBorder(16, 20, 16, 20));

        JLabel title = new JLabel("STUDENT ACADEMIC PERFORMANCE REPORT");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(Color.WHITE);

        JLabel subtitle = new JLabel("Office of the Academic Registrar | Relational SQLite MVC Architecture");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitle.setForeground(new Color(200, 225, 250));

        JPanel leftBox = new JPanel(new GridLayout(2, 1, 2, 2));
        leftBox.setOpaque(false);
        leftBox.add(title);
        leftBox.add(subtitle);

        JLabel timeLabel = new JLabel("<html><div style='text-align: right;'>Generated on:<br><b>"
                + reportData.getGeneratedTimestamp() + "</b></div></html>");
        timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        timeLabel.setForeground(new Color(220, 235, 255));

        header.add(leftBox, BorderLayout.WEST);
        header.add(timeLabel, BorderLayout.EAST);
        return header;
    }

    private JPanel createPersonalInfoPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 3, 14, 10));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(new Color(200, 210, 225)),
                        "  1. Student Personal Information  ",
                        TitledBorder.LEFT, TitledBorder.TOP,
                        new Font("Segoe UI", Font.BOLD, 13), new Color(24, 43, 73)),
                new EmptyBorder(10, 14, 12, 14)));

        panel.add(createInfoItem("Full Name", reportData.getName()));
        panel.add(createInfoItem("Roll Number", String.valueOf(reportData.getRollNo())));
        panel.add(createInfoItem("Registration No", reportData.getRegistrationNo()));

        panel.add(createInfoItem("Department / Stream", reportData.getDepartment()));
        panel.add(createInfoItem("Email Address",
                reportData.getEmail().isEmpty() ? "Not Registered" : reportData.getEmail()));
        panel.add(createInfoItem("Contact Number",
                reportData.getPhone().isEmpty() ? "Not Available" : reportData.getPhone()));

        return panel;
    }

    private JPanel createInfoItem(String label, String value) {
        JPanel item = new JPanel(new BorderLayout(2, 2));
        item.setOpaque(false);

        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lbl.setForeground(new Color(100, 110, 120));

        JLabel val = new JLabel(value);
        val.setFont(new Font("Segoe UI", Font.BOLD, 13));
        val.setForeground(new Color(30, 30, 30));

        item.add(lbl, BorderLayout.NORTH);
        item.add(val, BorderLayout.CENTER);
        return item;
    }

    private JPanel createKpiPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 4, 12, 12));
        panel.setOpaque(false);

        // 1. Total Courses
        panel.add(createKpiCard("Enrolled Courses", String.valueOf(reportData.getTotalCourses()) + " Courses",
                new Color(41, 128, 185), "Active courses enrolled"));

        // 2. Overall Attendance %
        Color attColor;
        if (reportData.getOverallAttendancePct() >= 75.0) {
            attColor = new Color(39, 174, 96);
        } else if (reportData.getOverallAttendancePct() >= 50.0) {
            attColor = new Color(230, 126, 34);
        } else {
            attColor = new Color(211, 47, 47);
        }
        panel.add(createKpiCard("Overall Attendance",
                String.format("%.1f%%", reportData.getOverallAttendancePct()),
                attColor, "Status: " + reportData.getOverallStatus()));

        // 3. Total Attendance Marks
        String attMarksText = String.format("%.1f / %.0f", reportData.getTotalAttendanceMarks(),
                reportData.getTotalMaxAttendanceMarks());
        panel.add(createKpiCard("Attendance Marks", attMarksText,
                new Color(142, 68, 173), "Max 10 marks per course"));

        // 4. Overall Average Score & Grade
        String scoreText = String.format("%.1f / 100 (%s)", reportData.getOverallAverageScore(),
                reportData.getOverallGrade());
        panel.add(createKpiCard("Overall Performance", scoreText,
                new Color(211, 84, 0), "Grade: " + reportData.getOverallGrade()));

        return panel;
    }

    private JPanel createKpiCard(String title, String mainValue, Color accentColor, String subtext) {
        JPanel card = new JPanel(new BorderLayout(4, 4));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(3, 0, 0, 0, accentColor),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(220, 225, 235), 1),
                        new EmptyBorder(10, 12, 10, 12))));

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblTitle.setForeground(new Color(90, 100, 110));

        JLabel lblValue = new JLabel(mainValue);
        lblValue.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblValue.setForeground(accentColor);

        JLabel lblSub = new JLabel(subtext);
        lblSub.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        lblSub.setForeground(new Color(130, 140, 150));

        card.add(lblTitle, BorderLayout.NORTH);
        card.add(lblValue, BorderLayout.CENTER);
        card.add(lblSub, BorderLayout.SOUTH);
        return card;
    }

    private JPanel createAttendanceAlertPanel() {
        JPanel alertPanel = new JPanel(new BorderLayout(10, 6));
        alertPanel.setBorder(new EmptyBorder(10, 14, 10, 14));

        double pct = reportData.getOverallAttendancePct();
        int shortageCount = reportData.getShortageCount();
        int dangerCount = reportData.getDangerCount();

        if (pct < 50.0 || dangerCount > 0) {
            // DANGER ALERT (< 50%)
            alertPanel.setBackground(new Color(248, 215, 218));
            alertPanel.setBorder(new CompoundBorder(
                    new LineBorder(new Color(220, 150, 155), 1, true),
                    new EmptyBorder(10, 14, 10, 14)));

            JLabel title = new JLabel("CRITICAL ATTENDANCE DANGER (< 50%) - STUDENT AT SEVERE RISK");
            title.setFont(new Font("Segoe UI", Font.BOLD, 13));
            title.setForeground(new Color(130, 20, 30));

            JLabel desc = new JLabel(String.format(
                    "<html>Student's overall attendance is <b>%.1f%%</b>, with <b>%d</b> course(s) having critical attendance below 50%%. "
                            + "The student faces mandatory debarment from end-semester examinations unless urgent institutional regularization is approved.</html>",
                    pct, dangerCount));
            desc.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            desc.setForeground(new Color(80, 15, 20));

            alertPanel.add(title, BorderLayout.NORTH);
            alertPanel.add(desc, BorderLayout.CENTER);

        } else if (pct < 75.0 || shortageCount > 0) {
            // SHORTAGE ALERT (< 75%)
            alertPanel.setBackground(new Color(255, 243, 205));
            alertPanel.setBorder(new CompoundBorder(
                    new LineBorder(new Color(230, 200, 120), 1, true),
                    new EmptyBorder(10, 14, 10, 14)));

            JLabel title = new JLabel("ATTENDANCE SHORTAGE - STUDENT AT RISK (< 75% Attendance)");
            title.setFont(new Font("Segoe UI", Font.BOLD, 13));
            title.setForeground(new Color(133, 100, 4));

            JLabel desc = new JLabel(String.format(
                    "<html>Student's overall attendance is <b>%.1f%%</b>, with <b>%d</b> course(s) below the mandatory 75%% attendance threshold. "
                            + "Zero attendance marks are awarded for courses below 75%%. Remedial classes are advised to avoid examination shortage penalties.</html>",
                    pct, shortageCount));
            desc.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            desc.setForeground(new Color(100, 75, 5));

            alertPanel.add(title, BorderLayout.NORTH);
            alertPanel.add(desc, BorderLayout.CENTER);

        } else {
            // SAFE (>= 75%)
            alertPanel.setBackground(new Color(212, 237, 218));
            alertPanel.setBorder(new CompoundBorder(
                    new LineBorder(new Color(170, 215, 180), 1, true),
                    new EmptyBorder(10, 14, 10, 14)));

            JLabel title = new JLabel("ATTENDANCE STATUS: SAFE (>= 75%)");
            title.setFont(new Font("Segoe UI", Font.BOLD, 13));
            title.setForeground(new Color(21, 87, 36));

            JLabel desc = new JLabel(String.format(
                    "<html>Student has maintained a satisfactory overall attendance record of <b>%.1f%%</b> across all enrolled courses. Meets all institutional requirements.</html>",
                    pct));
            desc.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            desc.setForeground(new Color(25, 75, 35));

            alertPanel.add(title, BorderLayout.NORTH);
            alertPanel.add(desc, BorderLayout.CENTER);
        }

        return alertPanel;
    }

    private JPanel createCourseTablePanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(new Color(200, 210, 225)),
                        "  3. Course-Wise Academic & Attendance Details  ",
                        TitledBorder.LEFT, TitledBorder.TOP,
                        new Font("Segoe UI", Font.BOLD, 13), new Color(24, 43, 73)),
                new EmptyBorder(8, 10, 8, 10)));

        String[] headers = {
                "Course Code", "Course Name", "Credits", "Attendance %",
                "Attendance Marks", "Exam Marks", "Total Marks", "Grade", "Status"
        };

        modelCourses = new DefaultTableModel(headers, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        for (StudentReportData.CoursePerformance cp : reportData.getCourses()) {
            modelCourses.addRow(new Object[] {
                    cp.getCourseCode(),
                    cp.getCourseName(),
                    cp.getCredits(),
                    String.format("%.1f%%", cp.getAttendancePct()),
                    String.format("%.1f / 10", cp.getAttendanceMarks()),
                    String.format("%.1f / 90", cp.getExamMarks()),
                    String.format("%.1f / 100", cp.getTotalMarks()),
                    cp.getGrade(),
                    cp.getStatus()
            });
        }

        tableCourses = new JTable(modelCourses);
        tableCourses.setRowHeight(26);
        tableCourses.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        tableCourses.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        tableCourses.getTableHeader().setBackground(new Color(240, 243, 248));
        tableCourses.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Center align numerical columns
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        tableCourses.getColumnModel().getColumn(0).setCellRenderer(centerRenderer); // Code
        tableCourses.getColumnModel().getColumn(2).setCellRenderer(centerRenderer); // Credits
        tableCourses.getColumnModel().getColumn(4).setCellRenderer(centerRenderer); // Att Marks
        tableCourses.getColumnModel().getColumn(5).setCellRenderer(centerRenderer); // Exam Marks
        tableCourses.getColumnModel().getColumn(6).setCellRenderer(centerRenderer); // Total Marks
        tableCourses.getColumnModel().getColumn(7).setCellRenderer(centerRenderer); // Grade

        // FEATURE 1: Custom Attendance color-coding renderer applied to Attendance %
        // (col 3) and Status (col 8)
        AttendanceCellRenderer attRenderer = new AttendanceCellRenderer();
        tableCourses.getColumnModel().getColumn(3).setCellRenderer(attRenderer);
        tableCourses.getColumnModel().getColumn(8).setCellRenderer(attRenderer);

        // Set column widths
        tableCourses.getColumnModel().getColumn(0).setPreferredWidth(90);
        tableCourses.getColumnModel().getColumn(1).setPreferredWidth(240);
        tableCourses.getColumnModel().getColumn(2).setPreferredWidth(60);
        tableCourses.getColumnModel().getColumn(3).setPreferredWidth(100);
        tableCourses.getColumnModel().getColumn(4).setPreferredWidth(110);
        tableCourses.getColumnModel().getColumn(5).setPreferredWidth(90);
        tableCourses.getColumnModel().getColumn(6).setPreferredWidth(90);
        tableCourses.getColumnModel().getColumn(7).setPreferredWidth(60);
        tableCourses.getColumnModel().getColumn(8).setPreferredWidth(90);

        JScrollPane tableScroll = new JScrollPane(tableCourses);
        tableScroll.setPreferredSize(
                new Dimension(800, Math.min(220, Math.max(100, (reportData.getTotalCourses() + 2) * 28))));
        panel.add(tableScroll, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createPolicyNotePanel() {
        JPanel panel = new JPanel(new GridLayout(3, 1, 2, 2));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(4, 4, 4, 4));

        JLabel l1 = new JLabel(
                "Grading Policy: Total Score (100) = Exam Marks (90) + Attendance Marks (10). Letter Grades: A+ (>=90), A (>=80), B (>=70), C (>=60), D (>=50), F (<50).");
        l1.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        l1.setForeground(new Color(100, 110, 120));

        JLabel l2 = new JLabel(
                "Attendance Marks Weightage: >=90% -> 10 marks | 85-89% -> 8 marks | 80-84% -> 6 marks | 75-79% -> 4 marks | <75% -> 0 marks (Shortage Flagged).");
        l2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        l2.setForeground(new Color(100, 110, 120));

        JLabel l3 = new JLabel(
                "* Calculation Note: Student's overall attendance is calculated as the unweighted arithmetic mean of attendance percentages across enrolled courses.");
        l3.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        l3.setForeground(new Color(110, 120, 130));

        panel.add(l1);
        panel.add(l2);
        panel.add(l3);
        return panel;
    }

    private JPanel createBottomButtonBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        bar.setBackground(Color.WHITE);
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 225, 235)));

        // Style buttons with explicit high-contrast colors and hover feedback
        styleSecondaryButton(btnExportPdf);
        styleSecondaryButton(btnPrint);
        stylePrimaryGreenButton(btnSendEmail);
        styleSecondaryButton(btnClose);

        bar.add(btnExportPdf);
        bar.add(btnPrint);
        bar.add(btnSendEmail);
        bar.add(btnClose);
        return bar;
    }

    private void handlePrint() {
        try {
            boolean complete = tableCourses.print(
                    JTable.PrintMode.FIT_WIDTH,
                    new java.text.MessageFormat(
                            "Academic Report: " + reportData.getName() + " (" + reportData.getRollNo() + ")"),
                    new java.text.MessageFormat("Page - {0} | Student Management System"));
            if (complete) {
                JOptionPane.showMessageDialog(this, "Report printed successfully.", "Print",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (PrinterException ex) {
            JOptionPane.showMessageDialog(this, "Print error: " + ex.getMessage(), "Print Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Styles Send Report via Email button with green theme, hover effect, and white
     * bold text.
     */
    public static void stylePrimaryGreenButton(JButton btn) {
        Color normalBg = new Color(25, 135, 84); // #198754
        Color hoverBg = new Color(20, 108, 67); // #146c43
        Color pressedBg = new Color(15, 81, 50);

        btn.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        btn.setBackground(normalBg);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setOpaque(true);
        btn.setContentAreaFilled(true);
        btn.setFocusPainted(false);
        btn.setBorderPainted(true);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(20, 108, 67), 1),
                BorderFactory.createEmptyBorder(7, 16, 7, 16)));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                if (btn.isEnabled()) {
                    btn.setBackground(hoverBg);
                }
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                if (btn.isEnabled()) {
                    btn.setBackground(normalBg);
                }
            }

            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (btn.isEnabled()) {
                    btn.setBackground(pressedBg);
                }
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                if (btn.isEnabled()) {
                    btn.setBackground(btn.contains(e.getPoint()) ? hoverBg : normalBg);
                }
            }
        });
    }

    /**
     * Styles secondary action buttons with subtle border, hover effect, and dark
     * readable text.
     */
    public static void styleSecondaryButton(JButton btn) {
        Color normalBg = new Color(245, 247, 250);
        Color hoverBg = new Color(230, 235, 242);
        Color pressedBg = new Color(215, 222, 232);
        Color textCol = new Color(33, 37, 41);

        btn.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        btn.setBackground(normalBg);
        btn.setForeground(textCol);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setOpaque(true);
        btn.setContentAreaFilled(true);
        btn.setFocusPainted(false);
        btn.setBorderPainted(true);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(205, 212, 220), 1),
                BorderFactory.createEmptyBorder(7, 14, 7, 14)));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                if (btn.isEnabled()) {
                    btn.setBackground(hoverBg);
                }
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                if (btn.isEnabled()) {
                    btn.setBackground(normalBg);
                }
            }

            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (btn.isEnabled()) {
                    btn.setBackground(pressedBg);
                }
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                if (btn.isEnabled()) {
                    btn.setBackground(btn.contains(e.getPoint()) ? hoverBg : normalBg);
                }
            }
        });
    }

    public StudentReportData getReportData() {
        return reportData;
    }
}
