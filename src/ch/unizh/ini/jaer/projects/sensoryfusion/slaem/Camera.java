/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.sensoryfusion.slaem;

import net.sf.jaer.chip.AEChip;

/**
 *
 * @author Christian
 */
public class Camera {
    
    int posX, posY, posZ;
            
    public Camera(AEChip chip){
        posX = chip.getSizeX()/2;
        posY = chip.getSizeY()/2;
        posZ = 0;
    }
    
}
