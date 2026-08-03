package net.sf.jaer.eventio.aedat4.dv;

public final class FrameSource {
    public static final byte UNDEFINED = 0;
    public static final byte SENSOR = 1;
    public static final byte ACCUMULATION = 2;
    public static final byte MOTION_COMPENSATION = 3;
    public static final byte SYNTHETIC = 4;
    public static final byte RECONSTRUCTION = 5;
    public static final byte VISUALIZATION = 6;
    public static final byte OTHER = 7;

    private FrameSource() {
    }
}
