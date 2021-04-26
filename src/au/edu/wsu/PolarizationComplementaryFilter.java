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
    private float[] aop;
    private float[] dop;
    FloatFunction exp = (s) -> (float) Math.exp(s);

    public PolarizationComplementaryFilter(final AEChip chip) {
        super(chip);
        apsDisplayPola = ImageDisplay.createOpenGLCanvas();
        apsFramePola = new JFrame("Polarization Information DoP - AoP");
        apsFramePola.setPreferredSize(new Dimension(600, 600));
        apsFramePola.getContentPane().add(apsDisplayPola, BorderLayout.CENTER);
        apsFramePola.pack();
        apsFramePola.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent e) {
                setShowAPSFrameDisplay(false);
            }
        });
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

    @Override
    public void initFilter() {
        super.initFilter();
        if(maxIDX > 0){
            indexf0 = new int[maxIDX];
            indexf45 = new int[maxIDX];
            indexf90 = new int[maxIDX];
            indexf135 = new int[maxIDX];
            apsDisplayPixmapBufferAop = new float[3 * maxIDX / 4 * 3];
            aop = new float[maxIDX / 4];
            dop = new float[maxIDX / 4];
            apsDisplayPola.setImageSize(width / 2, height / 2 * 3);
            PolarizationUtils.fillIndex(indexf0, indexf45, indexf90, indexf135, height, width);
            PolarizationUtils.drawLegend(apsDisplayPixmapBufferAop, height, width);
        }
    }
    
    public void displayPreBuffer() {
        if (maxIDX != indexf0.length && maxIDX > 0) {
            indexf0 = new int[maxIDX];
            indexf45 = new int[maxIDX];
            indexf90 = new int[maxIDX];
            indexf135 = new int[maxIDX];
            apsDisplayPixmapBufferAop = new float[3 * maxIDX / 4 * 3];
            aop = new float[maxIDX / 4];
            dop = new float[maxIDX / 4];
            apsDisplayPola.setImageSize(width / 2, height / 2 * 3);
            PolarizationUtils.fillIndex(indexf0, indexf45, indexf90, indexf135, height, width);
            PolarizationUtils.drawLegend(apsDisplayPixmapBufferAop, height, width);
        }
        PolarizationUtils.computeAoPDoP(logFinalFrame, aop, dop, exp, indexf0, indexf45, indexf90, indexf135, height, width);
        PolarizationUtils.setDisplay(apsDisplayPixmapBufferAop, aop, dop, height, width);
        apsDisplayPola.setPixmapArray(apsDisplayPixmapBufferAop);

//        PolarizationUtils.computeAoPDoP(logFinalFrame, aop, dop, exp, indexf0, indexf45, indexf90, indexf135, height, width);
//        PolarizationUtils.setDisplay(apsDisplayPixmapBuffer, aop, dop, height, width);
//        if(apsDisplayPixmapBufferAop.length > 0)
//            apsDisplayPola.setPixmapArray(apsDisplayPixmapBufferAop);
    }
    
    public float getDoP(int x, int y){
        return dop[getIndex(x, y)];
    }

    public float getAoP(int x, int y){
        return aop[getIndex(x, y)];
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
        float m_aop = 0, m_dop = 0;
        int nb = 0, idx;
        for(double x = roiRect.getCenterX() - roiRect.getWidth() / 2; x < roiRect.getCenterX() + roiRect.getWidth() / 2; x +=2){
            for (double y = roiRect.getCenterY() - roiRect.getHeight() / 2; y < roiRect.getCenterY() + roiRect.getHeight() / 2; y +=2){
                idx = (int)(x / 2 + y / 2 * width / 2);
                m_aop += aop[idx];
                m_dop += dop[idx];
                nb += 1;
            }
        }
        if(nb > 0){
            m_aop /= nb;
            m_dop /= nb;
        }
        System.out.printf("Angle: %f", m_aop * 180);   
        DrawGL.drawEllipse(gl, (float) roiRect.getCenterX(), (float) roiRect.getCenterY(), (float)(20), (float)( 20 * (1 - m_dop)), m_aop * 2.0f, 32);
        
    }

}
