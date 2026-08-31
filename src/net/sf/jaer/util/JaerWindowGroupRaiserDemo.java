package net.sf.jaer.util;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Headless checks for {@link JaerWindowGroupRaiser} raise policy and wiring.
 * Run after {@code ant compile}:
 * {@code java -cp build/classes;jars/*;lib/* net.sf.jaer.util.JaerWindowGroupRaiserDemo}
 */
public final class JaerWindowGroupRaiserDemo {

    public static void main(String[] args) throws Exception {
        testRaisePolicy();
        testWiring();
        System.out.println("ALL PASS");
    }

    private static void testRaisePolicy() {
        assertTrue(JaerWindowGroupRaiser.shouldRaiseOther(
                true, true, false, true, true, true, false, false),
                "visible sibling Frame is raised");
        assertTrue(JaerWindowGroupRaiser.shouldRaiseOther(
                true, false, true, true, true, true, false, false),
                "visible sibling Dialog is raised");
        assertTrue(!JaerWindowGroupRaiser.shouldRaiseOther(
                false, true, false, true, true, true, false, false),
                "popup / JWindow focus does not raise");
        assertTrue(!JaerWindowGroupRaiser.shouldRaiseOther(
                true, false, false, true, true, true, false, false),
                "non-frame/dialog windows are not raised");
        assertTrue(!JaerWindowGroupRaiser.shouldRaiseOther(
                true, true, false, false, true, false, false, false),
                "hidden windows are not raised");
        assertTrue(!JaerWindowGroupRaiser.shouldRaiseOther(
                true, true, false, true, true, true, false, true),
                "iconified frames stay minimized");
        assertTrue(!JaerWindowGroupRaiser.shouldRaiseOther(
                true, true, false, true, true, true, true, false),
                "always-on-top windows are skipped");
        System.out.println("PASS testRaisePolicy");
    }

    private static void testWiring() throws Exception {
        String raiser = Files.readString(
                Paths.get("src", "net", "sf", "jaer", "util", "JaerWindowGroupRaiser.java"),
                StandardCharsets.UTF_8);
        require(raiser.contains("WINDOW_ACTIVATED"),
                "raises on WINDOW_ACTIVATED, not every internal focus change");
        require(raiser.contains("setAutoRequestFocus(false)"),
                "siblings are raised without stealing focus");
        require(raiser.contains("invokeLater"),
                "raising flag clears after nested activation events");

        String jv = Files.readString(
                Paths.get("src", "net", "sf", "jaer", "JAERViewer.java"),
                StandardCharsets.UTF_8);
        require(jv.contains("JaerWindowGroupRaiser.install()"),
                "JAERViewer installs the group raiser");
        require(jv.contains("EVENT_RAISE_ALL_WINDOWS_ON_FOCUS"),
                "sibling AEViewers stay in sync for the prefs toggle");

        String viewer = Files.readString(
                Paths.get("src", "net", "sf", "jaer", "graphics", "AEViewer.java"),
                StandardCharsets.UTF_8);
        require(viewer.contains("PREF_RAISE_ALL_WINDOWS_ON_FOCUS"),
                "AEViewer exposes the prefs key");
        require(viewer.contains("setRaiseAllWindowsOnFocus"),
                "AEViewer setter updates the raiser");

        Path prefsDlg = Paths.get("src", "net", "sf", "jaer", "graphics",
                "AEViewerPreferencesDialog.java");
        String dlg = Files.readString(prefsDlg, StandardCharsets.UTF_8);
        require(dlg.contains("raiseAllWindowsOnFocusCB"),
                "Preferences has the raise-all-windows checkbox");
        require(dlg.contains("Raise all jAER windows"),
                "checkbox label is discoverable");
        System.out.println("PASS testWiring");
    }

    private static void require(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }
}
