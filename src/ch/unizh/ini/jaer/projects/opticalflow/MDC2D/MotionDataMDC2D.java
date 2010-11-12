package ch.unizh.ini.jaer.projects.opticalflow.MDC2D;

/*
 * MotionData.java
 *
 * Created on November 24, 2006, 6:58 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright November 24, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

import Jama.Matrix;
import ch.unizh.ini.jaer.projects.opticalflow.*;
import ch.unizh.ini.jaer.projects.opticalflow.graphics.MotionViewer;
import net.sf.jaer.chip.*;
import java.util.Random;

/**
 * Packs data returned from optical flow sensor.
 Values are floats that which are normalized as follows: all values are returned in range 0-1 float. Motion signals are centered around 0.5f.
 Global outputs are not - they are just from corner of chip.

 * @author tobi
 */
public class MotionDataMDC2D extends MotionData {



    /** Bits set in contents show what data has actually be acquired in this buffer.
     */

    private int contents=0;
    private static Random r=new Random();

    private float[][] lmc1;
    private float[][] lmc2;

    /** Creates a new instance of MotionData */
    public MotionDataMDC2D(Chip2DMotion setchip) {
        super(setchip);
        lmc1=new float[chip.getSizeX()][chip.getSizeY()];
        lmc2=new float[chip.getSizeX()][chip.getSizeY()];
        NUM_PASTMOTIONDATA=5;
    }


    /* Method override */
    protected void fillPh(){
        this.setPh( extractRawChannel(0)); //0 is the index of the first row
        this.fillAdditional(); //called here so that lmc is available in Motion algorithms
    }

    protected void fillUxUy(){
        int algorithm= MDC2D.getMotionMethod();
        switch(algorithm){
            case MDC2D.RANDOM://random
                this.calculateMotion_random(); //sets the localU randomly
                this.globalUxUy_averageLocal();
                break;
            case MDC2D.NORMAL_OPTICFLOW: //gradientBasedMethod
                this.calculateMotion_gradientBased(); // calculates the localU
                this.globalUxUy_averageLocal();
                break;
            case MDC2D.SRINIVASAN: // Srinivasan method
                this.setUx(zero()); //make local motion zero. It is not computed
                this.setUy(zero());
                this.calculateMotion_srinivasan(); //global motion according to srinivasan
        }
        globalUxUy_temporalAverage(3); //averages global x over some frames


    }

    /*
     * The global Motion is averaged
     */
    protected void globalUxUy_averageLocal(){
        float globalUx=0;
        float globalUy=0;
        for(int i=0;i<chip.NUM_COLUMNS;i++){
            for(int j=0;j<chip.NUM_ROWS;j++){
                globalUx += getUx()[i][j];
                globalUy += getUy()[i][j];
            }
        }
        globalUx /= chip.NUM_MOTION_PIXELS;
        this.setGlobalX(globalUx);
        globalUy /= chip.NUM_MOTION_PIXELS;
        this.setGlobalY(globalUy);
    }

    protected void globalUxUy_temporalAverage(int num){
        if(num>this.NUM_PASTMOTIONDATA) num=this.NUM_PASTMOTIONDATA;
        for(int i=0; i<num;i++){
            try{
                globalX +=pastMotionData[i].getGlobalX();
                globalY +=pastMotionData[i].getGlobalY();
            }catch(NullPointerException e){
                num--;
            }
        }
        globalX /=num+1;
        globalY /=num+1;


    }



    protected void fillMinMax(){
        minph = maxph = minux = maxux = minuy = maxuy =0;
        for(int i=0;i<chip.NUM_COLUMNS;i++){
            for(int j=0;j<chip.NUM_ROWS;j++){
                float a= ux[i][j];
                if (ux[i][j]<minux)  minux=ux[i][j];
                if (ux[i][j]>maxux)  maxux=ux[i][j];
                if (uy[i][j]<minuy)  minuy=uy[i][j];
                if (uy[i][j]>maxuy)  maxux=uy[i][j];
                if (ph[i][j]<minph)  minph=ph[i][j];
                if (ph[i][j]>maxph)  maxph=ph[i][j];
            }
        }

    }

    protected void fillAdditional(){
        this.lmc1 = extractRawChannel(1); //1 is the index of the 2nd row
        this.lmc2 = extractRawChannel(2); //3 is the index of the 3th row
    }

    protected void updateContents(){
        setContents(0x7F); //everything except bit7
    }

    //Overwrite extractRawCHannel method. This subclass needs the y values to be 
    // mirrored.
    public float[][] extractRawChannel(int channelNumber){
        int maxX=this.chip.NUM_COLUMNS;
        int maxY=this.chip.NUM_ROWS;
        float[][] channelData =new float[maxX][maxY] ;
        for(int x=0;x<maxX;x++){
            for(int y=0;y<maxY;y++){
                channelData[y][x]=this.rawDataPixel[channelNumber][y][x]; 
            }
        }
        return channelData;
    }

    public float[][] zero(){
        int maxPos=chip.NUM_COLUMNS;
        float[][] channelData =new float[maxPos][maxPos] ;
        for(int x=0;x<maxPos;x++){
            for(int y=0;y<maxPos;y++){
                channelData[y][x]=(float)0;
            }
        }
        return channelData;
    }


    /**
     * Motion methods
     */


        /**
         * RANDOM MOTION
         * This generates random values between -1 and 1 for ux and uy.
         * By itself not very interesting, but useful for testing.
         */
        private void calculateMotion_random(){
            //first fill Ux
            setUx(randomizeArray(getUx(),-1,1));
            //now fill Uy
            setUy(randomizeArray(getUy(),-1,1));
        }



        /**
         * NORMAL OPTIC FLOW algorithm
         * This is the implementation of a gradient based optical flow algorithm.
         * As additional constraint it is assumed that the correct flow is perpen-
         * dicular to the pixel orientation.
         * this results in the equations ux=-(Ix *It)/(Ix^2 +Iy^2)
         *                           and uy=-(Iy *It)/(Ix^2 +Iy^2)
         * with Ix=dI/dt, Iy=dI/dy, It=dI/dt
         */
        private void calculateMotion_gradientBased(){
            int chan=0;
            boolean filter=false;
            float dIdx;
            float dIdy;
            float dIdt;
            float[][] raw=this.extractRawChannel(chan);
            float[][] past=this.getPastMotionData()[0].extractRawChannel(chan);
            if(filter){ //RetoTODO see if it makes sense
                raw=this.filter_DOG(raw);
                past=this.filter_DOG(past);

            }
            //go through the whole image
            for(int i=0; i< chip.NUM_COLUMNS;i++){
                for(int j=0; j< chip.NUM_ROWS; j++){

                    //if at the border of the picture the local motion vectiors are 0
                    if(j==0 || i==0 || j==chip.NUM_COLUMNS-1 || i==chip.NUM_ROWS-1){
                        ux[j][i]=(float)0;
                        uy[j][i]=(float)0;
                    }else{
                        
                        dIdx = (raw[j][i+1]-raw[j][i-1])/2; // average slope to pixel before and after.
                        dIdy = (raw[j+1][i]-raw[j-1][i])/2; // average slope to pixel before and after.
                        float t1=getTimeCapturedMs(); //RetoTODO calculate dt
                        float t0=t1-2500;//(float)10E13;//pastMotionData[1].getTimeCapturedMs();
                        dIdt = (raw[j][i]-past[j][i])/2;//(getTimeCapturedMs()-pastMotionData[1].getTimeCapturedMs());

                        if(dIdx*dIdx + dIdy*dIdy!=0){ // check for division by 0
                            ux[j][i]=-dIdx*dIdt/(dIdx*dIdx + dIdy*dIdy);
                            uy[j][i]=-dIdy*dIdt/(dIdx*dIdx + dIdy*dIdy);
                        }else{
                            ux[j][i] = 0;
                            uy[j][i] = 0;
                        }
                    }
                }
            }
        }

        //RetoTODO delete this backup when not needed
        /**
        private void calculateMotion_gradientBased(){
            int chan=1;
            float dIdx;
            float dIdy;
            float dIdt;
            //go through the whole image
            for(int i=0; i< chip.NUM_COLUMNS;i++){
                for(int j=0; j< chip.NUM_ROWS; j++){

                    //if at the border of the picture the local motion vectiors are 0
                    if(j==0 || i==0 || j==chip.NUM_COLUMNS-1 || i==chip.NUM_ROWS-1){
                        ux[j][i]=(float)0;
                        uy[j][i]=(float)0;
                    }else{

                        dIdx = (rawDataPixel[chan][j][i+1]-rawDataPixel[chan][j][i])/1;
                        dIdy = (rawDataPixel[chan][j+1][i]-rawDataPixel[chan][j][i])/1;
                        float t1=getTimeCapturedMs(); //RetoTODO calculate dt
                        float t0=t1-2500;//(float)10E13;//pastMotionData[1].getTimeCapturedMs();
                        dIdt = (rawDataPixel[chan][j][i]-(pastMotionData[0].getRawDataPixel())[chan][j][i]);//(getTimeCapturedMs()-pastMotionData[1].getTimeCapturedMs());

                        if(dIdx*dIdx + dIdy*dIdy!=0){ // check for division by 0
                            ux[j][i]=-dIdx*dIdt/(dIdx*dIdx + dIdy*dIdy);
                            uy[j][i]=-dIdy*dIdt/(dIdx*dIdx + dIdy*dIdy);
                        }else{
                            ux[j][i] = 0;
                            uy[j][i] = 0;
                        }
                    }
                }
            }
        }*/
        void dummyToHideCommentAbove(){}

        
        /**
         * OPTIC FLOW ALGORITHM BY SRINIVASAN
         * This assumes that the brightness I(t,x,y) is a approximately a linear
         * combination of i=-n...n I(t-1,x+-i, y+-i).
         * Rotation is not calculated and should not appear in the image.
         * The algorithm computes a global motion.
         */
        private void calculateMotion_srinivasan(){
            int chan=1;
            float[][] raw=this.extractRawChannel(chan);
            float[][] past=this.getPastMotionData()[0].extractRawChannel(chan);
            Matrix A = new Matrix(new double[2][2]);
            Matrix b = new Matrix(new double[2][1]);
            float a11=0, a12=0;
            float a21=0, a22=0;
            float b1=0,  b2=0;
            for(int i=1; i< chip.NUM_COLUMNS-1;i++){ //leave out border pixel
                for(int j=1; j< chip.NUM_ROWS-1; j++){ //leave out border pixel
                    a11 += (past[j][i-1] - past[j][i+1])* (past[j][i-1] - past[j][i+1]);
                    a12 += (past[j-1][i]- past[j+1][i]) * (past[j][i-1] - past[j][i+1]);
                    a21 += (past[j-1][i]- past[j+1][i]) * (past[j][i-1] - past[j][i+1]);
                    a22 += (past[j-1][i]- past[j+1][i]) * (past[j-1][i]- past[j+1][i]);
                    b1  += 2 * (raw[j][i]- past[j][i])  * (past[j][i-1] - past[j][i+1]);
                    b2  += 2 * (raw[j][i]- past[j][i])  * (past[j-1][i] - past[j+1][i]);
                }
            }
            A.set(0, 0, a11);
            A.set(0, 1, a12);
            A.set(1, 0, a21);
            A.set(1, 1, a22);
            b.set(0, 0, b1);
            b.set(1, 0, b2);
            try{
                Matrix x = A.solve(b);
                this.setGlobalX((float)x.get(0, 0));
                this.setGlobalY((float)x.get(1, 0));
            } catch (Exception e) {
                this.setGlobalX(0);
                this.setGlobalY(0);
                System.out.println("Matrix decomposition failed. No global motion vector computed");

            }
        }


        /**
         * Implements a difference off gaussian filter with a center of one pixel
         * and souuound of the 8 neighboring pixels. Center has weight +1, while
         * each pixel of the surround has weight -1/8.
         */
        private float[][] filter_DOG(float[][] arrayToFilter){
            int maxi=arrayToFilter.length;
            int maxj=arrayToFilter[1].length;
            float[][] filtered=new float[maxj][maxi];
            for(int i=0; i<maxi;i++){
                for(int j=0;j<maxj;j++){
                    if(j==0 || i==0 || j==maxj-1 || i==maxi-1){
                        filtered[j][i]=(float)0;
                    }else{
                        filtered[j][i]=arrayToFilter[j][i]-(float)0.1* (arrayToFilter[j-1][i-1]+
                                                                        arrayToFilter[j-1][i]+
                                                                        arrayToFilter[j-1][i+1]+
                                                                        arrayToFilter[j][i-1]+
                                                                        arrayToFilter[j][i+1]+
                                                                        arrayToFilter[j+1][i-1]+
                                                                        arrayToFilter[j+1][i]+
                                                                        arrayToFilter[j+1][i+1]);
                    }
                }
            }
            return filtered;
        }

}












