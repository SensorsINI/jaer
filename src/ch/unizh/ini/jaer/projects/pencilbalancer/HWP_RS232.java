
// TODO: add support for setting / reading RTS/CTS, RI, DTR/DSR, etc...

package ch.unizh.ini.jaer.projects.pencilbalancer;

import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;

import java.io.IOException;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;

public class HWP_RS232 extends HWPort {

	private boolean singleCharFlushTransferFlag = false;
	protected SerialPort serialPort;

/* ************************************************************************************** */
	@SuppressWarnings("unchecked")
	public List<PortIdentifier> getPortIdentifierList() {
		CommPortIdentifier portId;
		Vector<PortIdentifier> serPortList = new Vector<PortIdentifier>();
		Enumeration<CommPortIdentifier> portList = CommPortIdentifier.getPortIdentifiers();

		while (portList.hasMoreElements()) {
			portId = (CommPortIdentifier) portList.nextElement();

			if (portId.getPortType() == CommPortIdentifier.PORT_SERIAL) {
				serPortList.add(new PortIdentifier(portId.getName(), "  "+portId.getName()));
			}
		}
		serPortList.add(new PortIdentifier("-rescan-", "-rescan-"));

		return(serPortList);
	}
	public Vector<PortAttribute> getPortAttributeList() {

		Vector<PortAttribute> pa = new Vector<PortAttribute>();

		pa.add(new PortAttribute(new Integer(3000000), "  3000000Bd"));
		pa.add(new PortAttribute(new Integer(2764800), "  2764800Bd"));
		pa.add(new PortAttribute(new Integer(2000000), "  2000000Bd"));
		pa.add(new PortAttribute(new Integer(1843200), "  1843200Bd"));
		pa.add(new PortAttribute(new Integer(1000000), "  1000000Bd"));
		pa.add(new PortAttribute(new Integer( 921600), "    921600Bd"));
		pa.add(new PortAttribute(new Integer( 500000), "    500000Bd"));
		pa.add(new PortAttribute(new Integer( 460800), "    460800Bd"));
		pa.add(new PortAttribute(new Integer( 250000), "    250000Bd"));
		pa.add(new PortAttribute(new Integer( 230400), "    230400Bd"));
		pa.add(new PortAttribute(new Integer( 115200), "    115200Bd"));
		pa.add(new PortAttribute(new Integer(  57600), "      57600Bd"));
		pa.add(new PortAttribute(new Integer(  38400), "      38400Bd"));
		pa.add(new PortAttribute(new Integer(  19200), "      19200Bd"));
		pa.add(new PortAttribute(new Integer(   9600), "        9600Bd"));
		pa.add(new PortAttribute(new Integer(   2400), "        2400Bd"));
		pa.add(new PortAttribute(new Integer(   1200), "        1200Bd"));
		pa.add(new PortAttribute(new Integer(    300), "          300Bd"));
		
		return(pa);
	}

/* ************************************************************************************** */
	public HWP_RS232() {
		serialPort = null;

		singleCharFlushTransferFlag = false;
	}
	@SuppressWarnings("unchecked")
	public synchronized int open(String portName, PortAttribute pa) {
		CommPortIdentifier portId;
		Enumeration<CommPortIdentifier> portList;

		boolean portFoundFlag = false;

		if (isOpen()) {
			close();
		}

		portList = CommPortIdentifier.getPortIdentifiers();
		while ((portList.hasMoreElements()) && (portFoundFlag == false)) {

			portId = (CommPortIdentifier) portList.nextElement();
			if (portId.getPortType() == CommPortIdentifier.PORT_SERIAL) {
				if (portId.getName().equals(portName)) {

					portFoundFlag = true;

					try {		// try opening the port, wait at most 50ms to get port
						serialPort = (SerialPort) portId.open("JavaRS232Port", 50);
					} catch (Exception e) { return(-1); }

					setAttribute(pa);

					try {		// get output and input stream
						outputStream = serialPort.getOutputStream();
						inputStream = serialPort.getInputStream();
					} catch (Exception e) {return(-4); }

				}		// end of equals portName
			}		// end of IsSerialPort
		}		// end of moreElements

		if (portFoundFlag==false) {
			return(-4);
		}
		return(0);	// here we have our port :-)
	}
	public synchronized boolean isOpen() {
		return(serialPort != null);
	}
	public synchronized int close() {
		try {		// close streams
			inputStream.close();
			outputStream.close();
		} catch (Exception e) {return(-1); }
		serialPort.close();
	
		serialPort = null;
		outputStream = null;
		inputStream = null;
		return(0);
	}

/* ************************************************************************************** */
	public synchronized int setAttribute(PortAttribute pa) {
		int baudRate = (Integer) pa.getO();
		return(setBaudRate(baudRate));
	}

/* ************************************************************************************** */
	public synchronized int setBaudRate(int baudRate) {
		if (isOpen()) {

			setSingleCharFlushTransfer(false);
			if (baudRate == 3000000) {
				setSingleCharFlushTransfer(true);
			}
			try {		// set serial port parameters
				serialPort.setSerialPortParams(baudRate,
						SerialPort.DATABITS_8,
						SerialPort.STOPBITS_1,
						SerialPort.PARITY_NONE);
			} catch (Exception e) {return(-2); }
			return(0);
		}
		return(-1);
	}
	public synchronized int setHardwareFlowControl(boolean flowControlFlag) {
		if (isOpen()) {
			try {
				if (flowControlFlag) {
					serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_RTSCTS_OUT | SerialPort.FLOWCONTROL_RTSCTS_IN);
				} else {
					serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);
				}
			} catch (Exception e) { return(-2); }
			return(0);
		}
		return(-1);
	}
	public synchronized void setSingleCharFlushTransfer(boolean singleCharFlushTransferFlag) {
		this.singleCharFlushTransferFlag = singleCharFlushTransferFlag;
	}

/* ************************************************************************************** */
	public synchronized int write(byte[] b) {
		try {
			if (singleCharFlushTransferFlag) {
				for (int n=0; n<b.length; n++) {
					outputStream.write(b[n]);
					outputStream.flush();
				}
			} else {
				outputStream.write(b);
			}
		} catch (IOException e) { return(-1); }
		return(0);
	}

/* ************************************************************************************** */
	protected int updateBuffer() {				// read from serial port
		try {
			int bytesAvailable = inputStream.available();

			if (bytesAvailable > 0 ) {

				if ((inputBufferReadPointer < inputBufferWritePointer) || (bytesInBuffer == 0)) {
					int byteCountToFetch = inputBufferSize - inputBufferWritePointer;
					if (byteCountToFetch > bytesAvailable) {
						byteCountToFetch = bytesAvailable;
					}
					int bytesObtained = inputStream.read(inputBuffer, inputBufferWritePointer, byteCountToFetch);
					bytesInBuffer += bytesObtained;
					inputBufferWritePointer += bytesObtained;
					inputBufferWritePointer &= inputBufferMask;

					if (bytesObtained != byteCountToFetch) {
						System.out.println("rs232 warning: read fewer bytes than anticipated(1): ("+bytesObtained+"/"+byteCountToFetch+")\n");
						bytesAvailable = 0;		// read fewer bytes than requested. assume no more available
					} else {
						bytesAvailable -= bytesObtained;
					}
				}

				if (bytesAvailable > 0) {
					if (inputBufferWritePointer < inputBufferReadPointer) {
						int byteCountToFetch = (inputBufferReadPointer - inputBufferWritePointer);
						if (byteCountToFetch > bytesAvailable) {
							byteCountToFetch = bytesAvailable;
						}
						int bytesObtained = inputStream.read(inputBuffer, inputBufferWritePointer, byteCountToFetch);
						bytesInBuffer += bytesObtained;
						inputBufferWritePointer += bytesObtained;
						inputBufferWritePointer &= inputBufferMask;

						if (bytesObtained != byteCountToFetch) {
							System.out.println("rs232 warning: read fewer bytes than anticipated(2): ("+bytesObtained+"/"+byteCountToFetch+")\n");
							bytesAvailable = 0;		// read fewer bytes than requested. assume no more available
						} else {
							bytesAvailable -= bytesObtained;
							if (bytesAvailable > 0) {
								System.out.println("rs232 warning: bytes available but input buffer full\n");
							}
						}
					}
				}	// end of if-bytes-available

			}	// end of if-bytes-available

		} catch (Exception e) { return(-1); }

		return(0);
	}
}
