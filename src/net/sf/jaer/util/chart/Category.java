/*
 * Category.java
 *
 * A Category is a view of a data series in a chart.
 *
 * Semester project Matthias Schrag, HS07
 */

package net.sf.jaer.util.chart;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL2ES1;
import java.util.Arrays;


/**
 * The Category class. A Category is a view of a data series in a chart.
 */
public class Category {

	public static final Axis DUMMY_AXIS = new Axis(0, 1);

	protected Series data;
	protected double[] transform;

	/** The color of the series. */
	protected float[] color;
	protected float lineWidth = 1f;

	/**
	 * Get the value of lineWidth
	 *
	 * @return the value of lineWidth
	 */
	public float getLineWidth() {
		return lineWidth;
	}

	/**
	 * Set the value of lineWidth
	 *
	 * @param lineWidth new value of lineWidth
	 */
	public void setLineWidth(float lineWidth) {
		this.lineWidth = lineWidth;
	}


	/** The axes of the series. */
	public Axis[] axes;

	/**
	 * Create a new Category
	 */
	public Category(Series data, Axis[] axes) {
		assert data != null;

		this.data = data;
		transform = new double[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};  // load identity
		color = new float[3];
		// copy axes; avoid null axes
		this.axes = (axes != null) ? Arrays.copyOf(axes, 3) : new Axis[3];
		if (this.axes[0] == null) {
			this.axes[0] = DUMMY_AXIS;
		}
		if (this.axes[1] == null) {
			this.axes[1] = DUMMY_AXIS;
		}
		if (this.axes[2] == null) {
			this.axes[2] = DUMMY_AXIS;
		}
	}

	/**
	 * Draw the category.
	 * Buffer series onto opengl device.
	 */
	public void draw(GL2 gl) {
		assert gl != null;

		/* set drawing parameters */
		gl.glColor3fv(color, 0);
		gl.glLineWidth(lineWidth);
		/* set cliping area for body: left, right, bottom, top. */
		gl.glClipPlane(GL2ES1.GL_CLIP_PLANE0, new double[] {1.0, 0.0, 0.0, 0.0}, 0);
		gl.glClipPlane(GL2ES1.GL_CLIP_PLANE1, new double[] {-1.0, 0.0, 0.0, 1.0}, 0);
		gl.glClipPlane(GL2ES1.GL_CLIP_PLANE2, new double[] {0.0, 1.0, 0.0, 0.0}, 0);
		gl.glClipPlane(GL2ES1.GL_CLIP_PLANE3, new double[] {0.0, -1.0, 0.0, 1.0}, 0);
		/* transform and draw series inside clipping area*/
		gl.glPushMatrix();
		gl.glScaled(1/axes[0].size, 1/axes[1].size, 1/axes[2].size);
		gl.glTranslated(-axes[0].min, -axes[1].min, -axes[2].min);
		gl.glMultMatrixd(transform, 0);
		gl.glEnable(GL2ES1.GL_CLIP_PLANE0);
		gl.glEnable(GL2ES1.GL_CLIP_PLANE1);
		gl.glEnable(GL2ES1.GL_CLIP_PLANE2);
		gl.glEnable(GL2ES1.GL_CLIP_PLANE3);
		data.draw(gl, GL2.GL_LINE_STRIP);
		gl.glDisable(GL2ES1.GL_CLIP_PLANE0);
		gl.glDisable(GL2ES1.GL_CLIP_PLANE1);
		gl.glDisable(GL2ES1.GL_CLIP_PLANE2);
		gl.glDisable(GL2ES1.GL_CLIP_PLANE3);
		gl.glPopMatrix();
	}

	/**
	 * Set the data displayed by the category using some data transformation.
	 */
	public void setData(Series data) {
		assert data != null;

		this.data = data;
	}

	/**
	 * Get the data displayed by the category using some data transformation.
	 */
	public Series getData() {
		return data;
	}

	/**
	 * Set the transformation of the data.
	 */
	public void setDataTransformation(double[] transform) {
		assert transform != null;

		this.transform = Arrays.copyOf(transform, 16);
	}

	/**
	 * Get the transformation of the data.
	 */
	public double[] getDataTransformation() {
		//        return transform.clone();
		return transform;   // alias is used for speed up hack
	}

	/**
	 * Set the color.
	 */
	public void setColor(float[] color) {
		assert (color != null) && (color.length >= 3);

		this.color = color;
	}

	/**
	 * Get the color.
	 */
	public float[] getColor() {
		return color.clone();
	}


}
