/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.multitracking;

import ch.unizh.ini.jaer.chip.multicamera.MultiDVS128CameraChip;
import ch.unizh.ini.jaer.chip.multicamera.MultiDavisCameraChip;
import ch.unizh.ini.jaer.chip.retina.DVS128;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import java.awt.geom.Point2D;
import java.util.Iterator;
import java.util.logging.LogManager;
import javafx.scene.paint.Color;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.MultiCameraApsDvsEvent;
import net.sf.jaer.event.MultiCameraEvent;
import net.sf.jaer.eventprocessing.tracking.MedianTracker;
import net.sf.jaer.graphics.FrameAnnotater;


/**
 *
 * @author Gemma
 */
public class ZebrafishMedianTracker extends MedianTracker implements FrameAnnotater {
    int MAX_NUMBER_OF_CAMERAS=8; //max number of camera for the MultiDavisChip
    int numCameras=0;
    int tauUs =getInt("tauUs", 1000); //Time constant in us (microseonds) of median location lowpass filter, 0 for instantaneous
    private float numStdDevsForBoundingBox = getFloat("numStdDevsForBoundingBox", 1f); //Multiplier for number of std deviations of x and y distances from median for drawing and returning bounding box
    int[] freePositionPacket= new int[MAX_NUMBER_OF_CAMERAS]; 
    private EventPacket[] camerasPacket=new EventPacket[MAX_NUMBER_OF_CAMERAS];
    private MedianTracker[] medianTrackers= new MedianTracker[MAX_NUMBER_OF_CAMERAS];
     
    /**
     * Creates a new instance of MedianTracker
     */
    public ZebrafishMedianTracker(AEChip chip) {
        super(chip);
        LogManager.getLogManager().reset();
        setPropertyTooltip("tauUs", "Time constant in us (microseonds) of median location lowpass filter, 0 for instantaneous");
        setPropertyTooltip("numStdDevsForBoundingBox", "Multiplier for number of std deviations of x and y distances from median for drawing and returning bounding box");

    }

    @Override
    public void resetFilter() {
    }


    @Override
    public void initFilter() {

    }

    @Override
    public EventPacket filterPacket(EventPacket in) {
        numCameras=0; //inizialization
               
        if ( !isFilterEnabled() ){
            return in;
        }
        
        int n = in.getSize();
        
        Iterator evItr = in.iterator();
        for(int i=0; i<n; i++) {
            Object e = evItr.next();
            if ( e == null ){
                log.warning("null event, skipping");
            }
            MultiCameraApsDvsEvent ev = (MultiCameraApsDvsEvent) e;
            if (ev.isSpecial()) {
                continue;
            }
            int camera= ev.camera;
            
            //Inizialization of the cameraPackets depending on how many cameras are connected
            //CameraPackets is an array of the EventPackets sorted by camera
            if(camera>=numCameras){
                numCameras=camera+1;
                for(int c=0; c<numCameras; c++) {
                    medianTrackers[c]= new MedianTracker(chip);
                    medianTrackers[c].setNumStdDevsForBoundingBox(numStdDevsForBoundingBox);
                    medianTrackers[c].setTauUs(tauUs);
                    camerasPacket[c]=new EventPacket();
                    camerasPacket[c].allocate(n);
                    camerasPacket[c].clear();
                }     
            }       
            
            //Allocation of each event in the new sorted Packet
            freePositionPacket[camera]=camerasPacket[camera].getSize();
            camerasPacket[camera].elementData[freePositionPacket[camera]]=ev;
            camerasPacket[camera].size=camerasPacket[camera].size+1;
        }
        
        //Median tracker for each sorted Packet
        for(int c=0; c<numCameras; c++) {
            medianTrackers[c].filterPacket(camerasPacket[c]);
        }
        
        return in; 
    }
    
    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (!isFilterEnabled()) {
            return;
        }

        GL2 gl = drawable.getGL().getGL2();
        gl.glPushMatrix();

        for (int c=0; c<numCameras; c++){
            medianTrackers[c].annotate(drawable);
            Float hue= (float) c*360/numCameras; // conversion in hue value, dependindin on the number of cameras
            Color color=Color.hsb(hue, 0.5f, 1f);
            gl.glColor3d(color.getRed(), color.getGreen(), color.getBlue());
            gl.glLineWidth(4);
            gl.glBegin(GL2.GL_LINE_LOOP);
            Point2D p = medianTrackers[c].getMedianPoint();
            Point2D s = medianTrackers[c].getStdPoint();
            int sx=chip.getSizeX();
            int sy=chip.getSizeY();
            gl.glVertex2d((c*sx/numCameras+p.getX()) - s.getX(), p.getY() - s.getY());
            gl.glVertex2d((c*sx/numCameras+p.getX()) + s.getX(), p.getY() - s.getY());
            gl.glVertex2d((c*sx/numCameras+p.getX()) + s.getX(), p.getY() + s.getY());
            gl.glVertex2d((c*sx/numCameras+p.getX()) - s.getX(), p.getY() + s.getY());
            gl.glEnd();
        }
        gl.glPopMatrix();
        
    }
    
    public int getTauUs() {
        return this.tauUs;
    }

    /**
     * @param tauUs the time constant of the 1st order lowpass filter on median
     * location
     */
    public void setTauUs(final int tauUs) {
        this.tauUs=tauUs;
        putInt("tauUs", tauUs);
    }
    
    public float getNumStdDevsForBoundingBox() {
        return this.numStdDevsForBoundingBox;
    }

    /**
     * @param numStdDevsForBoundingBox the numStdDevsForBoundingBox to set
     */
    public void setNumStdDevsForBoundingBox(float numStdDevsForBoundingBox) {
        this.numStdDevsForBoundingBox=numStdDevsForBoundingBox;
        putFloat("numStdDevsForBoundingBox",numStdDevsForBoundingBox);
    }

    
}
    
      

