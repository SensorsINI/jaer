/*
 * Copyright (C) 2026 Tobi Delbruck / SensorsINI.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 */
package net.sf.jaer.eventprocessing;

import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.JComponent;
import javax.swing.KeyStroke;

import net.sf.jaer.util.HtmlHelpFrame;

/**
 * Nonmodal HTML help for an {@link EventFilter}. Uses {@link HtmlHelpFrame}
 * type-to-search. Relative {@code <img src>} / {@code href} resolve against the
 * filter class package.
 *
 * @see EventFilter#showHelpDialog()
 * @see net.sf.jaer.Help
 */
public class EventFilterHelpDialog extends HtmlHelpFrame {

    private static final long serialVersionUID = 1L;

    public EventFilterHelpDialog(Window parent, EventFilter filter, String html) {
        super("Help — " + filter.getClass().getSimpleName(), parent,
                "EventFilterHelp-" + filter.getClass().getSimpleName(), 640, 520);
        setDocumentBase(packageBaseUrl(filter.getClass()));
        setHtml(html);
        setToggleHandler(filter::toggleHelpDialog);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_SLASH, InputEvent.SHIFT_DOWN_MASK), "toggle-html-help");
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke('?'), "toggle-html-help");
    }
}
