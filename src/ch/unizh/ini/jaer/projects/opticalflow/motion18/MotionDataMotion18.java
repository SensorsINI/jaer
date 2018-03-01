package ch.unizh.ini.jaer.projects.opticalflow.motion18;

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

import ch.unizh.ini.jaer.projects.opticalflow.Chip2DMotion;
import ch.unizh.ini.jaer.projects.opticalflow.MotionData;

/**
 * Packs data returned from optical flow sensor.
 Values are floats that which are normalized as follows: all values are returned in range 0-1 float. Motion signals are centered around 0.5f.
 Global outputs are not - they are just from corner of chip.

 * @author tobi
 */
public class MotionDataMotion18 extends MotionData {



    /** Bits set in contents show what data has actually be acquired in this buffer.
     @see #GLOBAL_Y
     @see #GLOBAL_X etc
     */
    private int contents=0;

    /** Creates a new instance of MotionData */
    public MotionDataMotion18(Chip2DMotion setchip) {
        super(setchip);
    }



    /* Method override */
    @Override
    protected void fillPh(){
        this.setPh( extractRawChannel(1)); //ph is the first channel from the chip
    }

    @Override
    protected void fillUxUy(){
        //first local channels: just copy from raw data
        this.setUx( extractRawChannel(2));
        this.setUy( extractRawChannel(3));
        // global is the average of the local motion vectors
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

        /*global could also be from chip output
            this.setGlobalX(this.getRawDataGlobal()[1]);
            this.setGlobalX(this.getRawDataGlobal()[2]);
         */
    }


    @Override
    protected void fillMinMax(){
        float minPh=0, maxPh=0, minUx=0, maxUx=0, minUy=0, maxUy =0;
        float[][] ux = getUx();
        float[][] uy = getUy();
        float[][] ph = getPh();
        for(int i=0;i<chip.NUM_COLUMNS;i++){
            for(int j=0;j<chip.NUM_ROWS;j++){
                if (ux[i][j]<minUx)  minUx=ux[i][j];
                if (ux[i][j]>maxUx)  maxUx=ux[i][j];
                if (uy[i][j]<minUy)  minUy=uy[i][j];
                if (uy[i][j]>maxUy)  maxUx=uy[i][j];
                if (ph[i][j]<minPh)  minPh=ph[i][j];
                if (ph[i][j]>maxPh)  maxPh=ph[i][j];
            }
        }

    }

    @Override
    protected void fillAdditional(){
        ; //nothing to do in this class
    }
    @Override
        protected void updateContents(){
        setContents(0x1F); //global X,Y ; localX,Y ; photoreceptor
    }

}






