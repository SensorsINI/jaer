package net.sf.jaer.hardwareinterface.serial.edvsviewer;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.ActionEvent;

public class EP_GlobalOpticFlowX extends EventProcessor {

	long timeMemory[][][] = new long[128][128][2];				// x, y, p
	double currentVelocityX;
	
	double velocityArrayX[][] = new double[16][16];

	public void callBackButtonPressed(ActionEvent e) {
	}

	public void init() {
		long currentTime = (System.nanoTime() / 1000);
		int x,y;

		isActive.setText("GlobalOpticFlowTrackerX");

		for (x=0; x<128; x++) {
			for (y=0; y<128; y++) {
				timeMemory[x][y][0] = currentTime;
				timeMemory[x][y][1] = currentTime;
			}
		}
		
		currentVelocityX = 0.0;
	}

	public void paintComponent(Graphics g) {

		int aX, aY, pX, pY;
		g.setColor(Color.CYAN);
		for (aX=0; aX<16; aX++) {
			pX = 8 + 4*aX*8;
			for (aY=0; aY<16; aY++) {
				pY = 8 + 4*aY*8;
				g.drawLine(pX, pY, ((int) (pX+10000.0*velocityArrayX[aX][aY])), pY);
			}
		}

	}

	long counter = 1;
	public int processNewEvent(int eventX, int eventY, int eventP) {
		
		final int PIXEL_DELTA = 1;
		final long MIN_TIME =  1000;		//  1ms
		final long MAX_TIME = 500000;		// 500ms

		long currentTime = (System.nanoTime() / 1000);
		
		if (eventP != 0) eventP = 1;

		if ((eventX>(PIXEL_DELTA-1)) && (eventX<(128-PIXEL_DELTA))) { // is event a non-boarder event?

								// is event "new"?   -- i.e. is the old memory "older" than neighbors?
			if (((timeMemory[eventX][eventY][eventP]) < (timeMemory[eventX-PIXEL_DELTA][eventY][eventP])) ||
				((timeMemory[eventX][eventY][eventP]) < (timeMemory[eventX+PIXEL_DELTA][eventY][eventP]))) {

				double diff_l, diff_r;
				double vel_l=0.0, vel_r=0.0;

				diff_l = currentTime - (timeMemory[eventX-PIXEL_DELTA][eventY][eventP]);
				diff_r = currentTime - (timeMemory[eventX+PIXEL_DELTA][eventY][eventP]);

				if (diff_l < diff_r) {
					if ((diff_l>MIN_TIME) && (diff_l<MAX_TIME)) vel_l = 1.0 / diff_l;
				} else {
					if ((diff_r>MIN_TIME) && (diff_r<MAX_TIME)) vel_r = 1.0 / diff_r;
				}

				currentVelocityX = 0.999*currentVelocityX + vel_l - vel_r;

				int aX, aY;
				for (aX=0; aX<16; aX++) {
					for (aY=0; aY<16; aY++) {
						velocityArrayX[aX][aY] = 0.999*velocityArrayX[aX][aY];
					}					
				}
				aX = eventX / 8;
				aY = eventY / 8;
				velocityArrayX[aX][aY] += +vel_l -vel_r;


				if ((--counter) == 0) {
					counter = 100;
					System.out.printf("Current Vel: %10.5f\n", currentVelocityX);
				}
			}
		}

		// remember current time
		timeMemory[eventX][eventY][eventP] = currentTime;

		return(0);
	}

	public void processSpecialData(String specialData) {
	}

}
