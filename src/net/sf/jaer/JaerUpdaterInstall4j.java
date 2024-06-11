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
import java.io.IOException;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.JOptionPane;
import com.install4j.api.update.*;
import com.install4j.api.launcher.Variables;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import net.sf.jaer.util.MessageWithLink;

/**
 * Handles self update with install4j. Based on HelloGui.java from install4j
 * samples.
 *
 * @author Tobi Delbruck (tobi@ini.uzh.ch)
 *
 */
public class JaerUpdaterInstall4j {

    public static final boolean DEBUG = false; // TODO Set true to always run update check; remember to revert false for production version\    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final Preferences prefs = Preferences.userNodeForPackage(JaerUpdaterInstall4j.class);
    public static String INSTALL4J_UPDATES_URL = "https://raw.githubusercontent.com/SensorsINI/jaer/master/updates.xml";

    public enum CheckFreq {
        Daily("days"), Weekly("weeks"), Monthly("months"), Never("never");
        public final String units;

        CheckFreq(String units) {
            this.units = units;
        }
    }
    public final String LAST_CHECK_TIME_KEY = "lastInstall4jCheckTime", CHECK_FREQ_KEY = "install4jCheckFreqKey";

    public void maybeDoPeriodicUpdateCheck(JFrame parent) {
        CheckFreq checkFreq = getPreferredCheckFrequency();
        if (checkFreq == CheckFreq.Never) {
            log.info("Not checking (CheckFreq is set to Never)");
            return;
        }
        long lastCheckTime = prefs.getLong(LAST_CHECK_TIME_KEY, 0);
        long timeNow = System.currentTimeMillis();
        long timeMsSinceLastCheck = timeNow - lastCheckTime;
        int days = (int) (timeMsSinceLastCheck / (24 * 60 * 60 * 1000));
        int weeks = days / 7;
        int months = weeks / 4;
        int years = weeks / 52;
        log.info(String.format("%,d y, %,d m, %,d w, %,d d since last update check. Check frequency is %s", years, months, weeks, days, checkFreq.toString()));
        switch (checkFreq) {
            case Daily ->
                updateCheck(parent, days, checkFreq);
            case Weekly ->
                updateCheck(parent, weeks, checkFreq);
            case Monthly ->
                updateCheck(parent, months, checkFreq);
        }

    }

    public CheckFreq getPreferredCheckFrequency() {
        CheckFreq checkFreq = CheckFreq.Monthly;
        try {
            checkFreq = CheckFreq.valueOf(prefs.get(CHECK_FREQ_KEY, CheckFreq.Monthly.toString()));
        } catch (Exception e) {
            log.warning(e.toString());
        }
        return checkFreq;
    }

    public void storePreferredCheckFrequency(CheckFreq freq) {
        prefs.put(CHECK_FREQ_KEY, freq.toString());
    }

    private void updateCheck(JFrame parent, int val, CheckFreq freq) {
        if (!DEBUG && val <= 0) {
            log.info(String.format("No update check needed (%d %s since last %s check)", val, freq.units, freq.toString()));
            return;
        }
        log.info(String.format("It has been %d %s since last check for %s check; checking for update", val, freq.units, freq.toString()));
        checkForInstall4jReleaseUpdate(parent, false);
    }

    /**
     * Check for possible release update
     *
     * @param parent the result dialog will be centered over this frame
     * @param interactive true to show dialog on results, false for automatic
     * checks where dialog only shows if there is one available
     */
    public void checkForInstall4jReleaseUpdate(JFrame parent, boolean interactive) {
        // check if rujning from installed version of jaer (fails if running from git compiled jaer)
        String currentVersion = "unknown";
        try {
            currentVersion = Variables.getCompilerVariable("sys.version");
        } catch (IOException e) {
            if (interactive) {
                JOptionPane.showMessageDialog(parent, "<html> Could not determine current version. <p>To check for udpates, you need to install jAER with an install4j installer. <p>(Probably are you running from git compiled development environment): <p>" + e.toString(), "Version check error", JOptionPane.ERROR_MESSAGE);
            } else {
                log.info(String.format("Could not determine current version of install4j release installation: %s.\nProbably you are a developer who is running from git checkout", e.toString()));
            }
            if (!DEBUG) {
                return;
            }
        }

        if (interactive) { // interactive check runs in the Swing thread where the user has launched it
            try {
                UpdateDescriptor updateDescriptor = UpdateChecker.getUpdateDescriptor(INSTALL4J_UPDATES_URL, ApplicationDisplayMode.GUI);
                if (updateDescriptor.getPossibleUpdateEntry() != null) {
                    // TODO an update is available, execute update downloader instead of just reporting update is available
                    UpdateDescriptorEntry updateDescriptorEntry = updateDescriptor.getEntryForCurrentMediaFileId();
                    String updateVersion = updateDescriptorEntry.getNewVersion();
                    MessageWithLink msg = new MessageWithLink("<html>Current version: " + currentVersion + "<p> Update " + updateVersion
                            + " is available; see <a href=\"https://github.com/SensorsINI/jaer/releases\">jAER releases</a>");
                    JaerUpdaterInstall4jDialog d = new JaerUpdaterInstall4jDialog(parent, this, msg);
                    d.setVisible(true);
                } else {
                    MessageWithLink msg = new MessageWithLink("<html>No update available;<br> you are running current release " + currentVersion + "<p>See <a href=\"https://github.com/SensorsINI/jaer/releases\">jAER releases</a>");
                    JaerUpdaterInstall4jDialog d = new JaerUpdaterInstall4jDialog(parent, this, msg);
                    d.setVisible(true);
                }
                storeUpdateCheckTime();
            } catch (IOException | UserCanceledException e) {
                JOptionPane.showMessageDialog(parent, "Could not check for release update: " + e.toString(), "Update check error", JOptionPane.ERROR_MESSAGE);
            }
        } else { // noninteractive (automatic) checks run in separate thread and show result in Swing thread when done
            log.info("starting background thread to check for updates");
            BackgroundUpdateChecker updateChecker = new BackgroundUpdateChecker(parent, currentVersion, this);
            Thread t = new Thread(updateChecker);
            t.start();
        }
    }

    private class BackgroundUpdateChecker implements Runnable {

        final JFrame parent;
        final String currentVersion;
        final JaerUpdaterInstall4j updater;

        public BackgroundUpdateChecker(JFrame parent, String currentVersion, JaerUpdaterInstall4j updater) {
            this.parent = parent;
            this.currentVersion = currentVersion;
            this.updater = updater;
        }

        @Override
        public void run() {
            MessageWithLink msg = null;
            try {
                UpdateDescriptor updateDescriptor = UpdateChecker.getUpdateDescriptor(INSTALL4J_UPDATES_URL, ApplicationDisplayMode.GUI);
                if (updateDescriptor.getPossibleUpdateEntry() != null) {
                    UpdateDescriptorEntry updateDescriptorEntry = updateDescriptor.getEntryForCurrentMediaFileId();
                    String updateVersion = updateDescriptorEntry.getNewVersion();
                    msg = new MessageWithLink("<html>Current version: " + currentVersion + "<p> Update " + updateVersion
                            + " is available; see <a href=\"https://github.com/SensorsINI/jaer/releases\">jAER releases</a>");
                } else {
                    msg = new MessageWithLink("<html>No update available;<br> you are running current release " + currentVersion + "<p>See <a href=\"https://github.com/SensorsINI/jaer/releases\">jAER releases</a>");
                }
                storeUpdateCheckTime();
            } catch (IOException | UserCanceledException e) {
                log.warning(String.format("Could not check for update: %s", e.toString()));
            }
            if (msg != null) {
                JaerUpdaterInstall4jDialog d = new JaerUpdaterInstall4jDialog(parent, updater, msg);
                SwingUtilities.invokeLater(() -> {
                    d.setVisible(true);
                });
            }
        }
    }

    private void storeUpdateCheckTime() {
        prefs.putLong(LAST_CHECK_TIME_KEY, System.currentTimeMillis());
    }

}
