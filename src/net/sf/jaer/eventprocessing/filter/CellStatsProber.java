/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing.filter;
import com.sun.opengl.util.GLUT;
import java.awt.*;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCanvas;
import javax.swing.JMenu;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.ChipRendererDisplayMethod;
import net.sf.jaer.graphics.DisplayMethod;
import net.sf.jaer.graphics.FrameAnnotater;
/**
 * Collects and displays statistics for a selected range of pixels / cells.
 *
 * @author tobi
 *
 * This is part of jAER
<a href="http://jaer.wiki.sourceforge.net">jaer.wiki.sourceforge.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
public class CellStatsProber extends EventFilter2D implements FrameAnnotater,MouseListener,MouseMotionListener{
    private GLCanvas glCanvas;
    private ChipCanvas canvas;
    private DisplayMethod displayMethod;
    private ChipRendererDisplayMethod chipRendererDisplayMethod;
    Rectangle selection = null;
    private static float[] selectionColor = { 0,0,1 };
    private static float lineWidth = 1f;
//    private JMenu popupMenu;
    int startx, starty, endx, endy;
    Point startPoint = null, endPoint = null, clickedPoint = null;
    private GLUT glut=new GLUT();
    private Stats stats=new Stats();

    public CellStatsProber (AEChip chip){
        super(chip);
        if ( chip.getCanvas() != null && chip.getCanvas().getCanvas() != null ){
            canvas = chip.getCanvas();
            glCanvas = (GLCanvas)chip.getCanvas().getCanvas();
        }
    }

    public void display (GLAutoDrawable drawable){
        if ( drawable == null || selection == null || chip.getCanvas() == null ){
            return;
        }
        canvas = chip.getCanvas();
        glCanvas = (GLCanvas)canvas.getCanvas();
        int sx = chip.getSizeX(), sy = chip.getSizeY();
        Rectangle chipRect = new Rectangle(sx,sy);
        GL gl = drawable.getGL();
        if ( !chipRect.intersects(selection) ){
            return;
        }
        drawSelection(gl,selection,selectionColor);


    }

    private boolean inSelection (BasicEvent e){
        if ( selection.contains(e.x,e.y) ){
            return true;
        }
        return false;
    }

    public void collectStats (EventPacket in){
        if ( selection == null ){
            return;
        }
        stats.count=0;
        for ( Object o:in ){
            BasicEvent e = (BasicEvent)o;
            if ( inSelection(e) ){
                stats.count++;
            }

        }
    }

    public void showContextMenu (){
    }

    private void drawSelection (GL gl,Rectangle r,float[] c){
        gl.glPushMatrix();
        gl.glColor3fv(c,0);
        gl.glLineWidth(lineWidth);
        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex2f(selection.x,selection.y);
        gl.glVertex2f(selection.x + selection.width,selection.y);
        gl.glVertex2f(selection.x + selection.width,selection.y + selection.height);
        gl.glVertex2f(selection.x,selection.y + selection.height);
        gl.glEnd();
        gl.glTranslatef(selection.x,selection.y,0);
        float s=7f/chip.getMaxSize();
        gl.glLineWidth(3);
        gl.glScalef(s,s,1);
        glut.glutStrokeString(GLUT.STROKE_MONO_ROMAN,stats.toString());
        gl.glPopMatrix();
    }

    @Override
    public EventPacket filterPacket (EventPacket in){
        collectStats(in);
        return in;
    }

    @Override
    synchronized public void resetFilter (){
        selection = null;
    }

    @Override
    public void initFilter (){
    }

    public void annotate (GLAutoDrawable drawable){
        if ( canvas.getDisplayMethod() instanceof ChipRendererDisplayMethod ){
            chipRendererDisplayMethod = (ChipRendererDisplayMethod)canvas.getDisplayMethod();
            display(drawable);
        }
    }

    public void mouseWheelMoved (MouseWheelEvent e){
    }

    public void mouseReleased (MouseEvent e){
        if ( startPoint == null ){
            return;
        }
        Point p = canvas.getPixelFromMouseEvent(e);
        endPoint = p;
        startx = min(startPoint.x,endPoint.x);
        starty = min(startPoint.y,endPoint.y);
        endx = max(startPoint.x,endPoint.x);
        endy = max(startPoint.y,endPoint.y);
        int w = endx - startx;
        int h = endy - starty;
        selection = new Rectangle(startx,starty,w,h);
    }

    private int min (int a,int b){
        return a < b ? a : b;
    }

    private int max (int a,int b){
        return a > b ? a : b;
    }

    public void mousePressed (MouseEvent e){
        Point p = canvas.getPixelFromMouseEvent(e);
        startPoint = p;
    }

    public void mouseMoved (MouseEvent e){
    }

    public void mouseExited (MouseEvent e){
    }

    public void mouseEntered (MouseEvent e){
    }

    public void mouseDragged (MouseEvent e){
        if ( startPoint == null ){
            return;
        }
        endPoint = canvas.getPixelFromMouseEvent(e);
    }

    public void mouseClicked (MouseEvent e){
        Point p = canvas.getPixelFromMouseEvent(e);
        clickedPoint = p;
    }

    @Override
    public synchronized void setFilterEnabled (boolean yes){
        super.setFilterEnabled(yes);
        if ( glCanvas == null ){
            return;
        }
        if ( yes ){
            glCanvas.addMouseListener(this);
            glCanvas.addMouseMotionListener(this);

        } else{
            glCanvas.removeMouseListener(this);
            glCanvas.removeMouseMotionListener(this);
        }
    }

    private class Stats{
        int count=0;

        public String toString(){
            return String.format("n=%d",count);
        }

    }
}
