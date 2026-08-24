package net.sf.jaer.eventio.aedat4.dv;

import com.google.flatbuffers.Constants;
import com.google.flatbuffers.FlatBufferBuilder;
import com.google.flatbuffers.Table;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Hand-written FlatBuffers helper for dv::EventPacket. */
public final class EventPacket extends Table {

    public static void ValidateVersion() {
        Constants.FLATBUFFERS_23_5_26();
    }

    public static EventPacket getRootAsEventPacket(ByteBuffer _bb) {
        return getRootAsEventPacket(_bb, new EventPacket());
    }

    public static EventPacket getRootAsEventPacket(ByteBuffer _bb, EventPacket obj) {
        _bb.order(ByteOrder.LITTLE_ENDIAN);
        return obj.__assign(_bb.getInt(_bb.position()) + _bb.position(), _bb);
    }

    public static EventPacket getSizePrefixedRootAsEventPacket(ByteBuffer _bb) {
        return getSizePrefixedRootAsEventPacket(_bb, new EventPacket());
    }

    public static EventPacket getSizePrefixedRootAsEventPacket(ByteBuffer _bb, EventPacket obj) {
        _bb.order(ByteOrder.LITTLE_ENDIAN);
        return obj.__assign(_bb.getInt(_bb.position() + Constants.SIZE_PREFIX_LENGTH) + _bb.position() + Constants.SIZE_PREFIX_LENGTH, _bb);
    }

    public void __init(int _i, ByteBuffer _bb) {
        __reset(_i, _bb);
    }

    public EventPacket __assign(int _i, ByteBuffer _bb) {
        __init(_i, _bb);
        return this;
    }

    public Event elements(int j) {
        return elements(new Event(), j);
    }

    public Event elements(Event obj, int j) {
        int o = __offset(4);
        return o != 0 ? obj.__assign(__vector(o) + j * 16, bb) : null;
    }

    public int elementsLength() {
        int o = __offset(4);
        return o != 0 ? __vector_len(o) : 0;
    }

    public static int createEventPacket(FlatBufferBuilder builder, int elementsOffset) {
        builder.startTable(1);
        addElements(builder, elementsOffset);
        return endEventPacket(builder);
    }

    public static void startEventPacket(FlatBufferBuilder builder) {
        builder.startTable(1);
    }

    public static void addElements(FlatBufferBuilder builder, int elementsOffset) {
        builder.addOffset(0, elementsOffset, 0);
    }

    public static int createElementsVector(FlatBufferBuilder builder, long[] timestamps, short[] xs, short[] ys, boolean[] polarities) {
        return createElementsVector(builder, timestamps, xs, ys, polarities, timestamps.length);
    }

    public static int createElementsVector(FlatBufferBuilder builder, long[] timestamps, short[] xs, short[] ys,
            boolean[] polarities, int n) {
        builder.startVector(16, n, 8);
        for (int i = n - 1; i >= 0; i--) {
            Event.createEvent(builder, timestamps[i], xs[i], ys[i], polarities[i]);
        }
        return builder.endVector();
    }

    public static void startElementsVector(FlatBufferBuilder builder, int numElems) {
        builder.startVector(16, numElems, 8);
    }

    public static int endEventPacket(FlatBufferBuilder builder) {
        return builder.endTable();
    }
}
