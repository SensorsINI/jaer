/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.davis;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;

/**
 * Davis346B camera
 * @author tobi
 */
@Description("DAVIS346 346x260 pixel APS-DVS DAVIS sensor")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class Davis346B extends Davis346BaseCamera {
	public Davis346B() {
		setName("Davis346B");
		setDefaultPreferencesFile("biasgenSettings/Davis346/Davis346.xml");
	}
}
