package ch.ethz.hest.balgrist.microscopetracker;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.FileHandler;
import java.util.logging.Logger;


import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.aemonitor.AEConstants;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.DrawGL;

/**
 * calculates the xy-movement of the sample under the microscope and uses TCP to
 * send the vector to LabView
 *
 * @author Niklaus Amrein
 *
 * TODO the algorithm is working well for slow vertical or slow horizontal movement, the position is a bit slow but that could be due to the median (lots of small values and a few big ones)
 * 		for diagonal movement, the position moves slower, probably because the diagonal pixels are not calculated as nearest neighbors.
 * 		for fast movement, the position updates are even slower, probably because not all pixels on the way are updated and that means that the nearest neighbors do not always contain the recent timestamps
 *
 * TODO get rid of the "drift"
 * 		the position tends to drift away, probably because the direction is chosen from either the positive or negative list.
 * 		This means that the result is either positive or negative and very unlikely to become 0.
 *
 * TODO "on" and "off" events are currently not treated differently
 * 		-> can we gain more precise information from that or not?
 *
 * TODO Currently currentTime is just the most recent timestamp, maybe there is a better way to implement this.
 * 		I implemented it like this so i can be sure that events with an older timestamp do not decrease the currentTime to a lower value than lastTime, which would result in a negative dt.
 *
 * TODO The difference currentTime - lastTime is not necessarily the correct time difference to calculate the position.
 * 		But time - told is not working correctly, position values are too big, probably because the time intervals overlap a lot
 *
 * TODO	If recorded data is rewinded, the currentTime is stuck at the highest value from the end of the recording.
 *		In the RectangularClusterTracker filter, this is not a problem, which
 *
 * TODO calculating the position is not using the correct time interval: The chosen median value is valid for the corresponding dt, but (currentTime - lastTime) is not the same.
 * 		However, if we use the correct dt (could for example be saved in VTelements), the time intervals dt could overlap or have gaps and the position would move a whole pixel per cycle
 * 		-> not necessarily correct either
 *
 */
@Description("calculates the xy-movement of the sample under the microscope and uses TCP to send the vector to LabView")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class MicroscopeTracker extends EventFilter2D implements FrameAnnotater {

	private final float VELPPS_SCALING = 1e6f / AEConstants.TICK_DEFAULT_US;

	// new functions (by default they are deactivated when the program is started)
	private boolean followGlobalMedianVelocity1 = false;
	private boolean followGlobalMedianVelocity2 = false;
	private boolean sendDataToLabview = false;
	private boolean logData = false;

	// new parameters
	private int thresholdTime = 10;
	private float percentage = (float) 0.1;
	private int minNumberOfEvents = 10;

	// construct a logger to record the data
	Logger txtLog;
	FileHandler txtFH;

	// construct a client for the TCP communication with LabView
	MicroscopeTrackerTCPclient client = new MicroscopeTrackerTCPclient();

	// value for the current time and the last time the timer was updated
	private int currentTime = 0;
	private int lastTime = 0;

	// the global average velocity that is used to calculate the global position
	private Point2D.Float globalAverageVelocityPPT = new Point2D.Float(0, 0); // [px/tick]
	private Point2D.Float globalAverageVelocityPPS = new Point2D.Float(0, 0); // [px/s]
	private Point2D.Float globalPosition = new Point2D.Float(0, 0); // [px]

	// 128x128 Matrix with all timestamps
	private int[][] timestamps = new int[128][128];

	// Lists for every direction, used to find the median of the Velocities
	private ArrayList<VTelement> vxPos = new ArrayList<>(); // v[px/tick] t[tick]
	private ArrayList<VTelement> vyPos = new ArrayList<>(); // v[px/tick] t[tick]
	private ArrayList<VTelement> vxNeg = new ArrayList<>(); // v[px/tick] t[tick]
	private ArrayList<VTelement> vyNeg = new ArrayList<>(); // v[px/tick] t[tick]

	// median value for every direction
	private VTelement vxPosMedian;
	private VTelement vyPosMedian;
	private VTelement vxNegMedian;
	private VTelement vyNegMedian;

	// constructor of the filter class
	public MicroscopeTracker(AEChip chip) {
		super(chip);
		initFilter();
		String group1 = "functions";
		String group2 = "logging";
		String group3 = "parameters";
		setPropertyTooltip(group1, "followGlobalMedianVelocity1", "follow the global median velocity vector (currentTime - lastTime)");
		setPropertyTooltip(group1, "followGlobalMedianVelocity2", "follow the global median velocity vector (time - told)");
		setPropertyTooltip(group1, "sendDataToLabview",	"sends estimated position and velocity to LabView via TCP link on \"127.0.0.1\", port 23");
		setPropertyTooltip(group2, "logData", "log data to a text file");
		setPropertyTooltip(group3, "thresholdTime", "time after which a timestamp gets ignored");
		setPropertyTooltip(group3, "percentage", "percentage value");
		setPropertyTooltip(group3, "minNumberOfEvents", "the minimum number of events to calculate the new position");
	}

	@Override
	public void initFilter() {
		initDefaults();
	}

	// reset all global variables
	// TODO refresh GUI
	// turning off all features doesn't really work unless the GUI is refreshed, because the displayed values would not update
	@Override
	public void resetFilter() {
		// followGlobalMedianVelocity1 = false;
		// followGlobalMedianVelocity2 = false;
		// sendDataToLabview = false;
		// logDataEnabled = false;
		// thresholdTime = 100;
		// percentage = 0.1f;
		// minNumberOfEvents = 10;

		// refresh GUI now

		resetValues();
	}

	// method is called when inputs are available and processes them.
	@Override
	synchronized public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {

		lastTime = currentTime;

		// update timestamps and currentTime
		updateTimestamps(in);

		// only used in the first iteration to make sure that dt doesn't get to big
		if (lastTime == 0) {
			lastTime = currentTime;
		}

		// sort out all outdated vectors
		deleteOldElements(vxPos);
		deleteOldElements(vyPos);
		deleteOldElements(vxNeg);
		deleteOldElements(vyNeg);

		// update velocities and sort in all new elements into the ArrayLists.
		// this is done after updating all timestamps, to make sure we only use up to date timestamps for all pixels.
		updateAndSortVelocities(in);

		// find median element for each direction
		vxPosMedian = findMedian(vxPos);
		vyPosMedian = findMedian(vyPos);
		vxNegMedian = findMedian(vxNeg);
		vyNegMedian = findMedian(vyNeg);

		// calculate Velocity and Position
		updateVelocityAndPosition();

		// send position vector to LabView if requested
		if (sendDataToLabview) {
			client.sendVector(Float.toString(globalPosition.x), Float.toString(globalPosition.y));
		}

		// log the values to a text file if requested
		if (logData) {
			/*
			String str = "";
			for (int i = 0; i < vxPos.size(); i++) {
				str = str + Float.toString(vxPos.get(i).getVelocity()) + " ";
			}
			txtLog.info(str);
			*/
		}

		return in;
	}

	// this function has to be overridden to also draw the average speed vector when we activate it
	@Override
	public synchronized void annotate(GLAutoDrawable drawable) {
		// vector and position are always displayed in blue
		GL2 gl = drawable.getGL().getGL2();
		gl.glColor3f(0, 0, 1);
		gl.glPointSize(5f);
		gl.glBegin(GL.GL_POINTS);
		gl.glVertex2d(64 + globalPosition.x, 64 + globalPosition.y);
		gl.glEnd();
		DrawGL.drawVector(gl, 64, 64, globalAverageVelocityPPS.x, globalAverageVelocityPPS.y);
	}

	// reset all variables and Lists
	private void resetValues() {
		globalPosition.x = 0;
		globalPosition.y = 0;

		globalAverageVelocityPPT.x = 0;
		globalAverageVelocityPPT.y = 0;
		globalAverageVelocityPPS.x = 0;
		globalAverageVelocityPPS.y = 0;

		currentTime = 0;
		lastTime = 0;

		timestamps = new int[128][128];

		vxPos.clear();
		vyPos.clear();
		vxNeg.clear();
		vyNeg.clear();
	}

	private void initDefaults() {
		initDefault("followGlobalMedianVelocity1", "false");
		initDefault("followGlobalMedianVelocity2", "false");
		initDefault("sendDataToLabview", "false");
		initDefault("logData", "false");
		initDefault("thresholdTime", "10");
		initDefault("percentage", "0.1");
		initDefault("minNumberOfEvents", "10");
	}

	private void initDefault(String key, String value) {
		if (getPrefs().get(key, null) == null) {
			getPrefs().put(key, value);
		}
	}

	// function to remove all VTelements that are to old
	private void deleteOldElements(ArrayList<VTelement> list) {
		for (int i = 0; i < list.size(); i++) {
			if (currentTime > (list.get(i).getTimeNew() + thresholdTime)) {
				list.remove(i);
				i--; // when an item gets removed, the next item doesn't get checked unless we decrement the index
			}
		}
	}

	// function to insert a new VTelement with binary sort
	private void binaryInsert(ArrayList<VTelement> list, VTelement e, int index1, int index2) {
		if ((index2 - index1) == 0) {
			// log.info("case 1");
			list.add(index1, e);
		}
		else if (e.getVelocity() > list.get(index1 + ((index2 - index1) / 2)).getVelocity()) {
			// log.info("case 2");
			binaryInsert(list, e, index1 + ((index2 - index1) / 2) + 1, index2);
		}
		else {
			// log.info("case 3");
			binaryInsert(list, e, index1, index1 + ((index2 - index1) / 2));
		}
	}

	private void updateTimestamps(EventPacket<? extends BasicEvent> in) {
		int x; // horizontal coordinate, by convention starts at left of image
		int y; // vertical coordinate, by convention starts at bottom of image
		int t;

		for (int i = 0; i < in.size; i++) {
			t = in.getEvent(i).timestamp;
			x = in.getEvent(i).x;
			y = in.getEvent(i).y;

			timestamps[x][y] = t; // log.info(Integer.toString(t));

			// update the current Time
			// TODO maybe not the best way to do it
			if (t > currentTime) {
				currentTime = t;
			}
		}
	}

	private void updateAndSortVelocities(EventPacket<? extends BasicEvent> in) {
		int x;
		int y;
		int dt;
		int timeNew;
		int timeOld;
		float v;

		for (int i = 0; i < in.size; i++) {
			x = in.getEvent(i).x;
			y = in.getEvent(i).y;

			// to avoid IndexOutOfBounds, the velocity is not calculated for pixels at the edge
			if ((x > 0) && (y > 0) && (x < 127) && (y < 127)) {

				timeNew = in.getEvent(i).timestamp;

				timeOld = timestamps[x - 1][y];
				dt = timeNew - timeOld;
				if (dt > 0) {
					v = (float) 1 / dt;
					VTelement exPos = new VTelement(v, timeNew, timeOld);
					binaryInsert(vxPos, exPos, 0, vxPos.size());
				}

				timeOld = timestamps[x][y - 1];
				dt = timeNew - timeOld;
				if (dt > 0) {
					v = (float) 1 / dt;
					VTelement eyPos = new VTelement(v, timeNew, timeOld);
					binaryInsert(vyPos, eyPos, 0, vyPos.size());
				}

				timeOld = timestamps[x + 1][y];
				dt = timeNew - timeOld;
				if (dt > 0) {
					v = (float) 1 / dt;
					VTelement exNeg = new VTelement(v, timeNew, timeOld);
					binaryInsert(vxNeg, exNeg, 0, vxNeg.size());
				}

				timeOld = timestamps[x][y + 1];
				dt = timeNew - timeOld;
				if (dt > 0) {
					v = (float) 1 / dt;
					VTelement eyNeg = new VTelement(v, timeNew, timeOld);
					binaryInsert(vyNeg, eyNeg, 0, vyNeg.size());
				}
			}
		}
	}

	private void updateVelocityAndPosition() {
		if (followGlobalMedianVelocity1) {
			int dt = currentTime - lastTime;

			if (vxPosMedian.getVelocity() > ((1 + percentage) * vxNegMedian.getVelocity())) {
				// x direction is positive
				globalAverageVelocityPPT.x = vxPosMedian.getVelocity();
				globalAverageVelocityPPS.x = globalAverageVelocityPPT.x * VELPPS_SCALING;
				globalPosition.x += (dt) * globalAverageVelocityPPT.x;
			}
			else if (vxNegMedian.getVelocity() > ((1 + percentage) * vxPosMedian.getVelocity())) {
				// x direction is negative
				globalAverageVelocityPPT.x = -vxNegMedian.getVelocity();
				globalAverageVelocityPPS.x = globalAverageVelocityPPT.x * VELPPS_SCALING;
				globalPosition.x += (dt) * globalAverageVelocityPPT.x;
			}
			else {
				// x velocity is assumed to be 0
				globalAverageVelocityPPT.x = 0;
				globalAverageVelocityPPS.x = 0;
				// globalPosition.x doesn't need to be updated
			}

			if (vyPosMedian.getVelocity() > ((1 + percentage) * vyNegMedian.getVelocity())) {
				// y direction is positive
				globalAverageVelocityPPT.y = vyPosMedian.getVelocity();
				globalAverageVelocityPPS.y = globalAverageVelocityPPT.y * VELPPS_SCALING;
				globalPosition.y += (dt) * globalAverageVelocityPPT.y;
			}
			else if (vyNegMedian.getVelocity() > ((1 + percentage) * vyPosMedian.getVelocity())) {
				// y direction is negative
				globalAverageVelocityPPT.y = -vyNegMedian.getVelocity();
				globalAverageVelocityPPS.y = globalAverageVelocityPPT.y * VELPPS_SCALING;
				globalPosition.y += (dt) * globalAverageVelocityPPT.y;
			}
			else {
				// y velocity is assumed to be 0
				globalAverageVelocityPPT.y = 0;
				globalAverageVelocityPPS.y = 0;
				// globalPosition.x doesn't need to be updated
			}

		}
		else if (followGlobalMedianVelocity2) {
			if (vxPosMedian.getVelocity() > ((1 + percentage) * vxNegMedian.getVelocity())) {
				// x direction is positive
				globalAverageVelocityPPT.x = vxPosMedian.getVelocity();
				globalAverageVelocityPPS.x = globalAverageVelocityPPT.x * VELPPS_SCALING;
				globalPosition.x += (vxPosMedian.getTimeNew() - vxPosMedian.getTimeOld()) * globalAverageVelocityPPT.x;
			}
			else if (vxNegMedian.getVelocity() > ((1 + percentage) * vxPosMedian.getVelocity())) {
				// x direction is negative
				globalAverageVelocityPPT.x = -vxNegMedian.getVelocity();
				globalAverageVelocityPPS.x = globalAverageVelocityPPT.x * VELPPS_SCALING;
				globalPosition.x += (vyPosMedian.getTimeNew() - vyPosMedian.getTimeOld()) * globalAverageVelocityPPT.x;
			}
			else {
				// x velocity is assumed to be 0
				globalAverageVelocityPPT.x = 0;
				globalAverageVelocityPPS.x = 0;
				// globalPosition.x doesn't need to be updated
			}

			if (vyPosMedian.getVelocity() > ((1 + percentage) * vyNegMedian.getVelocity())) {
				// y direction is positive
				globalAverageVelocityPPT.y = vyPosMedian.getVelocity();
				globalAverageVelocityPPS.y = globalAverageVelocityPPT.y * VELPPS_SCALING;
				globalPosition.y += (vxNegMedian.getTimeNew() - vxNegMedian.getTimeOld()) * globalAverageVelocityPPT.y;
			}
			else if (vyNegMedian.getVelocity() > ((1 + percentage) * vyPosMedian.getVelocity())) {
				// y direction is negative
				globalAverageVelocityPPT.y = -vyNegMedian.getVelocity();
				globalAverageVelocityPPS.y = globalAverageVelocityPPT.y * VELPPS_SCALING;
				globalPosition.y += (vyNegMedian.getTimeNew() - vyNegMedian.getTimeOld()) * globalAverageVelocityPPT.y;
			}
			else {
				// y velocity is assumed to be 0
				globalAverageVelocityPPT.y = 0;
				globalAverageVelocityPPS.y = 0;
				// globalPosition.x doesn't need to be updated
			}
		}
		else {
			// velocity is zero and position remains unchanged
			globalAverageVelocityPPT.x = 0;
			globalAverageVelocityPPT.y = 0;
			globalAverageVelocityPPS.x = 0;
			globalAverageVelocityPPS.y = 0;
		}
	}

	// function to find median
	private VTelement findMedian(ArrayList<VTelement> list) {
		int size;
		int index;

		size = list.size();
		if (size < minNumberOfEvents) {
			// too few elements in list
			return new VTelement(0, 0, 0);
		}
		else if ((size % 2) == 0) {
			// even number of elements in list -> median is the mean of the two middle elements
			index = size / 2;
			float v = (list.get(index-1).getVelocity() + list.get(index).getVelocity()) / 2;
			int timeNew = (list.get(index-1).getTimeNew() + list.get(index ).getTimeNew()) / 2;
			int timeOld = (list.get(index-1).getTimeOld() + list.get(index).getTimeOld()) / 2;
			return new VTelement(v, timeNew, timeOld);
		}
		else {
			// uneven number of elements in list -> median is the middle element
			index = (size - 1) / 2;
			return vxPos.get(index / 2);
		}
	}

	// check if global Median vector should be calculated by using currentTime - lastTime
	public boolean getFollowGlobalMedianVelocity1() {
		return followGlobalMedianVelocity1;
	}

	// set if global Median vector should be calculated by using currentTime - lastTime
	public void setFollowGlobalMedianVelocity1(boolean v) {
		if (v == true) {
			log.info("using currentTime - lastTime");
			followGlobalMedianVelocity1 = true;
		}
		else {
			log.info("not using currentTime - lastTime");
			followGlobalMedianVelocity1 = false;
		}
	}

	// check if global Median vector should be calculated by using time - told
	public boolean getFollowGlobalMedianVelocity2() {
		return followGlobalMedianVelocity2;
	}

	// set if global Median vector should be calculated by using time - told
	public void setFollowGlobalMedianVelocity2(boolean v) {
		if (v == true) {
			log.info("using time - told");
			followGlobalMedianVelocity2 = true;
		}
		else {
			log.info("not using time - told");
			followGlobalMedianVelocity2 = false;
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

	// check threshold time
	public int getThresholdTime() {
		return thresholdTime;
	}

	// set threshold time
	public void setThresholdTime(int t) {
		thresholdTime = t;
	}

	// check threshold time
	public int getMinNumberOfEvents() {
		return minNumberOfEvents;
	}

	// set threshold time
	public void setMinNumberOfEvents(int n) {
		minNumberOfEvents = n;
	}

	// check percentage
	public float getPercentage() {
		return percentage;
	}

	// set percentage
	public void setPercentage(float p) {
		percentage = p;
	}

	// check if data should be sent to LabView
	public boolean getSendDataToLabview() {
		return sendDataToLabview;
	}

	// set if vector should be sent to LabView
	public void setSendDataToLabview(boolean v) {
		resetValues();

		if (v == true) {
			// try to open connection to 127.0.0.1, port 23
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
			// try to close connection
			if (client.closeClient() == true) {
				log.info("client closed");
			}
			else {
				log.info("client was already closed");
			}

			log.info("not sending data");
			sendDataToLabview = false;
		}
	}
}
