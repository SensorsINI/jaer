/*
 * Masterbias.java
 *
 * Created on September 22, 2005, 9:00 AM
 */

package ch.unizh.ini.caviar.biasgen;

import java.util.*;
import java.util.prefs.*;

/**
 * Describes the master bias circuit and its configuration, and supplies methods for estimating parameters.
 *<p>
 *The schematic of a masterbias looks like this:
 *<p>
 *<img src="doc-files/masterbiasSchematic.png" />
 *<p>
 *The master current is estimated in strong inversion as indicated in
 *<a href="http://www.ini.unizh.ch/~tobi/biasgen">http://www.ini.unizh.ch/~tobi/biasgen/> as follows
 *<p>
 *<img src="doc-files/masterCurrentFormulas.png">
 *See http://www.ini.unizh.ch/~tobi/biasgen
 * @author tobi
 */
public class Masterbias extends Observable implements BiasgenPreferences {
    private Preferences prefs;
    Biasgen biasgen;
    /** Creates a new instance of Masterbias.  */
    public Masterbias(Biasgen biasgen) {
        this.biasgen=biasgen;
        prefs=biasgen.getChip().getPrefs();
    }
    
    /** true to power down masterbias, via powerDown input */
    public boolean powerDownEnabled=false;
    
    /** the total multiplier for the n-type current mirror */
    public float multiplier=9f*24f/4.8f;
    
    /** W/L ratio of input side of n-type current mirror */
    public float WOverL=4.8f/2.4f;
    
    /** boolean that is true if we are using the internal resistor in series with an off-chip external resistor */
    public boolean internalResistorUsed=false;
    
    /** internal (on-chip) resistor value. This is what you get when you tie pin rInternal to ground. */
    public float rInternal=46e3f;
    
    /** external resistor used. This can be the only capacitance
     * if you tie it in line with rExternal, "rx". If you tie it to rInternal, you get the sum. */
    public float rExternal=8.2e3f;
    
    public float getTotalResistance(){
        if(internalResistorUsed) return rInternal+rExternal; else return rExternal;
    }
    
    /** temperature in degrees celsius */
    public float temperatureCelsius=25f;
    
    /** the value of beta/2=mu*Cox/2 in Amps/Volt^2, called gain factor KPN by some fabs:
     * Gain factor
     * KP is measured from the slope of the large transistor, where Weff / Leff ~ W/L.
     * The drain voltage is forced to 0.1V, source and bulk are connected to ground. The gate voltage is swept to find the maximum
     * slope of the drain current as a function of the gate voltage. A linear regression is performed around this operating point:
     * )
     * 2
     * VDS VTO (VGS VDS
     * Leff
     * Weff KP IDS ? ? ? ? ? =
     * The voltage sweep is positive for n-channel devices and negative for p-channel devices.
     */
    public float kPrimeNFet=170e-6f;
    
    /** @return thermal voltage, computed from temperature */
    float thermalVoltage(){
        return 26e-3f*(temperatureCelsius+273f)/300f;
    }
    
    /** estimated current if operating in weak inversion: log(M) UT/R */
    public float getCurrentWeakInversion(){
        float iweak= (float)( (thermalVoltage()/getTotalResistance())*Math.log(multiplier));
        return iweak;
    }
    
    /** estimated current if master running in strong inversion, 
     * computed from formula in <a href="http://www.ini.unizh.ch/~tobi/biasgen">http://www.ini.unizh.ch/~tobi/biasgen/> paper.
     */
    public float getCurrentStrongInversion(){
        float r=getTotalResistance();
        float r2=r*r;
        float beta=kPrimeNFet*WOverL; // beta is mu*Cox*W/L, kPrimeNFet=beta/(W/L)
        float rfac=2/(beta*r2);
        float mfac=(float)(1-1/Math.sqrt(multiplier));
        mfac=mfac*mfac;
        float istrong= (float)(rfac*mfac);
        return istrong;
    }
    
    /** the sum of weak and strong inversion master current estimates */
    public float getCurrent(){
        float total=getCurrentStrongInversion()+getCurrentWeakInversion();
        return total;
    }
    
    
    public float getRInternal() {
        return this.rInternal;
    }
    
    public void setRInternal(final float rint) {
        if(rint!=rInternal){
            this.rInternal = rint;
            setChanged();
            notifyObservers("internalResistor");
        }
    }
    
    public float getRExternal() {
        return this.rExternal;
    }
    
    public void setRExternal(final float rx) {
        if(rx!=rExternal){
            this.rExternal = rx;
            setChanged();
            notifyObservers("externalResistor");
        }
    }
    
    public void exportPreferences(java.io.OutputStream os) {
    }
    
    public void importPreferences(java.io.InputStream is) {
    }
    
    public void loadPreferences() {
        setRExternal(prefs.getFloat("Masterbias.rx",getRExternal()));
        setRInternal(prefs.getFloat("Masterbias.rint",getRInternal()));
        setInternalResistorUsed(prefs.getBoolean("Masterbias.internalResistorUsed", isInternalResistorUsed()));
    }
    
    public void storePreferences() {
        prefs.putFloat("Masterbias.rx", getRExternal());
        prefs.putFloat("Masterbias.rint",getRInternal());
        prefs.putBoolean("Masterbias.internalResistorUsed",isInternalResistorUsed());
    }
    
    public boolean isPowerDownEnabled() {
        return this.powerDownEnabled;
    }
    
    public void setPowerDownEnabled(final boolean powerDownEnabled) {
        if(powerDownEnabled!=this.powerDownEnabled){
            this.powerDownEnabled = powerDownEnabled;
            setChanged();
            notifyObservers("powerDownEnabled");
        }
    }
    
    public boolean isInternalResistorUsed() {
        return this.internalResistorUsed;
    }
    
    public void setInternalResistorUsed(final boolean internalResistorUsed) {
        if(internalResistorUsed!=this.internalResistorUsed){
            this.internalResistorUsed = internalResistorUsed;
            setChanged();
            notifyObservers("internalReisistorUsed");
        }
    }
    
    public float getTemperatureCelsius() {
        return this.temperatureCelsius;
    }
    
    public void setTemperatureCelsius(final float temperatureCelsius) {
        if(this.temperatureCelsius != temperatureCelsius){
            this.temperatureCelsius = temperatureCelsius;
            setChanged();
            notifyObservers("temperatureChange");
        }
    }
}
