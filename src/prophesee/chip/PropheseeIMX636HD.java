package prophesee.chip;

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
import net.sf.jaer.util.VendorPrefsMigration;
import prophesee.usb.evt3.Evt3Parser;
import ch.unizh.ini.jaer.chip.EventOnlyChipDisplay;
import ch.unizh.ini.jaer.chip.retina.AETemporalConstastRetina;

/**
 * Prophesee EVK4 HD with Sony IMX636 (1280x720) over Cypress USB3, EVT3 events.
 *
 * @see https://www.prophesee.ai/
 */
@Description("Prophesee EVK4 HD IMX636 1280x720")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class PropheseeIMX636HD extends AETemporalConstastRetina implements Serializable {

    @Override
    public String prefsNodeNameOriginal() {
        final String legacy = VendorPrefsMigration.legacyChipPrefsPackage(
                VendorPrefsMigration.LEGACY_PROPHESEE_CHIP_PACKAGE);
        return legacy != null ? legacy : super.prefsNodeNameOriginal();
    }

    public PropheseeIMX636HD() {
        setName("PropheseeIMX636HD");
        setDefaultPreferencesFile("biasgenSettings/PropheseeIMX636HD/PropheseeIMX636HD_default.xml");
        setSizeX(Evt3Parser.WIDTH);
        setSizeY(Evt3Parser.HEIGHT);
        setNumCellTypes(2);
        setEventExtractor(new Extractor(this));
        setBiasgen(new PropheseeConfig(this));

        if (getBiasgen() instanceof PropheseeConfig config && !config.isInitialized()) {
            maybeLoadDefaultPreferences();
            config.loadPreferences();
        }
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

    public PropheseeIMX636HD(HardwareInterface hardwareInterface) {
        this();
        setHardwareInterface(hardwareInterface);
    }

    public class Extractor extends RetinaExtractor {

        private static final int XMASK = 0x7FF;
        private static final int YMASK = 0x7FF << 11;
        private static final int TYPEMASK = 1 << 22;

        public Extractor(AEChip chip) {
            super(chip);
            setXmask(XMASK);
            setXshift((byte) 0);
            setYmask(YMASK);
            setYshift((byte) 11);
            setTypemask(TYPEMASK);
            setTypeshift((byte) 22);
            setFlipx(false);
            setFlipy(true);
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
