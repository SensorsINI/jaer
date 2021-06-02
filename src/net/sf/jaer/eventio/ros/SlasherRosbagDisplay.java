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
package net.sf.jaer.eventio.ros;

import com.github.swrirobotics.bags.reader.exceptions.UninitializedFieldException;
import com.github.swrirobotics.bags.reader.messages.serialization.Float32Type;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUquadric;
import com.jogamp.opengl.util.awt.TextRenderer;
import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;

/**
 * Displays driver info for the Slasher car
 *
 * @author Tobi Delbruck
 */
@Description("Shows Slasher robot car PWM signals for steering throttle and gearshift")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class SlasherRosbagDisplay extends RosbagMessageDisplayer implements FrameAnnotater {

    /** steering, positive is to right, throttle, positive is faster forwards, gearshift? */
    private float steering, throttle, gear_shift;
    private boolean showSteering = getBoolean("showSteering", true);
    private boolean showThrottleBrake = getBoolean("showThrottleBrake", true);
    private boolean showSpeedo = getBoolean("showSpeedo", true);
    private boolean showGPS = getBoolean("showGPS", true);
    private boolean showTime = getBoolean("showTime", true);
    private boolean showText = getBoolean("showText", true);

    public SlasherRosbagDisplay(AEChip chip) {
        super(chip);
        ArrayList<String> topics = new ArrayList();
        topics.add("/raw_pwm");
        addTopics(topics);
    }

    /**
     * Messages are from
     * https://github.com/NeuromorphicProcessorProject/monstruck/blob/master/src/rally_msgs/msg/Pwm.msg
     *
     * @param message the message
     */
    @Override
    protected void parseMessage(RosbagFileInputStream.MessageWithIndex message) {
        String pkg = message.messageType.getPackage();
        String type = message.messageType.getType();
        try {
            float steeringPwm = message.messageType.<Float32Type>getField("steering").getValue();
            float throttlePwm = message.messageType.<Float32Type>getField("throttle").getValue();
            float gear_shiftPwm = message.messageType.<Float32Type>getField("gear_shift").getValue();
            log.info(String.format("PWM: steering: %8.2f\t throttle %8.2f\t gear: %8.2f", steeringPwm, throttlePwm, gear_shiftPwm));
            final float pwmCenter = 1500, pwmRange = 500; // us

            steering = -(steeringPwm - pwmCenter) / pwmRange; // produces an output that is zero for 1500 and ranges from -1 to +1
            throttle = (throttlePwm - pwmCenter) / pwmRange;
            gear_shift = (gear_shiftPwm - pwmCenter) / pwmRange;
        } catch (UninitializedFieldException ex) {
            Logger.getLogger(SlasherRosbagDisplay.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        TextRenderer textRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 24), true, true);
        textRenderer.setColor(Color.blue);

        gl.glColor3f(0, 0, 1);
        gl.glLineWidth(2);
        GLU glu = new GLU();
        GLUquadric quad = glu.gluNewQuadric();
//        if (showGPS) {
//                final float x = chip.getSizeX() * .7f, y = (chip.getSizeY()) * .1f, scale = .25f;
//                textRenderer.begin3DRendering();
//                String s = String.format("GPS: %10.6f, %10.6f", lastFordViState.latitude, lastFordViState.longitude);
//                Rectangle2D r = textRenderer.getBounds(s);
//                textRenderer.draw3D(s, (float) (x - scale * r.getWidth() / 2), (float) (y - scale * r.getHeight() / 2), 0, scale);
//                textRenderer.end3DRendering();
//        }
//        if (showTime) {
//            final float x = chip.getSizeX() * .7f, y = (chip.getSizeY()) * .05f, scale = .25f;
//            textRenderer.begin3DRendering();
//            String s = String.format("%s, dt=%4dms", new Date((long) lastFordViState.timestamp * 1000).toString(), (int) (lastFordViState.timestampDelta * 1000));
//            Rectangle2D r = textRenderer.getBounds(s);
//            textRenderer.draw3D(s, (float) (x - scale * r.getWidth() / 2), (float) (y - scale * r.getHeight() / 2), 0, scale);
//            textRenderer.end3DRendering();
//        }

//        if (showSpeedo) {
//            final float x = chip.getSizeX() * .8f, y = (chip.getSizeY()) * .8f, scale = .5f;
//            textRenderer.begin3DRendering();
//            String s = String.format("thr%.0f", thro);
//            Rectangle2D r = textRenderer.getBounds(s);
//            textRenderer.draw3D(s, (float) (x - scale * r.getWidth() / 2), (float) (y - scale * r.getHeight() / 2), 0, scale);
//            textRenderer.end3DRendering();
//        }
        if (showThrottleBrake) {
            final float x = chip.getSizeX() * .2f, y = (chip.getSizeY()) * .1f, scale = .4f;
            textRenderer.begin3DRendering();
            String s = String.format("Human throttle: %3.0f%%", throttle * 100);

            Rectangle2D r = textRenderer.getBounds(s);
            textRenderer.draw3D(s, (float) (x - scale * r.getWidth() / 2), (float) (y - scale * r.getHeight() / 2), 0, scale);
            textRenderer.end3DRendering();

        }
        if (showSteering) { 
            float x = chip.getSizeX() * .2f, y = (chip.getSizeY()) * .15f, scale = .4f;
            textRenderer.begin3DRendering();
            String s = String.format("Human steering: %5.2f", steering);

            Rectangle2D r = textRenderer.getBounds(s);
            textRenderer.draw3D(s, (float) (x - scale * r.getWidth() / 2), (float) (y - scale * r.getHeight() / 2), 0, scale);
            textRenderer.end3DRendering();
            
            final float radius = chip.getMinSize() * .2f;
            // draw steering wheel
            x = chip.getSizeX() / 2; y = (chip.getSizeY()) / 2;
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

    /**
     * @return the showGPS
     */
    public boolean isShowGPS() {
        return showGPS;
    }

    /**
     * @param showGPS the showGPS to set
     */
    public void setShowGPS(boolean showGPS) {
        this.showGPS = showGPS;
        putBoolean("showGPS", showGPS);
    }

    /**
     * @return the showTime
     */
    public boolean isShowTime() {
        return showTime;
    }

    /**
     * @param showTime the showTime to set
     */
    public void setShowTime(boolean showTime) {
        this.showTime = showTime;
        putBoolean("showTime", showTime);
    }

    /**
     * @return the showSpeedo
     */
    public boolean isShowSpeedo() {
        return showSpeedo;
    }

    /**
     * @return the showText
     */
    public boolean isShowText() {
        return showText;
    }

    /**
     * @param showText the showText to set
     */
    public void setShowText(boolean showText) {
        this.showText = showText;
        putBoolean("showText", showText);
    }

    /**
     * @param showSpeedo the showSpeedo to set
     */
    public void setShowSpeedo(boolean showSpeedo) {
        this.showSpeedo = showSpeedo;
        putBoolean("showSpeedo", showSpeedo);
    }

}
