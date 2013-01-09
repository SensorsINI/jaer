/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GraphicsConfiguration;
import java.awt.HeadlessException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

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
	
	
    public class ExpressionBasedSpatialIKPanel extends JPanel {
    	SpikingOutputViewer soViewer;
    	ExpressionBasedSpatialInputKernel inputKernel;
    	SimpleKernelProcessor kernelProcessor;
    	JPanel viewerPanel;
    	JButton editButton;
    	JButton deleteButton;
    	JCheckBox enableBox;
    	BorderLayout layout;
    	
    	public ExpressionBasedSpatialIKPanel(int kernelWidth, int kernelHeight, int inputWidth, int inputHeight, int outputWidth, int outputHeight) {
    		inputKernel = new SpaceableExpressionBasedSpatialIK(kernelWidth, kernelHeight);
    		soViewer = spikingOutputViewerManager.createOutputViewer(outputWidth, outputHeight);
    		inputKernel.setInputOutputSizes(inputWidth, inputHeight, outputWidth, outputHeight);
    		kernelProcessor = new SimpleKernelProcessor(128,128,inputKernel);
    		kernelProcessor.addSpikeHandler(soViewer);
    		stfFilter.addKernelProcessor(kernelProcessor);
    		
    		layout = new BorderLayout();
    		this.setLayout(layout);
    		viewerPanel = new JPanel();
    		this.add(viewerPanel, BorderLayout.CENTER);
    		JPanel buttonPanel = new JPanel();
    		//buttonPanel.setLayout(new )
    		editButton = new JButton("Edit");
    		deleteButton = new JButton("Delete");
    		enableBox = new JCheckBox("enable");
    		buttonPanel.add(enableBox);
    		buttonPanel.add(editButton);
    		buttonPanel.add(deleteButton);
    		this.add(buttonPanel, BorderLayout.SOUTH);
    		enableBox.addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(ChangeEvent arg0) {
					setEnabled(enableBox.isSelected());
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
    	}
    	protected void setActivationState(boolean enabled) {
    		// TODO
    	}
    	protected void editKernel() {
    		// TODO
    	}
    	protected void deleteKernel() {
    		stfFilter.removeKernelProcessor(kernelProcessor);
    		removePanel(this);
    	}
    	
    }
	
	/**
	 * @throws HeadlessException
	 */
	public ExpressionBasedIKUserInterface(SpatioTemporalFusion stfFilter, SpikingOutputViewerManager spikingOutputViewerManager) {
		super("ConvolutionKernel-Viewer");
		this.spikingOutputViewerManager = spikingOutputViewerManager;
		
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
	}
	
//	
//	ArrayList<SimpleKernelProcessor> kernelProcessors = new ArrayList<SimpleKernelProcessor>();
//	ArrayList<ExpressionBasedSpatialInputKernel> inputKernels = new ArrayList<ExpressionBasedSpatialInputKernel>();
//	ArrayList<SpikingOutputViewer> soViewers = new ArrayList<SpikingOutputViewer>();
//	ArrayList<ExpressionBasedSpatialIKPanel> soViewerPanels = new ArrayList<ExpressionBasedSpatialIKPanel>(); 
	
	protected void addKernel() {
		ExpressionBasedSpatialIKPanel soViewerPanel = new ExpressionBasedSpatialIKPanel(5,5,128,128,128,128);
		
		viewerPanel.add(soViewerPanel);
		
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
	
	protected void removePanel(ExpressionBasedSpatialIKPanel panel) {
		viewerPanel.remove(panel);
	}
	
}
