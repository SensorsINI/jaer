/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import java.util.prefs.Preferences;

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
	
	
//	public int getOffsetX();
//	public int getOffsetY();
//	public void setOffsetX(int offsetX);
//	public void setOffsetY(int offsetY);
}
