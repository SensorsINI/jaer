/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion.gui;

import java.awt.Dimension;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import ch.unizh.ini.jaer.projects.apsdvsfusion.FiringModelMap;
import ch.unizh.ini.jaer.projects.apsdvsfusion.SignalHandler;


/**
 * @author Dennis
 *
 */
public class SpikingOutputViewer implements ContinuousOutputViewer, SignalHandler, NonGLImageDisplay.UpdateListener, PropertyChangeListener {
 
	int sizeX = 0, sizeY = 0;
    NonGLImageDisplay display ;
    int maxValueInBuffer = 0;
    float[] state;  // Array of unit states.
	public int[][] receivedSpikes;
	public int[][] receivedSpikesBuffer;
	public int[][] outputBuffer;
	
	Object receivedSpikesLock = new Object();
	Object outputBufferLock = new Object();
    int grayLevels;
	boolean active = true;
    
    FiringModelMap map = null;
	
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

	public SpikingOutputViewer(FiringModelMap map, int grayLevels) {
    	this(map.getSizeX(), map.getSizeY(), grayLevels);
    	this.map = map;
    	map.addSignalHandler(this);
    	map.getSupport().addPropertyChangeListener("sizeX",this);
    	map.getSupport().addPropertyChangeListener("sizeY",this);
	}
	
	public SpikingOutputViewer(int sizeX, int sizeY, int grayLevels)    {
    	changeSize(sizeX, sizeY);
    	setGrayLevels(grayLevels);
        display = NonGLImageDisplay.createNonGLDisplay();
        display.addUpdateListener(this);
        display.setSizeX(sizeX);
        display.setSizeY(sizeY);
        display.setPreferredSize(new Dimension(250,250));
        display.addMouseWheelListener(new java.awt.event.MouseWheelListener() {
            public void mouseWheelMoved(java.awt.event.MouseWheelEvent evt) {
                int code = evt.getWheelRotation();
                if (map != null) {
                	int currentGrayLevels = map.getGrayLevels();
                	currentGrayLevels += code;
                	if (currentGrayLevels < 1) currentGrayLevels = 1;
                	map.setGrayLevels(currentGrayLevels);
                }
            }
        });
//        display.setBorderSpacePixels(0);
//        this.display.setFontSize(14);
    }
    
	public void releaseMap() {
		if (map != null) {
			map.removeSignalHandler(this);
	    	map.getSupport().removePropertyChangeListener("sizeX",this);
	    	map.getSupport().removePropertyChangeListener("sizeY",this);
	    	map = null;
		}
	}
	
    public void changeSize(int sizeX, int sizeY) {
    	if (sizeX != this.sizeX || sizeY != this.sizeY) {
    		synchronized (this) {
				synchronized (this.outputBufferLock) {
					synchronized (receivedSpikesLock) {
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
    	}
    }

    /* Update layerStatePlots to current network time */
    public void updateOutput()  {
    	// swap buffer and receivedSpikes:
//    	synchronized (this) {
    	if (active) {
    		synchronized (this.outputBufferLock) {
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
            			if (value >= 0 && value < grayLevels)
            				display.setPixmapGray(x, y, (float)value / (float)grayLevels);
            			else if (value < 0 && value > -grayLevels)
            				display.setPixmapRGB(x, y, -(float)value / (float)grayLevels, 0, 0);
            			else if (value > 0)
            				display.setPixmapGray(x, y, 1.0f);
            			else if (value < 0)
            				display.setPixmapRGB(x, y, 1.0f, 0, 0);
            				
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
	public void signalAt(int x, int y, int time, double value) {
		synchronized (receivedSpikesLock) {
			if (x < receivedSpikes.length && y < receivedSpikes[x].length)
				receivedSpikes[x][y] += value;
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
        synchronized (outputBufferLock) {
        	for (int x = 0; x < outputBuffer.length; x++) {
        		for (int y = 0; y < outputBuffer[x].length; y++) {
        			outputBuffer[x][y] = 0;
        		}
        	}
        }
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (evt.getSource() instanceof FiringModelMap) {
			FiringModelMap map = (FiringModelMap)evt.getSource();
			if ((evt.getPropertyName().equals("sizeX") && ((Integer)evt.getNewValue() != this.sizeX))) {
				changeSize((Integer)evt.getNewValue(), sizeY); 
			}
			else if ((evt.getPropertyName().equals("sizeY")) && ((Integer)evt.getNewValue() != this.sizeY)) {
				changeSize(sizeX,(Integer)evt.getNewValue()); 
			}
		}
	}

}
