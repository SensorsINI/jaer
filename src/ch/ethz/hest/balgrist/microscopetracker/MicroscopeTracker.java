package ch.ethz.hest.balgrist.microscopetracker;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.FileHandler;
import java.util.logging.Logger;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.DrawGL;

/**
 * calculates the xy-movement of the sample under the microscope and uses TCP to send the vector to LabView
 *
 * @author Niklaus Amrein
 */
@Description("calculates the xy-movement of the sample under the microscope and uses TCP to send the vector to LabView")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class MicroscopeTracker extends EventFilter2D implements FrameAnnotater {

	// new functions and parameters (by default they are deactivated when the program is started)
	private boolean showGlobalAverageVelocity = false;
	private boolean sendDataToLabview = false;
	private boolean logDataEnabled = false;
	private int thresholdTime = 10;
	private float percentage = (float) 0.1;

	// construct a logger to record the data
	Logger txtLog;
    FileHandler fh;

	// construct a client for the TCP communication with LabView
	MicroscopeTrackerTCPclient Client = new MicroscopeTrackerTCPclient();

	// value for the current time and the last time the timer was updated
	// TODO	Currently this time is just the most recent timestamp, maybe there is a better way to implement this.
	//		The difference currentTime - lastTime is not necessarily the correct time difference to calculate the position. See further below for details
	//		Also if recorded data is rewinded, the currentTime is stuck at the highest value from the end of the recording, this could be avoided if we use some kind of "system time" of the simulation
	private int currentTime = 0;
	private int lastUpdateTime = 0;

	// the global average velocity that is used to calculate the global position
	private Point2D.Float globalAverageVelocityPPT = new Point2D.Float(0, 0);	// [px/tick]
	private Point2D.Float globalPosition = new Point2D.Float(0, 0);	// [px]

	// 128x128 Matrix with all timestamps
	private int[][] timestamps = new int[128][128];

	// Lists for every direction, used to find the median of the Velocities
	private ArrayList<VTelement> vx_pos = new ArrayList<>();	// v[px/tick] t[tick]
	private ArrayList<VTelement> vy_pos = new ArrayList<>();	// v[px/tick] t[tick]
	private ArrayList<VTelement> vx_neg = new ArrayList<>();	// v[px/tick] t[tick]
	private ArrayList<VTelement> vy_neg = new ArrayList<>();	// v[px/tick] t[tick]

	// constructor of the filter class
	public MicroscopeTracker(AEChip chip) {
		super(chip);
		initFilter();
		String mt = "MicroscropeTracker";
		setPropertyTooltip(mt, "showGlobalAverageVelocity", "draws the estimated velocity vector");
		setPropertyTooltip(mt, "sendDataToLabview", "sends estimated position and velocity to LabView via TCP link on \"127.0.0.1\", port 23");
		setPropertyTooltip(mt, "LogDataEnabled", "log the full list of vectors to a text file");
		setPropertyTooltip(mt, "thresholdTime", "time after which a timestamp gets ignored");
		setPropertyTooltip(mt, "percentage", "percentage value");
	}

	@Override
	public void initFilter() {
		initDefaults();
	}

	// reset all global variables
	// (turning off all features doesn't really work because boxes in GUI remain unchanged)
	@Override
	public void resetFilter() {
		globalPosition.x = 0;
		globalPosition.y = 0;

		globalAverageVelocityPPT.x = 0;
		globalAverageVelocityPPT.y = 0;

		// setshowGlobalAverageVelocity(false);
		// setsendDataToLabview(false);
		// setthresholdTime(100);

		currentTime = 0;
		lastUpdateTime = 0;

		timestamps = new int[128][128];

		vx_pos = new ArrayList<>();
		vy_pos = new ArrayList<>();
		vx_neg = new ArrayList<>();
		vy_neg = new ArrayList<>();
	}

	public void initDefaults() {
		initDefault("showGlobalAverageVelocity", "false");
		initDefault("sendDataToLabview", "false");
		initDefault("logDataEnabled", "false");
		initDefault("thresholdTime", "10");
		initDefault("percentage", "0.1");
	}

	private void initDefault(String key, String value) {
		if (getPrefs().get(key, null) == null) {
			getPrefs().put(key, value);
		}
	}

	// method is called when inputs are available and processes them.
	@Override
	synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {

		lastUpdateTime = currentTime;

		// update timestamps
		int x;	// horizontal coordinate, by convention starts at left of image
		int y;	// vertical coordinate, by convention starts at bottom of image
		int dt;
		int t1;

		for (int i = 0; i < in.size; i++) {
			t1 = in.getEvent(i).timestamp;
			x = in.getEvent(i).x;
			y = in.getEvent(i).y;

			timestamps[x][y] = t1;	// log.info(Integer.toString(t));

			// update the current Time
			// TODO maybe not the best way to do it
			if (t1 > currentTime) {
				currentTime = t1;
			}
		}

		// only used in the first iteration to make sure that dt doesn't get to big
		if (lastUpdateTime == 0) {
			lastUpdateTime = currentTime;
		}

		// sort out all out dated vectors
		deleteOldElements(vx_pos);	// log.info(Integer.toString(vx_pos.size()));
		deleteOldElements(vy_pos);	// log.info(Integer.toString(vy_pos.size()));
		deleteOldElements(vx_neg);	// log.info(Integer.toString(vx_neg.size()));
		deleteOldElements(vy_neg);	// log.info(Integer.toString(vy_neg.size()));

		// update velocities and sort in all new elements into the ArrayLists. this is done after updating all timestamps, to make sure we only use up to date timestamps for all pixels.
		int t2;
		float v2;

		for (int i = 0; i < in.size; i++) {
			t2 = in.getEvent(i).timestamp;
			x = in.getEvent(i).x;
			y = in.getEvent(i).y;

			// to avoid IndexOutOfBounds, the velocity is not calculated for pixels at the edge
			if ((x > 0) && (y > 0) && (x < 127) && (y < 127)) {
				dt = t2 - timestamps[x - 1][y];
				if (dt > 0) {
					v2 = (float) 1 / dt;
					VTelement e_x_pos = new VTelement(v2, t2);
					// log.info("dt = " + Integer.toString(dt) + ", v = " + Float.toString(v2));
					binaryInsert(vx_pos, e_x_pos, 0, vx_pos.size());
				}

				dt = t2 - timestamps[x][y - 1];
				if (dt > 0) {
					v2 = (float) 1 / dt;
					VTelement e_y_pos = new VTelement(v2, t2);
					// log.info("dt = " + Integer.toString(dt) + ", v = " + Float.toString(v2));
					binaryInsert(vy_pos, e_y_pos, 0, vy_pos.size());
				}

				dt = t2 - timestamps[x + 1][y];
				if (dt > 0) {
					v2 = (float) 1 / dt;
					VTelement e_x_neg = new VTelement(v2, t2);
					// log.info("dt = " + Integer.toString(dt) + ", v = " + Float.toString(v2));
					binaryInsert(vx_neg, e_x_neg, 0, vx_neg.size());
				}

				dt = t2 - timestamps[x][y + 1];
				if (dt > 0) {
					v2 = (float) 1 / dt;
					VTelement e_y_neg = new VTelement(v2, t2);
					// log.info("dt = " + Integer.toString(dt) + ", v = " + Float.toString(v2));
					binaryInsert(vy_neg, e_y_neg, 0, vy_neg.size());
				}
			}
		}

		// choose median value from velocity arrays
		VTelement vx_pos_median;
		VTelement vx_neg_median;
		VTelement vy_pos_median;
		VTelement vy_neg_median;

		int index;
		int t3;
		float v3;

		index = vx_pos.size() / 2;
		if (index == 0) {
			vx_pos_median = new VTelement(0,0);
		} else if ((index % 2) == 0) {
			v3 = vx_pos.get(index).velocity + vx_pos.get(index + 1).velocity;
			t3 = vx_pos.get(index).time + vx_pos.get(index + 1).time;
			vx_pos_median = new VTelement(v3, t3);
		} else {
			vx_pos_median = vx_pos.get(vx_pos.size() / 2);
		}

		index = vy_pos.size() / 2;
		if (index == 0) {
			vy_pos_median = new VTelement(0,0);
		} else if ((index % 2) == 0) {
			v3 = vy_pos.get(index).velocity + vy_pos.get(index + 1).velocity;
			t3 = vy_pos.get(index).time + vy_pos.get(index + 1).time;
			vy_pos_median = new VTelement(v3, t3);
		} else {
			vy_pos_median = vy_pos.get(vy_pos.size() / 2);
		}

		index = vx_neg.size() / 2;
		if (index == 0) {
			vx_neg_median = new VTelement(0,0);
		} else if ((index % 2) == 0) {
			v3 = vx_neg.get(index).velocity + vx_neg.get(index + 1).velocity;
			t3 = vx_neg.get(index).time + vx_neg.get(index + 1).time;
			vx_neg_median = new VTelement(v3, t3);
		} else {
			vx_neg_median = vx_neg.get(vx_neg.size() / 2);
		}

		index = vy_neg.size() / 2;
		if (index == 0) {
			vy_neg_median = new VTelement(0,0);
		} else if ((index % 2) == 0) {
			v3 = vy_neg.get(index).velocity + vy_neg.get(index + 1).velocity;
			t3 = vy_neg.get(index).time + vy_neg.get(index + 1).time;
			vy_neg_median = new VTelement(v3, t3);
		} else {
			vy_neg_median = vy_neg.get(vy_neg.size() / 2);
		}

		// calculate Velocity and Position
		// TODO	calculating the position is not using the correct time interval:
		//		The chosen median value is valid for the corresponding dt, but (currentTime - lastUpdateTime) is not the same.
		//		However, if we use the correct dt (could for example be saved in VTelements), the time intervals dt could overlap or have gaps
		//		-> not necessarily correct either

		//TODO the position tends to drift away
		// probably because the direction is chosen to be either positive or negative, but the medians are always != 0
		// if vx_pos and vx_neg are combined like [-vx_neg, vx_pos], the median is always ~0
		// Solution?
		if (vx_pos_median.velocity > ((1 + percentage) * vx_neg_median.velocity)) {
			// x direction is positive
			globalAverageVelocityPPT.x = vx_pos_median.velocity;
			globalPosition.x += (currentTime - lastUpdateTime) * globalAverageVelocityPPT.x;
		} else if (vx_neg_median.velocity > ((1 + percentage) * vx_pos_median.velocity)) {
			// x direction is negative
			globalAverageVelocityPPT.x = -vx_neg_median.velocity;
			globalPosition.x += (currentTime - lastUpdateTime) * globalAverageVelocityPPT.x;
		} else {
			// x velocity is assumed to be 0
			globalAverageVelocityPPT.x = 0;
			// globalPosition.x doesn't need to be updated
		}

		if (vy_pos_median.velocity > ((1 + percentage) * vy_neg_median.velocity)) {
			// y direction is positive
			globalAverageVelocityPPT.y = vy_pos_median.velocity;
			globalPosition.y += (currentTime - lastUpdateTime) * globalAverageVelocityPPT.y;
		} else if (vy_neg_median.velocity > ((1 + percentage) * vy_pos_median.velocity)) {
			// y direction is negative
			globalAverageVelocityPPT.y = -vy_neg_median.velocity;
			globalPosition.y += (currentTime - lastUpdateTime) * globalAverageVelocityPPT.y;
		} else {
			// y velocity is assumed to be 0
			globalAverageVelocityPPT.y = 0;
			// globalPosition.x doesn't need to be updated
		}

		// log the full list of vectors to a text file if requested
		if (logDataEnabled) {
			String str = "vx_pos\n";
			for (int i = 0; i < vx_pos.size(); i++) {
				str += Float.toString(vx_pos.get(i).velocity) + " " + Integer.toString(vx_pos.get(i).time) + " ";
			}
			str = str.substring(0, str.length()-1);

			str += "\n" + "vy_pos\n";
			for (int i = 0; i < vy_pos.size(); i++) {
				str += Float.toString(vy_pos.get(i).velocity) + " " + Integer.toString(vy_pos.get(i).time) + " ";
			}
			str = str.substring(0, str.length()-1);

			str += "\n" + "vx_neg\n";
			for (int i = 0; i < vx_neg.size(); i++) {
				str += Float.toString(vx_neg.get(i).velocity) + " " + Integer.toString(vx_neg.get(i).time) + " ";
			}
			str = str.substring(0, str.length()-1);

			str += "\n" + "vy_neg\n";
			for (int i = 0; i < vy_neg.size(); i++) {
				str += Float.toString(vy_neg.get(i).velocity) + " " + Integer.toString(vy_neg.get(i).time) + " ";
			}
			str = str.substring(0, str.length()-1);

			txtLog.info(str);
		}

		// send position vector to LabView if requested
		if (sendDataToLabview) {
			Client.sendVector(Float.toString(globalPosition.x), Float.toString(globalPosition.y));
			// Client.sendVector(Float.toString(globalAverageVelocityPPT.x), Float.toString(globalAverageVelocityPPT.y));
		}
		return in;
	}

	// this function has to be overridden to also draw the average speed vector when we activate it
	@Override
	public synchronized void annotate(GLAutoDrawable drawable) {
		if (showGlobalAverageVelocity) {
			GL2 gl = drawable.getGL().getGL2();
			gl.glColor3f(1, 1, 0);
			gl.glPointSize(5f);
			gl.glBegin(GL.GL_POINTS);
			gl.glVertex2d(64 + globalPosition.x, 64 + globalPosition.y);
			gl.glEnd();
			DrawGL.drawVector(gl, 64, 64, 100000*globalAverageVelocityPPT.x, 100000*globalAverageVelocityPPT.y); // vector is really small, factor 100000 only used to make it visible.
		}
	}

	// function to remove all VTelements that are to old
	// (possible problem: IndexOutOfBounds if size changes during loop execution, but this shouldn't happen since loop condition is updated every iteration)
	public void deleteOldElements(ArrayList<VTelement> list) {
		for (int i = 0; i < list.size(); i++) {
			if (currentTime > (list.get(i).time + thresholdTime)) {
				list.remove(i);
				i--; // when an item gets removed, the next item doesn't get checked unless we decrement the index
			}
		}
	}

	// function to insert a new VTelement with binary sort
	public void binaryInsert(ArrayList<VTelement> list, VTelement e, int index1, int index2) {
		if ((index2 - index1) == 0) {
			// log.info("case 1");
			list.add(index1, e);
		}
		else if (e.velocity > list.get(index1 + ((index2 - index1) / 2)).velocity) {
			// log.info("case 2");
			binaryInsert(list, e, index1 + ((index2 - index1) / 2) + 1, index2);
		}
		else {
			// log.info("case 3");
			binaryInsert(list, e, index1, index1 + ((index2 - index1) / 2));
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

	// check threshold time
		public boolean getlogDataEnabled() {
			return logDataEnabled;
		}

		// set threshold time
		public void setlogDataEnabled(boolean v) {
			if (v == true) {
				logDataEnabled = true;
				// create logger
				txtLog = Logger.getLogger("Niggi");

			    try {
			        // This block configures the logger with handler and formatter
			    	fh = new FileHandler("C:/Users/Niggi Amrein/Documents/Retina Camera/Logged Data/log files");
			        txtLog.addHandler(fh);
				    txtLog.setUseParentHandlers(false);

			    } catch (SecurityException e) {
			        e.printStackTrace();
			    } catch (IOException e) {
			        e.printStackTrace();
			    }

			    log.info("logging data enabled");
			} else {
				logDataEnabled = false;
				txtLog.removeHandler(fh);
				fh.close();
				log.info("logging data disabled");
			}
		}

	// check threshold time
	public int getthresholdTime() {
		return thresholdTime;
	}

	// set threshold time
	public void setthresholdTime(int t) {
		thresholdTime = t;
	}

	// check percentage
	public float getpercentage() {
		return percentage;
	}

	// set percentage
	public void setpercentage(float p) {
		percentage = p;
	}

	// check if data should be sent to LabView
	public boolean getsendDataToLabview() {
		return sendDataToLabview;
	}

	// set if vector should be sent to LabView
	public void setsendDataToLabview(boolean v) {
		// reset global position
		globalPosition.x = 0;
		globalPosition.y = 0;

		if (v == true) {
			// try to open connection to 127.0.0.1, port 23
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
			// try to close connection
			if (Client.closeClient() == true) {
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