
package ch.unizh.ini.jaer.projects.bjoernbeyer.visualservo;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import java.util.Observable;
import java.util.Observer;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 *
 * @author Bjoern
 */
abstract class AbstractBackgroundSelectiveFilter extends EventFilter2D implements Observer, FrameAnnotater {
    
    /** ticks per ms of input time */
    protected final int US_PER_MS = 1000;
    protected final int minX = 0, minY = 0;
    
    protected int maxX, maxY;
     
    //with outerRadius=30 and innerRadius=20 we have 1576 pixels in the inhibitory
    // range. Hence roughly 10% of the overall 16'384 pixels.
    protected int inhibitionOuterRadiusPX       = getInt("inhibitionOuterRadius",30); //The outer radius of inhibition in pixel from the current position.
    protected int inhibitionInnerRadiusPX       = getInt("inhibitionInnerRadius",28); //The inner radius of inhibition (meaning pixel closer to current than this will not inhibit) in pixel.
    //with excitationRadius=5 we average over 80 pixels around the center pixel
    protected int excitationOuterRadiusPX       = getInt("excitationOuterRadius",6); //The excitation radius in pixel from current position. By default the current cell does not self excite.
    protected int excitationInnerRadiusPX       = getInt("excitationInnerRadius",1); //The excitation radius in pixel from current position. By default the current cell does not self excite.
    protected int inhibitoryCoarseness          = getInt("inhibitoryCoarseness",2);     
    protected int excitatoryCoarseness          = getInt("excitatoryCoarseness",1);
    protected float maxDtUs                     = getFloat("maxDtUs",50000f); //The maximum temporal distance in microseconds between the current event and the last event in an inhibiting or exciting location that is taken into acount for the averge inhibition/excitation vector. Events with a dt larger than this will be ignored. Events with half the dt than this will contribute with 50% of their length.
    protected boolean showRawInputEnabled       = getBoolean("showRawInputEnabled",false);
    protected boolean drawInhibitExcitePoints   = getBoolean("drawInhibitExcitePoints",false);
    protected boolean drawCenterCell            = getBoolean("drawCenterCell",false); 
    protected boolean showInhibitedEvents       = getBoolean("showInhibitedEvents", true);
    protected boolean outputPolarityEvents      = getBoolean("outputPolarityEvents", false);
    
    //Filter Variables
    protected byte hasGlobalMotion;
    // End filter Variables
    
    protected int[][] inhibitionCirc,excitationCirc;
    protected int x,y;
    
    
    public AbstractBackgroundSelectiveFilter(AEChip chip) {
        super(chip);
        initFilter();
        
        final String Inhib = "InhibitoryRegion", excite = "ExcitatoryRegion";
        setPropertyTooltip(Inhib,"inhibitionInnerRadius","");
        setPropertyTooltip(Inhib,"inhibitionOuterRadius","");
        setPropertyTooltip(Inhib,"inhibitoryCoarseness","");
        setPropertyTooltip(excite,"excitationInnerRadius","");
        setPropertyTooltip(excite,"excitationOuterRadius","");
        setPropertyTooltip(excite,"excitatoryCoarseness","");
    }
    
    @Override abstract public EventPacket<?> filterPacket(EventPacket<?> in); 
    abstract protected void checkMaps();
    
    @Override public void annotate(GLAutoDrawable drawable){
        if(!getOutputPolarityEvents()){
            GL2 gl = drawable.getGL().getGL2();
            gl.glLineWidth(3f);
            gl.glPointSize(2);

            if(getDrawInhibitExcitePoints()) {
                // <editor-fold defaultstate="collapsed" desc="-- annotate Pixels that where associated with global motion --">
                gl.glPushMatrix();
                    for (Object o : out) {
                        BackgroundInhibitedEvent e = (BackgroundInhibitedEvent) o;
                        float[][] c=chip.getRenderer().makeTypeColors(2);
                        gl.glColor3fv(c[e.hasGlobalMotion],0);
                        gl.glBegin(GL2.GL_POINTS);
                        gl.glVertex2d(e.x, e.y);
                        gl.glEnd();
                    }
                gl.glPopMatrix();
                // </editor-fold>
            }

            if(getDrawCenterCell()) {
                // <editor-fold defaultstate="collapsed" desc="-- annotates a exemplatory Center cell with inhibitory and excitatory region & the average activity in those regions --">
                gl.glPushMatrix();
                
                //Draw regions of average
                gl.glBegin(GL2.GL_POINTS);
                    gl.glColor3f(1, 1, 0);
                    for (int[] circ1 : inhibitionCirc) {
                        gl.glVertex2d(circ1[0]+maxX/2, circ1[1]+maxY/2);
                    }
                    gl.glColor3f(0, 1, 1);
                    for (int[] circ1 : excitationCirc) {
                        gl.glVertex2d(circ1[0]+maxX/2, circ1[1]+maxY/2);
                    }
                gl.glEnd();
                gl.glPopMatrix();
                // </editor-fold>
            }
        }
    }
 
    @Override public final void resetFilter() {
        checkMaps();
        
        maxX=chip.getSizeX();
        maxY=chip.getSizeY();
        
        inhibitionCirc = PixelCircle(inhibitionOuterRadiusPX,inhibitionInnerRadiusPX,inhibitoryCoarseness);
        excitationCirc = PixelCircle(excitationOuterRadiusPX,excitationInnerRadiusPX,excitatoryCoarseness);//inner Radius 1 means that the cell does not excite itself.
    }
    
    @Override public void initFilter() { 
        chip.addObserver(this);
        resetFilter(); 
    }

    @Override public void update(Observable o, Object arg) {
        if (o instanceof AEChip) {
            if (arg == AEChip.EVENT_SIZEX || arg == AEChip.EVENT_SIZEY) {
                resetFilter();
            }
        }
    }

    private int[][] PixelCircle(int outerRadius, int innerRadius, int coarseness){
        //savely oversizing the array, we return only the first n elements anyway
        // The integer sequence A000328 of pixels in a circle is expansive to compute
        // All upper bounds involve square roots and potentiation.
        int[][] pixCirc = new int[(1+4*outerRadius*outerRadius)-(4*innerRadius*innerRadius)][2]; 
        int n = 0;
        if(coarseness<1)coarseness=1;
        
        for(int xCirc = -outerRadius; xCirc<=outerRadius; xCirc+=coarseness) {
            for(int yCirc = -outerRadius; yCirc<=outerRadius; yCirc+=coarseness) {
                if(((xCirc*xCirc)+(yCirc*yCirc) <= (outerRadius*outerRadius)) && ((xCirc*xCirc)+(yCirc*yCirc) >= (innerRadius*innerRadius))){
                    pixCirc[n][0] = xCirc;
                    pixCirc[n][1] = yCirc;
                    n++;    
                }
            }
        }
        //Here we basically just copy the result into a new array with the exact size that we need.
        // This is not a problem as this only happens once when the precomputations are done.
        int[][] res = new int[n][2];
        for(int p=0;p<n;p++) {
            res[p][0] = pixCirc[p][0];
            res[p][1] = pixCirc[p][1];
        }
        return res;
    }

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --inhibitionOuterRadius--">
    public int getInhibitionOuterRadius() {
        return inhibitionOuterRadiusPX;
    }

    public void setInhibitionOuterRadius(final int inhibitionOuterRadius) {
        int setValue = inhibitionOuterRadius;
        if(setValue<=getInhibitionInnerRadius()){
            setValue = getInhibitionInnerRadius()+1;
        }
        support.firePropertyChange("inhibitionOuterRadius",this.inhibitionOuterRadiusPX,setValue);
        this.inhibitionOuterRadiusPX = setValue;
        putInt("inhibitionOuterRadius",setValue);
        resetFilter(); //need to recalculate the circles
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --inhibitionInnerRadius--">
    public int getInhibitionInnerRadius() {
        return inhibitionInnerRadiusPX;
    }

    public void setInhibitionInnerRadius(final int inhibitionInnerRadius) {
        int setValue = inhibitionInnerRadius;
        if(setValue>=getInhibitionOuterRadius()){
            setValue = getInhibitionOuterRadius()-1;
        }
        support.firePropertyChange("inhibitionInnerRadius",this.inhibitionInnerRadiusPX,setValue);
        this.inhibitionInnerRadiusPX = setValue;
        putInt("inhibitionInnerRadius",setValue);
        resetFilter(); //need to recalculate the circles
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --excitationOuterRadius--">
    public int getExcitationOuterRadius() {
        return excitationOuterRadiusPX;
    }

    public void setExcitationOuterRadius(final int excitationRadius) {
        int setValue = excitationRadius;
        if(setValue<=getExcitationInnerRadius()){
            setValue = getExcitationInnerRadius()+1;
        }
        support.firePropertyChange("excitationOuterRadius",this.excitationOuterRadiusPX,setValue);
        this.excitationOuterRadiusPX = setValue;
        putInt("excitationOuterRadius",setValue);
        resetFilter(); //need to recalculate the circles
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --excitationInnerRadius--">
    public int getExcitationInnerRadius() {
        return excitationInnerRadiusPX;
    }

    public void setExcitationInnerRadius(final int excitationRadius) {
        int setValue = excitationRadius;
        if(setValue>=getExcitationOuterRadius()){
            setValue = getExcitationOuterRadius()-1;
        }
        support.firePropertyChange("excitationInnerRadius",this.excitationInnerRadiusPX,setValue);
        this.excitationInnerRadiusPX = setValue;
        putInt("excitationInnerRadius",setValue);
        resetFilter(); //need to recalculate the circles
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --maxDtMs--">
    public float getMaxDtMs() {
      return maxDtUs/US_PER_MS;
    }

    public void setMaxDtMs(float maxDtMs) {
      float setValue = maxDtMs*US_PER_MS;
      if(setValue < 0)setValue=0;
      this.maxDtUs = setValue;
      putFloat("maxDtUs",setValue);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --InhibitoryCoarseness--">
    public int getInhibitoryCoarseness() {
        return inhibitoryCoarseness;
    }

    public void setInhibitoryCoarseness(int InhibitoryCoarseness) {
        int setValue = InhibitoryCoarseness;
        if(setValue<1){
            setValue = 1;
        }
        this.inhibitoryCoarseness = setValue;
        putInt("inhibitoryCoarseness",setValue);
        resetFilter(); //need to recalculate the circles
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --ExcitatoryCoarseness--">
    public int getExcitatoryCoarseness() {
        return excitatoryCoarseness;
    }

    public void setExcitatoryCoarseness(int excitatoryCoarseness) {
        int setValue = excitatoryCoarseness;
        if(setValue<1){
            setValue = 1;
        }
        this.excitatoryCoarseness = setValue;
        putInt("excitatoryCoarseness",setValue);
        resetFilter(); //need to recalculate the circles
    }
    // </editor-fold>
        
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --outputPolarityEvents--">
    public boolean getOutputPolarityEvents() {
        return outputPolarityEvents;
    }

    public void setOutputPolarityEvents(boolean outputPolarityEvents) {
        this.outputPolarityEvents = outputPolarityEvents;
        putBoolean("outputPolarityEvents",outputPolarityEvents);
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --showRawInputEnabled--">
    public boolean getShowRawInputEnabled() {
        return showRawInputEnabled;
    }

    public void setShowRawInputEnabled(final boolean showRawInputEnabled) {
        this.showRawInputEnabled = showRawInputEnabled;
        putBoolean("showRawInputEnabled",showRawInputEnabled);
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --drawInhibitExcitePoints--">
    public boolean getDrawInhibitExcitePoints() {
        return drawInhibitExcitePoints;
    }

    public void setDrawInhibitExcitePoints(boolean drawMotionVectors) {
        this.drawInhibitExcitePoints = drawMotionVectors;
        putBoolean("drawInhibitExcitePoints",drawMotionVectors);
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --drawCenterCell--">
    public boolean getDrawCenterCell() {
        return drawCenterCell;
    }

    public void setDrawCenterCell(boolean drawCenterCell) {
        this.drawCenterCell = drawCenterCell;
        putBoolean("drawCenterCell",drawCenterCell);
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --showInhibitedEvents--">
    public boolean getShowInhibitedEvents() {
        return showInhibitedEvents;
    }

    public void setShowInhibitedEvents(boolean showTotalInhibitedEvents) {
        this.showInhibitedEvents = showTotalInhibitedEvents;
        putBoolean("showInhibitedEvents",showTotalInhibitedEvents);
    }
    // </editor-fold>
}
