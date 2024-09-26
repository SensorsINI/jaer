/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing.tracking;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import java.awt.geom.Point2D;
import java.util.Observable;
import java.util.Observer;


import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip2D;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.util.filter.BandpassFilter2d;

/**
 * Estimates optical flow from tracking many small features using
 * RectangularClusterTracker.
 *
 * @author tobi
 */
@Description("Estimates optical flow from tracking many small features using RectangularClusterTracker")
public class ClusterBasedOpticalFlow extends RectangularClusterTracker implements Observer {

	private int pixelsPerBin = getInt("pixelsPerBin", 8);
	private float tauLowpassMs = getFloat("tauLowpassMs", 30);
	private float tauCornerMs = getFloat("tauCornerMs", 1000);
	private boolean annotateClusterTracking = getBoolean("annotateClusterTracking", true);
	private float vectorScalingPixelsPerPPS = getFloat("vectorScalingPixelsPerPPS", .1f);
	private float spatialSmoothingFactor = getFloat("spatialSmoothingFactor", 0.05f);
	private OFPoint[][] grid = null;
	private int lastPacketTime = 0;

	public ClusterBasedOpticalFlow(AEChip chip) {
		super(chip);
		String key = "OpticalFlow";
		setPropertyTooltip(key, "pixelsPerBin", "grid spacing in pixels in x and y directions");
		setPropertyTooltip(key, "tauLowpassMs", "time constant for lowpass filter for flow estimates");
		setPropertyTooltip(key, "tauCornerMs", "time constant for highpass filter for flow estimates; flow decays to zero with this time constant");
		setPropertyTooltip(key, "annotateClusterTracking", "draw the cluster tracking annotation");
		setPropertyTooltip(key, "vectorScalingPixelsPerPPS", "vectors are scaled by this much from pixels per second to pixels");
		setPropertyTooltip(key, "spatialSmoothingFactor", "flow estimates are computed at each update by mixing in this much of each neighboring estimate");
		allocateGrid();
	}

	private void updateGridParams() {
		if (grid == null) {
			return;
		}
		for (OFPoint[] pa : grid) {
			for (OFPoint p : pa) {
				p.filter.setTauMsLow(tauLowpassMs);
				p.filter.setTauMsHigh(tauCornerMs);
			}
		}
	}


	private class OFPoint  {

		private BandpassFilter2d filter = new BandpassFilter2d(tauLowpassMs, tauCornerMs);
		private Point2D.Float location;
		private int lastUpdateTimeUs = 0;

		public OFPoint(float x, float y) {
			location = new Point2D.Float(x, y);
		}

		void update(Point2D.Float measurement, int timeUs) {
			filter.filter2d(measurement, timeUs);
			lastUpdateTimeUs = timeUs;
		}

		void draw(GL2 gl) {
			gl.glPushMatrix();
			gl.glTranslatef(location.x, location.y, 0);
			gl.glLineWidth(2);
			gl.glColor3f(1, 1, 1);
			gl.glPointSize(4);
			gl.glBegin(GL.GL_POINTS);
			gl.glVertex2f(0, 0);
			gl.glEnd();
			gl.glBegin(GL.GL_LINES);
			gl.glVertex2f(0, 0);
			Point2D.Float p = filter.getValue2d();
			gl.glVertex2f(p.x * vectorScalingPixelsPerPPS, p.y * vectorScalingPixelsPerPPS);
			gl.glEnd();
			gl.glPopMatrix();
		}
	}


	int pix2grid(float pix){
		return Math.round((pix-(0.5f*pixelsPerBin))/pixelsPerBin);
	}

	float grid2pix(int grid){
		return (0.5f*pixelsPerBin)+(grid*pixelsPerBin);
	}

	synchronized private void allocateGrid() {
		float sx = chip.getSizeX(), sy = chip.getSizeY();
		int nx = (int) Math.ceil(sx / pixelsPerBin), ny = (int) Math.ceil(sy / pixelsPerBin);
		grid = new OFPoint[nx][ny];
		for (int x = 0; x < nx; x++) {
			for (int y = 0; y < ny; y++) {
				grid[x][y] = new OFPoint(grid2pix(x), grid2pix(y));
			}
		}
	}

	@Override
	public synchronized void annotate(GLAutoDrawable drawable) {
		if (annotateClusterTracking) {
			super.annotate(drawable);
		}
		GL2 gl = drawable.getGL().getGL2();
		for (OFPoint[] pa : grid) {
			for (OFPoint p : pa) {
				if ((lastPacketTime - p.lastUpdateTimeUs) > 100000) {
					continue;
				}
				p.draw(gl);
			}
		}

	}

	@Override
	synchronized public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
		EventPacket out = super.filterPacket(in);

		lastPacketTime = in.getLastTimestamp();
		return out;
	}

	/**
	 * @return the pixelsPerBin
	 */
	 public int getPixelsPerBin() {
		return pixelsPerBin;
	}

	/**
	 * @param pixelsPerBin the pixelsPerBin to set
	 */
	 public void setPixelsPerBin(int pixelsPerBin) {
		int old = this.pixelsPerBin;
		if (pixelsPerBin < 1) {
			pixelsPerBin = 1;
		}
		this.pixelsPerBin = pixelsPerBin;
		putInt("pixelsPerBin", pixelsPerBin);
		allocateGrid();
	}

	/**
	 * @return the tauLowpassMs
	 */
	 public float getTauLowpassMs() {
		 return tauLowpassMs;
	 }

	 /**
	  * @param tauLowpassMs the tauLowpassMs to set
	  */
	 public void setTauLowpassMs(float tauLowpassMs) {
		 this.tauLowpassMs = tauLowpassMs;
		 putFloat("tauLowpassMs", tauLowpassMs);
		 updateGridParams();
	 }

	 /**
	  * @return the tauCornerMs
	  */
	 public float getTauCornerMs() {
		 return tauCornerMs;
	 }

	 /**
	  * @param tauCornerMs the tauCornerMs to set
	  */
	 public void setTauCornerMs(float tauCornerMs) {
		 this.tauCornerMs = tauCornerMs;
		 putFloat("tauCornerMs", tauCornerMs);
		 updateGridParams();
	 }

	 /**
	  * @return the annotateClusterTracking
	  */
	 public boolean isAnnotateClusterTracking() {
		 return annotateClusterTracking;
	 }

	 /**
	  * @param annotateClusterTracking the annotateClusterTracking to set
	  */
	 public void setAnnotateClusterTracking(boolean annotateClusterTracking) {
		 this.annotateClusterTracking = annotateClusterTracking;
		 putBoolean("annotateClusterTracking", annotateClusterTracking);
	 }

	 /**
	  * @return the vectorScalingPixelsPerPPS
	  */
	 public float getVectorScalingPixelsPerPPS() {
		 return vectorScalingPixelsPerPPS;
	 }

	 /**
	  * @param vectorScalingPixelsPerPPS the vectorScalingPixelsPerPPS to set
	  */
	 public void setVectorScalingPixelsPerPPS(float vectorScalingPixelsPerPPS) {
		 this.vectorScalingPixelsPerPPS = vectorScalingPixelsPerPPS;
		 putFloat("vectorScalingPixelsPerPPS", vectorScalingPixelsPerPPS);
	 }

	 @Override
	 public void update(Observable o, Object arg) {
		 super.update(o, arg);
		 if (arg instanceof UpdateMessage) {
			 for (Cluster c : clusters) {
				 if (!c.isVisible()) {
					 continue;
				 }
				 Point2D.Float cloc = c.getLocation();
				 int x = pix2grid(cloc.x);
				 if ((x < 0) || (x >= grid.length)) {
					 continue;
				 }
				 int y = pix2grid(cloc.y);
				 if ((y < 0) || (y >= grid[0].length)) {
					 continue;
				 }
				 grid[x][y].update(c.getVelocity(), c.getLastEventTimestamp());
				 int n = 0;
				 float sx = 0, sy = 0;
				 if (x > 0) {
					 n++;
					 sx += grid[x - 1][y].filter.getValue2d().x;
					 sy += grid[x - 1][y].filter.getValue2d().y;
				 }
				 if (x < (grid.length - 1)) {
					 n++;
					 sx += grid[x + 1][y].filter.getValue2d().x;
					 sy += grid[x + 1][y].filter.getValue2d().y;
				 }
				 if (y > 0) {
					 n++;
					 sx += grid[x][y - 1].filter.getValue2d().x;
					 sy += grid[x][y - 1].filter.getValue2d().y;
				 }
				 if (y < (grid[0].length - 1)) {
					 n++;
					 sx += grid[x][y + 1].filter.getValue2d().x;
					 sy += grid[x][y + 1].filter.getValue2d().y;
				 }
				 float m1=1-spatialSmoothingFactor;
				 float oldvx=grid[x][y].filter.getValue2d().x;
				 float oldvy=grid[x][y].filter.getValue2d().y;
				 grid[x][y].filter.setInternalValue2d((m1*oldvx)+((spatialSmoothingFactor*sx)/n),(m1*oldvy)+((spatialSmoothingFactor*sy)/n));

			 }
		 } else if ((o instanceof AEChip) || (arg == Chip2D.EVENT_SIZEY)) {
			 allocateGrid();
		 }
	 }

	 /**
	  * @return the spatialSmoothingFactor
	  */
	 public float getSpatialSmoothingFactor() {
		 return spatialSmoothingFactor;
	 }

	 /**
	  * @param spatialSmoothingFactor the spatialSmoothingFactor to set
	  */
	 public void setSpatialSmoothingFactor(float spatialSmoothingFactor) {
		 this.spatialSmoothingFactor = spatialSmoothingFactor;
		 putFloat("spatialSmoothingFactor",spatialSmoothingFactor);
	 }
}
