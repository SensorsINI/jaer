package eu.seebetter.ini.chips.davis;

import java.awt.Point;

import eu.seebetter.ini.chips.DavisChip;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.PolarityEvent.Polarity;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.graphics.DavisRenderer;
import net.sf.jaer.hardwareinterface.HardwareInterface;

@Description("Davis208PixelParade base class for 208x192 pixel sensitive APS-DVS DAVIS sensor")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class Davis208PixelParade extends DavisBaseCamera {

    public static final short WIDTH_PIXELS = 208;
    public static final short HEIGHT_PIXELS = 192;

    public Davis208PixelParade() {
        setName("Davis208PixelParade");
        setDefaultPreferencesFile("biasgenSettings/Davis208PixelParade/Davis208PixelParade.xml");
        setSizeX(Davis208PixelParade.WIDTH_PIXELS);
        setSizeY(Davis208PixelParade.HEIGHT_PIXELS);

        setBiasgen(davisConfig = new Davis208PixelParadeConfig(this));

        davisRenderer = new DavisRenderer(this);
        davisRenderer.setMaxADC(DavisChip.MAX_ADC);
        setRenderer(davisRenderer);

        setEventExtractor(new Davis208EventExtractor(this));

        setApsFirstPixelReadOut(new Point(0, 0));
        setApsLastPixelReadOut(new Point(getSizeX() - 1, getSizeY() - 1));
    }

    public Davis208PixelParade(final HardwareInterface hardwareInterface) {
        this();
        setHardwareInterface(hardwareInterface);
    }

    /**
     * Flips event polarity to be correct (remove this class if fixed again in
     * camera logic)
     *
     */
    public class Davis208EventExtractor extends DavisBaseCamera.DavisEventExtractor {

        public Davis208EventExtractor(DavisBaseCamera chip) {
            super(chip);
        }

        @Override
        public int reconstructRawAddressFromEvent(TypedEvent te) {
            return super.reconstructRawAddressFromEvent(te); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public synchronized EventPacket extractPacket(AEPacketRaw in) {
            EventPacket<PolarityEvent> out = super.extractPacket(in);
            for (PolarityEvent e : out) {
                if (e.polarity == Polarity.Off) {
                    e.polarity = Polarity.On;
                } else if (e.polarity == Polarity.On) {
                    e.polarity = Polarity.Off;
                }
                if (e.type == 1) {
                    e.type = 0;
                } else if (e.type == 0) {
                    e.type = 1;
                }
            }
            return out;
        }

    }
}
