package net.sf.jaer.chip.multidevice;

import net.sf.jaer.event.BasicEvent;

/**
 * How slots share one chip. {@link #place} runs before filters.
 * {@link #tileOriginX} is display-only and must not be written into
 * {@code event.x} by a tiled relation.
 */
public interface DeviceRelation {

    /** Set the parent canvas size from the current slots. */
    void applyCanvas(MultiDeviceChip chip);

    /**
     * Copy a native event into the merged packet. May add the slot timestamp
     * offset. Must not require {@code nativeEvent} and {@code merged} to be
     * distinct objects.
     */
    void place(DeviceSlot slot, BasicEvent nativeEvent, BasicEvent merged);

    /**
     * Inverse of {@link #place} for a native AEDAT-4 track. Restores file
     * timestamps by subtracting the slot offset.
     */
    void unplace(DeviceSlot slot, BasicEvent merged, BasicEvent nativeOut);

    /** Canvas x of this slot's native origin. Zero when events are already in canvas space. */
    int tileOriginX(MultiDeviceChip chip, DeviceSlot slot);

    /** Canvas y of this slot's native origin. */
    int tileOriginY(MultiDeviceChip chip, DeviceSlot slot);
}
