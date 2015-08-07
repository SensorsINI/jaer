/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.davis;

import java.awt.Point;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;

/**
 * The DAVIS240C camera.
 *
 * @author Tobi
 */
@Description("The DAVIS240C camera")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class DAVIS240C extends DAVIS240BaseCamera {
    public DAVIS240C() {
        setName("DAVIS240C");
        setDefaultPreferencesFile("biasgenSettings/Davis240b/Davis240bBasic_GlobalShutter_ImuOn_AutoExposure.xml");
        apsFirstPixelReadOut=new Point(0,getSizeY()-1);
        apsLastPixelReadOut=new Point(getSizeX() - 1,0);
    }

  
}
