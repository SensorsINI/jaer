//package net.sf.jaer.hardwareinterface.serial.edvsviewer;
// tobi disabled this class because it requires jinput.jar and a bunch of jinput native libraries that cause problems resolving on some machines for unknown reasons
//
//import java.awt.Component;
//import java.awt.Container;
//import java.awt.event.ActionEvent;
//import java.awt.event.ActionListener;
//import java.util.prefs.Preferences;
//
//import javax.swing.JApplet;
//import javax.swing.JButton;
//import javax.swing.JComboBox;
//import javax.swing.JFrame;
//import javax.swing.JLabel;
//import javax.swing.JOptionPane;
//import javax.swing.JSlider;
//import javax.swing.JToggleButton;
//import javax.swing.event.ChangeEvent;
//import javax.swing.event.ChangeListener;
//
//import net.java.games.input.Controller;
//import net.java.games.input.ControllerEnvironment;
//
//public class Tools_GamePad {
//
//	private GamePadGUI gpGUI = null;
//	private GamePadThread gpThread = null;
//
//    Controller gamePad=null;
//    ControllerEnvironment ce = ControllerEnvironment.getDefaultEnvironment();
//    Controller[] Controllers = ce.getControllers();
//    net.java.games.input.Component[] comps = null;
//
//    int[] axesReferences = new int[4];
//  
//    IOThread iot = null;
//
//	Tools_GamePad() {
//		gpGUI = new GamePadGUI();
//	}
//
//	public void setIOThreadReference(IOThread iot) {
//		this.iot = iot;
//	}
//
//	public void close() {
//		if (gpGUI != null) {
//			gpGUI.quit();
//		}
//		if (gpThread != null) {
//			gpThread.quit();
//		}
//	}
//
//	public class GamePadThread extends Thread {
//		private Boolean threadRunning;
//		private long timeDelay;
//		private long gpTime, gpLastTime;
//		private double gpAxes[] = new double[4];
//		private int gpAxesInt[] = new int[4];
//		private char[] cmd = new char[11];
//
//		public GamePadThread(String gamePadID) {
//
//	        for (int i = 0; i < Controllers.length; i++) {
//	            Controller c = Controllers[i];
//	            if (c.getName().equals(gamePadID)) {
//	            	gamePad = c;
//		    		comps=gamePad.getComponents();
//	                break;
//	            }
//	        }
//
//	        if (gamePad == null) {
//				JOptionPane.showMessageDialog(null, "GamePad not found", "gp interface", JOptionPane.DEFAULT_OPTION);
//				return;
//	        }
//			
//			threadRunning = true;
//			this.start();
//		}
//
//		private void sendGamePadInfo() {
//			gamePad.poll();							// update the controller's components
//			for (int n=0; n<4; n++) {
//    			gpAxes[n] = comps[axesReferences[n]].getPollData();		// get current value 
////    			gpAxesInt[n] = ((int) (2048*gpAxes[n])) + 2048; 
//    			gpAxesInt[n] = ((int) (50*gpAxes[n]));
//			}
//			
////			System.out.printf("GP: LH %6.3f    LV %6.3f     RH %6.3f    RV %6.3f\n", gpAxes[0], gpAxes[1], gpAxes[2], gpAxes[3]);
//
////			cmd[2] = (char) (((gpAxesInt[0] & 0xFC0)>>6) + 32);
////			cmd[3] = (char) ( (gpAxesInt[0] & 0x03F)     + 32);
////			cmd[4] = (char) (((gpAxesInt[1] & 0xFC0)>>6) + 32);
////			cmd[5] = (char) ( (gpAxesInt[1] & 0x03F)     + 32);
////			cmd[6] = (char) (((gpAxesInt[2] & 0xFC0)>>6) + 32);
////			cmd[7] = (char) ( (gpAxesInt[2] & 0x03F)     + 32);
////			cmd[8] = (char) (((gpAxesInt[3] & 0xFC0)>>6) + 32);
////			cmd[9] = (char) ( (gpAxesInt[3] & 0x03F)     + 32);
//
////			String commandString = new String(cmd);
//
//			String commandString = "!D"+gpAxesInt[0]+","+(-gpAxesInt[1])+","+(-gpAxesInt[2]);
//			System.out.println("String: " + commandString);
//
//			if (iot != null) {
//				iot.sendCommand(commandString);
//			}
//
//		}
//		public void run() {
//
//
//			gpLastTime = System.currentTimeMillis();
//
//			cmd[ 0] = '!';
//			cmd[ 1] = 'G';
//			cmd[10] = '\n';
//
//			while (threadRunning) {
//				yield();
//
//				if (timeDelay == 0) {
//					sendGamePadInfo();
//				} else {
//					gpTime = System.currentTimeMillis();
//					if (gpTime>(gpLastTime+timeDelay)) {
//						gpLastTime = gpTime;
//						sendGamePadInfo();
//					}
//	    		}
//			}
//		}
//
//		public void setAxisAssignement(int axisID, int gamePadAxisID) {
//			axesReferences[axisID] = gamePadAxisID;
//		}
//		public void setTimingDelay(long delayTime) {
//			timeDelay = delayTime;
//		}
//
//		public void quit() {
//			threadRunning = false;
//		}
//	}
//	public class GamePadGUI extends JApplet {
//
//		private static final long serialVersionUID = 1;
//
//		private JFrame frame = new JFrame("GamePadGUI");
//
//		private JComboBox gpComboBox = new JComboBox();
//
//		private JButton quitButton = new JButton("quit");
//		private JToggleButton gpConnectButton = new JToggleButton("connect");
//		private JToggleButton gpCalibrate = new JToggleButton("calibrate");
//
//		private JComboBox[] gpSelectAxis = new JComboBox[4];
//		private JLabel[] axesText = new JLabel[4];
//
//		private JSlider timingDelay = new JSlider(0, 500, 50);
//		private JLabel timingDelayText = new JLabel("");
//
//		private void populateGPList() {
//
//	        ControllerEnvironment ce = ControllerEnvironment.getDefaultEnvironment();
//	        Controller[] Controllers = ce.getControllers();
//
//	        gpComboBox.removeAllItems();
//	        gpComboBox.addItem(" - none - ");
//	        for (int i = 0; i < Controllers.length; i++) {
//	            Controller c = Controllers[i];
//	            gpComboBox.addItem(c.getName());
//	        }
//	        gpComboBox.addItem(" - rescan - ");
//		}
//
//		private void saveConfig() {
//			Preferences prefs = Preferences.userNodeForPackage(Tools_GamePad.class);
//			prefs.putInt("GPDimensionWidth", (int) (frame.getSize().getWidth()));
//			prefs.putInt("GPDimensionHeight", (int) (frame.getSize().getHeight()));
//			prefs.putInt("GPFrameLocationX", frame.getLocation().x);
//			prefs.putInt("PGFrameLocationY", frame.getLocation().y);
//
//			prefs.putInt("GPselection", gpComboBox.getSelectedIndex());
//
//			prefs.putInt("GpAxis0", gpSelectAxis[0].getSelectedIndex());
//			prefs.putInt("GpAxis1", gpSelectAxis[1].getSelectedIndex());
//			prefs.putInt("GpAxis2", gpSelectAxis[2].getSelectedIndex());
//			prefs.putInt("GpAxis3", gpSelectAxis[3].getSelectedIndex());
//			
//			prefs.putInt("GPTime", timingDelay.getValue());
//		}
//		private void loadConfig() {
//			Preferences prefs = Preferences.userNodeForPackage(Tools_GamePad.class);
//
//			frame.setSize(prefs.getInt("GPDimensionWidth", 320),
//					prefs.getInt("GPDimensionHeight", 140));
//			frame.setLocation(prefs.getInt("GPFrameLocationX", 10),
//					prefs.getInt("GPFrameLocationY", 10));
//
//			try {
//				gpComboBox.setSelectedIndex(prefs.getInt("GPselection", 0));
//			} catch (Exception e) {
//				// ** //
//			}
//
//			gpSelectAxis[0].setSelectedIndex(prefs.getInt("GpAxis0", 0));
//			gpSelectAxis[1].setSelectedIndex(prefs.getInt("GpAxis1", 1));
//			gpSelectAxis[2].setSelectedIndex(prefs.getInt("GpAxis2", 2));
//			gpSelectAxis[3].setSelectedIndex(prefs.getInt("GpAxis3", 3));
//
//			timingDelay.setValue(prefs.getInt("GPTime", 50));
//		}
//
//		public GamePadGUI() {
//
//			// generate frame for our GUI
//			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//			frame.getContentPane().add(this);	
//			frame.setSize(320, 200);					// initial guess!
//
//			// put content in items on GUI
//			populateGPList();
//
//			for (int n=0; n<4; n++) {
//				gpSelectAxis[n] = new JComboBox();
//				for (int m=0; m<8; m++) {
//					gpSelectAxis[n].addItem(m);
//				}
//			}
//
//			// prepare graphics for GUI
//			Container cp = getContentPane();
//			cp.setLayout(null);
//
//			// add stuff to our GUI
//			cp.add(quitButton);
//			cp.add(gpConnectButton);
//			cp.add(gpComboBox);
//			cp.add(gpCalibrate);
//
//			gpComboBox.setBounds( 10, 10, 200, 20);
//			quitButton.setBounds(220, 10, 60, 20);
//			gpConnectButton.setBounds(290, 10, 80, 20);
//			gpCalibrate.setBounds(380, 10, 100, 20);
//
//			for (int n=0; n<4; n++) {
//				cp.add(gpSelectAxis[n]);
//				gpSelectAxis[n].setBounds(10+100*n, 60, 60, 20);
//			}
//			axesText[0] = new JLabel("LeftHor");  cp.add(axesText[0]); axesText[0].setBounds( 10, 40, 400, 20);
//			axesText[1] = new JLabel("LeftVer");  cp.add(axesText[1]); axesText[1].setBounds(110, 40, 400, 20);
//			axesText[2] = new JLabel("RightHor"); cp.add(axesText[2]); axesText[2].setBounds(210, 40, 400, 20);
//			axesText[3] = new JLabel("RightVer"); cp.add(axesText[3]); axesText[3].setBounds(310, 40, 400, 20);
//
//			cp.add(timingDelay);
//			timingDelay.setOrientation(JSlider.HORIZONTAL);
//			timingDelay.setBounds(10, 100, 200, 20);
//			timingDelay.addChangeListener(new SliderListener());
//
//			cp.add(timingDelayText);
//			timingDelayText.setText("command interval " + timingDelay.getValue() + "ms");
//			timingDelayText.setBounds(220, 100, 200, 20);
//
//			// display what happened
//			frame.setVisible(true);
//
//			ButtonListener bl = new ButtonListener();
//			// what to do when events happen
//			quitButton.addActionListener(bl);
//			gpConnectButton.addActionListener(bl);
//			gpComboBox.addActionListener(bl);
//			gpCalibrate.addActionListener(bl);
//			
//			for (int n=0; n<4; n++) {
//				gpSelectAxis[n].addActionListener(bl);
//			}
//
//			loadConfig();
//		}
//		public void quit() {
//			saveConfig();
//			frame.dispose();
//		}
//
//		private class ButtonListener implements ActionListener {
//			public void actionPerformed(ActionEvent e) {
//
//				if (e.getSource() == quitButton) {
//					close();			// this calls close of GP_Gui
//				}
//
//				if (e.getSource() == gpComboBox) {
//					if (gpComboBox.getSelectedItem().equals(" - rescan - ")) {
//						populateGPList();
//						gpComboBox.setSelectedIndex(0);
//					}
//				}
//
//				if (e.getSource() == gpCalibrate) {
//					JOptionPane.showMessageDialog(null, "Not yet implemented. Please use ControlPanel->GamePads for calibration!", "gp interface", JOptionPane.DEFAULT_OPTION);
//				}
//
//				if (e.getSource() == gpConnectButton) {
//					if (gpThread != null) {
//						gpThread.quit();
//					}
//					if (gpConnectButton.isSelected()) {
//						gpThread = new GamePadThread(((String) gpComboBox.getSelectedItem()));
//						gpThread.setAxisAssignement(0, gpSelectAxis[0].getSelectedIndex());
//						gpThread.setAxisAssignement(1, gpSelectAxis[1].getSelectedIndex());
//						gpThread.setAxisAssignement(2, gpSelectAxis[2].getSelectedIndex());
//						gpThread.setAxisAssignement(3, gpSelectAxis[3].getSelectedIndex());
//
//						gpThread.setTimingDelay(timingDelay.getValue());
//					}
//				}
//
//				if (gpThread != null) {
//					for (int n=0; n<4; n++) {
//						if (e.getSource() == gpSelectAxis[n]) {
//							gpThread.setAxisAssignement(n, gpSelectAxis[n].getSelectedIndex());
//						}
//					}
//				}
//			}
//		}
//
//		private class SliderListener implements ChangeListener {
//			public void stateChanged(ChangeEvent e) {
//				timingDelay.setToolTipText("delayMS: " + timingDelay.getValue());
//				timingDelayText.setText("command interval " + timingDelay.getValue() + "ms");
//
//				if (gpThread != null) {
//					gpThread.setTimingDelay(timingDelay.getValue());
//				}
//			}
//		}
//
//	}
//
//}
