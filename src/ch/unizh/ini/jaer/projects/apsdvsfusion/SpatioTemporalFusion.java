/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.ArrayList;
import java.util.prefs.Preferences;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.PolarityEvent.Polarity;
import net.sf.jaer.eventprocessing.EventFilter2D;
import ch.unizh.ini.jaer.projects.apsdvsfusion.gui.MapOutputViewer;
import ch.unizh.ini.jaer.projects.apsdvsfusion.gui.ParameterBrowserPanel;
import ch.unizh.ini.jaer.projects.apsdvsfusion.gui.SpikingOutputViewerManager;
//import ch.unizh.ini.jaer.projects.apsdvsfusion.SpikingOutputDisplay.SingleOutputViewer;

/**
 * Filter class that uses a defined kernel function to compute not spatially filtered output spikes.
 * 
 * @author Dennis Goehlsdorf
 *
 */
public class SpatioTemporalFusion extends EventFilter2D { //implements ActionListener {

	static ArrayList<SpatioTemporalFusion> runningInstances = new ArrayList<SpatioTemporalFusion>();
	
	static SpatioTemporalFusion getInstance(SignalTransformationKernel requestingKernel) {
		if (runningInstances.size() == 0)
			return null;
		else {
			return runningInstances.get(0);
		}
	}
	
	static SpatioTemporalFusion getInstance(FiringModelMap requestingMap) {
		if (runningInstances.size() == 0)
			return null;
		else {
			return runningInstances.get(0);
		}
	}
	
	ArrayList<String> usedExpressionStrings = new ArrayList<String>();
	
//	InputKernel inputKernel;
//	FiringModelMap firingModelMap;
//	
//	String expression = getPrefs().get("Expression", "1");
	FiringModelMap onMap, offMap;
	ArrayList<FiringModelMap> firingModelMaps = new ArrayList<FiringModelMap>();
	
	Object filteringLock = new Object();
	
	public final class STFParameterContainer extends ParameterContainer {
//		String myString;
//		float myFloat;
//		int myInt;
		JPanel customPanel = new JPanel();
		GridBagConstraints gbc = new GridBagConstraints();
		ArrayList<ParameterBrowserPanel> mapPanels = new ArrayList<ParameterBrowserPanel>(); 
		int panelCounter = 0;

//		@Deprecated
//		public STFParameterContainer(Preferences parentPrefs, String nodeName) {
//			super("Maps", parentPrefs.node(nodeName));
//			customPanel.setLayout(new GridBagLayout());
////			customPanel.setLayout(new BoxLayout(customPanel, BoxLayout.Y_AXIS));
//		}

		public STFParameterContainer(Preferences prefs) {
			super("Maps", prefs);
			customPanel.setLayout(new GridBagLayout());
//			customPanel.setLayout(new BoxLayout(customPanel, BoxLayout.Y_AXIS));
		}

		public void fillPanel() {
			customPanel.removeAll();
			
			gbc.weightx = 1;
			gbc.weighty = 1;
			gbc.gridy = 0;
			gbc.gridx = 0;
			gbc.fill = GridBagConstraints.BOTH;
			for (FiringModelMap map : firingModelMaps) {
				if (map != onMap && map != offMap) {
					ParameterBrowserPanel newMapPanel = new ParameterBrowserPanel(map); 
					customPanel.add(newMapPanel, gbc);
					mapPanels.add(newMapPanel);
					gbc.gridy++;
					panelCounter++;
				}
			}
	        JFrame frame = (JFrame) SwingUtilities.getRoot(customPanel);
	        if (frame != null)
	        	frame.pack();
		}
		
		public void mapAdded() {
			if (panelCounter < firingModelMaps.size()) {
				ParameterBrowserPanel newMapPanel = new ParameterBrowserPanel(firingModelMaps.get(firingModelMaps.size()-1));
				newMapPanel.toggleSelection();
				customPanel.add(newMapPanel, gbc);
				mapPanels.add(newMapPanel);
				gbc.gridy++;
				panelCounter++;
		        JFrame frame = (JFrame) SwingUtilities.getRoot(customPanel);
		        if (frame != null)
		        	frame.pack();
			}
		}
		@Override
		public JComponent createCustomControls() {
			fillPanel();
			return customPanel;
		}
	}

	ArrayList<KernelProcessor> kernelProcessors = new ArrayList<KernelProcessor>();
	
//	SpikingOutputDisplay spikingOutputDisplay = null;// = new SpikingOutputDisplay();
	
//	boolean kernelEditorActive = false;
//	ExpressionKernelEditor kernelEditor = null;// = new ExpressionKernelEditor(this);
	
	MapOutputViewer mapOutputViewer = null;
	SpikingOutputViewerManager spikingOutputViewerManager = null;
//	private int grayLevels = 4;
	
	private boolean panelAdded = false;
	private STFParameterContainer stfParameterContainer = new STFParameterContainer(getPrefs().node("spatioTemporalFusion"));

	boolean filterEvents = false;
	SignalHandler filterSpikeHandler = new SignalHandler() {
		public void signalAt(int x, int y, int time, double value) {
			PolarityEvent pe = (PolarityEvent)out.getOutputIterator().nextOutput();
			pe.setX((short)x);
			pe.setY((short)y);
			pe.setSpecial(false);
			if (value < 0)
				pe.setPolarity(Polarity.Off);
			else
				pe.setPolarity(Polarity.On);
			pe.setTimestamp(time);
		}

//		@Override
		public void reset() {
		}

	};
	
	/**
	 * @param chip
	 */
	public SpatioTemporalFusion(AEChip chip) {
		super(chip);
		runningInstances.add(this);
		spikingOutputViewerManager = new SpikingOutputViewerManager();
   		mapOutputViewer = new MapOutputViewer(this, spikingOutputViewerManager);
		
   		
//		this.setFilterEnabled(false);
        setPropertyTooltip("grayLevels", "Number of displayed gray levels");
        this.onMap = new FiringModelMap(128,128, getPrefs() , "onMap") {
			@Override
			public void reset() {
			}
			@Override
			public FiringModel get(int x, int y) {
				return null;
			}
			@Override
			public void buildUnits() {
			}
		};
		this.offMap = new FiringModelMap(128,128, getPrefs(), "offMap") {
			@Override
			public void buildUnits() {
			}
			@Override
			public FiringModel get(int x, int y) {
				return null;
			}
			@Override
			public void reset() {
			}
		};
		onMap.setName("On input map");
		offMap.setName("Off input map");
		onMap.setMapID(0);
		offMap.setMapID(1);
		firingModelMaps.add(onMap);
		firingModelMaps.add(offMap);
		stfParameterContainer.restoreParameters();
		loadSettings();
//		firingModelMap = new ArrayFiringModelMap(chip, IntegrateAndFire.getCreator());
//		inputKernel = new ExpressionBasedSpatialInputKernel(5, 5);
//		kernelProcessors 
		//AEViewer viewer = new AEViewer(null, "ch.unizh.ini.jaer.projects.apsdvsfusion.FusedInputSimulatedChip");
		//this.simchip = new FusedInputSimulatedChip();

		//viewer.setChip(simchip);
		
		//viewer.setVisible(true);
	}

	public Object getFilteringLock() {
		return filteringLock;
	}
	
	public void addExpressionString(String s) {
		ArrayList<String> oldList = new ArrayList<String>(usedExpressionStrings);
		if (usedExpressionStrings.contains(s)) {
			usedExpressionStrings.remove(s);
		}
		usedExpressionStrings.add(0, s);
		while (usedExpressionStrings.size() > 20) {
			usedExpressionStrings.remove(usedExpressionStrings.size()-1);
		}
		for (int i = 0; i < usedExpressionStrings.size(); i++) {
			getPrefs().put("usedExpressionString"+i, usedExpressionStrings.get(i));
		}
		getSupport().firePropertyChange("usedExpressionStrings", oldList, usedExpressionStrings);
	}
	
	public ArrayList<String> getUsedExpressionStrings() {
		return usedExpressionStrings;
	}
	
    @Override
    synchronized public void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if (yes) {
//        	if (kernelEditor == null)
//        		kernelEditor = new ExpressionKernelEditor(this);
//        	kernelEditor.setVisible(true);
//        	if (spikingOutputDisplay == null) 
//        		 spikingOutputDisplay = new SpikingOutputDisplay();
//        	spikingOutputDisplay.setVisible(true);
        	if (spikingOutputViewerManager == null) 
        		spikingOutputViewerManager = new SpikingOutputViewerManager();
       		spikingOutputViewerManager.run();
        	if (mapOutputViewer == null)
        		mapOutputViewer = new MapOutputViewer(this, spikingOutputViewerManager);
//       		expressionBasedIKUserInterface.setVisible(true);
        } else {
//        	if (kernelEditor != null)
//        		kernelEditor.setVisible(false);
//        	if (spikingOutputDisplay != null)
//        		spikingOutputDisplay.setVisible(false);
//            out = null; // garbage collect
        	if (spikingOutputViewerManager != null)
        		spikingOutputViewerManager.kill();
        	if (mapOutputViewer != null) {
        		mapOutputViewer.setVisible(false);
        		mapOutputViewer.savePrefs();
        	}
        }
    }
   
	
    public int getGrayLevels() {
    	if (mapOutputViewer != null) {
    		return mapOutputViewer.getGrayLevels();
    	}
    	else return getPrefs().getInt("SpatioTemporalFusion.UserInterface.grayLevels",4);
    }
    public void setGrayLevels(int grayLevels) {
    	if (mapOutputViewer != null) {
    		mapOutputViewer.setGrayLevels(grayLevels);
    	}
    	else getPrefs().putInt("SpatioTemporalFusion.UserInterface.grayLevels",grayLevels);
    }
    
	public boolean isFilterEvents() {
		return filterEvents;
	}

	public void setFilterEvents(boolean filterEvents) {
		if (filterEvents != this.filterEvents) {
			this.filterEvents = filterEvents;
			for (KernelProcessor kp : kernelProcessors) {
				if (kp instanceof SimpleKernelProcessor) {
					if (filterEvents)
						((SimpleKernelProcessor)kp).addSpikeHandler(filterSpikeHandler);
					else 
						((SimpleKernelProcessor)kp).removeSpikeHandler(filterSpikeHandler);
				}
			}
		}
	}
	
	public void addViewerFor(FiringModelMap map) {
		mapOutputViewer.addViewerFor(map);
	}
	
	public void removeViewerFor(FiringModelMap map) {
		mapOutputViewer.removeViewerFor(map);
	}

	int time = 0;
	/* (non-Javadoc)
	 * @see net.sf.jaer.eventprocessing.EventFilter2D#filterPacket(net.sf.jaer.event.EventPacket)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public EventPacket<?> filterPacket(EventPacket<?> in) {
		synchronized (filteringLock) {
	        if (!filterEnabled) {
	            return in;
	        }
	        if (enclosedFilter != null) {
	            in = enclosedFilter.filterPacket(in);
	        }
	//        getPrefs()
			checkOutputPacketEventType(in);
	//        OutputEventIterator<?> oi=out.outputIterator();
	//        firingModelMap.changeSize(chip.getSizeX(), chip.getSizeY());
	 //       PolarityEvent e;
	        out.setEventClass(PolarityEvent.class);
	//        int beforePackageTime = time;
	        int maxTime = Integer.MIN_VALUE;
			for (BasicEvent be : in) {
				if (be.getTimestamp() > maxTime) {
					maxTime = be.getTimestamp();
				}
				if (be instanceof PolarityEvent) {
					if (((PolarityEvent)be).getPolarity() == Polarity.On)
						onMap.getSignalHandler().signalAt(be.x, be.y, be.timestamp, 1.0);
					else
						offMap.getSignalHandler().signalAt(be.x, be.y, be.timestamp, 1.0);
	//				synchronized (kernelProcessors) {
	//					for (KernelProcessor kp : kernelProcessors) {
	////						if (be.timestamp < time)
	////							System.out.println(time + " -> " + be.timestamp);
	////						if (be.timestamp < beforePackageTime) 
	////							System.out.println("time decreased from last package: "+beforePackageTime+" -> "+be.timestamp);
	//						
	////							kp.signalAt(be.x,be.y,be.timestamp, -1.0);
	//							
	//						time = be.timestamp;
	//					}
	//				}
	//				inputKernel.apply(be.x, be.y, be.timestamp, ((PolarityEvent)be).polarity, firingModelMap, spikeHandler);
				}
			}
	//		if (expressionBasedIKUserInterface != null)
	//			expressionBasedIKUserInterface.processUntil(maxTime);
			for (FiringModelMap map : firingModelMaps) {
				if (map instanceof SchedulableFiringModelMap)
					((SchedulableFiringModelMap)map).processScheduledEvents(maxTime);
			}
			if (filterEvents)
				return out;
			else 
				return in;
		}
	}

	
	
//	public void doShow_KernelEditor() {
//		kernelEditorActive ^= true;
//		if (kernelEditor != null)
//			kernelEditor.setVisible(kernelEditorActive);
//		if (spikingOutputDisplay != null)
//			spikingOutputDisplay.setVisible(kernelEditorActive);
////		if (kernelEditorActive)
////			spikingOutputDisplay.runDisplays();
////		else
////			spikingOutputDisplay.setVisible(false);
//	}
	
	
	public void setMapObserved(FiringModelMap map, boolean observed) {
		if (observed) {
			if (!map.getSignalHandler().contains(filterSpikeHandler))
				map.addSignalHandler(filterSpikeHandler);
		}
		else {
			map.removeSignalHandler(filterSpikeHandler);
		}
	}

	
	public void doClear() {
		// setExpression(expression);
		if (mapOutputViewer != null)
			mapOutputViewer.reset();
		if (spikingOutputViewerManager != null) {
			spikingOutputViewerManager.reset();
		}
//		if (spikingOutputDisplay != null)
//			spikingOutputDisplay.reset();
		synchronized (kernelProcessors) {
			if (kernelProcessors != null)
				kernelProcessors.clear();
		}
	}
	
	/* (non-Javadoc)
	 * @see net.sf.jaer.eventprocessing.EventFilter#initFilter()
	 */
	
	public void doShowOutputViewer() {
		if (mapOutputViewer != null)
			mapOutputViewer.setVisible(!mapOutputViewer.isVisible());
	}
	
//	@Override
	public void doShowParameterPanel() {
		if (!panelAdded) {
			panelAdded = true;
			ParameterBrowserPanel controls = new ParameterBrowserPanel(stfParameterContainer, false);
			controls.toggleSelection();
			addControls(controls);
		}
		
//		setExpression("0");
	}
	
	public void doAddMap() {
		synchronized (firingModelMaps) {
			SchedulableWrapperMap newMap = new SchedulableWrapperMap(128, 128, null, getPrefs().node("map"+(firingModelMaps.size() - 2)));
			newMap.setName("Map "+(firingModelMaps.size() - 1));
			ArrayList<FiringModelMap> oldMaps = new ArrayList<FiringModelMap>(firingModelMaps);
			firingModelMaps.add(newMap);
			stfParameterContainer.mapAdded();
			getPrefs().putInt("mapCount", firingModelMaps.size()-2);
			getSupport().firePropertyChange("firingModelMaps", oldMaps, firingModelMaps);
		}
	}
	
	
	public void loadSettings() {
		ParameterContainer.disableStorage();
		for (int i = 0; i < 20; i++) {
			String s = getPrefs().get("usedExpressionString"+i, "");
			if (!s.equals("")) {
				addExpressionString(s);
			}
		}
		int mapCount = getPrefs().getInt("mapCount", 0);
		for (int i = 0; i < mapCount; i++) {
			doAddMap();
		}
		// operate in 2 steps to make sure the map IDs have been restored before initializing the kernels.
		for (FiringModelMap map : firingModelMaps) {
			if (map  != onMap && map != offMap)
				map.restoreParameters();
		}
		for (FiringModelMap map : firingModelMaps) {
			if (map  != onMap && map != offMap)
				map.restoreKernels();
		}
		ParameterContainer.enableStorage();
	}


//	public void setExpression(String expression) {
//		ExpressionBasedSpatialInputKernel ebsIK = (ExpressionBasedSpatialInputKernel)inputKernel;
//		ebsIK.setExpressionString(expression);
//		try {
//			ebsIK.evaluateExpression();
//			this.expression = expression;
//		} catch (IllegalExpressionException e) {
//			log.info("The expression "+expression+" could not be evaluated: "+e.getMessage()+", using "+this.expression+" instead!");
//			
////			getPrefs().node("Expression").
//			// TODO Auto-generated catch block
////			e.printStackTrace();
//		}
//		
//	}
//	public String getExpression() {
//		return expression;
//	}

	public synchronized ArrayList<FiringModelMap> getFiringModelMaps() {
		return firingModelMaps;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.sf.jaer.eventprocessing.EventFilter#resetFilter()
	 */
	@Override
	public void resetFilter() {
		synchronized (kernelProcessors) {
			for (KernelProcessor kp : kernelProcessors) {
				kp.reset();
			}
		}
		synchronized (firingModelMaps) {
			for (FiringModelMap map : firingModelMaps) {
				map.reset();
			}
		}
	}
	
	@Override
	synchronized public void cleanup() {
    	if (mapOutputViewer != null) {
    		mapOutputViewer.savePrefs();
    	}
    }


	
//	@Override
//	public void actionPerformed(ActionEvent e) {
////		SingleOutputViewer soViewer = spikingOutputDisplay.createOutputViewer(
////				128, 128);
//		SingleOutputViewer soViewer = spikingOutputDisplay.createOutputViewer(
//				kernelEditor.getOutWidth(), kernelEditor.getOutWidth());
//		ExpressionBasedSpatialInputKernel kernel = kernelEditor
//				.createInputKernel();
////		kernel.changeOffset((kernelEditor.getOutWidth() - 128) / 2 ,(kernelEditor.getOutHeight() - 128) / 2);
//		kernel.setInputOutputSizes(128, 128, kernelEditor.getOutWidth(), kernelEditor.getOutHeight());
//
//		SimpleKernelProcessor kernelProcessor = new SimpleKernelProcessor(
//				kernelEditor.getOutWidth(), kernelEditor.getOutHeight(),kernel);
//		kernelProcessor.addSpikeHandler(soViewer);
//		synchronized (kernelProcessors) {
//			kernelProcessors.add(kernelProcessor);
//		}
//	}
	
	public void addKernelProcessor(KernelProcessor kernelProcessor) {
		synchronized (kernelProcessors) {
			if (!kernelProcessors.contains(kernelProcessor))
				kernelProcessors.add(kernelProcessor);
		}
	}
	
	public void removeKernelProcessor(KernelProcessor kernelProcessor) {
		synchronized (kernelProcessors) {
			if (kernelProcessors.contains(kernelProcessor))
				kernelProcessors.remove(kernelProcessor);
		}
	}

	@Override
	public void initFilter() {
		// TODO Auto-generated method stub
		
	}
}
