/*
 * created 26 Oct 2008 for new cDVSTest chip
 * adapted apr 2011 for cDVStest30 chip by tobi
 * adapted 25 oct 2011 for SeeBetter10/11 chips by tobi
 */
package eu.seebetter.ini.chips.davis;

import net.sf.jaer.Description;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import eu.seebetter.ini.chips.DavisChip;

/**
 * Base camera for Tower Davis346 cameras
 *
 * @author tobi
 */
@Description("DAVIS346 base class for 346x260 pixel APS-DVS DAVIS sensor")
abstract public class Davis346BaseCamera extends DavisBaseCamera {

    public static final short WIDTH_PIXELS = 346;
    public static final short HEIGHT_PIXELS = 260;
    protected Davis346Config davisConfig;
  /**
     * Field for decoding pixel address and data type, shadows super values to customize the EventExtractor
     */
    public static final int YSHIFT = 22,
            YMASK = 511 << YSHIFT, // 9 bits from bits 22 to 30
            XSHIFT = 12,
            XMASK = 1023 << XSHIFT, // 10 bits from bits 12 to 21
            POLSHIFT = 11,
            POLMASK = 1 << POLSHIFT, //,    // 1 bit at bit 11
            EVENT_TYPE_SHIFT = 10,
            EVENT_TYPE_MASK = 3 << EVENT_TYPE_SHIFT, // these 2 bits encode readout type for APS and other event type (IMU/DVS) for
            EXTERNAL_INPUT_EVENT_ADDR = 1 << EVENT_TYPE_SHIFT, // This special address is is for external pin input events
            IMU_SAMPLE_VALUE = 3 << EVENT_TYPE_SHIFT; // this special code is for IMU sample events
            ; // event code cannot be higher than 7 in 3 bits

    /**
     * Creates a new instance.
     */
    public Davis346BaseCamera() {
        setName("DAVIS346BaseCamera");
        setDefaultPreferencesFile("biasgenSettings/Davis346/Davis346.xml");
        setSizeX(WIDTH_PIXELS);
        setSizeY(HEIGHT_PIXELS);

        setBiasgen(davisConfig = new Davis346Config(this));

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
    public Davis346BaseCamera(final HardwareInterface hardwareInterface) {
        this();
        setHardwareInterface(hardwareInterface);
    }


}
