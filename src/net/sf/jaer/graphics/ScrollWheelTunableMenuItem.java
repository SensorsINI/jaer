package net.sf.jaer.graphics;

import java.awt.Component;
import java.awt.Point;
import java.awt.event.KeyEvent;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.event.MenuKeyEvent;
import javax.swing.event.MenuKeyListener;
import javax.swing.event.MouseInputAdapter;

/**
 * Menu item that shows a live parameter value. Adjust with the mouse wheel while
 * the parent menu popup is open and this item is highlighted.
 * <p>
 * Swing delivers wheel events to the {@link JPopupMenu}, not to individual
 * {@link JMenuItem}s, so use {@link #installPopupWheelHandler(JMenu)} on the
 * containing menu.
 */
interface WheelAdjustableMenuItem {
    boolean adjustForWheelRotation(int wheelRotation);
}

public class ScrollWheelTunableMenuItem extends JMenuItem implements WheelAdjustableMenuItem {

    /**
     * Reads/writes a tunable integer and defines wheel steps.
     */
    public interface IntParameter {
        int get();

        void set(int value);

        /** Wheel up (away from user). */
        int stepUp(int current);

        /** Wheel down (toward user). */
        int stepDown(int current);

        String formatLabel(int value);
    }

    private IntParameter parameter;
    private Runnable onChanged;

    public ScrollWheelTunableMenuItem() {
    }

    /**
     * Routes mouse-wheel events from a menu popup to the highlighted tunable item.
     * Safe to call once per {@link JMenu}.
     */
    public static void installPopupWheelHandler(JMenu menu) {
        final JPopupMenu popup = menu.getPopupMenu();
        if (popup.getClientProperty(ScrollWheelTunableMenuItem.class) != null) {
            return;
        }
        popup.putClientProperty(ScrollWheelTunableMenuItem.class, Boolean.TRUE);

        popup.addMouseWheelListener(e -> {
            final WheelAdjustableMenuItem item = findTunableItem(e);
            if (item != null && item.adjustForWheelRotation(e.getWheelRotation())) {
                e.consume();
            }
        });

        popup.addMenuKeyListener(new MenuKeyListener() {
            @Override
            public void menuKeyTyped(MenuKeyEvent e) {
            }

            @Override
            public void menuKeyPressed(MenuKeyEvent e) {
                final int keyCode = e.getKeyCode();
                if (keyCode != KeyEvent.VK_UP && keyCode != KeyEvent.VK_DOWN) {
                    return;
                }
                final WheelAdjustableMenuItem item = findTunableItem(e.getPath());
                final int rotation = keyCode == KeyEvent.VK_UP ? -1 : 1;
                if (item != null && item.adjustForWheelRotation(rotation)) {
                    e.consume();
                }
            }

            @Override
            public void menuKeyReleased(MenuKeyEvent e) {
            }
        });

        // Keep highlight tracking in sync when the pointer moves over items.
        popup.addMouseMotionListener(new MouseInputAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                highlightItemAt(popup, e.getPoint());
            }
        });
    }

    private static void highlightItemAt(JPopupMenu popup, Point popupPoint) {
        final Component deepest = SwingUtilities.getDeepestComponentAt(popup, popupPoint.x, popupPoint.y);
        Component c = deepest;
        while (c != null) {
            if (c instanceof JMenuItem item) {
                item.setArmed(true);
                MenuSelectionManager.defaultManager().setSelectedPath(
                        new MenuElement[] { popup, item });
                return;
            }
            c = c.getParent();
        }
    }

    private static WheelAdjustableMenuItem findTunableItem(MenuElement[] path) {
        for (int i = path.length - 1; i >= 0; i--) {
            if (path[i] instanceof WheelAdjustableMenuItem item) {
                return item;
            }
        }
        return null;
    }

    private static WheelAdjustableMenuItem findTunableItem(java.awt.event.MouseWheelEvent e) {
        final WheelAdjustableMenuItem selected = findTunableItem(
                MenuSelectionManager.defaultManager().getSelectedPath());
        if (selected != null) {
            return selected;
        }
        if (e.getComponent() instanceof JPopupMenu popup) {
            final Point p = e.getPoint();
            Component c = SwingUtilities.getDeepestComponentAt(popup, p.x, p.y);
            while (c != null) {
                if (c instanceof WheelAdjustableMenuItem item) {
                    return item;
                }
                c = c.getParent();
            }
        }
        return null;
    }

    public void bind(IntParameter parameter, Runnable onChanged) {
        this.parameter = parameter;
        this.onChanged = onChanged;
        refreshLabel();
    }

    /** Shown after the value so users know the item responds to the mouse wheel. */
    private static final String SCROLL_HINT = "  \u25B2\u25BC";

    /** Step {@code current} up to the next power-of-two size (×2 ladder). */
    public static int stepPowerOfTwoUp(int current, int min, int max) {
        final int v = Math.max(current, min);
        final int next = isPowerOfTwo(v)
                ? v << 1
                : Integer.highestOneBit(v) << 1;
        return Math.min(Math.max(next, min), max);
    }

    /** Step {@code current} down to the previous power-of-two size (÷2 ladder). */
    public static int stepPowerOfTwoDown(int current, int min, int max) {
        final int v = Math.min(Math.max(current, min), max);
        final int next = isPowerOfTwo(v)
                ? v >> 1
                : Integer.highestOneBit(v);
        return Math.max(next, min);
    }

    private static boolean isPowerOfTwo(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }

    public void refreshLabel() {
        if (parameter != null) {
            setText(parameter.formatLabel(parameter.get()) + SCROLL_HINT);
        }
    }

    /**
     * @return true if a value change was applied
     */
    public boolean adjustForWheelRotation(int wheelRotation) {
        if (!isEnabled() || parameter == null || wheelRotation == 0) {
            return false;
        }
        final int current = parameter.get();
        final int next = wheelRotation < 0
                ? parameter.stepUp(current)
                : parameter.stepDown(current);
        if (next == current) {
            return false;
        }
        parameter.set(next);
        refreshLabel();
        if (onChanged != null) {
            onChanged.run();
        }
        return true;
    }

    /** Single menu click: step up once (same as one wheel notch up). */
    public void stepUpOnce() {
        adjustForWheelRotation(-1);
    }
}
