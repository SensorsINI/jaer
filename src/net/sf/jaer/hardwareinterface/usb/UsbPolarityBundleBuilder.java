package net.sf.jaer.hardwareinterface.usb;

import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.event.PolarityEvent;

/**
 * Shared helper for polarity-only USB demux (NRV, Prophesee, DVS128): fill a
 * pooled {@link PacketBundle} write buffer with {@link PolarityEvent}s.
 * <p>
 * Accumulates into one polarity packet for the write-buffer lifetime (across
 * USB callbacks). Reuses one {@link EventPacket} per pool slot so ViewLoop
 * swaps do not allocate multi-meg event arrays (GC pauses at high rate).
 */
public class UsbPolarityBundleBuilder {

    private PacketBundle out;
    private PacketBundle slot0;
    private PacketBundle slot1;
    private EventPacket<PolarityEvent> polarity0;
    private EventPacket<PolarityEvent> polarity1;
    private EventPacket<PolarityEvent> polarity;
    private EventPacket<PolarityEvent> fill;
    private OutputEventIterator<PolarityEvent> polarityOut;
    private boolean polarityInBundle;
    private int targetCapacity;

    /**
     * Pre-size both pool-slot polarity packets so live USB does not double
     * {@link EventPacket} capacity (that allocates hundreds of thousands of
     * {@link PolarityEvent}s and looks like GC pauses).
     */
    public void ensureCapacity(int n) {
        if (n <= 0) {
            return;
        }
        targetCapacity = Math.max(targetCapacity, n);
        if (polarity0 == null) {
            polarity0 = new EventPacket<>(PolarityEvent.class);
        }
        polarity0.allocate(targetCapacity);
        if (polarity1 == null) {
            polarity1 = new EventPacket<>(PolarityEvent.class);
        }
        polarity1.allocate(targetCapacity);
        if (fill == null) {
            fill = new EventPacket<>(PolarityEvent.class);
        }
        fill.allocate(targetCapacity);
    }

    public void attach(PacketBundle writeBundle) {
        if (writeBundle == null) {
            return;
        }
        if (writeBundle != this.out) {
            this.out = writeBundle;
            polarity = packetForSlot(writeBundle);
            // Reset size; keep grown capacity and PolarityEvent instances.
            polarityOut = polarity.outputIterator();
            polarityInBundle = false;
        }
    }

    private EventPacket<PolarityEvent> packetForSlot(PacketBundle writeBundle) {
        if (slot0 == null || writeBundle == slot0) {
            slot0 = writeBundle;
            if (polarity0 == null) {
                polarity0 = new EventPacket<>(PolarityEvent.class);
            }
            if (targetCapacity > 0) {
                polarity0.allocate(targetCapacity);
            }
            return polarity0;
        }
        if (slot1 == null || writeBundle == slot1) {
            slot1 = writeBundle;
            if (polarity1 == null) {
                polarity1 = new EventPacket<>(PolarityEvent.class);
            }
            if (targetCapacity > 0) {
                polarity1.allocate(targetCapacity);
            }
            return polarity1;
        }
        // Unexpected third identity (pool reallocated): remap slot0.
        slot0 = writeBundle;
        if (polarity0 == null) {
            polarity0 = new EventPacket<>(PolarityEvent.class);
        }
        if (targetCapacity > 0) {
            polarity0.allocate(targetCapacity);
        }
        return polarity0;
    }

    public void addPolarity(final int x, final int y, final boolean on, final int timestamp) {
        ensureActive();
        PolarityEvent e = polarityOut.nextOutput();
        e.reset();
        e.timestamp = timestamp;
        e.x = (short) x;
        e.y = (short) y;
        e.polarity = on ? PolarityEvent.Polarity.On : PolarityEvent.Polarity.Off;
        e.type = (byte) (on ? 1 : 0);
        e.setSpecial(false);
    }

    /**
     * Sync / external special event (DVS128): polarity packet with
     * {@link PolarityEvent#isSpecial()} set and invalid x/y.
     */
    public void addSpecial(final int address, final int timestamp) {
        ensureActive();
        PolarityEvent e = polarityOut.nextOutput();
        e.reset();
        e.timestamp = timestamp;
        e.address = address;
        e.x = -1;
        e.y = -1;
        e.type = -1;
        e.polarity = PolarityEvent.Polarity.On;
        e.setSpecial(true);
    }

    /**
     * Decode packed raw AE addresses into cooked polarity using the same layout
     * as {@link net.sf.jaer.chip.TypedEventExtractor}.
     */
    public void addPacked(final int[] addresses, final int[] timestamps, final int start, final int n,
            final int xMask, final int xShift, final int yMask, final int yShift,
            final int typeMask, final int typeShift,
            final boolean flipX, final boolean flipY, final boolean flipType,
            final int sizeX, final int sizeY) {
        if (n <= 0 || addresses == null || timestamps == null) {
            return;
        }
        final int sxm = sizeX > 0 ? sizeX - 1 : 0;
        final int sym = sizeY > 0 ? sizeY - 1 : 0;
        for (int i = 0; i < n; i++) {
            final int addr = addresses[start + i];
            final int ts = timestamps[start + i];
            int x = (addr & xMask) >>> xShift;
            int y = (addr & yMask) >>> yShift;
            int type = (addr & typeMask) >>> typeShift;
            if (flipX) {
                x = sxm - x;
            }
            if (flipY) {
                y = sym - y;
            }
            if (flipType) {
                type = 1 - type;
            }
            addPolarity(x, y, type != 0, ts);
        }
    }

    /**
     * Decode packed addresses into a private packet (not the pool write buffer).
     * Call off the {@code AEPacketRawPool} lock, then {@link #installFill}.
     */
    public void fillPackedOffline(final int[] addresses, final int[] timestamps, final int start, final int n,
            final int xMask, final int xShift, final int yMask, final int yShift,
            final int typeMask, final int typeShift,
            final boolean flipX, final boolean flipY, final boolean flipType,
            final int sizeX, final int sizeY) {
        if (n <= 0 || addresses == null || timestamps == null) {
            return;
        }
        if (fill == null) {
            fill = new EventPacket<>(PolarityEvent.class);
        }
        if (targetCapacity > 0) {
            fill.allocate(targetCapacity);
        }
        final EventPacket<PolarityEvent> saved = polarity;
        final OutputEventIterator<PolarityEvent> savedOut = polarityOut;
        final boolean savedIn = polarityInBundle;
        polarity = fill;
        polarityOut = fill.outputIterator();
        polarityInBundle = true;
        try {
            addPacked(addresses, timestamps, start, n,
                    xMask, xShift, yMask, yShift, typeMask, typeShift,
                    flipX, flipY, flipType, sizeX, sizeY);
        } finally {
            polarity = saved;
            polarityOut = savedOut;
            polarityInBundle = savedIn;
        }
    }

    /**
     * Install the offline fill into the pool write bundle. Empty write slot
     * swaps backing arrays in O(1); a partial packet appends by {@code copyFrom}.
     */
    public void installFill(PacketBundle writeBundle) {
        installFill(writeBundle, fill != null ? fill.getSize() : 0);
    }

    public void installFill(PacketBundle writeBundle, int maxEvents) {
        if (writeBundle == null || fill == null || fill.isEmpty() || maxEvents <= 0) {
            return;
        }
        if (fill.size > maxEvents) {
            fill.size = maxEvents;
        }
        attach(writeBundle);
        if (polarity.isEmpty()) {
            polarity.swapBackingStore(fill);
            polarityOut = null;
            polarityInBundle = false;
            flushAll();
            return;
        }
        final OutputEventIterator<PolarityEvent> out = polarity.getOutputIterator();
        final int n = fill.getSize();
        for (int i = 0; i < n; i++) {
            out.nextOutput().copyFrom(fill.getEvent(i));
        }
    }

    /**
     * Ensure the working polarity packet is in the write bundle. Safe to call
     * every USB chunk — adds once, then grows in place until pool swap.
     */
    public void flushAll() {
        if (out == null || polarity == null || polarity.isEmpty() || polarityInBundle) {
            return;
        }
        out.add(polarity);
        polarityInBundle = true;
    }

    private void ensureActive() {
        if (polarity == null) {
            polarity = new EventPacket<>(PolarityEvent.class);
            polarityOut = polarity.outputIterator();
            polarityInBundle = false;
        } else if (polarityOut == null) {
            polarityOut = polarity.isEmpty() ? polarity.outputIterator() : polarity.getOutputIterator();
        }
    }
}
