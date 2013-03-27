/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Random;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import ch.unizh.ini.jaer.projects.apsdvsfusion.ParameterContainer.SingleParameter;
import ch.unizh.ini.jaer.projects.apsdvsfusion.gui.CollapsablePanel;
import ch.unizh.ini.jaer.projects.apsdvsfusion.gui.ParameterBrowserPanel;

/**
 * @author Dennis Goehlsdorf
 *
 */
public abstract class FiringModelMap extends ParameterContainer {
	/**
	 * 
	 */
	private static final long serialVersionUID = 3772350769713414996L;
	
	private static Random mapIDSelector = new Random();
	
	private int mapID = -1;
	
	int sizeX = -1, sizeY = -1;
	FiringModelCreator firingModelCreator = null;
	SignalHandlerSet signalHandlerSet;
	ArrayList<SignalTransformationKernel> inputKernels = new ArrayList<SignalTransformationKernel>();
	private ArrayList<Integer> indexMappings = new ArrayList<Integer>();
	private int indexPosition = getPrefs().getInt("indexPosition", 0);
	
	private PropertyChangeListener creatorChangeListener = new PropertyChangeListener() {
		@Override
		public void propertyChange(PropertyChangeEvent e) {
			synchronized (SpatioTemporalFusion.getFilteringLock(FiringModelMap.this)) {
				buildUnits();
			}
		}
	};
	
	boolean monitored = false;
	boolean filterOutput = false;
	boolean enabled = true;
	
	public class FiringModelMapCustomControls extends JPanel {
		/**
		 * 
		 */
		private static final long serialVersionUID = 3283137316302825455L;
		GridBagConstraints gbc = new GridBagConstraints();
		GridBagConstraints gbcKernel = new GridBagConstraints();
		ArrayList<ParameterBrowserPanel> panels = new ArrayList<ParameterBrowserPanel>();
		int panelCounter = 0;
		JPanel kernelPanel;
		protected FiringModelMapCustomControls() {
			this.setLayout(new GridBagLayout());
			gbc.weightx = 1;
			gbc.weighty = 1;
			gbc.gridy = 0;
			gbc.gridx = 0;
			gbc.fill = GridBagConstraints.BOTH;
			gbcKernel.weightx = 1;
			gbcKernel.weighty = 1;
			gbcKernel.gridy = 0;
			gbcKernel.gridx = 0;
			gbcKernel.fill = GridBagConstraints.BOTH;
			fillPanel();
		}
		
		protected void fillPanel() {
//			removeAll();
			JPanel dummyPanel = new JPanel();
			dummyPanel.setLayout(new BorderLayout());
			dummyPanel.add(new JLabel("   "), BorderLayout.WEST);
			kernelPanel = new JPanel();
			dummyPanel.add(kernelPanel,BorderLayout.CENTER);
			kernelPanel.setLayout(new GridBagLayout());
			CollapsablePanel collapsablePanel = new CollapsablePanel("Input kernels",dummyPanel);
            for (SignalTransformationKernel stk : inputKernels) {
				ParameterBrowserPanel newPanel = new ParameterBrowserPanel(stk); 
				kernelPanel.add(newPanel, gbcKernel);
				panels.add(newPanel);
				gbcKernel.gridy++;
				panelCounter++;
			}
            collapsablePanel.toggleSelection();
            add(collapsablePanel, gbc);
            gbc.gridy++;
	        JFrame frame = (JFrame) SwingUtilities.getRoot(this);
	        if (frame != null)
	        	frame.pack();
		}
		public void kernelAdded() {
			if (panelCounter < inputKernels.size()) {
				ParameterBrowserPanel newPanel = new ParameterBrowserPanel(inputKernels.get(inputKernels.size()-1));
				newPanel.toggleSelection();
				kernelPanel.add(newPanel, gbcKernel);
				panels.add(newPanel);
				gbcKernel.gridy++;
				panelCounter++;
		        JFrame frame = (JFrame) SwingUtilities.getRoot(this);
		        if (frame != null)
		        	frame.pack();
			}
		}
		public void kernelRemoved(int index) {
			if (index >= 0 && index < panelCounter) {
				ParameterBrowserPanel panel = panels.get(index);
				panels.remove(index);
				kernelPanel.remove(panel);
				panelCounter--;
		        JFrame frame = (JFrame) SwingUtilities.getRoot(this);
		        if (frame != null)
		        	frame.pack();
			}
		}
		
		
	}
//	public class FiringModelMapParameterContainer extends ParameterContainer {
//		/**
//		 * 
//		 */
//		private static final long serialVersionUID = 1749152638935077965L;
//
//		public FiringModelMapParameterContainer(String name) {
//			super(name);
//		}
//
//		public void setSizeX
//		
//	}
//	
//	private FiringModelMapParameterContainer myParameterContainer = createParameterContainer();
	
	

	
	public FiringModelMap(int sizeX, int sizeY, Preferences prefs) {
		super("FiringModelMap", prefs);
		setMapID(Math.abs(mapIDSelector.nextInt()));
		addExcludedProperty("mapID");
		this.signalHandlerSet = new SignalHandlerSet()/*spikeHandler*/;
		changeSize(sizeX, sizeY);
	}

//	@Deprecated
//	public FiringModelMap(int sizeX, int sizeY, Preferences parentPrefs, String nodeName) {
//		super("FiringModelMap", parentPrefs, nodeName);
//		setMapID(Math.abs(mapIDSelector.nextInt()));
//		addExcludedProperty("mapID");
//		this.signalHandlerSet = new SignalHandlerSet()/*spikeHandler*/;
//		changeSize(sizeX, sizeY);
//	}
	
	public FiringModelMap(int sizeX, int sizeY, SignalHandler spikeHandler, Preferences prefs) {
		this(sizeX, sizeY, prefs);
		if (spikeHandler != null)
			this.signalHandlerSet.addSpikeHandler(spikeHandler);
	}

//	@Deprecated
//	public FiringModelMap(int sizeX, int sizeY, SignalHandler spikeHandler, Preferences parentPrefs, String nodeName) {
//		this(sizeX, sizeY, parentPrefs, nodeName);
//		if (spikeHandler != null)
//			this.signalHandlerSet.addSpikeHandler(spikeHandler);
//	}

	public int getMapID() {
		return mapID;
	}
	
	public void setMapID(int mapID) {
		getSupport().firePropertyChange("mapID", this.mapID, mapID);
		this.mapID = mapID;
	}
//	protected FiringModelMapParameterContainer createParameterContainer() {
//		return new FiringModelMapParameterContainer("FiringModelMap");
//	}
	
	
	public boolean isMonitored() {
		return monitored;
	}

	public void setMonitored(boolean monitored) {
		if (monitored != this.monitored) {
			getSupport().firePropertyChange("monitored", this.monitored, monitored);
			this.monitored = monitored;
			if (this.monitored) {
				SpatioTemporalFusion.getInstance(this).addViewerFor(this);
			}
			else
				SpatioTemporalFusion.getInstance(this).removeViewerFor(this);
		}
	}

	
	
	public boolean isFilterOutput() {
		return filterOutput;
	}

	public void setFilterOutput(boolean filterOutput) {
		if (filterOutput != this.filterOutput) {
			getSupport().firePropertyChange("filterOutput", this.filterOutput, filterOutput);
			this.filterOutput = filterOutput;
			SpatioTemporalFusion.getInstance(this).setMapObserved(this, filterOutput);
		}
	}

	public void removeKernel(SignalTransformationKernel kernel) {
		if (inputKernels.contains(kernel)) {
			int position = inputKernels.indexOf(kernel);
			inputKernels.remove(kernel);
			indexMappings.remove(position);
			kernel.disconnectKernel();
			getPrefs().putInt("kernelCount",inputKernels.size());
			for (int i = 0; i < indexMappings.size(); i++) {
				getPrefs().putInt("indexMappings"+(i), indexMappings.get(i));
			}
			if (myControls != null) 
				myControls.kernelRemoved(position);
		}
	}
	
	public void disconnectMap() {
		ArrayList<SignalTransformationKernel> inputKernels = new ArrayList<SignalTransformationKernel>(this.inputKernels);
		for (SignalTransformationKernel kernel : inputKernels) {
			removeKernel(kernel);
		}
		try {
			getPrefs().removeNode();
		} catch (BackingStoreException e) {
			e.printStackTrace();
		} catch (IllegalStateException e) {
			e.printStackTrace();
		}
	}
	
	public void doDelete() {
		SpatioTemporalFusion stf = SpatioTemporalFusion.getInstance(this);
		if (stf != null) {
			stf.removeMap(this);
		}
	}
	
	
	public FiringModelCreator getFiringModelCreator() {
		return firingModelCreator;
	}

	public void setFiringModelCreator(FiringModelCreator firingModelCreator) {
		if (firingModelCreator != this.firingModelCreator) {
			if (this.firingModelCreator != null)
				this.firingModelCreator.getSupport().removePropertyChangeListener(creatorChangeListener);
			this.firingModelCreator = firingModelCreator;
			if (this.firingModelCreator != null)
				this.firingModelCreator.getSupport().addPropertyChangeListener(creatorChangeListener);
			buildUnits();
		}
	}
	
	public abstract void buildUnits(); 

//	public void doBuild_Units() {
//		buildUnits();
//	}
	
	public SignalHandlerSet getSignalHandler() {
		return signalHandlerSet;
	}

	protected void setSignalHandlerSet(SignalHandlerSet signalHandlerSet) {
		this.signalHandlerSet = signalHandlerSet;
		// rebuild all units to make sure the units point to the correct signal handler!
		buildUnits();
	}
	
	public void addSignalHandler(SignalHandler signalHandler) {
		this.signalHandlerSet.addSpikeHandler(signalHandler);
	}

	public void removeSignalHandler(SignalHandler signalHandler) {
		this.signalHandlerSet.removeSpikeHandler(signalHandler);
	}
	
	
	public int getSizeX() {
		return sizeX;
	}
	public int getSizeY() {
		return sizeY;
	}

	
	//	public int getOffsetX();
//	public int getOffsetY();
	
	/**
	 * @param sizeX the sizeX to set
	 */
	public void setSizeX(int sizeX) {
//		getSupport().firePropertyChange("sizeX", this.sizeX, sizeX);
		changeSize(sizeX, sizeY);
	}

	/**
	 * @param sizeY the sizeY to set
	 */
	public void setSizeY(int sizeY) {
		changeSize(sizeX, sizeY);
	}

	public  void changeSize(int sizeX, int sizeY) {
		synchronized (SpatioTemporalFusion.getFilteringLock(this)) {
			if (sizeX != this.sizeX || sizeY != this.sizeY) {
	//			int ox = this.sizeX, oy = this.sizeY;
				int beforeX = this.sizeX;
				int beforeY = this.sizeY;
				this.sizeX = sizeX;
				this.sizeY = sizeY;
				buildUnits();
				if (beforeX != sizeX)
					getSupport().firePropertyChange("sizeX", beforeX, this.sizeX);
				if (beforeY != sizeY)
					getSupport().firePropertyChange("sizeY", beforeY, this.sizeY);
				for (SignalTransformationKernel kernel : inputKernels) {
					kernel.setOutputSize(sizeX, sizeY);
				}
			}
		}
	}

	FiringModelMapCustomControls myControls = null;
	@Override
	public JComponent createCustomControls() {
		if (myControls == null) {
			myControls = new FiringModelMapCustomControls();
		}
		return myControls;
	}
	
	public abstract FiringModel get(int x, int y);
	
	public void signalAt(int x, int y, double value, int timeInUs) {
		FiringModel model = get(x, y);
		if (model != null)
			model.receiveSpike(value, timeInUs);
	}
	
	public void reset() {
		for (SignalTransformationKernel kernel : inputKernels) 
			kernel.reset();
	}
	
	private void addKernel(SignalTransformationKernel newKernel, int nodeIndex) {
		if (newKernel != null) {
			newKernel.setOutputMap(this);
			inputKernels.add(newKernel);
			newKernel.setPreferences(getPrefs().node("inputKernel"+nodeIndex));
			indexMappings.add(nodeIndex);
			
			indexPosition = nodeIndex+1;
			for (Integer index : indexMappings) 
				if (index >= indexPosition)
					indexPosition = index + 1;
			
			if (myControls != null) {
				myControls.kernelAdded();
			}
			getPrefs().putInt("kernelCount",inputKernels.size());
			getPrefs().putInt("indexPosition", indexPosition);
			getPrefs().putInt("indexMappings"+(indexMappings.size()-1), indexMappings.get(indexMappings.size()-1));
		}
	}
	
	public void doAdd_Kernel() {
		SpaceableExpressionBasedSpatialIK newKernel = 
				new SpaceableExpressionBasedSpatialIK(7, 7, getPrefs().node("inputKernel"+indexPosition));
		newKernel.setName("kernel"+newKernel.getKernelID());
		addKernel(newKernel, indexPosition);
	}


    public void restoreKernels() {
    	int kernelCount = getPrefs().getInt("kernelCount",0);
    	for (int i = 0; i < kernelCount; i++) {
    		int mapping = getPrefs().getInt("indexMappings"+(indexMappings.size()), i);
    		SpaceableExpressionBasedSpatialIK newKernel = 
    				new SpaceableExpressionBasedSpatialIK(7, 7, getPrefs().node("inputKernel"+mapping));
    		addKernel(newKernel,mapping);
		}
    	for (SignalTransformationKernel kernel : inputKernels) {
    		kernel.restoreParameters();
    	}
    }

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		getSupport().firePropertyChange("enabled", this.enabled, enabled);
		this.enabled = enabled;
	}
	
    
}
