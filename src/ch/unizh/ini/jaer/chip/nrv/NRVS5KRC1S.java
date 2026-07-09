package ch.unizh.ini.jaer.chip.nrv;

import java.io.Serializable;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.RetinaExtractor;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.graphics.ChipRendererDisplayMethod;
import net.sf.jaer.graphics.ChipRendererDisplayMethodRGBA;
import net.sf.jaer.graphics.DisplayMethod;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.usb.nrv.S5KRC1SParser;
import ch.unizh.ini.jaer.chip.EventOnlyChipDisplay;
import ch.unizh.ini.jaer.chip.retina.AETemporalConstastRetina;

/**
 * NRV S5KRC1S DVS sensor (960x720) over Cypress USB3.
 */
@Description("NRV S5KRC1S 960x720 DVS camera")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class NRVS5KRC1S extends AETemporalConstastRetina implements Serializable {

    public NRVS5KRC1S() {
        setName("NRVS5KRC1S");
        setSizeX(S5KRC1SParser.WIDTH);
        setSizeY(S5KRC1SParser.HEIGHT);
        setNumCellTypes(2);
        setEventExtractor(new Extractor(this));
        setBiasgen(new NRVConfig(this));
        getRenderer().ensurePixmapReadyForDisplay();

        EventOnlyChipDisplay.apply(this);
    }

    @Override
    public void onRegistration() {
        super.onRegistration();
        EventOnlyChipDisplay.apply(this);
    }

    @Override
    public DisplayMethod getPreferredDisplayMethod() {
        EventOnlyChipDisplay.clearRgbaPreference(this);
        return new ChipRendererDisplayMethod(getCanvas());
    }

    @Override
    public void setPreferredDisplayMethod(Class<? extends DisplayMethod> clazz) {
        if (clazz == null || ChipRendererDisplayMethodRGBA.class.isAssignableFrom(clazz)) {
            EventOnlyChipDisplay.clearRgbaPreference(this);
            return;
        }
        if (!ChipRendererDisplayMethod.class.isAssignableFrom(clazz)) {
            return;
        }
        super.setPreferredDisplayMethod(clazz);
    }

    public NRVS5KRC1S(HardwareInterface hardwareInterface) {
        this();
        setHardwareInterface(hardwareInterface);
    }

    public class Extractor extends RetinaExtractor {

        private static final int XMASK = 0x3FF;
        private static final int YMASK = 0x3FF << 10;
        private static final int TYPEMASK = 1 << 20;

        public Extractor(AEChip chip) {
            super(chip);
            setXmask(XMASK);
            setXshift((byte) 0);
            setYmask(YMASK);
            setYshift((byte) 10);
            setTypemask(TYPEMASK);
            setTypeshift((byte) 20);
            setFlipx(false);
            setFlipy(true); // sensor Y increases opposite to jAER canvas
            setFliptype(false);
        }

        @Override
        public int reconstructRawAddressFromEvent(TypedEvent e) {
            return reconstructDefaultRawAddressFromEvent(e);
        }

        @Override
        synchronized public EventPacket extractPacket(AEPacketRaw in) {
            return super.extractPacket(in);
        }
    }
}
