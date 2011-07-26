package net.sf.jaer.hardwareinterface.serial.edvsviewer;

import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JCheckBox;

public abstract class EventProcessor {

	String EventProcessorName;

	JCheckBox isActive = new JCheckBox();
	JButton callBackButton = new JButton();

	EventProcessor() {
		isActive.setText(EventProcessorName);
		callBackButton.setText("call");
		callBackButton.addActionListener(new CallBackListener());
	}
	private class CallBackListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			callBackButtonPressed(e);
		}
	}

	abstract public void init();

	abstract public int processNewEvent(int eventX, int eventY, int eventP);
	abstract public void processSpecialData(String specialData);

	abstract public void paintComponent(Graphics g);

	abstract public void callBackButtonPressed(ActionEvent e);
}
