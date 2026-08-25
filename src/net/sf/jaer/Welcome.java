/*
 * Copyright (C) SensorsINI / jAER.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 */
package net.sf.jaer;

import java.util.ArrayList;
import java.util.List;

import net.sf.jaer.graphics.AEViewer;

/**
 * Copy for the centered chip-view welcome overlay shown while an
 * {@link AEViewer} is waiting for a device or recorded file.
 * <p>
 * Device advice follows the Interface menu that {@link AEViewer} already
 * builds ({@link AEViewer#getInterfaceMenuDeviceLabels()}):
 * <ul>
 * <li>no devices listed: plug in a camera or open a recording</li>
 * <li>several devices: list those menu labels one per line and point at Interface</li>
 * <li>user chose a camera: {@link #opening(AEViewer, String)} keeps the title
 * and adds opening-progress lines until LIVE or None</li>
 * </ul>
 *
 * @author tobi
 */
public final class Welcome {

    private Welcome() {
    }

    /**
     * Default overlay lines with no viewer context (same as
     * {@link #linesFor(AEViewer) linesFor(null)}).
     *
     * @return non-empty array of overlay lines
     */
    public static String[] defaultLines() {
        return linesFor(null);
    }

    /**
     * Overlay lines for a viewer. Title always includes
     * {@link JaerConstants#getReleaseVersion()}. Device hints come from the
     * viewer's Interface menu snapshot.
     *
     * @param viewer the AEViewer that will show the overlay, or {@code null}
     * @return non-empty array of overlay lines
     */
    public static String[] linesFor(AEViewer viewer) {
        List<String> devices = viewer != null
                ? viewer.getInterfaceMenuDeviceLabels()
                : List.of();
        List<String> lines = new ArrayList<>(5);
        addIfPresent(lines, title(viewer));
        if (devices.isEmpty()) {
            addIfPresent(lines, plugInOrOpenFile(viewer));
        } else if (devices.size() > 1) {
            lines.addAll(chooseCamera(viewer, devices));
        }
        addIfPresent(lines, sampleData(viewer));
        addIfPresent(lines, helpMenu(viewer));
        return lines.toArray(String[]::new);
    }

    /**
     * Overlay while a camera is opening. Keeps {@link #title(AEViewer)} and
     * adds progress lines (not the idle plug-in / sample-data copy).
     *
     * @param viewer used for the title line
     * @param cameraName Interface-menu label or hardware {@code toString}
     * @return overlay lines naming the camera being opened
     */
    public static String[] opening(AEViewer viewer, String cameraName) {
        String name = (cameraName == null || cameraName.isEmpty()) ? "camera" : cameraName;
        List<String> lines = new ArrayList<>(3);
        addIfPresent(lines, title(viewer));
        lines.add("Opening " + name + "…");
        lines.add("USB setup can take several seconds");
        return lines.toArray(String[]::new);
    }

    /**
     * First line, e.g. {@code Welcome to jAER-3.4.0}.
     *
     * @param viewer unused; reserved for per-viewer branding
     * @return title line
     */
    public static String title(AEViewer viewer) {
        return "Welcome to jAER-" + JaerConstants.getReleaseVersion();
    }

    /**
     * Shown when the Interface menu lists no choosable device.
     *
     * @param viewer unused
     * @return hint line
     */
    public static String plugInOrOpenFile(AEViewer viewer) {
        return "Plug in a device or use File/Open recorded data file..";
    }

    /**
     * Shown when the Interface menu lists several devices: a header line, then
     * one indented line per menu label so names fit the overlay width.
     *
     * @param viewer unused
     * @param devices Interface-menu item text from
     * {@link AEViewer#getInterfaceMenuDeviceLabels()}
     * @return hint lines (header plus one line per device)
     */
    public static List<String> chooseCamera(AEViewer viewer, List<String> devices) {
        List<String> out = new ArrayList<>(devices.size() + 1);
        out.add("Multiple devices found. Choose one from Interface menu:");
        for (String device : devices) {
            if (device != null && !device.isEmpty()) {
                out.add("  " + device);
            }
        }
        return out;
    }

    /**
     * Where to find sample recordings.
     *
     * @param viewer unused
     * @return hint line
     */
    public static String sampleData(AEViewer viewer) {
        return "Get sample data via Help / Sample data";
    }

    /**
     * Pointer to the Help menu.
     *
     * @param viewer unused
     * @return hint line
     */
    public static String helpMenu(AEViewer viewer) {
        return "See Help menu for more information";
    }

    private static void addIfPresent(List<String> lines, String line) {
        if (line != null && !line.isEmpty()) {
            lines.add(line);
        }
    }
}
