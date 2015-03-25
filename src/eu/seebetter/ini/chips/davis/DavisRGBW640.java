/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.davis;

import eu.seebetter.ini.chips.DavisChip;
import net.sf.jaer.graphics.AEFrameChipRenderer;

/**
 * CDAVIS camera with heterogenous mixture of DAVIS and RGB APS global shutter
 * pixels camera
 *
 * @author tobi
 */
public class DavisRGBW640 extends Davis346BaseCamera {
   public static final short WIDTH_PIXELS = 640;
    public static final short HEIGHT_PIXELS = 480;
    protected DavisRGBW640Config davisConfig;

   /**
     * Field for decoding pixel address and data type, shadows super values to customize the EventExtractor
     */
    public static final int YSHIFT = 22, // TODO fix for 640
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

    public DavisRGBW640() {
        
        setName("DavisRGBW640");
        setDefaultPreferencesFile("biasgenSettings/DavisRGBW640/DavisRGBW640.xml");
        setSizeX(WIDTH_PIXELS);
        setSizeY(HEIGHT_PIXELS);
  
        setBiasgen(davisConfig = new DavisRGBW640Config(this));

        apsDVSrenderer = new DavisRGBW640Renderer(this); // must be called after configuration is constructed, because it needs to know if frames are enabled to reset pixmap
        apsDVSrenderer.setMaxADC(DavisChip.MAX_ADC);
        setRenderer(apsDVSrenderer);
    }

    @Override
    public boolean firstFrameAddress(short x, short y) {
        return (x == getSizeX() - 1) && (y == getSizeY() - 1);
    }

    @Override
    public boolean lastFrameAddress(short x, short y) {
        return (x == 0) && (y == 0); //To change body of generated methods, choose Tools | Templates.
    }

}
