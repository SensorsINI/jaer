/*
 * DVS128.java
 *
 * Created on October 5, 2005, 11:36 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */
package ch.unizh.ini.jaer.chip.retina;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.Serializable;
import java.util.Observable;
import java.util.Observer;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JTabbedPane;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.biasgen.ChipControlPanel;
import net.sf.jaer.biasgen.IPot;
import net.sf.jaer.biasgen.IPotArray;
import net.sf.jaer.biasgen.PotTweakerUtilities;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.chip.RetinaExtractor;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.graphics.DavisRenderer;
import net.sf.jaer.graphics.ChipRendererDisplayMethodRGBA;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2DVS128HardwareInterface;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.HasLEDControl;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.HasLEDControl.LEDState;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.HasResettablePixelArray;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.HasSyncEventOutput;
import net.sf.jaer.util.HexString;
import net.sf.jaer.util.RemoteControlCommand;
import net.sf.jaer.util.RemoteControlled;
import net.sf.jaer.util.WarningDialogWithDontShowPreference;

/**
 * Describes DVS128 retina and its event extractor and bias generator. This
 * camera is the Tmpdiff128 chip with certain biases tied to the rails to
 * enhance AE bus bandwidth and it achieves about 2 Meps, as opposed to the
 * approx 500 keps using the on-chip Tmpdiff128 biases.
 * <p>
 * Two constructors ara available, the vanilla constructor is used for event
 * playback and the one with a HardwareInterface parameter is useful for live
 * capture. {@link #setHardwareInterface} is used when the hardware interface is
 * constructed after the retina object. The constructor that takes a hardware
 * interface also constructs the biasgen interface.
 *
 * @author tobi
 */
@Description("DVS128 Dynamic Vision Sensor")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class DVS640 extends AETemporalConstastRetina implements Serializable {

    private DavisRenderer dvsRenderer;
    private ChipRendererDisplayMethodRGBA dvsDisplayMethod = null;

    /**
     * Creates a new instance of DVS640. No biasgen is constructed.
     */
    public DVS640() {
        setName("DVS640");
        setSizeX(640);
        setSizeY(480);
        setNumCellTypes(2);
        setPixelHeightUm(9);
        setPixelWidthUm(9);
        setEventExtractor(new Extractor(this));
        setBiasgen(null); // only for viewing data
        //        ChipCanvas c = getCanvas();

        dvsRenderer = new DVS640.DvsRenderer(this);
        setRenderer(dvsRenderer);

        dvsDisplayMethod = new ChipRendererDisplayMethodRGBA(this.getCanvas());
        getCanvas().addDisplayMethod(dvsDisplayMethod);
        getCanvas().setDisplayMethod(dvsDisplayMethod);
    }

    /**
     * Creates a new instance of DVS128
     *
     * @param hardwareInterface an existing hardware interface. This constructor
     * is preferred. It makes a new Biasgen object to talk to the on-chip
     * biasgen.
     */
    public DVS640(HardwareInterface hardwareInterface) {
        this();
        setHardwareInterface(hardwareInterface);
    }

    @Override
    public void onDeregistration() {
        super.onDeregistration();
        if (getAeViewer() == null) {
            return;
        }
    }

    @Override
    public void onRegistration() {
        super.onRegistration();
        if (getAeViewer() == null) {
            return;
        }
    }

    /**
     * the event extractor for DVS640. DVS640 has two polarities 0 and 1. Here
     * the polarity is flipped by the extractor so that the raw polarity 0
     * becomes 1 in the extracted event. The ON events have raw polarity 0. 1 is
     * an ON event after event extraction, which flips the type. Raw polarity 1
     * is OFF event, which becomes 0 after extraction.
     */
    public class Extractor extends RetinaExtractor {

        final int XSHIFT = 1, XMASK = 0b11_1111_1111<<XSHIFT , YSHIFT = 11, YMASK = 0b11_1111_1111<<YSHIFT;

        public Extractor(DVS640 chip) {
            super(chip);
            setXmask(XMASK); // 10 bits for 640
            setXshift((byte) XSHIFT);
            setYmask(YMASK); // also 10 bits for 480
            setYshift((byte) YSHIFT);
            setTypemask(1);
            setTypeshift((byte) 0);
            setFlipx(true); // flip x to match rosbags from ev-imo dataset (tobi)
            setFlipy(false);
            setFliptype(false);
        }

        @Override
        public int reconstructRawAddressFromEvent(TypedEvent e) {
            return reconstructDefaultRawAddressFromEvent(e);
        }

        /**
         * extracts the meaning of the raw events.
         *
         * @param in the raw events, can be null
         * @return out the processed events. these are partially processed
         * in-place. empty packet is returned if null is supplied as in. This
         * event packet is reused and should only be used by a single thread of
         * execution or for a single input stream, or mysterious results may
         * occur!
         */
        @Override
        synchronized public EventPacket extractPacket(AEPacketRaw in) {
            if (out == null) {
                out = new EventPacket<PolarityEvent>(getChip().getEventClass());
            } else {
                out.clear();
            }
            extractPacket(in, out);
            return out;
        }
        private int printedSyncBitWarningCount = 3;

        /**
         * Extracts the meaning of the raw events. This form is used to supply
         * an output packet. This method is used for real time event filtering
         * using a buffer of output events local to data acquisition. An
         * AEPacketRaw may contain multiple events, not all of them have to sent
         * out as EventPackets. An AEPacketRaw is a set(!) of addresses and
         * corresponding timing moments.
         *
         * A first filter (independent from the other ones) is implemented by
         * subSamplingEnabled and getSubsampleThresholdEventCount. The latter
         * may limit the amount of samples in one package to say 50,000. If
         * there are 160,000 events and there is a sub sample threshold of
         * 50,000, a "skip parameter" set to 3. Every so now and then the
         * routine skips with 4, so we end up with 50,000. It's an
         * approximation, the amount of events may be less than 50,000. The
         * events are extracted uniform from the input.
         *
         * @param in the raw events, can be null
         * @param out the processed events. these are partially processed
         * in-place. empty packet is returned if null is supplied as input.
         */
        @Override
        synchronized public void extractPacket(AEPacketRaw in, EventPacket out) {

            if (in == null) {
                return;
            }
            int n = in.getNumEvents(); //addresses.length;
            out.systemModificationTimeNs = in.systemModificationTimeNs;

            int skipBy = 1;
            if (isSubSamplingEnabled()) {
                while ((n / skipBy) > getSubsampleThresholdEventCount()) {
                    skipBy++;
                }
            }
            int sxm = sizeX - 1;
            int[] a = in.getAddresses();
            int[] timestamps = in.getTimestamps();
            OutputEventIterator outItr = out.outputIterator();
            for (int i = 0; i < n; i += skipBy) { // TODO bug here?
                int addr = a[i];
                PolarityEvent e = (PolarityEvent) outItr.nextOutput();
                e.address = addr;
                e.timestamp = (timestamps[i]);
                e.setSpecial(false);
                e.type = (byte) (addr & 1);
                e.polarity = e.type == 0 ? PolarityEvent.Polarity.Off : PolarityEvent.Polarity.On;
                e.x = (short) (sxm - (((addr & XMASK) >>> XSHIFT)));
                e.y = (short) ((addr & YMASK) >>> YSHIFT);
            }
        }
    }

    private class DvsRenderer extends DavisRenderer {

        public DvsRenderer(AEChip chip) {
            super(chip);
        }

        @Override
        public boolean isDisplayEvents() {
            return true;
        }

        @Override
        public boolean isDisplayFrames() {
            return false;
        }

    }

}
