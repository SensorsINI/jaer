/*
 * VectorFieldChart.java
 *
 * A VectorFieldChart displays vectors on an equidistant grid.
 *
 * Semester project Matthias Schrag, HS07
 */

package ch.unizh.ini.caviar.util.chart;

import java.awt.Color;
import java.awt.Dimension;
import javax.media.opengl.GL;

/**
 * The VectorFieldChart class.
 * A VectorFieldChart displays vectors on an equidistant grid.
 */
public class VectorFieldChart extends Chart {
    
    /** The dimension of the chart */
    protected int sizeX;
    protected int sizeY;
    
    /**
     * Create a new VectorFieldChart.
     */
    public VectorFieldChart() {
        super();
    }
    
    /**
     * Create a new VectorFieldChart.
     */
    public VectorFieldChart(String title) {
        super(title);
    }
    
    public Dimension getPreferredSize() {
        Dimension parentSize = getParent().getSize();
        // return quadratic area
        if (parentSize.width < parentSize.height) {
            return new Dimension(parentSize.width, parentSize.width);
        } else {
            return new Dimension(parentSize.height, parentSize.height);
        }
    }
    
    /**
     * Create the components.
     */
    protected void createComponents(GL gl) {
    }
    
    /**
     * Layout the components.
     */
    protected void layoutComponents(GL gl, int x, int y, int width, int height) {
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
    }
    
    protected void drawDecoration(GL gl) {
    }
    
    /**
     * A test method.
     */
    public static void main(String[] args) {
        VectorFieldChart chart = new VectorFieldChart("Test");
//        chart.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
//        chart.setInsets(new Insets(10, 10, 10, 10));
        chart.setBackground(Color.YELLOW);
        VectorSeries series = new VectorSeries(3, 3);
        series.set(0, 1.0f, 2.0f);
        series.set(1, 0.0f, 2.0f);
        Axis xAxis = new Axis(0, 5);
        Axis yAxis = new Axis(0, 5);
        Axis[] axes = new Axis[] {xAxis, yAxis};
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
