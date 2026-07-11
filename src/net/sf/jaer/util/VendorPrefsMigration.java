package net.sf.jaer.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Migrates NRV and Prophesee preferences from pre-relocation package paths.
 */
public final class VendorPrefsMigration {

    private static final Logger log = Logger.getLogger("net.sf.jaer");

    public static final String LEGACY_NRV_CHIP_PACKAGE = "ch/unizh/ini/jaer/chip/nrv";
    public static final String LEGACY_PROPHESEE_CHIP_PACKAGE = "ch/unizh/ini/jaer/chip/prophesee";
    public static final String LEGACY_NRV_HW_PACKAGE = "net/sf/jaer/hardwareinterface/usb/nrv";
    public static final String LEGACY_PROPHESEE_HW_PACKAGE = "net/sf/jaer/hardwareinterface/usb/prophesee";

    private VendorPrefsMigration() {
    }

    /** Returns {@code legacyPackagePath} if that prefs node still exists. */
    public static String legacyChipPrefsPackage(String legacyPackagePath) {
        try {
            if (Preferences.userRoot().nodeExists(legacyPackagePath)) {
                return legacyPackagePath;
            }
        } catch (BackingStoreException ex) {
            log.warning(ex.toString());
        }
        return null;
    }

    /** Copies keys from a legacy hardware prefs node into {@code target}, without overwriting. */
    public static void migrateHardwarePrefs(String legacyPackagePath, Preferences target) {
        try {
            if (!Preferences.userRoot().nodeExists(legacyPackagePath)) {
                return;
            }
            final Preferences legacy = Preferences.userRoot().node(legacyPackagePath);
            int moved = 0;
            for (String key : legacy.keys()) {
                if (target.get(key, null) == null) {
                    target.put(key, legacy.get(key, null));
                    moved++;
                }
                legacy.remove(key);
            }
            if (moved > 0) {
                log.info(String.format("Migrated %d hardware preference keys from %s to %s",
                        moved, legacy.absolutePath(), target.absolutePath()));
            }
        } catch (BackingStoreException ex) {
            log.warning(ex.toString());
        }
    }

    /** Rewrites legacy package node names in exported Java preferences XML. */
    public static InputStream rewriteLegacyPreferencesXml(InputStream is) throws IOException {
        final String xml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        final String rewritten = xml
                .replace(LEGACY_NRV_CHIP_PACKAGE, "nrv/chip")
                .replace(LEGACY_PROPHESEE_CHIP_PACKAGE, "prophesee/chip")
                .replace(LEGACY_NRV_HW_PACKAGE, "jaer/hardware/NRV")
                .replace(LEGACY_PROPHESEE_HW_PACKAGE, "jaer/hardware/Prophesee")
                .replace("ch.unizh.ini.jaer.chip.nrv", "nrv.chip")
                .replace("ch.unizh.ini.jaer.chip.prophesee", "prophesee.chip")
                .replace("net.sf.jaer.hardwareinterface.usb.nrv", "nrv.usb")
                .replace("net.sf.jaer.hardwareinterface.usb.prophesee", "prophesee.usb");
        return new ByteArrayInputStream(rewritten.getBytes(StandardCharsets.UTF_8));
    }
}
