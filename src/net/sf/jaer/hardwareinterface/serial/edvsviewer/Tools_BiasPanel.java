package net.sf.jaer.hardwareinterface.serial.edvsviewer;

import java.awt.Container;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.prefs.Preferences;

import javax.swing.JApplet;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileFilter;

public class Tools_BiasPanel extends JApplet {

	static final long serialVersionUID = 1;

	private JFrame frame = new JFrame("Bias Settings");

	private DVSBiasSlider[] biasSlider = new DVSBiasSlider[12];

	private JCheckBox sendBiasImmediately = new JCheckBox("cont. send");
	private JButton setBiasButton  = new JButton("set");
	private JButton getBiasButton  = new JButton("get");
	private JButton saveBiasButton = new JButton("save");
	private JButton loadBiasButton = new JButton("load");

	private JButton initBiasButton = new JButton("init");
	private JButton zeroBiasButton = new JButton("zero");

	private IOThread terminalThread;

	JFileChooser fChooserBIAS = new JFileChooser();			// only pops-up at request!

	private void resetSliders() {
/*
		biasSlider[ 0].setValue(0x00042B);			// BIAS_DEFAULT
		biasSlider[ 1].setValue(0x00301C);
		biasSlider[ 2].setValue(0xFFFFFF);
		biasSlider[ 3].setValue(0x5523D4);
		biasSlider[ 4].setValue(0x000097);
		biasSlider[ 5].setValue(0x06864A);
		biasSlider[ 6].setValue(0x000000);
		biasSlider[ 7].setValue(0xFFFFFF);
		biasSlider[ 8].setValue(0x04853D);
		biasSlider[ 9].setValue(0x000E28);
		biasSlider[10].setValue(0x000027);
		biasSlider[11].setValue(0x000004);
*/
		
		biasSlider[ 0].setValue(        1067);		//BIAS_BRAGFOST
		biasSlider[ 1].setValue(       12316);
		biasSlider[ 2].setValue(    16777215);
		biasSlider[ 3].setValue(     5579731);
		biasSlider[ 4].setValue(          60);
		biasSlider[ 5].setValue(      427594);
		biasSlider[ 6].setValue(           0);
		biasSlider[ 7].setValue(    16777215);
		biasSlider[ 8].setValue(      567391);
		biasSlider[ 9].setValue(        6831);
		biasSlider[10].setValue(          39);
		biasSlider[11].setValue(           4);
	
	}
	private void initSliders(Container cp) {
		biasSlider[ 0] = new DVSBiasSlider("Tmpdiff128.IPot.cas    ",0);
		biasSlider[ 1] = new DVSBiasSlider("Tmpdiff128.IPot.injGnd ",0);
		biasSlider[ 2] = new DVSBiasSlider("Tmpdiff128.IPot.reqPd  ",0);
		biasSlider[ 3] = new DVSBiasSlider("Tmpdiff128.IPot.puX    ",0);
		biasSlider[ 4] = new DVSBiasSlider("Tmpdiff128.IPot.diffOff",0);
		biasSlider[ 5] = new DVSBiasSlider("Tmpdiff128.IPot.req    ",0);
		biasSlider[ 6] = new DVSBiasSlider("Tmpdiff128.IPot.refr   ",0);
		biasSlider[ 7] = new DVSBiasSlider("Tmpdiff128.IPot.puY    ",0);
		biasSlider[ 8] = new DVSBiasSlider("Tmpdiff128.IPot.diffOn ",0);
		biasSlider[ 9] = new DVSBiasSlider("Tmpdiff128.IPot.diff   ",0);
		biasSlider[10] = new DVSBiasSlider("Tmpdiff128.IPot.foll   ",0);
		biasSlider[11] = new DVSBiasSlider("Tmpdiff128.IPot.Pr     ",0);

		resetSliders();

		for (int n=0; n<12; n++) {
			biasSlider[n].display(cp, 10, 5+25*n);
		}
	}

	private void saveConfig() {
		Preferences prefs = Preferences.userNodeForPackage(EmbeddedDVS128Viewer.class);
		prefs.putInt("BiasDimensionWidth", (int) (frame.getSize().getWidth()));
		prefs.putInt("BiasDimensionHeight", (int) (frame.getSize().getHeight()));
		prefs.putInt("BiasFrameLocationX", frame.getLocation().x);
		prefs.putInt("BiasFrameLocationY", frame.getLocation().y);

//		prefs.putBoolean("SendBiasImmediately", sendBiasImmediately.isSelected());

//		for (int n=0; n<11; n++) {
//			prefs.putInt("Bias"+n, biasSlider[n].getValue());
//		}

		prefs.put("CurrentDirBIAS", (fChooserBIAS.getCurrentDirectory()).toString());
	}
	private void loadConfig() {
		Preferences prefs = Preferences.userNodeForPackage(EmbeddedDVS128Viewer.class);

		frame.setSize(prefs.getInt("BiasDimensionWidth", 660),
				prefs.getInt("BiasDimensionHeight", 12*20 + 120));
		frame.setLocation(prefs.getInt("BiasFrameLocationX", 10),
				prefs.getInt("BiasFrameLocationY", 10));

//		sendBiasImmediately.setSelected(prefs.getBoolean("SendBiasImmediately", true));

//		for (int n=0; n<11; n++) {
//			biasSlider[n].setValue(prefs.getInt("Bias"+n, 100));
//		}

		fChooserBIAS.setCurrentDirectory(new File(prefs.get("CurrentDirBIAS", "C:/")));
	}

	public Tools_BiasPanel(IOThread terminalThread) {

		this.terminalThread = terminalThread;

		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		frame.getContentPane().add(this);
		frame.setSize(660, 12*25 + 80);
		frame.setResizable(true);				// allow changes in size
		frame.setName("BiasPanelWindow");

		Container cp = getContentPane();
		cp.setLayout(null);


		sendBiasImmediately.setSelected(false);
		initSliders(cp);

		loadConfig();

		cp.add(sendBiasImmediately);
		cp.add(setBiasButton);
		cp.add(getBiasButton);
		cp.add(saveBiasButton);
		cp.add(loadBiasButton);
		cp.add(zeroBiasButton);
		cp.add(initBiasButton);

		Insets buttonMarginsTight = new Insets(0, 0, 0, 0);	// border top, left, bottom, right for tight boxes

		sendBiasImmediately.setMargin(buttonMarginsTight);
		setBiasButton.setMargin(buttonMarginsTight);
		getBiasButton.setMargin(buttonMarginsTight);
		loadBiasButton.setMargin(buttonMarginsTight);
		saveBiasButton.setMargin(buttonMarginsTight);
		zeroBiasButton.setMargin(buttonMarginsTight);
		initBiasButton.setMargin(buttonMarginsTight);

		sendBiasImmediately.setBounds(450, 310, 80, 20);
		setBiasButton.setBounds ( 10, 310, 40, 20);
		getBiasButton.setBounds ( 50, 310, 40, 20);
		saveBiasButton.setBounds(100, 310, 50, 20);
		loadBiasButton.setBounds(150, 310, 50, 20);
		initBiasButton.setBounds(250, 310, 50, 20);
		zeroBiasButton.setBounds(300, 310, 50, 20);

		ActionListener bl = new ButtonListener();
		sendBiasImmediately.addActionListener(bl);
		setBiasButton.addActionListener(bl);
		getBiasButton.addActionListener(bl);
		saveBiasButton.addActionListener(bl);
		loadBiasButton.addActionListener(bl);
		zeroBiasButton.addActionListener(bl);
		initBiasButton.addActionListener(bl);

		// display what happened
		frame.setVisible(true);
	}

	public void close() {
		saveConfig();
		frame.dispose();
	}

	public void setBiasFromChip(int biasID, int biasValue) {
		if ((biasID>=0) && (biasID<=12)) {
			biasSlider[biasID].setValue(biasValue);
		}
	}

	private void sendBiasValuesToChip() {
		if (terminalThread != null) {
			for (int n=0; n<12; n++) {
				terminalThread.sendCommand("!B"+n+"="+biasSlider[n].getValue());
				Thread.yield();
			}
			terminalThread.sendCommand("B");		// flush values to sensor!
		}
	}

	private class ButtonListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			if (e.getSource() == setBiasButton) {
				sendBiasValuesToChip();
			}
			if (e.getSource() == getBiasButton) {

				for (int n=0; n<12; n++) {
					biasSlider[n].setValue(-1);			// clear all biases (to clearly indicate if we missed fetching one of them!)
				}

				if (terminalThread != null) {
					for (int n=0; n<12; n++) {
									// use 3 digit format for number, otherwise the receiving thread gets confused, when expecting
									// two bytes for each event...
						String cmd = String.format("?B%03d", n);
						terminalThread.sendCommand(cmd);
						Thread.yield();
					}
				}
			}
			if (e.getSource() == saveBiasButton) {
				saveBiasValues();
			}
			
			if (e.getSource() == loadBiasButton) {
				loadBiasValues();
				if (sendBiasImmediately.isSelected()) {
					sendBiasValuesToChip();
				}
			}

			if (e.getSource() == zeroBiasButton) {
				sendBiasImmediately.setSelected(false);
				for (int n=0; n<12; n++) {
					biasSlider[n].setValue(0);
				}
			}
			if (e.getSource() == initBiasButton) {
				resetSliders();
				if (sendBiasImmediately.isSelected()) {
					sendBiasValuesToChip();
				}
			}
		
		}
	}

	private void saveBiasValues() {

		fChooserBIAS.setMultiSelectionEnabled(false);
		fChooserBIAS.setFileFilter(new FileEndingFilter(".DVSB"));
		fChooserBIAS.setFileSelectionMode(JFileChooser.FILES_ONLY);

		int returnVal = fChooserBIAS.showSaveDialog(null);
		if(returnVal == JFileChooser.APPROVE_OPTION) {

			String fileName = fChooserBIAS.getSelectedFile().getAbsolutePath();
			if (fileName.contains(".") == false) {
				fileName=fileName+".DVSB";
			}

			try {
				File biasFile = new File(fileName);
				biasFile.createNewFile();
				FileOutputStream logFileOutStream = new FileOutputStream(biasFile);

				for (int n=0; n<12; n++) {
					String l = String.format("%30s %10d\n", biasSlider[n].getName(), biasSlider[n].getValue());
					logFileOutStream.write(l.getBytes());
				}

				logFileOutStream.close();
				biasFile=null;
				logFileOutStream = null;

			} catch (Exception e) {
				JOptionPane.showMessageDialog(null, "Error Saving Bias Values!", "DVS128", JOptionPane.DEFAULT_OPTION);
			}

		}
	}
	private void loadBiasValues() {

		fChooserBIAS.setMultiSelectionEnabled(false);
		fChooserBIAS.setFileFilter(new FileEndingFilter(".DVSB"));
		fChooserBIAS.setFileSelectionMode(JFileChooser.FILES_ONLY);

		int returnVal = fChooserBIAS.showOpenDialog(null);
		if(returnVal == JFileChooser.APPROVE_OPTION) {
			String fileName = fChooserBIAS.getSelectedFile().getAbsolutePath();
			if (fileName.contains(".") == false) {
				fileName=fileName+".DVSB";
			}
			
			try {

				File biasFile = new File(fileName);
				FileInputStream biasFileInStream = new FileInputStream(biasFile);

				InputStreamReader isr = new InputStreamReader(biasFileInStream);
				LineNumberReader lnr = new LineNumberReader(isr);

				String line = lnr.readLine();
				while (line != null) {
					line = line.trim();
					String id = line.substring(0, line.indexOf(" ")).trim();
					String valString = line.substring(line.indexOf(" ")).trim();
					int val = new Integer(valString);

					System.out.println("" + id + ":" + val);

					for (int n=0; n<13; n++) {
						if (n==12) {
							JOptionPane.showMessageDialog(null, "Found unrecognized bias ID " + id + "!", "DVS128", JOptionPane.DEFAULT_OPTION);
							break;
						}
						if (((biasSlider[n].getName()).trim()).equals(id)) {
							biasSlider[n].setValue(val);
							n=14;
						}
					}

					line = lnr.readLine();
				}

				biasFileInStream.close();

			} catch (Exception e) {
				JOptionPane.showMessageDialog(null, "Error Loading Bias Values!", "DVS128", JOptionPane.DEFAULT_OPTION);
			}

		}
	}


	private class FileEndingFilter extends FileFilter {

		private String ending="";

		FileEndingFilter(String fileNameEnding) {
			ending = fileNameEnding;
		}
		public boolean accept(File f) {
			if (f.isDirectory()) {
				return true;
			}

			String fileName = f.getName();

			if ((fileName.toUpperCase()).endsWith(ending) ) {
				return(true);
			}
			return false;
		}

		//The description of this filter
		public String getDescription() {
			if (ending==null) {
				return("directories");
			}
			if (ending=="") {
				return("directories");
			}
			return(ending + "-files");
		}
	}

	private class DVSBiasSlider {
		
		private JSlider biasSlider = new JSlider(0, 0xFFFFFF, 100);
		private JTextField biasText = new JTextField();
		private JTextArea biasName = new JTextArea();

		private final double resolution = 0xFFFFFF;
		private final double logBase = resolution/2.0;

		public int toSlider(int val) {
			int u;
			u = (int) Math.round(resolution * (Math.log((logBase-1)*((double) val)/resolution + 1.0) / Math.log(logBase)) );
			return(u);
		}
		public int toBias(int val) {
			int u;
			u = (int) Math.round(resolution * ((Math.pow(logBase, val/resolution)-1)/(logBase-1)));
			return(u);
		}

		DVSBiasSlider(String name, int value) {
			biasSlider.setValue(toSlider(value));
			biasText.setText(""+value);
			biasName.setText(name);

			ChangeListener sl = new SliderListener();
			ActionListener tl = new TextListener();
			biasSlider.addChangeListener(sl);
			biasText.addActionListener(tl);
		}
		public void display(Container cp, int xpos, int ypos) {
			cp.add(biasSlider);
			biasSlider.setOrientation(JSlider.HORIZONTAL);
			biasSlider.setBounds(xpos, ypos, 400, 20);

			cp.add(biasText);
			biasText.setHorizontalAlignment(JTextField.RIGHT);
			biasText.setBounds(xpos+410, ypos,  70, 20);			

			cp.add(biasName);
			biasName.setBounds(xpos+490, ypos, 140, 20);			
			biasName.setEditable(false);
		}

		public void setValue(int value) {
			biasSlider.setValue(toSlider(value));
			biasText.setText(""+value);
		}
		public int getValue() {
			int s = biasSlider.getValue();
			return(toBias(s));
		}

		public String getName() {
			return(biasName.getText());
		}
		private class SliderListener implements ChangeListener {
			public void stateChanged(ChangeEvent e) {
				int s = biasSlider.getValue();
				int t = toBias(s);
				biasText.setText(""+t);
				
				if (sendBiasImmediately.isSelected()) {
					sendBiasValuesToChip();
				}
			}
		}
		private class TextListener implements ActionListener {
			public void actionPerformed(ActionEvent e) {
				try {
					int t = Integer.parseInt(biasText.getText());
					int s = toSlider(t);
					biasSlider.setValue(s);
					
					if (sendBiasImmediately.isSelected()) {
						sendBiasValuesToChip();
					}

				} catch (Exception e2) { /**/ }
			}
		}
	}
}
