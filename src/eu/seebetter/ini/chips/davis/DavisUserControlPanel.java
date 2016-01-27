/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.davis;

import java.awt.Color;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Logger;

import javax.swing.JSpinner;

import ch.unizh.ini.jaer.chip.retina.DVSTweaks;
import eu.seebetter.ini.chips.DavisChip;
import net.sf.jaer.biasgen.PotTweaker;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.graphics.AEChipRenderer;
import net.sf.jaer.graphics.AEFrameChipRenderer;

/**
 * Wraps some key apsDVS sensor control in more user-friendly control panel.
 *
 * @author tobi
 */
public class DavisUserControlPanel extends javax.swing.JPanel implements PropertyChangeListener, Observer {

	private static final long serialVersionUID = 3894988983329773402L;

	protected DavisChip chip = null;
	protected DavisTweaks apsDvsTweaks;
	DavisConfig.VideoControl videoControl;
	DavisVideoContrastController contrastController;
	AEChipRenderer renderer = null;

	private static final Logger log = Logger.getLogger("DVSFunctionalControlPanel");

	/**
	 * Creates new form ApsDVSUserControlPanel
	 */
	public DavisUserControlPanel(final Chip chip) {
		this.chip = (DavisChip) chip; // will throw ClassCastException if not right kind of chip.
		renderer = this.chip.getRenderer();
		apsDvsTweaks = (DavisTweaks) chip.getBiasgen();
		initComponents();
		// code must be after initComponents so that these components exist
		final PotTweaker[] tweakers = { thresholdTweaker, onOffBalanceTweaker, maxFiringRateTweaker, bandwidthTweaker };
		for (final PotTweaker tweaker : tweakers) {
			chip.getBiasgen().getSupport().addPropertyChangeListener(tweaker); // to reset sliders on load/save of
		}

		histCB.setSelected(this.chip.isShowImageHistogram());
		fdSp.setValue(getConfig().getFrameDelayMs());
		edSp.setValue(getConfig().getExposureDelayMs());
		glShutterCB.setSelected(((DavisBaseCamera) chip).getDavisConfig().isGlobalShutter());
		displayEventsCheckBox.setSelected(getConfig().isDisplayEvents());
		displayFramesCheckBox.setSelected(getConfig().isDisplayFrames());
		captureFramesCheckBox.setSelected(getConfig().isCaptureFramesEnabled());
		captureEventsCB.setSelected(getConfig().isCaptureEventsEnabled());
		autoshotThresholdSp.setValue(this.chip.getAutoshotThresholdEvents() >> 10);
		final int[] vals = { 10, 100, 1000 }, mults = { 1, 10, 100 };
		final float[] fvals = { .02f, .2f, 2, 20, 200, 2000 }, fmults = { .001f, .01f, .1f, 1, 10, 100 };
		autoshotThresholdSp.addMouseWheelListener(new SpinnerMouseWheelIntHandler(vals, mults));
		autoExpCB.setSelected(this.chip.isAutoExposureEnabled());
		fdSp.addMouseWheelListener(new SpinnerMouseWheelFloatHandler(fvals, fmults));
		edSp.addMouseWheelListener(new SpinnerMouseWheelFloatHandler(fvals, fmults));
		dvsContSp.addMouseWheelListener(new SpinnerMouseWheelIntHandler(new int[] { 10, 20 }, new int[] { 1, 2 }));
		dvsContSp.setValue(renderer.getColorScale());
		contrastSp.addMouseWheelListener(new SpinnerMouseWheelFloatHandler(new float[] { 1, 2 }, new float[] { .05f, .1f }));
		setDvsColorModeRadioButtons();
		renderer.getSupport().addPropertyChangeListener(this);
		if (getConfig() instanceof DavisConfig) {
			// add us as observer for various property changes
			final DavisConfig config = getConfig();
			videoControl = config.getVideoControl();
			videoControl.addObserver(this); // display of various events/frames enabled comes here
			// config.exposureControlRegister.addObserver(this);
			// config.frameDelayControlRegister.addObserver(this); // TODO generates loop that resets the spinner if the
			// new spinner
			// value does not change the exposureControlRegister in ms according to new register value
			imuVisibleCB.setSelected(config.isDisplayImu());
			imuEnabledCB.setSelected(config.isImuEnabled());
		}
		contrastController = ((AEFrameChipRenderer) ((AEChip) chip).getRenderer()).getContrastController();
		contrastController.addObserver(this); // get updates from contrast controller
		contrastSp.setValue(contrastController.getContrast());
		contrastSp.setEnabled(!contrastController.isUseAutoContrast());
		autoContrastCB.setSelected(contrastController.isUseAutoContrast());
		this.chip.addObserver(this);
		chip.getSupport().addPropertyChangeListener(this);
		chip.getBiasgen().getSupport().addPropertyChangeListener(this);

	}

	private void setDvsColorModeRadioButtons() {
		switch (renderer.getColorMode()) {
			case RedGreen:
				dvsRedGrRB.setSelected(true);
				break;
			case GrayLevel:
				dvsGrayRB.setSelected(true);
				break;
			default:
				dvsColorButGrp.clearSelection();
		}
	}

	private DavisConfig getConfig() {
		return ((DavisBaseCamera) chip).getDavisConfig();
	}

	private class SpinnerMouseWheelIntHandler implements MouseWheelListener {

		private final int[] vals, mults;

		/**
		 * Constructs a new instance
		 *
		 * @param vals
		 *            the threshold values
		 * @param mults
		 *            the multipliers for spinner values less than or equal to
		 *            that number
		 */
		SpinnerMouseWheelIntHandler(final int[] vals, final int[] mults) {
			if ((vals == null) || (mults == null)) {
				throw new RuntimeException("vals or mults is null");
			}
			if (vals.length != mults.length) {
				throw new RuntimeException("vals and mults array must be same length and they are not: vals.length=" + vals.length
					+ " mults.length=" + mults.length);
			}
			this.vals = vals;
			this.mults = mults;
		}

		@Override
		public void mouseWheelMoved(final MouseWheelEvent mwe) {
			final JSpinner spinner = (JSpinner) mwe.getSource();
			if (mwe.getScrollType() != MouseWheelEvent.WHEEL_UNIT_SCROLL) {
				return;
			}
			try {
				int value = (Integer) spinner.getValue();

				int i = 0;
				for (i = 0; i < vals.length; i++) {
					if (value < vals[i]) {
						break;
					}
				}
				if (i >= vals.length) {
					i = vals.length - 1;
				}
				final int mult = mults[i];
				value -= mult * mwe.getWheelRotation();
				if (value < 0) {
					value = 0;
				}
				spinner.setValue(value);
			}
			catch (final Exception e) {
				DavisUserControlPanel.log.warning(e.toString());
				return;
			}
		}
	}

	private class SpinnerMouseWheelFloatHandler implements MouseWheelListener {

		private final float[] vals, mults;

		/**
		 * Constructs a new instance
		 *
		 * @param vals
		 *            the threshold values
		 * @param mults
		 *            the multipliers for spinner values less than or equal to
		 *            that number
		 */
		SpinnerMouseWheelFloatHandler(final float[] vals, final float[] mults) {
			if ((vals == null) || (mults == null)) {
				throw new RuntimeException("vals or mults is null");
			}
			if (vals.length != mults.length) {
				throw new RuntimeException("vals and mults array must be same length and they are not: vals.length=" + vals.length
					+ " mults.length=" + mults.length);
			}
			this.vals = vals;
			this.mults = mults;
		}

		@Override
		public void mouseWheelMoved(final MouseWheelEvent mwe) {
			final JSpinner spinner = (JSpinner) mwe.getSource();
			if (mwe.getScrollType() != MouseWheelEvent.WHEEL_UNIT_SCROLL) {
				return;
			}
			try {
				float value = (Float) spinner.getValue();
				int i = 0;
				// int rot = mwe.getWheelRotation(); // >0 is roll down, want smaller value
				if (true) {
					for (i = 0; i < vals.length; i++) {
						if (value <= vals[i]) {
							break; // take value from vals array that is just above our current value, e.g. if our
									// current value is 11, then take 20 (if that is next higher vals) as next value and
									// mult of 20 value as decrement amount
						}
					}
					if (i > (vals.length - 1)) {
						i = vals.length - 1;
					}
				}
				// else { // roll up, want larger value
				// for (i = vals.length-1; i >=0 ; i--) {
				// if (value >= vals[i]) {
				// break; // now start at highest vals value and go down, until we find next lower or equal vals, e.g.
				// if we are at 11, then take 2 (if that is next lower value) and then choose that mult that goes with 2
				// to decr
				// }
				// }
				// if (i<0) {
				// i = 0;
				// }
				// }
				final float mult = mults[i];
				value -= mult * mwe.getWheelRotation();
				if (value < 0) {
					value = 0;
				}
				spinner.setValue(value);
			}
			catch (final Exception e) {
				DavisUserControlPanel.log.warning(e.toString());
			}
		}
	}

	private void setFileModified() {
		if ((getChip() != null) && (getChip().getAeViewer() != null) && (getChip().getAeViewer().getBiasgenFrame() != null)) {
			getChip().getAeViewer().getBiasgenFrame().setFileModified(true);
		}
	}

	/**
	 * Handles property changes from PotTweakers and measured
	 * exposureControlRegister and frame rate from chip
	 *
	 * @param evt
	 */
	@Override
	public void propertyChange(final PropertyChangeEvent evt) {
		final String name = evt.getPropertyName();
		try {
			switch (name) {
				case DavisVideoContrastController.AGC_VALUES: {
					contrastSp.setValue(contrastController.getAutoContrast());
				}
					break;
				case DVSTweaks.THRESHOLD: // fired from Davis240Config setBandwidthTweak
				{
					final float v = (Float) evt.getNewValue();
					thresholdTweaker.setValue(v);
				}
					break;
				case DVSTweaks.BANDWIDTH: // log.info(evt.toString());
				{
					final float v = (Float) evt.getNewValue();
					bandwidthTweaker.setValue(v);
				}
					break;
				case DVSTweaks.MAX_FIRING_RATE: {
					final float v = (Float) evt.getNewValue();
					maxFiringRateTweaker.setValue(v);
				}
					break;
				case DVSTweaks.ON_OFF_BALANCE: {
					final float v = (Float) evt.getNewValue();
					onOffBalanceTweaker.setValue(v);
				}
					break;
				case DavisChip.PROPERTY_MEASURED_EXPOSURE_MS: {
					exposMsTF.setText(String.format("%.3f", (Float) evt.getNewValue()));
				}
					break;
				case DavisChip.PROPERTY_FRAME_RATE_HZ: {
					fpsTF.setText(String.format("%.3f", (Float) evt.getNewValue()));
				}
					break;
				case AEChipRenderer.EVENT_COLOR_MODE_CHANGE: {
					setDvsColorModeRadioButtons();
				}
					break;
				case AEChipRenderer.EVENT_COLOR_SCALE_CHANGE: {
					dvsContSp.setValue(renderer.getColorScale());
				}
					break;
				case DavisDisplayConfigInterface.PROPERTY_IMU_ENABLED: {
					imuEnabledCB.setSelected((boolean) evt.getNewValue());
				}
					break;
				case DavisDisplayConfigInterface.PROPERTY_IMU_DISPLAY_ENABLED: {
					imuVisibleCB.setSelected((boolean) evt.getNewValue());
				}
					break;
				case DavisDisplayConfigInterface.PROPERTY_DISPLAY_FRAMES_ENABLED: {
					displayFramesCheckBox.setSelected((boolean) evt.getNewValue());
				}
					break;
				case DavisDisplayConfigInterface.PROPERTY_CAPTURE_FRAMES_ENABLED: {
					captureFramesCheckBox.setSelected((boolean) evt.getNewValue());
				}
					break;
				case DavisDisplayConfigInterface.PROPERTY_CAPTURE_EVENTS_ENABLED: {
					captureEventsCB.setSelected((boolean) evt.getNewValue());
				}
					break;
				case DavisDisplayConfigInterface.PROPERTY_DISPLAY_EVENTS_ENABLED: {
					displayEventsCheckBox.setSelected((boolean) evt.getNewValue());
				}
					break;
				case DavisVideoContrastController.PROPERTY_CONTRAST: {
					contrastSp.setValue((float) evt.getNewValue());
				}
					break;
				case DavisDisplayConfigInterface.PROPERTY_EXPOSURE_DELAY_US: {
					edSp.setValue((Integer) evt.getNewValue() * .001f);
				}
					break;
				case DavisDisplayConfigInterface.PROPERTY_FRAME_DELAY_US: {
					fdSp.setValue((Integer) evt.getNewValue() * .001f);
				}
					break;
				case DavisDisplayConfigInterface.PROPERTY_GLOBAL_SHUTTER_MODE_ENABLED: {
					glShutterCB.setSelected((Boolean) evt.getNewValue());
				}
					break;
				case DavisChip.PROPERTY_AUTO_EXPOSURE_ENABLED: {
					autoExpCB.setSelected((Boolean) evt.getNewValue());
				}
					break;
			}
		}
		catch (final Exception e) {
			DavisUserControlPanel.log.warning("responding to property change, caught " + e.toString());
			e.printStackTrace();
		}
	}

	/**
	 * This method is called from within the constructor to initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is always
	 * regenerated by the Form Editor.
	 */
	// <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
	private void initComponents() {

		dvsColorButGrp = new javax.swing.ButtonGroup();
		jPanel1 = new javax.swing.JPanel();
		jScrollPane1 = new javax.swing.JScrollPane();
		jPanel2 = new javax.swing.JPanel();
		dvsPanel = new javax.swing.JPanel();
		displayEventsCheckBox = new javax.swing.JCheckBox();
		dvsPixControl = new javax.swing.JPanel();
		bandwidthTweaker = new net.sf.jaer.biasgen.PotTweaker();
		thresholdTweaker = new net.sf.jaer.biasgen.PotTweaker();
		maxFiringRateTweaker = new net.sf.jaer.biasgen.PotTweaker();
		onOffBalanceTweaker = new net.sf.jaer.biasgen.PotTweaker();
		dvsRedGrRB = new javax.swing.JRadioButton();
		dvsGrayRB = new javax.swing.JRadioButton();
		dvsContLabel = new javax.swing.JLabel();
		dvsContSp = new javax.swing.JSpinner();
		captureEventsCB = new javax.swing.JCheckBox();
		apsPanel = new javax.swing.JPanel();
		jLabel1 = new javax.swing.JLabel();
		fdSp = new javax.swing.JSpinner();
		jLabel2 = new javax.swing.JLabel();
		edSp = new javax.swing.JSpinner();
		autoExpCB = new javax.swing.JCheckBox();
		jLabel3 = new javax.swing.JLabel();
		jLabel4 = new javax.swing.JLabel();
		jLabel5 = new javax.swing.JLabel();
		fpsTF = new javax.swing.JTextField();
		exposMsTF = new javax.swing.JTextField();
		jLabel6 = new javax.swing.JLabel();
		autoshotThresholdSp = new javax.swing.JSpinner();
		contrastSp = new javax.swing.JSpinner();
		autoContrastCB = new javax.swing.JCheckBox();
		displayFramesCheckBox = new javax.swing.JCheckBox();
		histCB = new javax.swing.JCheckBox();
		snapshotButton = new javax.swing.JButton();
		captureFramesCheckBox = new javax.swing.JCheckBox();
		glShutterCB = new javax.swing.JCheckBox();
		imuPanel = new javax.swing.JPanel();
		imuVisibleCB = new javax.swing.JCheckBox();
		imuEnabledCB = new javax.swing.JCheckBox();

		final javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
		jPanel1.setLayout(jPanel1Layout);
		jPanel1Layout.setHorizontalGroup(
			jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGap(0, 100, Short.MAX_VALUE));
		jPanel1Layout
			.setVerticalGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGap(0, 100, Short.MAX_VALUE));

		dvsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "DVS", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
			javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 1, 11))); // NOI18N
		dvsPanel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N

		displayEventsCheckBox.setText("Display events");
		displayEventsCheckBox.setToolTipText("Enables rendering of DVS events");
		displayEventsCheckBox.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(final java.awt.event.ActionEvent evt) {
				displayEventsCheckBoxActionPerformed(evt);
			}
		});

		final javax.swing.GroupLayout dvsPixControlLayout = new javax.swing.GroupLayout(dvsPixControl);
		dvsPixControl.setLayout(dvsPixControlLayout);
		dvsPixControlLayout.setHorizontalGroup(
			dvsPixControlLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGap(0, 0, Short.MAX_VALUE));
		dvsPixControlLayout.setVerticalGroup(
			dvsPixControlLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGap(0, 0, Short.MAX_VALUE));

		bandwidthTweaker.setLessDescription("Slower");
		bandwidthTweaker.setMinimumSize(new java.awt.Dimension(80, 30));
		bandwidthTweaker.setMoreDescription("Faster");
		bandwidthTweaker.setName("Adjusts pixel bandwidth"); // NOI18N
		bandwidthTweaker.setPreferredSize(new java.awt.Dimension(250, 47));
		bandwidthTweaker.setTweakDescription("");
		bandwidthTweaker.addChangeListener(new javax.swing.event.ChangeListener() {
			@Override
			public void stateChanged(final javax.swing.event.ChangeEvent evt) {
				bandwidthTweakerStateChanged(evt);
			}
		});

		thresholdTweaker.setLessDescription("Lower/more events");
		thresholdTweaker.setMinimumSize(new java.awt.Dimension(80, 30));
		thresholdTweaker.setMoreDescription("Higher/less events");
		thresholdTweaker.setName("Adjusts event threshold"); // NOI18N
		thresholdTweaker.setPreferredSize(new java.awt.Dimension(250, 47));
		thresholdTweaker.setTweakDescription("");
		thresholdTweaker.addChangeListener(new javax.swing.event.ChangeListener() {
			@Override
			public void stateChanged(final javax.swing.event.ChangeEvent evt) {
				thresholdTweakerStateChanged(evt);
			}
		});

		maxFiringRateTweaker.setLessDescription("Slower");
		maxFiringRateTweaker.setMinimumSize(new java.awt.Dimension(80, 30));
		maxFiringRateTweaker.setMoreDescription("Faster");
		maxFiringRateTweaker.setName("Adjusts maximum pixel firing rate (1/refactory period)"); // NOI18N
		maxFiringRateTweaker.setPreferredSize(new java.awt.Dimension(250, 47));
		maxFiringRateTweaker.setTweakDescription("");
		maxFiringRateTweaker.addChangeListener(new javax.swing.event.ChangeListener() {
			@Override
			public void stateChanged(final javax.swing.event.ChangeEvent evt) {
				maxFiringRateTweakerStateChanged(evt);
			}
		});

		onOffBalanceTweaker.setLessDescription("More Off events");
		onOffBalanceTweaker.setMinimumSize(new java.awt.Dimension(80, 30));
		onOffBalanceTweaker.setMoreDescription("More On events");
		onOffBalanceTweaker.setName("Adjusts balance bewteen On and Off events"); // NOI18N
		onOffBalanceTweaker.setPreferredSize(new java.awt.Dimension(250, 47));
		onOffBalanceTweaker.setTweakDescription("");
		onOffBalanceTweaker.addChangeListener(new javax.swing.event.ChangeListener() {
			@Override
			public void stateChanged(final javax.swing.event.ChangeEvent evt) {
				onOffBalanceTweakerStateChanged(evt);
			}
		});

		dvsColorButGrp.add(dvsRedGrRB);
		dvsRedGrRB.setText("Red/Green");
		dvsRedGrRB.setToolTipText("Show DVS events rendered as green (ON) and red (OFF) 2d histogram");
		dvsRedGrRB.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(final java.awt.event.ActionEvent evt) {
				dvsRedGrRBActionPerformed(evt);
			}
		});

		dvsColorButGrp.add(dvsGrayRB);
		dvsGrayRB.setText("Gray");
		dvsGrayRB.setToolTipText("Show DVS events rendered as gray scale 2d histogram");
		dvsGrayRB.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(final java.awt.event.ActionEvent evt) {
				dvsGrayRBActionPerformed(evt);
			}
		});

		dvsContLabel.setText("Contrast");

		dvsContSp.setModel(new javax.swing.SpinnerNumberModel(Integer.valueOf(1), Integer.valueOf(1), null, Integer.valueOf(1)));
		dvsContSp.setToolTipText(
			"Rendering contrast  of DVS events (1 is default). This many events makes full scale color, either black/white or red/green.");
		dvsContSp.addChangeListener(new javax.swing.event.ChangeListener() {
			@Override
			public void stateChanged(final javax.swing.event.ChangeEvent evt) {
				dvsContSpStateChanged(evt);
			}
		});

		captureEventsCB.setText("Capture events");
		captureEventsCB.setToolTipText("Enables capture of DVS events. Disabling capture turns off AER output of sensor.");
		captureEventsCB.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(final java.awt.event.ActionEvent evt) {
				captureEventsCBActionPerformed(evt);
			}
		});

		final javax.swing.GroupLayout dvsPanelLayout = new javax.swing.GroupLayout(dvsPanel);
		dvsPanel.setLayout(dvsPanelLayout);
		dvsPanelLayout.setHorizontalGroup(dvsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
			.addGroup(javax.swing.GroupLayout.Alignment.TRAILING,
				dvsPanelLayout.createSequentialGroup().addGap(10, 10, 10)
					.addGroup(dvsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
						.addComponent(thresholdTweaker, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE,
							javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
					.addComponent(maxFiringRateTweaker, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE,
						javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
				.addComponent(onOffBalanceTweaker, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
					Short.MAX_VALUE).addComponent(bandwidthTweaker, javax.swing.GroupLayout.DEFAULT_SIZE,
						javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
				.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED).addComponent(dvsPixControl,
					javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
			.addGroup(javax.swing.GroupLayout.Alignment.TRAILING,
				dvsPanelLayout.createSequentialGroup().addContainerGap()
					.addComponent(captureEventsCB, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
						Short.MAX_VALUE)
					.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
					.addComponent(displayEventsCheckBox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
						Short.MAX_VALUE)
					.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED).addComponent(dvsContLabel)
					.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
					.addComponent(dvsContSp, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
						javax.swing.GroupLayout.PREFERRED_SIZE)
					.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED).addComponent(dvsGrayRB)
					.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED).addComponent(dvsRedGrRB)));
		dvsPanelLayout.setVerticalGroup(dvsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(
			javax.swing.GroupLayout.Alignment.TRAILING,
			dvsPanelLayout.createSequentialGroup().addGap(0, 0, Short.MAX_VALUE)
				.addGroup(dvsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE).addComponent(captureEventsCB)
					.addComponent(displayEventsCheckBox).addComponent(dvsContLabel)
					.addComponent(dvsContSp, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
						javax.swing.GroupLayout.PREFERRED_SIZE)
					.addComponent(dvsGrayRB).addComponent(dvsRedGrRB))
				.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
				.addGroup(dvsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
					.addComponent(dvsPixControl, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
						javax.swing.GroupLayout.PREFERRED_SIZE)
					.addGroup(
						dvsPanelLayout.createSequentialGroup()
							.addComponent(bandwidthTweaker, javax.swing.GroupLayout.PREFERRED_SIZE, 60,
								javax.swing.GroupLayout.PREFERRED_SIZE)
							.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
							.addComponent(thresholdTweaker, javax.swing.GroupLayout.PREFERRED_SIZE, 63,
								javax.swing.GroupLayout.PREFERRED_SIZE)
						.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
						.addComponent(maxFiringRateTweaker, javax.swing.GroupLayout.PREFERRED_SIZE, 68,
							javax.swing.GroupLayout.PREFERRED_SIZE).addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
						.addComponent(onOffBalanceTweaker, javax.swing.GroupLayout.PREFERRED_SIZE, 63,
							javax.swing.GroupLayout.PREFERRED_SIZE)))
				.addContainerGap()));

		apsPanel.setBorder(
			javax.swing.BorderFactory.createTitledBorder(null, "Image Sensor", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
				javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 1, 11))); // NOI18N
		apsPanel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N

		jLabel1.setText("Frame Delay (ms)");

		fdSp.setModel(new javax.swing.SpinnerNumberModel(Float.valueOf(0.0f), Float.valueOf(0.0f), null, Float.valueOf(1.0f)));
		fdSp.setToolTipText("Delay of starting new frame capture after last frame");
		fdSp.addChangeListener(new javax.swing.event.ChangeListener() {
			@Override
			public void stateChanged(final javax.swing.event.ChangeEvent evt) {
				fdSpStateChanged(evt);
			}
		});

		jLabel2.setText("Exposure delay (ms)");

		edSp.setModel(new javax.swing.SpinnerNumberModel(Float.valueOf(0.0f), null, null, Float.valueOf(1.0f)));
		edSp.setToolTipText("The exposure delay; affects actual exposure time");
		edSp.addChangeListener(new javax.swing.event.ChangeListener() {
			@Override
			public void stateChanged(final javax.swing.event.ChangeEvent evt) {
				edSpStateChanged(evt);
			}
		});

		autoExpCB.setText("Auto exposure");
		autoExpCB.setToolTipText("Automatically set pixel exposure delay. See AutoExposureController panel for full parameter set.");
		autoExpCB.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
		autoExpCB.setHorizontalTextPosition(javax.swing.SwingConstants.LEFT);
		autoExpCB.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(final java.awt.event.ActionEvent evt) {
				autoExpCBActionPerformed(evt);
			}
		});

		jLabel3.setText("Auto-Shot kevents/frame");

		jLabel4.setText("Frame rate (Hz)");

		jLabel5.setText("Expos. (ms)");

		fpsTF.setEditable(false);
		fpsTF.setToolTipText("Measured frame rate in Hz");

		exposMsTF.setEditable(false);
		exposMsTF.setToolTipText("Measured exposure time in ms");
		exposMsTF.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(final java.awt.event.ActionEvent evt) {
				exposMsTFActionPerformed(evt);
			}
		});

		jLabel6.setText("Contrast");

		autoshotThresholdSp.setModel(new javax.swing.SpinnerNumberModel(Integer.valueOf(0), Integer.valueOf(0), null, Integer.valueOf(1)));
		autoshotThresholdSp.setToolTipText(
			"<html>Set non-zero to automatically trigger APS frame captures every this many thousand DVS events. <br>For better control of automatic frame capture,<br> including the use of pre-filtering of the DVS events, use the filter ApsDvsAutoShooter.");
		autoshotThresholdSp.addChangeListener(new javax.swing.event.ChangeListener() {
			@Override
			public void stateChanged(final javax.swing.event.ChangeEvent evt) {
				autoshotThresholdSpStateChanged(evt);
			}
		});

		contrastSp.setModel(
			new javax.swing.SpinnerNumberModel(Float.valueOf(1.0f), Float.valueOf(0.2f), Float.valueOf(5.0f), Float.valueOf(0.05f)));
		contrastSp.setToolTipText("Sets rendering contrast gain (1 is default). See Video Control panel for full set of controls.");
		contrastSp.addChangeListener(new javax.swing.event.ChangeListener() {
			@Override
			public void stateChanged(final javax.swing.event.ChangeEvent evt) {
				contrastSpStateChanged(evt);
			}
		});

		autoContrastCB.setText("Auto contrast");
		autoContrastCB.setToolTipText(
			"Uses DavisVideoContrastController to automatically set rendering contrast so that brightness (offset) and contrast (gain) scale min and max values to 0 and 1 respectively");
		autoContrastCB.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
		autoContrastCB.setHorizontalTextPosition(javax.swing.SwingConstants.LEFT);
		autoContrastCB.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(final java.awt.event.ActionEvent evt) {
				autoContrastCBActionPerformed(evt);
			}
		});

		displayFramesCheckBox.setText("Display Frames");
		displayFramesCheckBox.setToolTipText("Enables display of APS imager output");
		displayFramesCheckBox.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(final java.awt.event.ActionEvent evt) {
				displayFramesCheckBoxActionPerformed(evt);
			}
		});

		histCB.setText("histogram");
		histCB.setToolTipText("Display hisogram of captured pixel values from ADC (reset - signal values)");
		histCB.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
		histCB.setHorizontalTextPosition(javax.swing.SwingConstants.LEFT);
		histCB.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(final java.awt.event.ActionEvent evt) {
				histCBActionPerformed(evt);
			}
		});

		snapshotButton.setText("Take Snapshot");
		snapshotButton.setToolTipText("Triggers a single frame capture to onscreen buffer");
		snapshotButton.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(final java.awt.event.ActionEvent evt) {
				snapshotButtonActionPerformed(evt);
			}
		});

		captureFramesCheckBox.setText("Capture Frames");
		captureFramesCheckBox.setToolTipText("Enables capture of APS imager output (turns on ADC state machine)");
		captureFramesCheckBox.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(final java.awt.event.ActionEvent evt) {
				captureFramesCheckBoxActionPerformed(evt);
			}
		});

		glShutterCB.setText("Global shutter");
		glShutterCB.setToolTipText("Enables global (synchronous) electronic shutter. Unchecked enables rolling shutter readout.");
		glShutterCB.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
		glShutterCB.setHorizontalTextPosition(javax.swing.SwingConstants.LEFT);
		glShutterCB.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(final java.awt.event.ActionEvent evt) {
				glShutterCBActionPerformed(evt);
			}
		});

		final javax.swing.GroupLayout apsPanelLayout = new javax.swing.GroupLayout(apsPanel);
		apsPanel.setLayout(apsPanelLayout);
		apsPanelLayout.setHorizontalGroup(apsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
			.addGroup(apsPanelLayout.createSequentialGroup()
				.addGroup(apsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
					.addGroup(javax.swing.GroupLayout.Alignment.LEADING,
						apsPanelLayout.createSequentialGroup().addComponent(jLabel2)
							.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED).addComponent(edSp))
				.addGroup(javax.swing.GroupLayout.Alignment.LEADING,
					apsPanelLayout.createSequentialGroup().addGap(13, 13, 13).addComponent(jLabel1)
						.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
						.addComponent(fdSp, javax.swing.GroupLayout.PREFERRED_SIZE, 74, javax.swing.GroupLayout.PREFERRED_SIZE)))
				.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
				.addGroup(apsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addComponent(jLabel4)
					.addComponent(jLabel5))
				.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
				.addGroup(apsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false).addComponent(exposMsTF)
					.addComponent(fpsTF)))
			.addGroup(apsPanelLayout.createSequentialGroup().addContainerGap().addComponent(captureFramesCheckBox)
				.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED).addComponent(displayFramesCheckBox)
				.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED).addComponent(snapshotButton))
			.addGroup(apsPanelLayout.createSequentialGroup()
				.addGroup(apsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
					.addGroup(apsPanelLayout.createSequentialGroup().addGap(4, 4, 4).addComponent(jLabel6)
						.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
						.addComponent(contrastSp, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
							javax.swing.GroupLayout.PREFERRED_SIZE)
						.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED).addComponent(autoContrastCB))
				.addGroup(apsPanelLayout.createSequentialGroup().addComponent(jLabel3)
					.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
					.addComponent(autoshotThresholdSp, javax.swing.GroupLayout.PREFERRED_SIZE, 72, javax.swing.GroupLayout.PREFERRED_SIZE)))
				.addGap(18, 18, 18)
				.addGroup(apsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addComponent(autoExpCB)
					.addGroup(apsPanelLayout.createSequentialGroup().addComponent(glShutterCB)
						.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED).addComponent(histCB)))));
		apsPanelLayout.setVerticalGroup(apsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
			.addGroup(apsPanelLayout.createSequentialGroup()
				.addGroup(apsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
					.addGroup(apsPanelLayout.createSequentialGroup().addGap(4, 4, 4)
						.addGroup(apsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
							.addComponent(captureFramesCheckBox).addComponent(displayFramesCheckBox)))
					.addComponent(snapshotButton))
				.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
				.addGroup(apsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE).addComponent(jLabel1)
					.addComponent(fdSp, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
						javax.swing.GroupLayout.PREFERRED_SIZE)
					.addComponent(jLabel4).addComponent(fpsTF, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
						javax.swing.GroupLayout.PREFERRED_SIZE))
				.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
				.addGroup(apsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE).addComponent(jLabel2)
					.addComponent(edSp, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
						javax.swing.GroupLayout.PREFERRED_SIZE)
					.addComponent(jLabel5).addComponent(exposMsTF, javax.swing.GroupLayout.PREFERRED_SIZE,
						javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
				.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
				.addGroup(apsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
					.addComponent(jLabel3, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE)
					.addComponent(autoshotThresholdSp, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
						javax.swing.GroupLayout.PREFERRED_SIZE)
					.addComponent(autoExpCB))
				.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
				.addGroup(apsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE).addComponent(jLabel6)
					.addComponent(contrastSp, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
						javax.swing.GroupLayout.PREFERRED_SIZE)
					.addComponent(autoContrastCB).addComponent(histCB).addComponent(glShutterCB))));

		snapshotButton.getAccessibleContext().setAccessibleName("snapshotButton");

		imuPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("IMU"));
		imuPanel.setToolTipText("Controls for Inertial Measurement Unit (Gyro/Accelometer)");

		imuVisibleCB.setText("Display");
		imuVisibleCB.setToolTipText("show the IMU output if it is available");
		imuVisibleCB.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);
		imuVisibleCB.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(final java.awt.event.ActionEvent evt) {
				imuVisibleCBActionPerformed(evt);
			}
		});

		imuEnabledCB.setText("Enable");
		imuEnabledCB.setToolTipText("show the IMU output if it is available");
		imuEnabledCB.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);
		imuEnabledCB.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(final java.awt.event.ActionEvent evt) {
				imuEnabledCBActionPerformed(evt);
			}
		});

		final javax.swing.GroupLayout imuPanelLayout = new javax.swing.GroupLayout(imuPanel);
		imuPanel.setLayout(imuPanelLayout);
		imuPanelLayout.setHorizontalGroup(imuPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
			.addGroup(javax.swing.GroupLayout.Alignment.TRAILING, imuPanelLayout.createSequentialGroup()
				.addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE).addGroup(imuPanelLayout
					.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addComponent(imuVisibleCB).addComponent(imuEnabledCB))
				.addContainerGap()));
		imuPanelLayout
			.setVerticalGroup(imuPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
				.addGroup(imuPanelLayout.createSequentialGroup()
					.addComponent(imuEnabledCB, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
					.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE,
						Short.MAX_VALUE)
				.addComponent(imuVisibleCB, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)));

		final javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
		jPanel2.setLayout(jPanel2Layout);
		jPanel2Layout.setHorizontalGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
			.addGap(0, 0, Short.MAX_VALUE)
			.addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(jPanel2Layout
				.createSequentialGroup().addContainerGap()
				.addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
					.addGroup(jPanel2Layout.createSequentialGroup()
						.addComponent(apsPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
							javax.swing.GroupLayout.PREFERRED_SIZE)
						.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED).addComponent(imuPanel,
							javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
							javax.swing.GroupLayout.PREFERRED_SIZE))
					.addComponent(dvsPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
						javax.swing.GroupLayout.PREFERRED_SIZE))
				.addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))));
		jPanel2Layout
			.setVerticalGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGap(0, 0, Short.MAX_VALUE)
				.addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
					.addGroup(jPanel2Layout.createSequentialGroup().addContainerGap()
						.addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
							.addComponent(apsPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
								javax.swing.GroupLayout.PREFERRED_SIZE)
							.addComponent(imuPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
								javax.swing.GroupLayout.PREFERRED_SIZE))
						.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
						.addComponent(dvsPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
							javax.swing.GroupLayout.PREFERRED_SIZE)
					.addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))));

		jScrollPane1.setViewportView(jPanel2);

		final javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
		setLayout(layout);
		layout.setHorizontalGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addComponent(jScrollPane1));
		layout.setVerticalGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
			.addGroup(layout.createSequentialGroup().addComponent(jScrollPane1).addGap(16, 16, 16)));
	}// </editor-fold>//GEN-END:initComponents

	private void glShutterCBActionPerformed(final java.awt.event.ActionEvent evt) {// GEN-FIRST:event_glShutterCBActionPerformed
		((DavisBaseCamera) chip).getDavisConfig().setGlobalShutter(glShutterCB.isSelected());
	}// GEN-LAST:event_glShutterCBActionPerformed

	private void contrastSpStateChanged(final javax.swing.event.ChangeEvent evt) {// GEN-FIRST:event_contrastSpStateChanged
		if (contrastController.isUseAutoContrast()) {
			return; // don't update if only used for display of contrast
		}
		try {
			final float f = (float) contrastSp.getValue();
			contrastController.setContrast(f);
			contrastSp.setBackground(Color.GRAY);
		}
		catch (final Exception e) {
			DavisUserControlPanel.log.warning(e.toString());
			contrastSp.setBackground(Color.red);
		}
	}// GEN-LAST:event_contrastSpStateChanged

	private void autoContrastCBActionPerformed(final java.awt.event.ActionEvent evt) {// GEN-FIRST:event_autoContrastCBActionPerformed
		contrastController.setUseAutoContrast(autoContrastCB.isSelected());
	}// GEN-LAST:event_autoContrastCBActionPerformed

	private void bandwidthTweakerStateChanged(final javax.swing.event.ChangeEvent evt) {// GEN-FIRST:event_bandwidthTweakerStateChanged
		getDvsTweaks().setBandwidthTweak(bandwidthTweaker.getValue());
		setFileModified();
	}// GEN-LAST:event_bandwidthTweakerStateChanged

	private void thresholdTweakerStateChanged(final javax.swing.event.ChangeEvent evt) {// GEN-FIRST:event_thresholdTweakerStateChanged
		getDvsTweaks().setThresholdTweak(thresholdTweaker.getValue());
		setFileModified();
	}// GEN-LAST:event_thresholdTweakerStateChanged

	private void maxFiringRateTweakerStateChanged(final javax.swing.event.ChangeEvent evt) {// GEN-FIRST:event_maxFiringRateTweakerStateChanged
		getDvsTweaks().setMaxFiringRateTweak(maxFiringRateTweaker.getValue());
		setFileModified();
	}// GEN-LAST:event_maxFiringRateTweakerStateChanged

	private void onOffBalanceTweakerStateChanged(final javax.swing.event.ChangeEvent evt) {// GEN-FIRST:event_onOffBalanceTweakerStateChanged
		getDvsTweaks().setOnOffBalanceTweak(onOffBalanceTweaker.getValue());
		setFileModified();
	}// GEN-LAST:event_onOffBalanceTweakerStateChanged

	private void exposMsTFActionPerformed(final java.awt.event.ActionEvent evt) {// GEN-FIRST:event_exposMsTFActionPerformed
		try {
			getConfig().setExposureDelayMs((float) edSp.getValue());
		}
		catch (final Exception e) {
			DavisUserControlPanel.log.warning(e.toString());
		}
	}// GEN-LAST:event_exposMsTFActionPerformed

	private void fdSpStateChanged(final javax.swing.event.ChangeEvent evt) {// GEN-FIRST:event_fdSpStateChanged
		try {
			getConfig().setFrameDelayMs((float) fdSp.getValue());
			fdSp.setBackground(Color.WHITE);
		}
		catch (final Exception e) {
			DavisUserControlPanel.log.warning(e.toString());
			fdSp.setBackground(Color.RED);
		}
	}// GEN-LAST:event_fdSpStateChanged

	private void edSpStateChanged(final javax.swing.event.ChangeEvent evt) {// GEN-FIRST:event_edSpStateChanged
		try {
			getConfig().setExposureDelayMs((float) edSp.getValue());
			edSp.setBackground(Color.WHITE);
		}
		catch (final Exception e) {
			DavisUserControlPanel.log.warning(e.toString());
			edSp.setBackground(Color.RED);
		}
	}// GEN-LAST:event_edSpStateChanged

	private void displayFramesCheckBoxActionPerformed(final java.awt.event.ActionEvent evt) {// GEN-FIRST:event_displayFramesCheckBoxActionPerformed
		getConfig().setDisplayFrames(displayFramesCheckBox.isSelected());
	}// GEN-LAST:event_displayFramesCheckBoxActionPerformed

	private void displayEventsCheckBoxActionPerformed(final java.awt.event.ActionEvent evt) {// GEN-FIRST:event_displayEventsCheckBoxActionPerformed
		getConfig().setDisplayEvents(displayEventsCheckBox.isSelected());
	}// GEN-LAST:event_displayEventsCheckBoxActionPerformed

	private void autoshotThresholdSpStateChanged(final javax.swing.event.ChangeEvent evt) {// GEN-FIRST:event_autoshotThresholdSpStateChanged
		try {
			chip.setAutoshotThresholdEvents((Integer) autoshotThresholdSp.getValue() << 10);
		}
		catch (final Exception e) {
			DavisUserControlPanel.log.warning(e.toString());
		}
	}// GEN-LAST:event_autoshotThresholdSpStateChanged

	private void histCBActionPerformed(final java.awt.event.ActionEvent evt) {// GEN-FIRST:event_histCBActionPerformed
		chip.setShowImageHistogram(histCB.isSelected());
	}// GEN-LAST:event_histCBActionPerformed

	private void dvsGrayRBActionPerformed(final java.awt.event.ActionEvent evt) {// GEN-FIRST:event_dvsGrayRBActionPerformed
		renderer.setColorMode(AEChipRenderer.ColorMode.GrayLevel);
	}// GEN-LAST:event_dvsGrayRBActionPerformed

	private void dvsRedGrRBActionPerformed(final java.awt.event.ActionEvent evt) {// GEN-FIRST:event_dvsRedGrRBActionPerformed
		renderer.setColorMode(AEChipRenderer.ColorMode.RedGreen);
	}// GEN-LAST:event_dvsRedGrRBActionPerformed

	private void dvsContSpStateChanged(final javax.swing.event.ChangeEvent evt) {// GEN-FIRST:event_dvsContSpStateChanged
		renderer.setColorScale((Integer) dvsContSp.getValue());
	}// GEN-LAST:event_dvsContSpStateChanged

	private void imuVisibleCBActionPerformed(final java.awt.event.ActionEvent evt) {// GEN-FIRST:event_imuVisibleCBActionPerformed
		getConfig().setDisplayImu(imuVisibleCB.isSelected());
	}// GEN-LAST:event_imuVisibleCBActionPerformed

	private void snapshotButtonActionPerformed(final java.awt.event.ActionEvent evt) {// GEN-FIRST:event_snapshotButtonActionPerformed
		chip.takeSnapshot();
	}// GEN-LAST:event_snapshotButtonActionPerformed

	private void autoExpCBActionPerformed(final java.awt.event.ActionEvent evt) {// GEN-FIRST:event_autoExpCBActionPerformed
		chip.setAutoExposureEnabled(autoExpCB.isSelected());
	}// GEN-LAST:event_autoExpCBActionPerformed

	private void captureFramesCheckBoxActionPerformed(final java.awt.event.ActionEvent evt) {// GEN-FIRST:event_captureFramesCheckBoxActionPerformed
		getConfig().setCaptureFramesEnabled(captureFramesCheckBox.isSelected());
	}// GEN-LAST:event_captureFramesCheckBoxActionPerformed

	private void captureEventsCBActionPerformed(final java.awt.event.ActionEvent evt) {// GEN-FIRST:event_captureEventsCBActionPerformed
		getConfig().setCaptureEvents(captureEventsCB.isSelected());
	}// GEN-LAST:event_captureEventsCBActionPerformed

	private void imuEnabledCBActionPerformed(final java.awt.event.ActionEvent evt) {// GEN-FIRST:event_imuEnabledCBActionPerformed
		getConfig().setImuEnabled(imuEnabledCB.isSelected());
	}// GEN-LAST:event_imuEnabledCBActionPerformed

	// Variables declaration - do not modify//GEN-BEGIN:variables
	private javax.swing.JPanel apsPanel;
	private javax.swing.JCheckBox autoContrastCB;
	private javax.swing.JCheckBox autoExpCB;
	private javax.swing.JSpinner autoshotThresholdSp;
	private net.sf.jaer.biasgen.PotTweaker bandwidthTweaker;
	private javax.swing.JCheckBox captureEventsCB;
	private javax.swing.JCheckBox captureFramesCheckBox;
	private javax.swing.JSpinner contrastSp;
	private javax.swing.JCheckBox displayEventsCheckBox;
	private javax.swing.JCheckBox displayFramesCheckBox;
	private javax.swing.ButtonGroup dvsColorButGrp;
	private javax.swing.JLabel dvsContLabel;
	private javax.swing.JSpinner dvsContSp;
	private javax.swing.JRadioButton dvsGrayRB;
	private javax.swing.JPanel dvsPanel;
	private javax.swing.JPanel dvsPixControl;
	private javax.swing.JRadioButton dvsRedGrRB;
	private javax.swing.JSpinner edSp;
	private javax.swing.JTextField exposMsTF;
	private javax.swing.JSpinner fdSp;
	private javax.swing.JTextField fpsTF;
	private javax.swing.JCheckBox glShutterCB;
	private javax.swing.JCheckBox histCB;
	private javax.swing.JCheckBox imuEnabledCB;
	private javax.swing.JPanel imuPanel;
	private javax.swing.JCheckBox imuVisibleCB;
	private javax.swing.JLabel jLabel1;
	private javax.swing.JLabel jLabel2;
	private javax.swing.JLabel jLabel3;
	private javax.swing.JLabel jLabel4;
	private javax.swing.JLabel jLabel5;
	private javax.swing.JLabel jLabel6;
	private javax.swing.JPanel jPanel1;
	private javax.swing.JPanel jPanel2;
	private javax.swing.JScrollPane jScrollPane1;
	private net.sf.jaer.biasgen.PotTweaker maxFiringRateTweaker;
	private net.sf.jaer.biasgen.PotTweaker onOffBalanceTweaker;
	private javax.swing.JButton snapshotButton;
	private net.sf.jaer.biasgen.PotTweaker thresholdTweaker;
	// End of variables declaration//GEN-END:variables

	/**
	 * @return the chip
	 */
	public DavisChip getChip() {
		return chip;
	}

	/**
	 * @return the getConfig()
	 */
	public DavisDisplayConfigInterface getApsDvsConfig() {
		return getConfig();
	}

	/**
	 * @return the apsDvsTweaks
	 */
	public DVSTweaks getDvsTweaks() {
		return apsDvsTweaks;
	}

	/**
	 * Handles Observable updates from Davis240Config, and inner classes
	 * VideoControl and ApsReadoutControl
	 *
	 * @param o
	 * @param arg
	 */
	@Override
	public void update(final Observable o, final Object arg) {
		// updates to user friendly controls come from low level properties here
		if (o == ((DavisBaseCamera) chip).getDavisConfig().getExposureControlRegister()) {
			edSp.setValue(getConfig().getExposureDelayMs());
		}
		else if (o == ((DavisBaseCamera) chip).getDavisConfig().getFrameDelayControlRegister()) {
			fdSp.setValue(getConfig().getFrameDelayMs());
		}
		else if (o == videoControl) {
			displayFramesCheckBox.setSelected(getConfig().isDisplayFrames());
			displayEventsCheckBox.setSelected(getConfig().isDisplayEvents());
		}
		else if (o == contrastController) {
			autoContrastCB.setSelected(contrastController.isUseAutoContrast());
			contrastSp.setEnabled(!contrastController.isUseAutoContrast());
			contrastSp.setValue(contrastController.getContrast());
		}
	}
}
