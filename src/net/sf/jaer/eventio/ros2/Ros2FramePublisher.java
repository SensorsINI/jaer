/*
 * Copyright (C) 2026 Tobi Delbruck / SensorsINI.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 */
package net.sf.jaer.eventio.ros2;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import sensor_msgs.Image;
import std_msgs.Header;
import us.ihmc.fastddsjava.cdr.idl.IDLByteSequence;
import us.ihmc.jros2.ROS2Node;
import us.ihmc.jros2.ROS2Publisher;
import us.ihmc.jros2.ROS2QoSProfile;
import us.ihmc.jros2.ROS2Topic;

/**
 * jros2 DDS publisher for {@code sensor_msgs/Image}.
 */
public class Ros2FramePublisher implements AutoCloseable {

    private static final Logger log = Logger.getLogger("net.sf.jaer");

    private ROS2Node node;
    private final List<Pub> pubs = new ArrayList<>();
    private String lastError;

    public synchronized void open(String nodeName, int domainId) {
        close();
        lastError = null;
        try {
            node = new ROS2Node(nodeName, domainId);
            log.info("ROS2 node " + nodeName + " domain " + domainId);
        } catch (Throwable t) {
            lastError = t.toString();
            log.log(Level.WARNING, "Failed to start ROS2 node: " + t, t);
            node = null;
        }
    }

    public synchronized void setTopics(List<String> topics) {
        if (node == null) {
            return;
        }
        for (Pub p : pubs) {
            try {
                node.destroyPublisher(p.publisher);
            } catch (Exception e) {
                log.log(Level.FINE, e.toString(), e);
            }
        }
        pubs.clear();
        for (String topic : topics) {
            try {
                ROS2Topic<Image> t = new ROS2Topic<>(topic, Image.class, ROS2QoSProfile.BEST_EFFORT);
                ROS2Publisher<Image> publisher = node.createPublisher(t, ROS2QoSProfile.BEST_EFFORT);
                pubs.add(new Pub(topic, publisher, new Image()));
            } catch (Throwable t) {
                lastError = t.toString();
                log.log(Level.WARNING, "Failed to create publisher " + topic + ": " + t, t);
            }
        }
    }

    public synchronized void publish(String topic, EncodedImage image, String frameId, long timestampNs) {
        if (node == null) {
            return;
        }
        Pub p = null;
        for (Pub x : pubs) {
            if (x.topic.equals(topic)) {
                p = x;
                break;
            }
        }
        if (p == null) {
            return;
        }
        try {
            Image msg = p.scratch;
            Header h = msg.getHeader();
            h.setFrameId(frameId == null ? "dvs" : frameId);
            h.getStamp().setSec((int) (timestampNs / 1_000_000_000L));
            h.getStamp().setNanosec((int) (timestampNs % 1_000_000_000L));
            msg.setWidth(image.width);
            msg.setHeight(image.height);
            msg.setEncoding(image.encoding);
            msg.setIsBigendian((byte) 0);
            msg.setStep(image.step);
            IDLByteSequence data = msg.getData();
            data.clear();
            data.ensureMinCapacity(image.data.length);
            data.addAll(image.data);
            p.publisher.publish(msg);
        } catch (Throwable t) {
            lastError = t.toString();
            log.log(Level.WARNING, "ROS2 publish failed: " + t, t);
        }
    }

    public String getLastError() {
        return lastError;
    }

    public boolean isOpen() {
        return node != null && !node.isClosed();
    }

    @Override
    public synchronized void close() {
        for (Pub p : pubs) {
            try {
                if (node != null) {
                    node.destroyPublisher(p.publisher);
                }
            } catch (Exception e) {
                log.log(Level.FINE, e.toString(), e);
            }
        }
        pubs.clear();
        if (node != null) {
            try {
                node.close();
            } catch (Exception e) {
                log.log(Level.FINE, e.toString(), e);
            }
            node = null;
        }
    }

    private static final class Pub {
        final String topic;
        final ROS2Publisher<Image> publisher;
        final Image scratch;

        Pub(String topic, ROS2Publisher<Image> publisher, Image scratch) {
            this.topic = topic;
            this.publisher = publisher;
            this.scratch = scratch;
        }
    }
}
