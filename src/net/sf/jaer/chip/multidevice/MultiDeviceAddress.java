package net.sf.jaer.chip.multidevice;

/**
 * Raw address for a {@link MultiDeviceChip} playback slice.
 * <p>
 * Bits 0–11 are native {@code x}, 12–23 native {@code y}, bit 24 polarity
 * (1 = On), bits 25–30 the slot source (0–63). Bit 31 stays clear so the
 * address is not a {@link net.sf.jaer.event.BasicEvent} special event.
 * Davis addresses already use bit 31 and the high Y bits, so the source
 * cannot ride in a child-chip address.
 */
public final class MultiDeviceAddress {

    public static final int X_MASK = 0xfff;
    public static final int Y_SHIFT = 12;
    public static final int Y_MASK = 0xfff << Y_SHIFT;
    public static final int TYPE_SHIFT = 24;
    public static final int SOURCE_SHIFT = 25;
    public static final int SOURCE_MASK = 0x3f << SOURCE_SHIFT;
    /** Largest source index that fits in {@link #SOURCE_MASK}. */
    public static final int MAX_SOURCE = 63;

    private MultiDeviceAddress() {
    }

    public static int pack(int x, int y, int type, int source) {
        return (x & X_MASK)
                | ((y & 0xfff) << Y_SHIFT)
                | ((type & 1) << TYPE_SHIFT)
                | ((source & 0x3f) << SOURCE_SHIFT);
    }

    public static int x(int address) {
        return address & X_MASK;
    }

    public static int y(int address) {
        return (address & Y_MASK) >>> Y_SHIFT;
    }

    /** 1 = On, 0 = Off. Matches AEDAT-4 {@code event.polarity()}. */
    public static int type(int address) {
        return (address >>> TYPE_SHIFT) & 1;
    }

    public static int source(int address) {
        return (address & SOURCE_MASK) >>> SOURCE_SHIFT;
    }
}
