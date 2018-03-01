/*
 * MDC2D.java
 *
 * Created on November 12, 2010, 09:50 PM
 *
 *  Copyright T. Delbruck, Inst. of Neuroinformatics, 2010
 */

package ch.unizh.ini.jaer.projects.opticalflow.mdc2d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Observable;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;

import javax.swing.JPanel;

import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.BiasgenFrame;
import net.sf.jaer.biasgen.ChipControlPanel;
import net.sf.jaer.biasgen.IPot;
import net.sf.jaer.biasgen.IPotArray;
import net.sf.jaer.biasgen.Pot;
import net.sf.jaer.biasgen.PotArray;
import net.sf.jaer.biasgen.VDAC.DAC;
import net.sf.jaer.biasgen.VDAC.VPot;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.util.RemoteControlCommand;
import net.sf.jaer.util.RemoteControlled;
import ch.unizh.ini.jaer.projects.opticalflow.Chip2DMotion;
import ch.unizh.ini.jaer.projects.opticalflow.MotionData;
import ch.unizh.ini.jaer.projects.opticalflow.graphics.BiasgenPanelMDC2D;
import ch.unizh.ini.jaer.projects.opticalflow.graphics.OpticalFlowDisplayMethod;

/**
 * Describes the MDC2D chip from Shih-Chii Liu and Alan Stocker
 *
 * @author reto
 *
 * changes by andstein for compatability with new hardware interfaces
 * <ul>
 * <li>removed some hardware specific method calls into the respective
 *     HardwareInterace</li>
 * </ul>
 */
public class MDC2D extends Chip2DMotion  {

    // the names of the optic flow methods. The String array must have the same
    // order as the numbering of the constants below
    public static final String[] MOTIONMETHODLIST ={"Random","Normal Optic Flow","Srinivasan","Local Srinivasan","Time of Travel"};
    public static final int     RANDOM=0;
    public static final int     NORMAL_OPTICFLOW=1;
    public static final int     SRINIVASAN=2;
    public static final int     LOCAL_SRINIVASAN=3;
    public static final int     TIME_OF_TRAVEL=4;

    private static int selectedMotionMethodIndex; // only provides a number. To set and interpret the number has to be done in the MotionData class
    private static int rawChannelUsedByMotionMethod;


    /** Creates a new instance of MDC2D */
    public MDC2D() {
        CHIPNAME="MDC2D";
        VDD=(float)3.3;
        NUM_ROWS=20;
        NUM_COLUMNS=20;
        NUM_MOTION_PIXELS=NUM_COLUMNS*NUM_ROWS;
        acquisitionMode=MotionData.PHOTO|MotionDataMDC2D.LMC1|MotionDataMDC2D.LMC2|MotionDataMDC2D.ON_CHIP_ADC;
        dac=new DAC(16,12,0,5,VDD);
        setBiasgen(new MDC2DBiasgen(this, dac));
        setSizeX(NUM_COLUMNS);
        setSizeY(NUM_ROWS);
        setName(CHIPNAME); // for biasgen window title

        getCanvas().addDisplayMethod(new OpticalFlowDisplayMethod(this.getCanvas()));
     }


    // Returns a empty MotionData MDC2D Object
    @Override
    public MotionData getEmptyMotionData(){
        return new MotionDataMDC2D(this);
    }

    public static void setMotionMethod(int m){
        selectedMotionMethodIndex=m;
    }

    public static void setChannelForMotionAlgorithm(int chan){
        rawChannelUsedByMotionMethod=chan;
    }

    public static int getMotionMethod(){
        return selectedMotionMethodIndex;
    }

    public static int getChannelForMotionAlgorithm(){
        return rawChannelUsedByMotionMethod;
    }

    /**
     * Converts 10 bits single ended ADC output value to a float ranged 0-1.
     * 0 represents GND, 1 is most positive value (VDD).
     * @param value the 10 bit value.
     * @return the float value, ranges from 0 to 1023/1024 inclusive.
     */
    @Override
    public float convert10bitToFloat(int value) {
        return (float)value/1023;
    }


    /** describes the biases on the chip */
    public class MDC2DBiasgen extends Biasgen implements ChipControlPanel{




        public PotArray ipots = new IPotArray(this);
        public PotArray vpots = new PotArray(this);
        public int[] potValues;

        Chip chipp;

        public MDC2DBiasgen(Chip chip, DAC dac){
            super(chip);
            chipp=chip;


            bufferIPot.addObserver(this);

            potArray = new IPotArray(this);  // create the appropriate PotArray
            ipots=new IPotArray(this);
            vpots=new PotArray(this);

            potArray=vpots;

            // create the appropriate PotArray
            vpots.addPot(new VPot(MDC2D.this, "VRegRefBiasAmp", dac, 0, Pot.Type.NORMAL, Pot.Sex.P, 1,       16, "sets bias of feedback follower in srcbias"));
            vpots.addPot(new VPot(MDC2D.this,"VRegRefBiasMain",dac,      1,Pot.Type.NORMAL,Pot.Sex.P,1,      17,"sets bias of pfet which sets ref to srcbias"));
            vpots.addPot(new VPot(MDC2D.this,"VprBias",dac,              2,Pot.Type.NORMAL,Pot.Sex.P,1,      18,"bias current for photoreceptor"));
            vpots.addPot(new VPot(MDC2D.this,"Vlmcfb",dac,               3,Pot.Type.NORMAL,Pot.Sex.N,1,      19,"bias current for diffosor"));
            vpots.addPot(new VPot(MDC2D.this,"Vprbuff",dac,              4,Pot.Type.NORMAL,Pot.Sex.P,1,      20,"bias current for pr scr foll to lmc1"));
            vpots.addPot(new VPot(MDC2D.this,"Vprlmcbias",dac,           5,Pot.Type.NORMAL,Pot.Sex.P,1,      21,"bias current for lmc1"));
            vpots.addPot(new VPot(MDC2D.this,"Vlmcbuff",dac,             6,Pot.Type.NORMAL,Pot.Sex.P,1,      22,"bias current for lmc2"));
            vpots.addPot(new VPot(MDC2D.this,"Screfpix",dac,            7,Pot.Type.NORMAL,Pot.Sex.N,1,       23,"sets scr bias for lmc2"));
            vpots.addPot(new VPot(MDC2D.this,"FollBias",dac,            8,Pot.Type.NORMAL,Pot.Sex.N,1,       24,"sets bias for follower in pads"));
            vpots.addPot(new VPot(MDC2D.this,"Vpscrcfbias",dac,          9,Pot.Type.NORMAL,Pot.Sex.P,1,      25,"sets bias for ptype src foll in scanner readout"));
            vpots.addPot(new VPot(MDC2D.this,"VADCbias",dac,             0xa,Pot.Type.NORMAL,Pot.Sex.P,1,    26,"sets bias current for comperator in ADC"));
            vpots.addPot(new VPot(MDC2D.this,"Vrefminbias",dac,          0xb,Pot.Type.NORMAL,Pot.Sex.N,1,    27,"sets bias for Srcrefmin follower from resis divider"));
            vpots.addPot(new VPot(MDC2D.this,"Srcrefmin",dac,           0xc,Pot.Type.NORMAL,Pot.Sex.P,1,     28,"sets half Vdd for ADC"));
            vpots.addPot(new VPot(MDC2D.this,"refnegDAC",dac,           0xd,Pot.Type.NORMAL,Pot.Sex.na,1,    29,"description"));
            vpots.addPot(new VPot(MDC2D.this,"refposDAC",dac,           0xe,Pot.Type.NORMAL,Pot.Sex.na,1,    30,"description"));


            //ipotArray = new IPotArray(this); //construct IPotArray whit shift register stuff
            ipots.addPot(new IPot(this,"VRegRefBiasAmp", 0, IPot.Type.NORMAL, Pot.Sex.P, 1,       0, "sets bias of feedback follower in srcbias"));
            ipots.addPot(new IPot(this,"Vrefminbias",          1,Pot.Type.NORMAL,Pot.Sex.N,1,    1,"sets bias for Srcrefmin follower from resis divider"));
            ipots.addPot(new IPot(this,"VprBias",              2,Pot.Type.NORMAL,Pot.Sex.P,1,      2,"bias current for photoreceptor"));
            ipots.addPot(new IPot(this,"VRegRefBiasMain",      3,Pot.Type.NORMAL,Pot.Sex.P,1,      3,"sets bias of pfet which sets ref to srcbias"));
            ipots.addPot(new IPot(this,"Vlmcfb",               4,Pot.Type.NORMAL,Pot.Sex.N,1,      4,"bias current for diffosor"));
            ipots.addPot(new IPot(this,"Vlmcbuff",             5,Pot.Type.NORMAL,Pot.Sex.P,1,      5,"bias current for lmc2"));
            ipots.addPot(new IPot(this,"VADCbias",             6,Pot.Type.NORMAL,Pot.Sex.P,1,    6,"sets bias current for comperator in ADC"));
            ipots.addPot(new IPot(this,"FollBias",            7,Pot.Type.NORMAL,Pot.Sex.N,1,       7,"sets bias for follower in pads"));
            ipots.addPot(new IPot(this,"Vpscrcfbias",          8,Pot.Type.NORMAL,Pot.Sex.P,1,      8,"sets bias for ptype src foll in scanner readout"));
            ipots.addPot(new IPot(this,"NOT USED",           9,Pot.Type.NORMAL,Pot.Sex.P,1,     9,"not used. Any value will do"));
            ipots.addPot(new IPot(this,"Vprlmcbias",           0xa,Pot.Type.NORMAL,Pot.Sex.P,1,      0xa,"bias current for lmc1"));
            ipots.addPot(new IPot(this,"Vprbuff",              0xb,Pot.Type.NORMAL,Pot.Sex.P,1,      0xb,"bias current for pr scr foll to lmc1"));
   }


        @Override
        public void loadPreferences() {
            //        log.info("Biasgen.loadPreferences()");
            startBatchEdit();
            if (getPotArray() != null) {
                ipots.loadPreferences();
                vpots.loadPreferences();
                getMasterbias().loadPreferences();
            }

            try {
                endBatchEdit();
            } catch (HardwareInterfaceException e) {
                log.warning(e.toString());
            }
        }

        @Override
        public void storePreferences() {
            log.info("storing preferences to preferences tree");
            ipots.storePreferences();
            vpots.storePreferences();
            getMasterbias().storePreferences();
        }

        public PotArray getIPotArray() {
            return ipots;
        }

        public Iterator getShiftRegisterIterator(){
            return ((IPotArray)ipots).getShiftRegisterIterator();
        }


        @Override
        public JPanel buildControlPanel (){
            startBatchEdit();
            BiasgenFrame frame = null;
            if ( chipp instanceof AEChip ){
                AEViewer viewer = ( (AEChip)chipp ).getAeViewer();
                if ( viewer != null ){
                    frame = viewer.getBiasgenFrame();
                } else{
                    log.warning("no BiasgenFrame to build biasgen control panel for");
                    return null;
                }
            }
            JPanel panel = new BiasgenPanelMDC2D(this,frame);    /// makes a panel for the pots and populates it, the frame handles undo support
            try{
                endBatchEdit();
            } catch ( HardwareInterfaceException e ){
                log.warning(e.toString());
            }
            return panel;
        }


        @Override
        public void setPowerDown(boolean powerDown) throws HardwareInterfaceException {
            int pd;
            if(powerDown){
                pd=1;
            }else{
                pd=0;
            }
            hardwareInterface.setPowerDown(powerDown);
        }


        private ArrayList<HasPreference> hasPreferencesList = new ArrayList<HasPreference>();
        BufferIPot bufferIPot = new BufferIPot();

        /** The central point for communication with HW from biasgen. All objects in Biasgen are Observables
        and add Biasgen.this as Observer. They then call notifyObservers when their state changes.
         * @param observable IPot, Scanner, etc
         * @param object not used at present
         */
        @Override
        synchronized public void update(Observable observable, Object object) {  // thread safe to ensure gui cannot retrigger this while it is sending something
//            log.info(observable + " sent " + object);
            if (this.hardwareInterface == null) {
                return;
            }

            // "economised" BufferIPot (what's that for anyway??)
            if ((observable instanceof VPot) || (observable instanceof IPot)) {
                try {
                    hardwareInterface.sendConfiguration(this);
                } catch (HardwareInterfaceException ex) {
                    log.warning("could not send configuration : " + ex);
                }
            } else {
                super.update(observable, object);  // super (Biasgen) handles others, e.g. maasterbias
            }
        }






//        public PropertyChangeSupport getSupport() {
//            return support;
//        }
        class BufferIPot extends Observable implements RemoteControlled, PreferenceChangeListener, HasPreference {

            final int max = 63; // 8 bits
            private volatile int value;
            private final String key = "CochleaAMS1b.Biasgen.BufferIPot.value";

            BufferIPot() {
                if (getRemoteControl() != null) {
                    getRemoteControl().addCommandListener(this, "setbufferbias bitvalue", "Sets the buffer bias value");
                }
                loadPreference();
                getPrefs().addPreferenceChangeListener(this);
                hasPreferencesList.add(this);
            }

            public int getValue() {
                return value;
            }

            public void setValue(int value) {
                if (value > max) {
                    value = max;
                } else if (value < 0) {
                    value = 0;
                }
                this.value = value;

                setChanged();
                notifyObservers();
            }

            @Override
            public String toString() {
                return String.format("BufferIPot with max=%d, value=%d", max, value);
            }

            @Override
			public String processRemoteControlCommand(RemoteControlCommand command, String input) {
                String[] tok = input.split("\\s");
                if (tok.length < 2) {
                    return "bufferbias " + getValue() + "\n";
                } else {
                    try {
                        int val = Integer.parseInt(tok[1]);
                        setValue(val);
                    } catch (NumberFormatException e) {
                        return "?\n";
                    }

                }
                return "bufferbias " + getValue() + "\n";
            }

            @Override
			public void preferenceChange(PreferenceChangeEvent e) {
                if (e.getKey().equals(key)) {
                    setValue(Integer.parseInt(e.getNewValue()));
                }
            }

            @Override
			public void loadPreference() {
                setValue(getPrefs().getInt(key, max / 2));
            }

            @Override
			public void storePreference() {
                putPref(key, value);
            }
        }



        class Scanner extends Observable implements PreferenceChangeListener, HasPreference {

            Scanner() {
                loadPreference();
                getPrefs().addPreferenceChangeListener(this);
                hasPreferencesList.add(this);
            }
            int nstages = 64;
            private volatile int currentStage;
            private volatile boolean continuousScanningEnabled;
            private volatile int period;
            int minPeriod = 10; // to avoid FX2 getting swamped by interrupts for scanclk
            int maxPeriod = 255;

            public int getCurrentStage() {
                return currentStage;
            }

            public void setCurrentStage(int currentStage) {
                this.currentStage = currentStage;
                continuousScanningEnabled = false;
                setChanged();
                notifyObservers();
            }

            public boolean isContinuousScanningEnabled() {
                return continuousScanningEnabled;
            }

            public void setContinuousScanningEnabled(boolean continuousScanningEnabled) {
                this.continuousScanningEnabled = continuousScanningEnabled;
                setChanged();
                notifyObservers();
            }

            public int getPeriod() {
                return period;
            }

            public void setPeriod(int period) {
                if (period < minPeriod) {
                    period = 10; // too small and interrupts swamp the FX2
                }
                if (period > maxPeriod) {
                    period = (byte) (maxPeriod); // unsigned char
                }
                this.period = period;

                setChanged();
                notifyObservers();
            }

            @Override
			public void preferenceChange(PreferenceChangeEvent e) {
                if (e.getKey().equals("CochleaAMS1b.Biasgen.Scanner.currentStage")) {
                    setCurrentStage(Integer.parseInt(e.getNewValue()));
                } else if (e.getKey().equals("CochleaAMS1b.Biasgen.Scanner.currentStage")) {
                    setContinuousScanningEnabled(Boolean.parseBoolean(e.getNewValue()));
                }
            }

            @Override
			public void loadPreference() {
                setCurrentStage(getPrefs().getInt("CochleaAMS1b.Biasgen.Scanner.currentStage", 0));
                setContinuousScanningEnabled(getPrefs().getBoolean("CochleaAMS1b.Biasgen.Scanner.continuousScanningEnabled", false));
                setPeriod(getPrefs().getInt("CochleaAMS1b.Biasgen.Scanner.period", 50)); // 50 gives about 80kHz
            }

            @Override
			public void storePreference() {
                putPref("CochleaAMS1b.Biasgen.Scanner.period", period);
                putPref("CochleaAMS1b.Biasgen.Scanner.continuousScanningEnabled", continuousScanningEnabled);
                putPref("CochleaAMS1b.Biasgen.Scanner.currentStage", currentStage);
            }
        }


    }



}
