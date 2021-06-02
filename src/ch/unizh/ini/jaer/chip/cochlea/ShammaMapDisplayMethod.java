/*
 * RetinaCanvas.java
 *
 * Created on January 9, 2006, 6:50 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.chip.cochlea;


import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.fixedfunc.GLMatrixFunc;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Observable;
import java.util.Observer;


import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.DisplayMethod;
import net.sf.jaer.graphics.DisplayMethod2D;

/**
 * Shows events from stereo cochlea as Shamma map.
 * @author tobi
 */
public class ShammaMapDisplayMethod extends DisplayMethod implements DisplayMethod2D, Observer, PropertyChangeListener{

	private ShammaMap shammaMap;

	/**
	 * Creates a new instance of CochleaGramDisplayMethod
	 *
	 * @param c the canvas we are drawing on
	 */
	public ShammaMapDisplayMethod(ChipCanvas c) {
		super(c);
		shammaMap=new ShammaMap();
		chip.addObserver(this); // so when chip size is set in specific chip constructor we allocate memory
	}

	private class ShammaMap{
		float tauMs=20;
		ShammaMap(){
			allocateArrays();
		}
		private float[][] mapOutput; // this holds correlation values
		private int[][] lastTimesMap; // holds last event times from each cochlea as [y][x][ear]
		private int[][] lastMapUpdateTime;
		private float scaling=1f;
		private float maxCorr, minCorr;

		void allocateArrays(){
			mapOutput=new float[chip.getSizeX()][chip.getSizeX()];
			lastTimesMap=new int[chip.getSizeY()][chip.getSizeX()];
			lastMapUpdateTime=new int[chip.getSizeX()][chip.getSizeX()];
		}

		synchronized void reset(){
			allocateArrays();
			maxCorr=Float.NEGATIVE_INFINITY; minCorr=Float.POSITIVE_INFINITY;
		}

		float corrValue(int dt){
			if(dt==0) {
				return 1;
			}
			if(dt<0) {
				//                System.out.println("backwards timestamp dt="+dt);
				return 0;
			}
			float c=scaling/dt;
			if(c>1) {
				c=1;
			}
			return c;
		}

		void setScaling(float s){
			scaling=s;
		}

		void setScaling(){
			if(getRenderer()!=null){
				setScaling(10000f/(1<<getRenderer().getColorScale()));
			}else{
				setScaling(1000f);
			}
		}

		// for each event, we iterate over all mapOutput cells
		// for each event
		// store the time of the cochlea event in lastTimesMap
		// for each corr cell mapOutput for this cochlea tap
		// look up the last time the corr cell was updated in lastMapUpdateTime
		// compute the updated corr value decayed according to the dt since last update
		// compute the new corr based on the event time and the last time there was an event in the other cochlea
		// update the corr value by this corr
		// store the update time in the corr cell
		void processEvent(BinauralCochleaEvent e){
			lastTimesMap[e.y][e.x]=e.timestamp; // this is cochlea event time
			int n=chip.getSizeX();
			if(e.getEar()==BinauralCochleaEvent.Ear.RIGHT){ // right cochlea, iterate over left taps
				for(int i=0;i<n;i++){
					int dt=e.timestamp-lastTimesMap[1][i]; // dt is diff between this event and previous left tap
					if(dt<0) {
						reset();
					}
					float cval=corrValue(dt);
					float oldcval=mapOutput[i][e.x];
					float updatedcval=getDecayedValue(oldcval,e.timestamp-lastMapUpdateTime[i][e.x]);
					float newval=updatedcval+cval;
					mapOutput[i][e.x]=newval;
					lastMapUpdateTime[i][e.x]=e.timestamp;
					updateMaxMin(newval);
				}
			}else{ // left, iterate right taps
				for(int i=0;i<n;i++){
					int dt=e.timestamp-lastTimesMap[0][i];
					if(dt<0) {
						reset();
					}
					float cval=corrValue(dt);
					float oldcval=mapOutput[e.x][i];
					float updatedcval=getDecayedValue(oldcval,e.timestamp-lastMapUpdateTime[e.x][i]);
					float newval=updatedcval+cval;
					mapOutput[e.x][i]=newval;
					lastMapUpdateTime[e.x][i]=e.timestamp;
					updateMaxMin(newval);
				}
			}
		}

		float normalizer=1;
		float getNormMapOutput(int y, int x, int time){
			float nval=getDecayedValue(mapOutput[y][x],time-lastMapUpdateTime[y][x]);
			float normVal=nval/normalizer;
			return normVal; // gray value 0-1
		}

		void updateMaxMin(float v){
			if(v<minCorr) {
				minCorr=v;
			}
			if(v>maxCorr) {
				maxCorr=v;
			}
		}

		synchronized void processPacket(EventPacket ae){ // save event times for this packet
			int n = ae.getSize();
			shammaMap.setScaling();

			// assumption, cochlea has y=2 channels, x taps

			for(Object o:ae){
				BinauralCochleaEvent e=(BinauralCochleaEvent)o;
				processEvent(e);
			}
			normalizer=Math.max(Math.abs(maxCorr),Math.abs(minCorr));
		}

		private float getDecayedValue(float oldcval, int dt) {
			if(dt<0) {
				return oldcval;
			}
			double dtnorm=dt/1000f/tauMs; // dt is us, tau is ms
			float fac=(float)Math.exp(-dtnorm);
			float newval=oldcval*fac;
			return newval;
		}
	}

	final int BORDER=80; // pixels

	/** displays individual events as shamma cross correlation map.
	 * @param drawable the drawable passed in by OpenGL
	 */
	@Override
	public void display(GLAutoDrawable drawable){
		//        GL2 gl=setupGL(drawable);

		// render events

		EventPacket<TypedEvent> packet=(EventPacket)chip.getLastData();;
		if((packet==null) || (packet.getSize()==0)) {
			return;
		}
		shammaMap.processPacket(packet);
		// draw results
		GL2 gl = drawable.getGL().getGL2();
		// make sure we're drawing back buffer (this is probably true anyhow)
		gl.glDrawBuffer(GL.GL_BACK);
		gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
		gl.glLoadIdentity(); // very important to load identity matrix here so this works after first resize!!!
		gl.glOrtho(-BORDER,drawable.getSurfaceWidth()+BORDER,-BORDER,drawable.getSurfaceHeight()+BORDER,10000,-10000);
		gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
		gl.glClearColor(0,0,0,0f);
		gl.glClear(GL.GL_COLOR_BUFFER_BIT);
		gl.glLoadIdentity();
		// translate origin to this point
		gl.glTranslatef(0,0,0);
		// scale everything by rastergram scale
		float ys=(drawable.getSurfaceHeight())/(float)chip.getSizeX();// scale vertical is draableHeight/numPixels
		float xs=ys;
		gl.glScalef(xs,ys,1);

		try{
			int t=packet.getLastTimestamp();
			// now iterate over the frame (mapOutput)
			int m=chip.getSizeX();
			for (int x = 0; x < m; x++){
				for (int y = 0; y < m; y++){
					float f = shammaMap.getNormMapOutput(y,x,t);
					//                    if(f==gray) continue;
					//                    int x = i,  y = j; // dont flip y direction because retina coordinates are from botton to top (in user space, after lens) are the same as default OpenGL coordinates
					gl.glColor3f(f,f,f);
					gl.glRectf(x-.5f,y-.5f, x+.5f, y+.5f);
				}
			}
			// now plot cochlea activities as earlier rendered by ChipRenderer
			float[] fr=getRenderer().getPixmapArray();
			int y;
			y=0; // right
			for(int x=0;x<m;x++){
				int ind=getRenderer().getPixMapIndex(x, y);
				gl.glColor3f(fr[ind],fr[ind+1],fr[ind+2]);
				gl.glRectf(x-.5f,y-2, x+.5f, y-1);
			}
			y=1; // left
			for(int x=0;x<m;x++){
				int ind=getRenderer().getPixMapIndex(x, y);
				gl.glColor3f(fr[ind],fr[ind+1],fr[ind+2]);
				gl.glRectf(-2, x-.5f, -1, x+.5f);
			}

		}catch(ArrayIndexOutOfBoundsException e){
			log.warning("while drawing frame buffer");
			e.printStackTrace();
			getChipCanvas().unzoom(); // in case it was some other chip had set the zoom
			gl.glPopMatrix();
		}
		// outline frame
		gl.glColor4f(0,0,1f,0f);
		gl.glLineWidth(1f);
		gl.glBegin(GL.GL_LINE_LOOP);
		final float o = .5f;
		final float w = chip.getSizeX()-1;
		final float h = chip.getSizeX()-1;
		gl.glVertex2f(-o,-o);
		gl.glVertex2f(w+o,-o);
		gl.glVertex2f(w+o,h+o);
		gl.glVertex2f(-o,h+o);
		gl.glEnd();


	}

	@Override
	public void update(Observable o, Object arg) {
		shammaMap.reset();
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if((evt.getPropertyName()==AEInputStream.EVENT_POSITION) && (((Integer)evt.getNewValue()).intValue()==0)){
			log.info("got rewind event, resetting map");
			shammaMap.reset();
		}
	}

}
