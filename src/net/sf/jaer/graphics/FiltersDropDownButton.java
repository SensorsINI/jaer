package net.sf.jaer.graphics;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

/**
 * Split Filters control. The label toggles the filter window. The arrow opens
 * Disable all, the same temporary global pause as the filter window toolbar.
 * Green means at least one filter is enabled and filtering is not paused.
 */
public final class FiltersDropDownButton extends JPanel {

    private static final int ARC = 8;
    private static final int ARROW_WIDTH = 18;
    private static final int MAX_TOOLTIP_NAMES = 24;
    private static final Color ACTIVE_TOP = new Color(129, 199, 132);
    private static final Color ACTIVE_BOTTOM = new Color(67, 160, 71);
    private static final Color ACTIVE_BORDER = new Color(46, 125, 50);
    private static final Color IDLE_TOP = new Color(250, 250, 250);
    private static final Color IDLE_BOTTOM = new Color(220, 220, 220);
    private static final Color IDLE_BORDER = new Color(170, 170, 170);
    private static final String IDLE_TIP = "Shows or hides the filter window";

    private final JButton mainButton = new JButton("Filters") {
        @Override
        public void updateUI() {
            // Aqua paints a bezel even when contentAreaFilled is false, which
            // would cover the green/gray background drawn by this panel.
            setUI(new BasicButtonUI());
            if (labelFont != null) {
                setFont(labelFont);
            }
        }
    };
    private final JButton arrowButton = new JButton() {
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int cx = getWidth() / 2;
            int cy = getHeight() / 2 + 1;
            Path2D triangle = new Path2D.Float();
            triangle.moveTo(cx - 4, cy - 2);
            triangle.lineTo(cx + 4, cy - 2);
            triangle.lineTo(cx, cy + 3);
            triangle.closePath();
            g2.setColor(Color.DARK_GRAY);
            g2.fill(triangle);
            g2.dispose();
        }
    };
    private final JPopupMenu menu = new JPopupMenu();
    private final JCheckBoxMenuItem disableAllItem = new JCheckBoxMenuItem("Disable all");
    private boolean filtersActive;
    private int matchedHeight;
    private Font labelFont;

    public FiltersDropDownButton(Font font, Runnable onToggle, Consumer<Boolean> onDisableAll) {
        super(new BorderLayout(0, 0));
        setOpaque(false);
        setAlignmentY(0.0F);
        flatten(mainButton);
        flatten(arrowButton);
        labelFont = font;
        mainButton.setFont(font);
        mainButton.setForeground(Color.BLACK);
        mainButton.setMargin(new Insets(2, 8, 2, 4));
        arrowButton.setFont(font);
        arrowButton.setMargin(new Insets(2, 0, 2, 0));
        arrowButton.setPreferredSize(new Dimension(ARROW_WIDTH, 20));
        mainButton.getAccessibleContext().setAccessibleName("Filters");
        arrowButton.getAccessibleContext().setAccessibleName("Filter actions");
        mainButton.setToolTipText(IDLE_TIP);
        arrowButton.setToolTipText(IDLE_TIP);
        setToolTipText(IDLE_TIP);
        mainButton.addActionListener(e -> onToggle.run());
        arrowButton.addActionListener(e -> menu.show(this, 0, getHeight()));
        mainButton.getModel().addChangeListener(e -> repaint());
        arrowButton.getModel().addChangeListener(e -> repaint());
        disableAllItem.setToolTipText("Temporarily disables all filters");
        disableAllItem.addActionListener(e -> onDisableAll.accept(disableAllItem.isSelected()));
        menu.add(disableAllItem);
        add(mainButton, BorderLayout.CENTER);
        add(arrowButton, BorderLayout.EAST);
    }

    /** Match the height of the button this control replaces. */
    public void matchHeight(int height) {
        matchedHeight = height;
        revalidate();
    }

    /**
     * @param enabledFilterNames short names of enabled filters; ignored while
     * {@code filteringDisabled} is true because those filters are not running
     * @param filteringDisabled true when Disable all is on
     */
    public void setFilterActivity(List<String> enabledFilterNames, boolean filteringDisabled) {
        filtersActive = !filteringDisabled && enabledFilterNames != null && !enabledFilterNames.isEmpty();
        if (disableAllItem.isSelected() != filteringDisabled) {
            disableAllItem.setSelected(filteringDisabled);
        }
        String tip = tooltip(enabledFilterNames, filteringDisabled);
        setToolTipText(tip);
        mainButton.setToolTipText(tip);
        arrowButton.setToolTipText(tip);
        repaint();
    }

    private static String tooltip(List<String> enabledFilterNames, boolean filteringDisabled) {
        if (filteringDisabled) {
            return "Filtering is paused (Disable all). Click to show or hide the filter window.";
        }
        if (enabledFilterNames == null || enabledFilterNames.isEmpty()) {
            return "No filters are active. Click to show or hide the filter window.";
        }
        StringBuilder sb = new StringBuilder("<html>Active filters:");
        int shown = 0;
        for (String name : enabledFilterNames) {
            if (shown == MAX_TOOLTIP_NAMES) {
                sb.append("<br>and ").append(enabledFilterNames.size() - shown).append(" more");
                break;
            }
            sb.append("<br>").append(escapeHtml(name));
            shown++;
        }
        sb.append("<br>Click to show or hide the filter window</html>");
        return sb.toString();
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void flatten(JButton button) {
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setFocusable(false);
        button.setRolloverEnabled(true);
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        if (matchedHeight > 0) {
            d.height = matchedHeight;
        }
        return d;
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth() - 1;
        int h = getHeight() - 1;
        boolean pressed = mainButton.getModel().isPressed() || mainButton.getModel().isArmed()
                || arrowButton.getModel().isPressed() || arrowButton.getModel().isArmed();
        boolean hover = !pressed && (mainButton.getModel().isRollover() || arrowButton.getModel().isRollover());
        Color top = filtersActive ? ACTIVE_TOP : IDLE_TOP;
        Color bottom = filtersActive ? ACTIVE_BOTTOM : IDLE_BOTTOM;
        if (pressed) {
            top = top.darker();
            bottom = bottom.darker();
        } else if (hover) {
            top = top.brighter();
        }
        RoundRectangle2D shape = new RoundRectangle2D.Float(0, 0, w, h, ARC, ARC);
        g2.setClip(shape);
        g2.setPaint(new java.awt.GradientPaint(0, 0, top, 0, h, bottom));
        g2.fill(shape);
        g2.setClip(null);
        g2.setColor(filtersActive ? ACTIVE_BORDER : IDLE_BORDER);
        g2.draw(shape);
        int split = getWidth() - arrowButton.getWidth();
        g2.drawLine(split, 3, split, h - 3);
        g2.dispose();
    }
}
