/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiotemporalcloseness.util;

/**
 *
 * @author matthias
 */
public class Coord {
    public double x;
    public double y;
    
    public Coord(double x, double y) {
        this.x = x;
        this.y = y;
    }
    
    public int getX() {
        return (int)this.x;
    }
    
    public int getY() {
        return (int)this.y;
    }
    
    @Override
    public String toString() {
        return this.x + " / " + this.y;
    }
}
