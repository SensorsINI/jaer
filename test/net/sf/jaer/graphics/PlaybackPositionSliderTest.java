package net.sf.jaer.graphics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

import javax.swing.SwingUtilities;

import org.junit.Test;

/**
 * macOS Aqua JSlider thumb is 25px wide and drag breaks if TrackListener is
 * only re-registered as a MouseListener. PlaybackPositionSlider must stay on
 * a compact BasicSliderUI whose motion listener can scrub.
 */
public class PlaybackPositionSliderTest {

    @Test
    public void uiIsNotAquaAndThumbIsNarrow() {
        PlaybackPositionSlider slider = newSlider();
        assertTrue("must not use AquaSliderUI",
                !(slider.getUI().getClass().getName().contains("Aqua")));
        assertTrue(slider.getUI() instanceof PlaybackPositionSlider.PlaybackSliderUI);
        Rectangle thumb = ((PlaybackPositionSlider.PlaybackSliderUI) slider.getUI()).thumbRectSnapshot();
        assertEquals(PlaybackPositionSlider.PlaybackSliderUI.THUMB_WIDTH, thumb.width);
        assertTrue("thumb must stay compact (Aqua round thumb is 25px)", thumb.width <= 16);
    }

    @Test
    public void updateUIDoesNotReinstallAqua() {
        PlaybackPositionSlider slider = newSlider();
        slider.updateUI();
        assertTrue(slider.getUI() instanceof PlaybackPositionSlider.PlaybackSliderUI);
    }

    @Test
    public void trackListenerReceivesMouseDragged() {
        PlaybackPositionSlider slider = newSlider();
        PlaybackPositionSlider.PlaybackSliderUI ui =
                (PlaybackPositionSlider.PlaybackSliderUI) slider.getUI();
        assertTrue("TrackListener must be a MouseMotionListener so drag works",
                ui.motionListenerIsTrackListener());
    }

    @Test
    public void pressJumpsAndDragScrubs() throws Exception {
        PlaybackPositionSlider slider = newSlider();
        slider.setValue(0);
        paint(slider);

        SwingUtilities.invokeAndWait(() -> {
            slider.dispatchEvent(press(slider, 300, 12));
        });
        int afterPress = slider.getValue();
        assertTrue("click should jump along the track, was " + afterPress, afterPress > 200);

        SwingUtilities.invokeAndWait(() -> {
            slider.dispatchEvent(drag(slider, 480, 12));
        });
        int afterDrag = slider.getValue();
        assertTrue("drag should continue seeking, press=" + afterPress + " drag=" + afterDrag,
                afterDrag > afterPress);
    }

    private static PlaybackPositionSlider newSlider() {
        PlaybackPositionSlider slider = new PlaybackPositionSlider();
        slider.setMaximum(1000);
        slider.setValue(0);
        slider.setBounds(0, 0, 600, 25);
        slider.doLayout();
        return slider;
    }

    private static void paint(PlaybackPositionSlider slider) {
        BufferedImage img = new BufferedImage(600, 25, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            slider.paint(g);
        } finally {
            g.dispose();
        }
    }

    private static MouseEvent press(PlaybackPositionSlider slider, int x, int y) {
        return new MouseEvent(slider, MouseEvent.MOUSE_PRESSED, 0L,
                MouseEvent.BUTTON1_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON1);
    }

    private static MouseEvent drag(PlaybackPositionSlider slider, int x, int y) {
        return new MouseEvent(slider, MouseEvent.MOUSE_DRAGGED, 0L,
                MouseEvent.BUTTON1_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON1);
    }
}
