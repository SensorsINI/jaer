/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.chip;

import eu.seebetter.ini.chips.davis.DAVIS240BaseCamera;
import java.awt.Point;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.hardwareinterface.HardwareInterface;

/**
 *
 * @author minliu
 */
@Description("The specific chip for jaer 3.0 aedat format.")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class JAER3chip extends DAVIS240BaseCamera {
	public JAER3chip() {
		setName("JAER3CHIP");
		setDefaultPreferencesFile("biasgenSettings/Davis240a/David240aBasic.xml");

		setApsFirstPixelReadOut(new Point(getSizeX() - 1, getSizeY() - 1));
		setApsLastPixelReadOut(new Point(0, 0));
	}

	public JAER3chip(final HardwareInterface hardwareInterface) {
		this();
		setHardwareInterface(hardwareInterface);
	}    
}
