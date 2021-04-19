/**
 *
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.PolarityEvent.Polarity;
import net.sf.jaer.eventprocessing.EventFilter2D;
import ch.unizh.ini.jaer.projects.apsdvsfusion.gui.ContinuousOutputViewer;
import ch.unizh.ini.jaer.projects.apsdvsfusion.gui.ContinuousOutputViewerManager;
import ch.unizh.ini.jaer.projects.apsdvsfusion.gui.MapOutputViewer;
import ch.unizh.ini.jaer.projects.apsdvsfusion.gui.NonGLImageDisplay;
import ch.unizh.ini.jaer.projects.apsdvsfusion.gui.ParameterBrowserPanel;
//import ch.unizh.ini.jaer.projects.apsdvsfusion.SpikingOutputDisplay.SingleOutputViewer;
/**
 * Filter class for applying convolution kernels to the output of the DVS (tested on DVS128 and APS-DVS).
 * The user can create multiple response fields and connect these through convolution kernels. The
 * units in the response fields can have various response properties (for example  LIF), which can be modified by implementing
 * new child-classes of {@link FiringModel}.
 *
 *
 * @author Dennis Goehlsdorf
 *
 */
/**
 * @author Dennis
 *
 */
@Description("Allows to apply user-defined series of convolution filters")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class SpatioTemporalFusion extends EventFilter2D { //implements ActionListener {

	static ArrayList<SpatioTemporalFusion> runningInstances = new ArrayList<SpatioTemporalFusion>();

	/**
	 * Returns the filter instance that manages the SignalHandler requestingKernel
	 *
	 * Attention: Currently, only one running instance of SpatioTemporalFusion is supported. This function will always return the first created instance
	 * of SpatioTemporalFusion!
	 * @param requestingKernel The SignalHandler that searches for its managing filter instance.
	 * @return An instance of SpatioTemporalFusion
	 */
	static SpatioTemporalFusion getInstance(SignalHandler requestingKernel) {
		if (runningInstances.size() == 0) {
			return null;
		}
		else {
			return runningInstances.get(0);
		}
	}

	/**
	 * Returns the filter instance that manages the FiringModelMap requestingMap
	 *
	 * Attention: Currently, only one running instance of SpatioTemporalFusion is supported. This function will always return the first created instance
	 * of SpatioTemporalFusion!
	 * @param requestingMap The FiringModelMap that searches for its managing filter instance.
	 * @return An instance of SpatioTemporalFusion
	 */
	static SpatioTemporalFusion getInstance(FiringModelMap requestingMap) {
		if (runningInstances.size() == 0) {
			return null;
		}
		else {
			return runningInstances.get(0);
		}
	}

	/**
	 * Returns a lock object that is used to make sure changes to the data structure are only executed when the filter is inactive.
	 *
	 * Attention: Currently, only one running instance of SpatioTemporalFusion is supported. getInstance needs to be modified for a support of more instances.
	 * @param requestingKernel An instance of SignalHandler that wants to perform a change to the data structure.
	 * @return A lock object.
	 */
	static Object getFilteringLock(SignalHandler requestingKernel) {
		SpatioTemporalFusion stf = getInstance(requestingKernel);
		if (stf != null) {
			return stf.getFilteringLock();
		}
		else {
			return new Object();
		}
	}

	/**
	 * Returns a lock object that is used to make sure changes to the data structure are only executed when the filter is inactive.
	 *
	 * Attention: Currently, only one running instance of SpatioTemporalFusion is supported. getInstance needs to be modified for a support of more instances.
	 * @param requestingMap An instance of FiringModelMap that wants to perform a change to the data structure.
	 * @return A lock object.
	 */
	static Object getFilteringLock(FiringModelMap requestingMap) {
		SpatioTemporalFusion stf = getInstance(requestingMap);
		if (stf != null) {
			return stf.getFilteringLock();
		}
		else {
			return new Object();
		}
	}

	ArrayList<String> usedExpressionStrings = new ArrayList<String>();
	SpikeSoundSignalHandler spikeSoundSignalHandler;

	int currentSizeX = 128, currentSizeY = 128;

	int frameUpdateTime = getPrefs().getInt("frameUpdateTime", 30000);

//	InputKernel inputKernel;
//	FiringModelMap firingModelMap;
//
//	String expression = getPrefs().get("Expression", "1");
	FiringModelMap onMap, offMap;
	ArrayList<FiringModelMap> firingModelMaps = new ArrayList<FiringModelMap>();
	private ArrayList<Integer> mapIndexMappings = new ArrayList<Integer>();
	private int mapIndexPosition = getPrefs().getInt("mapIndexPosition", 0);


	Object filteringLock = new Object();

	Preferences myPreferencesNode;

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
			fillPanel();
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
				if ((map != onMap) && (map != offMap)) {
					ParameterBrowserPanel newMapPanel = new ParameterBrowserPanel(map);
					customPanel.add(newMapPanel, gbc);
					mapPanels.add(newMapPanel);
					gbc.gridy++;
					panelCounter++;
				}
			}
	        JFrame frame = (JFrame) SwingUtilities.getRoot(customPanel);
	        if (frame != null) {
				frame.pack();
			}
		}

		public void mapAdded() {
			if (panelCounter < (firingModelMaps.size()-2)) {
				ParameterBrowserPanel newMapPanel = new ParameterBrowserPanel(firingModelMaps.get(firingModelMaps.size()-1));
				if (!loading) {
					newMapPanel.toggleSelection();
				}
				customPanel.add(newMapPanel, gbc);
				mapPanels.add(newMapPanel);
				gbc.gridy++;
				panelCounter++;
		        JFrame frame = (JFrame) SwingUtilities.getRoot(customPanel);
		        if (frame != null) {
					frame.pack();
				}
			}
		}

		public void mapRemoved(int index) {
			if ((panelCounter > index) && (index >= 0)) {
				ParameterBrowserPanel panel = mapPanels.get(index);
				mapPanels.remove(index);
				customPanel.remove(panel);
				panelCounter--;
		        JFrame frame = (JFrame) SwingUtilities.getRoot(customPanel);
		        if (frame != null) {
					frame.pack();
				}
			}
		}

		@Override
		public JComponent createCustomControls() {
			return customPanel;
		}
	}

//	ArrayList<KernelProcessor> kernelProcessors = new ArrayList<KernelProcessor>();

//	SpikingOutputDisplay spikingOutputDisplay = null;// = new SpikingOutputDisplay();

//	boolean kernelEditorActive = false;
//	ExpressionKernelEditor kernelEditor = null;// = new ExpressionKernelEditor(this);

	MapOutputViewer mapOutputViewer = null;
	ContinuousOutputViewerManager spikingOutputViewerManager = null;
//	private int grayLevels = 4;

	private boolean panelAdded = false;
	private STFParameterContainer stfParameterContainer;

	boolean filterEvents = false;
	SignalHandler filterSpikeHandler = new SignalHandler() {
		@Override
		public void signalAt(int x, int y, int time, double value) {
			if (filterEvents) {
				PolarityEvent pe = (PolarityEvent)out.getOutputIterator().nextOutput();
				pe.setX((short)x);
				pe.setY((short)y);
				pe.setSpecial(false);
				if (value < 0) {
					pe.setPolarity(Polarity.Off);
				}
				else {
					pe.setPolarity(Polarity.On);
				}
				pe.setTimestamp(time);
			}
		}

//		@Override
		@Override
		public void reset() {
		}

	};



	/**
	 * @param chip
	 */
	public SpatioTemporalFusion(AEChip chip) {
		super(chip);

		stfParameterContainer = new STFParameterContainer(getPrefs());
		runningInstances.add(this);
		spikingOutputViewerManager = new ContinuousOutputViewerManager();
		spikingOutputViewerManager.setUpdateMicros(frameUpdateTime);
   		mapOutputViewer = new MapOutputViewer(this, spikingOutputViewerManager);

   		spikeSoundSignalHandler = new SpikeSoundSignalHandler(this,"SpikeSound",getPrefs().node("spikesound"));

//		this.setFilterEnabled(false);
//        setPropertyTooltip("grayLevels", "Number of displayed gray levels");
        this.onMap = new FiringModelMap(currentSizeX,currentSizeY, getPrefs().node("onMap")) {
			@Override
			public FiringModel get(int x, int y) {
				return null;
			}
			@Override
			public void buildUnits() {
			}
		};
		this.offMap = new FiringModelMap(currentSizeX,currentSizeY, getPrefs().node("offMap")) {
			@Override
			public void buildUnits() {
			}
			@Override
			public FiringModel get(int x, int y) {
				return null;
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

	@Override
	public Preferences getPrefs() {
		if (myPreferencesNode == null) {
			this.myPreferencesNode = super.getPrefs().node("apsdvsfusion").node("spatiotemporalfusion");
		}
		return myPreferencesNode;
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


	JFrame adcMonitor = null;
	JPanel adcMonitorPanel = null;
	int adcOutResolution = 549;
	NonGLImageDisplay display = new NonGLImageDisplay(adcOutResolution, adcOutResolution);

	class FrameOutputViewer extends NonGLImageDisplay implements ContinuousOutputViewer {
		public FrameOutputViewer(int sizeX, int sizeY) {
			super(sizeX, sizeY);
		}

		@Override
		public void updateOutput() {
			this.repaint();
		}
	}
	FrameOutputViewer myFrameViewer = new FrameOutputViewer(128, 128);
	JFrame apsMonitor = null;
	ContinuousOutputViewerManager frameViewerManager = new ContinuousOutputViewerManager();
	{
		frameViewerManager.addOutputViewer(myFrameViewer);
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
        	if (spikingOutputViewerManager == null) {
        		spikingOutputViewerManager = new ContinuousOutputViewerManager();
        		spikingOutputViewerManager.setUpdateMicros(frameUpdateTime);
        	}
       		spikingOutputViewerManager.run();
        	if (mapOutputViewer == null) {
				mapOutputViewer = new MapOutputViewer(this, spikingOutputViewerManager);
			}
        	if (adcMonitor == null) {
        		adcMonitor = new JFrame("Spike influence monitor");
        		adcMonitorPanel = new JPanel();
        		adcMonitor.setContentPane(display);
        		adcMonitor.setPreferredSize(new Dimension(300,300));
        	}
//    		adcMonitor.setVisible(true);

    		if (apsMonitor == null) {
    			apsMonitor = new JFrame("APS monitor");
    			apsMonitor.setContentPane(myFrameViewer);
    			apsMonitor.setPreferredSize(new Dimension(480,360));
    		}
//    		apsMonitor.setVisible(true);
    		frameViewerManager.run();
//       		expressionBasedIKUserInterface.setVisible(true);
        } else {
//        	if (kernelEditor != null)
//        		kernelEditor.setVisible(false);
//        	if (spikingOutputDisplay != null)
//        		spikingOutputDisplay.setVisible(false);
//            out = null; // garbage collect
        	if (spikingOutputViewerManager != null) {
				spikingOutputViewerManager.kill();
			}
        	if (mapOutputViewer != null) {
        		mapOutputViewer.setVisible(false);
        		mapOutputViewer.savePrefs();
        	}
        	if (adcMonitor != null) {
				adcMonitor.setVisible(false);
			}
        	if (apsMonitor != null) {
				apsMonitor.setVisible(false);
			}
        	if (frameViewerManager != null) {
				frameViewerManager.kill();
			}
        }
    }




//    public int getGrayLevels() {
//    	if (mapOutputViewer != null) {
//    		return mapOutputViewer.getGrayLevels();
//    	}
//    	else return getPrefs().getInt("SpatioTemporalFusion.UserInterface.grayLevels",4);
//    }
//    public void setGrayLevels(int grayLevels) {
//    	if (mapOutputViewer != null) {
//    		mapOutputViewer.setGrayLevels(grayLevels);
//    	}
//    	else getPrefs().putInt("SpatioTemporalFusion.UserInterface.grayLevels",grayLevels);
//    }
//


	/**
	 * @return the frameUpdateTime
	 */
	public int getFrameUpdateTime() {
		return frameUpdateTime;
	}

	/**
	 * @param frameUpdateTime the frameUpdateTime to set
	 */
	public void setFrameUpdateTime(int frameUpdateTime) {
		int before = this.frameUpdateTime;
		this.frameUpdateTime = frameUpdateTime;
		getPrefs().putInt("frameUpdateTime", frameUpdateTime);
		getSupport().firePropertyChange("frameUpdateTime", before, frameUpdateTime);
		if (spikingOutputViewerManager != null) {
			spikingOutputViewerManager.setUpdateMicros(frameUpdateTime);
		}
	}

	public boolean isFilterEvents() {
		return filterEvents;
	}

	public void setFilterEvents(boolean filterEvents) {
		if (filterEvents != this.filterEvents) {
			this.filterEvents = filterEvents;
//			for (FiringModelMap map : firingModelMaps) {
//				if (filterEvents)
//					map.addSignalHandler(filterSpikeHandler);
//				else
//					map.removeSignalHandler(filterSpikeHandler);
//			}
//			for (KernelProcessor kp : kernelProcessors) {
//				if (kp instanceof SimpleKernelProcessor) {
//					if (filterEvents)
//						((SimpleKernelProcessor)kp).addSpikeHandler(filterSpikeHandler);
//					else
//						((SimpleKernelProcessor)kp).removeSpikeHandler(filterSpikeHandler);
//				}
//			}
		}
	}

	public void addViewerFor(FiringModelMap map) {
		mapOutputViewer.addViewerFor(map);
	}

	public void removeViewerFor(FiringModelMap map) {
		mapOutputViewer.removeViewerFor(map);
	}

	public enum ADCType {
		A, B, C, DIFF_B
	}

	ADCType selectedOutput = ADCType.DIFF_B;
	int plusEvents = 1, minusEvents = 0;

	/**
	 * @return the selectedOutput
	 */
	public ADCType getSelectedOutputX() {
		return selectedOutput;
	}

	/**
	 * @param selectedOutput the selectedOutput to set
	 */
	public void setSelectedOutput(ADCType selectedOutput) {
		getSupport().firePropertyChange("selectedOutput", this.selectedOutput, selectedOutput);
		clearRegression();
		adcMin = Integer.MAX_VALUE;
		adcMax = Integer.MIN_VALUE;
		this.selectedOutput = selectedOutput;

	}



	/**
	 * @return the plusEvents
	 */
	public int getPlusEventsX() {
		return plusEvents;
	}

	/**
	 * @param plusEvents the plusEvents to set
	 */
	public void setPlusEvents(int plusEvents) {
		getSupport().firePropertyChange("plusEvents", this.plusEvents, plusEvents);
		clearRegression();
		this.plusEvents = plusEvents;
	}

	/**
	 * @return the minusEvents
	 */
	public int getMinusEventsX() {
		return minusEvents;
	}

	/**
	 * @param minusEvents the minusEvents to set
	 */
	public void setMinusEvents(int minusEvents) {
		getSupport().firePropertyChange("minusEvents", this.minusEvents, minusEvents);
		clearRegression();
		this.minusEvents = minusEvents;
	}

	void clearRegression() {
		adcMin = Integer.MAX_VALUE;
		adcMax = Integer.MIN_VALUE;
		before2sum = 0; plus2sum = 0; minus2sum = 0; beforeAfterSum = 0; plusAfterSum = 0; minusAfterSum = 0; plusChangeSum = 0; minusChangeSum = 0;
		plusEventEffects = 0; minusEventEffect = 0; m = 0;
		regressionOutputCount = 0;
		totalChange = 0;
		countedEvents = 0;
	}

	long before2sum = 0, plus2sum = 0, minus2sum = 0, beforeAfterSum = 0, plusAfterSum = 0, minusAfterSum = 0, plusChangeSum = 0, minusChangeSum = 0;
	float plusEventEffects = 0, minusEventEffect = 0, m = 0;
	long regressionOutputCount = 0;
	long totalChange = 0;
	long countedEvents = 0;

	int aTime = 0, bTime = 0;
	int lastValuePlus = 0, lastValueMinus = 0;


	public void XdoResetRatio() {
		this.plusCounter = 1;
		this.minusCounter = 1;
	}


	void evaluateADCEvent(ApsDvsEvent e) {
		int x = e.getX();
		int y = e.getY();
//		if (x == 100 && y == 100) {
//
//			if (e.isResetRead()) {
//				aTime = e.getTimestamp();
//				System.out.println("A event: time since last B: "+(aTime - bTime));
//				System.out.println("Total events since last occurrence: "+(adcPlusEventMap[x][y] + adcMinusEventMap[x][y] - lastValuePlus - lastValueMinus));
//			}
//			else if (e.isSignalRead()) {
//				bTime = e.getTimestamp();
//				System.out.println("B event: time since last A: "+(bTime - aTime));
//				System.out.println("Total events since last occurrence: "+(adcPlusEventMap[x][y] + adcMinusEventMap[x][y] - lastValuePlus - lastValueMinus));
//			}
//			if ((e.isResetRead() && selectedOutput == ADCType.A) || (e.isSignalRead() && selectedOutput == ADCType.B)) {
//				lastValuePlus = 0;
//				lastValueMinus = 0;
//			}
//			else {
//				lastValuePlus = adcPlusEventMap[x][y];
//				lastValueMinus = adcMinusEventMap[x][y];
//			}
//
//		}
		if ((x >= 0)	&& (x < currentSizeX) && (y >= 0)	&& (y < currentSizeY)) {
			int oldValue = 0;
			if (e.isResetRead()) {
				oldValue = adcAMap[x][y];
			}
			else if (e.isSignalRead()) {
				oldValue = adcBMap[x][y];
			}
//			else if (e.isC())
//				oldValue = adcCMap[x][y];
			int adcSample = e.getAdcSample();
                        if ((e.isResetRead() && (selectedOutput == ADCType.A)) ||
					(e.isSignalRead() && ((selectedOutput == ADCType.B) || (selectedOutput == ADCType.DIFF_B)))) {
//			if ((e.isResetRead() && selectedOutput == ADCType.A) ||
//					(e.isSignalRead() && (selectedOutput == ADCType.B || selectedOutput == ADCType.DIFF_B)) ||
//					(e.isC() && selectedOutput == ADCType.C)) {
				/*if (e.isStartOfFrame() && false) {
					// reduce influence of past measurements by dividing all accumulating values by 2 at the start of a new frame
					before2sum >>= 1;
					plus2sum >>= 1;
					minus2sum >>= 1;
					beforeAfterSum >>= 1;
					plusAfterSum >>= 1;
					minusAfterSum >>= 1;
					plusChangeSum >>= 1;
					minusChangeSum >>= 1;

				}*/
				if (oldValue >= 0) { // && adcSample != adcAMap[x][y]) {
					//						if (adcPlusEventMap[x][y] == plusEvents && adcMinusEventMap[x][y] == minusEvents) {
					if (selectedOutput == ADCType.DIFF_B) {
						oldValue = adcDIFF_BMap[x][y];
						adcSample = adcAMap[x][y] - adcSample;
					}
					if (adcSample < adcMin) {
						adcMin = adcSample;
					}
					if (adcSample >= adcMax) {
						adcMax = adcSample+1;
					}

					if (useAPSFrameToReset) {
						approximatedADCValues[x][y] = adcSample;
					}
					else {
						if (useMultiplication) {
							if (((plusEffect > 1.0f) && (adcSample > (approximatedADCValues[x][y]*plusEffect))) ||
									((plusEffect < 1.0) && (adcSample < (approximatedADCValues[x][y] * plusEffect)))) {
								approximatedADCValues[x][y] *= plusEffect;
							}
							if (((minusEffect > 1.0f) && (adcSample > (approximatedADCValues[x][y]*minusEffect))) ||
									((minusEffect < 1.0) && (adcSample < (approximatedADCValues[x][y] * minusEffect)))) {
								approximatedADCValues[x][y] *= minusEffect;
							}
						}
						else {
							if (((plusEffect > 0.0f) && (adcSample > (approximatedADCValues[x][y]+plusEffect))) ||
									((plusEffect < 0.0f) && (adcSample < (approximatedADCValues[x][y] + plusEffect)))) {
								approximatedADCValues[x][y] += plusEffect;
							}
							if (((minusEffect > 0.0f) && (adcSample > (approximatedADCValues[x][y]+minusEffect))) ||
									((minusEffect < 0.0) && (adcSample < (approximatedADCValues[x][y] + minusEffect)))) {
								approximatedADCValues[x][y] += minusEffect;
							}
						}
					}
					myFrameViewer.setPixmapGray(x, y, (((int)approximatedADCValues[x][y] - adcMin) << 8) / (adcMax - adcMin));

//					// linear regression: assume after = m * before + a * plus + b * minus + epsilon
//					// => minimize quadratic error
//					before2sum += oldValue*oldValue;
//					plus2sum += adcPlusEventMap[x][y]*adcPlusEventMap[x][y];
//					minus2sum += adcMinusEventMap[x][y]*adcMinusEventMap[x][y];
//					beforeAfterSum += oldValue * adcSample;
//					plusAfterSum += adcPlusEventMap[x][y] * adcSample;
//					minusAfterSum += adcMinusEventMap[x][y] * adcSample;
//					double total2 = before2sum + plus2sum + minus2sum;
//					if (total2 > 0) {
//						m = ((double)beforeAfterSum) / total2;
//						a = ((double)plusAfterSum) / total2;
//						b = ((double)minusAfterSum) / total2;
//						regressionOutputCount++;
//						if (regressionOutputCount % 100000 == 0)
//							System.out.println("After = "+m+" * before + "+a+" * plus + "+b+" * minus");
//					}

					// linear regression: assume change = a * plus + b * minus + epsilon
					// => minimize quadratic error
//					if (adcPlusEventMap[x][y] == plusEvents && adcMinusEventMap[x][y] == minusEvents) {
						int change = adcSample - oldValue;
						totalChange += change;
						countedEvents ++;
						plus2sum += adcPlusEventMap[x][y]*adcPlusEventMap[x][y];
						minus2sum += adcMinusEventMap[x][y]*adcMinusEventMap[x][y];
						plusChangeSum += adcPlusEventMap[x][y] * change;
						minusChangeSum += adcMinusEventMap[x][y] * change;
						float total2 = plus2sum + minus2sum;
						if (total2 > 0) {
							plusEventEffects = (plusChangeSum) / total2;
							minusEventEffect = (minusChangeSum) / total2;
							regressionOutputCount++;
//							if (regressionOutputCount % 1000 == 0)
//								System.out.println("Change = "+plusEventEffects+" * plus + "+minusEventEffect+" * minus; Avg change: "+((double)totalChange)/((double)countedEvents));
						}
//					}

				}
				if ((adcPlusEventMap[x][y] == plusEvents) && (adcMinusEventMap[x][y] == minusEvents) && (adcAMap[x][y] >= 0)) {
					//						xyAvgSum
					baList.add(new BeforeAfter(adcAMap[x][y],adcSample));
				}
				adcPlusEventMap[x][y] = 0;
				adcMinusEventMap[x][y] = 0;
			}
			if (e.isResetRead()) {
				adcAMap[x][y] = e.getAdcSample();
			}
			else if (e.isSignalRead()) {
				adcBMap[x][y] = e.getAdcSample();
				adcDIFF_BMap[x][y] = adcAMap[x][y] - e.getAdcSample();
			}
//			else if (e.isC())
//				adcCMap[x][y] = e.getAdcSample();
		}
			//			System.out.println(x+"/"+y);
	}


	float plusEffect = getPrefs().getFloat("plusEffect", 10);
	float minusEffect = getPrefs().getFloat("minusEffect", -10);

	float plusEffectBuffer = getPrefs().getFloat("plusEffectBuffer", 1.1f);
	float minusEffectBuffer = getPrefs().getFloat("minusEffectBuffer", 1.0f/1.1f);

	boolean useMultiplication = getPrefs().getBoolean("useMultiplication", false);
	boolean useAPSFrameToReset = getPrefs().getBoolean("useAPSFrameToReset", true);



	/**
	 * @return the plusEffect
	 */
	public float getPlusEffectX() {
		return plusEffect;
	}

	/**
	 * @param plusEffect the plusEffect to set
	 */
	public void setPlusEffect(float plusEffect) {
		getSupport().firePropertyChange("plusEffect", this.plusEffect, plusEffect);
		getPrefs().putFloat("plusEffect", plusEffect);
		this.plusEffect = plusEffect;
	}

	/**
	 * @return the minusEffect
	 */
	public float getMinusEffectX() {
		return minusEffect;
	}

	/**
	 * @param minusEffect the minusEffect to set
	 */
	public void setMinusEffect(float minusEffect) {
		getSupport().firePropertyChange("minusEffect", this.minusEffect, minusEffect);
		getPrefs().putFloat("minusEffect", minusEffect);
		this.minusEffect = minusEffect;
	}



	/**
	 * @return the useMultiplication
	 */
	public boolean isUseMultiplicationX() {
		return useMultiplication;
	}

	/**
	 * @param useMultiplication the useMultiplication to set
	 */
	public void setUseMultiplication(boolean useMultiplication) {
		if (this.useMultiplication != useMultiplication) {
			getSupport().firePropertyChange("useMultiplication", this.useMultiplication, useMultiplication);
			getPrefs().putBoolean("useMultiplication", useMultiplication);
			float pBuffer = plusEffect;
			float mBuffer = minusEffect;
			setPlusEffect(plusEffectBuffer);
			setMinusEffect(minusEffectBuffer);
			plusEffectBuffer = pBuffer;
			minusEffectBuffer = mBuffer;
			getPrefs().putFloat("plusEffectBuffer",pBuffer);
			getPrefs().putFloat("minusEffectBuffer",mBuffer);
			this.useMultiplication = useMultiplication;
		}
	}



	/**
	 * @return the useAPSFrameToReset
	 */
	public boolean isUseAPSFrameToResetX() {
		return useAPSFrameToReset;
	}

	/**
	 * @param useAPSFrameToReset the useAPSFrameToReset to set
	 */
	public void setUseAPSFrameToReset(boolean useAPSFrameToReset) {
		getSupport().firePropertyChange("useAPSFrameToReset", this.useAPSFrameToReset, useAPSFrameToReset);
		this.useAPSFrameToReset = useAPSFrameToReset;
		getPrefs().putBoolean("useAPSFrameToReset", useAPSFrameToReset);
	}

	void summarizeAPS() {
		if ((display.getSizeX() != adcOutResolution) || (display.getSizeY() != adcOutResolution)) {
			display.setImageSize(adcOutResolution, adcOutResolution);
		}
		int w = adcOutResolution, h = adcOutResolution;
//		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		int[][] counters = new int[w][h];



		for (BeforeAfter ba : baList) {
			counters[((ba.before - adcMin) * (w-1)) / (adcMax - adcMin)][((ba.after - adcMin) * (h-1)) / (adcMax - adcMin)]++;
		}

		baList.clear();
//		int maxValue = 1;
//
//		for (int i = 0; i < counters.length; i++) {
//			for (int j = 0; j < counters[i].length; j++) {
//				if (counters[i][j] > maxValue)
//					maxValue = counters[i][j];
//			}
//		}

		Random r = new Random();
		for (int i = 0; i < counters.length; i++) {
			int maxValue = 1;
			for (int j = 0; j < counters[i].length; j++) {
				if (counters[i][j] > maxValue) {
					maxValue = counters[i][j];
				}
			}
			for (int j = 0; j < counters[i].length; j++) {
//				int value = counters[i][j] * 255 / maxValue;
//				display.setPixmapGray(i, j, r.nextFloat());
				display.setPixmapGray(i, j, ((float)counters[i][j])/((float)maxValue));
//				img.setRGB(i, h-j-1, (value << 16) | (value << 8) | (value));
			}
		}
		display.repaint();
//		return img;
	}

	void evaluatePlusEvent(BasicEvent e) {
		int x = e.getX();
		int y = e.getY();
		if ((x >= 0) && (x < currentSizeX) && (y >= 0) && (y < currentSizeY)) {
			adcPlusEventMap[x][y]++;
			if (adcMax > adcMin) {
				if (useMultiplication) {
					approximatedADCValues[x][y] = ((approximatedADCValues[x][y] - adcMin) * plusEffect) + adcMin;
	//				approximatedADCValues[x][y] *= plusEffect;
				}
				else {
					approximatedADCValues[x][y] += plusEffect;
				}
//			approximatedADCValues[x][y] += plusEventEffects;
				myFrameViewer.setPixmapGray(x, y, (((int)approximatedADCValues[x][y] - adcMin) << 8) / (adcMax - adcMin));
			}
		}
	}

	void evaluateMinusEvent(BasicEvent e) {
		int x = e.getX();
		int y = e.getY();
		if ((x >= 0) && (x < currentSizeX) && (y >= 0) && (y < currentSizeY)) {
			adcMinusEventMap[x][y]++;
			if (adcMax > adcMin) {
				if (useMultiplication) {
					approximatedADCValues[x][y] = ((approximatedADCValues[x][y] - adcMin) * minusEffect) + adcMin;
				}
				else {
					approximatedADCValues[x][y] += minusEffect;
				}
//			approximatedADCValues[x][y] += minusEventEffect;
				myFrameViewer.setPixmapGray(x, y, (((int)approximatedADCValues[x][y] - adcMin) << 8) / (adcMax - adcMin));
			}
		}
	}

	class BeforeAfter {
		int before;
		int after;
		public BeforeAfter(int before, int after) {
			this.before = before;
			this.after = after;
		}
		/**
		 * @return the before
		 */
		public int getBefore() {
			return before;
		}
		/**
		 * @return the after
		 */
		public int getAfter() {
			return after;
		}

	}
	LinkedList<BeforeAfter> baList = new LinkedList<BeforeAfter>();

	int plusCounter = 0;
	int minusCounter = 0;

	int time = 0;

	float[][] approximatedADCValues =  new float[currentSizeX][currentSizeY];
	int adcAMap[][] = createADCMap(currentSizeX, currentSizeY);
	int adcBMap[][] = createADCMap(currentSizeX, currentSizeY);
	int adcDIFF_BMap[][] = createADCMap(currentSizeX, currentSizeY);
	int adcCMap[][] = createADCMap(currentSizeX, currentSizeY);
	int adcPlusEventMap[][] = createADCMap(currentSizeX, currentSizeY);
	int adcMinusEventMap[][] = createADCMap(currentSizeX, currentSizeY);

	int adcMin = Integer.MAX_VALUE;
	int adcMax = Integer.MIN_VALUE;

	int[][] createADCMap(int width, int height) {
		int adcMap[][] = new int[width][height];
		for (int i = 0; i < adcMap.length; i++) {
			for (int j = 0; j < adcMap[i].length; j++) {
				adcMap[i][j] = -1;
			}
		}
		return adcMap;
	}


	int maxTime = Integer.MIN_VALUE;

	protected void processEvent(BasicEvent be, boolean isAdcSample) {
		if (be.getTimestamp() > maxTime) {
			maxTime = be.getTimestamp();
		}
		if (be instanceof PolarityEvent) {
			boolean adcSample = false;
			if (be instanceof ApsDvsEvent) {
				ApsDvsEvent ade = ((ApsDvsEvent)be);
				if (ade.isApsData()) {
					adcSample = true;
					evaluateADCEvent(ade);
				}
			}
			if (!adcSample) {

				if (((PolarityEvent)be).getPolarity() == Polarity.On) {
					onMap.getSignalHandler().signalAt(be.x, be.y, be.timestamp, 1.0);
					evaluatePlusEvent(be);
					plusCounter++;
				}
				else {
					offMap.getSignalHandler().signalAt(be.x, be.y, be.timestamp, 1.0);
					evaluateMinusEvent(be);
					minusCounter++;
				}
//				if ((plusCounter + minusCounter) % 1000 == 0)
//					System.out.println("Ratio: "+((float)plusCounter)/((float)minusCounter));
			}
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
		for (FiringModelMap map : firingModelMaps) {
			if (map instanceof SchedulableFiringModelMap) {
				((SchedulableFiringModelMap)map).processScheduledEvents(be.getTimestamp());
			}
		}
	}

	protected boolean checkApsDvsEvent(ApsDvsEvent ade) {
		if (ade.isApsData()) {
			evaluateADCEvent(ade);
			return true;
		}
		else {
			return false;
		}
	}


	/* (non-Javadoc)
	 * @see net.sf.jaer.eventprocessing.EventFilter2D#filterPacket(net.sf.jaer.event.EventPacket)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
		synchronized (filteringLock) {
			// causes a reset of the output iterator.
			out.outputIterator();
	        if (!filterEnabled) {
	            return in;
	        }
	        if (enclosedFilter != null) {
	            in = enclosedFilter.filterPacket(in);
	        }
	//        getPrefs()

	        if ((chip.getSizeX() != currentSizeX) || (chip.getSizeY() != currentSizeY)) {
	        	currentSizeX = chip.getSizeX();
	        	currentSizeY = chip.getSizeY();
	        	approximatedADCValues = new float[currentSizeX][currentSizeY];
	        	myFrameViewer.setImageSize(currentSizeX, currentSizeY);
	        	onMap.setSizeX(currentSizeX);
	        	onMap.setSizeY(currentSizeY);
	        	offMap.setSizeX(currentSizeX);
	        	offMap.setSizeY(currentSizeY);
	        	adcAMap = createADCMap(currentSizeX, currentSizeY);
	        	adcBMap = createADCMap(currentSizeX, currentSizeY);
	        	adcDIFF_BMap = createADCMap(currentSizeX, currentSizeY);
	        	adcCMap = createADCMap(currentSizeX, currentSizeY);
	        	adcPlusEventMap = createADCMap(currentSizeX, currentSizeY);
	        	adcMinusEventMap = createADCMap(currentSizeX, currentSizeY);
	        }
			checkOutputPacketEventType(in);

//			out.setEventClass(getChip().getEventClass());//PolarityEvent.class);

			maxTime = Integer.MIN_VALUE;
			if (in instanceof ApsDvsEventPacket) {
				Iterator<?> it = ((ApsDvsEventPacket<? extends BasicEvent>)in).fullIterator();
				while (it.hasNext()) {
					ApsDvsEvent ade = (ApsDvsEvent)it.next();
					processEvent(ade, checkApsDvsEvent(ade));
				}
			}
			else {
				for (BasicEvent be : in) {
					processEvent(be,false);
				}

			}

	//		if (expressionBasedIKUserInterface != null)
	//			expressionBasedIKUserInterface.processUntil(maxTime);
			if (filterEvents) {
				return out;
			}
			else {
				return in;
			}
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
			if (!map.getSignalHandler().contains(filterSpikeHandler)) {
				map.addSignalHandler(filterSpikeHandler);
			}
		}
		else {
			map.removeSignalHandler(filterSpikeHandler);
		}
	}


//	public void doClear() {
//		// setExpression(expression);
//		if (mapOutputViewer != null)
//			mapOutputViewer.reset();
//		if (spikingOutputViewerManager != null) {
//			spikingOutputViewerManager.reset();
//		}
////		if (spikingOutputDisplay != null)
////			spikingOutputDisplay.reset();
//		synchronized (kernelProcessors) {
//			if (kernelProcessors != null)
//				kernelProcessors.clear();
//		}
//	}

	/* (non-Javadoc)
	 * @see net.sf.jaer.eventprocessing.EventFilter#initFilter()
	 */

	public void doShowOutputViewer() {
		if (mapOutputViewer != null) {
			mapOutputViewer.setVisible(!mapOutputViewer.isVisible());
		}
	}

//	@Override
	public void doShowParameterPanel() {
		if (!panelAdded) {
			panelAdded = true;
			ParameterBrowserPanel controls = new ParameterBrowserPanel(stfParameterContainer, false);
			ParameterBrowserPanel spikeSoundControls = new ParameterBrowserPanel(spikeSoundSignalHandler, false);
			controls.toggleSelection();
			JPanel myPanel = new JPanel();
			myPanel.setLayout(new GridBagLayout());
			GridBagConstraints gbc = new GridBagConstraints();
			gbc.weightx = 1;
			gbc.weighty = 1;
			gbc.gridy = 0;
			gbc.gridx = 0;
			gbc.fill = GridBagConstraints.BOTH;

			JPanel demoButtonPanel = new JPanel();
			JButton demoAButton = new JButton("Demo A");
			JButton demoBButton = new JButton("Demo B");
			JButton demoCButton = new JButton("Demo C");
			demoAButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					int index = 0;
					for (FiringModelMap map : firingModelMaps) {
						if ((map != onMap) && (map != offMap)) {
							boolean show = (index == 0);
							map.setEnabled(show);
							map.setMonitored(show);
							map.setControlsExpanded(show);
							index++;
						}
					}
				}
			});
			demoBButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					int index = 0;
					for (FiringModelMap map : firingModelMaps) {
						if ((map != onMap) && (map != offMap)) {
							boolean show = (index == 1);
							map.setEnabled(show);
							map.setMonitored(show);
							map.setControlsExpanded(show);
							if (show) {
								SignalTransformationKernel onKernel = map.getKernel(0);
								if (onKernel != null) {
									onKernel.setEnabled(true);
									onKernel.setControlsExpanded(false);
								}
								SignalTransformationKernel feedbackKernel = map.getKernel(1);
								if (feedbackKernel != null) {
									feedbackKernel.setEnabled(false);
									feedbackKernel.setControlsExpanded(true);
								}
							}
							index++;
						}
					}
				}
			});
			demoCButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					int index = 0;
					for (FiringModelMap map : firingModelMaps) {
						if ((map != onMap) && (map != offMap)) {
							boolean show = ((index >1) && (index <5));
							map.setEnabled(show);
							map.setMonitored(show);
							map.setControlsExpanded(false);
							index++;
						}
					}
				}
			});
			demoButtonPanel.add(demoAButton);
			demoButtonPanel.add(demoBButton);
			demoButtonPanel.add(demoCButton);
			myPanel.add(demoButtonPanel,gbc);
			gbc.gridy++;
			myPanel.add(controls,gbc);
			gbc.gridy++;
			myPanel.add(spikeSoundControls,gbc);
			gbc.gridy++;
			addControls(myPanel);
		}

//		setExpression("0");
	}

	public void removeMap(FiringModelMap map) {
		if (firingModelMaps.contains(map)) {
			int removedIndex = firingModelMaps.indexOf(map)-2;
			firingModelMaps.remove(removedIndex+2);

			mapIndexMappings.remove(removedIndex);
			mapIndexPosition = -1;
			for (Integer index : mapIndexMappings) {
				if (index >= mapIndexPosition) {
					mapIndexPosition = index + 1;
				}
			}
			map.disconnectMap();

			getPrefs().putInt("mapCount",firingModelMaps.size()-2);
			for (int i = 0; i < mapIndexMappings.size(); i++) {
				getPrefs().putInt("mapIndexMappings"+(i), mapIndexMappings.get(i));
			}
			stfParameterContainer.mapRemoved(removedIndex);
		}
	}

	protected void addMap(FiringModelMap map, int mapNodeIndex) {
		if (map != null) {
			map.setPreferences(getPrefs().node("map"+mapNodeIndex));
			ArrayList<FiringModelMap> oldMaps = new ArrayList<FiringModelMap>(firingModelMaps);
			firingModelMaps.add(map);
			mapIndexMappings.add(mapNodeIndex);
			mapIndexPosition = -1;
			for (Integer index : mapIndexMappings) {
				if (index >= mapIndexPosition) {
					mapIndexPosition = index + 1;
				}
			}
			stfParameterContainer.mapAdded();
			getSupport().firePropertyChange("firingModelMaps", oldMaps, firingModelMaps);

			getPrefs().putInt("mapCount", firingModelMaps.size()-2);
			getPrefs().putInt("mapIndexPosition", mapIndexPosition);
			getPrefs().putInt("mapIndexMappings"+(mapIndexMappings.size()-1), mapIndexMappings.get(mapIndexMappings.size()-1));
		}

	}

	public void doAdd_Map() {
		synchronized (firingModelMaps) {
			SchedulableWrapperMap newMap = new SchedulableWrapperMap(currentSizeX, currentSizeY, null, getPrefs().node("map"+mapIndexPosition));
			newMap.setName("Map "+(mapIndexPosition + 1));
			addMap(newMap, mapIndexPosition);


		}
	}

	boolean loading = false;
	public void loadSettings() {
		loading = true;
		ParameterContainer.disableStorage();
		for (int i = 0; i < 20; i++) {
			String s = getPrefs().get("usedExpressionString"+i, "");
			if (!s.equals("")) {

				addExpressionString(s);
			}
		}
		int mapCount = getPrefs().getInt("mapCount", 0);
		for (int i = 0; i < mapCount; i++) {
    		int mapping = getPrefs().getInt("mapIndexMappings"+(mapIndexMappings.size()), i);

			SchedulableWrapperMap newMap = new SchedulableWrapperMap(128, 128, null, getPrefs().node("map"+mapping));

			addMap(newMap, mapping);
		}
		// operate in 2 steps to make sure the map IDs have been restored before initializing the kernels.
		for (FiringModelMap map : firingModelMaps) {
			if ((map  != onMap) && (map != offMap)) {
				map.restoreParameters();
			}
		}
		for (FiringModelMap map : firingModelMaps) {
			if ((map  != onMap) && (map != offMap)) {
				map.restoreKernels();
			}
		}
		spikeSoundSignalHandler.restoreParameters();
		ParameterContainer.enableStorage();
		loading = false;
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
		if (isFilterEnabled()) {
//			before2sum = 0, plus2sum = 0, minus2sum = 0, beforeAfterSum = 0, plusAfterSum = 0, minusAfterSum = 0;
//			double a = 0, b = 0, m = 0;
//			long regressionOutputCount = 0;

			summarizeAPS();
		}
//		synchronized (kernelProcessors) {
//			for (KernelProcessor kp : kernelProcessors) {
//				kp.reset();
//			}
//		}
		synchronized (firingModelMaps) {
			for (FiringModelMap map : firingModelMaps) {
				map.reset();
			}
		}
		spikeSoundSignalHandler.reset();
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
//			kernelProcessors.appendCopy(kernelProcessor);
//		}
//	}

//	public void addKernelProcessor(KernelProcessor kernelProcessor) {
//		synchronized (kernelProcessors) {
//			if (!kernelProcessors.contains(kernelProcessor))
//				kernelProcessors.appendCopy(kernelProcessor);
//		}
//	}
//
//	public void removeKernelProcessor(KernelProcessor kernelProcessor) {
//		synchronized (kernelProcessors) {
//			if (kernelProcessors.contains(kernelProcessor))
//				kernelProcessors.remove(kernelProcessor);
//		}
//	}

	@Override
	public void initFilter() {
		// TODO Auto-generated method stub

	}
}
