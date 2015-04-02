package eu.seebetter.ini.chips.davis;

import net.sf.jaer.Description;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import eu.seebetter.ini.chips.DavisChip;

/**
 * Base camera for Tower Davis208PixelParade cameras
 *
 * @author Diederik
 */
@Description("Davis208PixelParade base class for 208x192 pixel sensitive APS-DVS DAVIS sensor")
public class Davis208PixelParade extends DavisBaseCamera {

    public static final short WIDTH_PIXELS = 208;
    public static final short HEIGHT_PIXELS = 192;
    protected Davis208PixelParadeConfig davisConfig;

    /**
     * Creates a new instance.
     */
    public Davis208PixelParade() {
        setName("Davis208PixelParade");
        setDefaultPreferencesFile("biasgenSettings/Davis208PixelParade/Davis208PixelParade.xml");
        setSizeX(WIDTH_PIXELS);
        setSizeY(HEIGHT_PIXELS);
        setBiasgen(davisConfig = new Davis208PixelParadeConfig(this));

        apsDVSrenderer = new AEFrameChipRenderer(this); // must be called after configuration is constructed, because it needs to know if frames are enabled to reset pixmap
        apsDVSrenderer.setMaxADC(DavisChip.MAX_ADC);
        setRenderer(apsDVSrenderer);
    }

    /**
     * Creates a new instance
     *
     * @param hardwareInterface an existing hardware interface. This constructor
     * is preferred. It makes a new cDVSTest10Biasgen object to talk to the
     * on-chip biasgen.
     */
    public Davis208PixelParade(final HardwareInterface hardwareInterface) {
        this();
        setHardwareInterface(hardwareInterface);
    }
}
