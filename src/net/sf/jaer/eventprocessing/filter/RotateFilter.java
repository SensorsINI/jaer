 /*
 * RotateFilter.java
 *
 * Created on July 7, 2006, 6:59 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright July 7, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */
package net.sf.jaer.eventprocessing.filter;

import net.sf.jaer.chip.*;
import net.sf.jaer.event.*;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import java.util.Observable;
import java.util.Observer;

/**
 * Transforms the events in various ways,
e.g. rotates the events so that x becomes y and y becomes x.
This filter acts on events in-place in the packet so it should be rather fast
because it doesn't need to copy events, only modify them.
 * @author tobi
 */
public class RotateFilter extends EventFilter2D implements Observer {

    private boolean swapXY = getPrefs().getBoolean("RotateFilter.swapXY", false);
    private boolean rotate90deg = getPrefs().getBoolean("RotateFilter.rotate90deg", false);
    private boolean invertY = getPrefs().getBoolean("RotateFilter.invertY", false);
    private boolean invertX = getPrefs().getBoolean("RotateFilter.invertX", false);
    private int sx,  sy;

    /** Creates a new instance of RotateFilter */
    public RotateFilter(AEChip chip) {
        super(chip);
        setPropertyTooltip("swapXY", "swaps x and y coordinates");
        setPropertyTooltip("rotate90deg", "rotates by 90 CCW");
        setPropertyTooltip("invertY", "flips Y");
        setPropertyTooltip("invertX", "flips X");
        sx = chip.getSizeX();
        sy = chip.getSizeY();
        chip.addObserver(this);  // to update chip size parameters
    }

    public EventPacket<?> filterPacket(EventPacket<?> in) {
        short tmp;
        if (!isFilterEnabled()) {
            return in;
        }
        for (Object o : in) {
            BasicEvent e = (BasicEvent) o;
            if (swapXY) {
                tmp = e.x;
                e.x = e.y;
                e.y = tmp;
            }
            if (rotate90deg) {
                tmp = e.x;
                e.x = (short) (sy - e.y - 1);
                e.y = tmp;
            }
            if (invertY) {
                e.y = (short) (sy - e.y - 1);

            }
            if (invertX) {
                e.x = (short) (sx - e.x - 1);

            }
        }
        return in;
    }

    public Object getFilterState() {
        return null;
    }

    public void resetFilter() {
    }

    public void initFilter() {
    }

    public boolean isSwapXY() {
        return swapXY;
    }

    public void setSwapXY(boolean swapXY) {
        this.swapXY = swapXY;
        getPrefs().putBoolean("RotateFilter.swapXY", swapXY);
    }

    public boolean isRotate90deg() {
        return rotate90deg;
    }

    public void setRotate90deg(boolean rotate90deg) {
        this.rotate90deg = rotate90deg;
        getPrefs().putBoolean("RotateFilter.rotate90deg", rotate90deg);
    }

    public boolean isInvertY() {
        return invertY;
    }

    public void setInvertY(boolean invertY) {
        this.invertY = invertY;
        getPrefs().putBoolean("RotateFilter.invertY", invertY);
    }

    public boolean isInvertX() {
        return invertX;
    }

    public void setInvertX(boolean invertX) {
        this.invertX = invertX;
        getPrefs().putBoolean("RotateFilter.invertX", invertX);
    }

    public void update(Observable o, Object arg) {
        sx = chip.getSizeX();
        sy = chip.getSizeY();
    }
}
