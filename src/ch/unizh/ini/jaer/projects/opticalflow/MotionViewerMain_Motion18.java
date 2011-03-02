/*
 * Main.java
 *
 * Created on December 7, 2006, 8:55 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright December 7, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package ch.unizh.ini.jaer.projects.opticalflow;

import ch.unizh.ini.jaer.projects.opticalflow.mdc2d.*;
import ch.unizh.ini.jaer.projects.opticalflow.motion18.*;
import ch.unizh.ini.jaer.projects.opticalflow.graphics.*;
import java.util.logging.Logger;

/**
 * The starter for the optical flow chip demo.q
 *
 
 * @author  tobi
 */
public class MotionViewerMain_Motion18{
    
    static final Logger log=Logger.getLogger("Main");
    public static void main(String[] args){
        
        Chip2DMotion chip=new Motion18();
        MotionViewer viewer=new MotionViewer(chip);
        viewer.setVisible(true);

    }
}

