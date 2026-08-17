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
import com.install4j.api.launcher.ApplicationLauncher;
import com.install4j.api.launcher.Variables;
import com.install4j.api.update.ApplicationDisplayMode;
import com.install4j.api.update.UpdateChecker;
import com.install4j.api.update.UpdateDescriptor;
import com.install4j.api.update.UpdateDescriptorEntry;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
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

    /** Set true to always run update check; remember to revert false for production. */
    public static final boolean DEBUG = false;
    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final Preferences prefs = JaerConstants.PREFS_ROOT;
    public static String INSTALL4J_UPDATES_URL = "https://raw.githubusercontent.com/SensorsINI/jaer/master/updates.xml";
    /** install4j Screens &amp; Actions id of the standalone update downloader. */
    public static final String UPDATER_APPLICATION_ID = "updater";
    /** Marker file in the installation directory written by brew/winget/deb packaging. */
    public static final String PACKAGE_MANAGER_MARKER = ".jaer-packaged-install";
    public static final String RELEASES_URL = "https://github.com/SensorsINI/jaer/releases";

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
        // check if running from installed version of jaer (fails if running from git compiled jaer)
        String currentVersion = "unknown";
        try {
            currentVersion = Variables.getCompilerVariable("sys.version");
        } catch (IOException e) {
            if (interactive) {
                JOptionPane.showMessageDialog(parent, "<html> Could not determine current version. <p>To check for updates, you need to install jAER with an install4j installer. <p>(Probably are you running from git compiled development environment): <p>" + e.toString(), "Version check error", JOptionPane.ERROR_MESSAGE);
            } else {
                log.info(String.format("Could not determine current version of install4j release installation: %s.\nProbably you are a developer who is running from git checkout", e.toString()));
            }
            if (!DEBUG) {
                return;
            }
        }

        if (interactive) { // interactive check runs in the Swing thread where the user has launched it
            try {
                UpdateCheckResult result = fetchUpdateCheckResult(currentVersion);
                showUpdateResultDialog(parent, result);
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

    private UpdateCheckResult fetchUpdateCheckResult(String currentVersion) throws IOException, UserCanceledException {
        UpdateDescriptor updateDescriptor = UpdateChecker.getUpdateDescriptor(INSTALL4J_UPDATES_URL, ApplicationDisplayMode.GUI);
        boolean updateAvailable = updateDescriptor.getPossibleUpdateEntry() != null;
        String updateVersion = null;
        if (updateAvailable) {
            UpdateDescriptorEntry updateDescriptorEntry = updateDescriptor.getEntryForCurrentMediaFileId();
            if (updateDescriptorEntry != null) {
                updateVersion = updateDescriptorEntry.getNewVersion();
            }
        }
        return new UpdateCheckResult(currentVersion, updateAvailable, updateVersion, isPackageManagedInstall());
    }

    private void showUpdateResultDialog(JFrame parent, UpdateCheckResult result) {
        MessageWithLink msg = result.toMessage();
        JaerUpdaterInstall4jDialog d = new JaerUpdaterInstall4jDialog(parent, this, msg, result.updateAvailable);
        d.setVisible(true);
    }

    /**
     * True when this install should be upgraded via winget/brew/apt rather than
     * the in-app install4j downloader.
     */
    public boolean isPackageManagedInstall() {
        File[] roots = {
            new File("."),
            new File(System.getProperty("user.dir", ".")),
        };
        for (File root : roots) {
            if (new File(root, PACKAGE_MANAGER_MARKER).isFile()) {
                log.info("Found " + PACKAGE_MANAGER_MARKER + " under " + root.getAbsolutePath());
                return true;
            }
        }
        String path = new File(".").getAbsolutePath().toLowerCase(Locale.ROOT);
        if (path.contains("caskroom") || path.contains("/cellar/") || path.contains("\\cellar\\")
                || path.contains("windowsapps") || path.contains("winget")) {
            log.info("Installation path looks package-managed: " + path);
            return true;
        }
        return false;
    }

    /**
     * Launch the install4j standalone update downloader, then quit jAER when
     * the downloader is ready to run the new installer.
     */
    public void launchUpdateDownloader(JFrame parent) {
        if (isPackageManagedInstall()) {
            JOptionPane.showMessageDialog(parent,
                    "<html>This copy of jAER was installed with a package manager.<p>"
                    + "Update with <code>winget upgrade SensorsINI.jAER</code> or "
                    + "<code>brew upgrade --cask jaer</code> instead of the in-app downloader.",
                    "Update via package manager", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            log.info("Launching install4j update downloader id=" + UPDATER_APPLICATION_ID);
            ApplicationLauncher.launchApplication(UPDATER_APPLICATION_ID, null, false, new ApplicationLauncher.Callback() {
                @Override
                public void exited(int exitValue) {
                    log.info("Update downloader exited with " + exitValue);
                }

                @Override
                public void prepareShutdown() {
                    log.info("Update downloader requested jAER shutdown so the new installer can replace files");
                    System.exit(0);
                }
            });
        } catch (IOException e) {
            log.warning("Could not start update downloader: " + e);
            JOptionPane.showMessageDialog(parent,
                    "<html>Could not start the update downloader.<p>"
                    + "Download the installer for your OS from "
                    + "<a href=\"" + RELEASES_URL + "\">jAER releases</a> instead.<p>"
                    + e.toString(),
                    "Update downloader error", JOptionPane.ERROR_MESSAGE);
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
            try {
                UpdateCheckResult result = fetchUpdateCheckResult(currentVersion);
                storeUpdateCheckTime();
                if (!result.updateAvailable) {
                    log.info("No install4j release update available (current " + currentVersion + ")");
                    return;
                }
                SwingUtilities.invokeLater(() -> {
                    showUpdateResultDialog(parent, result);
                });
            } catch (IOException | UserCanceledException e) {
                log.warning(String.format("Could not check for update: %s", e.toString()));
            }
        }
    }

    private void storeUpdateCheckTime() {
        prefs.putLong(LAST_CHECK_TIME_KEY, System.currentTimeMillis());
    }

    private static final class UpdateCheckResult {
        final String currentVersion;
        final boolean updateAvailable;
        final String updateVersion;
        final boolean packageManaged;

        UpdateCheckResult(String currentVersion, boolean updateAvailable, String updateVersion, boolean packageManaged) {
            this.currentVersion = currentVersion;
            this.updateAvailable = updateAvailable;
            this.updateVersion = updateVersion;
            this.packageManaged = packageManaged;
        }

        MessageWithLink toMessage() {
            if (!updateAvailable) {
                return new MessageWithLink("<html>No update available;<br> you are running current release " + currentVersion
                        + "<p>See <a href=\"" + RELEASES_URL + "\">jAER releases</a>");
            }
            String ver = updateVersion != null ? updateVersion : "a newer build";
            if (packageManaged) {
                return new MessageWithLink("<html>Current version: " + currentVersion + "<p>Update " + ver
                        + " is available.<p>This copy was installed with a package manager; "
                        + "run <code>winget upgrade SensorsINI.jAER</code> or <code>brew upgrade --cask jaer</code>, "
                        + "or see <a href=\"" + RELEASES_URL + "\">jAER releases</a>.");
            }
            return new MessageWithLink("<html>Current version: " + currentVersion + "<p>Update " + ver
                    + " is available (~300&nbsp;MB download).<p>Choose <b>Download and install</b> to fetch the installer for this OS, quit jAER, and run it. "
                    + "Or see <a href=\"" + RELEASES_URL + "\">jAER releases</a>.");
        }
    }

}
