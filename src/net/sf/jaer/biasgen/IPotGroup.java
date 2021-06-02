/*
 * PotGroup.java
 *
 * Created on October 28, 2006, 10:15 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright October 28, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package net.sf.jaer.biasgen;

import java.util.ArrayList;

import net.sf.jaer.chip.Chip;

/**
 * A class to hold a group of pots; can be used to indicate related functionality.
 
 * @author tobi
 */
public class IPotGroup extends ArrayList<IPot>{
    
    Chip chip;
    String name;
    
    /** Creates a new instance of IPotGroup
     @param chip the chip this group is for
     @param name the name of the group, displayed in GUI control 
     */
    public IPotGroup(Chip chip, String name) {
        this.chip=chip;
        this.name=name;
    }
    
}
