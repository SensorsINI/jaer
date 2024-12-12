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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.InvalidPreferencesFormatException;
import java.util.prefs.NodeChangeEvent;
import java.util.prefs.NodeChangeListener;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;

import net.sf.jaer.chip.Chip;
import net.sf.jaer.eventprocessing.EventFilter;
import net.sf.jaer.util.SubclassFinder;
import org.xml.sax.SAXException;

/**
 * Moves Preferences from old scheme (pre Nov 2024, jaer 2.6.3) to new scheme
 * with more hierarchy, where each Chip gets it's own node and each EventFilter
 * gets its own node.
 *
 * @author tobid
 */
public class PreferencesMover {

    static PrefsListener listener = null;
    /**
     * This built in Logger should be used for logging, e.g. via log.info or
     * log.warn
     *
     */
    protected static Logger log = Logger.getLogger("net.sf.jaer");

    static public class Result {

        public Result() {
            this(false, 0, 0, "No result");
        }

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

    public static boolean hasOldChipPreferences(Chip chip) {
        try {
            Preferences prefs = chip.getPrefs();
            Preferences oldPrefs = Preferences.userNodeForPackage(chip.getClass());
            boolean hasOldPref = false;
            String lastkey=null;
            for (String s : oldPrefs.keys()) {
                if (s.startsWith(chip.getClass().getSimpleName() + ".")) {
                    log.finer(String.format("found old-style preference key %s for chip %s in prefs %s", s, chip.getClass().getSimpleName(), oldPrefs.absolutePath()));
                    hasOldPref = true;
                    lastkey=s;
                    break;
                }
            }
            if (hasOldPref) {
                log.info(String.format("found old-style preference key %s for chip %s in old-style prefs node %s", lastkey, chip.getClass().getSimpleName(), oldPrefs.absolutePath()));
            }
            return hasOldPref;
        } catch (BackingStoreException ex) {
            Logger.getLogger(PreferencesMover.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }

    static class PrefsListener implements NodeChangeListener, PreferenceChangeListener {

        Preferences prefs;
        ArrayList<PrefsListener> listeners = new ArrayList();

        public PrefsListener(Preferences prefs) {
            this.prefs = prefs;
            prefs.addNodeChangeListener(this);
            prefs.addPreferenceChangeListener(this);
        }

        @Override
        public void childAdded(NodeChangeEvent evt) {
            log.info(String.format("Child node %s added", evt.toString()));
            Preferences newNode = (Preferences) evt.getChild();
            PrefsListener l = new PrefsListener(newNode);
            this.listeners.add(l);
        }

        @Override
        public void childRemoved(NodeChangeEvent evt) {
            log.info(evt.toString());
        }

        @Override
        public void preferenceChange(PreferenceChangeEvent evt) {
            log.info(evt.toString());
        }

    }

    public static Document inspectPrefs(File fXmlFile) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(fXmlFile);
        doc.getDocumentElement().normalize();

        System.out.println("Root element :" + doc.getDocumentElement().getNodeName());
        NodeList nList = doc.getChildNodes();
        System.out.println("----------------------------");

        for (int temp = 0; temp < nList.getLength(); temp++) {
            Node nNode = nList.item(temp);
            System.out.println("\nCurrent Element :" + nNode.getNodeName());
            if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                Element eElement = (Element) nNode;
                eElement.getChildNodes();
            }
        }
        return doc;
    }

    public static void immportPrefs(File file) throws BackingStoreException, IOException, InvalidPreferencesFormatException, ParserConfigurationException, SAXException {

        Document doc = inspectPrefs(file);
        Preferences prefs = Preferences.userRoot();
        listener = new PrefsListener(prefs);
        FileInputStream fis = new FileInputStream(file);
        Preferences.importPreferences(fis);
    }

    public static void migratePreferencesDialog(Component comp, Chip chip, boolean doChipPrefs, boolean doFilterPrefs) {
        if (!SwingUtilities.isEventDispatchThread()) {
            log.warning("cannot invoke GUI outside GUI event dispatch thread");
            return;
        }

        String dialogMsg = String.format("<html>Chip %s has old-style flat preferences at %s,<br>migrate to new hierarchical scheme at %s?"
                + "<ul>"
                + "<li>Choose <em>Yes</em> to (possibly) overrwrite existing chip preferences"
                + "<li>Choose <em>No</em> to preserve existing chip preferences, but the imported preferences will not be used by the AEChip"
                + "</ul>",
                chip.getClass().getSimpleName(),
                chip.prefsNodeNameOriginal(),
                chip.getPrefs().absolutePath()
        );

        int ret = JOptionPane.showConfirmDialog(comp,
                dialogMsg,
                "Migrate preferences?",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (ret == JOptionPane.YES_OPTION) {
            try {
                if (comp != null) {
                    comp.setCursor(new Cursor(Cursor.WAIT_CURSOR));
                }
                Result chipResults = new Result(), filterResults = new Result();
                String resultMsg = String.format("<html>Migration result:<ul>");
                if (doChipPrefs) {
                    chipResults = migrateChipPrefs(chip);
                    resultMsg += "<li>" + chipResults.msg;
                }
                if (doFilterPrefs) {
                    filterResults = migrateAllFilterPrefs(chip);
                    resultMsg += "<li>" + filterResults.msg;
                }
                resultMsg += "</ul> <p>Use <i>AEViewer</i> menu <i>AEChip/Renew AEChip</i> to see the results of migration in HW Configuraton";
                int status = JOptionPane.INFORMATION_MESSAGE;
                if ((doChipPrefs && !chipResults.movedSome) || (doFilterPrefs && !filterResults.movedSome)) {
                    status = JOptionPane.WARNING_MESSAGE;
                }
                JOptionPane.showMessageDialog(comp, resultMsg, "Migration result", status);
            } catch (BackingStoreException ex) {
                JOptionPane.showMessageDialog(comp, ex.toString(), "Error", JOptionPane.ERROR_MESSAGE);
            } finally {
                if (comp != null) {
                    comp.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                }
            }
        }
    }

    public static void migrateAllPreferencesDialog(Component comp, Chip chip) {
        migratePreferencesDialog(comp, chip, true, true);
    }

    public static Result migrateEventFilterPrefsDialog(EventFilter filter) {
        String dialogMsg = String.format("<html>Filter %s has old-style flat preferences at %s,<br>migrate to new hierarchical scheme at %s?"
                + "<ul>"
                + "<li>Choose <em>Yes</em> to (possibly) overrwrite existing filter preferences"
                + "<li>Choose <em>No</em> to preserve existing filter preferences, but the imported preferences will not be used by the filter"
                + "</ul>",
                filter.getClass().getSimpleName(),
                filter.getChip().prefsNodeNameOriginal(),
                filter.getPrefs().absolutePath()
        );

        Component comp = filter.getFilterPanel() != null ? filter.getFilterPanel() : null;

        int ret = JOptionPane.showConfirmDialog(comp,
                dialogMsg,
                "Migrate preferences?",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (ret == JOptionPane.YES_OPTION) {
            try {
                Result results = migrateEventFilterPrefs(filter);
                int status = JOptionPane.INFORMATION_MESSAGE;
                JOptionPane.showMessageDialog(filter.getFilterPanel(), results.msg, "Migration result", status);
            } catch (BackingStoreException ex) {
                JOptionPane.showMessageDialog(null, ex.toString(), "Error", JOptionPane.ERROR_MESSAGE);
                return new Result(false, 0, 0, ex.toString());
            }
        }
        return new Result(false, 0, 0, "Cancelled");
    }

    private static Result migrateChipPrefs(Chip chip) throws BackingStoreException {
        Preferences newPrefs = chip.getPrefs();
        Preferences oldPrefs = Preferences.userRoot().node(chip.prefsNodeNameOriginal());
        log.info(String.format("Migrating chip %s Preference keys from %s to move to new node %s", chip.getClass().getSimpleName(), oldPrefs.absolutePath(), newPrefs.absolutePath()));
        String chipLeader = chip.getClass().getSimpleName() + ".";
        int movedCount = 0, notMovedCount = 0;
        for (String key : oldPrefs.keys()) { // TODO add sub nodes
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
        if (movedCount > 0) {
            String msg = String.format("<html>Migrated %d AEChip %s Preference keys from <i>%s</i> to <i>%s</i>", movedCount, chip.getClass().getSimpleName(), oldPrefs.absolutePath(), newPrefs.absolutePath());
            log.info(msg);
            return new Result(true, movedCount, notMovedCount, msg);
        } else {
            String msg = String.format("Failed to migrate %d Preference keys from %s to %s", movedCount, oldPrefs.absolutePath(), newPrefs.absolutePath());
            log.info(msg);
            return new Result(false, movedCount, notMovedCount, msg);
        }
    }

    private static Result migrateEventFilterPrefs(EventFilter filter) throws BackingStoreException {
        Preferences newPrefs = filter.getPrefs();
        Preferences oldPrefs = Preferences.userRoot().node(filter.getChip().prefsNodeNameOriginal());
        int movedCount = 0, notMovedCount = 0;
        String keyStart = filter.getClass().getSimpleName() + ".";
        for (String key : oldPrefs.keys()) { // TODO add sub nodes
            log.finest(String.format("Checking key %s", key));

            if (key.startsWith(keyStart)) {
                log.fine(String.format("Key %s matches event filter key %s", key, keyStart));
                String value = oldPrefs.get(key, null);
                if (value != null) {
                    String newKey = key.substring(key.indexOf(".") + 1);
//                Preferences newChildNode = prefs.node(newNodeName);
                    newPrefs.put(newKey, value);
                    oldPrefs.remove(key);
                    movedCount++;
                    log.fine(String.format("Moved key=%s in %s to %s in %s", key, oldPrefs.absolutePath(), newKey, newPrefs));
                } else {
                    notMovedCount++;
                    log.finer(String.format("Key %s did not start with %s", key, keyStart));
                }
            }
        }
        StringBuilder sb=new StringBuilder();
        for(String childNodeName:oldPrefs.childrenNames()){
            sb.append(childNodeName).append(", ");
        }
        if (movedCount > 0) {
            String msg = String.format("<html>Migrated %d EventFilter keys from %s to %s and left behind %d keys.<p>Did <b>not</b> migrate %d child nodes named <br><i>%s</i>.<p>These nodes might contain enclosed filter preferences. These will need migration by manual XML editing.",
                    movedCount,
                    oldPrefs.absolutePath(),
                    newPrefs.absolutePath(),
                    notMovedCount, 
                    oldPrefs.childrenNames().length,
                    sb.toString());
            log.info(msg);
            return new Result(true, movedCount, notMovedCount, msg);
        } else {
            String msg = String.format("<html>Migrated %d EventFilter preferences from <i>%s</i> to <i>%s</i>", movedCount, oldPrefs.absolutePath(), newPrefs.absolutePath());
            return new Result(false, movedCount, notMovedCount, msg);
        }

    }

    private static Result migrateAllFilterPrefs(Chip chip) throws BackingStoreException {
        Preferences newPrefs = chip.getPrefs();
        Preferences oldPrefs = Preferences.userRoot().node(chip.prefsNodeNameOriginal());
        log.info(String.format("Migrating chip %s EventFilter preferences from %s to move to new node %s", chip.getClass().getSimpleName(), oldPrefs.absolutePath(), newPrefs.absolutePath()));
        ArrayList<String> eventFilterClassNames = SubclassFinder.findSubclassesOf(EventFilter.class.getName());
        log.fine(String.format("Found %d subclasses of EventFilter", eventFilterClassNames.size()));
        int movedCount = 0, notMovedCount = 0;
        String childNames = "Child nodes: ";
        for (String childNodeName : oldPrefs.childrenNames()) {
            childNames += childNodeName + ", ";
        }
        log.info(childNames);
        for (String key : oldPrefs.keys()) { // TODO add sub nodes
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
            log.info(msg);
            return new Result(true, movedCount, notMovedCount, msg);
        } else {
            String msg = String.format("Migrated %d EventFilter preferences from <i>%s</i> to <i>%s</i>", movedCount, oldPrefs.absolutePath(), newPrefs.absolutePath());
            return new Result(false, movedCount, notMovedCount, msg);
        }
    }
}
