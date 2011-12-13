/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.util;

/**
 *
 * @author matthias
 */
public class Color {
    private static int counter = 0;
    private static Color [] colors = {new Color(0, 1, 0), new Color(1, 1, 0), new Color(0, 1, 1), new Color(1, 0, 1)};
    
    private double[] color;
    
    private Color() {
        this(Math.random(), Math.random(), Math.random());
    }
        
    public Color(double r, double g, double b) {
        this.color = new double[3];
        
        this.color[0] = r;
        this.color[1] = g;
        this.color[2] = b;
    }
    
    public double get(int index) {
        return this.color[index];
    }
    
    public float getFloat(int index) {
        return (float)this.get(index);
    }
    
    public static void reset() {
        counter = 0;
    }
    
    public static Color getColor() {
        if (counter < colors.length) {
            return colors[counter++];
        }
        return new Color();
    }
    
    public static Color getBlue() {
        return new Color(0, 0, 1);
    }
    
    public static Color getYellow() {
        return new Color(1, 1, 0);
    }
}
