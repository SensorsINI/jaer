/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import net.sf.jaer.event.PolarityEvent;

/**
 * @author Dennis
 *
 */
public class SimpleKernelProcessor extends KernelProcessor {

	InputKernel inputKernel;
	FiringModelMap firingModelMap;
	SpikeHandlerSet spikeHandler;

	
	
	String onExpression = "1";//getPrefs().get("Expression", "1");
	String offExpression = "1";//getPrefs().get("Expression", "1");


	/**
	 * 
	 */
	public SimpleKernelProcessor(int outSizeX, int outSizeY, InputKernel inputKernel) {
//		firingModelMap = new ArrayFiringModelMap(outSizeX, outSizeY, IntegrateAndFire.getCreator());
		firingModelMap = new ArrayFiringModelMap(outSizeX, outSizeY, LeakyIntegrateAndFire.getCreator(36000, 7000,1.5f));
		spikeHandler = new SpikeHandlerSet();
		this.inputKernel = inputKernel;
	}

	public void addSpikeHandler(SpikeHandler handler) {
		spikeHandler.addSpikeHandler(handler);
	}
	public void removeSpikeHandler(SpikeHandler handler) {
		spikeHandler.removeSpikeHandler(handler);
	}
	
	public String getOnExpression() {
		return onExpression;
	}

	public void setOnExpression(String onExpression) {
		this.onExpression = onExpression;
	}

	public String getOffExpression() {
		return offExpression;
	}

	public void setOffExpression(String offExpression) {
		this.offExpression = offExpression;
	}

	
	@Override
	protected void processSpike(int x, int y, int time, PolarityEvent.Polarity polarity) {
		inputKernel.apply(x, y, time, polarity, firingModelMap, spikeHandler);
	}

}
