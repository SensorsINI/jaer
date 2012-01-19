/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.config;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * Container for CPLD configuration values that can generate the appropriate bits to send to the CPLD shift register.
 * 
 * @author tobi
 */
public class CPLDShiftRegister {

    static final Logger log = Logger.getLogger("CPLDConfig");
    int numBits;
    int minBit = Integer.MAX_VALUE;
    int maxBit = Integer.MIN_VALUE;
    ArrayList<CPLDConfigValue> cpldConfigValues = new ArrayList();
    byte[] bytes = null;

    /** Make a new container for CPLD configuration values.
     * @param chip containing chip object
     */
    public CPLDShiftRegister() {
    }

    /**
     * 
     * @param val configuration value to add to configuration
     */
    public void add(CPLDConfigValue val) {
        if (val.msb < val.lsb) {
            throw new RuntimeException("bad CPLDConfigValue with endBit<startBit: " + val);
        }
        if (val.msb > maxBit) {
            maxBit = val.msb;
        }
        if (val.lsb < minBit) {
            minBit = val.lsb;
        }
        cpldConfigValues.add(val);
    }

    /** Returns byte[] to send to uC to load into CPLD shift register.
     * <p>
    Computes the bits to be sent to the CPLD from all the CPLD config values: bits, tristateable bits, and ints.
     * Writes the bits boolean[] so that they are set according to the bit position, e.g. for a bit if lsb=0, then bits[0] is set.
     *
    The bytes should be sent from host so that the first byte
    holds the MSB, i.e., the bytes should be sent big-endian from the host.
    i.e., the msb of the first byte should be the biggest-numbered bit
    and the lsb of the last byte is bit 0 as specified in the CPLD HDL configuration.
     * <p>
    Each byte here is written out big-endian, from msb to lsb.
    Only integral bytes are written, so if the number of bytes is not a multiple of 8, 
    then the first byte written (the MSB) should be left padded so that the msb ends up at the correct
    position.
    <p>
     * This array is returned in big endian order so that
     * the bytes sent will be sent in big endian order to the device, according to how they are handled in firmware
     * and loaded into the CPLD shift register. In other words, the msb of the first byte returned (getBytes()[0] is the last bit
     * in the bits[] array of booleans, bit 63 in the case of 64 bits of CPLD SR contents.
     *
     * @return array to send to controller for shifting into CPLD shift register
     */
    public byte[] getBytes() {
        numBits=maxBit+1;
        boolean[] touched = new boolean[numBits];
        BigInteger bigint = new BigInteger("0");
        for (CPLDConfigValue v : cpldConfigValues) {
            if (v instanceof CPLDBit) {
                CPLDBit b = (CPLDBit) v;
                touched[v.lsb] = true;
                if (b.isSet()) {
                    bigint=bigint.setBit(v.lsb);
                } else {
                    bigint=bigint.clearBit(v.lsb);
                }
                if (v instanceof TriStateableCPLDBit) {
                    TriStateableCPLDBit t = (TriStateableCPLDBit) v;
                    touched[t.lsb + 1] = true; // assumes hiZ bit is next one up
                    if (t.isHiZ()) {
                        bigint=bigint.setBit(t.lsb + 1);
                    } else {
                        bigint=bigint.clearBit(t.lsb + 1); // TODO check tristate bit
                    }
                }
            } else if (v instanceof ConfigInt) {
                int i = ((ConfigInt) v).get();
                for (int k = v.lsb; k <= v.msb; k++) {
                    touched[k] = true;
                    if ((i & 1) != 0) {
                        bigint=bigint.setBit(k);
                    } else {
                        bigint=bigint.clearBit(k);
                    }
                    i = i >>> 1; // right shift i to get the new lsb
                }
            }
        }

        // check that all bits have been actually allocated to 
        boolean ok = true;
        StringBuilder sb = new StringBuilder("Error: untouched CPLD configuation bits at positions ");
        for (int i = 0; i < touched.length; i++) {
            if (!touched[i]) {
                ok = false;
                sb.append(i).append(" ");
            }
        }
        if (!ok) {
            throw new Error(sb.toString());
        }
        sb = new StringBuilder((numBits) + " CPLD config bits = ");
        for (int i = maxBit; i >= 0; i--) {
            sb.append(bigint.testBit(i) ? "1" : "0");
        }
        log.info(sb.toString());

        byte[] bytes = bigint.toByteArray();
        this.bytes = bytes;
        return bytes;
    }

    @Override
    public String toString() {
        return "CPLDConfig{" + "numBits=" + numBits + ", minBit=" + minBit + ", maxBit=" + maxBit + ", cpldConfigValues=" + cpldConfigValues + '}';
    }
    
//    public String getByteString(byte[] bytes){
//        String out = "";
//        for(int i = 0; i<bytes.length;i++){
//            out = out+(String.format("%16s", Integer.toBinaryString((int)bytes[i])).replace(' ', '0'));
//        }
//        return out;
//    }

    /** Clears list of values
     * 
     */
    public void clear() {
        cpldConfigValues.clear();
    }
}
