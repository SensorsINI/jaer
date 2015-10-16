/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.davis;

import eu.seebetter.ini.chips.DavisChip;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.hardwareinterface.HardwareInterface;

/**
 * Davis640 camera
 *
 * @author tobi
 */
@Description("DAVIS APS-DVS camera with 640x480 pixels")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class Davis640 extends DavisBaseCamera {

	public static final short WIDTH_PIXELS = 640;
	public static final short HEIGHT_PIXELS = 480;
	protected DavisTowerBaseConfig davisConfig;

	/**
	 * Creates a new instance.
	 */
	public Davis640() {
		setName("DAVIS640");
		setDefaultPreferencesFile("biasgenSettings/Davis640/Davis640.xml");
		setSizeX(Davis640.WIDTH_PIXELS);
		setSizeY(Davis640.HEIGHT_PIXELS);

		setBiasgen(davisConfig = new DavisTowerBaseConfig(this));

		apsDVSrenderer = new AEFrameChipRenderer(this); // must be called after configuration is constructed, because it
														// needs to know if frames are enabled to reset pixmap
		apsDVSrenderer.setMaxADC(DavisChip.MAX_ADC);
		setRenderer(apsDVSrenderer);
	}

	/**
	 * Creates a new instance of DAViS240
	 *
	 * @param hardwareInterface
	 *            an existing hardware interface. This constructor
	 *            is preferred. It makes a new cDVSTest10Biasgen object to talk to the
	 *            on-chip biasgen.
	 */
	public Davis640(final HardwareInterface hardwareInterface) {
		this();
		setHardwareInterface(hardwareInterface);
	}
}
