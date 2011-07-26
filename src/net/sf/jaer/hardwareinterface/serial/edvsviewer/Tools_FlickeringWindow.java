package net.sf.jaer.hardwareinterface.serial.edvsviewer;

import java.awt.Container;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;

import javax.swing.JApplet;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.WindowConstants;


public class Tools_FlickeringWindow extends JApplet {

	static final long serialVersionUID = 1;

	private JFrame frame = new JFrame("Flickeing Window");

	private ImagePanel imagePanel;
	private DisplayThread displayThread;

	private JSlider timeSlider = new JSlider(0, 400, 16);

	private JButton quitButton = new JButton("Quit");

	public Tools_FlickeringWindow() {

		// generate frame for our GUI
		frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		frame.getContentPane().add(this);
		frame.setSize(660, 700);
		frame.setResizable(true);				// allow changes in size

		// prepare graphics for GUI
		Container cp = getContentPane();
		cp.setLayout(null);

		imagePanel = new ImagePanel();

		cp.add(imagePanel);
		imagePanel.setBounds(10, 10, 600, 600);

		cp.add(quitButton);
		quitButton.setBounds(10, 620, 100, 30);

		quitButton.addActionListener(new ButtonListener());

		cp.add(timeSlider);
		timeSlider.setOrientation(JSlider.HORIZONTAL);
		timeSlider.setBounds(120, 620, 360, 20);
		
		// display what happened
		frame.setVisible(true);
		
		displayThread = new DisplayThread();
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

		private int size = 16;
		private BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);

		public void paintComponent(Graphics g) {
			super.paintComponent(g);
			g.drawImage(image, 0, 0, 600, 600, this);
		}

		ImagePanel() {
		}
		protected void setBlack() {
			
			for (int x=0; x<size; x++) {
				for (int y=0; y<size; y++) {
					image.setRGB(x, y, (Math.random()>0.5)?0xFFFFFFFF:0);
				}
			}
			repaint();
		}
		protected void setWhite() {
			image.setRGB(0, 0, 0xFFFFFFFF);
			repaint();
		}
	}

	public class DisplayThread extends Thread {
		private Boolean isRunning;

		public DisplayThread() {
			isRunning = true;
			this.start();
		}
		public void run() {
			while (isRunning) {
				try {
					Thread.sleep(timeSlider.getValue());
				} catch (Exception e) {};
				imagePanel.setBlack();

//				try {
//					Thread.sleep(timeSlider.getValue());
//				} catch (Exception e) {};
//				imagePanel.setWhite();
			}
		}

		public void terminate() {
			isRunning=false;
		}
	}
}
