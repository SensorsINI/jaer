/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.einsteintunnel.visualeffects;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.awt.GLCanvas;
//import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.fixedfunc.GLMatrixFunc;
import com.jogamp.opengl.glu.GLU;
//import com.jogamp.opengl.glu.GLU;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Random;

import javax.swing.JFrame;


/**
 *
 * @author braendch
 */
public class EinsteinTunnelDisplay {

	public static boolean makeHistogram = true;

	public static boolean dropPerls = false; //doesnt work yet

	public static int commandPort = 20021;
	public static int packetCount = 0;
	public static int csx = 128;
	public static int csy = 128;
	public static int dsx = 78;
	public static int dsy = 102;
	public static int maxClusters = 200;
	public static int packetLength;
	public static int maxHistogramX;
	public static int nrClusters;
	public static short[] xHistogram;
	public static float[] xPos;
	public static float[] yPos;

	protected static Random random = new Random();

	public static void main(String[] args) throws IOException{
		char[] msg = new char[4];
		packetLength = (maxClusters*8)+4+4;
		xPos = new float[maxClusters];
		yPos = new float[maxClusters];
		xHistogram = new short[dsx];
		maxHistogramX = 1;
		byte[] buf = new byte[packetLength];

		DatagramSocket socket = new DatagramSocket(commandPort);

		System.out.println("Einstein Tunnel Display Method");

		while(true){
			DatagramPacket packet = new DatagramPacket(buf, buf.length);
			socket.receive(packet);
			//seq #
			int packetNumber = ((buf[0] & 0xFF) << 24)
				| ((buf[1] & 0xFF) << 16)
				| ((buf[2] & 0xFF) << 8)
				| (buf[3] & 0xFF);
			//msg
			msg[0]=(char)(buf[4]);
			msg[1]=(char)(buf[5]);
			msg[2]=(char)(buf[6]);
			msg[3]=(char)(buf[7]);
			//nrc
			nrClusters = (((buf[8] & 0xFF) << 8)
				| (buf[9] & 0xFF));
			//data
			if((msg[0]=='h') && (msg[1]=='i') && (msg[2]=='s')){
				readHistogramPacket(buf);
				packetCount++;
			}
			if((msg[0]=='p') && (msg[1]=='o') && (msg[2]=='s')){
				readPositionPacket(buf);
				//System.out.println("Read position... first position: "+xPos[0]);
				packetCount++;
			}
			checkInput();
			displayCanvas.repaint();
			if (packetNumber>packetCount){
				System.out.println("Lost Packet! "+packetNumber);
				packetCount = packetNumber;
			}
		}
	}

	static void readHistogramPacket(byte[] buf){
		for(int i=0; i<dsx; i++){
			xHistogram[i] = (short)(((buf[10+(2*i)] & 0xFF) << 8)
				| (buf[11+(2*i)] & 0xFF));
		}
	}

	static void readPositionPacket(byte[] buf){
		for(int i=0; i<nrClusters; i++){
			int offset = i*8;
			int xInt =    ((buf[10+offset] & 0xFF) << 24)
				| ((buf[11+offset] & 0xFF) << 16)
				| ((buf[12+offset] & 0xFF) << 8)
				|  (buf[13+offset] & 0xFF);
			xPos[i] =  Float.intBitsToFloat(xInt);
			int yInt =    ((buf[14+offset] & 0xFF) << 24)
				| ((buf[15+offset] & 0xFF) << 16)
				| ((buf[16+offset] & 0xFF) << 8)
				|  (buf[17+offset] & 0xFF);
			yPos[i] = Float.intBitsToFloat(yInt);
		}
	}

	static GLU glu=null;
	static JFrame displayFrame=null;
	static GLCanvas displayCanvas=null;

	static void checkInput(){
		if((displayFrame==null) || ((displayFrame!=null) && !displayFrame.isVisible())){
			createDisplay();
		}
	}

	static void createDisplay(){

		displayFrame=new JFrame("Tunnel Display");
		Insets displayInsets = displayFrame.getInsets();
		displayFrame.setSize(dsx+displayInsets.left+displayInsets.right, dsy+displayInsets.bottom+displayInsets.top+40);
		//histogramFrame.setSize(new Dimension(dsx,dsy));
		//displayFrame.setResizable(false);
		displayFrame.setAlwaysOnTop(true);
		displayFrame.setLocation(0, 0);
		displayCanvas=new GLCanvas();
		displayCanvas.setSize(dsx,dsy);
		displayCanvas.addGLEventListener(new GLEventListener(){
			@Override
			public void init(GLAutoDrawable drawable) {
			}

			@Override
			synchronized public void display(GLAutoDrawable drawable) {
				GL2 gl=drawable.getGL().getGL2();
				gl.glLoadIdentity();
				gl.glClearColor(0,0,0,0);
				gl.glClear(GL.GL_COLOR_BUFFER_BIT);
				//iteration through the xHistogram
				if(makeHistogram){
					//background
					//drawBackgroundSnakes(gl);
					drawPerls(gl);
					//histogram
					drawHistogramFire(gl);
					//dot
					//drawDot(gl);
				}
				//if(dropPerls)drawPerls(gl);
				int error=gl.glGetError();
				if(error!=GL.GL_NO_ERROR){
					if(glu==null)
					{
						glu=new GLU();
						//log.warning("GL error number "+error+" "+glu.gluErrorString(error));
					}
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
		displayFrame.getContentPane().add(displayCanvas);
		//histogramFrame.pack();
		displayFrame.setVisible(true);
	}

	static public void drawDot(GL2 gl){
		for (int i=0; i<nrClusters; i++){
			gl.glPointSize(10);
			gl.glColor3f(1f,0.5f,0.5f);
			gl.glBegin(GL.GL_POINTS);
			gl.glVertex2i((int)xPos[i],(int)yPos[i]);
			gl.glEnd();
		}
	}

	public static class Perl{
		public int x;
		public int y;
		public int size;
		public float phase;
		public float color;

		public Perl(){
			x = random.nextInt(dsx);
			y = random.nextInt(dsy);
			size = random.nextInt(maxSize);
			phase  = random.nextFloat()*360;
			color = (float)Math.sin(phase);
		}

		public void update(){
			phase+=0.02;
			color = (float)Math.sin(phase);
		}
	}

	static int phase = 0;
	static int margin = 3;
	static int maxPerls = 100;
	static int maxSize = 8;
	static float refreshRate = 1; //in absolute numbers
	static ArrayList<Perl> perls = new ArrayList();

	static public void drawPerls(GL2 gl){
		while (perls.size()<maxPerls){
			perls.add(new Perl());
		}
		Iterator<Perl> itr = perls.iterator();
		while(itr.hasNext()){
			boolean drawPerl = true;
			Perl perl = itr.next();
			/*for(int j=0; j<nrClusters; j++){
                if (perl.x > xPos[j]-margin && perl.y < xPos[j]+margin && drawPerl){
                    System.out.println("perl "+perl.x+"/"+perl.y+" removed");
                    perls.remove(perl);
                    drawPerl = false;
                }
            }*/
			if(drawPerl){
				perl.update();
				gl.glPointSize(perl.size);
				gl.glColor3f(perl.color*0.5f,perl.color*0.5f,perl.color);
				gl.glBegin(GL.GL_POINTS);
				gl.glVertex2i(perl.x,perl.y);
				gl.glEnd();
			}
		}
		for(int i = 0; i<refreshRate; i++){
			int rmIndex = random.nextInt(perls.size());
			while(perls.get(rmIndex) == null){
				rmIndex = random.nextInt(perls.size());
			}
			if(perls.get(rmIndex).color<0.05) {
				perls.remove(rmIndex);
			}
		}
		phase++;
	}

	static public void drawHistogramFire(GL2 gl){
		gl.glBegin (GL.GL_LINES);
		for(int i = 0; i<dsx; i++){
			if(xHistogram[i]>maxHistogramX) {
				maxHistogramX = xHistogram[i];
			}
			if(xHistogram[i]>0){
				gl.glColor3f(1,1,0);
				gl.glVertex2i(i,0);
				gl.glColor3f(1,0,0);
				gl.glVertex2i(i,(xHistogram[i]*dsy)/maxHistogramX);
				gl.glVertex2i(i,(xHistogram[i]*dsy)/maxHistogramX);
				gl.glColor3f(0,0,0);
				gl.glVertex2i(i,(2*xHistogram[i]*dsy)/maxHistogramX);
			}
		}
		gl.glEnd ();
	}

	static float[][][] RGB;
	static public void drawBackgroundSnakes(GL2 gl){
		RGB = new float[3][dsx][dsy];
		gl.glPointSize(1);
		gl.glBegin (GL.GL_POINTS);
		//snake I
		for(int x=0; x<dsx; x++){
			Arrays.fill(RGB[0][x], 0.0f);
			Arrays.fill(RGB[1][x], 0.0f);
			Arrays.fill(RGB[2][x], 0.0f);
			int y = (int)((dsy/2)+((0.42*dsy*Math.sin((0.01*phase)+((1.11*x)/dsx)))+(0.124*dsy*Math.sin((0.035*phase)+((0.32*x)/dsx)))));
			if((y>0) && (y<dsy)){
				RGB[0][x][y]+=1;
				RGB[1][x][y]+=1;
				RGB[2][x][y]+=1;
				gl.glColor3f(RGB[0][x][y],RGB[1][x][y], RGB[2][x][y]);
				gl.glVertex2i(x,y);
			}
			for(int j=1; j<21; j++){
				if(((y+j)>0) && ((y+j)<dsy)){
					RGB[0][x][y+j]+=(float)(1-(0.1*j));
					RGB[1][x][y+j]+=(float)(1-(0.1*j));
					RGB[2][x][y+j]+=(float)(1-(0.05*j));
					gl.glColor3f(RGB[0][x][y+j],RGB[1][x][y+j], RGB[2][x][y+j]);
					gl.glVertex2i(x,y+j);
				}
				if(((y-j)>0) && ((y-j)<dsy)){
					RGB[0][x][y-j]+=(float)(1-(0.1*j));
					RGB[1][x][y-j]+=(float)(1-(0.1*j));
					RGB[2][x][y-j]+=(float)(1-(0.05*j));
					gl.glColor3f(RGB[0][x][y-j],RGB[1][x][y-j], RGB[2][x][y-j]);
					gl.glVertex2i(x,y-j);
				}
			}
		}
		//snake II
		for(int x=0; x<dsx; x++){
			int y = (int)((dsy/2)+((0.5123*dsy*Math.sin((0.019823*phase)+((1.265*x)/dsx)))+(0.0832*dsy*Math.sin((0.00465*phase)+((0.521*x)/dsx)))));
			if((y>0) && (y<dsy)){
				RGB[0][x][y]+=1;
				RGB[1][x][y]+=1;
				RGB[2][x][y]+=1;
				gl.glColor3f(RGB[0][x][y],RGB[1][x][y], RGB[2][x][y]);
				gl.glVertex2i(x,y);
			}
			for(int j=1; j<21; j++){
				if(((y+j)>0) && ((y+j)<dsy)){
					RGB[0][x][y+j]+=(float)(1-(0.1*j));
					RGB[1][x][y+j]+=(float)(1-(0.1*j));
					RGB[2][x][y+j]+=(float)(1-(0.05*j));
					gl.glColor3f(RGB[0][x][y+j],RGB[1][x][y+j], RGB[2][x][y+j]);
					gl.glVertex2i(x,y+j);
				}
				if(((y-j)>0) && ((y-j)<dsy)){
					RGB[0][x][y-j]+=(float)(1-(0.1*j));
					RGB[1][x][y-j]+=(float)(1-(0.1*j));
					RGB[2][x][y-j]+=(float)(1-(0.05*j));
					gl.glColor3f(RGB[0][x][y-j],RGB[1][x][y-j], RGB[2][x][y-j]);
					gl.glVertex2i(x,y-j);
				}
			}
		}
		gl.glEnd ();
		phase +=1;
	}

	public void annotate(float[][][] frame) {
	}

	public void annotate(Graphics2D g) {
	}

}
