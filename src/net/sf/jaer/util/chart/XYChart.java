/*
 * XYChart.java
 *
 * Semester project Matthias Schrag, HS07
 */
package net.sf.jaer.util.chart;


import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;


import com.jogamp.opengl.util.awt.TextRenderer;

/**
 * The XYChart class.
 */
public class XYChart extends Chart {

	protected boolean gridEnabled = true;

	/**
	 * Get the value of gridEnabled
	 *
	 * @return the value of gridEnabled
	 */
	public boolean isGridEnabled() {
		return gridEnabled;
	}

	/**
	 * Set the value of gridEnabled
	 *
	 * @param gridEnabled new value of gridEnabled
	 */
	public void setGridEnabled(boolean gridEnabled) {
		this.gridEnabled = gridEnabled;
	}
	protected String[] axesLabels;
	protected TextRenderer axisLabelRenderer;
	protected TextRenderer textRenderer;
	private Rectangle[] axisLabelAreas;

	/**
	 * Create a new XYChart.
	 */
	public XYChart() {
		super();
	}

	/**
	 * Create a new XYChart with given title.
	 */
	public XYChart(String title) {
		super(title);
	}

	/**
	 * Create the components.
	 */
	@Override
	protected void createComponents(GL2 gl) {
		axisLabelRenderer = new TextRenderer(new Font("Helvetica", Font.PLAIN, 12));
		textRenderer = new TextRenderer(new Font("Helvetica", Font.PLAIN, 10));

		axesLabels = new String[2];
		axisLabelAreas = new Rectangle[axesLabels.length];
		for (int i = 0; i < axesLabels.length; i++) {
			StringBuilder buf = new StringBuilder();
			//            for(Category s : categories) {
			Category s = categories[0];
			buf.append(s.axes[i].title);
			if (s.axes[i].unit != null) {
				buf.append(" [" + s.axes[i].unit + "]");
			}
			buf.append('\n');
			//            }
			String str = buf.toString();
			axesLabels[i] = str.substring(0, str.length() - 1);
			Rectangle2D bounds = axisLabelRenderer.getBounds(axesLabels[i]);
			axisLabelAreas[i] = new Rectangle((int) bounds.getWidth(), (int) bounds.getHeight());
		}
	}

	/**
	 * Draw the decoration.
	 */
	@Override
	protected void drawDecoration(GL2 gl) {
		axisLabelRenderer.beginRendering(getWidth(), getHeight());
		axisLabelRenderer.setColor(getForeground());
		axisLabelRenderer.draw(axesLabels[0], axisLabelAreas[0].x, axisLabelAreas[0].y);
		axisLabelRenderer.draw(axesLabels[1], axisLabelAreas[1].x, axisLabelAreas[1].y);
		axisLabelRenderer.endRendering();
	}

	/**
	 * Layout the components.
	 */
	@Override
	protected void layoutComponents(GL2 gl, int x, int y, int width, int height) {
		Insets insets = getInsets();
		// layout x-axis labels
		axisLabelAreas[0].x = (bodyArea.x + bodyArea.width) - axisLabelAreas[0].width;
		axisLabelAreas[0].y = insets.bottom / 2;
		bodyArea.y += axisLabelAreas[0].height;
		bodyArea.height -= axisLabelAreas[0].height;
		// layout y-axis labels
		axisLabelAreas[1].x = insets.left / 2;
		axisLabelAreas[1].y = (bodyArea.y + bodyArea.height) - axisLabelAreas[1].height;
		bodyArea.x += axisLabelAreas[1].width;
		bodyArea.width -= axisLabelAreas[1].width;
	}

	/**
	 * Draw the background of the chart. The grid could be drawn by this method.
	 * An OpenGL list is created for this.
	 */
	@Override
	protected void drawStaticBackground(GL2 gl) {
		float[] fg = new float[4];
		getForeground().getColorComponents(fg);
		gl.glColor3fv(fg, 0);
		gl.glBegin(GL.GL_LINE_LOOP);
		gl.glVertex2f(0.0f, 0.0f);
		gl.glVertex2f(1.0f, 0.0f);
		gl.glVertex2f(1.0f, 1.0f);
		gl.glVertex2f(0.0f, 1.0f);
		gl.glEnd();

		if (isGridEnabled()) {
			gl.glBegin(GL.GL_LINES);
			gl.glVertex2f(0.0f, 0.25f);
			gl.glVertex2f(1.0f, 0.25f);
			gl.glVertex2f(0.0f, 0.5f);
			gl.glVertex2f(1.0f, 0.5f);
			gl.glVertex2f(0.0f, 0.75f);
			gl.glVertex2f(1.0f, 0.75f);
			gl.glColor3f(0.5f, 0.5f, 0.5f);
			gl.glVertex2f(0.0f, 0.125f);
			gl.glVertex2f(1.0f, 0.125f);
			gl.glVertex2f(0.0f, 0.375f);
			gl.glVertex2f(1.0f, 0.375f);
			gl.glVertex2f(0.0f, 0.625f);
			gl.glVertex2f(1.0f, 0.625f);
			gl.glVertex2f(0.0f, 0.875f);
			gl.glVertex2f(1.0f, 0.875f);
			gl.glEnd();
		}
	}

	private static void delay(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException ex) {
			Logger.getLogger(XYChart.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	/**
	 * A test method.
	 */
	 public static void main(String[] args) {
		 Random r = new Random();
		 XYChart chart = new XYChart("Status");
		 //        chart.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		 chart.setInsets(new Insets(10, 10, 10, 10)); // top left bottom right
		 chart.setBackground(Color.YELLOW);
		 int NPOINTS = 100;
		 Series series = new Series(2);
		 series.add(0.0f, 0.0f);
		 series.add(0.5f, 1.0f);
		 series.add(0.8f, 0.8f);
		 series.add(1.0f, 0.0f);
		 Axis timeAxis = new Axis();
		 timeAxis.setTitle("dt");
		 timeAxis.setUnit("ms");
		 timeAxis.setRange(0.0, 1.0);
		 Axis ratio = new Axis(0, 1);
		 ratio.setTitle("ratio");
		 ratio.setUnit("%");
		 Axis[] axes = new Axis[]{timeAxis, ratio};
		 Category category = new Category(series, axes);
		 category.setColor(new float[]{1.0f, 0.0f, 0.0f});
		 //        series.setLineWidth(4);
		 chart.addCategory(category);
		 javax.swing.JFrame frame = new javax.swing.JFrame();
		 frame.setSize(800, 600);
		 frame.getContentPane().add(chart);
		 frame.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
		 frame.setVisible(true);
		 chart.repaint();
		 System.out.println("first paint");
		 delay(500);
		 timeAxis.setMaximum(10);
		 System.out.println("rescale y axis");
		 chart.repaint();
		 delay(500);
		 timeAxis.setMinimum(-3);
		 ratio.setMaximum(2);
		 series.clear();
		 chart.repaint();
		 System.out.println("cleared points");
		 delay(1000);
		 series.add(0.0f, r.nextFloat());
		 series.add(0.5f, r.nextFloat());
		 series.add(0.8f, r.nextFloat());
		 series.add(1.0f, r.nextFloat());
		 chart.repaint();
		 System.out.println("new x,y axes, new points");
		 delay(1000);
		 series.clear();
		 chart.repaint();
		 System.out.println("cleared points, starting strip chart");
		 float t = 0;
		 while (true) {
			 float y = r.nextFloat() + .5f;
			 float x = t;
			 t = t + 1;
			 series.add(x, y);
			 delay(30);
			 timeAxis.setMaximum(t);
			 timeAxis.setMinimum(t - NPOINTS);
			 timeAxis.setUnit(String.format("%f", t));
			 chart.repaint();
		 }

	 }
}
