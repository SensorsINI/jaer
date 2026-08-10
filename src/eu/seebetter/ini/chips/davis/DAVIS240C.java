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
 * The DAVIS240C camera.
 *
 * @author Tobi
 */
@Description("The DAVIS240C camera, 240x180, 18.5um pitch, the original DAVIS camera published 2014 in JSSC")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class DAVIS240C extends DAVIS240BaseCamera {

	public DAVIS240C() {
		setName("DAVIS240C");
		setDefaultPreferencesFileForFamily("Davis240");

		setApsFirstPixelReadOut(new Point(0, getSizeY() - 1));
		setApsLastPixelReadOut(new Point(getSizeX() - 1, 0));
	}

	public DAVIS240C(final HardwareInterface hardwareInterface) {
		this();
		setHardwareInterface(hardwareInterface);
	}
}
