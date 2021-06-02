/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.davis;

import java.awt.Point;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.hardwareinterface.HardwareInterface;

/**
 * The DAVIS240B camera.
 *
 * @author Tobi
 */
@Description("The DAVIS240B camera")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class DAVIS240B extends DAVIS240BaseCamera {

	public DAVIS240B() {
		setName("DAVIS240B");
		setDefaultPreferencesFile("biasgenSettings/Davis240b/Davis240bBasic_GlobalShutter_ImuOn_AutoExposure.xml");

		setApsFirstPixelReadOut(new Point(getSizeX() - 1, getSizeY() - 1));
		setApsLastPixelReadOut(new Point(0, 0));
	}

	public DAVIS240B(final HardwareInterface hardwareInterface) {
		this();
		setHardwareInterface(hardwareInterface);
	}
}
