package net.sf.jaer.chip.multidevice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * Per-slot viewing direction. Metadata for filters that label a face or
 * triangulate. Not written into event {@code x,y}.
 */
public final class ViewingPose {

    public static final class SlotPose {
        public float azimuthDeg;
        public float elevationDeg;
        public float baselineMm;
    }

    private final Map<String, SlotPose> bySlot = new LinkedHashMap<>();

    public SlotPose poseFor(String slotId) {
        String key = slotId == null ? "" : slotId;
        SlotPose pose = bySlot.get(key);
        if (pose == null) {
            pose = new SlotPose();
            bySlot.put(key, pose);
        }
        return pose;
    }

    public void load(Preferences prefs, List<DeviceSlot> slots) {
        if (prefs == null || slots == null) {
            return;
        }
        for (DeviceSlot slot : slots) {
            if (slot == null) {
                continue;
            }
            SlotPose pose = poseFor(slot.getId());
            String prefix = prefix(slot.getId());
            pose.azimuthDeg = prefs.getFloat(prefix + "azimuthDeg", pose.azimuthDeg);
            pose.elevationDeg = prefs.getFloat(prefix + "elevationDeg", pose.elevationDeg);
            pose.baselineMm = prefs.getFloat(prefix + "baselineMm", pose.baselineMm);
            slot.setTimestampOffsetUs(prefs.getInt(offsetKey(slot.getId()), slot.getTimestampOffsetUs()));
        }
    }

    public void store(Preferences prefs, List<DeviceSlot> slots) {
        if (prefs == null || slots == null) {
            return;
        }
        for (DeviceSlot slot : slots) {
            if (slot == null) {
                continue;
            }
            SlotPose pose = poseFor(slot.getId());
            String prefix = prefix(slot.getId());
            prefs.putFloat(prefix + "azimuthDeg", pose.azimuthDeg);
            prefs.putFloat(prefix + "elevationDeg", pose.elevationDeg);
            prefs.putFloat(prefix + "baselineMm", pose.baselineMm);
            prefs.putInt(offsetKey(slot.getId()), slot.getTimestampOffsetUs());
        }
    }

    static String prefix(String slotId) {
        return "pose." + (slotId == null ? "" : slotId) + ".";
    }

    static String offsetKey(String slotId) {
        return "slot." + (slotId == null ? "" : slotId) + ".timestampOffsetUs";
    }
}
