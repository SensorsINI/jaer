/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import java.util.ArrayList;

import javax.swing.JButton;
import javax.swing.JComponent;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.PolarityEvent.Polarity;
import net.sf.jaer.eventprocessing.EventFilter2D;
import ch.unizh.ini.jaer.projects.apsdvsfusion.gui.ParameterBrowserPanel;
import ch.unizh.ini.jaer.projects.apsdvsfusion.gui.ParameterContainer;
import ch.unizh.ini.jaer.projects.apsdvsfusion.gui.SpikingOutputViewerManager;
//import ch.unizh.ini.jaer.projects.apsdvsfusion.SpikingOutputDisplay.SingleOutputViewer;

/**
 * Filter class that uses a defined kernel function to compute not spatially filtered output spikes.
 * 
 * @author Dennis Goehlsdorf
 *
 */
public class SpatioTemporalFusion extends EventFilter2D { //implements ActionListener {

//	InputKernel inputKernel;
//	FiringModelMap firingModelMap;
//	
//	String expression = getPrefs().get("Expression", "1");

	ArrayList<KernelProcessor> kernelProcessors = new ArrayList<KernelProcessor>();
	
//	SpikingOutputDisplay spikingOutputDisplay = null;// = new SpikingOutputDisplay();
	
//	boolean kernelEditorActive = false;
//	ExpressionKernelEditor kernelEditor = null;// = new ExpressionKernelEditor(this);
	
	ExpressionBasedIKUserInterface expressionBasedIKUserInterface = null;
	SpikingOutputViewerManager spikingOutputViewerManager = null;
//	private int grayLevels = 4;
	
	boolean filterEvents = false;
	SpikeHandler filterSpikeHandler = new SpikeHandler() {
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

		@Override
		public void reset() {
		}

	};
	
	/**
	 * @param chip
	 */
	public SpatioTemporalFusion(AEChip chip) {
		super(chip);
//		this.setFilterEnabled(false);
        setPropertyTooltip("grayLevels", "Number of displayed gray levels");
//		firingModelMap = new ArrayFiringModelMap(chip, IntegrateAndFire.getCreator());
//		inputKernel = new ExpressionBasedSpatialInputKernel(5, 5);
//		kernelProcessors 
		//AEViewer viewer = new AEViewer(null, "ch.unizh.ini.jaer.projects.apsdvsfusion.FusedInputSimulatedChip");
		//this.simchip = new FusedInputSimulatedChip();

		//viewer.setChip(simchip);
		
		//viewer.setVisible(true);
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
        	if (expressionBasedIKUserInterface == null)
        		expressionBasedIKUserInterface = new ExpressionBasedIKUserInterface(this, spikingOutputViewerManager);
       		expressionBasedIKUserInterface.setVisible(true);
        } else {
//        	if (kernelEditor != null)
//        		kernelEditor.setVisible(false);
//        	if (spikingOutputDisplay != null)
//        		spikingOutputDisplay.setVisible(false);
//            out = null; // garbage collect
        	if (spikingOutputViewerManager != null)
        		spikingOutputViewerManager.kill();
        	if (expressionBasedIKUserInterface != null) {
        		expressionBasedIKUserInterface.setVisible(false);
        		expressionBasedIKUserInterface.savePrefs();
        	}
        }
    }
	
    public int getGrayLevels() {
    	if (expressionBasedIKUserInterface != null) {
    		return expressionBasedIKUserInterface.getGrayLevels();
    	}
    	else return getPrefs().getInt("SpatioTemporalFusion.UserInterface.grayLevels",4);
    }
    public void setGrayLevels(int grayLevels) {
    	if (expressionBasedIKUserInterface != null) {
    		expressionBasedIKUserInterface.setGrayLevels(grayLevels);
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
	

	int time = 0;
	/* (non-Javadoc)
	 * @see net.sf.jaer.eventprocessing.EventFilter2D#filterPacket(net.sf.jaer.event.EventPacket)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public EventPacket<?> filterPacket(EventPacket<?> in) {
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
				synchronized (kernelProcessors) {
					for (KernelProcessor kp : kernelProcessors) {
//						if (be.timestamp < time)
//							System.out.println(time + " -> " + be.timestamp);
//						if (be.timestamp < beforePackageTime) 
//							System.out.println("time decreased from last package: "+beforePackageTime+" -> "+be.timestamp);
						if (((PolarityEvent)be).getPolarity() == Polarity.On)
							kp.signalAt(be.x,be.y,be.timestamp, 1.0);
						else
							kp.signalAt(be.x,be.y,be.timestamp, -1.0);
							
						time = be.timestamp;
					}
				}
//				inputKernel.apply(be.x, be.y, be.timestamp, ((PolarityEvent)be).polarity, firingModelMap, spikeHandler);
			}
		}
		if (expressionBasedIKUserInterface != null)
			expressionBasedIKUserInterface.processUntil(maxTime);
		if (filterEvents)
			return out;
		else 
			return in;
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
	
	
	public void doClear() {
		// setExpression(expression);
		if (expressionBasedIKUserInterface != null)
			expressionBasedIKUserInterface.reset();
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
//	@Override
	public void do_addStuffToPanel() {
		addControls(new ParameterBrowserPanel(new ParameterContainer("TestClass") {
			String myString;
			float myFloat;
			int myInt;
			public String getMyString() {
				return myString;
			}
			public void setMyString(String myString) {
				this.myString = myString;
			}
			public float getMyFloat() {
				return myFloat;
			}
			public void setMyFloat(float myFloat) {
				this.myFloat = myFloat;
			}
			public int getMyInt() {
				return myInt;
			}
			public void setMyInt(int myInt) {
				this.myInt = myInt;
			}
			public void do_TestButton() {
				
			}
			@Override
			protected JComponent createCustomControls() {
				return new JButton("Hello");
			}
			
		}));
//		setExpression("0");
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
	}
	
	@Override
	synchronized public void cleanup() {
    	if (expressionBasedIKUserInterface != null) {
    		expressionBasedIKUserInterface.savePrefs();
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
