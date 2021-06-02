/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.graphics;

/**
 * Extending the adaptive intensity renderer for our first octopus colour retina
 * Not sure yet about how to do the adaption best
 * @authors: juanle, hafliger
 */
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.TypedEvent;


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

        //float x1=1.3f; //0.7RG Scale
        //float x2=4.2f;  //3.2GB Scale
        //float x3=2.2f;  //2.2B Scale

        float eb=0.7f; //0.7
        float eg=0.7f; //0.7
        float er=1f;

        float Cb=1.1f/14700f;//1.2f/14700f;
        float Cgb=1.2f/4500f;//1.3f/4500f;
        float Crg=1f/14100f;//0.95f/14100f;

        float gateing=0f; //0.3

     

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
        float[] p= getPixmapArray();
        float[] E=new float [22*22*3];
        float[] Emax=new float [22*22*3];
        float[] E_aux=new float [22*22*3];
        float EmaxR_global=0f;
        float EmaxG_global=0f;
        float EmaxB_global=0f;
        float Emax_global=0f;
        float Emax_local=0f;
        float Sensor_size=22f;
        float k=2f; //Scaling factor for the average value

        for (int i=0; i<Sensor_size*Sensor_size*3;i++){

           E[i]=0;
           Emax[i]=0;
            E_aux[i]=0;

       }



        

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
                    if ((e.y!=21)&(e.x!=21)&(e.y!=0)&(e.x!=0)) {
                    
                    int ind = getPixMapIndex(e.x, e.y);
                    switch (e.type) {
                        case 1:// RG
                        {
                                
                              
                                //Old color separation algorithm //p[ind + 0] = freq*x1*GUIscale;

                          
                            Emax_global=Emax_global-E[ind+0];
                            E[ind+0]=(freq*Crg-E[ind+1]*eb)/er;
                            
                            Emax_global=Emax_global+E[ind+0];
                            
                            //New gateing operation
                            
                            //Emax_local=Math.max(E[ind+0],E[ind+1]);
                            //Emax_local=Math.max(Emax_local,E[ind+2]);
                            //E_aux[ind+0]=Math.max(E[ind+0]-Emax_local*gateing, 0);
                            //E_aux[ind+0]=3*Sensor_size*Sensor_size*E_aux[ind+0]/(k*Emax_global);
                            
                            //E_aux[ind+0]=0.0212f*E[ind+0]-0.0688f*E[ind+1]+0.0119f*E[ind+2];
                            E_aux[ind+0]=3*Sensor_size*Sensor_size*E[ind+0]/(k*Emax_global);
                                                     
                            
                            if (E_aux[ind+0]>1){
                                  E_aux[ind+0]=1;
                            }
                            
                            E_aux[ind+0]=(E_aux[ind+0]-gateing)/(1-gateing);
                            p[ind+0]=GUIscale*Math.max(E_aux[ind+0],0);
                            
                           
                           
                            break;
                        }
                        case 2:// GB
                        {
                            
                                    //Old color separation algorithm //p[ind + 1] = (freq*x2-0*p[ind+0]*x1/x2)*GUIscale;
                               
                                
                                Emax_global=Emax_global-E[ind+1];
                                E[ind+1]=(freq*Cgb-E[ind+2]*eb)/eg;
                                
                                Emax_global=Emax_global+E[ind+1];
                                 
                                //New gateing operation
                            
                                //Emax_local=Math.max(E[ind+0],E[ind+1]);
                                //Emax_local=Math.max(Emax_local,E[ind+2]);
                                //E_aux[ind+1]=Math.max(E[ind+1]-Emax_local*gateing, 0);
                                //E_aux[ind+1]=3*Sensor_size*Sensor_size*E_aux[ind+1]/(k*Emax_global);
                           
                                //E_aux[ind+1]=-0.008f*E[ind+0]+0.1070f*E[ind+1]-0.0364f*E[ind+2];
                                E_aux[ind+1]=3*Sensor_size*Sensor_size*E[ind+1]/(k*Emax_global);
                                if (E_aux[ind+1]>1){
                                    E_aux[ind+1]=1;
                                }
                                
                                E_aux[ind+1]=(E_aux[ind+1]-gateing)/(1-gateing);
                                p[ind+1]=GUIscale*Math.max(E_aux[ind+1],0);
                               
                                
                            
                           
                            break;
                        }
                        case 3:// B
                        {
                          
                                //Old color separation algorithm //p[ind + 2] = (freq*x3-p[ind+0]*x1/x3)*GUIscale;
                       
                            
                           Emax_global=Emax_global-E[ind+2];
                           E[ind+2]=freq*Cb/eb;
                            
                           Emax_global=Emax_global+E[ind+2];
                           
                           //New gateing operation
                            
                            //Emax_local=Math.max(E[ind+0],E[ind+1]);
                            //Emax_local=Math.max(Emax_local,E[ind+2]);
                            //E_aux[ind+2]=Math.max(E[ind+2]-Emax_local*gateing, 0);
                            //E_aux[ind+2]=3*22*22*E_aux[ind+2]/(k*Emax_global);
                           
                                
                           //E_aux[ind+2]=-0.0036f*E[ind+0]-0.0592f*E[ind+1]+0.0566f*E[ind+2];
                           E_aux[ind+2]=3*Sensor_size*Sensor_size*E[ind+2]/(k*Emax_global);
                            if (E_aux[ind+2]>1){
                                    E_aux[ind+2]=1;
                            }
                          
                           E_aux[ind+2]=(E_aux[ind+2]-gateing)/(1-gateing);
                           p[ind+2]=GUIscale*Math.max(E_aux[ind+2],0);
                           
                          
                          
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
