/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferStrategy;

import javax.swing.SwingUtilities;

import net.sf.jaer.event.PolarityEvent.Polarity;
//import net.sf.jaer.graphics.ImageDisplay;

/**
 * @author Dennis
 *
 */
public class SpikingOutputViewer implements SpikeHandler, NonGLImageDisplay.UpdateListener {

	int sizeX = 0, sizeY = 0;
    NonGLImageDisplay display ;
    int maxValueInBuffer = 0;
    float[] state;  // Array of unit states.
	public int[][] receivedSpikes;
	public int[][] receivedSpikesBuffer;
	public int[][] outputBuffer;
	
	Object receivedSpikesLock = new Object();
    int grayLevels;
	boolean active = true;
    
    
	
	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public int getGrayLevels() {
		return grayLevels;
	}

	public void setGrayLevels(int grayLevels) {
		this.grayLevels = grayLevels;
	}

	public SpikingOutputViewer(int sizeX, int sizeY, int grayLevels)    {
    	changeSize(sizeX, sizeY);
    	setGrayLevels(grayLevels);
        display = NonGLImageDisplay.createNonGLDisplay();
        display.addUpdateListener(this);
        display.setSizeX(sizeX);
        display.setSizeY(sizeY);
        display.setPreferredSize(new Dimension(250,250));
//        display.setBorderSpacePixels(0);
//        this.display.setFontSize(14);
    }
    
    public void changeSize(int sizeX, int sizeY) {
    	if (sizeX != this.sizeX || sizeY != this.sizeY) {
    		synchronized (this) {
				
	        	this.sizeX = sizeX;
	        	this.sizeY = sizeY;
	        	this.receivedSpikes = new int[sizeX][sizeY];
	        	this.receivedSpikesBuffer = new int[sizeX][sizeY];
	        	this.outputBuffer = new int[sizeX][sizeY];
	        	if (display != null) {
	//                display = ImageDisplay.createOpenGLCanvas();
	//                display.setSizeX(sizeX);
	//                display.setSizeY(sizeY);
	//                display.setPreferredSize(new Dimension(250,250));
	//                display.setBorderSpacePixels(1);
	//                this.display.setFontSize(14);
	        		display.setImageSize(sizeX, sizeY);
				}
        	}
    	}
    	
    }

    /* Update layerStatePlots to current network time */
    public void update()  {
    	// swap buffer and receivedSpikes:
//    	synchronized (this) {
    	if (active) {
    		synchronized (this.outputBuffer) {
    			// quickly exchange buffer and receivedSpikes to allow further processing of spikes:
    			synchronized (receivedSpikesLock) {
    				int[][] dummy = receivedSpikes;
    				receivedSpikes = receivedSpikesBuffer;
    				receivedSpikesBuffer = dummy;
    			}
    			// now copy values and send them to display:
    			int value = 0;
            	for (int x = 0; x < receivedSpikesBuffer.length; x++) {
            		for (int y = 0; y < receivedSpikesBuffer[x].length; y++) {
            			outputBuffer[x][y] += receivedSpikesBuffer[x][y];
            			receivedSpikesBuffer[x][y] = 0;
            			value = outputBuffer[x][y];
            			if (value < grayLevels)
            				display.setPixmapGray(x, y, (float)value / (float)grayLevels);
            			else
            				display.setPixmapGray(x, y, 1.0f);
            		}
            	}
	    	}
    		display.repaint();
	//    	}
//	        SwingUtilities.invokeLater(new Runnable(){
//	                @Override
//	                public void run() {
//	                    synchronized (receivedSpikesBuffer) {
//	                    	for (int x = 0; x < receivedSpikesBuffer.length; x++) {
//	                    		for (int y = 0; y < receivedSpikesBuffer[x].length; y++) {
//	                    			int value = receivedSpikesBuffer[x][y];
////	                    			receivedSpikesBuffer[x][y] = 0;
//	                    			if (value < grayLevels)
//	                    				display.setPixmapGray(x, y, (float)value / (float)grayLevels);
//	                    			else
//	                    				display.setPixmapGray(x, y, 1.0f);
//	                    		}
//	                    	}
//	                    }
//	                    display.repaint();
//	                }
//	        });
    	}
    }

	@Override
	public void spikeAt(int x, int y, int time, Polarity polarity) {
		synchronized (receivedSpikesLock) {
			receivedSpikes[x][y]++;
		}
	}

	@Override
	public void reset() {
        synchronized (receivedSpikesBuffer) {
        	synchronized (receivedSpikesLock) {
	        	for (int x = 0; x < receivedSpikesBuffer.length; x++) {
	        		for (int y = 0; y < receivedSpikesBuffer[x].length; y++) {
	        			receivedSpikes[x][y] = 0;
	        			receivedSpikesBuffer[x][y] = 0;
	        			outputBuffer[x][y] = 0;
	        		}
	        	}
			}
        }
	}

	public NonGLImageDisplay getDisplay() {
		return display;
	}

	@Override
	public void displayUpdated(Object display) {
		// clear buffer
        synchronized (outputBuffer) {
        	for (int x = 0; x < outputBuffer.length; x++) {
        		for (int y = 0; y < outputBuffer[x].length; y++) {
        			outputBuffer[x][y] = 0;
        		}
        	}
        }
	}

}
