/*
 * Chart.java
 *
 * A Chart is an abstraction all chart classes in the package.
 * It displays data series in categories onto a OpenGL device.
 *
 * Semester project Matthias Schrag, HS07
 */
package net.sf.jaer.util.chart;


import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL2ES1;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.awt.GLJPanel;
import com.jogamp.opengl.fixedfunc.GLMatrixFunc;
import com.jogamp.opengl.glu.GLU;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;


import com.jogamp.opengl.util.awt.TextRenderer;

/**
 * The Chart class.
 * A Chart is an abstraction all chart classes in the package.
 * It displays data series in categories onto a OpenGL device.
 */
public abstract class Chart extends GLJPanel {

	//    static Logger log=Logger.getLogger("chart");
	/** The title of the chart. */
	protected String title = "";
	/** The chart's categories. */
	protected Category[] categories = new Category[0];
	/** Indicator whether the chart is running. */
	protected boolean running;
	/** A GL util object. */
	protected GLU glu;
	/** The insets, top left bottom right. */
	protected Insets insets = new Insets(10, 10, 10, 10);
	/** The area to draw the chart body. */
	protected Rectangle bodyArea;

	/**
	 * Create a new chart.
	 */
	public Chart() {
		glu = new GLU();
		addGLEventListener(new Renderer());   // let chart draw itself
		setDoubleBuffered(true);
	}

	/**
	 * Create a new chart with given title.
	 */
	public Chart(String title) {
		this.title = title;
		glu = new GLU();
		addGLEventListener(new Renderer());   // let chart draw itself
	}

	/**
	 * Get the title.
	 */
	public String getTitle() {
		return title;
	}

	/**
	 * Set the title.
	 */
	public void setTitle(String title) {
		this.title = title;
	}

	/**
	 * Add a category to the chart.
	 * The series is aliased.
	 */
	public void addCategory(Category category) {
		//        assert !running;
		int count = categories.length;
		Category[] cats = Arrays.copyOf(categories, count + 1);
		cats[count] = category;
		categories = cats;
	}

	public void clear() {
		categories = new Category[0];
	}

	/**
	 * Get the insets.
	 */
	@Override
	public Insets getInsets() {
		return insets;
	}

	/**
	 * Set the insets.
	 */
	public void setInsets(Insets insets) {
		this.insets = insets;
	}

	/**
	 * Draw the background of the chart. The grid could be drawn by this method.
	 * An OpenGL list is created for this.
	 */
	protected void drawStaticBackground(GL2 gl) {
	}

	/**
	 * Create the components.
	 */
	protected abstract void createComponents(GL2 gl);

	/**
	 * Layout the components.
	 */
	protected abstract void layoutComponents(GL2 gl, int x, int y, int width, int height);

	/**
	 * Draw the decoration.
	 */
	protected abstract void drawDecoration(GL2 gl);

	/**
	 * The OpenGL Renderer class for chart objects.
	 */
	public class Renderer implements GLEventListener {

		/** The area to draw the title. */
		private Rectangle titleArea;
		/** A renderer for the title. */
		private TextRenderer titleRenderer;
		protected int gridId; // the opengl id to access drawing list to draw grid

		/**
		 * Initialize the view for OpenGL drawing.
		 */
		@Override
		public void init(GLAutoDrawable drawable) {
			running = true;
			GL2 gl = drawable.getGL().getGL2();

			// background color
			float[] bg = new float[4];
			getBackground().getColorComponents(bg);
			gl.glClearColor(bg[0], bg[1], bg[2], bg[3]);

			// title
			titleRenderer = new TextRenderer(new Font("Helvetica", Font.PLAIN, 16));
			Rectangle2D bounds = titleRenderer.getBounds(title);
			titleArea = new Rectangle((int) bounds.getWidth(), (int) bounds.getHeight());

			/* create grid drawing routine */
			gridId = gl.glGenLists(1);
			gl.glNewList(gridId, GL2.GL_COMPILE);
			drawStaticBackground(gl);
			gl.glEndList();

			createComponents(drawable.getGL().getGL2());
		}

		@Override
		public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
			GL2 gl = drawable.getGL().getGL2();
			gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
			gl.glLoadIdentity();
			glu.gluOrtho2D(0, width, 0, height); // left, right, bottom, top
			/* layout components */
			Insets insets = getInsets();
			bodyArea = new Rectangle(insets.left, insets.bottom, width - insets.left - insets.right, height - insets.bottom - insets.top);
			// layout title
			titleArea.x = (width - titleArea.width) / 2;
			titleArea.y = height - (insets.top / 2) - titleArea.height;
			bodyArea.height -= titleArea.height;

			layoutComponents(gl, x, y, width, height);

			/* set cliping area for body: left, right, bottom, top. */
			gl.glClipPlane(GL2ES1.GL_CLIP_PLANE0, new double[]{1.0, 0.0, 0.0, -bodyArea.x}, 0);
			gl.glClipPlane(GL2ES1.GL_CLIP_PLANE1, new double[]{-1.0, 0.0, 0.0, bodyArea.x + bodyArea.width}, 0);
			gl.glClipPlane(GL2ES1.GL_CLIP_PLANE2, new double[]{0.0, 1.0, 0.0, -bodyArea.y}, 0);
			gl.glClipPlane(GL2ES1.GL_CLIP_PLANE3, new double[]{0.0, -1.0, 0.0, bodyArea.x + bodyArea.height}, 0);
		}

		/**
		 * Draw the chart view onto OpenGL drawable.
		 * The chart is drawn in the rectangular area [0, 0, width-1, height-1].
		 */
		@Override
		public void display(GLAutoDrawable drawable) {
                        drawable.getContext().makeCurrent();
			GL2 gl = drawable.getGL().getGL2();
			gl.glClear(GL.GL_COLOR_BUFFER_BIT);
			gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
			gl.glLoadIdentity();

			// title
			titleRenderer.beginRendering(getWidth(), getHeight());
			titleRenderer.setColor(getForeground());
			titleRenderer.draw(title, titleArea.x, titleArea.y);
			titleRenderer.endRendering();

			// decoration (axes, etc.)
			drawDecoration(gl);

			/* draw body */
			gl.glTranslated(bodyArea.x, bodyArea.y, 0);
			gl.glScaled(bodyArea.width, bodyArea.height, 1);
			// draw background
			gl.glCallList(gridId);

			/* draw series */
			for (Category category : categories) {
				category.draw(gl);
			}
		}

		/**
		 * Do nothing on changed display.
		 */
		public void displayChanged(GLAutoDrawable drawable, boolean modeChanged, boolean deviceChanged) {
		}

		@Override
		public void dispose(GLAutoDrawable arg0) {
			// TODO Auto-generated method stub

		}
	}
}
