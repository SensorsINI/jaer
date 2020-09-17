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
package net.sf.jaer.eventprocessing.filter;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.gl2.GLUT;
import java.io.IOException;
import java.util.HashMap;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.RemoteControlCommand;
import net.sf.jaer.util.RemoteControlled;

/**
 * Superclass for all noise filters
 *
 * @author tobi
 */
public abstract class AbstractNoiseFilter extends EventFilter2D implements FrameAnnotater, RemoteControlled {

    protected boolean showFilteringStatistics = getBoolean("showFilteringStatistics", true);
    protected int totalEventCount = 0;
    protected int filteredOutEventCount = 0;
    /** Map from noise filters to drawing positions of noise filtering statistics annotations */
    static protected HashMap<AbstractNoiseFilter,Integer> noiseStatDrawingMap=new HashMap<AbstractNoiseFilter,Integer>();
    protected int statisticsDrawingPosition=-10; // y coordinate we write ourselves to, start with -10 so we end up at 0 for first one (hack)

    public AbstractNoiseFilter(AEChip chip) {
        super(chip);
        setPropertyTooltip("showFilteringStatistics", "Annotates screen with percentage of filtered out events, if filter implements this count");
        
        if (chip.getRemoteControl() != null) {
            chip.getRemoteControl().addCommandListener(this, "setNoiseFilterParameters", "set correlation time or distance.");
        }
   }

    /**
     * @return the showFilteringStatistics
     */
    public boolean isShowFilteringStatistics() {
        return showFilteringStatistics;
    }

    /**
     * @param showFilteringStatistics the showFilteringStatistics to set
     */
    public void setShowFilteringStatistics(boolean showFilteringStatistics) {
        this.showFilteringStatistics = showFilteringStatistics;
        putBoolean("showFilteringStatistics", showFilteringStatistics);
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (!showFilteringStatistics) {
            return;
        }
        findUnusedDawingY();
        GL2 gl = drawable.getGL().getGL2();
        gl.glPushMatrix();
        final GLUT glut = new GLUT();
        gl.glColor3f(.2f, .2f, .8f); // must set color before raster position (raster position is like glVertex)
        gl.glRasterPos3f(0, statisticsDrawingPosition, 0);
        final float filteredOutPercent = 100 * (float) filteredOutEventCount / totalEventCount;
        String s = null;
        s = String.format("%s: filtered out %%%6.1f",
                getClass().getSimpleName(),
                filteredOutPercent);
        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, s);
        gl.glPopMatrix();
    }

    protected void findUnusedDawingY() {
        // find a y posiiton not used yet for us
        if(noiseStatDrawingMap.get(this)==null){
            for(int y:noiseStatDrawingMap.values()){
                if(y>statisticsDrawingPosition) statisticsDrawingPosition=y;
            }
            statisticsDrawingPosition=statisticsDrawingPosition+10;
            noiseStatDrawingMap.put(this,statisticsDrawingPosition);
        }
    }
     
    private String USAGE = "Need at least 2 arguments: noisefilter <command> <args>\nCommands are: specific to the filter\n";
 
    public String processRemoteControlCommand(RemoteControlCommand command, String input) {
        String[] tok = input.split("\\s", 3);
        if (tok.length < 2) {
            return USAGE;
        }

        log.info("Received Command:" + input);
        return USAGE;
    }
    
    public String setParameters(RemoteControlCommand command, String input) {
        String[] tok = input.split("\\s", 3);
        if (tok.length < 2) {
            return USAGE;
        }
        
        return USAGE;
    }


}
