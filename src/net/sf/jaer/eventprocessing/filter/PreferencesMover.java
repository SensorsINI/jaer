/*
 * Copyright (C) 2024 tobid.
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
package net.sf.jaer.eventprocessing.filter;

import java.awt.Component;
import java.awt.Cursor;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.eventprocessing.EventFilter;
import net.sf.jaer.util.SubclassFinder;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * Moves Preferences from old scheme (pre Nov 2024, jaer 2.6.3) to new scheme
 * with more hierarchy, where each Chip gets it's own node and each EventFilter
 * gets its own node.
 *
 * @author tobid
 */
public class PreferencesMover {

    /**
     * This built in Logger should be used for logging, e.g. via log.info or
     * log.warn
     *
     */
    protected static Logger log = Logger.getLogger("net.sf.jaer");

    static public class Result {

        public Result(boolean movedSome, int movedCount, int notMovedCount, String msg) {
            this.movedCount = movedCount;
            this.notMovedCount = notMovedCount;
            this.movedSome = movedSome;
            this.msg = msg;
        }
        int movedCount, notMovedCount;
        boolean movedSome;
        String msg;
    }

    public static boolean hasOldPreferences(Chip chip) {
        try {
            Preferences prefs = chip.getPrefs();
            Preferences oldPrefs = Preferences.userNodeForPackage(chip.getClass());
            boolean hasOldPref = false;
            for (String s : oldPrefs.keys()) {
                if (s.startsWith(chip.getClass().getSimpleName() + ".")) {
                    log.fine(String.format("found old-style preference key %s for chip %s in prefs %s", s, chip.getClass().getSimpleName(), oldPrefs.absolutePath()));
                    hasOldPref = true;
                    break;
                }
            }
            return hasOldPref;
        } catch (BackingStoreException ex) {
            Logger.getLogger(PreferencesMover.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }

    public static void migratePreferencesDialog(Component comp, Chip chip) {
        if (!SwingUtilities.isEventDispatchThread()) {
            log.warning("cannot invoke GUI outside GUI event dispatch thread");
            return;
        }

        String msg = String.format("<html>%s has old-style flat preferences at %s,<br>migrate to new hierarchical scheme at %s?"
                + "<ul>"
                + "<li>Choose <em>Yes</em> to (possibly) overrwrite existing chip preferences"
                + "<li>Choose <em>No</em> to preserve existing chip preferences, but the imported preferences will not be used by the AEChip"
                + "</ul>",
                chip.getClass().getSimpleName(),
                chip.prefsNodeNameOriginal(),
                chip.getPrefs().absolutePath()
        );

        int ret = JOptionPane.showConfirmDialog(comp,
                msg,
                "Migrate preferences?",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (ret == JOptionPane.YES_OPTION) {
            if (comp != null) {
                comp.setCursor(new Cursor(Cursor.WAIT_CURSOR));
            }
            Result[] result = PreferencesMover.migratePreferences(chip);
            if (result[0].movedSome || result[1].movedSome) {
                String resultMsg = String.format("<html>Successfully migrated:<p>%s<p>%s", result[0].msg, result[1].msg);
                JOptionPane.showMessageDialog(comp, resultMsg);
            } else {
                String resultMsg = String.format("<html>Migration result:<ul><li>%s<li>%s</ul>"
                        + "<p>Use <i>AEViewer</i> menu <i>AEChip/Renew AEChip</i> to see the results of migration in HW Configuraton",
                        result[0].msg,
                        result[1].msg);
                JOptionPane.showMessageDialog(comp, msg, "Warning", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    public static Result[] migratePreferences(Chip chip) {
        Preferences chipPrefs = chip.getPrefs();
        Preferences oldChipPrefs = Preferences.userRoot().node(chip.prefsNodeNameOriginal());
        Result[] results = new Result[2];
        String result = null;
        log.info(String.format("Migrating preferences %s to new node %s", oldChipPrefs.absolutePath(), chipPrefs.absolutePath()));
        try {
            results[0] = migrateChipPrefs(chip, oldChipPrefs, chipPrefs);
            results[1] = migrateFilterPrefs(chip, oldChipPrefs, chipPrefs);
        } catch (Exception e) {
            e.printStackTrace();
            log.severe(e.toString());
            results[0] = new Result(false, 0, 0, String.format(String.format("Error migrating: result=%s, Exception: %s", result, e.toString())));
        }
        return results;
    }

    private static Result migrateChipPrefs(Chip chip, Preferences oldPrefs, Preferences newPrefs) throws BackingStoreException {
        log.info(String.format("Migrating chip %s Preference keys from %s to move to new node %s", chip.getClass().getSimpleName(), oldPrefs.absolutePath(), newPrefs.absolutePath()));
        String chipLeader = chip.getClass().getSimpleName() + ".";
        int movedCount = 0, notMovedCount = 0;
        for (String key : oldPrefs.keys()) {
            if (key.startsWith(chipLeader)) {
                String value = oldPrefs.get(key, null);
                if (value != null) {
                    String newKey = key.substring(key.indexOf(".") + 1);
//                Preferences newChildNode = prefs.node(newNodeName);
                    newPrefs.put(newKey, value);
                    oldPrefs.remove(key);
                    movedCount++;
                    log.info(String.format("Moved key=%s in %s to %s in %s", key, oldPrefs.absolutePath(), newKey, newPrefs.absolutePath()));
                } else {
                    notMovedCount++;
                    log.finer(String.format("Key %s did not start with %s", key, chipLeader));
                }
            }
        }
        Result result = null;
        if (movedCount > 0) {
            String msg = String.format("<html>Migrated %d AEChip %s Preference keys from <i>%s</i> to <i>%s</i>", movedCount, chip.getClass().getSimpleName(), oldPrefs.absolutePath(), newPrefs.absolutePath());
            result = new Result(true, movedCount, notMovedCount, msg);
            log.info(msg);
        } else {
            String msg = String.format("Failed to migrate %d Preference keys from %s to %s", movedCount, oldPrefs.absolutePath(), newPrefs.absolutePath());
            result = new Result(false, movedCount, notMovedCount, msg);
        }
        return result;
    }

    private static Result migrateFilterPrefs(Chip chip, Preferences oldPrefs, Preferences newPrefs) throws BackingStoreException {
        Result result = null;
        log.info(String.format("Migrating chip %s EventFilter preferences from %s to move to new node %s", chip.getClass().getSimpleName(), oldPrefs.absolutePath(), newPrefs.absolutePath()));
        ArrayList<String> eventFilterClassNames = SubclassFinder.findSubclassesOf(EventFilter.class.getName());
        log.fine(String.format("Found %d subclasses of EventFilter", eventFilterClassNames.size()));
        String chipLeader = chip.getClass().getSimpleName() + ".";
        int movedCount = 0, notMovedCount = 0;
        for (String key : oldPrefs.keys()) {
            log.finest(String.format("Checking key %s", key));
            for (String eventFilterClassName : eventFilterClassNames) {
                String eventFilterSimpleName = eventFilterClassName.substring(eventFilterClassName.lastIndexOf('.'));
                if (eventFilterSimpleName.startsWith(".")) {
                    eventFilterSimpleName = eventFilterSimpleName.substring(1, eventFilterSimpleName.length());
                }
                if (key.startsWith(eventFilterSimpleName + ".")) {
                    log.fine(String.format("Key %s matches event filter %s", key, eventFilterClassName));
                    String value = oldPrefs.get(key, null);
                    if (value != null) {
                        Preferences filterPrefs = newPrefs.node(eventFilterSimpleName);
                        String newKey = key.substring(key.indexOf(".") + 1);
//                Preferences newChildNode = prefs.node(newNodeName);
                        filterPrefs.put(newKey, value);
                        oldPrefs.remove(key);
                        movedCount++;
                        log.fine(String.format("Moved key=%s in %s to %s in %s", key, oldPrefs.absolutePath(), newKey, filterPrefs.absolutePath()));
                    } else {
                        notMovedCount++;
                        log.finer(String.format("Key %s did not start with %s", key, eventFilterSimpleName));
                    }
                }
            }
        }
        if (movedCount > 0) {
            String msg = String.format("<html>Migrated %d EventFilter keys from %s to %s and left behind %d keys",
                    movedCount,
                    oldPrefs.absolutePath(),
                    newPrefs.absolutePath(),
                    notMovedCount);
            result = new Result(true, movedCount, notMovedCount, msg);
            log.info(msg);
        } else {
            String msg = String.format("Migrated %d EventFilter preferences from <i>%s</i> to <i>%s</i>", movedCount, oldPrefs.absolutePath(), newPrefs.absolutePath());
            result = new Result(true, movedCount, notMovedCount, msg);
        }
        return result;
    }

}
