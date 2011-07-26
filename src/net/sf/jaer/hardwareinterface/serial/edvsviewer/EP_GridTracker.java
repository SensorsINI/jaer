package net.sf.jaer.hardwareinterface.serial.edvsviewer;

import java.awt.Color;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.prefs.Preferences;

import javax.swing.JApplet;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;

public class EP_GridTracker extends EventProcessor {

	private JButton closeGUIButton = new JButton();

	private JFrame frame = new JFrame("Grid Tracker GUI");
	private JButton resetButton = new JButton();
	private JCheckBox stableFlagCheckBox = new JCheckBox();


	// ******************************************************************************************************************************************************
	// ******************************************************************************************************************************************************
	// ******************************************************************************************************************************************************
	// ******************************************************************************************************************************************************
	//------------------ that may look big, but I like how small it is, under 90 clock cycles  ---------------

	//------------ state variables ---------------

	/* estimate of position */

	/* posx, posy, posh=height are heli's 3D coordinates, theta is rotation */
	/* posx and posy are in floor-tile units */
	/* posh is in units where at one unit of height, the camera sees 4 (i.e. 2x2) floor tiles */
	/* no camera calibration is needed */
	/* theta is in radians */
	private double posx, posy, posh, theta, cth, sth;		/* cth and sth are cos theta  and sin theta */

	/* 4D Hough quadratic polynomial coefficients */
	private double aa, bb, cc, dd, ab, ac, ad, bc, bd, cd, la, lb, lc, ld;

	/* reciprocals */
	private double id1, id2, id3, id4;

	private boolean gridTrackerStableFlag = false; /* flag should be 0 until coefficients have stabilized, ~100 events */

	private void gridTrackerInit() {
		//------------- initialize --------------

		/* set polynomial to 0 */
		aa = bb = cc = dd = ab = ac = ad = bc = bd = cd = la = lb = lc = ld =  0.0;

		/* these correspond to start of LogfileD1 */
		posx = 0.4;
		posy = 0.35;
		posh = 0.9;
		theta = 0.4;
		cth = Math.cos(theta);
		sth = Math.sin(theta);
	}
	private void gridTrackerProcessEvent(int eventX, int eventY) {
		//-------------- every time an event arrives --------------

		double a, b;
		double xg, yg, px, qx, rx, kx, py, qy, ry, ky; /* intermediate results  

			/* first, scale the event to have coordinates in (-1,1) */
		a = ((double) eventX) / 63.5 - 1.0;
		b = ((double) eventY) / 63.5 - 1.0;

		/* now, find the coefficients for the Hough polynomial and add them in */

		qx = a * cth + b * sth;
		qy = b * cth - a * sth;
		px = posh * qy;
		py = -posh * qx;
		xg = posx - py;
		yg = posy + px;
		/* any event in outer 10% margin of grid square gets used */
		/* check nearest line x=k */
		kx = Math.round(xg); /* nearest integer */
		rx = kx - xg;
		if (rx > -0.1 && rx < 0.1) {
			aa *= .99; aa += 1;
			bb *= .99; bb += 0; /* obviously second statement can be optimized away */
			cc *= .99; cc += qx * qx;
			dd *= .99; dd += px * px;
			ab *= .99; ab += 0; /* ab is actually always zero and can be optimized away */
			ac *= .99; ac += qx;
			ad *= .99; ad += px;
			bc *= .99; bc += 0;
			bd *= .99; bd += 0;
			cd *= .99; cd += px * qx;
			la *= .99; la += rx;
			lb *= .99; lb += 0;
			lc *= .99; lc += rx * qx;
			ld *= .99; ld += rx * px;
		}
		/* check nearest line y=k */
		ky = Math.round(yg);
		ry = ky - yg;
		if (ry > -0.1 && ry < 0.1) {
			aa *= .99; aa += 0;
			bb *= .99; bb += 1;
			cc *= .99; cc += qy * qy;
			dd *= .99; dd += py * py;
			ab *= .99; ab += 0; /* see, here we also add 0, that's why ab is always 0 */
			ac *= .99; ac += 0;
			ad *= .99; ad += 0;
			bc *= .99; bc += qy;
			bd *= .99; bd += py;
			cd *= .99; cd += py * qy;
			la *= .99; la += 0;
			lb *= .99; lb += ry;
			lc *= .99; lc += ry * qy;
			ld *= .99; ld += ry * py;
		}

	}
	private void gridTrackerUpdatePositionEstimate() {
		//--------------- at least every 50 events (maybe even every event), update position estimate ----------------

		double L12, L13, L14, L23, L24, L34;
		double L12r, L13r, L14r, L23r, L24r, L34r;
		double d1, d2, d3, d4;
		double y1, y2, y3, y4;   /* these x/y/z vectors are unrelated to heli x/y/h */
		double z1, z2, z3, z4;
		double x1, x2, x3, x4;
		double b1, b2, b3, b4;    /* many more vars declared here than really needed */
		double err;

		b1 = la;  /* a lot of these variables can clearly be optimized away */
		b2 = lb;
		b3 = lc;
		b4 = ld;

		/* do a Cholesky LDU decomposition */
		d1 = aa;
		if (gridTrackerStableFlag) {
			err = d1*id1; id1 = id1*(2 - err);  /* maintain reciprocal */
			err = d1*id1; id1 = id1*(2 - err);
		} else {
			id1 = 1/d1;  /* if not stable, we must divide */
		}
		L12r = ab;  /* if you recall, ab is always 0... */
		L13r = ac;
		L14r = ad;
		L12 = id1*L12r;
		L13 = id1*L13r;
		L14 = id1*L14r;

		d2 = bb - L12r*L12;
		if (gridTrackerStableFlag) {
			err = d2*id2; id2 = id2*(2 - err);
			err = d2*id2; id2 = id2*(2 - err);
		} else {
			id2 = 1/d2;
		}
		L23r = bc - L12r*L13;
		L24r = bd - L12r*L14;
		L23 = id2*L23r;
		L24 = id2*L24r;

		d3 = cc - L23r*L23 - L13r*L13;
		if (gridTrackerStableFlag) {
			err = d3*id3; id3 = id3*(2 - err);
			err = d3*id3; id3 = id3*(2 - err);
		} else {
			id3 = 1/d3;
		}
		L34r = cd - L13r*L14 - L23r*L24;
		L34 = id3*L34r;

		d4 = dd - L34r*L34 - L24r*L24 - L14r*L14;
		if (gridTrackerStableFlag) {
			err = d4*id4; id4 = id4*(2 - err);
			err = d4*id4; id4 = id4*(2 - err);
		} else {
			id4 = 1/d4;
		}

		/* solve for y */
		y1 = b1;
		y2 = b2 - y1*L12;
		y3 = b3 - y1*L13 - y2*L23;
		y4 = b4 - y1*L14 - y2*L24 - y3*L34;

		/* solve for z */
		z1 = y1*id1;
		z2 = y2*id2;
		z3 = y3*id3;
		z4 = y4*id4;

		/* solve for x */
		x4 = z4;
		x3 = z3 - x4*L34;
		x2 = z2 - x4*L24 - x3*L23;
		x1 = z1 - x4*L14 - x3*L13 - x2*L12;

		/* well, there we have it! */

		/* add it in */
		posx += x1;
		posy += x2;
		posh += x3;
		theta += x4;
		cth += -sth * theta;
		sth += cth * theta;

		/* keep (cth,sth) normalized */
		err = (3 - cth * cth - sth * sth) / 2;
		cth *= err;
		sth *= err;

		/* compensate polynomial for self motion */
		la = lb = lc = ld = 0;
		if (posh < 0.75)
			posh = 0.75; // don't allow grid squares to blow up
	} // gridTrackerUpdatePositionEstimate

	private void gridTrackerDisplayGrid(Graphics g) {
		//-------------- if on a computer, draw grid lines to help double check that it is working correctly --------------
		g.setColor(Color.CYAN);

		int k;
		double x, y, p1x, p1y, p2x, p2y;

		for (k = -10; k < 10; k++) {
			/* p1 */
			x = k; y = -10;
			x = (x - posx) / posh;
			y = (y - posy) / posh;
			p1x = cth * x - sth * y;
			p1y = sth * x + cth * y;
			p1x *= 64; p1x += 64;  /* put it back in original event coordinates */
			p1y *= 64; p1y += 64;
			/* p2 */
			x = k; y = 10;
			x = (x - posx) / posh;
			y = (y - posy) / posh;
			p2x = cth * x - sth * y;
			p2y = sth * x + cth * y;
			p2x *= 64; p2x += 64;
			p2y *= 64; p2y += 64;
			g.drawLine(((int) (4.0*p1x)), ((int) (4.0*p1y)), ((int) (4.0*p2x)), ((int) (4.0*p2y))); /* or however you draw a line from p1 to p2 */

			/* p1 */
			x = -10; y = k;
			x = (x - posx) / posh;
			y = (y - posy) / posh;
			p1x = cth * x - sth * y;
			p1y = sth * x + cth * y;
			p1x *= 64; p1x += 64;
			p1y *= 64; p1y += 64;
			/* p2 */
			x = 10; y = k;
			x = (x - posx) / posh;
			y = (y - posy) / posh;
			p2x = cth * x - sth * y;
			p2y = sth * x + cth * y;
			p2x *= 64; p2x += 64;
			p2y *= 64; p2y += 64;
			g.drawLine(((int) (4.0*p1x)), ((int) (4.0*p1y)), ((int) (4.0*p2x)), ((int) (4.0*p2y))); /* or however you draw a line from p1 to p2 */
		}

	}

	// ******************************************************************************************************************************************************
	// ******************************************************************************************************************************************************
	// ******************************************************************************************************************************************************
	// ******************************************************************************************************************************************************

	public class showGUI extends JApplet {
		private static final long serialVersionUID = 1L;

		showGUI() {
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			frame.getContentPane().add(this);
			frame.setSize(400, 500);
			frame.setResizable(true);				// allow changes in size
			frame.setName("DVSViewerWindow");

			frame.setLocation(1600, 100);

			Container cp = getContentPane();
			cp.setLayout(null);		

			cp.add(closeGUIButton);
			closeGUIButton.setText("closeGUI");
			closeGUIButton.setBounds(280, 10, 100, 20);

			cp.add(stableFlagCheckBox);
			stableFlagCheckBox.setText("isStable");
			stableFlagCheckBox.setBounds(20, 20, 140, 20);

			cp.add(resetButton);
			resetButton.setText("init");
			resetButton.setBounds(160, 100, 120, 30);

			frame.setVisible(true);

			ActionListener bl = new ButtonListener();
			stableFlagCheckBox.addActionListener(bl);
			closeGUIButton.addActionListener(bl);
			resetButton.addActionListener(bl);

			loadConfig();
		}
	}

	public void saveConfig() {
		Preferences prefs = Preferences.userNodeForPackage(EmbeddedDVS128Viewer.class);
		prefs.putInt("PigTrackerFrameLocationX", frame.getLocation().x);
		prefs.putInt("PigTrackerFrameLocationY", frame.getLocation().y);
	}
	public void loadConfig() {
		Preferences prefs = Preferences.userNodeForPackage(EmbeddedDVS128Viewer.class);

		frame.setLocation(prefs.getInt("PigTrackerFrameLocationX", 10),
				prefs.getInt("PigTrackerFrameLocationY", 10));
	}

	public class ButtonListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {

			if (e.getSource() == closeGUIButton) {
				frame.dispose();
			}

			if (e.getSource() == stableFlagCheckBox) {
				gridTrackerStableFlag = stableFlagCheckBox.isSelected();
			}
			if (e.getSource() == resetButton) {
				gridTrackerInit();
			}

			saveConfig();
		}
	}

	public void init() {
		isActive.setText("GridTracker");
		gridTrackerInit();
	}

	public void paintComponent(Graphics g) {
		gridTrackerDisplayGrid(g);
	}

	long processEventCounter = 0;
	public int processNewEvent(int eventX, int eventY, int eventP) {
		
		gridTrackerProcessEvent(eventX, eventY);
		
		processEventCounter++;
		if (processEventCounter == 50) {
			gridTrackerUpdatePositionEstimate();
			processEventCounter = 0;
		}

		return(0);
	}

	public void processSpecialData(String specialData) {
	}

	public void callBackButtonPressed(ActionEvent e) {
		System.out.println("CallBack!");
		new showGUI();
	}
}

