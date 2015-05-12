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

import java.util.Iterator;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.fixedfunc.GLMatrixFunc;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.util.PMVMatrix;

import eu.seebetter.ini.chips.davis.DavisDisplayConfigInterface;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.util.EngineeringFormat;

import com.jogamp.opengl.util.gl2.GLUT;

import eu.seebetter.ini.chips.DavisChip;
import eu.seebetter.ini.chips.davis.imu.IMUSample;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Scanner;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.aemonitor.AEConstants;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.graphics.ChipCanvas.ClipArea;

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

    EngineeringFormat engFmt = new EngineeringFormat();

    private DavisDisplayConfigInterface config;
    private boolean displayEvents = true;
    private boolean displayFrames = true;
    boolean spikeListCreated = false;
    int spikeList = 1;
    GLUT glut = null;
    GLU glu = null;
    private boolean shadersInstalled = false;
    int shaderprogram;
    int vertexShader;
    int fragmentShader;
    int vao;
    int vbo;
//    final int polarity_vert = 0;
    final int v_vert = 0;
//    final int polarity_frag = 0;
    private int BUF_INITIAL_SIZE_EVENTS = 20000;
    private int BUF_SIZE_INCREMENT_FACTOR = 2;
    ByteBuffer eventVertexBuffer;
    float rsx = 1;
    float rsy = 1;
    int sx, sy, smax;
    private int timeSlice = 0;
//    private PMVMatrix pmvMatrix; // our own matrix stack that we manipulate ourselves, independent of GPU pipeline
    FloatBuffer mv = FloatBuffer.allocate(16);
    FloatBuffer proj = FloatBuffer.allocate(16);
    int idMv, idProj, idt0, idt1;
    ArrayList<BasicEvent> eventList = new ArrayList(BUF_INITIAL_SIZE_EVENTS), eventListTmp = new ArrayList(BUF_INITIAL_SIZE_EVENTS);
    private int timeWindowUs = 100000, t0;
    private static int EVENT_SIZE_BYTES = (Float.SIZE / 8) * 3;// size of event in shader ByteBuffer

    /**
     * Creates a new instance of SpaceTimeEventDisplayMethod
     */
    public SpaceTimeRollingEventDisplayMethod(final ChipCanvas chipCanvas) {
        super(chipCanvas);
        this.eventVertexBuffer = ByteBuffer.allocateDirect(BUF_INITIAL_SIZE_EVENTS * EVENT_SIZE_BYTES);
        eventVertexBuffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    private void installShaders(GL2 gl) throws IOException {
        if (shadersInstalled) {
            return;
        }
        gl.glEnable(GL3.GL_PROGRAM_POINT_SIZE);

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
        if (idMv < 0 || idProj < 0 || idt0 < 0 || idt1 < 0) {
            throw new RuntimeException("cannot locate uniform variable idMv, idProj, idt0 or idt1 in shader program");
        }
        checkGLError(gl, "getting IDs for uniform modelview and projection matrices in shaders");
    }

    private void checkEventBufferAllocation(int sizeEvents) {
        if (eventVertexBuffer.capacity() <= sizeEvents * EVENT_SIZE_BYTES) {
            eventVertexBuffer = ByteBuffer.allocateDirect(eventList.size() * EVENT_SIZE_BYTES);
            eventVertexBuffer.order(ByteOrder.LITTLE_ENDIAN);
        }
    }

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
        final int n = packet.getSize();
        if (n == 0) {
            return;
        }
        final int t0ThisPacket = packet.getFirstTimestamp();
        final int t1 = packet.getLastTimestamp();
        final int dtThisPacket = t1 - t0ThisPacket + 1;
//        float z;
        // the time that is displayed in rolling window is some multiple of either current frame duration (for live playback) or timeslice (for recorded playback)
        int colorScale = getRenderer().getColorScale(); // use color scale to determine multiple, up and down arrows set it then
        if (chip.getAeViewer().getPlayMode() == AEViewer.PlayMode.LIVE) {
            int frameDurationUs = (int) (1e6f / chip.getAeViewer().getFrameRater().getDesiredFPS());
            timeWindowUs = frameDurationUs * (1 << colorScale);
        } else if (chip.getAeViewer().getPlayMode() == AEViewer.PlayMode.PLAYBACK) {
            timeWindowUs = chip.getAeViewer().getAePlayer().getTimesliceUs() * (1 << colorScale);
        } else {
            timeWindowUs = 100000;
        }
        t0 = t1 - timeWindowUs;
        pruneOldEvents(t0);

        sx = chip.getSizeX();
        sy = chip.getSizeY();
        smax = chip.getMaxSize();

        addEventsToEventList(packet);
        checkEventBufferAllocation(eventList.size());
        eventVertexBuffer.clear();// TODO should not really clear, rather should erase old events

        for (BasicEvent ev : eventList) {
            eventVertexBuffer.putFloat(ev.x);
            eventVertexBuffer.putFloat(ev.y);
            eventVertexBuffer.putFloat(ev.timestamp - t1); // negative z
        }
        eventVertexBuffer.flip();
        checkGLError(gl, "set uniform t0 and t1");

        renderEvents(gl, drawable, eventVertexBuffer, eventList.size(), t0, t1);

    }

    private void addEventsToEventList(final EventPacket<BasicEvent> packet) {
        for (BasicEvent e : packet) {
//            BasicEvent e = (BasicEvent) o;
            if (e.isSpecial() || e.isFilteredOut()) {
                continue;
            }
            BasicEvent ne=new BasicEvent();
            ne.copyFrom(e);
            eventList.add(ne); // must do this unfortunately because otherwise the original event object in this list will be reused for a later packet
        }
    }

    private void pruneOldEvents(final int startTimeUs) {
        eventListTmp.clear();

        for (BasicEvent ev : eventList) {
            if (ev.timestamp >= startTimeUs) {
                eventListTmp.add(ev);
            }
        }
        eventList.clear();
        eventList.addAll(eventListTmp);
    }

    void renderEvents(GL2 gl, GLAutoDrawable drawable, ByteBuffer b, int nEvents, int t0, int t1) {
        gl.glClearColor(0, 0, 0, 0);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        // axes
        gl.glColor3f(0, 0, 1);
        gl.glLineWidth(1f);
        if (glu == null) {
            glu = new GLU();
        }

        gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
        gl.glLoadIdentity();
//        gl.glPushMatrix();
        ClipArea clip = getChipCanvas().getClipArea();
        gl.glRotatef(getChipCanvas().getAngley(), 0, 1, 0); // rotate viewpoint by angle deg around the y axis
        gl.glRotatef(-getChipCanvas().getAnglex(), 1, 0, 0); // rotate viewpoint by angle deg around the x axis
//        gl.glRotatef(-15, 1, 1, 0); // rotate viewpoint by angle deg around the y axis
        gl.glOrtho(clip.left, clip.right, clip.bottom, clip.top, -timeWindowUs*4, timeWindowUs * 8);
//        glu.gluPerspective(22, (float)drawable.getSurfaceWidth()/drawable.getSurfaceHeight(), .1, timeWindowUs*100);
//        glu.gluPerspective(30, (float)sx/sy, .1, timeWindowUs*1.1f);
//        gl.glFrustumf(clip.left, clip.right, clip.bottom, clip.top, .1f, timeWindowUs*20);
//        gl.glFrustumf(0,sx, 0, sy, .1f, timeWindowUs*20);
//        gl.glTranslatef(sx/2, sy/2, -1);
//        gl.glFrustumf(clip.left, clip.right, clip.bottom, clip.top, .1f, timeWindowUs * 10);
//        gl.glTranslatef(sx, sy, 1);
        gl.glTranslatef(getChipCanvas().getOrigin3dx(), getChipCanvas().getOrigin3dy(), 0);
        checkGLError(gl, "setting projection");

//        getChipCanvas().setDefaultProjection(gl, drawable);
        gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
        gl.glLoadIdentity();
        gl.glTranslatef(0, 0, 0);
        gl.glScalef(1,1,8);
//        gl.glTranslatef(0, 0, -timeWindowUs);
//        glu.gluLookAt(0, 0, 0,
//                0, 0, -1,
//                0, 1, 0);
//        gl.glTranslatef(-sx,0,0);

//        gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
//        gl.glPushMatrix();
        // axes
        gl.glBegin(GL.GL_LINES);

        final float zz = -timeWindowUs;

        gl.glVertex3f(0, 0, 0);
        gl.glVertex3f(sx, 0, 0);

        gl.glVertex3f(0, 0, 0);
        gl.glVertex3f(0, sy, 0);

        gl.glVertex3f(sx, 0, 0);
        gl.glVertex3f(sx, sy, 0);

        gl.glVertex3f(sx, sy, 0);
        gl.glVertex3f(0, sy, 0);

        gl.glVertex3f(0, 0, 0);
        gl.glVertex3f(0, 0, zz);
        gl.glVertex3f(sx, 0, 0);
        gl.glVertex3f(sx, 0, zz);
        gl.glVertex3f(0, sy, 0);
        gl.glVertex3f(0, sy, zz);
        gl.glVertex3f(sx, sy, 0);
        gl.glVertex3f(sx, sy, zz);

        gl.glVertex3f(0, 0, zz);
        gl.glVertex3f(sx, 0, zz);

        gl.glVertex3f(0, 0, zz);
        gl.glVertex3f(0, sy, zz);

        gl.glVertex3f(sx, 0, zz);
        gl.glVertex3f(sx, sy, zz);

        gl.glVertex3f(sx, sy, zz);
        gl.glVertex3f(0, sy, zz);

        gl.glEnd();

        // draw axes labels x,y,t. See tutorial at http://jerome.jouvie.free.fr/OpenGl/Tutorials/Tutorial18.php
        final int font = GLUT.BITMAP_TIMES_ROMAN_24;
        final int FS = 1; // distance in pixels of text from endZoom of axis
        gl.glRasterPos3f(sx, 0, 0);
        glut.glutBitmapString(font, "x=" + sx);
        gl.glRasterPos3f(0, sy, 0);
        glut.glutBitmapString(font, "y=" + sy);
        gl.glRasterPos3f(0, 0, zz);
        glut.glutBitmapString(font, "t=" + engFmt.format((t0 - t1) * AEConstants.TICK_DEFAULT_US * 1e-6f) + "s");
        gl.glRasterPos3f(0, 0, 0);
        glut.glutBitmapString(font, "t=0");
        checkGLError(gl, "drawing axes labels");

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
        gl.glGetFloatv(GL2.GL_PROJECTION_MATRIX, proj);
//        gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
//        gl.glLoadIdentity();
        gl.glGetFloatv(GL2.GL_MODELVIEW_MATRIX, mv);
        gl.glUniformMatrix4fv(idMv, 1, false, mv);
        gl.glUniformMatrix4fv(idProj, 1, false, proj);

        checkGLError(gl, "setting model/view matrix");

        gl.glUniform1f(idt0, (float) t0);
        gl.glUniform1f(idt1, (float) t1);
        checkGLError(gl, "setting t0 or t1 for buffer");

        gl.glBindVertexArray(vao);
//        gl.glEnableVertexAttribArray(polarity_vert);
        gl.glEnableVertexAttribArray(v_vert);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, b.limit(), b, GL2ES2.GL_STREAM_DRAW);
        checkGLError(gl, "binding vertex buffers");

//        gl.glEnable(GL.GL_DEPTH_TEST);
//        gl.glDepthMask(true);
//        gl.glEnable(GL.GL_BLEND);
//        gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE);
//        gl.glBlendEquation(GL.GL_FUNC_ADD);
//        checkGLError(gl, "setting blend function");
        // draw
        gl.glDrawArrays(GL.GL_POINTS, 0, nEvents);
        checkGLError(gl, "drawArrays");
        gl.glUseProgram(0);
        checkGLError(gl, "disable program");
//
//        gl.glPopMatrix(); // pop out so that shader uses matrix without applying it twice
//        gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
//        gl.glPopMatrix(); // pop out so that shader uses matrix without applying it twice
//        gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
        // re-enable depth sorting for everything else
//        gl.glDepthMask(true);
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

}
