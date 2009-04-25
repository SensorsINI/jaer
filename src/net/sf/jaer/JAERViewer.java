/*
 * JAERViewer.java
 *
 * Created on January 30, 2006, 10:41 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package net.sf.jaer;
import java.util.logging.Level;
import net.sf.jaer.JAERViewer.ToggleLoggingAction;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.eventio.*;
import net.sf.jaer.graphics.*;
import net.sf.jaer.util.*;
import java.awt.AWTEvent;
import java.awt.Toolkit;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.prefs.*;
import javax.swing.*;
/**
 * Used to show multiple chips simultaneously. A single viewer is launched initially, with a default chip. New ones can be constructed from the File menu.
 * @author tobi
 */
public class JAERViewer {
    static Preferences prefs;
    static Logger log;
    /** Can be used to globallhy display data */
    static public JAERDataViewer globalDataViewer=new JAERDataViewer("Global data viewer");
    private ArrayList<AEViewer> viewers=new ArrayList<AEViewer>();
    private boolean syncEnabled=prefs.getBoolean("JAERViewer.syncEnabled", true);
    ArrayList<AbstractButton> syncEnableButtons=new ArrayList<AbstractButton>(); // list of all viewer sync enable buttons, used here to change boolean state because this is not property of Action that buttons understand
    ToggleSyncEnabledAction toggleSyncEnabledAction=new ToggleSyncEnabledAction();
    volatile boolean loggingEnabled=false;
    //private boolean electricalTimestampResetEnabled=prefs.getBoolean("JAERViewer.electricalTimestampResetEnabled",false);
//    private String aeChipClassName=prefs.get("JAERViewer.aeChipClassName",Tmpdiff128.class.getName());
    private WindowSaver windowSaver; // TODO: encapsulate
    private boolean playBack=false;
    private static List<String> chipClassNames; // cache expensive search for all AEChip classes
    //some time variables for timing across threads
    static public long globalTime1,  globalTime2,  globalTime3;

    /** Creates a new instance of JAERViewer */
    public JAERViewer() {
        Thread.UncaughtExceptionHandler handler=new LoggingThreadGroup("jAER UncaughtExceptionHandler");
        Thread.setDefaultUncaughtExceptionHandler(handler);
//        Thread test=new Thread("UncaughtExceptionHandler Test"){
//            public void run(){
//                try {
//                    Thread.sleep(2000);
//                    throw new RuntimeException("test exception 1");
//                } catch (InterruptedException ex) {
//                }
//            }
//        };
//        test.start();
//        Thread test2=new Thread("UncaughtExceptionHandler Test2"){
//            public void run(){
//                try {
//                    Thread.sleep(5000);
//                    throw new RuntimeException("test exception 2");
//                } catch (InterruptedException ex) {
//                }
//            }
//        };
//        test2.start();
//        log.addHandler(handler);
        log.info("java.vm.version="+System.getProperty("java.vm.version"));
        windowSaver=new WindowSaver(this, prefs);
        Toolkit.getDefaultToolkit().addAWTEventListener(windowSaver, AWTEvent.WINDOW_EVENT_MASK); // adds windowSaver as JVM-wide event handler for window events
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                AEViewer v=new AEViewer(JAERViewer.this); // this call already adds the viwer to our list of viewers
//                v.pack();
                v.setVisible(true);
            }
        });
        try {
            // Create temp file.
            File temp=new File("JAERViewerRunning.txt");

            // Delete temp file when program exits.
            temp.deleteOnExit();

            // Write to temp file
            BufferedWriter out=new BufferedWriter(new FileWriter(temp));
            out.write("caviar viewer started");
            out.close();
        } catch(IOException e) {
            log.warning(e.getMessage());
        }
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                log.info("JAERViewer shutdown hook - saving window settings");
                if(windowSaver!=null) {
                    try {
                        windowSaver.saveSettings();
                    } catch(IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    /** The main launcher for AEViewer's. 
    @param args the first argument can be a recorded AE data filename (.dat) with full path; the viewer will play this file
     */
    public static void main(String[] args) {
        //redirect output to DataViewer window
        // should be before any logger is initialized 
//        globalDataViewer.redirectStreams(); // tobi removed because AEViewerConsoleOutputFrame replaces this logging output

        //init static fields
        prefs=Preferences.userNodeForPackage(JAERViewer.class);
        log=Logger.getLogger("graphics");

        // cache expensive search for all AEChip classes

        if(System.getProperty("os.name").startsWith("Windows")) {
            Runnable runnable=new Runnable() {
                public void run() {
                    chipClassNames=SubclassFinder.findSubclassesOf(AEChip.class.getName());
                }
            };
            Thread t=new Thread(runnable);
            t.setName("subclassFinder");
            t.setPriority(Thread.MIN_PRIORITY);
            t.start();

            String exepath=System.getProperty("exepath");
            if(exepath!=null) {
                System.out.println("exepath (set from JSmooth) = "+exepath);
            }
        }
        if(args.length>0) {
            log.info("starting with args[0]="+args[0]);
            final File f=new File(args[0]);
            try {
                new JAERViewer().getPlayer().startPlayback(f);
            } catch(IOException e) {
                JOptionPane.showMessageDialog(null, e);
            }
        } else {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    new JAERViewer();
                }
            });
        }
    }

    public void addViewer(AEViewer aEViewer) {
        getViewers().add(aEViewer);
        aEViewer.addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                if(evt.getSource() instanceof AEViewer) {
                    log.info("removing "+evt.getSource()+" from list of AEViewers");
                    removeViewer((AEViewer) evt.getSource());
                }
            }
        });
        buildMenus(aEViewer);
    }

    public void saveSetup(File f) {
        JOptionPane.showMessageDialog(null, "Not implemented yet");

//        File setupFile;
//            JFileChooser fileChooser=new JFileChooser();
//            String lastFilePath=prefs.get("JAERViewer.lastFile",""); // get the last folder
//            File cwd=new File(lastFilePath);
//            fileChooser.setCurrentDirectory(cwd); // sets the working directory of the chooser
//            int retValue=fileChooser.showOpenDialog(null);
//            if(retValue==JFileChooser.APPROVE_OPTION){
//                try{
//                    setupFile=fileChooser.getSelectedFile();
////                    if(lastFile!=null) recentFiles.addFile(lastFile);
//
//                    lastFilePath=setupFile.getPath();
//                    prefs.put("JAERViewer.lastFile",lastFilePath);
//                }catch(FileNotFoundException fnf){
//                    fnf.printStackTrace();
//                }
//            }
//            fileChooser=null;
    }

    public void loadSetup(File f) {
        JOptionPane.showMessageDialog(null, "Not implemented yet");
    }

    void buildMenus(AEViewer v) {
//        log.info("building AEViewer sync menus");
        JMenu m=v.getFileMenu();

        ToggleLoggingAction action=new ToggleLoggingAction(v);
        v.getLoggingButton().setAction(action);
        v.getLoggingMenuItem().setAction(action);

        // adds to each AEViewers syncenabled check box menu item the toggleSyncEnabledAction
        AbstractButton b=v.getSyncEnabledCheckBoxMenuItem();
        b.setAction(toggleSyncEnabledAction);
        syncEnableButtons.add(b);   // we need this stupid list because java 1.5 doesn't have Action property to support togglebuttons selected state (1.6 adds it)
        b.setSelected(isSyncEnabled());

        boolean en=true; //viewers.size()>1? true:false;
        for(AbstractButton bb : syncEnableButtons) {
            bb.setEnabled(en);
        }

//        if(en==false) syncEnableButtons.get(0).setSelected(false); // disable sync if there is only one viewer
    }

    public void removeViewer(AEViewer v) {
        if(getViewers().remove(v)==false) {
            log.warning("JAERViewer.removeViewer(): "+v+" is not in viewers list");
        } else {
            syncEnableButtons.remove(v.getSyncEnabledCheckBoxMenuItem());
        }
        boolean en=true; //viewers.size()>1? true:false;
        for(AbstractButton bb : syncEnableButtons) {
            bb.setEnabled(en);
        }
    }

    /** @return collection of viewers we manage */
    public ArrayList<AEViewer> getViewers() {
        return viewers;
    }

    public int getNumViewers() {
        return viewers.size();
    }
    File indexFile=null;
    final String indexFileNameHeader="JAERViewer-";
    final String indexFileSuffix=AEDataFile.INDEX_FILE_EXTENSION;
    DateFormat loggingFilenameDateFormat=new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ssZ");

    String getDateString() {
        String dateString=loggingFilenameDateFormat.format(new Date());
        return dateString;
    }

    File createIndexFile(String path) {
        String indexFileName=indexFileNameHeader+getDateString()+indexFileSuffix;
        log.info("createIndexFile "+path+File.separator+indexFileName);
        indexFile=new File(path+File.separator+indexFileName);
        if(indexFile.isFile()) {
            throw new RuntimeException("index file "+indexFile+" already exists");
        }
        return indexFile;
    }

    public void startSynchronizedLogging() {
        log.info("starting synchronized logging");

        if(viewers.size()>1) {// && !isElectricalSyncEnabled()){
            zeroTimestamps();
        } else {
            // log.info("not zeroing all board timestamps because they are specified electrically synchronized");
            }
        for(AEViewer v : viewers) {
            File f=v.startLogging();

        }

        loggingEnabled=true;


    }

    public void stopSynchronizedLogging() {
        log.info("stopping synchronized logging");
        FileWriter writer=null;
        boolean writingIndex=false;
        // pause all viewers
        viewers.get(0).aePlayer.pause();

        try {
            for(AEViewer v : viewers) {
                File f=v.stopLogging(false);

                if(f.exists()) { // if not cancelled
                    if(viewers.size()>1) {

                        if(writer==null) {
                            writingIndex=true;
                            createIndexFile(f.getParent());
                            writer=new FileWriter(indexFile);
                        }
                        writer.write(f.getName()+"\n");//  .getPath()+"\n");
                    }
                }
            }
            if(viewers.size()>1&&writingIndex) {
                writer.close();
            }
            for(AEViewer v : viewers) {
                v.getRecentFiles().addFile(indexFile);
            }
            if(indexFile!=null) {
                JOptionPane.showMessageDialog(null, "Saved index file "+indexFile.getAbsolutePath());
            }
        } catch(IOException e) {
            System.err.println("creating index file "+indexFile);
            e.printStackTrace();
        }
        // resume all viewers
        viewers.get(0).aePlayer.resume();


        loggingEnabled=false;
    }

    public void toggleSynchronizedLogging() {
        //TODO - unchecking synchronized logging in AEViewer still comes here and logs sychrnoized
        loggingEnabled=!loggingEnabled;
        if(loggingEnabled) {
            startSynchronizedLogging();
        } else {
            stopSynchronizedLogging();
        }
    }

    public void zeroTimestamps() {
//        if(!isElectricalSyncEnabled()){
        log.info("JAERViewer.zeroTimestamps(): zeroing timestamps on all AEViewers");
        for(AEViewer v : viewers) {
            v.zeroTimestamps();
        }
//        }else{
//            System.err.println("JAERViewer.zeroTimestamps(): electricalSyncEnabled, not resetting all viewer device timestamps");
//        }
    }
//    public class ViewerAction extends AbstractAction{
//        AEViewer viewer;
//        public ViewerAction(AEViewer viewer){
//            this.viewer=viewer;
//        }
//        public void actionPerformed(ActionEvent e){
//            throw new UnsupportedOperationException("this Action doesn't do anything, use subclass");
//        }
//    }
    File logIndexFile;
    /** this action toggles logging, possibily for all viewers depending on switch */
    public class ToggleLoggingAction extends AbstractAction {
        AEViewer viewer; // to find source of logging action

        public ToggleLoggingAction(AEViewer viewer) {
            this.viewer=viewer;
            putValue(NAME, "Start logging");
            putValue(SHORT_DESCRIPTION, "Controls synchronized logging on all viewers");
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_L, 0));
            putValue(MNEMONIC_KEY, new Integer(KeyEvent.VK_L));
        }

        public void actionPerformed(ActionEvent e) {
//            log.info("JAERViewer.ToggleLoggingAction.actionPerformed");
            if(isSyncEnabled()) {
                toggleSynchronizedLogging();
                if(loggingEnabled) {
                    putValue(NAME, "Stop logging");
                } else {
                    if(viewers.get(0).getPlayMode()==AEViewer.PlayMode.PLAYBACK) {
                        putValue(NAME, "Start Re-logging");
                    } else {
                        putValue(NAME, "Start logging");
                    }
                }
                log.info("loggingEnabled="+loggingEnabled);
            } else {
                viewer.toggleLogging();
            }
        }
    }
    public class ToggleSyncEnabledAction extends AbstractAction {
        public ToggleSyncEnabledAction() {
            String name="Synchronize viewers";
            putValue(NAME, name);
            putValue(SHORT_DESCRIPTION, "When enabled, viewer logging and playback are synchronized");
        }

        public void actionPerformed(ActionEvent e) {
            log.info("JAERViewer.ToggleSyncEnabledAction.actionPerformed");
            setSyncEnabled(!isSyncEnabled());
            for(AbstractButton b : syncEnableButtons) {
                b.setSelected(isSyncEnabled());
            }
        }
    }

    /** Controls whether multiple viewers are synchronized for logging and playback.
     *
     * @return true if sychronized.
     */
    public boolean isSyncEnabled() {
        return syncEnabled;
    }

    /** Controls whether multiple viewers are synchronized for logging and playback.
     *
     * @param syncEnabled true to be synchronized.
     */
    public void setSyncEnabled(boolean syncEnabled) {
        this.syncEnabled=syncEnabled;
        prefs.putBoolean("JAERViewer.syncEnabled", syncEnabled);
    }
    private SyncPlayer player=new SyncPlayer();
    /** Synchronized playback and control of such playback is not totally straightforward because of the bursty nature of AER - there are no frames to synchronize
     * on and you obviously cannot sync on event number.
     *<p>
     *This class sychronizes multiple viewer players. It assumes one is the master (whichever the user controls) and coordinates viewers synchrnously
     * so that all viewers can present a consistent view.
     *<p>
     * To achieve this, each viewer encapsulates its playback functionality on an AEPlayer
     *inner class instance that is controlled either by the Viewer GUI (the user) or by JAERViewer through its own SyncPlayer.
     *
     * The Players share a common interface so this is achieved by returning the correct object within AEViewer depending on whether the views are synchronized.
     *
     *<p>
     *The individual threads doing the rendering in each AEViewer are barricaded by the CyclicBarrier here. Each time an AEViewer asks for synchronized events, the call
     *here to SyncPlayer blocks until all threads asking for events have gotten them. Then rendering in each thread happens normally.
     *
     *
     */
    public class SyncPlayer implements AEPlayerInterface, PropertyChangeListener {
        boolean flexTimeEnabled=false; // true to play constant # of events
        private int samplePeriodUs=20000; // ms/sample to shoot for
        private int sampleNumEvents=2048;
        boolean fileInputEnabled=false;
        JFileChooser fileChooser;
        int currentTime=0;
//        boolean paused=false;
        File lastFile;
        volatile CyclicBarrier barrier; // used to sync up viewers for playback
        int numPlayers=0;
        ArrayList<AEViewer> playingViewers=new ArrayList<AEViewer>();

        public boolean isChoosingFile() {
            return (fileChooser!=null&&fileChooser.isVisible());
        }

        /** this call shows a file chooser for index files: files containing information on which AE data files go together */
        public void openAEInputFileDialog() {
            fileChooser=new JFileChooser();
            IndexFileFilter filter=new IndexFileFilter();
            String lastFilePath=prefs.get("JAERViewer.lastFile", ""); // get the last folder
            lastFile=new File(lastFilePath);
            fileChooser.setFileFilter(filter);
            fileChooser.setCurrentDirectory(lastFile); // sets the working directory of the chooser
//            boolean wasPaused=isPaused();
            setPaused(true);
            int retValue=fileChooser.showOpenDialog(null);
            if(retValue==JFileChooser.APPROVE_OPTION) {
                try {
                    lastFile=fileChooser.getSelectedFile();
//                    if(lastFile!=null) recentFiles.addFile(lastFile);
                    startPlayback(lastFile);
                    lastFilePath=lastFile.getPath();
                    prefs.put("JAERViewer.lastFile", lastFilePath);
                } catch(IOException fnf) {
                    fnf.printStackTrace();
                }
            }
            fileChooser=null;
            setPaused(false);
        }

        /** @return a simple class name (no package header) parsed from a .dat filename as the part before the "-"
         */
        String parseClassnameFromFilename(String filename) {
            StringBuilder className=new StringBuilder();
            int i=0;
            while(i<filename.length()&&filename.charAt(i)!='-') {
                className.append(filename.charAt(i));
                i++;
            }
            return className.toString();
        }

        private Class getChipClassFromSimpleName(String className) {
            Class deviceClass=null;
            for(String s : chipClassNames) {
                if(s.endsWith(className)) {
                    try {
                        deviceClass=Class.forName(s);
                        log.info("found class "+deviceClass+" for className "+className);
                        break;
                    } catch(ClassNotFoundException e) {
                        log.warning(e.getMessage());
                    }
                }
            }
            if(deviceClass==null) {
                log.warning("no chip class for chip className="+className);
            }
            return deviceClass;
        }

        synchronized void makeBarrier() {
            if(numPlayers<1) {
                log.warning("cannot make barrier for "+numPlayers+" viewers - something is wrong");

                log.warning("disabling sychronized playback because probably multiple viewers are active but we are not playing set of sychronized files");
                toggleSyncEnabledAction.actionPerformed(null); // toggle all the viewers syncenabled menu item
//               JOptionPane.showMessageDialog(null,"Disabled sychronized playback because files are not part of sychronized set"); 
                return;
            }
            barrier=new CyclicBarrier(numPlayers, new Runnable() {
                public void run() {
                    // this is run after await synchronization; it updates the time to read events from each AEInputStream
//                        log.info(Thread.currentThread()+" resetting barrier");
                    barrier.reset();
                    setTime(getTime()+getSamplePeriodUs());
//                        log.info(Thread.currentThread()+" reset barrier");
                }
            });
        }

        /** this call starts playback on the supplied index file, starting playback in each viewer appropriately.
        If the file is not an index file, then the first available viewer is called to start playback of the data file.
         * @param indexFile the .index file containing the filenames to play
         */
        public void startPlayback(File indexFile) throws IOException {
            log.info("indexFile="+indexFile);

            stopPlayback();

            // first check to make sure that index file is really an index file, in case a viewer called it
            if(!indexFile.getName().endsWith(AEDataFile.INDEX_FILE_EXTENSION)) {
                log.warning(indexFile+" doesn't appear to be an .index file, opening it in the first viewer and setting sync enabled false");
                AEViewer v=viewers.get(0);
                if(isSyncEnabled()) {
                    JOptionPane.showMessageDialog(v, "<html>You are opening a single data file so synchronization has been disabled<br>To reenable, use File/Synchronization enabled</html>");
//                    setSyncEnabled(false);
                    toggleSyncEnabledAction.actionPerformed(null); // toggle all the viewers syncenabled menu item
                }
                v.aePlayer.startPlayback(indexFile);
                return;
            }


            playingViewers.clear();

            // this map will map from the data files to the viewer windows
            HashMap<File, AEViewer> map=new HashMap<File, AEViewer>();
            setTime(0);
            BufferedReader reader;
            // files are in same folder as index file
            try {
                reader=new BufferedReader(new FileReader(indexFile));
                String filename;
                ArrayList<AEViewer> dontUseAgain=new ArrayList<AEViewer>();

                // for each line in index file, get the data file, class of chip (from filename) and find or make a viewer window for it
                while((filename=reader.readLine())!=null) {
//                    log.info("JAERViewer.startPlayback(): trying to open AE file "+filename);

                    // find chip classname from leading part of e.g. Tmpdiff128-2006-02-16T11-51-13+0100-0.dat up to '-'
//                    log.info("***********JAERViewer.SyncPlayer.startPlayback(): filename "+filename+" indicates chip class is "+className.toString());

                    // now get the data file
                    File file=new File(indexFile.getParentFile(), filename); // this is File object for the data file
                    if(!file.isFile()) {
                        JOptionPane.showMessageDialog(null, file+" from index file doesn't exist");
                        reader.close();
                        return;
                    }

                    // for each filename in the index file, find the right viewer window.
                    String className=parseClassnameFromFilename(filename);
                    AEViewer vToUse=null;
                    for(AEViewer v : viewers) {
                        //  a viewer is acceptable if its window title name starts with the same classname as the filename
                        // or if it is a virgin window named "AEViewer"
                        // AND if it hasn't already been assigned to some file
                        String windowTitle=v.getTitle();
//                        log.info("...AEViewer has window title "+windowTitle);
                        if((v.getTitle().startsWith(className)||v.getTitle().startsWith("AEViewer"))&&!dontUseAgain.contains(v)) {
                            vToUse=v; // always gets first one...
                            dontUseAgain.add(v); // don't use this one again
//                            log.info("... viewer "+v.getTitle()+" can be used for "+file);
                            break;
                        }
                    }

                    // if there is no acceptable window, create a new AEViewer for this file
                    if(vToUse==null) {
                        log.info("JAERViewer.SyncPlayer.startPlayback(): no window found for "+filename+", making new one");
                        vToUse=new AEViewer(JAERViewer.this);
                        dontUseAgain.add(vToUse);
                        vToUse.setVisible(true);
                    }
                    map.put(file, vToUse);
                    log.info("JAERViewer.SyncPlayer.startPlayback(): put map entry "+file+" -> "+vToUse);

                } // foreach data file

                if(reader!=null) {
                    reader.close();
                }

                // now make a cyclic barrier to synchronize the players
                numPlayers=map.size();
                log.info(Thread.currentThread()+" constructing barrier");
                makeBarrier();

                // now for each file, start playback in the correct window
                // also set the chip class for the viewer as parsed from the filename
                for(java.util.Map.Entry<File, AEViewer> e : map.entrySet()) {
                    AEViewer v=e.getValue();
                    File f=e.getKey();
                    log.info("Starting playback of File "+f+" in viewer "+v.getTitle());
                    String className=parseClassnameFromFilename(f.getName());
                    Class chipClass=getChipClassFromSimpleName(className);
//                    AEPlayerInterface p=v.getAePlayer(); // this resolves to this play (SyncPlayer), but we want the viewer local player
                    v.setAeChipClass(chipClass);
                    v.aePlayer.stopPlayback();
                    v.aePlayer.startPlayback(e.getKey());
                    v.aePlayer.getAEInputStream().getSupport().addPropertyChangeListener("rewind", this);
                    playingViewers.add(v);
                }

                initTime();
            } catch(FileNotFoundException e) {
                e.printStackTrace();
            } catch(IOException e) {
                e.printStackTrace();
            }
            setPlayBack(true);
        }

        /** stops playback on all players */
        public void stopPlayback() {
            log.info(Thread.currentThread()+" stopping playback");
            for(AEViewer v : viewers) {
                v.aePlayer.stopPlayback();
//                try {
//                    wait(100);
//                } catch (InterruptedException ex) {
//                    ex.printStackTrace();
//                }
            }
            setPlayBack(false);
        }

        // iniitalizes time pointer for all viewers by getting first timestep for each viewer's ae input stream
        // and setting global time to minimum value
        void initTime() {
            int minTime=Integer.MAX_VALUE;
            for(AEViewer v : viewers) {
                try {
                    int t=v.aePlayer.getAEInputStream().getFirstTimestamp();
                    if(t<minTime) {
                        minTime=t;
                    }
                } catch(NullPointerException e) {
                    log.warning("NullPointerException when initializing time for viewer "+v);
                }
            }
            log.info("JAERViewer.SyncPlayer.initialized time min value found: "+minTime);
            setTime(minTime);
        }

        /** rewinds all players */
        public void rewind() {
            for(AEViewer v : viewers) {
                v.aePlayer.rewind();
            }
            initTime();
        }

        /** pauses all players */
        public void pause() {
            setPaused(true);
        }

        /** resumes all players */
        public void resume() {
            setPaused(false);
        }
        volatile private boolean paused=false; // multiple threads will access

        /** returns true if the viewers are paused */
        public boolean isPaused() {
//            log.info("paused="+paused);
            return paused;
        }

        /**
         *pauses/unpauses all viewers
         */
        public void setPaused(boolean yes) {
            paused=yes;
//            log.info("JAERViewer.SyncPlayer.setPaused("+yes+")");
//            for(AEViewer v:viewers){
//                v.aePlayer.setPaused(yes);
//            }
        }
        final static int SYNC_PLAYER_TIMEOUT_SEC=30;

        /** returns next packet of AE data to the caller, which is a particular AEPlayer inner class of AEViewer.
         * The packet is sychronized in event time if sychronized playback is enabled.
         * @return a raw packet of events
         */
        public AEPacketRaw getNextPacket(AEPlayerInterface player) {
            // each player will call in their own thread the getNextPacket and then return the ae to be rendered here,
            // AFTER the blocking await call that synchronizes them.
            // if the viewer is paused during the await call, then we may get a timeout here.
            // therefore we do not stop playback if the viewers are paused, only very slowly step along
            AEPacketRaw ae=player.getNextPacket(player);
            if(numPlayers==1) {
                return ae;
            }
            try {
//                log.info(Thread.currentThread()+" starting wait on barrier, number threads already waiting="+barrier.getNumberWaiting());
                if(barrier==null) {
                    makeBarrier();
                }
                if(barrier==null) {
                    // still don't have barrier for some reason so just return null
                    return null;
                }
                int awaitVal=barrier.await(SYNC_PLAYER_TIMEOUT_SEC, TimeUnit.SECONDS);
//                log.info(Thread.currentThread()+" got awaitVal="+awaitVal);
            } catch(InterruptedException e) {
                log.warning(Thread.currentThread()+" interrupted"); //e.printStackTrace();
//                stopPlayback();
            } catch(BrokenBarrierException ignore) {
//                System.err.println("Thread "+Thread.currentThread()+" broken barrier exception "+e);
//                e.printStackTrace();
//                barrier=new CyclicBarrier(numPlayers);
            } catch(TimeoutException e) {
                if(!isPaused()) {
                    log.warning(e+": stopping playback for all viewers");
                    stopPlayback();
                }
            }
            return ae;
        }

        public void toggleDirection() {
            setSampleNumEvents(getSampleNumEvents()*-1);
            setSamplePeriodUs(getSamplePeriodUs()*-1);
        }

        public void speedUp() {
            setSampleNumEvents(getSampleNumEvents()*2);
            setSamplePeriodUs(getSamplePeriodUs()*2);
        }

        public void slowDown() {
            setSampleNumEvents(getSampleNumEvents()/2);
            if(getSampleNumEvents()==0) {
                setSampleNumEvents(1);
            }
            setSamplePeriodUs(getSamplePeriodUs()/2);
            if(getSamplePeriodUs()==0) {
                setSamplePeriodUs(1);
            }
            if(Math.abs(getSampleNumEvents())<1) {
                setSampleNumEvents((int) Math.signum(getSampleNumEvents()));
            }
            if(Math.abs(getSamplePeriodUs())<1) {
                setSamplePeriodUs((int) Math.signum(getSamplePeriodUs()));
            }
        }

        void toggleFlexTime() {
            throw new UnsupportedOperationException();
        }

        public boolean isPlayingForwards() {
            return getSamplePeriodUs()>0;
        }

        public float getFractionalPosition() {
            throw new UnsupportedOperationException();
        }

        public void mark() throws IOException {
            for(AEViewer v : viewers) {
                v.aePlayer.mark();
            }
        }

        public int position(AEFileInputStreamInterface stream) {
            return stream.position();
        }

        public int position() {
            throw new UnsupportedOperationException();
        }

        public void position(int event, AEFileInputStreamInterface stream) {
            stream.position(event);
        }

        public AEPacketRaw readPacketByNumber(int n) throws IOException {
            throw new UnsupportedOperationException();
        }

        public AEPacketRaw readPacketByNumber(int n, AEFileInputStreamInterface stream) throws IOException {
            return stream.readPacketByNumber(n);
        }

        public AEPacketRaw readPacketByTime(int dt) throws IOException {
            throw new UnsupportedOperationException();
        }

        public AEPacketRaw readPacketByTime(int dt, AEFileInputStreamInterface stream) throws IOException {
            return stream.readPacketByTime(dt);
        }

        public long size(AEFileInputStream stream) {
            return stream.size();
        }

        public long size() {
            throw new UnsupportedOperationException();
        }

        public void unmark() {
            for(AEViewer v : viewers) {
                v.aePlayer.unmark();
            }
        }

        public void setFractionalPosition(float frac) {
            for(AEViewer v : viewers) {
                v.aePlayer.setFractionalPosition(frac);
            }
        }

        /** Sets all viewers to the same time.
         * @param time current playback time relative to start in us */
        public void setTime(int time) {
            currentTime=time;
//            log.info("JAERViewer.SyncPlayer.setTime("+time+")");
            try {
                for(AEViewer v : playingViewers) {
                    v.aePlayer.setTime(getTime()); // we set the individual players (note do not use getAePlayer to avoid infinite recursion here)
                }
            } catch(ConcurrentModificationException e) {
                log.warning("couldn't set time on a viewer because of exception "+e.getMessage());
            }
        }

        /** @return current playback time relative to start in us */
        public int getTime() {
            return currentTime;
        }

        public AEPacketRaw getNextPacket() {
            throw new UnsupportedOperationException();
        }

        public int getSamplePeriodUs() {
            return samplePeriodUs;
        }

        public void setSamplePeriodUs(int samplePeriodUs) {
            this.samplePeriodUs=samplePeriodUs;
        }

        public int getSampleNumEvents() {
            return sampleNumEvents;
        }

        public void setSampleNumEvents(int sampleNumEvents) {
            this.sampleNumEvents=sampleNumEvents;
        }

        /** always returns null,  bince this is a sync player for multiple viewers */
        public AEFileInputStream getAEInputStream() {
            return null;
        }

        /** JAERViewer gets PropertyChangeEvent from the AEPlayer in the AEViewers. This method presently only logs this event.
         */
        public void propertyChange(PropertyChangeEvent evt) {
            if(evt.getPropertyName().equals("rewind")) { // comes from AEFileInputStream when file reaches end and AEViewer rewinds the file
                for(AEViewer v : viewers) {
                    v.getChip().getRenderer().resetFrame(v.getChip().getRenderer().getGrayValue()); // reset accumulation on all viewers on rewind, to allow accumulation on one of several players
                }

                log.info("rewind PropertyChangeEvent received by "+this+" from "+evt.getSource());
//                for(AEViewer v:viewers){
//                    if(v!=evt.getSource()) v.aePlayer.rewind();
//                }
            }
        }

        public void doSingleStep(AEViewer viewer) {
            for(AEViewer v : viewers) {
//                if(v!=viewer)
                v.doSingleStep();
//                doSingleStepEnabled=true;
            }
            setPaused(true);
//            log.info(this+" doSingleStep");
//            throw new UnsupportedOperationException("Not yet implemented");
        }
//        public void singleStepDone(){
//            doSingleStepEnabled=false;
//            log.info("singleStepDone");
//        }
//        public boolean isSingleStep(){
//            return doSingleStepEnabled;
//        }
    } // SyncPlalyer

    public void pause() {
    }

    public SyncPlayer getPlayer() {
        return player;
    }

    /** @return true if boards are electrically connected and this connection synchronizes the local timestamp value */
    /*public boolean isElectricalSyncEnabled(){
    return electricalTimestampResetEnabled;
    }*/
    /* public void setElectricalSyncEnabled(boolean b) {
    electricalTimestampResetEnabled=b;
    prefs.putBoolean("JAERViewer.electricalTimestampResetEnabled",electricalTimestampResetEnabled);
    for(AEViewer v:viewers){
    v.getElectricalSyncEnabledCheckBoxMenuItem().setSelected(b);
    }
    }*/
    public boolean isPlayBack() {
        return playBack;
    }

    public void setPlayBack(boolean playBack) {
        this.playBack=playBack;
    }
}
