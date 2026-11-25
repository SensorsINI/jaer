package net.sf.jaer.chip.multidevice;

import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.PolarityEvent;

/**
 * Vision slots sit side by side on the canvas. Filter events keep each
 * camera's {@code x,y}; {@link BasicEvent#source} is the slot. The renderer
 * adds {@link #tileOriginX} only when indexing the pixmap.
 */
public final class TiledNativeRelation implements DeviceRelation {

    @Override
    public void applyCanvas(MultiDeviceChip chip) {
        int width = 0;
        int height = 0;
        if (chip != null) {
            for (DeviceSlot slot : chip.getSlots()) {
                if (!isVision(slot) || slot.getChild() == null) {
                    continue;
                }
                width += Math.max(0, slot.getChild().getSizeX());
                height = Math.max(height, slot.getChild().getSizeY());
            }
        }
        if (width <= 0) {
            width = 1;
        }
        if (height <= 0) {
            height = 1;
        }
        chip.setSizeX(width);
        chip.setSizeY(height);
    }

    @Override
    public void place(DeviceSlot slot, BasicEvent nativeEvent, BasicEvent merged) {
        if (slot == null || nativeEvent == null || merged == null) {
            return;
        }
        int x = nativeEvent.x;
        int y = nativeEvent.y;
        int timestamp = nativeEvent.timestamp + slot.getTimestampOffsetUs();
        int address = nativeEvent.address;
        merged.x = (short) x;
        merged.y = (short) y;
        merged.timestamp = timestamp;
        merged.address = address;
        merged.source = slot.getSource();
        copyPolarity(nativeEvent, merged);
    }

    @Override
    public void unplace(DeviceSlot slot, BasicEvent merged, BasicEvent nativeOut) {
        if (slot == null || merged == null || nativeOut == null) {
            return;
        }
        int x = merged.x;
        int y = merged.y;
        int timestamp = merged.timestamp - slot.getTimestampOffsetUs();
        nativeOut.x = (short) x;
        nativeOut.y = (short) y;
        nativeOut.timestamp = timestamp;
        nativeOut.address = merged.address;
        nativeOut.source = slot.getSource();
        copyPolarity(merged, nativeOut);
    }

    @Override
    public int tileOriginX(MultiDeviceChip chip, DeviceSlot slot) {
        if (chip == null || slot == null || !isVision(slot)) {
            return 0;
        }
        int origin = 0;
        for (DeviceSlot other : chip.getSlots()) {
            if (other == slot) {
                return origin;
            }
            if (isVision(other) && other.getChild() != null) {
                origin += Math.max(0, other.getChild().getSizeX());
            }
        }
        return 0;
    }

    @Override
    public int tileOriginY(MultiDeviceChip chip, DeviceSlot slot) {
        return 0;
    }

    private static boolean isVision(DeviceSlot slot) {
        return slot != null && slot.getModality() == Modality.VISION;
    }

    private static void copyPolarity(BasicEvent from, BasicEvent to) {
        if (from instanceof PolarityEvent && to instanceof PolarityEvent && from != to) {
            ((PolarityEvent) to).setPolarity(((PolarityEvent) from).getPolarity());
        }
    }
}
