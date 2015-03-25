/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.davis;

import eu.seebetter.ini.chips.davis.imu.ImuControl;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.jaer.biasgen.AddressedIPotArray;
import net.sf.jaer.biasgen.Pot;
import net.sf.jaer.biasgen.coarsefine.ShiftedSourceBiasCF;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * Base configuration for Davis356 (ApsDvs346) on Tower wafer designs
 * @author tobi
 */
class DavisRGBW640Config extends DavisTowerBaseConfig {

   public DavisRGBW640Config(Chip chip) {
        super(chip);

        setPotArray(new AddressedIPotArray(this)); // garbage collect IPots added in super by making this new potArray

        vdacs = new TowerOnChip6BitVDAC[8];
        // TODO fix this code for actual vdacs
        //getPotArray().addPot(new TowerOnChip6BitVDAC(this, "", 0, 0, ""));
        getPotArray().addPot(new TowerOnChip6BitVDAC(this, "ApsCasBpc", 0, 0, "N-type cascode for protecting drain of DVS photoreceptor log feedback FET from APS transients"));
        getPotArray().addPot(new TowerOnChip6BitVDAC(this, "OVG1Lo", 1, 1, "Logic low level of the overflow gate in the DAVIS pixel if it's configured as adjustable"));
        getPotArray().addPot(new TowerOnChip6BitVDAC(this, "OVG2Lo", 2, 2, "Logic low level of the overflow gate in the APS pixel if it's configured as adjustable"));
        getPotArray().addPot(new TowerOnChip6BitVDAC(this, "TX2OVG2Hi", 3, 3, "Logic high level of the overflow gate and transfer gate in the APS pixel if it's configured as adjustable"));
        getPotArray().addPot(new TowerOnChip6BitVDAC(this, "Gnd07", 4, 4, "Elevated ground source at 0.7V for producing 4V reset signals"));
        getPotArray().addPot(new TowerOnChip6BitVDAC(this, "VTestADC", 5, 5, "A fixed voltage to test the on-chip ADC if it's configured to test mode"));
        getPotArray().addPot(new TowerOnChip6BitVDAC(this, "ADCRefHigh", 6, 6, "The upper limit of the input voltage to the on chip ADC"));
        getPotArray().addPot(new TowerOnChip6BitVDAC(this, "ADCRefLow", 7, 7, "The lower limit of the input voltage to the on chip ADC"));

        try {
            // added from gdoc https://docs.google.com/spreadsheet/ccc?key=0AuXeirzvZroNdHNLMWVldWVJdkdqNGNxOG5ZOFdXcHc#gid=6 
            // private AddressedIPotCF diffOn, diffOff, refr, pr, sf, diff;
            addAIPot("IFRefrBn,n,normal,Bias calibration refractory period"); // 8
            addAIPot("IFThrBn,n,normal,Bias calibration neuron threshold");//9
            addAIPot("LocalBufBn,n,normal,Local buffer strength");//10
            addAIPot("PadFollBn,n,normal,Follower-pad buffer strength");//11
            addAIPot("Blk1N,n,normal,Ununsed N type");//12
            addAIPot("PixInvBn,n,normal,DVS request inversion static inverter strength");//13
            addAIPot("DiffBn,n,normal,DVS differenciator gain");//14
            addAIPot("OnBn,n,normal,DVS on event threshold");//15
            addAIPot("OffBn,n,normal,DVS off event threshold");//16
            addAIPot("PrBp,p,normal,Photoreceptor bias current");//17
            addAIPot("PrSFBp,p,normal,Photoreceptor follower bias current"); //18
            addAIPot("RefrBp,p,normal,DVS refractory period"); //19
            addAIPot("ArrayBiasBufferBn,n,normal,Row/column bias buffer strength"); //20
            addAIPot("Blk1P,p,normal,Ununsed P type"); //21
            addAIPot("ArrayLogicBufferBn,n,normal,Row logic level buffer strength"); // 22
            addAIPot("FalltimeBn,n,normal,Fall time of the APS control signals");//23
            addAIPot("RisetimeBp,p,normal,Rise time of the APS control signals");//24
            addAIPot("ReadoutBufBp,p,normal,APS analog readout buffer strangth");//25
            addAIPot("ApsROSFBn,n,normal,APS readout source follower strength"); // 26
            addAIPot("ADCcompBp,p,normal,ADC comparator gain"); //27 
            addAIPot("DACBufBp,p,normal,ADC ramp buffer strength"); //28
            addAIPot("Blk2P,p,normal,Ununsed P type"); //29
            addAIPot("LcolTimeoutBn,n,normal,No column request timeout"); //30
            addAIPot("AEPdBn,n,normal,Request encoder static pulldown strength"); //31
            addAIPot("AEPuXBp,p,normal,AER column pullup strength"); //32
            addAIPot("AEPuYBp,p,normal,AER row pullup strength"); //33
           
            // shifted sources 
            ssn = new ShiftedSourceBiasCF(this);
            ssn.setSex(Pot.Sex.N);
            ssn.setName("SSN");
            ssn.setTooltipString("n-type shifted source that generates a regulated voltage near ground");
            ssn.addObserver(this);
            ssn.setAddress(35);

            ssp = new ShiftedSourceBiasCF(this);
            ssp.setSex(Pot.Sex.P);
            ssp.setName("SSP");
            ssp.setTooltipString("p-type shifted source that generates a regulated voltage near Vdd");
            ssp.addObserver(this);
            ssp.setAddress(36);

            ssBiases[1] = ssn;
            ssBiases[0] = ssp;

        } catch (Exception e) {
            throw new Error(e.toString());
        }       // TODO fix this code for actual vdacs


        // graphicOptions
        videoControl = new VideoControl();
        videoControl.addObserver(this);

        // on-chip configuration chain
        chipConfigChain = new DavisChipConfigChain(chip);
        chipConfigChain.addObserver(this);

        // control of log readout
        apsReadoutControl = new ApsReadoutControl();

        // imuControl
        imuControl = new ImuControl(this);

        setBatchEditOccurring(true);
        loadPreferences();
        setBatchEditOccurring(false);
        try {
            sendConfiguration(this);
        } catch (HardwareInterfaceException ex) {
            Logger.getLogger(DAVIS240BaseCamera.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
}
