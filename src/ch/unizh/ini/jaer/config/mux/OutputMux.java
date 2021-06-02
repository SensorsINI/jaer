/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.config.mux;

import java.util.Observable;
import java.util.logging.Logger;

import net.sf.jaer.biasgen.Biasgen.HasPreference;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.util.RemoteControlCommand;
import net.sf.jaer.util.RemoteControlled;

/**
 *
 * @author Tobi / Christian
 */
public class OutputMux extends Observable implements HasPreference, RemoteControlled {

    public int nSrBits;
    public int nInputs;
    Logger log;
    public Chip chip;
    OutputMap map;
    private String name = "OutputMux";
    public int selectedChannel = -1; // defaults to no input selected in the case of voltage and current, and channel 0 in the case of logic
    String bitString = null;
    final String CMD_SELECTMUX = "selectMux";

    /**
        *  A set of output mux channels.
        * @param nsr number of shift register bits
        * @param nin number of input ports to mux
        * @param m the map where the info is stored
        */
    public OutputMux(Chip chip, int nsr, int nin, OutputMap m) {
        this.chip = chip;
        nSrBits = nsr;
        nInputs = nin;
        map = m;
        setName(name); // stores remote contol command, maybe redundantly (which overwrites previous)
    }

    @Override
    public String toString() {
        return "OutputMux name=" + name + " nSrBits=" + nSrBits + " nInputs=" + nInputs + " selectedChannel=" + selectedChannel + " channelName=" + getChannelName(selectedChannel) + " code=" + getCode(selectedChannel) + " getBitString=" + bitString;
    }

    public void select(int i) {
        if (this.selectedChannel != i) {
            setChanged();
        }
        this.selectedChannel = i;
        notifyObservers();
    }

    public void put(int k, String name) { // maps from channel to string name
        map.put(k, name);
    }

    OutputMap getMap() {
        return map;
    }

    int getCode(int i) { // returns shift register binary code for channel i
        return map.get(i);
    }

    public void setChip(Chip chip){
        this.chip = chip;
    }

    /** Returns the bit string to send to the firmware to load a bit sequence for this mux in the shift register;
        * bits are loaded big endian, msb first but returned string has msb at right-most position, i.e. end of string.
        * @return big endian string e.g. code=11, s='1011', code=7, s='0111' for nSrBits=4.
        */
    public String getBitString() {
        StringBuilder s = new StringBuilder();
        int code = selectedChannel != -1 ? getCode(selectedChannel) : 0; // code 0 if no channel selected
        int k = nSrBits - 1;
        while (k >= 0) {
            int x = code & (1 << k); // start with msb
            boolean b = (x == 0); // get bit
            s.append(b ? '0' : '1'); // append to string 0 or 1, string grows with msb on left
            k--;
        } // construct big endian string e.g. code=14, s='1011'
        bitString = s.toString();
        return bitString;
    }

    public String getChannelName(int i) { // returns this channel name
        return map.nameMap.get(i);
    }

    public String getName() { // returns name of entire mux
        return name;
    }

    public void setName(String name) { // TODO should remove previous remote control command and add new one for this mux
        this.name = name;
        if (chip != null && chip.getRemoteControl() != null) {
            chip.getRemoteControl().addCommandListener(this, String.format("%s_%s <n>",CMD_SELECTMUX, getName()), "Selects mux "+getName()+" output number n");
        }
        }

    private String key() {
        return getClass().getSimpleName() + "." + name + ".selectedChannel";
    }

    @Override
    public void loadPreference() {
        select(chip.getPrefs().getInt(key(), -1));
    }

    @Override
    public void storePreference() {
        chip.getPrefs().putInt(key(), selectedChannel);
    }

    /** Command is e.g. "selectMux_Currents 1".
        *
        * @param command the first token which dispatches the command here for this class of Mux.
        * @param input the command string.
        * @return some informative string for debugging bad commands.
        */
    @Override
    public String processRemoteControlCommand(RemoteControlCommand command, String input) {
        String[] t = input.split("\\s");
        if (t.length < 2) {
            return "? " + this + "\n";
        } else {
            String s = t[0], a = t[1];
            try {
                select(Integer.parseInt(a));
                chip.getLog().info(getName()+": selected channel "+a);
                return this + "\n";
            } catch (NumberFormatException e) {
                chip.getLog().warning("Bad number format: " + input + " caused " + e);
                return e.toString() + "\n";
            } catch (Exception ex) {
                chip.getLog().warning(ex.toString());
                return ex.toString();
            }
        }
    }
}