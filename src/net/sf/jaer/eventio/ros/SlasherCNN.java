/*
 * Copyright (C) 2018 Tobi.
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
package net.sf.jaer.eventio.ros;

import ch.unizh.ini.jaer.projects.npp.AbstractDavisCNN;
import ch.unizh.ini.jaer.projects.npp.DavisClassifierCNNProcessor;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUquadric;
import com.jogamp.opengl.util.awt.TextRenderer;
import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;

/**
 * Runs slasher CNN and displays its output as in SlasherRosbagDisplay
 *
 * @author Tobi
 */
@Description("Shows Slasher robot car PWM signals for steering throttle and gearshift")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class SlasherCNN extends DavisClassifierCNNProcessor {

    /**
     * steering, positive is to right, throttle, positive is faster forwards,
     * gearshift?
     */
    private float steering, throttle, gear_shift;
    private boolean showSteering = getBoolean("showSteering", true);
    private boolean showThrottleBrake = getBoolean("showThrottleBrake", true);
    private boolean addedPropertyChangeListener = false;

    public SlasherCNN(AEChip chip) {
        super(chip);
    }

    @Override
    public synchronized EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!addedPropertyChangeListener && apsDvsNet != null) {
            addedPropertyChangeListener = true;
            apsDvsNet.getSupport().addPropertyChangeListener(AbstractDavisCNN.EVENT_MADE_DECISION, this);
        }
        return super.filterPacket(in); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public synchronized void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt); // runs the CNN
        switch (evt.getPropertyName()) {
            case AbstractDavisCNN.EVENT_MADE_DECISION:
                steering = apsDvsNet.getOutputLayer().getActivations()[0];
                break;
        }
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        TextRenderer textRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 24), true, true);
        textRenderer.setColor(Color.red);

        GL2 gl = drawable.getGL().getGL2();
        gl.glColor3f(0, 1, 0);
        gl.glLineWidth(2);
        GLU glu = new GLU();
        GLUquadric quad = glu.gluNewQuadric();
        if (showThrottleBrake) {
            final float x = chip.getSizeX() * .2f, y = (chip.getSizeY()) * .2f, scale = .4f;
            textRenderer.begin3DRendering();
            String s = String.format("Prediction throttle: %3.0f%%", throttle * 100);

            Rectangle2D r = textRenderer.getBounds(s);
            textRenderer.draw3D(s, (float) (x - scale * r.getWidth() / 2), (float) (y - scale * r.getHeight() / 2), 0, scale);
            textRenderer.end3DRendering();

        }
        if (showSteering) {
            float x = chip.getSizeX() * .2f, y = (chip.getSizeY()) * .25f, scale = .4f;
            textRenderer.begin3DRendering();
            String s = String.format("Prediction steering: %5.2f", steering);

            Rectangle2D r = textRenderer.getBounds(s);
            textRenderer.draw3D(s, (float) (x - scale * r.getWidth() / 2), (float) (y - scale * r.getHeight() / 2), 0, scale);
            textRenderer.end3DRendering();

            final float radius = chip.getMinSize() * .25f;
            // draw steering wheel
            x = chip.getSizeX() / 2;
            y = (chip.getSizeY()) / 2;
            gl.glPushMatrix();
            {
                gl.glTranslatef(x, y, 0);
                glu.gluQuadricDrawStyle(quad, GLU.GLU_FILL);
                glu.gluDisk(quad, radius, radius + 1, 32, 1);
            }
            gl.glPopMatrix();

            // draw steering vector, including external radio input value
            gl.glPushMatrix();
            {
                gl.glTranslatef(x, y, 0);
                gl.glBegin(GL2.GL_LINES);
                {
                    gl.glVertex2f(0, 0);
                    double a = -Math.PI * steering; // rad
                    float dx = radius * (float) Math.sin(a);
                    float dy = radius * (float) Math.cos(a);
                    gl.glVertex2f(dx, dy);
                }
                gl.glEnd();
            }
            gl.glPopMatrix();

        }
    }

    /**
     * @return the showSteering
     */
    public boolean isShowSteering() {
        return showSteering;
    }

    /**
     * @param showSteering the showSteering to set
     */
    public void setShowSteering(boolean showSteering) {
        this.showSteering = showSteering;
        putBoolean("showSteering", showSteering);
    }

    /**
     * @return the showThrottleBrake
     */
    public boolean isShowThrottleBrake() {
        return showThrottleBrake;
    }

    /**
     * @param showThrottleBrake the showThrottleBrake to set
     */
    public void setShowThrottleBrake(boolean showThrottleBrake) {
        this.showThrottleBrake = showThrottleBrake;
        putBoolean("showThrottleBrake", showThrottleBrake);
    }
}
