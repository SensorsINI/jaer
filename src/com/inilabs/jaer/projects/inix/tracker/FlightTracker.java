/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.inilabs.jaer.projects.inix.tracker;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;

import ch.unizh.ini.jaer.hardware.pantilt.*; 
import com.jogamp.opengl.GL;
import java.util.Observable;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
/**
 * Demonstrates tracking object(s) in flight.
 * Sep 2024
 * 
 * @author tobi, rjd
 */

@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
@Description("Tracks target(s) in flight.")

public class FlightTracker extends RectangularClusterTracker {
    RectangularClusterTracker.Cluster targetCluster = null;
    float targetArealDensity = 1.0f ;
    float targetWidth = 1.0f ;
        
	public FlightTracker(AEChip chip) {
		super(chip);
 //               getSupport().addPropertyChangeListener(this);
	}

       
    @Override
    public void update(Observable o, Object arg) {
        if (o == this) {
            UpdateMessage msg = (UpdateMessage) arg;
            updateClusterList(msg.timestamp);
            updateTargetParams();
        }
    }
        
    public void updateTargetParams() {
        if(getNumClusters()>0) {
      // here we should rank the potential targets      
          targetCluster=getClusters().get(0);
      // here we should use the lowpassed radius as an estimate of distance (given expected drone size)    
          targetWidth = targetCluster.getRadius()*2f ;  
        }
    }   
    
    public float getTargetArealDensity() {
        return targetArealDensity;
    }
    
    public float getTargetWidth() {
        return targetWidth;
    }
    
    
}
