/*
 * PacketType.java
 *
 * jAER 3.0: typed, homogeneous data packets (AEDAT-4 style).
 */
package net.sf.jaer.event;

import net.sf.jaer.aemonitor.EventRaw;

/**
 * Identifies the uniform data kind carried by a single packet in the jAER 3.0
 * pipeline. Aligned with {@link EventRaw.EventType} / AEDAT-3 and DV AEDAT-4
 * stream types (polarity events, frames, IMU, …).
 * <p>
 * Each {@link TypedDataPacket} holds exactly one of these kinds. A
 * {@link PacketBundle} may interleave several typed packets in time order.
 *
 * @author tobi
 * @see PacketBundle
 * @see TypedDataPacket
 */
public enum PacketType {

    SPECIAL(EventRaw.EventType.SpecialEvent),
    POLARITY(EventRaw.EventType.PolarityEvent),
    FRAME(EventRaw.EventType.FrameEvent),
    IMU6(EventRaw.EventType.Imu6Event),
    IMU9(EventRaw.EventType.Imu9Event),
    SAMPLE(EventRaw.EventType.SampleEvent),
    EAR(EventRaw.EventType.EarEvent),
    CONFIG(EventRaw.EventType.ConfigEvent),
    POINT1D(EventRaw.EventType.Point1DEvent),
    POINT2D(EventRaw.EventType.Point2DEvent),
    POINT3D(EventRaw.EventType.Point3DEvent),
    POINT4D(EventRaw.EventType.Point4DEvent),
    SPIKE(EventRaw.EventType.SpikeEvent);

    private final EventRaw.EventType eventRawType;

    PacketType(EventRaw.EventType eventRawType) {
        this.eventRawType = eventRawType;
    }

    /**
     * Numeric id matching AEDAT-3 / {@link EventRaw.EventType#getValue()}.
     */
    public int getValue() {
        return eventRawType.getValue();
    }

    public EventRaw.EventType toEventRawType() {
        return eventRawType;
    }

    /**
     * Maps an AEDAT-3 / {@link EventRaw.EventType} to {@link PacketType}.
     */
    public static PacketType fromEventRawType(EventRaw.EventType t) {
        if (t == null) {
            return null;
        }
        for (PacketType p : values()) {
            if (p.eventRawType == t) {
                return p;
            }
        }
        throw new IllegalArgumentException("No PacketType for EventRaw.EventType " + t);
    }

    public boolean isImu() {
        return this == IMU6 || this == IMU9;
    }

    public boolean isPolarity() {
        return this == POLARITY;
    }

    public boolean isFrame() {
        return this == FRAME;
    }
}
