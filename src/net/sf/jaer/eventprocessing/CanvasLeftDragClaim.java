package net.sf.jaer.eventprocessing;

/**
 * Chip-canvas mouse listener that takes a left-button drag ahead of view pan.
 * One-finger touchpad drags and left-button mouse drags pan the 2D image
 * unless a listener returns true from {@link #claimsCanvasLeftDrag()}.
 */
public interface CanvasLeftDragClaim {

    /**
     * @return true when this listener should receive the current left-drag
     * and the chip canvas must not pan
     */
    boolean claimsCanvasLeftDrag();
}
