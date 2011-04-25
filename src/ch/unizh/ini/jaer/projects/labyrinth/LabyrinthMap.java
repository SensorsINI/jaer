/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.labyrinth;

import com.kitfox.svg.*;
import com.kitfox.svg.SVGUniverse;
import com.sun.opengl.util.GLUT;
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
import javax.media.opengl.glu.GLU;
import javax.media.opengl.glu.GLUquadric;
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
    ArrayList<Ellipse2D.Float> holes = new ArrayList();
    ArrayList<Line2D.Float> lines = new ArrayList();
    ArrayList<ArrayList<Point2D.Float>> paths = new ArrayList();
    ArrayList<Point2D.Float> ballPath = null;
    Rectangle2D.Float outline = null;
    Rectangle2D bounds = null;

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
        log.info("loading map file " + file);
        svgUniverse.setVerbose(true);
        URI svgURI = svgUniverse.loadSVG(file.toURI().toURL());

        svgDiagram = svgUniverse.getDiagram(svgURI);
        SVGRoot root = svgDiagram.getRoot();
        loadChildren(root.getChildren(null));
        String s = String.format("map has %d holes, %d lines, %d paths, ball path has %d vertices", holes.size(), lines.size(), paths.size(), ballPath != null ? ballPath.size() : 0);
        log.info(s);
    }
    int longestPath = Integer.MIN_VALUE;

    private void loadChildren(List children) {
        for (Object o : children) {
            if (o instanceof Circle) {
                Circle c = (Circle) o;
                holes.add((Ellipse2D.Float) c.getShape());
            } else if (o instanceof Line) {
                Line l = (Line) o;
                lines.add((Line2D.Float) l.getShape());
            } else if (o instanceof Polyline) {
                Polyline l = (Polyline) o;
                Shape s = l.getShape();
                s.getBounds2D();
//                lines.add(l.getShape());
            } else if (o instanceof Rect) {
                Rect r = (Rect) o;
                outline = (Rectangle2D.Float) r.getShape();
                bounds = outline.getBounds2D();
            } else if (o instanceof Path) {
                // only the actual path of the ball should be a path, it should be a connected path
                Path r = (Path) o;
                GeneralPath path = (GeneralPath) r.getShape();
                PathIterator itr = path.getPathIterator(null, 0.01);
                ArrayList<Point2D.Float> pathList = new ArrayList();
                Point2D.Float pathStart=null, lastPoint=null;
                boolean closed=false;
                while (!itr.isDone()) {
                    float[] coords = new float[6];
                    int segtype = itr.currentSegment(coords);
                    switch (segtype) {
                        case PathIterator.SEG_MOVETO:
                            pathStart=new Point2D.Float(coords[0],coords[1]); // save start point
                        case PathIterator.SEG_LINETO:
                        case PathIterator.SEG_QUADTO:
                        case PathIterator.SEG_CUBICTO:
                            pathList.add((lastPoint=new Point2D.Float(coords[0], coords[1]))); // TODO store quads/cubes as well as lines
                            break;
                        case PathIterator.SEG_CLOSE:
                            closed=true;
                            if(pathStart!=null){
                                pathList.add(pathStart);
                            }
                            break;
                        default:
                            log.info("found other element " + segtype);
                    }
                    itr.next();
                }
                if(!closed && lastPoint!=null){
                    pathList.remove(lastPoint);
                }
                if (pathList.size() > longestPath) {
                    ballPath = pathList;
                    longestPath = ballPath.size();
                }
                paths.add(pathList);
            } else if (o instanceof List) {
                loadChildren((List) o);
            } else if (o instanceof Group) {
                Group g = (Group) o;
                List l = g.getChildren(null);
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
    boolean madeList = false;
    private GLU glu = new GLU();
    private GLUT glut = new GLUT();
    private GLUquadric holeQuad = glu.gluNewQuadric();
    int listnum = 0;

    @Override
    public void annotate(GLAutoDrawable drawable) {
        GL gl = drawable.getGL();
//        if (!madeList) {
//            listnum = gl.glGenLists(1);
//            if (listnum == 0) {
//                log.warning("cannot create display list to show the map, glGenLists returned 0");
//            }
//            gl.glNewList(1, GL.GL_COMPILE_AND_EXECUTE);
//            {
        gl.glPushMatrix();
        gl.glColor4f(0, 0, .2f, 0.3f);
        gl.glLineWidth(1);
        // scale and translate to match bounds of all shapes
        // drawing is e.g. in inches
        float s = (float) (chip.getSizeX() / bounds.getWidth());  // we'll scale up to pixels, and flip y while we're at it since drawing starts in UL
        gl.glScalef(s, -s, s);
        // now we'll translate so that minx in drawing is at left, and top of drawing is at bottom
        gl.glTranslated(-bounds.getMinX(), -bounds.getMaxY(), 0);
        {
            // draw outline
            gl.glBegin(GL.GL_LINE_LOOP);
            gl.glVertex2f(outline.x, outline.y);
            gl.glVertex2f(outline.x + outline.width, outline.y);
            gl.glVertex2f(outline.x + outline.width, outline.y + outline.height);
            gl.glVertex2f(outline.x, outline.y + outline.height);
            gl.glEnd();

            // draw maze walls
            gl.glBegin(GL.GL_LINES);
            for (Line2D.Float l : lines) {
                gl.glVertex2f(l.x1, l.y1);
                gl.glVertex2f(l.x2, l.y2);
            }
            gl.glEnd();
            for (ArrayList<Point2D.Float> p : paths) {
                if (p == ballPath) {
                    continue;
                }
                gl.glBegin(GL.GL_LINE_STRIP);
                for (Point2D.Float v : p) {
                    gl.glVertex2f(v.x, v.y);
                }
                gl.glEnd();
            }
        }
        // draw holes
        {
            glu.gluQuadricDrawStyle(holeQuad, GLU.GLU_LINE);
            gl.glColor4f(.1f, .1f, .1f, .3f);
            for (Ellipse2D.Float e : holes) { // ellipse has UL corner as x,y location (here LL corner)
                gl.glPushMatrix();
                gl.glTranslatef(e.x + e.width / 2, e.y + e.height / 2, 0);
                glu.gluDisk(holeQuad, 0, e.width / 2, 16, 1);
                gl.glPopMatrix();

            }
        }
        // draw path
        {
            if (ballPath != null) {
                gl.glColor4f(.1f, .1f, .1f, .3f);
                 gl.glBegin(GL.GL_LINE_STRIP);
                for (Point2D.Float l : ballPath) {
                    gl.glVertex2f(l.x, l.y);
                }
                gl.glEnd();
            }
        }
        gl.glPopMatrix();
//            }
//            gl.glEndList();
//            chip.getCanvas().checkGLError(gl, glu, "after making list for map");
//            madeList = true;
//        } else {
//            gl.glCallList(listnum);
//        }
    }
}
