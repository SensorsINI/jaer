/*
 * RetinaEventExtractor.java
 *
 * Created on September 1, 2005, 4:47 PM
 */

package ch.unizh.ini.jaer.chip.retina;

import java.io.Serializable;

/**
 * Extracts x,y,pol from testchipARCs double line sensor retina. You pass in a raw address array, then internally, arrays are new'ed and
 *the events are extracted to them. Results are accessed by referenc to fields like x, y that have been extracted.
 *See the matlab function showLiveRetina, for example.
 *
 * @author tobi
 */
public class TestchipARCSLineSensorEventExtractor implements Serializable {
    final int xmax=63, ymax=1;

    /** these fields reference the extracted events after
     * <@link #extract} is called on a raw address buffer
     * x,y 0-127, pol=-1,1
     *<p>
     *These are doubles because matlab likes doubles.
     */
    public double[] x,y,pol;
    int[] addresses;

    /** Creates a new instance of RetinaEventExtractor */
    public TestchipARCSLineSensorEventExtractor() {
    }

    public void extract(int[] addresses, int[] timestamps){
        int n;
        int tmp;
        if(addresses==null) {
			n=0;
		}
		else {
			n=addresses.length;
		}
        x=new double[n];
        y=new double[n];
        pol=new double[n];
        if(n>0){
            for(int i=0;i<addresses.length;i++){
                int a=addresses[i];
                tmp=(a&0x3f); tmp=Math.max(tmp,0); tmp=Math.min(tmp,xmax);
                x[i]=tmp;
                tmp=(a&0x40)>>6; tmp=Math.max(tmp,0); tmp=Math.min(tmp,ymax);
                y[i]=tmp;
                pol[i]=(a&0x80)!=0?1:-11;
            }
        }
    }

}

