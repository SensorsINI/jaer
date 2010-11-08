/*
 * FViewer.java
 *
 * Created on December 24, 2005, 1:58 PM
 */

package ch.unizh.ini.jaer.projects.opticalflow.graphics;

import ch.unizh.ini.jaer.projects.opticalflow.*;
import net.sf.jaer.aemonitor.*;
import net.sf.jaer.biasgen.*;
import net.sf.jaer.chip.*;
//import ch.unizh.ini.caviar.chip.convolution.Conv64NoNegativeEvents;
import ch.unizh.ini.jaer.chip.retina.*;
import net.sf.jaer.eventio.*;
import net.sf.jaer.graphics.*;
import net.sf.jaer.util.*;
import ch.unizh.ini.jaer.projects.opticalflow.io.*;
import ch.unizh.ini.jaer.projects.opticalflow.io.MotionOutputStream;
import ch.unizh.ini.jaer.projects.opticalflow.usbinterface.SiLabsC8051F320_OpticalFlowHardwareInterface;
import com.sun.java.swing.plaf.windows.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.awt.event.*;
import java.awt.image.*;
import java.beans.*;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.text.*;
import java.util.logging.*;
import java.util.prefs.*;
import javax.imageio.*;
import javax.swing.*;

/**
 * Shows retina live and allows for controlling view and recording and playing back events.
 * @author  tobi
 */
public class ChannelViewer extends MotionViewer implements PropertyChangeListener, DropTargetListener {
    
    
    private MotionOutputStream loggingOutputStream;
   

    volatile private PlayMode playMode=PlayMode.WAITING;


    ChipCanvas chipCanvas_chan;
    ViewLoop2 viewloop;

    private boolean loggingPlaybackImmediatelyEnabled=prefs.getBoolean("MotionViewer.loggingPlaybackImmediatelyEnabled",false);
    private long loggingTimeLimit=0, loggingStartTime=System.currentTimeMillis();
    private boolean logFilteredEventsEnabled=prefs.getBoolean("MotionViewer.logFilteredEventsEnabled",false);

    private String aeChipClassName=prefs.get("MotionViewer.aeChipClassName",Tmpdiff128.class.getName());

    
    /**
     * construct new instance and then set classname of device to show in it
     *
     */
    public ChannelViewer(Chip2DMotion chip){
        super(chip);
        try {
            UIManager.setLookAndFeel(new WindowsLookAndFeel());
        } catch (Exception e) {
            log.warning(e.getMessage());
        }
        setName("MotionViewer");
        log.setLevel(Level.INFO);
        initComponents();
        makeCanvas();
        statisticsLabel=new DynamicFontSizeJLabel();
        statisticsLabel.setToolTipText("Time slice/Absolute time, NumEvents/NumFiltered, events/sec, Frame rate acheived/desired, Time expansion X contraction /, delay after frame, color scale");
        statisticsPanel.add(statisticsLabel);

        int n=menuBar.getMenuCount();
        for(int i=0;i<n;i++){
            JMenu m=menuBar.getMenu(i);
            m.getPopupMenu().setLightWeightPopupEnabled(false);
        }

        pack();


        Runtime.getRuntime().addShutdownHook(new Thread());
        
        setFocusable(true);
        requestFocus();
        viewLoop=new ViewLoop();
        viewLoop.start();
        dropTarget=new DropTarget(imagePanel,this);
        
        

        
    }
    

    
    
    synchronized void makeCanvas(){
        chipCanvas_chan=chip.getCanvas();
        imagePanel.add(chipCanvas_chan.getCanvas());

        chipCanvas_chan.getCanvas().invalidate();

        // add the panel below the chip for controlling display of the chip (gain and offset values for rendered photoreceptor and motion vectors)
        JPanel cp=new ChannelViewerControlPanel((OpticalFlowDisplayMethod)chip.getCanvas().getDisplayMethod());

        imagePanel.add(cp);

        validate();
        pack();
        // causes a lot of flashing ... Toolkit.getDefaultToolkit().setDynamicLayout(true); // dynamic resizing  -- see if this bombs!
    }
    
   
    
    
    /** writes frames and frame sequences for video making using, e.g. adobe premiere */
    class CanvasFileWriter{
/*
 
 Part for OpenGL capture from http://www.cs.plu.edu/~dwolff/talks/jogl-ccsc/src/j_ScreenCapture/ScreenCaptureExample.java
 * Example 10: Screen capture
 *
 * This example demonstrates how to capture the OpenGL buffer into
 * a BufferedImage, and then optionally write it to a PNG file.
 *
 * Author: David Wolff
 *
 * Licensed under the Creative Commons Attribution License 2.5:
 * http://creativecommons.org/licenses/by/2.5/
 */
        
        boolean writingMovieEnabled=false;
        Canvas canvas;
        int frameNumber=0;
        java.io.File sequenceDir;
        String sequenceName="sequence";
        
        
        int snapshotNumber=0; // this is appended automatically to single snapshot filenames
        String snapshotName="snapshot";
        
        String getFilename(){
            return sequenceName+String.format("%04d.png",frameNumber);
        }
        
        synchronized void startWritingMovie(){
//            if(isOpenGLRenderingEnabled()){
//                JOptionPane.showMessageDialog(MotionViewer.this,"Disable OpenGL graphics from the View menu first");
//                return;
//            }
            sequenceName=JOptionPane.showInputDialog("Sequence name (this folder will be created)?");
            if(sequenceName==null || sequenceName.equals("")) {
                log.info("canceled image sequence");
                return;
            }
            log.info("creating directory "+sequenceName);
            sequenceDir=new File(sequenceName);
            if(sequenceDir.exists()){
                JOptionPane.showMessageDialog(ChannelViewer.this, sequenceName+" already exists");
                return;
            }
            boolean madeit=sequenceDir.mkdir();
            if(!madeit){
                JOptionPane.showMessageDialog(ChannelViewer.this, "couldn't create directory "+sequenceName);
                return;
            }
            frameNumber=0;
            writingMovieEnabled=true;
        }
        
        synchronized void stopWritingMovie(){
            writingMovieEnabled=false;
        }
        
        
        synchronized void writeMovieFrame(){
            try {
                Container container=getContentPane();
                canvas=chip.getCanvas().getCanvas();
                Rectangle r = canvas.getBounds();
                Image image = canvas.createImage(r.width, r.height);
                Graphics g = image.getGraphics();
                synchronized(container){
                    container.paintComponents(g);
                    if(chip.getCanvas().getImageOpenGL()!=null){
                        ImageIO.write(chip.getCanvas().getImageOpenGL(), "png", new File(sequenceDir,getFilename()));
                    }
                }
                frameNumber++;
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }

    }
    

    int renderCount=0;
    int numEvents;
    AEPacketRaw aeRaw;
    boolean skipRender=false;
    boolean overrunOccurred=false;
    public Player player=new Player();
    int noEventCounter=0;
    
    
    /** this class handles file input of AEs to control the number of events/sample or period of time in the sample, etc.
     *It handles the file input stream, opening a dialog box, etc.
     *It also handles synchronization of different viewers as follows:
     *<p>
     * If the viwer is not synchronized, then all calls from the GUI are passed directly to this instance of AEPlayer. Thus local control always happens.
     *<p>
     * If the viewer is sychronized, then all GUI calls pass instead to the CaviarViewer instance that contains (or started) this viewer. Then the CaviarViewer AEPlayer
     *calls all the viewers to take the player action (e.g. rewind, go to next slice, change direction).
     *<p>
     *Thus whichever controls the user uses to control playback, the viewers are all sychronized properly without recursively. The "master" is indicated by the GUI action,
     *which routes the request either to this instance's AEPlayer or to the CaviarViewer AEPlayer.
     */
    
    
    
    /** This thread acquires events and renders them to the RetinaCanvas for active rendering. The other components render themselves
     * on the usual Swing rendering thread.
     */
    class ViewLoop2 extends Thread{
        Graphics2D g=null;
//        volatile boolean rerenderOtherComponents=false;
//        volatile boolean renderImageEnabled=true;
        volatile boolean singleStepEnabled=false, doSingleStep=false;
        int numRawEvents, numFilteredEvents;
        long timeStarted=System.currentTimeMillis();
        
        public ViewLoop2(){
            super();
            setName("MotionViewer.ViewLoop");
        }
        
        //asks the canvas to paint itself
        // this in turn calls the display(GLAutoDrawable) method of the canvas, which calls the
        // diplayMethod of the chip, which knows now to draw the data
        void renderFrame(){
            
            if(canvasFileWriter.writingMovieEnabled) chipCanvas_chan.grabNextImage();
            chipCanvas_chan.paintFrame(); // actively paint frame now, either with OpenGL or Java2D, depending on switch
            
            if(canvasFileWriter.writingMovieEnabled){
                canvasFileWriter.writeMovieFrame();
            }
        } // renderFrame
        
        EngineeringFormat engFmt=new EngineeringFormat();
        long beforeTime=0, afterTime;
        int lastts=0;
        volatile boolean stop=false;
        
//        EventProcessingPerformanceMeter perf=new EventProcessingPerformanceMeter(new BackgroundActivityFilter(getChip()));
        
        final String waitString="Waiting for device";
        int waitCounter=0;


        /** the main loop - this is the 'game loop' of the program */
        synchronized public void run(){
            while(stop==false && !isInterrupted()){
                
                // now get the data to be displayed
                if(!isPaused() || isSingleStep()){
//                    if(isSingleStep()){
//                        log.info("getting data for single step");
//                    }
                    // if !paused we always get data. below, if singleStepEnabled, we set paused after getting data.
                    // when the user unpauses via menu, we disable singleStepEnabled
                    // another flag, doSingleStep, tells loop to do a single data acquisition and then pause again
                    // in this branch, get new data to show
                    frameRater.takeBefore();
                    switch(getPlayMode()){
                        
                        case LIVE:
                            if(hardware==null || !hardware.isOpen()) {
                                setPlayMode(PlayMode.WAITING);
                                try{Thread.currentThread().sleep(300);}catch(InterruptedException e){
                                    log.warning("LIVE openAEMonitor sleep interrupted");
                                }
                                continue;
                            }
                            motionData=MotionViewer.chip.lastMotionData; // exchanges data with hardware interface, returns the new data buffer
                            
                            chip.setLastData(motionData); // for use by rendering methods
                            
                            break;
                        case PLAYBACK:
                            break;
                        case WAITING:
//                            chip.setLastData(new MotionData());
//                            renderFrame(); // debug
                            //openHardware();
                            if(hardware==null || !hardware.isOpen()) {
                                StringBuffer s=new StringBuffer(waitString);
                                for(int i=0;i<waitCounter;i++) s.append('.');
                                if(waitCounter++==3) waitCounter=0;
                                statisticsLabel.setText(s.toString());
                                try{Thread.currentThread().sleep(300);}catch(InterruptedException e){
                                    log.warning("WAITING interrupted");
                                }
                                continue;
                            }
                    } // playMode switch to get data
                    
                    singleStepDone();
                } // getting data
                
                
                if(!isInterrupted()) {
                    // get data from device
                    renderFrame();
//                                motionData=hardware.getData();
                }
                makeStatisticsLabel();
                
                frameRater.takeAfter();
                renderCount++;
                
                fpsDelay();
            }
            log.warning("MotionViewer.run(): stop="+stop+" isInterrupted="+isInterrupted());

            
            if(windowSaver!=null)
                try {
                    windowSaver.saveSettings();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            
            chipCanvas_chan.getCanvas().setVisible(false);
            remove(chipCanvas_chan.getCanvas());
            dispose();

        } // viewLoop.run()
        
        void fpsDelay(){
            if(!isPaused()){
                frameRater.delayForDesiredFPS();
            }else{
                synchronized(this){ try {wait(100);} catch (java.lang.InterruptedException e) {
                    log.warning("viewLoop wait() interrupted: "+e.getMessage()+" cause is "+e.getCause());}
                }
            }
        }
        
        private void makeStatisticsLabel() {
            StringBuilder sb=new StringBuilder();
            if(motionData!=null){
                sb.append(String.format("Seq# %4d, ",motionData.getSequenceNumber()));
            }else{
                sb.append(String.format("Frame %4d, ",renderCount));
            }
            sb.append(String.format("%5.0f/%-5d",frameRater.getAverageFPS(),frameRater.getDesiredFPS()));
            sb.append(String.format(", FS=%d",chip.getRenderer().getColorScale()));
            setStatisticsLabel(sb.toString());
        }
    }
    
    void setStatisticsLabel(final String s){
        try {
            SwingUtilities.invokeAndWait(new Runnable(){
                public void run(){
                    statisticsLabel.setText(s);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    int getScreenRefreshRate(){
        int rate=60;
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] gs = ge.getScreenDevices();
        for (int i=0; i<gs.length; i++) {
            DisplayMode dm = gs[i].getDisplayMode();
            // Get refresh rate in Hz
            int refreshRate = dm.getRefreshRate();
            if (refreshRate == DisplayMode.REFRESH_RATE_UNKNOWN) {
//                log.warning("MotionViewer.getScreenRefreshRate: got unknown refresh rate for screen "+i+", assuming 60");
                refreshRate=60;
            }else{
//                log.info("MotionViewer.getScreenRefreshRate: screen "+i+" has refresh rate "+refreshRate);
            }
            if(i==0) rate=refreshRate;
        }
        return rate;
    }
    
// computes and executes appropriate delayForDesiredFPS to try to maintain constant rendering rate
    class FrameRater{
        final int MAX_FPS=120;
        int desiredFPS= prefs.getInt("MotionViewer.FrameRater.desiredFPS",getScreenRefreshRate());
        final int nSamples=10;
        long[] samplesNs=new long[nSamples];
        int index=0;
        int delayMs=1;
        int desiredPeriodMs=(int)(1000f/desiredFPS);
        
        
        void setDesiredFPS(int fps){
            if(fps<1) fps=1; else if(fps>MAX_FPS) fps=MAX_FPS;
            desiredFPS=fps;
            prefs.putInt("MotionViewer.FrameRater.desiredFPS",fps);
            desiredPeriodMs=1000/fps;
        }
        
        int getDesiredFPS(){
            return desiredFPS;
        }
        
        float getAveragePeriodNs(){
            int sum=0;
            for(int i=0;i<nSamples;i++){
                sum+=samplesNs[i];
            }
            return (float)sum/nSamples;
        }
        
        float getAverageFPS(){
            return 1f/(getAveragePeriodNs()/1e9f);
        }
        
        float getLastFPS(){
            return 1f/(lastdt/1e9f);
        }
        
        int getLastDelayMs(){
            return delayMs;
        }
        
        long getLastDtNs(){
            return lastdt;
        }
        
        long beforeTimeNs=System.nanoTime(), lastdt, afterTimeNs;
        
        //  call this ONCE after capture/render. it will store the time since the last call
        void takeBefore(){
            beforeTimeNs=System.nanoTime();
        }
        
        long lastAfterTime=System.nanoTime();
        
        //  call this ONCE after capture/render. it will store the time since the last call
        void takeAfter(){
            afterTimeNs=System.nanoTime();
            lastdt=afterTimeNs-beforeTimeNs;
            samplesNs[index++]=afterTimeNs-lastAfterTime;
            lastAfterTime=afterTimeNs;
            if(index>=nSamples) index=0;
        }
        
        // call this to delayForDesiredFPS enough to make the total time including last sample period equal to desiredPeriodMs
        void delayForDesiredFPS(){
            delayMs=(int)Math.round(desiredPeriodMs-(float)getLastDtNs()/1000000);
            if(delayMs<0) delayMs=1;
            try {Thread.currentThread().sleep(delayMs);} catch (java.lang.InterruptedException e) {}
        }
        
    }
    
    public void stopMe(){
//        log.info(Thread.currentThread()+ "MotionViewer.stopMe() called");
        switch(getPlayMode()){
            case PLAYBACK:
                getPlayer().stopPlayback();
                break;
            case LIVE:
            case WAITING:
                viewLoop.stop=true;
                break;
        }
    }
    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jProgressBar1 = new javax.swing.JProgressBar();
        renderModeButtonGroup = new javax.swing.ButtonGroup();
        statisticsPanel = new javax.swing.JPanel();
        imagePanel = new javax.swing.JPanel();
        mainToolBar = new javax.swing.JToolBar();
        toolbarPanel = new javax.swing.JPanel();
        jPanel1 = new javax.swing.JPanel();
        jPanel3 = new javax.swing.JPanel();
        resizeIconLabel = new javax.swing.JLabel();
        menuBar = new javax.swing.JMenuBar();
        fileMenu = new javax.swing.JMenu();
        jSeparator7 = new javax.swing.JSeparator();
        jSeparator6 = new javax.swing.JSeparator();
        jSeparator5 = new javax.swing.JSeparator();
        exitMenuItem = new javax.swing.JMenuItem();
        helpMenu = new javax.swing.JMenu();
        aboutMenuItem = new javax.swing.JMenuItem();

        setTitle("Retina");
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });
        addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentResized(java.awt.event.ComponentEvent evt) {
                formComponentResized(evt);
            }
        });

        statisticsPanel.setFocusable(false);
        statisticsPanel.addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentResized(java.awt.event.ComponentEvent evt) {
                statisticsPanelComponentResized(evt);
            }
        });
        getContentPane().add(statisticsPanel, java.awt.BorderLayout.NORTH);

        imagePanel.setEnabled(false);
        imagePanel.setFocusable(false);
        imagePanel.addMouseWheelListener(new java.awt.event.MouseWheelListener() {
            public void mouseWheelMoved(java.awt.event.MouseWheelEvent evt) {
                imagePanelMouseWheelMoved(evt);
            }
        });
        imagePanel.setLayout(new java.awt.GridLayout(2, 1));
        getContentPane().add(imagePanel, java.awt.BorderLayout.CENTER);
        getContentPane().add(mainToolBar, java.awt.BorderLayout.SOUTH);

        toolbarPanel.setLayout(new java.awt.BorderLayout());

        jPanel1.setLayout(new javax.swing.BoxLayout(jPanel1, javax.swing.BoxLayout.LINE_AXIS));
        toolbarPanel.add(jPanel1, java.awt.BorderLayout.WEST);

        jPanel3.setLayout(new java.awt.BorderLayout());

        resizeIconLabel.setIcon(new TriangleSquareWindowsCornerIcon());
        resizeIconLabel.setVerticalAlignment(javax.swing.SwingConstants.BOTTOM);
        resizeIconLabel.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            public void mouseDragged(java.awt.event.MouseEvent evt) {
                resizeIconLabelMouseDragged(evt);
            }
        });
        resizeIconLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                resizeIconLabelMouseEntered(evt);
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                resizeIconLabelMouseExited(evt);
            }
            public void mousePressed(java.awt.event.MouseEvent evt) {
                resizeIconLabelMousePressed(evt);
            }
        });
        jPanel3.add(resizeIconLabel, java.awt.BorderLayout.SOUTH);

        toolbarPanel.add(jPanel3, java.awt.BorderLayout.EAST);

        getContentPane().add(toolbarPanel, java.awt.BorderLayout.SOUTH);

        fileMenu.setMnemonic('f');
        fileMenu.setText("File");
        fileMenu.add(jSeparator7);
        fileMenu.add(jSeparator6);
        fileMenu.add(jSeparator5);

        exitMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_X, 0));
        exitMenuItem.setMnemonic('x');
        exitMenuItem.setText("Exit");
        exitMenuItem.setToolTipText("Exits all viewers");
        exitMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exitMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(exitMenuItem);

        menuBar.add(fileMenu);

        helpMenu.setMnemonic('h');
        helpMenu.setText("Help");

        aboutMenuItem.setText("About");
        aboutMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                aboutMenuItemActionPerformed(evt);
            }
        });
        helpMenu.add(aboutMenuItem);

        menuBar.add(helpMenu);

        setJMenuBar(menuBar);

        pack();
    }// </editor-fold>//GEN-END:initComponents
            
    volatile AEMulticastInput socketInputStream=null;
    
    volatile AEMulticastOutput socketOutputStream=null;
    
    
    private void resizeIconLabelMouseExited(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_resizeIconLabelMouseExited
        setCursor(preResizeCursor);
    }//GEN-LAST:event_resizeIconLabelMouseExited
    
    Cursor preResizeCursor=Cursor.getDefaultCursor();
    
    private void resizeIconLabelMouseEntered(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_resizeIconLabelMouseEntered
        preResizeCursor=getCursor();
        setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
    }//GEN-LAST:event_resizeIconLabelMouseEntered
    
    Dimension oldSize;
    Point startResizePoint;
    
    private void resizeIconLabelMouseDragged(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_resizeIconLabelMouseDragged
        Point resizePoint=evt.getPoint();
        int widthInc=resizePoint.x-startResizePoint.x;
        int heightInc=resizePoint.y-startResizePoint.y;
        setSize(getWidth()+widthInc,getHeight()+heightInc);
    }//GEN-LAST:event_resizeIconLabelMouseDragged
    
    private void resizeIconLabelMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_resizeIconLabelMousePressed
        oldSize=getSize();
        startResizePoint=evt.getPoint();
    }//GEN-LAST:event_resizeIconLabelMousePressed
            
    private void formWindowClosed(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosed
        log.info("window closed event, calling stopMe");
        stopMe();
    }//GEN-LAST:event_formWindowClosed
        
    volatile boolean doSingleStepEnabled=false;
    
    synchronized public void doSingleStep(){
        setDoSingleStepEnabled(true);
        setPaused(true);
    }
    
    public void setDoSingleStepEnabled(boolean yes){
        doSingleStepEnabled=yes;
    }
    
    synchronized public boolean isSingleStep(){
        return doSingleStepEnabled;
    }
    
    synchronized public void singleStepDone(){
        if(isSingleStep()){
            setDoSingleStepEnabled(false);
            setPaused(true);
        }
    }
        
    // used to print dt for measuring frequency from playback by using '1' keystrokes
    class Statistics{
        JFrame statFrame;
        JLabel statLabel;
        int lastTime=0, thisTime;
        EngineeringFormat fmt=new EngineeringFormat();{
            fmt.precision=2;
        }
        void printStats(){
            synchronized (player){
                thisTime=player.getTime();
                int dt=lastTime-thisTime;
                float dtSec=(float)((float)dt/1e6f+Float.MIN_VALUE);
                float freqHz=1/dtSec;
//                System.out.println(String.format("dt=%.2g s, freq=%.2g Hz",dtSec,freqHz));
                if(statFrame==null) {
                    statFrame=new JFrame("Statistics");
                    statLabel=new JLabel();
                    statLabel.setFont(statLabel.getFont().deriveFont(16f));
                    statLabel.setToolTipText("Type \"1\" to update interval statistics");
                    statFrame.getContentPane().setLayout(new BorderLayout());
                    statFrame.getContentPane().add(statLabel,BorderLayout.CENTER);
                    statFrame.pack();
                }
                String s=" dt="+fmt.format(dtSec)+"s, freq="+fmt.format(freqHz)+" Hz ";
//                System.out.println(s);
                statLabel.setText(s);
                statLabel.revalidate();
                statFrame.pack();
                if(!statFrame.isVisible()) statFrame.setVisible(true);
                requestFocus(); // leave the focus here
                lastTime=thisTime;
            }
        }
    }
    
    Statistics statistics;
        
    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        log.info("window closing event, only 1 viewer so calling System.exit");
        stopMe();
        System.exit(0);
    }//GEN-LAST:event_formWindowClosing
    
    private void formComponentResized(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_formComponentResized
        // handle statistics label font sizing here
//        System.out.println("*****************frame resize");
        double fw=getWidth();
        double lw=statisticsLabel.getWidth();
        
        if(fw<200) fw=200;
        double r=fw/lw;
        final double mn=.3, mx=2.3;
        if(r<mn) r=mn; if(r>mx) r=mx;
        
        final int minFont=10, maxFont=36;
//        System.out.println("frame/label width="+r);
        Font f=statisticsLabel.getFont();
        int size=f.getSize();
        int newsize=(int)Math.floor(size*r);
        if(newsize<minFont) newsize=minFont; if(newsize>maxFont) newsize=maxFont;
        if(size==newsize) return;
        Font nf=f.deriveFont((float)newsize);
//        System.out.println("old font="+f);
//        System.out.println("new font="+nf);
        statisticsLabel.setFont(nf);
        
    }//GEN-LAST:event_formComponentResized
    
    private void statisticsPanelComponentResized(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_statisticsPanelComponentResized
        
//        statisticsPanel.revalidate();
    }//GEN-LAST:event_statisticsPanelComponentResized
        
    CanvasFileWriter canvasFileWriter=new CanvasFileWriter();
            
    private void imagePanelMouseWheelMoved(java.awt.event.MouseWheelEvent evt) {//GEN-FIRST:event_imagePanelMouseWheelMoved
        int rotation=evt.getWheelRotation();
        renderer.setColorScale(renderer.getColorScale()+rotation);
    }//GEN-LAST:event_imagePanelMouseWheelMoved
                                                            
    //avoid stateChanged events from slider that is set by player
    volatile boolean sliderDontProcess=false;
    
    /** messages come back here from e.g. programmatic state changes, like a new aePlayer file posiiton.
     * This methods sets the GUI components to a consistent state, using a flag to tell the slider that it has not been set by
     * a user mouse action
     */
    public void propertyChange(PropertyChangeEvent evt) {
        if(evt.getPropertyName().equals("position")){
//            System.out.println("slider property change new val="+evt.getNewValue());
            sliderDontProcess=true;
            // note this cool semaphore/flag trick to avoid processing the
            // event generated when we programmatically set the slider position here
        }else if(evt.getPropertyName().equals("readerStarted")){
            log.info("MotionViewer.propertyChange: AEReader started, fixing device control menu");
            // cypress reader started, can set device control for cypress usbio reader thread
//            fixDeviceControlMenuItems();
        }
    }
                
    
  
 
    
    public String toString(){
        return getTitle();
    }
    
    private void exitMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exitMenuItemActionPerformed
//        System.exit(0);
        //stopMe();

        dispose();
    }//GEN-LAST:event_exitMenuItemActionPerformed

    private void aboutMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_aboutMenuItemActionPerformed
        new AEViewerAboutDialog(new javax.swing.JFrame(), true).setVisible(true);
}//GEN-LAST:event_aboutMenuItemActionPerformed

    protected void processWindowEvent(WindowEvent e) {
        super.processWindowEvent(e);
        
        if (e.getID() == WindowEvent.WINDOW_CLOSING) {
            switch(DISPOSE_ON_CLOSE) {
              case HIDE_ON_CLOSE:
                 setVisible(false);
                 break;
              case DISPOSE_ON_CLOSE:
                 dispose();
                 break;
              case DO_NOTHING_ON_CLOSE:
                 default:
                 break;
	      case EXIT_ON_CLOSE:
                  // This needs to match the checkExit call in
                  // setDefaultCloseOperation
		System.exit(0);
		break;
            }
        }
    }


    public int getFrameRate() {
        return frameRater.getDesiredFPS();
    }
    
    public void setFrameRate(int renderDesiredFrameRateHz) {
        frameRater.setDesiredFPS(renderDesiredFrameRateHz);
    }
    
    boolean paused=false;
    
    public boolean isPaused() {
        return paused;
    }
    
    /** sets paused. If viewing is synchronized, then all viwewers will be paused.
     *@param paused true to pause
     */
    public void setPaused(boolean paused) {
        this.paused=paused;
    }
    
    // drag and drop data file onto frame to play it
//          Called while a drag operation is ongoing, when the mouse pointer enters the operable part of the drop site for the DropTarget registered with this listener.
    public  void 	dragEnter(DropTargetDragEvent dtde){
        Transferable transferable=dtde.getTransferable();
        try{
            if(transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)){
                java.util.List<File> files=(java.util.List<File>)transferable.getTransferData(DataFlavor.javaFileListFlavor);
                for(File f:files){
                    if(f.getName().endsWith(AEDataFile.DATA_FILE_EXTENSION) || f.getName().endsWith(AEDataFile.INDEX_FILE_EXTENSION)
                            || f.getName().endsWith(AEDataFile.OLD_DATA_FILE_EXTENSION) || f.getName().endsWith(AEDataFile.OLD_INDEX_FILE_EXTENSION))  draggedFile=f; else draggedFile=null;
                }
//                System.out.println("MotionViewer.dragEnter(): draqged file="+draggedFile);
            }
        }catch(UnsupportedFlavorException e){
            e.printStackTrace();
        }catch(IOException e){
            e.printStackTrace();
        }
        
    }
    
//          Called while a drag operation is ongoing, when the mouse pointer has exited the operable part of the drop site for the DropTarget registered with this listener.
    public void 	dragExit(DropTargetEvent dte){
        draggedFile=null;
    }
//          Called when a drag operation is ongoing, while the mouse pointer is still over the operable part of the drop site for the DropTarget registered with this listener.
    public  void 	dragOver(DropTargetDragEvent dtde){}
    
    //  Called when the drag operation has terminated with a drop on the operable part of the drop site for the DropTarget registered with this listener.
    public void drop(DropTargetDropEvent dtde){
        if(draggedFile!=null){
            log.info("MotionViewer.drop(): opening file "+draggedFile);
            try{
                recentFiles.addFile(draggedFile);
                getPlayer().startPlayback(draggedFile);
            }catch(FileNotFoundException e){
                e.printStackTrace();
            }
        }
    }
    
//          Called if the user has modified the current drop gesture.
    public void dropActionChanged(DropTargetDragEvent dtde){}
    
    public boolean isLoggingPlaybackImmediatelyEnabled() {
        return loggingPlaybackImmediatelyEnabled;
    }
    
    public void setLoggingPlaybackImmediatelyEnabled(boolean loggingPlaybackImmediatelyEnabled) {
        this.loggingPlaybackImmediatelyEnabled = loggingPlaybackImmediatelyEnabled;
        prefs.putBoolean("MotionViewer.loggingPlaybackImmediatelyEnabled",loggingPlaybackImmediatelyEnabled);
    }
    
    /** @return the chip we are displaying */
    public Chip2D getChip() {
        return chip;
    }
    
    public void setChip(Chip2DMotion chip) {
        this.chip = chip;
        if(chip!=null){
            renderer=chip.getRenderer();
        }
    }
    
    public javax.swing.JMenu getFileMenu() {
        return fileMenu;
    }
    
   
    
    /** @return the local player, unless we are part of a synchronized playback gruop */
    public Player getPlayer() {
        return player;
    }
    
    
    public PlayMode getPlayMode() {
        return playMode;
    }
    
    /** sets mode, LIVE, PLAYBACK, WAITING, etc */
    public void setPlayMode(PlayMode playMode) {
        // there can be a race condition where user tries to open file, this sets
        // playMode to PLAYBACK but run() method in ViewLoop2 sets it back to WAITING
        this.playMode = playMode;
//        log.info("set playMode="+playMode);
    }
    

    
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuItem aboutMenuItem;
    private javax.swing.JMenuItem exitMenuItem;
    private javax.swing.JMenu fileMenu;
    private javax.swing.JMenu helpMenu;
    private javax.swing.JPanel imagePanel;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JProgressBar jProgressBar1;
    private javax.swing.JSeparator jSeparator5;
    private javax.swing.JSeparator jSeparator6;
    private javax.swing.JSeparator jSeparator7;
    private javax.swing.JToolBar mainToolBar;
    private javax.swing.JMenuBar menuBar;
    private javax.swing.ButtonGroup renderModeButtonGroup;
    private javax.swing.JLabel resizeIconLabel;
    private javax.swing.JPanel statisticsPanel;
    private javax.swing.JPanel toolbarPanel;
    // End of variables declaration//GEN-END:variables
    
}
