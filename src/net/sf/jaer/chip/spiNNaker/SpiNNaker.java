/*
 * SpiNNaker.java
 *
 * Created on October 5, 2005, 11:36 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */
package net.sf.jaer.chip.spiNNaker;

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
import javax.swing.JRadioButtonMenuItem;

import net.sf.jaer.Description;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.RetinaExtractor;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.HasLEDControl;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.HasLEDControl.LEDState;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.HasResettablePixelArray;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.HasSyncEventOutput;
import net.sf.jaer.util.RemoteControlCommand;
import net.sf.jaer.util.RemoteControlled;
import net.sf.jaer.util.WarningDialogWithDontShowPreference;
import ch.unizh.ini.jaer.chip.retina.AETemporalConstastRetina;

/**
 * Describes SpiNNaker and its event extractor and bias generator.
 * 
 * <p>
 * Two constructors ara available, the vanilla constructor is used for event playback and the
 *one with a HardwareInterface parameter is useful for live capture.
 * {@link #setHardwareInterface} is used when the hardware interface is constructed after the retina object.
 *The constructor that takes a hardware interface also constructs the biasgen interface.
 *
 * @author willconstable
 */
@Description("SpiNNaker Dynamic Vision Sensor")
public class SpiNNaker extends AETemporalConstastRetina implements Serializable, Observer, RemoteControlled {

    private JMenu spinnakerMenu = null;
    private JMenuItem arrayResetMenuItem = null, syncEnabledMenuItem = null;
    private JMenuItem setArrayResetMenuItem = null;
    private JMenu ledMenu = null;
    public static final String CMD_TWEAK_THESHOLD = "threshold", CMD_TWEAK_ONOFF_BALANCE = "balance", CMD_TWEAK_BANDWIDTH = "bandwidth", CMD_TWEAK_MAX_FIRING_RATE = "maxfiringrate";
    JComponent helpMenuItem1 = null, helpMenuItem2 = null, helpMenuItem3=null;


    /** Creates a new instance of SpiNNaker. No biasgen is constructed for this constructor, because there is no hardware interface defined. */
    public SpiNNaker() {
        setName("SpiNNaker");
        setSizeX(128);
        setSizeY(128);
        setNumCellTypes(2);
        setPixelHeightUm(40);
        setPixelWidthUm(40);
        setEventExtractor(new Extractor(this));
        if (getRemoteControl() != null) {
            getRemoteControl().addCommandListener(this, CMD_TWEAK_BANDWIDTH, CMD_TWEAK_BANDWIDTH + " val - tweaks bandwidth. val in range -1.0 to 1.0.");
            getRemoteControl().addCommandListener(this, CMD_TWEAK_ONOFF_BALANCE, CMD_TWEAK_ONOFF_BALANCE + " val - tweaks on/off balance; increase for more ON events. val in range -1.0 to 1.0.");
            getRemoteControl().addCommandListener(this, CMD_TWEAK_MAX_FIRING_RATE, CMD_TWEAK_MAX_FIRING_RATE + " val - tweaks max firing rate; increase to reduce refractory period. val in range -1.0 to 1.0.");
            getRemoteControl().addCommandListener(this, CMD_TWEAK_THESHOLD, CMD_TWEAK_THESHOLD + " val - tweaks threshold; increase to raise threshold. val in range -1.0 to 1.0.");
        }
        //        ChipCanvas c = getCanvas();
        addObserver(this);

    }

    /** Creates a new instance of SpiNNaker
     * @param hardwareInterface an existing hardware interface. This constructor is preferred.
     */
    public SpiNNaker(HardwareInterface hardwareInterface) {
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
                        log.warning("cannot reset pixels with hardware interface=" + hw + " (class " + (hw != null ? hw.getClass() : null) + "), interface doesn't implement HasResettablePixelArray");
                        return;
                    }
                    log.info("resetting pixels");
                    ((HasResettablePixelArray) hw).resetPixelArray();
                    setArrayResetMenuItem.setSelected(false); // after this reset, the array will not be held in reset
                }
            });
            spinnakerMenu.add(arrayResetMenuItem);

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

            spinnakerMenu.add(setArrayResetMenuItem);
        }

        if (syncEnabledMenuItem == null && getHardwareInterface() != null && getHardwareInterface() instanceof HasSyncEventOutput) {
            syncEnabledMenuItem = new JCheckBoxMenuItem("Timestamp master / Enable sync event output");
          //  syncEnabledMenuItem.setToolTipText("<html>Sets this device as timestamp master and enables sync event generation on external IN pin falling edges (disables slave clock input).<br>Falling edges inject special sync events with bitmask " + HexString.toString(CypressFX2SpiNNakerHardwareInterface.SYNC_EVENT_BITMASK) + " set<br>These events are not rendered but are logged and can be used to synchronize an external signal to the recorded data.<br>If you are only using one camera, enable this option.<br>If you want to synchronize two SpiNNaker, disable this option in one of the cameras and connect the OUT pin of the master to the IN pin of the slave and also connect the two GND pins.");
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
            spinnakerMenu.add(syncEnabledMenuItem);
            // show warning dialog (which can be suppressed) about this setting if special disabled and we are the only camera, since
            // timestamps will not advance in this case
            if (!h.isSyncEventEnabled()) {
                WarningDialogWithDontShowPreference d = new WarningDialogWithDontShowPreference(null, false, "Timestamps disabled",
                        "<html>Timestamps may not advance if you are using the SpiNNaker as a standalone camera. <br>Use SpiNNaker/Timestamp master / Enable sync event output to enable them.");
                d.setVisible(true);
            }
        }

        if (ledMenu == null && getHardwareInterface() != null && getHardwareInterface() instanceof HasLEDControl) {
            ledMenu = new JMenu("LED control");
            ledMenu.setToolTipText("LED control");
            final HasLEDControl h = (HasLEDControl) getHardwareInterface();
            final JRadioButtonMenuItem ledOnBut = new JRadioButtonMenuItem("Turn LED on");
            final JRadioButtonMenuItem ledOffBut = new JRadioButtonMenuItem("Turn LED off");
            final JRadioButtonMenuItem ledFlashingBut = new JRadioButtonMenuItem("Make LED flash");
            final ButtonGroup group = new ButtonGroup();
            group.add(ledOnBut);
            group.add(ledOffBut);
            group.add(ledFlashingBut);
            ledMenu.add(ledOffBut);
            ledMenu.add(ledOnBut);
            ledMenu.add(ledFlashingBut);
            switch (h.getLEDState(0)) {
                case ON:
                    ledOnBut.setSelected(true);
                    break;
                case OFF:
                    ledOffBut.setSelected(true);
                    break;
                case FLASHING:
                    ledFlashingBut.setSelected(true);
                    break;
            }

            ActionListener ledListener = new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    HardwareInterface hw = getHardwareInterface();
                    if (hw == null || !(hw instanceof HasLEDControl)) {
                        log.warning("cannot set LED of " + hw + " (class " + hw.getClass() + "), interface doesn't implement HasLEDControl");
                        return;
                    }
                    HasLEDControl h = (HasLEDControl) hw;
                    if (e.getSource() == ledOffBut) {
                        h.setLEDState(0, LEDState.OFF);
                    } else if (e.getSource() == ledOnBut) {
                        h.setLEDState(0, LEDState.ON);
                    } else if (e.getSource() == ledFlashingBut) {
                        h.setLEDState(0, LEDState.FLASHING);
                    }
                }
            };

            ledOffBut.addActionListener(ledListener);
            ledOnBut.addActionListener(ledListener);
            ledFlashingBut.addActionListener(ledListener);

            spinnakerMenu.add(ledMenu);
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
            if (ledMenu != null) {
                ledMenu.setEnabled(false);
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
            if (!(getHardwareInterface() instanceof HasLEDControl)) {
                if (ledMenu != null) {
                    ledMenu.setEnabled(false);
                }
            } else {
                ledMenu.setEnabled(true);
            }
        }

    }

    @Override
    public void onDeregistration() {
        super.onRegistration();
        if (getAeViewer() == null) {
            return;
        }
        getAeViewer().removeHelpItem(helpMenuItem1);
        getAeViewer().removeHelpItem(helpMenuItem2);
        getAeViewer().removeHelpItem(helpMenuItem3);
    }
/*
    @Override
    public void onRegistration() {
        super.onRegistration();
        if (getAeViewer() == null) {
            return;
        }
*/      

    @Override
    public String processRemoteControlCommand(RemoteControlCommand command, String input) {
        return input + ": unknown command";
    }

    /** the event extractor for SpiNNaker. SpiNNaker has two polarities 0 and 1. Here the polarity is flipped by the extractor so that the raw polarity 0 becomes 1
    in the extracted event. The ON events have raw polarity 0.
    1 is an ON event after event extraction, which flips the type. Raw polarity 1 is OFF event, which becomes 0 after extraction.
     */
    public class Extractor extends RetinaExtractor {

        final short XMASK = 0xfe, XSHIFT = 1, YMASK = 0x7f00, YSHIFT = 8;

        public Extractor(SpiNNaker chip) {
            super(chip);
            setXmask((short) 0x00fe);
            setXshift((byte) 1);
            setYmask((short) 0x7f00);
            setYshift((byte) 8);
            setTypemask((short) 1);
            setTypeshift((byte) 0);
            setFlipx(true);
            setFlipy(false);
            setFliptype(true);
        }

        /** extracts the meaning of the raw events.
         *@param in the raw events, can be null
         *@return out the processed events. these are partially processed in-place. empty packet is returned if null is supplied as in. This event packet is reused
         * and should only be used by a single thread of execution or for a single input stream, or mysterious results may occur!
         */
        @Override
        synchronized public EventPacket extractPacket(AEPacketRaw in) {
            if (out == null) {
                out = new EventPacket<PolarityEvent>(chip.getEventClass());
            } else {
                out.clear();
            }
            extractPacket(in, out);
            return out;
        }
        private int printedSyncBitWarningCount = 3;

        /**
         * Extracts the meaning of the raw events. This form is used to supply an output packet. This method is used for real time
         * event filtering using a buffer of output events local to data acquisition. An AEPacketRaw may contain multiple events,
         * not all of them have to sent out as EventPackets. An AEPacketRaw is a set(!) of addresses and corresponding timing moments.
         *
         * A first filter (independent from the other ones) is implemented by subSamplingEnabled and getSubsampleThresholdEventCount.
         * The latter may limit the amount of samples in one package to say 50,000. If there are 160,000 events and there is a sub sample
         * threshold of 50,000, a "skip parameter" set to 3. Every so now and then the routine skips with 4, so we end up with 50,000.
         * It's an approximation, the amount of events may be less than 50,000. The events are extracted uniform from the input.
         *
         * @param in 		the raw events, can be null
         * @param out 		the processed events. these are partially processed in-place. empty packet is returned if null is
         * 					supplied as input.
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
                while (n / skipBy > getSubsampleThresholdEventCount()) {
                    skipBy++;
                }
            }
            int sxm = sizeX - 1;
            int[] a = in.getAddresses();
            int[] timestamps = in.getTimestamps();
            OutputEventIterator outItr = out.outputIterator();
            for (int i = 0; i < n; i += skipBy) { // TODO bug here?
                int addr = a[i]; // TODO handle special events from hardware correctly
                PolarityEvent e = (PolarityEvent) outItr.nextOutput();
                e.address = addr;
                e.timestamp = (timestamps[i]);

                e.setSpecial(false);
                e.type = (byte) (1 - addr & 1);
                e.polarity = e.type == 0 ? PolarityEvent.Polarity.Off : PolarityEvent.Polarity.On;
                e.x = (short) (sxm - ((short) ((addr & XMASK) >>> XSHIFT)));
                e.y = (short) ((addr & YMASK) >>> YSHIFT);
                
            }
        }
    }

    /** 
     * Sets the hardware interface and the bias generators hardware interface
     *@param hardwareInterface the interface
     */
    /*
    @Override
    public void setHardwareInterface(final HardwareInterface hardwareInterface) {
        super.setHardwareInterface(hardwareInterface);
        this.hardwareInterface = hardwareInterface;
        try {
            if (getBiasgen() == null) {
                setBiasgen(new SpiNNaker.Biasgen(this));
            } else {
                getBiasgen().setHardwareInterface((BiasgenHardwareInterface) hardwareInterface);
            }
        } catch (ClassCastException e) {
            System.err.println(e.getMessage() + ": probably this chip object has a biasgen but the hardware interface doesn't, ignoring");
        }
        setChanged();
        notifyObservers(hardwareInterface);
    }
    * */

//    /** Called when this SpiNNaker notified, e.g. by having its AEViewer set
//     @param the calling object
//     @param arg the argument passed
//     */
//    public void update(Observable o, Object arg) {
//        log.info("SpiNNaker: received update from Observable="+o+", arg="+arg);
//    }
    /** Called when the AEViewer is set for this AEChip. Here we add the menu to the AEViewer.
     *
     * @param v the viewer
     */
    @Override
    public void setAeViewer(AEViewer v) {
        super.setAeViewer(v);
        if (v != null) {

            spinnakerMenu = new JMenu("SpiNNaker");
            spinnakerMenu.getPopupMenu().setLightWeightPopupEnabled(false); // to paint on GLCanvas
            spinnakerMenu.setToolTipText("Specialized menu for SpiNNaker chip");

            v.setMenu(spinnakerMenu);
        }
    }


  }
