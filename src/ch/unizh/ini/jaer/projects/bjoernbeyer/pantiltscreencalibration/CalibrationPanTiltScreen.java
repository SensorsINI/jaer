
package ch.unizh.ini.jaer.projects.bjoernbeyer.pantiltscreencalibration;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import net.sf.jaer.util.Matrix;

/**
 *
 * @author Bjoern
 */
public class CalibrationPanTiltScreen {
    private ArrayList<CalibrationPanTiltScreenListPoint> sampleList = new ArrayList<>();
    private float[][] transformation = new float[3][3];
    private float[][] inverseTransformation = new float[3][3];
    private float limitOfPan = -1f, limitOfTilt = -1f;
    private boolean isCalibrated = false;
    private String CalibrationName;
    private final String InitialCalibrationName;
    private Preferences myPrefs = null; //We need personal preferences, as we want to load the calibration independent from filters by different filters. the calibration is specific to the hardware, not the filterchain or the chip.
    
    private static final Logger log=Logger.getLogger("CalibrationTransformation");
    
    public static CalibrationPanTiltScreen getRetinaPanTiltDefaultCalibration() {
        CalibrationPanTiltScreen def = new CalibrationPanTiltScreen(null);
        float[][] trafo    = {{-0.0021130797f,0,0},{0,0.0021632595f,0},{0,0,1f}};
        float[][] invTrafo = {{-471.2072f    ,0,0},{0,460.00894f   ,0},{0,0,1f}};

        def.setTransformation(trafo);
        def.setInverseTransformation(invTrafo);
        def.setLimitsOfPanTilt(.5f, .5f);
        def.checkCalibrated();
        
        return def;
    }
    
    public static CalibrationPanTiltScreen getScreenPanTiltDefaultCalibration() {
        CalibrationPanTiltScreen def = new CalibrationPanTiltScreen(null);
        float[][] trafo    = {{0.409643f,-0.005999f,0.493012f} ,{0.019306f ,0.284783f,0.509727f} ,{0,0,1f}};
        float[][] invTrafo = {{2.659199f,0.007740f ,-1.314963f},{-0.078289f,3.611534f,-1.802297f},{0,0,1f}};

        def.setTransformation(trafo);
        def.setInverseTransformation(invTrafo);
        def.setLimitsOfPanTilt(.5f, .5f);
        def.checkCalibrated();
        
        return def;
    }
    
    //IMPORTANT: this class makes use of its own preferrences. This allows to
    // save the calibration by name that can be accessed by any other class.
    // This means, that one can calibrate a device once and save it under a
    // certain name by the calibrating filter. Then all other classes have access
    // to this particular calibration by initializing a new calss of this with the
    // same name as the calibration was done with. Of course this only works on
    // one Computer.
    public CalibrationPanTiltScreen( String CalibName ) {
        setCalibrationName(CalibName);
        InitialCalibrationName = CalibName;
        
        //This makes sure that we can load the same calibration from all different
        // filters as the preference is saved for this class and not per filter
        myPrefs = Preferences.userNodeForPackage(getClass());
        
        if(CalibName == null || !loadCalibration(CalibName)) {
            //if loadCalib returns true a calibration has been loaded and we
            // dont need to reset. Otherwise reset everything to make sure
            // things are initialized.
            // If no calibration is found the user can see this by checking the
            // 'isCalibrated' flag. Then he can load a static default with the
            // 'setCalibration' method.
            resetCalibration();
        }
    }

    public void addNewSamplePoint(float retX, float retY, float ptX, float ptY, float screenX, float screenY) {    
        CalibrationPanTiltScreenListPoint newSample  = new CalibrationPanTiltScreenListPoint(retX,retY,ptX,ptY,screenX,screenY);
        System.out.println("New samplepoint ("+getNumSamples() +") --> " + newSample.toString());
        sampleList.add(newSample);
    }
    
    public int getNumSamples() {
        return sampleList.size();
    }
    
    public float[] makeTransform(float[] fromDimension) {
        if(fromDimension.length != 3) throw new IllegalArgumentException("The value to transform must be a floatarray of size 3");
        float[] toDimension = new float[3];
        Matrix.multiply(getTransformation(),fromDimension, toDimension);
        return toDimension;
    }
    
    public float[] makeInverseTransform(float[] fromDimension) {
        if(fromDimension.length != 3) throw new IllegalArgumentException("The value to transform must be a floatarray of size 3");
        float[] toDimension = new float[3];
        Matrix.multiply(getInverseTransformation(),fromDimension, toDimension);
        return toDimension;
    }
    
    public final boolean loadCalibration( String name ) {
        resetCalibration();

        byte[] bytes = myPrefs.getByteArray(name, null);
        // Deserialize from a byte array
        if(bytes!=null) {
            try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                transformation        = (float[][]) in.readObject();
                inverseTransformation = (float[][]) in.readObject();
                limitOfPan            = (float) in.readObject();
                limitOfTilt           = (float) in.readObject();
                if((int) in.readObject() < 20) {
                    sampleList        = (ArrayList<CalibrationPanTiltScreenListPoint>) in.readObject();
                }
                isCalibrated          = true;
                setCalibrationName(name);
                log.info("Calibration >>" + name + "<< successfuly loaded!");
                return true;
            } catch( IOException | ClassNotFoundException | ClassCastException e) {
                log.warning(e.getMessage());
                return false;
            }
        } else {
            log.info("Calibration >>" + name + " << NOT FOUND! Please calibrate before loading!");
            return false;
        }
    }
    
    public void saveCalibration() {
        if(!isCalibrated()) return;
        byte[] buf;
        // Serialize to a byte array
        ByteArrayOutputStream bos=new ByteArrayOutputStream();
        try (ObjectOutput oos = new ObjectOutputStream(bos)) {
            oos.writeObject(transformation);
            oos.writeObject(inverseTransformation);
            oos.writeObject(limitOfPan);
            oos.writeObject(limitOfTilt);
            oos.writeObject(getNumSamples());
            if(getNumSamples() < 30) oos.writeObject(sampleList);
            // Get the bytes of the serialized object
            buf = bos.toByteArray();
        } catch(IOException e) {
            log.warning(e.getMessage());
            return;
        }
        myPrefs.putByteArray(getCalibrationName(), buf);
        log.info("The Calibration >>" + getCalibrationName() + " << was successfuly saved!!");
    }

    public final void resetCalibration() {
        sampleList.clear();
        transformation = new float[3][3];
        inverseTransformation = new float[3][3];
        limitOfTilt = -1;
        limitOfPan = -1;
        setCalibrationName(InitialCalibrationName);
        isCalibrated = false; 
    }
    
    public void setCalibration(CalibrationPanTiltScreen calibToSet) {
        this.setTransformation(calibToSet.getTransformation());
        this.setInverseTransformation(calibToSet.getInverseTransformation());
        this.setLimitsOfPanTilt(calibToSet.getLimitOfPan(), calibToSet.getLimitOfTilt());
        this.checkCalibrated();
    }
    
    public void displayCalibration(int precision) {
        if(!this.isCalibrated()) {
            return;
        }
        System.out.println("The pan- and tilt-limits for the >>"+getCalibrationName()+"<< are panLimit:"+getLimitOfPan()+" and tiltLimit:"+getLimitOfTilt());
        System.out.println("Transformation matrix for the >>"+getCalibrationName()+"<< calibration:");
        Matrix.print(this.getTransformation(),precision);
        System.out.println("InverseTransformation matrix for the >>"+getCalibrationName()+"<< calibration:");
        Matrix.print(this.getInverseTransformation(),precision);
    }
    
    // <editor-fold defaultstate="collapsed" desc="getter for --panTiltSamples & ScreenSamples & RetinaSamples--">
    public float[][] getPanTiltSamples() {
        int n = getNumSamples();
        float[][] res = new float[3][n];
        for(int i=0;i<n;i++){
            res[0][i] = sampleList.get(i).pt.x;
            res[1][i] = sampleList.get(i).pt.y;
            res[2][i] = 1;
        }
        return res;
    }

    public float[][] getScreenSamples() {
        int n = getNumSamples();
        float[][] res = new float[3][n];
        for(int i=0;i<n;i++){
            res[0][i] = sampleList.get(i).screen.x;
            res[1][i] = sampleList.get(i).screen.y;
            res[2][i] = 1;
        }
        return res;
    }

    public float[][] getRetinaSamples() {
        int n = getNumSamples();
        float[][] res = new float[3][n];
        for(int i=0;i<n;i++){
            res[0][i] = sampleList.get(i).ret.x;
            res[1][i] = sampleList.get(i).ret.y;
            res[2][i] = 1;
        }
        return res;
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --transformation--">
    public float[][] getTransformation() {
        return transformation;
    }
    
    public void setTransformation(float[][] trafoToSet) {
        transformation = trafoToSet;
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --inverseTransformation--">
    public float[][] getInverseTransformation() {
        return inverseTransformation;
    }
    
    public void setInverseTransformation(float[][] trafoToSet) {
        inverseTransformation = trafoToSet;
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --calibrated--">
    public boolean isCalibrated() {
        return isCalibrated;
    }
    
    public boolean checkCalibrated() {
        if(transformation != null && inverseTransformation != null && limitOfPan !=-1 && limitOfTilt !=-1) {
            isCalibrated = true;
            return true;
        } else{
            isCalibrated = false;
            return false;
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --calibrationName--">
    public String getCalibrationName() {
        return CalibrationName;
    }

    private void setCalibrationName(String CalibrationName) {
        this.CalibrationName = CalibrationName;
    }

    // </editor-fold>
    
    public float getLimitOfPan() {
        return limitOfPan;
    }

    public void setLimitOfPan(float panLimit) {
        float setValue = panLimit;
        if(setValue > 0.5f) setValue = 0.5f;
        if(setValue < 0) setValue = 0;
        this.limitOfPan = setValue;
    }

    public float getLimitOfTilt() {
        return limitOfTilt;
    }

    public void setLimitOfTilt(float tiltLimit) {
        float setValue = tiltLimit;
        if(setValue > 0.5f) setValue = 0.5f;
        if(setValue < 0) setValue = 0;
        this.limitOfTilt = setValue;
    }
    
    public void setLimitsOfPanTilt(float panLimit, float tiltLimit) {
        setLimitOfPan(panLimit);
        setLimitOfTilt(tiltLimit);
    }
    
    
    
}
