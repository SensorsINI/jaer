package ch.unizh.ini.caviar.graphics;

import ch.unizh.ini.caviar.chip.Chip2D;
import java.util.ArrayList;
import java.util.prefs.Preferences;

/**
 A general class for rendering chip output to a 2d array of float values for drawing
 @author tobi
 */
public class Chip2DRenderer {
    
    protected Preferences prefs = Preferences.userNodeForPackage(Chip2DRenderer.class);
    
    /** the chip rendered for */
    protected Chip2D chip;
    
    /** determines whether frame is reset to starting value on each rendering cycle. True to accumlate. */
    protected boolean accumulateEnabled = false;
    
    protected ArrayList<FrameAnnotater> annotators = new ArrayList<FrameAnnotater>();
    
    protected int autoScaleValue = 1;
    
    /** false for manual scaling, true for autoscaling of contrast */
    protected boolean autoscaleEnabled = prefs.getBoolean("Chip2DRenderer.autoscaleEnabled",false);
    
    /** the number of events for full scale saturated color */
    protected int colorScale = prefs.getInt("Chip2DRenderer.colorScale",1);
    
    /** the constrast attributed to an event, either level is multiplied or divided by this value depending on polarity of event. Gets set by setColorScale */
    protected float eventContrast = 1.1f;
    
    /** the rendered frame, RGB matrix of pixel values in 0-1 range.
     In matlab convention, the first dimesion is y, the second dimension is x, the third dimension is a 3 vector of RGB values.
     */
    protected float[][][] fr;
    
    protected float grayValue = 0;
    
    protected short xsel = -1;
    
    protected short ysel = -1;
    
    protected int selectedPixelEventCount = 0;
    
    public Chip2DRenderer(){
        
    }
    
    public Chip2DRenderer(Chip2D chip){
        this.chip=chip;
    }
    
    /** add an annotator to the rendered event histogram frame data. This is one way to annotate the image; the other way is to directly draw graphics.
     *@param annotator the object that will annotate the frame data
     *@see ch.unizh.ini.caviar.graphics.ChipCanvas#addAnnotator
     */
    public synchronized void addAnnotator(FrameAnnotater annotator){
        annotators.add(annotator);
    }
    
    protected void annotate(){
        if(annotators==null) return;
        for(FrameAnnotater a : annotators){
            //            log.info("calling annotator "+a+" to annotate "+this);
            a.annotate(fr);
        }
    }
    
    /** decrease contrast */
    public int decreaseContrast(){
        int cs = getColorScale();
        cs++;
        if(cs>255) cs=255;
        setColorScale(cs);
        return getColorScale();
    }
    
    /**@return current color scale, full scale in events */
    public int getColorScale() {
        if (!autoscaleEnabled)
            return colorScale;
        else
            return autoScaleValue;
    }
    
    /** @return the gray level of the rendered data; used to determine whether a pixel needs to be drawn */
    public float getGrayValue() {
        return this.grayValue;
    }
    
    public short getXsel() {
        return xsel;
    }
    
    public short getYsel() {
        return ysel;
    }
    
    /** increase image contrast */
    public int increaseContrast(){
        int cs = getColorScale();
        cs--;
        if(cs<1) cs=1;
        setColorScale(cs);
        return getColorScale();
    }
    
    public boolean isAccumulateEnabled() {
        return this.accumulateEnabled;
    }
    
    public boolean isAutoscaleEnabled() {
        return this.autoscaleEnabled;
    }
    
    public boolean isPixelSelected(){
        return xsel != -1 && ysel != -1;
    }
    
    public synchronized void removeAllAnnotators(){
        annotators.removeAll(annotators);
    }
    
    public void resetChannel(float value, int channel){
        for (int i = 0; i<fr.length; i++)
            for (int j = 0; j < fr[i].length; j++){
            float[] f = fr[i][j];
            f[channel]=value;
            }
    }
    
    /** Checks the frame buffer for the correct sizes;
     when constructed in superclass of a chip, sizes may not yet be set for chip. we can check every time
     */
    synchronized public void checkFr(){
        if (fr == null || fr.length==0){
            reallocateFr();
        }
    }
    
    /** reallocates the fr buffer using the current chip size */
    synchronized public void reallocateFr(){
        if (chip == null)return;
        fr = new float[chip.getSizeY()][chip.getSizeX()][3];
    }
    
    synchronized public void resetFrame(float value){
        grayValue = value;
        // more efficient to just set all elements to value, instead of allocating new array of zeros
        // profiling shows that copying array back to matlab takes most cycles!!!!
        for (int i = 0; i<fr.length; i++)
            for (int j = 0; j < fr[i].length; j++){
            float[] f = fr[i][j];
            f[0]=value;
            f[1]=value;
            f[2]=value;
            }
    }
    
    /** @param accumulateEnabled true to accumulate data to frame (don't reset to start value each cycle) */
    public void setAccumulateEnabled(final boolean accumulateEnabled) {
        this.accumulateEnabled = accumulateEnabled;
    }
    
    public void setAutoscaleEnabled(final boolean autoscaleEnabled) {
        this.autoscaleEnabled = autoscaleEnabled;
        prefs.putBoolean(("BinocularRenderer.autoscaleEnabled"),autoscaleEnabled);
    }
    
    /** set the color scale. 1 means a single event is full scale, 2 means a single event is half scale, etc.
     *only applies to some rendering methods.
     */
    public void setColorScale(int colorScale) {
        if(colorScale<1) colorScale=1;
        if(colorScale>64) colorScale=64;
        this.colorScale = colorScale;
        // we set eventContrast so that colorScale events takes us from .5 to 1, i.e., .5*(eventContrast^cs)=1, so eventContrast=2^(1/cs)
        eventContrast = (float) (Math.pow(2, 1.0/colorScale)); // e.g. cs=1, eventContrast=2, cs=2, eventContrast=2^0.5, etc
        prefs.putInt("Chip2DRenderer.colorScale",colorScale);
    }
    
    public void setXsel(short xsel) {
        this.xsel = xsel;
    }
    
    public void setYsel(short ysel) {
        this.ysel = ysel;
    }
    
    /** @return frame data.
     * fr is the rendered event data that we draw. Y is the first dimenion, X is the second dimension, RGB 3 vector is the last dimension
     @see #fr
     */
    public float[][][] getFr() {
        return fr;
    }
    
    public int getSelectedPixelEventCount() {
        return selectedPixelEventCount;
    }
    
}
