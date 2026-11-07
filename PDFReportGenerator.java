import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure Java PDF 1.4 Report Generator.
 * Generates clean, professional, standards-compliant PDF documents
 * without any external library dependencies.
 */
public class PDFReportGenerator {

    /**
     * Generates a PDF report file for the given student data.
     */
    public static void generateReport(StudentReportData data, File outputFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(outputFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {
            writePdfDocument(data, bos);
        }
    }

    /**
     * Internal class to buffer PDF objects and track exact byte offsets.
     */
    private static class PdfWriter {
        private final OutputStream out;
        private int byteCount = 0;
        private final List<Integer> offsets = new ArrayList<>();

        public PdfWriter(OutputStream out) {
            this.out = out;
        }

        public void writeBytes(byte[] b) throws IOException {
            out.write(b);
            byteCount += b.length;
        }

        public void writeString(String s) throws IOException {
            writeBytes(s.getBytes(StandardCharsets.ISO_8859_1));
        }

        public int startObject(int objNum) throws IOException {
            while (offsets.size() < objNum) {
                offsets.add(0);
            }
            offsets.set(objNum - 1, byteCount);
            writeString(objNum + " 0 obj\n");
            return objNum;
        }

        public void endObject() throws IOException {
            writeString("endobj\n");
        }

        public void writeStream(byte[] streamBytes) throws IOException {
            writeString("<< /Length " + streamBytes.length + " >>\nstream\n");
            writeBytes(streamBytes);
            writeString("\nendstream\n");
        }

        public void writeXrefAndTrailer(int catalogObj, int infoObj) throws IOException {
            int startXref = byteCount;
            int totalObjs = offsets.size() + 1;

            writeString("xref\n0 " + totalObjs + "\n");
            writeString("0000000000 65535 f \n");
            for (int offset : offsets) {
                writeString(String.format("%010d 00000 n \n", offset));
            }

            writeString("trailer\n<< /Size " + totalObjs + " /Root " + catalogObj + " 0 R /Info " + infoObj + " 0 R >>\n");
            writeString("startxref\n" + startXref + "\n%%EOF\n");
        }
    }

    /**
     * Writes the complete PDF document.
     */
    private static void writePdfDocument(StudentReportData data, OutputStream out) throws IOException {
        PdfWriter writer = new PdfWriter(out);

        // 1. Header
        writer.writeString("%PDF-1.4\n%\u00e2\u00e3\u00cf\u00d3\n");

        // Prepare page content stream
        byte[] contentBytes = buildPageContent(data);

        // Catalog: Obj 1
        writer.startObject(1);
        writer.writeString("<< /Type /Catalog /Pages 2 0 R >>\n");
        writer.endObject();

        // Pages: Obj 2
        writer.startObject(2);
        writer.writeString("<< /Type /Pages /Kids [3 0 R] /Count 1 >>\n");
        writer.endObject();

        // Page: Obj 3
        writer.startObject(3);
        writer.writeString("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                + "/Contents 4 0 R /Resources << /Font << "
                + "/F1 5 0 R /F2 6 0 R /F3 7 0 R "
                + ">> >> >>\n");
        writer.endObject();

        // Content Stream: Obj 4
        writer.startObject(4);
        writer.writeStream(contentBytes);
        writer.endObject();

        // Standard 14 Fonts (built-in to all PDF viewers)
        // Font F1: Helvetica
        writer.startObject(5);
        writer.writeString("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\n");
        writer.endObject();

        // Font F2: Helvetica-Bold
        writer.startObject(6);
        writer.writeString("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>\n");
        writer.endObject();

        // Font F3: Helvetica-Oblique
        writer.startObject(7);
        writer.writeString("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Oblique >>\n");
        writer.endObject();

        // Info dictionary: Obj 8
        writer.startObject(8);
        String escapedTitle = escapePdfString("Academic Report - " + data.getName() + " (" + data.getRollNo() + ")");
        writer.writeString("<< /Title (" + escapedTitle + ") "
                + "/Author (Student Management System) "
                + "/Creator (Student Management System - Advanced Feature Integration) "
                + "/Subject (Comprehensive Academic Performance Report) >>\n");
        writer.endObject();

        // Xref & Trailer
        writer.writeXrefAndTrailer(1, 8);
    }

    /**
     * Builds the graphic and text operations for the PDF page.
     */
    private static byte[] buildPageContent(StudentReportData data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        StringBuilder sb = new StringBuilder();

        // --- TOP BANNER (Height: 65, Y: 727 to 792) ---
        // Navy blue background
        sb.append("0.094 0.168 0.286 rg\n"); // #182B49
        sb.append("0 720 612 72 re f\n");

        // Header Title
        sb.append("BT\n");
        sb.append("/F2 18 Tf\n");
        sb.append("1 1 1 rg\n"); // White
        sb.append("36 756 Td\n");
        sb.append("(").append(escapePdfString("STUDENT ACADEMIC PERFORMANCE REPORT")).append(") Tj\n");
        sb.append("ET\n");

        // Header Subtitle
        sb.append("BT\n");
        sb.append("/F1 9 Tf\n");
        sb.append("0.78 0.86 0.94 rg\n");
        sb.append("36 738 Td\n");
        sb.append("(").append(escapePdfString("MVC Architecture | Relational SQLite Database | Office of the Academic Registrar")).append(") Tj\n");
        sb.append("ET\n");

        // Generated Date on Header Right
        sb.append("BT\n");
        sb.append("/F3 8 Tf\n");
        sb.append("0.85 0.90 0.98 rg\n");
        sb.append("420 756 Td\n");
        sb.append("(").append(escapePdfString("Generated: " + data.getGeneratedTimestamp())).append(") Tj\n");
        sb.append("ET\n");

        // --- SECTION A: STUDENT PERSONAL INFORMATION (Y: 620 to 705) ---
        double curY = 705;

        // Section header label
        sb.append("BT\n/F2 11 Tf\n0.094 0.168 0.286 rg\n36 ").append(curY).append(" Td\n");
        sb.append("(").append(escapePdfString("1. STUDENT PERSONAL INFORMATION")).append(") Tj\nET\n");

        curY -= 68;
        // Box background & border
        sb.append("0.97 0.98 0.99 rg\n");
        sb.append("0.82 0.86 0.90 RG\n");
        sb.append("0.8 w\n");
        sb.append("36 ").append(curY).append(" 540 60 re B\n");

        // Left Column Info
        drawText(sb, "F2", 9, 0.2, 0.2, 0.2, 46, curY + 44, "Full Name:");
        drawText(sb, "F1", 9, 0.05, 0.05, 0.05, 110, curY + 44, data.getName());

        drawText(sb, "F2", 9, 0.2, 0.2, 0.2, 46, curY + 28, "Roll Number:");
        drawText(sb, "F1", 9, 0.05, 0.05, 0.05, 110, curY + 28, String.valueOf(data.getRollNo()));

        drawText(sb, "F2", 9, 0.2, 0.2, 0.2, 46, curY + 12, "Registration No:");
        drawText(sb, "F1", 9, 0.05, 0.05, 0.05, 125, curY + 12, data.getRegistrationNo());

        // Right Column Info
        drawText(sb, "F2", 9, 0.2, 0.2, 0.2, 310, curY + 44, "Department:");
        drawText(sb, "F1", 9, 0.05, 0.05, 0.05, 380, curY + 44, data.getDepartment());

        drawText(sb, "F2", 9, 0.2, 0.2, 0.2, 310, curY + 28, "Email Address:");
        drawText(sb, "F1", 9, 0.05, 0.05, 0.05, 380, curY + 28, data.getEmail().isEmpty() ? "N/A" : data.getEmail());

        drawText(sb, "F2", 9, 0.2, 0.2, 0.2, 310, curY + 12, "Contact Phone:");
        drawText(sb, "F1", 9, 0.05, 0.05, 0.05, 380, curY + 12, data.getPhone().isEmpty() ? "N/A" : data.getPhone());

        // --- SECTION B: ACADEMIC PERFORMANCE OVERVIEW (KPI CARDS) ---
        curY -= 22;
        sb.append("BT\n/F2 11 Tf\n0.094 0.168 0.286 rg\n36 ").append(curY).append(" Td\n");
        sb.append("(").append(escapePdfString("2. ACADEMIC PERFORMANCE OVERVIEW")).append(") Tj\nET\n");

        curY -= 48;
        // 4 KPI Cards
        double cardW = 127.5;
        double cardH = 42;
        double gap = 10;

        // Card 1: Enrolled Courses
        drawKpiCard(sb, 36, curY, cardW, cardH, "Total Courses", String.valueOf(data.getTotalCourses()), 0.16, 0.50, 0.73);

        // Card 2: Overall Attendance %
        String attStr = String.format("%.1f%%", data.getOverallAttendancePct());
        double[] attColor = getAttendanceRgb(data.getOverallAttendancePct());
        drawKpiCard(sb, 36 + (cardW + gap), curY, cardW, cardH, "Overall Attendance", attStr, attColor[0], attColor[1], attColor[2]);

        // Card 3: Attendance Marks
        String attMarksStr = String.format("%.1f / %.0f", data.getTotalAttendanceMarks(), data.getTotalMaxAttendanceMarks());
        drawKpiCard(sb, 36 + 2 * (cardW + gap), curY, cardW, cardH, "Total Att. Marks", attMarksStr, 0.55, 0.27, 0.68);

        // Card 4: Overall Average Score & Grade
        String scoreStr = String.format("%.1f (%s)", data.getOverallAverageScore(), data.getOverallGrade());
        drawKpiCard(sb, 36 + 3 * (cardW + gap), curY, cardW, cardH, "Overall Score (Grade)", scoreStr, 0.83, 0.33, 0.0);

        // --- ATTENDANCE STATUS ALERT BANNER ---
        curY -= 28;
        if (data.getOverallAttendancePct() < 50.0 || data.getDangerCount() > 0) {
            // Soft Red Danger Banner
            sb.append("0.972 0.843 0.855 rg\n"); // #F8D7DA
            sb.append("0.88 0.67 0.69 RG\n");
            sb.append("0.8 w\n");
            sb.append("36 ").append(curY).append(" 540 22 re B\n");
            drawText(sb, "F2", 9, 0.447, 0.11, 0.141, 46, curY + 6,
                    "CRITICAL ATTENDANCE ALERT: Student attendance is in DANGER (< 50%). Action required immediately!");
        } else if (data.getOverallAttendancePct() < 75.0 || data.getShortageCount() > 0) {
            // Pale Yellow Warning Banner
            sb.append("1.0 0.953 0.804 rg\n"); // #FFF3CD
            sb.append("0.92 0.82 0.55 RG\n");
            sb.append("0.8 w\n");
            sb.append("36 ").append(curY).append(" 540 22 re B\n");
            drawText(sb, "F2", 9, 0.522, 0.392, 0.016, 46, curY + 6,
                    "ATTENDANCE SHORTAGE – STUDENT AT RISK: Attendance is below 75% threshold in one or more courses.");
        } else {
            // Soft Green Safe Banner
            sb.append("0.831 0.929 0.855 rg\n"); // #D4EDDA
            sb.append("0.63 0.82 0.69 RG\n");
            sb.append("0.8 w\n");
            sb.append("36 ").append(curY).append(" 540 22 re B\n");
            drawText(sb, "F2", 9, 0.082, 0.341, 0.141, 46, curY + 6,
                    "ATTENDANCE STATUS: SAFE (>= 75%). Student meets all academic attendance requirements.");
        }

        // --- SECTION C: COURSE-WISE PERFORMANCE DETAILS (TABLE) ---
        curY -= 22;
        sb.append("BT\n/F2 11 Tf\n0.094 0.168 0.286 rg\n36 ").append(curY).append(" Td\n");
        sb.append("(").append(escapePdfString("3. COURSE-WISE PERFORMANCE DETAILS")).append(") Tj\nET\n");

        curY -= 20;

        // Table Columns:
        // Code(50), Name(160), Cr(30), Att%(55), AttM(50), Exam(50), Total(50), Gr(45), Stat(50) = 540
        double[] colW = {50, 160, 30, 55, 50, 50, 50, 45, 50};
        String[] colHeaders = {"Code", "Course Name", "Cr", "Att %", "Att Marks", "Exam", "Total", "Grade", "Status"};

        // Draw Table Header
        double thH = 18;
        sb.append("0.15 0.24 0.38 rg\n"); // Dark Navy Header
        sb.append("36 ").append(curY).append(" 540 ").append(thH).append(" re f\n");

        double colX = 36;
        for (int i = 0; i < colHeaders.length; i++) {
            drawText(sb, "F2", 8, 1, 1, 1, colX + 4, curY + 5, colHeaders[i]);
            colX += colW[i];
        }

        // Draw Rows
        double rowH = 18;
        List<StudentReportData.CoursePerformance> courses = data.getCourses();

        if (courses.isEmpty()) {
            curY -= rowH;
            sb.append("0.98 0.98 0.98 rg\n36 ").append(curY).append(" 540 ").append(rowH).append(" re f\n");
            drawText(sb, "F3", 9, 0.4, 0.4, 0.4, 46, curY + 5, "No course enrollments found for this student.");
            // Stroke bottom border
            sb.append("0.8 0.8 0.8 RG\n0.5 w\n36 ").append(curY).append(" m 576 ").append(curY).append(" l S\n");
        } else {
            for (int r = 0; r < courses.size(); r++) {
                StudentReportData.CoursePerformance cp = courses.get(r);
                curY -= rowH;

                // Alternate default background
                if (r % 2 == 0) {
                    sb.append("1.0 1.0 1.0 rg\n");
                } else {
                    sb.append("0.97 0.98 0.99 rg\n");
                }
                sb.append("36 ").append(curY).append(" 540 ").append(rowH).append(" re f\n");

                // Highlight Attendance % and Status cells with attendance colors
                double attColX = 36 + colW[0] + colW[1] + colW[2]; // X for Att %
                double statusColX = 36 + colW[0] + colW[1] + colW[2] + colW[3] + colW[4] + colW[5] + colW[6] + colW[7]; // X for Status

                double[] cellColor = getAttendancePastelRgb(cp.getAttendancePct());

                // Att % cell fill
                sb.append(cellColor[0]).append(" ").append(cellColor[1]).append(" ").append(cellColor[2]).append(" rg\n");
                sb.append(attColX).append(" ").append(curY).append(" ").append(colW[3]).append(" ").append(rowH).append(" re f\n");

                // Status cell fill
                sb.append(cellColor[0]).append(" ").append(cellColor[1]).append(" ").append(cellColor[2]).append(" rg\n");
                sb.append(statusColX).append(" ").append(curY).append(" ").append(colW[8]).append(" ").append(rowH).append(" re f\n");

                // Subtle grid border
                sb.append("0.85 0.87 0.90 RG\n0.4 w\n");
                sb.append("36 ").append(curY).append(" 540 ").append(rowH).append(" re S\n");

                // Draw cell texts
                colX = 36;
                drawText(sb, "F1", 8, 0.1, 0.1, 0.1, colX + 4, curY + 5, cp.getCourseCode());
                colX += colW[0];

                String cName = cp.getCourseName();
                if (cName.length() > 30) cName = cName.substring(0, 28) + "..";
                drawText(sb, "F1", 8, 0.1, 0.1, 0.1, colX + 4, curY + 5, cName);
                colX += colW[1];

                drawText(sb, "F1", 8, 0.1, 0.1, 0.1, colX + 8, curY + 5, String.valueOf(cp.getCredits()));
                colX += colW[2];

                // Att %
                drawText(sb, "F2", 8, 0.1, 0.1, 0.1, colX + 6, curY + 5, String.format("%.1f%%", cp.getAttendancePct()));
                colX += colW[3];

                // Att Marks
                drawText(sb, "F1", 8, 0.1, 0.1, 0.1, colX + 4, curY + 5, String.format("%.1f/10", cp.getAttendanceMarks()));
                colX += colW[4];

                // Exam
                drawText(sb, "F1", 8, 0.1, 0.1, 0.1, colX + 4, curY + 5, String.format("%.1f/90", cp.getExamMarks()));
                colX += colW[5];

                // Total
                drawText(sb, "F2", 8, 0.1, 0.1, 0.1, colX + 4, curY + 5, String.format("%.1f/100", cp.getTotalMarks()));
                colX += colW[6];

                // Grade
                drawText(sb, "F2", 8, 0.1, 0.1, 0.1, colX + 12, curY + 5, cp.getGrade());
                colX += colW[7];

                // Status
                drawText(sb, "F2", 8, 0.1, 0.1, 0.1, colX + 6, curY + 5, cp.getStatus());
            }
        }

        // --- SECTION D: NOTES & CALCULATION TRANSPARENCY ---
        curY -= 26;
        sb.append("BT\n/F2 9 Tf\n0.2 0.2 0.2 rg\n36 ").append(curY).append(" Td\n");
        sb.append("(").append(escapePdfString("Grading Rules & Attendance Evaluation Policy:")).append(") Tj\nET\n");

        curY -= 12;
        drawText(sb, "F1", 8, 0.35, 0.35, 0.35, 36, curY,
                "Total Marks (100) = Exam Marks (90) + Attendance Marks (10). Grade: A+ (>=90), A (>=80), B (>=70), C (>=60), D (>=50), F (<50).");

        curY -= 11;
        drawText(sb, "F1", 8, 0.35, 0.35, 0.35, 36, curY,
                "Attendance Weightage: >=90%=10m, 85-89%=8m, 80-84%=6m, 75-79%=4m, <75%=0m (Shortage Flagged).");

        curY -= 11;
        drawText(sb, "F3", 8, 0.4, 0.4, 0.4, 36, curY,
                "* Note: Overall attendance is calculated as the unweighted arithmetic mean of course attendance percentages across enrolled courses.");

        // --- FOOTER ---
        sb.append("0.75 0.78 0.82 RG\n0.5 w\n36 32 m 576 32 l S\n");
        drawText(sb, "F3", 8, 0.45, 0.45, 0.45, 36, 20,
                "Confidential Academic Document - Generated by Student Management System | Class Group A1 - Team 2");
        drawText(sb, "F1", 8, 0.45, 0.45, 0.45, 520, 20, "Page 1 of 1");

        baos.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
        return baos.toByteArray();
    }

    private static void drawKpiCard(StringBuilder sb, double x, double y, double w, double h,
                                    String title, String val, double r, double g, double b) {
        // Background & border
        sb.append("0.98 0.99 1.0 rg\n0.84 0.88 0.92 RG\n0.7 w\n");
        sb.append(x).append(" ").append(y).append(" ").append(w).append(" ").append(h).append(" re B\n");

        // Top Accent line
        sb.append(r).append(" ").append(g).append(" ").append(b).append(" rg\n");
        sb.append(x).append(" ").append(y + h - 3).append(" ").append(w).append(" 3 re f\n");

        // Title
        drawText(sb, "F1", 8, 0.4, 0.4, 0.4, x + 6, y + 25, title);
        // Value
        drawText(sb, "F2", 12, r, g, b, x + 6, y + 8, val);
    }

    private static double[] getAttendancePastelRgb(double pct) {
        if (pct >= 75.0) {
            return new double[]{0.831, 0.929, 0.855}; // Soft Green #D4EDDA
        } else if (pct >= 50.0) {
            return new double[]{1.0, 0.953, 0.804};    // Pale Yellow #FFF3CD
        } else {
            return new double[]{0.972, 0.843, 0.855}; // Soft Red #F8D7DA
        }
    }

    private static double[] getAttendanceRgb(double pct) {
        if (pct >= 75.0) {
            return new double[]{0.15, 0.55, 0.25}; // Safe Green
        } else if (pct >= 50.0) {
            return new double[]{0.85, 0.55, 0.0};  // Warning Yellow/Orange
        } else {
            return new double[]{0.80, 0.15, 0.15}; // Danger Red
        }
    }

    private static void drawText(StringBuilder sb, String font, int size,
                                 double r, double g, double b,
                                 double x, double y, String text) {
        if (text == null) text = "";
        sb.append("BT\n");
        sb.append("/").append(font).append(" ").append(size).append(" Tf\n");
        sb.append(r).append(" ").append(g).append(" ").append(b).append(" rg\n");
        sb.append(x).append(" ").append(y).append(" Td\n");
        sb.append("(").append(escapePdfString(text)).append(") Tj\n");
        sb.append("ET\n");
    }

    private static String escapePdfString(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\\') sb.append("\\\\");
            else if (c == '(') sb.append("\\(");
            else if (c == ')') sb.append("\\)");
            else if (c >= 32 && c <= 126) sb.append(c);
            else sb.append(" "); // Replace non-ASCII safely
        }
        return sb.toString();
    }
}

