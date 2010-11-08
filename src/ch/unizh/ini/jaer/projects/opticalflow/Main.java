/*
 * Main.java
 *
 * Created on December 7, 2006, 8:55 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright December 7, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package ch.unizh.ini.jaer.projects.opticalflow;

import ch.unizh.ini.jaer.projects.opticalflow.MDC2D.*;
import ch.unizh.ini.jaer.projects.opticalflow.Motion18.*;
import ch.unizh.ini.jaer.projects.opticalflow.graphics.*;
import java.util.logging.Logger;

/**
 * The starter for the optical flow chip demo.q
 
 * @author  tobi
 */
public class Main{
    
    static final Logger log=Logger.getLogger("Main");
    public static void main(String[] args){
        
         //Motion18 chip=new Motion18();
        MDC2D chip=new MDC2D();
        MotionViewer viewer=new MotionViewer(chip);
        //ChannelViewer viewer=new ChannelViewer(chip);
        viewer.setVisible(true);
//        if(OpticalFlowHardwareInterfaceFactory.instance().getNumInterfacesAvailable()==0){
//            log.warning("no interfaces available, quitting");
//        }
//        try{
//            SiLabsC8051F320_OpticalFlowHardwareInterface siLabsIF = (SiLabsC8051F320_OpticalFlowHardwareInterface)OpticalFlowHardwareInterfaceFactory.instance().getFirstAvailableInterface();
//            chip.getBiasgen().setHardwareInterface(siLabsIF);
//            siLabsIF.open();
//        }catch(HardwareInterfaceException e){
//            e.printStackTrace();
//        }
    }
}

