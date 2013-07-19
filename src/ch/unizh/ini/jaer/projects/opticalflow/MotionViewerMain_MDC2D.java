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

import javax.swing.SwingUtilities;

import ch.unizh.ini.jaer.projects.opticalflow.graphics.MotionViewer;
import ch.unizh.ini.jaer.projects.opticalflow.mdc2d.MDC2D;

/**
 * The starter for the optical flow chip demo of Andreas Steiner and Shih-Chii Liu using the MDC2D chip.
 * 
 *
 
 * @author  tobi
 */
public class MotionViewerMain_MDC2D{
    
    static final Logger log=Logger.getLogger("Main");
    public static void main(String[] args){
        
        Chip2DMotion chip=new MDC2D();
        final MotionViewer viewer=new MotionViewer(chip);
        SwingUtilities.invokeLater(new Runnable() { // we have to do this or sometimes we get a deadlock in AWT waiting on something, if the window is slow to appear
            public void run() {
                viewer.setVisible(true);
            }
        });

    }
}

