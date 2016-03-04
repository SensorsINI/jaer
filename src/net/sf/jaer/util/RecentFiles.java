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
    int menuPosition=0;
    public static final int MAX_FILES=20, MAX_FOLDERS=15;
    ArrayList<JMenuItem> fileMenuList=null, folderMenuList=null;
    private static Logger log=Logger.getLogger("RecentFiles");
    
    /** Creates a new instance of RecentFiles
     * @param prefs the Preferences node to store recent files in
     * @param fileMenu the File mane to load with recent files. The menu is added just before the end, presumed to be Exit
     * @param listener the MenuListener to call when one of the items is selected
     */
    public RecentFiles(Preferences prefs, JMenu fileMenu, ActionListener listener) {
        this.prefs=prefs;
        this.fileMenu=fileMenu;
        this.listener=listener;
        getPrefs();
        fileMenuList=new ArrayList<JMenuItem>(MAX_FILES);
//        folderMenuList=new ArrayList<JMenuItem>(MAX_FOLDERS);
        
        fileMenu.insertSeparator(fileMenu.getItemCount()-2);
        fileMenu.insertSeparator(fileMenu.getItemCount()-2); // we put stuff after this
        buildMenu();
    }
    JSeparator fileSep=new JSeparator(), folderSep=new JSeparator();
    
    /** inserts the file items in the File menu */
    void buildMenu(){
        for(JMenuItem i:fileMenuList){
            fileMenu.remove(i);
        }
        fileMenuList.clear();
//        folderMenuList.clear();
        int filePos=fileMenu.getItemCount()-3; // right above sep/exit item
        int folderPos=filePos+1;
        int fileIndex=0;
        int folderIndex=0;
        for(File f:fileList){
            // add files
            if(f==null){
                System.err.println("RecentFiles.buildMenu(): null File in fileList");
                continue;
            }
            if(f.isFile()){
                String name=f.getName();
                if(fileIndex<9){
                    name=Integer.toString(fileIndex+1)+" "+f.getName();
                }
                JMenuItem item=new JMenuItem(name);
                item.setActionCommand(f.getPath());
                item.setToolTipText(f.getPath());
                item.addActionListener(listener);
                item.setMnemonic(item.getText().charAt(0));
                fileMenuList.add(item);
                fileMenu.insert(item, fileMenu.getItemCount()-3);
                fileIndex++;
                if(fileIndex>MAX_FILES) break;
            }
        }
        for(File f:fileList){
            // add folders
            if(f==null){
                System.err.println("RecentFiles.buildMenu(): null File in fileList");
                continue;
            }
            if(f.isDirectory()){
                String name=f.getName();
                JMenuItem item=new JMenuItem(name+File.separator);
                item.setActionCommand(f.getPath());
                item.setToolTipText(f.getPath());
                item.addActionListener(listener);
                fileMenuList.add(item);
                fileMenu.insert(item, fileMenu.getItemCount()-2);
                folderIndex++;
                if(folderIndex>MAX_FOLDERS) break;
            }
        }
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
//                System.out.println("********* num recent files "+fileList.size());
            }else{
//                System.out.println("no recent files");
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
    
    void removeLastFile(){
        File last=null;
        int index=MAX_FILES;
        for(File f:fileList){
            if(f.isFile()){
                if(--index==0) last=f;
            }
        }
        if(last!=null) {
//            log.info("removing last file "+last);
            fileList.remove(last);
        }
    }
    
    void removeLastFolder(){
        File last=null;
        int index=MAX_FOLDERS;
        for(File f:fileList){
            if(f.isDirectory()){
                if(--index==0) last=f;
            }
        }
        if(last!=null) {
//            log.info("removing last folder "+last);
            fileList.remove(last);
        }
    }
    
    /** adds files and their containing folders to list of recent files. List is pruned if too long.
     @param f a file to add
     */
    public void addFile(File f){
//        log.info("adding recent file "+f.getName());
        if(f==null){
            log.warning("RecentFiles.addFile(): tried to add null File");
            return;
        }
        if(fileList.contains(f)) {
            fileList.remove(f);
            fileList.add(0,f); // put to head of list
//            log.info("recentfiles moved "+f.getName()+" to head of list");
        }else{
            fileList.add(0, f);
//            log.info("recent files added "+f.getName()+" to head of list");
            removeLastFile();
//            if(fileList.size()>MAX_FILES+MAX_FOLDERS){
//                log.info("removing file "+fileList.get(MAX_FILES-1));
//                fileList.remove(MAX_FILES-1);
////                System.out.println("recent files pruned list to max size of "+MAX_FILES);
//            }
        }
        
        // add folder to list
        File parentFile=f.getParentFile();
        if(parentFile==null){
            log.warning("RecentFiles.addFile(): parent of File "+f+" is null, not adding directory");
        }else{
            if(fileList.contains(parentFile)) {
                fileList.remove(parentFile);
                fileList.add(0,parentFile); // put to head of list
//                System.out.println("recenffiles moved "+f.getName()+" to head of list");
            }else{
                fileList.add(0, parentFile);
//                System.out.println("recent files added "+f.getName()+" to head of list");
                removeLastFolder();
//                if(fileList.size()>MAX_FILES+MAX_FOLDERS){
//                    log.info("removing folder "+fileList.get(MAX_FILES-1));
//                    fileList.remove(fileList.size()-1);
////                    System.out.println("recent files pruned list to max size of "+MAX_FILES);
//                }
            }
        }
        pruneList();
        putPrefs();
        buildMenu();
    }
    
    public void removeFile(File f){
        if(!fileList.contains(f)) return;
        fileList.remove(f);
//        System.out.println("recent files removeFile("+f.getName()+")");
        putPrefs();
        buildMenu();
    }
    
    // prunes list to MAX_FILES and MAX_FOLDERS
    private void pruneList(){
        ArrayList<File> removeList=new ArrayList<File>();
        int nfiles=0, ndirs=0;
        for(File f:fileList){
            if(f.isFile()){
                nfiles++;
                if(nfiles>MAX_FILES){
                    removeList.add(f);
//                     log.info("removing "+nfiles+"'th file "+f+" because MAX_FILES="+MAX_FILES);
               }
            }else if(f.isDirectory()){
                ndirs++;
                if(ndirs>MAX_FOLDERS){
                    removeList.add(f);
//                    log.info("removing "+ndirs+"'th folder "+f+" because MAX_FOLDERS="+MAX_FOLDERS);
                }
            }else{
                removeList.add(f);
                log.warning("removing nonexistant file "+f);
            }
        }
        fileList.removeAll(removeList);
    }
    
    /** Returns most recent folder
     * 
     * @return most recent folder, or null if there is none
     */
    public File getMostRecentFolder(){
        if(fileList==null || fileList.isEmpty()) return null;
        for(File f:fileList){
            if(f.isDirectory()) return f;
        }
        return null;
    }
    
    /** Returns most recent file
     * 
     * @return most recent file, or null if there is none
     */
    public File getMostRecentFile(){
        if(fileList==null || fileList.isEmpty()) return null;
        for(File f:fileList){
            if(f.isFile()) return f;
        }
        return null;   
    }
    
    
}
