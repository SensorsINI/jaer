/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.einsteintunnel.visualeffects;

import java.io.*;
import java.nio.*;
import java.net.*;
import java.util.*;
import javax.media.opengl.*;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.glu.*;
import javax.swing.*;
import java.awt.*;
import java.awt.Graphics2D;


/**
 *
 * @author braendch
 */
public class EinsteinTunnelDisplay {

    public static boolean makeHistogram = true;
    
    public static boolean dropPerls = false; //doesnt work yet

    public static int commandPort = 20021;
    public static int packetCount = 0;
    public static int dsx = 504;
    public static int dsy = 80;
    public static int maxClusters = 200;
    public static int nrClusters;
    public static int packetLength;
    public static int maxHistogramX;
    public static short[] xHistogram;
    public static float[] xPos;
    public static float[] yPos;

    protected static Random random = new Random();

    public static void main(String[] args) throws IOException{
        char[] msg = new char[3];
        int msgSize;
        packetLength = maxClusters*8+4+4;
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
            msgSize=(int)(buf[7]);
            //System.out.println(msg[0]+msg[1]+msg[2]+" "+msgSize);
            //System.out.println(new String(msg));
            //data
            if(msg[0]=='h' && msg[1]=='i' && msg[2]=='s'){
                readHistogramPacket(buf);
                packetCount++;
            }
            if(msg[0]=='p' && msg[1]=='o' && msg[2]=='s'){
                readPositionPacket(buf,msgSize);
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
            xHistogram[i] = (short)(((buf[8+2*i] & 0xFF) << 8)
                           | (buf[9+2*i] & 0xFF));
        }
    }

    static void readPositionPacket(byte[] buf, int msgSize){
        nrClusters = msgSize;
        for(int i=0; i<msgSize; i+=8){
            xPos[i] = (float)(((buf[8+i] & 0xFF) << 24)
                            | ((buf[9+i] & 0xFF) << 16)
                            | ((buf[10+i] & 0xFF) << 8)
                            | ((buf[11+i] & 0xFF)));
            yPos[i] = (float)(((buf[12+i] & 0xFF) << 24)
                            | ((buf[13+i] & 0xFF) << 16)
                            | ((buf[14+i] & 0xFF) << 8)
                            | ((buf[15+i] & 0xFF)));
        }
    }

    static GLU glu=null;
    static JFrame displayFrame=null;
    static GLCanvas displayCanvas=null;

    static void checkInput(){
        if(displayFrame==null || (displayFrame!=null && !displayFrame.isVisible())){
            createDisplay();
        }
    }

    static void createDisplay(){

        displayFrame=new JFrame("Tunnel Display");
        Insets displayInsets = displayFrame.getInsets();
        displayFrame.setSize(dsx+displayInsets.left+displayInsets.right, dsy+displayInsets.bottom+displayInsets.top);
        //histogramFrame.setSize(new Dimension(dsx,dsy));
        displayFrame.setResizable(false);
        displayFrame.setAlwaysOnTop(true);
        displayFrame.setLocation(100, 100);
        displayCanvas=new GLCanvas();
        displayCanvas.addGLEventListener(new GLEventListener(){
            public void init(GLAutoDrawable drawable) {
            }

            synchronized public void display(GLAutoDrawable drawable) {
                GL gl=drawable.getGL();
                gl.glLoadIdentity();
                gl.glClearColor(0,0,0,0);
                gl.glClear(GL.GL_COLOR_BUFFER_BIT);
                //iteration through the xHistogram
                if(makeHistogram){
                    //background
                    //drawBackgroundSnakes(gl);
                    drawPerls(gl);
                    //histogram
                    drawDot(gl);
                    drawHistogramFire(gl);
                }
                //if(dropPerls)drawPerls(gl);
                int error=gl.glGetError();
                if(error!=GL.GL_NO_ERROR){
                    if(glu==null) glu=new GLU();
                    //log.warning("GL error number "+error+" "+glu.gluErrorString(error));
                }
            }

            synchronized public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
                GL gl=drawable.getGL();
                final int B=10;
                gl.glMatrixMode(GL.GL_PROJECTION);
                gl.glLoadIdentity(); // very important to load identity matrix here so this works after first resize!!!
                gl.glOrtho(-B,drawable.getWidth()+B,-B,drawable.getHeight()+B,10000,-10000);
                gl.glMatrixMode(GL.GL_MODELVIEW);
                gl.glViewport(0,0,width,height);
            }

            public void displayChanged(GLAutoDrawable drawable, boolean modeChanged, boolean deviceChanged) {
            }
        });
        displayFrame.getContentPane().add(displayCanvas);
        //histogramFrame.pack();
        displayFrame.setVisible(true);
    }

    static public void drawDot(GL gl){
        for (int i=0; i<nrClusters; i++){
            gl.glPointSize(10);
            gl.glColor3f(0.5f,0.6f,1.0f);
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
    static ArrayList<Perl> perls = new ArrayList();

    static public void drawPerls(GL gl){
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
        phase++;
    }

    static public void drawHistogramFire(GL gl){
        gl.glBegin (GL.GL_LINES);
        for(int i = 0; i<dsx; i++){
            if(xHistogram[i]>maxHistogramX) maxHistogramX = xHistogram[i];
            if(xHistogram[i]>0){
                gl.glColor3f(1,1,0);
                gl.glVertex2i(i,0);
                gl.glColor3f(1,0,0);
                gl.glVertex2i(i,xHistogram[i]*dsy/maxHistogramX);
                gl.glVertex2i(i,xHistogram[i]*dsy/maxHistogramX);
                gl.glColor3f(0,0,0);
                gl.glVertex2i(i,2*xHistogram[i]*dsy/maxHistogramX);
            }
        }
        gl.glEnd ();
    }

    static float[][][] RGB;
    static public void drawBackgroundSnakes(GL gl){
        RGB = new float[3][dsx][dsy];
        gl.glPointSize(1);
        gl.glBegin (GL.GL_POINTS);
        //snake I
        for(int x=0; x<dsx; x++){
            Arrays.fill(RGB[0][x], 0.0f);
            Arrays.fill(RGB[1][x], 0.0f);
            Arrays.fill(RGB[2][x], 0.0f);
            int y = (int)((dsy/2)+(0.42*dsy*Math.sin(0.01*phase+1.11*x/dsx)+0.124*dsy*Math.sin(0.035*phase+0.32*x/dsx)));
            if(y>0 && y<dsy){
                RGB[0][x][y]+=1;
                RGB[1][x][y]+=1;
                RGB[2][x][y]+=1;
                gl.glColor3f(RGB[0][x][y],RGB[1][x][y], RGB[2][x][y]);
                gl.glVertex2i(x,y);
            }
            for(int j=1; j<21; j++){
                if(y+j>0 && y+j<dsy){
                    RGB[0][x][y+j]+=(float)(1-0.1*j);
                    RGB[1][x][y+j]+=(float)(1-0.1*j);
                    RGB[2][x][y+j]+=(float)(1-0.05*j);
                    gl.glColor3f(RGB[0][x][y+j],RGB[1][x][y+j], RGB[2][x][y+j]);
                    gl.glVertex2i(x,y+j);
                }
                if(y-j>0 && y-j<dsy){
                    RGB[0][x][y-j]+=(float)(1-0.1*j);
                    RGB[1][x][y-j]+=(float)(1-0.1*j);
                    RGB[2][x][y-j]+=(float)(1-0.05*j);
                    gl.glColor3f(RGB[0][x][y-j],RGB[1][x][y-j], RGB[2][x][y-j]);
                    gl.glVertex2i(x,y-j);
                }
            }
        }
        //snake II
        for(int x=0; x<dsx; x++){
            int y = (int)((dsy/2)+(0.5123*dsy*Math.sin(0.019823*phase+1.265*x/dsx)+0.0832*dsy*Math.sin(0.00465*phase+0.521*x/dsx)));
            if(y>0 && y<dsy){
                RGB[0][x][y]+=1;
                RGB[1][x][y]+=1;
                RGB[2][x][y]+=1;
                gl.glColor3f(RGB[0][x][y],RGB[1][x][y], RGB[2][x][y]);
                gl.glVertex2i(x,y);
            }
            for(int j=1; j<21; j++){
                if(y+j>0 && y+j<dsy){
                    RGB[0][x][y+j]+=(float)(1-0.1*j);
                    RGB[1][x][y+j]+=(float)(1-0.1*j);
                    RGB[2][x][y+j]+=(float)(1-0.05*j);
                    gl.glColor3f(RGB[0][x][y+j],RGB[1][x][y+j], RGB[2][x][y+j]);
                    gl.glVertex2i(x,y+j);
                }
                if(y-j>0 && y-j<dsy){
                    RGB[0][x][y-j]+=(float)(1-0.1*j);
                    RGB[1][x][y-j]+=(float)(1-0.1*j);
                    RGB[2][x][y-j]+=(float)(1-0.05*j);
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
