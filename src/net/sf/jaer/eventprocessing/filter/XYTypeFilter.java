/*
 * RetinaBackgrondActivityFilter.java
 *
 * Created on October 21, 2005, 12:33 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package net.sf.jaer.eventprocessing.filter;

import net.sf.jaer.chip.*;
import net.sf.jaer.event.*;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import java.awt.Graphics2D;
import java.util.*;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.*;

/**
 * An AE filter that filters for a range of x,y,type address. These values are persistent and can be used to filter out borders of the input or particular
 * types of input events. A rectangular region may either be passed (default) or blocked.
 *
 * @author tobi
 */
public class XYTypeFilter extends EventFilter2D implements FrameAnnotater, Observer {
    public boolean isGeneratingFilter(){ return false;}
    private int startX=getPrefs().getInt("XYTypeFilter.startX",0);
    {setPropertyTooltip("startX","starting column");}
    private int endX=getPrefs().getInt("XYTypeFilter.endX",0);
    {setPropertyTooltip("endX","ending column");}
    private boolean xEnabled=getPrefs().getBoolean("XYTypeFilter.xEnabled",false);
    {setPropertyTooltip("xEnabled","filter based on column");}
    private int startY=getPrefs().getInt("XYTypeFilter.startY",0);
    {setPropertyTooltip("startY","starting row");}
    private int endY=getPrefs().getInt("XYTypeFilter.endY",0);
    {setPropertyTooltip("endY","ending row");}
    private boolean yEnabled=getPrefs().getBoolean("XYTypeFilter.yEnabled",false);
    {setPropertyTooltip("yEnabled","filter based on row");}
    private int startType=getPrefs().getInt("XYTypeFilter.startType",0);
    {setPropertyTooltip("startType","starting cell type");}
    private int endType=getPrefs().getInt("XYTypeFilter.endType",0);
    {setPropertyTooltip("endType","ending cell type");}
    private boolean typeEnabled=getPrefs().getBoolean("XYTypeFilter.typeEnabled",false);
    {setPropertyTooltip("typeEnabled","filter based on cell type");}
    private boolean invertEnabled=getPrefs().getBoolean("XYTypeFilter.invertEnabled",false);
    {setPropertyTooltip("invertEnabled","invert so that events inside region are blocked");}
    public short x=0, y=0;
    public byte type=0;
    
    private short xAnd;
    private short yAnd;
    private byte typeAnd;
    
    public XYTypeFilter(AEChip chip){
        super(chip);
        resetFilter();
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
            if(!invertEnabled){
                // if we pass all 'inside' tests then pass event, otherwise continue to next event
                if(xEnabled && (e.x<startX || e.x>endX) ) continue; // failed xtest, x outisde, goto next event
                if(yEnabled && (e.y<startY || e.y>endY)) continue;
                if(typeEnabled){
                    TypedEvent te=(TypedEvent)e;
                    if(te.type<startType || te.type>endType) continue;
                    pass(outItr,te);
                }else{
                    pass(outItr,e);
                }
            }else{
                // if we pass all outside tests then any test pass event
                if(xEnabled && (e.x>=startX && e.x<=endX) )
                    if(yEnabled && (e.y>=startY && e.y<=endY))
                        if(typeEnabled){
                    TypedEvent te=(TypedEvent)e;
                    if(te.type>=startType && te.type<=endType) continue;
                        }else{
                    continue;
                        }
                pass(outItr,e);
            }
        }
        
        return out;
    }
    
    private void pass(OutputEventIterator outItr, BasicEvent e){
        outItr.nextOutput().copyFrom(e);
    }
    
    private void pass(OutputEventIterator outItr, TypedEvent te){
        outItr.nextOutput().copyFrom(te);
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
        getPrefs().putInt("XYTypeFilter.startX",startX);
        setXEnabled(true);
    }
    
    public int getEndX() {
        return endX;
    }
    
    public void setEndX(int endX) {
        endX=clip(endX,chip.getSizeX());
        this.endX = endX;
        getPrefs().putInt("XYTypeFilter.endX",endX);
        setXEnabled(true);
    }
    
    public boolean isXEnabled() {
        return xEnabled;
    }
    
    public void setXEnabled(boolean xEnabled) {
        this.xEnabled = xEnabled;
        getPrefs().putBoolean("XYTypeFilter.xEnabled",xEnabled);
    }
    
    public int getStartY() {
        return startY;
    }
    
    public void setStartY(int startY) {
        startY=clip(startY,chip.getSizeY());
        this.startY = startY;
        getPrefs().putInt("XYTypeFilter.startY",startY);
        setYEnabled(true);
    }
    
    public int getEndY() {
        return endY;
    }
    
    public void setEndY(int endY) {
        endY=clip(endY,chip.getSizeY());
        this.endY = endY;
        getPrefs().putInt("XYTypeFilter.endY",endY);
        setYEnabled(true);
    }
    
    public boolean isYEnabled() {
        return yEnabled;
    }
    
    public void setYEnabled(boolean yEnabled) {
        this.yEnabled = yEnabled;
        getPrefs().putBoolean("XYTypeFilter.yEnabled",yEnabled);
    }
    
    public int getStartType() {
        return startType;
    }
    
    public void setStartType(int startType) {
        startType=clip(startType,chip.getNumCellTypes());
        this.startType = startType;
        getPrefs().putInt("XYTypeFilter.startType",startType);
        setTypeEnabled(true);
    }
    
    public int getEndType() {
        return endType;
    }
    
    public void setEndType(int endType) {
        endType=clip(endType,chip.getNumCellTypes());
        this.endType = endType;
        getPrefs().putInt("XYTypeFilter.endType",endType);
        setTypeEnabled(true);
    }
    
    public boolean isTypeEnabled() {
        return typeEnabled;
    }
    
    
    public void setTypeEnabled(boolean typeEnabled) {
        this.typeEnabled = typeEnabled;
        getPrefs().putBoolean("XYTypeFilter.typeEnabled",typeEnabled);
    }
    
    public void annotate(float[][][] frame) {
    }
    
    public void annotate(Graphics2D g) {
    }
    
    public void annotate(GLAutoDrawable drawable) {
        if(!isAnnotationEnabled() || !isFilterEnabled()) return;
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
    }
    
    public boolean isInvertEnabled() {
        return invertEnabled;
    }
    
    public void setInvertEnabled(boolean invertEnabled) {
        this.invertEnabled = invertEnabled;
        getPrefs().putBoolean("XYTypeFilter.invertEnabled",invertEnabled);
    }
    
}
