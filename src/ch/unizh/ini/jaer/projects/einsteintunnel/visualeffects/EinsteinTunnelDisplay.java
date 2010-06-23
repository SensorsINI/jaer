/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.einsteintunnel.visualeffects;

import java.io.*;
import java.net.*;
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
                for(int i = 0; i<dsx; i++){
                    if(xHistogram[i]>maxHistogramX) maxHistogramX = xHistogram[i];
                    gl.glColor3f(1,0,0);
                    gl.glRectf(i,0,i+1,xHistogram[i]*dsy/maxHistogramX);
                    //System.out.println("DSX: "+dsx/csx*i);
                    //System.out.println("histogram X: "+xHistogram[i]);
                }
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

    public void annotate(float[][][] frame) {
    }

    public void annotate(Graphics2D g) {
    }

}
