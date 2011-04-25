/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.jaer.eventprocessing;

import java.awt.geom.Point2D;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;

/**
 *
 * @author Tobi
 */
public class junkremoveme extends EventFilter2D{

    private Point2D.Float p=new Point2D.Float(0,0);

    public junkremoveme(AEChip chip) {
        super(chip);
    }



    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        return in;
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }

    /**
     * @return the p
     */
    public Point2D.Float getP() {
        return p;
    }

    /**
     * @param p the p to set
     */
    public void setP(Point2D.Float p) {
        this.p = p;
    }

}
