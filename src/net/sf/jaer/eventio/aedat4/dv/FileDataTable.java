package net.sf.jaer.eventio.aedat4.dv;

import com.google.flatbuffers.Constants;
import com.google.flatbuffers.FlatBufferBuilder;
import com.google.flatbuffers.Table;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Hand-written FlatBuffers helper for dv::FileDataTable. */
public final class FileDataTable extends Table {

    public static void ValidateVersion() {
        Constants.FLATBUFFERS_23_5_26();
    }

    public static FileDataTable getSizePrefixedRootAsFileDataTable(ByteBuffer _bb) {
        return getSizePrefixedRootAsFileDataTable(_bb, new FileDataTable());
    }

    public static FileDataTable getSizePrefixedRootAsFileDataTable(ByteBuffer _bb, FileDataTable obj) {
        _bb.order(ByteOrder.LITTLE_ENDIAN);
        return obj.__assign(_bb.getInt(_bb.position() + Constants.SIZE_PREFIX_LENGTH) + _bb.position() + Constants.SIZE_PREFIX_LENGTH, _bb);
    }

    public void __init(int _i, ByteBuffer _bb) {
        __reset(_i, _bb);
    }

    public FileDataTable __assign(int _i, ByteBuffer _bb) {
        __init(_i, _bb);
        return this;
    }

    public FileDataDefinition table(int j) {
        return table(new FileDataDefinition(), j);
    }

    public FileDataDefinition table(FileDataDefinition obj, int j) {
        int o = __offset(4);
        return o != 0 ? obj.__assign(__indirect(__vector(o) + j * 4), bb) : null;
    }

    public int tableLength() {
        int o = __offset(4);
        return o != 0 ? __vector_len(o) : 0;
    }

    public static int createPacketHeader(FlatBufferBuilder builder, int streamId, int size) {
        builder.prep(4, 8);
        builder.putInt(size);
        builder.putInt(streamId);
        return builder.offset();
    }

    public static int createFileDataTable(FlatBufferBuilder builder, int tableOffset) {
        builder.startTable(1);
        addTable(builder, tableOffset);
        return endFileDataTable(builder);
    }

    public static void addTable(FlatBufferBuilder builder, int tableOffset) {
        builder.addOffset(0, tableOffset, 0);
    }

    public static int createTableVector(FlatBufferBuilder builder, int[] offsets) {
        builder.startVector(4, offsets.length, 4);
        for (int i = offsets.length - 1; i >= 0; i--) {
            builder.addOffset(offsets[i]);
        }
        return builder.endVector();
    }

    public static int endFileDataTable(FlatBufferBuilder builder) {
        return builder.endTable();
    }
}
