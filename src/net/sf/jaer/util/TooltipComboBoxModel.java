package net.sf.jaer.util;

import java.awt.Component;
import java.awt.Dimension;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

/**
 * Combo model whose rows are {@link TooltipChoice}s. {@link #install} attaches
 * a renderer so each row's tooltip is shown, and refreshes the list when the
 * popup opens.
 * <p>
 * The selection callback runs only for a user change. Reloading the list does
 * not call it, so opening the popup does not rewrite preferences.
 */
public class TooltipComboBoxModel extends DefaultComboBoxModel<TooltipChoice> {

    private static final String INSTALLED = "tooltipComboInstalled";

    private final Supplier<List<TooltipChoice>> source;
    private final Consumer<String> onUserSelect;
    private boolean adjusting;

    /**
     * @param source        current rows; called when the popup opens and from {@link #reload(String)}
     * @param onUserSelect  persisted value of the row the user picked
     */
    public TooltipComboBoxModel(Supplier<List<TooltipChoice>> source, Consumer<String> onUserSelect) {
        this.source = source;
        this.onUserSelect = onUserSelect;
    }

    /** Replace the rows from {@link #source} and keep {@code keepValue} selected. */
    public void reload(String keepValue) {
        List<TooltipChoice> choices = source == null ? List.of() : source.get();
        reload(keepValue, choices);
    }

    /** Replace the rows and keep {@code keepValue} selected. Does not notify {@code onUserSelect}. */
    public void reload(String keepValue, List<TooltipChoice> choices) {
        adjusting = true;
        try {
            removeAllElements();
            TooltipChoice selected = null;
            if (choices != null) {
                for (TooltipChoice choice : choices) {
                    if (choice == null) {
                        continue;
                    }
                    addElement(choice);
                    if (selected == null && keepValue != null && choice.value().equalsIgnoreCase(keepValue)) {
                        selected = choice;
                    }
                }
            }
            if (selected == null && keepValue != null && !keepValue.isEmpty()) {
                selected = TooltipChoice.absent(keepValue);
                insertElementAt(selected, 0);
            }
            super.setSelectedItem(selected);
        } finally {
            adjusting = false;
        }
    }

    @Override
    public void setSelectedItem(Object item) {
        Object previous = getSelectedItem();
        super.setSelectedItem(item);
        if (adjusting || onUserSelect == null || !(item instanceof TooltipChoice choice)) {
            return;
        }
        if (previous instanceof TooltipChoice prior && prior.value().equalsIgnoreCase(choice.value())) {
            return;
        }
        onUserSelect.accept(choice.value());
    }

    /**
     * Renderer, per-row tooltips, and a refresh when the popup opens.
     * Safe to call once per combo box.
     */
    public static void install(JComboBox<?> combo) {
        if (combo == null || Boolean.TRUE.equals(combo.getClientProperty(INSTALLED))) {
            return;
        }
        combo.putClientProperty(INSTALLED, Boolean.TRUE);
        combo.setRenderer(new TooltipChoiceRenderer(combo));
        combo.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                if (combo.getModel() instanceof TooltipComboBoxModel model) {
                    Object selected = model.getSelectedItem();
                    String keep = selected instanceof TooltipChoice choice ? choice.value() : null;
                    model.reload(keep);
                }
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
            }
        });
        int chars = 16;
        if (combo.getModel() instanceof TooltipComboBoxModel model) {
            for (int i = 0; i < model.getSize(); i++) {
                TooltipChoice choice = model.getElementAt(i);
                if (choice != null) {
                    chars = Math.max(chars, choice.label().length());
                }
            }
        }
        chars = Math.min(chars, 48);
        @SuppressWarnings("rawtypes")
        JComboBox raw = combo;
        raw.setPrototypeDisplayValue(new TooltipChoice("", "m".repeat(chars), ""));
        combo.setMaximumSize(new Dimension(380, 28));
    }

    private static final class TooltipChoiceRenderer extends DefaultListCellRenderer {

        private final JComboBox<?> combo;

        TooltipChoiceRenderer(JComboBox<?> combo) {
            this.combo = combo;
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            Component rendered = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            String text = value == null ? "" : value.toString();
            String tip = null;
            if (value instanceof TooltipChoice choice) {
                text = choice.label();
                tip = choice.tooltip();
            }
            setText(text);
            if (index < 0) {
                combo.setToolTipText(tip);
            } else if (isSelected) {
                list.setToolTipText(tip);
            }
            return rendered;
        }
    }
}
