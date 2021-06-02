/*
 * Axis.java
 *
 * An axis is used to scale data in a chart.
 *
 * Semester project Matthias Schrag, HS07
 */

package net.sf.jaer.util.chart;

/**
 *
 * @author Matthias
 */
public class Axis {
    
    /** The title (variable name) of the axis */
    public String title;
    /** The unit */
    public String unit;
    /** A description */
    public String desc;
    
    public double min;
    public double size;
    
    /**
     * Create a new axis with extrema <code>min</code> and <code>max</code>.
     */
    public Axis(double min, double max) {
        this.min = min;
        this.size = max - min;
    }
    
    public Axis() {
        this(0, 1);
    }
    
    public void setRange(double min, double max) {
        this.min = min;
        this.size = max - min;
    }
    
    public void setMinimum(double min) {
        this.size -= min - this.min;
        this.min = min;
    }
    
    public double getMinimum() {
        return min;
    }
    
    public void setMaximum(double max) {
        this.size = max - min;
    }
    
    public double getMaximum() {
        return min + size;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setUnit(String unit) {
        this.unit = unit;
    }
    
    public String getUnit() {
        return unit;
    }
    
    public void setDescription(String desc) {
        this.desc = desc;
    }
    
    public String getDescription() {
        return desc;
    }
    
}
