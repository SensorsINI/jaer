/*
 * ChipRendererDisplayMethod.java
 *
 * Created on May 4, 2006, 9:07 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 * Copyright May 4, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */
package net.sf.jaer.graphics;

import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.prefs.Preferences;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL2ES1;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLException;
import com.jogamp.opengl.fixedfunc.GLLightingFunc;
import com.jogamp.opengl.fixedfunc.GLMatrixFunc;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.util.awt.TextRenderer;
import com.jogamp.opengl.util.gl2.GLUT;
import eu.seebetter.ini.chips.DavisChip;
import eu.seebetter.ini.chips.davis.DavisConfig;

import eu.seebetter.ini.chips.davis.DavisDisplayConfigInterface;
import java.awt.Font;
import java.beans.PropertyChangeEvent;
import java.util.LinkedList;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JSeparator;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.util.EngineeringFormat;

/**
 * Displays events in space time using a rolling view where old events are
 * erased and new ones are added to the front. In contrast with
 * SpaceTimeEventDisplayMethod, this method smoothly rolls the events through
 * the display. It uses a vertex and fragment shader program to accelerate the
 * rendering.
 *
 * @author tobi, nicolai waniek capocaccia 2015. See also
 * https://github.com/rochus/ebglvis
 */
@Description("Displays events in space time using a rolling view where old events are\n"
        + " * erased and new ones are added to the front.")
@DevelopmentStatus(DevelopmentStatus.Status.InDevelopment)
public class SpaceTimeRollingEventDisplayMethod extends DisplayMethod implements DisplayMethod3D {

    private EngineeringFormat engFmt = new EngineeringFormat();
    final Preferences prefs;

    private DavisDisplayConfigInterface config;
//    private boolean displayEvents = true;
//    private boolean displayFrames = true;
    private boolean spikeListCreated = false;
    private GLUT glut = null;
    private GLU glu = null;
    private boolean shadersInstalled = false;
    private int shaderprogram;
    private int vertexShader;
    private int fragmentShader;
    private int vao;
    private int vbo;
    final int v_vert = 0;
    private final int BUF_INITIAL_SIZE_EVENTS = 100000;
    private int sx, sy, smax;
    /**
     * time factor to multiply timestamps relative to most recent one for z
     * location of event (or frame)
     */
    private float tfac;
//    private int timeSlice = 0;
    private final FloatBuffer mv = FloatBuffer.allocate(16);
    private final FloatBuffer proj = FloatBuffer.allocate(16);
    private int idMv, idProj, idt0, idt1, idPointSize;
    private ArrayList<BasicEvent> eventList = null, eventListTmp = null;
    private ByteBuffer eventVertexBuffer;
    /**
     * total time in 3d window in us
     */
    private int timeWindowUs = 100000, t0;
    private static final int EVENT_SIZE_BYTES = (Float.SIZE / 8) * 3;// size of event in shader ByteBuffer
    private int axesDisplayListId = -1;
    private volatile boolean regenerateAxesDisplayList = true;
    private float timeAspectRatio; // depth of 3d cube compared to max of x and y chip dimension
    private float pointSize = 4f;

    private JMenu displayMenu = null;

    private boolean additiveColorEnabled;
    private boolean largePointSizeEnabled;

    private boolean displayDvsEvents = true;
    private boolean displayApsFrames = true;
//    private boolean displayAnnotation = false;

    final private FramesInTimeWindow apsFramesInTimeWindow = new FramesInTimeWindow(); // linked list of frames in time window
    final private FramesInTimeWindow dvsFramesInTimeWindow = new FramesInTimeWindow(); // linked list of frames in time window
    private float framesAlpha;
    private boolean drawFramesOnOwnAxes;
    private float frameEventSpacing;

    private boolean displayDvsFrames;

    private ChipCanvas.Zoom zoom = null;
    private ChipCanvas.Zoom oldZoom = null;

    private TextRenderer textRenderer = null;

    /**
     * Creates a new instance of SpaceTimeEventDisplayMethod
     *
     * @param chipCanvas
     */
    public SpaceTimeRollingEventDisplayMethod(final ChipCanvas chipCanvas) {
        super(chipCanvas);
        prefs = chipCanvas.prefs;
        drawFramesOnOwnAxes = prefs.getBoolean("drawFramesOnOwnAxes", false);
        frameEventSpacing = prefs.getFloat("frameEventSpacing", 1.2f);
        additiveColorEnabled = prefs.getBoolean("additiveColorEnabled", false);
        largePointSizeEnabled = prefs.getBoolean("largePointSizeEnabled", false);
        timeAspectRatio = prefs.getFloat("timeAspectRatio", 4); // depth of 3d cube compared to max of x and y chip dimension
        displayDvsFrames = prefs.getBoolean("displayDvsFrames", false);
        framesAlpha = prefs.getFloat("framesAlpha", .5f);
        zoom = chipCanvas.createZoom();
    }

    private void installShaders(GL2 gl) throws IOException {
        if (shadersInstalled) {
            return;
        }
        gl.glEnable(GL3.GL_PROGRAM_POINT_SIZE);
        checkEventVertexBufferAllocation(BUF_INITIAL_SIZE_EVENTS);

        shadersInstalled = true;
        IntBuffer b = IntBuffer.allocate(8); // buffer to hold return values
        shaderprogram = gl.glCreateProgram();
        vertexShader = gl.glCreateShader(GL2ES2.GL_VERTEX_SHADER);
        fragmentShader = gl.glCreateShader(GL2ES2.GL_FRAGMENT_SHADER);
        checkGLError(gl, "creating shaders and shader program");

        String vsrc = readFromStream(SpaceTimeRollingEventDisplayMethod.class
                .getResourceAsStream("SpaceTimeRollingEventDisplayMethod_Vertex.glsl"));
        gl.glShaderSource(vertexShader, 1, new String[]{vsrc}, (int[]) null, 0);
        gl.glCompileShader(vertexShader);
        b.clear();
        gl.glGetShaderiv(vertexShader, GL2ES2.GL_COMPILE_STATUS, b);
        if (b.get(0) != GL.GL_TRUE) {
            log.warning("error compiling vertex shader");
            printShaderLog(gl);
        }
        checkGLError(gl, "compiling vertex shader");

        String fsrc = readFromStream(SpaceTimeRollingEventDisplayMethod.class
                .getResourceAsStream("SpaceTimeRollingEventDisplayMethod_Fragment.glsl"));
        gl.glShaderSource(fragmentShader, 1, new String[]{fsrc}, (int[]) null, 0);
        gl.glCompileShader(fragmentShader);
        b.clear();
        gl.glGetShaderiv(fragmentShader, GL2ES2.GL_COMPILE_STATUS, b);
        if (b.get(0) != GL.GL_TRUE) {
            log.warning("error compiling fragment shader");
            printShaderLog(gl);
        }
        checkGLError(gl, "compiling fragment shader");

        gl.glAttachShader(shaderprogram, vertexShader);
        gl.glAttachShader(shaderprogram, fragmentShader);

        gl.glLinkProgram(shaderprogram);
        b.clear();
//        gl.glGetShaderiv(shaderprogram, GL2ES2.GL_COMPILE_STATUS, b);
//        if (b.get(0) != GL.GL_TRUE) {
//            log.warning("error linking shader program");
//            printShaderLog(gl);
//        }

        checkGLError(gl, "linking shader program");
        b.clear();
        gl.glGenVertexArrays(1, b);
        vao = b.get(0);
        gl.glBindVertexArray(vao);
        b.clear();
        gl.glGenBuffers(1, b);
        vbo = b.get(0);
        checkGLError(gl, "setting up vertex array and vertex buffer");

        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
//        gl.glBindAttribLocation(shaderprogram, polarity_vert, "polarity"); // symbolic names in vertex and fragment shaders
        gl.glBindAttribLocation(shaderprogram, v_vert, "v");
//        gl.glBindAttribLocation(shaderprogram, polarity_frag, "frag_polarity");
        checkGLError(gl, "binding shader attributes");

        gl.glVertexAttribPointer(v_vert, 3, GL.GL_FLOAT, false, EVENT_SIZE_BYTES, 0);
//        gl.glVertexAttribPointer(polarity_vert, 1, GL.GL_FLOAT, false, EVENT_SIZE_BYTES, 3);
        checkGLError(gl, "setting vertex attribute pointers");
        idMv = gl.glGetUniformLocation(shaderprogram, "mv");
        idProj = gl.glGetUniformLocation(shaderprogram, "proj");
        idt0 = gl.glGetUniformLocation(shaderprogram, "t0");
        idt1 = gl.glGetUniformLocation(shaderprogram, "t1");
        idPointSize = gl.glGetUniformLocation(shaderprogram, "pointSize");
        if ((idMv < 0) || (idProj < 0) || (idt0 < 0) || (idt1 < 0) || (idPointSize < 0)) {
            throw new RuntimeException("cannot locate uniform variable idMv, idProj, idt0, idt1, or idPointSize in shader program");
        }
        checkGLError(gl, "getting IDs for uniform modelview and projection matrices in shaders");
    }

    private void checkEventVertexBufferAllocation(int sizeEvents) {
        if ((eventVertexBuffer == null) || (eventVertexBuffer.capacity() <= (sizeEvents * EVENT_SIZE_BYTES))) {
            eventVertexBuffer = ByteBuffer.allocateDirect(sizeEvents * EVENT_SIZE_BYTES);
            eventVertexBuffer.order(ByteOrder.LITTLE_ENDIAN);
        }
    }

    private void checkEventListAllocation(int sizeEvents) {
        if (eventList == null) {
            eventList = new ArrayList(sizeEvents);
        }
    }

    private EventPacket lastPacketDisplayed = null;
    private int previousLasttimestamp = 0;

    @Override
    public void display(final GLAutoDrawable drawable) {
        if (!(chip instanceof AEChip)) {
            throw new RuntimeException("Can only render AEChip outputs: chip=" + chip);
        }
        final AEChip chip = (AEChip) getChipCanvas().getChip();
        if (glut == null) {
            glut = new GLUT();
        }
        final GL2 gl = drawable.getGL().getGL2();
        if (gl == null) {
            log.warning("null GL context - not displaying");
            return;
        }
        try {
            installShaders(gl);
        } catch (IOException ex) {
            log.warning("could not load shaders: " + ex.toString());
            return;
        }

        // render events
        final EventPacket packet = (EventPacket) chip.getLastData();
        if (packet == null) {
            log.warning("null packet to render");
            return;
        }

        if (packet.isEmpty()) {
            return;
        }
        boolean dirty = false;
        if (packet.getLastTimestamp() != previousLasttimestamp) {
            dirty = true;
        }
        previousLasttimestamp = packet.getLastTimestamp();
        if (dirty) {
            final int n = packet.getSize();
            if (n == 0) {
                return;
            }
//            final int t0ThisPacket = packet.getFirstTimestamp();
            final int t1 = packet.getLastTimestamp();
//        final int dtThisPacket = t1 - t0ThisPacket + 1;
            // the time that is displayed in rolling window is some multiple of either current frame duration (for live playback) or timeslice (for recorded playback)
            int timeScale = ((AEChipRenderer) getRenderer()).getFadingOrSlidingFrames(); // use color scale to determine multiple, up and down arrows set it then
            int newTimeWindowUs, frameDurationUs = 100000;
            if (chip.getAeViewer().getPlayMode() == AEViewer.PlayMode.LIVE) {
                frameDurationUs = (int) (1e6f / chip.getAeViewer().getFrameRater().getDesiredFPS());
            } else if (chip.getAeViewer().getPlayMode() == AEViewer.PlayMode.PLAYBACK) {
                frameDurationUs = chip.getAeViewer().getAePlayer().getTimesliceUs();
            }
            newTimeWindowUs = (int) (frameDurationUs * Math.pow(2, (timeScale - 1) / 4f));
            if (newTimeWindowUs < 10000) {
                newTimeWindowUs = 10000; // tobi - don't let time get too short for window, minimum 10ms
            }
            if (newTimeWindowUs != timeWindowUs) {
                regenerateAxesDisplayList = true;
                eventVertexBuffer.clear();
                if (eventList != null) {
                    eventList.clear();
                }
                apsFramesInTimeWindow.clear();
                dvsFramesInTimeWindow.clear();
            }
            timeWindowUs = newTimeWindowUs;
            t0 = t1 - timeWindowUs;

            sx = chip.getSizeX();
            sy = chip.getSizeY();
            smax = chip.getMaxSize();
            tfac = (float) (smax * getTimeAspectRatio()) / timeWindowUs;

            pruneOldEventsAndFrames(t0, t1);
            checkEventListAllocation((eventList != null ? eventList.size() : 0) + packet.getSize());
            addEventsToEventList(packet);
            checkEventVertexBufferAllocation(eventList.size());
            eventVertexBuffer.clear(); // sets pos=0 and limit=capacity // TODO should not really clear, rather should erase old events
            for (BasicEvent ev : eventList) {
                if ((ev.timestamp < t0) || (ev.timestamp > t1)) {
                    continue; // don't render events outside of box, no matter how they get there
                }
                eventVertexBuffer.putFloat(ev.x);
                eventVertexBuffer.putFloat(ev.y);
                final float z = tfac * (ev.timestamp - t1);
                eventVertexBuffer.putFloat(z); // negative z
            }
            eventVertexBuffer.flip(); // get ready for reading by setting limit=pos and then pos=0
            checkGLError(gl, "set uniform t0 and t1");
        }
        if (displayDvsFrames) {
            DavisRenderer renderer = (DavisRenderer) (chip.getRenderer());
            dvsFramesInTimeWindow.add(renderer.getDvsEventsMap(), renderer.getPacket().getLastTimestamp());
            log.log(Level.FINE, "New DVS frame with timestamp {0}", renderer.getPacket().getLastTimestamp());
        }
        renderEventsAndFrames(gl, drawable, eventVertexBuffer, eventVertexBuffer.limit(), 1e-6f * timeWindowUs, smax * getTimeAspectRatio());
        displayStatusChangeText(drawable);
    }

    private void addEventsToEventList(final EventPacket<BasicEvent> packet) {
        for (BasicEvent e : packet) {
//            BasicEvent e = (BasicEvent) o;
            if (e.isSpecial() || e.isFilteredOut()) {
                continue;
            }
            BasicEvent ne = new BasicEvent();
            ne.copyFrom(e);
            eventList.add(ne); // must do this unfortunately because otherwise the original event object in this list will be reused for a later packet
        }
    }

    /**
     * Prunes out events and frames that are outside the time window
     *
     * @param t0 the oldest time (<t1) at end of time window, furthest from us
     * if the oldest data is rendered in back @param t1 the youngest (latest,
     * >t0, time) at start of time window, closest to us if the new data is
     * rendered in front
     */
    private void pruneOldEventsAndFrames(final int t0, final int t1) {
//        log.finer(String.format("Pruning events and frames outside window t0=%,d, t1=%,d (t1-t0=%,d)", t0, t1, (t1 - t0)));
        if (eventList == null) {
            return;
        }
        if (eventListTmp == null) {
            eventListTmp = new ArrayList(eventList.size());
        } else {
            eventListTmp.clear();
        }

        for (BasicEvent ev : eventList) {
            if ((ev.timestamp >= t0) || (ev.timestamp < t1)) {
                eventListTmp.add(ev);
            }
        }
        eventList.clear();
        ArrayList<BasicEvent> tmp = eventList;
        eventList = eventListTmp;
        eventListTmp = tmp;

        apsFramesInTimeWindow.removeFramesOlderThan(t0);
        dvsFramesInTimeWindow.removeFramesOlderThan(t0);

    }

    /**
     * Draws DVS events and APS frames (or maybe DVS constant count frames if
     * isShowDvsConstantCountFrames()). Depends on displayEvents and
     * displayFrames and the various option parameters
     *
     * @param gl the OpenGL context
     * @param drawable the drawable surface
     * @param buffer the event vertex buffer
     * @param nEvents The number of events in the buffer
     * @param dtS the complete time window in seconds
     * @param zmax the scale of max time in past in units of pixels of array
     */
    synchronized void renderEventsAndFrames(GL2 gl, GLAutoDrawable drawable, ByteBuffer buffer, int nEvents, float dtS, float zmax) {

        if (chip.getRenderer() instanceof DavisRenderer) {
            final DavisRenderer frameRenderer = (DavisRenderer) chip.getRenderer();
            displayApsFrames = frameRenderer.isDisplayFrames();
            displayDvsEvents = frameRenderer.isDisplayEvents();
//            displayAnnotation = frameRenderer.isDisplayAnnotation();
            if (!displayApsFrames && !displayDvsEvents && !displayDvsFrames) {
                log.warning("Both frame types and event display off, enabling events display so something shows");
                frameRenderer.setDisplayEvents(true);
            }
        }
        gl.glDepthMask(true);
        gl.glDepthFunc(GL.GL_GEQUAL);
        gl.glEnable(GL.GL_DEPTH_TEST);
        // axes
        gl.glLineWidth(1f);
        if (glu == null) {
            glu = new GLU();
        }

        final float modelScale = 1f / 2; // everything is drawn at this scale
        maybeRegenerateAxesDisplayList(gl, zmax, modelScale, dtS);
        textRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 24), true, true);

//        gl.glMatrixMode(GLMatrixFunc.GL_TEXTURE_MATRIX);
//        gl.glPushMatrix();
        gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
        gl.glLoadIdentity();
        gl.glPushMatrix();
        ChipCanvas.Zoom.ClipArea clip = getChipCanvas().getClipArea(); // get the clip computed by fancy algorithm in chipcanvas that properly makes ortho clips to maintain pixel aspect ratio and put blank space or left/right or top/bottom depending on chip aspect ratio and window aspect ratio

//        gl.glRotatef(15, 1, 1, 0); // rotate viewpoint by angle deg around the y axis
        // determine the viewable area (that is not clipped to black).
        // this is not the view project!  This is the model projection. See later for viewport setting for where we look from.
//        gl.glOrtho(clip.left, clip.right, clip.bottom, clip.top, -zmax * 4, zmax * 4);
        gl.glFrustumf(clip.left, clip.right, clip.bottom, clip.top, zmax * 1.7f, zmax * .1f); // the z params are the far and near clips

        // go to the end, -zmax
        gl.glTranslatef(0, 0, -1 * zmax);

        // rotate according to mouse
        gl.glRotatef(-getChipCanvas().getAnglex(), 1, 0, 0); // rotate viewpoint by angle deg around the x axis
        gl.glRotatef(-getChipCanvas().getAngley(), 0, 1, 0); // rotate viewpoint by angle deg around the y axis
        gl.glTranslatef(getChipCanvas().getOrigin3dx(), getChipCanvas().getOrigin3dy(), 0);

        // Now back again to where we started
        gl.glTranslatef(0, 0, 1 * zmax);

//        gl.glTranslatef(sx/2, sy/2, zmax);
//        glu.gluPerspective(33, (float)drawable.getSurfaceWidth()/drawable.getSurfaceHeight(), .1, zmax*9);
//        gl.glTranslatef(-sx/2, -sy/2, -zmax);
//        glu.gluPerspective(30, (float)sx/sy, .1, timeWindowUs*1.1f);
//        gl.glFrustumf(0,sx, 0, sy, .1f, timeWindowUs*20);
//        gl.glFrustumf(clip.left, clip.right, clip.bottom, clip.top, .1f, timeWindowUs * 10);
//        gl.glTranslatef(sx, sy, 1);
        checkGLError(gl, "setting projection");
        gl.glDisable(GLLightingFunc.GL_LIGHTING);
        gl.glShadeModel(GLLightingFunc.GL_SMOOTH);
        gl.glDisable(GL.GL_BLEND);
        gl.glEnable(GL2ES1.GL_POINT_SMOOTH);
        gl.glEnable(GL.GL_BLEND);
        if (isAdditiveColorEnabled()) {
            gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE);
        } else {
            gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
        }
        gl.glBlendEquation(GL.GL_FUNC_ADD);
        checkGLError(gl, "setting blend function");

        gl.glClearColor(0, 0, 0, 1);
        gl.glClearDepthf(0);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);

        // act on view matrix (not projection). The view can change perspective
        gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
        gl.glLoadIdentity();
        gl.glTranslatef(0, 0, -1.0f * zmax); // go to -zmax, which is zmax away from the volume that is from 0 to zmax (zmax is positive)
        gl.glScalef(modelScale, modelScale, modelScale);
        gl.glCallList(axesDisplayListId);

        if (displayDvsEvents) {
//        getChipCanvas().applyProjection(gl, drawable);
            // draw points using shaders
            gl.glUseProgram(shaderprogram);
            gl.glValidateProgram(shaderprogram);
//        pmvMatrix.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
//        pmvMatrix.glLoadIdentity();
//        pmvMatrix.glOrthof(-10, sx / zoom + 10, -10, sy / zoom + 10, 10, -10); // clip area has same aspect ratio as screen!
//        pmvMatrix.glRotatef(getChipCanvas().getAngley(), 0, 1, 0); // rotate viewpoint by angle deg around the y axis
//        pmvMatrix.glRotatef(getChipCanvas().getAnglex(), 1, 0, 0); // rotate viewpoint by angle deg around the x axis
//        pmvMatrix.glTranslatef(getChipCanvas().getOrigin3dx(), getChipCanvas().getOrigin3dy(), 0);
//        pmvMatrix.glGetFloatv(GL2.GL_PROJECTION_MATRIX, proj);
//        pmvMatrix.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
//        pmvMatrix.glLoadIdentity();
//        pmvMatrix.glGetFloatv(GL2.GL_MODELVIEW_MATRIX, mv);
//        checkGLError(gl, "using shader program");
            gl.glGetFloatv(GLMatrixFunc.GL_PROJECTION_MATRIX, proj);
//        gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
//        gl.glLoadIdentity();
            gl.glGetFloatv(GLMatrixFunc.GL_MODELVIEW_MATRIX, mv);
            gl.glUniformMatrix4fv(idMv, 1, false, mv);
            gl.glUniformMatrix4fv(idProj, 1, false, proj);

            checkGLError(gl, "setting model/view matrix");

            gl.glUniform1f(idt0, -zmax);
            gl.glUniform1f(idt1, 0);
            if (isLargePointSizeEnabled()) {
                pointSize = 12;
            } else {
                pointSize = 4;
            }
            gl.glUniform1f(idPointSize, pointSize);
            checkGLError(gl, "setting dimensionless time limits t0 or t1 for event buffer rendering");

            gl.glBindVertexArray(vao);
//        gl.glEnableVertexAttribArray(polarity_vert);
            gl.glEnableVertexAttribArray(v_vert);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, buffer.limit(), buffer, GL2ES2.GL_STREAM_DRAW);
            checkGLError(gl, "binding vertex buffers");

            // draw
            gl.glDrawArrays(GL.GL_POINTS, 0, nEvents);
            checkGLError(gl, "drawArrays");
            gl.glBindVertexArray(0); // to use TextRenderers elsewhere; see http://forum.jogamp.org/TextRenderer-my-text-won-t-show-td4029291.html
            gl.glUseProgram(0);
            checkGLError(gl, "disable program");

            drawPlotLabel("Space", gl, .0f, .0f, -0.00f, zmax, 0);
            drawPlotLabel("Time", gl, 1f, 0f, -0.00f, zmax, 90);
            String s = "DVS events";
            drawPlotLabel(s, gl, 1.05f, 0, .25f, zmax, 90);
        }

        if (displayApsFrames) { // render APS frames
            gl.glPushMatrix();
            if (isDrawFramesOnOwnAxes()) {
                gl.glTranslatef(0, chip.getSizeY() * getFrameEventSpacing(), 0);
                gl.glCallList(axesDisplayListId);
            }
            int nFrames = 0;
//            gl.glShadeModel(GL2.GL_FLAT);
            DavisRenderer renderer = (DavisRenderer) chip.getRenderer();
            final EventPacket packet = (EventPacket) renderer.getPacket();
            int lastTimestamp = packet.getLastTimestamp();
            for (FrameWithTime frame : apsFramesInTimeWindow) {
                final int frameDt = frame.timestampUs - lastTimestamp;
                final float z = tfac * (frameDt);
//                log.finer(String.format("Frame %d has frameDt=%,dus (frame.timestampUs=%,d, lastTimestamp=%,d)", nFrames, frameDt, frame.timestampUs, lastTimestamp));
                if (z < -zmax || z > 0) {
                    log.warning(String.format("Frame has z=%f outside of range [0,%f]", z, -zmax));
                }
//                final int nx = chip.getSizeX(), ny = chip.getSizeY();
//                float[] pb = frame.pixBuffer.array();
//                for (int x = 0; x < nx; x++) {
//                    for (int y = 0; y < ny; y++) {
//                        final int idx = renderer.getPixMapIndex(x, y);
//                        float gr = pb[idx];
//                        gl.glColor4f(gr, gr, gr, framesAlpha);
//                        gl.glBegin(GL.GL_TRIANGLES);
//                        gl.glVertex3f(x, y, z);
//                        gl.glVertex3f(x, y + 1, z);
//                        gl.glVertex3f(x + 1, y + 1, z);
//                        gl.glVertex3f(x + 1, y + 1, z);
//                        gl.glVertex3f(x + 1, y, z);
//                        gl.glVertex3f(x, y, z);
//                        gl.glEnd();
//                    }
//                }
//                checkGLError(gl, "after rendering frame");
                gl.glBindTexture(GL.GL_TEXTURE_2D, 0); // use texture number 0 for all textures, otherwise causes problems in JOGL on MacOS
                gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
                gl.glEnable(GL.GL_TEXTURE_2D);
                gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL2.GL_CLAMP);
                gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL2.GL_CLAMP);
                final int nearestFilter = GL.GL_NEAREST;

                gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, nearestFilter);
                gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, nearestFilter);
                gl.glTexEnvf(GL2ES1.GL_TEXTURE_ENV, GL2ES1.GL_TEXTURE_ENV_MODE, GL2ES1.GL_REPLACE);
                gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA,
                        renderer.getWidth(), renderer.getHeight(),
                        0, GL.GL_RGBA, GL.GL_FLOAT,
                        frame.pixBuffer.rewind()
                );
                {
                    gl.glPushMatrix();
                    gl.glTranslatef(0, 0, z); // translate frame to correct time point in z plane
                    drawTexture(gl, renderer.getWidth(), renderer.getHeight());
                    gl.glPopMatrix();
                }
                gl.glDisable(GL.GL_TEXTURE_2D);
                getChipCanvas().checkGLError(gl, glu, "after texture frame rendering");
                nFrames++;
            }
            final int font = GLUT.BITMAP_TIMES_ROMAN_24;
            String s = "APS frames";
            if (chip instanceof DavisChip davis) {
                float fps = davis.getFrameRateHz();
                float exp = davis.getMeasuredExposureMs();
                s = String.format("APS frames (%s FPS, %ss exp)", engFmt.format(fps), engFmt.format(exp * 1e-3f));
            }
            drawPlotLabel(s, gl, 1, 1.05f, .05f, zmax, 90);

//            gl.glColor3f(1, 1, 1);
//            gl.glRasterPos3f(0, sy * 1.05f, -zmax / 3);
//            glut.glutBitmapString(font, s);
            gl.glPopMatrix();
//            log.fine(String.format("rendered %d texture APS frames", nFrames));
        }

        if (displayDvsFrames) { // render APS frames
            gl.glPushMatrix();
            if (isDrawFramesOnOwnAxes()) {
                gl.glTranslatef(0, chip.getSizeY() * getFrameEventSpacing(), 0);
                gl.glCallList(axesDisplayListId);
            }
            int nFrames = 0;
//            gl.glShadeModel(GL2.GL_FLAT);
            DavisRenderer renderer = (DavisRenderer) chip.getRenderer();
            final EventPacket packet = (EventPacket) renderer.getPacket();
            int lastTimestamp = packet.getLastTimestamp();
            for (FrameWithTime frame : dvsFramesInTimeWindow) {
                final int frameDt = frame.timestampUs - lastTimestamp;
                final float z = tfac * (frameDt);
//                log.finer(String.format("Frame %d has frameDt=%,dus (frame.timestampUs=%,d, lastTimestamp=%,d)", nFrames, frameDt, frame.timestampUs, lastTimestamp));
                if (z < -zmax || z > 0) {
                    log.warning(String.format("Frame has z=%f outside of range [0,%f]", z, -zmax));
                }

                gl.glBindTexture(GL.GL_TEXTURE_2D, 0); // use texture number 0 for all textures, otherwise causes problems in JOGL on MacOS
                gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
                gl.glEnable(GL.GL_TEXTURE_2D);
                gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL2.GL_CLAMP);
                gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL2.GL_CLAMP);
                final int nearestFilter = GL.GL_NEAREST;

                gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, nearestFilter);
                gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, nearestFilter);
                gl.glTexEnvf(GL2ES1.GL_TEXTURE_ENV, GL2ES1.GL_TEXTURE_ENV_MODE, GL2ES1.GL_REPLACE);
                gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA,
                        renderer.getWidth(), renderer.getHeight(),
                        0, GL.GL_RGBA, GL.GL_FLOAT,
                        frame.pixBuffer.rewind()
                );
                {
                    gl.glPushMatrix();
                    gl.glTranslatef(0, 0, z); // translate frame to correct time point in z plane
                    drawTexture(gl, renderer.getWidth(), renderer.getHeight());
                    gl.glPopMatrix();
                }
                gl.glDisable(GL.GL_TEXTURE_2D);
                getChipCanvas().checkGLError(gl, glu, "after texture frame rendering");
                nFrames++;
            }
            AbstractAEPlayer player = ((AEChip) chip).getAeViewer().getAePlayer();
            String s = "DVS events";
            switch (player.getPlaybackMode()) {
                case FixedPacketSize:
                    int n = player.getPacketSizeEvents();
                    s = String.format("DVS frames of %sev", engFmt.format(n));
                    break;
                case FixedTimeSlice:
                    int us = player.getTimesliceUs();
                    s = String.format("DVS frames of %ss", engFmt.format(us * 1e-6f));
            }
            drawPlotLabel(s, gl, 1, 1.05f, .25f, zmax, 90);

//            gl.glColor3f(1, 1, 1);
//            gl.glRasterPos3f(0, sy * 1.05f, -zmax / 4);
//            final int font = GLUT.BITMAP_TIMES_ROMAN_24;
//            glut.glutBitmapString(font, s);
            gl.glPopMatrix();
//            log.fine(String.format("rendered %d texture APS frames", nFrames));

        }

//
//        gl.glPopMatrix(); // pop out so that shader uses matrix without applying it twice
//       gl.glMatrixMode(GLMatrixFunc.GL_TEXTURE_MATRIX);
//        gl.glPopMatrix();
        gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
        gl.glPopMatrix(); // pop out so that shader uses matrix without applying it twice
        gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
        // re-enable depth sorting for everything else
//        gl.glDepthMask(true);
    }

    /**
     * Plot a label along axis at position x,y,z relative to entire axis rotated
     * along z direction (like the DVS events label)
     *
     * @param s the string
     * @param gl
     * @param x 0-1
     * @param y
     * @param z 0-1 along z
     * @param zmax the max z in pixels (i.e max of width and height times aspect
     * ratio)
     * @param angleDeg the additional angle, 90 to lie along z axis, 0 to align
     * with space axes
     * @throws GLException
     */
    private void drawPlotLabel(String s, GL2 gl, float x, float y, float z, float zmax, float angleDeg) throws GLException {
        gl.glPushMatrix();
        textRenderer.begin3DRendering();
        gl.glTranslatef(x * sx, y * sy, z * (-zmax));
//        gl.glRotatef(-getChipCanvas().getAnglex(), 1, 0, 0); // rotate viewpoint by angle deg around the x axis
        gl.glRotatef(angleDeg, 0, 1, 0); // rotate viewpoint by angle deg around the y axis
        textRenderer.draw3D(s, 0, 0, 0, 1.5f);
        textRenderer.end3DRendering();
        gl.glPopMatrix();
//            final int font = GLUT.BITMAP_TIMES_ROMAN_24;
//            gl.glColor3f(1, 1, 1);
//            gl.glRasterPos3f(sx * 1.05f, 0, -zmax / 4);
//            glut.glutBitmapString(font, s);
    }

    protected void maybeRegenerateAxesDisplayList(GL2 gl, float zmax, final float modelScale, float dtS) {
        if (regenerateAxesDisplayList) {
            regenerateAxesDisplayList = false;
            if (axesDisplayListId > 0) {
                gl.glDeleteLists(axesDisplayListId, 1);
            }
            axesDisplayListId = gl.glGenLists(1);
            gl.glNewList(axesDisplayListId, GL2.GL_COMPILE);
//        gl.glTranslatef(0, 0, -timeWindowUs);
//        glu.gluLookAt(0, 0, 0,
//                0, 0, -1,
//                0, 1, 0);
//        gl.glTranslatef(-sx,0,0);

//        gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
//        gl.glPushMatrix();
// axes
            gl.glLineWidth(12);
            gl.glBegin(GL.GL_LINES);

            gl.glColor3f(0, 0, 1);
            gl.glVertex3f(0, 0, 0);
            gl.glVertex3f(sx, 0, 0);

            gl.glVertex3f(0, 0, 0);
            gl.glVertex3f(0, sy, 0);

            gl.glVertex3f(sx, 0, 0);
            gl.glVertex3f(sx, sy, 0);

            gl.glVertex3f(sx, sy, 0);
            gl.glVertex3f(0, sy, 0);

            gl.glColor3f(0, 0, 1);
            gl.glVertex3f(0, 0, 0);
            gl.glColor3f(.5f, 0, 0);
            gl.glVertex3f(0, 0, -zmax);
            gl.glColor3f(0, 0, 1);
            gl.glVertex3f(sx, 0, 0);
            gl.glColor3f(.5f, 0, 0);
            gl.glVertex3f(sx, 0, -zmax);
            gl.glColor3f(0, 0, 1);
            gl.glVertex3f(0, sy, 0);
            gl.glColor3f(.5f, 0, 0);
            gl.glVertex3f(0, sy, -zmax);
            gl.glColor3f(0, 0, 1);
            gl.glVertex3f(sx, sy, 0);
            gl.glColor3f(.5f, 0, 0);
            gl.glVertex3f(sx, sy, -zmax);

            gl.glVertex3f(0, 0, -zmax);
            gl.glVertex3f(sx, 0, -zmax);

            gl.glVertex3f(0, 0, -zmax);
            gl.glVertex3f(0, sy, -zmax);

            gl.glVertex3f(sx, 0, -zmax);
            gl.glVertex3f(sx, sy, -zmax);

            gl.glVertex3f(sx, sy, -zmax);
            gl.glVertex3f(0, sy, -zmax);

            gl.glEnd();
            gl.glShadeModel(GLLightingFunc.GL_FLAT);

            // draw axes labels x,y,t. See tutorial at http://jerome.jouvie.free.fr/OpenGl/Tutorials/Tutorial18.php
            final int font = GLUT.BITMAP_TIMES_ROMAN_24;
            final int FS = 1; // distance in pixels of text from endZoom of axis
            float w;
            gl.glColor3f(0, 0, 1);
            gl.glRasterPos3f(sx * 1.05f, 0, 0);
            glut.glutBitmapString(font, "x=" + sx);
            gl.glRasterPos3f(0, sy * 1.05f, 0);
            glut.glutBitmapString(font, "y=" + sy);
            w = glut.glutBitmapLength(font, "t=0");
            gl.glRasterPos3f(-6 * w * modelScale, 0, 0);
            glut.glutBitmapString(font, "t=0");
            gl.glColor3f(1f, 0, 0);
            String tMaxString = "t=" + engFmt.format(-dtS) + "s";
            w = glut.glutBitmapLength(font, tMaxString);
            gl.glRasterPos3f(sx * 1.05f, 0, -zmax);
            glut.glutBitmapString(font, tMaxString);
            checkGLError(gl, "drawing axes labels");
            gl.glEndList();
        }
    }

    /**
     * Draws the current texture into the width and height of chip pixels
     *
     * @param gl the OpenGL context
     * @param width of texture (power of 2 larger than chip size)
     * @param height of texture (larger than chip pixel array height)
     */
    protected void drawTexture(final GL2 gl, final int width, final int height) {
        final double xRatio = (double) chip.getSizeX() / (double) width;
        final double yRatio = (double) chip.getSizeY() / (double) height;
        gl.glBegin(GL2.GL_POLYGON);

        gl.glTexCoord2d(0, 0);
        gl.glVertex2d(0, 0);
        gl.glTexCoord2d(xRatio, 0);
        gl.glVertex2d(xRatio * width, 0);
        gl.glTexCoord2d(xRatio, yRatio);
        gl.glVertex2d(xRatio * width, yRatio * height);
        gl.glTexCoord2d(0, yRatio);
        gl.glVertex2d(0, yRatio * height);

        gl.glEnd();
    }

    boolean checkGLError(final GL2 gl, String msg) {
        boolean r = false;
        int error = gl.glGetError();
        int nerrors = 10;
        while ((error != GL.GL_NO_ERROR) && (nerrors-- != 0)) {
            if (glu == null) {
                glu = new GLU();
            }
            StackTraceElement[] st = Thread.currentThread().getStackTrace();
            log.warning("GL error number " + error + " " + glu.gluErrorString(error) + "\n");
            new RuntimeException("GL error number " + error + " " + glu.gluErrorString(error)).printStackTrace();
            error = gl.glGetError();
            r = true;
        }
        return r;
    }

    private void printShaderLog(GL2 gl) {
        IntBuffer b = IntBuffer.allocate(8);
        gl.glGetProgramiv(shaderprogram, GL2ES2.GL_INFO_LOG_LENGTH, b);
        int logLength = b.get(0);
        ByteBuffer bb = ByteBuffer.allocate(logLength);
        b.clear();
        gl.glGetProgramInfoLog(shaderprogram, logLength, b, bb);
        String v = new String(bb.array(), java.nio.charset.StandardCharsets.UTF_8);
        log.info(v);

    }

    private String readFromStream(InputStream ins) throws IOException {
        if (ins == null) {
            throw new IOException("Could not read from stream.");
        }
        StringBuffer buffer = new StringBuffer();
        Scanner scanner = new Scanner(ins);
        try {
            while (scanner.hasNextLine()) {
                buffer.append(scanner.nextLine() + "\n");
            }
        } finally {
            scanner.close();
        }

        return buffer.toString();
    }

    @Override
    protected void onDeregistration() {
        if (chip.getRenderer() != null && chip.getRenderer() instanceof DavisRenderer) {
            chip.getRenderer().getSupport().removePropertyChangeListener(DavisRenderer.EVENT_NEW_FRAME_AVAILBLE, this);
            apsFramesInTimeWindow.clear(); // recover memory
        }
        AEChip aeChip = (AEChip) chip;
        if (displayMenu == null) {
            return;
        }
        AEViewer viewer = aeChip.getAeViewer();
        viewer.removeMenu(displayMenu);
        displayMenu = null;
        if (aeChip.getAeViewer() != null && aeChip.getAeViewer().getAePlayer() != null) {
            aeChip.getAeViewer().getSupport().removePropertyChangeListener(AEInputStream.EVENT_REWOUND, this);
        }
        getChipCanvas().setZoom(oldZoom);
    }

    @Override
    protected void onRegistration() {
        if (chip == null) {
            return;
        }
        AEChip aeChip = (AEChip) chip;
        AEViewer viewer = aeChip.getAeViewer();
        if (viewer == null) {
            log.warning("cannot add menu item to control SpaceTimeRollingEventDisplayMethod, AEViewer is null");
            return;
        }
        displayMenu = new JMenu("3-D Display Options");
        displayMenu.add(new JMenuItem(new SetTimeAspectRatioAction()));
        displayMenu.add(new JSeparator());
        displayMenu.add(new JCheckBoxMenuItem(new ToggleDisplayDvsFrames()));
        displayMenu.add(new JCheckBoxMenuItem(new ToggleDrawFramesOnOwnAxesAction()));
        displayMenu.add(new JMenuItem(new SetFrameEventSpacingAction()));
        displayMenu.add(new JSeparator());
        displayMenu.add(new JCheckBoxMenuItem(new ToggleLargePointsAction()));
        displayMenu.add(new JSeparator());
        displayMenu.add(new JCheckBoxMenuItem(new ToggleAdditiveColorAction()));
        displayMenu.add(new JMenuItem(new SetTransparencyAction()));
        viewer.addMenu(displayMenu);

        if (chip.getRenderer() != null) {
            chip.getRenderer().getSupport().addPropertyChangeListener(DavisRenderer.EVENT_NEW_FRAME_AVAILBLE, this);
        }
        if (aeChip.getAeViewer() != null && aeChip.getAeViewer().getAePlayer() != null) {
            aeChip.getAeViewer().getSupport().addPropertyChangeListener(AEInputStream.EVENT_REWOUND, this);
        }
        oldZoom = getChipCanvas().getZoom();
        getChipCanvas().setZoom(zoom);

    }

    @Override
    synchronized public void propertyChange(PropertyChangeEvent evt) {
        if (displayApsFrames && evt.getPropertyName() == DavisRenderer.EVENT_NEW_FRAME_AVAILBLE) {
            DavisRenderer renderer = (DavisRenderer) (chip.getRenderer());
//            float[] pixmap = renderer.getPixmapArray();
//            FrameWithTime newFrame = new FrameWithTime(renderer.getPixBuffer(), renderer.getTimestampFrameEnd());
            apsFramesInTimeWindow.add(renderer.getPixBuffer(), renderer.getTimestampFrameEnd());
            log.log(Level.FINE, "New frame with timestamp {0}", renderer.getTimestampFrameEnd());
        } else if (evt.getPropertyName() == AEInputStream.EVENT_REWOUND) {
            apsFramesInTimeWindow.clear();
            eventList.clear();
        }
    }

    /**
     * @return the drawFramesOnOwnAxes
     */
    public boolean isDrawFramesOnOwnAxes() {
        return drawFramesOnOwnAxes;
    }

    /**
     * @param drawFramesOnOwnAxes the drawFramesOnOwnAxes to set
     */
    public void setDrawFramesOnOwnAxes(boolean drawFramesOnOwnAxes) {
        this.drawFramesOnOwnAxes = drawFramesOnOwnAxes;
        prefs.putBoolean("drawFramesOnOwnAxes", drawFramesOnOwnAxes);
    }

    /**
     * @return the additiveColorEnabled
     */
    public boolean isAdditiveColorEnabled() {
        return additiveColorEnabled;
    }

    /**
     * @param additiveColorEnabled the additiveColorEnabled to set
     */
    public void setAdditiveColorEnabled(boolean additiveColorEnabled) {
        this.additiveColorEnabled = additiveColorEnabled;
        prefs.putBoolean("additiveColorEnabled", additiveColorEnabled);
    }

    /**
     * @return the largePointSizeEnabled
     */
    public boolean isLargePointSizeEnabled() {
        return largePointSizeEnabled;
    }

    /**
     * @param largePointSizeEnabled the largePointSizeEnabled to set
     */
    public void setLargePointSizeEnabled(boolean largePointSizeEnabled) {
        this.largePointSizeEnabled = largePointSizeEnabled;
        prefs.putBoolean("largePointSizeEnabled", largePointSizeEnabled);
    }

    /**
     * @return the frameEventSpacing
     */
    public float getFrameEventSpacing() {
        return frameEventSpacing;
    }

    /**
     * @param frameEventSpacing the frameEventSpacing to set
     */
    public void setFrameEventSpacing(float frameEventSpacing) {
        this.frameEventSpacing = frameEventSpacing;
        prefs.putFloat("frameEventSpacing", frameEventSpacing);
        regenerateAxesDisplayList = true;
    }

    /**
     * @return the timeAspectRatio
     */
    public float getTimeAspectRatio() {
        return timeAspectRatio;
    }

    /**
     * @param timeAspectRatio the timeAspectRatio to set
     */
    public void setTimeAspectRatio(float timeAspectRatio) {
        this.timeAspectRatio = timeAspectRatio;
        prefs.putFloat("timeAspectRatio", timeAspectRatio);
        regenerateAxesDisplayList = true;
    }

    /**
     * @return the displayDvsFrames
     */
    public boolean isDisplayDvsFrames() {
        return displayDvsFrames;
    }

    /**
     * @param displayDvsFrames the displayDvsFrames to set
     */
    public void setDisplayDvsFrames(boolean displayDvsFrames) {
        this.displayDvsFrames = displayDvsFrames;
        prefs.putBoolean("displayDvsFrames", displayDvsFrames);
    }

//    /**
//     * @return the dvsFramesEventCount
//     */
//    public int getDvsFramesEventCount() {
//        return dvsFramesEventCount;
//    }
//
//    /**
//     * @param dvsFramesEventCount the dvsFramesEventCount to set
//     */
//    public void setDvsFramesEventCount(int dvsFramesEventCount) {
//        this.dvsFramesEventCount = dvsFramesEventCount;
//        prefs.putInt("dvsFramesEventCount", dvsFramesEventCount);
//    }
    /**
     * A frame pixel buffer along with the frame (end of exposure) time
     */
    private class FrameWithTime {

        FloatBuffer pixBuffer;
        int timestampUs;

        public FrameWithTime(FloatBuffer input, int timestampUs) {
            this.pixBuffer = FloatBuffer.allocate(input.limit());
            input.rewind();
            this.pixBuffer.put(input);
            this.timestampUs = timestampUs;
        }

        public String toString() {
            return String.format("Frame with end timestamp %,d", timestampUs);
        }
    }

    /**
     * List of frames currently displayed
     */
    private class FramesInTimeWindow extends LinkedList<FrameWithTime> {

        LinkedList<FrameWithTime> unusedFrames = new LinkedList();

        /**
         * Adds a new frame to tail of list, removing head (oldest) if it is
         * past the time window. The last frame added is the youngest, rendered
         * closest to us if the youngest data is in the front.
         * <p>
         * The head of the list is the oldest frame, which gets rendered first.
         * The tail of the list (the last frame added) is the youngest one
         *
         * @param newFrame
         * @return true
         */
        @Override
        public boolean add(FrameWithTime newFrame) {
            if (!isEmpty() && newFrame.timestampUs < peekFirst().timestampUs) {
                clear(); // if rewound, clear all frames
            }
            super.add(newFrame); // add to tail of list
            return true;
        }

        /**
         * Add a new frame based on pixbuffer input and the frame end timestamp
         *
         * @param input the pix buffer from the renderer
         * @param timestampUs
         * @return true
         */
        public boolean add(FloatBuffer input, int timestampUs) {
            FrameWithTime fr = null;
            if (unusedFrames.isEmpty()) {
                fr = new FrameWithTime(input, timestampUs);
            } else {
                fr = unusedFrames.pop();
            }
            input.rewind();
            fr.pixBuffer.rewind();
            fr.pixBuffer.put(input);
            fr.timestampUs = timestampUs;
            return add(fr);
        }

        /**
         * Removes frames older than t0 time
         *
         * @param t0 the oldest timestamp in us
         * @return true if frame was removed, false otherwise
         */
        public boolean removeFramesOlderThan(int t0) {
            if (isEmpty()) {
                return false;
            }
            boolean removedSomeFrame = false;
            while (!isEmpty() && peekFirst().timestampUs < t0) {
                // if it is older than the oldest time in window, remove it
//                log.finest(String.format("Removing old frames %s", peekFirst()));
                FrameWithTime removed = removeFirst(); // prune frames outside the window
                unusedFrames.add(removed);
                removedSomeFrame = true;
            }
            return removedSomeFrame;

//            int tFrame = peekFirst().timestampUs;  // peekFirst gets the oldest frame
//            int dt = tFrame - t0;
//            if (dt < 0) {
//               
//                FrameWithTime removed = removeFirst(); // prune frames outside the window
//                unusedFrames.add(removed);
//                return true;
//            }
//            return false;
//        }
        }
    }

    abstract public class MyAction extends AbstractAction {

        public MyAction() {
            super();
        }

        public MyAction(String name) {
            putValue(Action.NAME, name);
//            putValue(ACCELERATOR_KEY, javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_P, java.awt.event.InputEvent.CTRL_DOWN_MASK));
            putValue("hideActionText", "true");
            putValue(Action.SHORT_DESCRIPTION, name);
        }

        public MyAction(String name, String tooltip) {
            putValue(Action.NAME, name);
            putValue("hideActionText", "true");
            putValue(Action.SHORT_DESCRIPTION, tooltip);
        }

        protected void showAction() {
            if (((AEChip) chip).getAeViewer() != null) {
                ((AEChip) chip).getAeViewer().showActionText((String) getValue(Action.SHORT_DESCRIPTION));
            }
        }

        protected void showAction(String s) {
            if (((AEChip) chip).getAeViewer() != null && s != null) {
                ((AEChip) chip).getAeViewer().showActionText(s);
            }
        }

        /**
         * Sets the name, which is the menu item string
         *
         * @param name
         */
        public void setName(String name) {
            putValue(Action.NAME, name);
        }
    }

    final public class ToggleLargePointsAction extends MyAction {

        public ToggleLargePointsAction() {
            super("Large points enabled", "<html>\"make the event points larger (12 points),<br> rather than the default (4 points) for better visibility with sparse event stream");
            putValue(Action.SELECTED_KEY, isLargePointSizeEnabled());
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            setLargePointSizeEnabled(!isLargePointSizeEnabled());
            putValue(Action.SELECTED_KEY, isLargePointSizeEnabled());
            showAction();
        }

        @Override
        protected void showAction() {
            if (isLargePointSizeEnabled()) {
                showAction("Small points enabled");
            } else {
                showAction("Large points enabled");
            }
        }
    }

    final public class ToggleAdditiveColorAction extends MyAction {

        public ToggleAdditiveColorAction() {
            super("Additive color enabled", "Use additive color rather than blending for drawing event points");
            putValue(Action.SELECTED_KEY, isAdditiveColorEnabled());
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            setAdditiveColorEnabled(!isAdditiveColorEnabled());
            putValue(Action.SELECTED_KEY, isAdditiveColorEnabled());
            showAction();
        }

        @Override
        protected void showAction() {
            if (isAdditiveColorEnabled()) {
                showAction("Additive color enabled");
            } else {
                showAction("Blending enabled");
            }
        }
    }

    final public class ToggleDrawFramesOnOwnAxesAction extends MyAction {

        public ToggleDrawFramesOnOwnAxesAction() {
            super("Shift frames vertically", "Draw the APS frames with a different set of axes");
            putValue(Action.SELECTED_KEY, isDrawFramesOnOwnAxes());
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            setDrawFramesOnOwnAxes(!isDrawFramesOnOwnAxes());
            putValue(Action.SELECTED_KEY, isDrawFramesOnOwnAxes());
            showAction();
        }
    }

    final public class SetTransparencyAction extends MyAction {

        public SetTransparencyAction() {
            super("Set transparency", String.format("Set frame tranparency alpha (currently %.2f)", framesAlpha));
        }

        @Override
        public void actionPerformed(ActionEvent ae) {
            String ret = JOptionPane.showInputDialog(String.format("New alpha value (currently %.2f", framesAlpha), String.format("%f", framesAlpha));
            if (ret == null) {
                return;
            }
            try {
                float v = Float.parseFloat(ret);
                if (v < .1 || v > 1) {
                    return;
                }
                framesAlpha = v;
                prefs.putFloat("framesAlpha", framesAlpha);
                putValue(Action.SHORT_DESCRIPTION, String.format("Set frame tranparency alpha (currently %.2f)", framesAlpha));
            } catch (NumberFormatException e) {
                log.warning(e.toString()); // TODO put in loop with cancel
            }
        }

        @Override
        protected void showAction() {
            showAction(String.format("Set alpha=%.2f", framesAlpha));
        }
    }

    final public class SetFrameEventSpacingAction extends MyAction {

        public SetFrameEventSpacingAction() {
            super("Set frame/event spacing", String.format("Set frame/event spacing as multiple of chip height (currently %.2f)", getFrameEventSpacing()));
        }

        @Override
        public void actionPerformed(ActionEvent ae) {
            String ret = JOptionPane.showInputDialog(String.format("New frame/event spacing value (currently %.2f", getFrameEventSpacing()), String.format("%f", getFrameEventSpacing()));
            if (ret == null) {
                return;
            }
            try {
                float v = Float.parseFloat(ret);
                setFrameEventSpacing(v);
                putValue(Action.SHORT_DESCRIPTION, String.format("Set frame/event spacing (currently %.2f)", getFrameEventSpacing()));
            } catch (NumberFormatException e) {
                log.warning(e.toString()); // TODO put in loop with cancel
            }
        }

        @Override
        protected void showAction() {
            showAction(String.format("Set frame/event spacing=%.2f", getFrameEventSpacing()));
        }
    }

    final public class SetTimeAspectRatioAction extends MyAction {

        public SetTimeAspectRatioAction() {
            super("Set time axis aspect ratio", String.format("Set aspect ratio of time axis relative to space axes (currently %.1f)", getTimeAspectRatio()));
        }

        @Override
        public void actionPerformed(ActionEvent ae) {
            String ret = JOptionPane.showInputDialog(String.format("New aspect ratio of time axis relative to space axes (currently %.1f)", getTimeAspectRatio()), String.format("%f", getTimeAspectRatio()));
            if (ret == null) {
                return;
            }
            try {
                float v = Float.parseFloat(ret);
                setTimeAspectRatio(v);
                putValue(Action.SHORT_DESCRIPTION, String.format("Set aspect ratio of time axis relative to space axes (currently %.1f)", getTimeAspectRatio()));
            } catch (NumberFormatException e) {
                log.warning(e.toString()); // TODO put in loop with cancel
            }
        }

        @Override
        protected void showAction() {
            showAction(String.format("Set frame/event spacing=%.2f", getFrameEventSpacing()));
        }
    }

    final public class ToggleDisplayDvsFrames extends MyAction {

        public ToggleDisplayDvsFrames() {
            super("Toggle DVS frames", String.format("Toggles display of accumulated-event-count DVS frames"));
            putValue(Action.SELECTED_KEY, isDisplayDvsFrames());
        }

        @Override
        public void actionPerformed(ActionEvent ae) {
            setDisplayDvsFrames(!displayDvsFrames);
            putValue(Action.SELECTED_KEY, isDisplayDvsFrames());
        }

        @Override
        protected void showAction() {
            showAction(String.format("Set showDvsFrames=%s", isDisplayDvsFrames()));
        }
    }

//    final public class SetDvsConstantCountAction extends MyAction {
//
//        public SetDvsConstantCountAction() {
//            super("Set number events per frame", String.format("Set number events per frame (currently %,d)", getDvsFramesEventCount()));
//        }
//
//        @Override
//        public void actionPerformed(ActionEvent ae) {
//            setShowDvsFrames(!displayDvsFrames);
//        }
//
//        @Override
//        protected void showAction() {
//            showAction(String.format("Set showDvsConstantCountFrames=%s", isShowDvsFrames()));
//        }
//    }
}
