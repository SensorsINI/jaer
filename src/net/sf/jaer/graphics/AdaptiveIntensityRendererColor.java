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

public class AdaptiveIntensityRendererColor extends AdaptiveIntensityRenderer implements Calibratible //implements Calibratible
{ // this renderer implements Calibratible so the AEViewer menu has the calibration menu enabled.

    float[][][] calibrationMatrix = new float[chip.getSizeY()][chip.getSizeX()][chip.getNumCellTypes()];
    float[][][] lastEvent = new float[chip.getSizeY()][chip.getSizeX()][chip.getNumCellTypes()];
    float avgEventRateHz = 1.0f;
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
        float a;
        int tt, dt = 0;
        float scaleR=2.0f*466.46f/354.739f;
        float scaleG=2.0f*466.46f/45.005f;//54
        float scaleB=1.0f*466.46f/66.886f;

        float adaptAreaNumSpikes = 0;

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
                    if (calibrationInProgress) {
                        calibrationMatrix[e.y][e.x][e.type] += 1;
                    }

                    tt = e.getTimestamp();

                    dt = (int) (tt - lastEvent[e.y][e.x][e.type]);           //Finds the time since previous spike
                    lastEvent[e.y][e.x][e.type] = tt;
                    a = 0.5f * ((float) Math.pow(2, colorScale)) / ((float) dt * 1e-6f) / avgEventRateHz * calibrationMatrix[e.y][e.x][e.type] * intensity_scaling;

                    int ind = getPixMapIndex(e.x, e.y);
                    switch (e.type) {
                        case 0:// R
                        {
                            p[ind + 0] = a * scaleR- p[ind + 1];
                            break;
                        }
                        case 1:// B
                        {
                            p[ind + 2] = a * scaleB;
                            break;
                        }
                        case 3:// G
                        {
                            p[ind + 1] = a * scaleG - p[ind + 2];
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
            log.warning(e.getCause() + ": ChipRenderer.render(), some event out of bounds for this chip type?");
        }
        adaptAreaNumSpikes = adaptAreaNumSpikes / ((float) (adaptAreaStop[0] - adaptAreaStart[0]) * (float) (adaptAreaStop[1] - adaptAreaStart[1]));
        if (((float) packet.getDurationUs()) > 0) {
            avgEventRateHz = (alpha * avgEventRateHz) + (1 - alpha) * ((float) adaptAreaNumSpikes / ((float) packet.getDurationUs() * 1e-6f));
        }
    }
}
