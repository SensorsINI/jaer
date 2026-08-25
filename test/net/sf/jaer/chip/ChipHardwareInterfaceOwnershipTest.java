package net.sf.jaer.chip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Tests that chip and bias-generator hardware ownership cannot diverge. */
public class ChipHardwareInterfaceOwnershipTest {

    public static junit.framework.Test suite() {
        return new junit.framework.JUnit4TestAdapter(
                ChipHardwareInterfaceOwnershipTest.class);
    }

    private Chip chip;
    private Biasgen biasgen;

    @Before
    public void setUp() {
        chip = new Chip();
        biasgen = new Biasgen(chip);
        chip.setBiasgen(biasgen);
    }

    @After
    public void tearDown() {
        chip.setHardwareInterface(null);
        if (chip.getRemoteControl() != null) {
            chip.getRemoteControl().close();
        }
    }

    @Test
    public void nullReplacementDetachesWithoutClosingPreviousInterface()
            throws Exception {
        final FakeBiasInterface first = new FakeBiasInterface();
        chip.setHardwareInterface(first);
        assertSame(first, biasgen.getHardwareInterface());
        assertEquals(1, first.sendCount);

        chip.setHardwareInterface(null);
        assertNull(biasgen.getHardwareInterface());
        biasgen.sendConfiguration(biasgen);
        biasgen.close();

        assertEquals(1, first.sendCount);
        assertEquals(0, first.closeCount);
    }

    @Test
    public void nonBiasReplacementDetachesPreviousBiasInterface()
            throws Exception {
        final FakeBiasInterface first = new FakeBiasInterface();
        final FakeHardwareInterface replacement = new FakeHardwareInterface();
        chip.setHardwareInterface(first);

        chip.setHardwareInterface(replacement);
        assertSame(replacement, chip.getHardwareInterface());
        assertNull(biasgen.getHardwareInterface());
        biasgen.sendConfiguration(biasgen);
        biasgen.close();

        assertEquals(1, first.sendCount);
        assertEquals(0, first.closeCount);
        assertEquals(0, replacement.closeCount);
    }

    @Test
    public void biasReplacementRemainsCurrentWhenInitialSendFails()
            throws Exception {
        final FakeBiasInterface first = new FakeBiasInterface();
        final FakeBiasInterface replacement = new FakeBiasInterface();
        replacement.failSend = true;
        chip.setHardwareInterface(first);

        chip.setHardwareInterface(replacement);
        assertSame(replacement, chip.getHardwareInterface());
        assertSame(replacement, biasgen.getHardwareInterface());
        assertEquals(1, replacement.sendCount);

        try {
            biasgen.sendConfiguration(biasgen);
        } catch (final HardwareInterfaceException expected) {
            // The current replacement owns this failure.
        }
        biasgen.close();

        assertEquals(1, first.sendCount);
        assertEquals(0, first.closeCount);
        assertEquals(2, replacement.sendCount);
        assertEquals(1, replacement.closeCount);
    }

    @Test
    public void constructingBiasgenWithNonBiasHardwareStartsDetached() {
        if (chip.getRemoteControl() != null) {
            chip.getRemoteControl().close();
        }
        final Chip nonBiasChip = new Chip();
        try {
            nonBiasChip.setHardwareInterface(new FakeHardwareInterface());
            final Biasgen constructed = new Biasgen(nonBiasChip);
            assertNull(constructed.getHardwareInterface());
        } finally {
            if (nonBiasChip.getRemoteControl() != null) {
                nonBiasChip.getRemoteControl().close();
            }
        }
    }

    private static class FakeHardwareInterface implements HardwareInterface {

        int closeCount;
        boolean open = true;

        @Override
        public String getTypeName() {
            return "fake";
        }

        @Override
        public void close() {
            closeCount++;
            open = false;
        }

        @Override
        public void open() {
            open = true;
        }

        @Override
        public boolean isOpen() {
            return open;
        }
    }

    private static final class FakeBiasInterface extends FakeHardwareInterface
            implements BiasgenHardwareInterface {

        int sendCount;
        boolean failSend;

        @Override
        public void setPowerDown(final boolean powerDown) {
        }

        @Override
        public void sendConfiguration(final Biasgen source)
                throws HardwareInterfaceException {
            sendCount++;
            if (failSend) {
                throw new HardwareInterfaceException("simulated configuration failure");
            }
        }

        @Override
        public void flashConfiguration(final Biasgen source) {
        }

        @Override
        public byte[] formatConfigurationBytes(final Biasgen source) {
            return new byte[0];
        }
    }
}
