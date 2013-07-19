/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.chip.dvs320;

import java.awt.BorderLayout;

import javax.swing.JPanel;

import net.sf.jaer.Description;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.graphics.AEViewer;

/**
 * Temporary, extends Biasgen to add config bits on SeeBetter10/11
 * @author tobi
 */
@Description("Temporary class for testing SeeBetter10 and SeeBetter11, will be replaced by SeeBetter1011")
public class SeeBetter1011_Temporary extends cDVSTest30 {

    public SeeBetter1011_Temporary() {
        setBiasgen(new SB10TmpBiasgen(this));
    }

    @Override
    public void onDeregistration() {
        unregisterControlPanel();
    }

    @Override
    public void onRegistration() {
        registerControlPanel();
    }
    
    SeeBetter1011_TemporaryDisplayControlPanel controlPanel = null;

    public void registerControlPanel() {
        try {
            AEViewer viewer = getAeViewer(); // must do lazy install here because viewer hasn't been registered with this chip at this point
            JPanel imagePanel = viewer.getImagePanel();
            imagePanel.add((controlPanel = new SeeBetter1011_TemporaryDisplayControlPanel(this)), BorderLayout.SOUTH);
            imagePanel.revalidate();
        } catch (Exception e) {
            log.warning("could not register control panel: " + e);
        }
    }

    void unregisterControlPanel() {
        try {
            AEViewer viewer = getAeViewer(); // must do lazy install here because viewer hasn't been registered with this chip at this point
            JPanel imagePanel = viewer.getImagePanel();
            imagePanel.remove(controlPanel);
            imagePanel.revalidate();
        } catch (Exception e) {
            log.warning("could not unregister control panel: " + e);
        }
    }

    
    /**
     *    has 22 biases * 3 bytes + 4 ss sources * 2 bytes + 1 byte vdac + 4 * 1 nibble ana mux + 5 nibbles dmux + 3 bytes extra config
    
     */
    public class SB10TmpBiasgen extends cDVSTest30.cDVSTestBiasgen {

        public SB10TmpBiasgen(Chip chip) {
            super(chip);
            configBits = new ExtraOnChipConfigBits();
            allMuxes = new AllMuxesSB1011();
        }

        /** Bits on the on-chip shift register but not an output mux control, added to end of shift register. Control
         * holding different pixel arrays in reset and how the RC delays are configured.
        
         */
        class ExtraOnChipConfigBits extends cDVSTest30.cDVSTestBiasgen.ConfigBits { // TODO fix for config bit of pullup

            ConfigBit pullupX = new ConfigBit("useStaticPullupX", 0, "turn on static pullup for X addresses (columns)"),
                    pullupY = new ConfigBit("useStaticPullupY", 1, "turn on static pullup for Y addresses (rows)"),
                    delayY0 = new ConfigBit("delayY0", 2, "RC delay columns, 1x"),
                    delayY1 = new ConfigBit("delayY1", 3, "RC delay columns, 2x"),
                    delayY2 = new ConfigBit("delayY2", 4, "RC delay columns 4x"),
                    delayX0 = new ConfigBit("delayX0", 5, "RC delay rows, 1x"),
                    delayX1 = new ConfigBit("delayX1", 6, "RC delay rows, 2x"),
                    delayX2 = new ConfigBit("delayX2", 7, "RC delay rows, 4x"),
                    sDVSReset = new ConfigBit("sDVSReset", 8, "holds sensitive DVS (sDVS) array in reset"),
                    bDVSReset = new ConfigBit("bDVSReset", 9, "holds big DVS + log intensity (bDVS) array in reset"),
                    ros = new ConfigBit("ROS", 10, "reset on scan enabled"),
                    delaySM0 = new ConfigBit("delaySM0", 11, "adds delay to state machine, 1x"),
                    delaySM1 = new ConfigBit("delaySM1", 12, "adds delay to state machine, 2x"),
                    delaySM2 = new ConfigBit("delaySM2", 13, "adds delay to state machine, 4x");
            final int TOTAL_NUM_BITS = 24;  // number of these bits on this chip, at end of biasgen shift register
            boolean value = false;

            public ExtraOnChipConfigBits() {
                // bits in order from input end of final shift register, to be padded out with 0s for unused bits
                configBits = new ConfigBit[]{pullupX, pullupY, delayY0, delayY1, delayY2, delayX0, delayX1, delayX2, sDVSReset, bDVSReset, ros, delaySM0, delaySM1, delaySM2};
            }

            /** Returns the bit string to send to the firmware to load a bit sequence for the config bits in the shift register.
             * 
             * Bytes sent to FX2 are loaded big endian into shift register (msb first). 
             * Here returned string has named config bits at right end and unused bits at left end. Right most character is pullupX.
             * Think of the entire on-chip shift register laid out from right to left with input at right end and extra config bits at left end.
             * Bits are loaded in order of bit string here starting from left end (the unused registers)
             * 
             * @return string of 0 and 1 with first element of configBits at right hand end, and starting with padding bits to fill unused registers.
             */
            @Override
            String getBitString() {
                StringBuilder s = new StringBuilder();
                // iterate over list
                for (int i = 0; i < TOTAL_NUM_BITS - configBits.length; i++) {
                    s.append("1"); // loaded first into unused parts of final shift register
                }
                for (int i = configBits.length-1; i >=0; i--) {
                    s.append(configBits[i].value ? "1" : "0");
                }
                log.info(s.length() + " extra config bits with unused registers at left end =" + s);
                return s.toString();
            }
        }
        
        // the output muxes
        class AllMuxesSB1011 extends cDVSTest30.cDVSTestBiasgen.AllMuxes {
            
            
            
            @Override
            String getBitString() {
                int nBits = 0;
                StringBuilder s = new StringBuilder();
                for (OutputMux m : this) {
                    s.append(m.getBitString());
                    nBits += m.nSrBits;
                }

                return s.toString();
            }

            AllMuxesSB1011() {

                dmuxes[0].setName("DigMux4");
                dmuxes[1].setName("DigMux3");
                dmuxes[2].setName("DigMux2");
                dmuxes[3].setName("DigMux1");
                dmuxes[4].setName("DigMux0");

                for (int i = 0; i < 5; i++) {
                    dmuxes[i].put(0, "nRxcolE");
                    dmuxes[i].put(1, "nAxcolE");
                    dmuxes[i].put(2, "nRY0");
                    dmuxes[i].put(3, "AY0");
                    dmuxes[i].put(4, "nAX0");
                    dmuxes[i].put(5, "nRXon");
                    dmuxes[i].put(6, "arbtopR");
                    dmuxes[i].put(7, "arbtopA");
                    dmuxes[i].put(8, "FF1");
                    dmuxes[i].put(9, "Acol");
                    dmuxes[i].put(10, "Rcol");
                    dmuxes[i].put(11, "Rrow");
                    dmuxes[i].put(12, "RxcolG");
                    dmuxes[i].put(13, "nArow");

                }

                    dmuxes[0].put(14, "nResetRxcol");
                    dmuxes[0].put(15, "nArowBottom");
                    dmuxes[1].put(14, "AY1right");
                    dmuxes[1].put(15, "nRY1right");
                    dmuxes[2].put(14, "AY1right");
                    dmuxes[2].put(15, "nRY1right");
                    dmuxes[3].put(14, "FF2");
                    dmuxes[3].put(15, "RCarb");
                    dmuxes[4].put(14, "FF2");
                    dmuxes[4].put(15, "RCarb");

                vmuxes[0].setName("AnaMux3");
                vmuxes[1].setName("AnaMux2");
                vmuxes[2].setName("AnaMux1");
                vmuxes[3].setName("AnaMux0");
                
                vmuxes[0].put(0, "readout");
                vmuxes[0].put(1, "Vmem");
                vmuxes[0].put(2, "Vdiff_18ls");
                vmuxes[0].put(3, "pr33sf");
                vmuxes[0].put(4, "pd33cas");
                vmuxes[0].put(5, "Vdiff_sDVS");
                vmuxes[0].put(6, "log_bDVS");
                vmuxes[0].put(7, "Vdiff_bDVS");

                vmuxes[1].put(0, "DiffAmpOut");
                vmuxes[1].put(1, "Vdiff_old");
                vmuxes[1].put(2, "pr18ls");
                vmuxes[1].put(3, "pd33sf");
                vmuxes[1].put(4, "casnode_33sf");
                vmuxes[1].put(5, "fb_sDVS");
                vmuxes[1].put(6, "prbuf_bDVS");
                vmuxes[1].put(7, "PhC_buffered");

                vmuxes[2].put(0, "InPh");
                vmuxes[2].put(1, "pr_old");
                vmuxes[2].put(2, "pd18ls");
                vmuxes[2].put(3, "nReset_33sf");
                vmuxes[2].put(4, "Vdiff_33cas");
                vmuxes[2].put(5, "pd_sDVS");
                vmuxes[2].put(6, "pr_bDVS");
                vmuxes[2].put(7, "Acol");

                vmuxes[3].put(0, "refcurrent");
                vmuxes[3].put(1, "pd_old");
                vmuxes[3].put(2, "nReset_18ls");
                vmuxes[3].put(3, "Vdiff_33sf");
                vmuxes[3].put(4, "pr33cas");
                vmuxes[3].put(5, "pr_sDVS");
                vmuxes[3].put(6, "pd_bDVS");
                vmuxes[3].put(7, "IFneuronReset");
            }
        }
    }
}
