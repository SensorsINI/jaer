/*
 * Copyright (C) 2026 Tobi Delbruck / SensorsINI.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 */
package net.sf.jaer.util;

import java.awt.AWTEvent;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.AbstractButton;

import net.sf.jaer.JaerConstants;

/**
 * Optional JSONL log of UI actions under {@code ${java.io.tmpdir}/jaer/interactions/}.
 * Off by default; enable with File → Preferences → Collect usage data.
 */
public final class UiInteractionLog {

    public static final String PREF_KEY = "collectUsageData";
    private static final Logger log = Logger.getLogger(UiInteractionLog.class.getName());
    private static final Object LOCK = new Object();
    private static final long WHEEL_MIN_INTERVAL_MS = 80;

    private static volatile boolean enabled;
    private static AWTEventListener listener;
    private static BufferedWriter writer;
    private static File currentFile;
    private static long lastWheelMs;

    private UiInteractionLog() {
    }

    public static boolean isEnabled() {
        return JaerConstants.PREFS_ROOT.getBoolean(PREF_KEY, false);
    }

    public static void setEnabled(boolean on) {
        JaerConstants.PREFS_ROOT.putBoolean(PREF_KEY, on);
        syncFromPrefs();
    }

    /** Directory {@code …/jaer/interactions} (created when logging starts). */
    public static File directory() {
        return new File(JaerTmpdir.get(), "interactions");
    }

    public static void syncFromPrefs() {
        boolean on = isEnabled();
        synchronized (LOCK) {
            if (on == enabled) {
                return;
            }
            if (on) {
                startLocked();
            } else {
                stopLocked();
            }
        }
    }

    private static void startLocked() {
        try {
            File dir = directory();
            if (!dir.isDirectory() && !dir.mkdirs()) {
                throw new IOException("Could not create " + dir.getAbsolutePath());
            }
            currentFile = new File(dir, "ui-" + Instant.now().toString().replace(':', '-') + ".jsonl");
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(currentFile, true),
                    StandardCharsets.UTF_8));
            listener = UiInteractionLog::eventDispatched;
            Toolkit.getDefaultToolkit().addAWTEventListener(listener,
                    AWTEvent.ACTION_EVENT_MASK | AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK
                            | AWTEvent.MOUSE_WHEEL_EVENT_MASK);
            enabled = true;
            writeLineLocked("{\"ts\":\"" + Instant.now() + "\",\"kind\":\"session\",\"file\":\""
                    + jsonEscape(currentFile.getAbsolutePath()) + "\"}");
            log.info("UI interaction log: " + currentFile.getAbsolutePath());
        } catch (IOException e) {
            log.log(Level.WARNING, "Could not start UI interaction log", e);
            stopLocked();
        }
    }

    private static void stopLocked() {
        enabled = false;
        if (listener != null) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(listener);
            listener = null;
        }
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                log.log(Level.FINE, "close interaction log", e);
            }
            writer = null;
        }
        currentFile = null;
    }

    private static void eventDispatched(AWTEvent event) {
        if (!enabled) {
            return;
        }
        try {
            if (event instanceof ActionEvent) {
                ActionEvent ae = (ActionEvent) event;
                String cmd = ae.getActionCommand();
                String src = ae.getSource() == null ? "" : ae.getSource().getClass().getSimpleName();
                String text = "";
                if (ae.getSource() instanceof AbstractButton) {
                    text = ((AbstractButton) ae.getSource()).getText();
                }
                append("action", "\"cmd\":\"" + jsonEscape(cmd) + "\",\"src\":\"" + jsonEscape(src)
                        + "\",\"text\":\"" + jsonEscape(text) + "\"");
            } else if (event instanceof KeyEvent) {
                KeyEvent ke = (KeyEvent) event;
                if (ke.getID() != KeyEvent.KEY_PRESSED) {
                    return;
                }
                int code = ke.getKeyCode();
                if (code == KeyEvent.VK_SHIFT || code == KeyEvent.VK_CONTROL || code == KeyEvent.VK_ALT
                        || code == KeyEvent.VK_META || code == KeyEvent.VK_UNDEFINED) {
                    return;
                }
                append("key", "\"code\":\"" + jsonEscape(KeyEvent.getKeyText(code)) + "\",\"mods\":\""
                        + jsonEscape(KeyEvent.getModifiersExText(ke.getModifiersEx())) + "\"");
            } else if (event instanceof MouseWheelEvent) {
                long now = System.currentTimeMillis();
                if (now - lastWheelMs < WHEEL_MIN_INTERVAL_MS) {
                    return;
                }
                lastWheelMs = now;
                MouseWheelEvent we = (MouseWheelEvent) event;
                append("wheel", "\"rot\":" + we.getWheelRotation() + ",\"mods\":\""
                        + jsonEscape(KeyEvent.getModifiersExText(we.getModifiersEx())) + "\"");
            } else if (event instanceof MouseEvent) {
                MouseEvent me = (MouseEvent) event;
                if (me.getID() != MouseEvent.MOUSE_CLICKED) {
                    return;
                }
                String src = me.getComponent() == null ? "" : me.getComponent().getClass().getSimpleName();
                append("click", "\"btn\":" + me.getButton() + ",\"n\":" + me.getClickCount()
                        + ",\"src\":\"" + jsonEscape(src) + "\"");
            }
        } catch (RuntimeException e) {
            log.log(Level.FINE, "interaction log event", e);
        }
    }

    private static void append(String kind, String fields) {
        writeLine("{\"ts\":\"" + Instant.now() + "\",\"kind\":\"" + kind + "\"," + fields + "}");
    }

    private static void writeLine(String line) {
        synchronized (LOCK) {
            writeLineLocked(line);
        }
    }

    private static void writeLineLocked(String line) {
        if (writer == null) {
            return;
        }
        try {
            writer.write(line);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            log.log(Level.WARNING, "Could not write UI interaction log", e);
            stopLocked();
        }
    }

    private static String jsonEscape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\':
                    b.append("\\\\");
                    break;
                case '"':
                    b.append("\\\"");
                    break;
                case '\n':
                    b.append("\\n");
                    break;
                case '\r':
                    b.append("\\r");
                    break;
                case '\t':
                    b.append("\\t");
                    break;
                default:
                    if (c < 32) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
            }
        }
        return b.toString();
    }
}
