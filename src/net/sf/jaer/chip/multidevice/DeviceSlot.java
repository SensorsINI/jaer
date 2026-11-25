package net.sf.jaer.chip.multidevice;

import net.sf.jaer.chip.AEChip;

/**
 * One sensor owned by a {@link MultiDeviceChip}. The child chip supplies
 * native size and, later, biases. It is not given its own {@code AEViewer}
 * or filter chain.
 */
public final class DeviceSlot {

    private final String id;
    private final Class<? extends AEChip> chipClass;
    private final AEChip child;
    private final Modality modality;
    private final byte source;
    private String sourceName;
    private int timestampOffsetUs;
    /** AEDAT-4 EVTS stream bound for playback, or {@code -1}. */
    private int boundStreamId = -1;

    DeviceSlot(String id, Class<? extends AEChip> chipClass, AEChip child, Modality modality, byte source) {
        this.id = id;
        this.chipClass = chipClass;
        this.child = child;
        this.modality = modality == null ? Modality.VISION : modality;
        this.source = source;
    }

    public String getId() {
        return id;
    }

    public Class<? extends AEChip> getChipClass() {
        return chipClass;
    }

    public AEChip getChild() {
        return child;
    }

    public Modality getModality() {
        return modality;
    }

    /** Value written to {@link net.sf.jaer.event.BasicEvent#source}. */
    public byte getSource() {
        return source;
    }

    /**
     * AEDAT-4 {@code source} string to match, or null to take the next
     * unbound EVTS stream. Matching is case-insensitive containment when
     * the name is at least 3 characters, or exact equality.
     */
    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    /** Added to file timestamps before the filter chain. Subtracted on record. */
    public int getTimestampOffsetUs() {
        return timestampOffsetUs;
    }

    public void setTimestampOffsetUs(int timestampOffsetUs) {
        this.timestampOffsetUs = timestampOffsetUs;
    }

    public int getBoundStreamId() {
        return boundStreamId;
    }

    void setBoundStreamId(int boundStreamId) {
        this.boundStreamId = boundStreamId;
    }
}
