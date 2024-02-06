/*
 * Copyright (C) 2020 tobid.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package net.sf.jaer;

import com.install4j.api.context.UserCanceledException;
import java.awt.Component;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.JOptionPane;
import com.install4j.api.update.*;
import com.install4j.api.launcher.Variables;
import net.sf.jaer.util.MessageWithLink;

/**
 * Handles self update with install4j. Based on HelloGui.java from install4j samples.
 *
 * @author Tobi Delbruck (tobi@ini.uzh.ch)
 *
 */
public class JaerUpdaterInstall4j {

    public static final boolean DEBUG = false; // TODO remember to revert false for production version,  true to clone here to tmp folders that do not overwrite our own .git
    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final Preferences prefs = Preferences.userNodeForPackage(JaerUpdaterInstall4j.class);
    public static String INSTALL4J_UPDATES_URL = "https://raw.githubusercontent.com/SensorsINI/jaer/master/updates.xml";

    public static void checkForInstall4jReleaseUpdate(Component parent) {
        // check if rujning from installed version of jaer (fails if running from git compiled jaer)
        String currentVersion = "unknown";
        try {
            currentVersion = Variables.getCompilerVariable("sys.version");
        } catch (IOException e) {
            // TODO not running in installation
            JOptionPane.showMessageDialog(parent, "<html> Could not determine current version. <p>To check for udpates, you need to install jAER with an install4j installer. <p>(Probably are you running from git compiled development environment): <p>" + e.toString(), "Version check error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String updateUrl = INSTALL4J_UPDATES_URL;
        try {
            UpdateDescriptor updateDescriptor = UpdateChecker.getUpdateDescriptor(updateUrl, ApplicationDisplayMode.GUI);
            if (updateDescriptor.getPossibleUpdateEntry() != null) {
                // TODO an update is available, execute update downloader
                UpdateDescriptorEntry updateDescriptorEntry = updateDescriptor.getEntryForCurrentMediaFileId();
                String updateVersion = updateDescriptorEntry.getNewVersion();
                JOptionPane.showMessageDialog(parent,
                        new MessageWithLink("<html>Current version: " + currentVersion + "<p> Update " + updateVersion
                                + " is available; see <a href=\"https://github.com/SensorsINI/jaer/releases\">jAER releases</a>"),
                        "Update available", JOptionPane.INFORMATION_MESSAGE);
//                JOptionPane.showMessageDialog(parent, "<html>Update " + updateVersion + " is available; see <a href=\"https://github.com/SensorsINI/jaer/releases\">jAER releases</a>", "Releases update check", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(parent, new MessageWithLink("<html>No update available;<br> you are running current release " + currentVersion + "<p>See <a href=\"https://github.com/SensorsINI/jaer/releases\">jAER releases</a>"), "No update available", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (IOException | UserCanceledException e) {
            JOptionPane.showMessageDialog(parent, "Could not check for release update: " + e.toString(), "Update check error", JOptionPane.ERROR_MESSAGE);
        }
    }

}
