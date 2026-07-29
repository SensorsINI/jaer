package ch.unizh.ini.jaer.chip.nrv;

import net.sf.jaer.hardwareinterface.HardwareInterface;

/**
 * Compatibility alias for saved viewer preferences and older builds.
 *
 * @deprecated Use {@link nrv.chip.NRVS5KRC1S}.
 */
@Deprecated
public class NRVS5KRC1S extends nrv.chip.NRVS5KRC1S {

    public NRVS5KRC1S() {
        super();
    }

    public NRVS5KRC1S(HardwareInterface hardwareInterface) {
        super(hardwareInterface);
    }
}
