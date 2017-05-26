/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.util.avioutput;

import java.awt.image.BufferedImage;
import java.io.IOException;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import static net.sf.jaer.eventprocessing.EventFilter.log;

/**
 * Writes AVI file from displayed AEViewer frames, The AVI file is in RAW
 * format.
 *
 * @author Tobi
 */
@Description("Writes AVI file AEViewer displayed OpenGL graphics")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class JaerAviWriter extends AbstractAviWriter {

    public JaerAviWriter(AEChip chip) {
        super(chip);
    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        super.filterPacket(in);
        return in;
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (aviOutputStream == null ) {
            return;
        }
        GL2 gl = drawable.getGL().getGL2();
        BufferedImage bi = toImage(gl, drawable.getNativeSurface().getSurfaceWidth(), drawable.getNativeSurface().getSurfaceHeight());

        try {
            aviOutputStream.writeFrame(bi);
            if (isWriteTimecodeFile()) {
                writeTimecode(chip.getAeViewer().getAePlayer().getTime());
            }
            incrementFramecountAndMaybeCloseOutput();

        } catch (Exception e) {
            log.warning("While writing AVI frame, caught exception, closing file: " + e.toString());
            doCloseFile();
        }
    }

}
