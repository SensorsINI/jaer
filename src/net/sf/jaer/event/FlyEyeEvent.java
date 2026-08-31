/*
 * FlyEyeEvent.java
 */
package net.sf.jaer.event;

/**
 * Polarity event from a FlyEye panoramic pair (two outward-looking DVS128).
 * {@code x} is panoramic chip x; {@link #camera} disambiguates the overlap
 * band.
 */
public class FlyEyeEvent extends PolarityEvent {

    public enum Camera {
        LEFT, RIGHT
    }

    public Camera camera = Camera.LEFT;

    public FlyEyeEvent() {
    }

    @Override
    public String toString() {
        return super.toString() + " camera=" + camera;
    }

    @Override
    public void reset() {
        super.reset();
        camera = Camera.LEFT;
    }

    @Override
    public void copyFrom(final BasicEvent src) {
        super.copyFrom(src);
        if (src instanceof FlyEyeEvent) {
            camera = ((FlyEyeEvent) src).camera;
        }
    }

    @Override
    public int getNumCellTypes() {
        return 4;
    }

    /**
     * LEFT Off=0, LEFT On=1, RIGHT Off=2, RIGHT On=3.
     */
    @Override
    public int getType() {
        int pol = polarity == Polarity.Off ? 0 : 1;
        return camera == Camera.RIGHT ? pol + 2 : pol;
    }
}
