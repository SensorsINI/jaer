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
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import net.sf.jaer.Description;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.biasgen.ChipControlPanel;
import net.sf.jaer.biasgen.IPot;
import net.sf.jaer.biasgen.IPotArray;
import net.sf.jaer.biasgen.PotTweakerUtilities;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.chip.RetinaExtractor;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.HasResettablePixelArray;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.HasSyncEventOutput;

/**
 * Describes DVS128 retina and its event extractor and bias generator.
 * This camera is the Tmpdiff128 chip with certain biases tied to the rails to enhance AE bus bandwidth and
 * it achieves about 2 Meps, as opposed to the approx 500 keps using the onchip Tmpdiff128 biases.
 * <p>
 * Two constructors ara available, the vanilla constructor is used for event playback and the
 *one with a HardwareInterface parameter is useful for live capture.
 * {@link #setHardwareInterface} is used when the hardware interface is constructed after the retina object.
 *The constructor that takes a hardware interface also constructs the biasgen interface.
 *
 * @author tobi
 */
@Description("DVS128 Dynamic Vision Sensor combined with CochleaAMS1b binaural cochlea")
public class DVS128andCochleaAMS1b extends AETemporalConstastRetina implements Serializable, Observer {

    private JMenu dvs128Menu = null;
    private JMenuItem arrayResetMenuItem = null, syncEnabledMenuItem = null;
    private JMenuItem setArrayResetMenuItem = null;

    static {
//        setPreferredHardwareInterface(CypressFX2Biasgen.class); // TODO causing problems in applet
    }

    /** Creates a new instance of DVS128. No biasgen is constructed for this constructor, because there is no hardware interface defined. */
    public DVS128andCochleaAMS1b() {
        setName("DVS128");
        setSizeX(128);
        setSizeY(256);
        setNumCellTypes(2);
        setPixelHeightUm(40);
        setPixelWidthUm(40);
        setEventExtractor(new Extractor(this));
        setBiasgen(new DVS128andCochleaAMS1b.Biasgen(this));
        ChipCanvas c = getCanvas();
        addObserver(this);
//        if(c!=null)c.setBorderSpacePixels(5);// make border smaller than default
    }

    /** Creates a new instance of DVS128
     * @param hardwareInterface an existing hardware interface. This constructer is preferred. It makes a new Biasgen object to talk to the on-chip biasgen.
     */
    public DVS128andCochleaAMS1b(HardwareInterface hardwareInterface) {
        this();
        setHardwareInterface(hardwareInterface);
    }

    /** Updates AEViewer specialized menu items according to capabilities of HardwareInterface.
     *
     * @param o the observable, i.e. this Chip.
     * @param arg the argument (e.g. the HardwareInterface).
     */
    public void update(Observable o, Object arg) {
        if (!(arg instanceof HardwareInterface)) {
            return;
        }
        if (arrayResetMenuItem == null && getHardwareInterface() != null && getHardwareInterface() instanceof HasResettablePixelArray) {
            arrayResetMenuItem = new JMenuItem("Momentarily reset pixel array");
            arrayResetMenuItem.setToolTipText("Applies a momentary reset to the pixel array");
            arrayResetMenuItem.addActionListener(new ActionListener() {

                public void actionPerformed(ActionEvent evt) {
                    HardwareInterface hw = getHardwareInterface();
                    if (hw == null || !(hw instanceof HasResettablePixelArray)) {
                        log.warning("cannot reset pixels with hardware interface=" + hw + " (class " + hw.getClass() + "), interface doesn't implement HasResettablePixelArray");
                        return;
                    }
                    log.info("resetting pixels");
                    ((HasResettablePixelArray) hw).resetPixelArray();
                    setArrayResetMenuItem.setSelected(false); // after this reset, the array will not be held in reset
                }
            });
            dvs128Menu.add(arrayResetMenuItem);

            setArrayResetMenuItem = new JCheckBoxMenuItem("Hold array in reset");
            setArrayResetMenuItem.setToolTipText("Sets the entire pixel array in reset");
            setArrayResetMenuItem.addActionListener(new ActionListener() {

                public void actionPerformed(ActionEvent evt) {
                    HardwareInterface hw = getHardwareInterface();
                    if (hw == null || !(hw instanceof HasResettablePixelArray)) {
                        log.warning("cannot reset pixels with hardware interface=" + hw + " (class " + hw.getClass() + "), interface doesn't implement HasResettablePixelArray");
                        return;
                    }
                    log.info("setting pixel array reset=" + setArrayResetMenuItem.isSelected());
                    ((HasResettablePixelArray) hw).setArrayReset(setArrayResetMenuItem.isSelected());
                }
            });

            dvs128Menu.add(setArrayResetMenuItem);
        }

        if (syncEnabledMenuItem == null && getHardwareInterface() != null && getHardwareInterface() instanceof HasSyncEventOutput) {
            syncEnabledMenuItem = new JCheckBoxMenuItem("Enable sync event output");
            syncEnabledMenuItem.setToolTipText("Enables sync event output (disables slave clock input)");
            HasSyncEventOutput h = (HasSyncEventOutput) getHardwareInterface();
            syncEnabledMenuItem.setSelected(h.isSyncEventEnabled());

            syncEnabledMenuItem.addActionListener(new ActionListener() {

                public void actionPerformed(ActionEvent evt) {
                    HardwareInterface hw = getHardwareInterface();
                    if (hw == null || !(hw instanceof HasSyncEventOutput)) {
                        log.warning("cannot change sync enabled state of " + hw + " (class " + hw.getClass() + "), interface doesn't implement HasSyncEventOutput");
                        return;
                    }
                    log.info("setting sync enabled");
                    ((HasSyncEventOutput) hw).setSyncEventEnabled(((AbstractButton) evt.getSource()).isSelected());
                }
            });
            dvs128Menu.add(syncEnabledMenuItem);
        }

        // if hw interface is not correct type then disable menu items
        if (getHardwareInterface() == null) {
            if (arrayResetMenuItem != null) {
                arrayResetMenuItem.setEnabled(false);
            }
            if (setArrayResetMenuItem != null) {
                setArrayResetMenuItem.setEnabled(false);
            }
            if (syncEnabledMenuItem != null) {
                syncEnabledMenuItem.setEnabled(false);
            }
        } else {
            if (!(getHardwareInterface() instanceof HasResettablePixelArray)) {
                if (arrayResetMenuItem != null) {
                    arrayResetMenuItem.setEnabled(false);
                }
                if (setArrayResetMenuItem != null) {
                    setArrayResetMenuItem.setEnabled(false);
                }
            } else {
                arrayResetMenuItem.setEnabled(true);
                setArrayResetMenuItem.setEnabled(true);
            }
            if (!(getHardwareInterface() instanceof HasSyncEventOutput)) {
                if (syncEnabledMenuItem != null) {
                    syncEnabledMenuItem.setEnabled(false);
                }
            } else {
                syncEnabledMenuItem.setEnabled(true);
            }

        }

    }

    /** the event extractor for DVS128. DVS128 has two polarities 0 and 1. Here the polarity is flipped by the extractor so that the raw polarity 0 becomes 1
    in the extracted event. The ON events have raw polarity 0.
    1 is an ON event after event extraction, which flips the type. Raw polarity 1 is OFF event, which becomes 0 after extraction.
     */
    public class Extractor extends RetinaExtractor {

        final int XMASK = 0xfe, XSHIFT = 1, YMASK = 0xff00, YSHIFT = 8;

        public Extractor(DVS128andCochleaAMS1b chip) {
            super(chip);
            setXmask((short) 0x00fe);
            setXshift((byte) 1);
            setYmask((short) 0xff00);
            setYshift((byte) 8);
            setTypemask((short) 1);
            setTypeshift((byte) 0);
            setFlipx(true);
            setFlipy(false);
            setFliptype(true);
        }

        /** extracts the meaning of the raw events.
         *@param in the raw events, can be null
         *@return out the processed events. these are partially processed in-place. empty packet is returned if null is supplied as in.
         */
        @Override
        synchronized public EventPacket extractPacket(AEPacketRaw in) {
            if (out == null) {
                out = new EventPacket<PolarityEvent>(getChip().getEventClass());
            } else {
                out.clear();
            }
            if (in == null) {
                return out;
            }
            int n = in.getNumEvents(); //addresses.length;

            int skipBy = 1;
            if (isSubSamplingEnabled()) {
                while (n / skipBy > getSubsampleThresholdEventCount()) {
                    skipBy++;
                }
            }
            int sxm = sizeX - 1;
            int[] a = in.getAddresses();
            int[] timestamps = in.getTimestamps();
            OutputEventIterator outItr = out.outputIterator();
            for (int i = 0; i < n; i += skipBy) { // TODO bug here?
                int addr = a[i];
                // TODO switch here depending on hw interface having this enabled.
//                if (addr == HasSyncEventOutput.SYNC_ADDRESS) {
//                    log.info("special event at timestamp=" + timestamps[i]);
//                    continue; // TODO do something here?
//                }
                if (addr > 65534) {
                    continue; // outside address space - presumably SyncEvent from external trigger
                    // TODO handle this by outputting SyncEvent's instead of PolarityEvent's
                } else {
                    PolarityEvent e = (PolarityEvent) outItr.nextOutput();
                    e.address=addr;
                    e.timestamp = (timestamps[i]);
                    e.type = (byte) (1 - addr & 1);
                    e.polarity = e.type == 0 ? PolarityEvent.Polarity.Off : PolarityEvent.Polarity.On;
                    e.x = (short) (sxm - ((short) ((addr & XMASK) >>> XSHIFT)));
                    e.y = (short) ((addr & YMASK) >>> YSHIFT);
                }

//                // debug
//                if((addr&(1<<15))!=0){
//                    log.info("found stereo event (bit 15 set) at timstamp="+timestamps[i]);
//                }
            }
            return out;
        }
    }

    /** overrides the Chip setHardware interface to construct a biasgen if one doesn't exist already.
     * Sets the hardware interface and the bias generators hardware interface
     *@param hardwareInterface the interface
     */
    @Override
    public void setHardwareInterface(final HardwareInterface hardwareInterface) {
        this.hardwareInterface = hardwareInterface;
        try {
            if (getBiasgen() == null) {
                setBiasgen(new DVS128andCochleaAMS1b.Biasgen(this));
            } else {
                getBiasgen().setHardwareInterface((BiasgenHardwareInterface) hardwareInterface);
            }
        } catch (ClassCastException e) {
            System.err.println(e.getMessage() + ": probably this chip object has a biasgen but the hardware interface doesn't, ignoring");
        }
        setChanged();
        notifyObservers(hardwareInterface);
    }

//    /** Called when this DVS128 notified, e.g. by having its AEViewer set
//     @param the calling object
//     @param arg the argument passed
//     */
//    public void update(Observable o, Object arg) {
//        log.info("DVS128: received update from Observable="+o+", arg="+arg);
//    }
    @Override
    public void setAeViewer(AEViewer v) {
        super.setAeViewer(v);
        if (v != null) {
            JMenuBar b = v.getJMenuBar();
            int n = b.getMenuCount();
            for (int i = 0; i < n; i++) {
                JMenu m = b.getMenu(i);
                if (m != null && m.getText().equals("DVS128")) {
                    b.remove(m);
                }
            }
            dvs128Menu = new JMenu("DVS128");
            dvs128Menu.getPopupMenu().setLightWeightPopupEnabled(false); // to paint on GLCanvas
            dvs128Menu.setToolTipText("Specialized menu for DVS128 chip");

            boolean didit = false;
            for (int i = 0; i < n; i++) {
                JMenu m = b.getMenu(i);
                if (m != null && m.getText().equals("Help")) {
                    v.getJMenuBar().add(dvs128Menu, i);
                    didit = true;
                }
            }
            if (!didit) {
                v.getJMenuBar().add(dvs128Menu);
            }
            v.validate();
        }
    }

    /**
     * Describes IPots on DVS128 retina chip. These are configured by a shift register as shown here:
     *<p>
     *<img src="doc-files/tmpdiff128biasgen.gif" alt="tmpdiff128 shift register arrangement"/>

    <p>
    This bias generator also offers an abstracted ChipControlPanel interface that is used for a simplified user interface.
     *
     * @author tobi
     */
    public class Biasgen extends net.sf.jaer.biasgen.Biasgen implements ChipControlPanel, DVSTweaks {

        private IPot diffOn, diffOff, refr, pr, sf, diff;

        /** Creates a new instance of Biasgen for DVS128 with a given hardware interface
         *@param chip the chip this biasgen belongs to
         */
        public Biasgen(Chip chip) {
            super(chip);
            setName("DVS128");


//  /** Creates a new instance of IPot
//     *@param biasgen
//     *@param name
//     *@param shiftRegisterNumber the position in the shift register, 0 based, starting on end from which bits are loaded
//     *@param type (NORMAL, CASCODE)
//     *@param sex Sex (N, P)
//     * @param bitValue initial bitValue
//     *@param displayPosition position in GUI from top (logical order)
//     *@param tooltipString a String to display to user of GUI telling them what the pots does
//     */
////    public IPot(Biasgen biasgen, String name, int shiftRegisterNumber, final Type type, Sex sex, int bitValue, int displayPosition, String tooltipString) {

            // create potArray according to our needs
            setPotArray(new IPotArray(this));

            getPotArray().addPot(new IPot(this, "cas", 11, IPot.Type.CASCODE, IPot.Sex.N, 0, 2, "Photoreceptor cascode"));
            getPotArray().addPot(new IPot(this, "injGnd", 10, IPot.Type.CASCODE, IPot.Sex.P, 0, 7, "Differentiator switch level, higher to turn on more"));
            getPotArray().addPot(new IPot(this, "reqPd", 9, IPot.Type.NORMAL, IPot.Sex.N, 0, 12, "AER request pulldown"));
            getPotArray().addPot(new IPot(this, "puX", 8, IPot.Type.NORMAL, IPot.Sex.P, 0, 11, "2nd dimension AER static pullup"));
            getPotArray().addPot(diffOff = new IPot(this, "diffOff", 7, IPot.Type.NORMAL, IPot.Sex.N, 0, 6, "OFF threshold, lower to raise threshold"));
            getPotArray().addPot(new IPot(this, "req", 6, IPot.Type.NORMAL, IPot.Sex.N, 0, 8, "OFF request inverter bias"));
            getPotArray().addPot(refr = new IPot(this, "refr", 5, IPot.Type.NORMAL, IPot.Sex.P, 0, 9, "Refractory period"));
            getPotArray().addPot(new IPot(this, "puY", 4, IPot.Type.NORMAL, IPot.Sex.P, 0, 10, "1st dimension AER static pullup"));
            getPotArray().addPot(diffOn = new IPot(this, "diffOn", 3, IPot.Type.NORMAL, IPot.Sex.N, 0, 5, "ON threshold - higher to raise threshold"));
            getPotArray().addPot(diff = new IPot(this, "diff", 2, IPot.Type.NORMAL, IPot.Sex.N, 0, 4, "Differentiator"));
            getPotArray().addPot(sf = new IPot(this, "foll", 1, IPot.Type.NORMAL, IPot.Sex.P, 0, 3, "Src follower buffer between photoreceptor and differentiator"));
            getPotArray().addPot(pr = new IPot(this, "Pr", 0, IPot.Type.NORMAL, IPot.Sex.P, 0, 1, "Photoreceptor"));

            loadPreferences();

        }

        /** sends the ipot values over the hardware interface if there is not a batch edit occuring.
         *@param biasgen the bias generator object.
         * This parameter is necessary because the same method is used in the hardware interface,
         * which doesn't know about the particular bias generator instance.
         *@throws HardwareInterfaceException if there is a hardware error. If there is no interface, prints a message and just returns.
         *@see #startBatchEdit
         *@see #endBatchEdit
         **/
        public void sendConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
            if (hardwareInterface == null) {
//            log.warning("Biasgen.sendIPotValues(): no hardware interface");
                return;
            }
            if (!isBatchEditOccurring() && hardwareInterface != null) {
//            log.info("calling hardwareInterface.sendConfiguration");
//            hardwareInterface.se(this);
            }
        }
        /** the change in current from an increase* or decrease* call */
        public final float RATIO = 1.05f;
        /** the minimum on/diff or diff/off current allowed by decreaseThreshold */
        public final float MIN_THRESHOLD_RATIO = 2f;
        public final float MAX_DIFF_ON_CURRENT = 6e-6f;
        public final float MIN_DIFF_OFF_CURRENT = 1e-9f;

        synchronized public void increaseThreshold() {
            if (diffOn.getCurrent() * RATIO > MAX_DIFF_ON_CURRENT) {
                return;
            }
            if (diffOff.getCurrent() / RATIO < MIN_DIFF_OFF_CURRENT) {
                return;
            }
            diffOn.changeByRatio(RATIO);
            diffOff.changeByRatio(1 / RATIO);
        }

        synchronized public void decreaseThreshold() {
            float diffI = diff.getCurrent();
            if (diffOn.getCurrent() / MIN_THRESHOLD_RATIO < diffI) {
                return;
            }
            if (diffOff.getCurrent() > diffI / MIN_THRESHOLD_RATIO) {
                return;
            }
            diffOff.changeByRatio(RATIO);
            diffOn.changeByRatio(1 / RATIO);
        }

        synchronized public void increaseRefractoryPeriod() {
            refr.changeByRatio(1 / RATIO);
        }

        synchronized public void decreaseRefractoryPeriod() {
            refr.changeByRatio(RATIO);
        }

        synchronized public void increaseBandwidth() {
            pr.changeByRatio(RATIO);
            sf.changeByRatio(RATIO);
        }

        synchronized public void decreaseBandwidth() {
            pr.changeByRatio(1 / RATIO);
            sf.changeByRatio(1 / RATIO);
        }

        synchronized public void moreONType() {
            diffOn.changeByRatio(1 / RATIO);
            diffOff.changeByRatio(RATIO);
        }

        synchronized public void moreOFFType() {
            diffOn.changeByRatio(RATIO);
            diffOff.changeByRatio(1 / RATIO);
        }
        JComponent expertTab, basicTab;

        /** @return a new panel for controlling this bias generator functionally
         */
        @Override
        public JPanel buildControlPanel() {
            JPanel panel = new JPanel();
            panel.setLayout(new BorderLayout());
            final JTabbedPane pane = new JTabbedPane();

            pane.addTab("Basic controls", basicTab = new DVSFunctionalControlPanel(DVS128andCochleaAMS1b.this));
            pane.addTab("Expert controls", expertTab = super.buildControlPanel());
            panel.add(pane, BorderLayout.CENTER);
            pane.setSelectedIndex(getPrefs().getInt("DVS128.selectedBiasgenControlTab", 0));
            pane.addMouseListener(new java.awt.event.MouseAdapter() {

                public void mouseClicked(java.awt.event.MouseEvent evt) {
                    getPrefs().putInt("DVS128.selectedBiasgenControlTab", pane.getSelectedIndex());
                }
            });

            return panel;
        }

        public void setBandwidthTweak(float val) {
            final float MAX = 300;
            pr.changeByRatioFromPreferred(PotTweakerUtilities.getRatioTweak(val, MAX));
            sf.changeByRatioFromPreferred(PotTweakerUtilities.getRatioTweak(val, MAX));
        }

        public void setMaxFiringRateTweak(float val) {
            final float MAX = 300;
            refr.changeByRatioFromPreferred(PotTweakerUtilities.getRatioTweak(val, MAX));
        }

        public void setThresholdTweak(float val) {
            final float MAX = 100;
            diffOn.changeByRatioFromPreferred(PotTweakerUtilities.getRatioTweak(val, MAX));
            diffOff.changeByRatioFromPreferred(1 / PotTweakerUtilities.getRatioTweak(val, MAX));
        }

        public void setOnOffBalanceTweak(float val) {
            final float MAX = 100;
            diff.changeByRatioFromPreferred(PotTweakerUtilities.getRatioTweak(val, MAX));
        }

        @Override
        public float getBandwidthTweak() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public float getMaxFiringRateTweak() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public float getThresholdTweak() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public float getOnOffBalanceTweak() {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    } // Tmpdiff128Biasgen
}
