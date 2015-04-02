/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.davis;

import net.sf.jaer.Description;

/**
 * The DAVIS240A camera.
 *
 * @author Tobi
 */
@Description("The DAVIS240A camera")
public class DAVIS240A extends DAVIS240BaseCamera {

	public DAVIS240A() {
		setName("DAVIS240A");
		setDefaultPreferencesFile("biasgenSettings/Davis240a/David240aBasic.xml");
	}
}
