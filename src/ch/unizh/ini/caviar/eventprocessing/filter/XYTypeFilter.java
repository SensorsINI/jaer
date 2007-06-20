/*
 * RetinaBackgrondActivityFilter.java
 *
 * Created on October 21, 2005, 12:33 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package ch.unizh.ini.caviar.eventprocessing.filter;

import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import ch.unizh.ini.caviar.graphics.FrameAnnotater;
import java.awt.Graphics2D;
import java.util.*;
import java.util.prefs.*;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.*;

/**
 * An AE filter that filters for a range of x,y,type address. These values are persistent and can be used to filter out borders of the input or particular
 types of input events.
 *
 * @author tobi
 */
public class XYTypeFilter extends EventFilter2D implements FrameAnnotater, Observer {
    static Preferences prefs=Preferences.userNodeForPackage(XYTypeFilter.class);
    public boolean isGeneratingFilter(){ return false;}
    private int startX=prefs.getInt("XYTypeFilter.startX",0);
    private int endX=prefs.getInt("XYTypeFilter.endX",0);
    private boolean xEnabled=prefs.getBoolean("XYTypeFilter.xEnabled",false);
    private int startY=prefs.getInt("XYTypeFilter.startY",0);
    private int endY=prefs.getInt("XYTypeFilter.endY",0);
    private boolean yEnabled=prefs.getBoolean("XYTypeFilter.yEnabled",false);
    private int startType=prefs.getInt("XYTypeFilter.startType",0);
    private int endType=prefs.getInt("XYTypeFilter.endType",0);
    private boolean typeEnabled=prefs.getBoolean("XYTypeFilter.typeEnabled",false);
    
    public short x=0, y=0;
    public byte type=0;
    
    private short xAnd;
    private short yAnd;
    private byte typeAnd;
    
    public XYTypeFilter(AEChip chip){
        super(chip);
        resetFilter();
        chip.getCanvas().addAnnotator(this);
    }
    
    int maxEvents=0;
    int index=0;
    private short xspike,yspike;
    private byte typespike;
    private int ts,repMeasure,i;
    
    /**
     * filters in to out. if filtering is enabled, the number of out may be less
     * than the number put in
     *@param in input events can be null or empty.
     *@return the processed events, may be fewer in number. filtering may occur in place in the in packet.
     */
    synchronized public EventPacket filterPacket(EventPacket in) {
        if(in==null) return null;
        if(!filterEnabled) return in;
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        int i;
        
        // filter
        
        int n=in.getSize();
        if(n==0) return in;
        checkOutputPacketEventType(in);
        OutputEventIterator outItr=out.outputIterator();
        
        // for each event only write it to the tmp buffers if it matches
        for(Object obj:in){
            BasicEvent e=(BasicEvent)obj;
            
            // if we pass all tests then pass event
            if(xEnabled && (e.x<startX || e.x>endX) ) continue;
            if(yEnabled && (e.y<startY || e.y>endY)) continue;
            if(typeEnabled){
                TypedEvent te=(TypedEvent)e;
                if(te.type<startType || te.type>endType) continue;
                outItr.nextOutput().copyFrom(te);
            }else{
                outItr.nextOutput().copyFrom(e);
            }
        }
        
        return out;
    }
    
    
    public Object getFilterState() {
        return null;
    }
    
    synchronized public void resetFilter() {
//        startX=0; endX=chip.getSizeX();
//        startY=0; endY=chip.getSizeY();
//        startType=0; endType=chip.getNumCellTypes();
    }
    
    public void initFilter() {
        resetFilter();
    }
    
    private int clip(int val, int limit){
        if(val>limit && limit != 0) return limit; else if(val<0) return 0; 
        return val;
    }

    public int getStartX() {
        return startX;
    }
    
    public void setStartX(int startX) {
        startX=clip(startX,chip.getSizeX());
        this.startX = startX;
        prefs.putInt("XYTypeFilter.startX",startX);
    }
    
    public int getEndX() {
        return endX;
    }
    
    public void setEndX(int endX) {
        endX=clip(endX,chip.getSizeX());
        this.endX = endX;
        prefs.putInt("XYTypeFilter.endX",endX);
    }
    
    public boolean isXEnabled() {
        return xEnabled;
    }
    
    public void setXEnabled(boolean xEnabled) {
        this.xEnabled = xEnabled;
        prefs.putBoolean("XYTypeFilter.xEnabled",xEnabled);
    }
    
    public int getStartY() {
        return startY;
    }
    
    public void setStartY(int startY) {
        startY=clip(startY,chip.getSizeY());
        this.startY = startY;
        prefs.putInt("XYTypeFilter.startY",startY);
    }
    
    public int getEndY() {
        return endY;
    }
    
    public void setEndY(int endY) {
        endY=clip(endY,chip.getSizeY());
        this.endY = endY;
        prefs.putInt("XYTypeFilter.endY",endY);
    }
    
    public boolean isYEnabled() {
        return yEnabled;
    }
    
    public void setYEnabled(boolean yEnabled) {
        this.yEnabled = yEnabled;
        prefs.putBoolean("XYTypeFilter.yEnabled",yEnabled);
    }
    
    public int getStartType() {
        return startType;
    }
    
    public void setStartType(int startType) {
        startType=clip(startType,chip.getNumCellTypes());
        this.startType = startType;
        prefs.putInt("XYTypeFilter.startType",startType);
    }
    
    public int getEndType() {
        return endType;
    }
    
    public void setEndType(int endType) {
        endType=clip(endType,chip.getNumCellTypes());
        this.endType = endType;
        prefs.putInt("XYTypeFilter.endType",endType);
    }
    
    public boolean isTypeEnabled() {
        return typeEnabled;
    }
    
    
    public void setTypeEnabled(boolean typeEnabled) {
        this.typeEnabled = typeEnabled;
        prefs.putBoolean("XYTypeFilter.typeEnabled",typeEnabled);
    }

    public void annotate(float[][][] frame) {
    }

    public void annotate(Graphics2D g) {
    }

    public void annotate(GLAutoDrawable drawable) {
        if(!isAnnotationEnabled()) return;
        if(!isFilterEnabled()) return;
        GL gl=drawable.getGL();
        gl.glPushMatrix();
        {
            gl.glColor3f(0,0,1);
            gl.glLineWidth(2f);
            gl.glBegin(gl.GL_LINE_LOOP);
            gl.glVertex2i(startX,startY);
            gl.glVertex2i(endX,startY);
            gl.glVertex2i(endX,endY);
            gl.glVertex2i(startX,endY);
            gl.glEnd();
        }
        gl.glPopMatrix();
        
    }

    public void update(Observable o, Object arg) {
        chip.getCanvas().addAnnotator(this);
    }
    
}
