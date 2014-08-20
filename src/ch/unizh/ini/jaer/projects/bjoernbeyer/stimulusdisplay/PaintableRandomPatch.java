/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.bjoernbeyer.stimulusdisplay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.RectangularShape;
import javax.swing.JPanel;
import java.io.Serializable;
import java.util.Random;

/**
 *
 * @author Bjoern
 */
public class PaintableRandomPatch extends PaintableObject implements Serializable{
    private static final long serialVersionUID = 44L;
    private final static Random rand = new Random();
    private int numberPatchObjects = 1;
    private float maxObjectHeight, minObjectHeight, maxObjectWidth, minObjectWidth;
    private float[] objectHeight, objectWidth;
    private float[] objectLocX, objectLocY;
    private float[][] objectPathX, objectPathY;
    
    public PaintableRandomPatch(String objectName, RectangularShape objectShape, JPanel canvas) {
        super(objectName, objectShape, canvas);
        setRandomObjectNumber(1,20);
        setObjectSizeConstraints(.01f,.5f,.01f,.5f);
        setRandomObjectSize();
        setRandomObjectLocations(0f,1f);
    }
    
    public PaintableRandomPatch(String objectName, RectangularShape objectShape, JPanel canvas, float width, float height) {
        super(objectName, objectShape, canvas, width, height);
        setRandomObjectNumber(1,20);
        setObjectSizeConstraints(.01f,.5f,.01f,.5f);
        setRandomObjectSize();
        setRandomObjectLocations(0f,1f);
    }
    
    public final void setRandomObjectNumber(int min, int max) {
        setNumberPatchObjects(randInt(min,max));
    }
    
    //assumes same width and height
    public final void setRandomObjectSize() {
        int n = getNumberPatchObjects();
        objectHeight = new float[n];
        objectWidth  = new float[n];
        for(int i=0;i<n;i++){
            objectHeight[i] = randFloat(getMinObjectHeight(),getMaxObjectHeight());
            objectWidth[i]  = randFloat(getMinObjectWidth(),getMaxObjectWidth());
        }
    }
    
    public void setUniformObjectSize(float width, float height) {
        int n = getNumberPatchObjects();
        objectHeight = new float[n];
        objectWidth  = new float[n];
        for(int i=0;i<n;i++){
            objectHeight[i] = height;
            objectWidth[i]  = width;
        }
    }
    
    //assumes that both xPos and yPos are randomized
    public final void setRandomObjectLocations(float min, float max) {
        int n = getNumberPatchObjects();
        objectLocX = new float[n];
        objectLocY  = new float[n];
        for(int i=0;i<n;i++){
            objectLocX[i] = randFloat(min,max);
            objectLocY[i]  = randFloat(min,max);
        }
    }
    
    @Override public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        if(!getFlash()) return;
        
        g2.setStroke(new BasicStroke(getStroke()));
        
        g2.rotate(Math.toRadians(getAngle()), getX()+getWidth()/2, getY()+getHeight()/2);
        for(int i=0;i<getNumberPatchObjects();i++) {
//            System.out.println(getObjectLocXatIndex(i)+" , "+getObjectLocYatIndex(i)+" , "+getObjectWidthAtIndex(i)+" , "+getObjectHeightAtIndex(i));
            getObjectShape().setFrame(getX() + (getWidth()*(1-getObjectWidthAtIndex(i))*getObjectLocXatIndex(i)),
                                      getY() + (getHeight()*(1-getObjectHeightAtIndex(i))*getObjectLocYatIndex(i)),
                                      getWidth() *getObjectWidthAtIndex(i),
                                      getHeight()*getObjectHeightAtIndex(i));
            
            if(getStroke() == 0){
                g2.setPaint(Color.white); //if the stroke is 0 we dont want to see a hairline but nothing.
            }else{
                g2.setPaint(Color.black);
            }

            g2.draw(getObjectShape());
            g2.setPaint(super.getUpdatedPaint());
            g2.fill(getObjectShape());
        }
        g2.dispose();
        
        firePropertyChangeIfUpdated();
    }

    public float[] getObjectHeights() {
        return objectHeight;
    }
    
    public float getObjectHeightAtIndex(int index) {
        return objectHeight[index];
    }

    public void setObjectHeights(float[] objectHeights) {
        this.objectHeight = objectHeights;
    }

    public float[] getObjectWidths() {
        return objectWidth;
    }
    
    public float getObjectWidthAtIndex(int index) {
        return objectWidth[index];
    }

    public void setObjectWidths(float[] objectWidths) {
        this.objectWidth = objectWidths;
    }

    public float[] getObjectLocX() {
        return objectLocX;
    }
    
    public float getObjectLocXatIndex(int index) {
        return objectLocX[index];
    }

    public void setObjectLocX(float[] objectLocX) {
        this.objectLocX = objectLocX;
    }

    public float[] getObjectLocY() {
        return objectLocY;
    }
    
    public float getObjectLocYatIndex(int index) {
        return objectLocY[index];
    }

    public void setObjectLocY(float[] objectLocY) {
        this.objectLocY = objectLocY;
    }

    public float[][] getObjectPathX() {
        return objectPathX;
    }

    public void setObjectPathX(float[][] objectPathX) {
        this.objectPathX = objectPathX;
    }

    public float[][] getObjectPathY() {
        return objectPathY;
    }

    public void setObjectPathY(float[][] objectPathY) {
        this.objectPathY = objectPathY;
    }
    
    public int getNumberPatchObjects() {
        return numberPatchObjects;
    }
    
    public void setNumberPatchObjects(int numberPatchObjects) {
        int setValue = numberPatchObjects;
        if(setValue<1)setValue=1;
        this.numberPatchObjects = setValue;
    }

    public float getMaxObjectHeight() {
        return maxObjectHeight;
    }

    public void setMaxObjectHeight(float maxObjectHeight) {
        this.maxObjectHeight = maxObjectHeight;
    }

    public float getMinObjectHeight() {
        return minObjectHeight;
    }

    public void setMinObjectHeight(float minObjectHeight) {
        this.minObjectHeight = minObjectHeight;
    }
    
    public void setObjectHeights(float min, float max) {
        setMinObjectHeight(min);
        setMaxObjectHeight(max);
    }

    public float getMaxObjectWidth() {
        return maxObjectWidth;
    }

    public void setMaxObjectWidth(float maxObjectWidth) {
        this.maxObjectWidth = maxObjectWidth;
    }

    public float getMinObjectWidth() {
        return minObjectWidth;
    }

    public void setMinObjectWidth(float minObjectWidth) {
        this.minObjectWidth = minObjectWidth;
    }
    
    public void setObjectWidths(float min, float max) {
        setMinObjectWidth(min);
        setMaxObjectWidth(max);
    }
    
    public final void setObjectSizeConstraints(float minHeight, float maxHeight, float minWidth, float maxWidth) {
        setObjectHeights(minHeight,maxHeight);
        setObjectWidths(minWidth,maxWidth);
    }
    
    /**
     * Returns a pseudo-random number between min and max, inclusive.
     * The difference between min and max can be at most
     * <code>Integer.MAX_VALUE - 1</code>.
     *
     * @param min Minimum value
     * @param max Maximum value.  Must be greater than min.
     * @return Integer between min and max, inclusive.
     * @see java.util.Random#nextInt(int)
     */
    public static int randInt(int min, int max) {
        // nextInt is normally exclusive of the top value,
        // so add 1 to make it inclusive
        int randomNum = rand.nextInt((max - min) + 1) + min;

        return randomNum;
    }
    
    /**
     * Returns a pseudo-random number between min and max, exclusive of max.
     * The difference between min and max can be at most
     * <code>Integer.MAX_VALUE - 1</code>.
     *
     * @param min Minimum value
     * @param max Maximum value.  Must be greater than min.
     * @return Float between min and max, exclusive.
     * @see java.util.Random#nextFloat()
     */
    public static float randFloat(float min, float max) {
        // nextFloat is exclusive of the top value but we dont care
        float randomNum = (max-min)*rand.nextFloat() + min;
        
        return randomNum;
    }
}
