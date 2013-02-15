/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.ArrayList;
import java.util.prefs.Preferences;

import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

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
	
	int sizeX = -1, sizeY = -1;
	FiringModelCreator firingModelCreator = null;
	SpikeHandlerSet spikeHandlerSet;
	ArrayList<SignalTransformationKernel> inputKernels = new ArrayList<SignalTransformationKernel>();
	
	public class FiringModelMapCustomControls extends JPanel {
		GridBagConstraints gbc = new GridBagConstraints();
		ArrayList<ParameterBrowserPanel> panels = new ArrayList<ParameterBrowserPanel>();
		int panelCounter = 0;
		protected FiringModelMapCustomControls() {
			this.setLayout(new GridBagLayout());
			gbc.weightx = 1;
			gbc.weighty = 1;
			gbc.gridy = 0;
			gbc.gridx = 0;
			gbc.fill = GridBagConstraints.BOTH;
			fillPanel();
		}
		
		protected void fillPanel() {
			removeAll();

            for (SignalTransformationKernel stk : inputKernels) {
				ParameterBrowserPanel newPanel = new ParameterBrowserPanel(stk); 
				add(newPanel, gbc);
				panels.add(newPanel);
				gbc.gridy++;
				panelCounter++;
			}
	        JFrame frame = (JFrame) SwingUtilities.getRoot(this);
	        if (frame != null)
	        	frame.pack();
		}
		public void kernelAdded() {
			if (panelCounter < inputKernels.size()) {
				ParameterBrowserPanel newPanel = new ParameterBrowserPanel(inputKernels.get(inputKernels.size()-1)); 
				add(newPanel, gbc);
				panels.add(newPanel);
				gbc.gridy++;
				panelCounter++;
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
		this.spikeHandlerSet = new SpikeHandlerSet()/*spikeHandler*/;
		changeSize(sizeX, sizeY);
	}

	public FiringModelMap(int sizeX, int sizeY, Preferences parentPrefs, String nodeName) {
		super("FiringModelMap", parentPrefs, nodeName);
		this.spikeHandlerSet = new SpikeHandlerSet()/*spikeHandler*/;
		changeSize(sizeX, sizeY);
	}
	
	public FiringModelMap(int sizeX, int sizeY, SpikeHandler spikeHandler, Preferences prefs) {
		this(sizeX, sizeY, prefs);
		if (spikeHandler != null)
			this.spikeHandlerSet.addSpikeHandler(spikeHandler);
	}

	public FiringModelMap(int sizeX, int sizeY, SpikeHandler spikeHandler, Preferences parentPrefs, String nodeName) {
		this(sizeX, sizeY, parentPrefs, nodeName);
		if (spikeHandler != null)
			this.spikeHandlerSet.addSpikeHandler(spikeHandler);
	}

//	protected FiringModelMapParameterContainer createParameterContainer() {
//		return new FiringModelMapParameterContainer("FiringModelMap");
//	}
	
	public FiringModelCreator getFiringModelCreator() {
		return firingModelCreator;
	}

	public void setFiringModelCreator(FiringModelCreator firingModelCreator) {
		this.firingModelCreator = firingModelCreator;
		buildUnits();
	}
	
	public abstract void buildUnits(); 

	public SpikeHandler getSpikeHandler() {
		return spikeHandlerSet;
	}

	public void addSpikeHandler(SpikeHandler spikeHandler) {
		this.spikeHandlerSet.addSpikeHandler(spikeHandler);
	}

	public void removeSpikeHandler(SpikeHandler spikeHandler) {
		this.spikeHandlerSet.removeSpikeHandler(spikeHandler);
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
	public synchronized void setSizeX(int sizeX) {
		changeSize(sizeX, sizeY);
	}

	/**
	 * @param sizeY the sizeY to set
	 */
	public synchronized void setSizeY(int sizeY) {
		changeSize(sizeX, sizeY);
	}

	public synchronized void changeSize(int sizeX, int sizeY) {
		if (sizeX != this.sizeX || sizeY != this.sizeY) {
			int ox = this.sizeX, oy = this.sizeY;
			this.sizeX = sizeX;
			this.sizeY = sizeY;
			buildUnits();
			for (SignalTransformationKernel kernel : inputKernels) {
				kernel.outputSizeChanged(ox, oy, sizeX, sizeY);
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
	public abstract void reset();
	
	public void doAddKernel() {
		inputKernels.add(new SpaceableExpressionBasedSpatialIK(7, 7, getPrefs(), "inputKernel"+inputKernels.size()));
		if (myControls != null) {
			myControls.kernelAdded();
		}
	}
}
