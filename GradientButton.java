import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * Custom JButton that paints a soft, premium Pistachio-to-Mint green gradient
 * inspired by the Cityvibes "Shades of Green" color palette.
 *
 * Supports smooth visual states:
 * - Normal:   Pistachio (#C5DFCE) -> Mint (#A8D5BA), text #244D36
 * - Hover:    Mint (#A8D5BA) -> Seafoam (#78C6A0), text #1B3E2A
 * - Pressed:  Deep Mint (#98C7AA) -> Sage (#7FAF92), text #153322
 * - Disabled: Soft Grey-Green (#E2ECE5 -> #D6E3DA), text #7C8E82
 */
public class GradientButton extends JButton {

    // Palette Colors
    private static final Color PISTACHIO = new Color(0xC5, 0xDF, 0xCE); // #C5DFCE
    private static final Color MINT      = new Color(0xA8, 0xD5, 0xBA); // #A8D5BA
    private static final Color SEAFOAM   = new Color(0x78, 0xC6, 0xA0); // #78C6A0
    private static final Color SAGE      = new Color(0x8F, 0xAF, 0x91); // #8FAF91
    private static final Color DEEP_SAGE = new Color(0x64, 0x8B, 0x72); // #648B72

    // State Colors
    private static final Color NORMAL_BORDER   = new Color(0x8F, 0xAF, 0x91);
    private static final Color NORMAL_TEXT     = new Color(0x24, 0x4D, 0x36);

    private static final Color HOVER_BORDER    = new Color(0x64, 0x8B, 0x72);
    private static final Color HOVER_TEXT      = new Color(0x1B, 0x3E, 0x2A);

    private static final Color PRESSED_START   = new Color(0x98, 0xC7, 0xAA);
    private static final Color PRESSED_END     = new Color(0x7F, 0xAF, 0x92);
    private static final Color PRESSED_BORDER  = new Color(0x50, 0x72, 0x5C);
    private static final Color PRESSED_TEXT    = new Color(0x15, 0x33, 0x22);

    private static final Color DISABLED_START  = new Color(0xE2, 0xEC, 0xE5);
    private static final Color DISABLED_END    = new Color(0xD6, 0xE3, 0xDA);
    private static final Color DISABLED_BORDER = new Color(0xC0, 0xD0, 0xC6);
    private static final Color DISABLED_TEXT   = new Color(0x7C, 0x8E, 0x82);

    private final int cornerRadius;
    private boolean isHovered = false;

    public GradientButton(String text) {
        this(text, 14);
    }

    public GradientButton(String text, int cornerRadius) {
        super(text);
        this.cornerRadius = cornerRadius;

        setFont(new Font("Segoe UI", Font.BOLD, 14));
        setForeground(NORMAL_TEXT);
        setBorder(new EmptyBorder(8, 18, 8, 18));
        setOpaque(false);
        setContentAreaFilled(false);
        setFocusPainted(false);
        setBorderPainted(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Interactive hover tracking
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (isEnabled()) {
                    isHovered = true;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                isHovered = false;
                repaint();
            }
        });
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setCursor(enabled ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                          : Cursor.getDefaultCursor());
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();

        Color startColor;
        Color endColor;
        Color borderColor;
        Color textColor;
        float borderStrokeWidth = 1.2f;

        if (!isEnabled()) {
            startColor = DISABLED_START;
            endColor = DISABLED_END;
            borderColor = DISABLED_BORDER;
            textColor = DISABLED_TEXT;
        } else if (getModel().isPressed() || getModel().isArmed()) {
            startColor = PRESSED_START;
            endColor = PRESSED_END;
            borderColor = PRESSED_BORDER;
            textColor = PRESSED_TEXT;
            borderStrokeWidth = 1.5f;
        } else if (isHovered || getModel().isRollover()) {
            // Hover: Mint -> Seafoam with strengthened border
            startColor = MINT;
            endColor = SEAFOAM;
            borderColor = HOVER_BORDER;
            textColor = HOVER_TEXT;
            borderStrokeWidth = 1.5f;
        } else {
            // Normal: Pistachio -> Mint
            startColor = PISTACHIO;
            endColor = MINT;
            borderColor = NORMAL_BORDER;
            textColor = NORMAL_TEXT;
        }

        // 1. Draw smooth horizontal Pistachio-to-Mint gradient background
        GradientPaint gradient = new GradientPaint(0, 0, startColor, width, 0, endColor);
        g2.setPaint(gradient);
        RoundRectangle2D.Float shape = new RoundRectangle2D.Float(
                1f, 1f, width - 2f, height - 2f, cornerRadius, cornerRadius);
        g2.fill(shape);

        // 2. Draw subtle rounded border
        g2.setColor(borderColor);
        g2.setStroke(new BasicStroke(borderStrokeWidth));
        g2.draw(shape);

        // 3. Focus indication (accessibility)
        if (isFocusOwner() && isEnabled()) {
            g2.setColor(new Color(DEEP_SAGE.getRed(), DEEP_SAGE.getGreen(), DEEP_SAGE.getBlue(), 90));
            g2.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{3, 3}, 0));
            RoundRectangle2D.Float focusShape = new RoundRectangle2D.Float(
                    3f, 3f, width - 6f, height - 6f, Math.max(4, cornerRadius - 4), Math.max(4, cornerRadius - 4));
            g2.draw(focusShape);
        }

        g2.dispose();

        // 4. Paint text and icon with correct high-contrast forest-green foreground
        setForeground(textColor);
        super.paintComponent(g);
    }
}
