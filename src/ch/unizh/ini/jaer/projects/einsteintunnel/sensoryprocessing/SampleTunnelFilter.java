/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.einsteintunnel.sensoryprocessing;
import net.sf.jaer.chip.*;
import net.sf.jaer.event.*;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import javax.media.opengl.*;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.glu.*;
import javax.swing.*;
import java.awt.Graphics2D;
import java.awt.Dimension;
import java.util.*;
import java.util.Observable;
import java.util.Observer;

/**
 *
 * @author braendch
 */
public class SampleTunnelFilter extends EventFilter2D {


    public SampleTunnelFilter(AEChip chip) {
        super(chip);

        initFilter();
    }

    public void initFilter() {
        resetFilter();
    }

    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {

        return in;
    }

    synchronized public void resetFilter() {

    }
}
