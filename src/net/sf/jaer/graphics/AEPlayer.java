package net.sf.jaer.graphics;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.eventio.AEDataFile;
import net.sf.jaer.eventio.AEFileInputStream;
import net.sf.jaer.eventio.AEFileInputStreamInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.util.DATFileFilter;
import net.sf.jaer.util.IndexFileFilter;
/**
 * Handles file input of AEs to control the number of
 * events/sample or period of time in the sample, etc.
 * It handles the file input stream, opening a dialog box, etc.
 * It also handles synchronization of different AEViewers as follows
 * (this refers to multiple-AEViewer time-locked playback sychronization, not
 * java object locking):
 * <p>
 * If the viewer is not synchronized, then all calls from the GUI
 * are passed directly
 * to this instance of AEPlayer. Thus local control always happens.
 * <p>
 * If the viewer is synchronized, then all GUI calls pass
 * instead to the JAERViewer instance that contains
 * (or started) this viewer. Then the JAERViewer AEPlayer
 * calls all the viewers to take the player action (e.g. rewind,
 * go to next slice, change direction).
 * <p>
 * Thus whichever controls the user uses to control playback,
 * the viewers are all sychronized properly without recursively.
 * The "master" is indicated by the GUI action,
 * which routes the request either to this instance's AEPlayer
 * or to the JAERViewer AEPlayer.
 */
public class AEPlayer extends AbstractAEPlayer implements AEFileInputStreamInterface{
    AEViewer outer;
    boolean fileInputEnabled = false;
    JFileChooser fileChooser;

    public AEPlayer (AEViewer viewer,AEViewer outer){
        super(viewer);
        this.outer = outer;
    }

    private boolean isSyncEnabled(){
        return viewer.getJaerViewer().isSyncEnabled();
    }

    public boolean isChoosingFile (){
        return fileChooser != null && fileChooser.isVisible();
    }
    FileFilter lastFilter = null;

    /** Called when user asks to open data file file dialog.
     */
    public void openAEInputFileDialog (){
//        try{Thread.currentThread().sleep(200);}catch(InterruptedException e){}
        float oldScale = outer.chipCanvas.getScale();
        fileChooser = new JFileChooser();
//            new TypeAheadSelector(fileChooser);
        //com.sun.java.plaf.windows.WindowsFileChooserUI;
//            fileChooser.addKeyListener(new KeyAdapter() {
//                public void keyTyped(KeyEvent e){
//                    System.out.println("keycode="+e.getKeyCode());
//                }
//            });
//            System.out.println("fileChooser.getUIClassID()="+fileChooser.getUIClassID());
//            KeyListener[] keyListeners=fileChooser.getKeyListeners();
        ChipDataFilePreview preview = new ChipDataFilePreview(fileChooser,outer.getChip());
        // from book swing hacks
        new FileDeleter(fileChooser,preview);
        fileChooser.addPropertyChangeListener(preview);
        fileChooser.setAccessory(preview);
        String lastFilePath = AEViewer.prefs.get("AEViewer.lastFile","");
        // get the last folder
        outer.lastFile = new File(lastFilePath);
//            fileChooser.setFileFilter(datFileFilter);
        IndexFileFilter indexFileFilter = new IndexFileFilter();
        fileChooser.addChoosableFileFilter(indexFileFilter);
        DATFileFilter datFileFilter = new DATFileFilter();
        fileChooser.addChoosableFileFilter(datFileFilter);
        if ( lastFilter == null ){
            fileChooser.setFileFilter(datFileFilter);
        } else{
            fileChooser.setFileFilter(lastFilter);
        }
        fileChooser.setCurrentDirectory(outer.lastFile);
        // sets the working directory of the chooser
//            boolean wasPaused=isPaused();
        setPaused(true);
        int retValue = fileChooser.showOpenDialog(outer);
        if ( retValue == JFileChooser.APPROVE_OPTION ){
            lastFilter = fileChooser.getFileFilter();
            try{
                outer.lastFile = fileChooser.getSelectedFile();
                if ( outer.lastFile != null ){
                    outer.recentFiles.addFile(outer.lastFile);
                }
                startPlayback(outer.lastFile);
            } catch ( IOException fnf ){
                log.warning(fnf.toString());
            }
        } else{
            preview.showFile(null);
        }
        fileChooser = null;
        outer.chipCanvas.setScale(oldScale);
        // restore persistent scale so that we don't get tiny size on next startup
        setPaused(false);
    }

    @Override
    public void setDoSingleStepEnabled (boolean b){
        outer.doSingleStepEnabled = b;
    }

    @Override
    public void doSingleStep (){
//        log.info("doSingleStep");
        outer.setDoSingleStepEnabled(true);
    }
    public class FileDeleter extends KeyAdapter implements PropertyChangeListener{
        private JFileChooser chooser;
        private ChipDataFilePreview preview;
        File file = null;

        /** adds a keyreleased listener on the JFileChooser FilePane inner classes so that user can use Delete key to delete the file
         * that is presently being shown in the preview window
         * @param chooser the chooser
         * @param preview the data file preview
         */
        public FileDeleter (JFileChooser chooser,ChipDataFilePreview preview){
            super();
            this.chooser = chooser;
            this.preview = preview;
            chooser.addPropertyChangeListener(JFileChooser.SELECTED_FILE_CHANGED_PROPERTY,this);
            Component comp = addDeleteListener(chooser);
        }

        /** is called when the file selection is changed. Bound to the SELECTED_FILE_CHANGED_PROPERTY. */
        public void propertyChange (PropertyChangeEvent evt){
            // comes from chooser when new file is selected
            if ( evt.getNewValue() instanceof File ){
                file = (File)evt.getNewValue();
            } else{
                file = null;
            }
        }

        private Component addDeleteListener (Component comp){
//            System.out.println("");
//            System.out.println("comp="+comp);
//            if (comp.getClass() == sun.swing.FilePane.class) return comp;
            if ( comp instanceof Container ){
//                System.out.println(comp+"\n");
//                comp.addMouseListener(new MouseAdapter(){
//                    public void mouseEntered(MouseEvent e){
//                        System.out.println("mouse entered: "+e);
//                    }
//                });
                // if this is a known filepane class, then add a key listener for deleting log files.
                // may need to remove this in future release of java and
                //find a portable way to detect we are in the FilePane
//                    if(comp.getClass().getEnclosingClass()==sun.swing.FilePane.class) {
//                        System.out.println("******adding keyListener to "+comp);
                comp.addKeyListener(new KeyAdapter(){
                    @Override
                    public void keyReleased (KeyEvent e){
                        if ( e.getKeyCode() == KeyEvent.VK_DELETE ){
//                                    System.out.println("delete key typed from "+e.getSource());
                            deleteFile();
                        }
                    }
                });
//                    }
                Component[] components = ( (Container)comp ).getComponents();
                for ( int i = 0 ; i < components.length ; i++ ){
                    Component child = addDeleteListener(components[i]);
                    if ( child != null ){
                        return child;
                    }
                }
            }
            return null;
        }

        void deleteFile (){
            if ( file == null ){
                return;
            }
            log.fine("trying to delete file " + file);
            preview.deleteCurrentFile();
        }
    }

    /** Starts playback on the data file.
    If the file is an index file,
    the JAERViewer is called to start playback of the set of data files.
    Fires a property change event "fileopen", after playMode is changed to PLAYBACK.
    @param file the File to play.
     */
    @Override
    public synchronized void startPlayback (File file) throws IOException{
        super.startPlayback(file);
        if ( file == null || !file.isFile() ){
            throw new FileNotFoundException("file not found: " + file);
        }
        // idea is that we set open the file and set playback mode and the ViewLoop.run
        // loop will then render from the file.
        // TODO problem is that ViewLoop run loop is still running
        // and opens hardware during this call, esp at high frame rate,
        // which sets playmode LIVE, ignoring open file and playback.
        String ext="."+IndexFileFilter.getExtension(file); // TODO change to use of a new static method in AEDataFile for determining file type
        if (ext.equals(AEDataFile.INDEX_FILE_EXTENSION) || ext.equals(AEDataFile.OLD_INDEX_FILE_EXTENSION) ){
            if ( outer.getJaerViewer() != null ){
                outer.getJaerViewer().getSyncPlayer().startPlayback(file);
            }
            return;
        }
//            System.out.println("AEViewer.starting playback for DAT file "+file);
        outer.setCurrentFile(file);
        aeFileInputStream = new AEFileInputStream(file);
        aeFileInputStream.setNonMonotonicTimeExceptionsChecked(outer.getCheckNonMonotonicTimeExceptionsEnabledCheckBoxMenuItem().isSelected());
        aeFileInputStream.setTimestampResetBitmask(outer.getAeFileInputStreamTimestampResetBitmask());
        aeFileInputStream.setFile(file);
        // so that users of the stream can get the file information
        if ( outer.getJaerViewer() != null && outer.getJaerViewer().getViewers().size() == 1 ){
            // if there is only one viewer, start it there
            try{
                aeFileInputStream.rewind();
            } catch ( IOException e ){
                e.printStackTrace();
            }
        }
        // don't waste cycles grabbing events while playing back
            outer.setPlayMode(AEViewer.PlayMode.PLAYBACK);
        // TODO ugly remove/add of new control panel to associate it with correct player
//            playerControlPanel.remove(getPlayerControls());
//            setPlayerControls(new AePlayerAdvancedControlsPanel(AEViewer.this));
//            playerControlPanel.add(getPlayerControls());
        outer.getPlayerControls().addMeToPropertyChangeListeners(aeFileInputStream);
        // so that slider is updated when position changes
        outer.setPlaybackControlsEnabledState(true);
        outer.fixLoggingControls();
            // TODO we grab the monitor for the viewLoop here, any other thread which may change playmode should also grab it
        if ( outer.aemon != null && outer.aemon.isOpen() ){
            try{
                if ( outer.getPlayMode().equals(outer.getPlayMode().SEQUENCING) ){
                    outer.stopSequencing();
                } else{
                    outer.aemon.setEventAcquisitionEnabled(false);
                }
            } catch ( HardwareInterfaceException e ){
                e.printStackTrace();
            }
        }
        getSupport().firePropertyChange("fileopen",null,file);  // TODO fix literal
    }

    /** stops playback.
     *If not in PLAYBACK mode, then just returns.
     *If playing  back, could be waiting during sleep or during CyclicBarrier.await call in CaviarViewer. In case this is the case, we send
     *an interrupt to the the ViewLoop thread to stop this waiting.
     */
    public void stopPlayback (){
        if ( outer.getPlayMode() != AEViewer.PlayMode.PLAYBACK ){
            return;
        }

        if ( outer.aemon != null && outer.aemon.isOpen() ){
            try{
                outer.aemon.setEventAcquisitionEnabled(true);
            } catch ( HardwareInterfaceException e ){
                outer.setPlayMode(AEViewer.PlayMode.WAITING);
                e.printStackTrace();
            }
            outer.setPlayMode(AEViewer.PlayMode.LIVE);
        } else{
            outer.setPlayMode(AEViewer.PlayMode.WAITING);
        }
        outer.setPlaybackControlsEnabledState(false);
        try{
            if ( aeFileInputStream != null ){
                aeFileInputStream.close();
                aeFileInputStream = null;
            }
        } catch ( IOException ignore ){
            ignore.printStackTrace();
        }
        outer.setTitleAccordingToState();
    }

    public void rewind (){
        if ( aeFileInputStream == null ){
            return;
        }
//            System.out.println(Thread.currentThread()+" AEViewer.AEPlayer.rewind() called, rewinding "+aeFileInputStream);
        try{
            aeFileInputStream.rewind();
            outer.filterChain.reset();
        } catch ( Exception e ){
            log.warning("rewind exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void pause (){
        super.pause();
        outer.setPaused(true);
    }

    @Override
    public void resume (){
        super.resume();
        outer.setPaused(false);
    }

    /** sets the AEViewer paused flag */
    @Override
    public void setPaused (boolean yes){
        super.setPaused(yes);
        outer.setPaused(yes);
    }

    /** gets the AEViewer paused flag */
    @Override
    public boolean isPaused (){
        return outer.isPaused();
    }

    public AEPacketRaw getNextPacket (){
        return getNextPacket(null);
    }

    public AEPacketRaw getNextPacket (AbstractAEPlayer player){
        if ( player != this ){
            throw new UnsupportedOperationException("tried to get data from some other player");
        }
        AEPacketRaw aeRaw = null;
        try{
            if ( !outer.aePlayer.isFlexTimeEnabled() ){
                aeRaw = aeFileInputStream.readPacketByTime(outer.getAePlayer().getTimesliceUs());
            } else{
                aeRaw = aeFileInputStream.readPacketByNumber(outer.getAePlayer().getPacketSizeEvents());
            }
//                if(aeRaw!=null) time=aeRaw.getLastTimestamp();
            return aeRaw;
        } catch ( EOFException e ){
            try{
                Thread.sleep(200);
            } catch ( InterruptedException ignore ){
            }
            // when we get to end, we now just wraps in either direction, to make it easier to explore the ends
//                System.out.println("***********"+this+" reached EOF, calling rewind");
            outer.getAePlayer().rewind();
            // we force a rewind on all players in case we are not the only one
//                                if(!aePlayer.isPlayingForwards())
            //getAePlayer().toggleDirection();
            return aeRaw;
        } catch ( Exception anyOtherException ){
            log.warning(anyOtherException.toString() + ", returning empty AEPacketRaw");
            anyOtherException.printStackTrace();
            return new AEPacketRaw(0);
        }
    }

    /** Tries to adjust timeslice to approach realtime playback.
     *
     */
    public void adjustTimesliceForRealtimePlayback (){
        if ( !isRealtimeEnabled() || isPaused() ){
            return;
        }
        float fps = outer.getFrameRater().getAverageFPS();
        float samplePeriodS = getTimesliceUs() * 1.0E-6F;
        float factor = fps * samplePeriodS;
//            System.out.println("fps=" + fps + " samplePeriodS=" + samplePeriodS + " factor=" + factor);
//            if ( factor < 1.2 || factor > 0.8f ){
        setTimesliceUs((int)( getTimesliceUs() / factor ));
    }

    public float getFractionalPosition (){
        if ( aeFileInputStream == null ){
            log.warning("AEViewer.AEPlayer.getFractionalPosition: null fileAEInputStream, returning 0");
            return 0;
        }
        float fracPos = aeFileInputStream.getFractionalPosition();
        return fracPos;
    }

    public void mark () throws IOException{
        aeFileInputStream.mark();
    }

    public int position (){
        return aeFileInputStream.position();
    }

    public void position (int event){
        aeFileInputStream.position(event);
    }

    public AEPacketRaw readPacketByNumber (int n) throws IOException{
        return aeFileInputStream.readPacketByNumber(n);
    }

    public AEPacketRaw readPacketByTime (int dt) throws IOException{
        return aeFileInputStream.readPacketByTime(dt);
    }

    public long size (){
        return aeFileInputStream.size();
    }

    public void unmark (){
        aeFileInputStream.unmark();
    }

    public boolean isMarkSet (){
        return aeFileInputStream.isMarkSet();
    }

//        public synchronized AEPacketRaw readPacketToTime(int time, boolean forwards) throws IOException {
//            return aeFileInputStream.readPacketToTime(time,forwards);
//        }
//
    public void setFractionalPosition (float frac){
        aeFileInputStream.setFractionalPosition(frac);
    }

    public void setTime (int time){
//            System.out.println(this+".setTime("+time+")");
        if ( aeFileInputStream != null ){
            aeFileInputStream.setCurrentStartTimestamp(time);
        } else{
            log.warning("null AEInputStream");
        }
    }

    public int getTime (){
        if ( aeFileInputStream == null ){
            return 0;
        }
        return aeFileInputStream.getMostRecentTimestamp();
    }

    @Override
    public AEFileInputStream getAEInputStream (){
        return aeFileInputStream;
    }

    public boolean isNonMonotonicTimeExceptionsChecked (){
        if ( aeFileInputStream == null ){
            return false;
        }
        return aeFileInputStream.isNonMonotonicTimeExceptionsChecked();
    }

    public void setNonMonotonicTimeExceptionsChecked (boolean yes){
        if ( aeFileInputStream == null ){
            log.warning("null fileAEInputStream");
            return;
        }
        aeFileInputStream.setNonMonotonicTimeExceptionsChecked(yes);
    }
}
