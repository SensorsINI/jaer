/*
 * Copyright (C) 2018 Tobi Delbruck.
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
package org.ine.telluride.jaer.tell2018;

import ch.unizh.ini.jaer.projects.npp.AbstractDavisCNN;
import ch.unizh.ini.jaer.projects.npp.DavisClassifierCNNProcessor;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import java.beans.PropertyChangeEvent;
import java.io.File;
import javax.swing.JFrame;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.graphics.AEChipRenderer;
import net.sf.jaer.graphics.DavisRenderer;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.ImageDisplay;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;

/**
 * Runs the trained CNN with tensorflow to track Greg Cohen's foosball ball.
 * Telluride 2018 in ESP18 group.
 * 
 * See 
 * https://drive.google.com/drive/folders/1o-kMdFZIw26YB2Q81hA5bu4mUt608FYK
 * https://github.com/Neuromorphs18/foosball2018  
 * https://docs.google.com/document/d/1CWOP7UmQa_P_RjM9oXD9f639kCdbEsU9dVkN4ZefgkY/edit#
 * @author Tobi Delbruck, Damien Joubert, 2018
 */
@Description("Runs the trained CNN with tensorflow to track Greg Cohen's foosball ball")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class FoosballCNNBallTracker extends DavisClassifierCNNProcessor implements FrameAnnotater {

    private float annotateAlpha = getFloat("annotateAlpha", 0.5f);
    AEChipRenderer renderer = null;
    float xTarget = Float.NaN;
    float nbTarget = 0;
    float yTarget = Float.NaN;
    long heatMapCNN[][][][] = new long[1][90][120][1];
    private ImageDisplay imageDisplay;
    private JFrame activationsFrame = null;

    public FoosballCNNBallTracker(AEChip chip) {
        super(chip);
        setPropertyTooltip("annotation", "annotateAlpha", "Sets the transparency for the heatmap display. ");
    }

    @Override
    protected void loadNetwork(File f) throws Exception {
        super.loadNetwork(f);
        if (apsDvsNet != null) {
            apsDvsNet.getSupport().addPropertyChangeListener(AbstractDavisCNN.EVENT_MADE_DECISION, this);
        }
    }

    @Override
    public synchronized void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt);
        processDecision(evt);

        if (evt.getPropertyName() == AbstractDavisCNN.EVENT_MADE_DECISION) {
            processDecision(evt);
        } else if (evt.getPropertyName() == AEViewer.EVENT_FILEOPEN) {
            resetFilter();
        }

    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        AEChipRenderer renderer = (AEChipRenderer) chip.getRenderer();
        GL2 gl = drawable.getGL().getGL2();
        if (apsDvsNet != null && apsDvsNet.getNetname() != null) {
            MultilineAnnotationTextRenderer.resetToYPositionPixels(chip.getSizeY() * 1f);
            MultilineAnnotationTextRenderer.setScale(.3f);
            MultilineAnnotationTextRenderer.renderMultilineString(apsDvsNet.getNetname());
            if (measurePerformance && performanceString != null /*&& !performanceString.equals(lastPerformanceString)*/) {
                MultilineAnnotationTextRenderer.renderMultilineString(performanceString);
                lastPerformanceString = performanceString;
            }
        }
        
        nbTarget =0;
        xTarget= 0;
        yTarget = 0;
        gl.glColor3f(0, 0, 1);
        gl.glPointSize(8);
        gl.glBegin(GL.GL_POINTS);
        for (int i = 0; i < 90; i++) {
            for (int j = 0; j < 120; j++) {
                if (resHeatMap[0][i][j] == 1 ) {
                    nbTarget++;
                    xTarget += j;
                    yTarget += i;
                     gl.glVertex2f(j*2, i*2);
                }
            }
        }
        gl.glEnd();
        
        gl.glLineWidth(4);
        gl.glColor3f(1, 0, 0);
        gl.glBegin(GL.GL_LINE_LOOP);
            gl.glVertex2f(xTarget-5, yTarget-5);
            gl.glVertex2f(xTarget-5, yTarget+5);
            gl.glVertex2f(xTarget+5, yTarget+5);
            gl.glVertex2f(xTarget+5, yTarget-5);
        gl.glEnd();
        // output is heat map
        //renderer.resetAnnotationFrame(0.0f);
        //float[] output = apsDvsNet.getOutputLayer().getActivations();

    }

    @Override
    public void resetFilter() {
        super.resetFilter();
        if (renderer != null) { // might be null on startup, if initFilter is called from AEChip constructor
            renderer.setExternalRenderer(false);
        }
        setAnnotateAlpha(annotateAlpha);
    }

    /**
     * Resets the filter
     *
     * @param yes true to reset
     */
    @Override
    synchronized public void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if (!yes && renderer != null) {
            renderer.setExternalRenderer(false);
        }
    }

    private float getOutputValue(float[] output, int x, int y) {
        final int width = 120, height = 90;
        return output[x * height + y];
    }

    /**
     * @return the annotateAlpha
     */
    public float getAnnotateAlpha() {
        return annotateAlpha;
    }

    /**
     * @param annotateAlpha the annotateAlpha to set
     */
    public void setAnnotateAlpha(float annotateAlpha) {
        if (annotateAlpha > 1.0) {
            annotateAlpha = 1.0f;
        }
        if (annotateAlpha < 0.0) {
            annotateAlpha = 0.0f;
        }
        this.annotateAlpha = annotateAlpha;
        if (renderer != null && renderer instanceof DavisRenderer) {
            DavisRenderer frameRenderer = (DavisRenderer) renderer;
            frameRenderer.setAnnotateAlpha(annotateAlpha);
        }
    }

    private void processDecision(PropertyChangeEvent evt) {
         
        if (resHeatMap != null) {
            
            if(nbTarget > 0){
                xTarget /= nbTarget ;
                yTarget /= nbTarget ;
            }
            else{
                xTarget = -1;
                yTarget = -1;                
            }
            xTarget *= 2;
            yTarget *= 2;
            //System.out.println("xTarget = " + Float.toString(xTarget) + " , yTarget = " + Float.toString(yTarget));
            //log.info(String.format("x : %f, y : %f", xTarget, yTarget));

        }

    }

}
