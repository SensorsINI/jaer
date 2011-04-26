/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.labyrinth;

import com.kitfox.svg.*;
import com.kitfox.svg.SVGUniverse;
import java.awt.Shape;
import java.awt.geom.*;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
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
    public static final String DEFAULT_MAP = "labyrinth-hardest.svg"; // in source tree
    ArrayList<Ellipse2D.Float> holesSVG = new ArrayList();
    ArrayList<Line2D.Float> linesSVG = new ArrayList();
    ArrayList<ArrayList<Point2D.Float>> pathsSVG = new ArrayList();
    ArrayList<Point2D.Float> ballPathSVG = null;
    Rectangle2D.Float outlineSVG = null;
    Rectangle2D boundsSVG = null;
    // chip space stuff, in retina coordinates
    ArrayList<Point2D.Float> ballPath = new ArrayList();
    ArrayList<Point2D.Float> holes = new ArrayList();
    ArrayList<Float> holeRadii = new ArrayList();
    ArrayList<ArrayList<Point2D.Float>> walls = new ArrayList();
    ArrayList<Point2D.Float> outline = new ArrayList();
    // properties
    private boolean displayMap = getBoolean("displayMap", true);
    private float rotationDegCCW = getFloat("rotationDegCCW", 0);
    private float scale = getFloat("scale", 1);
    private float transXPixels = getFloat("transXPixels", 0);
    private float transYPixels = getFloat("transYPixels", 0);
    private boolean recomputeDisplayList = true;  // to flag annotation to recompute its display list

    public LabyrinthMap(AEChip chip) {
        super(chip);
        setPropertyTooltip("loadMap", "opens file dialog to select SVG file of map");
        File f = getLastFilePrefs();
        try {
            loadMapFromFile(f);
        } catch (Exception ex) {
            log.warning("couldn't load map information from file " + f + ", caught " + ex + "; you are missing the SVG file describing the Labyrinth maze or it is corrupted");
        }
        setPropertyTooltip("displayMap", "Enables map display");  // TODO
        setPropertyTooltip("rotationDegCCW", "rotates the map CCW in degrees");
        setPropertyTooltip("scale", "scales the map");
        setPropertyTooltip("transXPixels", "translates the map; positive to move it up");
        setPropertyTooltip("transYPixels", "translates the map; positive to move to right");
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

    // transforms all coordinates from SVG to retina space. Doesnt' change base SVG data.
    synchronized private void computeTransformsToRetinaCoordinates() {
        if (boundsSVG == null) {
            log.warning("can't compute transforms - no SVG map data");
            return;
        }
        float s = getScale() * (float) (chip.getSizeX() / boundsSVG.getWidth());  // we'll scale up to pixels, and flip y while we're at it since drawing starts in UL
        float tx = -(float) boundsSVG.getMinX(), ty = -(float) boundsSVG.getMaxY(); // this is LL corner of bounding box in Java2d space
        float cos = (float) Math.cos(getRotationDegCCW() * Math.PI / 180);
        float sin = (float) Math.sin(getRotationDegCCW() * Math.PI / 180);

        float[][] trm1={
            {1,0,tx},
            {0,1,ty},
            {0,0,1}
        };

        // affine transform from SVG coords to pixel coords
        float[][] scm = {
            {s, 0, 0},
            {0, -s, 0},
            {0, 0, 1}
        };
        // now transform according to desired rotation and translation
        float[][] rotm = {
            {cos, -sin, 0},
            {sin, cos, 0},
            {0, 0, 1}
        };
        float[][] trm2 = {
            {1, 0, getTransXPixels()},
            {0, 1, getTransYPixels()},
            {0, 0, 1}
        };
        // now compute t*r*x so that we first transform to pixel space, then rotate, then translate
        float[][] m1 = Matrix.multMatrix(scm, trm1);
        float[][] m2 = Matrix.multMatrix(rotm, m1);
        float[][] tsrt = Matrix.multMatrix(trm2, m2);

        // now transform all Point2D coordinates
        if (ballPathSVG != null) {
            ballPath.clear();
            for (Point2D.Float v : ballPathSVG) {
                ballPath.add(transform(tsrt, v));
            }
        }
        holes.clear();
        holeRadii.clear();
        for (Ellipse2D.Float e : holesSVG) {
            Point2D.Float center = new Point2D.Float(e.x + e.width / 2, e.y + e.height / 2);
            holes.add(transform(tsrt, center));
            holeRadii.add(e.height*s/2);
        }
        walls.clear();
        for (ArrayList<Point2D.Float> path : pathsSVG) {
            ArrayList<Point2D.Float> wall = new ArrayList();
            for (Point2D.Float v : path) {
                wall.add(transform(tsrt, v));
            }
            walls.add(wall);
        }

        // outline
        outline.clear();
        Point2D.Float p1 = transform(tsrt, new Point2D.Float((float) outlineSVG.getMinX(), (float) outlineSVG.getMinY()));
        Point2D.Float p2 = transform(tsrt, new Point2D.Float((float) outlineSVG.getMaxX(), (float) outlineSVG.getMaxY()));
        outline.add(transform(tsrt, outlineSVG.x, outlineSVG.y));
        outline.add(transform(tsrt, outlineSVG.x + outlineSVG.width, outlineSVG.y));
        outline.add(transform(tsrt, outlineSVG.x + outlineSVG.width, outlineSVG.y + outlineSVG.height));
        outline.add(transform(tsrt, outlineSVG.x, outlineSVG.y + outlineSVG.height));
        outline.add(transform(tsrt, outlineSVG.x, outlineSVG.y));
        invalidateDisplayList();
    }

    private Point2D.Float transform(float[][] m, Point2D.Float p) {
        float[] v = {p.x, p.y, 1};
        float[] pt = Matrix.multMatrix(m, v);
        return new Point2D.Float(pt[0], pt[1]);
    }

    private Point2D.Float transform(float[][] m, float x, float y) {
        float[] v = {x, y, 1};
        float[] pt = Matrix.multMatrix(m, v);
        return new Point2D.Float(pt[0], pt[1]);
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
                ArrayList<Point2D.Float> pathList = new ArrayList();
                Line2D.Float line = (Line2D.Float) l.getShape();
                pathList.add(new Point2D.Float(line.x1, line.y1));
                pathList.add(new Point2D.Float(line.x2, line.y2));
                pathsSVG.add(pathList);
            } else if (o instanceof Polyline) {
                Polyline l = (Polyline) o;
                Shape s = l.getShape();
                if (s instanceof GeneralPath) {
                    GeneralPath path = (GeneralPath) s;
                    addGeneralPath(path);
                }
            } else if (o instanceof Rect) { // assumes only 1 rect which is outline of map
                Rect r = (Rect) o;
                outlineSVG = (Rectangle2D.Float) r.getShape(); // this returned rect has x,y relative to UL of viewBox in SVG increasing down and to right (in Java2D coordinates)
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
        String pack = this.getClass().getPackage().getName();
        String path = "src" + File.separator + pack.replace(".", File.separator) + File.separator + DEFAULT_MAP;
//        return new File(path);
        return new File(getString("lastFile", path));
    }

    private void putLastFilePrefs(File file) {
        putString("lastFile", file.toString());
    }
    private GLU glu = new GLU();
    private GLUquadric holeQuad = glu.gluNewQuadric();
    int listnum = 0;

    @Override
    synchronized public void annotate(GLAutoDrawable drawable) {

        if (!isDisplayMap()) {
            return;
        }
        GL gl = drawable.getGL();
        if (recomputeDisplayList) {
            if(listnum>0){
                gl.glDeleteLists(listnum, 1);
            }
            listnum = gl.glGenLists(1);
            if (listnum == 0) {
                log.warning("cannot create display list to show the map, glGenLists returned 0");
                return;
            }
            gl.glNewList(1, GL.GL_COMPILE_AND_EXECUTE);
            {
                gl.glPushMatrix();
                gl.glColor4f(0, 0, .2f, 0.3f);
                gl.glLineWidth(1);
                {
                    // draw outline
                    gl.glBegin(GL.GL_LINE_STRIP);
                    for (Point2D.Float p : outline) {
                        gl.glVertex2f(p.x, p.y);
                    }
                    gl.glEnd();

                    // draw maze walls
                    for (ArrayList<Point2D.Float> p : walls) {
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
                    Iterator<Float> it=holeRadii.iterator();
                    for (Point2D.Float e : holes) { // ellipse has UL corner as x,y location (here LL corner)
                        gl.glPushMatrix();
                        gl.glTranslatef(e.x, e.y, 0);
                        glu.gluDisk(holeQuad, 0, it.next(), 16, 1);
                        gl.glPopMatrix();
                    }
                }
                // draw path
                {
                    // render ball path using retina coordinates
                    gl.glColor4f(.1f, .4f, .1f, .3f);
                    gl.glLineWidth(3);
                    gl.glBegin(GL.GL_LINE_STRIP);
                    for (Point2D.Float l : ballPath) {
                        gl.glVertex2f(l.x, l.y);
                    }
                    gl.glEnd();
                }
                gl.glPopMatrix();
            }
            gl.glEndList();
            chip.getCanvas().checkGLError(gl, glu, "after making list for map");
            recomputeDisplayList = false;
        } else {
            gl.glCallList(listnum);
        }


    }

    @Override
    public void update(Observable o, Object arg) {
        if (o instanceof AEChip) {
            if (arg instanceof String) {
                String s = (String) arg;
                if (s.equals(AEChip.EVENT_SIZEY) || s.equals(AEChip.EVENT_SIZEX)) {
                    if (chip.getNumPixels() > 0) {
                        computeTransformsToRetinaCoordinates(); // can only compute transform once the chip sizes are set
                    }
                }
            }
        }
    }

    /**
     * @return the displayMap
     */
    public boolean isDisplayMap() {
        return displayMap;
    }

    /**
     * @param displayMap the displayMap to set
     */
    public void setDisplayMap(boolean displayMap) {
        this.displayMap = displayMap;
        putBoolean("displayMap", displayMap);
    }

    /**
     * @return the rotationDegCCW
     */
    public float getRotationDegCCW() {
        return rotationDegCCW;
    }

    /**
     * @param rotationDegCCW the rotationDegCCW to set
     */
    synchronized public void setRotationDegCCW(float rotationDegCCW) {
        this.rotationDegCCW = rotationDegCCW;
        putFloat("rotationDegCCW", rotationDegCCW);
        computeTransformsToRetinaCoordinates();
    }

    /**
     * @return the scale
     */
    public float getScale() {
        return scale;
    }

    /**
     * @param scale the scale to set
     */
    synchronized public void setScale(float scale) {
        this.scale = scale;
        putFloat("scale", scale);
        computeTransformsToRetinaCoordinates();
    }

    /**
     * @return the transXPixels
     */
    public float getTransXPixels() {
        return transXPixels;
    }

    /**
     * @param transXPixels the transXPixels to set
     */
    synchronized public void setTransXPixels(float transXPixels) {
        this.transXPixels = transXPixels;
        putFloat("transXPixels", transXPixels);
        computeTransformsToRetinaCoordinates();
    }

    /**
     * @return the transYPixels
     */
    public float getTransYPixels() {
        return transYPixels;
    }

    /**
     * @param transYPixels the transYPixels to set
     */
    synchronized public void setTransYPixels(float transYPixels) {
        this.transYPixels = transYPixels;
        putFloat("transYPixels", transYPixels);
        computeTransformsToRetinaCoordinates();
    }

    private void invalidateDisplayList() {
        recomputeDisplayList = true;
    }
}
