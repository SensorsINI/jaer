/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.labyrinth;

import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUquadric;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.Matrix;

import com.kitfox.svg.Circle;
import com.kitfox.svg.Group;
import com.kitfox.svg.Line;
import com.kitfox.svg.Path;
import com.kitfox.svg.Polygon;
import com.kitfox.svg.Polyline;
import com.kitfox.svg.Rect;
import com.kitfox.svg.SVGDiagram;
import com.kitfox.svg.SVGRoot;
import com.kitfox.svg.SVGUniverse;

/**
 *  Loads SVG file describing a board and displays it in the annotate method and provides methods for finding nearest path points
 * on the maze and the bounding box of the maze.
 * 
 * @author Tobi
 */
@Description("Handles SVG maps of Labyrinth game")
public class LabyrinthMap extends EventFilter2D implements FrameAnnotater, Observer {

    // svg stuff
    public static final String DEFAULT_MAP = "labyrinth-hardest.svg"; // in source tree
    ArrayList<Ellipse2D.Float> holesSVG = new ArrayList();
    ArrayList<Line2D.Float> linesSVG = new ArrayList();
    ArrayList<ArrayList<Point2D.Float>> pathsSVG = new ArrayList();
    ArrayList<Point2D.Float> ballPathSVG = new ArrayList();
    Rectangle2D.Float outlineSVG = null;
    Rectangle2D boundsSVG = null;
    // chip space stuff, in retina coordinates
    private LinkedList<PathPoint> ballPath = new LinkedList();
    private ArrayList<Point2D.Float> holes = new ArrayList();
    private ArrayList<Float> holeRadii = new ArrayList();
    private ArrayList<ArrayList<Point2D.Float>> walls = new ArrayList();
    private ArrayList<Point2D.Float> outline = new ArrayList();
    private ClosestPointLookupTable closestPointComputer = new ClosestPointLookupTable();
    private Rectangle2D.Float boundingBox=new Rectangle2D.Float();

    /**
     * @return the boundingBox
     */
    public Rectangle2D.Float getBoundingBox() {
        return boundingBox;
    }

    enum TrackPointType {

        Normal, Start, End
    };

    public class PathPoint extends Point2D.Float {

        public int index = -1;
        public TrackPointType type = TrackPointType.Normal;
        public float fractionToNext=0;

        public PathPoint(float x, float y, int index) {
            super(x, y);
            this.index = index;
        }

        public PathPoint(Point2D.Float p, int index) {
            this(p.x, p.y, index);
        }

        public PathPoint next() {
            if (index == getNumPathVertices() - 1) {
                return loopEnabled ? ballPath.getFirst() : ballPath.getLast();
            } else {
                return ballPath.get(index + 1);
            }
        }
        
        /** Computes a new Point2D some fraction of the way to the next point
         * 
         * @param fraction 0 to give this point, 1 to give next point
         * @return new point in between.
         */
        public Point2D.Float getPointFractionToNext(float fraction){
            PathPoint next=next();
            float b=1-fraction;
            Point2D.Float ret=new Point2D.Float(b*x+fraction*next.x,b*y+fraction*next.y);
            return ret;
        }

        @Override
        public String toString() {
            return super.toString() + " index=" + index + " type=" + type.toString();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof PathPoint) {
                PathPoint p2d = (PathPoint) obj;
                return p2d.index == this.index;
            }
            return super.equals(obj);
        }
    }
    // properties
    private boolean displayMap = getBoolean("displayMap", true);
    private float rotationDegCCW = getFloat("rotationDegCCW", 0);
    private float scale = getFloat("scale", 1);
    private float transXPixels = getFloat("transXPixels", 0);
    private float transYPixels = getFloat("transYPixels", 0);
    protected boolean flipY=getBoolean("flipY", false);
    protected boolean flipX=getBoolean("flipX", false);
    private boolean loopEnabled = getBoolean("loopEnabled", true);
    private boolean recomputeDisplayList = true;  // to flag annotation to recompute its display list

    public LabyrinthMap(AEChip chip) {
        super(chip);
        setPropertyTooltip("loadMap", "opens file dialog to select SVG file of map");
        setPropertyTooltip("clearMap", "clears map data");
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
        setPropertyTooltip("flipY", "flips Y coordinate after translation");
        setPropertyTooltip("loopEnabled", "loops path instead of just finishing");
        chip.addObserver(this);// to get informed about changes to chip size
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        return in;
    }

    @Override
    synchronized public void resetFilter() {
        invalidateDisplayList();
    }

    @Override
    public void initFilter() {
    }

    private void addGeneralPath(GeneralPath path) {
        PathIterator itr = path.getPathIterator(null, 0.1);
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

        float[][] trm1 = {
            {1, 0, tx},
            {0, 1, ty},
            {0, 0, 1}
        };
        

        // affine transform from SVG coords to pixel coords
        float[][] scm = {
            {s, 0, 0},
            {0, -s, 0},
            {0, 0, 1}
        };
        // now transform according to desired rotation and translation
        
        float[][] rott1 = {
            {1, 0, -chip.getSizeX()/2},
            {0, 1, -chip.getSizeY()/2},
            {0, 0, 1}
        };
      float[][] rotm = {
            {cos, -sin, 0},
            {sin, cos, 0},
            {0, 0, 1}
        };
        float[][] rott2 = {
            {1, 0, chip.getSizeX()/2},
            {0, 1, chip.getSizeY()/2},
            {0, 0, 1}
        };
        float[][] trm2 = {
            {1, 0, getTransXPixels()},
            {0, 1, getTransYPixels()},
            {0, 0, 1}
        };
        // flipY
        float[][] flip= {
            {flipX?-1:1, 0, flipX?chip.getSizeX():0F},
            {0, flipY?-1:1, flipY?chip.getSizeY():0},
            {0, 0, 1}
        };
        
        // now compute t*r*x so that we first transform to pixel space, then rotate, then translate
        float[][] m1 = Matrix.multMatrix(scm, trm1);
        float[][] m2a = Matrix.multMatrix(rott1, m1);
        float[][] m2b = Matrix.multMatrix(rotm, m2a);
        float[][] m2c = Matrix.multMatrix(rott2, m2b);
        float[][] m3 = Matrix.multMatrix(trm2, m2c);
        float[][] tsrt = Matrix.multMatrix(flip, m3);

        // now transform all Point2D coordinates
        if (ballPathSVG != null) {
            ballPath.clear();
            int idx = 0;
            for (Point2D.Float v : ballPathSVG) {
                PathPoint p = new PathPoint(transform(tsrt, v), idx);
                if (idx == 0) {
                    p.type = TrackPointType.Start;
                } else if (idx == ballPathSVG.size() - 1) {
                    p.type = TrackPointType.End;
                } else {
                    p.type = TrackPointType.Normal;
                }
                ballPath.add(p);
                idx++;
            }
        }
        holes.clear();
        holeRadii.clear();
        for (Ellipse2D.Float e : holesSVG) {
            Point2D.Float center = new Point2D.Float(e.x + e.width / 2, e.y + e.height / 2);
            holes.add(transform(tsrt, center));
            holeRadii.add(e.height * s / 2);
        }
        walls.clear();
        for (ArrayList<Point2D.Float> path : pathsSVG) {
            if (path == ballPathSVG) {
                continue;
            }
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
        
        float minx=Float.POSITIVE_INFINITY, miny=Float.POSITIVE_INFINITY, maxx=Float.NEGATIVE_INFINITY, maxy=Float.NEGATIVE_INFINITY;
        for(Point2D.Float p:outline){
            if(p.x>maxx) maxx=p.x; else if(p.x<minx) minx=p.x;
            if(p.y>maxy) maxy=p.y; else if(p.y<miny) miny=p.y;
        }
        getBoundingBox().setFrame(minx, miny, maxx-minx, maxy-miny);
        invalidateDisplayList();
        closestPointComputer.init();
        if(chip.getAeViewer()!=null) chip.getAeViewer().interruptViewloop(); // refresh if we are paused

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

    synchronized public void doClearMap() {
        // use to clear any map for pure mouse control on a blank area
        holesSVG.clear();
        pathsSVG.clear();
        ballPathSVG.clear();

        ballPath.clear();
        walls.clear();
        outline.clear();
        holes.clear();
        holeRadii.clear();
        closestPointComputer.init();
        longestPath = Integer.MIN_VALUE;
        invalidateDisplayList();
    }

    synchronized private void loadMapFromFile(File file) throws MalformedURLException {
        log.info("loading map file " + file);
        doClearMap();
        SVGUniverse svgUniverse = new SVGUniverse();
        SVGDiagram svgDiagram = null;
        svgUniverse.setVerbose(false); // set true to see parsing debug info
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
        StringBuilder sb = new StringBuilder("Shapes found:");
        for (Object o : children) {
            if (o instanceof Circle) {
                Circle c = (Circle) o;
                holesSVG.add((Ellipse2D.Float) c.getShape());
                sb.append("\n Circle ").append(c);

            } else if (o instanceof Line) {
                Line l = (Line) o;
                ArrayList<Point2D.Float> pathList = new ArrayList();
                Line2D.Float line = (Line2D.Float) l.getShape();
                pathList.add(new Point2D.Float(line.x1, line.y1));
                pathList.add(new Point2D.Float(line.x2, line.y2));
                pathsSVG.add(pathList);
                sb.append("\n Line ").append(l);
            } else if (o instanceof Polyline) {
                Polyline l = (Polyline) o;
                Shape s = l.getShape();
                if (s instanceof GeneralPath) {
                    GeneralPath path = (GeneralPath) s;
                    addGeneralPath(path);
                }
                sb.append("\n PolyLine ").append(l);
            } else if (o instanceof Rect) { // assumes only 1 rect which is outline of map
                Rect r = (Rect) o;
                outlineSVG = (Rectangle2D.Float) r.getShape(); // this returned rect has x,y relative to UL of viewBox in SVG increasing down and to right (in Java2D coordinates)
                boundsSVG = outlineSVG.getBounds2D();
                sb.append("\n Rect ").append(r);
            } else if (o instanceof Path) {
                // only the actual path of the ball should be a path, it should be a connected path
                Path r = (Path) o;
                GeneralPath path = (GeneralPath) r.getShape();
                addGeneralPath(path);
                sb.append("\n Path ").append(r);

            } else if (o instanceof Polygon) {
                // only the actual path of the ball should be a path, it should be a connected path
                Polygon r = (Polygon) o;
                GeneralPath path = (GeneralPath) r.getShape();
                addGeneralPath(path);
                sb.append("\n Polygon ").append(r);

            } else if (o instanceof List) {
                sb.append("\n List ").append(o);
                loadChildren((List) o);
            } else if (o instanceof Group) {
                Group g = (Group) o;
                sb.append("\n Group ").append(g);

                List l = g.getChildren(null);
                loadChildren(l);
            }
        }
        log.info(sb.toString());
    }

    private File getLastFilePrefs() {
        String pack = this.getClass().getPackage().getName();
        String path = "src" + File.separator + pack.replace(".", File.separator) + File.separator + DEFAULT_MAP;
        return new File(getString("lastFile", path));
    }

    private void putLastFilePrefs(File file) {
        putString("lastFile", file.toString());
    }
    private GLU glu = null;
    private GLUquadric holeQuad = null;
    int listnum = 0;

    @Override
    synchronized public void annotate(GLAutoDrawable drawable) {

        if(glu==null) glu = new GLU();

        if(holeQuad==null) holeQuad = glu.gluNewQuadric();
 
        if (!isDisplayMap()) {
            return;
        }
        GL2 gl = drawable.getGL().getGL2();
        if (recomputeDisplayList) {
            if (listnum > 0) {
                gl.glDeleteLists(listnum, 1);
            }
            listnum = gl.glGenLists(1);
            if (listnum == 0) {
                log.warning("cannot create display list to show the map, glGenLists returned 0");
                return;
            }
            gl.glNewList(listnum, GL2.GL_COMPILE_AND_EXECUTE);
            {
                gl.glColor4f(0, 0, .5f, 0.3f);
                gl.glLineWidth(4);
                {
                    // draw outline
                    gl.glBegin(GL2.GL_LINE_STRIP);
                    for (Point2D.Float p : outline) {
                        gl.glVertex2f(p.x, p.y);
                    }
                    gl.glEnd();

                    // draw maze walls
                    for (ArrayList<Point2D.Float> p : walls) {

                        gl.glBegin(GL2.GL_LINE_STRIP);
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
                    Iterator<Float> it = holeRadii.iterator();
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
                    gl.glBegin(GL2.GL_LINE_STRIP);
                    for (Point2D.Float l : ballPath) {
                        gl.glVertex2f(l.x, l.y);
                    }
                    gl.glEnd();
                    gl.glPointSize(9);
                    gl.glBegin(GL.GL_POINTS);
                    for (Point2D.Float l : ballPath) {
                        gl.glVertex2f(l.x, l.y);
                    }
                    gl.glEnd();
                }
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
    synchronized public void setDisplayMap(boolean displayMap) {
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

    /**
     * @return the flipY
     */
    public boolean isFlipY() {
        return flipY;
    }

    /**
     * @param flipY the flipY to set
     */
    public void setFlipY(boolean flipY) {
        this.flipY = flipY;
        putBoolean("flipY", flipY);
         computeTransformsToRetinaCoordinates();
   }

    /**
     * @return the flipX
     */
    public boolean isFlipX() {
        return flipX;
    }

    /**
     * @param flipX the flipX to set
     */
    public void setFlipX(boolean flipX) {
        this.flipX = flipX;
        putBoolean("flipX",flipX);
         computeTransformsToRetinaCoordinates();
    }

    private void invalidateDisplayList() {
        recomputeDisplayList = true;
    }

    /** Computes nearest path point using lookup from table */
    class ClosestPointLookupTable {

        private int size = 64;
        private int[] map = new int[size * size];
        int sx, sy;
        float xPixPerUnit, yPixPerUnit = 1;
        Rectangle2D.Float bounds = new Rectangle2D.Float(); // overall bounds of entire labyrinth map
        private boolean displayClosestPointMap = false;

        public ClosestPointLookupTable() {
            init();
        }

        final void init() {
//            if(ballPath==null || ballPath.isEmpty()) 
            computeBounds();
            xPixPerUnit = (float) bounds.getWidth() / size;
            yPixPerUnit = (float) bounds.getHeight() / size;
            Arrays.fill(map, -1);
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    // for each grid entry, locate point at center of grid point, then find nearest path vertex and put in map
                    Point2D.Float pos = new Point2D.Float(x * xPixPerUnit + xPixPerUnit / 2 + (float) bounds.getX(), y * yPixPerUnit + yPixPerUnit / 2 + (float) bounds.getY());
                    int idx = findClosestIndex(pos, Float.POSITIVE_INFINITY, false); // TODO make tolerance larger
                    setMapEntry(idx, x, y);
                }
            }
//            if (displayClosestPointMap) {
//                if (closestPointFrame == null) {
//                    closestPointFrame = new JFrame("Closest Point Map");
//                    closestPointFrame.setPreferredSize(new Dimension(200, 200));
//
//                    closestPointImage = ImageDisplay.createOpenGLCanvas();
//                    closestPointImage.setFontSize(10);
//                    closestPointImage.setSize(size, size);
//                    closestPointImage.setxLabel("x");
//                    closestPointImage.setyLabel("y");
//
//                    closestPointImage.addGLEventListener(new GLEventListener() {
//
//                        @Override
//                        public void init(GLAutoDrawable drawable) {
//                        }
//
//                        @Override
//                        public void display(GLAutoDrawable drawable) {
//                            closestPointImage.checkPixmapAllocation();
//                            for (int x = 0; x < size; x++) {
//                                for (int y = 0; y < size; y++) {
//                                    int point = getMapEntry(x, y);
//                                    if (point == -1) {
//                                        closestPointImage.setPixmapGray(x, y, 0);
//                                    } else {
//                                        Color c = Color.getHSBColor((float) point / getNumPoints(), .5f, .5f);
//                                        float[] rgb = c.getColorComponents(null);
//                                        closestPointImage.setPixmapRGB(x, y, rgb);
//                                        closestPointImage.drawCenteredString(x, y, Integer.toString(point));
//                                    }
//                                }
//                            }
////                            GL2 gl=drawable.getGL().getGL2();
////                            gl.glPushMatrix();
////                            gl.glLineWidth(.5f);
////                            gl.glColor3f(0,0,1);
////                            gl.glBegin(GL2.GL_LINE_LOOP);
////                            for(Point2D.Float p:trackPoints){
////                                gl.glVertex2f(p.x, p.y);
////                            }
////                            gl.glEnd();
////                            gl.glBegin
////                            gl.glPopMatrix();  // TODO needs coordinate transform to ImageDisplay pixels to draw track
//                        }
//
//                        @Override
//                        public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
//                        }
//
//                        @Override
//                        public void displayChanged(GLAutoDrawable drawable, boolean modeChanged, boolean deviceChanged) {
//                        }
//                    });
//
//
//                    closestPointFrame.getContentPane().add(closestPointImage, BorderLayout.CENTER);
//                    closestPointFrame.setVisible(true);
//
//                }
//                closestPointImage.repaint();
//            }
        }

        int getMapEntry(int x, int y) {
            final int idx = x + size * y;
            if (idx < 0 || idx >= map.length) {
                return -1;
            }
            return map[idx];
        }

        void setMapEntry(int val, int x, int y) {
            final int idx = x + size * y;
            if (idx < 0 || idx > map.length) {
                return;
            }
            map[x + size * y] = val;
        }

        int findPoint(Point2D.Float pos) {
            int x = (int) (size * (pos.x - bounds.getX() /*- xPixPerUnit / 2*/) / bounds.getWidth());
            int y = (int) (size * (pos.y - bounds.getY() /*- yPixPerUnit / 2*/) / bounds.getHeight());
            return getMapEntry(x, y);
        }

        int findPoint(double x, double y) {
            int xx = (int) (size * (x - bounds.getX() /*- xPixPerUnit / 2*/) / bounds.getWidth());
            int yy = (int) (size * (y - bounds.getY() /*- yPixPerUnit / 2*/) / bounds.getHeight());
            return getMapEntry(xx, yy);
        }

        private void computeBounds() {

            // bounds are computed from outline polygon

            final float extraFraction = .25f;
            if (outline == null) {
                log.warning("no outline, can't compute bounds of ClosestPointLookupTable");
                return;
            }
            float minx = Float.MAX_VALUE, miny = Float.MAX_VALUE, maxx = Float.MIN_VALUE, maxy = Float.MIN_VALUE;
            for (Point2D.Float p : outline) {
                if (p.x < minx) {
                    minx = p.x;
                }
                if (p.y < miny) {
                    miny = p.y;
                }
                if (p.x > maxx) {
                    maxx = p.x;
                }
                if (p.y > maxy) {
                    maxy = p.y;
                }
            }
            final float w = maxx - minx, h = maxy - miny;
            bounds.setRect(minx - w * extraFraction, miny - h * extraFraction, w * (1 + 2 * extraFraction), h * (1 + 2 * extraFraction));
        }

        /**
         * @return the displayClosestPointMap
         */
        public boolean isDisplayClosestPointMap() {
            return displayClosestPointMap;
        }

        /**
         * @param displayClosestPointMap the displayClosestPointMap to set
         */
        public void setDisplayClosestPointMap(boolean displayClosestPointMap) {
            this.displayClosestPointMap = displayClosestPointMap;
        }
    }
    private int lastFindIdx = -1;

    /** Find the closest point on the path with option for local minimum distance or global minimum distance search.
     * @param pos Point in x,y Cartesian space for which to search closest path point.
     * @param maxDist the maximum allowed distance.
     * @param fastLocalSearchEnabled if true, the search uses the ClosestPointLookupTable lookup and maxDist is ignored.
     * @return Index of closest point on path or -1 if no path point is <= maxDist from pos or is not found in the ClosestPointLookupTable.
     */
    synchronized public int findClosestIndex(Point2D pos, float maxDist, boolean fastLocalSearchEnabled) {
        if (pos == null) {
            return -1;
        }
        int n = ballPath.size();
        if (n == 0) {
            return -1;
        }

        if (fastLocalSearchEnabled) {
            lastFindIdx = closestPointComputer.findPoint(pos.getX(), pos.getY());
            return lastFindIdx;
        } else {
            int idx = 0, closestIdx = -1;
            float closestDist = Float.MAX_VALUE;
            for (Point2D.Float p : ballPath) {
                float d = (float) p.distance(pos);
                if (d <= maxDist) {
                    if (d < closestDist) {
                        closestDist = d;
                        closestIdx = idx;
                    }
                }
                idx++;
            }
            return closestIdx;
        }
    }

//    public Point2D.Float find
    /** returns the path index, or -1 if there is no ball or is too far away from the path.
     * 
     * @return nearest path index
     */
    public int findNearestPathIndex(Point2D point) {
        return findClosestIndex(point, 15, true);
    }

    public int getNumPathVertices() {
        return getBallPath().size();
    }

    public PathPoint findNearestPathPoint(Point2D point) {
        if (point == null) {
            return null;
        }
        int ind = findNearestPathIndex(point);
        if (ind == -1) {
            return null;
        }
        return getBallPath().get(ind);
    }

    public PathPoint findNextPathPoint(Point2D point) {
        if (point == null) {
            return null;
        }
        int ind = findNearestPathIndex(point);
        if (ind == -1) {
            return null;
        }
        if (!isLoopEnabled()) {
            if (ind == getNumPathVertices() - 1) {
                return getBallPath().get(ind);
            }
        } else {
            if (ind == getNumPathVertices() - 1) {
                return getBallPath().get(0);
            }
        }
        return getBallPath().get(ind + 1);
    }

    /**
     * @return the ballPath, a list of Point2D starting at the start point and ending at the end point.
     */
    public LinkedList<PathPoint> getBallPath() {
        return ballPath;
    }

    /**
     * @return the holes
     */
    public ArrayList<Point2D.Float> getHoles() {
        return holes;
    }

    /**
     * @return the holeRadii for the holes
     */
    public ArrayList<Float> getHoleRadii() {
        return holeRadii;
    }

    /**
     * @return the walls
     */
    public ArrayList<ArrayList<Point2D.Float>> getWalls() {
        return walls;
    }

    /**
     * @return the outline of the entire map.
     */
    public ArrayList<Point2D.Float> getOutline() {
        return outline;
    }

    /**
     * @return the loopEnabled
     */
    public boolean isLoopEnabled() {
        return loopEnabled;
    }

    /**
     * @param loopEnabled the loopEnabled to set
     */
    public void setLoopEnabled(boolean loopEnabled) {
        this.loopEnabled = loopEnabled;
        putBoolean("loopEnabled", loopEnabled);
    }
}
