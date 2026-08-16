/*
 * RotateFilter.java
 *
 * Created on July 7, 2006, 6:59 AM
 *
 *Copyright July 7, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */
package net.sf.jaer.eventprocessing.filter;

import eu.seebetter.ini.chips.davis.DavisBaseCamera;
import java.awt.Point;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.Help;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.FramePacket;
import net.sf.jaer.event.PacketType;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 * Transforms the events in various ways, e.g. rotates the events so that x
 * becomes y and y becomes x. This filter acts on events in-place in the packet
 * so it should be rather fast because it doesn't need to copy events, only
 * modify them.
 * <p>
 * jAER 3.0: also remaps {@link FramePacket} pixels with the same geometry.
 * APS first/last readout corners are still swapped when invertX&amp;invertY so
 * legacy extractPacket SOF/EOF stay consistent.
 *
 * @author tobi
 */
@Description("Rotates or otherwise transforms the event addresses")
@Help("""
<html>
<body>
<h2>RotateFilter</h2>
<p>Remaps event addresses in place (no copy) so the scene matches how you hold the camera
or how later filters expect <code>x</code>/<code>y</code>. On DAVIS chips it also remaps
APS frame pixels with the same geometry.</p>
<hr>
<h3>How to use</h3>
<ol>
<li>Check <b>Enabled</b>.</li>
<li>Turn on the transform you need (they can be combined; order is swap, 90&deg;, invert, then arbitrary angle):</li>
</ol>
<ul>
<li><code>swapXY</code> &mdash; exchange column and row (portrait/landscape).</li>
<li><code>rotate90deg</code> &mdash; 90&deg; counterclockwise.</li>
<li><code>invertX</code> / <code>invertY</code> &mdash; flip about that axis.
Set <b>both</b> for 180&deg;.</li>
<li><code>angleDeg</code> &mdash; additional CCW rotation about the array center (events that
fall outside the chip after rotation are filtered out).</li>
</ul>
<p>Leave all options off for a no-op. Special events are left unchanged.</p>
</body>
</html>
""")
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

    @Override
    public boolean accepts(PacketType type) {
        return type == PacketType.POLARITY || type == PacketType.FRAME;
    }

    @Override
    public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        checkDavisApsHack();
        final int sx = chip.getSizeX();
        final int sy = chip.getSizeY();
        for (BasicEvent e : in) {
            if (e == null || e.isSpecial()) {
                continue;
            }
            if (!transformAddress(e, sx, sy)) {
                e.setFilteredOut(true);
            }
        }
        return in;
    }

    @Override
    public FramePacket processFrame(FramePacket in) {
        checkDavisApsHack();
        if (in == null || in.isEmpty() || !anyTransformEnabled()) {
            return in;
        }
        final int w = in.getWidth();
        final int h = in.getHeight();
        final short[] src = in.getPixels();
        final short[] dst = new short[src.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int nx = x, ny = y;
                short tmp;
                if (swapXY) {
                    tmp = (short) nx;
                    nx = ny;
                    ny = tmp;
                }
                if (rotate90deg) {
                    tmp = (short) nx;
                    nx = h - ny - 1;
                    ny = tmp;
                }
                if (invertY) {
                    ny = h - ny - 1;
                }
                if (invertX) {
                    nx = w - nx - 1;
                }
                if (angleDeg != 0) {
                    final int sx2 = w / 2, sy2 = h / 2;
                    int x2 = nx - sx2, y2 = ny - sy2;
                    nx = (int) Math.round(+cosAng * x2 - sinAng * y2) + sx2;
                    ny = (int) Math.round(+sinAng * x2 + cosAng * y2) + sy2;
                }
                if (nx >= 0 && nx < w && ny >= 0 && ny < h) {
                    dst[ny * w + nx] = src[y * w + x];
                }
            }
        }
        System.arraycopy(dst, 0, src, 0, src.length);
        return in;
    }

    /** @return false if result is out of chip bounds */
    private boolean transformAddress(BasicEvent e, final int sx, final int sy) {
        short tmp;
        final int sx2 = sx / 2, sy2 = sy / 2;
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
            int x3 = (int) Math.round(+cosAng * x2 - sinAng * y2);
            int y3 = (int) Math.round(+sinAng * x2 + cosAng * y2);
            e.x = (short) (x3 + sx2);
            e.y = (short) (y3 + sy2);
        }
        return e.x >= 0 && e.x < sx && e.y >= 0 && e.y < sy;
    }

    private boolean anyTransformEnabled() {
        return swapXY || rotate90deg || invertX || invertY || angleDeg != 0;
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
        this.angleDeg = angleDeg;
        putFloat("angleDeg", angleDeg);
        cosAng = (float) Math.cos(angleDeg * Math.PI / 180);
        sinAng = (float) Math.sin(angleDeg * Math.PI / 180);
    }

    private void checkDavisApsHack() {
        if (!davisCamera || !isFilterEnabled()) {
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
