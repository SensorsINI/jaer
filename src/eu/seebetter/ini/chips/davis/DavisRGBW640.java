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
	public static final float[][] COLOR_CORRECTION = { { 1.75f, -0.19f, -0.56f, 0.15f }, { -0.61f, 1.39f, 0.07f, 0.21f },
		{ -0.42f, -1.13f, 2.45f, 0.18f } };

	public DavisRGBW640() {
		setName("DavisRGBW640");
		setDefaultPreferencesFile("biasgenSettings/DavisRGBW640/DavisRGBW640test6.xml");
		setSizeX(DavisRGBW640.WIDTH_PIXELS);
		setSizeY(DavisRGBW640.HEIGHT_PIXELS);
                setPixelHeightUm(10);
                setPixelWidthUm(10); // subpixel size, APS pixel spacing

		setEventExtractor(new DavisColorEventExtractor(this, true, false, DavisRGBW640.COLOR_FILTER, true));

		setBiasgen(davisConfig = new DavisRGBW640Config(this));

		davisRenderer = new DavisColorRenderer(this, true, DavisRGBW640.COLOR_FILTER, true, DavisRGBW640.COLOR_CORRECTION);
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
