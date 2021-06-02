/*
 * StereoMatchingCanvas.java
 *
 * Created on 13. Juni 2006, 09:34
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package net.sf.jaer.stereopsis;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Image;

import net.sf.jaer.chip.AEChip;

/**
 * Visualizes a 2D matching matrix as a grayscale image. The colors are scaled s.t. the maximal values are white and the minimal
 * values are black.
 * Additionally the disparity with highest probability is shown as a diagonal.
 *
 * @author Peter Hess
 */
public class StereoMatchingCanvas extends Canvas {
    private AEChip chip;
    /** content of this 2D array will be drawn as a grayscale image */
    private float[][] matchings;
    private int bestDisp;
    /** pixel size for one cell in matchings[][] */
    private int cellSize = 3;
    /* for double buffering */
    private Image dbImage;
    private Graphics dbGraphics;
    
    /** Creates a new instance of StereoMatchingCanvas */
    public StereoMatchingCanvas(AEChip chip) {
        this.chip = chip;
        
        setSize(cellSize*chip.getSizeX(), cellSize*chip.getSizeX());
        setBackground(Color.BLACK);
        matchings = new float[chip.getSizeX()][chip.getSizeX()];
    }
    
    public void setCellSize(int cellSize) {
        if (cellSize < 1) cellSize = 1;
        this.cellSize = cellSize;
        setSize(cellSize*chip.getSizeX(), cellSize*chip.getSizeX());
    }
    
    public int getCellSize() {
        return cellSize;
    }
    
    public void setMatchings(float[][] matchings) {
        this.matchings = matchings;
    }
    
    public void setBestDisp(int bestDisp) {
        this.bestDisp = bestDisp;
    }
    
    public void paint(Graphics g) {
        super.paint(g);
        
        // calculate min and max values
        float minMatch = Float.MAX_VALUE;
        float maxMatch = Float.MIN_VALUE;
        for(int i = 0; i < matchings.length; i++) {
            for(int j = 0; j < matchings[i].length; j++) {
                if (matchings[i][j] < minMatch) minMatch = matchings[i][j];
                if (matchings[i][j] > maxMatch) maxMatch = matchings[i][j];
            }
        }
        
        // draw grayscale image
        for(int i = 0; i < matchings.length; i++) {
            for(int j = 0; j < matchings[i].length; j++) {
                // grayscale value between 0 and 1
                float a = (matchings[i][j] - minMatch)/(maxMatch - minMatch);
                // catch division by zero
                // TODO sometimes strange behaviour: out of [0,1] range
                if (a > 1f) a = 1f;
                if (a < 0f) a = 0f;
                g.setColor(new Color(a, a, a));
                g.fillRect(cellSize*i, getHeight()-1 - cellSize*j, cellSize, cellSize);
            }
        }
        
        // draw best disparity
        g.setColor(Color.RED);
        g.drawLine(0, getWidth()-1 + cellSize*bestDisp, getHeight()-1 + cellSize*bestDisp, 0);
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
