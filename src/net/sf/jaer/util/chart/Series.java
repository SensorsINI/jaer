/*
 * Series.java
 *
 * A Series is a data series in a chart; its view is a Category object.
 * The data are cached and then transferred to an OpenGL device.
 *
 * Semester project Matthias Schrag, HS07
 */
package net.sf.jaer.util.chart;

import java.nio.BufferOverflowException;
import java.nio.FloatBuffer;
import java.util.logging.Logger;


import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.fixedfunc.GLPointerFunc;
import com.jogamp.opengl.glu.GLU;

/**
 * The Series class.
 * A Series is a data series in a chart; its view is a Category object.
 * The data are cached and then transferred to an OpenGL device.
 */
public class Series {

	static Logger log = Logger.getLogger("chart");
	/** Default capacity of series */
	protected static final int DEFAULT_CAPACITY = 10000;
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
	/** A buffer to cache new data points before they are transfered to the OpenGL device - if buffer extension available. */
	protected FloatBuffer cache;
	/** Local buffer that holds charted points in case buffer extension not available. */
	protected FloatBuffer vertices;
	/** The interface to opengl */
	protected GL2 gl;
	/** the opengl buffer id */
	private int bufferId;
	//    /** The line width of the series in pixels, default 1.*/
	//    protected float lineWidth=1;
	GLU glu = new GLU();
	private volatile boolean clearEnabled = false;

	/**
	 * Create a new Series object with <code>capacity</code>.
	 * @param dimensions the number of dimensions (2 or 3)
	 * @param capacity max number of points
	 */
	public Series(int dimensions, int capacity) {
		dimension = dimensions;
		this.capacity = capacity;
		elementSize = Float.SIZE / 8;
		cache = Buffers.newDirectFloatBuffer(dimension * capacity);
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
	synchronized public void add(float x, float y) {
		// data is added here and drained by the draw method as it is copied to the opengl buffer
		assert dimension == 2;
		if (cache.position() >= (cache.capacity() - 2)) {
			return; // have to wait and drop some data
			// TODO when we render from the buffer, we need to clear the buffer here or set position to 0
		}
		cache.put(x);
		cache.put(y);
	}

	/**
	 * Add a data item to the series cache.
	 */
	synchronized public void add(float x, float y, float z) {
		assert dimension == 3;
		if (cache.position() >= (cache.capacity() - 3)) {
			return; // have to wait and drop some data
		}
		cache.put(x);
		cache.put(y);
		cache.put(z);
	}

	/**
	 * Set the capacity of the series cache.
	 */
	synchronized public void setCapacity(int capacity) {
		this.capacity = capacity;
		cache = Buffers.newDirectFloatBuffer(dimension * capacity);
	}

	/** Schedules a clear of existing points in vertex buffer on the next {@code display()}.
	 * Call {@code clear()} if you want to clear out existing points before drawing
	 * new ones that you have added to the Series.
	 */
	synchronized public void clear() {
		clearEnabled = true;
	}

	/** Utility method to check for GL errors. Prints stacked up errors up to a limit.
    @param g the GL context
    @param glu the GLU used to obtain the error strings
    @param msg an error message to log to e.g., show the context
	 */
	public void checkGLError(GL2 g, GLU glu, String msg) {
		if (g == null) {
			log.warning("null GL");
			return;
		}
		int error = g.glGetError();
		int nerrors = 3;
		while ((error != GL.GL_NO_ERROR) && (nerrors-- != 0)) {
			StackTraceElement[] trace = Thread.currentThread().getStackTrace();
			if (trace.length > 1) {
				String className = trace[2].getClassName();
				String methodName = trace[2].getMethodName();
				int lineNumber = trace[2].getLineNumber();
				log.warning("GL error number " + error + " " + glu.gluErrorString(error) + " : " + msg + " at " + className + "." + methodName + " (line " + lineNumber + ")");
			} else {
				log.warning("GL error number " + error + " " + glu.gluErrorString(error) + " : " + msg);
			}
			//             Thread.dumpStack();
			error = g.glGetError();
		}
	}
	//    /**
	//     * @return the lineWidth
	//     */
	//    public float getLineWidth() {
	//        return lineWidth;
	//    }
	//
	//    /**
	//     * @param lineWidth the lineWidth to set
	//     */
	//    public void setLineWidth(float lineWidth) {
	//        this.lineWidth = lineWidth;
	//    }
	private boolean hasBufferExtension = false, checkedBufferExtension = false;

	/**
	 * Flushes data to opengl graphics device and draws the vertices.
	 * The gl object must be always the same; if not the existing one is discarded and a new one obtained to bind a vertex buffer to it.
	 * @param gl the OpenGL context (must be identical between calls)
	 * @param method the method of drawing the series line segments, e.g. <code>GL2.GL_LINE_STRIP</code>.
	 */
	synchronized public void draw(GL2 gl, int method) {

		if (!checkedBufferExtension) {
			log.info("checking once to see if vertex buffer extensions available (OpenGL 1.5+)");
			String glVersion = gl.glGetString(GL.GL_VERSION);

			hasBufferExtension = gl.isExtensionAvailable("GL_VERSION_1_5");
			checkedBufferExtension = true;
			log.info("Open GL version " + glVersion + ", gl.isExtensionAvailable(\"GL_VERSION_1_5\") = " + hasBufferExtension);
		}

		/* bind to gl object if necessary (implicit 2nd phase constructor) */
		if (this.gl == null) {
			gl.glEnableClientState(GLPointerFunc.GL_VERTEX_ARRAY);
			int[] bufferIds = new int[1];
			if (hasBufferExtension) {
				gl.glGenBuffers(1, bufferIds, 0);   // create buffer id
				bufferId = bufferIds[0];
			}
		} else if (this.gl != gl) { // error: cannot bind to multiple devices
			log.warning("Chart data series: Expected the same GL object! this.gl=" + this.gl + " but called gl=" + gl+". Discarding GL context to make a new one");
			this.gl = null;
			return;
		}
		if (hasBufferExtension) {
			//            gl.glLineWidth(lineWidth);
			gl.glBindBuffer(GL.GL_ARRAY_BUFFER, bufferId);
			int add = cache.position();   // check for new data...
			if ((add > 0) || (this.gl == null) ||clearEnabled) {    // ...and transfer them to opengl buffer if necessary
				cache.position(0);
				if ((elementsCount >= capacity) || clearEnabled) {
					this.gl = null; // if we filled up this buffer to our limit, null the gl to force us to make a new data buffer
					elementsCount = 0;
				}
				if ((this.gl == null) || clearEnabled) {
					// if clear is enabled, then just allocated a new buffer pointer and bind it
					gl.glBufferData(GL.GL_ARRAY_BUFFER, dimension * capacity * elementSize, cache, GL.GL_STATIC_DRAW);   // create buffer and flush
					gl.glBindBuffer(GL.GL_ARRAY_BUFFER, bufferId);
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
			/* flush data to opengl device if necessary.
            if we don't have vertex buffer extension, we must render all the data again, from the cache itself. */
		} else { // no GPU buffer extension, must render cache as host vertex buffer
			if (clearEnabled ) {
				if(vertices!=null) {
					vertices.clear();
				}
			}
			if ((vertices == null) || clearEnabled) {
				vertices = Buffers.newDirectFloatBuffer(dimension * capacity); // allocates direct buffer
			}
			if (this.gl == null) {
				this.gl = gl;
			}
			//            gl.glLineWidth(lineWidth);
			if (cache.position() > 0) { // if new points have been added, copy new values to end of vertices buffer
				try {
					vertices.put((FloatBuffer) cache.flip()); // copy them to the vertices
				} catch (BufferOverflowException e) {
					vertices.clear(); // if vertices is full, just clear it and start over
				}
				cache.clear(); // ready for new data to be put here
			}
			int elements = vertices.position();  // this many values to plot
			vertices.position(0); // point to start
			gl.glVertexPointer(2, GL.GL_FLOAT, 0, vertices);   // tell gl where to look
			gl.glDrawArrays(method, 0, elements / dimension); // draw the vertices
			vertices.position(elements); // continue adding from here
		}
		checkGLError(this.gl, glu, "after Series draw");
		clearEnabled = false;
	}

	public int getCapacity() {
		return capacity;
	}
}
