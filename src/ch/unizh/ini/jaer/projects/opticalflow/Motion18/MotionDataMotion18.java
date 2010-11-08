package ch.unizh.ini.jaer.projects.opticalflow.Motion18;

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
public class MotionDataMotion18 extends MotionData {



    /** Bits set in contents show what data has actually be acquired in this buffer.
     @see #GLOBAL_Y
     @see #GLOBAL_X etc
     */
    private int contents=0;
    private static Random r=new Random();

    /** Creates a new instance of MotionData */
    public MotionDataMotion18(Chip2DMotion setchip) {
        super();
    }

    // returns a copy of the Input
    public MotionData getCopy(MotionData data){
        MotionData out=new MotionDataMotion18(new Motion18());
        out.setPastMotionData(data.getPastMotionData());
        out.setRawDataGlobal(data.getRawDataGlobal());
        out.setRawDataPixel(data.getRawDataPixel());
        out.collectMotionInfo();
        return out;
    }


    /* Method override */
    protected void fillPh(){
        this.setPh( extractRawChannel(1));
    }

    protected void fillLocalUxUy(){
        this.setUx( extractRawChannel(2));
        this.setUy( extractRawChannel(3));
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

    /*protected void fillGlobalUx(){
        this.setGlobalX(this.getRawDataGlobal()[1]);
    }
     *
    protected void fillGlobalUy(){
        this.setGlobalX(this.getRawDataGlobal()[2]);
    }*/

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

    protected void fillAdditional(){
        ;
    }
        protected void updateContents(){
        setContents(0x1F); //global X,Y ; localX,Y ; photoreceptor
    }

}






