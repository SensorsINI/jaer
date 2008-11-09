/*
 * ChipRenderer.java
 *
 * Created on May 2, 2006, 1:49 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.caviar.graphics;

import ch.unizh.ini.caviar.chip.AEChip;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.util.SpikeSound;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.JButton;

/**
 * Superclass for classes that render AEs to a memory buffer so that they can be painted on the screen.
 * Note these classes do not actually render to the graphcs
 *device; they take AEPacket's and render them to a memory buffer that later gets painted by a ChipCanvas.
 *The method chosen (by user cycling method from GUI) chooses how the events are painted.
 *In effect the events are histogrammed for most rendering methods except for "color-time", and even there they are histogrammed or averaged.
 * For methods that render polarized events (such as ON-OFF) then ON events increase the rendered value while OFF events decreases it.
 *Thus the rendered image fr can be drawn in 3-d if desired and it will represent a histogram, although the default method using for drawing the rendered frame
 *is to paint the cell brightness.
 *
 * @author tobi
 */
public class AEChipRenderer extends Chip2DRenderer {

    /** @see AEChipRenderer#typeColorRGBComponents
    */
    public float[][] getTypeColorRGBComponents() {
        checkTypeColors(chip.getNumCellTypes()); // should be efficient
        return typeColorRGBComponents;
    }

     /** @see AEChipRenderer#typeColorRGBComponents */
   public void setTypeColorRGBComponents(float[][] typeColors) {
        this.typeColorRGBComponents = typeColors;
    }

     /** @see AEChipRenderer#typeColors */
   public Color[] getTypeColors() {
        return typeColors;
    }

     /** @see AEChipRenderer#typeColors */
   public void setTypeColors(Color[] typeColors) {
        this.typeColors = typeColors;
    }
    
    public enum ColorMode {GrayLevel, Contrast, RedGreen, ColorTime };
    private ColorMode[] colorModes=ColorMode.values(); // array of mode enums
    ColorMode colorMode;
    {
        ColorMode oldMode=ColorMode.valueOf(prefs.get("ChipRenderer.colorMode",ColorMode.GrayLevel.toString()));
        for(ColorMode c:colorModes){
            if(c==oldMode) colorMode=c;
        }
    }
    
    /** perceptually separated hues - as estimated quickly by tobi */
    protected static final int[] HUES = {
        0,
        36,
        45,
        61,
        70,
        100,
        169,
        188,
        205,
        229,
        298,
        318,
    };
    
    /** the number of rendering methods implemented */
    public static int NUM_METHODS = 4;
    
    /** number of colors used to represent time of event */
    public static final int NUM_TIME_COLORS = 255;
    
    /** chip shadows Chip2D's chip to declare it as AEChip */
    protected AEChip chip;
//    protected AEPacket2D ae = null;
    protected EventPacket packet=null;
    /** the chip rendered for */
    protected boolean ignorePolarityEnabled = false;
    protected Logger log = Logger.getLogger("ch.unizh.ini.caviar.graphics");
    
    /** The Colors that different cell types are painted. checkTypeColors should populate this array. */
    protected Color[] typeColors;
    /** Used for rendering multiple cell types in different RGB colors. checkTypeColors should populate this array of [numTypes][3] size. */
    protected float[][] typeColorRGBComponents;    
    
    protected SpikeSound spikeSound;
    protected float step;  // this is last step of RGB value used in rendering
    protected boolean stereoEnabled = false;
    protected int subsampleThresholdEventCount = prefs.getInt("ChipRenderer.subsampleThresholdEventCount",50000);
    /** determines subSampling of rendered events (for speed) */
    protected boolean subsamplingEnabled = prefs.getBoolean("ChipRenderer.subsamplingEnabled",false);
    protected float[][] timeColors;
    
    public AEChipRenderer(AEChip chip){
        super(chip);
        if(chip==null){
            throw new Error("tried to build ChipRenderer with null chip");
        }
        setChip(chip);
        spikeSound=new SpikeSound();
        timeColors=new float[NUM_TIME_COLORS][3];
        float s=1f/NUM_TIME_COLORS;
        for(int i=0;i<NUM_TIME_COLORS;i++){
            if(true){
                int rgb=Color.HSBtoRGB((float)0.66f*(NUM_TIME_COLORS-i)/NUM_TIME_COLORS,1f,1f);
                Color c=new Color(rgb);
                float[] comp=c.getRGBColorComponents(null);
                timeColors[i][0]=comp[0];
                timeColors[i][2]=comp[2];
                timeColors[i][1]=comp[1];
//                System.out.println(String.format("%.2f %.2f %.2f",comp[0],comp[1],comp[2]));
            }else{
                timeColors[i][0]=s*i;
                timeColors[i][2]=s*(NUM_TIME_COLORS-1-i);
                timeColors[i][1]=2*Math.min(s*i,s*(NUM_TIME_COLORS-1-i));
            }
        }
    }
    
    /**
     * does the rendering using selected method.
     *
     * @param packet a packet of events (already extracted from raw events)
     * @return reference to the frame
     * @see #setColorMode
     */
    public synchronized float[][][] render(EventPacket packet){
        if(packet==null) return fr;
        this.packet=packet;
        int numEvents = packet.getSize();
        int skipBy = 1;
        if(isSubsamplingEnabled()){
            while(numEvents/skipBy>getSubsampleThresholdEventCount()){
                skipBy++;
            }
//            System.out.println(numEvents+" events, skipping by "+skipBy);
        }
        float a;
        int tt;
        selectedPixelEventCount = 0; // init it for this packet
        checkFr();
        boolean ignorePolarity=isIgnorePolarityEnabled();
        try{
            if (packet.getNumCellTypes()>2){
                checkTypeColors(packet.getNumCellTypes());
                if(!accumulateEnabled) resetFrame(0);
                step = 1f / (colorScale);
                for(Object obj:packet){
//                for (int i = 0; i<numEvents; i+=skipBy){
//                    BasicEvent e=packet.getEvent(i);
                    BasicEvent e=(BasicEvent)obj;
                    int type=e.getType();
                    if (e.x == xsel && e.x == ysel){
                        playSpike(type);;
                    }
                    float[] f = fr[e.y][e.x];
                    float[] c = typeColorRGBComponents[type];
                    if(obj instanceof OrientationEvent && ((OrientationEvent)obj).hasOrientation==false){
                        // if event is orientation event but orientation was not set, just draw as gray level
                        f[0] +=  step; //if(f[0]>1f) f[0]=1f;
                        f[1] +=  step; //if(f[1]>1f) f[1]=1f;
                        f[2] +=  step; //if(f[2]>1f) f[2]=1f;
                    }else if (colorScale > 1){
                        f[0] += c[0] * step; //if(f[0]>1f) f[0]=1f;
                        f[1] += c[1] * step; //if(f[1]>1f) f[1]=1f;
                        f[2] += c[2] * step; //if(f[2]>1f) f[2]=1f;
                    }else{
                        // if color scale is 1, then last value is used as the pixel value, which quantizes the color to full scale.
                        f[0]=c[0]; //if(f[0]>1f) f[0]=1f;
                        f[1]=c[1]; //if(f[1]>1f) f[1]=1f;
                        f[2]=c[2]; //if(f[2]>1f) f[2]=1f;
                    }
                }
                autoScaleFrame(fr,grayValue);
            }else{
                switch(colorMode) {
                    case GrayLevel:
                        
                        if(!accumulateEnabled) resetFrame(.5f); // also sets grayValue
                        
                        step = 2f / (colorScale + 1);
                        
                        // colorScale=1,2,3;  step = 1, 1/2, 1/3, 1/4,  ;
                        // later type-grayValue gives -.5 or .5 for spike value, when
                        // multipled gives steps of 1/2, 1/3, 1/4 to end up with 0 or 1 when colorScale=1 and you have one event
                        for(Object obj:packet){
                            BasicEvent e=(BasicEvent)obj;
//                        for (int i = 0; i<numEvents; i+=skipBy){
//                            BasicEvent e=packet.getEvent(i);
                            int type=e.getType();
                            if (e.x == xsel && e.y == ysel)playSpike(type);;
                            a = (fr[e.y][e.x][0]);
                            if (!ignorePolarity){
                                a += step * (type- grayValue);  // type-.5 = -.5 or .5; step*type= -.5, .5, (cs=1) or -.25, .25 (cs=2) etc.
                            }else{
                                a += step * (1 - grayValue);  // type-.5 = -.5 or .5; step*type= -.5, .5, (cs=1) or -.25, .25 (cs=2) etc.
                                
                            }
                            fr[e.y][e.x][0] = a;
                            fr[e.y][e.x][1] = a;
                            fr[e.y][e.x][2] = a;
                        }
                        
//                        autoScaleFrame(fr,grayValue);
                        break;
                    case Contrast:
                        
                        if(!accumulateEnabled) resetFrame(.5f);
                        
                        float eventContrastRecip = 1/eventContrast;
                        
                        for (int i = 0; i<numEvents; i+=skipBy){
                            BasicEvent e=packet.getEvent(i);
                            int type=e.getType();
                            if (e.x == xsel && e.y == ysel)playSpike(type);;
                            a = (fr[e.y][e.x][0]);
                            switch(type) {
                                case 0:
                                    
                                    a*=eventContrastRecip; // off cell divides gray
                                    
                                    break;
                                case 1:
                                    
                                    a*=eventContrast; // on multiplies gray
                            }
                            fr[e.y][e.x][0] = a;
                            fr[e.y][e.x][1] = a;
                            fr[e.y][e.x][2] = a;
                        }
                        
//                        autoScaleFrame(fr,grayValue);
                        break;
                    case RedGreen:
                        
                        if(!accumulateEnabled) resetFrame(0);
                        
                        step = 1f / (colorScale); // cs=1, step=1, cs=2, step=.5
                        
                        for (int i = 0; i<numEvents; i+=skipBy){
                            BasicEvent e=packet.getEvent(i);
                            int type=e.getType();
                            if (e.x == xsel && e.y == ysel)playSpike(type);;
                            tt=type; // 0,1
                            a = (fr[e.y][e.x][tt]); // polarity 0 makes red, 1 makes green. For tmpdiff128, 1 is an ON event after event extraction, which flips the type (raw polarity 0 is ON)
                            a += step;
                            fr[e.y][e.x][tt] = a;
                        }
                        
//                        autoScaleFrame(fr,grayValue);
                        break;
                    case ColorTime:
                        
                        if(!accumulateEnabled) resetFrame(0);
                        
                        if (numEvents==0)                            return fr;
                        
                        int ts0=packet.getFirstTimestamp();
                        float dt=packet.getDurationUs();
                        
                        step = 1f / (colorScale); // cs=1, step=1, cs=2, step=.5
                        
                        
                        for (int i = 0; i<numEvents; i+=skipBy){
                            BasicEvent e=packet.getEvent(i);
                            int type=e.getType();
                            if (e.x == xsel && e.y == ysel)playSpike(type);;
                            int ind = (int)Math.floor((NUM_TIME_COLORS-1)*(e.timestamp-ts0)/dt);
                            if(ind<0) ind=0; else if(ind>=timeColors.length) ind=timeColors.length-1;
                            if (colorScale > 1){
                                for (int c = 0; c<3; c++){
                                    a = fr[e.y][e.x][c];
                                    a = a + timeColors[ind][c] * step;
                                    fr[e.y][e.x][c] = a;
                                }
                            }else{
                                fr[e.y][e.x][0] = timeColors[ind][0];
                                fr[e.y][e.x][1] = timeColors[ind][1];
                                fr[e.y][e.x][2] = timeColors[ind][2];
                            }
                        }
                        
//                        autoScaleFrame(fr,grayValue);
                        break;
                    default:
                        // rendering method unknown, reset to default value
                        log.warning("colorMode "+colorMode+" unknown, reset to default value 0");
                        setColorMode(ColorMode.GrayLevel);
                }
                autoScaleFrame(fr,grayValue);
            }
            annotate();
        } catch (ArrayIndexOutOfBoundsException e){
            e.printStackTrace();
            log.warning(e.getCause()+": ChipRenderer.render(), some event out of bounds for this chip type?");
        }
        return fr;
    }
    
    /** autoscales frame data so that max value is 1. If autoscale is disabled, then values are just clipped to 0-1 range.
     If autoscale is enabled, then gray is mapped back to gray and following occurs:
     <p>
     Global normalizer is tricky because we want to map max value to 1 OR min value to 0, whichever is greater magnitude, max or min.
     ALSO, max and min are distances from gray level in positive and negative directions. After global normalizer is computed, all values
     *are divided by normalizer in order to keep gray level constant.
     @param fr the frame rgb data [y][x][rgb]
     @param gray the gray level
     */
    protected void autoScaleFrame(float[][][] fr, float gray){
        if (!autoscaleEnabled) { // just clip values to 0..1 for painting
            for (int i = 0; i<fr.length; i++){
                for (int j = 0; j<fr[i].length; j++){
                    for (int k = 0; k<3; k++){
                        float f = fr[i][j][k];
                        if(f<0) f=0; else if(f>1) f=1;
                        fr[i][j][k]=f;
                    }
                }
            }
        }else{ // compute min and max values and divide to keep gray level constant
            //            float[] mx={Float.MIN_VALUE,Float.MIN_VALUE,Float.MIN_VALUE}, mn={Float.MAX_VALUE,Float.MAX_VALUE,Float.MAX_VALUE};
            float max = Float.NEGATIVE_INFINITY,  min = Float.POSITIVE_INFINITY;
            //               max=max-.5f; // distance of max from gray
            //            min=.5f-min; // distance of min from gray
            for (int i = 0; i<fr.length; i++){
                for (int j = 0; j<fr[i].length; j++){
                    for (int k = 0; k<3; k++){
                        float f = fr[i][j][k]-gray;
                        if(f>max)max=f; else if(f<min)min=f;
                    }
                }
            }
            // global normalizer here
            // this is tricky because we want to map max value to 1 OR min value to 0, whichever is greater magnitude, max or min
            // ALSO, max and min are distances from gray level in positive and negative directions
            float m, b = gray; // slope/intercept of mapping function
            if(max==min) return; // if max==min then no need to normalize or do anything, just paint gray
            
            if(max>-min){ // map max to 1, gray to gray
                m=(1-gray)/(max);
                b=gray-gray*m;
            }else{ // map min to 0, gray to gray
                m=gray/(-min);
                b=gray-gray*m;
            }
            
            //            float norm=(float)Math.max(Math.abs(max),Math.abs(min)); // norm is max distance from gray level
            //            System.out.println("norm="+norm);
            if (colorMode != ColorMode.Contrast){
                autoScaleValue = (int) Math.round(Math.max(max,-min) / step);  // this is value shown to user, step was computed during rendering to be (usually) 1/colorScale
            }else{
                if(max>-min){
                    autoScaleValue=1;  // this is value shown to user, step was computed during rendering to be (usually) 1/colorScale
                }else{
                    autoScaleValue=-1;  // this is value shown to user, step was computed during rendering to be (usually) 1/colorScale
                }
            }
            // normalize all channels
            for (int i = 0; i<fr.length; i++){
                for (int j = 0; j<fr[i].length; j++){
                    for (int k = 0; k<3; k++){
                        float f = fr[i][j][k];
                        float f2 = m*f+b;
                        if(f2<0)
                            f2=0;
                        else
                            if(f2>1)
                                f2=1; // shouldn't need this
                        fr[i][j][k]=f2;
                    }
                }
            }
            
        }
    }
    
    
    /** Creates colors for each cell type (e.g. orientation)
     so that they are spread over hue space in a manner to attempt to be maximally different in hue.
     * 
     * <p>
     * Subclasses can override this method to customize the colors drawn but the subclasses should check if the color have been created since checkTypeColors is called on every
     * rendering cycle. This method should first check if typeColorRGBComponents already exists and has the correct number of elements. If not, 
     * allocate and populate typeColorRGBComponents so that type t corresponds to typeColorRGBComponents[t][0] for red, typeColorRGBComponents[t][1] for green, and
     * typeColorRGBComponents[t][3] for blue. It should also populate the Color[] typeColors.
     * @param numCellTypes the number of colors to generate
     * @see #typeColors
     * @see #typeColorRGBComponents
     */
    protected void checkTypeColors(int numCellTypes){
        
        if (typeColorRGBComponents == null || typeColorRGBComponents.length!=numCellTypes){
            typeColorRGBComponents = new float[numCellTypes][3];
            setTypeColors(new Color[numCellTypes]);
            for (int i = 0; i<typeColorRGBComponents.length; i++){
                int hueIndex = (int)Math.floor((float)i/typeColorRGBComponents.length*HUES.length);
                //                float hue=(float)(numCellTypes-i)/(numCellTypes);
                float hue = (float)HUES[hueIndex]/255f;
                //                hue=hue*hue;
                //                Color c=space.fromCIEXYZ(comp);
                Color c = Color.getHSBColor(hue,1,1);
                getTypeColors()[i] = c;
                typeColorRGBComponents[i][0] = (float) c.getRed() / 255;
                typeColorRGBComponents[i][1] = (float) c.getGreen() / 255;
                typeColorRGBComponents[i][2] = (float) c.getBlue() / 255;
                JButton but = new JButton(" ");
                but.setBackground(c);but.setForeground(c);
//                System.out.println("Cell type #"+i+" with hue index #"+hueIndex+" and hue="+HUES[hueIndex]+" is Color "+c);
            }
//            JFrame win=new JFrame("MultiCellColors");
//            JPanel pan=new ColorPanel(typeColors);
//            win.getContentPane().add(pan);
//            win.pack();
//            win.setVisible(true);
        }
    }
    
    
    /** go on to next rendering method */
    public synchronized void cycleColorMode(){
        int m=colorMode.ordinal();
        if(++m>=colorModes.length) m=0;
        setColorMode(colorModes[m]);
        
//        method++;
//        if (method > NUM_METHODS-1)            method = 0;
//        setColorMode(method); // store preferences
    }
    
    
    /** returns the last packet rendered
     @return the last packet that was rendered
     */
    public EventPacket getPacket(){
        return packet;
    }
    
    public void setChip(AEChip chip) {
        this.chip = chip;
    }
    public AEChip getChip() {
        return chip;
    }
    
    public ColorMode getColorMode() {
        return colorMode;
    }
    
    public int getSubsampleThresholdEventCount() {
        return subsampleThresholdEventCount;
    }
    
    public boolean isIgnorePolarityEnabled() {
        return ignorePolarityEnabled;
    }
    
    protected boolean isMethodMonochrome(){
        if( (colorMode==ColorMode.GrayLevel) || (colorMode==ColorMode.Contrast)) return true; else return false;
    }
    
    public boolean isStereoEnabled() {
        return stereoEnabled;
    }
    
    public boolean isSubsamplingEnabled() {
        return subsamplingEnabled;
    }
    
    protected void playSpike(int type){
        spikeSound.play(type);
        selectedPixelEventCount++;
    }
    
    
    public void setIgnorePolarityEnabled(boolean ignorePolarityEnabled) {
        this.ignorePolarityEnabled = ignorePolarityEnabled;
    }
    
    /**@param colorMode the rendering method, e.g. gray, red/green opponency, time encoded.
     */
    public synchronized void setColorMode(ColorMode colorMode) {
        this.colorMode=colorMode;
        prefs.put("ChipRenderer.colorMode",colorMode.toString());
        log.info("set colorMode="+colorMode);
//        if (method<0 || method >NUM_METHODS-1)            throw new RuntimeException("no such rendering method "+method);
//        this.method = method;
//        prefs.putInt("ChipRenderer.method",method);
    }
    
    public void setStereoEnabled(boolean stereoEnabled) {
        this.stereoEnabled = stereoEnabled;
    }
    
    public void setSubsampleThresholdEventCount(int subsampleThresholdEventCount) {
        prefs.putInt("ChipRenderer.subsampleThresholdEventCount",subsampleThresholdEventCount);
        this.subsampleThresholdEventCount = subsampleThresholdEventCount;
    }
    
    public void setSubsamplingEnabled(boolean subsamplingEnabled) {
        this.subsamplingEnabled = subsamplingEnabled;
        prefs.putBoolean("ChipRenderer.subsamplingEnabled",subsamplingEnabled);
    }
    
}
