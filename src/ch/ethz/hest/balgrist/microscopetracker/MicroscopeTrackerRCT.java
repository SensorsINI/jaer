package ch.ethz.hest.balgrist.microscopetracker;

import java.awt.geom.Point2D;
import java.util.LinkedList;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.util.DrawGL;

/**
 * Tracks multiple clusters and computes global average velocity of all clusters and uses TCP to send the vector to LabView
 *
 * @author Niklaus Amrein
 */
@Description("Tracks multiple clusters and computes global average velocity of all clusters and uses TCP to send the vector to LabView")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class MicroscopeTrackerRCT extends RectangularClusterTracker {

	// TODO Median Filter instead of mean average
	// TODO initialization of time1, time2 requires unnecessary if-statement for every updateAverageVelocity
	// TODO averageVelocityPPS is only used for debugging, visualization and can be deleted

	// constructor of the class
	public MicroscopeTrackerRCT(AEChip chip) {
		super(chip);
		String mt = "1: MicroscropeTrackerRCT";
		setPropertyTooltip(mt, "showGlobalAverageVelocity", "draws the estimated velocity vector");
		setPropertyTooltip(mt, "sendDataToLabview",
			"sends estimated position to LavView via TCP link on \"127.0.0.1\", port 23");
	}

	// our new functions are deactivated when the program is started
	private boolean showGlobalAverageVelocity = false;
	private boolean sendDataToLabview = false;

	// these values have to be initialized before the first time we send a vector
	private Point2D.Float globalAverageVelocityPPT = new Point2D.Float(0, 0); // [px/tick]
	private Point2D.Float globalAverageVelocityPPS = new Point2D.Float(0, 0); // [px/s]
	private Point2D.Float globalPosition = new Point2D.Float(0, 0); // [px]

	// construct a client for the TCP communication with LabView
	MicroscopeTrackerTCPclient Client = new MicroscopeTrackerTCPclient();

	// time of last cluster updates
	float time1 = 0; // [tick]
	float time2 = 0; // [tick]

	// this function has to be changed to also draw the average speed vector when we activate it
	@Override
	public synchronized void annotate(GLAutoDrawable drawable) {
		super.annotate(drawable);

		if (showGlobalAverageVelocity) {
			GL2 gl = drawable.getGL().getGL2();
			gl.glColor3f(1, 1, 0);
			gl.glPointSize(5f);
			gl.glBegin(GL.GL_POINTS);
			gl.glVertex2d(64, 64);
			gl.glEnd();
			DrawGL.drawVector(gl, 64, 64, globalAverageVelocityPPS.x, globalAverageVelocityPPS.y);
		}
	}

	// Override Factory Methods
	@Override
	public Cluster createCluster(BasicEvent ev) {
		return new Cluster(ev);
	}

	@Override
	public Cluster createCluster(RectangularClusterTracker.Cluster one, RectangularClusterTracker.Cluster two) {
		return new Cluster(one, two);
	}

	@Override
	public Cluster createCluster(BasicEvent ev, OutputEventIterator itr) {
		return new Cluster(ev, itr);
	}

	// the Cluster class needs to be extended
	public class Cluster extends RectangularClusterTracker.Cluster {

		// new Constructors
		public Cluster() {
			super();
		}

		public Cluster(BasicEvent ev) {
			super(ev);
		}

		public Cluster(BasicEvent ev, OutputEventIterator outItr) {
			super(ev, outItr);
		}

		public Cluster(RectangularClusterTracker.Cluster one, RectangularClusterTracker.Cluster two) {
			super(one, two);
		}

		// update average velocity when Cluster vanishes
		@Override
		protected void prune() {
			updateAverageVelocity();
		}

		// update average velocity when speed of one of the clusters changes
		@Override
		protected void updateVelocity() {
			super.updateVelocity();
			if (sendDataToLabview) {
				if (Client.sendVector(globalPosition.x, globalPosition.y) == true) {
					// send was successful
				}
				else {
					setsendDataToLabview(false);
				}
			}
			updateAverageVelocity();
		}

		// Method to send previous vector and to calculate new average velocity
		public void updateAverageVelocity() {
			if ((time1 == 0) && (time2 == 0)) {
				time1 = lastUpdateTime; // [tick]
				time2 = lastUpdateTime; // [tick]
			}
			time1 = time2; // [tick]
			time2 = lastUpdateTime; // [tick]

			// calculate new velocity vector
			globalAverageVelocityPPT.x = 0;
			globalAverageVelocityPPT.y = 0;
			globalAverageVelocityPPS.x = 0;
			globalAverageVelocityPPS.y = 0;

			LinkedList<RectangularClusterTracker.Cluster> Clusters = getVisibleClusters();
			int NoC = Clusters.size();

			// 0/0 results in NaN -> error when sending NaN as float
			if (NoC > 0) {
				for (int i = 0; i < NoC; i++) {
					globalAverageVelocityPPT.x += Clusters.get(i).getVelocityPPT().x; // [px/tick]
					globalAverageVelocityPPT.y += Clusters.get(i).getVelocityPPT().y; // [px/tick]
					globalAverageVelocityPPS.x += Clusters.get(i).getVelocityPPS().x; // [px/s]
					globalAverageVelocityPPS.y += Clusters.get(i).getVelocityPPS().y; // [px/s]
				}
				globalAverageVelocityPPT.x /= NoC;
				globalAverageVelocityPPT.y /= NoC;
				globalAverageVelocityPPS.x /= NoC;
				globalAverageVelocityPPS.y /= NoC;
			}

			// calculate new global Position
			globalPosition.x += (globalAverageVelocityPPT.x * (time2 - time1)); // [px]
			globalPosition.y += (globalAverageVelocityPPT.y * (time2 - time1)); // [px]
		}
	}

	// check if vector should be shown
	public boolean getshowGlobalAverageVelocity() {
		return showGlobalAverageVelocity;
	}

	// set if vector should be shown
	public void setshowGlobalAverageVelocity(boolean v) {
		if (v == true) {
			log.info("displaying vector");
			showGlobalAverageVelocity = true;
		}
		else {
			log.info("not displaying vector");
			showGlobalAverageVelocity = false;
		}
	}

	// check if data should be sent to LabView
	public boolean getsendDataToLabview() {
		return sendDataToLabview;
	}

	// set if vector should be sent to LabView
	public void setsendDataToLabview(boolean v) {
		if (v == true) {
			// reset global position
			globalPosition.x = 0;
			globalPosition.y = 0;

			// open connection to 127.0.0.1, port 23
			if (Client.createClient("127.0.0.1", 23) == true) {
				log.info("sending data");
				sendDataToLabview = true;
			}
			else {
				log.warning("failed to connect to server");
				sendDataToLabview = false;
			}

		}
		else {
			Client.closeClient();

			// reset global position
			globalPosition.x = 0;
			globalPosition.y = 0;

			log.info("not sending data");
			sendDataToLabview = false;
		}
	}
}
