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
    }
}
