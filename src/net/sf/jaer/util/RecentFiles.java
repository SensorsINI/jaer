/*
 * RecentFiles.java
 *
 * Created on October 27, 2005, 8:54 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */
package net.sf.jaer.util;

import java.awt.event.ActionListener;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;

/**
 * Keeps track of recent files and folders opened.
 * @author tobi
 */
public class RecentFiles {
    transient Preferences prefs;
    ArrayList<File> fileList; // contains files and folders mixed together
    transient JMenu fileMenu;
    transient ActionListener listener;
    public static final int MAX_FILES=20, MAX_FOLDERS=15;
    ArrayList<JMenuItem> fileMenuList=null;
    private static Logger log=Logger.getLogger("net.sf.jaer");

    /** Separator before recent files, between files and folders, and before Preferences. */
    private final JSeparator beforeFilesSep = new JSeparator();
    private final JSeparator beforeFoldersSep = new JSeparator();
    private final JSeparator beforePreferencesSep = new JSeparator();
    
    /** Creates a new instance of RecentFiles
     * @param prefs the Preferences node to store recent files in
     * @param fileMenu the File menu to load with recent files. Items are inserted
     * just above Preferences (if present) or Exit.
     * @param listener the MenuListener to call when one of the items is selected
     */
    public RecentFiles(Preferences prefs, JMenu fileMenu, ActionListener listener) {
        this.prefs=prefs;
        this.fileMenu=fileMenu;
        this.listener=listener;
        getPrefs();
        fileMenuList=new ArrayList<JMenuItem>(MAX_FILES);
        buildMenu();
    }

    /**
     * Index at which to insert recent-file items: immediately before Preferences
     * (if present) or Exit. Keeps Exit as the last menu item.
     */
    private int getRecentFilesInsertIndex() {
        int prefsIdx = findMenuItemIndexByText("Preferences...");
        if (prefsIdx >= 0) {
            return prefsIdx;
        }
        int exitIdx = findMenuItemIndexByText("Exit");
        if (exitIdx >= 0) {
            return exitIdx;
        }
        return fileMenu.getItemCount();
    }

    private int findMenuItemIndexByText(String text) {
        for (int i = 0; i < fileMenu.getItemCount(); i++) {
            JMenuItem item = fileMenu.getItem(i);
            if (item != null && text.equals(item.getText())) {
                return i;
            }
        }
        return -1;
    }
    
    private boolean buildingMenu;

    /** inserts the file items in the File menu */
    void buildMenu(){
        if (buildingMenu) {
            return;
        }
        buildingMenu = true;
        try {
            buildMenuBody();
        } finally {
            buildingMenu = false;
        }
    }

    private void buildMenuBody(){
        for(JMenuItem i:fileMenuList){
            fileMenu.remove(i);
        }
        fileMenuList.clear();
        fileMenu.remove(beforeFilesSep);
        fileMenu.remove(beforeFoldersSep);
        fileMenu.remove(beforePreferencesSep);

        ArrayList<JMenuItem> fileItems = new ArrayList<>();
        ArrayList<JMenuItem> folderItems = new ArrayList<>();
        int fileIndex=0;
        int folderIndex=0;
        Map<File, FileAccessTimeout.Kind> kinds = FileAccessTimeout.classify(fileList);
        dropUnreachable(kinds);
        for(File f:fileList){
            if(f==null){
                System.err.println("RecentFiles.buildMenu(): null File in fileList");
                continue;
            }
            FileAccessTimeout.Kind k = kinds.getOrDefault(f, FileAccessTimeout.Kind.MISSING);
            if(k == FileAccessTimeout.Kind.FILE){
                String name=f.getName();
                if(fileIndex<9){
                    name=Integer.toString(fileIndex+1)+" "+f.getName();
                }
                JMenuItem item=new JMenuItem(name);
                item.setActionCommand(f.getPath());
                item.setToolTipText(String.format("<html>%s<p>(Hold Shift and select to open folder)",f.getPath()));
                item.addActionListener(listener);
                item.setMnemonic(item.getText().charAt(0));
                fileItems.add(item);
                fileIndex++;
                if(fileIndex>MAX_FILES) break;
            }else if(k != FileAccessTimeout.Kind.DIRECTORY){
                log.info(String.format("File %s dis not a file or directory",f.toString()));
            }
        }
        for(File f:fileList){
            if(f==null){
                System.err.println("RecentFiles.buildMenu(): null File in fileList");
                continue;
            }
            FileAccessTimeout.Kind k = kinds.getOrDefault(f, FileAccessTimeout.Kind.MISSING);
            if(k == FileAccessTimeout.Kind.DIRECTORY){
                String name=f.getName();
                JMenuItem item=new JMenuItem(name+File.separator);
                item.setActionCommand(f.getPath());
                item.setToolTipText(f.getPath());
                item.addActionListener(listener);
                folderItems.add(item);
                folderIndex++;
                if(folderIndex>MAX_FOLDERS) break;
            }
        }

        boolean hasFiles = !fileItems.isEmpty();
        boolean hasFolders = !folderItems.isEmpty();

        // Always keep the recent-files block so File does not run Preferences/Exit
        // into the previous group when the list is empty (fresh prefs, new machine).
        // [sep] files... [sep] folders... [sep] Preferences
        int idx = getRecentFilesInsertIndex();
        fileMenu.add(beforeFilesSep, idx++);
        if (!hasFiles && !hasFolders) {
            JMenuItem empty = new JMenuItem("No recent files");
            empty.setEnabled(false);
            fileMenu.insert(empty, idx++);
            fileMenuList.add(empty);
        } else {
            for (JMenuItem item : fileItems) {
                fileMenu.insert(item, idx++);
                fileMenuList.add(item);
            }
            if (hasFolders) {
                fileMenu.add(beforeFoldersSep, idx++);
                for (JMenuItem item : folderItems) {
                    fileMenu.insert(item, idx++);
                    fileMenuList.add(item);
                }
            }
        }
        fileMenu.add(beforePreferencesSep, idx);
    }
    
    void putPrefs(){
        try {
            // Serialize to a byte array
            ByteArrayOutputStream bos = new ByteArrayOutputStream() ;
            ObjectOutput out = new ObjectOutputStream(bos) ;
            out.writeObject(fileList);
            out.close();
            
            // Get the bytes of the serialized object
            byte[] buf = bos.toByteArray();
            prefs.putByteArray("recentFiles", buf);
        } catch (IOException e) {
            e.printStackTrace();
        }catch(IllegalArgumentException e2){
            log.warning("RecentFiles tried to store too many files in Preferences, fileList has "+fileList.size()+" files");
        }
        
    }
    
    @SuppressWarnings("unchecked")
    void getPrefs(){
        // Deserialize from a byte array
        try {
            byte[] bytes=prefs.getByteArray("recentFiles",null);
            if(bytes!=null){
                ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes));
                fileList = (ArrayList<File>) in.readObject();
                in.close();
            }else{
                fileList=new ArrayList<File>(MAX_FILES);
            }
        }catch(ClassCastException e){
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }finally{
            if(fileList==null) fileList=new ArrayList<File>(MAX_FILES);
        }
    }
    
    /** adds files and their containing folders to list of recent files. List is pruned if too long.
     @param f a file to add
     */
    public void addFile(File f){
        if(f==null){
            log.warning("RecentFiles.addFile(): tried to add null File");
            return;
        }
        if(fileList.contains(f)) {
            fileList.remove(f);
            fileList.add(0,f); // put to head of list
        }else{
            fileList.add(0, f);
        }
        
        // add folder to list
        File parentFile=f.getParentFile();
        if(parentFile==null){
            log.warning("RecentFiles.addFile(): parent of File "+f+" is null, not adding directory");
        }else{
            if(fileList.contains(parentFile)) {
                fileList.remove(parentFile);
                fileList.add(0,parentFile); // put to head of list
            }else{
                fileList.add(0, parentFile);
            }
        }
        pruneList();
        putPrefs();
        buildMenu();
    }
    
    public void removeFile(File f){
        if (f == null || fileList == null) {
            return;
        }
        boolean removed = fileList.remove(f);
        if (!removed) {
            File abs = f.getAbsoluteFile();
            removed = fileList.removeIf(x -> x != null && samePath(x, abs));
        }
        if (!removed) {
            return;
        }
        putPrefs();
        buildMenu();
    }

    private static boolean samePath(File a, File b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.equals(b)) {
            return true;
        }
        return a.getAbsoluteFile().equals(b.getAbsoluteFile());
    }

    /**
     * Forget paths that timed out, are missing, or are neither file nor
     * directory. One failed probe is enough; otherwise a wedged UNC/Dropbox
     * folder is re-probed on every save dialog / menu rebuild.
     *
     * @return true if anything was removed
     */
    private boolean dropUnreachable(Map<File, FileAccessTimeout.Kind> kinds) {
        if (fileList == null || kinds == null) {
            return false;
        }
        ArrayList<File> removeList = new ArrayList<>();
        for (File f : fileList) {
            if (f == null) {
                removeList.add(f);
                continue;
            }
            FileAccessTimeout.Kind k = kinds.getOrDefault(f, FileAccessTimeout.Kind.MISSING);
            if (k != FileAccessTimeout.Kind.FILE && k != FileAccessTimeout.Kind.DIRECTORY) {
                removeList.add(f);
            }
        }
        if (removeList.isEmpty()) {
            return false;
        }
        fileList.removeAll(removeList);
        for (File f : removeList) {
            log.warning("Removing unreachable recent path " + f
                    + " (" + kinds.getOrDefault(f, FileAccessTimeout.Kind.MISSING) + ")");
        }
        putPrefs();
        return true;
    }
    
    // prunes list to MAX_FILES and MAX_FOLDERS
    private void pruneList(){
        Map<File, FileAccessTimeout.Kind> kinds = FileAccessTimeout.classify(fileList);
        dropUnreachable(kinds);
        ArrayList<File> removeList=new ArrayList<File>();
        int nfiles=0, ndirs=0;
        for(File f:fileList){
            FileAccessTimeout.Kind k = kinds.getOrDefault(f, FileAccessTimeout.Kind.MISSING);
            if(k == FileAccessTimeout.Kind.FILE){
                nfiles++;
                if(nfiles>MAX_FILES){
                    removeList.add(f);
               }
            }else if(k == FileAccessTimeout.Kind.DIRECTORY){
                ndirs++;
                if(ndirs>MAX_FOLDERS){
                    removeList.add(f);
                }
            }
        }
        fileList.removeAll(removeList);
    }
    
    /**
     * Existing directories from the recent list, most recent first (at most
     * {@link #MAX_FOLDERS}). Missing or timed-out paths are forgotten.
     */
    public List<File> getRecentFolders() {
        ArrayList<File> folders = new ArrayList<>();
        if (fileList == null) {
            return folders;
        }
        Map<File, FileAccessTimeout.Kind> kinds = FileAccessTimeout.classify(fileList);
        if (dropUnreachable(kinds)) {
            buildMenu();
        }
        for (File f : fileList) {
            if (f != null && kinds.getOrDefault(f, FileAccessTimeout.Kind.MISSING) == FileAccessTimeout.Kind.DIRECTORY) {
                folders.add(f);
                if (folders.size() >= MAX_FOLDERS) {
                    break;
                }
            }
        }
        return folders;
    }

    /** Returns most recent folder
     * 
     * @return most recent folder, or null if there is none
     */
    public File getMostRecentFolder(){
        List<File> folders = getRecentFolders();
        return folders.isEmpty() ? null : folders.get(0);
    }
    
    /** Returns most recent file
     * 
     * @return most recent file, or null if there is none
     */
    public File getMostRecentFile(){
        if(fileList==null || fileList.isEmpty()) return null;
        Map<File, FileAccessTimeout.Kind> kinds = FileAccessTimeout.classify(fileList);
        if (dropUnreachable(kinds)) {
            buildMenu();
        }
        for(File f:fileList){
            if(kinds.getOrDefault(f, FileAccessTimeout.Kind.MISSING) == FileAccessTimeout.Kind.FILE) return f;
        }
        return null;   
    }
    
    
}
