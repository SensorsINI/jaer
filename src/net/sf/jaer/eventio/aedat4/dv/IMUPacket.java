package net.sf.jaer.eventio.aedat4.dv;

import com.google.flatbuffers.Constants;
import com.google.flatbuffers.FlatBufferBuilder;
import com.google.flatbuffers.Table;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Hand-written FlatBuffers helper for dv::IMUPacket. */
public final class IMUPacket extends Table {

    public static void ValidateVersion() {
        Constants.FLATBUFFERS_23_5_26();
    }

    public static IMUPacket getSizePrefixedRootAsIMUPacket(ByteBuffer _bb) {
        return getSizePrefixedRootAsIMUPacket(_bb, new IMUPacket());
    }

    public static IMUPacket getSizePrefixedRootAsIMUPacket(ByteBuffer _bb, IMUPacket obj) {
        _bb.order(ByteOrder.LITTLE_ENDIAN);
        return obj.__assign(_bb.getInt(_bb.position() + Constants.SIZE_PREFIX_LENGTH) + _bb.position() + Constants.SIZE_PREFIX_LENGTH, _bb);
    }

    public void __init(int _i, ByteBuffer _bb) {
        __reset(_i, _bb);
    }

    public IMUPacket __assign(int _i, ByteBuffer _bb) {
        __init(_i, _bb);
        return this;
    }

    public IMU elements(int j) {
        return elements(new IMU(), j);
    }

    public IMU elements(IMU obj, int j) {
        int o = __offset(4);
        return o != 0 ? obj.__assign(__indirect(__vector(o) + j * 4), bb) : null;
    }

    public int elementsLength() {
        int o = __offset(4);
        return o != 0 ? __vector_len(o) : 0;
    }

    public static int createIMUPacket(FlatBufferBuilder builder, int elementsOffset) {
        builder.startTable(1);
        addElements(builder, elementsOffset);
        return endIMUPacket(builder);
    }

    public static void addElements(FlatBufferBuilder builder, int offset) { builder.addOffset(0, offset, 0); }
    public static int createElementsVector(FlatBufferBuilder builder, int[] data) {
        builder.startVector(4, data.length, 4);
        for (int i = data.length - 1; i >= 0; i--) {
            builder.addOffset(data[i]);
        }
        return builder.endVector();
    }
    public static void startElementsVector(FlatBufferBuilder builder, int numElems) { builder.startVector(4, numElems, 4); }
    public static int endIMUPacket(FlatBufferBuilder builder) { return builder.endTable(); }
}
