package net.sf.jaer.util;

import java.util.Locale;

/**
 * One row of a combo box: the value stored in preferences, the label shown,
 * and the tooltip for that row.
 */
public final class TooltipChoice {

    private final String value;
    private final String label;
    private final String tooltip;

    public TooltipChoice(String value, String label, String tooltip) {
        this.value = value == null ? "" : value;
        this.label = (label == null || label.isEmpty()) ? this.value : label;
        this.tooltip = tooltip == null ? "" : tooltip;
    }

    /** Saved value that is not among the ports currently present. */
    public static TooltipChoice absent(String value) {
        String saved = value == null ? "" : value;
        return new TooltipChoice(saved, saved + " (not connected)",
                "<html>Saved port is not connected:<br>" + escape(saved) + "</html>");
    }

    public String value() {
        return value;
    }

    public String label() {
        return label;
    }

    /** HTML or plain text. Swing shows HTML when the string starts with {@code <html>}. */
    public String tooltip() {
        return tooltip;
    }

    @Override
    public String toString() {
        return label;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TooltipChoice choice && value.equalsIgnoreCase(choice.value);
    }

    @Override
    public int hashCode() {
        return value.toLowerCase(Locale.ROOT).hashCode();
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
