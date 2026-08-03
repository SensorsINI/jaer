package net.sf.jaer.eventio.aedat4.dv;

import com.google.flatbuffers.Constants;
import com.google.flatbuffers.FlatBufferBuilder;
import com.google.flatbuffers.Table;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Hand-written FlatBuffers helper for dv::Frame. */
public final class Frame extends Table {

    public static void ValidateVersion() {
        Constants.FLATBUFFERS_23_5_26();
    }

    public static Frame getSizePrefixedRootAsFrame(ByteBuffer _bb) {
        return getSizePrefixedRootAsFrame(_bb, new Frame());
    }

    public static Frame getSizePrefixedRootAsFrame(ByteBuffer _bb, Frame obj) {
        _bb.order(ByteOrder.LITTLE_ENDIAN);
        return obj.__assign(_bb.getInt(_bb.position() + Constants.SIZE_PREFIX_LENGTH) + _bb.position() + Constants.SIZE_PREFIX_LENGTH, _bb);
    }

    public void __init(int _i, ByteBuffer _bb) {
        __reset(_i, _bb);
    }

    public Frame __assign(int _i, ByteBuffer _bb) {
        __init(_i, _bb);
        return this;
    }

    public long timestamp() { int o = __offset(4); return o != 0 ? bb.getLong(o + bb_pos) : 0; }
    public long timestampStartOfFrame() { int o = __offset(6); return o != 0 ? bb.getLong(o + bb_pos) : 0; }
    public long timestampEndOfFrame() { int o = __offset(8); return o != 0 ? bb.getLong(o + bb_pos) : 0; }
    public long timestampStartOfExposure() { int o = __offset(10); return o != 0 ? bb.getLong(o + bb_pos) : 0; }
    public long timestampEndOfExposure() { int o = __offset(12); return o != 0 ? bb.getLong(o + bb_pos) : 0; }
    public byte format() { int o = __offset(14); return o != 0 ? bb.get(o + bb_pos) : FrameFormat.OPENCV_8U_C1; }
    public short sizeX() { int o = __offset(16); return o != 0 ? bb.getShort(o + bb_pos) : 0; }
    public short sizeY() { int o = __offset(18); return o != 0 ? bb.getShort(o + bb_pos) : 0; }
    public short positionX() { int o = __offset(20); return o != 0 ? bb.getShort(o + bb_pos) : 0; }
    public short positionY() { int o = __offset(22); return o != 0 ? bb.getShort(o + bb_pos) : 0; }
    public int pixels(int j) { int o = __offset(24); return o != 0 ? bb.get(__vector(o) + j) & 0xff : 0; }
    public int pixelsLength() { int o = __offset(24); return o != 0 ? __vector_len(o) : 0; }
    public ByteBuffer pixelsAsByteBuffer() { return __vector_as_bytebuffer(24, 1); }
    public long exposure() { int o = __offset(26); return o != 0 ? bb.getLong(o + bb_pos) : 0; }
    public byte source() { int o = __offset(28); return o != 0 ? bb.get(o + bb_pos) : FrameSource.UNDEFINED; }

    public static int createFrame(FlatBufferBuilder builder, long timestamp, long timestampStartOfFrame,
            long timestampEndOfFrame, long timestampStartOfExposure, long timestampEndOfExposure, byte format,
            short sizeX, short sizeY, short positionX, short positionY, int pixelsOffset, long exposure, byte source) {
        builder.startTable(13);
        addExposure(builder, exposure);
        addPixels(builder, pixelsOffset);
        addTimestampEndOfExposure(builder, timestampEndOfExposure);
        addTimestampStartOfExposure(builder, timestampStartOfExposure);
        addTimestampEndOfFrame(builder, timestampEndOfFrame);
        addTimestampStartOfFrame(builder, timestampStartOfFrame);
        addTimestamp(builder, timestamp);
        addPositionY(builder, positionY);
        addPositionX(builder, positionX);
        addSizeY(builder, sizeY);
        addSizeX(builder, sizeX);
        addSource(builder, source);
        addFormat(builder, format);
        return endFrame(builder);
    }

    public static void addTimestamp(FlatBufferBuilder builder, long timestamp) { builder.addLong(0, timestamp, 0); }
    public static void addTimestampStartOfFrame(FlatBufferBuilder builder, long value) { builder.addLong(1, value, 0); }
    public static void addTimestampEndOfFrame(FlatBufferBuilder builder, long value) { builder.addLong(2, value, 0); }
    public static void addTimestampStartOfExposure(FlatBufferBuilder builder, long value) { builder.addLong(3, value, 0); }
    public static void addTimestampEndOfExposure(FlatBufferBuilder builder, long value) { builder.addLong(4, value, 0); }
    public static void addFormat(FlatBufferBuilder builder, byte value) { builder.addByte(5, value, FrameFormat.OPENCV_8U_C1); }
    public static void addSizeX(FlatBufferBuilder builder, short value) { builder.addShort(6, value, 0); }
    public static void addSizeY(FlatBufferBuilder builder, short value) { builder.addShort(7, value, 0); }
    public static void addPositionX(FlatBufferBuilder builder, short value) { builder.addShort(8, value, 0); }
    public static void addPositionY(FlatBufferBuilder builder, short value) { builder.addShort(9, value, 0); }
    public static void addPixels(FlatBufferBuilder builder, int offset) { builder.addOffset(10, offset, 0); }
    public static int createPixelsVector(FlatBufferBuilder builder, byte[] data) { return builder.createByteVector(data); }
    public static void addExposure(FlatBufferBuilder builder, long value) { builder.addLong(11, value, 0); }
    public static void addSource(FlatBufferBuilder builder, byte value) { builder.addByte(12, value, FrameSource.UNDEFINED); }
    public static int endFrame(FlatBufferBuilder builder) { return builder.endTable(); }
}
