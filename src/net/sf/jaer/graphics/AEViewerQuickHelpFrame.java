/*
 * Copyright (C) 2026 Tobi Delbruck / SensorsINI.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 */
package net.sf.jaer.graphics;

import java.io.IOException;
import java.net.URL;

import net.sf.jaer.JaerConstants;
import net.sf.jaer.util.HtmlHelpFrame;

/**
 * Nonmodal HTML help window with type-to-search (Esc, F3 / Shift+F3).
 * Default constructor loads {@code /net/sf/jaer/quickhelp.html}. Other
 * constructors take a title and {@link #setHtml(String)} content.
 */
public class AEViewerQuickHelpFrame extends HtmlHelpFrame {

    private static final long serialVersionUID = 1L;
    static final String RESOURCE = "/net/sf/jaer/quickhelp.html";

    /** Shortcuts / F1 help for this viewer. */
    public AEViewerQuickHelpFrame(AEViewer viewer) {
        this(viewer, "Quick help / Shortcuts", "AEViewerQuickHelp", true);
        URL url = AEViewerQuickHelpFrame.class.getResource(RESOURCE);
        try {
            if (url != null) {
                setPage(url);
            } else {
                setHtml("<p>Missing classpath resource " + RESOURCE
                        + ".</p><p><a href=\"" + JaerConstants.HELP_URL_USER_GUIDE
                        + "\">See user guide</a></p>");
            }
        } catch (IOException e) {
            setHtml("<p>Could not load quick help: " + e
                    + "</p><p><a href=\"" + JaerConstants.HELP_URL_USER_GUIDE
                    + "\">See user guide</a></p>");
        }
    }

    /**
     * Same searchable window with a custom title; load content with
     * {@link #setHtml(String)} or {@link #setPage(java.net.URL)}.
     */
    public AEViewerQuickHelpFrame(AEViewer viewer, String title) {
        this(viewer, title, windowName(title), false);
    }

    private AEViewerQuickHelpFrame(AEViewer viewer, String title, String windowName, boolean shortcutsMode) {
        super(title, viewer, windowName, 720, 640);
        if (viewer != null) {
            setIconImage(viewer.getIconImage());
            if (shortcutsMode) {
                setToggleHandler(viewer::toggleQuickHelp);
            }
        }
    }

    private static String windowName(String title) {
        if (title == null || title.isBlank()) {
            return "AEViewerHelp";
        }
        return title.replaceAll("[^A-Za-z0-9]+", "");
    }
}
