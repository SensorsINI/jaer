/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.davis;

import net.sf.jaer.Description;

/**
 * The DAVIS240C camera.
 *
 * @author Tobi
 */
@Description("The DAVIS240C camera")
public class DAVIS240C extends DAVIS240BaseCamera {
    public DAVIS240C() {
        setName("DAVIS240C");
        setDefaultPreferencesFile("biasgenSettings/Davis240b/Davis240bBasic_GlobalShutter_ImuOn_AutoExposure.xml");
    }

    @Override
    public boolean firstFrameAddress(short x, short y) {
       return (x == 0) && (y == getSizeY()-1); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean lastFrameAddress(short x, short y) {
        return (x == (getSizeX()-1)) && (y == 0);
     }
}
