package au.edu.wsu;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.graphics.ImageDisplay;
import ch.unizh.ini.jaer.projects.davis.frames.DavisComplementaryFilter;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import net.sf.jaer.util.DrawGL;

/**
 * Extracts Polarization information using Cedric's complementary filter to
 * obtain the absolute light intensity.
 *
 * @author Damien Joubert, Tobi Delbruck
 */
@Description("Method to extract polarization information from a stream of APS/DVS events using Cedric's complementary filter")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class PolarizationComplementaryFilter extends DavisComplementaryFilter {

    // offset of f0, f45, f90 and f135 acording to the index
    private int[] indexf0, indexf45, indexf90, indexf135;
    private JFrame apsFramePola = null;
    public ImageDisplay apsDisplayPola;
    private float[] apsDisplayPixmapBufferAop;

    public PolarizationComplementaryFilter(final AEChip chip) {
        super(chip);
        apsDisplayPola = ImageDisplay.createOpenGLCanvas();
        apsFramePola = new JFrame("Polarization Information DoP - AOP");
        apsFramePola.setPreferredSize(new Dimension(800, 800));
        apsFramePola.getContentPane().add(apsDisplayPola, BorderLayout.CENTER);
        apsFramePola.pack();
        apsFramePola.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent e) {
                setShowAPSFrameDisplay(false);
            }
        });
        indexf0 = new int[maxIDX];
        indexf45 = new int[maxIDX];
        indexf90 = new int[maxIDX];
        indexf135 = new int[maxIDX];
        initFilter();
    }

    @Override
    public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        checkMaps();
        in = super.filterPacket(in);
        displayPreBuffer();
        if (showAPSFrameDisplay) {
            apsDisplayPola.repaint();
        }
        return in; // should be denoised output
    }

    private void checkMaps() {
        apsDisplayPola.checkPixmapAllocation();
        if (showAPSFrameDisplay && !apsFramePola.isVisible()) {
            apsFramePola.setVisible(true);
        }
    }

    public void displayPreBuffer() {
        if (maxIDX != indexf0.length) {
            indexf0 = new int[maxIDX];
            indexf45 = new int[maxIDX];
            indexf90 = new int[maxIDX];
            indexf135 = new int[maxIDX];
            apsDisplayPixmapBufferAop = new float[3 * maxIDX / 4 * 3];
            apsDisplayPola.setImageSize(width / 2, height / 2 * 3);
            PolarizationUtils.fillIndex(indexf0, indexf45, indexf90, indexf135, height, width);
            PolarizationUtils.drawLegend(apsDisplayPixmapBufferAop, height, width);
        }
        PolarizationUtils.computePolarizationLog(logFinalFrame, apsDisplayPixmapBufferAop, indexf0, indexf45, indexf90, indexf135, height, width);
        apsDisplayPola.setPixmapArray(apsDisplayPixmapBufferAop);
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        super.annotate(drawable); // draws the ROI selection rectangle
        GL2 gl = drawable.getGL().getGL2();

        if (roiRect == null) {
            return;
        }
// draw the polarization ellipse
        gl.glColor3f(1, 1, 1);  // set the RGB color
        gl.glLineWidth(3); // set the line width in screen pixels
        // draw the polarization ellipse

        DrawGL.drawEllipse(gl, (float) roiRect.getCenterX(), (float) roiRect.getCenterY(), 20, 30, 45, 32);
    }

}
