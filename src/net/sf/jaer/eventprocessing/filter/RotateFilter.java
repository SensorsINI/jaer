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

import eu.seebetter.ini.chips.davis.DavisBaseCamera;
import java.awt.Point;
import java.util.Iterator;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 * Transforms the events in various ways, e.g. rotates the events so that x
 * becomes y and y becomes x. This filter acts on events in-place in the packet
 * so it should be rather fast because it doesn't need to copy events, only
 * modify them.
 *
 * @author tobi
 */
@Description("Rotates or otherwise transforms the event addresses")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class RotateFilter extends EventFilter2D {

    private boolean swapXY = getBoolean("swapXY", false);
    private boolean rotate90deg = getBoolean("rotate90deg", false);
    private boolean invertY = getBoolean("invertY", false);
    private boolean invertX = getBoolean("invertX", false);
    private float angleDeg = getFloat("angleDeg", 0f);
    private float cosAng = (float) Math.cos(angleDeg * Math.PI / 180);
    private float sinAng = (float) Math.sin(angleDeg * Math.PI / 180);
    private boolean davisCamera = false;
    Point origFirstPixel = null, origLastPixel = null;

    /**
     * Creates a new instance of RotateFilter
     */
    public RotateFilter(AEChip chip) {
        super(chip);
        setPropertyTooltip("swapXY", "swaps x and y coordinates");
        setPropertyTooltip("rotate90deg", "rotates by 90 CCW");
        setPropertyTooltip("invertY", "flips Y; to rotate 180 deg set both invertX and invertY");
        setPropertyTooltip("invertX", "flips X; to rotate 180 deg set both invertX and invertY");
        setPropertyTooltip("angleDeg", "CCW rotation angle in degrees");
        if (chip instanceof DavisBaseCamera) {
            davisCamera = true;
        }
    }

    public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        short tmp;
        final int sx = chip.getSizeX();
        final int sy = chip.getSizeY();
        final int sx2 = sx / 2, sy2 = sy / 2;
        Iterator itr;
        boolean davisCamera;
        checkDavisApsHack();
        if (in instanceof ApsDvsEventPacket) {
            itr = ((ApsDvsEventPacket) in).fullIterator();
            davisCamera = true;
        } else {
            itr = in.iterator();
            davisCamera = false;
        }
        while (itr.hasNext()) {
            Object o = itr.next();
            BasicEvent e = (BasicEvent) o;
            if (e.isSpecial() || (davisCamera && (e.x == -1 && e.y == -1))) {
                continue;  // TODO hack to avoid transforming "flag events"; see DavisBaseCamera line 617 createApsFlagEvent()
            }
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

            if (angleDeg != 0) {
                int x2 = e.x - sx2, y2 = e.y - sy2;
                int x3 = (int) Math.round(+cosAng * (x2) - sinAng * (y2));
                int y3 = (int) Math.round(+sinAng * (x2) + cosAng * (y2));
                e.x = (short) (x3 + sx2);
                e.y = (short) (y3 + sy2);
            }
            if (e.x < 0 || e.x >= sx || e.y < 0 || e.y >= sy) {
                e.setFilteredOut(true);
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
        putBoolean("swapXY", swapXY);
    }

    public boolean isRotate90deg() {
        return rotate90deg;
    }

    public void setRotate90deg(boolean rotate90deg) {
        this.rotate90deg = rotate90deg;
        putBoolean("rotate90deg", rotate90deg);
    }

    public boolean isInvertY() {
        return invertY;
    }

    public void setInvertY(boolean invertY) {
        this.invertY = invertY;
        putBoolean("invertY", invertY);
    }

    public boolean isInvertX() {
        return invertX;
    }

    public void setInvertX(boolean invertX) {
        this.invertX = invertX;
        putBoolean("invertX", invertX);
    }

    /**
     * @return the angleDeg
     */
    public float getAngleDeg() {
        return angleDeg;
    }

    /**
     * @param angleDeg the angleDeg to set
     */
    public void setAngleDeg(float angleDeg) {
        // round to nearest 5 deg
//        if(angleDeg==0) this.angleDeg=0; else if(angleDeg>this.angleDeg) this.angleDeg+=1; else if(angleDeg<this.angleDeg)this.angleDeg-=1;
//        this.angleDeg = (int)Math.round(this.angleDeg);
        this.angleDeg = angleDeg;
        putFloat("angleDeg", angleDeg);
        cosAng = (float) Math.cos(angleDeg * Math.PI / 180);
        sinAng = (float) Math.sin(angleDeg * Math.PI / 180);
    }

    private void checkDavisApsHack() {
        if (!davisCamera || !isFilterEnabled()) { // try to prevent swapping corners when this filter is not actually filtering! (tobi)
            return;
        }
        DavisBaseCamera d = (DavisBaseCamera) chip;
        if (origFirstPixel == null) {
            origFirstPixel = d.getApsFirstPixelReadOut();
        }
        if (origLastPixel == null) {
            origLastPixel = d.getApsLastPixelReadOut();
        }
        if (!(invertX && invertY)) {
            d.setApsFirstPixelReadOut(origFirstPixel);
            d.setApsLastPixelReadOut(origLastPixel);
        } else {
            d.setApsFirstPixelReadOut(origLastPixel);
            d.setApsLastPixelReadOut(origFirstPixel);
        }
    }
}
