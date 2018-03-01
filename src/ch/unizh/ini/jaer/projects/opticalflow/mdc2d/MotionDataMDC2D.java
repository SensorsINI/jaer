package ch.unizh.ini.jaer.projects.opticalflow.mdc2d;

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

import net.sf.jaer.util.jama.Matrix;
import ch.unizh.ini.jaer.projects.opticalflow.Chip2DMotion;
import ch.unizh.ini.jaer.projects.opticalflow.MotionData;

/**
 * Packs data returned from optical flow sensor.
 * The different methods to extract motion out of raw data are implemented in this
 * class

 * @author reto
 */
public class MotionDataMDC2D extends MotionData {

    // these additional bits are used in frame-descriptor and in setCaptureMode
    // see unpackData() somewhere in the hardware interface and setCaptureMode
    // in Chip2DMotion and the hardware interfaces
    public static final int LMC1=BIT5,
                            LMC2=BIT6,
                            ON_CHIP_ADC=BIT7;

    private int channel; //the channel used for calculations in the MotionAlgorithms

    private int numLocalToAverageForGlobal; //the number of pixel which have meaningful output. Some noise reduction methods set local motion values to zero if for example the neighbors dont show a similar response. When averaging these 0 schuold not be counted for normalization reasons.
    /** Creates a new instance of MotionData */
    public MotionDataMDC2D(Chip2DMotion setchip) {
        super(setchip);
        NUM_PASTMOTIONDATA=50; // store the 5 previous MotionData for computations
    }


    public static boolean enabled = false;
    public static float thresh=0;
    public static float match =0;
    public static int   temporalAveragesNum= 5;
    
    /* Method override */
    @Override
    protected void fillPh(){
        this.setPh( extractRawChannel(0)); //0 is the index of the first row
        this.fillAdditional(); //called here so that lmc is available in Motion algorithms
    }

    @Override
    protected void fillUxUy(){
        int algorithm= MDC2D.getMotionMethod(); //gets the MotionMethod set from the GUI
        channel = MDC2D.getChannelForMotionAlgorithm();
        numLocalToAverageForGlobal=chip.NUM_MOTION_PIXELS; //when averaging the default number of valid pixels is ALL of them.this can change later
        switch(algorithm){
            case MDC2D.NORMAL_OPTICFLOW: //gradientBasedMethod
                this.calculateMotion_gradientBased(); // calculates the localU using a gradient based method
//                this.localUxUy_filterByCorrelation(0); // filter local motion. average correlation of the pixel and its neighbors must be >(limit). (limit)=0.5 means that the pixel output has in average a 60deg angle to its neighbors
//                this.localUxUy_spatialAverage();
                this.localUxUy_temporalAverage(3); //temporal average over (num)) frames for local Motion
                this.globalUxUy_averageLocal(); // average the local motion to get the global one
//                this.globalUxUy_temporalAverage(2); //averages global motion over (num) frames
                this.globalUxUy_temporalAverage(temporalAveragesNum); //averages global motion over (num) frames
                break;
            case MDC2D.SRINIVASAN: // Srinivasan method
                this.setUx(zero()); //make local motion zero. It is not computed
                this.setUy(zero());
                this.calculateMotion_srinivasan(); //global motion according to srinivasan
//                this.globalUxUy_temporalAverage(5); //averages global motion over (num) frames
                this.globalUxUy_temporalAverage(temporalAveragesNum); //averages global motion over (num) frames
                break;
            case MDC2D.LOCAL_SRINIVASAN:
                this.setUx(zero()); //some points are filled later
                this.setUy(zero());
                this.calculateMotion_localSrinivasan(4); // calculate the local motion of (int)*(int) sections of the frame
                this.localUxUy_temporalAverage(3); //average local motion over time for (num) frames
                this.globalUxUy_averageLocal(); //get global motion
//                this.globalUxUy_temporalAverage(5); //averages global motion over (num) frames
                this.globalUxUy_temporalAverage(temporalAveragesNum); //averages global motion over (num) frames
                break;
            case MDC2D.TIME_OF_TRAVEL:
                float thresholdContrast=thresh/100; // the miminum contrast between two pixels to be recognized by the algorithm
                float matchContrast=match/100; //the maximum allowed difference in contrast between two frames to recognize it as the same feature
                this.calculateMotion_timeOfTravel_absValue(thresholdContrast,matchContrast); //calculate the local motion with a time to travel algorithm
                this.globalUxUy_averageLocal(); //get global motion
//                this.globalUxUy_temporalAverage(5); //averages global motion over (num) frames
                this.globalUxUy_temporalAverage(temporalAveragesNum); //averages global motion over (num) frames
                break;
            case MDC2D.RANDOM://random
                this.calculateMotion_random(); //sets the localU randomly
//                this.localUxUy_temporalAverage(3); //average local motion over time for (num) frames
                this.globalUxUy_temporalAverage(temporalAveragesNum); //averages global motion over (num) frames
                this.globalUxUy_averageLocal();
                break;
        }
//        System.out.print(globalX);System.out.print("\t");System.out.println(globalY);
    }

    @Override
    protected void fillMinMax(){
        minph = 1; minux = 1; minuy = 1;
        maxph = 0; maxux = 0; maxuy =0;
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

    @Override
    protected void fillAdditional(){
    }



    /**
     * Motion methods: the different methods to extract motion.
     */

    /**
     * NORMAL OPTIC FLOW algorithm
     * This is the implementation of a gradient based optical flow algorithm.
     * As additional constraint it is assumed that the correct flow is perpen-
     * dicular to the pixel orientation.
     * this results in the equations ux=-(Ix *It)/(Ix^2 +Iy^2)
     *                           and uy=-(Iy *It)/(Ix^2 +Iy^2)
     * with Ix=dI/dt, Iy=dI/dy, It=dI/dt
     */
    protected void calculateMotion_gradientBased(){
        float dIdx;
        float dIdy;
        float dIdt;
        float[][] raw=this.extractRawChannel(channel);
        float[][] past=this.getPastMotionData()[0].extractRawChannel(channel);

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
                    dIdt = (raw[j][i]-past[j][i])/(float)dt; //unit dI/ms
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
    protected void calculateMotion_srinivasan(){
        if (getPastMotionData() == null)
            return;
        float[][] raw=this.extractRawChannel(channel);
        float[][] past=this.getPastMotionData()[0].extractRawChannel(channel);
        Matrix A = new Matrix(new double[2][2]);
        Matrix b = new Matrix(new double[2][1]);
        float a11=0, a12=0;
        float a21=0, a22=0;
        float b1=0,  b2=0;
        float f1, f2, f3, f4;
        try{
            for(int x=1; x< chip.NUM_COLUMNS-1;x++){ //leave out border pixel
                for(int y=1; y< chip.NUM_ROWS-1; y++){ //leave out border pixel
                    f1=past[y][x+1];
                    f2=past[y][x-1];
                    f3=past[y+1][x];
                    f4=past[y-1][x];
                    a11 += (f2 - f1) * (f2 - f1);
                    a12 += (f4 - f3) * (f2 - f1);
                    a21 += (f4 - f3) * (f2 - f1);
                    a22 += (f4 - f3) * (f4 - f3);

                    b1  += 2 * (raw[y][x]- past[y][x])  * (f2 - f1);
                    b2  += 2 * (raw[y][x]- past[y][x])  * (f4 - f3);
                }
            }
        } catch(ArrayIndexOutOfBoundsException e){
            System.out.println(e);//dont do anything for the moment
        }
        A.set(0, 0, a11);   A.set(0, 1, a12);    
        A.set(1, 0, a21);   A.set(1, 1, a22);   

        b.set(0, 0, b1);
        b.set(1, 0, b2);
        
        long dt=getTimeCapturedMs()-getPastMotionData()[0].getTimeCapturedMs();

        try{
            Matrix x = A.solve(b);
            if(dt!=0){
                this.setGlobalX((float)x.get(0, 0));
                this.setGlobalY((float)x.get(1, 0));
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

    /**
     * OPTIC FLOW ALGORITHM BY SRINIVASAN INCLUDING ROTATION
     * This assumes that the brightness I(t,x,y) is a approximately a linear
     * combination of x=-n...n I(t-1,x+-x, y+-x).
     *
     * The algorithm computes a global motion.
     */
    protected void calculateMotion_srinivasan_inclRot(){
        float phi =45;
        phi=(float) Math.toRadians(phi);
        float[][] raw=this.extractRawChannel(channel);
        float[][] past=this.getPastMotionData()[0].extractRawChannel(channel);
        Matrix A = new Matrix(new double[3][3]);
        Matrix b = new Matrix(new double[3][1]);
        float a11=0, a12=0, a13=0;
        float a21=0, a22=0, a23=0;
        float a31=0, a32=0, a33=0;
        float b1=0,  b2=0, b3=0;
        float f1, f2, f3, f4, f5, f6;
        double xprime,yprime;
        try{
            for(int x=1; x< chip.NUM_COLUMNS-1;x++){ //leave out border pixel
                for(int y=1; y< chip.NUM_ROWS-1; y++){ //leave out border pixel
                    f1=past[y][x+1];
                    f2=past[y][x-1];
                    f3=past[y+1][x];
                    f4=past[y-1][x];
                    xprime=x*Math.cos(phi)+y*Math.sin(phi);
                    yprime=x*Math.sin(phi)+y*Math.cos(phi);
//                    f5=past[(int)(yprime)][xprime];
                    xprime=x*Math.cos(-phi)+y*Math.sin(-phi);
                    yprime=x*Math.sin(-phi)+y*Math.cos(-phi);
//                    f6=past[yprime][xprime];
                    a11 += (f2 - f1) * (f2 - f1);
                    a12 += (f4 - f3) * (f2 - f1);
//                    a13 += (f6 - f5) * (f2 - f1);

                    a21 += (f4 - f3) * (f2 - f1);
                    a22 += (f4 - f3) * (f4 - f3);
//                    a23 += (f6 - f5) * (f4 - f3);

//                    a31 += (f2 - f1) * (f6 - f5);
//                    a32 += (f4 - f3) * (f6 - f5);
//                    a33 += (f6 - f5) * (f6 - f5);

//                    b1  += 2 * (raw[y][x]- past[y][x])  * (f2 - f1);
//                    b2  += 2 * (raw[y][x]- past[y][x])  * (f4 - f3);
//                    b3  += 2 * (raw[y][x]- past[y][x])  * (f6 - f5);
                }
            }
        } catch(ArrayIndexOutOfBoundsException e){
            System.out.println(e);//dont do anything for the moment
        }
//        A.set(0, 0, a11);   A.set(0, 1, a12);    A.set(0, 2, a13);
//        A.set(1, 0, a21);   A.set(1, 1, a22);    A.set(1, 2, a23);
//        A.set(2, 0, a31);   A.set(2, 1, a32);    A.set(2, 2, a33);
//        b.set(0, 0, b1);
//        b.set(1, 0, b2);
//        b.set(2, 0, b3);



        
        long dt=getTimeCapturedMs()-getPastMotionData()[0].getTimeCapturedMs();

        try{
            Matrix x = A.solve(b);
            if(dt!=0){
                this.setGlobalX((float)x.get(0, 0));
                this.setGlobalY((float)x.get(1, 0));
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

    /**
     * LOCAL OPTIC FLOW ALGORITHM BY SRINIVASAN
     * This assumes that the brightness I(t,x,y) is a approximately a linear
     * combination of x=-n...n I(t-1,x+-x, y+-x).
     * Rotation is not calculated and should not appear in the image.
     * The algorithm computes is the same as in calculateMotion_srinivasan. The
     * only difference is that here the picture is split into sections for which
     * motion is calculated separatly using the srinivasan algorithm.
     */
    protected void calculateMotion_localSrinivasan(int divideSideBy){
        float[][] globalRaw=this.extractRawChannel(channel);
        float[][] globalPast=this.getPastMotionData()[0].extractRawChannel(channel);
        int div = divideSideBy; // in how many parts a side of the frame is divided (eg. div=2 results in 2*2=4subsections
        int subarrayLength=chip.NUM_COLUMNS/div; //the chip has equal height and width
        float[][] localRaw= new float[subarrayLength][subarrayLength];
        float[][] localPast= new float[subarrayLength][subarrayLength];
        Matrix A = new Matrix(new double[2][2]);
        Matrix b = new Matrix(new double[2][1]);
        float a11=0, a12=0;
        float a21=0, a22=0;
        float b1=0,  b2=0;
        long dt=getTimeCapturedMs()-getPastMotionData()[0].getTimeCapturedMs();
        for(int i=0;i<div;i++){
            for(int j=0;j<div;j++){

                //copy subarray of the whole frames (past and current) into better handable units
                for(int c=0;c<subarrayLength;c++){
                    for(int d=0;d<subarrayLength;d++){
                        localRaw[c][d]=globalRaw[j*subarrayLength+c][i*subarrayLength+d];

                        localPast[c][d]=globalPast[j*subarrayLength+c][i*subarrayLength+d];
                    }
                }

                //apply srinivasan method to subarray
                for(int x=1; x< subarrayLength-1;x++){ //leave out border pixel
                    for(int y=1; y< subarrayLength-1; y++){ //leave out border pixel
                        a11 += (localPast[y][x-1] - localPast[y][x+1])* (localPast[y][x-1] - localPast[y][x+1]);
                        a12 += (localPast[y-1][x]- localPast[y+1][x]) * (localPast[y][x-1] - localPast[y][x+1]);
                        a21 += (localPast[y-1][x]- localPast[y+1][x]) * (localPast[y][x-1] - localPast[y][x+1]);
                        a22 += (localPast[y-1][x]- localPast[y+1][x]) * (localPast[y-1][x]- localPast[y+1][x]);
                        b1  += 2 * (localRaw[y][x]- localPast[y][x])  * (localPast[y][x-1] - localPast[y][x+1]);
                        b2  += 2 * (localRaw[y][x]- localPast[y][x])  * (localPast[y-1][x] - localPast[y+1][x]);
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
                    if(dt!=0){
                        this.ux[(int)((j+0.5)*subarrayLength)][(int)((i+0.5)*subarrayLength)]=((float)x.get(0, 0)/(float)dt*subarrayLength*subarrayLength);
                        this.uy[(int)((j+0.5)*subarrayLength)][(int)((i+0.5)*subarrayLength)]=((float)x.get(1, 0)/(float)dt*subarrayLength*subarrayLength);
                    }else{
                        ; //if matrix equation doesnt have solution. set the local motion to 0: This is already done and thus not neccessary
                    }
                } catch (Exception e) {
                    ; //if matrix equation doesnt have solution. set the local motion to 0: This is already done and thus not neccessary
                    System.out.println("Matrix decomposition failed. ");
                }
            }
        }
    }

    /**
     * Looks for pixels with high spatial contrast in horizontal or vertical direction.
     * Lower contrasts are ignored and also not taken into account for the global
     * motion. Channel is only lmc1 since this is a amplified and differentiated version
     * of the photoreceptor stage. Its feature is a high reaction to spatial contrasts.
     */
    protected void calculateMotion_timeOfTravel_absValue(float thresholdPercentage, float contrastMatch){
        numLocalToAverageForGlobal=chip.NUM_MOTION_PIXELS;
        int ttChannel=1;//use the lmc1 channel for this method.
        int numPastFrame=5;
        float[][] raw=this.extractRawChannel(ttChannel);
        float[][][] past = new float[numPastFrame][chip.NUM_ROWS][chip.NUM_COLUMNS];
        long dt;

        if(thresholdPercentage>0.5) {
            thresholdPercentage=(float)0.5;
            System.out.println("WARNING: The threshold percentage in Time of Travel method is high. Thus a large number of pixel will be analyzed, what possibly decreases reliability");
        }

        //calculate the max and min of the raw data
        float max=0, min=1;
        for(int i=0;i<chip.NUM_ROWS;i++){
            for(int j=0;j<chip.NUM_COLUMNS;j++){
                if (raw[j][i]<min) min=raw[j][i];
                if (raw[j][i]>max) max=raw[j][i];
            }
        }

        try { //to handle nullpointerException which is thrown when less pastMotionData then required are available. This is only the case at startup
            //get past Frames
            for(int i=0; i<numPastFrame; i++){
                past[i]=this.getPastMotionData()[0].extractRawChannel(ttChannel);
            }
            int offset=3;
            float localValue;
            for(int i=0; i<chip.NUM_COLUMNS;i++){
                for(int j=0; j<chip.NUM_ROWS; j++){
                    //at the borders its not possible to calculate motion because one would get ArrayOutOfBoundException. So set it to 0
                    if(i<offset||j<offset||i>=chip.NUM_ROWS-offset||j>=chip.NUM_COLUMNS-offset){
                        ux[j][i]=0;
                        uy[j][i]=0;
                        this.numLocalToAverageForGlobal--;
                    }else{
                        thresholdPercentage=(float)0.1;
                        //look for strong values, either dark or bright
                        localValue=raw[j][i];
                        if(localValue <max-(max-min)*thresholdPercentage && localValue >min+(max-min)*thresholdPercentage){//min*(1+thresholdPercentage )){// && localValue<max*(1-thresholdPercentage){ //if pixel is not in near the brightest and darkest pixel: ignore pixel
                            ux[j][i]=0;
                            uy[j][i]=0;
                            this.numLocalToAverageForGlobal--;
                        } else {
                            ux[j][i]=1;uy[j][i]=1;
                            {//First look for motion in x direction
                                //value is high. look for the same value in x direction. Look only at the next pixel to the left and right but at different frames
                                boolean match=false;
                                int cnt=0;
                                while(!match){
                                    if(Math.abs(past[cnt][j][i+offset] / localValue  -1)  < contrastMatch){ //if vslue at left neighbor at t-1 is within +-contrastMatch% of the contrast at the current pixel at t this is considered as match
                                       dt=this.getTimeCapturedMs()-this.pastMotionData[cnt].getTimeCapturedMs();
                                       if(dt==0){
                                           ux[j][i]=0;
                                       }else{
                                            ux[j][i]=-1/(float)dt; // 1pixel in dt since we only look at one neighboring pixel
                                            System.out.println("ux="+ux[j][i]);
                                       }
                                       match=true;
                                    }
                                    if(Math.abs(past[cnt][j][i-offset] / localValue  -1)  < contrastMatch){ // d the same for the neighbor to the right
                                       dt=this.getTimeCapturedMs()-this.pastMotionData[cnt].getTimeCapturedMs();
                                       if(dt==0){
                                            ux[j][i]=0;
                                       }else{
                                            ux[j][i]=(1/(float)dt+ux[j][i])/2; // 1pixel in dt since we only look at one neighboring pixel
                                            System.out.println("ux="+ux[j][i]);
                                       }
                                       match=true;
                                    }
                                    cnt++;
                                    if(cnt>=numPastFrame-1){
                                        ux[j][i]=0;
                                        break;
                                    }
                                }
                            }

                            {//First look for motion in x direction
                                //value is high. look for the same value in x direction. Look only at the next pixel to the left and right but at different frames
                                boolean match=false;
                                int cnt=0;
                                while(!match){
                                    if(Math.abs(past[cnt][j+offset][i] / localValue  -1)  < contrastMatch){ //if vslue at left neighbor at t-1 is within +-contrastMatch% of the contrast at the current pixel at t this is considered as match
                                       dt=this.getTimeCapturedMs()-this.pastMotionData[cnt].getTimeCapturedMs();
                                       if(dt==0){
                                           uy[j][i]=0;
                                       }else{
                                            uy[j][i]=-1/(float)dt; // 1pixel in dt since we only look at one neighboring pixel
                                            System.out.println("uy="+uy[j][i]);
                                       }
                                       match=true;
                                    }
                                    if(Math.abs(past[cnt][j-offset][i] / localValue  -1)  < contrastMatch){ // d the same for the neighbor to the right
                                       dt=this.getTimeCapturedMs()-this.pastMotionData[cnt].getTimeCapturedMs();
                                       if(dt==0){
                                            uy[j][i]=0;
                                       }else{
                                            uy[j][i]=(1/(float)dt+uy[j][i])/2; // 1pixel in dt since we only look at one neighboring pixel
                                            System.out.println("uy="+uy[j][i]);
                                       }
                                       match=true;
                                    }
                                    cnt++;
                                    if(cnt>=numPastFrame-1){
                                        uy[j][i]=0;
                                        if(ux[j][i]==0) numLocalToAverageForGlobal--;
                                        break;
                                    }
                                }
                            }
                         }
//                    System.out.println("ux="+ux[j][i]); System.out.println("uy="+uy[j][i]);
                    }
                }
            }

        } catch(NullPointerException e) {
            System.out.println(e+" tried to access past data, which doesnt exist yet. Ignore and wait some frames");
        }
//        System.out.println("numValid"+this.numLocalToAverageForGlobal);
    }
    
    //old code delete if non useful
    /**
//    protected void calculateMotion_timeOfTravel(float thresholdContrast, float contrastMatch){
//        numLocalToAverageForGlobal=chip.NUM_MOTION_PIXELS;
//        int ttChannel=1;//use the lmc1 channel for this method.
//        int numPastFrame=5;
//        float[][] raw=this.extractRawChannel(ttChannel);
//        float[][][] past = new float[numPastFrame][chip.NUM_ROWS][chip.NUM_COLUMNS];
//        long dt;
//        
//        //calculate the max and min of the raw data
//        float max=0, min=0;
//        for(int i=0;i<chip.NUM_ROWS;i++){
//            for(int j=0;j<chip.NUM_COLUMNS;j++){
//                if (raw[j][i]<min) min=raw[j][i];
//                if (raw[j][i]>max) max=raw[j][i];
//            }
//        }
//
//        try { //to handle nullpointerException which is thrown when less pastMotionData then required are available. This is only the case at startup
//            //get past Frames
//            for(int i=0; i<numPastFrame; i++){
//                past[i]=this.getPastMotionData()[0].extractRawChannel(ttChannel);
//            }
//            float localContrastX;
//            float localContrastY;
//            for(int i=0; i<chip.NUM_COLUMNS;i++){
//                for(int j=0; j<chip.NUM_ROWS; j++){
//                    //at the borders its not possible to calculate motion because one would get ArrayOutOfBoundException. So set it to 0
//                    if(i==0||j==0||i>chip.NUM_ROWS-3||j>chip.NUM_COLUMNS-3){
//                        ux[j][i]=0;
//                        uy[j][i]=0;
////                        this.numLocalToAverageForGlobal--;
//                    }else{
//                        {//First look for motion in x direction
//                            localContrastX=raw[j][i]/raw[j][i+1];
//                            if(localContrastX>thresholdContrast){ //low contrast: ignore pixel
//                                ux[j][i]=0;
////                                this.numLocalToAverageForGlobal--;
//                            }else{ //contrast is high. look for the same contrast in x direction. Look only at the next pixel to the left and right but at different frames
//                                boolean match=false;
//                                int cnt=0;
//                                while(!match){
//                                    if(Math.abs(past[cnt][j][i+1-1] / localContrastX  -1)<contrastMatch){ //if contrast at neighbor at t-1 is within +-contrastMatch% of the contrast at the current pixel at t this is considered as match
//                                       dt=this.getTimeCapturedMs()-this.pastMotionData[cnt].getTimeCapturedMs();
//                                       if(dt==0){
//                                           ux[j][i]=0;
//                                       }else {
//                                            ux[j][i]=1/dt;
//                                       } // 1pixel in dt since we only look at one neighboring pixel
//                                       match=true;
//                                    } else if(Math.abs(past[cnt][j][i+1+1] / localContrastX  -1)<contrastMatch){
//                                       dt=this.getTimeCapturedMs()-this.pastMotionData[cnt].getTimeCapturedMs();
//                                       if(dt==0){
//                                            ux[j][i]=0;
//                                       }else{
//                                          ux[j][i]=-1/dt; // 1pixel in dt since we only look at one neighboring pixel
//                                       match=true;
//                                       }
//                                    }
//                                    cnt++;
//                                    if(cnt>=numPastFrame-1){
//                                        ux[j][i]=0;
//                                        break;
//                                    }
//                                }
//                            }
//                         }
//                         {//First look for motion in y direction
//                            localContrastY=raw[j][i]/raw[j+1][i];
//                            if(localContrastX<thresholdContrast){ //low contrast: ignore pixel
//                                ux[j][i]=0;
////                                this.numLocalToAverageForGlobal--;
//                            }else{ //contrast is high. look for the same contrast in x direction. Look only at the next pixel to the left and right but at different frames
//                                boolean match=false;
//                                int cnt=0;
//                                while(!match){
//                                    if(Math.abs(past[cnt][j+1-1][i] / localContrastX  -1)<contrastMatch){ //if contrast at neighbor at t-1 is within +-contrastMatch% of the contrast at the current pixel at t this is considered as match
//                                       dt=this.getTimeCapturedMs()-this.pastMotionData[cnt].getTimeCapturedMs();
//                                       if(dt==0){
//                                            uy[j][i]=0;
////                                            if(ux[j][i]==0) this.numLocalToAverageForGlobal--;
//                                       }else {
//                                           uy[j][i] = 1 / dt;// 1pixel in dt since we only look at one neighboring pixel
//                                           match=true;
//                                       } 
//                                    } else if(Math.abs(past[cnt][j+1+1][i] / localContrastX  -1)<contrastMatch){
//                                       dt=this.getTimeCapturedMs()-this.pastMotionData[cnt].getTimeCapturedMs();
//                                       if(dt==0){
//                                            uy[j][i]=0;
////                                            if(ux[j][i]==0) this.numLocalToAverageForGlobal--;
//                                       }else {
//                                           uy[j][i]=-1/dt; // 1pixel in dt since we only look at one neighboring pixel
//                                           match=true;
//                                       }
//                                    }
//                                    cnt++;
//                                    if(cnt==numPastFrame-1){
//                                        uy[j][i]=0;
//                                        break;
//                                    }
//                                }
//                            }
//                         }  System.out.println("ux="+ux[j][i]); System.out.println("uy="+uy[j][i]);
//                    }
//                }
//            }
//
//        } catch(NullPointerException e) {
//            System.out.println(e+" tried to access past data, which doesnt exist yet. Ignore and wait some frames");
//        }
//        System.out.println("numValid"+this.numLocalToAverageForGlobal);
//    }
    
     * 
*/

    private void pixel_setInvalid(int j, int i){
        ux[j][i]=0;
        uy[j][i]=0;
        this.numLocalToAverageForGlobal--;
    }


    /**
     * RANDOM MOTION
     * This generates random values between -1 and 1 for ux and uy.
     * By itself not very interesting, but useful for testing.
     */
    private void calculateMotion_random(){
        float range=(float)0.1;
        //first fill Ux
        setUx(randomizeArray(getUx(),-range,range));
        //now fill Uy
        setUy(randomizeArray(getUy(),-range,range));
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
        if(numLocalToAverageForGlobal!=0){ //check for division by 0 (not very likely to happen, but if it does it clamps the global vector to infinity
            globalUx /= this.numLocalToAverageForGlobal;
            this.setGlobalX(globalUx);
            globalUy /= this.numLocalToAverageForGlobal;
            this.setGlobalY(globalUy);
        } else {
            globalUx=0;
            globalUy=0;
        }
    }

    //temporally average the global motion vector over some frames
    protected void globalUxUy_temporalAverage(int num){
        if(num>this.NUM_PASTMOTIONDATA) num=this.NUM_PASTMOTIONDATA;
        for(int i=0; i<num;i++){
            try{ // at startup therer are not enough frames. catch exception and ignore
                globalX +=pastMotionData[i].getGlobalX();
                globalY +=pastMotionData[i].getGlobalY();
            }catch(NullPointerException e){
                num--;
                System.out.println(e+ ":can be ignored. When more frames captured its ok");
            }
        }
        globalX /=num+1;
        globalY /=num+1;
    }

    //temporally average the local motion 
    protected void localUxUy_temporalAverage(int num){
        if(num>this.NUM_PASTMOTIONDATA) num=this.NUM_PASTMOTIONDATA;
        //add all data from past to present at each point
        for(int i=0; i<num;i++){
            try{
                for(int j=0;j<chip.NUM_COLUMNS;j++){
                    for(int k=0;k<chip.NUM_ROWS;k++){
                        ux[j][k] += pastMotionData[i].getUx()[j][k];
                        uy[j][k] += pastMotionData[i].getUy()[j][k];
                    }
                }
            } catch (NullPointerException e) {
                num--;
                System.out.println(e+ ":can be ignored. When more frames captured its ok");
            }
        }
        //now divide by the number of frames to normalize
        for(int j=0;j<chip.NUM_COLUMNS;j++){
            for(int k=0;k<chip.NUM_ROWS;k++){
                ux[j][k] /= num+1;
                uy[j][k] /= num+1;
            }
        }
    }

    /**
     * Goes through the ux and uy arrays simultaniously. At each pixel it compares
     * the (ux,uy) vector to the 8 neighboring ones. To each of the neighbors the
     * cosine of the angle between the two vectors is computed. This is taken as
     * correlation measure. The cosines from the current pixel to all its neighbors
     * are avereged. If the average is smaller then a given value the motion at this
     * pixel (ux and uy) is considered invalid. The value of the pixel is set to 0.
     */
    protected void localUxUy_filterByCorrelation(double limitCorrelation){
        numLocalToAverageForGlobal=chip.NUM_MOTION_PIXELS; //when averaging the default number of valid pixels is all of them
        
        //copy the array. Necessary because the original one is altered and later computations mustnt be influenced by earlier ones
        //but must be done with the original data
        float[][]copyux=new float[chip.NUM_COLUMNS][chip.NUM_ROWS],copyuy = new float[chip.NUM_COLUMNS][chip.NUM_ROWS];
        for(int i=0;i<chip.NUM_COLUMNS;i++){
            for(int j=0;j<chip.NUM_ROWS;j++){
                copyux[j][i]=ux[j][i];
                copyuy[j][i]=uy[j][i];
            }
        }
        //go through the picture. Compute the cosine (correlation of a pixel and its
        //8 neighbors. average the cosines and if it is below a threshold the motion
        //on that pixel is considered invalid.
        for(int i=0; i< chip.NUM_COLUMNS;i++){
            for(int j=0; j< chip.NUM_ROWS; j++){
                //at the borders comparing 8neigbors is not possible. set invalid
                if(i==0||j==0||i==chip.NUM_COLUMNS-1||j==chip.NUM_ROWS-1){
                    pixel_setInvalid(j,i);
                } else{
                    double u1x, u1y,u2x, u2y;
                    double cos=0;
                    //select surrounding pixel. one after another
                    for(int m=-1;m<2;m++){
                        for(int n=-1;n<2;n++){
                            if(i!=j){//the correlation to itself is 1 and not taken into account
                                u1x=copyux[i][j];
                                u1y=copyuy[i][j];
                                u2x=copyux[i+m][j+n];
                                u2y=copyuy[i+m][j+n];
                                double scalarprod=(u1x*u2x+u1y*u2y);
                                double u1Norm=Math.sqrt(u1x*u1x + u1y*u1y);
                                double u2Norm=Math.sqrt(u2x*u2x + u2y*u2y);
                                if((u1x==0&&u1y==0) || (u2x==0&&u2y==0)) { //check for sqrt(0)
                                    cos+=0;
                                }
                                else cos += scalarprod / (u1Norm * u2Norm); //calculate the cos between the two angles.
                            }
                        }
                    }
                    cos /=  8; // cos has been added for all surrounding pixels. now normalize
                    if(cos<limitCorrelation) {
                        pixel_setInvalid(j,i); //the global average must be computed by dividing by one less because the pixel is not actually 0 but invalid
                    }
                }
            }
        }
    }


    /**
     * Goes through the ux and uy arrays simultaniously. At each pixel it computes
     * average of the pixel itself and its 8 neighbors. The average is the value
     * assigned for the pixel in the center.
     */
    protected void localUxUy_spatialAverage(){
        numLocalToAverageForGlobal=chip.NUM_MOTION_PIXELS; //when averaging the default number of valid pixels is all of them
        
        //copy the array. Necessary because the original one is altered and later computations mustnt be influenced by earlier ones
        //but must be done with the original data
        float[][]copyux=new float[chip.NUM_COLUMNS][chip.NUM_ROWS],copyuy = new float[chip.NUM_COLUMNS][chip.NUM_ROWS];
        for(int i=0;i<chip.NUM_COLUMNS;i++){
            for(int j=0;j<chip.NUM_ROWS;j++){
                copyux[j][i]=ux[j][i];
                copyuy[j][i]=uy[j][i];
            }
        }
        //go through the picture. Compute the average of 3x3 region
        for(int i=0; i< chip.NUM_COLUMNS;i++){
            for(int j=0; j< chip.NUM_ROWS; j++){
                //at the borders comparing 8neigbors is not possible. set invalid
                if(i==0||j==0||i==chip.NUM_COLUMNS-1||j==chip.NUM_ROWS-1){
                    pixel_setInvalid(j,i);
                } else{
                    ux[j][i]=0;
                    for(int v=-1;v<=1;v++){
                        for(int w=-1;w<=1;w++){
                            ux[j][i]+=copyux[j+v][i+w];
                            uy[j][i]+=copyuy[j+v][i+w];
                        }
                    }
                    ux[j][i]/=9;
                    uy[j][i]/=9;
                }
            }
        }
    }




    @Override
    protected void updateContents(){
//        setContents(0x7F);
//        if(true)return;
        // add the calculated contents...
        setContents( chip.getCaptureMode()
                     | MotionData.GLOBAL_X | MotionData.GLOBAL_Y
                     | MotionData.UX | MotionData.UY );
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














