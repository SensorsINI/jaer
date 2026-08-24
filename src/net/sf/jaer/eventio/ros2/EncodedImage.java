/*
 * Copyright (C) 2026 Tobi Delbruck / SensorsINI.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 */
package net.sf.jaer.eventio.ros2;

/**
 * One encoded image ready to publish (ROS2 {@code sensor_msgs/Image} or
 * Foxglove {@code foxglove.RawImage}).
 */
public final class EncodedImage {

    /** Topic path after the prefix, e.g. {@code event_count}. */
    public final String topicSuffix;
    public final int width;
    public final int height;
    /** Row stride in bytes. */
    public final int step;
    /** {@code 32FC1}, {@code rgb8}, or {@code mono8}. */
    public final String encoding;
    /** Packed pixels, little-endian for multi-byte encodings. */
    public final byte[] data;

    public EncodedImage(String topicSuffix, int width, int height, int step, String encoding, byte[] data) {
        this.topicSuffix = topicSuffix;
        this.width = width;
        this.height = height;
        this.step = step;
        this.encoding = encoding;
        this.data = data;
    }
}
