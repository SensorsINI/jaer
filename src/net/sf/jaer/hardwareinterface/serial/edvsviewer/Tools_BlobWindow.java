package net.sf.jaer.hardwareinterface.serial.edvsviewer;

import java.awt.Color;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.AffineTransform;
import java.util.LinkedList;

import javax.swing.JApplet;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.WindowConstants;

public class Tools_BlobWindow extends JApplet {

	static final long serialVersionUID = 1;

	private JFrame frame = new JFrame("Blobbing Window");

	private ImagePanel imagePanel;
	private DisplayThread displayThread;

	private JSlider numberOfBlobs = new JSlider(0, 10, 4);
	private JSlider delayTime	  = new JSlider(0, 100, 30);

	private JCheckBox circleEnabled = new JCheckBox();
	private JCheckBox deformEnabled = new JCheckBox();
	private JCheckBox rotateEnabled = new JCheckBox();

	private JButton quitButton = new JButton("Quit");

	public Tools_BlobWindow() {

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
		imagePanel.setBounds(10, 10, 610, 610);

		cp.add(quitButton);
		quitButton.setBounds(10, 620, 100, 30);

		quitButton.addActionListener(new ButtonListener());

		cp.add(numberOfBlobs);
		numberOfBlobs.setOrientation(JSlider.HORIZONTAL);
		numberOfBlobs.setBounds(120, 620, 360, 20);

		cp.add(delayTime);
		delayTime.setOrientation(JSlider.HORIZONTAL);
		delayTime.setBounds(120, 640, 360, 20);

		cp.add(circleEnabled);
		cp.add(deformEnabled);
		cp.add(rotateEnabled);
		circleEnabled.setText("circle");
		circleEnabled.setBounds(490, 620, 60, 20);
		deformEnabled.setText("deform");
		deformEnabled.setBounds(550, 620, 70, 20);
		rotateEnabled.setText("rotate");
		rotateEnabled.setBounds(550, 640, 70, 20);

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

		private final int size = 600;
		private final int min_diam= 30;
		private final int max_diam = 100;

		private class ellipse {
			double x, y, dx, dy, alpha;							// current pos
			double x_dt, y_dt, dx_dt, dy_dt, alpha_dt;

			ellipse() {
				this.x = 100+(size-200)*Math.random();
				this.y = 100+(size-200)*Math.random();
				this.dx = min_diam+(max_diam-min_diam)*Math.random();
				if (circleEnabled.isSelected()) {
					this.dy = dx;
				} else {
					this.dy = min_diam+(max_diam-min_diam)*Math.random();
				}
				this.alpha = 360.0*Math.random();

				x_dt = 4.0*(-0.5+Math.random());
				y_dt = 4.0*(-0.5+Math.random());

				if (deformEnabled.isSelected()) {
					dx_dt = 0.2*Math.random();
					dy_dt = 0.2*Math.random();
				} else {
					dx_dt = 0.0;
					dy_dt = 0.0;
				}
				if (rotateEnabled.isSelected()) {
					alpha_dt = Math.random();
				} else {
					alpha_dt = 0.0;
				}
			}
			void iterate(){
				x += x_dt;
				y += y_dt;
				dx += dx_dt;
				dy += dy_dt;
				alpha += alpha_dt;

				double dxdy = dx+dy;

				if ((x-dxdy)<0)     x_dt = -x_dt;
				if ((x+dxdy)>size)  x_dt = -x_dt;
				if ((y-dxdy)<0)     y_dt = -y_dt;
				if ((y+dxdy)>size)  y_dt = -y_dt;

				if (dx<min_diam)  dx_dt = -dx_dt;
				if (dx>max_diam)  dx_dt = -dx_dt;
				if (dy<min_diam)  dy_dt = -dy_dt;
				if (dy>max_diam)  dy_dt = -dy_dt;
				
				if (alpha>360.0) alpha -=360.0;
				if (alpha<  0.0) alpha +=360.0;
			}
			void draw(Graphics g) {

				Graphics2D g2 = (Graphics2D) g;
				AffineTransform saveAT = g2.getTransform();

				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);

				g2.translate(((int) (x-dx/2)), ((int) (y-dy/2)));
				g2.rotate(Math.PI * alpha / 180.0);
				g2.fillOval(0, 0, ((int) (dx)), ((int) (dy)));
//				g2.fillOval(((int) (x-dx/2)), ((int) (y-dy/2)), ((int) (dx)), ((int) (dy)));

				g2.setTransform(saveAT);

			}
		}

		public LinkedList<ellipse> el = new LinkedList<ellipse>();

		public void paintComponent(Graphics g) {
			super.paintComponent(g);
			g.setColor(Color.WHITE);
			g.fillRect(0, 0, size, size);
			g.setColor(Color.BLACK);
			for (ellipse e:el) {
				e.draw(g);
			}
		}

		ImagePanel() {
		}
		
		public void update() {

			while (el.size() > numberOfBlobs.getValue()) {
				el.removeLast();
			}

			while (el.size() < numberOfBlobs.getValue()) {
				el.add(new ellipse());
			}

			for (ellipse e:el) {
				e.iterate();
			}
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
			while (isRunning) {
				try {
//					yield();
					Thread.sleep(delayTime.getValue());
				} catch (Exception e) {};

				i.update();
			}
		}

		public void terminate() {
			isRunning=false;
		}
	}
}
