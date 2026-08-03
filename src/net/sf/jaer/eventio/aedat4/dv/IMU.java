package net.sf.jaer.eventio.aedat4.dv;

import com.google.flatbuffers.FlatBufferBuilder;
import com.google.flatbuffers.Table;
import java.nio.ByteBuffer;

/** Hand-written FlatBuffers helper for dv::IMU. */
public final class IMU extends Table {

    public void __init(int _i, ByteBuffer _bb) {
        __reset(_i, _bb);
    }

    public IMU __assign(int _i, ByteBuffer _bb) {
        __init(_i, _bb);
        return this;
    }

    public long timestamp() { int o = __offset(4); return o != 0 ? bb.getLong(o + bb_pos) : 0; }
    public float temperature() { int o = __offset(6); return o != 0 ? bb.getFloat(o + bb_pos) : 0; }
    public float accelerometerX() { int o = __offset(8); return o != 0 ? bb.getFloat(o + bb_pos) : 0; }
    public float accelerometerY() { int o = __offset(10); return o != 0 ? bb.getFloat(o + bb_pos) : 0; }
    public float accelerometerZ() { int o = __offset(12); return o != 0 ? bb.getFloat(o + bb_pos) : 0; }
    public float gyroscopeX() { int o = __offset(14); return o != 0 ? bb.getFloat(o + bb_pos) : 0; }
    public float gyroscopeY() { int o = __offset(16); return o != 0 ? bb.getFloat(o + bb_pos) : 0; }
    public float gyroscopeZ() { int o = __offset(18); return o != 0 ? bb.getFloat(o + bb_pos) : 0; }
    public float magnetometerX() { int o = __offset(20); return o != 0 ? bb.getFloat(o + bb_pos) : 0; }
    public float magnetometerY() { int o = __offset(22); return o != 0 ? bb.getFloat(o + bb_pos) : 0; }
    public float magnetometerZ() { int o = __offset(24); return o != 0 ? bb.getFloat(o + bb_pos) : 0; }

    public static int createIMU(FlatBufferBuilder builder, long timestamp, float temperature,
            float accelerometerX, float accelerometerY, float accelerometerZ, float gyroscopeX,
            float gyroscopeY, float gyroscopeZ, float magnetometerX, float magnetometerY, float magnetometerZ) {
        builder.startTable(11);
        addTimestamp(builder, timestamp);
        addMagnetometerZ(builder, magnetometerZ);
        addMagnetometerY(builder, magnetometerY);
        addMagnetometerX(builder, magnetometerX);
        addGyroscopeZ(builder, gyroscopeZ);
        addGyroscopeY(builder, gyroscopeY);
        addGyroscopeX(builder, gyroscopeX);
        addAccelerometerZ(builder, accelerometerZ);
        addAccelerometerY(builder, accelerometerY);
        addAccelerometerX(builder, accelerometerX);
        addTemperature(builder, temperature);
        return endIMU(builder);
    }

    public static void addTimestamp(FlatBufferBuilder builder, long value) { builder.addLong(0, value, 0); }
    public static void addTemperature(FlatBufferBuilder builder, float value) { builder.addFloat(1, value, 0); }
    public static void addAccelerometerX(FlatBufferBuilder builder, float value) { builder.addFloat(2, value, 0); }
    public static void addAccelerometerY(FlatBufferBuilder builder, float value) { builder.addFloat(3, value, 0); }
    public static void addAccelerometerZ(FlatBufferBuilder builder, float value) { builder.addFloat(4, value, 0); }
    public static void addGyroscopeX(FlatBufferBuilder builder, float value) { builder.addFloat(5, value, 0); }
    public static void addGyroscopeY(FlatBufferBuilder builder, float value) { builder.addFloat(6, value, 0); }
    public static void addGyroscopeZ(FlatBufferBuilder builder, float value) { builder.addFloat(7, value, 0); }
    public static void addMagnetometerX(FlatBufferBuilder builder, float value) { builder.addFloat(8, value, 0); }
    public static void addMagnetometerY(FlatBufferBuilder builder, float value) { builder.addFloat(9, value, 0); }
    public static void addMagnetometerZ(FlatBufferBuilder builder, float value) { builder.addFloat(10, value, 0); }
    public static int endIMU(FlatBufferBuilder builder) { return builder.endTable(); }
}
