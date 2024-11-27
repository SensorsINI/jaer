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

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import net.sf.jaer.chip.Chip;

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

    public static boolean hasOldPreferences(Chip chip){
        try {
            Preferences prefs=chip.getPrefs();
            Preferences oldPrefs=Preferences.userNodeForPackage(chip.getClass());
            boolean hasOldPref=false;
            for(String s:oldPrefs.keys()){
                if(s.startsWith(chip.getClass().getSimpleName()+".")){
                    log.fine(String.format("found old-style preference key %s for chip %s in prefs %s",s,chip.getClass().getSimpleName(),oldPrefs.absolutePath()));
                    hasOldPref=true;
                    break;
                }
            }
            return hasOldPref;
        } catch (BackingStoreException ex) {
            Logger.getLogger(PreferencesMover.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }
    
    public static void movePreferencesDialog(Chip chip){
        if(!SwingUtilities.isEventDispatchThread()){
            log.warning("cannot invoke GUI outside GUI event dispatch thread");
            return;
        }
        
        int ret=JOptionPane.showConfirmDialog(null, "Old-style flat preferences detected, move to hierarhical scheme?", "Preferences", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if(ret==JOptionPane.YES_OPTION){
            PreferencesMover.movePreferences(chip.getPrefs());
        }
        
    }
    
    public static void movePreferences(Preferences prefs) {
        log.info(String.format("Moving preferences %s to new scheme", prefs.absolutePath()));
        try {
            moveChildren(prefs);
        } catch (BackingStoreException e) {
            e.printStackTrace();
            log.severe(e.toString());
        }
    }

    private static void moveChildren(Preferences prefs) throws BackingStoreException {
        log.info("Processing " + prefs.absolutePath());
        for (String child : prefs.childrenNames()) {
//            if (prefs.nodeExists(child)) {
                log.info(" Processing child node " + child);
                Preferences childPrefs = prefs.node(child);
                moveChildren(childPrefs);
//            }
        }
        for (String k : prefs.keys()) {
            log.info(String.format("              key=%s, value=%s", k, prefs.get(k, null)));
            if (k.contains(".")) {
//                String newNodeName = k.substring(0, k.indexOf('.'));
                String newKeyName = k.substring(k.indexOf(".") + 1);
//                Preferences newChildNode = prefs.node(newNodeName);
                prefs.put(newKeyName, prefs.get(k, null));
                prefs.remove(k);
            }
        }
    }

}
