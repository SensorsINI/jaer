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
//    LabyrinthMap map;
    FilterChain filterChain;

    public LabyrinthGame(AEChip chip) {
        super(chip);
        filterChain = new FilterChain(chip);

//        filterChain.add(map=new LabyrinthMap(chip));
        filterChain.add(controller = new LabyrinthBallController(chip));
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




}
