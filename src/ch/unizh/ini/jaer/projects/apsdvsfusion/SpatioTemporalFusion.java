/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.util.ArrayList;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.Spring;
import javax.swing.SpringLayout;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import jspikestack.ImageDisplay;
import jspikestack.KernelMaker2D.Scaling;

import ch.unizh.ini.jaer.projects.apsdvsfusion.SpikingOutputDisplay.SingleOutputViewer;
import ch.unizh.ini.jaer.projects.apsdvsfusion.mathexpression.IllegalExpressionException;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.PolarityEvent.Polarity;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.AEViewer;

/**
 * Filter class that uses a defined kernel function to compute not spatially filtered output spikes.
 * 
 * @author Dennis Goehlsdorf
 *
 */
public class SpatioTemporalFusion extends EventFilter2D implements ActionListener {

//	InputKernel inputKernel;
//	FiringModelMap firingModelMap;
//	
//	String expression = getPrefs().get("Expression", "1");

	ArrayList<KernelProcessor> kernelProcessors = new ArrayList<KernelProcessor>();
	SpikingOutputDisplay spikingOutputDisplay = null;// = new SpikingOutputDisplay();
	boolean kernelEditorActive = false;
	ExpressionKernelEditor kernelEditor = null;// = new ExpressionKernelEditor(this);
	
	boolean filterEvents = false;
	SpikeHandler filterSpikeHandler = new SpikeHandler() {
		public void spikeAt(int x, int y, int time, Polarity polarity) {
			PolarityEvent pe = (PolarityEvent)out.getOutputIterator().nextOutput();
			pe.setX((short)x);
			pe.setY((short)y);
			pe.setSpecial(false);
			pe.setPolarity(polarity);
			pe.setTimestamp(time);
		}

	};
	
	/**
	 * @param chip
	 */
	public SpatioTemporalFusion(AEChip chip) {
		super(chip);
		this.setFilterEnabled(false);
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
        	if (kernelEditor == null)
        		kernelEditor = new ExpressionKernelEditor(this);
        	kernelEditor.setVisible(true);
        	if (spikingOutputDisplay == null) 
        		 spikingOutputDisplay = new SpikingOutputDisplay();
        	spikingOutputDisplay.setVisible(true);
        } else {
        	if (kernelEditor != null)
        		kernelEditor.setVisible(false);
        	if (spikingOutputDisplay != null)
        		spikingOutputDisplay.setVisible(false);
            out = null; // garbage collect
        }
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
	
	
	/* (non-Javadoc)
	 * @see net.sf.jaer.eventprocessing.EventFilter2D#filterPacket(net.sf.jaer.event.EventPacket)
	 */
	@Override
	public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!filterEnabled) {
            return in;
        }
        if (enclosedFilter != null) {
            in = enclosedFilter.filterPacket(in);
        }
		checkOutputPacketEventType(in);
        OutputEventIterator<?> oi=out.outputIterator();
//        firingModelMap.changeSize(chip.getSizeX(), chip.getSizeY());
 //       PolarityEvent e;
        out.setEventClass(PolarityEvent.class);
		for (BasicEvent be : in) {
			if (be instanceof PolarityEvent) {
				synchronized (kernelProcessors) {
					for (KernelProcessor kp : kernelProcessors) {
						kp.spikeAt(be.x,be.y,be.timestamp, ((PolarityEvent)be).getPolarity());
					}
				}
//				inputKernel.apply(be.x, be.y, be.timestamp, ((PolarityEvent)be).polarity, firingModelMap, spikeHandler);
			}
		}
		if (filterEvents)
			return out;
		else 
			return in;
	}

	
	
	public void doShow_KernelEditor() {
		kernelEditorActive ^= true;
		if (kernelEditor != null)
			kernelEditor.setVisible(kernelEditorActive);
		if (spikingOutputDisplay != null)
			spikingOutputDisplay.setVisible(kernelEditorActive);
//		if (kernelEditorActive)
//			spikingOutputDisplay.runDisplays();
//		else
//			spikingOutputDisplay.setVisible(false);
	}
	
	
	/* (non-Javadoc)
	 * @see net.sf.jaer.eventprocessing.EventFilter#initFilter()
	 */
	@Override
	public void initFilter() {
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
		// setExpression(expression);
		if (spikingOutputDisplay != null)
			spikingOutputDisplay.reset();
		synchronized (kernelProcessors) {
			if (kernelProcessors != null)
				kernelProcessors.clear();
		}
	}

	@Override
	public void actionPerformed(ActionEvent e) {
//		SingleOutputViewer soViewer = spikingOutputDisplay.createOutputViewer(
//				128, 128);
		SingleOutputViewer soViewer = spikingOutputDisplay.createOutputViewer(
				kernelEditor.getOutWidth(), kernelEditor.getOutWidth());
		ExpressionBasedSpatialInputKernel kernel = kernelEditor
				.createInputKernel();
		SimpleKernelProcessor kernelProcessor = new SimpleKernelProcessor(
				kernelEditor.getOutWidth(), kernelEditor.getOutHeight(), 
				// center kernel:
				(kernelEditor.getOutWidth() - 128) / 2 ,(kernelEditor.getOutHeight() - 128) / 2,kernel);
		kernelProcessor.addSpikeHandler(soViewer);
		synchronized (kernelProcessors) {
			kernelProcessors.add(kernelProcessor);
		}

	}
}
