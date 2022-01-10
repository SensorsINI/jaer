/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.util.avioutput;

import java.awt.image.BufferedImage;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import java.awt.Color;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;

/**
 * Writes AVI file from displayed AEViewer frames, The AVI file is in RAW
 * format.
 *
 * @author Tobi
 */
@Description("Writes AVI file AEViewer displayed OpenGL graphics")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class JaerAviWriter extends AbstractAviWriter {

    private boolean showTimeFactor = getBoolean("showTimeFactor", false);
    private float showTimeFactorTextScale = getFloat("showTimeFactorTextScale", .2f);
    private float timeExpansionFactor = 1;
    
    private volatile boolean writeFrameNowFlag=false;

    public JaerAviWriter(AEChip chip) {
        super(chip);
        setPropertyTooltip("showTimeFactor", "Displays the realtime slowdown or speedup factor");
        setPropertyTooltip("showTimeFactorTextScale", "Font size for time scaling factor");
    }

    @Override
    synchronized public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        super.filterPacket(in);
        if (in.getDurationUs() > 0) {
            timeExpansionFactor = in.getDurationUs() * 1e-6f * getFrameRate();
        }
        writeFrameNowFlag=true; // frame is processed by filter chain, write it on next rendering cycle
//        log.info("processing"); // TODO debug
        return in;
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (showTimeFactor) {
            String s = null;
            if (timeExpansionFactor < 1) {
                s = String.format("%.1fX slow-down", 1 / timeExpansionFactor);
            } else {
                s = String.format("%.1fX speed-up", timeExpansionFactor);
            }
            MultilineAnnotationTextRenderer.resetToYPositionPixels(chip.getSizeY() * .1f);
            MultilineAnnotationTextRenderer.setScale(showTimeFactorTextScale);
            MultilineAnnotationTextRenderer.setColor(Color.blue);
            MultilineAnnotationTextRenderer.renderMultilineString(s);
        }

        if (isRecordingActive() && isWriteEnabled() && writeFrameNowFlag) {
            GL2 gl = drawable.getGL().getGL2();
            BufferedImage bi = toImage(gl, drawable.getNativeSurface().getSurfaceWidth(), drawable.getNativeSurface().getSurfaceHeight());
            int timecode = chip.getAeViewer().getAePlayer().getTime();
            writeFrame(bi, timecode);
            writeFrameNowFlag=false; // TODO move to super
        }
    }

    /**
     * @return the showTimeFactor
     */
    public boolean isShowTimeFactor() {
        return showTimeFactor;
    }

    /**
     * @param showTimeFactor the showTimeFactor to set
     */
    public void setShowTimeFactor(boolean showTimeFactor) {
        this.showTimeFactor = showTimeFactor;
        putBoolean("showTimeFactor", showTimeFactor);
    }

    /**
     * @return the showTimeFactorTextScale
     */
    public float getShowTimeFactorTextScale() {
        return showTimeFactorTextScale;
    }

    /**
     * @param showTimeFactorTextScale the showTimeFactorTextScale to set
     */
    public void setShowTimeFactorTextScale(float showTimeFactorTextScale) {
        this.showTimeFactorTextScale = showTimeFactorTextScale;
        putFloat("showTimeFactorTextScale", showTimeFactorTextScale);
    }

}
