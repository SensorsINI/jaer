/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;


import net.sf.jaer.event.PolarityEvent.Polarity;

/**
 * @author Dennis
 *
 */
public class NativeConvolutionTest extends SignalTransformationKernel {

	static {
		 System.loadLibrary("NativeConvolution"); 
	}
	private long nativeConvolutionInstance = 0;
	
	/** 
	 * 
	 */
	public NativeConvolutionTest() {
	}

	/* (non-Javadoc)
	 * @see ch.unizh.ini.jaer.projects.apsdvsfusion.InputKernel#apply(int, int, int, net.sf.jaer.event.PolarityEvent.Polarity, ch.unizh.ini.jaer.projects.apsdvsfusion.FiringModelMap, ch.unizh.ini.jaer.projects.apsdvsfusion.SpikeHandler)
	 */
	
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

//	@Override
//	public int getOffsetX() {
//		return 0;
//	}
//
//	@Override
//	public int getOffsetY() {
//		return 0;
//	}
//
//	@Override
//	public void setOffsetX(int offsetX) {
//		
//	}
//
//	@Override
//	public void setOffsetY(int offsetY) {
//		
//	}

	@Override
	public void signalAt(int x, int y, int time, double value) {
		
	}

}
