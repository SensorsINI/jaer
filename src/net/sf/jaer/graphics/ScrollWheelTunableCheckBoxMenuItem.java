package net.sf.jaer.graphics;

import javax.swing.JCheckBoxMenuItem;

/**
 * Checkbox menu item whose integer parameter can be adjusted with the mouse
 * wheel or arrow keys without changing its selected state.
 */
public class ScrollWheelTunableCheckBoxMenuItem extends JCheckBoxMenuItem
        implements WheelAdjustableMenuItem {

    private static final String SCROLL_HINT = "  \u25B2\u25BC";

    private ScrollWheelTunableMenuItem.IntParameter parameter;
    private Runnable onChanged;

    public void bind(ScrollWheelTunableMenuItem.IntParameter parameter, Runnable onChanged) {
        this.parameter = parameter;
        this.onChanged = onChanged;
        refreshLabel();
    }

    public void refreshLabel() {
        if (parameter != null) {
            setText(parameter.formatLabel(parameter.get()) + SCROLL_HINT);
        }
    }

    @Override
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
}
