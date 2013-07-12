/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.ine.telluride.jaer.cochlea;


import java.awt.Graphics2D;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.glu.GLU;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.util.HexString;

import org.ine.telluride.jaer.wowwee.RoboQuadCommands;
import org.ine.telluride.jaer.wowwee.WowWeeRSHardwareInterface;

/**
 * Calculates ITD from binaural cochlea input
 * 
 * @author ahs (Andrew Schwartz, MIT)
 */
@Description("Computes ITD of incoming binaural signal")
public class AuditoryReflex extends EventFilter2D implements FrameAnnotater {

	private boolean drawOutput=getPrefs().getBoolean("MSO.drawOutput",true);
	{setPropertyTooltip("drawOutput", "Enable drawing");}

	private RoboQuadCommands rCommands;
	private static int SPIKE_UPDATE_INTERVAL = 500;
	private static long COMMAND_UPDATE_INTERVAL = 700;
	private static int NUM_CHANS = 32;
	private float[] ITDState=null;
	private int numITDBins=0;
	private int[] ITDBins=null;
	private int ITDBinWidth=0;
	private int spikeCount=0;
	private MSO mso=null;
	private int ii, jj, bin, count;
	private int cmd, lastCmd=0;
	private float direction=0;
	private float scale;
	private WowWeeRSHardwareInterface hw;
	private boolean startupCommandSequenceDone = false;
	private int startupCommandCount = 0;
	private long time, lastTime=0;

	public AuditoryReflex(AEChip chip) {
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
		if (time>(lastTime+COMMAND_UPDATE_INTERVAL)) {
			lastTime = time;
			checkHardware();
			if(hw!=null) {
				if (!startupCommandSequenceDone) { //shut the damn thing up
					switch (startupCommandCount) {
						case 0:
							cmd = RoboQuadCommands.Toggle_Sensors;
							break;
						case 1:
						case 2:
						case 3:
							cmd = RoboQuadCommands.Volume_Down;
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
					startupCommandCount++;
					hw.sendWowWeeCmd((short)cmd);
					System.out.println("Send command - "+ HexString.toString((short) cmd));

				} else { // normal behavior
					count=mso.getNumBins();
					if (count != numITDBins) {
						System.out.println("Bins changed!  Allocating new memory");
						allocateITDState();
					}
					direction = computeDirection();
					if (direction < 30) {
						cmd = RoboQuadCommands.Rotate_Counter_Clockwise;
					}
					else if (direction > 30) {
						cmd = RoboQuadCommands.Rotate_Clockwise;
					}
					else {
						cmd = RoboQuadCommands.Stop;
					}

					if (cmd!=lastCmd) {
						lastCmd = cmd;
						hw.sendWowWeeCmd((short)cmd);
						System.out.println("Send command - "+ HexString.toString((short) cmd));
					}

				}
			}
		}

		return;
	}

	@Override
	public void resetFilter() {
		lastTime = 0;
		startupCommandSequenceDone = false;
		startupCommandCount = 0;
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

	@Override
	public void annotate(GLAutoDrawable drawable) {


		if(!isFilterEnabled() || !drawOutput) {
			return;
			//   if(isRelaxed) return;
		}
		GL2 gl=drawable.getGL().getGL2();
		gl.glPushMatrix();

		//draw ITD histogram
		gl.glBegin(GL.GL_LINES);
		gl.glColor3d(0.8, 0, 0);

		gl.glVertex3f((direction/ITDBinWidth)+numITDBins/2,0,0);
		gl.glVertex3f((direction/ITDBinWidth)+numITDBins/2,1,0);

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
