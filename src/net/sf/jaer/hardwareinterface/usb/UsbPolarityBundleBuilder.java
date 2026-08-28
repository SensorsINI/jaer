package net.sf.jaer.hardwareinterface.usb;

import net.sf.jaer.event.AcquisitionMetadata;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.event.PacketType;
import net.sf.jaer.event.PolarityEvent;

/**
 * Shared helper for polarity-only USB demux (NRV, Prophesee, DVS128, DVX): fill a
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
    private boolean authoritative;
    private AcquisitionMetadata authoritativeMetadata;
    private int authoritativeCapacity;
    private int authoritativeAcceptedElements;
    private long authoritativeTimestampEpoch;
    private long pendingHostCapacityLoss;

    /**
     * Pre-size both pool-slot polarity packets so live USB does not double
     * {@link EventPacket} capacity (that allocates hundreds of thousands of
     * {@link PolarityEvent}s and looks like GC pauses).
     */
    public synchronized void ensureCapacity(int n) {
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
    }

    public synchronized void attach(PacketBundle writeBundle) {
        authoritative = false;
        attachPacket(writeBundle);
    }

    /**
     * Attaches an authoritative write bundle and resets accounting when its
     * acquisition-metadata identity changes. Repeated USB callbacks for the same
     * write slot continue growing the same packet and share one capacity budget.
     */
    public synchronized void beginAuthoritative(PacketBundle writeBundle,
            int capacity, long timestampEpoch) {
        if (writeBundle == null) {
            throw new NullPointerException("writeBundle");
        }
        if (capacity < 0) {
            throw new IllegalArgumentException("authoritative capacity must be non-negative");
        }
        if (timestampEpoch < 0) {
            throw new IllegalArgumentException("timestamp epoch must be non-negative");
        }
        final AcquisitionMetadata metadata = writeBundle.getAcquisitionMetadata();
        if (metadata == null) {
            throw new IllegalStateException("authoritative write bundle has no acquisition metadata");
        }
        attachPacket(writeBundle);
        if (metadata != authoritativeMetadata) {
            authoritativeMetadata = metadata;
            authoritativeAcceptedElements = 0;
            pendingHostCapacityLoss = 0;
        }
        authoritative = true;
        authoritativeCapacity = capacity;
        authoritativeTimestampEpoch = timestampEpoch;
    }

    private void attachPacket(PacketBundle writeBundle) {
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

    public synchronized void addPolarity(final int x, final int y, final boolean on, final int timestamp) {
        addPolarity(x, y, on, timestamp, 0);
    }

    public synchronized void addPolarity(final int x, final int y, final boolean on, final int timestamp, final int address) {
        addPolarityUnchecked(x, y, on, timestamp, address);
    }

    /**
     * Adds one authoritative polarity event, or records one exact host-capacity
     * loss while still allowing the decoder to advance its timestamp state.
     */
    public synchronized boolean addAuthoritativePolarity(final int x, final int y,
            final boolean on, final int timestamp, final int address) {
        if (!authoritative) {
            throw new IllegalStateException("authoritative polarity added before beginAuthoritative");
        }
        if (authoritativeAcceptedElements >= authoritativeCapacity) {
            pendingHostCapacityLoss = Math.addExact(pendingHostCapacityLoss, 1L);
            return false;
        }
        ensureActive();
        if (polarity.isEmpty()) {
            polarity.setTimestampEpoch(authoritativeTimestampEpoch);
        }
        addPolarityUnchecked(x, y, on, timestamp, address);
        authoritativeAcceptedElements++;
        return true;
    }

    private void addPolarityUnchecked(final int x, final int y, final boolean on,
            final int timestamp, final int address) {
        ensureActive();
        PolarityEvent e = polarityOut.nextOutput();
        e.reset();
        e.timestamp = timestamp;
        e.x = (short) x;
        e.y = (short) y;
        e.polarity = on ? PolarityEvent.Polarity.On : PolarityEvent.Polarity.Off;
        e.type = (byte) (on ? 1 : 0);
        e.setSpecial(false);
        e.address = address;
    }

    /** Flushes the old epoch packet and starts a fresh one in the same bundle. */
    public synchronized void onTimestampReset(final long timestampEpoch) {
        if (!authoritative) {
            return;
        }
        if (timestampEpoch <= authoritativeTimestampEpoch) {
            throw new IllegalArgumentException("timestamp epoch must advance");
        }
        flushAll();
        authoritativeTimestampEpoch = timestampEpoch;
        polarity = new EventPacket<>(PolarityEvent.class);
        polarityOut = polarity.outputIterator();
        polarityInBundle = false;
    }

    /**
     * Sync / external special event (DVS128): polarity packet with
     * {@link PolarityEvent#isSpecial()} set and invalid x/y.
     */
    public synchronized void addSpecial(final int address, final int timestamp) {
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
    public synchronized void addPacked(final int[] addresses, final int[] timestamps, final int start, final int n,
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
            addPolarity(x, y, type != 0, ts, addr);
        }
    }

    /**
     * Decode packed addresses into a private packet (not the pool write buffer).
     * Call off the {@code AEPacketRawPool} lock, then {@link #installFill}.
     */
    public synchronized void fillPackedOffline(final int[] addresses, final int[] timestamps, final int start, final int n,
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
    public synchronized void installFill(PacketBundle writeBundle) {
        installFill(writeBundle, fill != null ? fill.getSize() : 0);
    }

    public synchronized void installFill(PacketBundle writeBundle, int maxEvents) {
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
    public synchronized void flushAll() {
        if (out == null) {
            return;
        }
        if (polarity != null && !polarity.isEmpty() && !polarityInBundle) {
            out.add(polarity);
            polarityInBundle = true;
        }
        if (authoritative && pendingHostCapacityLoss > 0) {
            final AcquisitionMetadata metadata = out.getAcquisitionMetadata();
            if (metadata == null) {
                throw new IllegalStateException("authoritative capacity loss has no acquisition metadata");
            }
            metadata.recordExactLoss(PacketType.POLARITY,
                    AcquisitionMetadata.LossKind.HOST_CAPACITY,
                    pendingHostCapacityLoss,
                    "DVX authoritative typed per-bundle capacity exhausted");
            pendingHostCapacityLoss = 0;
        }
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
