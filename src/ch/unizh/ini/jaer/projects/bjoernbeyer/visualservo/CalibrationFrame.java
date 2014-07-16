/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.bjoernbeyer.visualservo;

import java.awt.Graphics2D;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.event.ActionListener;

/**
 *
 * @author Bjoern
 */
public class CalibrationFrame extends JPanel implements ActionListener{
    private boolean frameVisible = false, rectVisible = false;
    public int FlashFreqHz = 20;

    private float posX  , posY;
    private float scaleX , scaleY;
    private int halfScreenW, halfScreenH;
    private final int size = 10;
    private boolean Flash = false;

    //private java.util.Timer timer;
    private Timer timer;

    CalibrationFrame(){
        super();
        reset();
    }
    public void startFlashing(){
        if(timer!=null)stopFlashing();
        frameVisible=true;
        timer = new Timer(1000/FlashFreqHz,this);
        timer.start(); 
    }
    public void stopFlashing() {
        frameVisible = false;
        if(timer!=null) {
            timer.stop();
            timer = null;
        }
    }
    @Override public void actionPerformed(ActionEvent e) {
        this.repaint();
        Flash = !Flash;
    }
    public void setFlashFreqHz(int FlashFreqHz) {
        this.FlashFreqHz = FlashFreqHz;
        startFlashing();
    }
    public int getFlashFreqHz() {
        return this.FlashFreqHz;
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);     
        Graphics2D g2 = (Graphics2D) g;
        g2.setStroke(new BasicStroke(10));

        int X1=(int)((1+posX+scaleX)*halfScreenW);
        int X2=(int)((1+posX-scaleX)*halfScreenW);
        int Y1=(int)((1+posY+scaleY)*halfScreenH);
        int Y2=(int)((1+posY-scaleY)*halfScreenH);
        if(Flash) {
            if(frameVisible){
                g2.drawLine(X1, Y1-size, X1, Y1);
                g2.drawLine(X1, Y2+size, X1, Y2);
                g2.drawLine(X2, Y1-size, X2, Y1);
                g2.drawLine(X2, Y2+size, X2, Y2);

                g2.drawLine(X1-size, Y1, X1, Y1);
                g2.drawLine(X1-size, Y2, X1, Y2);
                g2.drawLine(X2+size, Y1, X2, Y1);
                g2.drawLine(X2+size, Y2, X2, Y2);
            }
            if(frameVisible || rectVisible) {
                 g2.fillRect((int)((1+posX)*halfScreenW-size), (int)((1+posY)*halfScreenH-size), 2*size, 2*size);
            }  
        }
    }

    public void move(float X,float Y) {
        this.posX+=X;
        this.posY+=Y;
    }
    public void rescale(float sX, float sY) {
        this.scaleX += sX;
        this.scaleY += sY;
    }
    public float getPosX() {
        return posX;
    }
    public void setPosX(float newPos) {
        posX = newPos;
    }
    public float getPosY() {
        return posY;
    } 
    public void setPosY(float newPos) {
        posY = newPos;
    }
    public float getScaleX() {
        return scaleX;
    }
    public void setScaleX(float newScale) {
        scaleX = newScale;
    }
    public float getScaleY() {
       return scaleY; 
    }
    public void setScaleY(float newScale) {
        scaleY = newScale;
    }
    public final void reset() {
        stopFlashing();
        posX = 0;posY = 0;
        scaleX = 0.3f;scaleY=0.3f;
        halfScreenW = (int)getWidth()/2;
        halfScreenH = (int)getHeight()/2;
        setBackground(Color.white); 
    }
    public boolean isFrameVisible() {
        return frameVisible;
    }
    public void setFrameVisible(boolean vis) {
        frameVisible = vis;
    }
    public boolean isRectVisible() {
        return rectVisible;
    }
    public void setRectVisible(boolean vis) {
        rectVisible = vis;
    }
}

    