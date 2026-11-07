import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/**
 * Custom Table Cell Renderer for Attendance Percentage and Attendance Status.
 *
 * Implements dynamic color coding:
 * - >= 75%: Soft Green (Safe)
 * - 50% - 74.9%: Pale Yellow (Warning)
 * - < 50%: Soft Red (Danger)
 *
 * Preserves black text readability, spreadsheet aesthetic, and row selection contrast.
 */
public class AttendanceCellRenderer extends DefaultTableCellRenderer {

    // Spreadsheet-style soft pastel colors
    public static final Color COLOR_SAFE_BG = new Color(212, 237, 218);       // #D4EDDA
    public static final Color COLOR_SAFE_SEL = new Color(185, 222, 194);
    public static final Color COLOR_SAFE_BORDER = new Color(160, 210, 175);

    public static final Color COLOR_WARNING_BG = new Color(255, 243, 205);    // #FFF3CD
    public static final Color COLOR_WARNING_SEL = new Color(242, 226, 175);
    public static final Color COLOR_WARNING_BORDER = new Color(235, 210, 140);

    public static final Color COLOR_DANGER_BG = new Color(248, 215, 218);     // #F8D7DA
    public static final Color COLOR_DANGER_SEL = new Color(236, 193, 198);
    public static final Color COLOR_DANGER_BORDER = new Color(225, 170, 175);

    public static final Color TEXT_COLOR = new Color(25, 25, 25);            // Readable dark/black

    public AttendanceCellRenderer() {
        setHorizontalAlignment(SwingConstants.CENTER);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus,
                                                   int row, int column) {
        Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        if (value == null) {
            return c;
        }

        String str = value.toString().trim();
        Double pct = parsePercentage(str);

        Color targetBg = null;
        Color targetSelBg = null;
        Color targetBorder = null;

        if (pct != null) {
            if (pct >= 75.0) {
                targetBg = COLOR_SAFE_BG;
                targetSelBg = COLOR_SAFE_SEL;
                targetBorder = COLOR_SAFE_BORDER;
            } else if (pct >= 50.0) {
                targetBg = COLOR_WARNING_BG;
                targetSelBg = COLOR_WARNING_SEL;
                targetBorder = COLOR_WARNING_BORDER;
            } else {
                targetBg = COLOR_DANGER_BG;
                targetSelBg = COLOR_DANGER_SEL;
                targetBorder = COLOR_DANGER_BORDER;
            }
        } else {
            // Check status keywords
            if ("SAFE".equalsIgnoreCase(str)) {
                targetBg = COLOR_SAFE_BG;
                targetSelBg = COLOR_SAFE_SEL;
                targetBorder = COLOR_SAFE_BORDER;
            } else if ("WARNING".equalsIgnoreCase(str)) {
                targetBg = COLOR_WARNING_BG;
                targetSelBg = COLOR_WARNING_SEL;
                targetBorder = COLOR_WARNING_BORDER;
            } else if ("DANGER".equalsIgnoreCase(str)) {
                targetBg = COLOR_DANGER_BG;
                targetSelBg = COLOR_DANGER_SEL;
                targetBorder = COLOR_DANGER_BORDER;
            }
        }

        if (targetBg != null) {
            if (isSelected) {
                c.setBackground(targetSelBg);
                if (c instanceof JComponent) {
                    ((JComponent) c).setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(targetBorder, 1),
                            new EmptyBorder(0, 4, 0, 4)
                    ));
                }
            } else {
                c.setBackground(targetBg);
                if (c instanceof JComponent) {
                    ((JComponent) c).setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(targetBorder, 1),
                            new EmptyBorder(0, 4, 0, 4)
                    ));
                }
            }
            c.setForeground(TEXT_COLOR);
            c.setFont(c.getFont().deriveFont(Font.BOLD));
        } else {
            if (isSelected) {
                c.setBackground(table.getSelectionBackground());
                c.setForeground(table.getSelectionForeground());
            } else {
                c.setBackground(table.getBackground());
                c.setForeground(table.getForeground());
            }
        }

        return c;
    }

    /**
     * Parses numeric percentage from strings like "78.5%", "78.5", "  50.0 % ".
     */
    public static Double parsePercentage(String text) {
        if (text == null) return null;
        String cleaned = text.replaceAll("[^0-9.]", "").trim();
        if (cleaned.isEmpty()) return null;
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}

