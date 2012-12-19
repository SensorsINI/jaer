/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import java.util.LinkedList;

import net.sf.jaer.event.PolarityEvent.Polarity;

/**
 * @author Dennis
 *
 */
public class NativeConvolutionTest implements InputKernel {

	static {
		 System.loadLibrary("NativeConvolution"); 
	}
	private long nativeConvolutionInstance = 0;
	
	/** 
	 * 
	 */
	public NativeConvolutionTest() {
		// TODO Auto-generated constructor stub
	}

	/* (non-Javadoc)
	 * @see ch.unizh.ini.jaer.projects.apsdvsfusion.InputKernel#apply(int, int, int, net.sf.jaer.event.PolarityEvent.Polarity, ch.unizh.ini.jaer.projects.apsdvsfusion.FiringModelMap, ch.unizh.ini.jaer.projects.apsdvsfusion.SpikeHandler)
	 */
	@Override
	public void apply(int x, int y, int time, Polarity polarity,
			FiringModelMap map, SpikeHandler spikeHandler) {
		// TODO Auto-generated method stub

	}
	
	protected void spike(int x, int y) {
		System.out.format("Spike at %d/%d!", x,y);
	}
	private native void runNative(int x, int y, int time, NativeConvolutionTest spikeHandler);
	private native long initNativeSimpleClass();
    private native void destroyNativeSimpleClass();
    
    public void destroy() {
        destroyNativeSimpleClass();
        nativeConvolutionInstance = 0L;
    }
    
    protected void finalize() throws Throwable {
        destroyNativeSimpleClass();
        nativeConvolutionInstance = 0L;
    }
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		NativeConvolutionTest test = new NativeConvolutionTest();
		test.initNativeSimpleClass();
		System.out.println("piep");
		test.runNative(1,1,1,test);
	}

}
