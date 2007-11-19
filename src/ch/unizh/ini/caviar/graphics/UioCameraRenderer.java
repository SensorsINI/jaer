/*
 * UioCameraRenderer.java
 *
 * Created on 16. mai 2006, 10:19
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.caviar.graphics;
import ch.unizh.ini.caviar.chip.AEChip;
//import ch.unizh.ini.caviar.aemonitor.AEPacket2D;
import ch.unizh.ini.caviar.event.*;

/**
 * This class overrides the render(AEPacket2D ae) to display an image
 * with gray levels on black. The number of spikes that a packet needs to
 * saturate a pixels is given by the colorScale variable which is adjustable
 * with the up and down arrow keys (i.e. contrast).
 *
 * Future plans (TODO's): 
 * - The contrast step height should be normalized by the packet size and 
 * time-frame to give a more stable image at high rendering speed and 
 * to keep contrast when swapping packetsize.
 * - Implement a fixed-pattern noise canceler
 * @author hansbe@ifi.uio.no
 */
public class UioCameraRenderer extends AEChipRenderer {
    
//    public enum ColorMode {PositiveGrayLevelOnBlack};
    
    /** Creates a new instance of UioCameraRenderer
     * @param chip the chip we're rendering for
     */
    public UioCameraRenderer(AEChip chip) {        
        super(chip);        
        
    }
    
    public boolean firstRender = true;
    public boolean accumulateStarted = false;
    
    
    
    /**
     * Does the rendering (only one colorMode is available)
     * 
     * @param packet a packet of events (already extracted to x,y,type
     * @return reference to the frame     
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
        //Here comes all extra variables I would need for my light intensity algorithm.
        float a, eventsPerSec = packet.getEventRateHz(), averageEventsPerPix = 0,
                newAverageEventsPerPix = 0, pixEventHz = 0, alpha = (float)0.8;
        //alpha decides how fast the average intensity should change. 1 gives no change and 
        //0 gives instant change. 0.8 is a good number, gives semi-slow change.
        //We like that. *nodnods excitedly*
        int tt, dt = 0;  
        float numSpikes, numPixels;
        //Checks what chip we got and adjusts some sizes after that. It can easily be expanded...
        //up to some point where it gets impractical.
        if (chip.getClass().getName().equals("ch.unizh.ini.caviar.chip.foveated.UioFoveatedImager")) {
            numSpikes = (63-16)*(67-16);
            numPixels = (63-16)*(67-16);
        } else {
            numSpikes = (92)*(92);
            numPixels = (92)*(92);
        }
        float meanSpikes = 1;
        checkFr();
        if (firstRender == true) {
            System.out.println("Please hold a uniform surface in front of the retina and press 'P'");
            System.out.println("to start measuring the mean spiking rate of pixels. This will then");
            System.out.println("be used to reduce the pixel noise of the camera."                  );
            System.out.println("Avoid movement.");
            System.out.println("Press 'P' again after a second or two and see the results.");
            for (int i = 0; i<fr.length; i++)
            for (int j = 0; j < fr[i].length; j++){
            float[] f = fr[i][j];
            f[0]=0;
            f[1]=1;
            f[2]=1;
            }            
            numSpikes = numPixels;
            meanSpikes = 1;
            firstRender = false;
            }
        
        if (accumulateEnabled) {
            if (!accumulateStarted){
                for (int i = 0; i<fr.length; i++)
                    for (int j = 0; j < fr[i].length; j++)
                        fr[i][j][2] = 0;
                numSpikes = packet.getSize();
                meanSpikes = numSpikes/numPixels;
                accumulateStarted = true;
            } else {
                numSpikes += packet.getSize();
                meanSpikes = numSpikes/numPixels;
            }
        }
        else {
            if (accumulateStarted){
            accumulateStarted = false;
            for (int i = 0; i<fr.length; i++)
                    for (int j = 0; j < fr[i].length; j++)
                        fr[i][j][2] = meanSpikes/fr[i][j][2];
            }                                         
        }
            
        
        boolean ignorePolarity=isIgnorePolarityEnabled();
        try{
            if (packet.getNumCellTypes()<2){                
                for(Object obj:packet){
                    BasicEvent e=(BasicEvent)obj;
                    tt = e.getTimestamp();                                       
                    if (accumulateEnabled) {
                        fr[e.y][e.x][2] += 1;
                        a = 10000*colorScale*fr[e.y][e.x][2]/((tt - fr[e.y][e.x][1])*meanSpikes);
                        }
                    else
                    {
                        if (chip.getClass().getName().equals("ch.unizh.ini.caviar.chip.foveated.UioFoveatedImager")) {
                            //This is the intensity calculation used by Mehdi's Foveated Imager
                            dt = (int)(tt - fr[e.y][e.x][1]);
                            pixEventHz = (float)1/((float)dt/862069); //calculates the frequenzy of this pixel in Hz.
                            a = (200000*(float)(colorScale*0.1)*fr[e.y][e.x][2])/(dt);
                            fr[e.y][e.x][0] = a;
                        } else {
                            //This is another intensity calculation which adjust the intensity calculation to match a "gray" world
                            //Used with Jenny's StaticBioVis, I am sure it would work with other chips too that does not need
                            //to use fr[1] and fr[2] as well.
                            
                            dt = (int)(tt - fr[e.y][e.x][1]);              //Finds the time since previous spike
                            averageEventsPerPix = fr[e.y][e.x][2];         //Gets the average events per pixel per second stored in fr[2]
                            newAverageEventsPerPix = eventsPerSec/numPixels; //Calculates new average events per pixel per second
                            
                            averageEventsPerPix = alpha*averageEventsPerPix+(1-alpha)*newAverageEventsPerPix;
                            //Calculates what the new average events per pix per sec should be. This decides what our intensity
                            //will be when shown on screen. alpha is the intensity adaption rate, it will slow down the change in
                            //averageEventsPerPix. Quick changes in averageEventsPerPix could lead do undesired image effects if
                            //eventsPerSec changes rapidly or stays unstable, this could even generate noise in our image
                            
                            pixEventHz = (float)1/((float)dt/862069); //calculates the frequenzy of this pixel in Hz.
                            
                            a = (pixEventHz*colorScale*(float)0.1)/(2*averageEventsPerPix); //calculates the intensity.
                            //Intensity is calculated in the way that we assume that 0Hz will be black, and 2*averageEventsPerPix
                            //will be shown as white. Anything above this is also shown as white, and we do not really expect
                            //any pixels to be able to trigger in a frequency below 0Hz. With the colorScale we are able to adjust
                            //the brightness of the image shown. It is not always desired to depict the world as medium gray.
                           
                            fr[e.y][e.x][0] = a;                    //Here lies our intensity saved
                            fr[e.y][e.x][2] = averageEventsPerPix;  //Here lies our averageEventsPerPix(PerSec) saved
                        }
                    }
                    if (e.x == xsel && e.y == ysel) System.out.println("Scale="+colorScale+" tt="+tt+" dt="+dt+" pixHz="+pixEventHz+" a="+a + " eps="+eventsPerSec+" aEPP="+averageEventsPerPix);
                    fr[e.y][e.x][1] = tt;
                    
                }
            }
        }catch (ArrayIndexOutOfBoundsException e){
            e.printStackTrace();
            log.warning(e.getCause()+": ChipRenderer.render(), some event out of bounds for this chip type?");
        }
        return fr;
        
    }
    
//        short[] x = ae.getXs();
//        short[] y = ae.getYs();
//        byte[] type = ae.getTypes();
//        int[] ts = ae.getTimestamps();
//        int numEvents = ae.getNumEvents();
//        int skipBy = 1;
//        if(isSubsamplingEnabled()){
//            while(numEvents/skipBy>getSubsampleThresholdEventCount()){
//                skipBy++;
//            }
////            System.out.println(numEvents+" events, skipping by "+skipBy);
//        }
//        float a;
//        int tt;
//        selectedPixelSpikeCount = 0; // init it for this packet
//        checkFr();
//        try{
//            if (ae.getNumCellTypes()>2){
//                createMultiCellColors(ae.getNumCellTypes());
//                if(!accumulateEnabled) resetFrame(0);
//                step = 1f / (colorScale);
//                for (int i = 0; i<numEvents; i+=skipBy){
//                    if (x[i] == xsel && y[i] == ysel)playSpike(type[i]);;
//                    float[] f = fr[y[i]][x[i]];
//                    float[] c = multiCellColors[type[i]];
//                    if (colorScale > 1){
//                        f[0] += c[0] * step; //if(f[0]>1f) f[0]=1f;
//                        f[1] += c[1] * step; //if(f[1]>1f) f[1]=1f;
//                        f[2] += c[2] * step; //if(f[2]>1f) f[2]=1f;
//                    }else{
//                        f[0]=c[0]; //if(f[0]>1f) f[0]=1f;
//                        f[1]=c[1]; //if(f[1]>1f) f[1]=1f;
//                        f[2]=c[2]; //if(f[2]>1f) f[2]=1f;
//                    }
//                }
//                autoScaleFrame(fr,grayValue);
//            }else{
//
//
//// case PositiveGrayLevelOnBlack:
//
//                        if(!accumulateEnabled) resetFrame(0f); // also sets grayValue
//                        
//                        step = 1f / (colorScale + 1);
//                        
//                        // colorScale=1,2,3;  step = 1, 1/2, 1/3, 1/4,  ;
//                        // later type-grayValue gives -.5 or .5 for spike value, when
//                        // multipled gives steps of 1/2, 1/3, 1/4 to end up with 0 or 1 when colorScale=1 and you have one event
//                        for (int i = 0; i<numEvents; i+=skipBy){
////                            if (x[i] == xsel && y[i] == ysel)playSpike(type[i]);;       
//                            fr[y[i]][x[i]][0] += step;
//                            fr[y[i]][x[i]][1] += step;
//                            fr[y[i]][x[i]][2] += step;
//
//                        }
//                                               
//                autoScaleFrame(fr,grayValue);
//            }
//            annotate();
//        } catch (ArrayIndexOutOfBoundsException e){
//            e.printStackTrace();
//            log.warning(e.getCause()+": ChipRenderer.render(), some event out of bounds for this chip type?");
//        }
//        return fr;
//    }
//    /**
//     * does the rendering using selected method.
//     *
//     * @param packet a packet of events (already extracted from raw events)
//     * @return reference to the frame
//     * @see #setColorMode
//     */
//    public synchronized float[][][] render(EventPacket packet){
//        if(packet==null) return fr;
//        this.packet=packet;
//        int numEvents = packet.getSize();
//        int skipBy = 1;
//        if(isSubsamplingEnabled()){
//            while(numEvents/skipBy>getSubsampleThresholdEventCount()){
//                skipBy++;
//            }
////            System.out.println(numEvents+" events, skipping by "+skipBy);
//        }
//        float a;
//        int tt;
//        selectedPixelSpikeCount = 0; // init it for this packet
//        checkFr();
//        boolean ignorePolarity=isIgnorePolarityEnabled();
//        try{
//            if (packet.getNumCellTypes()>2){
//                createMultiCellColors(packet.getNumCellTypes());
//                if(!accumulateEnabled) resetFrame(0);
//                step = 1f / (colorScale);
//                for(Object obj:packet){
////                for (int i = 0; i<numEvents; i+=skipBy){
////                    BasicEvent e=packet.getEvent(i);
//                    BasicEvent e=(BasicEvent)obj;
//                    int type=e.getType();
//                    if (e.x == xsel && e.x == ysel)playSpike(type);;
//                    float[] f = fr[e.y][e.x];
//                    float[] c = multiCellColors[type];
//                    if (colorScale > 1){
//                        f[0] += c[0] * step; //if(f[0]>1f) f[0]=1f;
//                        f[1] += c[1] * step; //if(f[1]>1f) f[1]=1f;
//                        f[2] += c[2] * step; //if(f[2]>1f) f[2]=1f;
//                    }else{
//                        f[0]=c[0]; //if(f[0]>1f) f[0]=1f;
//                        f[1]=c[1]; //if(f[1]>1f) f[1]=1f;
//                        f[2]=c[2]; //if(f[2]>1f) f[2]=1f;
//                    }
//                }
//                autoScaleFrame(fr,grayValue);
//            }else{
//                switch(colorMode) {
//                    case GrayLevel:
//                        
//                        if(!accumulateEnabled) resetFrame(.5f); // also sets grayValue
//                        
//                        step = 2f / (colorScale + 1);
//                        
//                        // colorScale=1,2,3;  step = 1, 1/2, 1/3, 1/4,  ;
//                        // later type-grayValue gives -.5 or .5 for spike value, when
//                        // multipled gives steps of 1/2, 1/3, 1/4 to end up with 0 or 1 when colorScale=1 and you have one event
//                        for(Object obj:packet){
//                            BasicEvent e=(BasicEvent)obj;
////                        for (int i = 0; i<numEvents; i+=skipBy){
////                            BasicEvent e=packet.getEvent(i);
//                            int type=e.getType();
//                            if (e.x == xsel && e.y == ysel)playSpike(type);;
//                            a = (fr[e.y][e.x][0]);
//                            if (!ignorePolarity){
//                                a += step * (type- grayValue);  // type-.5 = -.5 or .5; step*type= -.5, .5, (cs=1) or -.25, .25 (cs=2) etc.
//                            }else{
//                                a += step * (1 - grayValue);  // type-.5 = -.5 or .5; step*type= -.5, .5, (cs=1) or -.25, .25 (cs=2) etc.
//                                
//                            }
//                            fr[e.y][e.x][0] = a;
//                            fr[e.y][e.x][1] = a;
//                            fr[e.y][e.x][2] = a;
//                        }
//                        
////                        autoScaleFrame(fr,grayValue);
//                        break;
//                    case Contrast:
//                        
//                        if(!accumulateEnabled) resetFrame(.5f);
//                        
//                        float eventContrastRecip = 1/eventContrast;
//                        
//                        for (int i = 0; i<numEvents; i+=skipBy){
//                            BasicEvent e=packet.getEvent(i);
//                            int type=e.getType();
//                            if (e.x == xsel && e.y == ysel)playSpike(type);;
//                            a = (fr[e.y][e.x][0]);
//                            switch(type) {
//                                case 0:
//                                    
//                                    a*=eventContrastRecip; // off cell divides gray
//                                    
//                                    break;
//                                case 1:
//                                    
//                                    a*=eventContrast; // on multiplies gray
//                            }
//                            fr[e.y][e.x][0] = a;
//                            fr[e.y][e.x][1] = a;
//                            fr[e.y][e.x][2] = a;
//                        }
//                        
////                        autoScaleFrame(fr,grayValue);
//                        break;
//                    case RedGreen:
//                        
//                        if(!accumulateEnabled) resetFrame(0);
//                        
//                        step = 1f / (colorScale); // cs=1, step=1, cs=2, step=.5
//                        
//                        for (int i = 0; i<numEvents; i+=skipBy){
//                            BasicEvent e=packet.getEvent(i);
//                            int type=e.getType();
//                            if (e.x == xsel && e.y == ysel)playSpike(type);;
//                            tt=type; // 0,1
//                            a = (fr[e.y][e.x][tt]); // polarity 0 makes red, 1 makes green. For tmpdiff128, 1 is an ON event after event extraction, which flips the type (raw polarity 0 is ON)
//                            a += step;
//                            fr[e.y][e.x][tt] = a;
//                        }
//                        
////                        autoScaleFrame(fr,grayValue);
//                        break;
//                    case ColorTime:
//                        
//                        if(!accumulateEnabled) resetFrame(0);
//                        
//                        if (numEvents==0)                            return fr;
//                        
//                        int ts0=packet.getFirstTimestamp();
//                        float dt=packet.getDurationUs();
//                        
//                        step = 1f / (colorScale); // cs=1, step=1, cs=2, step=.5
//                        
//                        
//                        for (int i = 0; i<numEvents; i+=skipBy){
//                            BasicEvent e=packet.getEvent(i);
//                            int type=e.getType();
//                            if (e.x == xsel && e.y == ysel)playSpike(type);;
//                            int ind = (int)Math.floor((NUM_TIME_COLORS-1)*(e.timestamp-ts0)/dt);
//                            if(ind<0) ind=0; else if(ind>=timeColors.length) ind=timeColors.length-1;
//                            if (colorScale > 1){
//                                for (int c = 0; c<3; c++){
//                                    a = fr[e.y][e.x][c];
//                                    a = a + timeColors[ind][c] * step;
//                                    fr[e.y][e.x][c] = a;
//                                }
//                            }else{
//                                fr[e.y][e.x][0] = timeColors[ind][0];
//                                fr[e.y][e.x][1] = timeColors[ind][1];
//                                fr[e.y][e.x][2] = timeColors[ind][2];
//                            }
//                        }
//                        
////                        autoScaleFrame(fr,grayValue);
//                        break;
//                    default:
//                        // rendering method unknown, reset to default value
//                        log.warning("colorMode "+colorMode+" unknown, reset to default value 0");
//                        setColorMode(ColorMode.GrayLevel);
//                }
//                autoScaleFrame(fr,grayValue);
//            }
//            annotate();
//        } catch (ArrayIndexOutOfBoundsException e){
//            e.printStackTrace();
//            log.warning(e.getCause()+": ChipRenderer.render(), some event out of bounds for this chip type?");
//        }
//        return fr;
//    }
      
    
}
