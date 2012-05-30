/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.sensoryfusion.slaem;

import java.util.*;

/**
 *
 * @author Christian
 */
public class Bodies {
    
    Vector<Body> objects;
    
    public Bodies(){
        objects = new Vector(20,5);
    }
    
    public void clear(){
        objects = new Vector();
    }
    
    public class Body{
        
        Edges edges;
        
        public Body(){
            //edges = new Edges();
            
        }
        
    }
    
}
