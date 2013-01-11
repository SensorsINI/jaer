/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.HeadlessException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;

import ch.unizh.ini.jaer.projects.apsdvsfusion.SpikingOutputDisplay.SingleOutputViewer;

/**
 * @author Dennis
 *
 */
public class ExpressionBasedIKUserInterface extends JFrame {
	private JPanel mainPanel = new JPanel();
	private BorderLayout mainPanelLayout = new BorderLayout();
	private JPanel viewerPanel = new JPanel();
	private SpatioTemporalFusion stfFilter;
	SpikingOutputViewerManager spikingOutputViewerManager;
	ExpressionBasedKernelEditDialog editDialog;
	ArrayList<ExpressionBasedSpatialIKPanel> panels = new ArrayList<ExpressionBasedIKUserInterface.ExpressionBasedSpatialIKPanel>();
	
    public class ExpressionBasedSpatialIKPanel extends JPanel {
    	SpikingOutputViewer soViewer;
    	ExpressionBasedSpatialInputKernel inputKernel;
    	SimpleKernelProcessor kernelProcessor;
    	JPanel viewerPanel;
    	JButton editButton;
    	JButton deleteButton;
    	JCheckBox enableBox;
    	BorderLayout layout;
    	int inputWidth, inputHeight;
    	
    	public ExpressionBasedSpatialIKPanel(int kernelWidth, int kernelHeight, int inputWidth, int inputHeight, int outputWidth, int outputHeight, String onExpressionString, String offExpressionString) {
    		this.inputWidth = inputWidth;
    		this.inputHeight = inputHeight;
    		inputKernel = new SpaceableExpressionBasedSpatialIK(kernelWidth, kernelHeight);
    		inputKernel.setOnExpressionString(onExpressionString);
    		inputKernel.setOffExpressionString(offExpressionString);
    		soViewer = spikingOutputViewerManager.createOutputViewer(outputWidth, outputHeight);
    		inputKernel.setInputOutputSizes(inputWidth, inputHeight, outputWidth, outputHeight);
    		kernelProcessor = new SimpleKernelProcessor(outputWidth,outputHeight,inputKernel);
    		kernelProcessor.addSpikeHandler(soViewer);
    		stfFilter.addKernelProcessor(kernelProcessor);
    		
    		layout = new BorderLayout();
    		this.setLayout(layout);
    		viewerPanel = new JPanel();
            viewerPanel.setLayout(new BorderLayout());
    		this.add(viewerPanel, BorderLayout.CENTER);
    		viewerPanel.add(soViewer.getDisplay(), BorderLayout.CENTER);
//    		viewerPanel.setPreferredSize(new Dimension(400,400));
    		viewerPanel.validate();
            
//            GridBagConstraints c = new GridBagConstraints();
//            c.fill=GridBagConstraints.HORIZONTAL;
//            c.gridx=viewers.size() % 5;
//            c.gridy=viewers.size() / 5;
//            c.gridheight=2;
           
   		
    		
    		JPanel buttonPanel = new JPanel();
    		//buttonPanel.setLayout(new )
    		editButton = new JButton("Edit");
    		deleteButton = new JButton("Delete");
    		enableBox = new JCheckBox("enable");
    		enableBox.setSelected(true);
    		buttonPanel.add(enableBox);
    		buttonPanel.add(editButton);
    		buttonPanel.add(deleteButton);
    		this.add(buttonPanel, BorderLayout.SOUTH);
    		enableBox.addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(ChangeEvent arg0) {
					setActivationState(enableBox.isSelected());
				}
			});
    		editButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					editKernel();
				}
			});
    		deleteButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					deleteKernel();
				}
			});
    		this.setBorder(BorderFactory.createLineBorder(Color.black));
    		this.validate();
    	}
    	protected void setActivationState(boolean enabled) {
    		kernelProcessor.setEnabled(enabled);
    	}
    	protected void editKernel() {
			editDialog.setParameters(inputKernel.getWidth(),
					inputKernel.getHeight(), inputWidth, inputHeight,
					kernelProcessor.getOutWidth(),
					kernelProcessor.getOutHeight(),
					inputKernel.getOnExpressionString(),
					inputKernel.getOffExpressionString());
			editDialog.setVisible(true);
			if (editDialog.isValuesAccepted()) {
				synchronized (inputKernel) {
					synchronized (kernelProcessor) {
//						viewerPanel.remove(soViewer.getDisplay());
						inputKernel.changeSize(editDialog.getKernelWidth(), editDialog.getKernelHeight());
						kernelProcessor.changeOutSize(editDialog.getOutWidth(), editDialog.getOutHeight());
						soViewer.changeSize(editDialog.getOutWidth(), editDialog.getOutHeight());
						inputKernel.setOnExpressionString(editDialog.getOnExpressionString());
						inputKernel.setOffExpressionString(editDialog.getOffExpressionString());
						inputKernel.setInputOutputSizes(inputWidth, inputHeight, editDialog.getOutWidth(), editDialog.getOutHeight());
						ExpressionBasedIKUserInterface.this.pack();
//						viewerPanel.add(soViewer.getDisplay());
					}
				}
			}
    		// TODO
    	}
    	protected void deleteKernel() {
    		stfFilter.removeKernelProcessor(kernelProcessor);
    		spikingOutputViewerManager.removeOutputViewer(soViewer);
    		removePanel(this);
    		ExpressionBasedIKUserInterface.this.pack();
    		ExpressionBasedIKUserInterface.this.repaint();
    		panels.remove(this);
    	}
    	
    }
	
	/**
	 * @throws HeadlessException
	 */
	public ExpressionBasedIKUserInterface(SpatioTemporalFusion stfFilter, SpikingOutputViewerManager spikingOutputViewerManager) {
		super("ConvolutionKernel-Viewer");
		editDialog = new ExpressionBasedKernelEditDialog(this);
		this.spikingOutputViewerManager = spikingOutputViewerManager;
		this.stfFilter = stfFilter;
		this.setContentPane(mainPanel);
		mainPanel.setLayout(mainPanelLayout);
		JPanel buttonPanel = new JPanel();
		JButton addKernelButton = new JButton("Add convolution kernel");
		buttonPanel.add(addKernelButton);
		addKernelButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				addKernel();
			} 
		});
		mainPanel.add(buttonPanel,BorderLayout.NORTH);
		mainPanel.add(viewerPanel,BorderLayout.CENTER);
		mainPanel.setPreferredSize(new Dimension(850,400));
		this.pack();
		this.setVisible(true);
		addKernel(7,7,128,128,128,128,"0.05","0.05");
	}
	
//	
//	ArrayList<SimpleKernelProcessor> kernelProcessors = new ArrayList<SimpleKernelProcessor>();
//	ArrayList<ExpressionBasedSpatialInputKernel> inputKernels = new ArrayList<ExpressionBasedSpatialInputKernel>();
//	ArrayList<SpikingOutputViewer> soViewers = new ArrayList<SpikingOutputViewer>();
//	ArrayList<ExpressionBasedSpatialIKPanel> soViewerPanels = new ArrayList<ExpressionBasedSpatialIKPanel>(); 
	
	public void addKernel(int kernelWidth, int kernelHeight, int inputWidth, int inputHeight, int outputWidth, int outputHeight, String onExpressionString, String offExpressionString) {
		final ExpressionBasedSpatialIKPanel soViewerPanel = new ExpressionBasedSpatialIKPanel(kernelWidth, kernelHeight, inputWidth, inputHeight, outputWidth, outputHeight, onExpressionString, offExpressionString);
		if (SwingUtilities.isEventDispatchThread()) {
			panels.add(soViewerPanel);
			viewerPanel.add(soViewerPanel);
			this.pack();
		}
		else {
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					panels.add(soViewerPanel);
					viewerPanel.add(soViewerPanel);
					ExpressionBasedIKUserInterface.this.pack();
				}
			});
		}
		
	}
	protected void addKernel() {
		editDialog.setParameters(7,7, 128, 128,128,128, "0.01","0.01");
		editDialog.setVisible(true);
		if (editDialog.isValuesAccepted()) {
			ExpressionBasedSpatialIKPanel soViewerPanel = new ExpressionBasedSpatialIKPanel(editDialog.getKernelWidth(), editDialog.getKernelHeight(), 128,128,editDialog.getOutWidth(), editDialog.getOutHeight(), editDialog.getOnExpressionString(), editDialog.getOffExpressionString());

			panels.add(soViewerPanel);
			viewerPanel.add(soViewerPanel);
			this.pack();
		}
		
		
//		SimpleKernelProcessor kernelProcessor = new SimpleKernelProcessor(128,128,kernel);
//		kernelProcessor.addSpikeHandler(soViewer);
		
		
		
//		SimpleKernelProcessor kernelProcessor = new SimpleKernelProcessor(
//				kernelEditor.getOutWidth(), kernelEditor.getOutHeight(),kernel);
		
//		SingleOutputViewer soViewer = spikingOutputDisplay.createOutputViewer(
//				kernelEditor.getOutWidth(), kernelEditor.getOutWidth());
		
//		ExpressionBasedSpatialInputKernel kernel = kernelEditor
//				.createInputKernel();


//		synchronized (kernelProcessors) {
//			kernelProcessors.add(kernelProcessor);
//		}
//		synchronized (inputKernels) {
//			inputKernels.add(kernel);
//		}
//		synchronized (soViewers) {
//			soViewers.add(soViewer);
//		}
//		synchronized (soViewerPanels) {
//			soViewerPanels.add(soViewerPanel);
//		}
	}
	
	
	public void reset() {
		ArrayList<ExpressionBasedSpatialIKPanel> panels = new ArrayList<ExpressionBasedIKUserInterface.ExpressionBasedSpatialIKPanel>(this.panels);
		for (ExpressionBasedSpatialIKPanel panel : panels) {
			panel.deleteKernel();
		}
		viewerPanel.removeAll();
		this.pack();
		
	}
	protected void removePanel(ExpressionBasedSpatialIKPanel panel) {
		viewerPanel.remove(panel);
	}
	public static void main(String[] args) {
		SpatioTemporalFusion stfFilter = new SpatioTemporalFusion(null);
		SpikingOutputViewerManager spikingOutputViewerManager = new SpikingOutputViewerManager(); 
		stfFilter.setFilterEnabled(true);
		ExpressionBasedIKUserInterface ui = stfFilter.expressionBasedIKUserInterface;//new ExpressionBasedIKUserInterface(stfFilter, spikingOutputViewerManager);
		Random r = new Random(1);
		ui.addKernel(7,7,128,128,128,128,"0.05","0.05");
		int time = 0;
		for (int i = 0; i < 100000; i++) {
			EventPacket<PolarityEvent> ep = new EventPacket<PolarityEvent>(PolarityEvent.class);
            OutputEventIterator outItr = ep.outputIterator();
			Iterator<PolarityEvent> it = ep.inputIterator();
			for (int j = 0; j < 1000; j++) {
                PolarityEvent pe = (PolarityEvent) outItr.nextOutput();
				
//				PolarityEvent pe = it.next();//new PolarityEvent();
				pe.setX((short)r.nextInt(128));
				pe.setY((short)r.nextInt(128));
				pe.setTimestamp(time++);
//				ep.(packet)(pe);
			}
			stfFilter.filterPacket(ep);
		}
		System.exit(0);
	}
}
