/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.graphics;


import ch.unizh.ini.jaer.chip.multicamera.MultiDVS128CameraChip;
import ch.unizh.ini.jaer.chip.multicamera.MultiDavisCameraChip;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import static com.jogamp.opengl.GL2ES3.GL_COLOR;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.glu.GLU;

import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.DisplayMethod;
import net.sf.jaer.graphics.DisplayMethod3D;
import net.sf.jaer.chip.Chip2D;

import com.jogamp.opengl.util.gl2.GLUT;
import eu.seebetter.ini.chips.davis.DavisBaseCamera;
import eu.seebetter.ini.chips.davis.DavisBaseCamera.DavisDisplayMethod;
import java.awt.geom.Point2D;
import java.util.Iterator;
import net.sf.jaer.JAERViewer;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.MultiCameraApsDvsEvent;
import net.sf.jaer.graphics.ChipCanvas.Zoom;
import net.sf.jaer.stereopsis.MultiCameraHardwareInterface;

/**
 * Displays events in 3D space
 * @author Gemma
 */
public class MultiViewMultiCamera extends DisplayMethod implements DisplayMethod3D{
    
    private int NUM_CAMERAS; 

   /**
     * Creates a new instance of 3DSpaceDisplayMethod
     */
    public MultiViewMultiCamera(ChipCanvas chipCanvas) {
        super(chipCanvas);
    }

    GLUT glut = null;
    GLU glu = null;

    @Override
	public void display(GLAutoDrawable drawable) {
            
            Chip2D multichip = getChipCanvas().getChip();
            NUM_CAMERAS=getNumberOfCamera(multichip);
            GL2 gl = drawable.getGL().getGL2();

            ChipCanvas multicanvas= this.getChipCanvas();
            
            
            
            Chip2D chipType;
            if (multichip instanceof MultiDavisCameraChip){
                chipType= ((MultiDavisCameraChip)multichip).getChipType();                
            }else {
                chipType=multichip;
            }
    
            AEFrameChipRenderer[] davisRenderercameras= new AEFrameChipRenderer[NUM_CAMERAS];
            EventPacket[] camerasPacket= new EventPacket[NUM_CAMERAS];
            EventPacket multicamerapacket = (EventPacket) multichip.getLastData();
            
            if (multicamerapacket == null) {
                log.warning("null packet to render");
                gl.glPopMatrix();
                return;
            }
            int n = multicamerapacket.getSize();
            if (n == 0) {
               gl.glPopMatrix();
                 return;
            }
            
            Iterator evItr = multicamerapacket.iterator();
            
            for(int i=0; i<n; i++) {
                Object e = evItr.next();
                MultiCameraApsDvsEvent ev = (MultiCameraApsDvsEvent) e;
                int camera= ev.camera;
                camerasPacket[camera].elementData[i]=ev;    
            }
            
            for(int c=0; c<NUM_CAMERAS; c++) {
                davisRenderercameras[c]=new AEFrameChipRenderer((AEChip) chipType);
                davisRenderercameras[c].render(camerasPacket[c]);
            }
            

//            DavisDisplayMethod davisdisplaymethod=davisbasecamera.new DavisDisplayMethod(davisbasecamera);
//            getChipCanvas().setDisplayMethod(davisdisplaymethod);
        

        


    }

    private int getNumberOfCamera(Chip2D chip){
        int n=2;
        AEChip aechip=(AEChip) chip;
        if (chip instanceof MultiDavisCameraChip){
            MultiDavisCameraChip chipFound=(MultiDavisCameraChip)chip;
            n=chipFound.NUM_CAMERAS;
        }else if (chip instanceof MultiDVS128CameraChip){
            MultiDVS128CameraChip chipFound=(MultiDVS128CameraChip)chip;
            n=chipFound.NUM_CAMERAS;
        }
        else{
            JAERViewer JAERV=aechip.getAeViewer().getJaerViewer();    
            n=JAERV.getNumViewers();
        }
        return n;
    }
}
