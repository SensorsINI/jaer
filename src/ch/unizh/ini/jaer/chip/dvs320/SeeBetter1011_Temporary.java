/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.chip.dvs320;

import eu.seebetter.ini.chips.config.HasPreference;
import eu.seebetter.ini.chips.seebetter1011.SeeBetter1011;
import java.awt.event.ActionEvent;
import java.math.BigInteger;
import java.util.Observable;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import net.sf.jaer.Description;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * Temporary, extends Biasgen to add config bits on SeeBetter10/11
 * @author tobi
 */
@Description("Temporary class for testing SeeBetter10 and SeeBetter11, will be replaced by SeeBetter1011")
public class SeeBetter1011_Temporary extends cDVSTest30 {

    cDVSTest30.cDVSTestBiasgen sb10biasgen;

    public SeeBetter1011_Temporary() {
        this.sb10biasgen = (cDVSTest30.cDVSTestBiasgen) super.biasgen;
        sb10biasgen.configBits.configBits = new cDVSTestBiasgen.ConfigBits.ConfigBit[13];

    }

    class SB10TmpBiasgen extends cDVSTest30.cDVSTestBiasgen {

        public SB10TmpBiasgen(Chip chip) {
            super(chip);
        }

        /** Bits on the on-chip shift register but not an output mux control, added to end of shift register. Control
         * holding different pixel arrays in reset and how the RC delays are configured.
        
         */
        class ExtraOnChipConfigBits extends cDVSTest30.cDVSTestBiasgen.ConfigBits { // TODO fix for config bit of pullup

            ConfigBit pullupX = new ConfigBit("useStaticPullupX", 0, "turn on static pullup for X addresses (columns)"),
                    pullupY = new ConfigBit("useStaticPullupY", 1, "turn on static pullup for Y addresses (rows)"),
                    delayY0 = new ConfigBit("delayY0", 2, "RC delay columns"),
                    delayY1 = new ConfigBit("delayY1", 3, "RC delay columns"),
                    delayY2 = new ConfigBit("delayY2", 4, "RC delay columns"),
                    delayX0 = new ConfigBit("delayX0", 5, "RC delay rows"),
                    delayX1 = new ConfigBit("delayX1", 6, "RC delay rows"),
                    delayX2 = new ConfigBit("delayX2", 7, "RC delay rows"),
                    sDVSReset = new ConfigBit("sDVSReset", 8, "holds sensitive DVS (sDVS) array in reset"),
                    bDVSReset = new ConfigBit("bDVSReset", 9, "holds big DVS + log intensity (bDVS) array in reset"),
                    ros = new ConfigBit("ROS", 10, "reset on scan enabled"),
                    delaySM0 = new ConfigBit("delaySM0", 11, "adds delay"),
                    delaySM1 = new ConfigBit("delaySM1", 12, "adds delay"),
                    delaySM2 = new ConfigBit("delaySM2", 13, "adds delay");
            final int TOTAL_NUM_BITS = 24;  // number of these bits on this chip, at end of biasgen shift register
            boolean value = false;

            public ExtraOnChipConfigBits() {
                configBits = new ConfigBit[]{pullupX, pullupY, delayY0, delayY1, delayY2, delayX0, delayX1, delayX2, sDVSReset, bDVSReset, ros, delaySM0, delaySM1, delaySM2};
            }

            /** Returns the bit string to send to the firmware to load a bit sequence for the config bits in the shift register;
             * bits are loaded big endian into shift register (msb first) but here returned string has msb at right-most position, i.e. end of string.
             * @return big endian string e.g. code=11, s='1011', code=7, s='0111' for nSrBits=4.
             */
            String getBitString() {
                StringBuilder s = new StringBuilder();
                // iterate over list
                int n = configBits.length;
                for (int i = 0; i < TOTAL_NUM_BITS - n; i++) {
                    s.append("0"); // loaded first
                }
                for (int i = n - 1; i <= 0; i++) {
                    s.append(configBits[i].value ? "1" : "0"); // backwards from end
                }
                return s.toString();
            }
        }
    }
}
