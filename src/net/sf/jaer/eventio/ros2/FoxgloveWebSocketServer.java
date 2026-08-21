/*
 * Copyright (C) 2026 Tobi Delbruck / SensorsINI.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 */
package net.sf.jaer.eventio.ros2;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.protocols.IProtocol;
import org.java_websocket.protocols.Protocol;
import org.java_websocket.server.WebSocketServer;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Foxglove WebSocket protocol v1 server. Advertises {@code foxglove.RawImage}
 * JSON channels; Foxglove Studio connects with no ROS2 install.
 */
public class FoxgloveWebSocketServer extends WebSocketServer {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    public static final String SUBPROTOCOL = "foxglove.websocket.v1";
    public static final String SCHEMA_NAME = "foxglove.RawImage";
    private static final byte OPCODE_MESSAGE_DATA = 0x01;

    private final Gson gson = new Gson();
    private final String schemaJson;
    private final String sessionId = UUID.randomUUID().toString();
    private final List<Channel> channels = Collections.synchronizedList(new ArrayList<>());
    /** client -> (subscriptionId -> channelId) */
    private final Map<WebSocket, Map<Integer, Integer>> subscriptions = new ConcurrentHashMap<>();
    private volatile int nextChannelId = 1;

    public static String loadRawImageSchema() throws IOException {
        try (InputStream in = FoxgloveWebSocketServer.class.getResourceAsStream("RawImage.json")) {
            if (in == null) {
                throw new IOException("RawImage.json missing on classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public FoxgloveWebSocketServer(InetSocketAddress address, String schemaJson) {
        super(address, Collections.singletonList(new Draft_6455(
                Collections.emptyList(),
                Collections.<IProtocol>singletonList(new Protocol(SUBPROTOCOL)))));
        setReuseAddr(true);
        this.schemaJson = schemaJson;
    }

    public int getClientCount() {
        return getConnections() == null ? 0 : getConnections().size();
    }

    /**
     * Replace advertised channels. Topics are full names, e.g. {@code /jaer/event_count}.
     */
    public void setTopics(List<String> topics) {
        List<Integer> oldIds = new ArrayList<>();
        synchronized (channels) {
            for (Channel c : channels) {
                oldIds.add(c.id);
            }
            channels.clear();
            for (String topic : topics) {
                channels.add(new Channel(nextChannelId++, topic));
            }
        }
        JsonObject unadv = new JsonObject();
        unadv.addProperty("op", "unadvertise");
        JsonArray ids = new JsonArray();
        for (int id : oldIds) {
            ids.add(id);
        }
        unadv.add("channelIds", ids);
        if (!oldIds.isEmpty()) {
            broadcast(unadv.toString());
        }
        broadcast(advertiseJson());
    }

    public void publish(String topic, EncodedImage image, String frameId, long timestampNs, long sequence) {
        Channel ch = findChannel(topic);
        if (ch == null) {
            return;
        }
        JsonObject msg = new JsonObject();
        JsonObject ts = new JsonObject();
        ts.addProperty("sec", timestampNs / 1_000_000_000L);
        ts.addProperty("nsec", (int) (timestampNs % 1_000_000_000L));
        msg.add("timestamp", ts);
        msg.addProperty("frame_id", frameId == null ? "dvs" : frameId);
        msg.addProperty("width", image.width);
        msg.addProperty("height", image.height);
        msg.addProperty("encoding", image.encoding);
        msg.addProperty("step", image.step);
        msg.addProperty("data", Base64.getEncoder().encodeToString(image.data));
        msg.addProperty("sequence", sequence);
        byte[] payload = gson.toJson(msg).getBytes(StandardCharsets.UTF_8);
        long logTime = timestampNs;
        for (WebSocket conn : getConnections()) {
            Map<Integer, Integer> subs = subscriptions.get(conn);
            if (subs == null) {
                continue;
            }
            for (Map.Entry<Integer, Integer> e : subs.entrySet()) {
                if (e.getValue() == ch.id) {
                    sendMessageData(conn, e.getKey(), logTime, payload);
                }
            }
        }
    }

    private Channel findChannel(String topic) {
        synchronized (channels) {
            for (Channel c : channels) {
                if (c.topic.equals(topic)) {
                    return c;
                }
            }
        }
        return null;
    }

    private void sendMessageData(WebSocket conn, int subscriptionId, long timestampNs, byte[] payload) {
        ByteBuffer buf = ByteBuffer.allocate(1 + 4 + 8 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.put(OPCODE_MESSAGE_DATA);
        buf.putInt(subscriptionId);
        buf.putLong(timestampNs);
        buf.put(payload);
        buf.flip();
        try {
            conn.send(buf);
        } catch (Exception e) {
            log.log(Level.FINE, "foxglove send: {0}", e.toString());
        }
    }

    private String serverInfoJson() {
        JsonObject o = new JsonObject();
        o.addProperty("op", "serverInfo");
        o.addProperty("name", "jAER ROSOutput");
        o.add("capabilities", new JsonArray());
        JsonArray enc = new JsonArray();
        enc.add("json");
        o.add("supportedEncodings", enc);
        o.addProperty("sessionId", sessionId);
        return o.toString();
    }

    private String advertiseJson() {
        JsonObject o = new JsonObject();
        o.addProperty("op", "advertise");
        JsonArray arr = new JsonArray();
        synchronized (channels) {
            for (Channel c : channels) {
                JsonObject ch = new JsonObject();
                ch.addProperty("id", c.id);
                ch.addProperty("topic", c.topic);
                ch.addProperty("encoding", "json");
                ch.addProperty("schemaName", SCHEMA_NAME);
                ch.addProperty("schema", schemaJson);
                ch.addProperty("schemaEncoding", "jsonschema");
                arr.add(ch);
            }
        }
        o.add("channels", arr);
        return o.toString();
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        subscriptions.put(conn, new ConcurrentHashMap<>());
        conn.send(serverInfoJson());
        conn.send(advertiseJson());
        log.info("Foxglove client connected from " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        subscriptions.remove(conn);
        log.info("Foxglove client disconnected");
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            JsonObject o = gson.fromJson(message, JsonObject.class);
            if (o == null || !o.has("op")) {
                return;
            }
            String op = o.get("op").getAsString();
            if ("subscribe".equals(op)) {
                Map<Integer, Integer> subs = subscriptions.computeIfAbsent(conn, k -> new ConcurrentHashMap<>());
                JsonArray arr = o.getAsJsonArray("subscriptions");
                if (arr == null) {
                    return;
                }
                for (JsonElement el : arr) {
                    JsonObject s = el.getAsJsonObject();
                    int subId = s.get("id").getAsInt();
                    int chId = s.get("channelId").getAsInt();
                    subs.put(subId, chId);
                }
            } else if ("unsubscribe".equals(op)) {
                Map<Integer, Integer> subs = subscriptions.get(conn);
                if (subs == null) {
                    return;
                }
                JsonArray ids = o.getAsJsonArray("subscriptionIds");
                if (ids == null) {
                    return;
                }
                for (JsonElement el : ids) {
                    subs.remove(el.getAsInt());
                }
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "Foxglove client message: " + e, e);
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        log.log(Level.WARNING, "Foxglove WebSocket: " + ex, ex);
    }

    @Override
    public void onStart() {
        log.info("Foxglove WebSocket listening on " + getAddress());
    }

    private static final class Channel {
        final int id;
        final String topic;

        Channel(int id, String topic) {
            this.id = id;
            this.topic = topic;
        }
    }
}
