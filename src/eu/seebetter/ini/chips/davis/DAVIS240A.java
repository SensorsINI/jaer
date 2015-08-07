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
 * The DAVIS240A camera.
 *
 * @author Tobi
 */
@Description("The DAVIS240A camera")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class DAVIS240A extends DAVIS240BaseCamera {

    public DAVIS240A() {
        setName("DAVIS240A");
        setDefaultPreferencesFile("biasgenSettings/Davis240a/David240aBasic.xml");
        apsFirstPixelReadOut = new Point(getSizeX() - 1, getSizeY() - 1);
        apsLastPixelReadOut = new Point(0, 0);
    }
}
