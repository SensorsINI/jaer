/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ine.telluride.jaer.cochlea;
import java.awt.Graphics2D;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.glu.GLU;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

import com.jogamp.opengl.util.gl2.GLUT;

/**
 * Calculates ITD from binaural cochlea input
 * 
 * @author ahs (Andrew Schwartz, MIT)
 */
@Description("Computes ITD of incoming binaural signal")
public class MSO extends EventFilter2D implements FrameAnnotater {
	private static final int NUM_CHANS=32;
	private int binWidth=getPrefs().getInt("MSO.binWidth", 100);
	{setPropertyTooltip("binWidth", "Bin width for ITD hisotgram");}
	private int numBins=getPrefs().getInt("MSO.numBins", 15);
	{setPropertyTooltip("numBins", "Total number of bins, centered about 0");}
	private int smoothBins=getPrefs().getInt("MSO.smoothBins",3);
	{setPropertyTooltip("smoothBins", "Width, in bins, by which to smooth (applies both in positive and negative direction) ITD values");}
	private int toggleChannel=getPrefs().getInt("MSO.toggleChannel",0);
	{setPropertyTooltip("toggleChannel", "Channel number to toggle on/off in ITD computation");}
	private boolean toggleChannelOn=getPrefs().getBoolean("MSO.toggleChannelOn",true);
	{setPropertyTooltip("toggleChannelOn", "True to include toggleChannel in ITD computation");}
	private boolean drawOutput=getPrefs().getBoolean("MSO.drawOutput",true);
	{setPropertyTooltip("drawOutput", "Enable drawing of ITD histogram");}
	private float displayScale=getPrefs().getFloat("MSO.displayScale",1);
	{setPropertyTooltip("displayScale", "Multiplicative scaling factor for display of histogram");}


	private float[] ITDBuffer=null;
	private float[] ITDBufferCopy=null;
	private int[] ITDBins=null;
	private int[] ITDBinEdges=null;
	private int[][] delays=null;
	private ANFSpikeBuffer anf=null;
	private int chan, ii, jj, bin, count;
	private int spikeCount = 0;
	private int bufferSize = 0;
	private int newBufferSize = 0;
	private int[][][] spikeBuffer = null;
	private boolean[][] bufferFull;
	private boolean[] includeChannelArray = new boolean[NUM_CHANS];
	private float[] channelWeights = new float[NUM_CHANS];
	private int glBins = numBins;
	private char [] maskBits = new char[NUM_CHANS];
	private int numActiveChannels = NUM_CHANS;
	private float scale;

	public MSO(AEChip chip) {
		super(chip);
		for (chan=0;chan<NUM_CHANS;chan++) {
			includeChannelArray[chan] = true;
			channelWeights[chan]=1;
		}
		initFilter();
	}

	@Override
	synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
		if(!isFilterEnabled()) {
			return in;
		}
		if(!checkSpikeBuffer()){
			throw new RuntimeException("Can't find prior ANFSpikeBuffer in filter chain");
		}
		if(in==null) {
			return in;
		}
		for(Object o : in) {
			spikeCount++;
			if (spikeCount==100) {
				computeITD();
				spikeCount = 0;
			}
		}
		return in;
	}

	public void computeITD() {
		if (numActiveChannels>0) {
			newBufferSize = anf.getBufferSize();
			if (newBufferSize != bufferSize) {
				System.out.println("Buffer size changed!  Allocating new memory for buffers");
				allocateSpikeBuffer();
			}
			bufferFull = anf.getBufferFull();
			spikeBuffer = anf.getBuffer();
			if (getPrefs().getInt("MSO.numBins", 15) != numBins) {
				allocateITDBuffer();
			}
			else {
				initializeITDBuffer();
			}

			//compute delays in buffers
			for(chan=0; chan<NUM_CHANS; chan++) {
				if( includeChannelArray[chan] && bufferFull[0][chan] && bufferFull[1][chan]) {
					//compute delays in this channel
					for(ii=0; ii<bufferSize; ii++) {
						for(jj=0; jj<bufferSize; jj++) {
							delays[ii][jj]=spikeBuffer[0][chan][ii]-spikeBuffer[1][chan][jj];
						}
					}
					//bin delays
					for(bin=0; bin<numBins; bin++) {
						count=0;
						for(ii=0; ii<bufferSize; ii++) {
							for(jj=0; jj<bufferSize; jj++) {
								if((delays[ii][jj]>=ITDBinEdges[bin])&&(delays[ii][jj]<=ITDBinEdges[bin+1])) {
									count++;
								}
							}
						}
						ITDBuffer[bin]+=(count * channelWeights[chan]) / (numActiveChannels*bufferSize*binWidth);
					}
				} // if (IncludeChannelInITD)
			} //for (chan=0; chan<NUM_CHANS; chan++)

			//smooth output
			if (smoothBins>1) {
				for (ii=0;ii<numBins;ii++) {
					ITDBufferCopy[ii]=0;
				}
				for (bin=0;bin<numBins;bin++) {
					for (ii=-smoothBins;ii<=smoothBins;ii++){
						scale = 1 - (((ii>=0)?(float)ii:(float)-ii)/(smoothBins));
						if (((bin+ii)<0) || ((bin+ii)>=numBins)) {
							ITDBufferCopy[bin] += scale*ITDBuffer[bin];
						}
						else {
							ITDBufferCopy[bin] += scale*ITDBuffer[bin+ii];
						}
					}
				}
				for (ii=0;ii<numBins;ii++) {
					ITDBuffer[ii]=ITDBufferCopy[ii]/smoothBins;
				}
			}
		}
		return;
	}

	public float[] getITD() {
		return ITDBuffer;
	}


	@Override
	public void resetFilter() {
		binWidth = getPrefs().getInt("MSO.binWidth",100);
		numBins = getPrefs().getInt("MSO.numBins", 15);

		allocateITDBuffer();
	}

	@Override
	public void initFilter() {
		binWidth = getPrefs().getInt("MSO.binWidth",100);
		numBins = getPrefs().getInt("MSO.numBins", 15);

		allocateITDBuffer();
	}


	// --- private functions
	private boolean checkSpikeBuffer() {
		if(anf==null) {
			anf=(ANFSpikeBuffer) chip.getFilterChain().findFilter(ANFSpikeBuffer.class);
			allocateSpikeBuffer();

			return anf!=null;
		} else {
			return true;
		}
	}

	private void allocateSpikeBuffer() {
		System.out.println("Allocate spike buffer");
		bufferSize = anf.getBufferSize();
		spikeBuffer= new int[2][NUM_CHANS][bufferSize];
		bufferFull = new boolean[2][NUM_CHANS];
		delays=new int[bufferSize][bufferSize];
	}

	private void allocateITDBuffer() {
		System.out.println("Allocate ITD buffer");
		ITDBuffer=new float[numBins];
		ITDBufferCopy = new float[numBins];
		ITDBinEdges=new int[numBins+1];
		ITDBins=new int[numBins];
		initializeITDBuffer();
	}

	private void initializeITDBuffer() {
		//set ITD buffer values
		for(ii=0; ii<numBins; ii++) {
			ITDBinEdges[ii]=((-numBins*binWidth)/2)+(ii*binWidth);
			ITDBins[ii]=((-(numBins-1)*binWidth)/2)+(ii*binWidth);
			ITDBuffer[ii]=0;
		}
		ITDBinEdges[numBins]=-ITDBinEdges[0];
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
		if (glBins!=numBins) {
			System.out.println("glBins differs from numBins!  Reallocating...");
			allocateITDBuffer();
			glBins = numBins;
		}
		if (ITDBuffer!=null) {
			GL2 gl=drawable.getGL().getGL2();
			gl.glPushMatrix();

			//draw ITD histogram
			gl.glBegin(GL.GL_LINE_STRIP);
			gl.glColor3d(0, 1, 1);
			for (bin=0;bin<numBins;bin++) {
				gl.glVertex3f(bin,1+(displayScale*2000*ITDBuffer[bin]),0);
			}
			gl.glEnd( );

			//print text labels
			for (bin=0;bin<numBins;bin+=3) {
				gl.glRasterPos2f(bin - (float)0.5,0);
				chip.getCanvas().getGlut().glutBitmapString(GLUT.BITMAP_HELVETICA_12, ""+ITDBins[bin]);
			}

			//print channel mask
			for (chan=0;chan<NUM_CHANS;chan++) {
				maskBits[chan] = includeChannelArray[chan]?'1':'0';
			}
			gl.glRasterPos2i(0,-1);
			chip.getCanvas().getGlut().glutBitmapString(GLUT.BITMAP_HELVETICA_12,String.valueOf(numActiveChannels)+":"+String.copyValueOf(maskBits));
			gl.glPopMatrix();
		}
	}


	// --- getters and setters
	public int getBinWidth() {
		return binWidth;
	}
	public void setBinWidth(int binWidth) {
		if (binWidth>0) {
			this.binWidth=binWidth;
			getPrefs().putInt("MSO.binWidth", binWidth);
			allocateITDBuffer();
		} else {
			System.out.println("Invalid binWidth!");
		}
	}

	public int getNumBins() {
		return numBins;
	}
	public void setNumBins(int numBins) {
		if (numBins>0) {
			this.numBins=numBins;
			getPrefs().putInt("MSO.numBins", numBins);
			allocateITDBuffer();
		} else {
			System.out.println("Invalid numBins!");
		}
	}

	public int getSmoothBins() {
		return smoothBins;
	}
	public void setSmoothBins(int smoothBins) {
		this.smoothBins=smoothBins;
		getPrefs().putInt("MSO.smoothBins", smoothBins);
		System.out.println("Smooth bins set to "+smoothBins);
	}

	public int getToggleChannel() {
		return toggleChannel;
	}
	public void setToggleChannel(int toggleChannel) {
		if ((toggleChannel >= 0) && (toggleChannel < NUM_CHANS)) {
			this.toggleChannel = toggleChannel;
			getPrefs().putInt("MSO.toggleChannel", toggleChannel);
			getPrefs().putBoolean("MSO.toggleChannelOn", includeChannelArray[toggleChannel]);
		} else {
			System.out.println("Invalid channel!");
			getPrefs().putInt("MSO.toggleChannel", this.toggleChannel);
		}
	}

	public boolean getToggleChannelOn() {
		return toggleChannelOn;
	}
	public void setToggleChannelOn(boolean toggleChannelOn) {
		this.toggleChannelOn = toggleChannelOn;
		includeChannelArray[toggleChannel] = toggleChannelOn;
		getPrefs().putBoolean("MSO.toggleChannelOn", includeChannelArray[toggleChannel]);
		getPrefs().putBoolean("MSO.toggleChannelOn", toggleChannelOn);
		numActiveChannels=0;
		for (chan=0;chan<NUM_CHANS;chan++) {
			if (includeChannelArray[chan]) {
				numActiveChannels++;
			}
		}

		System.out.println("includeChannelArray:");
		System.out.print(String.valueOf(numActiveChannels)+": ");
		for (chan=NUM_CHANS-1;chan>=0;chan--) {
			System.out.print(includeChannelArray[chan]?"1":"0");
		}
		System.out.print('\n');
	}

	public boolean isChannelIncluded(int chan) {
		if ((chan>=0) && (chan <NUM_CHANS)) {
			return includeChannelArray[chan];
		}
		else {
			return false;
		}
	}
	public void setIncludeChannel(int chan, boolean include) {
		if ((chan>=0) && (chan<NUM_CHANS)) {
			includeChannelArray[chan]=include;
		}
	}

	public float getChannelWeight(int chan) {
		if ((chan>=0) && (chan <NUM_CHANS)) {
			return channelWeights[chan];
		}
		else {
			return 0;
		}
	}
	public void setChannelWeight(int chan, float weight) {
		if ((chan>=0) && (chan<NUM_CHANS)) {
			channelWeights[chan]=weight;
		}
	}


	public boolean getDrawOutput() {
		return drawOutput;
	}
	public void setDrawOutput(boolean drawOutput) {
		this.drawOutput = drawOutput;
		getPrefs().putBoolean("MSO.drawOutput",drawOutput);
	}
	public float getDisplayScale() {
		return displayScale;
	}
	public void setDisplayScale(float displayScale) {
		this.displayScale = displayScale;
		getPrefs().putFloat("MSO.drawOutput",displayScale);
	}

	public float[] getITDState() {
		return ITDBuffer;
	}

	public int[] getITDBins() {
		return ITDBins;
	}
}
