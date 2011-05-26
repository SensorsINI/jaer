/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.graphics;

/**
 * Extending the adaptive intensity renderer for our first octopus colour retina
 * Not sure yet about how to do the adaption best
 * @author hafliger
 */
import net.sf.jaer.chip.Calibratible;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.*;


 public class AdaptiveIntensityRendererColor extends AEChipRenderer  //implements Calibratible
//public class AdaptiveIntensityRendererColor extends AdaptiveIntensityRenderer implements Calibratible //implements Calibratible
{ // this renderer implements Calibratible so the AEViewer menu has the calibration menu enabled.

    float[][][] calibrationMatrix = new float[chip.getSizeY()][chip.getSizeX()][chip.getNumCellTypes()];
    float[][][] lastEvent = new float[chip.getSizeY()][chip.getSizeX()][chip.getNumCellTypes()];
    float[][][] freqMatrix = new float[chip.getSizeY()][chip.getSizeX()][chip.getNumCellTypes()];
    float avgEventRateHz = 1.0f;
    float avgEventRateHzRed = 1.0f;
    float avgEventRateHzGreen = 1.0f;
    float avgEventRateHzBlue = 1.0f;
    float meanSpikes = 1;
    boolean calibrationStarted = false;
    boolean calibrationInProgress = false;
    float numSpikes = 0;
    float numPixels = chip.getSizeX() * chip.getSizeY();
    protected int[] adaptAreaStart = new int[2];
    protected int[] adaptAreaStop = new int[2];
    protected float intensity_scaling = 0.001f; //initially this was simply 1, but by having it smaller
    // one can play with software gain directly with the
    // FS parameter (up-arrow and down-arrow) which is the variable 'colorScale'
 public AdaptiveIntensityRendererColor(AEChip chip) {
        super(chip);

        AEViewer aev;
        for (int i = 0; i < chip.getSizeY(); i++) {
            for (int j = 0; j < chip.getSizeX(); j++) {
                for (int k = 0; k < chip.getNumCellTypes(); k++) {
                    calibrationMatrix[i][j][k] = 1.0f;
                    lastEvent[i][j][k] = 0.0f;
                    freqMatrix[i][j][k]=0.0f;
                }
            }
        }
        adaptAreaStart[0] = 0;
        adaptAreaStart[1] = 0;
        adaptAreaStop[0] = (int) chip.getSizeX() - 1;
        adaptAreaStop[1] = (int) chip.getSizeY() - 1;
        checkPixmapAllocation();  // make sure DisplayMethod (which uses this) has the array allocated.
    }

    //public void setCalibrationInProgress(final boolean calibrationInProgress) {
    //    this.calibrationInProgress = calibrationInProgress;
    //}
    //public boolean isCalibrationInProgress() {
    //    return(this.calibrationInProgress);
    //}
    //public void setAdaptiveArea(int sx, int ex, int sy, int ey){// to acount for the UiO foveated imager, where only the center is an Octopus type retina
    //    adaptAreaStart[0]=sx;
    //    adaptAreaStart[1]=sy;
    //    adaptAreaStop[0]=ex;
    //    adaptAreaStop[1]=ey;
    //}
    public synchronized void render(EventPacket packet) {
        if (packet == null) {
            return;
        }
        this.packet = packet;
        //int numEvents = packet.getSize();
        float alpha = 0.9f;
        float freq;
        int tt, dt = 0;

        float x1= 0.8f;
        float x2=3.0f;
        float x3=2.4f;

        float freqWhiteB=66.0f;//33.0f;
        float Cb=1.0f;
        float transductB=freqWhiteB*Cb/3;

        

        float freqWhiteGB=45.0f;//20.0f;
        float Cgb=1.5f;
        float transductG=(freqWhiteGB*Cgb-3*transductB)/2;

        
        float freqWhiteRG=350.0f;//60.0f;
        float Crg=1.0f;
        float transductR=freqWhiteRG*Crg-2*transductG;
        

        float adaptAreaNumSpikes = 0;
        

        float GUIscale=1.0f;
        checkPixmapAllocation();


        if (calibrationInProgress) {// accumulating calibration data while the camera looks at a uniform surface
            // set by pressing 'P', stopped by pressing 'P' again
            if (!calibrationStarted) {
                for (int i = 0; i < chip.getSizeY(); i++) {
                    for (int j = 0; j < chip.getSizeX(); j++) {
                        for (int k = 0; k < chip.getNumCellTypes(); k++) {
                            calibrationMatrix[i][j][k] = 0;
                        }
                    }
                }
                numSpikes = packet.getSize();
                meanSpikes = numSpikes / numPixels;
                calibrationStarted = true;
            } else {
                numSpikes += packet.getSize();
                meanSpikes = numSpikes / numPixels;
            }
        } else {
            if (calibrationStarted) {
                calibrationStarted = false;
                for (int i = 0; i < chip.getSizeY(); i++) {
                    for (int j = 0; j < chip.getSizeX(); j++) {
                        for (int k = 0; k < chip.getNumCellTypes(); k++) {
                            if (calibrationMatrix[i][j][k] != 0.0f) {
                                calibrationMatrix[i][j][k] = meanSpikes / calibrationMatrix[i][j][k];
                            } else {
                                calibrationMatrix[i][j][k] = 2;
                            }
                        }
                    }
                }
            }
        }
        float[] p = getPixmapArray();
        //avgEventRateHz=(alpha*avgEventRateHz)+(1-alpha)*(packet.getEventRateHz()/numPixels);
        try {
            if (packet.getNumCellTypes() < 2) {
                for (Object obj : packet) {
                    TypedEvent e = (TypedEvent) obj;
                    //TypedEventRGB eRGB = (TypedEventRGB) obj;
                    
                    if (calibrationInProgress) {
                        calibrationMatrix[e.y][e.x][e.type] += 1;
                    }

                    tt = e.getTimestamp();

                    dt = (int) (tt - lastEvent[e.y][e.x][e.type]);           //Finds the time since previous spike
                    lastEvent[e.y][e.x][e.type] = tt;

                    GUIscale=0.5f * ((float) Math.pow(1.3f, colorScale))* intensity_scaling;
                    freq = 1.0f / ((float) dt * 1e-6f) * calibrationMatrix[e.y][e.x][e.type] ;
                    //freq = 0.5f * ((float) Math.pow(2, colorScale)) / ((float) dt * 1e-6f) / avgEventRateHz * calibrationMatrix[e.y][e.x][e.type] * intensity_scaling;
                    int ind = getPixMapIndex(e.x, e.y);
                    switch (e.type) {
                        case 1:// RG
                        {
                            //freq=350.0f;
                           //Philipp's Code
                            //freqMatrix[e.x][e.y][0] = ((freq*Crg - freqMatrix[e.x][e.y][1]*transductG)/(transductG+ transductR));
                            //p[ind + 0] = freqMatrix[e.x][e.y][0]*GUIscale;

                            p[ind + 0] = freq*x1*GUIscale;
                            //My code
                            //p[ind + 0] = ((freq*Crg - p[ind + 1]*transductG)/(transductG+ transductR));
                           // p[ind + 0] = (a - p[ind + 1])*transductR;

                            break;
                        }
                        case 2:// GB
                        {
                           //freq=45.0f;
                            //Philipp's Code
                           //freqMatrix[e.x][e.y][1] = ((freq*Cgb  - freqMatrix[e.x][e.y][2]*transductB)/(transductB+transductG) - freqMatrix[e.x][e.y][0]);
                          // p[ind + 1] = freqMatrix[e.x][e.y][1]*GUIscale;
                           p[ind + 1] = (freq*x2-p[ind+0]*x1/x2)*GUIscale;
                             //My Code
                           //p[ind + 1] = ((freq*Cgb  - p[ind + 2]*transductB)/(transductB+transductG) - p[ind+0]);
                           //p[ind + 1] = (a  - p[ind + 2])*transductG;
                           //p[ind + 1]=p[ind+1]-p[ind+0]/transductG;
                           
                            break;
                        }
                        case 3:// B
                        {
                           //freq=66.0f;
                            //Philipp's Code
                           //freqMatrix[e.x][e.y][2] = (freq*Cb / transductB -freqMatrix[e.x][e.y][1] -freqMatrix[e.x][e.y][0]);
                           //p[ind + 2] = freqMatrix[e.x][e.y][2]*GUIscale;
                           p[ind + 2] = (freq*x3-p[ind+0]*x1/x3)*GUIscale;
                            //My Code
                           //p[ind + 2] = (freq*Cb / transductB -p[ind+1] -p[ind+0]);
                           //p[ind + 2] = a * transductB -p[ind+1]/transductB -p[ind+0]/transductB;
                            break;
                        }
                        default:
                            break;
                    }
                    if ((e.x >= adaptAreaStart[0]) && (e.y >= adaptAreaStart[1]) && (e.x <= adaptAreaStop[0]) && (e.y <= adaptAreaStop[1])) {
                        adaptAreaNumSpikes += 1;
                        
                    }
                }
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
            log.warning(e.getCause() + ": ChipRenderer.render(), some event out of bounds for this chip RGBtype?");
        }
        adaptAreaNumSpikes = adaptAreaNumSpikes / ((float) (adaptAreaStop[0] - adaptAreaStart[0]) * (float) (adaptAreaStop[1] - adaptAreaStart[1]));
        if (((float) packet.getDurationUs()) > 0) {
            avgEventRateHz = (alpha * avgEventRateHz) + (1 - alpha) * ((float) adaptAreaNumSpikes / ((float) packet.getDurationUs() * 1e-6f));
            
        }
    }
}
