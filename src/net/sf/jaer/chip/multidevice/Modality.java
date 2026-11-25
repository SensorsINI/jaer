package net.sf.jaer.chip.multidevice;

/**
 * What a {@link DeviceSlot} contributes to a {@link MultiDeviceChip}.
 * Vision slots share the tiled canvas. Other modalities stay on their own
 * packet types in the same {@link net.sf.jaer.event.PacketBundle}.
 */
public enum Modality {
    VISION,
    AUDIO,
    IMU,
    GNSS
}
