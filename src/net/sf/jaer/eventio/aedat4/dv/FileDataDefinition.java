package net.sf.jaer.eventio.aedat4.dv;

import com.google.flatbuffers.FlatBufferBuilder;
import com.google.flatbuffers.Table;
import java.nio.ByteBuffer;

/** Hand-written FlatBuffers helper for dv::FileDataDefinition. */
public final class FileDataDefinition extends Table {

    public void __init(int _i, ByteBuffer _bb) {
        __reset(_i, _bb);
    }

    public FileDataDefinition __assign(int _i, ByteBuffer _bb) {
        __init(_i, _bb);
        return this;
    }

    public long byteOffset() { int o = __offset(4); return o != 0 ? bb.getLong(o + bb_pos) : 0; }
    public int packetInfoStreamID() { int o = __offset(6); return o != 0 ? bb.getInt(o + bb_pos) : 0; }
    public int packetInfoSize() { int o = __offset(6); return o != 0 ? bb.getInt(o + bb_pos + 4) : 0; }
    public long numElements() { int o = __offset(8); return o != 0 ? bb.getLong(o + bb_pos) : 0; }
    public long timestampStart() { int o = __offset(10); return o != 0 ? bb.getLong(o + bb_pos) : 0; }
    public long timestampEnd() { int o = __offset(12); return o != 0 ? bb.getLong(o + bb_pos) : 0; }

    public static int createFileDataDefinition(FlatBufferBuilder builder, long byteOffset, int streamId,
            int size, long numElements, long timestampStart, long timestampEnd) {
        builder.startTable(5);
        int packetInfoOffset = FileDataTable.createPacketHeader(builder, streamId, size);
        addPacketInfo(builder, packetInfoOffset);
        addTimestampEnd(builder, timestampEnd);
        addTimestampStart(builder, timestampStart);
        addNumElements(builder, numElements);
        addByteOffset(builder, byteOffset);
        return endFileDataDefinition(builder);
    }

    public static void addByteOffset(FlatBufferBuilder builder, long value) { builder.addLong(0, value, 0); }

    public static void addPacketInfo(FlatBufferBuilder builder, int packetInfoOffset) {
        builder.addStruct(1, packetInfoOffset, 0);
    }

    public static void addNumElements(FlatBufferBuilder builder, long value) { builder.addLong(2, value, 0); }
    public static void addTimestampStart(FlatBufferBuilder builder, long value) { builder.addLong(3, value, 0); }
    public static void addTimestampEnd(FlatBufferBuilder builder, long value) { builder.addLong(4, value, 0); }
    public static int endFileDataDefinition(FlatBufferBuilder builder) { return builder.endTable(); }
}
