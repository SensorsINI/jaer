/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.test;

import ch.unizh.ini.jaer.hardware.pantilt.PanTilt;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;


/**
 *
 * @author matthias
 */
public class ServoControl implements Runnable {
    public final float CRITICAL_UPPER_LIMIT = 0.75f;
    public final float CRITICAL_LOWER_LIMIT = 0.25f;
    public final float USED_UPPER_LIMIT = 0.60f;
    public final float USED_LOWER_LIMIT = 0.50f;
    public final float MAX_VELOCITY = 0.0005f;
    public final float ACCELERATION = 1.0f;
    
    private PanTilt panTiltHardware;
    
    private boolean forward = true;
    private float x = 0.5f;
    private float velocity = 0;
    
    public ServoControl() {
        panTiltHardware = new PanTilt();
        panTiltHardware.setPanServoNumber(1);
        panTiltHardware.setTiltServoNumber(2);
        panTiltHardware.setJitterAmplitude(.02f);
        panTiltHardware.setJitterFreqHz(1);
        panTiltHardware.setJitterEnabled(false);
        panTiltHardware.setPanInverted(false);
        panTiltHardware.setTiltInverted(false);
        
        panTiltHardware.acquire();
    }
    
    public void setValues(float x, float y) {
        try {
            panTiltHardware.setPanTiltValues(x, y);
        } 
        catch (HardwareInterfaceException ex) {
            ex.printStackTrace();
        }
    }
    
    @Override
    public void run() {
        while (true) {
            System.out.println(this.x + " / " + this.velocity);
            this.x += this.velocity;
            if (this.forward) {
                this.velocity = Math.min(this.velocity + this.ACCELERATION, this.MAX_VELOCITY);
                if (this.x + this.velocity > USED_UPPER_LIMIT) {
                    this.forward= false;
                }
            } 
            else {
                this.velocity = Math.max(this.velocity - this.ACCELERATION, -this.MAX_VELOCITY);
                if (this.x + this.velocity < USED_LOWER_LIMIT) {
                    this.forward = true;
                }
            }
            this.setValues(this.x, 0.5f);
            try {
                Thread.sleep(5);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }
    
    public static void main(String [] args) {
        Thread thread = new Thread(new ServoControl());
        thread.run();
    }   
}
