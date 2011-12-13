/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.data;

/**
 *
 * @author matthias
 * 
 * This class has to test whether the rotation matrix works.
 */
public class TestRotation {
    
    
    public static void main(String [] args) {
        Matrix rotation = Matrix.getRotation2D((float)Math.PI);
        Vector r = Vector.getDefault(2);
        r.set(0, 1);
        r.set(1, 0);
        
        System.out.println(rotation.toString());
        System.out.println(rotation.multiply(r).toString());
    }
}
