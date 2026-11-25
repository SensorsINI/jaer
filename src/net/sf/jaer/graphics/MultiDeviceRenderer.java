package net.sf.jaer.graphics;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.multidevice.DeviceRelation;
import net.sf.jaer.chip.multidevice.DeviceSlot;
import net.sf.jaer.chip.multidevice.Modality;
import net.sf.jaer.chip.multidevice.MultiDeviceChip;
import net.sf.jaer.event.BasicEvent;

/**
 * Draws a {@link MultiDeviceChip} by adding each vision slot's tile origin
 * at pixmap index time. Event {@code x,y} stay native for filters.
 */
public class MultiDeviceRenderer extends AEChipRenderer {

    public MultiDeviceRenderer(AEChip chip) {
        super(chip);
    }

    @Override
    protected int displayX(BasicEvent event) {
        return canvasX(chip instanceof MultiDeviceChip ? (MultiDeviceChip) chip : null, event);
    }

    @Override
    protected int displayY(BasicEvent event) {
        return canvasY(chip instanceof MultiDeviceChip ? (MultiDeviceChip) chip : null, event);
    }

    /** Canvas column for {@code event}, including the slot tile origin. */
    public static int canvasX(MultiDeviceChip chip, BasicEvent event) {
        if (event == null) {
            return 0;
        }
        DeviceSlot slot = slotIfVision(chip, event);
        if (slot == null) {
            return event.x;
        }
        DeviceRelation relation = chip.getRelation();
        if (relation == null) {
            return event.x;
        }
        return event.x + relation.tileOriginX(chip, slot);
    }

    /** Canvas row for {@code event}, including the slot tile origin. */
    public static int canvasY(MultiDeviceChip chip, BasicEvent event) {
        if (event == null) {
            return 0;
        }
        DeviceSlot slot = slotIfVision(chip, event);
        if (slot == null) {
            return event.y;
        }
        DeviceRelation relation = chip.getRelation();
        if (relation == null) {
            return event.y;
        }
        return event.y + relation.tileOriginY(chip, slot);
    }

    private static DeviceSlot slotIfVision(MultiDeviceChip chip, BasicEvent event) {
        if (chip == null || event == null) {
            return null;
        }
        DeviceSlot slot = chip.slot(event);
        if (slot == null || slot.getModality() != Modality.VISION) {
            return null;
        }
        return slot;
    }
}
