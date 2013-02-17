/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.prefs.Preferences;

import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import ch.unizh.ini.jaer.projects.apsdvsfusion.FiringModelMap.FiringModelMapCustomControls;

import net.sf.jaer.event.PolarityEvent;

/**
 * @author Dennis Goehlsdorf
 *
 */
public abstract class SignalTransformationKernel extends ParameterContainer implements SpikeHandler  {
//	public void apply(int x, int y, int time, PolarityEvent.Polarity polarity, FiringModelMap map, SpikeHandler spikeHandler);
//	public void apply(int x, int y, int time, PolarityEvent.Polarity polarity, FiringModelMap map, SpikeHandler spikeHandler);
//	public void addOffset(int x, int y);
	
	//	public 
	private int inputWidth, inputHeight;
	private int outputWidth, outputHeight;
	
	private FiringModelMap inputMap;
	private FiringModelMap outputMap;
	
	public SignalTransformationKernel(String name, Preferences parentPrefs,	String nodeName) {
		super(name, parentPrefs, nodeName);
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

	protected void intputSizeChanged(int oldWidth, int oldHeight, int newWidth, int newHeight) {
	}
	
	public synchronized void setInputSize(int width, int height) {
		if (width != inputWidth || height != inputHeight) {
			int dw = this.inputWidth;
			int dh = this.inputHeight;
			this.inputWidth = width;
			this.inputHeight = height;
			outputSizeChanged(dw, dh, inputWidth, inputHeight);
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
		this.inputMap = inputMap;
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
		this.outputMap = outputMap;
	}
	
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

		myPanel.add(myComboBox);
		
		myComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				Object newSelection = myComboBox.getSelectedItem();
				if (newSelection != currentSelection) {
					currentSelection = newSelection;
					setInputMap((FiringModelMap)currentSelection);
				}
			}
		});
		return myPanel;
	}
	
	protected void updateComboBox(ArrayList<FiringModelMap> oldContents, ArrayList<FiringModelMap> newContents) {
		Object selection = myComboBox.getSelectedItem();
		myComboBox.removeAllItems();
		if (newContents != null) {
			for (FiringModelMap map : newContents) {
				myComboBox.addItem(map);
				if (!oldContents.contains(map))
					map.getSupport().addPropertyChangeListener("name",this);
			}
			if (newContents.contains(selection))
				myComboBox.setSelectedItem(selection);
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
	

//	public int getOffsetX();
//	public int getOffsetY();
//	public void setOffsetX(int offsetX);
//	public void setOffsetY(int offsetY);
}
