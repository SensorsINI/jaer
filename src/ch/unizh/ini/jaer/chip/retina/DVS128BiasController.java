/*
 *
 * Created on January 9, 2006, 10:41 AM
 * Cloned from DVS128BiasController Feb 2011 by Tobi
 *
 */
package ch.unizh.ini.jaer.chip.retina;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;


import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.EventRateEstimator;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.EngineeringFormat;

import com.jogamp.opengl.util.gl2.GLUT;

/**
 * Controls the rate of events from the retina by controlling retina biases.
The event threshold is increased if rate exceeds rateHigh until rate drops below rateHigh.
The threshold is decreased if rate is lower than rateLow.
Hysterisis limits crossing noise.
A lowpass filter smooths the rate measurements.
 *
 * @author tobi
 */
@Description("Adaptively controls biases on DVS128 to control event rate")
public class DVS128BiasController extends EventFilter2D implements FrameAnnotater {

	protected int rateHigh = getInt("rateHigh", 400);

	//    private int rateMid=getInt("rateMid",300);
	private int rateLow = getInt("rateLow", 100);
	private int rateHysteresis = getInt("rateHysteresis", 50);
	private float hysteresisFactor = getFloat("hysteresisFactor", 1.3f);
	private int minCommandIntervalMs = getInt("minCommandIntervalMs",300);
	private long lastCommandTime = 0; // limits use of status messages that control biases
	private float tweakStepAmount = getFloat("tweakStepAmount", .001f);
	private EventRateEstimator rateEstimator;

	enum State {

		INITIAL, LOW, MEDIUM, HIGH;
		private long timeChanged = 0;

		long getTimeChanged() {
			return timeChanged;
		}

		void setTimeChanged(long t) {
			timeChanged = t;
		}
	};
	State state = State.INITIAL, lastState = State.INITIAL;
	Writer logWriter;
	private boolean writeLogEnabled = false;
	long timeNowMs = 0;

	/**
	 * Creates a new instance of DVS128BiasController
	 */
	 public DVS128BiasController(AEChip chip) {
		 super(chip);
		 if (!(chip instanceof DVS128)) {
			 log.warning(chip + " is not of type DVS128");
		 }
		 rateEstimator=new EventRateEstimator(chip);
		 FilterChain chain=new FilterChain(chip);
		 chain.add(rateEstimator);
		 setEnclosedFilterChain(chain);

		 setPropertyTooltip("rateLow", "event rate in keps for LOW state");
		 setPropertyTooltip("rateHigh", "event rate in keps for HIGH state");
		 setPropertyTooltip("rateHysteresis", "hysteresis for state change; after state entry, state exited only when avg rate changes by this factor from threshold");
		 setPropertyTooltip("hysteresisFactor", "hysteresis for state change; after state entry, state exited only when avg rate changes by this factor from threshold");
		 setPropertyTooltip("tweakStepAmount", "amount to tweak bias by each step");
		 setPropertyTooltip("minCommandIntervalMs", "min time in ms between changing biases");
	 }

	 public Object getFilterState() {
		 return null;
	 }

	 @Override
	 synchronized public void resetFilter() {
		 if (chip.getHardwareInterface() == null) {
			 return;  // avoid sending hardware commands unless the hardware is there and we are active
		 }
		 if (chip.getBiasgen() == null) {
			 setFilterEnabled(false);
			 log.warning("null biasgen object to operate on, disabled filter");
			 return;
		 }
		 if (!(chip.getBiasgen() instanceof DVS128.Biasgen)) {
			 setFilterEnabled(false);
			 log.warning("Wrong type of biasgen object; should be DVS128.Biasgen but object is " + chip.getBiasgen() + "; disabled filter");
			 return;
		 }
		 DVS128.Biasgen biasgen = (DVS128.Biasgen) getChip().getBiasgen();
		 if (biasgen == null) {
			 //            log.warning("null biasgen, not doing anything");
			 return;
		 }
		 biasgen.loadPreferences();
		 state = State.INITIAL;
	 }

	 public int getRateHigh() {
		 return rateHigh;
	 }

	 synchronized public void setRateHigh(int upperThreshKEPS) {
		 rateHigh = upperThreshKEPS;
		 putInt("rateHigh", upperThreshKEPS);
	 }

	 public int getRateLow() {
		 return rateLow;
	 }

	 synchronized public void setRateLow(int lowerThreshKEPS) {
		 rateLow = lowerThreshKEPS;
		 putInt("rateLow", lowerThreshKEPS);
	 }

	 @Override
	 synchronized public EventPacket filterPacket(EventPacket in) {
		 // TODO reenable check for LIVE mode here
		 //        if (chip.getAeViewer().getPlayMode() != AEViewer.PlayMode.LIVE) {
		 //            return in;  // don't servo on recorded data!
		 //        }
		 getEnclosedFilterChain().filterPacket(in);

		 float r = rateEstimator.getFilteredEventRate() * 1e-3f;


		 setState(r);
		 setBiases();
		 if (writeLogEnabled) {
			 if (logWriter == null) {
				 logWriter = openLoggingOutputFile();
			 }
			 try {
				 logWriter.write(in.getLastTimestamp() + " " + r + " " + state.ordinal() + "\n");
			 } catch (Exception e) {
				 e.printStackTrace();
			 }
		 }
		 return in;
	 }

	 void setState(float r) {
		 lastState = state;
		 switch (state) {
			 case LOW:
				 if (r > (rateLow * hysteresisFactor)) {
					 state = State.MEDIUM;
				 }
				 break;
			 case MEDIUM:
				 if (r < (rateLow / hysteresisFactor)) {
					 state = State.LOW;
				 } else if (r > (rateHigh * hysteresisFactor)) {
					 state = State.HIGH;
				 }
				 break;
			 case HIGH:
				 if (r < (rateHigh / hysteresisFactor)) {
					 state = State.MEDIUM;
				 }
				 break;
			 default:
				 state = State.MEDIUM;
		 }
	 }

	 void setBiases() {
		 timeNowMs = System.currentTimeMillis();
		 long dt=timeNowMs - lastCommandTime;
		 if ((dt>0) && (dt < getMinCommandIntervalMs())) {
			 return; // don't saturate setup packet bandwidth and stall on blocking USB writes
		 }
		 lastCommandTime=timeNowMs;
		 DVS128.Biasgen biasgen = (DVS128.Biasgen) getChip().getBiasgen();
		 if (biasgen == null) {
			 log.warning("null biasgen, not doing anything");
			 return;
		 }
		 float bw = biasgen.getThresholdTweak();
		 switch (state) {
			 case LOW:
				 biasgen.setThresholdTweak(bw - getTweakStepAmount());
				 //                biasgen.decreaseThreshold();
				 break;
			 case HIGH:
				 biasgen.setThresholdTweak(bw + getTweakStepAmount());
				 //               biasgen.increaseThreshold();
				 break;
			 default:
		 }
		 System.out.println("bw="+bw);

	 }

	 @Override
	 public void initFilter() {
	 }

	 public float getHysteresisFactor() {
		 return hysteresisFactor;
	 }

	 synchronized public void setHysteresisFactor(float h) {
		 if (h < 1) {
			 h = 1;
		 } else if (h > 5) {
			 h = 5;
		 }
		 hysteresisFactor = h;
		 putFloat("hysteresisFactor", hysteresisFactor);
	 }

	 /**
	  * @return the tweakStepAmount
	  */
	 public float getTweakStepAmount() {
		 return tweakStepAmount;
	 }

	 /**
	  * @param tweakStepAmount the tweakStepAmount to set
	  */
	 public void setTweakStepAmount(float tweakStepAmount) {
		 this.tweakStepAmount = tweakStepAmount;
		 putFloat("tweakStepAmount", tweakStepAmount);
	 }


	 Writer openLoggingOutputFile() {
		 DateFormat loggingFilenameDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ssZ");
		 String dateString = loggingFilenameDateFormat.format(new Date());
		 String className = "DVS128BiasController";
		 int suffixNumber = 0;
		 boolean suceeded = false;
		 String filename;
		 Writer writer = null;
		 File loggingFile;
		 do {
			 filename = className + "-" + dateString + "-" + suffixNumber + ".txt";
			 loggingFile = new File(filename);
			 if (!loggingFile.isFile()) {
				 suceeded = true;
			 }
		 } while ((suceeded == false) && (suffixNumber++ <= 5));
		 if (suceeded == false) {
			 log.warning("could not open a unigue new file for logging after trying up to " + filename);
			 return null;
		 }
		 try {
			 writer = new FileWriter(loggingFile);
			 log.info("starting logging bias control at " + dateString);
			 writer.write("# time rate lpRate state\n");
			 writer.write(String.format("# rateLow=%f rateHigh=%f hysteresisFactor=%f\n", rateLow, rateHigh, hysteresisFactor));
		 } catch (Exception e) {
			 e.printStackTrace();
		 }
		 return writer;
	 }

	 EngineeringFormat fmt = new EngineeringFormat();

	 @Override
	 public void annotate(GLAutoDrawable drawable) {
		 if (!isFilterEnabled()) {
			 return;
		 }
		 GL2 gl = drawable.getGL().getGL2();
		 gl.glPushMatrix();
		 final GLUT glut = new GLUT();
		 gl.glColor3f(1, 1, 1); // must set color before raster position (raster position is like glVertex)
		 gl.glRasterPos3f(0, 0, 0);
		 glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, String.format("lpRate=%s, state=%s", fmt.format(rateEstimator.getFilteredEventRate()), state.toString()));
		 gl.glPopMatrix();
	 }

	 public boolean isWriteLogEnabled() {
		 return writeLogEnabled;
	 }

	 public void setWriteLogEnabled(boolean writeLogEnabled) {
		 this.writeLogEnabled = writeLogEnabled;
	 }

	 /**
	  * @return the minCommandIntervalMs
	  */
	 public int getMinCommandIntervalMs() {
		 return minCommandIntervalMs;
	 }

	 /**
	  * @param minCommandIntervalMs the minCommandIntervalMs to set
	  */
	 public void setMinCommandIntervalMs(int minCommandIntervalMs) {
		 this.minCommandIntervalMs = minCommandIntervalMs;
		 putInt("minCommandIntervalMs",minCommandIntervalMs);
	 }
}
