/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.rccar;


import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;

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
 * The HingeLaneTracker is a version of the HingeLineTracker that is able to track two lines by seperating the image into two parts:
 * One part left and one right of a separator. To understand what it does one should first study the HingeLineTracker and than examine
 * how the separator works.
 * In the HingeLaneTracker the hingeNumber describes the number of total hinges -left AND right, so it has to be twice as big as in the
 * hingeLineTracker. hinges on the right side of the image carry a even number and on the left they have an odd one (especially important
 * to understand the arrays) --> the x-coordinate is 0 on the right border
 *
 */
public class HingeLaneTracker extends EventFilter2D implements FrameAnnotater, Observer, HingeDetector {

	private float hingeThreshold=getPrefs().getFloat("LineTracker.hingeThreshold",2.5f);
	{setPropertyTooltip("hingeThreshold","the threshold for the hinge to react");}
	private float attentionRadius=getPrefs().getFloat("LineTracker.attentionRadius",12);
	{setPropertyTooltip("attentionRadius","the size of the attention balls");}
	private float separatorOffset=getPrefs().getFloat("LineTracker.separatorOffset",5);
	{setPropertyTooltip("separatorOffset","handles the width of the separator line");}
	private float attentionFactor=getPrefs().getFloat("LineTracker.attentionFactor",2);
	{setPropertyTooltip("attentionFactor","how much is additionally added to the accumArray if the attention is on a certain spike");}
	private float hingeDecayFactor=getPrefs().getFloat("LineTracker.hingeDecayFactor",0.6f);
	{setPropertyTooltip("hingeDecayFactor","hinge accumulator cells are multiplied by this factor before each frame, 0=no memory, 1=infinite memory");}
	private float attentionDecayFactor=getPrefs().getFloat("LineTracker.attentionDecayFactor",0.6f);
	{setPropertyTooltip("attentionDecayFactor","the slope of attention decay, 0=no memory, 1=infinite memory");}
	private int shiftSpace=getPrefs().getInt("LineTracker.shiftSpace",5);
	{setPropertyTooltip("shiftSpace","minimal distance between paoli hinge and seperation");}
	private boolean showRowWindow=false;
	private int topHinge=getPrefs().getInt("LineTracker.topHinge",80);
	{setPropertyTooltip("topHinge","the horizontal position of the top hinge (in px)");}
	private int bottomHinge=getPrefs().getInt("LineTracker.bottomHinge",40);
	{setPropertyTooltip("bottomHinge","the horizontal position of the bottom hinge (in px)");}
	private int hingeNumber=getPrefs().getInt("LineTracker.hingeNumber",4);
	{setPropertyTooltip("hingeNumber","total number of hinges: left AND right - MUST BE EVEN");}
	private boolean drawOutput=getPrefs().getBoolean("LineTracker.drawOutput",false);
	{setPropertyTooltip("drawOutput","should the output be drawn");}

	private float[][] accumArray;
	private float[][][] attentionArray;
	private float[] hingeMax;
	private int[] maxIndex;
	private int[] maxIndexHistory;
	private int[] hingeArray;
	private int[] separator ;
	private boolean[] isPaoli;
	private boolean[] isWaiting;
	private float attentionMax;
	private int sx;
	private int sy;

	private int height = 4;
	private int width = 4; //should be even


	FilterChain preFilterChain;
	private PerspecTransform perspecTransform;
	private OrientationCluster orientationCluster;


	public HingeLaneTracker(AEChip chip) {
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
			attentionArray= new float[sx+1][sy][2];
			hingeMax= new float[hingeNumber];
			hingeArray= new int[hingeNumber];
			maxIndex= new int[hingeNumber];
			maxIndexHistory= new int[hingeNumber];
			separator = new int[(hingeNumber/2)+1];
			isPaoli= new boolean[hingeNumber];
			isWaiting = new boolean[2];
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
				for(int j=0; j<sy; j++) {
					Arrays.fill(attentionArray[i][j],0);
				}
			}
			Arrays.fill(hingeMax,Float.NEGATIVE_INFINITY);
			Arrays.fill(maxIndex, 0);
			Arrays.fill(maxIndexHistory,0);
			Arrays.fill(separator,chip.getSizeX()/(2*width));
			Arrays.fill(isWaiting, false);
			log.info("HingeLineTracker.reset!");

			for(int i=0; i<hingeNumber; i++){
				float hingeDiff = (2*(topHinge-bottomHinge))/(hingeNumber-2);
				hingeArray[i] = bottomHinge+(int)((i/2)*hingeDiff);
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
			for(int i=0;i<hingeNumber;i+=2){
				if((!isWaiting[0] && !isWaiting[1]) || ((isWaiting[0] && (e.x>(sx/2))) || (isWaiting[1] && (e.x<(sx/2))))){
					if((e.y <= (hingeArray[i]+height)) && (e.y >= (hingeArray[i]-height)) ){
						if(e.y<40){
							if((e.x/width)<(separator[i/2])){
								if(attentionArray[e.x][e.y][0]>0.01) {
									updateHingeAccumulator(i,e.x/width,0);
								}
							} else {
								if(attentionArray[e.x][e.y][1]>0.01) {
									updateHingeAccumulator(i+1,e.x/width,1);
								}
							}
						}else{
							if((e.x/width)<(separator[i/2])){
								updateHingeAccumulator(i,e.x/width,0);
							} else {
								updateHingeAccumulator(i+1,e.x/width,1);
							}
						}
					}}
			}
		}

		decayAttentionArray();
		updateAttention();
		decayAccumArray();
		updateSeparation();
		updatePaoli();

		if(showRowWindow) {
			checkAccumFrame();
			accumCanvas.repaint();
		}
		return in;
	}

	private void updateHingeAccumulator(int hingeNumber, int x, int leori) {
		float f=accumArray[hingeNumber][x];
		f++;
		f=f+(attentionFactor*attentionArray[x*width][hingeArray[hingeNumber]][leori]);
		accumArray[hingeNumber][x]=f; // update the accumulator
	}

	private void updateAttention(){
		//the attention has to be actualized --> see HingeLineTracker for further comments
		for(int i=0;i<hingeNumber;i++){
			int virtualX;
			int virtualY;
			if(isPaoli[i]){
				//the attention has to be set to where the hinge could be in the next event packet --> prediction based on where it was before
				int x0 = width*((2*maxIndex[i])-maxIndexHistory[i]);
				if(x0<0){
					for(int x=0; x<attentionRadius; x++){
						for(int y=(int)(hingeArray[i]-attentionRadius); y<(int)(hingeArray[i]+attentionRadius); y++){
							if((float)(Math.sqrt(((x)*(x))+((y-hingeArray[i])*(y-hingeArray[i]))))<attentionRadius){
								if((x>=0) && (y>=0) && (x<sx) && (y<sy)) {
									attentionArray[x][y][i%2]=attentionArray[x][y][i%2]+1;
								}
							}
						}
					}
				}else if(x0 > sx){
					for(int x=(int)(sx-attentionRadius); x<sx; x++){
						for(int y=(int)(hingeArray[i]-attentionRadius); y<(int)(hingeArray[i]+attentionRadius); y++){
							if((float)(Math.sqrt(((sx-x)*(sx-x))+((y-hingeArray[i])*(y-hingeArray[i]))))<attentionRadius){
								if((x>=0) && (y>=0) && (x<sx) && (y<sy)) {
									attentionArray[x][y][i%2]=attentionArray[x][y][i%2]+1;
								}
							}
						}
					}
				}else{
					for(int x=(int)(x0-attentionRadius); x<(int)(x0+attentionRadius); x++){
						for(int y=(int)(hingeArray[i]-attentionRadius); y<(int)(hingeArray[i]+attentionRadius); y++){
							if((float)(Math.sqrt(((x-x0)*(x-x0))+((y-hingeArray[i])*(y-hingeArray[i]))))<attentionRadius){
								if((x>=0) && (y>=0) && (x<sx) && (y<sy)) {
									attentionArray[x][y][i%2]=attentionArray[x][y][i%2]+1;
								}
							}
						}
					}
				}
				//the attention has to be putString on the spot where the next upper hinge might be
				if(((i+2)<hingeNumber) && isPaoli[i+2]){
					virtualX = width*((2*maxIndex[i])-maxIndex[i+2]);
					virtualY = (2*hingeArray[i])-hingeArray[i+2];
					for(int x=(int)(virtualX-attentionRadius); x<(int)(virtualX+attentionRadius); x++){
						for(int y=(int)(virtualY-attentionRadius); y<(int)(virtualY+attentionRadius); y++){
							if((float)(Math.sqrt(((x-virtualX)*(x-virtualX))+((y-virtualY)*(y-virtualY))))<attentionRadius){
								if((x>=0) && (y>=0) && (x<sx) && (y<sy)) {
									attentionArray[x][y][i%2]=attentionArray[x][y][i%2]+1;
								}
							}
						}
					}
				}
				// the attention has to be putString on the spot where the next lower hinge might be
				if(((i-2)>=0) && (i<hingeNumber) && isPaoli[i-2]){
					virtualX = width*((2*maxIndex[i])-maxIndex[i-2]);
					virtualY = (2*hingeArray[i])-hingeArray[i-2];
					for(int x=(int)(virtualX-attentionRadius); x<(int)(virtualX+attentionRadius); x++){
						for(int y=(int)(virtualY-attentionRadius); y<(int)(virtualY+attentionRadius); y++){
							if((float)(Math.sqrt(((x-virtualX)*(x-virtualX))+((y-virtualY)*(y-virtualY))))<attentionRadius){
								if((x>=0) && (y>=0) && (x<sx) && (y<sy)) {
									attentionArray[x][y][i%2]=attentionArray[x][y][i%2]+1;
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
		//same as HingeLineTracker
		if(accumArray==null) {
			return;
		}
		for(int hinge=0; hinge<hingeNumber; hinge++){
			hingeMax[hinge]*=hingeDecayFactor;
			float[] f=accumArray[hinge];
			for(int x=0;x<(f.length/width);x++){
				float fval=f[x];
				fval*=hingeDecayFactor;
				if(fval>hingeMax[hinge]) {
					if(isPaoli[hinge]){
						if(Math.abs(maxIndex[hinge]-x) < shiftSpace){
							maxIndexHistory[hinge]=maxIndex[hinge];
							maxIndex[hinge]=x;
							hingeMax[hinge]=fval;
							isPaoli[hinge]=true;
							hingeMax[(hinge+1)-((hinge%2)*2)]=fval;
						}
					}else{
						maxIndexHistory[hinge]=maxIndex[hinge];
						maxIndex[hinge]=x;
						hingeMax[hinge]=fval;
						isPaoli[hinge]=true;
						hingeMax[(hinge+1)-((hinge%2)*2)]=fval;
					}
				}
				f[x]=fval;
			}
			if(accumArray[hinge][maxIndex[hinge]]<hingeThreshold){
				maxIndex[hinge]=((hinge%2)*sx)/width;
				isPaoli[hinge]=false;

			}
			if(isPaoli[hinge] && ((((hinge-2)>0) && isPaoli[hinge-2]) || (((hinge+2)<hingeNumber) && isPaoli[hinge+2]))) {
				isWaiting[hinge%2]=false;
			}
		}
	}


	private void decayAttentionArray() {
		//same as HingeLineTracker
		if(accumArray==null) {
			return;
		}
		attentionMax=0;
		for(int x=0; x<sx; x++){
			for(int y=0; y<sy; y++){
				for(int leori=0; leori<2; leori++){
					attentionArray[x][y][leori]*=attentionDecayFactor;
					if(attentionArray[x][y][leori]>attentionMax){
						attentionMax=attentionArray[x][y][leori];
					}
					setAttention(x,y);
				}
			}
		}
	}


	public void updateSeparation(){

		for(int i=0; i<(hingeNumber/2); i++){
			//check if the separator should be pulled back to the middle
			//if both hinges are not part of the line the separator slowly goes back to the middle
			if(!isPaoli[2*i] && !isPaoli[(2*i)+1] &&!isWaiting[0] && !isWaiting[1]) {
				separator[i]=(int)((sx/(2*width)) + (0.9*(separator[i]-(sx/(2*width)))));
			}
			//if the hinge is in on the right side of the
			if(((maxIndex[2*i]*width)<(sx/2)) && isPaoli[2*i]){
				separator[i]= sx/(2*width);
			}
			if(((maxIndex[(2*i)+1]*width)>(sx/2)) && isPaoli[(2*i)+1]){
				separator[i]= sx/(2*width);
			}
			//check if separator should be pushed away
			while(((attentionArray[width*separator[i]][hingeArray[2*i]][0]-0.01)>attentionArray[width*separator[i]][hingeArray[2*i]][1]) && (((separator[i])*width)<sx) ){
				separator[i]=separator[i]+1;
			}
			while((attentionArray[width*separator[i]][hingeArray[2*i]][0]<(attentionArray[width*separator[i]][hingeArray[2*i]][1]-0.01)) && (separator[i]>0)){
				separator[i]=separator[i]-1;
			}
			//check if separator is inbetween two parts of a line
			if((((2*i)+2) < hingeNumber) && (((2*i)-2)>0) && isPaoli[(2*i)-2] && isPaoli[(2*i)+2] && (separator[i]<((maxIndex[(2*i)-2]+maxIndex[(2*i)+2])/2))){
				maxIndex[2*i]=(maxIndex[(2*i)-2]+maxIndex[(2*i)+2])/2;
				separator[i]=(separator[i-1]+separator[i+1])/2;
				isPaoli[(2*i)+2]=true;
			}
			if((((2*i)+3) < hingeNumber) && (((2*i)-1)>0) && isPaoli[(2*i)-1] && isPaoli[(2*i)+3] && (separator[i]>((maxIndex[(2*i)-1]+maxIndex[(2*i)+3])/2))){
				maxIndex[(2*i)+1]=(maxIndex[(2*i)-1]+maxIndex[(2*i)+3])/2;
				isPaoli[(2*i)+1]=true;
				separator[i]=(separator[i-1]+separator[i+1])/2;
			}
			//waiting separator point pull their neighbors
			if(isWaiting[i%2] && ((i%2) == 0) && (separator[i]==(sx/width))){
				for(int j=0; j<(hingeNumber/width); j++) {
					separator[j]=sx/width;
				}
			}
			if(isWaiting[i%2] && ((i%2) == 1) && (separator[i]==0)){
				for(int j=0; j<(hingeNumber/width); j++) {
					separator[j]=0;
				}
			}
		}
	}

	public void updatePaoli() {
		//exception handling for leaving and entering lines
		for(int i=0; i<hingeNumber; i++){
			if((i%2) == 1){
				//---left---
				//line cutoff
				if(isPaoli[i] && ((i-2)>0) && ((maxIndex[i]>((sx/width)-2)) || (maxIndex[i]<2)) && isPaoli[i-2]) {
					isPaoli[i-2]=false;
				}
				//leaving line
				if(maxIndex[i]<=1){
					for(int j=0; j<(hingeNumber/2); j++) {
						separator[j]=0;
					}
					isWaiting[1]=true;
				}

			} else {
				//---right---
				//line cutoff
				if(isPaoli[i] && ((i-2)>0) && ((maxIndex[i]>((sx/width)-2)) || (maxIndex[i]<2)) && isPaoli[i-2]) {
					isPaoli[i-2]=false;
				}
				//leaving line
				if(maxIndex[i]>=((sx/width)-2)){
					for(int j=0; j<(hingeNumber/2); j++) {
						separator[j]=sx/width;
					}
					isWaiting[0]=true;
				}
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
		//attention
		gl.glPointSize(2);
		for(int x=0; x<sx; x++){
			for(int y=0; y<sy; y++){
				gl.glBegin(GL.GL_POINTS);
				gl.glColor3f(attentionArray[x][y][0]/attentionMax,attentionArray[x][y][1]/attentionMax,(attentionArray[x][y][0]+attentionArray[x][y][1])/attentionMax);
				gl.glVertex2i(x,y);
				gl.glEnd();
			}
		}
		//separator
		gl.glPointSize(8);
		gl.glColor3f(1,1,1);
		for(int i=0; i<(hingeNumber/2);i++){
			gl.glBegin(GL.GL_POINTS);
			gl.glVertex2f(width*separator[i], hingeArray[2*i]);
			gl.glEnd();
		}
		gl.glBegin(GL.GL_LINE_STRIP);
		for(int i=0; i<(hingeNumber/2);i++){
			gl.glVertex2i(width*separator[i], hingeArray[2*i]);
		}
		gl.glEnd();
		//points
		for(int i=0; i<hingeNumber;i++){
			gl.glColor3f(1-(i%2),i%2,1);
			gl.glBegin(GL.GL_POINTS);
			gl.glVertex2f(width*maxIndex[i], hingeArray[i]);
			gl.glEnd();
		}
		//right line
		gl.glColor3f(1,0,1);
		gl.glBegin(GL.GL_LINE_STRIP);
		for(int i=0; i<hingeNumber;i+=2){
			if(isPaoli[i]){
				gl.glVertex2i(width*maxIndex[i],hingeArray[i]);
			}
		}
		//left line
		gl.glEnd();
		gl.glColor3f(0,1,1);
		gl.glBegin(GL.GL_LINE_STRIP);
		for(int i=1; i<hingeNumber;i+=2){
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
				if(accumArray==null) {
					return;
				}
				GL2 gl=drawable.getGL().getGL2();
				gl.glLoadIdentity();
				gl.glScalef((width*drawable.getSurfaceWidth())/sx,drawable.getSurfaceHeight()/sy,1);
				gl.glClearColor(0,0,0,0);
				gl.glClear(GL.GL_COLOR_BUFFER_BIT);
				//left
				for(int i=0;i<hingeNumber;i+=2){
					for(int j=0;j<separator[i/2];j++){
						float f=accumArray[i][j]/hingeMax[i];
						gl.glColor3f(f,0,f);
						gl.glRectf(j,hingeArray[i]-height,j+1,hingeArray[i]+height);
					}
					//hinges
					gl.glColor3f(1,0,0);
					gl.glRectf(maxIndex[i],hingeArray[i]-height,maxIndex[i]+1,hingeArray[i]+height);
				}
				//right
				for(int i=1;i<hingeNumber;i+=2){
					for(int j=separator[(i-1)/2];j<(accumArray[i].length/width);j++){
						float f=accumArray[i][j]/hingeMax[i];
						gl.glColor3f(0,f,f);
						gl.glRectf(j,hingeArray[i]-height,j+1,hingeArray[i]+height);
					}
					//hinges
					gl.glColor3f(1,0,0);
					gl.glRectf(maxIndex[i],hingeArray[i]-height,maxIndex[i]+1,hingeArray[i]+height);
				}
				//separator
				for(int i=0;i<(hingeNumber/2);i++){
					gl.glColor3f(1,1,1);
					gl.glRectf(separator[i],hingeArray[2*i]-height,separator[i]+1,hingeArray[2*i]+height);
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
		orientationCluster.attention[x][y]=attentionArray[x][y][0]+attentionArray[x][y][1];
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

	public boolean isDrawOutput() {
		return drawOutput;
	}

	synchronized public void setDrawOutput(boolean drawOutput) {
		this.drawOutput = drawOutput;
	}

	@Override
	public void update(Observable o, Object arg){
		resetFilter();
	}

	@Override
	public float getPhi(){
		if(accumArray == null) {
			return 0;
		}
		return getRightPhi()+getLeftPhi();
	}

	public float getRightPhi(){
		float phiTotal =0;
		int phiNumber =0;
		for(int i=1; i<(hingeNumber-2); i+=2){
			if (isPaoli[i] && isPaoli[i+2]){
				phiNumber++;
				phiTotal = phiTotal + (float)((phiNumber*Math.tanh((width*(maxIndex[i+2]-maxIndex[i]))/(float)(hingeArray[i+2]-hingeArray[i]))*2)/(Math.PI));
			}}
		if( phiNumber != 0) {
			return - phiTotal/phiNumber;
		}
		else {
			return 0;
		}
	}

	public float getLeftPhi(){
		float phiTotal =0;
		int phiNumber =0;
		for(int i=0; i<(hingeNumber-2); i+=2){
			if(isPaoli[i] && isPaoli[i+2]){
				phiNumber++;
				phiTotal = phiTotal + (float)((phiNumber*Math.tanh((width*(maxIndex[i+2]-maxIndex[i]))/(float)(hingeArray[i+2]-hingeArray[i]))*2)/(Math.PI));
			}}
		if(phiNumber != 0) {
			return  - phiTotal/phiNumber;
		}
		else {
			return 0;
		}
	}

	@Override
	public float getX(){
		if(accumArray == null) {
			return 0;
		}
		return getRightX()+getLeftX();
	}

	public float getRightX(){
		float xTotal = 0;
		float xNumber = 0;
		for(int i=1; i<(hingeNumber-2); i+=2){
			if (isPaoli[i]){
				xNumber++;
				xTotal = xTotal + (((2*width*maxIndex[i])/(float)(sx))-1);
			}
		}
		if (xNumber != 0) {
			return xTotal/xNumber;
		}
		else {
			return 1;
		}
	}

	public float getLeftX(){
		float xTotal = 0;
		float xNumber = 0;
		for(int i=0; i<(hingeNumber-2); i+=2){
			if (isPaoli[i]){
				xNumber++;
				xTotal = xTotal + (((2*width*maxIndex[i])/(float)(sx))-1);
			}
		}
		if (xNumber != 0) {
			return xTotal/xNumber;
		}
		else {
			return -1;
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
	}

	public float getAttentionRadius() {
		return attentionRadius;
	}

	public void setAttentionRadius(float attentionRadius) {
		this.attentionRadius = attentionRadius;
		getPrefs().putFloat("LineTracker.attentionRadius",attentionRadius);
	}

	public float getSeparatorOffset() {
		return separatorOffset;
	}

	public void setSeparatorOffset(float separatorOffset) {
		this.separatorOffset = separatorOffset;
		getPrefs().putFloat("LineTracker.seperatorOffset",separatorOffset);
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
}



