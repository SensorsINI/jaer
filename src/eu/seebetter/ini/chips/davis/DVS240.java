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

@Description("The DVS240 camera")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class DVS240 extends DAVIS240BaseCamera {

	public DVS240() {
		setName("DVS240");
		setDefaultPreferencesFile("biasgenSettings/Davis240bc/dvs240.xml");

		setApsFirstPixelReadOut(new Point(getSizeX() - 1, 0));
		setApsLastPixelReadOut(new Point(0, getSizeY() - 1));
	}

	public DVS240(final HardwareInterface hardwareInterface) {
		this();
		setHardwareInterface(hardwareInterface);
	}
}
