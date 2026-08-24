/*
 * Copyright (C) 2026 Tobi Delbruck / SensorsINI.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 */
package net.sf.jaer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates an {@link net.sf.jaer.eventprocessing.EventFilter} with longer HTML
 * help shown in a nonmodal dialog on first selection (controls expanded) and
 * again from the FilterPanel {@code ?} button.
 * <p>
 * Use it like {@link Description}, but with HTML for a user guide rather than a
 * one-line tooltip. Links are clickable in the dialog. Relative
 * {@code <img src="file.png">} paths resolve against the filter class package
 * (put the PNG next to the {@code .java} file; Ant copies it onto the classpath).
 * <pre>
 * {@code
 * @Description("Short tooltip")
 * @Help("""
 * <html>
 * <h2>MyFilter</h2>
 * <p>How to use this filter. See
 * <a href="https://github.com/SensorsINI/jaer">jAER</a>.</p>
 * <p><img src="diagram.png" alt="diagram" width="480"></p>
 * </html>
 * """)
 * public class MyFilter extends EventFilter2D { }
 * }
 * </pre>
 *
 * @author tobi
 * @see Description
 * @see net.sf.jaer.eventprocessing.EventFilter#showHelpDialog()
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Help {

    /**
     * HTML body (or a full {@code <html>...} document) for the help dialog.
     */
    String value();
}
