/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.davis;

import java.awt.Point;

import eu.seebetter.ini.chips.DavisChip;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.event.ApsDvsEvent.ColorFilter;
import net.sf.jaer.hardwareinterface.HardwareInterface;

/**
 * CDAVIS camera with heterogenous mixture of DAVIS and RGB APS global shutter
 * pixels camera
 *
 * @author Chenghan Li, Luca Longinotti, Tobi Delbruck
 */
@Description("CDAVIS APS-DVS camera with RGBW CFA color filter array and 640x480 APS pixels and 320x240 DAVIS pixels")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class DavisRGBW640 extends DavisBaseCamera {

	public static final short WIDTH_PIXELS = 640;
	public static final short HEIGHT_PIXELS = 480;
	public static final ColorFilter[] COLOR_FILTER = { ColorFilter.B, ColorFilter.W, ColorFilter.R, ColorFilter.G };

	public DavisRGBW640() {
		setName("DavisRGBW640");
		setDefaultPreferencesFile("biasgenSettings/DavisRGBW640/DavisRGBW640.xml");
		setSizeX(DavisRGBW640.WIDTH_PIXELS);
		setSizeY(DavisRGBW640.HEIGHT_PIXELS);

		setEventExtractor(new DavisColorEventExtractor(this, true, false, COLOR_FILTER, true));

		setBiasgen(davisConfig = new DavisRGBW640Config(this));

		davisRenderer = new DavisColorRenderer(this, true, COLOR_FILTER);
		davisRenderer.setMaxADC(DavisChip.MAX_ADC);
		setRenderer(davisRenderer);

		setApsFirstPixelReadOut(new Point(0, 0));
		setApsLastPixelReadOut(new Point(getSizeX() - 1, getSizeY() - 1));
	}

	public DavisRGBW640(final HardwareInterface hardwareInterface) {
		this();
		setHardwareInterface(hardwareInterface);
	}
}
