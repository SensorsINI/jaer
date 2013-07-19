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

import java.util.logging.Logger;

import ch.unizh.ini.jaer.projects.opticalflow.graphics.MotionViewer;
import ch.unizh.ini.jaer.projects.opticalflow.mdc2d.MDC2D;
import ch.unizh.ini.jaer.projects.opticalflow.motion18.Motion18;

/**
 * The starter for the optical flow chip demo using Alan Stockers sensor built Pit Gebbers; see http://www.ini.uzh.ch/~tobi/studentProjectReports/gebbersUSBMotionBoard2007.pdf.
 *
 
 * @author  tobi
 */
public class MotionViewerMain{
    
    static final Logger log=Logger.getLogger("Main");
    public static void main(String[] args){
        
        Chip2DMotion chip=new Motion18();
        chip=new MDC2D();
        MotionViewer viewer=new MotionViewer(chip);
        viewer.setVisible(true);

    }
}

