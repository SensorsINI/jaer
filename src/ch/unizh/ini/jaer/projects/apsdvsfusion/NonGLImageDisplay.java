package ch.unizh.ini.jaer.projects.apsdvsfusion;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;

import javax.swing.JPanel;

public class NonGLImageDisplay extends JPanel {
		Color[][] pixmap;
		int sizeX = -1, sizeY = -1;
		boolean square;
		private float rectWidth, rectHeight; 
		
		public NonGLImageDisplay(int width, int height, boolean square) {
			this.square = square;
			setImageSize(sizeX, sizeY);
//			this.createBufferStrategy(2);
//			bufferStrategy = getBufferStrategy();			
		}
		public NonGLImageDisplay(int width, int height) {
			this(width, height, true);
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
				this.pixmap = new Color[this.sizeX][this.sizeY];
			}
		}
		
		public void setPixmapGray(int x, int y, float value) {
			if (value == 0.0)
				this.pixmap[x][y] = null;
			else
				this.pixmap[x][y] = new Color(value, value, value);
		}
		public void setPixmapRGB(int x, int y, float r, float g, float b) {
			if (r == 0.0 && g == 0.0 && b == 0.0)
				this.pixmap[x][y] = null;
			else
				this.pixmap[x][y] = new Color(r,g,b);
		}

		public void paint(Graphics g) {
//			Graphics2D g2 = (Graphics2D)g;
			final Rectangle rect = g.getClipBounds();
			
			float rWidth = (float)rect.width / (float)sizeX;
			float rHeight = (float)rect.height / (float)sizeY;
			
			float startx = 0;
			float starty = rect.height;
			if (square) {
				if (rWidth < rHeight) {
					rHeight = rWidth;
					starty -= (rect.height - sizeY * rHeight) / 2;
				}
				else {
					rWidth = rHeight;
					startx = (rect.width - sizeX * rWidth) / 2;
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
			float posx = startx, nextPosx = posx+rWidth, posy = starty - rHeight, lastPosY = starty;
			for (int x = 0; x < pixmap.length; x++) {
				posy = starty - rHeight;
				lastPosY = starty;
				for (int y = 0; y < pixmap[x].length; y++) {
					if (pixmap[x][y] != null) {
						g.setColor(pixmap[x][y]);
						g.fillRect((int)posx,  (int)posy, (int)nextPosx - (int)posx, (int)lastPosY - (int) posy);
					}
					lastPosY = posy;
					posy -= rHeight;
				}
				posx = nextPosx;
				nextPosx += rWidth;
			}
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