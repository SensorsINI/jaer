package net.sf.jaer.graphics;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.prefs.Preferences;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import net.sf.jaer.util.WindowSaver;

/**
 * Regression checks for USB tuning pack size and {@link WindowSaver} restore
 * (no UL snap on stale height, empty prefs leave caller bounds, Biasgen
 * close/save, {@link WindowSaver#placeAdjacent}). After {@code ant compile}:
 * {@code java -cp build/classes;jars/*;lib/* net.sf.jaer.graphics.UsbTuningFrameDemo}
 */
public final class UsbTuningFrameDemo {

    private static int assertions;

    private UsbTuningFrameDemo() {
    }

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("needs a display");
        }
        SwingUtilities.invokeAndWait(() -> {
            try {
                testPackedSizeStaysBelowScreen();
                testPackedSizeWithNoteStaysBelowScreen();
                testClampCapsOversizeFrame();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        testDontResizeEmptyPrefsLeavesCallerLocation();
        testDontResizeStaleHeightDoesNotSnapToOrigin();
        testFirstShowCentersOnOwnerWhenPrefsEmpty();
        testResizableNoPrefsLeavesCallerBounds();
        testAeViewerNoPrefsGetsHalfMonitorWidth();
        testResizableStaleHeightClampsNotOrigin();
        testPlaceAdjacentPutsChildBesideOwner();
        testCloseSavesBoundsForReopen();
        System.out.println("USB_TUNING_LAYOUT ASSERTIONS=" + assertions);
        System.out.println("USB_TUNING_LAYOUT PASS");
    }

    private static void testPackedSizeStaysBelowScreen() {
        UsbTuningFrame frame = new UsbTuningFrame(null);
        try {
            frame.pack();
            assertPackedFitsScreen(frame, "initial pack");
        } finally {
            frame.dispose();
        }
    }

    private static void testPackedSizeWithNoteStaysBelowScreen() throws Exception {
        UsbTuningFrame frame = new UsbTuningFrame(null);
        try {
            Method setNote = UsbTuningFrame.class.getDeclaredMethod("setNote", String.class);
            setNote.setAccessible(true);
            setNote.invoke(frame, "Each bulk IN completes at a fixed size; raising FIFO does not make completions larger");
            Method packToContent = UsbTuningFrame.class.getDeclaredMethod("packToContent");
            packToContent.setAccessible(true);
            packToContent.invoke(frame);
            assertPackedFitsScreen(frame, "pack with note");
        } finally {
            frame.dispose();
        }
    }

    private static void testFirstShowCentersOnOwnerWhenPrefsEmpty() throws Exception {
        String id = UUID.randomUUID().toString();
        Preferences root = Preferences.userRoot().node("jaer-test/UsbTuningFrameDemo/" + id);
        JFrame owner = new JFrame("viewer");
        UsbTuningFrame frame = new UsbTuningFrame(null);
        try {
            owner.setBounds(200, 120, 900, 700);
            owner.setVisible(true);
            Method packToContent = UsbTuningFrame.class.getDeclaredMethod("packToContent");
            packToContent.setAccessible(true);
            packToContent.invoke(frame);
            frame.setLocationRelativeTo(owner);
            int centeredX = frame.getX();
            int centeredY = frame.getY();
            require(centeredX > 50 && centeredY > 40,
                    "setLocationRelativeTo should not be UL, was " + centeredX + "," + centeredY);
            WindowSaver saver = new WindowSaver(null, root);
            saver.loadSettings(frame);
            flushEdt();
            packToContent.invoke(frame);
            if (!saver.hasSavedOrigin(frame)) {
                frame.setLocationRelativeTo(owner);
            }
            WindowSaver.clampToScreen(frame);
            require(Math.abs(frame.getX() - centeredX) < 80,
                    "empty prefs must keep viewer-relative origin, was "
                            + frame.getX() + "," + frame.getY() + " expected ~" + centeredX + "," + centeredY);
            require(frame.getY() > 20, "must not jump to UL after WindowSaver, y=" + frame.getY());
            assertPackedFitsScreen(frame, "first show after empty WindowSaver");
        } finally {
            frame.dispose();
            owner.dispose();
            root.removeNode();
        }
    }

    private static void testClampCapsOversizeFrame() {
        JFrame frame = new JFrame("clamp");
        frame.setName("ClampTest");
        try {
            Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            frame.setBounds(80, 40, 400, usable.height + 400);
            WindowSaver.clampToScreen(frame);
            require(frame.getHeight() <= usable.height,
                    "clampToScreen caps height to usable screen, was " + frame.getHeight());
            require(frame.getY() >= usable.y, "clamped origin stays in usable y");
            require(frame.getY() + frame.getHeight() <= usable.y + usable.height + 1,
                    "clamped frame does not hang off the bottom");
        } finally {
            frame.dispose();
        }
    }

    private static void testDontResizeEmptyPrefsLeavesCallerLocation() throws Exception {
        String id = UUID.randomUUID().toString();
        Preferences root = Preferences.userRoot().node("jaer-test/UsbTuningFrameDemo/" + id);
        try {
            WindowSaver saver = new WindowSaver(null, root);
            PackedFrame frame = new PackedFrame("UsbTuningEmpty");
            frame.setLocation(320, 180);
            frame.setSize(420, 360);
            int x = frame.getX();
            int y = frame.getY();
            int w = frame.getWidth();
            int h = frame.getHeight();
            saver.loadSettings(frame);
            flushEdt();
            require(frame.getX() == x && frame.getY() == y,
                    "DontResize with no prefs must not move to UL, was "
                            + frame.getX() + "," + frame.getY());
            require(frame.getWidth() == w && frame.getHeight() == h,
                    "DontResize with no prefs must not resize");
        } finally {
            root.removeNode();
        }
    }

    private static void testDontResizeStaleHeightDoesNotSnapToOrigin() throws Exception {
        String id = UUID.randomUUID().toString();
        Preferences root = Preferences.userRoot().node("jaer-test/UsbTuningFrameDemo/" + id);
        try {
            WindowSaver saver = new WindowSaver(null, root);
            Preferences ws = root.node("WindowSaver");
            ws.putInt("UsbTuningStale.x", 220);
            ws.putInt("UsbTuningStale.y", 140);
            ws.putInt("UsbTuningStale.w", 400);
            ws.putInt("UsbTuningStale.h", 915);
            PackedFrame frame = new PackedFrame("UsbTuningStale");
            frame.setLocation(50, 50);
            frame.setSize(420, 360);
            saver.loadSettings(frame);
            flushEdt();
            require(frame.getX() == 220 && frame.getY() == 140,
                    "DontResize must restore saved origin, not 0,0 from stale height; was "
                            + frame.getX() + "," + frame.getY());
            require(frame.getWidth() == 420 && frame.getHeight() == 360,
                    "DontResize must keep packed size despite saved h=915");
        } finally {
            root.removeNode();
        }
    }

    private static void testResizableNoPrefsLeavesCallerBounds() throws Exception {
        String id = UUID.randomUUID().toString();
        Preferences root = Preferences.userRoot().node("jaer-test/UsbTuningFrameDemo/" + id);
        try {
            WindowSaver saver = new WindowSaver(null, root);
            JFrame frame = new JFrame("Davis240C - biases.txt - Biases");
            frame.setName("Biasgen");
            try {
                frame.setLocation(260, 170);
                frame.setSize(380, 340);
                saver.loadSettings(frame);
                flushEdt();
                require(frame.getX() == 260 && frame.getY() == 170,
                        "no Biasgen prefs must not move to 10,10/UL, was "
                                + frame.getX() + "," + frame.getY());
                require(frame.getWidth() == 380 && frame.getHeight() == 340,
                        "no Biasgen prefs must not apply 500x500 default size");
            } finally {
                frame.dispose();
            }
        } finally {
            root.removeNode();
        }
    }

    private static void testAeViewerNoPrefsGetsHalfMonitorWidth() throws Exception {
        String id = UUID.randomUUID().toString();
        Preferences root = Preferences.userRoot().node("jaer-test/UsbTuningFrameDemo/" + id);
        try {
            WindowSaver saver = new WindowSaver(null, root);
            JFrame frame = new JFrame("AEViewer");
            frame.setName("AEViewer-0");
            try {
                frame.setLocation(80, 60);
                frame.setSize(200, 200);
                saver.loadSettings(frame);
                flushEdt();
                Rectangle expected = WindowSaver.firstRunAeViewerBounds();
                require(Math.abs(frame.getWidth() - expected.width) <= 2,
                        "first-run AEViewer width should be ~half monitor, was "
                                + frame.getWidth() + " expected " + expected.width);
                require(frame.getWidth() > 200,
                        "first-run AEViewer must not stay packed 200px, was " + frame.getWidth());
                require(Math.abs(frame.getX() - expected.x) <= 2
                                && Math.abs(frame.getY() - expected.y) <= 2,
                        "first-run AEViewer should start near top-left, was "
                                + frame.getX() + "," + frame.getY()
                                + " expected " + expected.x + "," + expected.y);
                require(frame.getHeight() >= expected.height - 2,
                        "first-run AEViewer height should fill work area, was "
                                + frame.getHeight() + " expected " + expected.height);
            } finally {
                frame.dispose();
            }
        } finally {
            root.removeNode();
        }
    }

    private static void testResizableStaleHeightClampsNotOrigin() throws Exception {
        String id = UUID.randomUUID().toString();
        Preferences root = Preferences.userRoot().node("jaer-test/UsbTuningFrameDemo/" + id);
        try {
            WindowSaver saver = new WindowSaver(null, root);
            Preferences ws = root.node("WindowSaver");
            ws.putInt("Biasgen.x", 180);
            ws.putInt("Biasgen.y", 90);
            ws.putInt("Biasgen.w", 420);
            ws.putInt("Biasgen.h", 915);
            JFrame frame = new JFrame("Davis240C - biases.txt - Biases");
            frame.setName("Biasgen");
            try {
                frame.setLocation(10, 10);
                frame.setSize(300, 280);
                saver.loadSettings(frame);
                flushEdt();
                Rectangle usable = WindowSaver.usableBounds();
                require(frame.getX() != 0 || frame.getY() != 0,
                        "stale h=915 must not snap Biasgen to UL origin");
                require(frame.getX() == 180, "keep saved x=180, was " + frame.getX());
                require(frame.getHeight() <= usable.height,
                        "cap height to usable, was " + frame.getHeight());
                require(frame.getY() + frame.getHeight() <= usable.y + usable.height + 1,
                        "clamped Biasgen must stay in usable screen");
            } finally {
                frame.dispose();
            }
        } finally {
            root.removeNode();
        }
    }

    private static void testPlaceAdjacentPutsChildBesideOwner() {
        JFrame owner = new JFrame("AEViewer");
        JFrame child = new JFrame("HW Config");
        try {
            owner.setBounds(80, 60, 500, 400);
            owner.setVisible(true);
            child.setSize(360, 300);
            WindowSaver.placeAdjacent(owner, child);
            require(child.getX() >= owner.getX() + owner.getWidth()
                    || child.getX() + child.getWidth() <= owner.getX(),
                    "child should sit beside owner, not on top; child.x=" + child.getX()
                            + " owner=" + owner.getX() + "+" + owner.getWidth());
            require(child.getX() != owner.getX() || child.getY() != owner.getY(),
                    "child must not share owner origin");
        } finally {
            child.dispose();
            owner.dispose();
        }
    }

    private static void testCloseSavesBoundsForReopen() throws Exception {
        String id = UUID.randomUUID().toString();
        Preferences root = Preferences.userRoot().node("jaer-test/UsbTuningFrameDemo/" + id);
        try {
            WindowSaver saver = new WindowSaver(null, root);
            JFrame frame = new JFrame("HW Config");
            frame.setName("Biasgen");
            try {
                frame.setBounds(240, 130, 410, 360);
                saver.saveOneFrame(frame);
                frame.setBounds(0, 0, 100, 80);
                saver.loadSettings(frame);
                flushEdt();
                require(frame.getX() == 240 && frame.getY() == 130,
                        "close/save must restore origin, was " + frame.getX() + "," + frame.getY());
                require(frame.getWidth() == 410 && frame.getHeight() == 360,
                        "close/save must restore size");
            } finally {
                frame.dispose();
            }
        } finally {
            root.removeNode();
        }
    }

    private static void assertPackedFitsScreen(JFrame frame, String label) {
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        Dimension size = frame.getSize();
        require(size.width > 200 && size.height > 200, label + " has a real packed size " + size);
        require(size.width < usable.width, label + " width " + size.width + " overfills screen " + usable.width);
        require(size.height < usable.height,
                label + " height " + size.height + " overfills screen " + usable.height);
        require(size.height < usable.height * 0.75,
                label + " height " + size.height + " is not a compact packed dialog");
    }

    private static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
        });
        SwingUtilities.invokeAndWait(() -> {
        });
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new AssertionError(message);
        }
        assertions++;
    }

    private static final class PackedFrame extends JFrame implements WindowSaver.DontResize {
        PackedFrame(String name) {
            super(name);
            setName(name);
        }
    }
}
