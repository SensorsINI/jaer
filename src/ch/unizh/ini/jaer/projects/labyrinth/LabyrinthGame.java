/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.labyrinth;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.*;
import net.sf.jaer.eventprocessing.EventFilter2DMouseAdaptor;

/**
 * Top level labyrinth robot class.
 * @author tobi
 */
public class LabyrinthGame extends EventFilter2DMouseAdaptor {

    public static String getDescription() {
        return "Top level labyinth game class";
    }
    LabyrinthBallController controller;
    LabyrinthVirtualBall virtualBall=null;
//    LabyrinthMap map;
    FilterChain filterChain;

    public LabyrinthGame(AEChip chip) {
        super(chip);
        controller = new LabyrinthBallController(chip);
        virtualBall=new LabyrinthVirtualBall(chip,this);
        filterChain = new FilterChain(chip);

//        filterChain.add(map=new LabyrinthMap(chip));
        filterChain.add(virtualBall);
        filterChain.add(controller);
        setEnclosedFilterChain(filterChain);
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        return filterChain.filterPacket(in);
    }

    @Override
    public void resetFilter() {
        filterChain.reset();
    }

    @Override
    public void initFilter() {
        resetFilter();
    }

    public void doDisableServos() {
        controller.disableServos();
    }

    public void doCenterTilts() {
        controller.centerTilts();
    }

    public void doControlTilts() {
        controller.controlTilts();
    }

    
    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        virtualBall.setFilterEnabled(false); // don't enable by default
    }

}
