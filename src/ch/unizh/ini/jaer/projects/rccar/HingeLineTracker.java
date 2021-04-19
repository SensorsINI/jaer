/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.rccar;


import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Arrays;
import java.util.Observable;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.fixedfunc.GLMatrixFunc;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUquadric;
import javax.swing.JFrame;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 *
 * @author braendch
 * The hingeLaneTracker tries to approximate a more or less vertical line by setting hinges at different heights
 * The incomming events activate the AccumArray which contains row cells that subsample the vertical lines
 * The row cell of a row gets the hinge if it fulfils some exception handling conditions
 * As output the getPhi() and getX() methods can be used because the produce a weightend average of the phi and x
 * values of a line approximation
 * To make the algorithm more stable, already set hinges produce an attention which makes events wihin this attetion
 * more excite the row cells
 *
 */
public class HingeLineTracker extends EventFilter2D implements FrameAnnotater,HingeDetector {

	private float hingeThreshold=getPrefs().getFloat("LineTracker.hingeThreshold",2.5f);
	{setPropertyTooltip("hingeThreshold","the threshold for the hinge to react");}
	private float attentionRadius=getPrefs().getFloat("LineTracker.attentionRadius",12);
	{setPropertyTooltip("attentionRadius","the size of the attention balls");}
	private float attentionFactor=getPrefs().getFloat("LineTracker.attentionFactor",2);
	{setPropertyTooltip("attentionFactor","how much is additionally added to the accumArray if the attention is on a certain spike");}
	private float hingeDecayFactor=getPrefs().getFloat("LineTracker.hingeDecayFactor",0.6f);
	{setPropertyTooltip("hingeDecayFactor","hinge accumulator cells are multiplied by this factor before each frame, 0=no memory, 1=infinite memory");}
	private float attentionDecayFactor=getPrefs().getFloat("LineTracker.attentionDecayFactor",0.6f);
	{setPropertyTooltip("attentionDecayFactor","the slope of attention decay, 0=no memory, 1=infinite memory");}
	private int shiftSpace=getPrefs().getInt("LineTracker.shiftSpace",5);
	{setPropertyTooltip("shiftSpace","minimal distance between paoli hinge and seperation");}
	private int topHinge=getPrefs().getInt("LineTracker.topHinge",80);
	{setPropertyTooltip("topHinge","the horizontal position of the top hinge (in px)");}
	private int bottomHinge=getPrefs().getInt("LineTracker.bottomHinge",40);
	{setPropertyTooltip("bottomHinge","the horizontal position of the bottom hinge (in px)");}
	private int hingeNumber=getPrefs().getInt("LineTracker.hingeNumber",4);
	{setPropertyTooltip("hingeNumber","the number of hinges to be set");}
	private boolean showRowWindow=false;
	{setPropertyTooltip("showRowWindow","");}
	private boolean drawOutput=getPrefs().getBoolean("LineTracker.drawOutput",false);
	{setPropertyTooltip("drawOutput","should the output be drawn");}

	//the row cell activity
	private float[][] accumArray;
	//the attention matrix
	private float[][] attentionArray;
	//hingeMax is the row cell value for a hinge with a given number
	private float[] hingeMax;
	//maxIndex is its x-value
	private int[] maxIndex;
	//the maxIndexHistory saves the x-values of the most recent past hinges
	private int[] maxIndexHistory;
	//the hingeArray saves the y-value of a line where a hinge can be placed
	private int[] hingeArray;
	//isPaoli stands for is PArt Of a LIne --> if =false, the hinge waits at the frameborder
	private boolean[] isPaoli;
	//the highest value of attention
	private float attentionMax;
	//sizes of the chip
	private int sx;
	private int sy;

	//the sizes of the receptive field of a row cell
	private int height = 5;
	private int width = 4; //should be even
	// the last output values
	private float xValue = 0;
	private float phiValue = 0;


	FilterChain preFilterChain;
	private PerspecTransform perspecTransform;
	private OrientationCluster orientationCluster;


	public HingeLineTracker(AEChip chip) {
		super(chip);

		//build hierachy
		preFilterChain = new FilterChain(chip);
		perspecTransform = new PerspecTransform(chip);
		orientationCluster = perspecTransform.getOrientationCluster();

		this.setEnclosedFilter(perspecTransform);

		perspecTransform.setEnclosed(true, this);


		initFilter();
		resetFilter();
	}

	private void checkMaps(){
		//it has to be checked if the VectorMap fits on the actual chip
		if((accumArray==null)
			|| (accumArray.length!=hingeNumber)
			|| (accumArray[0].length!=chip.getSizeX())) {
			allocateMaps();
		}
	}

	synchronized private void allocateMaps() {
		//the VectorMap is fitted on the chip size
		if(!isFilterEnabled()) {
			return;
		}
		log.info("HingeLineTracker.allocateMaps()");

		sx=chip.getSizeX();
		sy=chip.getSizeY();

		if(chip!=null){
			accumArray= new float[hingeNumber][sx];
			attentionArray= new float[sx+1][sy];
			hingeMax= new float[hingeNumber];
			hingeArray= new int[hingeNumber];
			maxIndex= new int[hingeNumber];
			maxIndexHistory= new int[hingeNumber];
			isPaoli= new boolean[hingeNumber];
		}
		resetFilter();

	}

	@Override
	synchronized public void resetFilter() {

		if(!isFilterEnabled()) {
			return;
		}

		if(accumArray!=null){
			for (float[] element : accumArray) {
				Arrays.fill(element,0);
			}
			for(int i=0;i<(sx+1);i++) {
				Arrays.fill(attentionArray[i],0);
			}
			Arrays.fill(hingeMax,Float.NEGATIVE_INFINITY);
			Arrays.fill(hingeArray,0);
			Arrays.fill(maxIndex, 0);
			Arrays.fill(maxIndexHistory,0);
			xValue = 0;
			phiValue = 0;
			log.info("HingeLineTracker.reset!");

			//set the height of the hinges
			for(int i=0; i<hingeNumber; i++){
				float hingeDiff = (topHinge-bottomHinge)/(hingeNumber-1);
				hingeArray[i] = bottomHinge+(int)(i*hingeDiff);
			}
		}else{
			return;
		}
	}

	@Override
	synchronized public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {

		if(!isFilterEnabled()) {
			return in;
		}
		if(getEnclosedFilter()!=null) {
			in=getEnclosedFilter().filterPacket(in);
		}
		if(getEnclosedFilterChain()!=null) {
			in=getEnclosedFilterChain().filterPacket(in);
		}

		int n=in.getSize();
		if(n==0) {
			return in;
		}

		checkMaps();

		for(BasicEvent e:in){
			//for each event it is checked if it belongs to the rf of an row cell
			for(int i=0;i<hingeNumber;i++){
				if((e.y <= (hingeArray[i]+height)) && (e.y >= (hingeArray[i]-height)) ){
					//if this is the case the row cell activity gets updated
					updateHingeAccumulator(i,e.x/width);
					//due to noise protection events below 40 px have to reach a certain threshold
					if(e.y<40){
						if(attentionArray[e.x][e.y]>0.01) {
							updateHingeAccumulator(i,e.x/width);
						}
					}else{
						updateHingeAccumulator(i,e.x/width);
					}
				}
			}
		}
		//the attention has to be decayed
		decayAttentionArray();
		//the attention gest updated
		updateAttention();
		//the row cell activity is updated
		decayAccumArray();
		//the paoli-values have to be updated
		updatePaoli();

		if(showRowWindow) {
			checkAccumFrame();
			accumCanvas.repaint();
		}
		return in;
	}

	private void updateHingeAccumulator(int hingeNumber, int x) {
		float f=accumArray[hingeNumber][x];
		//the event gets added
		f++;
		//and if it has some attention on it it gets additionally enlarged
		f=f+(attentionFactor*attentionArray[x*width][hingeArray[hingeNumber]]);
		accumArray[hingeNumber][x]=f; // update the accumulator
	}

	private void updateAttention(){
		//the attention has to be actualized
		for(int i=0;i<hingeNumber;i++){
			int virtualX;
			int virtualY;
			if(isPaoli[i]){
				//the attention has to be set to where the hinge could be in the next event packet
				//--> prediction based on where it was before due to lateral movement
				int x0 = width*((2*maxIndex[i])-maxIndexHistory[i]);
				//the case where the hinge is predicted to be outside the image
				//left border
				if(x0<0){
					//iteration through surrounding area
					for(int x=0; x<attentionRadius; x++){
						for(int y=(int)(hingeArray[i]-attentionRadius); y<(int)(hingeArray[i]+attentionRadius); y++){
							//take only the hinges within a circle
							if((float)(Math.sqrt(((x)*(x))+((y-hingeArray[i])*(y-hingeArray[i]))))<attentionRadius){
								if((x>=0) && (y>=0) && (x<sx) && (y<sy)) {
									attentionArray[x][y]=attentionArray[x][y]+1;
								}
							}
						}
					}
					//right border
				}else if(x0 > sx){
					for(int x=(int)(sx-attentionRadius); x<sx; x++){
						for(int y=(int)(hingeArray[i]-attentionRadius); y<(int)(hingeArray[i]+attentionRadius); y++){
							if((float)(Math.sqrt(((sx-x)*(sx-x))+((y-hingeArray[i])*(y-hingeArray[i]))))<attentionRadius){
								if((x>=0) && (y>=0) && (x<sx) && (y<sy)) {
									attentionArray[x][y]=attentionArray[x][y]+1;
								}
							}
						}
					}
					//hinges within the image
				}else{
					for(int x=(int)(x0-attentionRadius); x<(int)(x0+attentionRadius); x++){
						for(int y=(int)(hingeArray[i]-attentionRadius); y<(int)(hingeArray[i]+attentionRadius); y++){
							if((float)(Math.sqrt(((x-x0)*(x-x0))+((y-hingeArray[i])*(y-hingeArray[i]))))<attentionRadius){
								if((x>=0) && (y>=0) && (x<sx) && (y<sy)) {
									attentionArray[x][y]=attentionArray[x][y]+1;
								}
							}
						}
					}
				}
				//the attention has to be putString on the spot where the next upper hinge might be
				if(((i+1)<hingeNumber) && isPaoli[i+1]){
					//the center of the attention is determined
					virtualX = width*((2*maxIndex[i])-maxIndex[i+1]);
					virtualY = (2*hingeArray[i])-hingeArray[i+1];
					for(int x=(int)(virtualX-attentionRadius); x<(int)(virtualX+attentionRadius); x++){
						for(int y=(int)(virtualY-attentionRadius); y<(int)(virtualY+attentionRadius); y++){
							if((float)(Math.sqrt(((x-virtualX)*(x-virtualX))+((y-virtualY)*(y-virtualY))))<attentionRadius){
								if((x>=0) && (y>=0) && (x<sx) && (y<sy)) {
									attentionArray[x][y]=attentionArray[x][y]+1;
								}
							}
						}
					}
				}
				// the attention has to be putString on the spot where the next lower hinge might be
				if(((i-1)>=0) && (i<hingeNumber) && isPaoli[i-1]){
					//the center of the attention is determined
					virtualX = width*((2*maxIndex[i])-maxIndex[i-1]);
					virtualY = (2*hingeArray[i])-hingeArray[i-1];
					for(int x=(int)(virtualX-attentionRadius); x<(int)(virtualX+attentionRadius); x++){
						for(int y=(int)(virtualY-attentionRadius); y<(int)(virtualY+attentionRadius); y++){
							if((float)(Math.sqrt(((x-virtualX)*(x-virtualX))+((y-virtualY)*(y-virtualY))))<attentionRadius){
								if((x>=0) && (y>=0) && (x<sx) && (y<sy)) {
									attentionArray[x][y]=attentionArray[x][y]+1;
								}
							}
						}
					}
				}
			} else {
				isPaoli[i]=false;
			}
		}
	}

	private void decayAccumArray() {
		//the values of the AccumArray are decayed
		if(accumArray==null) {
			return;
		}
		//since the AccumArray is only filled where hinges are, the
		for(int hinge=0; hinge<hingeNumber; hinge++){
			//the maximal value gets decayed
			hingeMax[hinge]*=hingeDecayFactor;
			float[] f=accumArray[hinge];
			//iteration through each row cell in the AccumArray
			for(int x=0;x<(f.length/width);x++){
				float fval=f[x];
				//decay of the value
				fval*=hingeDecayFactor;
				//check if the hinge has to be set to the actual row cell
				if(fval>hingeMax[hinge]) {
					//if the hinge was a hinge before the hinge can only be set to a
					//position within the shiftSpace
					if(isPaoli[hinge]){
						if(Math.abs(maxIndex[hinge]-x) < shiftSpace){
							maxIndexHistory[hinge]=maxIndex[hinge];
							maxIndex[hinge]=x;
							hingeMax[hinge]=fval;
							isPaoli[hinge]=true;
						}
					}else{
						maxIndexHistory[hinge]=maxIndex[hinge];
						maxIndex[hinge]=x;
						hingeMax[hinge]=fval;
						isPaoli[hinge]=true;
					}
				}
				//the decayed values are written into the AccumArray
				f[x]=fval;
			}
			//if the activity at the hinge is below the threshold it is taken away
			if(accumArray[hinge][maxIndex[hinge]]<hingeThreshold){
				maxIndex[hinge]=0;
				isPaoli[hinge]=false;
			}
		}
	}


	private void decayAttentionArray() {
		if(accumArray==null) {
			return;
		}
		attentionMax=0;
		//iteration trough the whole image
		for(int x=0; x<sx; x++){
			for(int y=0; y<sy; y++){
				//decay of the attention by the attentionDecayFactor
				attentionArray[x][y]*=attentionDecayFactor;
				//the attentionMax has to be set
				if(attentionArray[x][y]>attentionMax){
					attentionMax=attentionArray[x][y];
				}
				//the values are transfered to the OrientationCluster
				setAttention(x,y);
			}
		}
	}

	public void updatePaoli() {
		//the hinges are taken from the image if they are underneath the place where the line leaves the image
		for(int i=0; i<hingeNumber; i++){
			if(isPaoli[i] && ((i-1)>0) && ((maxIndex[i]>((sx/width)-2)) || (maxIndex[i]<2)) && isPaoli[i-1]) {
				isPaoli[i-1]=false;
			}
		}
	}

	GLU glu=null;
	GLUquadric wheelQuad;
	@Override
	public void annotate(GLAutoDrawable drawable) {
		if(!isFilterEnabled()) {
			return;
		}
		if(hingeArray == null) {
			return;
		}
		GL2 gl=drawable.getGL().getGL2();

		gl.glColor3f(1,1,1);

		gl.glLineWidth(3);

		if(glu==null) {
			glu=new GLU();
		}
		if(wheelQuad==null) {
			wheelQuad = glu.gluNewQuadric();
		}
		gl.glPushMatrix();
		//attention is drawn
		gl.glPointSize(2);
		for(int x=0; x<sx; x++){
			for(int y=0; y<sy; y++){
				gl.glBegin(GL.GL_POINTS);
				gl.glColor3f(attentionArray[x][y]/attentionMax,attentionArray[x][y]/attentionMax,attentionArray[x][y]/attentionMax);
				gl.glVertex2i(x,y);
				gl.glEnd();
			}
		}
		//hinges
		gl.glPointSize(8);
		for(int i=0; i<hingeNumber;i++){
			gl.glColor3f(1,1,1);
			gl.glBegin(GL.GL_POINTS);
			gl.glVertex2f(width*maxIndex[i], hingeArray[i]);
			gl.glEnd();
		}
		//line
		gl.glColor3f(1,1,1);
		gl.glBegin(GL.GL_LINE_STRIP);
		for(int i=0; i<hingeNumber;i++){
			if(isPaoli[i]){
				gl.glVertex2i(width*maxIndex[i],hingeArray[i]);
			}
		}
		gl.glEnd();
		if(drawOutput){
			//direction
			gl.glColor3f(1,0,0);
			gl.glBegin(GL.GL_LINE_STRIP);
			gl.glVertex2i((int)(sx*(0.5+(0.5*getX()))),hingeArray[0]);
			gl.glVertex2i((int)((sx*(0.5+(0.5*getX())))-((hingeArray[hingeNumber-1]-hingeArray[0])*Math.tan(getPhi()))),hingeArray[hingeNumber-1]);
			gl.glEnd();}
		gl.glPopMatrix();
	}

	void checkAccumFrame(){
		if(showRowWindow && ((accumFrame==null) || ((accumFrame!=null) && !accumFrame.isVisible()))) {
			createAccumFrame();
		}
	}

	JFrame accumFrame=null;
	GLCanvas accumCanvas=null;

	void createAccumFrame(){
		accumFrame=new JFrame("Hinge accumulator");
		accumFrame.setPreferredSize(new Dimension(400,400));
		accumCanvas=new GLCanvas();
		accumCanvas.addGLEventListener(new GLEventListener(){
			@Override
			public void init(GLAutoDrawable drawable) {
			}

			@Override
			synchronized public void display(GLAutoDrawable drawable) {
				//draw the row cell activity = AccumArray
				if(accumArray==null) {
					return;
				}
				GL2 gl=drawable.getGL().getGL2();
				gl.glLoadIdentity();
				gl.glScalef((width*drawable.getSurfaceWidth())/sx,drawable.getSurfaceHeight()/sy,1);
				gl.glClearColor(0,0,0,0);
				gl.glClear(GL.GL_COLOR_BUFFER_BIT);
				//iteration through the AccumArray
				for(int i=0;i<hingeNumber;i++){
					for(int j=0;j<(accumArray[i].length/width);j++){
						float f=accumArray[i][j]/hingeMax[i];
						gl.glColor3f(f,f,f);
						gl.glRectf(j,hingeArray[i]-height,j+1,hingeArray[i]+height);
					}
					//hinges
					gl.glColor3f(1,0,0);
					gl.glRectf(maxIndex[i],hingeArray[i]-height,maxIndex[i]+1,hingeArray[i]+height);
				}

				int error=gl.glGetError();
				if(error!=GL.GL_NO_ERROR){
					if(glu==null) {
						glu=new GLU();
					}
					log.warning("GL error number "+error+" "+glu.gluErrorString(error));
				}
			}

			@Override
			synchronized public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
				GL2 gl=drawable.getGL().getGL2();
				final int B=10;
				gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
				gl.glLoadIdentity(); // very important to load identity matrix here so this works after first resize!!!
				gl.glOrtho(-B,drawable.getSurfaceWidth()+B,-B,drawable.getSurfaceHeight()+B,10000,-10000);
				gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
				gl.glViewport(0,0,width,height);
			}

			public void displayChanged(GLAutoDrawable drawable, boolean modeChanged, boolean deviceChanged) {
			}

			@Override
			public void dispose(GLAutoDrawable arg0) {
				// TODO Auto-generated method stub

			}
		});
		accumFrame.getContentPane().add(accumCanvas);
		accumFrame.pack();
		accumFrame.setVisible(true);
	}

	public Object getFilterState() {
		return null;
	}

	public void setAttention(int x, int y){
		if(orientationCluster == null) {
			return;
		}
		if(attentionArray == null) {
			return;
		}
		if(orientationCluster.attention == null) {
			return;
		}
		orientationCluster.attention[x][y]=attentionArray[x][y];
	}


	@Override
	public void initFilter() {
		resetFilter();
	}

	public void annotate(float[][][] frame) {
	}

	public void annotate(Graphics2D g) {
	}

	public boolean isShowRowWindow() {
		return showRowWindow;
	}

	synchronized public void setShowRowWindow(boolean showRowWindow) {
		this.showRowWindow = showRowWindow;
	}

	@Override
	public float getPhi(){
		if(accumArray==null) {
			return 0;
		}
		float phiTotal =0;
		int phiNumber =0;
		for(int i=0; i<(hingeNumber-1); i++){
			if (isPaoli[i] && isPaoli[i+1]){
				phiNumber++;
				//phi is zero for vertical hinge-connections and 1 for horizontal ones
				//phi values from higher in the image count more -->phiNumber*
				phiTotal = phiTotal + (float)((phiNumber*Math.atan((width*(maxIndex[i+1]-maxIndex[i]))/(float)(hingeArray[i+1]-hingeArray[i]))*2)/(Math.PI));
			}}
		//if less than 2 hinges are set the "old" value is send out
		if( phiNumber > 2){
			phiValue = - phiTotal/phiNumber;
			return - phiTotal/phiNumber;}
		else {
			return phiValue;
		}
	}

	@Override
	public float getX(){
		if(accumArray==null) {
			return 0;
		}
		float xTotal = 0;
		float xNumber = 0;
		for(int i=0; i<(hingeNumber-1); i++){
			if (isPaoli[i]){
				xNumber++;
				//x is -1 at the left border and +1 at the right one
				xTotal = xTotal + (((2*width*maxIndex[i])/(float)(sx))-1);
			}
		}
		//if less than 2 hinges are set the "old" value is send out
		if (xNumber > 2){
			xValue = xTotal/xNumber;
			return xTotal/xNumber;}
		else {
			return xValue;
		}
	}

	public int getShiftSpace() {
		return shiftSpace;
	}

	public void setShiftSpace(int shiftSpace) {
		this.shiftSpace = shiftSpace;
		getPrefs().putInt("LineTracker.shiftSpace",shiftSpace);
	}

	public float getHingeThreshold() {
		return hingeThreshold;
	}

	public void setHingeThreshold(float hingeThreshold) {
		this.hingeThreshold = hingeThreshold;
		getPrefs().putFloat("LineTracker.hingeThreshold",hingeThreshold);
		resetFilter();
	}

	public int getBottomHinge() {
		return bottomHinge;
	}

	public void setBottomHinge(int bottomHinge) {
		this.bottomHinge = bottomHinge;
		getPrefs().putInt("LineTracker.bottomHinge",bottomHinge);
		resetFilter();
	}

	public int getTopHinge() {
		return topHinge;
	}

	public void setTopHinge(int topHinge) {
		this.topHinge = topHinge;
		getPrefs().putInt("LineTracker.topHinge",topHinge);
		resetFilter();
	}

	public int getHingeNumber() {
		return hingeNumber;
	}

	public void setHingeNumber(int hingeNumber) {
		this.hingeNumber = hingeNumber;
		getPrefs().putInt("LineTracker.hingeNumber",hingeNumber);
	}

	public float getAttentionRadius() {
		return attentionRadius;
	}

	public void setAttentionRadius(float attentionRadius) {
		this.attentionRadius = attentionRadius;
		getPrefs().putFloat("LineTracker.attentionRadius",attentionRadius);
	}

	public float getHingeDecayFactor() {
		return hingeDecayFactor;
	}

	public void setAttentionFactor(float attentionFactor) {
		this.attentionFactor = attentionFactor;
		getPrefs().putFloat("LineTracker.attentionFactor",attentionFactor);
	}

	public float getAttentionFactor() {
		return attentionFactor;
	}

	public void setHingeDecayFactor(float hingeDecayFactor) {
		if(hingeDecayFactor<0) {
			hingeDecayFactor=0;
		}
		else if(hingeDecayFactor>1) {
			hingeDecayFactor=1;
		}
		this.hingeDecayFactor = hingeDecayFactor;
		getPrefs().putFloat("LineTracker.hingeDecayFactor",hingeDecayFactor);
	}

	public float getAttentionDecayFactor() {
		return attentionDecayFactor;
	}

	public void setAttentionDecayFactor(float attentionDecayFactor) {
		if(attentionDecayFactor<0) {
			attentionDecayFactor=0;
		}
		else if(attentionDecayFactor>1) {
			attentionDecayFactor=1;
		}
		this.attentionDecayFactor = attentionDecayFactor;
		getPrefs().putFloat("LineTracker.attentionDecayFactor",attentionDecayFactor);
	}

	public PerspecTransform getPerspec() {
		return perspecTransform;
	}

	public void setPerspec(PerspecTransform perspecTransform) {
		this.perspecTransform = perspecTransform;
	}

	public boolean isDrawOutput() {
		return drawOutput;
	}

	synchronized public void setDrawOutput(boolean drawOutput) {
		this.drawOutput = drawOutput;
	}
}



