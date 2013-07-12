/*
 * CircularConvolutionFilter.java
 *
 * Created on 24.2.2006 Tobi
 *
 */

package net.sf.jaer.eventprocessing.filter;

import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;

import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Computes circular convolutions by splatting out events and checking receiving pixels to see if they exceed a threshold.
 * A behavioral model of Raphael/Bernabe convolution chip, but limited in that it presently only allows positive binary kernel weights, thus
 *the output events can be triggered by lots of input activity.
 *
 * @author tobi
 */
@Description("Computes circular convolutions by splatting out events and checking receiving pixels to see if they exceed a threshold")
public final class CircularConvolutionFilter extends EventFilter2D implements Observer, FrameAnnotater {

	static final int NUM_INPUT_CELL_TYPES=1;
	private boolean useBalancedKernel=getBoolean("useBalancedKernel",true);


	/** the number of cell output types */
	public final int NUM_OUTPUT_TYPES=1; // we make it big so rendering is in color

	/** Creates a new instance of CircularConvolutionFilter */
	public CircularConvolutionFilter(AEChip chip) {
		super(chip);
		chip.addObserver(this);
		resetFilter();
		setFilterEnabled(false);
		setPropertyTooltip("useBalancedKernel","balances kernel to zero sum with positive and negative weights");
	}

	@Override
	synchronized public EventPacket filterPacket(EventPacket in) {
		checkOutputPacketEventType(in);
		int sx=chip.getSizeX()-1;
		int sy=chip.getSizeY()-1;
		int n=in.getSize();

		OutputEventIterator oi=out.outputIterator();
		for(Object o:in){
			PolarityEvent e=(PolarityEvent)o;
			x=e.x;
			y=e.y;
			ts=e.timestamp;

			for(Splatt s:splatts){
				int xoff=x+s.x; if((xoff<0)||(xoff>sx))
				{
					continue; //precheck array access
				}
				int yoff=y+s.y; if((yoff<0)||(yoff>sy)) {
					continue;
				}

				float dtMs=(ts-convolutionLastEventTime[xoff][yoff])*1e-3f;
				float vmold=convolutionVm[xoff][yoff];
				vmold=(float)(vmold*(Math.exp(-dtMs/tauMs)));
				float vm=vmold+s.weight;
				convolutionVm[xoff][yoff]=vm;
				convolutionLastEventTime[xoff][yoff]=ts;
				if( vm>threshold ){
					PolarityEvent oe=(PolarityEvent)oi.nextOutput();
					oe.copyFrom(e);
					oe.x=(short)xoff;
					oe.y=(short)yoff;
					convolutionVm[xoff][yoff]=0;
				}
			}
		}
		return out;
	}


	@Override
	synchronized public void resetFilter() {
		allocateMap();
	}

	@Override
	public void annotate(GLAutoDrawable drawable) {
		GL2 gl=drawable.getGL().getGL2();
		final int xp=-10, yp=10; // where to draw it
		final int sz=3; // how big each one
		for (Splatt s : splatts) {
			if (s.weight > 0) {
				gl.glColor3f(0, s.weight, 0);
			} else {
				gl.glColor3f(-s.weight,0, 0);
			}
			gl.glRectf(xp + ((s.x-.5f) * sz), yp + ((s.y-.5f) * sz), xp + ((s.x + .5f) * sz), yp + ((s.y + .5f) * sz));
		}
	}

	final class Splatt{
		int x, y;
		float weight=1;
		Splatt(){
			x=0;
			y=0;
			weight=1;
		}
		Splatt(int x, int y){
			this.x=x;
			this.y=y;
		}
		Splatt(int x, int y, float w){
			this.x=x;
			this.y=y;
			weight=w;
		}
		@Override
		public String toString(){
			return "Splatt: "+x+","+y+","+String.format("%.1f",weight);
		}
	}

	private Splatt[] splatts;

	// computes the indices to splatt to from a source event
	// these are octagonal around a point to the neighboring pixels at a certain radius
	// eg, radius=0, impulse kernal=identity kernel
	// radius=1, nearest neighbors in 8 directions
	// radius=2 octagon?
	// TODO include negative weights where there is no kernel circle to end with zero net kernel sum
	synchronized void computeSplattLookup(){
		ArrayList<Splatt> list=new ArrayList<Splatt>();
		double circum=2*Math.PI*radius; // num pixels
		int xlast = -1, ylast = -1;
		int n = (int) Math.ceil(circum);
		if (radius < 3) {

			switch (radius) {
				case 0: // identity
					list.add(new Splatt());
					break;
				case 1:
					list.add(new Splatt(1, 0,.25f));
					list.add(new Splatt(0, 1,.25f));
					list.add(new Splatt(-1, 0,.25f));
					list.add(new Splatt(0, -1,.25f));
					if (useBalancedKernel) {
						list.add(new Splatt(0, 0, -1));
					}
					break;
				case 2:
					list.add(new Splatt(1, 0));
					list.add(new Splatt(0, 1));
					list.add(new Splatt(-1, 0));
					list.add(new Splatt(0, -1));
					list.add(new Splatt(1, 1));
					list.add(new Splatt(-1, 1));
					list.add(new Splatt(-1, -1));
					list.add(new Splatt(1, -1));
					if (useBalancedKernel) {
						list.add(new Splatt(0, 0, -8));
					}
					float f=1f/list.size();
					for(Splatt s:list){
						s.weight*=f;
					}

					break;
				default:
			}
		} else {
			for (int i = 0; i < n; i++) {
				double theta = (2 * Math.PI * i) / circum;
				double xoff = Math.cos(theta) * radius;
				double yoff = Math.sin(theta) * radius;
				double xround = Math.round(xoff);
				double yround = Math.round(yoff);
				if ((xlast != xround) || (ylast != yround)) { // dont make multiple copies of the same splatt around the circle
					Splatt s = new Splatt();
					s.x = (int) xround;
					s.y = (int) yround;
					s.weight = 1; //(float)(1-Math.sqrt((x-xround)*(x-xround)+(y-yround)*(y-yround)));
					xlast = s.x;
					ylast = s.y;
					list.add(s);
				}
			}
		}
		//        log.info("splatt has "+list.size()+" +1 elements");
		if(radius>2){
			// make negative outside ring, 1/2 weight
			xlast=-1; ylast=-1;
			for(int i=0;i<n;i++){
				double theta=(2*Math.PI*i)/circum;
				double off=(Math.cos(theta)*radius)+1;
				double yoff=(Math.sin(theta)*radius)+1;
				double xround=Math.round(off);
				double yround=Math.round(yoff);
				if((xlast!=xround) || (ylast!=yround)){ // dont make multiple copies of the same splatt around the circle
					Splatt s=new Splatt();
					s.x=(int)xround;
					s.y=(int)yround;
					s.weight= -0.5f; //(float)(1-Math.sqrt((x-xround)*(x-xround)+(y-yround)*(y-yround)));
					xlast=s.x; ylast=s.y;
					list.add(s);
				}
			}
			xlast=-1; ylast=-1;
			for(int i=0;i<n;i++){
				double theta=(2*Math.PI*i)/circum;
				double xoff=(Math.cos(theta)*radius)+-1;
				double yoff=(Math.sin(theta)*radius)-1;
				double xround=Math.round(xoff);
				double yround=Math.round(yoff);
				if((xlast!=xround) || (ylast!=yround)){ // dont make multiple copies of the same splatt around the circle
					Splatt s=new Splatt();
					s.x=(int)xround;
					s.y=(int)yround;
					s.weight= -0.5f; //(float)(1-Math.sqrt((x-xround)*(x-xround)+(y-yround)*(y-yround)));
					xlast=s.x; ylast=s.y;
					list.add(s);
				}
			}
		}
		//        log.info("splatt has "+list.size()+" total elements");
		float sum=0;
		Object[] oa=list.toArray();
		splatts=new Splatt[oa.length];
		for(int i=0;i<oa.length;i++){
			splatts[i]=(Splatt)oa[i];
			sum+=splatts[i].weight;
		}
		log.info("splatt total weight = "+sum+" num weights="+splatts.length);

	}

	int PADDING=0, P=0;
	float[][] convolutionVm;
	int[][] convolutionLastEventTime;

	private void allocateMap() {
		if((chip.getSizeX()==0) || (chip.getSizeY()==0)){
			//            log.warning("tried to allocateMap in CircularConvolutionFilter but chip size is 0");
			return;
		}
		//        PADDING=2*radius;
		//        P=radius;
		convolutionVm=new float[chip.getSizeX()][chip.getSizeY()];
		convolutionLastEventTime=new int[chip.getSizeX()][chip.getSizeY()];
		computeSplattLookup();
	}

	private short x,y;
	private byte type;
	private int ts;

	@Override
	public void initFilter() {
		resetFilter();
	}

	@Override
	public void update(Observable o, Object arg) {
		if(!isFilterEnabled()) {
			return;
		}
		initFilter();
	}

	private int radius=getInt("radius",3);
	{setPropertyTooltip("radius","radius in pixels of kernal");}

	public int getRadius() {
		return radius;
	}

	synchronized public void setRadius(int radius) {
		if(radius<0) {
			radius=0;
		}
		else if(radius>chip.getMaxSize()) {
			radius=chip.getMaxSize();
		}
		if(radius!=this.radius) {
			this.radius = radius;
			putInt("radius",radius);
			resetFilter();
		}
	}

	private float tauMs=getFloat("tauMs",10f);
	{setPropertyTooltip("tauMs","time constant in ms of integrator neuron potential decay");}

	public float getTauMs() {
		return tauMs;
	}

	synchronized public void setTauMs(float tauMs) {
		if(tauMs<0) {
			tauMs=0;
		}
		else if(tauMs>10000) {
			tauMs=10000f;
		}
		this.tauMs = tauMs;
		getPrefs().putFloat("CircularConvolutionFilter.tauMs",tauMs);
	}

	private float threshold=getFloat("threshold",1f);
	{setPropertyTooltip("threshold","threahold on ms for firing output event from integrating neuron");}

	public float getThreshold() {
		return threshold;
	}

	synchronized public void setThreshold(float threshold) {
		if(threshold<0) {
			threshold=0;
		}
		else if(threshold>100) {
			threshold=100;
		}
		this.threshold = threshold;
		putFloat("threshold",threshold);
	}


}
