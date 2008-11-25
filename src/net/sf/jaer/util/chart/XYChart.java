/*
 * XYChart.java
 *
 * Semester project Matthias Schrag, HS07
 */

package net.sf.jaer.util.chart;

import com.sun.opengl.util.j2d.TextRenderer;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import javax.media.opengl.GL;

/**
 * The XYChart class.
 */
public class XYChart extends Chart {
    
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
    protected void createComponents(GL gl) {
        axisLabelRenderer = new TextRenderer(new Font("Helvetica", Font.PLAIN, 12));
        textRenderer = new TextRenderer(new Font("Helvetica", Font.PLAIN, 10));
        
        axesLabels = new String[2];
        axisLabelAreas = new Rectangle[axesLabels.length];
        for (int i = 0; i < axesLabels.length; i++) {
            StringBuilder buf = new StringBuilder();
//            for(Category s : categories) {
            Category s = categories[0];
            buf.append(s.axes[i].title);
            if (s.axes[i].unit != null) buf.append(" [" + s.axes[i].unit + "]");
            buf.append('\n');
//            }
            String str = buf.toString();
            axesLabels[i] = str.substring(0, str.length()-1);
            Rectangle2D bounds = axisLabelRenderer.getBounds(axesLabels[i]);
            axisLabelAreas[i] = new Rectangle((int) bounds.getWidth(), (int) bounds.getHeight());
        }
    }
    
    /**
     * Draw the decoration.
     */
    protected void drawDecoration(GL gl) {
        axisLabelRenderer.beginRendering(getWidth(), getHeight());
        axisLabelRenderer.setColor(getForeground());
        axisLabelRenderer.draw(axesLabels[0], axisLabelAreas[0].x, axisLabelAreas[0].y);
        axisLabelRenderer.draw(axesLabels[1], axisLabelAreas[1].x, axisLabelAreas[1].y);
        axisLabelRenderer.endRendering();
    }
    
    /**
     * Layout the components.
     */
    protected void layoutComponents(GL gl, int x, int y, int width, int height) {
        Insets insets = getInsets();
        // layout x-axis labels
        axisLabelAreas[0].x = bodyArea.x - bodyArea.width;
        axisLabelAreas[0].y = insets.bottom/2;
        bodyArea.y += axisLabelAreas[0].height;
        bodyArea.height -= axisLabelAreas[0].height;
        // layout y-axis labels
        axisLabelAreas[1].x = insets.left/2;
        axisLabelAreas[1].y = bodyArea.y + bodyArea.height - axisLabelAreas[1].height;
        bodyArea.x += axisLabelAreas[1].width;
        bodyArea.width -= axisLabelAreas[1].width;
    }
    
    /**
     * Draw the background of the chart. The grid could be drawn by this method.
     * An OpenGL list is created for this.
     */
    protected void drawStaticBackground(GL gl) {
        float[] fg = new float[4];
        getForeground().getColorComponents(fg);
        gl.glColor3fv(fg, 0);
        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex2f(0.0f, 0.0f);
        gl.glVertex2f(1.0f, 0.0f);
        gl.glVertex2f(1.0f, 1.0f);
        gl.glVertex2f(0.0f, 1.0f);
        gl.glEnd();
        
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
    
    /**
     * A test method.
     */
    public static void main(String[] args) {
        XYChart chart = new XYChart("Status");
//        chart.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        chart.setInsets(new Insets(10, 10, 10, 10));
        chart.setBackground(Color.YELLOW);
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
        Axis[] axes = new Axis[] {timeAxis, ratio};
        Category category = new Category(series, axes);
        category.setColor(new float[] {1.0f, 0.0f, 0.0f});
        chart.addCategory(category);
        javax.swing.JFrame frame = new javax.swing.JFrame();
        frame.setSize(800, 600);
        frame.getContentPane().add(chart);
        frame.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }
    
}
