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
    private static int SPIKE_UPDATE_INTERVAL = 3000;
    private static long COMMAND_UPDATE_INTERVAL = 700;
    private static int NUM_CHANS = 32;
    private float[] ITDState=null;
    private int numITDBins=0;
    private int[] ITDBins=null;
    private int ITDBinWidth=0;
    private int spikeCount=0;
    private MSO mso=null;
    private int ii, jj, bin, count, cmdCount;
    private int cmd;
    private float direction=0;
    private float scale;
    private WowWeeRSHardwareInterface hw;
    private boolean startupCommandSequenceDone = false;
    private int startupCommandCount = 0;
    private long time, lastTime=0;
    
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
        time = System.currentTimeMillis();
        if (time>lastTime+COMMAND_UPDATE_INTERVAL) {
            lastTime = time;
            checkHardware();
            if(hw!=null) {
                if (!startupCommandSequenceDone) { //shut the damn thing up
                    switch (startupCommandCount) {
                        case 0:
                            cmd = rCommands.Toggle_Sensors;
                            break;
                        case 1:
                        case 2:
                        case 3:
                            cmd = rCommands.Volume_Down;
                            break;
                        default:
                            startupCommandSequenceDone = true;
                            System.out.println("Done startup command sequence.");
                            /*
                            cmd = rCommands.Toggle_Activity_Level_3;
                            cmd = rCommands.Toggle_Awareness_3;
                            cmd = rCommands.Toggle_Aggression_3;
                             */
                            
                    }
                } else { // normal behavior
                    count=mso.getNumBins();
                    if (count != numITDBins) {
                        System.out.println("Bins changed!  Allocating new memory");
                        allocateITDState();
                    }
                    direction = computeDirection();
                    if (direction < 30)
                        cmd = rCommands.Rotate_Counter_Clockwise;
                    else if (direction > 30)
                         cmd = rCommands.Rotate_Clockwise;
                    else
                         cmd = rCommands.Stop;
                }
            hw.sendWowWeeCmd((short)cmd);
            System.out.println("Send command - "+ HexString.toString((short) cmd));
            }
        }

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
        ITDBinWidth = mso.getBinWidth();
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
        
        gl.glVertex3f(direction/(float)ITDBinWidth+(float)(numITDBins/2),0,0);
        gl.glVertex3f(direction/(float)ITDBinWidth+(float)(numITDBins/2),1,0);
        
        gl.glColor3d(0, 0, .6);
        gl.glVertex3i(0,0,0);
        gl.glVertex3i(0,1,0);
        
        gl.glColor3d(0, 0, .6);
        gl.glVertex3i(numITDBins,0,0);
        gl.glVertex3i(numITDBins,1,0);
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
