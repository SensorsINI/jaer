/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.config;

import eu.seebetter.ini.chips.config.ConfigBit;
import eu.seebetter.ini.chips.config.ConfigInt;
import java.util.ArrayList;
import java.util.Arrays;
import net.sf.jaer.chip.Chip;

/**
 * Container for CPLD configuration values that can generate the appropriate bits to send to the CPLD shift register.
 * 
 * @author tobi
 */
public class CPLDConfig {

    int numBits;
    int minBit = Integer.MAX_VALUE;
    int maxBit = Integer.MIN_VALUE;
    ArrayList<CPLDConfigValue> cpldConfigValues = new ArrayList();
    boolean[] bits;
    byte[] bytes = null;

    /** Make a new container for CPLD configuration values.
     * @param chip containing chip object
     */
    public CPLDConfig() {
    }

    /** Computes the bits to be sent to the CPLD from all the CPLD config values: bits, tristateable bits, and ints.
     * Writes the bits boolean[] so that they are set according to the bit position, e.g. for a bit if startBit=0, then bits[0] is set.
     *
     */
    private void computeBitsFromConfigValues() {
        if (minBit > 0) {
            return; // notifyChange yet, we haven't filled in bit 0 yet
        }
        bits = new boolean[maxBit + 1];
        for (CPLDConfigValue v : cpldConfigValues) {
            if (v instanceof CPLDBit) {
                bits[v.startBit] = ((ConfigBit) v).isSet();
                if (v instanceof TriStateableCPLDBit) {
                    bits[v.startBit + 1] = ((TriStateableCPLDBit) v).isHiZ(); // assumes hiZ bit is next one up
                }
            } else if (v instanceof ConfigInt) {
                int i = ((ConfigInt) v).get();
                for (int k = v.startBit; k < v.endBit; k++) {
                    bits[k] = (i & 1) == 1;
                    i = i >>> 1;
                }
            }
        }
    }

    /**
     * 
     * @param val configuration value to add to configuration
     */
    public void add(CPLDConfigValue val) {
        if (val.endBit < val.startBit) {
            throw new RuntimeException("bad CPLDConfigValue with endBit<startBit: " + val);
        }
        if (val.endBit > maxBit) {
            maxBit = val.endBit;
        }
        if (val.startBit < minBit) {
            minBit = val.startBit;
        }
        cpldConfigValues.add(val);
        computeBitsFromConfigValues();
    }

    /** Returns byte[] to send to uC to load into CPLD shift register.
     * <p>
     * This array is returned in big endian order so that
     * the bytes sent will be sent in big endian order to the device, according to how they are handled in firmware
     * and loaded into the CPLD shift register. In other words, the msb of the first byte returned (getBytes()[0] is the last bit
     * in the bits[] array of booleans, bit 63 in the case of 64 bits of CPLD SR contents.
     *
     * @return array to send to controller for shifting into CPLD shift register
     */
    public byte[] getBytes() {
        computeBitsFromConfigValues();
        int nBytes = bits.length / 8;
        if (bits.length % 8 != 0) {
            nBytes++;
        }
        if (bytes == null || bytes.length != nBytes) {
            bytes = new byte[nBytes];
        }
        Arrays.fill(bytes, (byte) 0);
        int byteCounter = 0;
        int bitcount = 0;
        for (int i = bits.length - 1; i >= 0; i--) {
            // start with msb and go down
            bytes[byteCounter] = (byte) (255 & bytes[byteCounter] << 1); // left shift the bits in this byte that are already there
            //                    if (bits[i]) {
            //                        System.out.println("true bit at bit " + i);
            //                    }
            bytes[byteCounter] = (byte) (255 & (bytes[byteCounter] | (bits[i] ? 1 : 0))); // set or clear the current bit
            bitcount++;
            if ((bitcount) % 8 == 0) {
                byteCounter++; // go to next byte when we finish each 8 bits
            }
        }
        return bytes;
    }

    @Override
    public String toString() {
        return "CPLDConfig{" + "numBits=" + numBits + ", minBit=" + minBit + ", maxBit=" + maxBit + ", cpldConfigValues=" + cpldConfigValues + ", bits=" + bits + ", bytes=" + bytes + '}';
    }

    /** Clears list of values
     * 
     */
    public void clear() {
        cpldConfigValues.clear();
    }
}
