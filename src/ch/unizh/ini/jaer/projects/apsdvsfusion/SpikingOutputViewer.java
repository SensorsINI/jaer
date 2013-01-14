/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferStrategy;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import net.sf.jaer.event.PolarityEvent.Polarity;
import net.sf.jaer.graphics.ImageDisplay;

/**
 * @author Dennis
 *
 */
public class SpikingOutputViewer implements SpikeHandler {

	int sizeX = 0, sizeY = 0;
    ImageDisplay display ;
    int maxValueInBuffer = 0;
    float[] state;  // Array of unit states.
	public int[][] receivedSpikes;
	public int[][] receivedSpikesBuffer;
    
	public static class ImageDisplay extends JPanel {
		float[][] pixmap;
		int sizeX = -1, sizeY = -1;
		private float rectWidth, rectHeight; 
		
		public ImageDisplay(int width, int height) {
			setImageSize(sizeX, sizeY);
//			this.createBufferStrategy(2);
//			bufferStrategy = getBufferStrategy();			
		}
		public void setSizeX(int sizeX) {
			setImageSize(sizeX, sizeY);
		}
		public void setSizeY(int sizeY) {
			setImageSize(sizeX, sizeY);
		}
		public void setImageSize(int sizeX, int sizeY) {
			if (sizeX != this.sizeX || sizeY != this.sizeY) {
				this.sizeX = sizeX;
				this.sizeY = sizeY;
				if (this.sizeX < 0)
					this.sizeX = 0;
				if (this.sizeY < 0)
					this.sizeY = 0;
				this.pixmap = new float[this.sizeX][this.sizeY];
			}
		}
		
		public void setPixmapGray(int x, int y, float value) {
			this.pixmap[x][y] = value;
		}

		public void paint(Graphics g) {
//			Graphics2D g2 = (Graphics2D)g;
			
			final Rectangle rect = g.getClipBounds();
			
			float rWidth = (float)rect.width / (float)sizeX;
			float rHeight = (float)rect.height / (float)sizeY;
			
			int w = (int)rWidth;
			if (w < 1)
				w = 1;
			int h = (int)rHeight;
			if (h < 1)
				h = 1;
			float posx = 0, posy = rect.height - rHeight;
			for (int x = 0; x < pixmap.length; x++) {
				posy = rect.height - rHeight;
				for (int y = 0; y < pixmap[x].length; y++) {
					float value = pixmap[x][y];
					g.setColor(new Color(value,value,value));
					g.drawRect((int)posx,  (int)posy, w, h);
					posy -= rHeight;
				}
				posx += rWidth;
			}
		}

		public static ImageDisplay createOpenGLCanvas() {
			return new ImageDisplay(10,10);
		}
		
		@Deprecated
		public void setBorderSpacePixels(int pixels) {
		}
		
		@Deprecated
		public void setFontSize(int size) {
		}
		
		
	}
	
    public SpikingOutputViewer(int sizeX, int sizeY)    {
    	changeSize(sizeX, sizeY);
        display = ImageDisplay.createOpenGLCanvas();
        display.setSizeX(sizeX);
        display.setSizeY(sizeY);
        display.setPreferredSize(new Dimension(250,250));
        display.setBorderSpacePixels(0);
        this.display.setFontSize(14);
    }
    
    public void changeSize(int sizeX, int sizeY) {
    	if (sizeX != this.sizeX || sizeY != this.sizeY) {
    		synchronized (this) {
				
	        	this.sizeX = sizeX;
	        	this.sizeY = sizeY;
	        	this.receivedSpikes = new int[sizeX][sizeY];
	        	this.receivedSpikesBuffer = new int[sizeX][sizeY];
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
    	synchronized (this.receivedSpikes) {
    		synchronized (this.receivedSpikesBuffer) {
            	this.maxValueInBuffer = 1; 
//    			int[][] dummy = this.receivedSpikesBuffer;
//    			this.receivedSpikesBuffer = this.receivedSpikes;
//    			this.receivedSpikes = dummy;
    			for (int i = 0; i < receivedSpikes.length; i++) {
					for (int j = 0; j < receivedSpikes[i].length; j++) {
						receivedSpikesBuffer[i][j] += receivedSpikes[i][j];
						receivedSpikes[i][j] = 0;
					}
				}
    		}
    	}
//    	}
        SwingUtilities.invokeLater(new Runnable(){
                @Override
                public void run() {
                    synchronized (receivedSpikesBuffer) {
                    	for (int x = 0; x < receivedSpikesBuffer.length; x++) {
                    		for (int y = 0; y < receivedSpikesBuffer[x].length; y++) {
                    			int value = receivedSpikesBuffer[x][y];
                    			receivedSpikesBuffer[x][y] = 0;
                    			if (value > maxValueInBuffer) value = maxValueInBuffer;
                    			display.setPixmapGray(x, y, (float)value / (float)maxValueInBuffer);
                    		}
                    	}
                    }
//TODO: title!                        display.setTitleLabel(layer.getName()+" "+stateTrackers[iimax].getLabel(ssmin,ssmax));
                    display.repaint();
                }
        });
    }

	@Override
	public void spikeAt(int x, int y, int time, Polarity polarity) {
		synchronized (receivedSpikes) {
			receivedSpikes[x][y]++;
		}
	}

	@Override
	public void reset() {
        synchronized (receivedSpikesBuffer) {
        	for (int x = 0; x < receivedSpikesBuffer.length; x++) {
        		for (int y = 0; y < receivedSpikesBuffer[x].length; y++) {
        			receivedSpikes[x][y] = 0;
        			receivedSpikesBuffer[x][y] = 0;
        		}
        	}
        }
	}

	public ImageDisplay getDisplay() {
		return display;
	}

}
