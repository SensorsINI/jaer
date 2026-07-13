package net.sf.jaer.eventio.aedat4.dv;

import com.google.flatbuffers.FlatBufferBuilder;
import com.google.flatbuffers.Struct;
import java.nio.ByteBuffer;

/** Hand-written FlatBuffers helper for dv::Event. */
public final class Event extends Struct {

    public void __init(int _i, ByteBuffer _bb) {
        __reset(_i, _bb);
    }

    public Event __assign(int _i, ByteBuffer _bb) {
        __init(_i, _bb);
        return this;
    }

    public long timestamp() {
        return bb.getLong(bb_pos);
    }

    public short x() {
        return bb.getShort(bb_pos + 8);
    }

    public short y() {
        return bb.getShort(bb_pos + 10);
    }

    public boolean polarity() {
        return bb.get(bb_pos + 12) != 0;
    }

    public static int createEvent(FlatBufferBuilder builder, long timestamp, short x, short y, boolean polarity) {
        builder.prep(8, 16);
        builder.pad(3);
        builder.putBoolean(polarity);
        builder.pad(1);
        builder.putShort(y);
        builder.putShort(x);
        builder.putLong(timestamp);
        return builder.offset();
    }
}
