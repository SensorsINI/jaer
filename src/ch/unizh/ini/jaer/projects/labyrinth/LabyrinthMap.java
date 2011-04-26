/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.labyrinth;

import com.kitfox.svg.*;
import com.kitfox.svg.SVGUniverse;
import com.sun.opengl.util.GLUT;
import java.awt.Shape;
import java.awt.geom.*;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
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
import net.sf.jaer.util.Matrix;

/**
 *  Loads SVG file describing a board and displays it in the annotate method.
 * 
 * @author Tobi
 */
public class LabyrinthMap extends EventFilter2D implements FrameAnnotater, Observer {

    public static String getDescription() {
        return "Handles SVG maps of Labyrinth game";
    }
    // svg stuff
    ArrayList<Ellipse2D.Float> holesSVG = new ArrayList();
    ArrayList<Line2D.Float> linesSVG = new ArrayList();
    ArrayList<ArrayList<Point2D.Float>> pathsSVG = new ArrayList();
    ArrayList<Point2D.Float> ballPathSVG = null;
    Rectangle2D.Float outlineSVG = null;
    Rectangle2D boundsSVG = null;
    // chip space stuff, in retina coordinates
    ArrayList<Point2D.Float> ballPath = null;
    // properties
    private boolean displayMap = getBoolean("displayMap", true);
    private float rotationDegCCW = getFloat("rotationDegCCW", 0);
    private float scale = getFloat("scale", 1);
    private float transXPixels = getFloat("transXPixels", 0);
    private float transYPixels = getFloat("transYPixels", 0);

    public LabyrinthMap(AEChip chip) {
        super(chip);
        setPropertyTooltip("loadMap", "opens file dialog to select SVG file of map");
        File f = getLastFilePrefs();
        try {
            loadMapFromFile(f);
        } catch (Exception ex) {
            log.warning("couldn't load map information from file " + f + ", caught " + ex + "; you are missing the SVG file describing the Labyrinth maze or it is corrupted");
        }
        setPropertyTooltip("displayMap", "");  // TODO
        setPropertyTooltip("rotationDegCCW", "");
        setPropertyTooltip("scale", "");
        setPropertyTooltip("transXPixels", "");
        setPropertyTooltip("transYPixels", "");
        chip.addObserver(this);// to get informed about changes to chip size
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

    private void addGeneralPath(GeneralPath path) {
        PathIterator itr = path.getPathIterator(null, 0.01);
        ArrayList<Point2D.Float> pathList = new ArrayList();
        Point2D.Float pathStart = null, lastPoint = null;
        boolean closed = false;
        while (!itr.isDone()) {
            float[] coords = new float[6];
            int segtype = itr.currentSegment(coords);
            switch (segtype) {
                case PathIterator.SEG_MOVETO:
                    pathStart = new Point2D.Float(coords[0], coords[1]); // save start point
                case PathIterator.SEG_LINETO:
                case PathIterator.SEG_QUADTO:
                case PathIterator.SEG_CUBICTO:
                    pathList.add((lastPoint = new Point2D.Float(coords[0], coords[1]))); // TODO store quads/cubes as well as linesSVG
                    break;
                case PathIterator.SEG_CLOSE:
                    closed = true;
//                            if (pathStart != null) {
//                                pathList.add(pathStart);
//                            }
                    break;
                default:
                    log.info("found other element " + segtype);
            }
            itr.next();
        }
        if (closed && lastPoint != null) {
            pathList.remove(lastPoint);
        }
        if (pathList.size() > longestPath) {
            ballPathSVG = pathList;
            longestPath = ballPathSVG.size();
        }
        pathsSVG.add(pathList);
    }

    private void computeTransformsToRetinaCoordinates() {
//      private float rotationDegCCW = getFloat("rotationDegCCW", 0);
//    private float scale = getFloat("scale", 1);
//    private float transXPixels = getFloat("transXPixels", 0);
//    private float transYPixels = getFloat("transYPixels", 0);
        if (boundsSVG == null) {
            log.warning("can't compute transforms - no SVG map data");
            return;
        }
        float s = scale * (float) (chip.getSizeX() / boundsSVG.getWidth());  // we'll scale up to pixels, and flip y while we're at it since drawing starts in UL
        float tx = -(float) boundsSVG.getMinX(), ty = -(float) boundsSVG.getMaxY();
        float cos = (float) Math.cos(rotationDegCCW * Math.PI / 180);
        float sin = (float) Math.sin(rotationDegCCW * Math.PI / 180);
        // affine transform from SVG coords to pixel coords
        float[][] x = {
            {s, 0, tx},
            {0, s, ty},
            {0, 0, 1}
        };
        // now transform according to desired rotation and translation
        float[][] r = {
            {cos, -sin, 0},
            {sin, cos, 0},
            {0, 0, 1}
        };
        float[][] t = {
            {1, 0, transXPixels},
            {0, 1, transYPixels},
            {0, 0, 1}
        };
        // now compute t*r*x so that we first transform to pixel space, then rotate, then translate
        float[][] rx = Matrix.multMatrix(r, x);
        float[][] trx = Matrix.multMatrix(t, rx);

        // now transform all Point2D coordinates
        if (ballPathSVG != null) {
            if (ballPath == null) {
                ballPath = new ArrayList();
            } else {
                ballPath.clear();
            }
            for (Point2D.Float v : ballPathSVG) {
                float[] p = {v.x, v.y, 1};
                float[] pt = Matrix.multMatrix(trx, p);
                Point2D.Float v2 = new Point2D.Float(pt[0], pt[1]);
                ballPath.add(v2);
            }
        }
    }

    public void doLoadMap() {
        final JFileChooser fc = new JFileChooser();
        fc.setCurrentDirectory(getLastFilePrefs());  // defaults to startup runtime folder
        fc.setFileFilter(new FileFilter() {

            @Override
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

            @Override
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
        SVGUniverse svgUniverse = new SVGUniverse();
        SVGDiagram svgDiagram = null;
        svgUniverse.setVerbose(true);
        URI svgURI = svgUniverse.loadSVG(file.toURI().toURL());

        svgDiagram = svgUniverse.getDiagram(svgURI);
        SVGRoot root = svgDiagram.getRoot();
        loadChildren(root.getChildren(null));
        String s = String.format("map has %d holes, %d lines, %d paths, ball path has %d vertices", holesSVG.size(), linesSVG.size(), pathsSVG.size(), ballPathSVG != null ? ballPathSVG.size() : 0);
        log.info(s);
        computeTransformsToRetinaCoordinates();
    }
    int longestPath = Integer.MIN_VALUE;

    private void loadChildren(List children) {
        for (Object o : children) {
            if (o instanceof Circle) {
                Circle c = (Circle) o;
                holesSVG.add((Ellipse2D.Float) c.getShape());
            } else if (o instanceof Line) {
                Line l = (Line) o;
                linesSVG.add((Line2D.Float) l.getShape());
            } else if (o instanceof Polyline) {
                Polyline l = (Polyline) o;
                Shape s = l.getShape();
                if (s instanceof GeneralPath) {
                    GeneralPath path = (GeneralPath) s;
                    addGeneralPath(path);
                }
            } else if (o instanceof Rect) {
                Rect r = (Rect) o;
                outlineSVG = (Rectangle2D.Float) r.getShape();
                boundsSVG = outlineSVG.getBounds2D();
            } else if (o instanceof Path) {
                // only the actual path of the ball should be a path, it should be a connected path
                Path r = (Path) o;
                GeneralPath path = (GeneralPath) r.getShape();
                addGeneralPath(path);
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
        if (!displayMap) {
            return;
        }
        GL gl = drawable.getGL();
        if (!madeList) {
            listnum = gl.glGenLists(1);
            if (listnum == 0) {
                log.warning("cannot create display list to show the map, glGenLists returned 0");
            }
            gl.glNewList(1, GL.GL_COMPILE_AND_EXECUTE);
            {
                gl.glPushMatrix();
                gl.glColor4f(0, 0, .2f, 0.3f);
                gl.glLineWidth(1);
                // scale and translate to match boundsSVG of all shapes
                // drawing is e.g. in inches
                float s = (float) (chip.getSizeX() / boundsSVG.getWidth());  // we'll scale up to pixels, and flip y while we're at it since drawing starts in UL
                gl.glScalef(s, -s, s);
                // now we'll translate so that minx in drawing is at left, and top of drawing is at bottom
                gl.glTranslated(-boundsSVG.getMinX(), -boundsSVG.getMaxY(), 0);
                {
                    // draw outlineSVG
                    gl.glBegin(GL.GL_LINE_LOOP);
                    gl.glVertex2f(outlineSVG.x, outlineSVG.y);
                    gl.glVertex2f(outlineSVG.x + outlineSVG.width, outlineSVG.y);
                    gl.glVertex2f(outlineSVG.x + outlineSVG.width, outlineSVG.y + outlineSVG.height);
                    gl.glVertex2f(outlineSVG.x, outlineSVG.y + outlineSVG.height);
                    gl.glEnd();

                    // draw maze walls
                    gl.glBegin(GL.GL_LINES);
                    for (Line2D.Float l : linesSVG) {
                        gl.glVertex2f(l.x1, l.y1);
                        gl.glVertex2f(l.x2, l.y2);
                    }
                    gl.glEnd();
                    for (ArrayList<Point2D.Float> p : pathsSVG) {
                        if (p == ballPathSVG) {
                            continue;
                        }
                        gl.glBegin(GL.GL_LINE_STRIP);
                        for (Point2D.Float v : p) {
                            gl.glVertex2f(v.x, v.y);
                        }
                        gl.glEnd();
                    }
                }
                // draw holesSVG
                {
                    glu.gluQuadricDrawStyle(holeQuad, GLU.GLU_LINE);
                    gl.glColor4f(.1f, .1f, .1f, .3f);
                    for (Ellipse2D.Float e : holesSVG) { // ellipse has UL corner as x,y location (here LL corner)
                        gl.glPushMatrix();
                        gl.glTranslatef(e.x + e.width / 2, e.y + e.height / 2, 0);
                        glu.gluDisk(holeQuad, 0, e.width / 2, 16, 1);
                        gl.glPopMatrix();

                    }
                }
                // draw path
                {
                    if (ballPathSVG != null) {
                        gl.glColor4f(.1f, .1f, .1f, .3f);
                        gl.glBegin(GL.GL_LINE_STRIP);
                        for (Point2D.Float l : ballPathSVG) {
                            gl.glVertex2f(l.x, l.y);
                        }
                        gl.glEnd();
                    }
                }
                gl.glPopMatrix();
            }
            gl.glEndList();
            chip.getCanvas().checkGLError(gl, glu, "after making list for map");
            madeList = true;
        } else {
            gl.glCallList(listnum);
        }

        // render ball path to test it using retina coordinates
        gl.glColor4f(.1f, .4f, .1f, .3f);
        gl.glLineWidth(3);
        gl.glBegin(GL.GL_LINE_STRIP);
        for (Point2D.Float l : ballPath) {
            gl.glVertex2f(l.x, l.y);
        }
        gl.glEnd();
    }

    @Override
    public void update(Observable o, Object arg) {
        if (o instanceof AEChip) {
            if (arg instanceof String) {
                String s = (String) arg;
                if (s.equals(AEChip.EVENT_SIZEY) || s.equals(AEChip.EVENT_SIZEX)) {
                    computeTransformsToRetinaCoordinates();
                }
            }
        }
    }
}
