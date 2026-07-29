package ch.unizh.ini.jaer.chip.nrv;

import net.sf.jaer.chip.AEChip;

/**
 * Compatibility alias for saved viewer preferences and older builds.
 *
 * @deprecated Use {@link nrv.chip.NRVConfig}.
 */
@Deprecated
public class NRVConfig extends nrv.chip.NRVConfig {

    public NRVConfig(AEChip chip) {
        super(chip);
    }
}
