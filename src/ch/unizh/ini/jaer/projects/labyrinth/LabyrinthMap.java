/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.labyrinth;

import com.kitfox.svg.*;
import com.kitfox.svg.SVGUniverse;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.*;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import javax.media.opengl.*;
import javax.media.opengl.GLAutoDrawable;
import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.*;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 *  Loads SVG file describing a board and displays it in the annotate method.
 * 
 * @author Tobi
 */
public class LabyrinthMap extends EventFilter2D implements FrameAnnotater {

    public static String getDescription() {
        return "Handles SVG maps of Labyrinth game";
    }
    SVGUniverse svgUniverse = new SVGUniverse();
    SVGDiagram svgDiagram = null;
    ArrayList<Ellipse2D.Float> holes=new ArrayList();
    ArrayList<Line2D.Float> lines=new ArrayList();
    GeneralPath path=null;
    Rectangle2D.Float outline=null;
    Rectangle2D bounds=null;

    public LabyrinthMap(AEChip chip) {
        super(chip);
        setPropertyTooltip("loadMap", "opens file dialog to select SVG file of map");
        File f = getLastFilePrefs();
        try {
           loadMapFromFile(f);
        } catch (Exception ex) {
            log.warning("couldn't load map information from file " + f + ", caught " + ex + "; you are missing the SVG file describing the Labyrinth maze or it is corrupted");
        }
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        return in;
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }

    public void doLoadMap() {
        final JFileChooser fc = new JFileChooser();
        fc.setCurrentDirectory(getLastFilePrefs());  // defaults to startup runtime folder
        fc.setFileFilter(new FileFilter() {

            public boolean accept(File f) {
                return f.isDirectory()
                        || f.getName().toLowerCase().endsWith(".svg");
            }

            @Override
            public String getDescription() {
                return "SVG files (scalable vector graphics)";
            }
        });

        final int[] state = new int[1];
        state[0] = Integer.MIN_VALUE;

        SwingUtilities.invokeLater(new Runnable() {

            public void run() {
                fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
                fc.setSelectedFile(new File(getString("lastFile", System.getProperty("user.dir"))));
                state[0] = fc.showOpenDialog(chip.getAeViewer() != null && chip.getAeViewer().getFilterFrame() != null ? chip.getAeViewer().getFilterFrame() : null);
                if (state[0] == JFileChooser.APPROVE_OPTION) {
                    File file = fc.getSelectedFile();
                    putLastFilePrefs(file);
                    try {
                        loadMapFromFile(file);
                    } catch (Exception e) {
                        log.warning(e.toString());
                        JOptionPane.showMessageDialog(fc, "Couldn't load map from file " + file + ", caught exception " + e, "Map file error", JOptionPane.WARNING_MESSAGE);
                    }
                } else {
                    log.info("Cancelled saving!");
                }
            }
        });
    }

    private void loadMapFromFile(File file) throws MalformedURLException {
        log.info("loading map file "+file);
        svgUniverse.setVerbose(true);
        URI svgURI = svgUniverse.loadSVG(file.toURI().toURL());

        svgDiagram = svgUniverse.getDiagram(svgURI);
        float width=svgDiagram.getWidth();
        float height=svgDiagram.getHeight();
        SVGRoot root=svgDiagram.getRoot();
        loadChildren(root.getChildren(null));

    }

   private void loadChildren(List children) {
        for(Object o:children){
            if(o instanceof Circle){
                Circle c=(Circle)o;
                holes.add((Ellipse2D.Float)c.getShape());
            }else if(o instanceof Line){
                Line l=(Line)o;
                lines.add((Line2D.Float)l.getShape());
            }else if(o instanceof Polyline){
                Polyline l=(Polyline)o;
                l.getShape();
//                lines.add(l.getShape());
            }else if(o instanceof Rect){
                Rect r=(Rect)o;
                outline=(Rectangle2D.Float)r.getShape();
                bounds=outline.getBounds2D();
           }else if(o instanceof Path){
                Path r=(Path)o;
                path=(GeneralPath)r.getShape();
            }else if(o instanceof List){
                loadChildren((List)o);
            }else if(o instanceof Group){
                Group g=(Group)o;
                List l=g.getChildren(null);
                loadChildren(l);
            }
        }
    }

    private File getLastFilePrefs() {
        return new File(getString("lastFile", System.getProperty("user.dir")));
    }

    private void putLastFilePrefs(File file) {
        putString("lastFile", file.toString());
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        GL gl=drawable.getGL();
        gl.glPushMatrix();
        gl.glColor4f(0,0,.2f,0.3f);
        gl.glLineWidth(1);
        // scale and translate to match bounds of all shapes
        float s=(float)(chip.getSizeX()/bounds.getWidth());
        gl.glScalef(s, s,s);
        gl.glTranslated(-bounds.getMinX(), -bounds.getMinY(), 0);
        gl.glBegin(GL.GL_LINES);
        for(Line2D.Float l:lines){
            gl.glVertex2f(l.x1,l.y1);
            gl.glVertex2f(l.x2,l.y2);
        }
        gl.glEnd();
        gl.glPopMatrix();
    }


}
