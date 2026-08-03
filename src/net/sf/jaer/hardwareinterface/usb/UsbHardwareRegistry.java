/*
 * UsbHardwareRegistry.java
 *
 * Central VID/PID → HardwareInterface class map shared by libusb factories
 * and live AEChip matching.
 */
package net.sf.jaer.hardwareinterface.usb;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.apache.commons.lang3.tuple.ImmutablePair;

import net.sf.jaer.hardwareinterface.HardwareInterface;

/**
 * Singleton registry of USB vendor/product IDs to {@link HardwareInterface}
 * implementation classes. Libusb factories register their supported devices
 * here; {@link net.sf.jaer.graphics.AEViewer} and
 * {@link LiveDeviceChipDetector} use it for lookups.
 */
public final class UsbHardwareRegistry {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final UsbHardwareRegistry INSTANCE = new UsbHardwareRegistry();

    private final Map<ImmutablePair<Short, Short>, Class<? extends HardwareInterface>> map
            = new ConcurrentHashMap<>();

    private UsbHardwareRegistry() {
    }

    public static UsbHardwareRegistry instance() {
        return INSTANCE;
    }

    /**
     * Register a VID/PID → interface class mapping. Later registrations for the
     * same pair replace earlier ones and log a warning.
     */
    @SuppressWarnings("unchecked")
    public void register(short vid, short pid, Class<?> interfaceClass) {
        if (interfaceClass == null || !HardwareInterface.class.isAssignableFrom(interfaceClass)) {
            throw new IllegalArgumentException("Not a HardwareInterface class: " + interfaceClass);
        }
        ImmutablePair<Short, Short> key = new ImmutablePair<>(vid, pid);
        Class<? extends HardwareInterface> hiClass = (Class<? extends HardwareInterface>) interfaceClass;
        Class<? extends HardwareInterface> prev = map.put(key, hiClass);
        if (prev != null && prev != hiClass) {
            log.warning(String.format("USB VID/PID %04x:%04x remapped from %s to %s",
                    vid & 0xffff, pid & 0xffff, prev.getSimpleName(), hiClass.getSimpleName()));
        } else if (prev == null) {
            log.info(String.format("USB VID/PID %04x:%04x → %s",
                    vid & 0xffff, pid & 0xffff, hiClass.getSimpleName()));
        }
    }

    /** @return interface class for this VID/PID, or null if unknown */
    public Class<? extends HardwareInterface> interfaceClassFor(short vid, short pid) {
        return map.get(new ImmutablePair<>(vid, pid));
    }

    public boolean isSupported(short vid, short pid) {
        return map.containsKey(new ImmutablePair<>(vid, pid));
    }

    /** Unmodifiable view of registered mappings. */
    public Map<ImmutablePair<Short, Short>, Class<? extends HardwareInterface>> entries() {
        return Collections.unmodifiableMap(map);
    }
}
