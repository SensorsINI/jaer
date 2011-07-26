package net.sf.jaer.hardwareinterface.serial.edvsviewer;

import java.awt.Color;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JApplet;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.WindowConstants;

public class Tools_WheelWindow extends JApplet {

	static final long serialVersionUID = 1;

	private JFrame frame = new JFrame("Wheel(ing) Window");

	private final int windowSize = 800;
	private final int wheelSize  = 500;

	private ImagePanel imagePanel;
	private DisplayThread displayThread;

	private JSlider shadeOfGray = new JSlider(0, 255, 32);
	private JSlider delayTime	  = new JSlider(0, 50, 20);

	private JButton quitButton = new JButton("Quit");

	private double angle;

	public Tools_WheelWindow() {

		// generate frame for our GUI
		frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		frame.getContentPane().add(this);
		frame.setSize(windowSize+60, windowSize+100);
		frame.setLocation(1440+1920-windowSize-60, 1080-windowSize-100);
		frame.setResizable(true);				// allow changes in size

		// prepare graphics for GUI
		Container cp = getContentPane();
		cp.setLayout(null);

		imagePanel = new ImagePanel();

		cp.add(quitButton);
		quitButton.setBounds(10, 10, 100, 30);

		quitButton.addActionListener(new ButtonListener());

		cp.add(shadeOfGray);
		shadeOfGray.setOrientation(JSlider.HORIZONTAL);
		shadeOfGray.setBounds(120, 10, 360, 20);

		cp.add(delayTime);
		delayTime.setOrientation(JSlider.HORIZONTAL);
		delayTime.setBounds(120, 30, 360, 20);


		
		// and finally the display
		cp.add(imagePanel);
		imagePanel.setBounds(10, 50, windowSize+10, windowSize+10);

		// display what happened
		frame.setVisible(true);
		
		displayThread = new DisplayThread(imagePanel);
	}
	public void close() {
		displayThread.terminate();
		frame.dispose();
	}

	private class ButtonListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			close();
		}
	}

	private class ImagePanel extends JPanel {
		static final long serialVersionUID = 1;


		public void paintComponent(Graphics g) {
			super.paintComponent(g);
			
			g.setColor(Color.WHITE);
			g.fillRect(0, 0, windowSize, windowSize);

			int c = shadeOfGray.getValue();
			g.setColor(new Color(c,c,c));

			final int cx = windowSize/2;
			final int cy = windowSize/2;
			final int sx = cx-wheelSize/2;
			final int sy = cy-wheelSize/2;
			
			g.fillArc(sx, sy, wheelSize, wheelSize, (int) angle    , 30);
			g.fillArc(sx, sy, wheelSize, wheelSize, (int) angle+ 60, 30);
			g.fillArc(sx, sy, wheelSize, wheelSize, (int) angle+120, 30);
			g.fillArc(sx, sy, wheelSize, wheelSize, (int) angle+180, 30);
			g.fillArc(sx, sy, wheelSize, wheelSize, (int) angle+240, 30);
			g.fillArc(sx, sy, wheelSize, wheelSize, (int) angle+300, 30);

			g.setColor(Color.BLACK);
			g.fillArc(cx-50, cy-50, 100, 100, 0, 360);
		}

		ImagePanel() {
		}
		
		public void update() {

			// shadeOfGray.getValue()

			angle += 1;
			if (angle >= (360)) angle -= (360);

			repaint();
		}
	}

	public class DisplayThread extends Thread {
		private Boolean isRunning;
		private ImagePanel i;

		public DisplayThread(ImagePanel i) {
			isRunning = true;
			this.i = i;
			this.start();
		}
		public void run() {
			long lastTime;
			long currentTime;

			lastTime = System.nanoTime();
			while (isRunning) {
//				try {
////					yield();
//					Thread.sleep(delayTime.getValue());
//				} catch (Exception e) {};

				while ((currentTime = System.nanoTime()) < (lastTime+(1000000*delayTime.getValue()))) {
					yield();
				}
				lastTime = currentTime;

				i.update();
			}
		}

		public void terminate() {
			isRunning=false;
		}
	}
}
