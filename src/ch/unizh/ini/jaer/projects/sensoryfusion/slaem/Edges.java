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
public class Edges {
    
    Vector<Edge> edges;
    
    public Edges(){
        edges = new Vector<Edge>(20,5);
    }
    
    public class Edge{
        
        EdgeVoxels voxels;
        
        public Edge(){
            voxels = new EdgeVoxels();
        }
    }
}
