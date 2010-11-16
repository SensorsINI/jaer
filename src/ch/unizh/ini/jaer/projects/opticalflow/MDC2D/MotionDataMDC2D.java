package ch.unizh.ini.jaer.projects.opticalflow.MDC2D;

/*
 * MotionDataMDC2D.java
 *
 * Created on November 12, 2010, 10:00 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright November 12, 2010 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

import Jama.Matrix;
import ch.unizh.ini.jaer.projects.opticalflow.*;

/**
 * Packs data returned from optical flow sensor.
 * The different methods to extract motion out of raw data are implemented in this
 * class

 * @author reto
 */
public class MotionDataMDC2D extends MotionData {

    /** Bits set in contents show what data has actually be acquired in this buffer.
     */
    private int contents=0;

    // data from the lmc channels of the MDC2D chip
    private float[][] lmc1;
    private float[][] lmc2;

    private int channel; //the channel used for calculations in the MotionAlgorithms

    private int globalScaleFactor =10; //this is an arbituary scale factor that compensates for historical differences in the display method for global vector

    /** Creates a new instance of MotionData */
    public MotionDataMDC2D(Chip2DMotion setchip) {
        super(setchip);
        lmc1=new float[chip.getSizeX()][chip.getSizeY()];
        lmc2=new float[chip.getSizeX()][chip.getSizeY()];
        NUM_PASTMOTIONDATA=5; // store the 5 previous MotionData for computations
    }


    /* Method override */
    protected void fillPh(){
        this.setPh( extractRawChannel(0)); //0 is the index of the first row
        this.fillAdditional(); //called here so that lmc is available in Motion algorithms
    }

    protected void fillUxUy(){
        int algorithm= MDC2D.getMotionMethod(); //gets the MotionMethod set from the GUI
        channel = MDC2D.getChannelForMotionAlgorithm();
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
        globalUxUy_temporalAverage(5); //averages global motion over some frames
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



    /**
     * Motion methods: the different methods to extract motion.
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
        boolean filter=false;
        float dIdx;
        float dIdy;
        float dIdt;
        float[][] raw=this.extractRawChannel(channel);
        float[][] past=this.getPastMotionData()[0].extractRawChannel(channel);
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

                    dIdx = (raw[j][i+1]-raw[j][i-1])/2; // average slope to pixel before and after. unit dI/pixel
                    dIdy = (raw[j+1][i]-raw[j-1][i])/2; // average slope to pixel before and after.
                    long dt=getTimeCapturedMs()-getPastMotionData()[0].getTimeCapturedMs();
                    dIdt = (raw[j][i]-past[j][i])/2/dt; //unit dI/ms
                    if(dIdx*dIdx + dIdy*dIdy!=0 && dt!=0){ // check for division by 0
                        ux[j][i]=-dIdx*dIdt/(dIdx*dIdx + dIdy*dIdy); // unit pixel/ms
                        uy[j][i]=-dIdy*dIdt/(dIdx*dIdx + dIdy*dIdy);
                    }else{
                        ux[j][i] = 0;
                        uy[j][i] = 0;
                    }
                }
            }
        }
    }

    /**
     * OPTIC FLOW ALGORITHM BY SRINIVASAN
     * This assumes that the brightness I(t,x,y) is a approximately a linear
     * combination of x=-n...n I(t-1,x+-x, y+-x).
     * Rotation is not calculated and should not appear in the image.
     * The algorithm computes a global motion.
     */
    private void calculateMotion_srinivasan(){
        float[][] raw=this.extractRawChannel(channel);
        float[][] past=this.getPastMotionData()[0].extractRawChannel(channel);
//          for(int x=1; x< chip.NUM_COLUMNS-1;x++){ //leave out border pixel
//            for(int y=1; y< chip.NUM_ROWS-1; y++){
//                past[y][x]=raw[y+1][x-1];
//            }
//        }
        Matrix A = new Matrix(new double[2][2]);
        Matrix b = new Matrix(new double[2][1]);
        float a11=0, a12=0;
        float a21=0, a22=0;
        float b1=0,  b2=0;
        for(int x=1; x< chip.NUM_COLUMNS-1;x++){ //leave out border pixel
            for(int y=1; y< chip.NUM_ROWS-1; y++){ //leave out border pixel
                a11 += (past[y][x-1] - past[y][x+1])* (past[y][x-1] - past[y][x+1]);
                a12 += (past[y-1][x]- past[y+1][x]) * (past[y][x-1] - past[y][x+1]);
                a21 += (past[y-1][x]- past[y+1][x]) * (past[y][x-1] - past[y][x+1]);
                a22 += (past[y-1][x]- past[y+1][x]) * (past[y-1][x]- past[y+1][x]);
                b1  += 2 * (raw[y][x]- past[y][x])  * (past[y][x-1] - past[y][x+1]);
                b2  += 2 * (raw[y][x]- past[y][x])  * (past[y-1][x] - past[y+1][x]);
            }
        }
        A.set(0, 0, a11);
        A.set(0, 1, a12);
        A.set(1, 0, a21);
        A.set(1, 1, a22);
        b.set(0, 0, b1);
        b.set(1, 0, b2);
        
        long dt=getTimeCapturedMs()-getPastMotionData()[0].getTimeCapturedMs();

        try{
            Matrix x = A.solve(b);
            if(dt!=0){
                this.setGlobalX((float)x.get(0, 0)/dt*globalScaleFactor);
                this.setGlobalY((float)x.get(1, 0)/dt*globalScaleFactor);
            }else{
                this.setGlobalX((0));
                this.setGlobalY((0));

            }
        } catch (Exception e) {
            this.setGlobalX(0);
            this.setGlobalY(0);
            System.out.println("Matrix decomposition failed. No global motion vector computed");

        }
    }







    /*
     * Support methods
     */
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


    //average the local motion vectors to get the global one
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
        this.setGlobalX(globalUx*globalScaleFactor);
        globalUy /= chip.NUM_MOTION_PIXELS;
        this.setGlobalY(globalUy*globalScaleFactor);
    }

    //temporally average the global motion vector over some frames
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


    protected void updateContents(){
        setContents(0x7F); //everything except bit7
    }

    //returns a 2D array of the picture size filled with 0s
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

}












