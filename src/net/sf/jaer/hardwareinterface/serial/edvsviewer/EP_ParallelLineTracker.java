package net.sf.jaer.hardwareinterface.serial.edvsviewer;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.ActionEvent;

public class EP_ParallelLineTracker extends EventProcessor {

	float polyDecay=0.99f;
	float polyStddev=2f;

	float currentBaseL=59f;
	float currentSlope=0f;
	float currentBaseR=69f;


	float polyA=0f;
	float polyB=0f;
	float polyC=0f;
	float polyD=0f;
	float polyE=0f;
	float polyF=0f;
	float polyG=0f;
	float polyH=0f;
	float polyI=0f;

	private void addEventLeftLine(int x, int y) {
		float proposedL = currentBaseL + y * currentSlope;
		float error = x - proposedL;
		float weight = (float) Math.exp(-error * error / (2f * polyStddev * polyStddev));
		// if (weight > .001) System.out.println("adding event (" + x + "," + y + ") to left (L,R,m)=(" + currentBaseL + "," + currentBaseR + "," + currentSlope + ")with weight " + weight);
		//     else return;
		float dec = (polyDecay + (1f - polyDecay) * (1f - weight));
		weight=1-dec;
		polyA = dec * polyA;
		polyB = dec * polyB;
		polyC = dec * polyC;
		polyD = dec * polyD;
		polyE = dec * polyE;
		polyF = dec * polyF;
		polyG = dec * polyG;
		polyH = dec * polyH;
		polyI = dec * polyI;

		// squared error is   bl^2 + m^2 y^2 + x^2 + 2 bl m y - 2 bl x - 2 x y m
		// poly is   polyA br^2 + polyB bl^2 + polyC m^2 + polyD br bl + polyE br m + polyF bl m + polyG br + polyH bl + polyI m
		polyA += weight * 0;
		polyB += weight * 1;
		polyC += weight * (y * y);
		polyD += weight * 0;
		polyE += weight * 0;
		polyF += weight * (2f * y);
		polyG += weight * 0;
		polyH += weight * (-2f * x);
		polyI += weight * (-2f * x * y);

		//   System.out.println(" baseL " + currentBaseL + " baseR " + currentBaseR + ", slope " + currentSlope);
	}
	private void addEventRightLine(int x, int y) {
		float proposedR = currentBaseR + y * currentSlope;
		float error = x - proposedR;
		float weight = (float) Math.exp(-error * error / (2f * polyStddev * polyStddev));
		//  if (weight > .001) System.out.println("adding event (" + x + "," + y + ") to right (L,R,m)=(" + currentBaseL + "," + currentBaseR + "," + currentSlope + ")with weight " + weight);
		//      else return;
		float dec = (polyDecay + (1f - polyDecay) * (1f - weight));
		weight=1-dec;

		polyA = dec * polyA;
		polyB = dec * polyB;
		polyC = dec * polyC;
		polyD = dec * polyD;
		polyE = dec * polyE;
		polyF = dec * polyF;
		polyG = dec * polyG;
		polyH = dec * polyH;
		polyI = dec * polyI;

		// squared error is   br^2 + m^2 y^2 + x^2 + 2 br m y - 2 br x - 2 x y m
		// poly is   polyA br^2 + polyB bl^2 + polyC m^2 + polyD br bl + polyE br m + polyF bl m + polyG br + polyH bl + polyI m
		polyA += weight * 1;
		polyB += weight * 0;
		polyC += weight * (y * y);
		polyD += weight * 0;
		polyE += weight * (2f * y);
		polyF += weight * 0;
		polyG += weight * (-2f * x);
		polyH += weight * 0;
		polyI += weight * (-2f * x * y);

		//  System.out.println(" baseL " + currentBaseL + " baseR " + currentBaseR + ", slope " + currentSlope);
	}

	public void init() {

		isActive.setText("ParallelLineTracker");

		for (int y=0;y<128;y++) {
			addEventLeftLine(59,y);
			addEventRightLine(69,y);
			updateEstimate();
		}

		currentBaseL=59f;
		currentSlope=0f;
		currentBaseR=69f;

		// add point 64/0
		for (int i = 0; i < 100; i++) {
			addEventLeftLine(59,0);
			addEventLeftLine(59,10);
			addEventRightLine(69,0);
			addEventRightLine(69,10);
			updateEstimate();
		}
	}

	long lastTime=0;
	public int processNewEvent(int x, int y, int pol) {

		//System.out.println("1 - baseL " + currentBaseL + " baseR " + currentBaseR + ", slope " + currentSlope);

		float proposedL = currentBaseL + y * currentSlope;
		float proposedR = currentBaseR + y * currentSlope;

		if (Math.abs(proposedL-x )< Math.abs(proposedR-x)) {
			addEventLeftLine(x,y);
		} else {
			addEventRightLine(x,y);
		}

		updateEstimate();
        return(0);
	}
	public void processSpecialData(String specialData) {
	}

	private float determinant(float a1, float b1, float c1,float a2, float b2, float c2,float a3, float b3, float c3) {
		float det=a1*b2*c3 + b1*c2*a3 + c1*a2*b3 - a3*b2*c1 - b3*c2*a1 - c3*a2*b1;
		return det;
	}
	private void updateEstimate() {
		float  denominator = determinant(2*polyA,polyD,polyE,polyD,2*polyB,polyF,polyE,polyF,2*polyC);
		if (denominator != 0.0) {
			currentBaseR = determinant( -polyG, polyD, polyE,-polyH,2*polyB, polyF,-polyI, polyF,2*polyC) / denominator;
			currentBaseL = determinant(2*polyA,-polyG, polyE, polyD, -polyH, polyF, polyE,-polyI,2*polyC) / denominator;
			currentSlope = determinant(2*polyA, polyD,-polyG, polyD,2*polyB,-polyH, polyE, polyF, -polyI) / denominator;

			if (currentSlope < -.1f) currentSlope = -.1f;
			if (currentSlope > +.1f) currentSlope = +.1f;
		}
	}

	
	public void callBackButtonPressed(ActionEvent e) {
	}

	public void paintComponent(Graphics g) {
		updateEstimate();

		double lowX  = currentBaseL +   0.0 * currentSlope;
		double highX = currentBaseL + 127.0 * currentSlope;
		g.setColor(Color.cyan);
		g.drawLine(4*((int) lowX), 0, 4*((int) highX), 4*127);

		lowX  = currentBaseR +   0.0 * currentSlope;
		highX = currentBaseR + 127.0 * currentSlope;
		g.setColor(Color.red);
		g.drawLine(4*((int) lowX), 0, 4*((int) highX), 4*127);

	}
}
