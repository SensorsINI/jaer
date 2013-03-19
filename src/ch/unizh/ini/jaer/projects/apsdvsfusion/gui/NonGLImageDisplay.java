package ch.unizh.ini.jaer.projects.apsdvsfusion.gui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

import javax.swing.JPanel;

public class NonGLImageDisplay extends JPanel {
		/**
	 * 
	 */
	private static final long serialVersionUID = -31584012951370181L;
	BufferedImage image = null;
	Color[][] pixmap;
	int sizeX = -1, sizeY = -1;
	boolean square;
	//		private float rectWidth, rectHeight; 
	ArrayList<UpdateListener> listeners = new ArrayList<UpdateListener>();

	public interface UpdateListener {
		public void displayUpdated(Object display);
	}

	public NonGLImageDisplay(int width, int height, boolean square) {
		this.square = square;
		setImageSize(sizeX, sizeY);
		//			this.createBufferStrategy(2);
		//			bufferStrategy = getBufferStrategy();			
	}
	public NonGLImageDisplay(int width, int height) {
		this(width, height, true);
	}

	public void addUpdateListener(UpdateListener listener) {
		listeners.add(listener);
	}
	public void removeUpdateListener(UpdateListener listener) {
		if (listeners.contains(listener)) 
			listeners.remove(listener);
	}
	protected void informAboutUpdate() {
		for (UpdateListener listener : listeners)
			listener.displayUpdated(this);
	}

	public void setSizeX(int sizeX) {
		setImageSize(sizeX, sizeY);
	}
	public void setSizeY(int sizeY) {
		setImageSize(sizeX, sizeY);
	}
	public int getSizeX() {
		return sizeX;
	}
	public int getSizeY() {
		return sizeY;
	}
	public void setImageSize(int sizeX, int sizeY) {
		if (sizeX != this.sizeX || sizeY != this.sizeY) {
			this.sizeX = sizeX;
			this.sizeY = sizeY;
			if (this.sizeX < 0)
				this.sizeX = 0;
			if (this.sizeY < 0)
				this.sizeY = 0;
//			this.pixmap = new Color[this.sizeX][this.sizeY];
			if (sizeX > 0 && sizeY > 0)
				this.image = new BufferedImage(this.sizeX, this.sizeY, BufferedImage.TYPE_INT_RGB);
			else 
				this.image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_BGR);
		}
	}

	public void setPixmapGray(int x, int y, int value) {
		if (value < 0)
			value = 0;
		else if (value > 255)
			value = 255;
		image.setRGB(x, sizeY-y-1, (value << 16)|(value << 8)|(value));
	}
	
	public void setPixmapGray(int x, int y, float value) {
		if (value < 0)
			value = 0;
		if (value > 1.0)
			value = 1;
		int r = (int)(value * 255f);
		image.setRGB(x, sizeY - y - 1, (r<<16) + (r<<8) + r);
//		if (value == 0.0)
//			this.pixmap[x][y] = null;
//		else
//			this.pixmap[x][y] = new Color(value, value, value);
	}
	public void setPixmapRGB(int x, int y, float r, float g, float b) {
		if (r < 0)
			r = 0;
		else if (r > 1.0)
			r = 1;
		
		if (g < 0)
			g = 0;
		else if (g > 1.0)
			g = 1;

		if (b < 0)
			b = 0;
		else if (b > 1.0)
			b = 1;
		
		image.setRGB(x, sizeY - y - 1, (((int)(r * 255f))<<16) + (((int)(g * 255f))<<8) + ((int)(b * 255f)));
//		if (r == 0.0 && g == 0.0 && b == 0.0)
//			this.pixmap[x][y] = null;
//		else {
//			this.pixmap[x][y] = new Color(r,g,b);
//		}
	}

	public void paint(Graphics g) {
		//			Graphics2D g2 = (Graphics2D)g;
		final Rectangle rect = g.getClipBounds();

		float rWidth = (float)rect.width / (float)sizeX;
		float rHeight = (float)rect.height / (float)sizeY;
		int imageWidth = rect.width;
		int imageHeight = rect.height;
		int imageStartX = 0;
		int imageStartY = 0;

		float startx = 0;
		float starty = rect.height;
		if (square) {
			if (rWidth < rHeight) {
				rHeight = rWidth;
				starty -= (rect.height - sizeY * rHeight) / 2;

				imageHeight = (int)(rWidth * sizeY);
				imageStartY = (int)((rect.height - sizeY * rHeight) / 2);
			}
			else {
				rWidth = rHeight;
				startx = (rect.width - sizeX * rWidth) / 2;

				imageWidth = (int)(rHeight * sizeX);
				imageStartX = (int)((rect.width - sizeX * rWidth) / 2);
			}
		}


		//			int w = (int)rWidth;
		//			if (w < 1)
		//				w = 1;
		//			int h = (int)rHeight;
		//			if (h < 1)
		//				h = 1;
		g.clearRect(rect.x, rect.y, rect.width, rect.height);
		g.setColor(Color.black);
		g.fillRect((int) startx, (int)(starty- sizeY*rHeight),(int)(sizeX * rWidth),(int)(sizeY*rHeight));
//		float posx = startx, nextPosx = posx+rWidth, posy = starty - rHeight, lastPosY = starty;
		
		
		g.drawImage(image, imageStartX, imageStartY, imageWidth, imageHeight, Color.black, null);
//		for (int x = 0; x < pixmap.length; x++) {
//			posy = starty - rHeight;
//			lastPosY = starty;
//			for (int y = 0; y < pixmap[x].length; y++) {
//				if (pixmap[x][y] != null) {
//					g.setColor(pixmap[x][y]);
//					g.fillRect((int)posx,  (int)posy, (int)nextPosx - (int)posx, (int)lastPosY - (int) posy);
//				}
//				lastPosY = posy;
//				posy -= rHeight;
//			}
//			posx = nextPosx;
//			nextPosx += rWidth;
//		}
		informAboutUpdate();
	}

	public static NonGLImageDisplay createNonGLDisplay() {
		return new NonGLImageDisplay(10,10);
	}

	@Deprecated
	public void setBorderSpacePixels(int pixels) {
	}

	@Deprecated
	public void setFontSize(int size) {
	}


}