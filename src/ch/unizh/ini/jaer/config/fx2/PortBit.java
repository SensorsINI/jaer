/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.config.fx2;

import net.sf.jaer.chip.Chip;
import ch.unizh.ini.jaer.config.AbstractConfigBit;
import ch.unizh.ini.jaer.config.ConfigBit;

/**
 * A direct port bit output from CypressFX2 port.
 * @author tobi
 */
public class PortBit extends AbstractConfigBit implements ConfigBit {
    String portBitString;
    int port;
    private short portbit; // has port as char in MSB, bitmask in LSB
    int bitmask;

    public PortBit(Chip chip, String portBit, String name, String tip, boolean def) {
        super(chip, name, tip, def);
        if (portBit == null || portBit.length() != 2) {
            throw new Error("BitConfig portBit=" + portBit + " but must be 2 characters");
        }
        String s = portBit.toLowerCase();
        if (!(s.startsWith("a") || s.startsWith("c") || s.startsWith("d") || s.startsWith("e"))) {
            throw new Error("BitConfig portBit=" + portBit + " but must be 2 characters and start with A, C, D, or E");
        }
        portBitString = portBit;
        char ch = s.charAt(0);
        switch (ch) {
            case 'a':
                port = 0;
                break;
            case 'c':
                port = 1;
                break;
            case 'd':
                port = 2;
                break;
            case 'e':
                port = 3;
                break;
            default:
                throw new Error("BitConfig portBit=" + portBit + " but must be 2 characters and start with A, C, D, or E");
        }
        bitmask = 1 << Integer.valueOf(s.substring(1, 2));
        portbit = (short) (65535 & ((port << 8) + (255 & bitmask)));
    }

    @Override
    public String toString() {
        return String.format("PortBit name=%s port=%s value=%s", name, portBitString, value);
    }

    /**
     * has port as char in MSB, bitmask in LSB
     * 
     * @return the portbit
     */
    public short getPortbit() {
        return portbit;
    }

    /**
     * has port as char in MSB, bitmask in LSB
     * 
     * @param portbit the portbit to set
     */
    public void setPortbit(short portbit) {
        this.portbit = portbit;
    }
    
}
