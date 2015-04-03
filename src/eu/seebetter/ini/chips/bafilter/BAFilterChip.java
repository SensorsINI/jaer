/*
 * created 26 Oct 2008 for new cDVSTest chip
 * adapted apr 2011 for cDVStest30 chip by tobi
 * adapted 25 oct 2011 for SeeBetter10/11 chips by tobi
 */
package eu.seebetter.ini.chips.bafilter;

import eu.seebetter.ini.chips.davis.*;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUquadric;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

import net.sf.jaer.Description;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.chip.RetinaExtractor;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.graphics.ChipRendererDisplayMethodRGBA;
import net.sf.jaer.graphics.DisplayMethod;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.util.RemoteControlCommand;
import net.sf.jaer.util.RemoteControlled;

import com.jogamp.opengl.util.awt.TextRenderer;
import eu.seebetter.ini.chips.DavisChip;
import net.sf.jaer.chip.AEChip;

/**
 *
 * @author hongjie
 */
@Description("Background Activity Filter Chip")
public class BAFilterChip extends AEChip implements RemoteControlled, Observer {

    private JMenu chipMenu = null;
    private JMenuItem syncEnabledMenuItem = null;
    private boolean isTimestampMaster = true;

    private final int ADC_NUMBER_OF_TRAILING_ZEROS = Integer.numberOfTrailingZeros(DavisChip.ADC_READCYCLE_MASK);
	// speedup in loop
    // following define bit masks for various hardware data types.
    // The hardware interface translateEvents method packs the raw device data into 32 bit 'addresses' and timestamps.
    // timestamps are unwrapped and timestamp resets are handled in translateEvents. Addresses are filled with either AE
    // or ADC data.
    // AEs are filled in according the XMASK, YMASK, XSHIFT, YSHIFT below.
    /**
     * bit masks/shifts for cDVS AE data
     */
    private BAFilterChipDisplayMethod davisDisplayMethod = null;
    private final AEFrameChipRenderer apsDVSrenderer;
    private int frameExposureStartTimestampUs = 0; // timestamp of first sample from frame (first sample read after
    // reset released)

    private BAFilterChipConfig config;
    JFrame controlFrame = null;
    public static final short WIDTH = 240;
    public static final short HEIGHT = 180;
    int sx1 = getSizeX() - 1, sy1 = getSizeY() - 1;
    private final String CMD_EXPOSURE = "exposure";
    private final String CMD_EXPOSURE_CC = "exposureCC";
    private final String CMD_RS_SETTLE_CC = "resetSettleCC";

    /**
     * Creates a new instance of cDVSTest20.
     */
    public BAFilterChip() {
        setName("BAFilterChip");
        setDefaultPreferencesFile("biasgenSettings/BAFilterChip/BAFilterChipDefault.xml");
        setEventClass(ApsDvsEvent.class);
        setSizeX(BAFilterChip.WIDTH);
        setSizeY(BAFilterChip.HEIGHT);
        setNumCellTypes(3); // two are polarity and last is intensity
        setPixelHeightUm(18.5f);
        setPixelWidthUm(18.5f);

        setEventExtractor(new BAFilterChipExtractor(this));

        setBiasgen(config = new BAFilterChipConfig(this));

        // hardware interface is ApsDvsHardwareInterface
        apsDVSrenderer = new AEFrameChipRenderer(this);
        apsDVSrenderer.setMaxADC(DavisChip.MAX_ADC);
        setRenderer(apsDVSrenderer);

        davisDisplayMethod = new BAFilterChipDisplayMethod(this);
        getCanvas().addDisplayMethod(davisDisplayMethod);
        getCanvas().setDisplayMethod(davisDisplayMethod);

        if (getRemoteControl() != null) {
            getRemoteControl()
                    .addCommandListener(this, CMD_EXPOSURE, CMD_EXPOSURE + " val - sets exposure. val in ms.");
            getRemoteControl().addCommandListener(this, CMD_EXPOSURE_CC,
                    CMD_EXPOSURE_CC + " val - sets exposure. val in clock cycles");
            getRemoteControl().addCommandListener(this, CMD_RS_SETTLE_CC,
                    CMD_RS_SETTLE_CC + " val - sets reset settling time. val in clock cycles");
        }
        addObserver(this); // we observe ourselves so that if hardware interface for example calls notifyListeners we
        // get informed
    }

    @Override
    public String processRemoteControlCommand(final RemoteControlCommand command, final String input) {
        Chip.log.info("processing RemoteControlCommand " + command + " with input=" + input);
        if (command == null) {
            return null;
        }
        final String[] tokens = input.split(" ");
        if (tokens.length < 2) {
            return input + ": unknown command - did you forget the argument?";
        }
        if ((tokens[1] == null) || (tokens[1].length() == 0)) {
            return input + ": argument too short - need a number";
        }
        float v = 0;
        try {
            v = Float.parseFloat(tokens[1]);
        } catch (final NumberFormatException e) {
            return input + ": bad argument? Caught " + e.toString();
        }
        final String c = command.getCmdName();
        if (c.equals(CMD_RS_SETTLE_CC)) {
            config.resSettle.set((int) v);
        } else {
            return input + ": unknown command";
        }
        return "successfully processed command " + input;
    }

    /**
     * Creates a new instance of DAViS240
     *
     * @param hardwareInterface an existing hardware interface. This constructor
     * is preferred. It makes a new cDVSTest10Biasgen object to talk to the
     * on-chip biasgen.
     */
    public BAFilterChip(final HardwareInterface hardwareInterface) {
        this();
        setHardwareInterface(hardwareInterface);
    }

    @Override
    public void update(Observable o, Object arg) {
        // TODO handles updates to this chip
    }

    // int pixcnt=0; // TODO debug
    /**
     * The event extractor. Each pixel has two polarities 0 and 1.
     *
     * <p>
     * The bits in the raw data coming from the device are as follows.
     * <p>
     * Bit 0 is polarity, on=1, off=0<br>
     * Bits 1-9 are x address (max value 320)<br>
     * Bits 10-17 are y address (max value 240) <br>
     * <p>
     */
    public class BAFilterChipExtractor extends RetinaExtractor {

        /**
         *
         */
        private int warningCount = 0;
        private static final int WARNING_COUNT_DIVIDER = 10000;

        public BAFilterChipExtractor(final BAFilterChip chip) {
            super(chip);
        }

        /**
         * extracts the meaning of the raw events.
         *
         * @param in the raw events, can be null
         * @return out the processed events. these are partially processed
         * in-place. empty packet is returned if null is supplied as in.
         */
        @Override
        synchronized public EventPacket extractPacket(final AEPacketRaw in) {
            if (!(chip instanceof DavisChip)) {
                return null;
            }
            if (out == null) {
                out = new ApsDvsEventPacket(chip.getEventClass());
            } else {
                out.clear();
            }
            out.setRawPacket(in);
            if (in == null) {
                return out;
            }
            final int n = in.getNumEvents(); // addresses.length;
            sx1 = chip.getSizeX() - 1;
            sy1 = chip.getSizeY() - 1;

            final int[] datas = in.getAddresses();
            final int[] timestamps = in.getTimestamps();
            final OutputEventIterator outItr = out.outputIterator();

            return out;
        } // extractPacket

    } // extractor

    /**
     * overrides the Chip setHardware interface to construct a biasgen if one
     * doesn't exist already. Sets the hardware interface and the bias
     * generators hardware interface
     *
     * @param hardwareInterface the interface
     */
    @Override
    public void setHardwareInterface(final HardwareInterface hardwareInterface) {
        this.hardwareInterface = hardwareInterface;
        try {
            if (getBiasgen() == null) {
                setBiasgen(new BAFilterChipConfig(this));
                // now we can addConfigValue the control panel
            } else {
                getBiasgen().setHardwareInterface((BiasgenHardwareInterface) hardwareInterface);
            }
        } catch (final ClassCastException e) {
            Chip.log.warning(e.getMessage()
                    + ": probably this chip object has a biasgen but the hardware interface doesn't, ignoring");
        }
    }

    /**
     * Displays data from SeeBetter test chip SeeBetter10/11.
     *
     * @author Tobi
     */
    public class BAFilterChipDisplayMethod extends ChipRendererDisplayMethodRGBA {

        private static final int FONTSIZE = 10;
        private static final int FRAME_COUNTER_BAR_LENGTH_FRAMES = 10;

        private final TextRenderer exposureRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN,
                BAFilterChipDisplayMethod.FONTSIZE), true, true);

        public BAFilterChipDisplayMethod(final BAFilterChip chip) {
            super(chip.getCanvas());
        }

        @Override
        public void display(final GLAutoDrawable drawable) {
            getCanvas().setBorderSpacePixels(50);

            super.display(drawable);

        }

    }
}
