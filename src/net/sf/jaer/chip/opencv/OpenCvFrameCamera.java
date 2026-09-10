package net.sf.jaer.chip.opencv;

import java.awt.event.ActionEvent;
import java.util.Locale;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSeparator;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

import org.opencv.videoio.Videoio;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.TypedEventExtractor;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.graphics.ChipRendererDisplayMethodRGBA;
import net.sf.jaer.graphics.DisplayMethod;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.opencv.OpenCvCameraHardwareInterface;
import net.sf.jaer.hardwareinterface.opencv.OpenCvCaptureControls;

/**
 * Standard frame camera (webcam) via OpenCV {@code VideoCapture}. Live data is
 * {@link net.sf.jaer.event.FramePacket} RGB only; no polarity or biasgen.
 */
@Description("OpenCV / UVC webcam: RGB frames in AEViewer and AEDAT-4 FRME")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class OpenCvFrameCamera extends AEChip implements OpenCvCaptureControls {

    private static final String PREF_WIDTH = "opencv.reqWidth";
    private static final String PREF_HEIGHT = "opencv.reqHeight";
    private static final String PREF_FOURCC = "opencv.reqFourcc";
    private static final String PREF_FPS = "opencv.reqFps";

    private static final int[][] SIZES = {
        {0, 0},
        {320, 240},
        {640, 480},
        {800, 600},
        {1280, 720},
        {1920, 1080}
    };
    private static final String[] FOURCCS = {"", "YUY2", "MJPG"};
    private static final double[] FPSES = {0, 15, 30, 60};

    private JMenu opencvMenu;

    public OpenCvFrameCamera() {
        setName("OpenCvFrameCamera");
        setSizeX(640);
        setSizeY(480);
        setNumCellTypes(1);
        setEventClass(PolarityEvent.class);
        setPixelHeightUm(1);
        setPixelWidthUm(1);
        OpenCvFrameRenderer renderer = new OpenCvFrameRenderer(this);
        setRenderer(renderer);
        DisplayMethod rgba = new ChipRendererDisplayMethodRGBA(getCanvas());
        getCanvas().addDisplayMethod(rgba);
        getCanvas().setDisplayMethod(rgba);
        setEventExtractor(new EmptyExtractor(this));
    }

    public OpenCvFrameCamera(HardwareInterface hardwareInterface) {
        this();
        setHardwareInterface(hardwareInterface);
    }

    @Override
    public void setHardwareInterface(HardwareInterface hardwareInterface) {
        super.setHardwareInterface(hardwareInterface);
        if (hardwareInterface instanceof OpenCvCameraHardwareInterface) {
            ((OpenCvCameraHardwareInterface) hardwareInterface).setChip(this);
        }
    }

    @Override
    public void onRegistration() {
        super.onRegistration();
        if (getAeViewer() == null) {
            return;
        }
        opencvMenu = new JMenu("OpenCV");
        opencvMenu.setToolTipText("Requests OpenCV VideoCapture size, format, and analog controls. "
                + "The driver may keep VGA; these are not a capability list.");
        opencvMenu.getPopupMenu().setLightWeightPopupEnabled(false);
        JMenu sizeMenu = new JMenu("Size");
        ButtonGroup sizeGroup = new ButtonGroup();
        for (int[] sz : SIZES) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(new SizeAction(sz[0], sz[1]));
            sizeGroup.add(item);
            sizeMenu.add(item);
        }
        opencvMenu.add(sizeMenu);
        JMenu fourccMenu = new JMenu("Pixel format");
        ButtonGroup fourccGroup = new ButtonGroup();
        for (String fcc : FOURCCS) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(new FourccAction(fcc));
            fourccGroup.add(item);
            fourccMenu.add(item);
        }
        opencvMenu.add(fourccMenu);
        JMenu fpsMenu = new JMenu("Frame rate");
        ButtonGroup fpsGroup = new ButtonGroup();
        for (double fps : FPSES) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(new FpsAction(fps));
            fpsGroup.add(item);
            fpsMenu.add(item);
        }
        opencvMenu.add(fpsMenu);
        opencvMenu.add(new JSeparator());
        opencvMenu.add(new JMenuItem(new NudgeAction("Brightness +", Videoio.CAP_PROP_BRIGHTNESS, 1, "Brightness")));
        opencvMenu.add(new JMenuItem(new NudgeAction("Brightness -", Videoio.CAP_PROP_BRIGHTNESS, -1, "Brightness")));
        opencvMenu.add(new JMenuItem(new NudgeAction("Contrast +", Videoio.CAP_PROP_CONTRAST, 1, "Contrast")));
        opencvMenu.add(new JMenuItem(new NudgeAction("Contrast -", Videoio.CAP_PROP_CONTRAST, -1, "Contrast")));
        opencvMenu.add(new JSeparator());
        opencvMenu.add(new JCheckBoxMenuItem(new AutofocusAction()));
        opencvMenu.add(new JMenuItem(new ShowModeAction()));
        if (isWindows()) {
            opencvMenu.add(new JMenuItem(new OsSettingsAction()));
        }
        opencvMenu.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                syncMenuFromPrefsAndHardware();
            }

            @Override
            public void menuDeselected(MenuEvent e) {
            }

            @Override
            public void menuCanceled(MenuEvent e) {
            }
        });
        getAeViewer().addMenu(opencvMenu);
    }

    @Override
    public void onDeregistration() {
        super.onDeregistration();
        if (getAeViewer() != null && opencvMenu != null) {
            getAeViewer().removeMenu(opencvMenu);
        }
        opencvMenu = null;
    }

    @Override
    public void onCaptureOpened(OpenCvCameraHardwareInterface hw) {
        String msg = hw.applyControls(reqWidth(), reqHeight(), reqFourcc(), reqFps());
        log.info("OpenCV controls after open: " + msg);
    }

    public void pushControlsToHardware() {
        OpenCvCameraHardwareInterface hw = liveHw();
        if (hw == null) {
            return;
        }
        announce(hw.applyControls(reqWidth(), reqHeight(), reqFourcc(), reqFps()));
    }

    private OpenCvCameraHardwareInterface liveHw() {
        HardwareInterface hw = getHardwareInterface();
        if (hw instanceof OpenCvCameraHardwareInterface && hw.isOpen()) {
            return (OpenCvCameraHardwareInterface) hw;
        }
        return null;
    }

    private int reqWidth() {
        return getPrefs().getInt(PREF_WIDTH, 0);
    }

    private int reqHeight() {
        return getPrefs().getInt(PREF_HEIGHT, 0);
    }

    private String reqFourcc() {
        return getPrefs().get(PREF_FOURCC, "");
    }

    private double reqFps() {
        return getPrefs().getDouble(PREF_FPS, 0);
    }

    private void announce(String msg) {
        log.info(msg);
        if (getAeViewer() != null) {
            getAeViewer().showActionText(msg);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private void syncMenuFromPrefsAndHardware() {
        if (opencvMenu == null) {
            return;
        }
        OpenCvCameraHardwareInterface hw = liveHw();
        boolean live = hw != null;
        int rw = reqWidth();
        int rh = reqHeight();
        String rf = reqFourcc() == null ? "" : reqFourcc();
        double rfps = reqFps();
        syncTree(opencvMenu, live, rw, rh, rf, rfps, hw);
    }

    private void syncTree(JMenu menu, boolean live, int rw, int rh, String rf, double rfps,
            OpenCvCameraHardwareInterface hw) {
        for (int i = 0; i < menu.getItemCount(); i++) {
            JMenuItem item = menu.getItem(i);
            if (item == null) {
                continue;
            }
            if (item instanceof JMenu) {
                syncTree((JMenu) item, live, rw, rh, rf, rfps, hw);
                continue;
            }
            Action a = item.getAction();
            if (a instanceof SizeAction sa) {
                sa.putValue(Action.SELECTED_KEY, sa.width == rw && sa.height == rh);
            } else if (a instanceof FourccAction fa) {
                fa.putValue(Action.SELECTED_KEY, fa.fourcc.equalsIgnoreCase(rf));
            } else if (a instanceof FpsAction pa) {
                pa.putValue(Action.SELECTED_KEY, Math.abs(pa.fps - rfps) < 0.01);
            } else if (a instanceof NudgeAction || a instanceof AutofocusAction
                    || a instanceof OsSettingsAction || a instanceof ShowModeAction) {
                item.setEnabled(live);
            }
            if (a instanceof AutofocusAction && hw != null) {
                boolean supported = hw.propertySupported(Videoio.CAP_PROP_AUTOFOCUS);
                item.setEnabled(live && supported);
                a.putValue(Action.SELECTED_KEY, supported && hw.isAutofocusOn());
            }
        }
    }

    final class SizeAction extends AbstractAction {
        final int width;
        final int height;

        SizeAction(int width, int height) {
            this.width = width;
            this.height = height;
            putValue(Action.NAME, width == 0 ? "Driver default" : width + " × " + height);
            putValue(Action.SHORT_DESCRIPTION,
                    "Request this size. OpenCV has no caps list; the driver may keep another mode.");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            getPrefs().putInt(PREF_WIDTH, width);
            getPrefs().putInt(PREF_HEIGHT, height);
            OpenCvCameraHardwareInterface hw = liveHw();
            if (hw == null) {
                announce(width == 0 ? "Size: driver default when the camera opens"
                        : "Size " + width + "×" + height + " when the camera opens");
                return;
            }
            announce(hw.applyControls(width, height, reqFourcc(), reqFps()));
        }
    }

    final class FourccAction extends AbstractAction {
        final String fourcc;

        FourccAction(String fourcc) {
            this.fourcc = fourcc == null ? "" : fourcc;
            putValue(Action.NAME, this.fourcc.isEmpty() ? "Driver default" : this.fourcc);
            putValue(Action.SHORT_DESCRIPTION,
                    "MJPG often unlocks 720p/1080p on UVC. Frames are still converted to RGB.");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            getPrefs().put(PREF_FOURCC, fourcc);
            OpenCvCameraHardwareInterface hw = liveHw();
            if (hw == null) {
                announce(fourcc.isEmpty() ? "Format: driver default when the camera opens"
                        : "Format " + fourcc + " when the camera opens");
                return;
            }
            announce(hw.applyControls(reqWidth(), reqHeight(), fourcc, reqFps()));
        }
    }

    final class FpsAction extends AbstractAction {
        final double fps;

        FpsAction(double fps) {
            this.fps = fps;
            putValue(Action.NAME, fps < 0.5 ? "Driver default" : ((int) fps) + " fps");
            putValue(Action.SHORT_DESCRIPTION, "Many backends ignore fps; the overlay shows what get() reports.");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            getPrefs().putDouble(PREF_FPS, fps);
            OpenCvCameraHardwareInterface hw = liveHw();
            if (hw == null) {
                announce(fps < 0.5 ? "FPS: driver default when the camera opens"
                        : ((int) fps) + " fps when the camera opens");
                return;
            }
            announce(hw.applyControls(reqWidth(), reqHeight(), reqFourcc(), fps));
        }
    }

    final class NudgeAction extends AbstractAction {
        private final int propId;
        private final int direction;
        private final String name;

        NudgeAction(String label, int propId, int direction, String name) {
            putValue(Action.NAME, label);
            this.propId = propId;
            this.direction = direction;
            this.name = name;
            putValue(Action.SHORT_DESCRIPTION, "Change " + name + " if this UVC camera exposes it.");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            OpenCvCameraHardwareInterface hw = liveHw();
            if (hw == null) {
                announce("Open the camera from Interface first");
                return;
            }
            announce(hw.nudgeProperty(propId, direction, name));
        }
    }

    final class AutofocusAction extends AbstractAction {
        AutofocusAction() {
            putValue(Action.NAME, "Autofocus");
            putValue(Action.SHORT_DESCRIPTION, "Laptop modules only; disabled if OpenCV reports unsupported.");
            putValue(Action.SELECTED_KEY, false);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            OpenCvCameraHardwareInterface hw = liveHw();
            if (hw == null) {
                announce("Open the camera from Interface first");
                return;
            }
            boolean want = Boolean.TRUE.equals(getValue(Action.SELECTED_KEY));
            if (e.getSource() instanceof JCheckBoxMenuItem cb) {
                want = cb.isSelected();
            }
            announce(hw.setAutofocus(want));
            putValue(Action.SELECTED_KEY, hw.isAutofocusOn());
        }
    }

    final class ShowModeAction extends AbstractAction {
        ShowModeAction() {
            putValue(Action.NAME, "Show current mode");
            putValue(Action.SHORT_DESCRIPTION, "Width, height, FOURCC, and fps reported by OpenCV get().");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            OpenCvCameraHardwareInterface hw = liveHw();
            announce(hw == null ? "OpenCV camera is not open" : hw.modeStatus());
        }
    }

    final class OsSettingsAction extends AbstractAction {
        OsSettingsAction() {
            putValue(Action.NAME, "Windows camera settings…");
            putValue(Action.SHORT_DESCRIPTION,
                    "DirectShow property page (CAP_PROP_SETTINGS). Blocks until you close the dialog.");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            OpenCvCameraHardwareInterface hw = liveHw();
            if (hw == null) {
                announce("Open the camera from Interface first");
                return;
            }
            announce("Windows camera settings…");
            hw.openOsSettingsDialog();
        }
    }

    /** Raw AE extractor unused on the live typed path. */
    public static final class EmptyExtractor extends TypedEventExtractor<PolarityEvent> {
        public EmptyExtractor(AEChip chip) {
            super(chip);
        }

        @Override
        public synchronized EventPacket<PolarityEvent> extractPacket(AEPacketRaw in) {
            if (out == null) {
                out = new EventPacket<>(PolarityEvent.class);
            } else {
                out.clear();
            }
            return out;
        }
    }
}
