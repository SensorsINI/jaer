package net.sf.jaer.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class TooltipComboBoxModelTest {

    @Test
    public void reloadKeepsSavedValueAndDoesNotNotify() {
        AtomicInteger picks = new AtomicInteger();
        TooltipComboBoxModel model = new TooltipComboBoxModel(
                () -> List.of(new TooltipChoice("/dev/cu.usbmodem1", "Teensy", "tip")),
                value -> picks.incrementAndGet());
        model.reload("/dev/cu.missing");
        assertEquals(2, model.getSize());
        TooltipChoice selected = (TooltipChoice) model.getSelectedItem();
        assertEquals("/dev/cu.missing", selected.value());
        assertTrue(selected.label().contains("not connected"));
        assertEquals(0, picks.get());

        model.reload("/dev/cu.usbmodem1");
        assertEquals("/dev/cu.usbmodem1", ((TooltipChoice) model.getSelectedItem()).value());
        assertEquals(1, model.getSize());
        assertEquals(0, picks.get());
    }

    @Test
    public void userSelectionNotifiesOnce() {
        AtomicInteger picks = new AtomicInteger();
        StringBuilder chosen = new StringBuilder();
        TooltipChoice teensy = new TooltipChoice("COM5", "Teensy", "<html>COM5</html>");
        TooltipChoice other = new TooltipChoice("COM3", "Other", "COM3");
        TooltipComboBoxModel model = new TooltipComboBoxModel(
                () -> List.of(teensy, other),
                value -> {
                    picks.incrementAndGet();
                    chosen.setLength(0);
                    chosen.append(value);
                });
        model.reload("COM3");
        assertEquals(0, picks.get());
        model.setSelectedItem(teensy);
        assertEquals(1, picks.get());
        assertEquals("COM5", chosen.toString());
        model.setSelectedItem(teensy);
        assertEquals(1, picks.get());
    }

    @Test
    public void blankKeepValueSelectsNothing() {
        TooltipComboBoxModel model = new TooltipComboBoxModel(List::of, value -> {
        });
        model.reload("", List.of(new TooltipChoice("COM4", "COM4", "COM4")));
        assertNull(model.getSelectedItem());
        assertEquals(1, model.getSize());
    }
}
