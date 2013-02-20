/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import java.awt.GridBagConstraints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.prefs.Preferences;

import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import ch.unizh.ini.jaer.projects.apsdvsfusion.FiringModelCreator.FiringModelType;
import ch.unizh.ini.jaer.projects.apsdvsfusion.FiringModelMap.FiringModelMapCustomControls;
import ch.unizh.ini.jaer.projects.apsdvsfusion.gui.ParameterBrowserPanel;

/**
 * @author Dennis
 * 
 */
public class SchedulableWrapperMap extends SchedulableFiringModelMap /*
																	 * implements
																	 * FiringModelCreator
																	 */{

	/**
	 * 
	 */
	private static final long serialVersionUID = 3135959582771085761L;
	final ArrayList<Object> creatorList = new ArrayList<Object>();
	private int firingModelCreatorCounter;

	public class SchedulableWrapperMapCustomControls extends
			FiringModelMapCustomControls {
		/**
		 * 
		 */
		private static final long serialVersionUID = -3732732764361496540L;

		GridBagConstraints creatorConstraints;

		JPanel creatorPanel = null;

		protected SchedulableWrapperMapCustomControls() {
			super();
		}

		protected int getCreatorIndex() {
			// super stupid and slow way to find out which entry in the Combo box should be selected. Any other ideas?
			FiringModelCreator fmc = getFiringModelCreator();
			SchedulableFiringModelCreator sfmc = getFiringModelMapCreator();
			Class<?> c;
			if (sfmc != null) {
				c = sfmc.getClass();
			}
			else {
				if (fmc == null)
					return 0;
				c = fmc.getClass();
			}
			int ret = 1;
			for (FiringModelCreator.FiringModelType type : FiringModelCreator.FiringModelType.values()) {
				if (FiringModelCreator.getCreator(type, getPrefs()).getClass().equals(c))
					return ret;
				ret++;
			}
			for (SchedulableFiringModelCreator.FiringModelType type : SchedulableFiringModelCreator.FiringModelType.values()) {
				if (SchedulableFiringModelCreator.getCreator(type, getPrefs()).getClass().equals(c))
					return ret;
				ret++;
			}
			return 0;
		}
		
		protected void fillPanel() {
			JPanel creatorPanel = new JPanel();
            creatorPanel.setLayout(new BoxLayout(creatorPanel, BoxLayout.X_AXIS));
            creatorPanel.setAlignmentX(ParameterBrowserPanel.ALIGNMENT);
            JLabel label = new JLabel("Creator:");
            label.setFont(label.getFont().deriveFont(10f));
			creatorPanel.add(label);


			final JComboBox creatorComboBox = new JComboBox(creatorList.toArray());
			getSupport().addPropertyChangeListener("firingModelCreator", new PropertyChangeListener() {
				@Override
				public void propertyChange(PropertyChangeEvent evt) {
					int index = 0;
					int i = 0;
					for (Object o : creatorList) {
						if (o.toString().equals(evt.getNewValue().toString()))
							index = i;
						i++;
					}
					if (index != creatorComboBox.getSelectedIndex())
						creatorComboBox.setSelectedIndex(index);
				}
			});
			
			creatorComboBox.setFont(creatorComboBox.getFont().deriveFont(10f));
			creatorPanel.add(creatorComboBox);
			creatorComboBox.setSelectedIndex(getCreatorIndex());
			creatorComboBox.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					setFiringModelCreator(creatorComboBox.getSelectedItem());
//					if (creatorComboBox.getSelectedIndex() == 0) {
//						setFiringModelCreator((FiringModelCreator)null);
//						setFiringModelCreator((SchedulableFiringModelCreator)null);
//					}
//					else if (creatorComboBox.getSelectedIndex() <= firingModelCreatorCounter) {
//						SchedulableWrapperMap.this.setFiringModelCreator(FiringModelCreator
//								.getCreator(
//										(FiringModelCreator.FiringModelType) creatorComboBox
//												.getSelectedItem(), getPrefs()
//												.node("creator")));
//					}
//					else {
//						SchedulableWrapperMap.this.setFiringModelCreator(SchedulableFiringModelCreator
//								.getCreator(
//										(SchedulableFiringModelCreator.FiringModelType) creatorComboBox
//												.getSelectedItem(), getPrefs()
//												.node("creator")));
//					}
//					getSupport().fire
					creatorChanged();
				}
			});
			
			add(creatorPanel,gbc);
			gbc.gridy++;
			
			creatorConstraints = new GridBagConstraints();
			creatorConstraints.weightx = gbc.weightx;
			creatorConstraints.weighty = gbc.weighty;
			creatorConstraints.gridx = gbc.gridx;
			creatorConstraints.gridy = gbc.gridy;
			creatorConstraints.fill = gbc.fill;
			gbc.gridy++;
			
			creatorChanged();
			
			super.fillPanel();
		}

		protected void creatorChanged() {
			if (creatorPanel != null) {
				remove(creatorPanel);
			}
			if (schedulableFiringModelCreator == null) {
				FiringModelCreator firingModelCreator2 = getFiringModelCreator();
				if (firingModelCreator2 != null) {
					firingModelCreator2.setName("Creator parameters");
					creatorPanel = new ParameterBrowserPanel(
							firingModelCreator2, false);
				} else
					creatorPanel = null;
			} else {
				schedulableFiringModelCreator.setName("Creator parameters");
				creatorPanel = new ParameterBrowserPanel(
						schedulableFiringModelCreator, false);
			}
			if (creatorPanel != null) {
				add(creatorPanel, creatorConstraints);
			}
			JFrame frame = (JFrame) SwingUtilities.getRoot(this);
			if (frame != null)
				frame.pack();
		}
		// public void kernelAdded() {
		// if (panelCounter < inputKernels.size()) {
		// ParameterBrowserPanel newPanel = new
		// ParameterBrowserPanel(inputKernels.get(inputKernels.size()-1));
		// add(newPanel, gbc);
		// panels.add(newPanel);
		// gbc.gridy++;
		// panelCounter++;
		// JFrame frame = (JFrame) SwingUtilities.getRoot(this);
		// if (frame != null)
		// frame.pack();
		// }
		// }

	}

	public class SchedulableWrapperFiringModelCreator extends
			FiringModelCreator {

		/**
		 * 
		 */
		private static final long serialVersionUID = -3674544035563757031L;

		SchedulableWrapperFiringModelCreator(Preferences parentPrefs,
				String nodeName) {
			super("SchedulableWrapperFiringModel", parentPrefs, nodeName);
			// final ArrayList<Object> creatorList = new ArrayList<Object>();

		}

		@Override
		public FiringModel createUnit(int x, int y, FiringModelMap map) {
			if (schedulableFiringModelCreator != null)
				return schedulableFiringModelCreator.createUnit(x, y,
						SchedulableWrapperMap.this);
			else if (getFiringModelCreator() != null)
				return getFiringModelCreator().createUnit(x, y, map);
			else
				return null;
		}

	}

	FiringModelMap map = null;
	SchedulableFiringModelCreator schedulableFiringModelCreator = null;
	SchedulableWrapperFiringModelCreator myCreatorProxy = new SchedulableWrapperFiringModelCreator(
			getPrefs(), "creatorProxy");

//	/**
//	 * @param sizeX
//	 * @param sizeY
//	 * @param signalHandler
//	 */
//	@Deprecated
//	public SchedulableWrapperMap(int sizeX, int sizeY,
//			SignalHandler signalHandler, Preferences parentPrefs,
//			String nodeName) {
//		super(sizeX, sizeY, signalHandler, parentPrefs, nodeName);
//		init();
//	}

	public SchedulableWrapperMap(int sizeX, int sizeY,
			SignalHandler signalHandler, FiringModelMap internalMap,
			Preferences prefs) {
		super(sizeX, sizeY, signalHandler, prefs);
		setFiringModelMap(internalMap);
		init();
		if (internalMap != null)
			internalMap.setSignalHandlerSet(getSignalHandler());
	}

	public SchedulableWrapperMap(int sizeX, int sizeY,
			SignalHandler signalHandler, Preferences prefs) {
		this(sizeX, sizeY, signalHandler, new ArrayFiringModelMap(sizeX, sizeY,
				null, prefs.node("internalMap")), prefs);

	}

	public SchedulableFiringModelCreator getFiringModelMapCreator() {
		return schedulableFiringModelCreator;
	}

	public void setFiringModelCreator(Object descriptor) {
		getSupport().firePropertyChange("firingModelCreator", "unknown", descriptor.toString());
		if (descriptor == null || (descriptor.equals("none") || descriptor.equals("null"))) {
			setFiringModelCreator((FiringModelCreator) null);
			setFiringModelCreator((SchedulableFiringModelCreator) null);
		} else if (descriptor instanceof FiringModelCreator.FiringModelType) {
			setFiringModelCreator(FiringModelCreator.getCreator(
					(FiringModelCreator.FiringModelType) descriptor, 
					getPrefs().node("creator")));
		} else if (descriptor instanceof SchedulableFiringModelCreator.FiringModelType) {
			setFiringModelCreator(SchedulableFiringModelCreator.getCreator(
					(SchedulableFiringModelCreator.FiringModelType) descriptor,
					getPrefs().node("creator")));

		} else if (descriptor instanceof String) {
			for (Object o : creatorList) {
				if (o.toString().equals(descriptor)) {
					setFiringModelCreator(o);
					break;
				}
			}
		}
		if (descriptor != null)
			getPrefs().put("creator-type", descriptor.toString());
		else 
			getPrefs().put("creator-type", "none");
	}

	private void init() {
		final FiringModelType[] firingModelCreatorTypes = FiringModelCreator.FiringModelType.class
				.getEnumConstants();
		creatorList.add("none");
		creatorList.addAll(Arrays.asList(firingModelCreatorTypes));
		creatorList.addAll(Arrays
				.asList(SchedulableFiringModelCreator.FiringModelType.class
						.getEnumConstants()));
		firingModelCreatorCounter = firingModelCreatorTypes.length;
	}

	@Override
	public synchronized void setFiringModelCreator(FiringModelCreator creator) {
		this.schedulableFiringModelCreator = null;
		super.setFiringModelCreator(creator);
		if (map != null) {
			map.buildUnits();
		}
	}

	public synchronized void setFiringModelCreator(
			SchedulableFiringModelCreator creator) {
		this.schedulableFiringModelCreator = creator;
		super.setFiringModelCreator(null);
		if (map != null) {
			map.buildUnits();
		}
	}

	public void setFiringModelMap(FiringModelMap map) {
		this.map = map;
		if (map != null) {
			map.setFiringModelCreator(myCreatorProxy);
			map.changeSize(sizeX, sizeY);
			map.buildUnits();
		}
	}

	// /* (non-Javadoc)
	// * @see
	// ch.unizh.ini.jaer.projects.apsdvsfusion.FiringModelCreator#createUnit(int,
	// int, ch.unizh.ini.jaer.projects.apsdvsfusion.FiringModelMap)
	// */
	// @Override
	// public FiringModel createUnit(int x, int y, FiringModelMap map) {
	// }

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * ch.unizh.ini.jaer.projects.apsdvsfusion.SchedulableFiringModelMap#get
	 * (int, int)
	 */
	@Override
	public FiringModel get(int x, int y) {
		if (map != null && enabled) {
			return map.get(x, y);
		} else
			return null;
	}

	@Override
	public synchronized void changeSize(int sizeX, int sizeY) {
		if (sizeX != this.sizeX || sizeY != this.sizeY) {
			clearHeap();
			super.changeSize(sizeX, sizeY);
//			this.sizeX = sizeX;
//			this.sizeY = sizeY;
			if (map != null)
				map.changeSize(sizeX, sizeY);
		}
	}

	@Override
	public void buildUnits() {
		if (map != null)
			map.buildUnits();
	}

	@Override
	public void reset() {
		clearHeap();
		if (map != null)
			map.reset();

	}

	@Override
	public void restoreParameters() {
		super.restoreParameters();
		setFiringModelCreator(getPrefs().get("creator-type", "none"));
	}

	@Override
	public JComponent createCustomControls() {
		if (myControls == null) {
			myControls = new SchedulableWrapperMapCustomControls();
		}
		return myControls;
	}

}
