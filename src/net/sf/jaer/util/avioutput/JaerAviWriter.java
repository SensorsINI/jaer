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
import net.sf.jaer.Help;
import net.sf.jaer.Preferred;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.util.DrawGL;

/**
 * Writes AVI file from displayed AEViewer frames. Frame capture is driven by
 * the AEViewer ViewLoop via {@link #requestFrameCapture()} /
 * {@link #awaitFrameCapture(long)} so each rendered frame is written before the
 * loop advances (recording may run slower than real time). AVI playback rate
 * matches the AEViewer target rendering rate when
 * {@link #isMatchViewerFrameRate()} is enabled.
 *
 * @author Tobi
 */
@Description("Writes AVI file AEViewer displayed OpenGL graphics")
@Help("""
<html>
<body>
<h2>JaerAviWriter</h2>
<p>Records the <b>AEViewer OpenGL canvas</b> (events, APS overlay, annotations) to AVI.
Each rendered frame is captured from the ViewLoop before the player advances, so the file
can be slower than real time. Playback FPS follows the viewer when
<code>matchViewerFrameRate</code> is on (see <code>AbstractAviWriter</code>).</p>
<hr>
<h3>How to use</h3>
<ol>
<li>Enable the filter. Arrange the window (optionally
<code>resizeWindowTo16To9Format</code> / <code>resizeWindowTo4To3Format</code>).</li>
<li><code>startRecordingAndSaveAs</code>, play the stream, then <code>finishRecording</code>.</li>
<li><code>showTimeFactor</code> burns in the realtime slowdown/speedup factor
(<code>fontSize</code>).</li>
</ol>
<p>For DVS histogram slices at chip resolution use <code>DvsSliceAviWriter</code>.
For DAVIS APS frames (not the OpenGL view) use <code>DavisFrameAviWriter</code>.</p>
</body>
</html>
""")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class JaerAviWriter extends AbstractAviWriter {

    @Preferred private boolean showTimeFactor = getBoolean("showTimeFactor", false);
    private int fontSize = getInt("fontSize", 9);
    private float timeExpansionFactor = 1;

    public JaerAviWriter(AEChip chip) {
        super(chip);
        setPropertyTooltip("showTimeFactor", "Displays the realtime slowdown or speedup factor");
        setPropertyTooltip("fontSize", "Font size for time scaling factor");
    }

    @Override
    synchronized public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        super.filterPacket(in);
        if (in.getDurationUs() > 0) {
            timeExpansionFactor = in.getDurationUs() * 1e-6f * getFrameRate();
        }
        // Frame capture is requested by AEViewer.ViewLoop immediately before paintFrame(),
        // not here — that keeps one AVI frame per rendered view regardless of filter mode.
        return in;
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (showTimeFactor) {
            String s;
            if (timeExpansionFactor < 1) {
                s = String.format("%.1fX slow-down", 1 / timeExpansionFactor);
            } else {
                s = String.format("%.1fX speed-up", timeExpansionFactor);
            }
            DrawGL.drawString(fontSize, 0, 0, 0, Color.white, s);
        }

        if (isRecordingActive() && isWriteEnabled() && isFrameCapturePending()) {
            if (chip.getAeViewer() != null && !chip.getAeViewer().isActiveRenderingEnabled()) {
                chip.getAeViewer().setActiveRenderingEnabled(true);
                showPlainMessageDialogInSwingThread(
                        "Set active rendering enabled (View/View/Filtering options) to ensure that each frame is painted",
                        "Active rendering enabled");
            }
            try {
                GL2 gl = drawable.getGL().getGL2();
                BufferedImage bi = toImage(gl, drawable.getNativeSurface().getSurfaceWidth(),
                        drawable.getNativeSurface().getSurfaceHeight());
                int timecode = chip.getAeViewer() != null && chip.getAeViewer().getAePlayer() != null
                        ? chip.getAeViewer().getAePlayer().getTime()
                        : 0;
                writeFrame(bi, timecode);
            } finally {
                // Always release ViewLoop wait, even if write failed/closed the file
                signalFrameCaptureComplete();
            }
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
     * @return the fontSize
     */
    public int getFontSize() {
        return fontSize;
    }

    /**
     * @param fontSize the fontSize to set
     */
    public void setFontSize(int fontSize) {
        this.fontSize = fontSize;
        putInt("fontSize", fontSize);
    }

}
