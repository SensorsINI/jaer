package net.sf.jaer.eventio.aedat4;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.sf.jaer.eventio.aedat4.dv.Event;
import net.sf.jaer.eventio.aedat4.dv.EventPacket;

/**
 * One decompressed EVTS packet: either a DV FlatBuffer or an LBEV time-bin view.
 * {@link #load(int)} fills {@link #timestamp}, {@link #x}, {@link #y}, and {@link #type}.
 */
public final class Aedat4PolaritySamples {

    private final EventPacket flat;
    private final Event scratch = new Event();
    private final Aedat4LossyTimeBins.View lossy;

    public long timestamp;
    public int x;
    public int y;
    /** 1 = On, 0 = Off. */
    public int type;

    private Aedat4PolaritySamples(EventPacket flat, Aedat4LossyTimeBins.View lossy) {
        this.flat = flat;
        this.lossy = lossy;
    }

    public static Aedat4PolaritySamples fromDecompressed(ByteBuffer flat) throws IOException {
        if (flat == null) {
            return new Aedat4PolaritySamples(null, null);
        }
        ByteBuffer view = flat.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        view.rewind();
        if (Aedat4LossyTimeBins.isLossyPayload(view)) {
            return new Aedat4PolaritySamples(null, Aedat4LossyTimeBins.decode(view));
        }
        return new Aedat4PolaritySamples(EventPacket.getSizePrefixedRootAsEventPacket(view), null);
    }

    public boolean isLossy() {
        return lossy != null;
    }

    public int length() {
        if (lossy != null) {
            return lossy.expandedLength();
        }
        if (flat == null) {
            return 0;
        }
        return flat.elementsLength();
    }

    /** @return false when the index is missing (empty or a null FlatBuffer slot) */
    public boolean load(int index) {
        if (lossy != null) {
            if (index < 0 || index >= lossy.expandedLength()) {
                return false;
            }
            timestamp = lossy.timestamp(index);
            x = lossy.x(index);
            y = lossy.y(index);
            type = lossy.type(index);
            return true;
        }
        if (flat == null) {
            return false;
        }
        Event event = flat.elements(scratch, index);
        if (event == null) {
            return false;
        }
        timestamp = event.timestamp();
        x = event.x() & 0xffff;
        y = event.y() & 0xffff;
        type = event.polarity() ? 1 : 0;
        return true;
    }
}
