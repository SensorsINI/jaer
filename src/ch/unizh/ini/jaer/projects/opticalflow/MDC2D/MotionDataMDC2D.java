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

import ch.unizh.ini.jaer.projects.opticalflow.*;
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
    private MotionData out=new MotionDataMDC2D(chip);

    /** Creates a new instance of MotionData */
    public MotionDataMDC2D(Chip2DMotion setchip) {
        super(setchip);
        lmc1=new float[chip.getSizeX()][chip.getSizeY()];
        lmc2=new float[chip.getSizeX()][chip.getSizeY()];
        NUM_PASTMOTIONDATA=2;
    }


    /* Method override */
    protected void fillPh(){
        this.setPh( extractRawChannel(0)); //0 is the index of the first row
    }

    protected void fillLocalUxUy(){
        MotionMethod motionMethod=null;
        int algorithm=1;
        switch(algorithm){
            case 1:   motionMethod = new RandomMotion(this);
            break;
        }

        this.setUx(motionMethod.calculateMotion().getUx());
        this.setUy(motionMethod.calculateMotion().getUy());

    }


    protected void fillGlobalUxUy(){
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
                channelData[x][maxY-1-y]=this.rawDataPixel[channelNumber][x][y];
            }
        }
        return channelData;
    }



    abstract class MotionMethod{
        protected MotionData motionData;

        MotionMethod(MotionData motionData){
            this.motionData = motionData;
        }
        abstract MotionData calculateMotion();
    }

    class RandomMotion extends MotionMethod{

        RandomMotion(MotionData motionData){
            super(motionData);
        }

        MotionData calculateMotion(){
            //first fill Ux
            motionData.setUx(randomizeArray(motionData.getUx(),-1,1));
            //now fill Uy
            motionData.setUy(randomizeArray(motionData.getUy(),-1,1));
            return motionData;
        }

    }
}









