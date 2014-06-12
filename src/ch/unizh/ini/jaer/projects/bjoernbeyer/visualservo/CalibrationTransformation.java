/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.bjoernbeyer.visualservo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.util.Matrix;

/**
 *
 * @author Bjoern
 */
public class CalibrationTransformation extends EventFilter2D {
    private ArrayList<CalibrationPointPanTiltScreen> sampleList = new ArrayList<>();
    private float[][] transformation = new float[3][3];
    private float[][] inverseTransformation = new float[3][3];
    private boolean isCalibrated = false;
    private String CalibrationName;
    private final String InitialCalibrationName;
    private Preferences myPrefs = null; //We need personal preferences, as we want to load the calibration independent from filters by different filters. the calibration is specific to the hardware, not the filterchain or the chip.
    
    private static final Logger log=Logger.getLogger("CalibrationTransformation");
    
    //IMPORTANT: this class makes use of its own preferrences. This allows to
    // save the calibration by name that can be accessed by any other class.
    // This means, that one can calibrate a device once and save it under a
    // certain name by the calibrating filter. Then all other classes have access
    // to this particular calibration by initializing a new calss of this with the
    // same name as the calibration was done with. Of course this only works on
    // one Computer.
    CalibrationTransformation(AEChip chip, String CalibName) {
        super(chip);
        setCalibrationName(CalibName);
        InitialCalibrationName = CalibName;
        
        myPrefs = Preferences.userNodeForPackage(getClass());
        
        if(CalibName == null || !loadCalibration(CalibName)) {
            //if loadCalib returns true a calibration has been loaded and we
            // dont need to reset. Otherwise reset everything to make sure
            // things are initialized.
            if(!loadCalibration("defaultCalibration")){
                resetFilter();
                log.warning("No Calibration could be found! Please calibrate the system first!");
            } else log.warning("Calibration not found. Default calibration is loaded instead.");
        }
    }
    CalibrationTransformation(AEChip chip) {
        this(chip,"defaultCalibration");
    }

    public void addNewSamplePoint(float retX, float retY, float ptX, float ptY, float screenX, float screenY) {    
        CalibrationPointPanTiltScreen newSample  = new CalibrationPointPanTiltScreen(retX,retY,ptX,ptY,screenX,screenY);
        System.out.println("New samplepoint ("+getNumSamples() +") --> " + newSample.toString());
        sampleList.add(newSample);
    }

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

    public int getNumSamples() {
        return sampleList.size();
    }

    public float[][] getTransformation() {
        return transformation;
    }
    public void setTransformation(float[][] trafoToSet) {
        transformation = trafoToSet;
    }
    public void saveCalibration() {
        if(isCalibrated()) myPrefs.putByteArray(getCalibrationName(), getByteStream());
    }

    public float[][] getInverseTransformation() {
        return inverseTransformation;
    }
    public void setInverseTransformation(float[][] trafoToSet) {
        inverseTransformation = trafoToSet;
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

    public boolean isCalibrated() {
        return isCalibrated;
    }
    public void setCalibrated(boolean calibrated) {
        if(transformation != null && inverseTransformation != null) {
            isCalibrated = calibrated;
        } else{
            isCalibrated = false;
        }
    }

    public byte[] getByteStream() {
        // Serialize to a byte array
        ByteArrayOutputStream bos=new ByteArrayOutputStream();
        try (ObjectOutput oos = new ObjectOutputStream(bos)) {
            oos.writeObject(transformation);
            oos.writeObject(inverseTransformation);
            oos.writeObject(getNumSamples());
            if(getNumSamples() < 30) oos.writeObject(sampleList);
            // Get the bytes of the serialized object
            byte[] buf=bos.toByteArray();
            return buf;
        } catch(IOException e) {
            log.warning(e.getMessage());
            return null;
        }
    }

    public final boolean loadCalibration( String name ) {
        resetFilter();

        byte[] bytes = myPrefs.getByteArray(name, null);
        // Deserialize from a byte array
        if(bytes!=null) {
            try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                transformation        = (float[][]) in.readObject();
                inverseTransformation = (float[][]) in.readObject();
                if((int) in.readObject() < 20) {
                    sampleList        = (ArrayList<CalibrationPointPanTiltScreen>) in.readObject();
                }
                isCalibrated          = true;
                setCalibrationName(name);
                log.info("Calibration successfuly loaded!");
                return true;
            } catch( IOException | ClassNotFoundException e) {
                log.warning(e.getMessage());
                return false;
            }
        } else {
            log.info("No calibration found. Please calibrate before loading!");
            return false;
        }
    }

    @Override public EventPacket<?> filterPacket(EventPacket<?> in) {
        return in;
    }

    @Override public final void resetFilter() {
        sampleList.clear();
        transformation = new float[3][3];
        inverseTransformation = new float[3][3];
        setCalibrationName(InitialCalibrationName);
        isCalibrated = false; 
    }

    @Override public void initFilter() {
        resetFilter();
    }

    public String getCalibrationName() {
        return CalibrationName;
    }

    private void setCalibrationName(String CalibrationName) {
        this.CalibrationName = CalibrationName;
    }
}
