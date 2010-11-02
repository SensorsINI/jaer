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

  

    public static final int PHOTO=0x01, LMC1=0x02, LMC2=0x04;


    /** Bits set in contents show what data has actually be acquired in this buffer.
     @see #GLOBAL_Y
     @see #GLOBAL_X etc
     */
    private int contents=0;
    private static Random r=new Random();

    /** Creates a new instance of MotionData */
    public MotionDataMDC2D(Chip2DMotion setchip) {
        chip=setchip;
        setGlobalX(0); setGlobalY(0);
        setPh(new float[chip.getSizeX()][chip.getSizeY()]);
        setUx(new float[chip.getSizeX()][chip.getSizeY()]);
        setUy(new float[chip.getSizeX()][chip.getSizeY()]);
        // debug
//        randomizeArray(ph,0,1);
//        randomizeArray(ux,-1,1);
//        randomizeArray(uy,-1,1);
//        globalX=-1+2*r.nextFloat();
//        globalY=-1+2*r.nextFloat();
    }


}





