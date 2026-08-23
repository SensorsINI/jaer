package ch.unizh.ini.jaer.chip.retina;

/**
 * Deprecated alias for {@link DVSUserControlPanel} so DVS128 / Cochlea combo
 * call sites keep compiling.
 *
 * @deprecated use {@link DVSUserControlPanel}
 */
@Deprecated
public class DVSFunctionalControlPanel extends DVSUserControlPanel {

    public DVSFunctionalControlPanel(AETemporalConstastRetina chip) {
        super(chip);
    }
}
