/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.tobi.goalie;

// This programs ball shooter by outputing high/low 0-3.3V on port 2, configured
// in the 'push pull configuration!'

import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.jaer.hardwareinterface.ServoInterface;
import net.sf.jaer.hardwareinterface.usb.silabs.SiLabsC8051F320_USBIO_ServoController;

/**
 *
 * @author Benedek Roska
 */
public class BallShooting extends Thread {
	ServoInterface s;

	private volatile boolean runnable = true;
	private volatile int theTimeForWhichShooting;
	private volatile int theSpeedForWhichShooting;

	// method exists the thread created
	public final void exitBallShootingThread() {
		runnable = false;
	}

	// Thread started - this thread then will control the time, and speed of the
	// ball shooter - these parameters are taken from the GUI
	public void theStartingPoint(int timeForWhichShooting, int SpeedForWhichShooting, ServoInterface sm) {
		theTimeForWhichShooting = timeForWhichShooting;
		theSpeedForWhichShooting = (1000 / SpeedForWhichShooting);
		s = sm;
		start();
	}

	@Override
	public void run() {
		while (runnable) {
			// checking servo interface
			if (s == null) {
				return;
			}
			if (!(s instanceof SiLabsC8051F320_USBIO_ServoController)) {
				return;
			}
			SiLabsC8051F320_USBIO_ServoController s2 = (SiLabsC8051F320_USBIO_ServoController) s;
			// setting port 2 to high and to push pull configuration
			s2.setPortDOutRegisters((byte) 0xff, (byte) 0xff);

			// loop - where a sqare wave is created on port 2 - its frequency
			// and duration are controlled by the 'speed' and 'time of ball
			// shooting respectively
			for (int i = 0; i < theTimeForWhichShooting; i++) {
				try {
					s2.setPort2(0xff);
					Thread.sleep(theSpeedForWhichShooting);

					s2.setPort2(0x00);
					Thread.sleep(theSpeedForWhichShooting);

				}
				catch (InterruptedException ex) {
					Logger.getLogger(BallShooting.class.getName()).log(Level.SEVERE, null, ex);
				}
			}
			s2.setPort2(0);
			// exiting thread
			exitBallShootingThread();

			try {
				Thread.sleep(500);
			}
			catch (Exception e) {
			}
		}
	}
}
