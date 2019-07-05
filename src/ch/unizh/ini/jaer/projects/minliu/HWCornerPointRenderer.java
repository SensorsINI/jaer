/*
 * Copyright (C) 2019 tobi.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package ch.unizh.ini.jaer.projects.minliu;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import java.util.ArrayList;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.DrawGL;

/**
 * To show corners points from FPGA EFAST corner point detector
 *
 * @author Tobi/Min
 */
public class HWCornerPointRenderer extends EventFilter2D implements FrameAnnotater {

    private ArrayList<BasicEvent> cornerEvents = new ArrayList(100);

    public HWCornerPointRenderer(AEChip chip) {
        super(chip);
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        cornerEvents.clear();
        for (BasicEvent e : in) {
            if ((e.getAddress() & 1) == 0) {
                // corner event
                cornerEvents.add(e);
                e.setFilteredOut(true);
            }
        }
        return in;
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {  
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        gl.glColor3f(1, 0, 0);
        for (BasicEvent e : cornerEvents) {
//            DrawGL.drawCircle(gl, e.x, e.y, 2 /*radius*/, 16);
        }
    }

}
