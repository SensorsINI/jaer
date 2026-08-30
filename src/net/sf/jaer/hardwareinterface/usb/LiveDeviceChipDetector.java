/*
 * LiveDeviceChipDetector.java
 *
 * Match plugged-in USB cameras to AEChip classes via @UsbDevices.
 */
package net.sf.jaer.hardwareinterface.usb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import ch.unizh.ini.jaer.chip.retina.DVXplorer;
import ch.unizh.ini.jaer.chip.retina.DVXplorerMicro;
import net.sf.jaer.UsbDevice;
import net.sf.jaer.UsbDevices;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.DVXplorerFX3HardwareInterface;
import net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.DVXplorerMicroFX3HardwareInterface;
import net.sf.jaer.util.JaerAllowedSubclasses;

/**
 * Resolves which loaded {@link AEChip} classes match a live USB device's
 * VID/PID, using optional {@link UsbDevices} annotations (including
 * {@link java.lang.annotation.Inherited} annotations on superclasses).
 * <p>
 * Parallel to {@link net.sf.jaer.eventio.RecordingChipDetector} for files.
 */
public final class LiveDeviceChipDetector {

    private static final Logger log = Logger.getLogger("net.sf.jaer");

    private LiveDeviceChipDetector() {
    }

    /**
     * All loaded AEChip classes that declare compatibility with this VID/PID.
     */
    public static List<Class<? extends AEChip>> findMatches(short vid, short pid,
            List<String> loadedChipClassNames) {
        if (loadedChipClassNames == null || loadedChipClassNames.isEmpty()) {
            return Collections.emptyList();
        }
        List<Class<? extends AEChip>> matches = new ArrayList<>();
        for (String name : loadedChipClassNames) {
            Class<? extends AEChip> chipClass = loadChipClass(name);
            if (chipClass == null) {
                continue;
            }
            if (declaresVidPid(chipClass, vid, pid)) {
                matches.add(chipClass);
            }
        }
        return matches;
    }

    /**
     * Matches for a hardware interface (peeks VID/PID without full open).
     */
    public static List<Class<? extends AEChip>> findMatches(HardwareInterface hw,
            List<String> loadedChipClassNames) {
        UsbIds.Pair ids = UsbIds.peek(hw);
        if (!ids.isKnown()) {
            return Collections.emptyList();
        }
        List<Class<? extends AEChip>> matches = findMatches(ids.vid, ids.pid, loadedChipClassNames);
        matches.removeIf(c -> !chipCompatibleWithHardware(c, hw));
        return matches;
    }

    /**
     * Sole matching chip among loaded menu entries, or null if zero/many.
     */
    public static Class<? extends AEChip> detectUnique(short vid, short pid,
            List<String> loadedChipClassNames) {
        List<Class<? extends AEChip>> matches = findMatches(vid, pid, loadedChipClassNames);
        if (matches.size() == 1) {
            return matches.get(0);
        }
        return null;
    }

    public static Class<? extends AEChip> detectUnique(HardwareInterface hw,
            List<String> loadedChipClassNames) {
        List<Class<? extends AEChip>> matches = findMatches(hw, loadedChipClassNames);
        if (matches.size() == 1) {
            return matches.get(0);
        }
        return null;
    }

    /** True if this chip class (or an inherited @UsbDevices) lists the pair. */
    public static boolean declaresVidPid(Class<?> chipClass, short vid, short pid) {
        if (chipClass == null) {
            return false;
        }
        UsbDevices devices = chipClass.getAnnotation(UsbDevices.class);
        if (devices == null) {
            return false;
        }
        for (UsbDevice d : devices.value()) {
            if (d.vid() == vid && d.pid() == pid) {
                return true;
            }
        }
        return false;
    }

    public static boolean currentChipMatches(Class<?> currentChipClass, HardwareInterface hw) {
        if (currentChipClass == null || hw == null) {
            return false;
        }
        UsbIds.Pair ids = UsbIds.peek(hw);
        return ids.isKnown() && declaresVidPid(currentChipClass, ids.vid, ids.pid)
                && chipCompatibleWithHardware(currentChipClass, hw);
    }

    /**
     * Same VID/PID is not enough for DVXplorer vs Mini/Micro: factory already
     * chose the HI from {@code bcdDevice}.
     */
    public static boolean chipCompatibleWithHardware(Class<?> chipClass, HardwareInterface hw) {
        if (chipClass == null || hw == null) {
            return false;
        }
        if (hw instanceof DVXplorerMicroFX3HardwareInterface) {
            return DVXplorerMicro.class.isAssignableFrom(chipClass);
        }
        if (hw instanceof DVXplorerFX3HardwareInterface) {
            return DVXplorer.class.isAssignableFrom(chipClass)
                    && !DVXplorerMicro.class.isAssignableFrom(chipClass);
        }
        return true;
    }

    /** True if this class (or a superclass) declares {@link UsbDevices}. */
    public static boolean declaresAnyUsbDevices(Class<?> chipClass) {
        return chipClass != null && chipClass.getAnnotation(UsbDevices.class) != null;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends AEChip> loadChipClass(String fqcn) {
        if (fqcn == null || fqcn.isEmpty()) {
            return null;
        }
        try {
            Class<?> c = JaerAllowedSubclasses.load(fqcn, AEChip.class);
            if (AEChip.class.isAssignableFrom(c)) {
                return (Class<? extends AEChip>) c;
            }
        } catch (ClassNotFoundException e) {
            log.fine("Loaded chip list entry not found: " + fqcn);
        }
        return null;
    }
}
