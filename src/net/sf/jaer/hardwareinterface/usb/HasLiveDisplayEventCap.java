package net.sf.jaer.hardwareinterface.usb;

/**
 * Optional live-view keep limit for high-rate USB readers (currently Prophesee).
 * Caps how many polarity events are committed into each ViewLoop packet so a
 * large AE buffer does not hitch rendering. AEDAT logging uses the same packet,
 * so raising this also raises how many events a recording can keep per frame.
 * <p>
 * Drivers that do not implement this interface have no separate live keep limit;
 * their AE buffer size alone bounds the packet.
 */
public interface HasLiveDisplayEventCap {

    /** Default keep limit chosen to protect live ViewLoop hitching. */
    int DEFAULT_LIVE_DISPLAY_EVENT_CAP = 1 << 18; // 262,144

    int getLiveDisplayEventCap();

    void setLiveDisplayEventCap(int events);

    /** Inclusive lower bound for {@link #setLiveDisplayEventCap(int)}. */
    default int getMinLiveDisplayEventCap() {
        return 1 << 16; // 65,536
    }

    /** Inclusive upper bound for {@link #setLiveDisplayEventCap(int)}. */
    default int getMaxLiveDisplayEventCap() {
        return 1 << 23; // 8,388,608
    }
}
