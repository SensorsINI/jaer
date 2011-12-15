/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.test;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.filter.acceleration.AccelerationStructure;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.filter.acceleration.Block;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.filter.util.Coord;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.util.FileHandler;
import com.sun.opengl.util.j2d.TextRenderer;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 *
 * @author matthias
 */
public class PathRecorder extends EventFilter2D implements Observer, FrameAnnotater {
    public String path = "C:\\Users\\matthias\\Documents\\02_eth\\05_semester\\01_master\\02_repo\\doc\\experiments\\tracking\\results\\path\\hand.txt";
    
    private boolean hasMouseListener = false;
    
    private boolean isVirgin;
    private TypedEvent last;
    
    private AccelerationStructure<TimedCoord> structure;
    private List<TimedCoord> visualization;
    
    public PathRecorder(AEChip chip){
        super(chip);
        chip.addObserver(this);
        
        initFilter();
        resetFilter();
    }

    @Override
    public void initFilter() {
        this.structure = new Block<TimedCoord>(128, 128, 16);
        this.visualization = new ArrayList<TimedCoord>();
    }
    
    @Override
    public void resetFilter() {
        if (!this.structure.getElements().isEmpty()) {
            TimedCoord[] coords = this.structure.getElements().toArray(new TimedCoord[0]);
            Arrays.sort(coords, new Comparator<TimedCoord>() {

                @Override
                public int compare(TimedCoord o1, TimedCoord o2) {
                    return o1.timestamp - o2.timestamp;
                }
            });
            
            FileHandler handler = FileHandler.getInstance(this.path);
            for (int i = 0; i < coords.length; i++) {
                handler.writeLine(coords[i].toString());
            }
        }
        
        this.hasMouseListener = false;
        this.isVirgin = true;
        
        this.structure.clear();
    }

    @Override
    public void update(Observable o, Object arg) { }
    
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        for (Object o : in) {
            TypedEvent e = (TypedEvent)o;
            
            if (!this.isVirgin) this.isVirgin = false;
            this.last = e;
            
        }
        return in;
    }

    
    private TextRenderer renderer = new TextRenderer(new Font("Arial",Font.PLAIN,7),true,true);
    
    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (!this.hasMouseListener) {
            this.hasMouseListener = true;
            /*
             * Adds the MouseListener to the drawable instance.
             */
            drawable.addMouseListener(new MouseAdapter() {
                
                @Override
                public void mouseClicked(MouseEvent e) {
                    Point point = PathRecorder.this.chip.getCanvas().getPixelFromMouseEvent(e);
                    Coord coord = new Coord(point.getX(), point.getY());
                    
                    switch (e.getButton()) {
                        case 1:
                            // left mouse click
                            TimedCoord tc = new TimedCoord(coord, PathRecorder.this.last.timestamp);
                            PathRecorder.this.structure.add(coord, tc);
                            break;
                        case 3:
                            // right mouse click
                            synchronized(PathRecorder.this) {
                                TimedCoord candidate = PathRecorder.this.structure.getClosest(coord);
                                if (candidate != null) PathRecorder.this.structure.remove(candidate);
                            }
                            break;
                    }
                }

                @Override
                public void mousePressed(MouseEvent e) { }

                @Override
                public void mouseReleased(MouseEvent e) { }

                @Override
                public void mouseEntered(MouseEvent e) { }

                @Override
                public void mouseExited(MouseEvent e) { }
            });
        }
        
        /*
         * draws the points
         */
        GL gl = drawable.getGL();
        gl.glColor3f(0, 0, 1);
        
        this.visualization.clear();
        this.visualization.addAll(this.structure.getElements());
        
        gl.glPointSize(5);
        for (TimedCoord p : this.visualization) {
            gl.glBegin(GL.GL_POINTS);
                gl.glVertex2d(p.coord.x, p.coord.y);
            gl.glEnd();
            /*
            renderer.begin3DRendering();
            renderer.setColor(0,0,1,0.8f);
            renderer.draw3D(p.toString(), p.coord.getX(), p.coord.getY() - 4, 0, 0.5f);
            renderer.end3DRendering();
             */
        }
    }   
    
    public class TimedCoord {
        public Coord coord;
        public int timestamp;
        
        public TimedCoord(Coord coord, int timestamp) {
            this.coord = coord;
            this.timestamp = timestamp;
        }
        
        public String toString() {
            return timestamp + ", " + coord.x + ", " + coord.y;
        }
    }
}
