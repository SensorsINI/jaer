/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.config.onchip;

import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;

import javax.swing.JPanel;

import net.sf.jaer.biasgen.Biasgen.HasPreference;
import net.sf.jaer.chip.Chip;
import ch.unizh.ini.jaer.config.MuxControlPanel;

/**
 * Describes a chip or FPGA/CPLD level configuration shift register.
 * 
 * @author Christian
 */
public abstract class ChipConfigChain extends Observable implements HasPreference, Observer {

    public Chip sbChip;

    //Config Bits
    public OnchipConfigBit[] configBits;
    public int TOTAL_CONFIG_BITS = 0;

    ArrayList<OutputMux> muxes = new ArrayList();
    MuxControlPanel controlPanel = null;

    public ChipConfigChain(Chip chip){  
        this.sbChip = chip;
    }

    public abstract String getBitString();

    public abstract MuxControlPanel buildMuxControlPanel();

    public abstract JPanel getChipConfigPanel();

    @Override
    public void loadPreference() {
        for (OnchipConfigBit b : configBits) {
            b.loadPreference();
        }
        for (OutputMux m : muxes) {
            m.loadPreference();
        }
    }

    @Override
    public void storePreference() {
        for (OnchipConfigBit b : configBits) {
            b.storePreference();
        }
        for (OutputMux m : muxes) {
            m.storePreference();
        }
    }

    @Override
    public void update(Observable o, Object arg) {
        setChanged();
        notifyObservers(arg);
    }
}
