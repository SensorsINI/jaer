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
public class EdgeVoxels {
    public Vector<EdgeVoxel> voxels;
    
    public EdgeVoxels(){
        voxels = new Vector<EdgeVoxel>(200,20);
    }
    
    public class EdgeVoxel{
        int x,y,z;
        
    }
    
}
