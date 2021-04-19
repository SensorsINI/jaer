/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.geom.Point2D;

import javax.swing.JFrame;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.freme.Freme;
import net.sf.jaer.graphics.ImageDisplay;

/**
 * Abstract class for filters that create a 2D Map of the event stream they 
 * receive
 * 
 * @author Christian
 */
public abstract class FremeExtractor extends EventFilter2D{
    
    protected boolean displayFreme=getPrefs().getBoolean(getClass().getSimpleName()+".displayFreme",true);
    {setPropertyTooltip("displayFreme","Should the allocation pixels be drawn");}
    
    protected boolean renderNew=getPrefs().getBoolean(getClass().getSimpleName()+".renderNew",false);
    {setPropertyTooltip("renderNew","Should only the new events be rendered");}
    
    protected float[] rgbValues;
    protected int sizeX, sizeY, size;
    
    protected ImageDisplay display;
    protected JFrame frame;
    
    /** Subclasses should call this super initializer */
    public FremeExtractor(AEChip chip) {
        super(chip);
        this.chip = chip;
    }

    /** Subclasses implement this method to define custom processing.
    @param in the input packet
    @return the output packet
     */
    @Override
    public abstract EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in);
    
    /** Subclasses implement this method to reset the custom processing.
     */
    @Override
    public abstract void resetFilter();

    /** Subclasses implement this method to initialize the custom processing.
     */
    @Override
    public abstract void initFilter();
    
    /** Subclasses implement this method to ensure that the map has the right 
     * size and is not empty.
     */
    public void checkDisplay(){
        if(sizeX != chip.getSizeX() || sizeY != chip.getSizeY() || size != sizeX*sizeY){
            sizeX = chip.getSizeX();
            sizeY = chip.getSizeY();
            size = sizeX*sizeY;
        }
        if(rgbValues == null || rgbValues.length != 3*size){
            rgbValues = new float[3*size];
        }
        if(frame == null || display == null || display.getWidth() != sizeX || display.getHeight() != sizeY){
            display = ImageDisplay.createOpenGLCanvas(); // makde a new ImageDisplay GLCanvas with default OpenGL capabilities
            display.setImageSize(sizeX, sizeY); // set dimensions of image      
            
            frame = new JFrame(getClass().getSimpleName());  // make a JFrame to hold it
            frame.setPreferredSize(new Dimension(sizeX*4, sizeY*4));  // set the window size
            frame.getContentPane().add(display, BorderLayout.CENTER); // add the GLCanvas to the center of the window
            frame.pack(); // otherwise it wont fill up the display
        }
        
        if(this.isFilterEnabled() && this.isDisplayFreme() && !frame.isVisible()){
            frame.setVisible(true);
        }
        
        if((!this.isFilterEnabled() || !this.isDisplayFreme()) && frame.isVisible()){
            frame.setVisible(false);
        }
    }
    
    public abstract void checkFreme();
    
    public abstract Freme<? extends Object> getFreme();   
    
    public abstract void setRGB(int idx);
    
    /** Subclasses call this method after processing a packet
     */
    public void repaintFreme(){
        checkDisplay();
        if(!this.isDisplayFreme())return;
        display.checkPixmapAllocation();
        display.setPixmapArray(rgbValues);
        display.repaint();
    }
    
        
    /** Calculates the index of a coordinate
     @param x x coordinate 
     @param y y coordinate
     @return the index of the according coordinate
     */
    public int getIndex(int x, int y){
        return (y*sizeX+x);
    }
    
    /** Calculates the coordiantes of a index as Point2D
     @param idx the index 
     @return the coordinates of the index as Point2D
     */
    public Point2D.Float getCoordinates(int idx){
        int cX = idx%sizeY;
        int cY = idx/sizeY;
        return new Point2D.Float((float)cX, (float)cY);
    }
    
    /**
     * @return the displayFreme
     */
    public boolean isDisplayFreme() {
        return displayFreme;
    }

    /**
     * @param displayFreme the displayFreme to set
     */
    public void setDisplayFreme(boolean displayFreme) {
        this.displayFreme = displayFreme;
        prefs().putBoolean(getClass().getSimpleName()+".displayFreme", displayFreme);
    }
    
    /**
     * @return the renderNew
     */
    public boolean isRenderNew() {
        return renderNew;
    }

    /**
     * @param renderNew the renderNew to set
     */
    public void setRenderNew(boolean renderNew) {
        this.renderNew = renderNew;
        prefs().putBoolean(getClass().getSimpleName()+".renderNew", renderNew);
    }
    
}
