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

    public static int commandPort = 5888;
    public static int packetCount = 0;
    public static int dsx = 504;
    public static int dsy = 80;
    public static int maxHistogramX;
    public static short[] xHistogram;

    public static void main(String[] args) throws IOException{

        byte[] buf = new byte[dsx*2+4+4];
        char[] msg = new char[4];
        xHistogram = new short[dsx];
        maxHistogramX = 1;

        DatagramSocket socket = new DatagramSocket(commandPort);
        InetAddress address = InetAddress.getByName("localhost");
        
        System.out.println("Einstein Tunnel Display Method");

        while(true){

            //System.out.println(address.getCanonicalHostName()+":"+commandPort);
            DatagramPacket packet = new DatagramPacket(buf, buf.length, address, commandPort);
            packet = new DatagramPacket(buf, buf.length);
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
            //System.out.println(new String(msg));
            //data
            if(msg[0]=='h'&&msg[1]=='i'&&msg[2]=='s'&&msg[3]=='t'){
                for(int i=0; i<dsx; i++){
                    xHistogram[i] = (short)(((buf[8+2*i] & 0xFF) << 8)
                                   | (buf[9+2*i] & 0xFF));
                    //System.out.print(xHistogram[i]+" ");
                }
                //System.out.println();
            }
            //System.out.print("Packet number "+packetNumber);
            checkHistogram();
            histogramCanvas.repaint();
            packetCount++;
            if (packetNumber>packetCount){
                System.out.println("Lost Packet! "+packetNumber);
                packetCount = packetNumber;
            }
        }
    }

    static GLU glu=null;
    static JFrame histogramFrame=null;
    static GLCanvas histogramCanvas=null;

    static void checkHistogram(){
        if(histogramFrame==null || (histogramFrame!=null && !histogramFrame.isVisible())){
            createSimpleHistogram();
        }
    }

    static void createSimpleHistogram(){

        histogramFrame=new JFrame("Histogram");
        Insets histogramInsets = histogramFrame.getInsets();
        histogramFrame.setSize(dsx+histogramInsets.left+histogramInsets.right, dsy+histogramInsets.bottom+histogramInsets.top);
        //histogramFrame.setSize(new Dimension(dsx,dsy));
        histogramFrame.setResizable(false);
        histogramFrame.setAlwaysOnTop(true);
        histogramFrame.setLocation(100, 100);
        histogramCanvas=new GLCanvas();
        histogramCanvas.addGLEventListener(new GLEventListener(){
            public void init(GLAutoDrawable drawable) {
            }

            synchronized public void display(GLAutoDrawable drawable) {
                GL gl=drawable.getGL();
                gl.glLoadIdentity();
                gl.glClearColor(0,0,0,0);
                gl.glClear(GL.GL_COLOR_BUFFER_BIT);
                //iteration through the xHistogram
                //background
                drawBackground(gl);
                //histogram
                gl.glBegin (GL.GL_LINES);
                for(int i = 0; i<dsx; i++){
                    if(xHistogram[i]>maxHistogramX) maxHistogramX = xHistogram[i];
                    if(xHistogram[i]>0){
                        //gl.glColor3f(0,0,0);
                        //gl.glVertex2i(i,0);
                        //gl.glVertex2i(i,dsy);
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
        histogramFrame.getContentPane().add(histogramCanvas);
        //histogramFrame.pack();
        histogramFrame.setVisible(true);
    }

    static int phase = 0;
    static float[][][] RGB;
    static public void drawBackground(GL gl){
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
