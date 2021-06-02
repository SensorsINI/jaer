/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion.firingmodel;

/**
 * @author Dennis
 *
 */
public class IntegerDecayModel {
	private int value;
	private int tau = (int)(1.0/Math.log(2.0));
	
	private final static int shifter = 20;
	private final static int multValueDivisorShift = 10;	
	private final static int multValueCounterShift = 14;

	public final static int ONE = 1 << shifter;
	public final static int HALF = ONE >> 1;
	public final static int QUARTER = ONE >> 2;
	public final static int MINUSONE = -ONE;
	public final static int MINUSHALF = -HALF;
	public final static int MINUSQUARTER = -QUARTER;
	
	private int multiplicator = ONE;
//	private final int intThreshold = 1 << shifter;
//	private int intMembranePotential = 0;
	
	private final static int multValueCounter = 1 << multValueCounterShift;
	private final static int multValues[] = new int[multValueCounter];
	static {
		for (int i = 0; i < multValues.length; i++) {
			multValues[i] = (int)(Math.pow(2.0,((double)(-(i))) / ((double)multValueCounter)) * ((double)(1 << multValueDivisorShift)));
		}
	}
	
	/**
	 * 
	 */
	public IntegerDecayModel() {
	}

	public int getIntValue() {
		return value;
	}

	public void setIntValue(int value) {
		this.value = value;
	}
	
	public float getValue() {
		return (float)value / (float)multiplicator;
	}

	public void setValue(float value) {
		this.value = (int)(value * multiplicator);
	}

	public void setValue(double value) {
		this.value = (int)(value * multiplicator);
	}
	
	public void setTimeConstant(float tau) {
		this.tau = (int)(Math.log(2.0)*tau);
	}
	
	public float getTimeConstant() {
		return (float)(tau / Math.log(2.0));
	}
	
	public void setIntTimeConstant(int tau) {
		this.tau = tau;
	}
	
	public int getIntTimeConstant() {
		return tau;
	}

	public void add(float value) {
		this.value += ((int)(value * multiplicator));
	}
	
	public void add(double value) {
		this.value += ((int)(value * multiplicator));
	}

//	public void multiply(double )
	
	public void setMultiplicator(float multiplicator) {
		this.multiplicator = (int)((ONE) * (multiplicator));
	}
	
	public float getMultiplicator() {
		return (float)this.multiplicator / (float)ONE;
	}
	
	public void decay(int timePassed) {
		final int reductions = timePassed / tau;
		value *= multValues[(int)((((long)(timePassed - (reductions * tau))) << multValueCounterShift) / tau)];
		value >>= (reductions+multValueDivisorShift);
	}
	public static void main(String[] args) {
		int x = (- 1)<<30;
		System.out.println(" << 1: "+(x<<1));
		System.out.println(" << 2: "+(x<<2));
		System.out.println(" << 2 >> 1: "+((x<<2 ) >> 1));
		System.out.println(" >> 0: "+(x>>0));
		System.out.println(" >> 10: "+(x>>10));
		System.out.println(" >> 19: "+(x>>19));
	}

}
