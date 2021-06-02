package ch.ethz.hest.balgrist.microscopetracker;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.logging.FileHandler;
import java.util.logging.Logger;


import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.util.DrawGL;

/**
 * Tracks multiple clusters and computes global average velocity of all clusters
 * and uses TCP to send the vector to LabView
 *
 * @author Niklaus Amrein
 *
 * This filter is working nicely. The best function to calculate the position seems to be followVisibleMedianVelocity, compared to the mean it removed a lot of noise.
 * The position is a bit noisy, but with the BackgroundActivityFilter and good settings on the microscope, the whole system works.
 * However the position error sums up over time and can reach random magnitude.
 *
 * TODO initialization of time1 and time2 requires unnecessary if-statement for every updateAverageVelocity
 *
 * TODO averageVelocityPPS can be deleted because it's only used for visualization
 *
 */
@Description("Tracks multiple clusters and computes global average velocity of all clusters and uses TCP to send the vector to LabView")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class MicroscopeTrackerRCT extends RectangularClusterTracker {

	// constructor of the class
	public MicroscopeTrackerRCT(AEChip chip) {
		super(chip);
		String mt = "1: MicroscropeTrackerRCT";
		setPropertyTooltip(mt, "followGlobalMeanVelocity", "follows the mean velocity vector of all clusters (visible and invisible)");
		setPropertyTooltip(mt, "followVisibleMeanVelocity", "follows the mean velocity vector of all visible clusters");
		setPropertyTooltip(mt, "followVisibleMedianVelocity", "follows the median velocity vector of all visible clusters");
		setPropertyTooltip(mt, "sendDataToLabview", "sends estimated position to LavView via TCP link on \"127.0.0.1\", port 23");
		setPropertyTooltip(mt, "logData", "log data to a text file");
	}

	// new functions are deactivated when the program is started
	private boolean followGlobalMeanVelocity = false;
	private boolean followVisibleMeanVelocity = false;
	private boolean followVisibleMedianVelocity = false;
	private boolean sendDataToLabview = false;
	private boolean logData = false;

	// these values have to be initialized before the first time we send a vector
	private Point2D.Float globalAverageVelocityPPT = new Point2D.Float(0, 0); // [px/tick]
	private Point2D.Float globalAverageVelocityPPS = new Point2D.Float(0, 0); // [px/s]
	private Point2D.Float globalPosition = new Point2D.Float(0, 0); // [px]

	// list to sort the clusters the clusters according to their velocity
	private ArrayList<ClusterElement> sortedVelocitiesX = new ArrayList<>();
	private ArrayList<ClusterElement> sortedVelocitiesY = new ArrayList<>();

	// construct a client for the TCP communication with LabView
	private MicroscopeTrackerTCPclient client = new MicroscopeTrackerTCPclient();

	// construct a logger to record the data
	Logger txtLog;
	FileHandler txtFH;

	// time of last cluster updates
	private float time1 = 0; // [tick]
	private float time2 = 0; // [tick]

	@Override
	public synchronized void resetFilter() {
		super.resetFilter();

		resetValues();
	}

	@Override
	protected void updateClusterList(int t) {
		super.updateClusterList(t);

		// update sorted Lists
		sortedVelocitiesX.clear();
		sortedVelocitiesY.clear();

		LinkedList<RectangularClusterTracker.Cluster> c = getVisibleClusters();

		for (int i = 0; i < c.size(); i++) {
			ClusterElement ex = new ClusterElement(c.get(i).getVelocityPPT().x, c.get(i).getVelocityPPS().x, c.hashCode());
			ClusterElement ey = new ClusterElement(c.get(i).getVelocityPPT().y, c.get(i).getVelocityPPS().y, c.hashCode());

			binaryInsert(sortedVelocitiesX, ex, 0, sortedVelocitiesX.size());
			binaryInsert(sortedVelocitiesY, ey, 0, sortedVelocitiesY.size());
		}
	}

	// this function has to be changed to also draw the average speed vector when activated it
	@Override
	public synchronized void annotate(GLAutoDrawable drawable) {
		super.annotate(drawable);

		// vector and position are always displayed in blue
		GL2 gl = drawable.getGL().getGL2();
		gl.glColor3f(0, 0, 1);
		gl.glPointSize(5f);
		gl.glBegin(GL.GL_POINTS);
		gl.glVertex2d(64 + globalPosition.x, 64 + globalPosition.y);
		gl.glEnd();
		DrawGL.drawVector(gl, 64, 64, globalAverageVelocityPPS.x, globalAverageVelocityPPS.y);
	}

	// Override Factory Methods
	@Override
	public Cluster createCluster(BasicEvent ev) {
		return new Cluster(ev);
	}

	@Override
	public Cluster createCluster(RectangularClusterTracker.Cluster one, RectangularClusterTracker.Cluster two) {
		return new Cluster((Cluster) one, (Cluster) two);
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

		public Cluster(Cluster one, Cluster two) {
			super(one, two);
		}

		// update average velocity when Cluster vanishes
		@Override
		protected void onPruning() {
			if (followGlobalMeanVelocity) {
				updateGlobalMeanVelocity();
			}
			else if (followVisibleMeanVelocity) {
				updateVisibleMeanVelocity();
			}
			else if (followVisibleMedianVelocity) {
				updateVisibleMedianVelocity();
			}
			else {
				resetValues();
			}
		}

		// update average velocity when speed of one of the clusters changes
		@Override
		protected void updateVelocity() {
			super.updateVelocity();

			if (followGlobalMeanVelocity) {
				updateGlobalMeanVelocity();
			}
			else if (followVisibleMeanVelocity) {
				updateVisibleMeanVelocity();
			}
			else if (followVisibleMedianVelocity) {
				updateVisibleMedianVelocity();
			}
			else {
				resetValues();
			}
		}

		// Method to send previous vector and to calculate new average velocity from all clusters
		private void updateGlobalMeanVelocity() {
			if ((time2 == 0) && (time1 == 0)) {
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

			int NoC = clusters.size();

			// 0/0 results in NaN -> error when sending NaN as float
			if (NoC > 0) {
				for (int i = 0; i < NoC; i++) {
					globalAverageVelocityPPT.x += clusters.get(i).getVelocityPPT().x; // [px/tick]
					globalAverageVelocityPPT.y += clusters.get(i).getVelocityPPT().y; // [px/tick]
					globalAverageVelocityPPS.x += clusters.get(i).getVelocityPPS().x; // [px/s]
					globalAverageVelocityPPS.y += clusters.get(i).getVelocityPPS().y; // [px/s]
				}
				globalAverageVelocityPPT.x /= NoC;
				globalAverageVelocityPPT.y /= NoC;
				globalAverageVelocityPPS.x /= NoC;
				globalAverageVelocityPPS.y /= NoC;
			}

			// calculate new global Position
			globalPosition.x += (globalAverageVelocityPPT.x * (time2 - time1)); // [px]
			globalPosition.y += (globalAverageVelocityPPT.y * (time2 - time1)); // [px]

			if (sendDataToLabview) {
				client.sendVector(globalPosition.x, globalPosition.y);
			}

			// log the values to a text file if requested
			if (logData) {
				// log something
				txtLog.info(globalAverageVelocityPPT.x + ", " + globalAverageVelocityPPT.y);
			}
		}

		// Method to send previous vector and to calculate new average velocity from all visible clusters
		private void updateVisibleMeanVelocity() {
			if ((time2 == 0) && (time1 == 0)) {
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

			LinkedList<RectangularClusterTracker.Cluster> c = getVisibleClusters();

			int numberOfClusters = c.size();

			// 0/0 results in NaN -> error when sending NaN as float
			if (numberOfClusters > 0) {
				for (int i = 0; i < numberOfClusters; i++) {
					globalAverageVelocityPPT.x += c.get(i).getVelocityPPT().x; // [px/tick]
					globalAverageVelocityPPT.y += c.get(i).getVelocityPPT().y; // [px/tick]
					globalAverageVelocityPPS.x += c.get(i).getVelocityPPS().x; // [px/s]
					globalAverageVelocityPPS.y += c.get(i).getVelocityPPS().y; // [px/s]
				}
				globalAverageVelocityPPT.x /= numberOfClusters;
				globalAverageVelocityPPT.y /= numberOfClusters;
				globalAverageVelocityPPS.x /= numberOfClusters;
				globalAverageVelocityPPS.y /= numberOfClusters;
			}

			// calculate new global Position
			globalPosition.x += (globalAverageVelocityPPT.x * (time2 - time1)); // [px]
			globalPosition.y += (globalAverageVelocityPPT.y * (time2 - time1)); // [px]

			if (sendDataToLabview) {
				client.sendVector(globalPosition.x, globalPosition.y);
			}

			// log the values to a text file if requested
			if (logData) {
				// log something
				txtLog.info(globalAverageVelocityPPT.x + ", " + globalAverageVelocityPPT.y);
			}
		}

		// Method to send previous vector and to calculate new median velocity
		private void updateVisibleMedianVelocity() {
			if ((time1 == 0) && (time2 == 0)) {
				time1 = lastUpdateTime; // [tick]
				time2 = lastUpdateTime; // [tick]
			}
			time1 = time2; // [tick]
			time2 = lastUpdateTime; // [tick]

			// chose median from sorted lists
			int size = sortedVelocitiesX.size();

			if ((size % 2) == 0) {
				if (size == 0) {
					globalAverageVelocityPPT.x = 0;
					globalAverageVelocityPPT.y = 0;
					globalAverageVelocityPPS.x = 0;
					globalAverageVelocityPPS.y = 0;
				}
				else {
					globalAverageVelocityPPT.x = (sortedVelocitiesX.get((size / 2) - 1).getVelocityPPT() + sortedVelocitiesX.get(size / 2).getVelocityPPT()) / 2;
					globalAverageVelocityPPT.y = (sortedVelocitiesY.get((size / 2) - 1).getVelocityPPT() + sortedVelocitiesY.get(size / 2).getVelocityPPT()) / 2;
					globalAverageVelocityPPS.x = (sortedVelocitiesX.get((size / 2) - 1).getVelocityPPS() + sortedVelocitiesX.get(size / 2).getVelocityPPS()) / 2;
					globalAverageVelocityPPS.y = (sortedVelocitiesY.get((size / 2) - 1).getVelocityPPS() + sortedVelocitiesY.get(size / 2).getVelocityPPS()) / 2;
				}
			}
			else {
				globalAverageVelocityPPT.x = sortedVelocitiesX.get((size - 1) / 2).getVelocityPPT();
				globalAverageVelocityPPT.y = sortedVelocitiesY.get((size - 1) / 2).getVelocityPPT();
				globalAverageVelocityPPS.x = sortedVelocitiesX.get((size - 1) / 2).getVelocityPPS();
				globalAverageVelocityPPS.y = sortedVelocitiesY.get((size - 1) / 2).getVelocityPPS();
			}

			// calculate new global Position
			globalPosition.x += (globalAverageVelocityPPT.x * (time2 - time1)); // [px]
			globalPosition.y += (globalAverageVelocityPPT.y * (time2 - time1)); // [px]

			if (sendDataToLabview) {
				client.sendVector(globalPosition.x, globalPosition.y);
			}

			// log the values to a text file if requested
			if (logData) {
				// log something
				txtLog.info(globalAverageVelocityPPT.x + ", " + globalAverageVelocityPPT.y);
			}
		}
	}

	// reset all variables and Lists
	private void resetValues() {
		globalPosition.x = 0;
		globalPosition.y = 0;

		globalAverageVelocityPPT.x = 0;
		globalAverageVelocityPPT.y = 0;

		globalAverageVelocityPPS.x = 0;
		globalAverageVelocityPPS.y = 0;

		sortedVelocitiesX.clear();
		sortedVelocitiesY.clear();

		time1 = 0;
		time2 = 0;
	}

	// function to delete a cluster element from the list
	private void deleteOldEntry(ArrayList<ClusterElement> list, int id) {
		for (int i = 0; i < list.size(); i++) {
			if (id == list.get(i).getClusterID()) {
				list.remove(i);
				break;
			}
		}
	}

	// function to insert a new ClusterElement with binary sort, at the moment using the PPT velocity
	private void binaryInsert(ArrayList<ClusterElement> list, ClusterElement c, int index1, int index2) {
		if ((index2 - index1) == 0) {
			// log.info("case 1");
			list.add(index1, c);
		}
		else if (c.getVelocityPPT() > list.get(index1 + ((index2 - index1) / 2)).getVelocityPPT()) {
			// log.info("case 2");
			binaryInsert(list, c, index1 + ((index2 - index1) / 2) + 1, index2);
		}
		else {
			// log.info("case 3");
			binaryInsert(list, c, index1, index1 + ((index2 - index1) / 2));
		}
	}

	// check if global mean vector should be calculated
	public boolean getFollowGlobalMeanVelocity() {
		return followGlobalMeanVelocity;
	}

	// set if global mean vector should be calculated
	public void setFollowGlobalMeanVelocity(boolean v) {
		resetValues();

		if (v == true) {
			log.info("following mean velocity vector");
			followGlobalMeanVelocity = true;
		}
		else {
			log.info("not following mean velocity vector");
			followGlobalMeanVelocity = false;
		}
	}

	// check if mean vector from visible clusters should be calculated
	public boolean getFollowVisibleMeanVelocity() {
		return followGlobalMeanVelocity;
	}

	// set if mean vector from visible clusters should be calculated
	public void setFollowVisibleMeanVelocity(boolean v) {
		resetValues();

		if (v == true) {
			log.info("following mean velocity vector");
			followGlobalMeanVelocity = true;
		}
		else {
			log.info("not following mean velocity vector");
			followGlobalMeanVelocity = false;
		}
	}

	// check if median vector from visible clusters should be calculated
	public boolean getFollowVisibleMedianVelocity() {
		return followVisibleMedianVelocity;
	}

	// set if median vector from visible clusters should be calculated
	public void setFollowVisibleMedianVelocity(boolean v) {
		if (v == true) {
			log.info("following median velocity vector");
			followVisibleMedianVelocity = true;
		}
		else {
			log.info("not following median velocity vector");
			followVisibleMedianVelocity = false;
		}
	}

	// check if data should be sent to LabView
	public boolean getSendDataToLabview() {
		return sendDataToLabview;
	}

	// set if vector should be sent to LabView
	public void setSendDataToLabview(boolean v) {
		resetValues();

		if (v == true) {
			// open connection to 127.0.0.1, port 23
			if (client.createClient("127.0.0.1", 23) == true) {
				log.info("sending data");
				sendDataToLabview = true;
			}
			else {
				log.warning("failed to connect to server");
				sendDataToLabview = false;
			}
		}
		else {
			client.closeClient();

			log.info("not sending data");
			sendDataToLabview = false;
		}
	}

	// check if data should be written to a log file
	public boolean getLogData() {
		return logData;
	}

	// set if data should be written to a log file
	public void setLogData(boolean v) {
		if (v == true) {
			logData = true;
			// create logger
			txtLog = Logger.getLogger(MicroscopeTracker.class.getName());
			txtLog.setUseParentHandlers(false);

			try {
				// This block configures the logger with handler and formatter
				txtFH = new FileHandler("C:/Users/Niggi Amrein/Documents/Retina Camera/Logged Data/log files/logfile");
				txtLog.addHandler(txtFH);

			}
			catch (SecurityException e) {
				e.printStackTrace();
			}
			catch (IOException e) {
				e.printStackTrace();
			}

			log.info("logging data enabled");
		}
		else {
			logData = false;
			txtFH.close();
			txtLog.removeHandler(txtFH);
			log.info("logging data disabled");
		}
	}
}
