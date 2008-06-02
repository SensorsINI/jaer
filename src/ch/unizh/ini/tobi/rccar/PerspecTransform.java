package ch.unizh.ini.tobi.rccar;

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
 * @author chbraen
 */
public class PerspecTransform extends EventFilter2D implements FrameAnnotater, Observer {
    public boolean isGeneratingFilter(){ return false;}
    private int horizon=getPrefs().getInt("PerspecTransform.horizon",90);
    {setPropertyTooltip("horizon","the height of the horizon (in pixles)");}
    private float ratio=getPrefs().getFloat("PerspecTransform.ratio",0.8f);
    {setPropertyTooltip("ratio","The ratio of the horizon to the with at the lower end of the picture");}
    //private boolean lensEnabled=getPrefs().getBoolean("PerspecTransform.lensEnabled",false);
    {setPropertyTooltip("lenseEnabled","should the flexion of the lens be respected");}
    //private int lensFlex=getPrefs().getInt("PerspecTransform.lensFlex",0);
    {setPropertyTooltip("lensFlex","how stron should the lense flexion be (in pixels)");}
    
    private short[][] dx;
    private int sx;
    private int mx;
    private int sy;
    private int my;
    private float xRatio;
    private float factor;
    
    public PerspecTransform(AEChip chip){
        super(chip);
        resetFilter();
        chip.getCanvas().addAnnotator(this);
    }
    
    /**
     */
    synchronized public EventPacket filterPacket(EventPacket in) {
        if(in==null) return null;
        if(!filterEnabled) return in;
        if(dx == null) return in;
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        
        // filter
        
        int n=in.getSize();
        if(n==0) return in;
        checkOutputPacketEventType(in);
        OutputEventIterator outItr=out.outputIterator();
        
        // for each event only write it to the tmp buffers if it matches
        for(Object obj:in){
            TypedEvent e=(TypedEvent)obj;
            TypedEvent o=(TypedEvent)outItr.nextOutput();
            if(e.y<horizon){
                o.copyFrom(e);
                o.setX((short)(e.x+dx[e.x][e.y]));
            }
        }
        return out;
    }
    
    public Object getFilterState() {
        return null;
    }
    
    synchronized public void resetFilter() {
        if(chip == null) return;
        sx = chip.getSizeX();
        mx = sx/2;
        sy = chip.getSizeY();
        my = sy/2;
        dx = new short[sx][sy];
        buildMatrix();
    }
    
    public void initFilter() {
        resetFilter();
    }
    
    private void buildMatrix(){
        for(int y=0; y<horizon; y++){
            factor = (1-ratio)*((float)horizon - (float)y)/(float)horizon;
            for(int x=0; x<sx; x++){
                dx[x][y] = (short)((float)(mx-x)*factor);
            }
        }
    }
    
    public int getHorizon() {
        return horizon;
    }
    
    public void setHorizon(int horizon) {
        this.horizon = horizon;
        getPrefs().putInt("PerspecTransform.horizon",horizon);
        resetFilter();
    }
    
    public float getRatio() {
        return ratio;
    }
    
    public void setRatio(float ratio) {
        this.ratio = ratio;
        getPrefs().putFloat("PerspecTransform.ratio",ratio);
        resetFilter();
    }
    
    /*public boolean isLensEnabled() {
        return lensEnabled;
    }
    
    public void setLensEnabled(boolean lensEnabled) {
        this.lensEnabled = lensEnabled;
        getPrefs().putBoolean("PerspecTransform.lensEnabled",lensEnabled);
        resetFilter();
    }
    
    public int getLensFlex() {
        return lensFlex;
    }
    
    public void setLensFlex(int lensFlex) {
        this.lensFlex = lensFlex;
        getPrefs().putInt("PerspecTransform.lensFlex",lensFlex);
        setLensEnabled(true);
        resetFilter();
    }
    */
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
            
        }
        gl.glPopMatrix();
        
    }
    
    public void update(Observable o, Object arg) {
        chip.getCanvas().addAnnotator(this);
    }
    
}
