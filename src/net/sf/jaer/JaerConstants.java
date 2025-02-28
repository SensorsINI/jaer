/*
 * Copyright (C) 2020 Tobi.
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

import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import org.apache.commons.io.IOUtils;

/**
 * Should hold constants for project
 * 
 * 
 * @author Tobi
 */
public class JaerConstants {
    private static final Logger log=Logger.getLogger(JaerConstants.class.getName());
    
    /** Root of jaer preferences */
    public static final String PREFS_ROOT_NAME="jaer";
    public static final Preferences PREFS_ROOT=Preferences.userRoot().node(PREFS_ROOT_NAME);
    /** Used for devices, retinas, cochleas */
    public static final Preferences PREFS_ROOT_CHIPS=Preferences.userRoot().node(PREFS_ROOT_NAME).node("chips");
    /** Used for things like USB interfaces that might not (yet) be attached to specific chips */
    public static final Preferences PREFS_ROOT_HARDWARE=Preferences.userRoot().node(PREFS_ROOT_NAME).node("hardware");
    public static final String ICON_IMAGE="/net/sf/jaer/images/jaer-icon.png";
    public static final String ICON_IMAGE_MAIN="/net/sf/jaer/images/jaer-main.png";
    public static final String ICON_IMAGE_HARDWARE="/net/sf/jaer/images/jaer-hardware.png";
    public static final String ICON_IMAGE_FILTERS="/net/sf/jaer/images/jaer-filters.png";
    public static final String SPLASH_SCREEN_IMAGE="/net/sf/jaer/images/SplashScreen.png";
    public static final String VERSION_FILE="BUILDVERSION.txt";
    public static final String JAER_HOME = "https://github.com/SensorsINI/jaer.git";
    public static final String JAER_RELEASES = "https://github.com/SensorsINI/jaer/releases";
    public static final String JAER_COMMITS = "https://github.com/SensorsINI/jaer/commits/master";
    public static final String HELP_URL_JAER_HOME = JAER_HOME;
    public static final String HELP_USER_GUIDE_URL_FLASHY = "https://docs.inivation.com/hardware/hardware-advanced-usage/firmware-update.html"; //"https://gitlab.com/inivation/devices-bin";
    public static final String HELP_URL_USER_GUIDE = "https://docs.google.com/document/d/1fb7VA8tdoxuYqZfrPfT46_wiT1isQZwTHgX8O22dJ0Q/edit?usp=sharing";
    public static final String HELP_URL_HARDWARE_USER_GUIDE = "http://www.inivation.com/support/hardware/";
    public static final String HELP_URL_HELP_FORUM = "https://groups.google.com/forum/#!forum/jaer-users";
    
    public static final String getBuildVersion(){
        // when running from webstart  we are not allowed to open a file on the local file system, but we can
        // get a the contents of a resource, which in this case is the echo'ed date stamp written by ant on the last build
        ClassLoader cl = JaerConstants.class.getClassLoader(); // get this class'es class loader
        log.fine("Loading version info from resource " + VERSION_FILE);
        URL versionURL = cl.getResource(VERSION_FILE); // get a URL to the time stamp file
        log.fine("Version URL=" + versionURL);
        if (versionURL != null) {
            try {
                Object urlContents = versionURL.getContent();
                if (urlContents instanceof InputStream) {
                    StringWriter writer=new StringWriter();
                    IOUtils.copy((InputStream)urlContents, writer, "UTF-8");
                    String version=writer.toString();
                    log.info("found version "+version);
                    return version;
                }
                return "(version not found)";
            } catch (Exception e) {
                return String.format("(version not found, caught %$s)",e.toString());
            }
        } else {
            return "(missing file " + VERSION_FILE + " in jAER.jar)";
        }
    }
}
