package ch.unizh.ini.jaer.chip;

import java.util.ArrayList;
import java.util.logging.Logger;

import net.sf.jaer.chip.Chip2D;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.ChipRendererDisplayMethod;
import net.sf.jaer.graphics.ChipRendererDisplayMethodRGBA;
import net.sf.jaer.graphics.DisplayMethod;

/**
 * Configures pure event DVS chips (no intensity frames) to use {@link ChipRendererDisplayMethod}
 * and clears any stale {@link ChipRendererDisplayMethodRGBA} preference from prior sessions.
 */
public final class EventOnlyChipDisplay {

    private static final Logger log = Logger.getLogger("net.sf.jaer");

    private EventOnlyChipDisplay() {
    }

    public static void apply(Chip2D chip) {
        clearRgbaPreference(chip);
        final ChipCanvas canvas = chip.getCanvas();
        if (canvas == null) {
            return;
        }
        final ArrayList<DisplayMethod> registered = new ArrayList<>(canvas.getDisplayMethods());
        for (DisplayMethod method : registered) {
            if (method instanceof ChipRendererDisplayMethodRGBA) {
                canvas.removeDisplayMethod(method);
            }
        }
        ChipRendererDisplayMethod eventMethod = findEventDisplayMethod(canvas);
        if (eventMethod == null) {
            eventMethod = new ChipRendererDisplayMethod(canvas);
            canvas.addDisplayMethod(eventMethod);
        }
        final DisplayMethod active = canvas.getDisplayMethod();
        // Only replace RGBA or unset methods — leave 3D display methods (SpaceTime*, etc.) alone.
        if (active == null || active instanceof ChipRendererDisplayMethodRGBA) {
            canvas.setDisplayMethod(eventMethod);
        }
        log.fine("Event-only display for " + chip.getName() + ": "
                + canvas.getDisplayMethod().getClass().getSimpleName());
    }

    public static void clearRgbaPreference(Chip2D chip) {
        final String prefKey = chip.getClass().getSimpleName() + ".preferredDisplayMethod";
        final String saved = chip.getPrefs().get(prefKey, null);
        if (saved != null && saved.contains("ChipRendererDisplayMethodRGBA")) {
            log.fine("Clearing stale RGBA display preference for " + chip.getName());
            chip.getPrefs().remove(prefKey);
        }
    }

    private static ChipRendererDisplayMethod findEventDisplayMethod(ChipCanvas canvas) {
        for (DisplayMethod method : canvas.getDisplayMethods()) {
            if (method instanceof ChipRendererDisplayMethod && !(method instanceof ChipRendererDisplayMethodRGBA)) {
                return (ChipRendererDisplayMethod) method;
            }
        }
        return null;
    }
}
