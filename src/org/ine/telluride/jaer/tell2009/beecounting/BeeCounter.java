/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ine.telluride.jaer.tell2009.beecounting;
import com.sun.opengl.util.GLUT;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
/**
 * Subclases RectangularClusterTracker to count objects that cross marked crossing lines.
 *
 * @author tobi, Robyn Verrinder, Brian Smith, Telluride 2009
 *
 * This is part of jAER
<a href="http://jaer.wiki.sourceforge.net">jaer.wiki.sourceforge.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
public class BeeCounter extends RectangularClusterTracker{
    private float topLine = getPrefs().getFloat("BeeCounter.topLine",0.75f);
    private float botLine = getPrefs().getFloat("BeeCounter.botLine",0.25f);
    private int nOut = 0,  nIn = 0;

    public BeeCounter (AEChip chip){
        super(chip);
        setPropertyTooltip("BeeCounting","topLine","exit line as fraction of screen");
        setPropertyTooltip("BeeCounting","botLine","entrance line as fraction of screen");

    }

    @Override
    public synchronized void resetFilter (){
        super.resetFilter();
        nOut = 0;
        nIn = 0;
    }
    private final GLUT glut = new GLUT();

    @Override
    public synchronized void annotate (GLAutoDrawable drawable){
        super.annotate(drawable);
        final int sx = chip.getSizeX(),  sy = chip.getSizeY();
        final GL gl = drawable.getGL();
        gl.glRasterPos2f(nIn,nIn);
        gl.glLineWidth(2f);
        gl.glColor3f(0,0,1);
        gl.glRasterPos3f(0,sy * getTopLine(),0);
        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18,String.format("%d exited",nOut));
        gl.glRasterPos3f(0,sy * getBotLine(),0);
        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18,String.format("%d entered",nIn));
        gl.glBegin(GL.GL_LINES);
        gl.glVertex2f(0,sy * getTopLine());
        gl.glVertex2f(sx,sy * getTopLine());
        gl.glVertex2f(0,sy * getBotLine());
        gl.glVertex2f(sx,sy * getBotLine());
        gl.glEnd();
    }

    /**
     * @return the topLine
     */
    public float getTopLine (){
        return topLine;
    }

    /**
     * @param topLine the topLine to set
     */
    public void setTopLine (float topLine){
        if ( topLine < 0 ){
            topLine = 0;
        } else if ( topLine > 1 ){
            topLine = 1;
        }
        this.topLine = topLine;
        getPrefs().putFloat("BeeCounter.topLine",topLine);
    }

    /**
     * @return the botLine
     */
    public float getBotLine (){
        return botLine;
    }

    /**
     * @param botLine the botLine to set
     */
    public void setBotLine (float botLine){
        if ( botLine < 0 ){
            botLine = 0;
        } else if ( botLine > 1 ){
            botLine = 1;
        }
        this.botLine = botLine;
        getPrefs().putFloat("BeeCounter.botLine",botLine);
    }

//    @Override
//    public EventPacket filterPacket (EventPacket in){
//        in=super.filterPacket(in);
//    }
}
