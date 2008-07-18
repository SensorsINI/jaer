/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.ine.telluride.cochlea;


import ch.unizh.ini.caviar.chip.AEChip;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import ch.unizh.ini.caviar.graphics.FrameAnnotater;
import ch.unizh.ini.caviar.hardwareinterface.HardwareInterfaceException;
import ch.unizh.ini.caviar.util.HexString;
import com.sun.opengl.util.GLUT;
import java.awt.Graphics2D;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.glu.GLU;
import org.ine.telluride.wowwee.RoboQuadCommands;
import org.ine.telluride.wowwee.WowWeeRSHardwareInterface;

/**
 * Calculates ITD from binaural cochlea input
 * 
 * @author ahs (Andrew Schwartz, MIT)
 */
public class auditoryReflex extends EventFilter2D implements FrameAnnotater {
    
    private boolean drawOutput=getPrefs().getBoolean("MSO.drawOutput",true);
    {setPropertyTooltip("drawOutput", "Enable drawing");}
    
    private RoboQuadCommands rCommands;
    private static int SPIKE_UPDATE_INTERVAL = 1000;
    private static int NUM_CHANS = 32;
    private float[] ITDState=null;
    private int numITDBins=0;
    private int[] ITDBins=null;
    private int spikeCount=0;
    private MSO mso=null;
    private int ii, jj, bin, count;
    private int curCommand=0, nextCommand;
    private float direction;
    private float scale;
    private WowWeeRSHardwareInterface hw;
    private boolean shutUp = false;
    
    @Override
    public String getDescription() {
        return "Computes ITD of incoming binaural signal";
    }

    public auditoryReflex(AEChip chip) {
        super(chip);
        initFilter();
    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!isFilterEnabled()) {
            return in;
        }
        if(!checkMSO()){
            throw new RuntimeException("Can't find prior ANFSpikeBuffer in filter chain");
        }
        if(in==null) {
            return in;
        }
        for(Object o : in) {
            spikeCount++;
            if (spikeCount==SPIKE_UPDATE_INTERVAL) {
                determineBehavior();
                spikeCount = 0;
            }
        }
        return in;
    }

    public void determineBehavior() {
        count=mso.getNumBins();
        if (count != numITDBins) {
            System.out.println("Bins changed!  Allocating new memory");
            allocateITDState();
        }
        direction = computeDirection();
        
//        try {
            checkHardware();
            if(hw!=null) {
                if (!shutUp) {
                    hw.sendWowWeeCmd((short) rCommands.Volume_Down);
                    hw.sendWowWeeCmd((short) rCommands.Volume_Down);
                    hw.sendWowWeeCmd((short) rCommands.Volume_Down);
                    shutUp = true;
                }
                if (direction < 25)
                    nextCommand = rCommands.Rotate_Counter_Clockwise;
                else if (direction > 25)
                     nextCommand = rCommands.Rotate_Clockwise;
                else
                    nextCommand = rCommands.Stop;
                
                if (nextCommand != curCommand) {
                    System.out.println("New command - "+ HexString.toString((short) curCommand));
                    curCommand = nextCommand;
                    hw.sendWowWeeCmd((short)curCommand);
                }
            System.out.print(".");
            System.out.flush();
            }
//        } catch(Exception e) {
//            log.warning(e.toString());
//        }


        return;
    }

    @Override
    public Object getFilterState() {
        return null;
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }

    
    // --- private functions
    private boolean checkMSO() {
        if(mso==null) {
            mso=(MSO) chip.getFilterChain().findFilter(MSO.class);            
            return mso!=null;
        } else {
            return true;
        }
    }
    
        void checkHardware() {
        if(hw==null) {
            hw=new WowWeeRSHardwareInterface();
        }
        try {
            if(!hw.isOpen()) {
                hw.open();
            }
        } catch(HardwareInterfaceException e) {
            log.warning(e.toString());
        }
    }
    
    private void allocateITDState() {
        System.out.println("Allocate ITD state");
        numITDBins = mso.getNumBins();
        ITDState = new float[numITDBins];
    }
    
    private float computeDirection() {
        ITDState = mso.getITDState();
        ITDBins = mso.getITDBins();
        numITDBins = mso.getNumBins();
        scale = 0;
        direction = 0;
        for (bin=0;bin<numITDBins;bin++) {
            direction += ITDBins[bin]*ITDState[bin];
            scale += ITDState[bin];
        }
        direction = direction/scale;
        return direction;
    }
    
    // --- OpenGL
    public void annotate(float[][][] frame) {
    }

    public void annotate(Graphics2D g) {
    }
    private GLU glu=new GLU();

    public void annotate(GLAutoDrawable drawable) {
        
        
        if(!isFilterEnabled() || !drawOutput) {
            return;
        //   if(isRelaxed) return;
        }
        GL gl=drawable.getGL();
        gl.glPushMatrix();

        //draw ITD histogram
        gl.glBegin(gl.GL_LINES);
        gl.glColor3d(0.8, 0, 0);
        
        gl.glVertex3f(direction+(float)(numITDBins/2),0,0);
        gl.glVertex3f(direction+(float)(numITDBins/2),1,0);
        
        gl.glColor3d(0, 0, 1);
        gl.glVertex3f((float)(numITDBins/2),0,0);
        gl.glVertex3f((float)(numITDBins/2),1,0);
        
        gl.glColor3d(0, 1, 1);
        gl.glVertex3i(0,1,0);
        gl.glVertex3i(0,2,0);
        
        gl.glColor3d(1, 1, 0);
        gl.glVertex3i(numITDBins,1,0);
        gl.glVertex3i(numITDBins,2,0);
        
        gl.glEnd();
            
        gl.glPopMatrix();
        }
    
    // --- getters and setters
    
    public boolean getDrawOutput() {
            return drawOutput;
    }
    public void setDrawOutput(boolean drawOutput) {
        this.drawOutput = drawOutput;
        getPrefs().putBoolean("MSO.drawOutput",drawOutput);
    }

    
}
