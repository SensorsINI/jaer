/*
 * Series.java
 *
 * A Series is a data series in a chart; its view is a Category object.
 * The data are cached and then transferred to an OpenGL device.
 *
 * Semester project Matthias Schrag, HS07
 */

package net.sf.jaer.util.chart;

import com.sun.opengl.util.BufferUtil;
import java.nio.FloatBuffer;
import java.util.logging.Logger;
import javax.media.opengl.GL;

/**
 * The Series class.
 * A Series is a data series in a chart; its view is a Category object.
 * The data are cached and then transferred to an OpenGL device.
 */
public class Series {
    
    static Logger log=Logger.getLogger("chart");
    
    protected static final int DEFAULT_CAPACITY = 1000000;
    
    /** The dimension of the elements (=vertices) */
    protected int dimension;
    /** The max number of data points in the series. */
    protected int capacity;
    
    /** number of bytes per element */
    protected final int elementSize;
    /** number of flushed elements (= dimension * verticesCount) */
    protected int elementsCount;
    /** The size of the buffer */
//    private int flushedBytes;
    /** A buffer to cache new data points before they are transfered to the OpenGL device. */
    protected FloatBuffer cache;
    /** The interface to opengl */
    protected GL gl;
    /** the opengl buffer id */
    private int bufferId;
    
    /**
     * Create a new Series object with <code>capacity</code>.
     * @param dimensions the number of dimensions (2 or 3)
     * @param capacity max number of points
     */
    public Series(int dimensions, int capacity) {
        this.dimension = dimensions;
        this.capacity = capacity;
        elementSize = Float.SIZE / 8;
        cache = BufferUtil.newFloatBuffer(dimension * capacity);
    }
    
    /**
     * Create a new Series object with default capacity.
     * @param dimensions the number of dimensions (2 or 3)
     */
    public Series(int dimensions) {
        this(dimensions, DEFAULT_CAPACITY);
    }
    
    /**
     * Add a data item to the series cache.
     * @param x the x value
     * @param y the y value
     */
    public void add(float x, float y) {
        assert dimension == 2;
        
        cache.put(x);
        cache.put(y);
    }
    
    /**
     * Add a data item to the series cache.
     */
    public void add(float x, float y, float z) {
        assert dimension == 3;
        
        cache.put(x);
        cache.put(y);
        cache.put(z);
    }
    
    /**
     * Flushes data to opengl graphics device and draws the vertices.
     * The gl object must be always the same.
     * @param gl the OpenGL context (must be identical betweeen calls)
     * @param method the method of drawing the series line segments, e.g. <code>GL.GL_LINE_STRIP</code>.
     */
    public void draw(GL gl, int method) {
        /* bind to gl object if necessary (implicit 2nd phase constructor) */
        if (this.gl == null) {
            gl.glEnableClientState(GL.GL_VERTEX_ARRAY);
            int[] bufferIds = new int[1];
            gl.glGenBuffers(1, bufferIds, 0);   // create buffer id
            bufferId = bufferIds[0];
        } else if (this.gl != gl) { // error: cannot bind to multiple devices
            log.warning("Chart data series: Always same GL object expected! this.gl="+this.gl+" but called gl="+gl);
            this.gl=gl;
            return;
        }
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, bufferId);
        /* flush data to opengl device if necessary */
        int add = cache.position();   // check for new data...
        if (add > 0) {    // ...and transfer them to opengl buffer if necessary
            cache.position(0);
            if (this.gl == null) {
                gl.glBufferData(GL.GL_ARRAY_BUFFER, dimension * capacity * elementSize, cache, GL.GL_STATIC_DRAW);   // create buffer and flush
                this.gl = gl;
            } else {
                gl.glBindBuffer(GL.GL_ARRAY_BUFFER, bufferId);
                gl.glBufferSubData(GL.GL_ARRAY_BUFFER, elementsCount * elementSize, add * elementSize, cache);   // flush
            }
            elementsCount += add;   // move position
            cache.position(0);
        }
        /* draw data series */
        gl.glVertexPointer(2, GL.GL_FLOAT, 0, 0);   // 2D-vertices of float from current opengl buffer beginning at 0
        gl.glDrawArrays(method, 0, elementsCount / dimension);
    }
}
