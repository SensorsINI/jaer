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

import ch.unizh.ini.jaer.projects.davis.frames.ApsFrameExtractor;
import ch.unizh.ini.jaer.projects.npp.DavisClassifierCNNProcessor;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import eu.seebetter.ini.chips.DavisChip;
import java.awt.Color;
import java.beans.PropertyChangeEvent;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.AEChipRenderer;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;

/**
 * Runs the trained CNN with tensorflow to track Greg Cohen's foosball ball.
 * Telluride 2018 in ESP18 group.
 *
 * @author Tobi Delbruck
 */
public class FoosballCNNBallTracker extends DavisClassifierCNNProcessor implements FrameAnnotater {

    private float annotateAlpha = getFloat("annotateAlpha", 0.5f);
    AEChipRenderer renderer = null;

    public FoosballCNNBallTracker(AEChip chip) {
        super(chip);
        setPropertyTooltip("annotation", "annotateAlpha", "Sets the transparency for the heatmap display. ");
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

        // output is heat map
        renderer.resetAnnotationFrame(0.0f);
        float[] output = apsDvsNet.getOutputLayer().getActivations();

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
        if (!yes) {
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
        if (renderer != null && renderer instanceof AEFrameChipRenderer) {
            AEFrameChipRenderer frameRenderer = (AEFrameChipRenderer) renderer;
            frameRenderer.setAnnotateAlpha(annotateAlpha);
        }
    }

}
