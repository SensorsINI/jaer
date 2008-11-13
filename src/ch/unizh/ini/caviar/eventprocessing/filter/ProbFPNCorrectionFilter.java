/*
 * ProbFPNCorrectionFilter.java
 *
 * Created on June 16, 2006, 10:00 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright June 16, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package ch.unizh.ini.caviar.eventprocessing.filter;

import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.event.OutputEventIterator;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import ch.unizh.ini.caviar.graphics.FrameAnnotater;
import java.awt.Graphics2D;
import java.util.*;
import javax.media.opengl.*;
import javax.media.opengl.GLAutoDrawable;

/**
 * Adjust probability of transmission of event so that average rate of activity is the same for all cells.
 It does this by measuring average rate for each cell and using the global average rate, adjusts the probability for each cell to make its expectation
 firing rate the same as the global average.
 * @author tobi
 */
public class ProbFPNCorrectionFilter extends EventFilter2D implements FrameAnnotater{
    
    float[][][] isi;
    int[][][] lastTs;
    float[] avgIsi;
    Random random=new Random();
    private float alpha=.9f;
    private float mixingFactor = 1e-2f;
    float avgIsiMixingFactor=mixingFactor; // learning update rate, each event's ISI is mixed with prior rate estimate by this factor
    private boolean annotationEnabled=false;
    final int DEFAULT_ISI=10000000; // ten seconds
    
    /**
     * Creates a new instance of ProbFPNCorrectionFilter
     */
    public ProbFPNCorrectionFilter(AEChip chip) {
        super(chip);
    }
    
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!isFilterEnabled()) return in;
        checkOutputPacketEventType(in);
        checkMap(in);
        setMixingFactor(mixingFactor); // to set global avg mixing factor
        OutputEventIterator oi=out.outputIterator();
        for(Object o:in){
            PolarityEvent e=(PolarityEvent)o;
            int type=e.getType();
            float lastIsi=isi[e.x][e.y][e.type];
            int dt=e.timestamp-lastTs[e.x][e.y][e.type];
            float newIsi=lastIsi*(1-mixingFactor)+dt*mixingFactor;
            avgIsi[type]=avgIsi[type]+(newIsi-avgIsi[type])*avgIsiMixingFactor;
            lastTs[e.x][e.y][type]=e.timestamp;
            isi[e.x][e.y][type]=newIsi;
            float r=random.nextFloat();
            float prob=probOfTransmission(newIsi,type);
            if(r<=prob){
                PolarityEvent oe=(PolarityEvent)oi.nextOutput();
                oe.copyFrom(e);
            }
        }
        return out;
    }
    
    void checkMap(EventPacket in){
        if(isi==null || isi.length!=chip.getSizeX() || isi[0].length!=chip.getSizeY() || isi[0][0].length!=in.getNumCellTypes()){
            isi=new float[chip.getSizeX()][chip.getSizeY()][in.getNumCellTypes()];
            lastTs=new int[chip.getSizeX()][chip.getSizeY()][in.getNumCellTypes()];
            avgIsi=new float[chip.getNumCellTypes()];
        }
        resetFilter();
    }
    
    public Object getFilterState() {
        return null;
    }
    
    synchronized public void resetFilter() {
        if(!isFilterEnabled()) return;
        Arrays.fill(avgIsi,DEFAULT_ISI);
        for(int x=0; x<isi.length;x++)
            for(int y=0;y<isi[0].length;y++)
                for(int t=0;t<isi[0][0].length;t++)
                    isi[x][y][t]=DEFAULT_ISI;
    }
    
    float probOfTransmission(float isi,int type){
        return alpha*isi/avgIsi[type];
    }
    
    public void initFilter() {
    }
    
    public float getMixingFactor() {
        return mixingFactor;
    }
    
    public void setMixingFactor(float mixingFactor) {
        this.mixingFactor = mixingFactor;
        avgIsiMixingFactor=mixingFactor/(chip.getNumCells());
    }
    
    public float getAlpha() {
        return alpha;
    }
    
    public void setAlpha(float alpha) {
        this.alpha = alpha;
    }
    
    public void annotate(float[][][] frame) {
    }
    
    public void annotate(Graphics2D g) {
    }
    
    public void annotate(GLAutoDrawable drawable) {
        if(!annotationEnabled) return;
        GL gl=drawable.getGL();
        gl.glPushMatrix();
        gl.glClearColor(0,0,0,0);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        int sx=chip.getSizeX();
        int sy=chip.getSizeY();
        int st=chip.getNumCellTypes();
        float[] rgb=new float[3];
        for(int x=0;x<sx;x++){
            for(int y=0;y<sy;y++){
                for(int t=0;t<st;t++){
                    float v=probOfTransmission(isi[x][y][t],t);
                    rgb[t]=v;
                }
                gl.glColor3fv(rgb,0);
                gl.glRectf(x,y,x+1,y+1);
            }
        }
        gl.glPopMatrix();
    }
    
    public boolean isAnnotationEnabled() {
        return annotationEnabled;
    }
    
    public void setAnnotationEnabled(boolean annotationEnabled) {
        this.annotationEnabled = annotationEnabled;
    }
    
}
