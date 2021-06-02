/*
Copyright June 13, 2011 Andreas Steiner, Inst. of Neuroinformatics, UNI-ETH Zurich

This file is part of dsPICserial.

dsPICserial is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

dsPICserial is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with dsPICserial.  If not, see <http://www.gnu.org/licenses/>.
*/


package ch.unizh.ini.jaer.projects.dspic.serial;

import java.awt.Color;
import java.awt.Graphics;

import javax.swing.BorderFactory;
import javax.swing.JPanel;

/**
 * this class accepts data as recieved from a 2D visual chip and displays
 * it via slow swing based drawing -- used by StreamCommandTest
 * 
 * @author andstein
 */
public class RetinaPanel extends JPanel {

    private static final long UPDATE_PERIOD = 100; // in ms

    private int width,height;

    private int[][] data;
    private long lastUpdate;
    private boolean painting;
    private boolean autoGain;
    private int maxValue;
    private float gx,gy;
    private float stretchVector= 1f;

    public RetinaPanel(int w,int h) {
        width= w;
        height= h;
        data= null;
        lastUpdate= 0;
        painting= false;
        autoGain= false;
        maxValue= 1024;
        gx=0; gy=0;

        setBorder(BorderFactory.createLineBorder(Color.black));
    }


    public void setData(byte[][] data)
    {
        if (painting) return;
        
        maxValue= 256;
        if (this.data == null)
            this.data= new int[height][width];
        for(int y=0; y<height; y++)
            for(int x=0; x<width; x++)
                this.data[y][x]= data[y][x] &0xff;

        repaint();
    }
    public void setData(int[][] data,int max)
    {
        if (painting) return;
        maxValue= max;
        this.data= data;

        repaint();
    }
    public void setData(int[][] data)
    {
        setData(data,getMaximalValue());
    }
    

    @Override
    public void paintComponent(Graphics g) {

        //HACK avoid generating too much latency
        if (System.currentTimeMillis() - lastUpdate < UPDATE_PERIOD)
            return;

        super.paintComponent(g);

        if (data == null)
        {
            g.setColor(Color.red);
            g.drawLine(0, 0, getWidth(), getHeight());
            g.drawLine(0,getHeight(),getWidth(),0);
            return;
        }

        //HACK "multithread safe"
        painting= true;
        
        int minv=0,maxv=maxValue;
        if (autoGain)
        {
            maxv=0; minv=99999;
            for(int y=0; y<height; y++)
                for(int x=0; x<width; x++) {
                    if (y == height-1 || (y==0 && x==0) )
                        continue;   //"bogus pixels"
                    if (data[y][x] > maxv) maxv=data[y][x];
                    if (data[y][x] < minv) minv=data[y][x];
                }
            if (maxv == minv) maxv++;
        }
        
        int dw= getWidth()/ width;
        int dh= getHeight()/height;

        for(int y=0; y<height; y++)
            for(int x=0; x<width; x++)
            {
                int v= (data[y][x]-minv) *255  /(maxv-minv);
                if (v<0) v=0;
                if (v>255) v=255;
                g.setColor( new Color(0,v,0) );
                g.fillRect(x*dw, y*dh, dw, dh);
            }
//        System.err.println("repainted.");
        
        int cx= getWidth()/2;
        int cy= getHeight()/2;
        int dx= (int) (gx*getStretchVector()*cx);
        int dy= (int) (gy*getStretchVector()*cy);
        g.setColor(new Color(255,0,0));
        g.drawLine(cx, cy, cx+dx, cy+dy);

        painting= false;
        lastUpdate= System.currentTimeMillis();
    }

    public void setAutoGain(boolean b) { autoGain= b; }
    public boolean getAutoGain() { return autoGain; }

    public void setMaximalValue(int x) { maxValue= x; }
    public int getMaximalValue() { return maxValue; }


    public void setDataFromMessage(RetinaMessage msg)
    {
        int frame[][]= new int[RetinaMessage.HEIGHT][RetinaMessage.WIDTH];
        
        for(int y=0; y<RetinaMessage.WIDTH; y++)
            for(int x=0; x<RetinaMessage.HEIGHT; x++)
                frame[y][x] = msg.getPixelAt(x,y); 
        
        setData(frame);
        
        gx= msg.getDx();
        gy= msg.getDy();
    }

    public float getStretchVector() {
        return stretchVector;
    }

    public void setStretchVector(float stretchVector) {
        this.stretchVector = stretchVector;
    }
    
    

}
