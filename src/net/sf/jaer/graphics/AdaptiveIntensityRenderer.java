/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.jaer.graphics;

/**
 * Using the idea of Jenny Olsson employed in the UioCameraRenderer.java 
 * and making  a more general renderer suitable for Octopus type sensors:
 * Her idea was to define gray as the average pixel activity and a range from
 * 0 to 2*gray as black to white. The average pixel activity is adapted slowly,
 * i.e. not updated for every frame.
 * @author hafliger
 */

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Calibratible;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;

public class AdaptiveIntensityRenderer  extends AEChipRenderer implements Calibratible  { // this renderer implements Calibratible so the AEViewer menu has the calibration menu enabled.


    float[][] calibrationMatrix=new float[chip.getSizeY()][chip.getSizeX()];
    float[][] lastEvent=new float[chip.getSizeY()][chip.getSizeX()];
    float avgEventRateHz=1.0f;
    float meanSpikes =1;
    boolean calibrationStarted = false;
    boolean calibrationInProgress = false;
    float numSpikes=0;
    float numPixels=chip.getSizeX()*chip.getSizeY();
    protected int[] adaptAreaStart=new int[2];
    protected int[] adaptAreaStop=new int[2];
    protected float intensity_scaling=0.001f; //initially this was simply 1, but by having it smaller
                                            // one can play with software gain directly with the 
                                            // FS parameter (up-arrow and down-arrow) which is the variable 'colorScale'

    public AdaptiveIntensityRenderer(AEChip chip) {        
        super(chip);   
        
        AEViewer aev;
        for (int i = 0; i<chip.getSizeY(); i++)
            for (int j = 0; j < chip.getSizeX(); j++){
                calibrationMatrix[i][j] = 1.0f;
                lastEvent[i][j] = 0.0f;
            }
        adaptAreaStart[0]=0;
        adaptAreaStart[1]=0;
        adaptAreaStop[0]=(int)chip.getSizeX()-1;
        adaptAreaStop[1]=(int)chip.getSizeY()-1;
        checkPixmapAllocation();  // make sure DisplayMethod (which uses this) has the array allocated.
    }
    
    public void setCalibrationInProgress(final boolean calibrationInProgress) {
        this.calibrationInProgress = calibrationInProgress;
    }

    public boolean isCalibrationInProgress() {
        return(this.calibrationInProgress);
    }

    public void setAdaptiveArea(int sx, int ex, int sy, int ey){// to acount for the UiO foveated imager, where only the center is an Octopus type retina
        adaptAreaStart[0]=sx;
        adaptAreaStart[1]=sy;
        adaptAreaStop[0]=ex;
        adaptAreaStop[1]=ey;
    }   
        
    public synchronized void render(EventPacket packet){
        if(packet==null) return;
        this.packet=packet;
        //int numEvents = packet.getSize();
        float alpha=0.9f;
        float a;
        int tt, dt = 0;  
    
        float adaptAreaNumSpikes=0;

        checkPixmapAllocation();
        
        
        if (calibrationInProgress) {// accumulating calibration data while the camera looks at a uniform surface
                                // set by pressing 'P', stopped by pressing 'P' again
            if (!calibrationStarted){
                for (int i = 0; i<chip.getSizeY(); i++)
                    for (int j = 0; j < chip.getSizeX(); j++)
                        calibrationMatrix[i][j] = 0;
                numSpikes = packet.getSize();
                meanSpikes = numSpikes/numPixels;
                calibrationStarted = true;
            } else {
                numSpikes += packet.getSize();
                meanSpikes = numSpikes/numPixels;
            }
        }else{
            if (calibrationStarted){
            calibrationStarted = false;
            for (int i = 0; i<chip.getSizeY(); i++)
                    for (int j = 0; j < chip.getSizeX(); j++){
                        if (calibrationMatrix[i][j]!=0.0f){
                            calibrationMatrix[i][j] = meanSpikes/calibrationMatrix[i][j];
                        }else{
                            calibrationMatrix[i][j] = 2;
                        }
                    }
            }                                         
        }
        float[] p=getPixmapArray();
        //avgEventRateHz=(alpha*avgEventRateHz)+(1-alpha)*(packet.getEventRateHz()/numPixels);
        try{
            if (packet.getNumCellTypes()<2){                    
                for(Object obj:packet){
                    BasicEvent e=(BasicEvent)obj;
                    if (calibrationInProgress) {
                        calibrationMatrix[e.y][e.x] += 1;
                    }

                    tt = e.getTimestamp();                                       
                    
                    dt = (int)(tt - lastEvent[e.y][e.x]);           //Finds the time since previous spike
                    lastEvent[e.y][e.x] = tt; 
                    a=0.5f*((float)Math.pow(2, colorScale))/((float)dt*1e-6f)/avgEventRateHz*calibrationMatrix[e.y][e.x]*intensity_scaling;

                    int ind=getPixMapIndex(e.x, e.y);
                    p[ind+0] =a;
                    p[ind+1] =a;
                    p[ind+2] =a;
                    if ((e.x>=adaptAreaStart[0])&&(e.y>=adaptAreaStart[1])&&(e.x<=adaptAreaStop[0])&&(e.y<=adaptAreaStop[1])){
                        adaptAreaNumSpikes +=1;
                    }
                }
            }
        }catch (ArrayIndexOutOfBoundsException e){
        e.printStackTrace();
        log.warning(e.getCause()+": ChipRenderer.render(), some event out of bounds for this chip type?");
        }
        adaptAreaNumSpikes=adaptAreaNumSpikes/((float)(adaptAreaStop[0]-adaptAreaStart[0])*(float)(adaptAreaStop[1]-adaptAreaStart[1]));
        if (((float)packet.getDurationUs())>0)
            avgEventRateHz=(alpha*avgEventRateHz)+(1-alpha)*( (float)adaptAreaNumSpikes/((float)packet.getDurationUs()*1e-6f));
    }
}
