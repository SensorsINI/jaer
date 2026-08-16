package net.sf.jaer.util.textio;

import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.PolarityEvent.Polarity;

/**
 * Shared DVS text/CSV line formatting used by {@link DavisTextOutputWriter} and
 * File → Save As.
 */
public final class DavisTextEventFormatter {

    private final boolean useCSV;
    private final boolean useUsTimestamps;
    private final boolean useSignedPolarity;
    private final boolean timestampLast;
    private final boolean specialEvents;
    private final boolean flipPolarity;

    public DavisTextEventFormatter(boolean useCSV, boolean useUsTimestamps, boolean useSignedPolarity,
            boolean timestampLast, boolean specialEvents, boolean flipPolarity) {
        this.useCSV = useCSV;
        this.useUsTimestamps = useUsTimestamps;
        this.useSignedPolarity = useSignedPolarity;
        this.timestampLast = timestampLast;
        this.specialEvents = specialEvents;
        this.flipPolarity = flipPolarity;
    }

    public static DavisTextEventFormatter from(AbstractDavisTextIo io) {
        return new DavisTextEventFormatter(io.useCSV, io.useUsTimestamps, io.useSignedPolarity,
                io.timestampLast, io.specialEvents, io.flipPolarity);
    }

    public static DavisTextEventFormatter rpg() {
        return new DavisTextEventFormatter(false, false, false, false, false, false);
    }

    public char separator() {
        return useCSV ? ',' : ' ';
    }

    public int polarityValue(Polarity p) {
        Polarity pol = p;
        if (flipPolarity) {
            pol = p == Polarity.Off ? Polarity.On : Polarity.Off;
        }
        if (useSignedPolarity) {
            return pol == Polarity.Off ? -1 : 1;
        }
        return pol == Polarity.Off ? 0 : 1;
    }

    public String format(PolarityEvent ae) {
        char sep = separator();
        String ts = useUsTimestamps ? Integer.toString(ae.timestamp) : Float.toString(1e-6f * ae.timestamp);
        StringBuilder sb = new StringBuilder(48);
        if (timestampLast) {
            sb.append(Integer.toString(ae.x)).append(sep)
                    .append(Integer.toString(ae.y)).append(sep)
                    .append(Integer.toString(polarityValue(ae.polarity))).append(sep)
                    .append(ts);
        } else {
            sb.append(ts).append(sep)
                    .append(Integer.toString(ae.x)).append(sep)
                    .append(Integer.toString(ae.y)).append(sep)
                    .append(Integer.toString(polarityValue(ae.polarity)));
        }
        if (specialEvents) {
            sb.append(sep).append(ae.isSpecial() ? '1' : '0');
        }
        return sb.toString();
    }

    /**
     * Human-readable column legend for a header comment.
     */
    public String columnLegend() {
        String ts = useUsTimestamps ? "timestamp(int32 us)" : "timestamp(float s)";
        String pol = useSignedPolarity ? "polarity(off/on=-1/+1)" : "polarity(off/on=0/1)";
        if (flipPolarity) {
            pol = pol + " (flipped)";
        }
        String order = timestampLast ? "x y " + pol + " " + ts : ts + " x y " + pol;
        if (specialEvents) {
            order += " special(1=special,0=normal)";
        }
        return order.replace(' ', separator());
    }

    public String shortFormatHint() {
        char sep = separator();
        String format = timestampLast ? "x,y,p,t" : "t,x,y,p";
        if (specialEvents) {
            format += ",s";
        }
        return format.replace(',', sep);
    }

    public boolean isUseCSV() {
        return useCSV;
    }

    public boolean isUseUsTimestamps() {
        return useUsTimestamps;
    }

    public boolean isUseSignedPolarity() {
        return useSignedPolarity;
    }

    public boolean isTimestampLast() {
        return timestampLast;
    }

    public boolean isSpecialEvents() {
        return specialEvents;
    }

    public boolean isFlipPolarity() {
        return flipPolarity;
    }
}
