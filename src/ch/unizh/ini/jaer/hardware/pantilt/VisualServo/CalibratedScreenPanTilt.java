/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.hardware.pantilt.VisualServo;

import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.chip.AEChip;
import ch.unizh.ini.jaer.hardware.pantilt.PanTilt;
import java.awt.Graphics;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import java.beans.PropertyChangeListener;
import java.util.logging.Logger;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.Matrix;
import java.util.TimerTask;

/**
 *
 * @author Bjoern
 */
public class CalibratedScreenPanTilt extends EventFilter2D implements PropertyChangeListener, FrameAnnotater {
    
    RectangularClusterTracker tracker;
    PanTilt panTilt;
    ScreenActionCanvas CalibrationGUI;
    private final CalibrationFrame CalFrame;
    private boolean Calibrating = false;
    private int flashingFreqHz = getInt("flashingFreqHz",10);
    private boolean testScreenCalibEnabled = getBoolean("testScreenCalibEnabled",false);
    private boolean testRetinaCalibEnabled = getBoolean("testRetinaCalibEnabled",false);
    
    private int CalibrationStep = 1;
    private float[] screenCenterPoint = new float[2];
    private float[] panTiltCenterPoint = new float[2];
    private final calibTestPanel testPanel = new calibTestPanel();

    private float newPanLimit, newTiltLimit;
    
    private final java.util.Timer timer = new java.util.Timer();
    private TimerTask task,task2;
    
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
    private final CalibrationTransformation screenPTCalib, retinaPTCalib;
    
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
                }   break;
            case "key":
                float[] curTarget = panTilt.getTarget();
                switch ((Integer) evt.getNewValue()) {
                    case KeyEvent.VK_DOWN:
                        if((Integer) evt.getOldValue() == 0) CalFrame.move(0, .01f);
                        else panTilt.setTarget(curTarget[0], curTarget[1]-.02f);
                        break;
                    case KeyEvent.VK_UP:
                        if((Integer) evt.getOldValue() == 0) CalFrame.move(0, -.01f);
                        else panTilt.setTarget(curTarget[0], curTarget[1]+.02f);
                        break;
                    case KeyEvent.VK_LEFT:
                        if((Integer) evt.getOldValue() == 0) CalFrame.move(-.01f, 0);
                        else panTilt.setTarget(curTarget[0]-.02f, curTarget[1]);
                        break;
                    case KeyEvent.VK_RIGHT:
                        if((Integer) evt.getOldValue() == 0) CalFrame.move(.01f, 0);
                        else panTilt.setTarget(curTarget[0]+.02f, curTarget[1]);
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
                        CalFrame.setRectVisible(!CalFrame.isRectVisible());
                        break;
                    default:
                        Toolkit.getDefaultToolkit().beep();
                }   break;
        }
    }

    CalibratedScreenPanTilt(AEChip chip, PanTilt pt, ScreenActionCanvas CalibGUI) {
        super(chip);
        
        panTilt = pt;
        tracker = new RectangularClusterTracker(chip);
        CalibrationGUI = CalibGUI;
        
        CalibrationGUI.addPropertyChangeListener(this); //need to knwo when this is resized
        panTilt.addPropertyChangeListener(this);
        CalFrame = new CalibrationFrame();
        CalibrationGUI.setContentPane(CalFrame);
        
        screenPTCalib = new CalibrationTransformation(chip,"screenPTCalib");
        retinaPTCalib = new CalibrationTransformation(chip,"retinaPTCalib");
        
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
        resetCalibration(); //this means we are trowing away everything we had
    }

    @Override public void initFilter() {
        resetFilter();
    } 
    
    @Override public void annotate(GLAutoDrawable drawable) {
        if (!isFilterEnabled()) return;
        tracker.annotate(drawable);
    }
    
    public void doShowCalibration() {
        Matrix.print(retinaPTCalib.getInverseTransformation());
        Matrix.print(retinaPTCalib.getTransformation());
    }
    
    public void doCalibrate() {
        if(!CalibrationGUI.isShowing()) {
            CalibrationGUI.setVisible(true);
            CalibrationGUI.requestFocus();
        }
        CalibrationGUI.setContentPane(CalFrame);

        log.info("Align the DVS such that the displayed flashing frame is \n" +
                 "filling out the whole visual space. Use SHIFT+Arrow_Keys to \n" +
                 "move the PanTilt. Use Arrow_Keys to move frame. Use WASD to \n" +
                 "make the frame bigger or smaller. When the frame is aligned \n" +
                 "use ENTER to make the outer parts disapear. Then use SPACE \n" +
                 "to initiate a calibration step.");
        
        startCalibration();
    }
    
    public void doResetCalibration() {
        resetCalibration();
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
    
    private void setNewSamplePoint(CalibrationTransformation trafo) {
        Point2D.Float pRet;        
        if( tracker.getNumVisibleClusters() == 1 ) {
            RectangularClusterTracker.Cluster c=tracker.getVisibleClusters().get(0);
            if(c.getVelocityPPS().distance(0,0) < 3f) {
                pRet = c.getLocation();
            } else {
                //log.info("cluster too fast to measure accurately!");
                return;
            }
        } else {
            log.info("More than one clusters visible. ignoring point");
            return;
        }
        float[] curValues = panTilt.getPanTiltValues();
        int size = trafo.getNumSamples();
        float[][] lastSamples = trafo.getPanTiltSamples();
        if(size != 0 && curValues[0] == lastSamples[0][size-1] && curValues[1] == lastSamples[1][size-1]) {
            //log.info("panTilt values did not change to last point! ignoring point.");
            return;
        }
        trafo.addNewSamplePoint(pRet.x,pRet.y,curValues[0],curValues[1],CalFrame.getPosX(),CalFrame.getPosY());
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
            panTiltCenterPoint = panTilt.getPanTiltValues();
            
            //In the first step set the current limits for comparison
            newPanLimit = panTilt.getLimitOfPan();
            newTiltLimit = panTilt.getLimitOfTilt();
            
            CalFrame.setFrameVisible(false);
            CalFrame.setRectVisible(true);
            
            log.info("Centerpositions of screen and panTilt aquired. \n"
                    + "Next will be the calibration of the retina to the panTilt." +
                     "For that please move the PanTilt around such that the dot" +
                     " appears in different places on the screen. When the dot"
                    + "is stationarry and the pantilt position is different from"
                    + "the last recorded a samplepoint will automatically be"
                    + "recorded. Do NOT move the dot on the screen with the arrow keys " +
                     "or otherwise the calinbration will fail!");

            task2 = new TimerTask() {
                @Override public void run() {
                    if(retinaPTCalib.getNumSamples() > 20) {
                        CalibrationStep = 3;
                        log.info("Retina calibration aquired. press SPACE to continue");
                        task2.cancel();
                    }
                    setNewSamplePoint(retinaPTCalib);
                }
            };
            timer.scheduleAtFixedRate(task2, 100, 500); 
        } else if(CalibrationStep == 3) {
            setNewSamplePoint(retinaPTCalib);
            computeRetinaPanTiltCalibration() ;//DO the Inner calib here
            log.info("Next will be the calibration" +
                     " of the screen to panTilt. For that please move the panTilt" +
                     " such that only the screen is visible, not the edges. Then" +
                     " move the frame such that it fills the whole visiual area " +
                     "of the retina. Then press ENTER to make the outer frame" + 
                     " disapear and press SPACE to record a new calibration point.");
            CalFrame.setFrameVisible(true);
            CalFrame.setRectVisible(false);
            
            panTilt.setTarget(0, 1); // This will most likely be too far. In the next step we have a prior on where the edge of the screen is.
            
            CalFrame.setPosX(-.9f+CalFrame.getScaleX());
            CalFrame.setPosY(-.9f+CalFrame.getScaleY());
            
            CalibrationStep = 4;
        } else if(CalibrationStep == 4) {
            setNewSamplePoint(screenPTCalib);
            newPanTiltLimitClue();
            panTilt.setTarget(.5f + newPanLimit, .5f + newTiltLimit); //top right
            
            CalFrame.setPosX(-CalFrame.getPosX());
            CalFrame.setPosY(CalFrame.getPosY());
            CalibrationStep = 7; 
        } else if(CalibrationStep == 7) {
            setNewSamplePoint(screenPTCalib);
            newPanTiltLimitClue();
            panTilt.setTarget(.5f + newPanLimit, .5f - newTiltLimit); //bottom right
           
            CalFrame.setPosX(CalFrame.getPosX());
            CalFrame.setPosY(-CalFrame.getPosY());
            CalibrationStep = 5;
        } else if(CalibrationStep == 5) {
            setNewSamplePoint(screenPTCalib);
            newPanTiltLimitClue();
            panTilt.setTarget(.5f - newPanLimit, .5f - newTiltLimit); //bottom left
           
            CalFrame.setPosX(-CalFrame.getPosX());
            CalFrame.setPosY(CalFrame.getPosY());
            CalibrationStep = 6;
        } else if(CalibrationStep == 6) {
            setNewSamplePoint(screenPTCalib);
            newPanTiltLimitClue();
            computeScreenPanTiltCalibration();
            panTilt.setLimitOfPan(newPanLimit);
            panTilt.setLimitOfTilt(newTiltLimit);
            
            panTilt.setTarget(panTiltCenterPoint[0], panTiltCenterPoint[1]); //center
            CalFrame.stopFlashing();
            setCalibrating(false);
        }   
    }
     
    public void startCalibration() {
        resetCalibration(); //just to make sure
        setCalibrating(true);
        CalibrationGUI.requestFocus();

        panTilt.setTarget(.5f, .5f); //center panTilt
        panTilt.setLimitOfPan(.5f);
        panTilt.setLimitOfTilt(.5f);
        
        CalFrame.startFlashing();
        CalFrame.setRectVisible(false);
    }

    public void resetCalibration() {
        CalFrame.stopFlashing();
        CalFrame.reset();
        retinaPTCalib.resetFilter();
        screenPTCalib.resetFilter();
        screenCenterPoint = new float[2];
        CalibrationStep=1;
        setCalibrating(false);
        if(task2 != null) {
           task2.cancel();
           task2 = null;
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
        //pantiltvalues=screenPanTiltTransform*ScreenValues; P=TS.
        // we want to find T.
        // screenPanTiltTransform is a 3x3 matrix
        // screenvalues is a 3xn matrix, where each column is X,Y,1 (relative to ScreenCenter)
        // pantiltvalues is a 2xn matrix, where each column is pan,tilt
        //
        // This routine finds the least squares fit from the retina to pantilt coordinates.
        float[][] invScreen = new float[3][3];
        float[][] foo = new float[n][3];
        float[][] transform = new float[3][3];
        float[][] transScreen = Matrix.transposeMatrix(screenSamples); //S^t
        Matrix.multiply(screenSamples,transScreen,invScreen); // S*S^t
        Matrix.invert(invScreen); //(S*S^t)^-1
        Matrix.multiply(transScreen, invScreen, foo);// S^t * (S*S^t)^-1
        Matrix.multiply(ptSamples,foo,transform);// P*S^t * (S*S^t)^-1
        
        //Theoretically the invTransform should just be the matrix inverse of the
        // transform. However due to float precission, estimated inversion and
        // inconsistent data it is safer to calculate the inverse seperately.
        float[][] invPT = new float[3][3];
        float[][] invTransform = new float[3][3];
        float[][] transPT = Matrix.transposeMatrix(ptSamples); //R^t
        Matrix.multiply(ptSamples,transPT,invPT); // R*R^t
        Matrix.invert(invPT); //(R*R^t)^-1
        Matrix.multiply(transPT, invPT, foo);// R^t * (R*R^t)^-1
        Matrix.multiply(screenSamples,foo,invTransform);// P*R^t * (R*R^t)^-1
        
        screenPTCalib.setTransformation(transform);
        screenPTCalib.setInverseTransformation(invTransform);
        
        System.out.println("screen samples:");
        Matrix.print(screenSamples);
        System.out.println("pan-tilt samples:");
        Matrix.print(ptSamples);
        System.out.println("transform from screen to pan-tilt:");
        Matrix.print(screenPTCalib.getTransformation());
        System.out.println("transform from pan-tilt to screen:");
        Matrix.print(retinaPTCalib.getInverseTransformation(),10);
        
        screenPTCalib.setCalibrated(true);
        screenPTCalib.saveCalibration();
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
        
        //relativepantiltvalues=retPanTiltTransform*relativeretinavalues; P=TR.
        // we want to find T.
        // retPanTiltTransform is a 3x3 matrix
        // retinavalues is a 3xn matrix, where each column is deltaX,deltaY,1,
        // pantiltvalues is a 3xn matrix, where each column is deltaPAN,deltaTILT,1
        //
        // This routine finds the least squares fit from the retina to pantilt coordinates.
        float[][] invRelRet = new float[3][3];
        float[][] foo = new float[n-1][3];
        float[][] transform = new float[3][3];
        float[][] transRelRet = Matrix.transposeMatrix(relRet); //R^t
        Matrix.multiply(relRet,transRelRet,invRelRet); // R*R^t
        Matrix.invert(invRelRet); //(R*R^t)^-1
        Matrix.multiply(transRelRet, invRelRet, foo);// R^t * (R*R^t)^-1
        Matrix.multiply(relPT,foo,transform);// P*R^t * (R*R^t)^-1
        
        //Theoretically the invTransform should just be the matrix inverse of the
        // transform. However due to float precission, estimated inversion and
        // inconsistent data it is safer to calculate the inverse seperately.
        float[][] invRelPT = new float[3][3];
        float[][] invTransform = new float[3][3];
        float[][] transRelPT = Matrix.transposeMatrix(relPT); //R^t
        Matrix.multiply(relPT,transRelPT,invRelPT); // R*R^t
        Matrix.invert(invRelPT); //(R*R^t)^-1
        Matrix.multiply(transRelPT, invRelPT, foo);// R^t * (R*R^t)^-1
        Matrix.multiply(relRet,foo,invTransform);// P*R^t * (R*R^t)^-1
        
        transform[0][1]=0;
        transform[1][0]=0;
        transform[2][0]=0;
        transform[2][1]=0;
        transform[0][2]=0;
        transform[1][2]=0;
        
        invTransform[0][1]=0;
        invTransform[1][0]=0;
        invTransform[2][0]=0;
        invTransform[2][1]=0;
        invTransform[0][2]=0;
        invTransform[1][2]=0;
        
        retinaPTCalib.setTransformation(transform);
        retinaPTCalib.setInverseTransformation(invTransform);
        
        System.out.println("relative retina samples:");
        Matrix.print(relRet,5);
        System.out.println("relative pan-tilt samples:");
        Matrix.print(relPT,5);
        System.out.println("transform from relative retina to relative pan-tilt:");
        Matrix.print(retinaPTCalib.getTransformation(),10);
        System.out.println("transform from relative pan-tilt to relative retina:");
        Matrix.print(retinaPTCalib.getInverseTransformation(),10);

        retinaPTCalib.setCalibrated(true);
        retinaPTCalib.saveCalibration();
    }
    
    public void doTestScreenCalib() {
        if(!testScreenCalibEnabled){
            if(!screenPTCalib.isCalibrated()) return;
            if(!CalibrationGUI.isShowing()) {
                CalibrationGUI.setVisible(true);
            }
            CalibrationGUI.setContentPane(testPanel);
            CalibrationGUI.requestFocus();
            log.info("Enabeling following dot on screen that mimiks retina center position.");
        }
        testScreenCalibEnabled = !testScreenCalibEnabled;    
    }
    
    public void doTestRetinaCalib() {
        if(!retinaPTCalib.isCalibrated()) return;
        testRetinaCalibEnabled = !testRetinaCalibEnabled;

        if(task != null) {
            task.cancel();
            task = null;
            log.info("STOPING retinaCalibration test task.");
        }else{
            task = new TimerTask() {
                Point2D.Float pRet;
                @Override
                public void run() {
                    if( tracker.getNumVisibleClusters() == 1 ) {
                        RectangularClusterTracker.Cluster c=tracker.getVisibleClusters().get(0);
                        pRet =  c.getLocation();
                        float[] newTarget = transRetinaToPanTilt(new float[] {pRet.x,pRet.y});
                        log.info("found cluster at ("+pRet.x+","+pRet.y+") this gives a delta pantilt of ("+newTarget[0]+","+newTarget[1]+")");
                        panTilt.setTargetChange(newTarget[0], newTarget[1]);
                    } else {
                        log.info("Works only with exactly one cluster visble.");
                    }
                }
            };
            timer.scheduleAtFixedRate(task, 0, 500); // 500 ms delay 
            log.info("STARTING retinaCalibration test task.");
        }
    }
    
    public float[][] getRetinaPanTiltTransform() {
        return retinaPTCalib.getTransformation();
    }
    
    public float[][] getScreenPanTiltTransform() {
        return screenPTCalib.getTransformation();
    }
    
    public float[] transRetinaToPanTilt(float[] fromPos,float[] toPos) {
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
        float[] pt;
        if(panTiltPos.length == 3) {
            pt = panTiltPos;
        }else if(panTiltPos.length == 2) {
            // add the constant placeholder
            pt = new float[] {panTiltPos[0],panTiltPos[1],1};
        }else return null;
        
        return screenPTCalib.makeInverseTransform(pt);
    }
  
}
