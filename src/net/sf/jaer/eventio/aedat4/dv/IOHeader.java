package net.sf.jaer.eventio.aedat4.dv;

import com.google.flatbuffers.Constants;
import com.google.flatbuffers.FlatBufferBuilder;
import com.google.flatbuffers.Table;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Hand-written FlatBuffers helper for dv::IOHeader. */
public final class IOHeader extends Table {

    public static void ValidateVersion() {
        Constants.FLATBUFFERS_23_5_26();
    }

    public static IOHeader getSizePrefixedRootAsIOHeader(ByteBuffer _bb) {
        return getSizePrefixedRootAsIOHeader(_bb, new IOHeader());
    }

    public static IOHeader getSizePrefixedRootAsIOHeader(ByteBuffer _bb, IOHeader obj) {
        _bb.order(ByteOrder.LITTLE_ENDIAN);
        return obj.__assign(_bb.getInt(_bb.position() + Constants.SIZE_PREFIX_LENGTH) + _bb.position() + Constants.SIZE_PREFIX_LENGTH, _bb);
    }

    public void __init(int _i, ByteBuffer _bb) {
        __reset(_i, _bb);
    }

    public IOHeader __assign(int _i, ByteBuffer _bb) {
        __init(_i, _bb);
        return this;
    }

    public int compression() { int o = __offset(4); return o != 0 ? bb.getInt(o + bb_pos) : CompressionType.NONE; }
    public long dataTablePosition() { int o = __offset(6); return o != 0 ? bb.getLong(o + bb_pos) : -1; }
    public String infoNode() { int o = __offset(8); return o != 0 ? __string(o + bb_pos) : null; }

    public static int createIOHeader(FlatBufferBuilder builder, int compression, long dataTablePosition, int infoNodeOffset) {
        builder.startTable(3);
        addDataTablePosition(builder, dataTablePosition);
        addInfoNode(builder, infoNodeOffset);
        addCompression(builder, compression);
        return endIOHeader(builder);
    }

    public static void addCompression(FlatBufferBuilder builder, int compression) {
        builder.addInt(0, compression, CompressionType.NONE);
    }

    public static void addDataTablePosition(FlatBufferBuilder builder, long dataTablePosition) {
        builder.addLong(1, dataTablePosition, -1);
    }

    public static void addInfoNode(FlatBufferBuilder builder, int infoNodeOffset) {
        builder.addOffset(2, infoNodeOffset, 0);
    }

    public static int endIOHeader(FlatBufferBuilder builder) {
        return builder.endTable();
    }
}
