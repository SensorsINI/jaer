/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing.filter;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.awt.TextRenderer;
import java.awt.Font;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.AEChipRenderer;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 *
 * Sets accumulate mode on AEViewer display and resets at fixed DVS event count
 *
 * @author tobi
 */
@Description("Display control filter that sets accumulate mode on AEViewer display and resets at fixed DVS event count")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class AccumulateAndResetFilter extends EventFilter2D implements FrameAnnotater {

    public enum Method {
        EventCount, TimeInterval
    }

    private Method method = Method.valueOf(getString("method", Method.EventCount.toString()));

    private int numDvsEventsToResetAccumulation = getInt("numDvsEventsToResetAccumulation", 5000);
    private int timeIntervalToResetAccumulationUs = getInt("timeIntervalToResetAccumulationUs", 100000);
    private boolean showEventsAccumulatedBar = getBoolean("showEventsAccumulatedBar", true);
    private boolean showTimeElapsedText = getBoolean("showTimeElapsedText", true);

    public AccumulateAndResetFilter(AEChip chip) {
        super(chip);
        setPropertyTooltip("numDvsEventsToResetAccumulation", "sets number of dvs events to reset accumulation of image");
        setPropertyTooltip("timeIntervalToResetAccumulationUs", "sets time interval in us to reset accumulation of image");
        setPropertyTooltip("showEventsAccumulatedBar", "shows a bar for num events accumulated");
        setPropertyTooltip("showTimeElapsedText", "shows text for time elapsed since last accumulation resst");
        setPropertyTooltip("method", "method to reset accumulation");
    }

    private int numEventsAccumulated = 0;
    private int lastResetTimestampUs = 0, currentTimestamp = 0;

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        AEChipRenderer renderer = chip.getRenderer();

        for (Object o : in) {
            BasicEvent e = (BasicEvent) o;
            if (e.isFilteredOut() || e.isSpecial()) {
                continue;
            }
            numEventsAccumulated++;
            currentTimestamp=e.timestamp;
            if(currentTimestamp<lastResetTimestampUs) lastResetTimestampUs=currentTimestamp; // handle wrapping/rewind
            switch (method) {
                case EventCount:
                    if (numEventsAccumulated >= numDvsEventsToResetAccumulation) {
                        renderer.resetFrame(renderer.getGrayValue());
                        numEventsAccumulated = 0;
                        lastResetTimestampUs = currentTimestamp;
                    }
                    break;
                case TimeInterval:
                    if (currentTimestamp-lastResetTimestampUs >= timeIntervalToResetAccumulationUs
                            || lastResetTimestampUs==0) {
                        renderer.resetFrame(renderer.getGrayValue());
                        numEventsAccumulated = 0;
                        lastResetTimestampUs = currentTimestamp;
                    }
                    break;

            }
        }
        currentTimestamp = in.getLastTimestamp();
        return in;
    }

    @Override
    public void resetFilter() {
        numEventsAccumulated = getNumDvsEventsToResetAccumulation();
        lastResetTimestampUs=0;
    }

    @Override
    public void initFilter() {
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        chip.getRenderer().setAccumulateEnabled(yes);
    }

    /**
     * @return the numDvsEventsToResetAccumulation
     */
    public int getNumDvsEventsToResetAccumulation() {
        return numDvsEventsToResetAccumulation;
    }

    /**
     * @param numDvsEventsToResetAccumulation the
     * numDvsEventsToResetAccumulation to set
     */
    public void setNumDvsEventsToResetAccumulation(int numDvsEventsToResetAccumulation) {
        this.numDvsEventsToResetAccumulation = numDvsEventsToResetAccumulation;
        putInt("numDvsEventsToResetAccumulation", numDvsEventsToResetAccumulation);
    }

    /**
     * @return the showEventsAccumulatedBar
     */
    public boolean isShowEventsAccumulatedBar() {
        return showEventsAccumulatedBar;
    }

    /**
     * @param showEventsAccumulatedBar the showEventsAccumulatedBar to set
     */
    public void setShowEventsAccumulatedBar(boolean showEventsAccumulatedBar) {
        this.showEventsAccumulatedBar = showEventsAccumulatedBar;
        putBoolean("showEventsAccumulatedBar", showEventsAccumulatedBar);
    }

    /**
     * @return the showTimeElapsedText
     */
    public boolean isShowTimeElapsedText() {
        return showTimeElapsedText;
    }

    /**
     * @param showTimeElapsedText the showTimeElapsedText to set
     */
    public void setShowTimeElapsedText(boolean showTimeElapsedText) {
        this.showTimeElapsedText = showTimeElapsedText;
        putBoolean("showTimeElapsedText", showTimeElapsedText);
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        gl.glColor3f(.2f, .2f, .8f);
        TextRenderer renderer = null;
        if (showTimeElapsedText || showEventsAccumulatedBar) {
            renderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 18));
            renderer.setColor(.2f, .2f, .8f,.75f);
        }

        if (showEventsAccumulatedBar) {
            gl.glLineWidth(10);
            gl.glBegin(GL.GL_LINES);
            float x1 = .9f * chip.getSizeX() * (float) numEventsAccumulated / numDvsEventsToResetAccumulation;
            float y1 = .9f * chip.getSizeY();
            gl.glVertex2f(.1f * chip.getSizeX(), y1);
            gl.glVertex2f(x1, y1);
            gl.glEnd();
            renderer.begin3DRendering();
            renderer.draw3D(String.format("%.1f kev", 1e-3f * numEventsAccumulated), x1, y1, 0, 1f);
            renderer.end3DRendering();
        }
        if (showTimeElapsedText) {
            String s = String.format("%.1f ms", 1e-3f * (currentTimestamp - lastResetTimestampUs));
            renderer.begin3DRendering();
            renderer.draw3D(s, .1f * chip.getSizeX(), .8f * chip.getSizeY(), 0, 1f);
            renderer.end3DRendering();
        }
    }

    /**
     * @return the method
     */
    public Method getMethod() {
        return method;
    }

    /**
     * @param method the method to set
     */
    public void setMethod(Method method) {
        this.method = method;
        putString("method", method.toString());
    }

    /**
     * @return the timeIntervalToResetAccumulationUs
     */
    public int getTimeIntervalToResetAccumulationUs() {
        return timeIntervalToResetAccumulationUs;
    }

    /**
     * @param timeIntervalToResetAccumulationUs the timeIntervalToResetAccumulationUs to set
     */
    public void setTimeIntervalToResetAccumulationUs(int timeIntervalToResetAccumulationUs) {
        this.timeIntervalToResetAccumulationUs = timeIntervalToResetAccumulationUs;
        putInt("timeIntervalToResetAccumulationUs",timeIntervalToResetAccumulationUs);
    }

}
