/*
 * VectorSeries.java
 *
 * A VectorSeries is a data series to store vectors.
 * The vectors can be accessed through indices.
 *
 * Semester project Matthias Schrag, HS07
 */

package net.sf.jaer.util.chart;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.fixedfunc.GLPointerFunc;

/**
 * The VectorSeries class.
 * * A VectorSeries is a data series to store vectors.
 * The vectors can be accessed through indices.
 */
public class VectorSeries extends Series {

	protected int sizeX;
	protected int sizeY;
	protected int sizeZ;

	/**
	 * Create a new VectorSeries in 2 dimensions of given size.
	 * @param sizeX number of x points
	 * @param sizeY number of y points
	 */
	public VectorSeries(int sizeX, int sizeY) {
		super(2, 2 * sizeX * sizeY);
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		elementsCount = capacity * dimension;

		for (int y = 0; y < sizeY; y++) {
			for (int x = 0; x < sizeX; x++) {
				// vector target (is changed in set-method)
				cache.put(y);
				cache.put(x);
				// vector position (is fixed)
				cache.put(y);
				cache.put(x);
			}
		}
		cache.position(0);
	}

	/**
	 * Create a new VectorSeries in 3 dimensions of given size.
	 */
	public VectorSeries(int sizeX, int sizeY, int sizeZ) {
		super(3, 2 * sizeX * sizeY * sizeZ);
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.sizeZ = sizeZ;
		elementsCount = capacity * dimension;

		for (int z = 0; z < sizeZ; z++) {
			for (int y = 0; y < sizeY; y++) {
				for (int x = 0; x < sizeX; x++) {
					// vector target (is changed in set-method)
					cache.put(z);
					cache.put(y);
					cache.put(x);
					// vector position (is fixed)
					cache.put(z);
					cache.put(y);
					cache.put(x);
				}
			}
		}
		cache.position(0);
	}


	/**
	 * Set a vector in the series cache.
	 * @param position the element number
	 * @param x the x value
	 * @param y the y value
	 */
	public void set(int position, float x, float y) {
		assert dimension == 2;

		int index = 2 * position * dimension;
		cache.put(index++, x);
		cache.put(index, y);
	}

	/**
	 * Set a vector in the series cache.
	 * @param position the element number
	 * @param x the x value
	 * @param y the y value
        @param z the z value
	 */
	public void set(int position, float x, float y, float z) {
		assert dimension == 3;

		int index = 2 * position * dimension;
		cache.put(index++, x);
		cache.put(index++, y);
		cache.put(index, z);
	}

	/**
	 * Draw the vectors.
	 * The gl object must be always the same.
	 * <code>method</code> is ignored - the vectors are always drawn as lines.
	 */
	@Override
	public synchronized void draw(GL2 gl, int method) {
		gl.glEnableClientState(GLPointerFunc.GL_VERTEX_ARRAY);
		/* draw data series */
		cache.position(0);
		gl.glVertexPointer(dimension, GL.GL_FLOAT, 0, cache);   // draw vertices of float from cache beginning at 0
		gl.glDrawArrays(GL.GL_LINES, 0, elementsCount / dimension);
	}
}
