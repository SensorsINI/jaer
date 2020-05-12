package ch.unizh.ini.jaer.projects.rccar;

import java.awt.Graphics2D;
import java.util.Observable;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * @author chbraen
 * The PerspeTransform is a transformation filter that accounts for two kinds of distortion: 
 * -The perspective distortion is corrected by a linear horizontal squeezing (if ratio = 1 --> no correction)
 * -The lens distortion is corrected by a firt order radial correction (if k1 = 0 --> no correction)
 * To transformation information is stored in look up matrices which redirect the events
 * Additionally the filter cuts of all events above the horizon which is approximated by a hyperbole
 */
public class PerspecTransform extends EventFilter2D implements FrameAnnotater {
    public boolean isGeneratingFilter(){ return false;}
    private int horizon=getPrefs().getInt("PerspecTransform.horizon",90);
    {setPropertyTooltip("horizon","the height of the horizon (in pixles)");}
    private float horizonFactor=getPrefs().getFloat("PerspecTransform.horizonFactor",0);
    {setPropertyTooltip("horizonFactor","the curvature of the horizon");}
    private float ratio=getPrefs().getFloat("PerspecTransform.ratio",0.5f);
    {setPropertyTooltip("ratio","The ratio of the horizon to the with at the lower end of the picture");}
    private boolean lensEnabled=getPrefs().getBoolean("PerspecTransform.lensEnabled",false);
    {setPropertyTooltip("lenseEnabled","should the distortion of the lens be respected");}
    private float k1=getPrefs().getFloat("PerspecTransform.k1",0.001f);
    {setPropertyTooltip("k1","lense distortion coefficent 1");}
    
    //the redirection matrices
    private int[][] dx;
    private int[][] dy;
    //the filter matrix for the horizon
    private boolean[][] pass;
    
    private int sx;
    private int mx;
    private int sy;
    private int my;
    private float horizonFactorB;
    private float factor;
    private float alpha;
    private float r;
    private float ro;
    
    FilterChain preFilterChain;
    private OrientationCluster orientationCluster;
    
    //the filterchain has to be set up
    public PerspecTransform(AEChip chip){
        super(chip);
        
        preFilterChain = new FilterChain(chip);
        orientationCluster = new OrientationCluster(chip);
        
        this.setEnclosedFilter(orientationCluster);
        
        orientationCluster.setEnclosed(true, this);

        
        buildMatrix();
    }
    
    /**
     */
    synchronized public EventPacket filterPacket(EventPacket in) {
        if(in==null) return null;
        if(!filterEnabled) return in;
        if(dx == null || pass == null) {
            resetFilter();
            return in;
        }
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        
        // filter
        
        int n=in.getSize();
        if(n==0) return in;
        checkOutputPacketEventType(in);
        OutputEventIterator outItr=out.outputIterator();

     
        for(Object obj:in){
            TypedEvent e=(TypedEvent)obj;
            TypedEvent o=(TypedEvent)outItr.nextOutput();
            //check if it is under the horizon
            if(pass[e.x][e.y]){
                o.copyFrom(e);
                //transform it
                o.setX((short)(e.x+dx[e.x][e.y]));
                o.setY((short)(e.y+dy[e.x][e.y]));
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
        dx = new int[sx][sy];
        dy = new int[sx][sy];
        pass = new boolean[sx][sy];
        buildMatrix();
    }
    
    public void initFilter() {
        resetFilter();
    }
    
    private void buildMatrix(){
        //the pass matrix has to be set up
        //the horizon is described by a hyperbole with y=horizonFactorB*x^2 + horizonFactor
        horizonFactorB=-horizonFactor/(mx*mx);
        for(int y=0; y<sy; y++){
            for(int x=0; x<sx; x++){
                if(y<horizon || ((y-horizon)<horizonFactorB*(x-mx)*(x-mx)+horizonFactor)) pass[x][y] = true;
                else pass[x][y] = false;
            }
        }
        //the transformation matrix has to be calculated
        for(int y=0; y<sy; y++){
            //the factor of how strong the events of a horizontal line have to be squeezed has to be set up
            factor = (1-ratio)*((float)horizon - (float)y)/(float)horizon;
            for(int x=0; x<sx; x++){
                //the angle of the radial vector (from the center of the image) has to be calculated
                if(x!=mx){
                    alpha = (float)Math.atan(Math.abs(((float)my-(float)y)/((float)mx-(float)x)));
                } else {
                    alpha = (float)(Math.signum(x)*Math.PI/2);
                }
                //the length of the original radius is calculated
                ro = (float)Math.sqrt((mx-x)*(mx-x)+(my-y)*(my-y));
                if(lensEnabled){
                    //-->lens distorion
                    //the radius gets transformed
                    r = lenseTransform(ro);
                    //the transformation matrices getString set up
                    dx[x][y] = (int)((r/ro)*Math.cos(alpha)*Math.signum(mx-x));
                    dy[x][y] = (int)((r/ro)*Math.sin(alpha)*Math.signum(my-y));
                } else {
                    dx[x][y]=0;
                    dy[x][y]=0;
                }
                //--> perspective distorion
                dx[x][y] = dx[x][y] + (short)((float)(mx-x)*factor);
                
            }
        }

        //-->uncomment to see the transformation matrix
        /*for(int y=horizon-1; y>=0; y--){
            for(int x=0; x<sx; x++){
                System.out.print(dx[x][y] + " ");
            }
            System.out.println();
        }*/
    }
    
    private float lenseTransform(float r){
        return (float)(r*(1+(k1*(r*r))));
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

    public float getHorizonFactor() {
        return horizonFactor;
    }
    
    public void setHorizonFactor(float horizonFactor) {
        this.horizonFactor = horizonFactor;
        getPrefs().putFloat("PerspecTransform.horizonFactor",horizonFactor);
        resetFilter();
    }
    
    public boolean isLensEnabled() {
        return lensEnabled;
    }
    
    public void setLensEnabled(boolean lensEnabled) {
        this.lensEnabled = lensEnabled;
        getPrefs().putBoolean("PerspecTransform.lensEnabled",lensEnabled);
        resetFilter();
    }
    
    public float getK1() {
        return k1;
    }
    
    public void setK1(float k1) {
        this.k1 = k1;
        getPrefs().putFloat("PerspecTransform.k1",k1);
        resetFilter();
    }
    
    public OrientationCluster getOrientationCluster() {
        return orientationCluster;
    }

    public void setOrientationCluster(OrientationCluster orientationCluster) {
        this.orientationCluster = orientationCluster;
    }
    
    public void annotate(float[][][] frame) {
    }
    
    public void annotate(Graphics2D g) {
    }
    
    public void annotate(GLAutoDrawable drawable) {
        if(!isAnnotationEnabled() || !isFilterEnabled()) return;
        if(!isFilterEnabled()) return;
        GL2 gl=drawable.getGL().getGL2();
        gl.glPushMatrix();
        {
            
        }
        gl.glPopMatrix();
        
    }
    
}

