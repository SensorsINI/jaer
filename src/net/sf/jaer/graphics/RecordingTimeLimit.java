package net.sf.jaer.graphics;

import java.awt.event.ActionEvent;

import javax.swing.JComboBox;
import javax.swing.JTextField;

import org.joda.time.Period;
import org.joda.time.PeriodType;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;

/**
 * Parse/format for the session recording time limit (presets or free-form
 * duration). {@code 0} / {@link #NO_LIMIT} means no limit.
 */
public final class RecordingTimeLimit {

    public static final String NO_LIMIT = "No limit";
    public static final String[] PRESETS = {
        NO_LIMIT,
        "1m", "10m", "30m", "1h", "3h", "12h", "24h", "1d", "7d", "14d", "30d"
    };

    private static final PeriodFormatter FORMATTER = new PeriodFormatterBuilder()
            .appendDays().appendSuffix("d")
            .appendSeparator(" ")
            .appendHours().appendSuffix("h")
            .appendSeparator(" ")
            .appendMinutes().appendSuffix("m")
            .appendSeparator(" ")
            .appendSeconds().appendSuffix("s")
            .appendSeparator(" ")
            .appendMillis()
            .toFormatter();

    private RecordingTimeLimit() {
    }

    public static String formatForDialog(long ms) {
        if (ms <= 0) {
            return NO_LIMIT;
        }
        Period p = new Period(ms).normalizedStandard(PeriodType.dayTime());
        String printed = FORMATTER.print(p);
        return printed.isEmpty() ? Long.toString(ms) : printed;
    }

    public static String initialValue(long limitMs) {
        if (limitMs <= 0) {
            return NO_LIMIT;
        }
        for (String preset : PRESETS) {
            if (NO_LIMIT.equals(preset)) {
                continue;
            }
            try {
                if (parseMs(preset) == limitMs) {
                    return preset;
                }
            } catch (IllegalArgumentException e) {
                // skip unmatched preset
            }
        }
        return formatForDialog(limitMs);
    }

    public static long parseMs(String ans) {
        if (ans == null) {
            throw new IllegalArgumentException("null duration");
        }
        ans = ans.trim();
        if (ans.isEmpty()) {
            return 0L;
        }
        if (ans.equalsIgnoreCase(NO_LIMIT)) {
            return 0L;
        }
        if (ans.matches("\\d+")) {
            return Long.parseLong(ans);
        }
        Period p = FORMATTER.parsePeriod(ans);
        return p.toStandardDuration().getMillis();
    }

    /** Copies a preset into {@code freeForm} (No limit → {@code 0}). */
    public static void bindPresetChooser(JComboBox<String> chooser, JTextField freeForm) {
        chooser.addActionListener((ActionEvent e) -> {
            Object sel = chooser.getSelectedItem();
            if (sel == null) {
                return;
            }
            String preset = sel.toString();
            freeForm.setText(NO_LIMIT.equals(preset) ? "0" : preset);
            freeForm.requestFocusInWindow();
            freeForm.selectAll();
        });
    }

    public static int presetIndex(String initial) {
        for (int i = 0; i < PRESETS.length; i++) {
            if (PRESETS[i].equals(initial)) {
                return i;
            }
        }
        return -1;
    }
}
