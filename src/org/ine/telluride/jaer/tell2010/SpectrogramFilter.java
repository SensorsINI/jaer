/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ine.telluride.jaer.tell2010;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.Observable;

import java.util.Observer;

import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.awt.GLCanvas;
import javax.swing.JFrame;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.ImageDisplay;
import ch.unizh.ini.jaer.chip.cochlea.BinauralCochleaEvent;

import com.jogamp.opengl.util.gl2.GLUT;

/**
 * Shows live cochlea spectrogram in a separate ImageDisplay window.
 * @author Andrew
 */
@Description("Generate a spectrogram from incoming spikes")
public final class SpectrogramFilter extends EventFilter2D implements Observer, FrameAnnotater {

	private int numChannels = getPrefs().getInt("SpectrogramFilter.numChannels", 64);
	private int binWidth = getPrefs().getInt("SpecdtrogramFilter.binWidth", 16000);
	private int numTimeBins = getPrefs().getInt("SpecdtrogramFilter.numTimeBins", 50);
	private int redundancy = getPrefs().getInt("SpecdtrogramFilter.redundancy", 1); //not used now
	private int spikeBufferLength = getPrefs().getInt("SpecotragramFilter.spikeBufferLength", 10000);
	private int colorScale = getPrefs().getInt("SpecdtrogramFilter.colorScale", 50);
	private float[][] spectrogram;
	private int currentTime;
	private int[] spikeBufferTs;
	private int[] spikeBufferChans;
	private boolean[] spikeBufferValid;
	private int spikeBufferIndex;
	private ImageDisplay imageDisplay = null;
	private JFrame imageFrame = null;

	public SpectrogramFilter(AEChip chip) {
		super(chip);
		// add display method here?
		spectrogram = new float[numChannels][numTimeBins];
		spikeBufferIndex = 0;
		spikeBufferTs = new int[spikeBufferLength];
		spikeBufferChans = new int[spikeBufferLength];
		resetFilter();
	}

	@Override
	public synchronized void setFilterEnabled(boolean yes) {
		super.setFilterEnabled(yes);
		if (yes) {
			checkImageDisplay();
			imageFrame.setVisible(true);
		} else {
			if (imageFrame != null) {
				imageFrame.setVisible(false);
			}

		}

	}

	@Override
	public void initFilter() {
	}

	@Override
	synchronized public void resetFilter() {
		clearSpectrogram();

		/*
        int oldSpikeBufferLength;
        oldSpikeBufferLength = spikeBufferLength;
        spikeBufferLength = numTimeBins * binWidth * numChannels * 400;

        if (spikeBufferLength != oldSpikeBufferLength) {
        spikeBufferTs = new int[spikeBufferLength];
        spikeBufferChans = new int[spikeBufferLength];
        }
		 *
		 */

		//-1's will not show up in spectrogram display
		for (int spike = 0; spike < spikeBufferLength; spike++) {
			spikeBufferTs[spike] = -1;
			spikeBufferChans[spike] = -1;
		}

	}

	@Override
	public void update(Observable o, Object arg) {
	}

	@Override
	public synchronized EventPacket<?> filterPacket(EventPacket<?> in) {

		if (!isFilterEnabled()) {
			return in;
		}
		if (in == null) {
			return in;
		}

		//add spikes to per-channel buffers
		for (Object e : in) {
			BinauralCochleaEvent i = (BinauralCochleaEvent) e;
			spikeBufferChans[spikeBufferIndex] = i.x;
			spikeBufferTs[spikeBufferIndex] = i.timestamp;
			currentTime = i.timestamp;  //move outside the loop?

			//circular buffer bounds check
			if (++spikeBufferIndex >= spikeBufferLength) {
				spikeBufferIndex = 0;
			}
		}
		binSpikes();
		//        makeSpecDisplay();
		//        if(glCanvas!=null) glCanvas.repaint();

		return in;
	}

	/*
    private void makeSpecDisplay(){
    if (specFrame == null) {
    specFrame = new JFrame("SpecDisplay");
    glCanvas = new GLCanvas() {

    @Override
    public void reshape(int i, int i1, int i2, int i3) {
    super.reshape(i, i1, i2, i3);
    }

    @Override
    public void display() {
    super.display();
    GL2 gl = glCanvas.getGL();
    gl.glPushMatrix();
    //        GL2 gl = drawable.getGL().getGL2();
    // make sure we're drawing back buffer (this is probably true anyhow)

    //        gl.glDrawBuffer(GL.GL_BACK);
    gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
    gl.glLoadIdentity(); // very important to load identity matrix here so this works after first resize!!!
    int BORDER=10;
    gl.glOrtho(-BORDER,getWidth()+BORDER,-BORDER,getHeight()+BORDER,10000,-10000);
    gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
    // translate origin to this point
    gl.glTranslatef(0, 0, 0);
    // scale everything by rastergram scale
    float ys = (glCanvas.getHeight()) / (float) chip.getSizeX();// scale vertical is draableHeight/numPixels
    float xs = (glCanvas.getWidth()) / (float) numTimeBins;
    gl.glScalef(xs, ys, 1);


    gl.glClearColor(0, 0, 0, 0);
    gl.glClearDepth(0.0);
    gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);

    gl.glColor3d(1, 0, 0);
    gl.glRectd(0, 0, xs, ys);
    gl.glPopMatrix();

    }
    };
    specFrame.getContentPane().add(glCanvas, BorderLayout.CENTER);
    glCanvas.setPreferredSize(new Dimension(200, 200));
    specFrame.setVisible(true);
    }

    }
	 */
	private void binSpikes() {
		/* this could be computationally more efficient if we just moved
		 * previously binned spikes, but at the cost of having an exactly
		 * accurate spectogram at each spike packet time
		 */
		clearSpectrogram();
		int tbin, chan, spike, spikeT, t, tOff;
		for (spike = 0; spike < spikeBufferLength; spike++) {
			chan = spikeBufferChans[spike];
			spikeT = spikeBufferTs[spike];
			t = currentTime - spikeT;       // - time relative to now
			tOff = t / binWidth;              // above in units of bins
			tbin = numTimeBins - 1 - tOff;  // time bin index (current time is last column)

			if ((tbin > 0) && (chan != -1)) {
				if ((chan < 0) || (chan >= numChannels) || (tbin < 0) || (tbin >= numTimeBins)) //throw error here? this shouldn't happen
				{
					spectrogram[chan][tbin]++;
				} else {
					spectrogram[chan][tbin]++;
				}
			}
		}

	}

	private void checkImageDisplay() {
		if (imageFrame == null) {
			imageFrame = new JFrame("Spectrogram");
			imageFrame.setPreferredSize(new Dimension(200, 200));
			imageDisplay = ImageDisplay.createOpenGLCanvas();
			imageFrame.getContentPane().add(imageDisplay, BorderLayout.CENTER);
			imageFrame.pack();
			imageDisplay.setxLabel("time");
			imageDisplay.setyLabel("channel");

		}
		if ((numChannels != imageDisplay.getSizeY()) || (numTimeBins != imageDisplay.getSizeX())) {
			imageDisplay.setImageSize(numTimeBins, numChannels);
		}
	}

	private void clearSpectrogram() {

		for (int chan = 0; chan < numChannels; chan++) {
			for (int time = 0; time < numTimeBins; time++) {
				spectrogram[chan][time] = 0;
			}
		}
	}
	private GLUT glut = new GLUT();
	private JFrame specFrame = null;
	private GLCanvas glCanvas = null;

	@Override
	public void annotate(GLAutoDrawable drawable) {
		checkImageDisplay();

		imageDisplay.checkPixmapAllocation();
		float c;
		for (int chan = 0; chan < numChannels; chan++) {
			for (int time = 0; time < numTimeBins; time++) {
				c = spectrogram[chan][time] / colorScale;
				imageDisplay.setPixmapGray(time, chan, c);
			}
		}
		imageDisplay.display();

		//        GL2 gl=drawable.getGL().getGL2();
		//
		//        int width = drawable.getWidth();
		//        int height = drawable.getHeight();
		//
		//        float c;
		//        for (int chan = 0; chan < numChannels; chan++) {
		//            for (int time = 0; time < numTimeBins; time++) {
		//                c = (float) spectrogram[chan][time] / colorScale;
		//                gl.glColor3d(c, 1, c);
		//                float x1 = (float)time / (float)numTimeBins * width;
		//                float x2 = (float)(time+1) / (float)numTimeBins * width;
		//
		//                gl.glRectf(x1/1000,chan, x2/1000, chan+1);
		//            }
		//        }



		//        gl.glPushMatrix();

		//print channel mask
		//        gl.glRasterPos2i(0,-1);
		//       chip.getCanvas().getGlut().glutBitmapString(GLUT.BITMAP_HELVETICA_12,String.valueOf(numActiveChannels)+":"+String.copyValueOf(maskBits));
		//      gl.glPopMatrix();
	}

	/**
	 * @return the binWidth
	 */
	public int getBinWidth() {
		return binWidth;
	}

	/**
	 * @param binWidth the binWidth to set
	 */
	public void setBinWidth(int binWidth) {
		this.binWidth = binWidth;
		prefs().putInt("SpectrogramFilter.binWidth", binWidth);
	}

	/**
	 * @return the numTimeBins
	 */
	public int getNumTimeBins() {
		return numTimeBins;
	}

	/**
	 * @param numTimeBins the numTimeBins to set
	 */
	synchronized public void setNumTimeBins(int numTimeBins) {
		this.numTimeBins = numTimeBins;
		prefs().putInt("SpectrogramFilter.numTimeBins", numTimeBins);
		spectrogram = new float[numChannels][numTimeBins];
	}

	/**
	 * @return the redundancy
	 */
	public int getRedundancy() {
		return redundancy;
	}

	/**
	 * @param redundancy the redundancy to set
	 */
	synchronized public void setRedundancy(int redundancy) {
		this.redundancy = redundancy;
		prefs().putInt("SpectrogramFilter.binWidth", redundancy);
	}

	/**
	 * @return the spikeBufferLength
	 */
	public int getSpikeBufferLength() {
		return spikeBufferLength;
	}

	/**
	 * @param spikeBufferLength the spikeBufferLength to set
	 */
	synchronized public void setSpikeBufferLength(int spikeBufferLength) {
		this.spikeBufferLength = spikeBufferLength;
		prefs().putInt("SpectrogramFilter.spikeBufferLength", spikeBufferLength);
		spikeBufferTs = new int[spikeBufferLength];
		spikeBufferChans = new int[spikeBufferLength];
	}

	/**
	 * @return the colorScale
	 */
	public int getColorScale() {
		return colorScale;
	}

	/**
	 * @param colorScale the colorScale to set
	 */
	public void setColorScale(int colorScale) {
		this.colorScale = colorScale;
		prefs().putInt("SpectrogramFilter.colorScale", colorScale);
	}
	/*
    public void display(GLAutoDrawable drawable){
    //        GL2 gl=setupGL(drawable);

    // render events

    EventPacket<TypedEvent> packet=(EventPacket)chip.getLastData();
    if(packet.getSize()==0) return;
    shammaMap.processPacket(packet);
    // draw results
    GL2 gl = drawable.getGL().getGL2();
    // make sure we're drawing back buffer (this is probably true anyhow)
    gl.glDrawBuffer(GL.GL_BACK);
    gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
    gl.glLoadIdentity(); // very important to load identity matrix here so this works after first resize!!!
    gl.glOrtho(-BORDER,drawable.getWidth()+BORDER,-BORDER,drawable.getHeight()+BORDER,10000,-10000);
    gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
    gl.glClearColor(0,0,0,0f);
    gl.glClear(GL2.GL_COLOR_BUFFER_BIT);
    gl.glLoadIdentity();
    // translate origin to this point
    gl.glTranslatef(0,0,0);
    // scale everything by rastergram scale
    float ys=(drawable.getHeight())/(float)chip.getSizeX();// scale vertical is draableHeight/numPixels
    float xs=ys;
    gl.glScalef(xs,ys,1);

    try{
    int t=packet.getLastTimestamp();
    // now iterate over the frame (mapOutput)
    int m=chip.getSizeX();
    for (int x = 0; x < m; x++){
    for (int y = 0; y < m; y++){
    float f = shammaMap.getNormMapOutput(y,x,t);
    //                    if(f==gray) continue;
    //                    int x = i,  y = j; // dont flip y direction because retina coordinates are from botton to top (in user space, after lens) are the same as default OpenGL coordinates
    gl.glColor3f(f,f,f);
    gl.glRectf(x-.5f,y-.5f, x+.5f, y+.5f);
    }
    }
    // now plot cochlea activities as earlier rendered by ChipRenderer
    float[] fr=getRenderer().getPixmapArray();
    int y;
    y=0; // right
    for(int x=0;x<m;x++){
    int ind=getRenderer().getPixMapIndex(x, y);
    gl.glColor3f(fr[ind],fr[ind+1],fr[ind+2]);
    gl.glRectf(x-.5f,y-2, x+.5f, y-1);
    }
    y=1; // left
    for(int x=0;x<m;x++){
    int ind=getRenderer().getPixMapIndex(x, y);
    gl.glColor3f(fr[ind],fr[ind+1],fr[ind+2]);
    gl.glRectf(-2, x-.5f, -1, x+.5f);
    }

    }catch(ArrayIndexOutOfBoundsException e){
    log.warning("while drawing frame buffer");
    e.printStackTrace();
    getChipCanvas().unzoom(); // in case it was some other chip had set the zoom
    gl.glPopMatrix();
    }
    // outline frame
    gl.glColor4f(0,0,1f,0f);
    gl.glLineWidth(1f);
    gl.glBegin(GL2.GL_LINE_LOOP);
    final float o = .5f;
    final float w = chip.getSizeX()-1;
    final float h = chip.getSizeX()-1;
    gl.glVertex2f(-o,-o);
    gl.glVertex2f(w+o,-o);
    gl.glVertex2f(w+o,h+o);
    gl.glVertex2f(-o,h+o);
    gl.glEnd();


    }
	 */
}
