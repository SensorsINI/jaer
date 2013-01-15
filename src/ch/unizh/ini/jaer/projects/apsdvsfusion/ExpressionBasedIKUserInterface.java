/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.HeadlessException;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
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
	private JPanel viewerPanel = new JPanel(new GridBagLayout());
	private GridBagConstraints viewerPanelConstraints = new GridBagConstraints();
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
            viewerPanel.setBackground(Color.BLACK);


            viewerPanel.add(soViewer.getDisplay(), BorderLayout.CENTER);
//    		viewerPanel.setPreferredSize(new Dimension(400,400));
    		viewerPanel.validate();
            
    		this.add(viewerPanel, BorderLayout.CENTER);
    		
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
					ExpressionBasedIKUserInterface.this.savePrefs();
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
		public void savePrefs(Preferences prefs, String prefString) {
			inputKernel.savePrefs(prefs, prefString+"inputKernel.");
			kernelProcessor.savePrefs(prefs, prefString+"kernelProcessor.");
			prefs.putBoolean(prefString+"enabled", kernelProcessor.isEnabled());
		}
		public void loadPrefs(Preferences prefs, String prefString) {
			inputKernel.loadPrefs(prefs, prefString+"inputKernel.");
			kernelProcessor.loadPrefs(prefs, prefString+"kernelProcessor.");
			setActivationState(prefs.getBoolean(prefString+"enabled", kernelProcessor.isEnabled()));
			
		}
    	protected void setActivationState(boolean enabled) {
    		kernelProcessor.setEnabled(enabled);
    		enableBox.setSelected(enabled);
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
						ExpressionBasedIKUserInterface.this.savePrefs();
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
			ExpressionBasedIKUserInterface.this.savePrefs();
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
		
		viewerPanelConstraints.gridx = 0;
		viewerPanelConstraints.gridy = 0;
		viewerPanelConstraints.fill = GridBagConstraints.BOTH;
		viewerPanelConstraints.weightx = 0.5;
		viewerPanelConstraints.weighty = 0.5;
		viewerPanelConstraints.insets = new Insets(5, 5, 5, 5);
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
				ExpressionBasedIKUserInterface.this.savePrefs();
			} 
		});
		mainPanel.add(buttonPanel,BorderLayout.NORTH);
		mainPanel.add(viewerPanel,BorderLayout.CENTER);
		mainPanel.setPreferredSize(new Dimension(850,400));
		
		loadPrefs();
		this.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent arg0) {
				savePrefs();
			}
		});
		
		this.pack();
		this.setVisible(true);
//		addKernel(7,7,128,128,128,128,"0.05","0.05");
		
	}

	protected void savePrefs() {
		Preferences p = getPrefs();
		p.putInt(makePrefString("kernelCount"), panels.size());
		for (int i = 0; i < panels.size(); i++) {
			ExpressionBasedSpatialIKPanel panel = panels.get(i);
			panel.savePrefs(p, makePrefString("kernel"+i+"."));
		}
	}

	protected void loadPrefs() {
		Preferences p = getPrefs();
		int panelCount = p.getInt(makePrefString("kernelCount"), panels.size());
		panels.clear();
		for (int i = 0; i < panelCount; i++) {
			addKernel(3,3,128,128,128,128,"0.01","0.01");
			panels.get(panels.size()-1).loadPrefs(p, makePrefString("kernel"+i+"."));
		}
	}
	
	protected Preferences getPrefs() {
		return stfFilter.getPrefs();
	}
	
	protected String makePrefString(String name) {
		return "SpatioTemporalFusion.UserInterface."+name;
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
			viewerPanel.add(soViewerPanel, viewerPanelConstraints);
			viewerPanelConstraints.gridx++;
			this.pack();
		}
		else {
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					panels.add(soViewerPanel);
					viewerPanel.add(soViewerPanel, viewerPanelConstraints);
					viewerPanelConstraints.gridx++;
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
			viewerPanel.add(soViewerPanel, viewerPanelConstraints);
			viewerPanelConstraints.gridx++;
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
		ui.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent we) {
				System.exit(0);
			}
		});
		
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
	}
}
