
package ch.unizh.ini.jaer.projects.bjoernbeyer.visualservo;

import ch.unizh.ini.jaer.projects.bjoernbeyer.pantiltscreencalibration.CalibrationPanTiltScreen;
import ch.unizh.ini.jaer.hardware.pantilt.PanTilt;
import ch.unizh.ini.jaer.projects.bjoernbeyer.stimulusdisplay.PaintableObject;
import ch.unizh.ini.jaer.projects.bjoernbeyer.stimulusdisplay.StimulusDisplayGUI;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.locks.*;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 *
 * @author Bjoern
 */
public class EvaluateTrackingSuccess extends EventFilter2D implements PropertyChangeListener{
    private final StimulusDisplayGUI stimDisp;
    private final CalibrationPanTiltScreen screenPtCalib;
    private final PanTilt panTilt;
    private final SimpleVStrackerInterface tracker;
    
    private final static String FILE_NAME_PREFIX = "jAER1.5_TrackingData_";
    private final static String VIS_TARGET_NAME = "visualTarget";
    
    private File writeFile;
    private BufferedWriter outWriter;
    private boolean writeDataToFile = false;
    private final DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
    private final Date date;
    private int fileNumber = 1;
    private int numberRepeats = 5;
    private int millisBetweenTrials = 1000;
    private String evaluationSetName = "";
    private final Lock lock = new ReentrantLock();
    private final Condition notFinished = lock.newCondition();
    private long nanoStartTime;
    
    private EvaluationSetPlayer player = null;
    
    private class EvaluationSetPlayer extends Thread {
        boolean cancelMe = false;
        private int numberRepeats = 1;
        private int millisSleep = 0;
        
        EvaluationSetPlayer(int numberRepeats, int millisBetweenStimDisplays){
            super();
            this.numberRepeats = numberRepeats;
            this.millisSleep = millisBetweenStimDisplays;
        }
        
        void cancel() {
            cancelMe = true;
            synchronized (this) { interrupt(); }
        }

        @Override public void run() {
            int currentPlayTime = 0;
            while (!cancelMe) {
                try {
                    startDisplayStimulus();
                    lock.lock();
                    notFinished.await(); //When the path is played once a propertyChangeEvent is fired that we listen to. The propertyChange signals the Condition.
                    Thread.sleep(this.millisSleep);
                    System.out.println(nanoStartTime);
                    closeFile();
                    
                    currentPlayTime++;
                    if(currentPlayTime >= this.numberRepeats) cancel();
                } catch (InterruptedException ex) { 
                    log.warning(ex.getMessage());
                } finally {
                    lock.unlock();
                }
            }
        }
    }
    
    public EvaluateTrackingSuccess(AEChip chip) {
        super(chip);
        
        panTilt       = PanTilt.getInstance(0);
        screenPtCalib = new CalibrationPanTiltScreen("screenPTCalib");
        stimDisp      = new StimulusDisplayGUI();
        tracker       = new SimpleVS_Cluster(chip);
            tracker.setTrackerEnabled(false);
            
        setEnclosedFilter((EventFilter2D) tracker);
        
        initFilter();
        
        
        //We need the following trajectories:
        // Target/JitterTarget --> PanTiltAimer
        // PanTiltPos --> PanTilt
        // VisualTarget --> StimulusFrame
        
        // We need to hack things... If there is more than one visual target moving
        // search for the one that is named 'visualTarget' or otherwise cancel.

        date = new Date();
    }
    
    @Override public void propertyChange(PropertyChangeEvent evt) {
        if(!screenPtCalib.isCalibrated()) return;
        
        TrackingSuccessEvaluationPoint evaluationPoint;
        switch (evt.getPropertyName()) {
            case "objectAdded": //Sent by StimulusDisplayGUI
                // We need to know when the object changes position, so we need to listen to alle the objects.
                // We add propertyChangeListeners to all objects. They will signal when they are painted at a different location
                ((PaintableObject) evt.getNewValue()).addPropertyChangeListener(this);
                break;
            case "visualObjectChange": //Sent by PaintableObject
                evaluationPoint = (TrackingSuccessEvaluationPoint) evt.getOldValue();
                if(evaluationPoint.name.equals(VIS_TARGET_NAME)) {
                    float[] toTransform = {evaluationPoint.newPosX,evaluationPoint.newPosY,0f};
                    float[] transformed = screenPtCalib.makeInverseTransform(toTransform);
                    writeDataToFile(new TrackingSuccessEvaluationPoint(evaluationPoint.name,transformed[0],transformed[1],evaluationPoint.changeTime));
                }       
                break;
            case "PanTiltValues": //Sent by PanTilt
                float[] newPTvalues = (float[]) evt.getNewValue();
                evaluationPoint = new TrackingSuccessEvaluationPoint("PanTilt",newPTvalues[0],newPTvalues[1],System.nanoTime());
                writeDataToFile(evaluationPoint);
                break;
            case "Target": //Sent by PanTilt
                float[] newPTtargetValues = (float[]) evt.getNewValue();
                evaluationPoint = new TrackingSuccessEvaluationPoint("ptTarget",newPTtargetValues[0],newPTtargetValues[1],System.nanoTime());
                writeDataToFile(evaluationPoint);
                break;
            case "pathPlayedDone": //Sent by PaintableObject
                //This signals, that the path of the current object has played till the end.
                if(evt.getOldValue().equals(VIS_TARGET_NAME)) {
                    lock.lock();
                    try{ notFinished.signal();
                    } finally { lock.unlock(); }
                }
                break;
        }
    }
    
    private void writeDataToFile(TrackingSuccessEvaluationPoint evaluationPoint) {
        if(!isWriteDataToFile()) return;
        
        String message = "";
        try {
            if (writeFile == null) {
                if(getEvaluationSetName().equals("")) {
                    writeFile = new File(System.getProperty("user.home")+"/"+FILE_NAME_PREFIX+dateFormat.format(date)+"_"+fileNumber+".txt");
                } else {
                    writeFile = new File(System.getProperty("user.home")+"/"+FILE_NAME_PREFIX+evaluationSetName+"_"+dateFormat.format(date)+"_"+fileNumber+".txt");
                }
                writeFile.createNewFile();
                
                fileNumber++;
            
                FileWriter datawriter = new FileWriter(writeFile);
                outWriter = new BufferedWriter(datawriter);
            }
            
            if (writeFile.exists()) {
                switch (evaluationPoint.name) {
                    case VIS_TARGET_NAME:
                        message = String.valueOf(evaluationPoint.changeTime)+","+String.valueOf(evaluationPoint.newPosX)+","+String.valueOf(evaluationPoint.newPosY)+",,,,";
                        break;
                    case "PanTilt":
                        message = String.valueOf(evaluationPoint.changeTime)+",,,"+String.valueOf(evaluationPoint.newPosX)+","+String.valueOf(evaluationPoint.newPosY)+",,";
                        break;
                    case "ptTarget":
                        message = String.valueOf(evaluationPoint.changeTime)+",,,,,"+String.valueOf(evaluationPoint.newPosX)+","+String.valueOf(evaluationPoint.newPosY);
                        break;
                }
                synchronized(this){
                    outWriter.append(message);
                    outWriter.newLine();
                }
            }
        } catch (IOException e) {
            log.warning("fail to write file: "+e.getMessage());
        }
    }
    
    public void doStartStim() {
        startDisplayStimulus();
    }
    
    public void doStartEvaluationSet() {
        if(getVisualTarget() == null) return;
        
        closeFile(); //To make sure we start with a clean file
        setWriteDataToFile(true);
        player = new EvaluationSetPlayer(getNumberRepeats(),1000);
        player.start();
    }
    
    public void doDisableServos() {
        try {
            panTilt.disableAllServos();
        } catch (HardwareInterfaceException ex) {
            log.warning(ex.getMessage());
        }
    }
    
    public void doWriteFile() {
        closeFile();
    }
    
    public void doCenterPT() {
        boolean enabled = tracker.isTrackerEnabled();
        tracker.setTrackerEnabled(false); //So that the pt is not directly guided somewhere else when we reset it now.
        try {
            panTilt.setPanTiltValues(.5f, .5f);
        } catch (HardwareInterfaceException ex) {
            log.warning(ex.getMessage());
        }
        tracker.setTrackerEnabled(enabled);
    }
    
    private void startDisplayStimulus() {
        if(!(getVisualTarget() == null)){
            nanoStartTime = System.nanoTime();
            getVisualTarget().playPathOnce();
        }
    }
    
    private PaintableObject getVisualTarget() {
        Object[] objectList = stimDisp.getObjectListArray();
        if(!(objectList == null) && !(objectList.length == 0)) {
            for(Object foo : objectList) {
                PaintableObject p = (PaintableObject) foo;
                if(p.getObjectName().equals(VIS_TARGET_NAME) && p.isHasPath()) {
                    return p; 
                }
            }
        }
        return null;
    }
    
    private void closeFile() {
        if(writeFile == null || !writeFile.exists()) {
            System.out.println("No file to close!");
            return;
        }
        
        try {
            synchronized(this){
                //The last line of the file will be the start time of the visual stimulus. 
                // This allows to center the tracking around the start time of the vis stim.
                outWriter.append(String.valueOf(nanoStartTime)+",,,,,,");
                outWriter.newLine();
            }
            
            outWriter.close();
            outWriter = null;
        } catch (IOException ex) {
            log.warning(ex.getMessage());
        }
        System.out.println("File >>"+writeFile.getName()+"<< written to hard drive.");
        writeFile = null;
    }
    
    public void doShowStimGUI() {
        stimDisp.setVisible(true);
    }
    
    @Override public EventPacket<?> filterPacket(EventPacket<?> in) {
        return in;
    }

    @Override public final void resetFilter() { 
        if(!screenPtCalib.isCalibrated() && !screenPtCalib.loadCalibration("screenPTCalib")) {
            //If neither the system is already calibrated nor can the calibration
            // be loaded with the standard name we load it from default.
            log.warning("The panTilt-Screen calibration is not found. The default is loaded instead.");
            screenPtCalib.setCalibration(CalibrationPanTiltScreen.getScreenPanTiltDefaultCalibration());
        }
    }

    @Override public final void initFilter() { 
        stimDisp.addPropertyChangeListener(this);
        panTilt.addPropertyChangeListener(this);
        resetFilter(); 
    }

    public int getNumberRepeats() {
        return numberRepeats;
    }

    public void setNumberRepeats(int numberRepeats) {
        this.numberRepeats = numberRepeats;
    }

    public String getEvaluationSetName() {
        return evaluationSetName;
    }

    public void setEvaluationSetName(String fileName) {
        this.evaluationSetName = fileName;
    }

    public boolean isWriteDataToFile() {
        return writeDataToFile;
    }

    public void setWriteDataToFile(boolean writeDataToFile) {
        boolean OldValue = this.writeDataToFile;
        this.writeDataToFile = writeDataToFile;
        support.firePropertyChange("writeDataToFile",OldValue,writeDataToFile);
    }

    public int getMillisBetweenTrials() {
        return millisBetweenTrials;
    }

    public void setMillisBetweenTrials(int millisBetweenTrials) {
        this.millisBetweenTrials = millisBetweenTrials;
    }

    
    
}
