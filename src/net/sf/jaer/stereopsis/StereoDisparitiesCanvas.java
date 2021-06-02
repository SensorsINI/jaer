/*
 * StereoDisparitiesCanvas.java
 *
 * Created on 14. Juni 2006, 10:02
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package net.sf.jaer.stereopsis;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;

/**
 * Visualizes the accumulated weights for all possible disparities in a histogram.
 * The best disparity is marked red. (note: histogram maximum and best disparity are rarely the same, because
 * best disparity is lowpassfiltered)
 *
 * @author Peter Hess
 */
public class StereoDisparitiesCanvas extends Canvas {
    private int maxDisp;
    /** content of this array will be drawn as a histogram */
    private float[] disparities;
    private int bestDisp;
    /* width and maximal height of a histogram bar */
    private int cellWidth = 5;
    private int cellHeight = 30;
    /* for double buffering */
    private Image dbImage;
    private Graphics dbGraphics;
    
    /** Creates a new instance of StereoDisparitiesCanvas */
    public StereoDisparitiesCanvas(int maxDisp) {
        this.maxDisp = maxDisp;
        
        setSize(cellWidth*(2*maxDisp + 1), cellHeight);
        setBackground(Color.BLACK);
        disparities = new float[2*maxDisp + 1];
    }
    
    public void setCellSize(int cellWidth, int cellHeight) {
        if (cellWidth < 1) cellWidth = 1;
        if (cellHeight < 1) cellHeight = 1;
        this.cellWidth = cellWidth;
        this.cellHeight = cellHeight;
        setSize(cellWidth*(2*maxDisp + 1), cellHeight);
    }
    
    public Dimension getCellSize() {
        return new Dimension(cellWidth, cellHeight);
    }
    
    public void setDisparities(float[] disparities) {
        this.disparities = disparities;
    }
    
    public void setBestDisp(int bestDisp) {
        this.bestDisp = bestDisp;
    }
    
    public void paint(Graphics g) {
        super.paint(g);
        
        // calculate min and max values
        float minMatch = Float.MAX_VALUE;
        float maxMatch = Float.MIN_VALUE;
        for(int i = 0; i < disparities.length; i++) {
            if (disparities[i] < minMatch) minMatch = disparities[i];
            if (disparities[i] > maxMatch) maxMatch = disparities[i];
        }
        
        // draw grayscale image
        for(int i = 0; i < disparities.length; i++) {
            // grayscale value between 0 and 1
            float a = (disparities[i] - minMatch)/(maxMatch - minMatch);
            // catch division by zero
            // TODO sometimes strange behaviour: out of [0,1] range
            if (a > 1f) a = 1f;
            if (a < 0f) a = 0f;
            g.setColor(new Color(a, a, a));
            g.fillRect(cellWidth*i, (int)((1-a)*cellHeight), cellWidth, (int)(a*cellHeight + 1));
        }
        
        // draw best disparity
        g.setColor(Color.RED);
        g.drawRect(cellWidth*(disparities.length/2 + bestDisp), 0, cellWidth, cellHeight);
    }
    
    public void update(Graphics g) {
        // double buffering
        if (dbImage == null) dbImage = createImage(getWidth(), getHeight());
        dbGraphics = dbImage.getGraphics();
        dbGraphics.setColor(getBackground());
        dbGraphics.fillRect(0, 0, getWidth(), getHeight());
        paint(dbGraphics);
        g.drawImage(dbImage, 0, 0, this);
    }
}
