/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.davis;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTabbedPane;
import javax.swing.border.TitledBorder;

import net.sf.jaer.biasgen.AddressedIPot;
import net.sf.jaer.biasgen.AddressedIPotArray;
import net.sf.jaer.biasgen.Biasgen.HasPreference;
import net.sf.jaer.biasgen.IPot;
import net.sf.jaer.biasgen.Masterbias;
import net.sf.jaer.biasgen.Pot;
import net.sf.jaer.biasgen.PotTweakerUtilities;
import net.sf.jaer.biasgen.VDAC.VPot;
import net.sf.jaer.biasgen.coarsefine.AddressedIPotCF;
import net.sf.jaer.biasgen.coarsefine.ShiftedSourceBiasCF;
import net.sf.jaer.biasgen.coarsefine.ShiftedSourceControlsCF;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.CypressFX3;
import net.sf.jaer.util.HasPropertyTooltips;
import net.sf.jaer.util.ParameterControlPanel;
import net.sf.jaer.util.PropertyTooltipSupport;
import ch.unizh.ini.jaer.chip.retina.DVSTweaks;
import ch.unizh.ini.jaer.config.MuxControlPanel;
import ch.unizh.ini.jaer.config.OutputMap;
import ch.unizh.ini.jaer.config.boards.LatticeLogicConfig;
import ch.unizh.ini.jaer.config.cpld.CPLDByte;
import ch.unizh.ini.jaer.config.cpld.CPLDConfigValue;
import ch.unizh.ini.jaer.config.cpld.CPLDInt;
import ch.unizh.ini.jaer.config.fx2.PortBit;
import ch.unizh.ini.jaer.config.fx2.TriStateablePortBit;
import ch.unizh.ini.jaer.config.onchip.ChipConfigChain;
import ch.unizh.ini.jaer.config.onchip.OnchipConfigBit;
import ch.unizh.ini.jaer.config.onchip.OutputMux;

/**
 * Bias generator, On-chip diagnostic readout, video acquisition and rendering
 * controls for the Davis240 vision sensors.
 *
 * @author Christian/Tobi
 */
public class Davis240Config extends DavisConfig {

    /**
     * Creates a new instance of chip configuration
     *
     * @param chip the chip this configuration belongs to
     */
    public Davis240Config(Chip chip) {
        super(chip);
        this.chip = (AEChip) chip;
        setName("Davis240Config");
    }

}
