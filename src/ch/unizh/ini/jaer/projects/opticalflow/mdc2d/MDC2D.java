/*
 * MDC2D.java
 *
 * Created on November 12, 2010, 09:50 PM
 *
 *  Copyright T. Delbruck, Inst. of Neuroinformatics, 2010
 */

package ch.unizh.ini.jaer.projects.opticalflow.mdc2d;

import ch.unizh.ini.jaer.projects.opticalflow.*;
import ch.unizh.ini.jaer.projects.opticalflow.graphics.BiasgenPanelMDC2D;
import ch.unizh.ini.jaer.projects.opticalflow.graphics.OpticalFlowDisplayMethod;
import java.util.Iterator;
import javax.swing.JPanel;
import net.sf.jaer.biasgen.*;
import net.sf.jaer.biasgen.VDAC.*;
import net.sf.jaer.chip.*;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * Describes the MDC2D chip from Shih-Chii Liu and Alan Stocker
 *
 * @author reto
 */
public class MDC2D extends Chip2DMotion {

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
        acquisitionMode=MotionData.PHOTO|MotionData.BIT5|MotionData.BIT6;
        dac=new DAC(16,12,0,5,VDD);
        setBiasgen(new MDC2DBiasgen(this, dac));
        setSizeX(NUM_COLUMNS);
        setSizeY(NUM_ROWS);

        getCanvas().addDisplayMethod(new OpticalFlowDisplayMethod(this.getCanvas()));
        getCanvas().setOpenGLEnabled(true);
        getCanvas().setScale(22f);
    }


    // Returns a empty MotionData MDC2D Object
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
    public float convert10bitToFloat(int value) {
        return (float)value/1023;
    }
    
 
    /** describes the biases on the chip */
    public class MDC2DBiasgen extends Biasgen{

        public PotArray ipots = new IPotArray(this);
        public PotArray vpots = new PotArray(this);

        Chip chipp;

         //private ArrayList<HasPreference> hasPreferencesList = new ArrayList<HasPreference>();
       


        
        public MDC2DBiasgen(Chip chip, DAC dac){
            super(chip);
            chipp=chip;
            potArray = new IPotArray(this);  // create the appropriate PotArray
//            ipots=(IPotArray)potArray; // temporary.   //RetoTODO: remove if t works otherwise
//            vpots=potArray;

            ipots=new IPotArray(this);
            vpots=new PotArray(this);
            potArray=vpots;


            // create the appropriate PotArray
            vpots.addPot(new VPot(MDC2D.this, "VRegRefBiasAmp", dac, 0, Pot.Type.NORMAL, Pot.Sex.P, 1,       16, "sets bias of feedback follower in srcbias"));
            vpots.addPot(new VPot(MDC2D.this,"VRegRefBiasMain",dac,      1,Pot.Type.NORMAL,Pot.Sex.P,1,      17,"sets bias of pfet which sets ref to srcbias"));
            vpots.addPot(new VPot(MDC2D.this,"VprBias",dac,              2,Pot.Type.NORMAL,Pot.Sex.P,1,      18,"bias current for pr"));
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
            ipots.addPot(new IPot(this, "VRegRefBiasAmp", 0, IPot.Type.NORMAL, Pot.Sex.P, 1,       1, "sets bias of feedback follower in srcbias"));
            ipots.addPot(new IPot(this,"VRegRefBiasMain",      1,Pot.Type.NORMAL,Pot.Sex.P,1,      2,"sets bias of pfet which sets ref to srcbias"));
            ipots.addPot(new IPot(this,"VprBias",              2,Pot.Type.NORMAL,Pot.Sex.P,1,      3,"bias current for pr"));
            ipots.addPot(new IPot(this,"Vlmcfb",               3,Pot.Type.NORMAL,Pot.Sex.N,1,      4,"bias current for diffosor"));
            ipots.addPot(new IPot(this,"Vprbuff",              4,Pot.Type.NORMAL,Pot.Sex.P,1,      5,"bias current for pr scr foll to lmc1"));
            ipots.addPot(new IPot(this,"Vprlmcbias",           5,Pot.Type.NORMAL,Pot.Sex.P,1,      6,"bias current for lmc1"));
            ipots.addPot(new IPot(this,"Vlmcbuff",             6,Pot.Type.NORMAL,Pot.Sex.P,1,      7,"bias current for lmc2"));
            ipots.addPot(new IPot(this,"Screfpix",            7,Pot.Type.NORMAL,Pot.Sex.N,1,       8,"sets scr bias for lmc2"));
            ipots.addPot(new IPot(this,"FollBias",            8,Pot.Type.NORMAL,Pot.Sex.N,1,       9,"sets bias for follower in pads"));
            ipots.addPot(new IPot(this,"Vpscrcfbias",          9,Pot.Type.NORMAL,Pot.Sex.P,1,      10,"sets bias for ptype src foll in scanner readout"));
            ipots.addPot(new IPot(this,"VADCbias",             0xa,Pot.Type.NORMAL,Pot.Sex.P,1,    11,"sets bias current for comperator in ADC"));
            ipots.addPot(new IPot(this,"Vrefminbias",          0xb,Pot.Type.NORMAL,Pot.Sex.N,1,    12,"sets bias for Srcrefmin follower from resis divider"));
            ipots.addPot(new IPot(this,"Srcrefmin",           0xc,Pot.Type.NORMAL,Pot.Sex.P,1,     13,"sets half Vdd for ADC"));
            ipots.addPot(new IPot(this,"refnegDAC",           0xd,Pot.Type.NORMAL,Pot.Sex.na,1,    14,"description"));
            ipots.addPot(new IPot(this,"refposDAC",           0xe,Pot.Type.NORMAL,Pot.Sex.na,1,    15,"description"));
        }

        @Override
        public PotArray getPotArray(){
            return this.potArray;
        }




        public Iterator getShiftRegisterIterator(){
            return ((IPotArray)potArray).getShiftRegisterIterator();
        }

        public int getNumPots(){
            return potArray.getNumPots();
        }

        public void setPotArray(PotArray set){
            potArray= set;
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
    }
 
}
