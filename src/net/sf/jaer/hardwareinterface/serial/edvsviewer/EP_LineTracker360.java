package net.sf.jaer.hardwareinterface.serial.edvsviewer;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.ActionEvent;

public class EP_LineTracker360 extends EventProcessor {

	/* ***************************************************************************************************** */
	/* **  The following stuff we need to compute line tracking and desired table position ******************* */
	/* ***************************************************************************************************** */
	private static final double polyDecay = 0.98;
	private static final double polyStddev = 4.0;

	private double polyA, polyB, polyC, polyD, polyE, polyF;
	private double currentBase,  currentSlope;

	private long outCount = 100;
	private int currentRotation = 0;

    private void updateCurrentEstimate() {
        double denominator;
        denominator = 1.0 / (4.0 * polyA * polyC - polyB * polyB);
        if (denominator != 0.0) {
            currentBase  = (polyD * polyB - 2.0 * polyA * polyE) * denominator;
            currentSlope = (polyB * polyE - 2.0 * polyC * polyD) * denominator;

            if (currentSlope > 1.0) {			// oha, tracking went out of way... correct!
System.out.println("Flip ++");
            	currentRotation--;
            	if (currentRotation==-1) currentRotation=3;
            	double tmp;
            	tmp = polyA; polyA =  polyF; polyF =  tmp;
            	tmp = polyB; polyB = -polyE; polyE = -tmp;
//            	currentBase  = -currentBase/currentSlope;
//            	currentSlope = -2.0-currentSlope;
            	updateCurrentEstimate();
            }
            if (currentSlope < -1.0) {			// oha, tracking went out of way... correct!
System.out.println("Flip --");
            	currentRotation++;
            	if (currentRotation==4) currentRotation=0;

            	double tmp;
            	tmp = polyA; polyA =  polyF; polyF =  tmp;
            	tmp = polyB; polyB = -polyE; polyE = -tmp;
//            	currentBase  = -currentBase/currentSlope;
//            	currentSlope = -2.0-currentSlope;
            	updateCurrentEstimate();
            }
        
        }
        if ((outCount--)==0) {
            System.out.printf("CurrentBase %8.3f  CurrentSlope %8.6f\n", currentBase, currentSlope);
            outCount=1000;
        }
    }
    private void polyAddEvent(int x, int y, int t) { // x,y in pixels, t in microseconds

    	updateCurrentEstimate();
    	int tmp;
    	switch (currentRotation) {
    	case 0: break;
    	case 1: tmp = x; x = y; y = tmp; break;
    	case 2: break;
    	case 3: tmp = x; x = y; y = tmp; break;
    	}
//    	switch (currentRotation) {
//    	case 0: break;
//    	case 1: tmp = x; x = y; y = 127-tmp; break;
//    	case 2: x = 127-x; y = 127-y; break;
//    	case 3: tmp = x; x = 127-y; y = tmp; break;
//    	}

		// short = 16bit
        // int = 32bit
        // long = 64bit

    	double proposedX = currentBase + y * currentSlope;
        double error = x - proposedX;
        double weight = Math.exp(-error * error / (2.0 * polyStddev * polyStddev));
        
        double dec = (polyDecay + (1.0 - polyDecay) * (1.0 - weight));
        polyA = dec * polyA + weight * (+y * y);
        polyB = dec * polyB + weight * (+2.0 * y);
        polyC = dec * polyC + weight * (+1.0);
        polyD = dec * polyD + weight * (-2.0 * x * y);
        polyE = dec * polyE + weight * (-2.0 * x);
        polyF = dec * polyF + weight * (+x * x);
    }
    private void resetPolynomial() {
        polyA = 0.0;
        polyB = 0.0;
        polyC = 0.0;
        polyD = 0.0;
        polyE = 0.0;
        polyF = 0.0;

        // add two "imaginary" events to filter, resulting in an initial vertical line
          double x, y;
        // add point 64/0
        x = 64;
        y = 0;
        polyA += (y * y);
        polyB += (2.0 * y);
        polyC += (1.0);
        polyD += (-2.0 * x * y);
        polyE += (-2.0 * x);
        polyF += (x * x);
        
        // add point 64/127
        x = 64;
        y = 127;
        polyA += (y * y);
        polyB += (2.0 * y);
        polyC += (1.0);
        polyD += (-2.0 * x * y);
        polyE += (-2.0 * x);
        polyF += (x * x);

        updateCurrentEstimate();
    }

	public void init() {
		resetPolynomial();
		isActive.setText("LineTracker360");
	}
	public int processNewEvent(int eventX, int eventY, int eventP) {
		polyAddEvent(eventX, eventY, 0);
        return(0);
	}

	public void processSpecialData(String specialData) {
	}

	public void paintComponent(Graphics g) {
		updateCurrentEstimate();
		double lowX  = currentBase +   0.0 * currentSlope;
		double highX = currentBase + 127.0 * currentSlope;
		g.setColor(Color.cyan);
		g.drawLine(4*((int) lowX), 0, 4*((int) highX), 4*127);
	}

	public void callBackButtonPressed(ActionEvent e) {
		System.out.println("CallBack!");
	}
}
