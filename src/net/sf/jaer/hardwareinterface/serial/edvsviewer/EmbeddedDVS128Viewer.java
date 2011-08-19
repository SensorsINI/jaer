/*
 * Created on Dec 2, 2004
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package net.sf.jaer.hardwareinterface.serial.edvsviewer;

import java.awt.Color;
import java.awt.Container;
import java.awt.event.*;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.*;
import net.sf.jaer.hardwareinterface.serial.edvsviewer.HWPort.*;
import net.sf.jaer.hardwareinterface.serial.edvsviewer.HWPort.PortIdentifier;



/**
 * A standalone viewer application for the eDVS128 cameras with FTDI serial-port emulating FTDI interfaces.
 * 
 * <pre>
 * 
 *  Top right buttons:
   logE: start / stop event capturing in memory. Once stopped, events will get stored on Harddisc in human readable form
   F: select logfile for event capturing.

  Three sliders top right:
  - (left) intensity for each new incoming event
  - (center) time constant for event-image fading; at bottom events will be displayed forever.
  - (right) PWM modulated output on separate PIN of sensor, not used in your setting. Ignore.

  Buttons:
  OE: output enable (dump raw sensor data on shell window). Do not use
  B/W: switch to black/white display (gray shading) instead of color
  fX / fY: flip display in X / Y direction
  X<=>Y: flip axes
  Foveated: simulate a foveated view (we used that last year with spiNNaker)

  Bias: open a bias setting panel. A little tricky to use, but maybe helpful. You need to click "set" after changes (or enable "cont. send" which is kind of flaky). Click same "bias" button again to close window.
  !E0!E+/- : set data format (E0) and enable/disable streaming of data --- this will get you started
  !R+/-: report on-sensor event count (shown in red/green and black-total). This is counted on sensor instead of in Java, so the numbers are more precise.
  !T+/-: enable/disable embedded tracking algorithm (not relevant; not contained in your microcontroller code))
  !A+/-: a first approach for automatic gain tuning (not relevant; not contained in your microcontroller code)

 * </pre>
 * 
 * @author Jorg Conradt (jconradt)
 *
 */
public class EmbeddedDVS128Viewer extends JApplet {

	static final long serialVersionUID = 1;

	private JFrame frame = new JFrame("DVS Viewer");

	private JButton quitButton = new JButton("Quit");
	private JTextField txt = new JTextField(24);
	private JToggleButton connectButton = new JToggleButton("connect");
	
	private JRadioButton selectComPort = new JRadioButton();
	private JRadioButton selectTCPPort = new JRadioButton();

	private JComboBox comComboBox = new JComboBox();
	private JComboBox baudComboBox = new JComboBox();
	private JRadioButton rtsCtsButton = new JRadioButton();

	private JTextField tcpAddress = new JTextField();

	private JButton replayEventsButton = new JButton("Replay Events from File");
	private JToggleButton gamePadButton = new JToggleButton("GamePad");

	private JButton showFlickerWindowButton = new JButton("showFlicker");
	private JButton showBlobWindowButton = new JButton("showBlobs");
	private JButton showWheelWindowButton = new JButton("showWheel");
	private JButton runTestButton = new JButton("Test");

	private IOThread terminalThread = null;
	private EventProcessorWindow viewerWindow = null;
//	private Tools_GamePad gpThread = null;

	private HWP_RS232 portRS232 = new HWP_RS232();
	private HWP_TCP portTCP = new HWP_TCP();
	private HWPort port = null;

	private int lastPortIndex = 0;

	private void saveConfig() {
		Preferences prefs = Preferences.userNodeForPackage(EmbeddedDVS128Viewer.class);
//System.out.println("f: " + frame.getSize());
		prefs.putInt("DimensionWidth", (int) (frame.getSize().getWidth()));
		prefs.putInt("DimensionHeight", (int) (frame.getSize().getHeight()));
		prefs.putInt("FrameLocationX", frame.getLocation().x);
		prefs.putInt("FrameLocationY", frame.getLocation().y);

		prefs.putBoolean("selectComPort", selectComPort.isSelected());
		prefs.putBoolean("selectTCPPort", selectTCPPort.isSelected());
		prefs.putInt("ComPortIndex", comComboBox.getSelectedIndex());
		prefs.putInt("BaudRateIndex", baudComboBox.getSelectedIndex());	
		prefs.putBoolean("rtscts", rtsCtsButton.isSelected());
		
		prefs.put("tcpAddress", tcpAddress.getText());
	}
	private void loadConfig() {
		Preferences prefs = Preferences.userNodeForPackage(EmbeddedDVS128Viewer.class);

		frame.setSize(prefs.getInt("DimensionWidth", 460),
				prefs.getInt("DimensionHeight", 130));
		frame.setLocation(prefs.getInt("FrameLocationX", 10),
				prefs.getInt("FrameLocationY", 10));

		selectComPort.setSelected(prefs.getBoolean("selectComPort", false));
		selectTCPPort.setSelected(prefs.getBoolean("selectTCPPort", false));
		try {
			comComboBox.setSelectedIndex(prefs.getInt("ComPortIndex", 0));
			lastPortIndex = comComboBox.getSelectedIndex();
			baudComboBox.setSelectedIndex(prefs.getInt("BaudRateIndex", 0));
		} catch (Exception e) {/* ** */}
		rtsCtsButton.setSelected(prefs.getBoolean("rtscts", true));
		
		tcpAddress.setText(prefs.get("tcpAddress", "wrobot01.wlan.ini.uzh.ch:80"));
	}

	public EmbeddedDVS128Viewer() {

		// generate frame for our GUI
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.getContentPane().add(this);
		frame.setSize(420, 100);
		frame.setResizable(true);				// allow changes in size
		frame.setName("eDVSViewer");

		// fill items on GUI
		txt.setText("eDVS128 - Interface");
		txt.setHorizontalAlignment(SwingConstants.CENTER);
		txt.setEditable(false);
		txt.setBackground(Color.WHITE);

		refreshPortList();

		rtsCtsButton.setText("RTS/CTS");

		// retrieve old configuration
		this.loadConfig();

		// prepare graphics for GUI
		Container cp = getContentPane();
		cp.setLayout(null);

		// add stuff to our GUI
		cp.add(quitButton);
		cp.add(txt);
		cp.add(connectButton);

		cp.add(selectComPort);
		cp.add(selectTCPPort);

		cp.add(comComboBox);
		cp.add(baudComboBox);
		cp.add(rtsCtsButton);
		
		cp.add(tcpAddress);

		cp.add(replayEventsButton);
		cp.add(gamePadButton);

		cp.add(showFlickerWindowButton);
		cp.add(showBlobWindowButton);
		cp.add(showWheelWindowButton);
		cp.add(runTestButton);

		quitButton.setBounds(10, 10, 100, 30);
		txt.setBounds(120, 15, 180, 20);
		connectButton.setBounds(310, 10, 120, 30);

		selectComPort.setBounds(5, 60, 20, 20);
		comComboBox.setBounds(35, 60, 80, 20);
		baudComboBox.setBounds(125, 60, 175, 20);
		rtsCtsButton.setBounds(339, 60, 80, 20);

		selectTCPPort.setBounds(5, 90, 20, 20);
		tcpAddress.setBounds(35, 90, 395, 20);

		replayEventsButton.setBounds(35, 120, 190, 20);
		gamePadButton.setBounds(240, 120, 190, 20);

		showFlickerWindowButton.setBounds(10, 200, 120, 20);
		showBlobWindowButton.setBounds(10, 230, 120, 20);
		showWheelWindowButton.setBounds(10, 260, 120, 20);
		runTestButton.setBounds(260, 200, 60, 20);

		// what to do when events happen

		ActionListener bl = new ButtonListener();

		selectComPort.addActionListener(bl);
		selectTCPPort.addActionListener(bl);

		quitButton.addActionListener(bl);
		connectButton.addActionListener(bl);
		comComboBox.addActionListener(bl);
		baudComboBox.addActionListener(bl);

		replayEventsButton.addActionListener(bl);
		gamePadButton.addActionListener(bl);

		showFlickerWindowButton.addActionListener(bl);
		showBlobWindowButton.addActionListener(bl);
		showWheelWindowButton.addActionListener(bl);
		runTestButton.addActionListener(bl);
                
                baudComboBox.setToolTipText("4000000 is standard rate for eDVS128 cameras");
                rtsCtsButton.setToolTipText("RTS/CTS active for eDVS128 camera");
                comComboBox.setToolTipText("To find correct port, use the DeviceManager in Windows to see which COM ports are added when you plug in the camera and select the lower numbered one");
		// display what happened
		frame.setVisible(true);
	}

	private void refreshPortList() {
		comComboBox.removeAllItems();
		// add available COM ports to menu
		List<PortIdentifier> lid = portRS232.getPortIdentifierList();    
		for (PortIdentifier id : lid) {
			comComboBox.addItem(id);
		}
		comComboBox.setSelectedIndex(lastPortIndex);

		int index = baudComboBox.getSelectedIndex();
		baudComboBox.removeAllItems();
		List<PortAttribute> lpa = portRS232.getPortAttributeList();
		for (PortAttribute pa : lpa) {
			baudComboBox.addItem(pa);
		}
		baudComboBox.setSelectedIndex(index);
	}

	private void connect() {
		if (connectButton.isSelected()) {

			if (terminalThread != null) {
				terminalThread.terminate();
			}

			if (selectComPort.isSelected()) {
				String portName = (String) (((PortIdentifier) (comComboBox.getSelectedItem())).getID());

				terminalThread = new IOThread(portRS232, this);
				terminalThread.start();

				terminalThread.openPort(
						portName,
						(PortAttribute) baudComboBox.getSelectedItem());

				portRS232.setHardwareFlowControl(rtsCtsButton.isSelected());
				portRS232.purgeInput();

				port = portRS232;

			} else {

				String name = tcpAddress.getText();
				String portStr = "80";
				if (name.contains(":")) {
					int pos = name.indexOf(":");
					portStr = name.substring(pos+1);
					name = name.substring(0, pos);
				}
									System.out.println("name: " + name);
									System.out.println("port: " + portStr);

				PortAttribute pa = portTCP.new PortAttribute(new Integer(portStr), " Port :<>");

				terminalThread = new IOThread(portTCP, this);
				terminalThread.start();
				terminalThread.openPort(name, pa);

				portTCP.purgeInput();

				port = portTCP;
			}
			
			if (viewerWindow != null) {
				viewerWindow.close();
			}
			viewerWindow = new EventProcessorWindow(port, terminalThread);

		} else {

			if (viewerWindow != null) {
				viewerWindow.close();
			}

			if (terminalThread != null) {
				terminalThread.terminate();
			}
			terminalThread = null;

			port = null;
		}

//		if (gpThread != null) {
//			gpThread.setIOThreadReference(terminalThread);
//		}

	}
	private class ButtonListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {

			saveConfig();

			if (e.getSource() == quitButton) {
				if (connectButton.isSelected()) {
					if (terminalThread.isAlive()) {
						terminalThread.terminate();
					}
					terminalThread = null;
				}
				Thread.yield();
				System.exit(0);
			}
			if (e.getSource() == connectButton) {
				connect();
			}

			if (e.getSource() == comComboBox) {
				String portName = (String) (((PortIdentifier) (comComboBox.getSelectedItem())).getID());

				if (portName.equals("-rescan-")) {
					refreshPortList();
					System.out.println("port rescan done!");
				}
				lastPortIndex = comComboBox.getSelectedIndex();
				saveConfig();
			}

			if (e.getSource() == baudComboBox) {
			
				if (portRS232.isOpen()) {
					portRS232.setAttribute((PortAttribute) baudComboBox.getSelectedItem());
				}
			}

			if (e.getSource() == selectComPort) {
				selectComPort.setSelected(true);
				selectTCPPort.setSelected(false);
			}
			if (e.getSource() == selectTCPPort) {
				selectComPort.setSelected(false);
				selectTCPPort.setSelected(true);
			}
			
			if (e.getSource() == replayEventsButton) {
				Thread t = new ReplayEventsFromFile();			// starts a thread to replay events
				t.start();
			}
//
//			if (e.getSource() == gamePadButton) {
//				if (gpThread != null) {
//					gpThread.close();
//				}
//				gpThread = null;
//				if (gamePadButton.isSelected()) {
//					gpThread = new Tools_GamePad();
//					gpThread.setIOThreadReference(terminalThread);
//				}
//			}

			if (e.getSource() == showFlickerWindowButton) {
				new Tools_FlickeringWindow();
			}

			if (e.getSource() == showBlobWindowButton) {
				new Tools_BlobWindow();
			}
			
			if (e.getSource() == showWheelWindowButton) {
				new Tools_WheelWindow();
			}
			
			if (e.getSource() == runTestButton) {
				runTest();
			}
		}
	}

	private void runTest() {
		new Tools_Test();
	}

	public static void main(String[] args) {
		new EmbeddedDVS128Viewer();
	}
}
