package net.sf.jaer.eventio.aedat4;

import org.junit.Test;

/** Numeric check for packet-activity clock correlation. No recording file. */
public class MulticameraClockDriftTest {

    @Test
    public void selfCheckRecoversOffsetAndScale() {
        MulticameraClockDrift.selfCheck();
    }
}
