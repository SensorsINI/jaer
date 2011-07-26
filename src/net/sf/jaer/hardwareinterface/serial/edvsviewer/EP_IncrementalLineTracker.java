package net.sf.jaer.hardwareinterface.serial.edvsviewer;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.ActionEvent;

public class EP_IncrementalLineTracker extends EventProcessor {

	/* ***************************************************************************************************** */
	/* **  The following stuff we need to compute line tracking and desired table position ******************* */
	/* ***************************************************************************************************** */
	private static final double polyDecay = 0.98;
	private static final double polyStddev = 4.0;

	private double polyA,  polyB,  polyC,  polyD,  polyE;		// the analytical version
	private double currentBaseA,  currentSlopeA;

	private double currentBaseI, currentSlopeI;					// the stochastic version

    private void updateCurrentEstimate() {
        double denominator;
        denominator = 1.0 / (4.0 * polyA * polyC - polyB * polyB);
        if (denominator != 0.0) {
            currentBaseA  = (polyD * polyB - 2.0 * polyA * polyE) * denominator;
            currentSlopeA = (polyB * polyE - 2.0 * polyC * polyD) * denominator;
        }
    }
    private void polyAddEvent(int x, int y, int t) { // x,y in pixels, t in microseconds

    	updateCurrentEstimate();

		// short = 16bit
        // int = 32bit
        // long = 64bit

        double proposedX = currentBaseA + y * currentSlopeA;
        double error = x - proposedX;
        double weight = Math.exp(-error * error / (2.0 * polyStddev * polyStddev));
        
        double dec = (polyDecay + (1.0 - polyDecay) * (1.0 - weight));
        
        
        													// update of analytical version
        polyA = dec * polyA + weight * (y * y);
        polyB = dec * polyB + weight * (2.0 * y);
        polyC = dec * polyC + weight * (1.0);
        polyD = dec * polyD + weight * (-2.0 * x * y);
        polyE = dec * polyE + weight * (-2.0 * x);

    }
    private void resetPolynomial() {
        polyA = 0.0;
        polyB = 0.0;
        polyC = 0.0;
        polyD = 0.0;
        polyE = 0.0;
//        polyFX = 0.0;

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
//        polyFX += (x * x);
        
        // add point 64/127
        x = 64;
        y = 127;
        polyA += (y * y);
        polyB += (2.0 * y);
        polyC += (1.0);
        polyD += (-2.0 * x * y);
        polyE += (-2.0 * x);
//        polyFX += (x * x);

        updateCurrentEstimate();
    }
	
	public void init() {
		isActive.setText("IncrementalLineTracker");

		resetPolynomial();

        currentBaseI = 0;
        currentSlopeI = 0;
	}
	public int processNewEvent(int eventX, int eventY, int eventP) {
		// update of "traditional" version
		polyAddEvent(eventX, eventY, 0);

		double x = eventX-63.5;
		double y = eventY-63.5;

		// update of incremental version
        double proposedX = currentBaseI + y * currentSlopeI;
        double error = x - proposedX;
        double weight = Math.exp(-error * error / (2.0 * polyStddev * polyStddev));

		currentBaseI  += -0.01   * weight * (currentBaseI + currentSlopeI*y - x);
		currentSlopeI += -0.0001 * weight * (currentBaseI + currentSlopeI*y - x) * y;
        return(0);
	}

	public void processSpecialData(String specialData) {
	}

	public void paintComponent(Graphics g) {
		updateCurrentEstimate();
		double lowX  = currentBaseA +   0.0 * currentSlopeA;
		double highX = currentBaseA + 127.0 * currentSlopeA;
		g.setColor(Color.cyan);
		g.drawLine(4*((int) lowX), 0, 4*((int) highX), 4*127);

	
		lowX  = 63.6 + currentBaseI +   -63.5 * currentSlopeI;
		highX = 63.5 + currentBaseI +   +63.5 * currentSlopeI;
		g.setColor(Color.yellow);
		g.drawLine(4*((int) lowX), 0, 4*((int) highX), 4*127);
}

	public void callBackButtonPressed(ActionEvent e) {
		System.out.println("CallBack!");
	}
}
