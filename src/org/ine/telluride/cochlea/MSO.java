/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ine.telluride.cochlea;
import ch.unizh.ini.caviar.chip.AEChip;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import ch.unizh.ini.caviar.graphics.FrameAnnotater;
import com.sun.opengl.util.GLUT;
import java.awt.Graphics2D;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.glu.GLU;

/**
 * Calculates ITD from binaural cochlea input
 * 
 * @author ahs (Andrew Schwartz, MIT)
 */
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
    private boolean includeChannel=getPrefs().getBoolean("MSO.includeCHannel",true);
    {setPropertyTooltip("includeChannel", "True to include toggleChannel in ITD computation");}
    
    private float[] ITDBuffer=null;
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
    private int glBins = numBins;
    
    @Override
    public String getDescription() {
        return "Computes ITD of incoming binaural signal";
    }

    public MSO(AEChip chip) {
        super(chip);
        for (chan=0;chan<NUM_CHANS;chan++) {
            includeChannelArray[chan] = true;
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
        newBufferSize = anf.getBufferSize();
        if (newBufferSize != bufferSize) {
            System.out.println("Buffer size changed!  Allocating new memory for buffers");
            allocateSpikeBuffer();
        }
        bufferFull = anf.getBufferFull();
        spikeBuffer = anf.getBuffer();
        initializeITDBuffer();
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
                            if(delays[ii][jj]>=ITDBinEdges[bin]&&delays[ii][jj]<=ITDBinEdges[bin+1]) {
                                count++;
                            }
                        }
                    }
                    ITDBuffer[bin]+=(float)count / (float)(NUM_CHANS * bufferSize*binWidth);
                }
            } // if (IncludeChannelInITD)
        } //for (chan=0; chan<NUM_CHANS; chan++)
        
        //smooth output
        for (bin=0;bin<numBins;bin++) {
            
        }
        
        return;
    }

    public float[] getITD() {
        return ITDBuffer;
    }

    
    @Override
    public Object getFilterState() {
        return null;
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
        ITDBinEdges=new int[numBins+1];
        ITDBins=new int[numBins];
        initializeITDBuffer();
    }
    
    private void initializeITDBuffer() {
        //set ITD buffer values
        for(ii=0; ii<numBins; ii++) {
            ITDBinEdges[ii]=(-numBins*binWidth)/2+ii*binWidth;
            ITDBins[ii]=(-(numBins-1)*binWidth)/2+ii*binWidth;
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

    public void annotate(GLAutoDrawable drawable) {
        if(!isFilterEnabled()) {
            return;
        //   if(isRelaxed) return;
        }
        if (glBins!=numBins) {
            System.out.println("glBins differs from numBins!  Reallocating...");
            allocateITDBuffer();
            glBins = numBins;
        }
        if (ITDBuffer!=null) {
            GL gl=drawable.getGL();
            gl.glPushMatrix();
            
            //draw ITD histogram
            gl.glBegin(gl.GL_LINE_STRIP);
            gl.glColor3d(0, 1, 1);
            for (bin=0;bin<numBins;bin++) {
                gl.glVertex3f(bin,4000*ITDBuffer[bin],0);
            }
            gl.glEnd( );
            
            //print text labels
            for (bin=0;bin<numBins;bin+=3) {
                gl.glRasterPos2i(bin,-1);
                chip.getCanvas().getGlut().glutBitmapString(GLUT.BITMAP_HELVETICA_12, ""+ITDBins[bin]);
            }
            
            gl.glPopMatrix();
        }
    }
    
    
    // --- getters and setters
    public int getBinWidth() {
        return binWidth;
    }
    public void setBinWidth(int binWidth) {
        this.binWidth=binWidth;
        getPrefs().putInt("MSO.binWidth", binWidth);
        allocateITDBuffer();
    }

    public int getNumBins() {
        return numBins;
    }
    public void setNumBins(int numBins) {
        this.numBins=numBins;
        getPrefs().putInt("MSO.numBins", numBins);
        allocateITDBuffer();
    }

    public int getSmoothBins() {
        return smoothBins;
    }
    public void setSmoothBins(int smoothBins) {
        this.smoothBins=smoothBins;
        getPrefs().putInt("MSO.smoothBins", numBins);
        System.out.println("Smooth bins set to "+smoothBins);
    }

    public int getToggleChannel() {
        return toggleChannel;
    }
    public void setToggleChannel(int toggleChannel) {
        if (toggleChannel > 0 && toggleChannel < NUM_CHANS) {
            this.toggleChannel = toggleChannel;
            getPrefs().putInt("MSO.toggleChannel", toggleChannel);
            getPrefs().putBoolean("MSO.includeCHannel", includeChannelArray[toggleChannel]);
        } else {
            System.out.println("Invalid channel!");
            getPrefs().putInt("MSO.toggleChannel", this.toggleChannel);
        }
    }
    
    public boolean getIncludeChannel() {
            return includeChannel;
    }
    public void setIncludeChannel(boolean includeChannel) {
        this.includeChannel = includeChannel;
        includeChannelArray[toggleChannel] = includeChannel;
        getPrefs().putBoolean("MSO.includeCHannel", includeChannelArray[toggleChannel]);
        System.out.println("includeChannelArray:");
        for (chan=NUM_CHANS-1;chan>=0;chan--) {
            System.out.print(includeChannelArray[chan]?"1":"0");
        }
        System.out.print('\n');
    }
    //Integer.toHexString(intVal)
    //Integer.parseInt(String s, int radix)
    
}
