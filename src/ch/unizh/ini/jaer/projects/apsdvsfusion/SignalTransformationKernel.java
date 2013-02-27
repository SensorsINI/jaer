/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent; 
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

 
/**
 * @author Dennis Goehlsdorf
 *
 */
public abstract class SignalTransformationKernel extends ParameterContainer implements SignalHandler  {
//	public void apply(int x, int y, int time, PolarityEvent.Polarity polarity, FiringModelMap map, SpikeHandler spikeHandler);
//	public void apply(int x, int y, int time, PolarityEvent.Polarity polarity, FiringModelMap map, SpikeHandler spikeHandler);
//	public void addOffset(int x, int y);
	
	private static int kernelCounter = 0;
	private int getNextKernelID() {
		return ++kernelCounter;
	}
	
	//	public 
	private int inputWidth, inputHeight;
	private int outputWidth, outputHeight;
	
	private FiringModelMap inputMap;
	private FiringModelMap outputMap;
	
	private int kernelID = getNextKernelID();
	
	private boolean enabled = true;
	
	public SignalTransformationKernel(String name, Preferences prefs) {
		super(name, prefs);
	}

//	@Deprecated
//	public SignalTransformationKernel(String name, Preferences parentPrefs,	String nodeName) {
//		super(name, parentPrefs, nodeName);
//	}
	
	public int getKernelID() {
		return kernelID;
	}
	
	/**
	 * @return the inputWidth
	 */
	protected int getInputWidth() {
		return inputWidth;
	}

	/**
	 * @return the inputHeight
	 */
	protected int getInputHeight() {
		return inputHeight;
	}

	/**
	 * @return the outputWidth
	 */
	protected int getOutputWidth() {
		return outputWidth;
	}

	/**
	 * @return the outputHeight
	 */
	protected int getOutputHeight() {
		return outputHeight;
	}

	protected void inputSizeChanged(int oldWidth, int oldHeight, int newWidth, int newHeight) {
	}
	
	protected void outputSizeChanged(int oldWidth, int oldHeight, int newWidth, int newHeight) {
	}
	
	public synchronized void setOutputSize(int width, int height) {
		if (width != outputWidth || height != outputHeight) {
			int dw = this.outputWidth;
			int dh = this.outputHeight;
			this.outputWidth = width;
			this.outputHeight = height;
			outputSizeChanged(dw, dh, outputWidth, outputHeight);
		}
	}

	
	public synchronized void setInputSize(int width, int height) {
		if (width != inputWidth || height != inputHeight) {
			int dw = this.inputWidth;
			int dh = this.inputHeight;
			this.inputWidth = width;
			this.inputHeight = height;
			inputSizeChanged(dw, dh, inputWidth, inputHeight);
		}
	}

	/**
	 * @return the inputMap
	 */
	public synchronized FiringModelMap getInputMap() {
		return inputMap;
	}

	/**
	 * @param inputMap the inputMap to set
	 */
	public synchronized void setInputMap(FiringModelMap inputMap) {
		if (inputMap != this.inputMap) {
			getSupport().firePropertyChange("inputMap", this.inputMap, inputMap);
			if (this.inputMap != null)
				this.inputMap.removeSignalHandler(this);
			boolean changeSize = (this.inputMap == null || inputMap == null || this.inputMap.getSizeX() != inputMap.getSizeX() || this.inputMap.getSizeY() != inputMap.getSizeY());
			this.inputMap = inputMap;
			if (changeSize) {
				if (inputMap != null)
					setInputSize(inputMap.getSizeX(), inputMap.getSizeY());
				else 
					setInputSize(1, 1);
			}
			if (this.inputMap != null) {
				this.inputMap.addSignalHandler(this);
				getPrefs().putInt("inputMapID", this.inputMap.getMapID());
			}
			else 
				getPrefs().putInt("inputMapID", -1);
		}
		
	}

	/**
	 * @return the outputMap
	 */
	public synchronized FiringModelMap getOutputMap() {
		return outputMap;
	}

	/**
	 * @param outputMap the outputMap to set
	 */
	public synchronized void setOutputMap(FiringModelMap outputMap) {
		if (outputMap != this.outputMap) {
			boolean changeSize = (this.inputMap == null || inputMap == null || this.inputMap.getSizeX() != inputMap.getSizeX() || this.inputMap.getSizeY() != inputMap.getSizeY());
			this.outputMap = outputMap;
			if (changeSize) {
				if (outputMap != null)
					setOutputSize(outputMap.getSizeX(), outputMap.getSizeY());
				else 
					setOutputSize(1, 1);
			}
		}
	}
	
	@SuppressWarnings("rawtypes")
	JComboBox myComboBox = new JComboBox();
	Object currentSelection = null;
	
	@Override
	public JComponent createCustomControls() {
		JPanel myPanel = new JPanel();
		myPanel.setLayout(new BoxLayout(myPanel,BoxLayout.X_AXIS));
		JLabel label = new JLabel("Input map:");
		label.setFont(label.getFont().deriveFont(10f));
		myPanel.add(label);
		myComboBox.setFont(myComboBox.getFont().deriveFont(10f));
		SpatioTemporalFusion stf = SpatioTemporalFusion.getInstance(this);
		ArrayList<FiringModelMap> contents;
		if (stf != null) {
			stf.getSupport().addPropertyChangeListener("firingModelMaps", this);
			contents = stf.getFiringModelMaps();
		}
		else 
			contents = new ArrayList<FiringModelMap>();
		updateComboBox(new ArrayList<FiringModelMap>(), contents);

		getSupport().addPropertyChangeListener("inputMap", new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				if (evt.getNewValue() != evt.getOldValue()) {
					if (evt.getNewValue() != null)
						myComboBox.setSelectedItem(evt.getNewValue());
					else
						myComboBox.setSelectedIndex(0);
				}
			}
		});
		myPanel.add(myComboBox);
		
		myComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				Object newSelection = myComboBox.getSelectedItem();
				if (newSelection != currentSelection) {
					SpatioTemporalFusion stf = SpatioTemporalFusion.getInstance(SignalTransformationKernel.this);
					if (stf != null) {
						synchronized (stf.getFilteringLock()) {
							currentSelection = newSelection;
							if (currentSelection instanceof FiringModelMap)
								setInputMap((FiringModelMap)currentSelection);
							else 
								setInputMap(null);
						}

					}

				}
			}
		});
		return myPanel;
	}
	
	@SuppressWarnings("unchecked")
	protected void updateComboBox(ArrayList<FiringModelMap> oldContents, ArrayList<FiringModelMap> newContents) {
		Object selection = myComboBox.getSelectedItem();
		if (myComboBox.getItemCount() == 0) {
			selection = getInputMap();
		}
		myComboBox.removeAllItems();
		if (newContents != null) {
			myComboBox.addItem("none");
			for (FiringModelMap map : newContents) {
				myComboBox.addItem(map);
				if (!oldContents.contains(map))
					map.getSupport().addPropertyChangeListener("name",this);
			}
			if (newContents.contains(selection))
				myComboBox.setSelectedItem(selection);
			else if (selection == null) 
				myComboBox.setSelectedIndex(0);
		}
		if (oldContents != null) {
			for (FiringModelMap map : oldContents) {
				if (!newContents.contains(map)) 
					map.getSupport().removePropertyChangeListener(this);
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	public void propertyChange(PropertyChangeEvent propertyChangeEvent) {
		super.propertyChange(propertyChangeEvent);
		SpatioTemporalFusion stf = SpatioTemporalFusion.getInstance(this);
		if (propertyChangeEvent.getSource() == stf) {
			if (propertyChangeEvent.getPropertyName().equals("firingModelMaps")) {
				updateComboBox((ArrayList<FiringModelMap>)propertyChangeEvent.getOldValue(),(ArrayList<FiringModelMap>)propertyChangeEvent.getNewValue());
			}
		}
		else if (propertyChangeEvent.getSource() instanceof FiringModelMap) {
			ArrayList<FiringModelMap> contents;
			if (stf != null) contents = stf.getFiringModelMaps();
			else contents = new ArrayList<FiringModelMap>();
			updateComboBox(contents, contents);
		}
	}

	@Override
	public void restoreParameters() {
    	super.restoreParameters();
    	int inputMapID = getPrefs().getInt("inputMapID",-1);
    	if (inputMapID >= 0) {
    		ArrayList<FiringModelMap> maps = SpatioTemporalFusion.getInstance(this).getFiringModelMaps();
    		synchronized (maps) { 
    			for (FiringModelMap map : maps) {
    				if (map.getMapID() == inputMapID)
    					this.setInputMap(map);
    			}
    		}
    	}
    }
	
	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		getSupport().firePropertyChange("enabled", this.enabled, enabled);
		this.enabled = enabled;
	}
	
	protected void disconnectKernel() {
		SpatioTemporalFusion stf = SpatioTemporalFusion.getInstance(this);
		ArrayList<FiringModelMap> contents;
		if (stf != null) {
			// stop listening to changes in firingModelMaps:
			stf.getSupport().removePropertyChangeListener(this);
			contents = stf.getFiringModelMaps();
		}
		else 
			contents = new ArrayList<FiringModelMap>();
		// stop listening to changes to the list of available maps:
		updateComboBox(contents,new ArrayList<FiringModelMap>());
		
		setInputMap(null);
		setOutputMap(null);
		try {
			getPrefs().removeNode();
		} catch (BackingStoreException e) {
			e.printStackTrace();
		} catch (IllegalStateException e) {
			e.printStackTrace();
		}
	}
	
	public void doDelete() {
		FiringModelMap map = getOutputMap();
		if (map != null)
			map.removeKernel(this);
	}

	
	
//	public int getOffsetX();
//	public int getOffsetY();
//	public void setOffsetX(int offsetX);
//	public void setOffsetY(int offsetY);
}
