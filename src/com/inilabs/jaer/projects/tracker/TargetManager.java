/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.inilabs.jaer.projects.tracker;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;

import com.jogamp.opengl.GL;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2DMouseAdaptor;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import com.jogamp.opengl.util.gl2.GLUT;
import net.sf.jaer.eventprocessing.tracking.ClusterInterface;
import net.sf.jaer.eventprocessing.tracking.ClusterPathPoint;
import net.sf.jaer.util.DrawGL;
import net.sf.jaer.util.EngineeringFormat;
import net.sf.jaer.util.filter.LowpassFilter;

import com.inilabs.jaer.gimbal.GimbalAimer;
import java.awt.Color;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeSupport;
import java.util.Iterator;
import java.util.List;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker.Cluster;

/**
 *  Provides gimbal control and testing. 
 *  
 * Displays gimbal limits of pan as box, and pose as cross hairs with current pose as text.
 * 
 * Mouse displays mouse location  chip x,y (pixels);  pan tilt in FOV (0-1);  deltaYaw, deltaTilt (degs) with respect to current pose.
 * 
 * Mouse click causes gimbal to pan to mouse location.
 * 
 * @author tobi, rjd
 */

@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
@Description("Rev 27Oct24:  Displays RS4 gimbal pose,  Pans to mouse-clicked FOV location.")
public class TargetManager extends EventFilter2DMouseAdaptor implements FrameAnnotater {
	RectangularClusterTracker tracker;
	GimbalAimer panTilt=null;
        Point2D.Float targetLocation = null;
        RectangularClusterTracker.Cluster targetCluster = null;
        
             private boolean mousePressed = false;
    private boolean shiftPressed = false;
    private boolean ctlPressed = false;
    private boolean altPressed = false;
    private Point mousePoint = null;
    private String who = "";  // the name of  this class, for locking gimbal access (if necessary) 
      private float [] rgb = {0, 0, 0, 0};

	public TargetManager(AEChip chip) {
		super(chip);
                                        targetLocation = new Point2D.Float(100, 100);
		
                                        FilterChain filterChain=new FilterChain(chip);
		tracker=new RectangularClusterTracker(chip);
                                        tracker.getSupport().addPropertyChangeListener(this);
		panTilt= new GimbalAimer(chip);
                                        panTilt.getSupport().addPropertyChangeListener(this);     
                                        filterChain.add(tracker);
		filterChain.add(panTilt);
		setEnclosedFilterChain(filterChain);
               
                                          who="TargetManager";
                                           support = new PropertyChangeSupport(this);  
	}


	@Override
	public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
		if(!isFilterEnabled()) {
			return in;
		}
		getEnclosedFilterChain().filterPacket(in);
//		if(panTilt.isLockOwned()) {
//			return in;
//		}
		if(tracker.getNumClusters()>0) {
			targetCluster=tracker.getClusters().get(0);
			if(targetCluster.isVisible()) {
				Point2D.Float p=targetCluster.getLocation();
                                                                                targetLocation = p;
				float[] xy={p.x, p.y, 1};
				try {
					setPanTiltVisualAimPixels(p.x, p.y);
				} catch(Exception ex) {
					log.warning(ex.toString());
				}
				panTilt.getGimbalBase().setLaserOn(true);
			} else {
				panTilt.getGimbalBase().setLaserOn(false);
			}
		} else {
			panTilt.getGimbalBase().setLaserOn(false);
		}

                return in;
	}

        
     // <editor-fold defaultstate="collapsed" desc="GUI button --Aim--">
    /** Invokes the calibration GUI
     * Calibration values are stored persistently as preferences.
     * Built automatically into filter parameter panel as an action. */
    public void doEnableGimbal() {
        panTilt.getGimbalBase().enableGimbal(true);
    }
    // </editor-fold>      
    
     // <editor-fold defaultstate="collapsed" desc="GUI button --Aim--">
    /** Invokes the calibration GUI
     * Calibration values are stored persistently as preferences.
     * Built automatically into filter parameter panel as an action. */
    public void doDisableGimbal() {
        panTilt.getGimbalBase().enableGimbal(false);
    }
    // </editor-fold>      
        

  public Cluster bestTargetCluster() {
     //  RectangularClusterTracker.Cluster thresholdCluster = new RectangularClusterTracker.Cluster();
     
    float eventThreshold = 2f;
    float  sizeThreshold  = 10f;
        
    if (tracker.getClusters() != null ) {
     
   // intitialize cluster with first in group
     Cluster bestCluster = tracker.getClusters().get(0);
      List<Cluster> clusters = tracker.getClusters();
      Iterator <Cluster>  clusterIterator = clusters.iterator();
   
      // check is there is a better custer than the first one
      while (clusterIterator.hasNext()) 
  {
    Cluster c = clusterIterator.next();
    if (c.getAvgEventRateHzPerPx() >= eventThreshold && c.getRadius() < sizeThreshold && c.getAvgEventRateHzPerPx() > bestCluster.getMeasuredAverageEventRate() ) {
        bestCluster = c;
       } 
    }
    if (bestCluster.getAvgEventRateHzPerPx() >= eventThreshold && bestCluster.getRadius() < sizeThreshold ) {
        targetCluster = bestCluster;
        targetCluster.setColor(Color.red);
    }  else { 
       // there is no suitable cluster
       targetCluster = null;
    }
    } else {
          // there is no suitable cluster
         targetCluster = null;
    }
    
    return targetCluster;   
    
  }        
        

        
        
         public void setPanTiltVisualAimPixels(float pan, float tilt) {
        // convert pixels to normalized (0-1) location
        float normalizedPan = pan/chip.getSizeX();
        float normalizedTilt = tilt/chip.getSizeY();
      //  targetCluster = null;  // debug
        if (targetCluster != null) {
        panTilt.getGimbalBase().setTargetEnabled(true);    
       // panTilt.getGimbalBase().setTarget(normalizedPan, normalizedTilt);
          panTilt.setPanTiltTarget(normalizedPan, normalizedTilt);
     //   targetCluster = null;
        } else {
            panTilt.getGimbalBase().setTargetEnabled(false);      
            panTilt.getGimbalBase().sendDefaultGimbalPose();
        }
    }

         // <editor-fold defaultstate="collapsed" desc="getter/setter for --PanTiltTarget--">
   
               

                 @Override
                 public void mouseClicked(MouseEvent e) {
                     Point p = this.getMousePixel(e);
                     setPanTiltVisualAimPixels(p.x, p.y);
                 }
              
       
	@Override
	public void resetFilter() {
		panTilt.resetFilter();
	}

	@Override
	public void initFilter() {
		panTilt.resetFilter();
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
       //         fmt.setPrecision(1); // digits after decimel point
        GL2 gl = drawable.getGL().getGL2(); // when we getString this we are already set up with updateShape 1=1 pixel,
        // at LL corner
        if (gl == null) {
          //  log.warn("null GL in TargetManager.annotate");
            return;
        }
                
                    //             super.annotate(drawable);
                    //    tracker.annotate(drawable);
                        if (targetCluster != null ) {
 try {
            gl.glPushMatrix();
            {
                    drawTargetLocation(gl);
            }
        } catch (java.util.ConcurrentModificationException e) {
            // this is in case cluster list is modified by real time filter during rendering of clusters
        //    log.warn("concurrent modification of target list while drawing ");
        } finally {
            gl.glPopMatrix();
        } 
                        }
    }

     

        
        /** Shows the transform on top of the rendered events.
	 *
	 * @param gl the OpenGL context.
	 */

         private void drawTargetLocation(GL2 gl) {
                                        float sx = chip.getSizeX() / 32;
	                  	// draw gimbal pose cross-hair 
		gl.glPushMatrix();
                                        gl.glPushAttrib(GL2.GL_CURRENT_BIT);
                                        gl.glTranslatef(targetLocation.x, targetLocation.y, 0);  
                                        gl.glColor3f(0, 1, 1);
                                            // text annoations on clusters, setup
                GLUT cGLUT = chip.getCanvas().getGlut();
                final int font = GLUT.BITMAP_TIMES_ROMAN_24;
                  gl.glRasterPos3f(0, sx, 0);
                cGLUT.glutBitmapString(font, String.format("Target #  1"));
                gl.glRasterPos3f(0, -sx, 0);
                cGLUT.glutBitmapString(font, String.format("Cluster # " + 
                        targetCluster.getClusterNumber()) );
                        drawCircle(gl, 0.0f, 0.0f,  sx, 10);
                                        
		gl.glPopMatrix();
         }
         
         private void drawCircle(GL2 gl, float cx, float cy, float radius, int segments) {
        gl.glBegin(GL2.GL_LINE_LOOP); // Use GL_LINE_LOOP to draw the outline of the circle
        for (int i = 0; i < segments; i++) {
            double theta = 2.0 * Math.PI * i / segments; // Calculate the angle for each segment
            float x = (float)(radius * Math.cos(theta));
            float y = (float)(radius * Math.sin(theta));
            gl.glVertex2f(x + cx, y + cy); // Set vertex positions relative to the center
        }
        gl.glEnd();
    }
         
         
         
        }
        