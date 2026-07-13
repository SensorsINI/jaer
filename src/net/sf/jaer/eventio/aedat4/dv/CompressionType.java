package net.sf.jaer.eventio.aedat4.dv;

public final class CompressionType {
    public static final int NONE = 0;
    public static final int LZ4 = 1;
    public static final int LZ4_HIGH = 2;
    public static final int ZSTD = 3;
    public static final int ZSTD_HIGH = 4;

    private CompressionType() {
    }
}
