package ch.unizh.ini.jaer.chip.cochlea;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Logger;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.event.UndoableEditListener;
import javax.swing.undo.StateEdit;
import javax.swing.undo.StateEditable;
import javax.swing.undo.UndoableEditSupport;

import ch.unizh.ini.jaer.chip.cochlea.CochleaLP.Biasgen;
import ch.unizh.ini.jaer.chip.cochlea.CochleaLP.CochleaChannel;
import ch.unizh.ini.jaer.config.spi.SPIConfigBit;
import ch.unizh.ini.jaer.config.spi.SPIConfigInt;
import ch.unizh.ini.jaer.config.spi.SPIConfigValue;
import net.sf.jaer.biasgen.BiasgenPanel;
import net.sf.jaer.biasgen.coarsefine.ShiftedSourceControlsCF;
import net.sf.jaer.util.ParameterControlPanel;

/**
 * Control panel for CochleaLP
 *
 * @author Luca Longinotti, Minhao Liu, Shih-Chii Liu, Tobi Delbruck
 */
public final class CochleaLPControlPanel extends JTabbedPane implements Observer {

	private static final long serialVersionUID = -7435419921722582550L;

	private final Logger log = Logger.getLogger("CochleaLPControlPanel");

	private final CochleaLP chip;
	private final CochleaLP.Biasgen biasgen;

	private final Map<SPIConfigValue, JComponent> configValueMap = new HashMap<>();

	public CochleaLPControlPanel(final CochleaLP chip) {
		this.chip = chip;
		biasgen = (Biasgen) chip.getBiasgen();

		initComponents();

		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(final MouseEvent evt) {
				tabbedPaneMouseClicked(evt);
			}
		});

		SPIConfigBit.makeSPIBitConfig(biasgen.biasForceEnable, onchipBiasgenPanel, configValueMap, getBiasgen());

		onchipBiasgenPanel.add(new ShiftedSourceControlsCF(biasgen.ssBiases[0]));
		onchipBiasgenPanel.add(new ShiftedSourceControlsCF(biasgen.ssBiases[1]));
		biasgen.setPotArray(biasgen.ipots);
		onchipBiasgenPanel.add(new BiasgenPanel(getBiasgen()));

		onchipBiasgenPanel.add(Box.createVerticalGlue()); // push up to prevent expansion of PotPanel

		SPIConfigBit.makeSPIBitConfig(biasgen.dacRun, offchipDACPanel, configValueMap, getBiasgen());

		biasgen.setPotArray(biasgen.vpots);
		offchipDACPanel.add(new BiasgenPanel(getBiasgen()));

		for (final SPIConfigValue cfgVal : biasgen.scannerControl) {
			if (cfgVal instanceof SPIConfigBit) {
				SPIConfigBit.makeSPIBitConfig((SPIConfigBit) cfgVal, scannerPanel, configValueMap, getBiasgen());
			}
			else if (cfgVal instanceof SPIConfigInt) {
				SPIConfigInt.makeSPIIntConfig((SPIConfigInt) cfgVal, scannerPanel, configValueMap, getBiasgen());
			}
		}

		for (final SPIConfigValue cfgVal : biasgen.aerControl) {
			if (cfgVal instanceof SPIConfigBit) {
				SPIConfigBit.makeSPIBitConfig((SPIConfigBit) cfgVal, aerPanel, configValueMap, getBiasgen());
			}
			else if (cfgVal instanceof SPIConfigInt) {
				SPIConfigInt.makeSPIIntConfig((SPIConfigInt) cfgVal, aerPanel, configValueMap, getBiasgen());
			}
		}

		for (final SPIConfigValue cfgVal : biasgen.adcControl) {
			if (cfgVal instanceof SPIConfigBit) {
				SPIConfigBit.makeSPIBitConfig((SPIConfigBit) cfgVal, adcPanel, configValueMap, getBiasgen());
			}
			else if (cfgVal instanceof SPIConfigInt) {
				SPIConfigInt.makeSPIIntConfig((SPIConfigInt) cfgVal, adcPanel, configValueMap, getBiasgen());
			}
		}

                for (final SPIConfigValue cfgVal : biasgen.chipDiagChain) {
			if (cfgVal instanceof SPIConfigBit) {
				SPIConfigBit.makeSPIBitConfig((SPIConfigBit) cfgVal, chipDiagPanel, configValueMap, getBiasgen());
			}
			else if (cfgVal instanceof SPIConfigInt) {
				SPIConfigInt.makeSPIIntConfig((SPIConfigInt) cfgVal, chipDiagPanel, configValueMap, getBiasgen());
			}
		}

		// Add cochlea channel configuration GUI.
		final int CHAN_PER_COL = 32;
		int chanCount = 0;
		JPanel colPan = new JPanel();
		colPan.setLayout(new BoxLayout(colPan, BoxLayout.Y_AXIS));
		colPan.setAlignmentY(0); // puts panel at top
		final CochleaChannelControlPanel gPan = new CochleaChannelControlPanel(null); // global control
		colPan.add(gPan);

		for (final CochleaChannel chan : biasgen.cochleaChannels) {
			// TODO add preference change or update listener to
			// synchronize when config is loaded

			// TODO add undo/redo support for channels

			final CochleaChannelControlPanel cPan = new CochleaChannelControlPanel(chan);
			colPan.add(cPan);
			chanCount++;
			if ((chanCount % CHAN_PER_COL) == 0) {
				channelPanel.add(colPan);
				colPan = new JPanel();
				colPan.setLayout(new BoxLayout(colPan, BoxLayout.Y_AXIS));
				colPan.setAlignmentY(0);
			}
		}
		setTabLayoutPolicy(JTabbedPane.WRAP_TAB_LAYOUT);
		channelPanel.setPreferredSize(new Dimension(600, 600));
		channelPanel.revalidate();
		setPreferredSize(new Dimension(800, 600));
		revalidate();

		setSelectedIndex(chip.getPrefs().getInt("CochleaLPControlPanel.bgTabbedPaneSelectedIndex", 0));
	}

	/**
	 * @return the biasgen
	 */
	public CochleaLP.Biasgen getBiasgen() {
		return biasgen;
	}

	/**
	 * Handles updates to GUI controls from any source, including preference
	 * changes
	 */
	@Override
	public synchronized void update(final Observable observable, final Object object) {
		try {
			if (observable instanceof SPIConfigBit) {
				final SPIConfigBit cfgBit = (SPIConfigBit) observable;

				// Ensure GUI is up-to-date.
				if (configValueMap.containsKey(cfgBit)) {
					((JRadioButton) configValueMap.get(cfgBit)).setSelected(cfgBit.isSet());
				}
			}
			else if (observable instanceof SPIConfigInt) {
				final SPIConfigInt cfgInt = (SPIConfigInt) observable;

				// Ensure GUI is up-to-date.
				if (configValueMap.containsKey(cfgInt)) {
					((JTextField) configValueMap.get(cfgInt)).setText(Integer.toString(cfgInt.get()));
				}
			}
			else if (observable instanceof CochleaChannel) {
				final CochleaChannel c = (CochleaChannel) observable;

				if (c.getControlPanel() != null) {
					c.getControlPanel().updateGUI();
				}
			}
			else {
				log.warning("unknown observable " + observable + " , not sending anything");
			}
		}
		catch (final Exception e) {
			log.warning(e.toString());
		}
	}

	private static final int TF_MAX_HEIGHT = 15;
	private static final int TF_HEIGHT = 6;
	private static final int TF_MIN_W = 15, TF_PREF_W = 20, TF_MAX_W = 40;

	/**
	 * Complex class to control a single channel of cochlea, enable key and
	 * mouse listeners in text fields, and provide undo support
	 */
	public class CochleaChannelControlPanel extends JPanel implements StateEditable {

		private static final long serialVersionUID = -5426824180502211200L;

		final JRadioButton but = new JRadioButton();
		final JTextField tf0 = new JTextField();
		final JTextField tf1 = new JTextField();
		final JTextField tf2 = new JTextField();
		final JTextField tf3 = new JTextField();
		final JTextField tf4 = new JTextField();
		final CochleaChannel chan;
		StateEdit edit = null;
		UndoableEditSupport editSupport = new UndoableEditSupport();
		private boolean addedUndoListener = false;
		private long lastMouseWheelMovementTime = 0;
		private final long minDtMsForWheelEditPost = 500;

		/**
		 * Construct control for one channel
		 *
		 * @param chan,
		 *            the channel, or null to control all channels
		 */
		public CochleaChannelControlPanel(final CochleaChannel chan) {
			this.chan = chan;
			setLayout(new BoxLayout(this, BoxLayout.X_AXIS));

			final JLabel label = new JLabel();
			if (chan == null) {
				label.setText("global");
				label.setToolTipText("<html> all channels: " + "<br>Enter value or use mouse wheel or arrow keys to change value.");
			}
			else if (chan != null) {
				label.setText(chan.getName());
				label.setToolTipText("<html>" + chan.toString() + "<br>" + chan.getDescription()
					+ "<br>Enter value or use mouse wheel or arrow keys to change value.");
			}
			add(label);

			final float[] sums = new float[5];
			int nchan = 0;
			if (chan == null) {
				// compute summed values for global control which will show average to start with
				for (final CochleaChannel c : biasgen.cochleaChannels) {
					sums[0] += c.getDelayCapConfigADM();
					sums[1] += c.getResetCapConfigADM();
					sums[2] += c.getLnaGainConfig();
					sums[3] += c.getAttenuatorConfig();
					sums[4] += c.getqTuning();
					nchan++;
				}

			}

			but.setToolTipText("Comparator self-oscillation enable. i.e. spike generation enable 1 bit, select to turn on");
			but.setSelected(chan == null ? true : chan.isComparatorSelfOscillationEnable());
			but.setAlignmentX(Component.LEFT_ALIGNMENT);
			but.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(final ActionEvent e) {
					final JRadioButton button = (JRadioButton) e.getSource();
					if (chan == null) {
						for (final CochleaChannel c : biasgen.cochleaChannels) {
							c.setComparatorSelfOscillationEnable(button.isSelected());
						}
					}
					else {
						chan.setComparatorSelfOscillationEnable(button.isSelected());
					}
					if(chan!=null) chan.setFileModified();
				}
			});
			add(but);

			tf0.setToolTipText((chan == null ? "global" : chan.getName())
				+ " - Delay cap configuration in ADM. Reset signal φrst pulse width control 3bits, 0 is shortest delay, if pulse is too short then ADM does not function correctly");
			tf0.setText(chan == null ? Integer.toString((int) (sums[0] / nchan)) : Integer.toString(chan.getDelayCapConfigADM()));
			tf0.setMinimumSize(new Dimension(CochleaLPControlPanel.TF_MIN_W, CochleaLPControlPanel.TF_HEIGHT));
			tf0.setPreferredSize(new Dimension(CochleaLPControlPanel.TF_PREF_W, CochleaLPControlPanel.TF_HEIGHT));
			tf0.setMaximumSize(new Dimension(CochleaLPControlPanel.TF_MAX_W, CochleaLPControlPanel.TF_MAX_HEIGHT));
			tf0.addActionListener(new CochleaChannelIntAction(chan, 0)); // TODO add mouse wheel and mouse scroll
																			// listeners
			tf0.addKeyListener(new CochleaChannelKeyAction(chan, 0));
			tf0.addMouseWheelListener(new CochleaChannelMouseWheelAction(chan, 0));
			add(tf0);

			tf1.setToolTipText((chan == null ? "global" : chan.getName())
				+ " - Reset cap configuration in ADM. ADM reset level control, 2bits, 0 is lowest subtraction 1delta, 1 is 1.5delta, 2 is 2delta, 3 is max subtraction 2.5delta, used to compensate slope overload and delay.");
			tf1.setText(chan == null ? Integer.toString((int) (sums[1] / nchan)) : Integer.toString(chan.getResetCapConfigADM()));
			tf1.setMinimumSize(new Dimension(CochleaLPControlPanel.TF_MIN_W, CochleaLPControlPanel.TF_HEIGHT));
			tf1.setPreferredSize(new Dimension(CochleaLPControlPanel.TF_PREF_W, CochleaLPControlPanel.TF_HEIGHT));
			tf1.setMaximumSize(new Dimension(CochleaLPControlPanel.TF_MAX_W, CochleaLPControlPanel.TF_MAX_HEIGHT));
			tf1.addActionListener(new CochleaChannelIntAction(chan, 1));
			tf1.addKeyListener(new CochleaChannelKeyAction(chan, 1));
			tf1.addMouseWheelListener(new CochleaChannelMouseWheelAction(chan, 1));
			add(tf1);

			tf2.setToolTipText((chan == null ? "global" : chan.getName())
				+ " - PGA gain configuration. PGA gain control 3 bits, effectively 4 levels, uses only values 0,1,3,7. 0 is highest gain (40dB designed), 7 is lowest (18dB designed??)");
			tf2.setText(chan == null ? Integer.toString((int) (sums[2] / nchan)) : Integer.toString(chan.getLnaGainConfig()));
			tf2.setMinimumSize(new Dimension(CochleaLPControlPanel.TF_MIN_W, CochleaLPControlPanel.TF_HEIGHT));
			tf2.setPreferredSize(new Dimension(CochleaLPControlPanel.TF_PREF_W, CochleaLPControlPanel.TF_HEIGHT));
			tf2.setMaximumSize(new Dimension(CochleaLPControlPanel.TF_MAX_W, CochleaLPControlPanel.TF_MAX_HEIGHT));
			tf2.addActionListener(new CochleaChannelIntAction(chan, 2));
			tf2.addKeyListener(new CochleaChannelKeyAction(chan, 2));
			tf2.addMouseWheelListener(new CochleaChannelMouseWheelAction(chan, 2));
			add(tf2);

			tf3.setToolTipText((chan == null ? "global" : chan.getName())
				+ " - Attenuator configuration. Attenuation level control 3 bits, 8 levels, 7 is no attenuation, 0 is -18dB");
			tf3.setText(chan == null ? Integer.toString((int) (sums[3] / nchan)) : Integer.toString(chan.getAttenuatorConfig()));
			tf3.setMinimumSize(new Dimension(CochleaLPControlPanel.TF_MIN_W, CochleaLPControlPanel.TF_HEIGHT));
			tf3.setPreferredSize(new Dimension(CochleaLPControlPanel.TF_PREF_W, CochleaLPControlPanel.TF_HEIGHT));
			tf3.setMaximumSize(new Dimension(CochleaLPControlPanel.TF_MAX_W, CochleaLPControlPanel.TF_MAX_HEIGHT));
			tf3.addActionListener(new CochleaChannelIntAction(chan, 3));
			tf3.addKeyListener(new CochleaChannelKeyAction(chan, 3));
			tf3.addMouseWheelListener(new CochleaChannelMouseWheelAction(chan, 3));
			add(tf3);

			tf4.setToolTipText((chan == null ? "global" : chan.getName())
				+ " - QTuning configuration. Q control, 8 bits. 255 is lowest Q, 0 is highest (unstable). Reduce to increase effective Q.");
			tf4.setText(chan == null ? Integer.toString((int) (sums[4] / nchan)) : Integer.toString(chan.getqTuning()));
			tf4.setMinimumSize(new Dimension(CochleaLPControlPanel.TF_MIN_W, CochleaLPControlPanel.TF_HEIGHT));
			tf4.setPreferredSize(new Dimension(CochleaLPControlPanel.TF_PREF_W, CochleaLPControlPanel.TF_HEIGHT));
			tf4.setMaximumSize(new Dimension(CochleaLPControlPanel.TF_MAX_W, CochleaLPControlPanel.TF_MAX_HEIGHT));
			tf4.addActionListener(new CochleaChannelIntAction(chan, 4));
			tf4.addKeyListener(new CochleaChannelKeyAction(chan, 4));
			tf4.addMouseWheelListener(new CochleaChannelMouseWheelAction(chan, 4));
			add(tf4);

			if (chan != null) {
				chan.setControlPanel(this);
				chan.addObserver(CochleaLPControlPanel.this);
			}
			addAncestorListener(new javax.swing.event.AncestorListener() {
				@Override
				public void ancestorMoved(final javax.swing.event.AncestorEvent evt) {
				}

				@Override
				public void ancestorAdded(final javax.swing.event.AncestorEvent evt) {
					formAncestorAdded(evt);
				}

				@Override
				public void ancestorRemoved(final javax.swing.event.AncestorEvent evt) {
				}
			});

		}

		// march up till we reach the Container that handles undos
		private void formAncestorAdded(final javax.swing.event.AncestorEvent evt) {// GEN-FIRST:event_formAncestorAdded
			if (addedUndoListener) {
				return;
			}
			addedUndoListener = true;
			if (evt.getComponent() instanceof Container) {
				Container anc = evt.getComponent();
				while ((anc != null) && (anc instanceof Container)) {
					if (anc instanceof UndoableEditListener) {
						editSupport.addUndoableEditListener((UndoableEditListener) anc);
						break;
					}
					anc = anc.getParent();
				}
			}
		}// GEN-LAST:event_formAncestorAdded

		public void updateGUI() {
			but.setSelected(chan.isComparatorSelfOscillationEnable());
			tf0.setText(Integer.toString(chan.getDelayCapConfigADM()));
			tf1.setText(Integer.toString(chan.getResetCapConfigADM()));
			tf2.setText(Integer.toString(chan.getLnaGainConfig()));
			tf3.setText(Integer.toString(chan.getAttenuatorConfig()));
			tf4.setText(Integer.toString(chan.getqTuning()));
		}

		private class CochleaChannelIntAction implements ActionListener {

			private final CochleaChannel channel;
			private final int componentID;

			CochleaChannelIntAction(final CochleaChannel chan, final int component) {
				channel = chan;
				componentID = component;
			}

			@Override
			public void actionPerformed(final ActionEvent e) {
				final JTextField tf = (JTextField) e.getSource();
				startEdit();
				try {
					switch (componentID) {
						case 0:
							if (channel != null) {
								channel.setDelayCapConfigADM(Integer.parseInt(tf.getText()));
							}
							else {
								for (final CochleaChannel c : biasgen.cochleaChannels) {
									c.setDelayCapConfigADM(Integer.parseInt(tf.getText()));
								}
							}
							break;
						case 1:
							if (channel != null) {
								channel.setResetCapConfigADM(Integer.parseInt(tf.getText()));
							}
							else {
								for (final CochleaChannel c : biasgen.cochleaChannels) {
									c.setResetCapConfigADM(Integer.parseInt(tf.getText()));
								}
							}
							break;
						case 2:
							if (channel != null) {
								channel.setLnaGainConfig(Integer.parseInt(tf.getText()));
							}
							else {
								for (final CochleaChannel c : biasgen.cochleaChannels) {
									c.setLnaGainConfig(Integer.parseInt(tf.getText()));
								}
							}
							break;
						case 3:
							if (channel != null) {
								channel.setAttenuatorConfig(Integer.parseInt(tf.getText()));
							}
							else {
								for (final CochleaChannel c : biasgen.cochleaChannels) {
									c.setAttenuatorConfig(Integer.parseInt(tf.getText()));
								}
							}
							break;
						case 4:
							if (channel != null) {
								channel.setqTuning(Integer.parseInt(tf.getText()));
							}
							else {
								for (final CochleaChannel c : biasgen.cochleaChannels) {
									c.setqTuning(Integer.parseInt(tf.getText()));
								}
							}
							break;
						default:
							log.warning("Unknown component ID for CochleaChannel GUI.");
							return;
					}

					channel.setFileModified();

					tf.setBackground(Color.white);
				}
				catch (final Exception ex) {
					tf.selectAll();
					tf.setBackground(Color.red);

					log.warning(ex.toString());
				}
				endEdit();
			}
		}

		private class CochleaChannelKeyAction extends KeyAdapter {

			private final CochleaChannel channel;
			private final int componentID;

			CochleaChannelKeyAction(final CochleaChannel chan, final int component) {
				channel = chan;
				componentID = component;
			}

			@Override
			public void keyPressed(final KeyEvent e) {

				boolean up = false, down = false;
				up = (e.getKeyCode() == KeyEvent.VK_UP);
				down = (e.getKeyCode() == KeyEvent.VK_DOWN);
				if (!up && !down) {
					return;
				}
				final int inc = up ? 1 : -1;
				startEdit();
				try {
					switch (componentID) {
						case 0:
							if (channel != null) {
								channel.setDelayCapConfigADM(channel.getDelayCapConfigADM() + inc);
							}
							else {
								for (final CochleaChannel c : biasgen.cochleaChannels) {
									c.setDelayCapConfigADM(c.getDelayCapConfigADM() + inc);
								}
							}
							break;
						case 1:
							if (channel != null) {
								channel.setResetCapConfigADM(channel.getResetCapConfigADM() + inc);
							}
							else {
								for (final CochleaChannel c : biasgen.cochleaChannels) {
									c.setResetCapConfigADM(c.getResetCapConfigADM() + inc);
								}
							}
							break;
						case 2:
							if (channel != null) {
								channel.setLnaGainConfig(channel.getLnaGainConfig() + inc);
							}
							else {
								for (final CochleaChannel c : biasgen.cochleaChannels) {
									c.setLnaGainConfig(c.getLnaGainConfig() + inc);
								}
							}
							break;
						case 3:
							if (channel != null) {
								channel.setAttenuatorConfig(channel.getAttenuatorConfig() + inc);
							}
							else {
								for (final CochleaChannel c : biasgen.cochleaChannels) {
									c.setAttenuatorConfig(c.getAttenuatorConfig() + inc);
								}
							}
							break;
						case 4:
							if (channel != null) {
								channel.setqTuning(channel.getqTuning() + inc);
							}
							else {
								for (final CochleaChannel c : biasgen.cochleaChannels) {
									c.setqTuning(c.getqTuning() + inc);
								}
							}
							break;
						default:
							log.warning("Unknown component ID for CochleaChannel GUI.");
							return;
					}

					if(channel!=null) channel.setFileModified();

				}
				catch (final Exception ex) {
					log.warning(ex.toString());
				}
				endEdit();
			}
		}

		private class CochleaChannelMouseWheelAction extends MouseAdapter {

			private final CochleaChannel channel;
			private final int componentID;

			public CochleaChannelMouseWheelAction(final CochleaChannel channel, final int componentID) {
				this.channel = channel;
				this.componentID = componentID;
			}

			@Override
			public void mouseWheelMoved(final java.awt.event.MouseWheelEvent evt) {
				final int clicks = evt.getWheelRotation();
				// startEdit();
				final long t = System.currentTimeMillis();
				if ((t - lastMouseWheelMovementTime) > minDtMsForWheelEditPost) {
					endEdit();
				}
				lastMouseWheelMovementTime = t;

				final int inc = -clicks;
				if (clicks == 0) {
					return;
				}
				startEdit();
				try {
					switch (componentID) {
						case 0:
							if (channel != null) {
								channel.setDelayCapConfigADM(channel.getDelayCapConfigADM() + inc);
							}
							else {
								for (final CochleaChannel c : biasgen.cochleaChannels) {
									c.setDelayCapConfigADM(c.getDelayCapConfigADM() + inc);
								}
							}
							break;
						case 1:
							if (channel != null) {
								channel.setResetCapConfigADM(channel.getResetCapConfigADM() + inc);
							}
							else {
								for (final CochleaChannel c : biasgen.cochleaChannels) {
									c.setResetCapConfigADM(c.getResetCapConfigADM() + inc);
								}
							}
							break;
						case 2:
							if (channel != null) {
								channel.setLnaGainConfig(channel.getLnaGainConfig() + inc);
							}
							else {
								for (final CochleaChannel c : biasgen.cochleaChannels) {
									c.setLnaGainConfig(c.getLnaGainConfig() + inc);
								}
							}
							break;
						case 3:
							if (channel != null) {
								channel.setAttenuatorConfig(channel.getAttenuatorConfig() + inc);
								break;
							}
							else {
								for (final CochleaChannel c : biasgen.cochleaChannels) {
									c.setAttenuatorConfig(c.getAttenuatorConfig() + inc);
								}
							}
							break;
						case 4:
							if (channel != null) {
								channel.setqTuning(channel.getqTuning() + inc);
							}
							else {
								for (final CochleaChannel c : biasgen.cochleaChannels) {
									c.setqTuning(c.getqTuning() + inc);
								}
							}
							break;
						default:
							log.warning("Unknown component ID for CochleaChannel GUI.");
							return;
					}

					if(channel!=null) channel.setFileModified();

				}
				catch (final Exception ex) {
					log.warning(ex.toString());
				}
				finally {
					endEdit();
				}
			}
		}

		private int oldValue = 0;

		void startEdit() {
			if (edit != null) {
				return;
			}
			if (chan == null) {
				return;
			}
			edit = new MyStateEdit(this, "pot change");
			oldValue = chan.getFullValue();
		}

		String STATE_KEY = "cochlea channel state";

		void endEdit() {
			if (chan == null) {
				return;
			}
			if (oldValue == chan.getFullValue()) {
				return;
			}
			if (edit != null) {
				edit.end();
				editSupport.postEdit(edit);
				edit = null;
			}
		}

		@Override
		public void storeState(final Hashtable<Object, Object> hashtable) {
			// System.out.println(" storeState "+pot);
			if (chan == null) {
				return;
			}
			hashtable.put(STATE_KEY, new Integer(chan.getFullValue()));
		}

		@Override
		public void restoreState(final Hashtable<?, ?> hashtable) {
			// System.out.println("restore state");
			if (hashtable == null) {
				throw new RuntimeException("null hashtable; can't restore state");
			}
			if (hashtable.get(STATE_KEY) == null) {
				log.warning("channel " + chan + " not in hashtable " + hashtable + " with size=" + hashtable.size());
				return;
			}
			final int v = (Integer) hashtable.get(STATE_KEY);
			chan.setFullValue(v);
		}

		class MyStateEdit extends StateEdit {

			/**
			 *
			 */
			private static final long serialVersionUID = -4321012835355063717L;

			public MyStateEdit(final StateEditable o, final String s) {
				super(o, s);
			}

			@Override
			protected void removeRedundantState() {
			}
		}

	}

	/**
	 * This method is called from within the constructor to initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is always
	 * regenerated by the Form Editor.
	 */
	private void initComponents() {
		setToolTipText("Select a tab to configure an aspect of the device.");

		onchipBiasgenPanel = new JPanel();
		onchipBiasgenPanel.setLayout(new BoxLayout(onchipBiasgenPanel, BoxLayout.Y_AXIS));
		onchipBiasgenPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		addTab("On-chip biases (biasgen)", (onchipBiasgenPanel));

		offchipDACPanel = new JPanel();
		offchipDACPanel.setLayout(new BoxLayout(offchipDACPanel, BoxLayout.Y_AXIS));
		addTab("Off-chip biases (DAC)", (offchipDACPanel));

		channelPanel = new JPanel();
		channelPanel.setLayout(new BoxLayout(channelPanel, BoxLayout.X_AXIS));
		addTab("Channels", (channelPanel));

		scannerPanel = new JPanel();
		scannerPanel.setLayout(new BoxLayout(scannerPanel, BoxLayout.Y_AXIS));
		addTab("Scanner Config", (scannerPanel));

		aerPanel = new JPanel();
		aerPanel.setLayout(new BoxLayout(aerPanel, BoxLayout.Y_AXIS));
		addTab("AER Config", (aerPanel));

                adcPanel = new JPanel();
                adcPanel.setLayout(new BoxLayout(adcPanel, BoxLayout.Y_AXIS));
                addTab("ADC", (adcPanel));
		
                chipDiagPanel = new JPanel();
		chipDiagPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		chipDiagPanel.setLayout(new BoxLayout(chipDiagPanel, BoxLayout.Y_AXIS));
		addTab("Chip Diag Config", (chipDiagPanel));

	}

	protected void tabbedPaneMouseClicked(final MouseEvent evt) {
		chip.getPrefs().putInt("CochleaLPControlPanel.bgTabbedPaneSelectedIndex", getSelectedIndex());
	}

	private JPanel onchipBiasgenPanel;
	private JPanel offchipDACPanel;
	private JPanel channelPanel;
	private JPanel scannerPanel;
	private JPanel aerPanel;
	private JPanel adcPanel;
	private JPanel chipDiagPanel;
}
