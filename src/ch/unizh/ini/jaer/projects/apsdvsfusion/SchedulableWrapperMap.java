/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import java.awt.GridBagConstraints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
public class SchedulableWrapperMap extends SchedulableFiringModelMap /*implements
		FiringModelCreator */{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 3135959582771085761L;

	
	public class SchedulableWrapperMapCustomControls extends FiringModelMapCustomControls {
		/**
		 * 
		 */
		private static final long serialVersionUID = -3732732764361496540L;

		GridBagConstraints creatorConstraints;
		
		JPanel creatorPanel = null;
		protected SchedulableWrapperMapCustomControls() {
			super();
		}
		
		protected void fillPanel() {
			JPanel creatorPanel = new JPanel();
            creatorPanel.setLayout(new BoxLayout(creatorPanel, BoxLayout.X_AXIS));
            creatorPanel.setAlignmentX(ParameterBrowserPanel.ALIGNMENT);
            JLabel label = new JLabel("Creator:");
            label.setFont(label.getFont().deriveFont(10f));
			creatorPanel.add(label);

			final ArrayList<Object> creatorList = new ArrayList<Object>();
			final FiringModelType[] firingModelCreatorTypes = FiringModelCreator.FiringModelType.class.getEnumConstants();
			creatorList.addAll(Arrays.asList(firingModelCreatorTypes));
			creatorList.addAll(Arrays.asList(SchedulableFiringModelCreator.FiringModelType.class.getEnumConstants()));

			final JComboBox creatorComboBox = new JComboBox(creatorList.toArray());
			creatorComboBox.setFont(creatorComboBox.getFont().deriveFont(10f));
			creatorPanel.add(creatorComboBox);
			
			creatorComboBox.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					if (creatorComboBox.getSelectedIndex() < firingModelCreatorTypes.length) {
						SchedulableWrapperMap.this.setFiringModelCreator(FiringModelCreator
								.getCreator(
										(FiringModelCreator.FiringModelType) creatorComboBox
												.getSelectedItem(), getPrefs()
												.node("creator")));
					}
					else {
						SchedulableWrapperMap.this.setFiringModelCreator(SchedulableFiringModelCreator
								.getCreator(
										(SchedulableFiringModelCreator.FiringModelType) creatorComboBox
												.getSelectedItem(), getPrefs()
												.node("creator")));
					}
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
				if (getFiringModelCreator() != null) {
					creatorPanel = new ParameterBrowserPanel(getFiringModelCreator());
				}
				else creatorPanel = null;
			}
			else
				creatorPanel = new ParameterBrowserPanel(schedulableFiringModelCreator);
			if (creatorPanel != null) {
				add(creatorPanel, creatorConstraints);
			}
	        JFrame frame = (JFrame) SwingUtilities.getRoot(this);
	        if (frame != null)
	        	frame.pack();
		}
//		public void kernelAdded() {
//			if (panelCounter < inputKernels.size()) {
//				ParameterBrowserPanel newPanel = new ParameterBrowserPanel(inputKernels.get(inputKernels.size()-1)); 
//				add(newPanel, gbc);
//				panels.add(newPanel);
//				gbc.gridy++;
//				panelCounter++;
//		        JFrame frame = (JFrame) SwingUtilities.getRoot(this);
//		        if (frame != null)
//		        	frame.pack();
//			}
//		}
		
		
	}
	
	public class SchedulableWrapperFiringModelCreator extends FiringModelCreator {

		/**
		 * 
		 */
		private static final long serialVersionUID = -3674544035563757031L;

		SchedulableWrapperFiringModelCreator(Preferences parentPrefs, String nodeName) {
			super("SchedulableWrapperFiringModel", parentPrefs, nodeName);
		}


		@Override
		public FiringModel createUnit(int x, int y, FiringModelMap map) {
			if (schedulableFiringModelCreator != null)
				return schedulableFiringModelCreator.createUnit(x, y, SchedulableWrapperMap.this);
			else if (getFiringModelCreator() != null)
				return getFiringModelCreator().createUnit(x, y, map);
			else
				return null;
		}
		
	}
	
	FiringModelMap map = null;
	SchedulableFiringModelCreator schedulableFiringModelCreator = null;
	SchedulableWrapperFiringModelCreator myCreatorProxy = new SchedulableWrapperFiringModelCreator(getPrefs(), "creatorProxy");
	
	/**
	 * @param sizeX
	 * @param sizeY
	 * @param spikeHandler
	 */
	public SchedulableWrapperMap(int sizeX, int sizeY, SpikeHandler spikeHandler, Preferences parentPrefs, String nodeName) {
		super(sizeX, sizeY, spikeHandler, parentPrefs, nodeName);
		// TODO Auto-generated constructor stub
	}
	public SchedulableWrapperMap(int sizeX, int sizeY, SpikeHandler spikeHandler, Preferences prefs) {
		super(sizeX, sizeY, spikeHandler, prefs);
		// TODO Auto-generated constructor stub
	}
	
	public SchedulableFiringModelCreator getFiringModelMapCreator() {
		return schedulableFiringModelCreator;
	}
	
	@Override
	public synchronized void setFiringModelCreator(FiringModelCreator creator) {
		this.schedulableFiringModelCreator = null;
		super.setFiringModelCreator(creator);
		if (map != null) {
			map.buildUnits();
		}
	}
	
	public synchronized void setFiringModelCreator(SchedulableFiringModelCreator creator) {
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

//	/* (non-Javadoc)
//	 * @see ch.unizh.ini.jaer.projects.apsdvsfusion.FiringModelCreator#createUnit(int, int, ch.unizh.ini.jaer.projects.apsdvsfusion.FiringModelMap)
//	 */
//	@Override
//	public FiringModel createUnit(int x, int y, FiringModelMap map) {
//	}

	/* (non-Javadoc)
	 * @see ch.unizh.ini.jaer.projects.apsdvsfusion.SchedulableFiringModelMap#get(int, int)
	 */
	@Override
	public FiringModel get(int x, int y) {
		if (map != null) {
			return map.get(x,y);
		}
		else
			return null;
	}
	
	@Override
	public synchronized void changeSize(int sizeX, int sizeY) {
		if (sizeX != this.sizeX || sizeY != this.sizeY) {
			this.sizeX = sizeX;
			this.sizeY = sizeY;
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
		if (map != null)
			map.reset();
		
	}

	@Override
	public JComponent createCustomControls() {
		if (myControls == null) {
			myControls = new SchedulableWrapperMapCustomControls();
		}
		return myControls;
	}

}
