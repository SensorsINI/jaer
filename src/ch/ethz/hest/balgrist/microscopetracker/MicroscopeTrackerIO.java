package ch.ethz.hest.balgrist.microscopetracker;

import gnu.io.CommPortIdentifier;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.UnsupportedCommOperationException;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;

public class MicroscopeTrackerIO {

	public MicroscopeTrackerIO() {
	}

	SerialPort serialPort;
	CommPortIdentifier serialPortID;
	OutputStream outputStream;
	Enumeration IDs;

	public boolean openPort(String portName, int baudrate, int dataBits, int stopBits, int parity) {

		boolean foundPort = false;

		IDs = CommPortIdentifier.getPortIdentifiers();
		while (IDs.hasMoreElements()) {
			serialPortID = (CommPortIdentifier) IDs.nextElement();
			if (portName.contentEquals(serialPortID.getName())) {
				foundPort = true;
				System.out.println("port found: " + serialPortID.getName());
				break;
			}
		}

		if (foundPort != true) {
			System.out.println("couldn't find port: " + portName);
			return false;
		}

		try {
			serialPort = (SerialPort) serialPortID.open("open and send", 100);
		}
		catch (PortInUseException e1) {
			System.out.println("Port already in use");
		}

		try {
			outputStream = serialPort.getOutputStream();
		}
		catch (IOException e1) {
			System.out.println("No access to output stream");
		}

		try {
			serialPort.setSerialPortParams(baudrate, dataBits, stopBits, parity);
		}
		catch (UnsupportedCommOperationException e) {
			System.out.println("couldn't set parameters");
		}

		System.out.println("Port opened");
		return true;
	}

	public void closePort() {
		serialPort.close();
		System.out.println("port closed");
	}

	public boolean sendCommand(String command) {
		try {
			outputStream.write(command.getBytes());
			System.out.println("sending command: " + command);
		} catch (IOException e) {
			System.out.println("Error while sending command: " + command);
		}
		return false;
	}

}
