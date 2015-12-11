/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.chip.cochlea;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.prefs.Preferences;

import javax.swing.BoxLayout;
import javax.swing.JPanel;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.util.chart.Axis;
import net.sf.jaer.util.chart.Category;
import net.sf.jaer.util.chart.Series;
import net.sf.jaer.util.chart.XYChart;
import ch.unizh.ini.jaer.chip.cochlea.CochleaAMS1cADCSamples.ChannelBuffer;
import ch.unizh.ini.jaer.chip.cochlea.CochleaAMS1cADCSamples.DataBuffer;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.awt.GLJPanel;

/**
 * Displays data from CochleaAMS1c using RollingCochleaGramDisplayMethod with additional rolling strip chart of ADC samples.
 * There are really two distinct display methods. One is the "strip chart" display of a large number of samples vs. time, for example from
 * the microphones or a particular BM section. The other is
 * the display of an "image" of the data from scanning out the scanner data from the chip, showing all the BM sections at once.
 * <p>
 * Depending on the data in CochleaAMS1cADCSamples the data is handled differently.
 * <p> In the strip chart case, the data is acquired from
 * the ADCSamples object, resetting it in the process, and passed to the Series plotting object.  In the meantime the USB thread is filling the other ADCSamples buffer.
 * <P>
 * In the image case, the data in the ADCSamples object is not reset by the acquisition. Rather, whatever data is in the ADCSamples object is passed to Series after the Series has been reset.
 * Thus the data shown represents the latest data available, which could be a mixture of different "frames".
 *
 * @author Tobi
 */
public class CochleaAMS1cRollingCochleagramADCDisplayMethod extends RollingCochleaGramDisplayMethod {

	private static final Preferences prefs = Preferences.userNodeForPackage(CochleaAMS1cRollingCochleagramADCDisplayMethod.class);
	//    private CochleaAMS1c chip = null;
	boolean registeredChart = false;
	private Series[] activitySeries;
	private Axis timeAxis;
	private Axis activityAxis;
	private Category[] activityCategories;
	private XYChart activityChart;
	/** Max number of ADC samples to display for each ADC channel */
	public static final int NUM_ACTIVITY_SAMPLES = CochleaAMS1cADCSamples.MAX_NUM_SAMPLES;
	/** Trace color */
	public static final Color[] colors = {Color.RED, Color.GREEN, Color.BLUE, Color.getHSBColor(60f/360, .5f, .8f)};
	GLJPanel activityPan; // holds ADC chart and controls
	private final int NCHAN = 4;
	private CochleaAMS1cRollingCochleagramADCDisplayMethodGainGUI[] gainGuis = new CochleaAMS1cRollingCochleagramADCDisplayMethodGainGUI[NCHAN];
	DisplayControl[] displayControl = new DisplayControl[NCHAN];
	int xmin = Integer.MAX_VALUE, xmax = Integer.MIN_VALUE;

	public CochleaAMS1cRollingCochleagramADCDisplayMethod(ChipCanvas canvas) { // TODO fix all DisplayMethods so that they can accept the AEChip object and not the canvas, or else fix here so that given canvas with null chip (as is the case during AEChip creation) we can still do a lazy instantiation of necesary objects
		super(canvas);

		for (int i = 0; i < NCHAN; i++) {
			displayControl[i] = new DisplayControl(i);
			gainGuis[i] = new CochleaAMS1cRollingCochleagramADCDisplayMethodGainGUI(this, displayControl[i]);
		}
	}

        private boolean printedNoAdcSamplesWarning=false;
        
	@Override
	public void display(GLAutoDrawable drawable) {
		drawable.getContext().makeCurrent();

		super.display(drawable); // display the events from cochlea channels as for normal rolling cochleagram display
		checkAndAddToViewer();
		if(!addedToViewer){
			log.warning("cannot display ADC samples because ADC viewer panel not added to AEViewer canvas yet");
			return;
		}

                if(!(getChipCanvas().getChip() instanceof CochleaAMS1c)){
                    if(!printedNoAdcSamplesWarning){
                        log.warning("not a CochleaAMS1c, so cannot currently ADC samples to display; supressing further warnings");
                        printedNoAdcSamplesWarning=true;
                    }
                    return;
                }
		CochleaAMS1cADCSamples adcSamples = ((CochleaAMS1c)getChipCanvas().getChip()).getAdcSamples();
		boolean scannerRunning = adcSamples.isHasScannerData();
		// branch here depending on whether scanner is running or we are display strip chart
		if (scannerRunning) {
			DataBuffer data = adcSamples.getCurrentWritingDataBuffer(); // plot data being collected now, without consuming it
			timeAxis.setMinimum(0);
			timeAxis.setMaximum(adcSamples.getScanLength());
			int chan = 0;
			for (CochleaAMS1cADCSamples.ChannelBuffer cb : data.channelBuffers) {
				if (isHidden(chan)) {
					activitySeries[chan].clear();
					chan++;
					continue;
				}

				int n = cb.size(); // must find size here since array contains junk outside the count
				int g = getGain(chan);
				int o = getOffset(chan);
				activitySeries[chan].clear();
				if (activitySeries[chan].getCapacity() != NUM_ACTIVITY_SAMPLES) {
					activitySeries[chan].setCapacity(NUM_ACTIVITY_SAMPLES);
				}

				for (int i = 0; i < n; i++) {
					ch.unizh.ini.jaer.chip.cochlea.CochleaAMS1cADCSamples.ADCSample s = cb.samples[i];
					activitySeries[chan].add(i, clip((s.data + o) * g));
				}
				chan++;
			}
		} else {// strip chart
			adcSamples.swapBuffers();  // only add new data
			DataBuffer data = adcSamples.getCurrentReadingDataBuffer(); // plot data being collected now, without consuming it

			timeAxis.setMinimum(startTime);
			int maxTime=adcSamples.getMaxTime();
			if(maxTime>(startTime+timeWidthUs)) {
				clearScreenEnabled=true;
				startTime=maxTime;
			} // reset strip chart

			timeAxis.setMaximum(startTime+timeWidthUs); // TODO this comes from AE data in rolling event strip chart, but if no events, not set properly
			int chan = 0;
			for (ChannelBuffer cb : data.channelBuffers) {
				if (isHidden(chan)) {
					activitySeries[chan].clear();
					chan++;
					continue;
				} // TODO does nothing now because clear() doesn't work I think. Maybe have fixed that.

				int n = cb.size(); // must find size here since array contains junk outside the count
				int g = getGain(chan);
				int o = getOffset(chan);

				if (activitySeries[chan].getCapacity() != NUM_ACTIVITY_SAMPLES) {
					activitySeries[chan].setCapacity(NUM_ACTIVITY_SAMPLES);
				}

				for (int i = 0; i < n; i++) {
					ch.unizh.ini.jaer.chip.cochlea.CochleaAMS1cADCSamples.ADCSample s = cb.samples[i];
					activitySeries[chan].add(s.time,  clip((s.data + o) * g));
					updateLimits(s.time);
				}
				chan++;
			}
		}
		resetLimits();

		activityAxis.setMinimum(0);
		activityAxis.setMaximum(CochleaAMS1cADCSamples.MAX_ADC_VALUE);
		try {
			activityChart.display();
		} catch (Exception e) {
			log.warning("while displaying activity chart caught " + e);
		}
		drawable.getGL().glFlush();
	}


	@Override
	protected void onDeregistration(){
		if(activityPan!=null){
			activityPan.setVisible(false);
		}
	}

	@Override
	protected void onRegistration() {
		if(activityPan!=null){
			activityPan.setVisible(true);
			return;
		}
		try {

			timeAxis = new Axis(0, 1000000);
			timeAxis.setTitle("time");
			timeAxis.setUnit("us");

			activityAxis = new Axis(0, CochleaAMS1cADCSamples.MAX_ADC_VALUE);
			activityAxis.setTitle("sample");
			activitySeries = new Series[CochleaAMS1cADCSamples.NUM_CHANNELS];
			activityCategories = new Category[CochleaAMS1cADCSamples.NUM_CHANNELS];
			for (int i = 0; i < CochleaAMS1cADCSamples.NUM_CHANNELS; i++) {
				activitySeries[i] = new Series(2, NUM_ACTIVITY_SAMPLES);
				activityCategories[i] = new Category(activitySeries[i], new Axis[]{timeAxis, activityAxis});
				activityCategories[i].setColor(colors[i].getRGBColorComponents(null));
			}
			//            activityAxis.setUnit("events");


			activityChart = new XYChart("ADC Samples");
			activityChart.setPreferredSize(new Dimension(600, 200));
			activityChart.setBackground(Color.black);
			activityChart.setForeground(Color.white);
			activityChart.setGridEnabled(false);

			for (int i = 0; i < CochleaAMS1cADCSamples.NUM_CHANNELS; i++) {
				activityChart.addCategory(activityCategories[i]);
			}
			activityPan = new GLJPanel(); // holds chart and controls
			activityPan.setLayout(new BorderLayout());
			activityPan.setBackground(Color.black);
			activityPan.add(activityChart, BorderLayout.CENTER);
			JPanel gainPan = new JPanel();
			gainPan.setLayout(new BoxLayout(gainPan, BoxLayout.X_AXIS));
			for (int i = 0; i < NCHAN; i++) {
				CochleaAMS1cRollingCochleagramADCDisplayMethodGainGUI gaingui = new CochleaAMS1cRollingCochleagramADCDisplayMethodGainGUI(this, displayControl[i]);
				gainPan.add(gaingui);
			}
			activityPan.add(gainPan, BorderLayout.SOUTH);
			activityPan.validate();
			checkAndAddToViewer();
			registeredChart = true;
		} catch (Exception e) {
			log.warning("could not register display panel: " + e);
		}
	}

	//    void unregisterChart() {
	//        try {
	//            AEChip chip = (AEChip) getChipCanvas().getChip();
	//            AEViewer viewer = chip.getAeViewer(); // must do lazy install here because viewer hasn't been registered with this chip at this point
	//            JPanel imagePanel = viewer.getImagePanel();
	//            imagePanel.remove(activityChart);
	//            registeredChart = false;
	//        } catch (Exception e) {
	//            log.warning("could not unregister control panel: " + e);
	//        }
	//    }

	//    private void drawRectangle(GL2 gl, float x, float y, float w, float h) {
	//        gl.glLineWidth(2f);
	//        gl.glColor3f(1, 1, 1);
	//        gl.glBegin(GL2.GL_LINE_LOOP);
	//        gl.glVertex2f(x, y);
	//        gl.glVertex2f(x + w, y);
	//        gl.glVertex2f(x + w, y + h);
	//        gl.glVertex2f(x, y + h);
	//        gl.glEnd();
	//    }

	public int getGain(int chan) {
		return displayControl[chan].getGain();
	}

	public void setGain(int chan, int gain) {
		displayControl[chan].setGain(gain);
	}

	public int getOffset(int chan) {
		return displayControl[chan].getOffset();
	}

	public void setOffset(int chan, int offset) {
		displayControl[chan].setOffset(offset);
	}

	public void setHidden(int chan, boolean yes) {
		displayControl[chan].setHidden(yes);
	}

	public boolean isHidden(int chan) {
		return displayControl[chan].isHidden();
	}

	private void updateLimits(int time) {
		if (time < xmin) {
			xmin = time;
		} else if (time > xmax) {
			xmax = time;
		}

	}

	private void resetLimits() {
		xmin = Integer.MAX_VALUE;
		xmax = Integer.MIN_VALUE;
	}

	private float clip(int i) {
		if(i<0) {
			return 0;
		}
		else if(i>CochleaAMS1cADCSamples.MAX_ADC_VALUE) {
			return CochleaAMS1cADCSamples.MAX_ADC_VALUE;
		}
		else {
			return i;
		}
	}

	private boolean addedToViewer=false;

	private void checkAndAddToViewer() {
		if(addedToViewer) {
			return;
		}
		AEChip chip = (AEChip) getChipCanvas().getChip();
		if(chip==null) {
			return;
		}
		AEViewer viewer = chip.getAeViewer(); // must do lazy install here because viewer hasn't been registered with this chip at this point
		if(viewer==null) {
			return;
		}
		JPanel imagePanel = viewer.getImagePanel();
		if(imagePanel==null) {
			return;
		}
		imagePanel.add(activityPan, BorderLayout.SOUTH);
		imagePanel.validate();
		addedToViewer=true;
	}

	public class DisplayControl {

		private int chan;
		private int gain;
		private int offset;
		private boolean hidden;
		private String name;

		public DisplayControl(int chan) {
			this.chan = chan;
			gain = prefs.getInt("CochleaAMS1cRollingCochleagramADCDisplayMethod.gain" + chan, 1);
			offset = prefs.getInt("CochleaAMS1cRollingCochleagramADCDisplayMethod.offset" + chan, 0);
			hidden = prefs.getBoolean("CochleaAMS1cRollingCochleagramADCDisplayMethod.hidden" + chan, false);
			name = prefs.get("CochleaAMS1cRollingCochleagramADCDisplayMethod.name" + chan, "Chan" + chan);
		}

		/**
		 * @return the gain
		 */
		public int getGain() {
			return gain;
		}

		/**
		 * @param gain the gain to set
		 */
		public void setGain(int gain) {
			this.gain = gain;
			prefs.putInt("CochleaAMS1cRollingCochleagramADCDisplayMethod.gain" + chan, gain);
		}

		/**
		 * @return the offset
		 */
		public int getOffset() {
			return offset;
		}

		/**
		 * @param offset the offset to set
		 */
		public void setOffset(int offset) {
			this.offset = offset;
			prefs.putInt("CochleaAMS1cRollingCochleagramADCDisplayMethod.offset" + chan, offset);
		}

		/**
		 * @return the hidden
		 */
		public boolean isHidden() {
			return hidden;
		}

		/**
		 * @param hidden the hidden to set
		 */
		public void setHidden(boolean hidden) {
			this.hidden = hidden;
			prefs.putBoolean("CochleaAMS1cRollingCochleagramADCDisplayMethod.hidden" + chan, hidden);
		}

		/**
		 * @return the name
		 */
		public String getName() {
			if(name==null) {
				return "";
			}
			return name;
		}

		/**
		 * @param name the name to set
		 */
		public void setName(String name) {
			this.name = name;
			prefs.put("CochleaAMS1cRollingCochleagramADCDisplayMethod.name" + chan, name);
		}

		/**
		 * @return the chan
		 */
		public int getChan() {
			return chan;
		}
	}
}
