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
public class FlightTracker extends RectangularClusterTracker implements FrameAnnotater {
	RectangularClusterTracker tracker;
	public FlightTracker(AEChip chip) {
		super(chip);
		tracker=new RectangularClusterTracker(chip);
                tracker.getSupport().addPropertyChangeListener(this);
	}

	@Override
	public void resetFilter() {
		tracker.resetFilter();
	}

	@Override
	public void initFilter() {
		resetFilter();
	}

	public void annotate(float[][][] frame) {
	}

	public void annotate(Graphics2D g) {
	}

	@Override
	public void annotate(GLAutoDrawable drawable) {
		if(!isFilterEnabled()) {
			return;
		}
		tracker.annotate(drawable);
	}

       
	
}
