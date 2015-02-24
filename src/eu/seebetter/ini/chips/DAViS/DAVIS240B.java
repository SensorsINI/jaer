/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.DAViS;

import net.sf.jaer.Description;

/**
 * The DAVIS240C camera.
 *
 * @author Tobi
 */
@Description("The DAVIS240B camera")
public class DAVIS240B extends DAVIS240BaseCamera {

    public DAVIS240B() {
        setName("DAVIS240B");
        setDefaultPreferencesFile("biasgenSettings/Davis240b/Davis240bBasic_GlobalShutter_ImuOn_AutoExposure.xml");
    }

    @Override
    public boolean firstFrameAddress(short x, short y) {
        return (x == sx1) && (y == sy1);
    }

    @Override
    public boolean lastFrameAddress(short x, short y) {
        return (x == 0) && (y == 0); //To change body of generated methods, choose Tools | Templates.
    }
}
