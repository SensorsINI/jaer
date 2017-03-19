/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing.tracking;

import java.awt.geom.Point2D;
import java.util.Observable;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.filter.LowpassFilter;
import net.sf.jaer.util.filter.LowpassFilter2D;

/**
 * Computes camera pose changes based on tracking many clusters of local activity.
 *
 * @author tobi
 */
@Description("Computes camera pose changes based on tracking many clusters of local activity")
public class OpticalGyro extends RectangularClusterTracker implements FrameAnnotater {

	private Point2D.Float translation = new Point2D.Float(); // translation in pixels
	private LowpassFilter2D translationFilter = new LowpassFilter2D(translation);
	private float rotationAngle = 0, cosAngle = 1, sinAngle = 0; // transform angle in radians
	private LowpassFilter rotationFilter = new LowpassFilter();
	//        Point2D.Float focusOfExpansion=new Point2D.Float();
	//        float expansion;
	//        private float[][] transform = new float[][]{{1, 0, 0}, {0, 1, 0}}; // transformEvent matrix from x,y,1 to xt,yt
	private Point2D.Float velocityPPt = new Point2D.Float();
	private int averageClusterAge = 0; // weighted average cluster age
	private SmallAngleTransformFinder smallAngleTransformFinder = new SmallAngleTransformFinder();
	private float opticalGyroTauLowpassMs = getFloat("opticalGyroTauLowpassMs", 100);
	private boolean opticalGyroRotationEnabled = getBoolean("opticalGyroRotationEnabled", false);
	int sx2 = 0, sy2 = 0;

	public OpticalGyro(AEChip chip) {
		super(chip);
		addObserver(this); // to getString updates during packet
		final String optgy = "Optical Gryo";
		setPropertyTooltip(optgy, "opticalGyroTauLowpassMs", "lowpass filter time constant in ms for optical gyro position, increase to smooth values");
		setPropertyTooltip(optgy, "opticalGyroEnabled", "enables global cluster movement reporting");
		setPropertyTooltip(optgy, "opticalGyroRotationEnabled", "false computes just translation, true computes linear transform tranalation plus rotation");
		translationFilter.setTauMs(opticalGyroTauLowpassMs);
	}

	@Override
	public synchronized void annotate(GLAutoDrawable drawable) {
		super.annotate(drawable);
		glAnnotate(drawable.getGL().getGL2());
	}

	/** Shows the transform on top of the rendered events.
	 *
	 * @param gl the OpenGL context.
	 */
	private void glAnnotate(GL2 gl) {
		// this whole annotation is translated by the enclosing filter SceneStabilizer so that
		// clusters appear on top of tracked features.
		int sx2 = chip.getSizeX() / 2, sy2 = chip.getSizeY() / 2;

		// draw translation
		gl.glPushMatrix();
		gl.glTranslatef(-translation.x + sx2, -translation.y + sy2, 0);
		gl.glRotatef((float) ((-rotationAngle * 180) / Math.PI), 0, 0, 1);
		//        gl.glTranslatef(sx2, sy2, 0);
		// draw translation
		gl.glLineWidth(2f);
		gl.glColor3f(0, 1, 1);
		gl.glBegin(GL.GL_LINES);
		gl.glVertex2f(-sx2, 0);
		gl.glVertex2f(sx2, 0);
		gl.glVertex2f(0, -sy2);
		gl.glVertex2f(0, sy2);
		gl.glEnd();
		gl.glPopMatrix();

		if (isUseVelocity()) {
			gl.glBegin(GL.GL_LINES);
			float x = (velocityPPt.x / 10) + sx2, y = (velocityPPt.y / 10) + sy2;
			gl.glVertex2f(sx2, sy2);
			gl.glVertex2f(x, y);
			gl.glEnd();
		}
	}

	@Override
	public synchronized EventPacket<?> filterPacket(EventPacket<?> in) {
		super.filterPacket(in);  // run cluster tracker
		if(!in.isEmpty())
		{
			update(in.getLastTimestamp()); // compute gryo parameters
		}
		return in;  // return input packet
	}

	@Override
	public synchronized void resetFilter() {
		super.resetFilter();
		translation.setLocation(0, 0);
		translationFilter.setInternalValue2d(chip.getSizeX() / 2, chip.getSizeY() / 2);
		rotationAngle = 0;
		rotationFilter.setInternalValue(0);
		velocityPPt.setLocation(0, 0);
		smallAngleTransformFinder.reset();
	}

	/** Transforms an event in-place according to the current gyro estimate of translation and tranalation velocityPPT with
	 * individual gains for each.
	 *
	 * @param e the event to transform.
	 */
	public void transformEvent(BasicEvent e, float gainTranslation, float gainVelocity) {
		if (opticalGyroRotationEnabled) {
			smallAngleTransformFinder.transformEvent(e, gainTranslation, gainVelocity); // float[] p0={e.x,e.y,1};
			//                float[] p1=new float[2];
			//                Matrix.multiply(transform,p0,p1);
			//                e.x=(short)Math.round(p1[0]);
			//                e.y=(short)Math.round(p1[1]);
		} else {
			int time = averageClusterAge;
			e.x += ((int) gainTranslation * translation.x) + (time * velocityPPt.x * gainVelocity);
			e.y += ((int) gainTranslation * translation.y) + (time * velocityPPt.y * gainVelocity);
		}
	}

	/** Computes transformEvent of scene ("translation" and optionally "rotation")
	 * from the history of cluster locations relative to
	 * their "birth location". Each cluster contributes according to its weight, which is
	 * the number of events it has collected.
	 *
	 * This is a bit tricky for several reasons.
	 *
	 * 1. The clusters are not born all at the same instant. Each cluster has moved since
	 * it has become visible, but depending on how long
	 * it has been alive it may have moved more or less. It may be
	 * that the clusters have moved
	 * to the right, for instance, and then moved back
	 * again to where they started. What do we use as our reference then?  We choose to try to
	 * transform all spikes or use the pantilt to getString events to
	 * come out as close as possible to where all the clusters started.

	 * @param t the time of the update in timestamp ticks (us).
	 */
	private void update(int t) {
		// update optical gyro value
		if (!isOpticalGyroRotationEnabled()) {
			int nn = getNumVisibleClusters();
			if (nn == 0) {
				return; // no visible clusters
			}

			// find cluster shifts and weight of each cluster, count visible clusters
			// the clusters are born at different times.
			// therefore find the shift of each cluster as though
			// it had lived as long as the oldest cluster.
			// then average all the clusters weighted by the number of events
			// collected by each cluster.
			int weightSum = 0;
			int ageSum = 0;
			nn = 0;
			Cluster oldestCluster = null;
			float avgxloc = 0, avgyloc = 0, avgxvel = 0, avgyvel = 0;
			for (Cluster c : clusters) {
				if (c.isVisible()) {
					float weight = c.getMassNow(t);
					weightSum += weight;
					avgxloc += (c.getLocation().x - c.getBirthLocation().x) * weight;
					avgyloc += (c.getLocation().y - c.getBirthLocation().y) * weight;
					avgxvel += c.getVelocityPPT().x * weight;
					avgyvel += c.getVelocityPPT().y * weight;
					ageSum += c.getLifetime() * weight;
					nn++;
					if ((oldestCluster == null) || (c.getBirthTime() < oldestCluster.getBirthTime())) {
						oldestCluster = c;
					}
				}
			}
			if (weightSum == 0) {
				return;
			}
			averageClusterAge = ageSum / weightSum;

			// compute weighted-mean scene shift, but only if there is at least 1 visible cluster

			if (nn > 0) {
				avgxloc /= weightSum;
				avgyloc /= weightSum;
				velocityPPt.x = avgxvel / weightSum;
				velocityPPt.y = avgyvel / weightSum;
				avgyvel /= weightSum;
				smallAngleTransformFinder.filterTransform(-avgxloc, -avgyloc, 0, t);
			}
			//            System.out.println(String.format("x,y= %.1f , %.1f ",avgxloc,avgyloc));
		} else { // using general transformEvent rather than weighted sum of cluster movements
			smallAngleTransformFinder.update(t);
		}

	}

	/**
	 * @return the velocityPPt of the transform, computed from weighted average cluster velocities
	 */
	public Point2D.Float getVelocityPPt() {
		return velocityPPt;
	}

	/**
	 * Finds optimal transform of form
	 * <pre>
	 * H=
	 * 1  -a  Tx
	 * a   1  Ty
	 * </pre>
	 * which tranforms a present event q expressed in homogenous coordinates
	 * (qx,qy,1) into an original position p=(px,py) via p=Hq.
	 * The P matrix are all the birth locations of clusters with one position per column, and the Q matrix
	 * are the present cluster locations expressed as one position per column.
	 *
	 * The solution here is the exact least squares fit that minimizes sum(P-Hq)^2.
	 */
	public class SmallAngleTransformFinder {

		/** Transforms an event in-place
		 * according to the current gyro estimate of translation and tranalation velocityPPT with
		 * individual gains for each. This transform should move the event back towards the locations they would have
		 * come from given the cluster birth locations.
		 *
		 * @param e the event to transform.
		 */
		public void transformEvent(BasicEvent e, float gainTranslation, float gainVelocity) {
			int sx2 = chip.getSizeX() / 2, sy2 = chip.getSizeY() / 2;
			e.x -= sx2;
			e.y -= sy2;
			e.x = (short) (((cosAngle * e.x) - (sinAngle * e.y)) + translation.x);
			e.y = (short) ((sinAngle * e.x) + (cosAngle * e.y) + translation.y);
			e.x += sx2;
			e.y += sy2;
		}

		private class DebugCluster extends Cluster {

			DebugCluster(int bx, int by, int tx, int ty, float a) {
				super();
				int sx2 = chip.getSizeX() / 2, sy2 = chip.getSizeY() / 2;
				float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
				float x = ((cos * (bx - sx2)) - (sin * (by - sy2))) + tx + sx2, y = (sin * (bx - sx2)) + (cos * (by - sy2)) + ty + sy2;  // xform birth by a, tx,ty
				setLocation(new Point2D.Float(x, y));
				setBirthLocation(new Point2D.Float(bx, by));
				hasObtainedSupport = true;
			}
		}

		public void update(int t) {
			float qy2 = 0, qx2 = 0, qy = 0, qx = 0, px = 0, py = 0, pxqy = 0, pyqx = 0; // statistics of inputs
			int sx2 = chip.getSizeX() / 2, sy2 = chip.getSizeY() / 2;
			int n = getNumVisibleClusters();
			if (n < 3) {
				//                    log.warning("only " + n + " clusters, need at least 3");
				return;
			}
			// debug
			//                clusters = new ArrayList<Cluster>();
			//                int tx = 0, ty = 0;
			//                float a = -0.1f;
			////                for ( int i = 0 ; i < 30 ; i++ ){
			////                    clusters.add(new DebugCluster(random.nextInt(128),random.nextInt(128),tx,ty,a));
			////                }
			//                     clusters.add(new DebugCluster(64,74,tx,ty,a));
			//                     clusters.add(new DebugCluster(74,74,tx,ty,a));
			//                     clusters.add(new DebugCluster(74,64,tx,ty,a));
			//               n = clusters.size();

			// form sums
			int wSum = 0;
			for (Cluster c : clusters) {
				if (!c.isVisible()) {
					continue;
				}
				int w = 1; // c.getNumEvents();
				wSum += w;
				float ppx = c.getBirthLocation().x - sx2; // birth loc
				float ppy = c.getBirthLocation().y - sy2;
				float qqx = c.getLocation().x - sx2; // present loc
				float qqy = c.getLocation().y - sy2;
				qy2 += w * (qqy * qqy);
				qx2 += w * (qqx * qqx);
				qx += w * (qqx);
				qy += w * (qqy);
				px += w * (ppx);
				py += w * (ppy);
				pxqy += w * (ppx * qqy);
				pyqx += w * (ppy * qqx);
			}
			// a=num/den
			float aden = ((qy2 + qx2) - (((qy * qy) + (qx * qx)) / wSum));
			if (Math.abs(aden) < (Float.MIN_NORMAL * 100)) {
				log.warning("singlular or nearly singular solution for transform angle, demoninator=" + aden);
				return;
			}
			float anum = (((((qy * (px - qx)) - (qx * (py - qy))) / wSum) - pxqy) + pyqx);
			float instantaneousAngle = anum / aden;
			float translationx = ((px - qx) + (instantaneousAngle * qy)) / (wSum);
			float translationy = ((py - qy) - (instantaneousAngle * qx)) / (wSum);
			cosAngle = (float) Math.cos(rotationAngle);
			sinAngle = (float) Math.sin(rotationAngle);
			filterTransform(translationx, translationy, instantaneousAngle, t);
		}

		public void reset() {
			rotationAngle = 0;
			rotationFilter.setInternalValue(0);
			translation.setLocation(0, 0);
			translationFilter.setInternalValue2d(0, 0);
		}
		//            public String toString (){
		//                return String.format("tx = %-8.1f ty = %-8.1f a = %-8.4f",translation.x,translation.y,instantaneousAngle);
		//            }

		/** Filters the actual OpticalGyro transform values given instantaneous values.
		 *
		 * @param tx x translation.
		 * @param ty y translation.
		 * @param rotation in radians, CCW.
		 * @param t time in timestamp ticks (us).
		 */
		private void filterTransform(float tx, float ty, float rotation, int t) {

			translation = translationFilter.filter(tx, ty, t);
			if (isOpticalGyroRotationEnabled()) {
				rotationAngle = rotationFilter.filter(rotation, t);
			}

		}
	}

	/**
	 * @return the opticalGyroRotationEnabled
	 */
	public boolean isOpticalGyroRotationEnabled() {
		return opticalGyroRotationEnabled;
	}

	/**
	 * @param opticalGyroRotationEnabled the opticalGyroRotationEnabled to set
	 */
	public void setOpticalGyroRotationEnabled(boolean opticalGyroRotationEnabled) {
		this.opticalGyroRotationEnabled = opticalGyroRotationEnabled;
		putBoolean("opticalGyroRotationEnabled", opticalGyroRotationEnabled);
	}

	/** Returns the rotation instantaneousAngle in radians, >0 for CCW rotation, computed from the OpticalGyro.
	 *
	 * @return instantaneousAngle in radians, CCW gives >0.
	 */
	public float getOpticalGyroRotation() {
		return rotationAngle;
	}

	/** Returns the current location of the optical gyro filter translation

    @return a Point2D.Float with x,y, values that show the present position of the gyro output. Returns null if the optical gyro is disabled.
	 */
	public Point2D.Float getOpticalGyroTranslation() {
		return translation;
	}

	/** Represents a single tracked object */
	/**
	 * @return the opticalGyroTauLowpassMs
	 */
	public float getOpticalGyroTauLowpassMs() {
		return opticalGyroTauLowpassMs;
	}

	/** Sets the highpass corner time constant. The optical gyro values decay to 0 with this time constant.
	 * @param opticalGyroTauLowpassMs the opticalGyroTauLowpassMs to set
	 */
	public void setOpticalGyroTauLowpassMs(float opticalGyroTauLowpassMs) {
		this.opticalGyroTauLowpassMs = opticalGyroTauLowpassMs;
		putFloat("opticalGyroTauLowpassMs", opticalGyroTauLowpassMs);
		translationFilter.setTauMs(opticalGyroTauLowpassMs);
		rotationFilter.setTauMs(opticalGyroTauLowpassMs);
	}

	@Override
	public void update(Observable o, Object arg) { // called by enclosed tracker
		super.update(o, arg);
		if (arg instanceof UpdateMessage) {
			update(((UpdateMessage) arg).timestamp); // update gryo every time the cluster locations are updated
		}
	}
}
