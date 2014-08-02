
package ch.unizh.ini.jaer.projects.bjoernbeyer.pantiltscreencalibration;

import ch.unizh.ini.jaer.hardware.pantilt.PanTilt;
import ch.unizh.ini.jaer.projects.bjoernbeyer.stimulusdisplay.ScreenActionCanvas;
import java.awt.Graphics;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.TimerTask;
import java.util.logging.Logger;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.util.Matrix;

/**
 *
 * @author Bjoern
 */
@Description("Allows calibrating the 'DVS mounted on the PanTilt' system as "+ 
             "well as calibrating the panTilt and a Display for quantitative tracking results.")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class CalibratorFilterPanTiltScreen extends EventFilter2D implements PropertyChangeListener, FrameAnnotater {
    
    private RectangularClusterTracker tracker;
    private PanTilt panTilt;
    private ScreenActionCanvas CalibrationGUI;
    private final CalibrationFrame CalFrame;
    private boolean Calibrating = false;
    private int flashingFreqHz = getInt("flashingFreqHz",10);
    private boolean testScreenCalibEnabled = getBoolean("testScreenCalibEnabled",false);
    
    private int CalibrationStep = 1;
    private float clusterSpeedThreshold = 3f;
    private float[] screenCenterPoint = new float[2];
    private float[] panTiltCenterPoint = new float[2];
    private final calibTestPanel testPanel = new calibTestPanel();

    private float newPanLimit, newTiltLimit;
    
    private final java.util.Timer timer = new java.util.Timer();
    private TimerTask retinaTestTask,retinaPTsampleTask;
    private CalibrationPanTiltScreen screenPTCalib, retinaPTCalib;
    
    private static final Logger log=Logger.getLogger("CalibratedScreenPanTilt");
    
    private class calibTestPanel extends javax.swing.JPanel {
        private float x,y;
        int width;
        int height;
        
        calibTestPanel() {
            super();
            this.width = getWidth()/2;
            this.height = getHeight()/2;
        }
        
        public void setPosition(float xVal, float yVal) {
            this.width = getWidth()/2;
            this.height = getHeight()/2;
            x=xVal;
            y=yVal;
        }
        
        @Override public void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.fillOval((int)(width*(1+x)), (int)(height*(1+y)), 10, 10);
        }
    }
    
    public CalibratorFilterPanTiltScreen(AEChip chip) {
        this(chip,PanTilt.getInstance(0));
    }
    public CalibratorFilterPanTiltScreen(AEChip chip,PanTilt pt) {
        this(chip,pt,new ScreenActionCanvas());
    }
    public CalibratorFilterPanTiltScreen(AEChip chip,PanTilt pt, ScreenActionCanvas calibGUI) {
        super(chip);
        
        CalFrame = new CalibrationFrame();
        
        tracker = new RectangularClusterTracker(chip);
            tracker.setAnnotationEnabled(true);
            tracker.setShowClusterVelocity(true);
            tracker.setClusterMassDecayTauUs(200000);
            tracker.setThresholdMassForVisibleCluster(20);
            tracker.setPathLength(300);
            tracker.setUseVelocity(false);
            tracker.setMaxNumClusters(1);
            
        CalibrationGUI = calibGUI;
            CalibrationGUI.addPropertyChangeListener(this); //need to knwo when this is resized
            CalibrationGUI.setContentPane(CalFrame);
            
        panTilt = pt;
            panTilt.addPropertyChangeListener(this);
        
        setEnclosedFilter(tracker);
    }
    
    //We dont really do anything here. We only need the enclosed cluster tracker
    @Override public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!isFilterEnabled()) return in;
        tracker.filterPacket(in);
        return in;
    }

    @Override public void resetFilter() {
        tracker.resetFilter();
    }

    @Override public void initFilter() { resetFilter(); } 
    
    @Override public void annotate(GLAutoDrawable drawable) {
        if (!isFilterEnabled()) return;
        tracker.annotate(drawable);
    }
    
    public void doLoadCalibration() {
        if(screenPTCalib==null) screenPTCalib = new CalibrationPanTiltScreen("screenPTCalib");
        if(retinaPTCalib == null) retinaPTCalib = new CalibrationPanTiltScreen("retinaPTCalib");
        doShowCalibration();
    }
    
    public void doShowCalibration() {
        if(screenPTCalib == null) screenPTCalib = new CalibrationPanTiltScreen("screenPTCalib");
        if(retinaPTCalib == null) retinaPTCalib = new CalibrationPanTiltScreen("retinaPTCalib");
        
        retinaPTCalib.displayCalibration(10);
        screenPTCalib.displayCalibration(10);
    }
    
    public void doLoadDefaultCalibration() {
        if(screenPTCalib==null) screenPTCalib = new CalibrationPanTiltScreen("screenPTCalib");
        if(retinaPTCalib == null) retinaPTCalib = new CalibrationPanTiltScreen("retinaPTCalib");
        
        retinaPTCalib.setCalibration(CalibrationPanTiltScreen.getRetinaPanTiltDefaultCalibration());
        retinaPTCalib.saveCalibration();
        
        screenPTCalib.setCalibration(CalibrationPanTiltScreen.getScreenPanTiltDefaultCalibration());
        screenPTCalib.saveCalibration();
        
        doShowCalibration();
    }
    
    public void doCalibrate() {
        if(!CalibrationGUI.isShowing()) {
            CalibrationGUI.setVisible(true);
            CalibrationGUI.requestFocus();
        }
        CalibrationGUI.setContentPane(CalFrame);

        screenPTCalib = new CalibrationPanTiltScreen("screenPTCalib");
        retinaPTCalib = new CalibrationPanTiltScreen("retinaPTCalib");
        
        startCalibration();
    }
    
    public void doResetCalibration() {
        resetCalibration();
    }
    
    public void doDisableServos() {
        try {
            panTilt.disableAllServos();
        } catch (HardwareInterfaceException ex) {
            log.warning(ex.getMessage());
        }
    }
    
    public void doTestScreenCalib() {
        if(!screenPTCalib.isCalibrated()) {
            log.warning("screen-PanTilt is not calibrated! Please load or do a calibration!");
            return;
        }
        
        testScreenCalibEnabled = !testScreenCalibEnabled;
        if(testScreenCalibEnabled){
            if(!CalibrationGUI.isShowing()) {
                CalibrationGUI.setVisible(true);
            }
            CalibrationGUI.setContentPane(testPanel);
            CalibrationGUI.revalidate();
            CalibrationGUI.repaint();
            CalibrationGUI.requestFocus();
            log.info("Enabeling following dot on screen that mimiks retina center position.");
        }
            
    }
    
    public void doTestRetinaCalib() {
        if(!retinaPTCalib.isCalibrated()) {
            log.warning("retina-PanTilt is not calibrated! Please load or do a calibration!");
            return;
        }

        if(retinaTestTask != null) {
            retinaTestTask.cancel();
            retinaTestTask = null;
            System.out.println("STOPPING retinaCalibration test task.");
        } else {
            retinaTestTask = new TimerTask() {
                Point2D.Float pRet;
                @Override public void run() {
                    if( tracker.getNumVisibleClusters() == 1 ) {
                        RectangularClusterTracker.Cluster c=tracker.getVisibleClusters().get(0);
                        pRet =  c.getLocation();
                        float[] newTarget = transRetinaToPanTilt(new float[] {pRet.x,pRet.y});
                        System.out.println("found cluster at ("+pRet.x+","+pRet.y+") this gives a delta pantilt of ("+newTarget[0]+","+newTarget[1]+")");
                        panTilt.setTargetChange(newTarget[0], newTarget[1]);
                    } else {
                        System.out.println("   --> Works only with exactly one cluster visble.");
                    }
                }
            };
            timer.scheduleAtFixedRate(retinaTestTask, 0, 500); // 500 ms delay 
            System.out.println("STARTING retinaCalibration test task.");
        }
    }
    
    private boolean setNewSamplePoint(CalibrationPanTiltScreen trafo) {
        Point2D.Float pRet;        
        if( tracker.getNumVisibleClusters() == 1 ) {
            RectangularClusterTracker.Cluster c = tracker.getVisibleClusters().get(0);
            if(c.getVelocityPPS().distance(0,0) < clusterSpeedThreshold) {
                pRet = c.getLocation();
            } else {
                //log.info("cluster too fast to measure accurately!");
                return false;
            }
        } else if(tracker.getNumVisibleClusters() == 0 ) {
            System.out.println("   --> NO cluster visible. Ignoring point");
            return false;
        } else {
            System.out.println("   --> More than one clusters visible. Ignoring point");
            return false;
        }
        
        int size = trafo.getNumSamples();
        float[]   curValues   = panTilt.getPanTiltValues();
        float[][] lastSamples = trafo.getPanTiltSamples();
        if(size != 0 && curValues[0] == lastSamples[0][size-1] && curValues[1] == lastSamples[1][size-1]) {
            //log.info("panTilt values did not change to last point! ignoring point.");
            return false;
        }
        trafo.addNewSamplePoint(pRet.x,pRet.y,curValues[0],curValues[1],CalFrame.getPosX(),CalFrame.getPosY());
        return true;
    }
    
    private void newPanTiltLimitClue() {
        //As the pantilt is moved to the outer edges of the screen we save the
        // smallest values and set them as limits in the last step, thus making
        // sure that the dvs is only seeing the screen but never the edge which
        // would cause distractions.
        float[] curValues = panTilt.getPanTiltValues();
        float diffPan = Math.abs(0.5f-curValues[0]);
        float diffTilt = Math.abs(0.5f-curValues[1]);
        if(diffPan  < newPanLimit)  newPanLimit = diffPan;
        if(diffTilt < newTiltLimit) newTiltLimit = diffTilt;
    }
    
    private void makeCalibrationStep() {
        if(!isCalibrating()) return;

        if(CalibrationStep == 1) {//inner calibration not finished
            screenCenterPoint[0] = CalFrame.getPosX();
            screenCenterPoint[1] = CalFrame.getPosY();
            panTiltCenterPoint   = panTilt.getPanTiltValues();
            
            //In the first step set the current limits for comparison
            newPanLimit  = panTilt.getLimitOfPan();
            newTiltLimit = panTilt.getLimitOfTilt();
            
            CalFrame.setFrameVisible(false);
            
            System.out.println("Screen center point: ("+screenCenterPoint[0]+","+screenCenterPoint[1]+")");
            log.info("Centerpositions of screen and panTilt aquired. \n"
                    + "Next will be the calibration of the retina to the panTilt." +
                     "For that please move the PanTilt around such that the dot" +
                     " appears in different places on the screen. When the dot"
                    + "is stationarry and the pantilt position is different from"
                    + "the last recorded a samplepoint will automatically be"
                    + "recorded. Do NOT move the dot on the screen with the arrow keys " +
                     "or otherwise the calinbration will fail!");

            retinaPTsampleTask  = new TimerTask() {
                @Override public void run() {
                    if(retinaPTCalib.getNumSamples() > 20) {
                        CalibrationStep = 3;
                        log.info("Retina calibration aquired. press SPACE to continue");
                        retinaPTsampleTask.cancel();
                    }
                    setNewSamplePoint(retinaPTCalib);
                }
            };
            timer.scheduleAtFixedRate(retinaPTsampleTask, 100, 500); 
            CalibrationStep = 3;
        } else if(CalibrationStep == 3) {
            computeRetinaPanTiltCalibration() ;//DO the Inner calib here
            log.info("Next will be the calibration" +
                     " of the screen to panTilt. For that please move the panTilt" +
                     " such that only the screen is visible, not the edges. Then" +
                     " move the frame such that it fills the whole visiual area " +
                     "of the retina. Then press ENTER to make the outer frame" + 
                     " disapear and press SPACE to record a new calibration point.");
            CalFrame.setFrameVisible(true);
            
            panTilt.setTarget(0, 0); // This will most likely be too far. In the next step we have a prior on where the edge of the screen is.
            //Top left
            
            CalFrame.setPosX(-.9f+CalFrame.getScaleX());
            CalFrame.setPosY(-.9f+CalFrame.getScaleY());
            
            CalibrationStep = 4;
        } else if(CalibrationStep == 4) { //top right
            acquireScreenPTSample(1, -1,-1,1);
        } else if(CalibrationStep == 5) { //bottom right
            acquireScreenPTSample(1, 1,1,-1);
        } else if(CalibrationStep == 6) { //bottom left
            acquireScreenPTSample(-1, 1,-1,1);
        } else if(CalibrationStep == 7) {
            acquireScreenPTSample(0, 0,0,0);
        } else if(CalibrationStep == 8) {
            screenPTCalib.setLimitsOfPanTilt(newPanLimit, newTiltLimit);
            retinaPTCalib.setLimitsOfPanTilt(newPanLimit, newTiltLimit);

            if(retinaPTCalib.checkCalibrated()){
                retinaPTCalib.saveCalibration();
            } else {
                log.info("The retinaPT calibration was not complete and hence has not been saved!");
            }
            
            computeScreenPanTiltCalibration();
            if(screenPTCalib.checkCalibrated()) {
                screenPTCalib.saveCalibration();
            } else {
                log.info("The screenPT calibration was not complete and hence has not been saved!");
            }
        
            panTilt.setLimitOfPan(newPanLimit);
            panTilt.setLimitOfTilt(newTiltLimit);

            CalFrame.stopFlashing();
            try {
                panTilt.disableAllServos();
            } catch (HardwareInterfaceException ex) {
                log.warning(ex.getMessage());
            }
            setCalibrating(false);
        }   
    }
    
    private void acquireScreenPTSample(float relPosPan, float relPosTilt, float CalFrameX, float CalFrameY) {
        float panTarget, tiltTarget;
        if(setNewSamplePoint(screenPTCalib)){
            newPanTiltLimitClue();
            
            panTarget = .5f + relPosPan*newPanLimit;
            tiltTarget = .5f + relPosTilt*newTiltLimit;
            panTilt.setTarget(panTarget, tiltTarget);

            CalFrame.setPosX(CalFrameX*CalFrame.getPosX());
            CalFrame.setPosY(CalFrameY*CalFrame.getPosY());
            
            CalibrationStep++;
        } else {
            System.out.println("   --> sample point could not be acquired. Please try again!");
        }
    }
     
    public void startCalibration() {
        log.info("Align the DVS such that the displayed flashing frame is \n" +
                 "filling out the whole visual space. Use SHIFT+Arrow_Keys to \n" +
                 "move the PanTilt. Use Arrow_Keys to move frame. Use WASD to \n" +
                 "make the frame bigger or smaller. When the frame is aligned \n" +
                 "use ENTER to make the outer parts disapear. Then use SPACE \n" +
                 "to initiate a calibration step.");
        
        resetCalibration(); //just to make sure
        setCalibrating(true);
        CalibrationGUI.requestFocus();

        panTilt.setTarget(.5f, .5f); //center panTilt
        panTilt.setLimitOfPan(.5f);
        panTilt.setLimitOfTilt(.5f);
        
        CalFrame.startFlashing();
    }

    public void resetCalibration() {
        CalFrame.stopFlashing();
        CalFrame.reset();
        retinaPTCalib.resetCalibration();
        screenPTCalib.resetCalibration();
        screenCenterPoint = new float[2];
        CalibrationStep=1;
        setCalibrating(false);
        if(retinaPTsampleTask != null) {
           retinaPTsampleTask.cancel();
           retinaPTsampleTask = null;
        }
    }
    
    public float[] getScreenCenter() {
        if(screenPTCalib.isCalibrated()) {
            return screenCenterPoint;
        }
        return null;
    }
    
    public float[] getPanTiltCenter() {
        if(screenPTCalib.isCalibrated()) {
            return panTiltCenterPoint;
        }
        return null;
    }

    private void computeScreenPanTiltCalibration() {
        float[][] retSamples = screenPTCalib.getRetinaSamples();
        float[][] ptSamples  = screenPTCalib.getPanTiltSamples();
        float[][] screenSamples = screenPTCalib.getScreenSamples();
        int n = screenPTCalib.getNumSamples();
        
        if(retinaPTCalib.isCalibrated()) {
            for(int i=0;i<n;i++){
                float[] retPoint = {(float) retSamples[0][i],(float)retSamples[1][i]};
                float[] corPanTilt = transRetinaToPanTilt(retPoint);
                ptSamples[0][i] += corPanTilt[0];
                ptSamples[1][i] += corPanTilt[1];
            }
            log.info("improved pantilt samplepoints based on existing pantilt-retina calibration.");
        } else {
            log.warning("Trying to calibrate the Screen while the Retina is not. This will decrease accuracy!");
        }
        
        //Theoretically the invTransform should just be the matrix inverse of the
        // transform. However due to float precission, estimated inversion and
        // inconsistent data it is safer to calculate the inverse seperately.
        float[][] transform = computeTransform(screenSamples,ptSamples);
        float[][] invTransform = computeTransform(ptSamples,screenSamples);
        
        screenPTCalib.setTransformation(transform);
        screenPTCalib.setInverseTransformation(invTransform);
        
        
        System.out.println("screen samples:");
        Matrix.print(screenSamples,5);
        System.out.println("pan-tilt samples:");
        Matrix.print(ptSamples,5);
        screenPTCalib.displayCalibration(10);
    }
    
    private void computeRetinaPanTiltCalibration() {
        float[][] retSamples = retinaPTCalib.getRetinaSamples();
        float[][] ptSamples  = retinaPTCalib.getPanTiltSamples();
        float[][] screenSamples = retinaPTCalib.getScreenSamples();
        int n = retinaPTCalib.getNumSamples();
        if(n==0){
            log.warning("No sample found, calibration can not be completed!");
            return;
        }
        float[][] relRet = new float[3][n-1],relPT = new float[3][n-1];
        for(int i=0;i<n-1;i++){
            if(screenSamples[0][i]!=screenSamples[0][i+1] || screenSamples[1][i]!=screenSamples[1][i+1]){
                log.warning("Retina-PanTilt calibration can not be finished, as not all points are generated from the same screen position");
                resetCalibration();
                return;
            }
            
            //We actually want to retPanTiltTransform changes on retina to changes on pantilt
            // This is because in pantilt-space the absolute positions obviously
            // dont make any sense, as the camera is mounted ontop of it. So we
            // are only interested in realtive positions in the pantilt. As for the 
            // retina, we could do the retPanTiltTransform with a fixed position and retPanTiltTransform
            // to the neccessary change to center the image on the retina. Or we use
            // relative coordinates in the retina as well such that we have the freedom
            // to set the retinal image to any desired location.
            relRet[0][i] = retSamples[0][i]-retSamples[0][i+1];
            relRet[1][i] = retSamples[1][i]-retSamples[1][i+1];
            
            relRet[2][i] = 1;
            relPT[0][i] = ptSamples[0][i]-ptSamples[0][i+1];
            relPT[1][i] = ptSamples[1][i]-ptSamples[1][i+1];
            relPT[2][i] = 1;
        }
        
        //Theoretically the invTransform should just be the matrix inverse of the
        // transform. However due to float precission, estimated inversion and
        // inconsistent data it is safer to calculate the inverse seperately.
        float[][] transform = computeTransform(relRet,relPT);
        float[][] invTransform = computeTransform(relPT,relRet);
        
        retinaPTCalib.setTransformation(transform);
        retinaPTCalib.setInverseTransformation(invTransform);
        
        System.out.println("relative retina samples:");
        Matrix.print(relRet,5);
        System.out.println("relative pan-tilt samples:");
        Matrix.print(relPT,5);
        retinaPTCalib.displayCalibration(10);
    }
    
    private float[][] computeTransform(float[][] fromSamples, float[][] toSamples) {
        //toSamples = transform*fromSamples; P=TS.
        // we want to find T.
        // the return matrix "transform" is a 3x3 matrix
        //
        // This routine finds the least squares fit from the 'fromSamples' coordinates to 'toSamples' coordinates.
        int n = fromSamples[0].length;
        float[][] inverse = new float[3][3];
        float[][] foo = new float[n][3];
        float[][] transform = new float[3][3];
        float[][] transposed = Matrix.transposeMatrix(fromSamples); //S^t
        Matrix.multiply(fromSamples,transposed,inverse); // S*S^t
        Matrix.invert(inverse); //(S*S^t)^-1
        Matrix.multiply(transposed, inverse, foo);// S^t * (S*S^t)^-1
        Matrix.multiply(toSamples,foo,transform);// P*S^t * (S*S^t)^-1
        return transform;
    }
    
    public float[][] getRetinaPanTiltTransform() {
        if(!retinaPTCalib.isCalibrated()) throw new IllegalStateException("The RetinaPanTilt is not calibrated! Cannot return transformation!");
        return retinaPTCalib.getTransformation();
    }
    
    public float[][] getScreenPanTiltTransform() {
        if(!screenPTCalib.isCalibrated()) throw new IllegalStateException("The ScreenPanTilt is not calibrated! Cannot return transformation!");
        return screenPTCalib.getTransformation();
    }
    
    public float[] transRetinaToPanTilt(float[] fromPos,float[] toPos) {
        if(!retinaPTCalib.isCalibrated()) throw new IllegalStateException("The RetinaPanTilt is not calibrated! Cannot transform values!");
        float[] retDiff = new float[3];
        retDiff[0] = toPos[0]-fromPos[0];
        retDiff[1] = toPos[0]-fromPos[1];
        retDiff[2] = 1;
        return retinaPTCalib.makeTransform(retDiff);
    }
    
    public float[] transRetinaToPanTilt(float[] fromPos) {
        float[] zero = {64f,64f}; //TODO should this be parametric? For larger Retinas?
        return transRetinaToPanTilt(fromPos,zero);
    }
    
    public float[] transScreenToPanTilt(float[] screenPos) {
        if(!screenPTCalib.isCalibrated()) throw new IllegalStateException("The ScreenPanTilt is not calibrated! Cannot transform values!");
        float[] sp;
        if(screenPos.length == 3) {
            sp = screenPos;
        }else if(screenPos.length == 2) {
            // add the constant placeholder
            sp = new float[] {screenPos[0],screenPos[1],1};
        }else return null;
        
        return screenPTCalib.makeTransform(sp);
    }
    
    public float[] transPanTiltToScreen(float[] panTiltPos) {
        if(!screenPTCalib.isCalibrated()) throw new IllegalStateException("The ScreenPanTilt is not calibrated! Cannot transform values!");
        float[] pt;
        if(panTiltPos.length == 3) {
            pt = panTiltPos;
        }else if(panTiltPos.length == 2) {
            // add the constant placeholder
            pt = new float[] {panTiltPos[0],panTiltPos[1],1};
        }else return null;
        
        return screenPTCalib.makeInverseTransform(pt);
    }

    // <editor-fold defaultstate="collapsed" desc="getter-private setter for --Calibrating--">
    public boolean isCalibrating() {
        return Calibrating;
    }
    private void setCalibrating(boolean setCal) {
        Calibrating = setCal;
    }
    // </editor-fold>  
    
    // <editor-fold defaultstate="collapsed" desc="getter-setter for --FlashingFreqHz--">
    public void setFlashingFreqHz(int newFreq) {
        CalFrame.setFlashFreqHz(newFreq);
        putInt("flashingFreqHz",newFreq);
        int OldValue = getFlashingFreqHz();
        this.flashingFreqHz = newFreq;
        support.firePropertyChange("flashingFreqHz",OldValue,newFreq);  
    }
    public int getFlashingFreqHz() {
        return this.flashingFreqHz;
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter-setter for --clusterSpeedThreshold--">
    public float getClusterSpeedThreshold() {
        return clusterSpeedThreshold;
    }

    public void setClusterSpeedThreshold(float clusterSpeedThreshold) {
        this.clusterSpeedThreshold = clusterSpeedThreshold;
    }
    // </editor-fold>
       
    @Override public void propertyChange(PropertyChangeEvent evt) {
        switch (evt.getPropertyName()) {
            case "size":
                if(isCalibrating()) resetCalibration();//calibration is off now
                CalFrame.reset();
                break;
            case "Target":
                if(testScreenCalibEnabled){
                    float[] newVal = (float[]) evt.getNewValue();
                    float[] screenVal = transPanTiltToScreen(newVal);
                    testPanel.setPosition(screenVal[0], screenVal[1]);
                    testPanel.repaint();
                }  
                break;
            case "key":
                float[] curTarget = panTilt.getTarget();
                switch ((Integer) evt.getNewValue()) {
                    case KeyEvent.VK_DOWN:
                        if((Integer) evt.getOldValue() == 0) CalFrame.move(0, .01f);
                        else panTilt.setTarget(curTarget[0], curTarget[1]+.01f);
                        break;
                    case KeyEvent.VK_UP:
                        if((Integer) evt.getOldValue() == 0) CalFrame.move(0, -.01f);
                        else panTilt.setTarget(curTarget[0], curTarget[1]-.01f);
                        break;
                    case KeyEvent.VK_LEFT:
                        if((Integer) evt.getOldValue() == 0) CalFrame.move(-.01f, 0);
                        else panTilt.setTarget(curTarget[0]-.01f, curTarget[1]);
                        break;
                    case KeyEvent.VK_RIGHT:
                        if((Integer) evt.getOldValue() == 0) CalFrame.move(.01f, 0);
                        else panTilt.setTarget(curTarget[0]+.01f, curTarget[1]);
                        break;
                    case KeyEvent.VK_W:
                        CalFrame.rescale(0, .01f);break;
                    case KeyEvent.VK_S:
                        CalFrame.rescale(0, -.01f);break;
                    case KeyEvent.VK_A:
                        CalFrame.rescale(-.01f, 0);break;
                    case KeyEvent.VK_D:
                        CalFrame.rescale(.01f, 0);break;
                    case KeyEvent.VK_SPACE:
                        makeCalibrationStep();
                        break;
                    case KeyEvent.VK_ENTER:
                        //Turn of the frame (as it is supposed to be aligned now) and only show
                        // the central dot. This allowes the rect.cluster tracker to only identify
                        // one cluster that should be in the center of the retina. We only do this
                        // to be more accurate as we will never be square in the center.
                        CalFrame.setFrameVisible(!CalFrame.isFrameVisible());
                        break;
                    default:
                        Toolkit.getDefaultToolkit().beep();
                }
                break;
        }
    }
}
