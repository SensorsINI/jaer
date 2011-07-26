package net.sf.jaer.hardwareinterface.serial.edvsviewer;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.ActionEvent;

public class EP_TobiasBoxTracker extends EventProcessor {

	public void callBackButtonPressed(ActionEvent e) {
	}

	public void init() {
		UpdateLine(0);
		UpdateLine(1);
		UpdateLine(2);
		UpdateLine(3);
		printStatus();

		isActive.setText("TobiasBoxTracker");
	}

	private void printStatus() {
		for (int n=0; n<4; n++) {
			double base = ((-Linedata[n][2] / Linedata[n][0]) + 1.0 ) *63.5;
			double slope = -Linedata[n][1] / Linedata[n][0];
			System.out.println("Printing line " + n + "   base: " + base + "   slope: " + slope + "   line: " + Linedata[n][0] + " " + Linedata[n][1] + " " + Linedata[n][2]);
		}
	}
	public void paintComponent(Graphics g) {
		g.setColor(Color.CYAN);
		for (int n=0; n<4; n++) {
			g.setColor(Color.cyan);

			double y1 = 254.0 * ((+Linedata[n][0] - Linedata[n][2]) / Linedata[n][1] + 1.0);
			double y2 = 254.0 * ((-Linedata[n][0] - Linedata[n][2]) / Linedata[n][1] + 1.0);
			g.drawLine(0, (int)(y1), 511, (int)(y2));
		}

//		System.out.println("LD : " + Linedata[0][0] + "   " + Linedata[0][1] + "   " + Linedata[0][2]);

//		System.out.print("Matrix: ");
//		for (int n=0; n<6; n++) {
//			System.out.print("    " + Matrix[3][n]);
//		}
//		System.out.println("");
	}

//	double Matrix[][] = new double[4][6];

	double Matrix[][] = {
			{ 0.52, 0, 1.0, 0.52, -0.2, 2.0 },
			{ 0.52, 0, 0.2, 0.52, 1.0, 2.0 },
			{ 0.52, 0, -1.0, 0.52, 0.2, 2.0 },
			{ 0.52, 0, -0.2, 0.52, -1.0, 2.0 },
		};

	double Linedata[][] = new double[4][4];				// four affine linear functions in 2D, with squared norm
	double factor = 0.001;								// inverse of total discount
	double UPDATE_THRES = 0.125;
	double UPDATE_THRES_2 = UPDATE_THRES * UPDATE_THRES;
	double INVERSE_DISCOUNT = 1.00001;


	////////////////////////////////////////////////////////////
	// line estimation code


	void NormalizeLineXX(int line)
	{
		if (Linedata[line][3] > 0.0)
		{
			// normalize solution
			double norm = Math.sqrt(Linedata[line][3]);
			Linedata[line][0] /= norm;
			Linedata[line][1] /= norm;
			Linedata[line][2] /= norm;
			Linedata[line][3] = 1.0;
		}
		else
		{
			// avoid division by zero
			Linedata[line][0] = 0.0;
			Linedata[line][1] = 0.0;
			Linedata[line][2] = 0.0;
			Linedata[line][3] = 0.0;
		}
	}
	void UpdateLine(int line)
	{
		// quadratic equation det(M - \lambda T) = 0
		double A = 4.0 * Matrix[line][5];
		double B = 2.0 * (Matrix[line][0] * Matrix[line][5] + Matrix[line][3] * Matrix[line][5] - Matrix[line][2] * Matrix[line][2] - Matrix[line][4] * Matrix[line][4]);
		double t1 = Matrix[line][3] * Matrix[line][5] - Matrix[line][4] * Matrix[line][4];
		double t2 = Matrix[line][1] * Matrix[line][5] - Matrix[line][2] * Matrix[line][4];
		double t3 = Matrix[line][1] * Matrix[line][4] - Matrix[line][2] * Matrix[line][3];
		double C = Matrix[line][0] * t1 - Matrix[line][1] * t2 + Matrix[line][2] * t3;
		double D = B * B - 4 * A * C;			// shifted by FC
		double w = Math.sqrt(D);						// first square root operation
		double lambda = (w - B) / A;				// (twice the) 1. solution

		// find vector in the kernel of (M - \lambda T)
		double M0mod = Matrix[line][0] - lambda;
		double M3mod = Matrix[line][3] - lambda;
		double s12 = Math.abs(M0mod * Matrix[line][1] + Matrix[line][1] * M3mod + Matrix[line][2] * Matrix[line][4]);
		double s13 = Math.abs(M0mod * Matrix[line][2] + Matrix[line][1] * Matrix[line][4] + Matrix[line][2] * Matrix[line][5]);
		double s23 = Math.abs(Matrix[line][1] * Matrix[line][2] + M3mod * Matrix[line][4] + Matrix[line][4] * Matrix[line][5]);
		if (s12 < s13 && s12 < s23)
		{
			Linedata[line][0] = Matrix[line][1] * Matrix[line][4] - Matrix[line][2] * M3mod;
			Linedata[line][1] = Matrix[line][2] * Matrix[line][1] - M0mod * Matrix[line][4];
			Linedata[line][2] = M0mod * M3mod - Matrix[line][1] * Matrix[line][1];
		}
		else if (s13 < s23)
		{
			Linedata[line][0] = Matrix[line][1] * Matrix[line][5] - Matrix[line][2] * Matrix[line][4];
			Linedata[line][1] = Matrix[line][2] * Matrix[line][2] - M0mod * Matrix[line][5];
			Linedata[line][2] = M0mod * Matrix[line][4] - Matrix[line][1] * Matrix[line][2];
		}
		else
		{
			Linedata[line][0] = M3mod * Matrix[line][5] - Matrix[line][4] * Matrix[line][4];
			Linedata[line][1] = Matrix[line][4] * Matrix[line][2] - Matrix[line][1] * Matrix[line][5];
			Linedata[line][2] = Matrix[line][1] * Matrix[line][4] - M3mod * Matrix[line][2];
		}

//		Linedata[line][3] = Linedata[line][0] * Linedata[line][0] + Linedata[line][1] * Linedata[line][1];
		double norm2 = Linedata[line][0] * Linedata[line][0] + Linedata[line][1] * Linedata[line][1];
		if (norm2 > 0.0)
		{
			double norm = Math.sqrt(norm2);
			Linedata[line][0] /= norm;
			Linedata[line][1] /= norm;
			Linedata[line][2] /= norm;
			Linedata[line][3] = 1.0;
		}
		else
		{
			Linedata[line][0] = 0.0;
			Linedata[line][1] = 0.0;
			Linedata[line][2] = 0.0;
			Linedata[line][3] = 0.0;
		}
	}
	void AddPoint(int line, double u, double v)
	{
		Matrix[line][0] += factor * u * u;
		Matrix[line][1] += factor * u * v;
		Matrix[line][2] += factor * u;
		Matrix[line][3] += factor * v * v;
		Matrix[line][4] += factor * v;
		Matrix[line][5] += factor;
		UpdateLine(line);
		System.out.println("[AddPoint]  line=" + line + "    u=" + u + "   v=" + v);
//		printStatus();
	}
	void DiscountLines()
	{
//		factor *= INVERSE_DISCOUNT;
//		if (factor > 1.0)
//		{
//			double f = 1.0 / factor;
//			int i, j;
//			for (i=0; i<4; i++) for (j=0; j<6; j++) Matrix[i][j] *= f;
//			factor = 0.001;
//		}
		int i, j;
		for (i=0; i<4; i++) for (j=0; j<6; j++) Matrix[i][j] /= INVERSE_DISCOUNT;
	}

	private double f(int line, double u, double v)
	{
		return Linedata[line][0] * u + Linedata[line][1] * v + Linedata[line][2];
	}

	public int processNewEvent(int eventX, int eventY, int eventP) {
		DiscountLines();

		double u = ((((double) (eventX))/63.5)-1.0);
		double v = ((((double) (eventY))/63.5)-1.0);

		double f0 = f(0, u, v);
		double f1 = f(1, u, v);
		double f2 = f(2, u, v);
		double f3 = f(3, u, v);

		double h0 = Linedata[0][0] * (Linedata[2][0] * Linedata[2][2])
				  + Linedata[0][1] * (Linedata[2][1] * Linedata[2][2])
				  + Linedata[0][2];
		double h1 = Linedata[1][0] * (Linedata[3][0] * Linedata[3][2])
				  + Linedata[1][1] * (Linedata[3][1] * Linedata[3][2])
				  + Linedata[1][2];
		double h2 = Linedata[2][0] * (Linedata[0][0] * Linedata[0][2])
				  + Linedata[2][1] * (Linedata[0][1] * Linedata[0][2])
				  + Linedata[2][2];
		double h3 = Linedata[3][0] * (Linedata[1][0] * Linedata[1][2])
				  + Linedata[3][1] * (Linedata[1][1] * Linedata[1][2])
				  + Linedata[3][2];
		if (h0 >= 0.0) f0 = -f0;
		if (h1 >= 0.0) f1 = -f1;
		if (h2 >= 0.0) f2 = -f2;
		if (h3 >= 0.0) f3 = -f3;

		if (f0 > UPDATE_THRES || f1 > UPDATE_THRES || f2 > UPDATE_THRES || f3 > UPDATE_THRES) return(0);
//		System.out.println("u=" + u + " v=" + v + "  f=" + f0 + "  " + f1 + "  " + f2 + "  " + f3);

		boolean b0 = (f0 > -UPDATE_THRES);
		boolean b1 = (f1 > -UPDATE_THRES);
		boolean b2 = (f2 > -UPDATE_THRES);
		boolean b3 = (f3 > -UPDATE_THRES);
		if (b0 && ! b1 && ! b2 && ! b3) AddPoint(0, u, v);
		if (! b0 && b1 && ! b2 && ! b3) AddPoint(1, u, v);
		if (! b0 && ! b1 && b2 && ! b3) AddPoint(2, u, v);
		if (! b0 && ! b1 && ! b2 && b3) AddPoint(3, u, v);
        return(0);
	}

	public void processSpecialData(String specialData) {
	}

}
