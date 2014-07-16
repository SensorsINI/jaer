/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.bjoernbeyer.stimulusdisplay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Paint;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.RectangularShape;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.Serializable;
import javax.swing.Timer;

/**
 *
 * @author Bjoern
 */
public class PaintableObject implements ActionListener, Serializable {
    private static int numberObjects;
            
    private final String objectName;
    private final RectangularShape objectShape;
    private float origX, origY;
    private float width, height;
    private float stroke;
    private int halfScreenWidth, halfScreenHeight;
    private int FlashFreqHz = 20;
    private boolean Flash = true;
    private boolean flashEnabled = false;
    private boolean pathPaintEnabled = false;
    private boolean pathLoop = false;
    private boolean hasGradient = false;
    private float[] gradFractions;
    private float[] gradPos;
    private Color[] gradColors;
    
    private Paint objectColor;
    
    private Timer timer;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private MouseTrajectory objectPath;
    private TrajectoryPlayer player = null;
    
    public static int getNumberObjects() {
        return numberObjects;
    }
    
    private class TrajectoryPlayer extends Thread implements Serializable{
        boolean cancelMe = false;
        private boolean once = false;
        
        TrajectoryPlayer(boolean playOnce){
            super();
            once = playOnce;
        }
        
        void cancel() {
            cancelMe = true;
            synchronized (this) { interrupt(); }
        }

        @Override public void run() {
            while (!cancelMe) {
                for (MouseTrajectoryPoint p : objectPath) {
                    if (cancelMe) break;
                    try {
                        Thread.sleep(p.getDifferenceTimeMillis());
                    } catch (InterruptedException ex) { 
                        break; 
                    }
                    PaintableObject.this.setOrigXY(p.getX(),p.getY());
                }
                if(once) cancel();
            }
        }
    }

    public PaintableObject(String objectName, RectangularShape objectShape) {
        super();
        origX = 0; origY = 0;
        halfScreenWidth = -1; halfScreenHeight = -1; //Initialize this as negative. When we add objects in StimulusFrame we set this
        this.objectShape = objectShape;
        
        if(objectName.equals("")) {
            this.objectName = "PrintObj"+String.valueOf(numberObjects);
        }else{
            this.objectName = objectName;
        }
        
        objectPath = new MouseTrajectory();
        
        //Initialize paint and shape to avoid painting errors if user forgets to set those.
        objectColor = Color.white;
        
        numberObjects++;
    }
    
    public PaintableObject(String objectName, RectangularShape objectShape, float width, float height) {
        this(objectName, objectShape);
        this.width = width; this.height = height;
    }
    
    public void startFlashing(){
        if(timer!=null)stopFlashing();
        timer = new Timer(1000/FlashFreqHz,this);
        timer.start(); 
        setFlashEnabled(true);
    }
    
    public void stopFlashing() {
        if(timer!=null) {
            timer.stop();
            timer = null;
        }
        Flash = true; // So that object is visible
        setFlashEnabled(false);
    }
        
    @Override public void actionPerformed(ActionEvent e) {
        Flash = !Flash;
        this.pcs.firePropertyChange("repaint", null, null);
    }
    
    public void playPathOnce() {
        if (player != null) player.cancel();
        player = new TrajectoryPlayer(true);
        player.start();
    }
    
    public void playPathLoopToggle() {
        if (player != null) player.cancel();
        setPathLoop(!isPathLoop());
        if (isPathLoop()) {
            player = new TrajectoryPlayer(false);
            player.start();
        } else {
            player.cancel();
        }
    }

    public float getOrigX() {
        return origX;
    }
    
    public int getOrigXonScreen() {
        return (int) ((1+getOrigX())*getHalfScreenWidth());
    }

    public void setOrigX(float origX) {
        this.origX = origX;
        this.pcs.firePropertyChange("repaint", null, null);
    }

    public float getOrigY() {
        return origY;
    }
    
    public int getOrigYonScreen() {
        return (int) ((1+getOrigY())*getHalfScreenHeight());
    }

    public void setOrigY(float origY) {
        this.origY = origY;
        this.pcs.firePropertyChange("repaint", null, null);
    }
    
    public void setOrigXY(float origX, float origY) {
        this.origX = origX;
        this.origY = origY;
        this.pcs.firePropertyChange("repaint", null, null);
    }

    public int getFlashFreqHz() {
        return FlashFreqHz;
    }

    public void setFlashFreqHz(int FlashFreqHz) {
        this.FlashFreqHz = FlashFreqHz;
    }
    
    public boolean getFlash() {
        return Flash;
    }

    public float getWidth() {
        return width;
    }
    
    public int getWidthOnScreen() {
        return (int) (getWidth()*2*getHalfScreenWidth());
    }

    public void setWidth(float width) {
        this.width = width;
        this.pcs.firePropertyChange("repaint", null, null);
    }

    public float getHeight() {
        return height;
    }
    
    public int getHeightOnScreen() {
        return (int) (getHeight()*2*getHalfScreenHeight());
    }

    public void setHeight(float height) {
        this.height = height;
        this.pcs.firePropertyChange("repaint", null, null);
    }

    public int getHalfScreenWidth() {
        return halfScreenWidth;
    }

    public void setHalfScreenWidth(int halfScreenWidth) {
        this.halfScreenWidth = halfScreenWidth;
        this.pcs.firePropertyChange("repaint", null, null);
    }

    public int getHalfScreenHeight() {
        return halfScreenHeight;
    }

    public void setHalfScreenHeight(int halfScreenHeight) {
        this.halfScreenHeight = halfScreenHeight;
        this.pcs.firePropertyChange("repaint", null, null);
    }
    
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        this.pcs.addPropertyChangeListener(listener);
    }

    public MouseTrajectory getObjectPath() {
        return objectPath;
    }

    public void setObjectPath(MouseTrajectory objectPath) {
        this.objectPath.clear();
        this.objectPath.addAll(objectPath);
    }
    
    public void paintPath(Graphics2D g2,int width,int height) {
        if(isPathPaintEnabled()) this.objectPath.paintPath(g2, Color.blue, 1000, width, height);
    }

    public boolean isPathPaintEnabled() {
        return pathPaintEnabled;
    }

    public void setPathPaintEnabled(boolean paintPathEnabled) {
        this.pathPaintEnabled = paintPathEnabled;
    }

    public boolean isFlashEnabled() {
        return flashEnabled;
    }

    public void setFlashEnabled(boolean flashEnabled) {
        this.flashEnabled = flashEnabled;
    }

    public String getObjectName() {
        return objectName;
    }

    public boolean isPathLoop() {
        return pathLoop;
    }

    public void setPathLoop(boolean loopPath) {
        this.pathLoop = loopPath;
    }
    
    public Shape getObjectShape() {
        return objectShape;
    }

    public float getStroke() {
        return stroke;
    }

    public void setStroke(float stroke) {
        this.stroke = stroke;
    }
    
    private void updateShapeFrame() {
        int left = (int)(getOrigXonScreen()-getWidthOnScreen()/(float)2);
        int top  = (int)(getOrigYonScreen()-getHeightOnScreen()/(float)2);
        
        this.objectShape.setFrame(left, top, getWidthOnScreen(), getHeightOnScreen());
    }
    
    public void setObjectColor(Color color){
        this.hasGradient = false;
        this.objectColor = color;
    }
    
    /**
     *
     * @param numberCycles
     * @param samplePoints
     * @param startX startPoint relative to Object. 0 is left, 1 is right end of object
     * @param startY startPoint relative to Object. 0 is top, 1 is bottom end of object
     * @param endX endPoint relative to Object. 0 is left, 1 is right end of object
     * @param endY endPoint relative to Object. 0 is top, 1 is bottom end of object
     * @param color
     */
    public void setPaintGradient(int numberCycles, float startX, float startY, float endX, float endY, Color color) {
        this.hasGradient = true;
        int samplePoints = numberCycles *20;
        float[] fractions = new float[samplePoints+1];
        Color[] colors = new Color[samplePoints+1];
        
        for(int i=0;i<=samplePoints;i++) {
            fractions[i] = i*(1/(float)samplePoints); 
            double cosArg = Math.PI+((i*numberCycles*2*Math.PI)/samplePoints);
            double rectifiedCos = (Math.cos(cosArg)+1)/2;
            colors[i]    = new Color(color.getRed(),color.getGreen(),color.getBlue(),(int)(255*rectifiedCos));
        }
        this.gradFractions = fractions;
        this.gradColors = colors;
        this.gradPos = new float[] {startX,startY,endX,endY};
    }
    
    public void setPaintStripes(int numberStripes, float startX, float startY, float endX, float endY, Color color1, Color color2) {
        this.hasGradient = true;
        float[] fractions = new float[4*numberStripes];
        Color[] colors = new Color[4*numberStripes];
        
        for(int i=0;i<2*numberStripes;i++) {
            fractions[2*i]   = i    *(1/(float)(2*numberStripes))+(1/(float)(1000*numberStripes)); 
            fractions[2*i+1] = (i+1)*(1/(float)(2*numberStripes))-(1/(float)(1000*numberStripes)); 
            if(i%2 == 0){
                colors[2*i]   = color1;
                colors[2*i+1] = color1;
            }else{
                colors[2*i]   = color2;
                colors[2*i+1] = color2;
            }
        }
        this.gradFractions = fractions;
        this.gradColors = colors;
        this.gradPos = new float[] {startX,startY,endX,endY};
    }
    
    private Paint getUpdatedPaint() {
        if(hasGradient){
            int left = (int)(getOrigXonScreen()-getWidthOnScreen()/(float)2);
            int top  = (int)(getOrigYonScreen()-getHeightOnScreen()/(float)2);
            Point2D.Double startP = new Point2D.Double(left-(this.gradPos[0]*getWidthOnScreen()),top-(this.gradPos[1]*getHeightOnScreen()));
            Point2D.Double endP   = new Point2D.Double(left+(this.gradPos[2]*getWidthOnScreen())  ,top+(this.gradPos[3]*getHeightOnScreen()));

            LinearGradientPaint gradient = new LinearGradientPaint(startP, endP, this.gradFractions, this.gradColors);

            return gradient;
        } else {
            return objectColor;
        }
    }
    
    public void paint(Graphics2D g2) {
        if(!getFlash()) return;

        updateShapeFrame();
        g2.setStroke(new BasicStroke(stroke));
        if(stroke == 0){
            g2.setPaint(Color.white); //if the stroke is 0 we dont want to see a hairline but nothing.
        }else{
            g2.setPaint(Color.black);
        }
        g2.draw(this.objectShape);
        g2.setPaint(getUpdatedPaint());
        g2.fill(this.objectShape);
    }
    
}
