/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.HeadlessException;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import ch.unizh.ini.jaer.projects.apsdvsfusion.FiringModelMap;
import ch.unizh.ini.jaer.projects.apsdvsfusion.SpatioTemporalFusion;

/**
 * @author Dennis
 *
 */
public class MapOutputViewer extends JFrame {

	/**
	 * 
	 */
	private static final long serialVersionUID = -4355665819932342352L;
	private JPanel mainPanel = new JPanel();
	private BorderLayout mainPanelLayout = new BorderLayout();
	private JPanel viewerPanel = new JPanel(new GridBagLayout());
	private GridBagConstraints viewerPanelConstraints = new GridBagConstraints();
	private SpatioTemporalFusion stfFilter;
	ContinuousOutputViewerManager spikingOutputViewerManager;
//	ExpressionBasedKernelEditDialog editDialog;
	ArrayList<MapPanel> panels = new ArrayList<MapPanel>();
//	int grayLevels = -1;
	
    public class MapPanel extends JPanel {
    	/**
		 * 
		 */
		private static final long serialVersionUID = 3797114114409894258L;
		SpikingOutputViewer soViewer;
//    	ExpressionBasedSpatialInputKernel inputKernel;
//    	SimpleKernelProcessor kernelProcessor;
    	JPanel viewerPanel;
//    	JButton editButton;
//    	JButton deleteButton;
    	JCheckBox enableBox;
    	BorderLayout layout;
    	int inputWidth, inputHeight;
    	JLabel nameLabel;
    	FiringModelMap map;
    	PropertyChangeListener mapNameListener;
    	
    	public MapPanel(FiringModelMap map) {
    		this.map = map;
//    		inputKernel.setOnExpressionString(onExpressionString);
//    		inputKernel.setOffExpressionString(offExpressionString);
    		soViewer = spikingOutputViewerManager.createOutputViewer(map, map.getGrayLevels());
    		map.getSupport().addPropertyChangeListener("grayLevels",new PropertyChangeListener() {
				@Override
				public void propertyChange(PropertyChangeEvent e) {
					if (e.getPropertyName().equals("grayLevels"))
						soViewer.setGrayLevels((Integer)e.getNewValue());
				}
			});
//    		inputKernel.setInputOutputSizes(inputWidth, inputHeight, outputWidth, outputHeight);
//    		kernelProcessor = new SimpleKernelProcessor(outputWidth,outputHeight,inputKernel, stfFilter.getPrefs().node("kernelProcessor"));
//    		kernelProcessor.addSpikeHandler(soViewer);
//    		stfFilter.addKernelProcessor(kernelProcessor);
    		
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
//    		editButton = new JButton("Edit");
//    		deleteButton = new JButton("Delete");
    		enableBox = new JCheckBox("enable");
    		enableBox.setSelected(true);
    		nameLabel = new JLabel(map.getName());
    		mapNameListener = new PropertyChangeListener() {
				@Override
				public void propertyChange(PropertyChangeEvent evt) {
					if (evt.getPropertyName().equals("name"))
						nameLabel.setText(evt.getNewValue().toString());
				}
			};
			map.getSupport().addPropertyChangeListener("name", mapNameListener);
    		buttonPanel.add(nameLabel);
//    		buttonPanel.add(enableBox); 
//    		buttonPanel.add(editButton);
//    		buttonPanel.add(deleteButton);
    		
    		
    		this.add(buttonPanel, BorderLayout.SOUTH);
    		
    		
//    		enableBox.addChangeListener(new ChangeListener() {
//				@Override
//				public void stateChanged(ChangeEvent arg0) {
//					setActivationState(enableBox.isSelected());
//					ExpressionBasedIKUserInterface.this.savePrefs();
//				}
//			});
//    		editButton.addActionListener(new ActionListener() {
//				@Override
//				public void actionPerformed(ActionEvent arg0) {
//					editKernel();
//				}
//			});
//    		deleteButton.addActionListener(new ActionListener() {
//				@Override
//				public void actionPerformed(ActionEvent arg0) {
//					deleteKernel();
//				}
//			});
    		
    		this.setBorder(BorderFactory.createLineBorder(Color.black));
    		this.validate();
  		
    	}

//    	public void savePrefs(Preferences prefs, String prefString) {
//			inputKernel.savePrefs(prefs, prefString+"inputKernel.");
//			kernelProcessor.savePrefs(prefs, prefString+"kernelProcessor.");
//			prefs.putBoolean(prefString+"enabled", kernelProcessor.isEnabled());
//		}
//		public void loadPrefs(Preferences prefs, String prefString) {
//			inputKernel.loadPrefs(prefs, prefString+"inputKernel.");
//			kernelProcessor.loadPrefs(prefs, prefString+"kernelProcessor.");
//			setActivationState(prefs.getBoolean(prefString+"enabled", kernelProcessor.isEnabled()));
//			soViewer.changeSize(kernelProcessor.getOutWidth(), kernelProcessor.getOutHeight());
//			this.validate();
//		}
    	protected void setActivationState(boolean enabled) {
//    		kernelProcessor.setEnabled(enabled);
    		soViewer.setActive(enabled);
    		enableBox.setSelected(enabled);
    	}
//    	protected void editKernel() {
//			editDialog.setParameters(inputKernel.getWidth(),
//					inputKernel.getHeight(), inputWidth, inputHeight,
//					kernelProcessor.getOutWidth(),
//					kernelProcessor.getOutHeight(),
//					inputKernel.getExpressionString(), "0");
////					inputKernel.getOffExpressionString());
//			editDialog.setVisible(true);
//			if (editDialog.isValuesAccepted()) {
//				synchronized (kernelProcessor) {
//					synchronized (inputKernel) {
////						viewerPanel.remove(soViewer.getDisplay());
//						inputKernel.changeSize(editDialog.getKernelWidth(), editDialog.getKernelHeight());
//						kernelProcessor.changeOutSize(editDialog.getOutWidth(), editDialog.getOutHeight());
//						soViewer.changeSize(editDialog.getOutWidth(), editDialog.getOutHeight());
//						inputKernel.setExpressionString(editDialog.getOnExpressionString());
////						inputKernel.setOffExpressionString(editDialog.getOffExpressionString());
//						inputKernel.setInputOutputSizes(inputWidth, inputHeight, editDialog.getOutWidth(), editDialog.getOutHeight());
//						ExpressionBasedIKUserInterface.this.savePrefs();
////						ExpressionBasedIKUserInterface.this.pack();
//						ExpressionBasedIKUserInterface.this.validate();
//						ExpressionBasedIKUserInterface.this.repaint();
////						viewerPanel.add(soViewer.getDisplay());
//					}
//				}
//			}
//    		// TODO
//    	}
    	protected void delete() {
    		map.getSupport().removePropertyChangeListener("name", mapNameListener);
//    		stfFilter.removeKernelProcessor(kernelProcessor);
    		spikingOutputViewerManager.removeOutputViewer(soViewer);
    		soViewer.releaseMap();
    		removePanel(this);
//    		panels.remove(this);
//			ExpressionBasedIKUserInterface.this.savePrefs();
//			ExpressionBasedIKUserInterface.this.validate();
//			ExpressionBasedIKUserInterface.this.repaint();
//    		ExpressionBasedIKUserInterface.this.pack();
//    		ExpressionBasedIKUserInterface.this.repaint();
    	}
//    	public void processUntil(int timeInUs) {
//    		kernelProcessor.processUntil(timeInUs);
//    	}
    }
	
	/**
	 * @throws HeadlessException
	 */
	public MapOutputViewer(SpatioTemporalFusion stfFilter, ContinuousOutputViewerManager spikingOutputViewerManager) {
		super("Map-Output-Viewer");
		
		viewerPanelConstraints.gridx = 0;
		viewerPanelConstraints.gridy = 0;
		viewerPanelConstraints.fill = GridBagConstraints.BOTH;
		viewerPanelConstraints.weightx = 0.5;
		viewerPanelConstraints.weighty = 0.5;
		viewerPanelConstraints.insets = new Insets(5, 5, 5, 5);
//		editDialog = new ExpressionBasedKernelEditDialog(this, stfFilter.getPrefs());
		this.spikingOutputViewerManager = spikingOutputViewerManager;
		this.stfFilter = stfFilter;
		this.setContentPane(mainPanel);
		mainPanel.setLayout(mainPanelLayout);
//		JPanel buttonPanel = new JPanel();
//		JButton addKernelButton = new JButton("Add convolution kernel");
//		buttonPanel.add(addKernelButton);
//		addKernelButton.addActionListener(new ActionListener() {
//			@Override
//			public void actionPerformed(ActionEvent arg0) {
//				addKernel();
//				ExpressionBasedIKUserInterface.this.savePrefs();
//			} 
//		});
//		mainPanel.add(buttonPanel,BorderLayout.NORTH);
		mainPanel.add(viewerPanel,BorderLayout.CENTER);
		mainPanel.setPreferredSize(new Dimension(850,400));
		
		loadPrefs();
		this.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent arg0) {
				savePrefs();
			}
		});
		
		this.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent arg0) {
				saveWindowBounds();
			}
			@Override
			public void componentMoved(ComponentEvent arg0) {
				saveWindowBounds();
			}
		});

		this.pack();
		loadWindowBounds();
//		this.setVisible(true);
//		setGrayLevels(getPrefs().getInt(makePrefString("grayLevels"), 4));
//		addKernel(7,7,128,128,128,128,"0.05","0.05");
		
	}

//	public int getGrayLevels() {
//		return grayLevels;
//	}
//
//	public void setGrayLevels(int grayLevels) {
//		if (this.grayLevels != grayLevels) {
//			this.grayLevels = grayLevels;
//			for (MapPanel panel : panels) {
//				panel.soViewer.setGrayLevels(grayLevels);
//			}
//			getPrefs().putInt(makePrefString("grayLevels"), grayLevels);			
//		}
//		
//	}
	
	protected void saveWindowBounds() {
		Preferences p = getPrefs();
		Rectangle rect = this.getBounds();
		p.putDouble(makePrefString("windowX"), rect.getX());
		p.putDouble(makePrefString("windowY"), rect.getY());
		p.putDouble(makePrefString("windowWidth"), rect.getWidth());
		p.putDouble(makePrefString("windowHeight"), rect.getHeight());
	}
	
	protected void loadWindowBounds() {
		Preferences p = getPrefs();
		
		Rectangle rect = this.getBounds();
		this.setBounds(new Rectangle((int)p.getDouble(makePrefString("windowX"), rect.getX()),
				(int)p.getDouble(makePrefString("windowY"), rect.getY()),
				(int)p.getDouble(makePrefString("windowWidth"), rect.getWidth()),
				(int)p.getDouble(makePrefString("windowHeight"), rect.getHeight())));
	}
	
	public void savePrefs() {
		Preferences p = getPrefs();
		saveWindowBounds();
		
//		p.putInt(makePrefString("kernelCount"), panels.size());
//		for (int i = 0; i < panels.size(); i++) {
//			MapPanel panel = panels.get(i);
//			panel.savePrefs(p, makePrefString("kernel"+i+"."));
//		}
	}

	public void loadPrefs() {
		Preferences p = getPrefs();
		loadWindowBounds();

//		int panelCount = p.getInt(makePrefString("kernelCount"), panels.size());
//		panels.clear();
//		for (int i = 0; i < panelCount; i++) {
//			addKernel(3,3,128,128,128,128,"0.01"/*,"0.01"*/);
//			while (panels.size() <= i) {
//				try {
//					Thread.sleep(100);
//				} catch (InterruptedException e) {
//				}
//			}
//			panels.get(panels.size()-1).loadPrefs(p, makePrefString("kernel"+i+"."));
//		}
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
	
//	public void addKernel(int kernelWidth, int kernelHeight, int inputWidth, int inputHeight, int outputWidth, int outputHeight, String onExpressionString/*, String offExpressionString*/) {
//		final ExpressionBasedSpatialIKPanel soViewerPanel = new ExpressionBasedSpatialIKPanel(kernelWidth, kernelHeight, inputWidth, inputHeight, outputWidth, outputHeight, onExpressionString/*, offExpressionString*/);
//		if (SwingUtilities.isEventDispatchThread()) {
//			panels.add(soViewerPanel);
//			viewerPanel.add(soViewerPanel, viewerPanelConstraints);
//			viewerPanelConstraints.gridx++;
//			this.validate();
//			this.repaint();
//		}
//		else {
//			SwingUtilities.invokeLater(new Runnable() {
//				@Override
//				public void run() {
//					panels.add(soViewerPanel);
//					viewerPanel.add(soViewerPanel, viewerPanelConstraints);
//					viewerPanelConstraints.gridx++;
//					ExpressionBasedIKUserInterface.this.validate();
//					ExpressionBasedIKUserInterface.this.repaint();
//				}
//			});
//		}
//		
//	}
//	protected void addKernel() {
//		editDialog.setParameters(7,7, 128, 128,128,128, "0.01","0.01");
//		editDialog.setVisible(true);
//		if (editDialog.isValuesAccepted()) {
//			ExpressionBasedSpatialIKPanel soViewerPanel = new ExpressionBasedSpatialIKPanel(editDialog.getKernelWidth(), editDialog.getKernelHeight(), 128,128,editDialog.getOutWidth(), editDialog.getOutHeight(), editDialog.getOnExpressionString()/*, editDialog.getOffExpressionString()*/);
//
//			panels.add(soViewerPanel);
//			viewerPanel.add(soViewerPanel, viewerPanelConstraints);
//			viewerPanelConstraints.gridx++;
//			this.validate();
//			this.repaint();
//		}
//		
//		
////		SimpleKernelProcessor kernelProcessor = new SimpleKernelProcessor(128,128,kernel);
////		kernelProcessor.addSpikeHandler(soViewer);
//		
//		
//		
////		SimpleKernelProcessor kernelProcessor = new SimpleKernelProcessor(
////				kernelEditor.getOutWidth(), kernelEditor.getOutHeight(),kernel);
//		
////		SingleOutputViewer soViewer = spikingOutputDisplay.createOutputViewer(
////				kernelEditor.getOutWidth(), kernelEditor.getOutWidth());
//		
////		ExpressionBasedSpatialInputKernel kernel = kernelEditor
////				.createInputKernel();
//
//
////		synchronized (kernelProcessors) {
////			kernelProcessors.add(kernelProcessor);
////		}
////		synchronized (inputKernels) {
////			inputKernels.add(kernel);
////		}
////		synchronized (soViewers) {
////			soViewers.add(soViewer);
////		}
////		synchronized (soViewerPanels) {
////			soViewerPanels.add(soViewerPanel);
////		}
//	}
	
	
	public void reset() {
//		ArrayList<MapPanel> panels = new ArrayList<MapPanel>(this.panels);
//		for (MapPanel panel : panels) {
//			panel.deleteKernel();
//		}
		panels.clear();
		viewerPanel.removeAll();
		this.pack();
		
	}
	
//	public void processUntil(int timeInUs) {
//		for (MapPanel panel : panels)
//			panel.processUntil(timeInUs);
//	}
	protected void removePanel(MapPanel panel) {
		viewerPanel.remove(panel);
		if (panels.contains(panel))
			panels.remove(panel);
	}
	
	public void removeViewerFor(final FiringModelMap map) {
		if (SwingUtilities.isEventDispatchThread()) {
			for (MapPanel panel : panels) {
				if (panel.map == map) {
					panel.delete();
					break;
				}
			}
			this.validate();
			this.repaint();
		}
		else {
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					for (MapPanel panel : panels) {
						if (panel.map == map) {
							panel.delete();
							break;
						}
					}
					MapOutputViewer.this.validate();
					MapOutputViewer.this.repaint();
				}
			});
		}
	}
	
	public void addViewerFor(FiringModelMap map) {
		final MapPanel newPanel = new MapPanel(map);
		if (SwingUtilities.isEventDispatchThread()) {
			panels.add(newPanel);
			viewerPanel.add(newPanel, viewerPanelConstraints);
			viewerPanelConstraints.gridx++;
			this.validate();
			this.repaint();
		}
		else {
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					panels.add(newPanel);
					viewerPanel.add(newPanel, viewerPanelConstraints);
					viewerPanelConstraints.gridx++;
					MapOutputViewer.this.validate();
					MapOutputViewer.this.repaint();
				}
			});
		}
		
	}
	
//	public static void main(String[] args) {
//		SpatioTemporalFusion stfFilter = new SpatioTemporalFusion(null);
////		SpikingOutputViewerManager spikingOutputViewerManager = new SpikingOutputViewerManager(); 
//		stfFilter.setFilterEnabled(true);
//		ExpressionBasedIKUserInterface ui = stfFilter.expressionBasedIKUserInterface;//new ExpressionBasedIKUserInterface(stfFilter, spikingOutputViewerManager);
//		ui.addWindowListener(new WindowAdapter() {
//			public void windowClosing(WindowEvent we) {
//				System.exit(0);
//			}
//		});
//		
//		Random r = new Random(1);
////		ui.addKernel(7,7,128,128,128,128,"0.05","0.05");
//		int time = 0;
//		long startTime = System.nanoTime();
//		for (int i = 0; i < 1000; i++) {
//			EventPacket<PolarityEvent> ep = new EventPacket<PolarityEvent>(PolarityEvent.class);
//            @SuppressWarnings("rawtypes")
//			OutputEventIterator outItr = ep.outputIterator();
////			Iterator<PolarityEvent> it = ep.inputIterator();
//			for (int j = 0; j < 1000; j++) {
//                PolarityEvent pe = (PolarityEvent) outItr.nextOutput();
//				
////				PolarityEvent pe = it.next();//new PolarityEvent();
//				pe.setX((short)r.nextInt(128));
//				pe.setY((short)r.nextInt(128));
//				pe.setTimestamp(time++);
////				ep.(packet)(pe);
//			}
//			stfFilter.filterPacket(ep);
//		}
//		long endTime = System.nanoTime();
//		System.out.println("Total time in ms: "+(endTime-startTime)/1000000);
//	}
}
