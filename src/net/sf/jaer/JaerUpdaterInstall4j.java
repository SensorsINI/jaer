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
import javax.swing.JFrame;
import net.sf.jaer.util.MessageWithLink;

/**
 * Handles self update with install4j. Based on HelloGui.java from install4j
 * samples.
 *
 * @author Tobi Delbruck (tobi@ini.uzh.ch)
 *
 */
public class JaerUpdaterInstall4j {

    public static final boolean DEBUG = false; // TODO remember to revert false for production version,  true to clone here to tmp folders that do not overwrite our own .git
    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final Preferences prefs = Preferences.userNodeForPackage(JaerUpdaterInstall4j.class);
    public static String INSTALL4J_UPDATES_URL = "https://raw.githubusercontent.com/SensorsINI/jaer/master/updates.xml";

    public enum CheckFreq {
        Daily("days"), Weekly("weeks"), Monthly("months"), Never("never");
        public final String units;
        CheckFreq(String units){
            this.units=units;
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
            case Daily:
                updateCheck(parent, days, checkFreq);
                break;
            case Weekly:
                updateCheck(parent, weeks, checkFreq);
                break;
            case Monthly:
                updateCheck(parent, months, checkFreq);
                break;
        }

    }

    public CheckFreq getPreferredCheckFrequency() {
        CheckFreq checkFreq=CheckFreq.Monthly;
        try{
            checkFreq = CheckFreq.valueOf(prefs.get(CHECK_FREQ_KEY, CheckFreq.Monthly.toString()));
        }catch(Exception e){
            log.warning(e.toString());
        }
        return checkFreq;
    }
    
    public void storePreferredCheckFrequency(CheckFreq freq){
        prefs.put(CHECK_FREQ_KEY, freq.toString());
    }
    
    private void updateCheck(JFrame parent, int val, CheckFreq freq){
        if(val<=0){
            log.info(String.format("No update check needed (%d %s since last %s check)",val,freq.units,freq.toString()));
            return;
        }
        log.info(String.format("It has been %d %s since last check for %s check; checking for update", val, freq.units, freq.toString()));
        checkForInstall4jReleaseUpdate(parent, false);
    }

    /**
     * Check for possible release update
     *
     * @param parent where to center result dialog
     * @param interactive true to report that we cannot update because we
     * are not running from installed release, false to not report this (for
     * automatic checks)
     * @param parent the result dialog will be centered over this frame
     * @param interactive true to show dialog on results, false for automatic checks where dialog only shows if there is one available
     */
    public void checkForInstall4jReleaseUpdate(JFrame parent, boolean interactive) {
        // check if rujning from installed version of jaer (fails if running from git compiled jaer)
        String currentVersion = "unknown";
        try {
            currentVersion = Variables.getCompilerVariable("sys.version");
        } catch (IOException e) {
            // TODO not running in installation
            if (interactive) {
                JOptionPane.showMessageDialog(parent, "<html> Could not determine current version. <p>To check for udpates, you need to install jAER with an install4j installer. <p>(Probably are you running from git compiled development environment): <p>" + e.toString(), "Version check error", JOptionPane.ERROR_MESSAGE);
            } else {
                log.info(String.format("Could not determine current version of install4j release installation: %s; probably you are a developer who is running from git checkout",e.toString()));
            }
            storeUpdateCheckTime();
            return;
        }

        String updateUrl = INSTALL4J_UPDATES_URL;
        try {
            UpdateDescriptor updateDescriptor = UpdateChecker.getUpdateDescriptor(updateUrl, ApplicationDisplayMode.GUI);
            if (updateDescriptor.getPossibleUpdateEntry() != null) {
                // TODO an update is available, execute update downloader
                UpdateDescriptorEntry updateDescriptorEntry = updateDescriptor.getEntryForCurrentMediaFileId();
                String updateVersion = updateDescriptorEntry.getNewVersion();
                MessageWithLink msg= new MessageWithLink("<html>Current version: " + currentVersion + "<p> Update " + updateVersion
                                + " is available; see <a href=\"https://github.com/SensorsINI/jaer/releases\">jAER releases</a>");
                JaerUpdaterInstall4jDialog d=new JaerUpdaterInstall4jDialog(parent, this,msg);
                d.setVisible(true);
//                JOptionPane.showMessageDialog(parent,
//                       msg,
//                        "Update available", JOptionPane.INFORMATION_MESSAGE);
//                JOptionPane.showMessageDialog(parent, "<html>Update " + updateVersion + " is available; see <a href=\"https://github.com/SensorsINI/jaer/releases\">jAER releases</a>", "Releases update check", JOptionPane.INFORMATION_MESSAGE);
            } else if(interactive) {
                MessageWithLink msg=new MessageWithLink("<html>No update available;<br> you are running current release " + currentVersion + "<p>See <a href=\"https://github.com/SensorsINI/jaer/releases\">jAER releases</a>");
                JaerUpdaterInstall4jDialog d=new JaerUpdaterInstall4jDialog(parent, this,msg);
                d.setVisible(true);
//                JOptionPane.showMessageDialog(parent, msg, "No update available", JOptionPane.INFORMATION_MESSAGE);
            }
            storeUpdateCheckTime();
        } catch (IOException | UserCanceledException e) {
            JOptionPane.showMessageDialog(parent, "Could not check for release update: " + e.toString(), "Update check error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void storeUpdateCheckTime() {
        prefs.putLong(LAST_CHECK_TIME_KEY, System.currentTimeMillis());
    }

}
