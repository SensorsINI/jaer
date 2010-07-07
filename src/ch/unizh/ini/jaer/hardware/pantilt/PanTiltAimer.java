/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.hardware.pantilt;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.ServoInterface;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;

/**
 * This filter enables aiming the pantilt using a GUI and allows controlling jitter of the pantilt when not moving it.
 * 
 * @author Tobi Delbruck
 */
public class PanTiltAimer extends EventFilter2D implements FrameAnnotater, PanTiltInterface, LaserOnOffControl {

    public static final String getDescription(){ return "Allows control of pan-tilt using a panel to aim it and parameters to control the jitter";}
    private PanTilt panTiltHardware;
    private PanTiltAimerGUI calibrator;

    /** Constructs instance of the new 'filter' CalibratedPanTilt. The only time events are actually used
     * is during calibration. The PanTilt hardware interface is also constructed.
     * @param chip
     */
    public PanTiltAimer(AEChip chip) {
        super(chip);
        panTiltHardware = new PanTilt();
        setPropertyTooltip("jitterAmplitude","Jitter of pantilt amplitude");
        setPropertyTooltip("jitterFreqHz","Jitter frequency in Hz");
    }
    
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        return in;
    }
 
    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
        resetFilter();
    }

    public void annotate(GLAutoDrawable drawable) {

//        GL gl = drawable.getGL(); // when we get this we are already set up with scale 1=1 pixel, at LL corner

    }


    /** Invokes the calibration GUI. Calibration values are stored persistently as preferences. 
     * Built automatically into filter parameter panel as an action.
     */
    public void doAim() {
        if (calibrator == null) {
            calibrator = new PanTiltAimerGUI(panTiltHardware);
        }
        calibrator.setVisible(true);
    }

    public PanTilt getPanTiltHardware() {
        return panTiltHardware;
    }

    public void setPanTiltHardware(PanTilt panTilt) {
        this.panTiltHardware=panTilt;
    }
    
       public float getJitterAmplitude() {
        return panTiltHardware.getJitterAmplitude();
    }

    /** Sets the amplitude (1/2 of peak to peak) of circular jitter of pan tilt during jittering
     * 
     * @param jitterAmplitude the amplitude
     */
    public void setJitterAmplitude(float jitterAmplitude) {
        panTiltHardware.setJitterAmplitude(jitterAmplitude);
    }

    public float getJitterFreqHz() {
        return panTiltHardware.getJitterFreqHz();
    }

    /** The frequency of the jitter
     * 
     * @param jitterFreqHz in Hz
     */
    public void setJitterFreqHz(float jitterFreqHz) {
        panTiltHardware.setJitterFreqHz(jitterFreqHz);
    }

    public void acquire() {
        getPanTiltHardware().acquire();
    }

    public float[] getPanTiltValues() {
        return getPanTiltHardware().getPanTiltValues();
    }

    public boolean isLockOwned() {
        return getPanTiltHardware().isLockOwned();
    }

    public void release() {
        getPanTiltHardware().release();
    }

    public void startJitter() {
        getPanTiltHardware().startJitter();
    }

    public void stopJitter() {
        getPanTiltHardware().stopJitter();
    }

    /** Sets the pan and tilt servo values
     @param pan 0 to 1 value
     @param tilt 0 to 1 value
     */
    public void setPanTiltValues(float pan, float tilt) throws HardwareInterfaceException {
        getPanTiltHardware().setPanTiltValues(pan, tilt);
    }

    public void setServoInterface(ServoInterface servo) {
       getPanTiltHardware().setServoInterface(servo);
    }

    public ServoInterface getServoInterface() {
        return getPanTiltHardware().getServoInterface();
    }

    public void setLaserEnabled(boolean yes) {
        getPanTiltHardware().setLaserEnabled(yes);
    }

 
}
