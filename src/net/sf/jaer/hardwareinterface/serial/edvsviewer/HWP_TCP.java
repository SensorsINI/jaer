
//TODO: add support for setting / reading RTS/CTS, RI, DTR/DSR, etc...

package net.sf.jaer.hardwareinterface.serial.edvsviewer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.util.*;


public class HWP_TCP extends HWPort {

	static final int SOCKET_MAX_DATA_LENGTH = ((int) (1024*1024));

	SocketChannel	socketChannel = null;
	Socket			socket = null;

	private int socketPortNumber;
	private String serverHostname;

	private InetSocketAddress inetSocketAddress = null;
//	private BufferedWriter socketWriter;

	private boolean isConnected = false;

	/* ************************************************************************************** */
	public List<PortIdentifier> getPortIdentifierList() {
		Vector<PortIdentifier> TCPPortList = new Vector<PortIdentifier>();

		TCPPortList.add(new PortIdentifier("192.168.91.101", " INIrwlan 101"));
		TCPPortList.add(new PortIdentifier("192.168.91.102", " INIrwlan 102"));
		TCPPortList.add(new PortIdentifier("192.168.91.103", " INIrwlan 103"));
		TCPPortList.add(new PortIdentifier("192.168.91.104", " INIrwlan 104"));
		TCPPortList.add(new PortIdentifier("192.168.91.105", " INIrwlan 105"));
		TCPPortList.add(new PortIdentifier("192.168.91.106", " INIrwlan 106"));
		TCPPortList.add(new PortIdentifier("192.168.91.107", " INIrwlan 107"));
		TCPPortList.add(new PortIdentifier("192.168.91.108", " INIrwlan 108"));
		TCPPortList.add(new PortIdentifier("--------------", "-------------"));
		TCPPortList.add(new PortIdentifier("192.168.1.1", "  home 1.1"));
		TCPPortList.add(new PortIdentifier("192.168.1.2", "  home 1.2"));
		TCPPortList.add(new PortIdentifier("192.168.1.3", "  home 1.3"));
		TCPPortList.add(new PortIdentifier("192.168.1.4", "  home 1.4"));
		TCPPortList.add(new PortIdentifier("192.168.1.5", "  home 1.5"));
		TCPPortList.add(new PortIdentifier("192.168.1.6", "  home 1.6"));
		TCPPortList.add(new PortIdentifier("192.168.1.7", "  home 1.7"));
		TCPPortList.add(new PortIdentifier("192.168.1.8", "  home 1.8"));
		TCPPortList.add(new PortIdentifier("192.168.1.9", "  home 1.9"));
		TCPPortList.add(new PortIdentifier("192.168.1.10", "  home 1.10"));

		return(TCPPortList);
	}
	public Vector<PortAttribute> getPortAttributeList() {

		Vector<PortAttribute> pa = new Vector<PortAttribute>();

		pa.add(new PortAttribute(new Integer(   80), " Port        :80"));
		pa.add(new PortAttribute(new Integer(64000), " Port :64000"));
		pa.add(new PortAttribute(new Integer(64001), " Port :64001"));
		return(pa);
	}

	/* ************************************************************************************** */
	public HWP_TCP() {
		socketChannel = null;
	}

	private int connect() {
		try {
			socketChannel = SocketChannel.open();		// open initial socket

			socket = socketChannel.socket();			// get socket reference
			socket.setReuseAddress(true);
			socket.setSoTimeout(25);					// timeout in ms
			socket.connect(inetSocketAddress);

			inputStream = new BufferedInputStream(socket.getInputStream());

			outputStream = socket.getOutputStream();
//			socketWriter = new BufferedWriter( new OutputStreamWriter(outputStream) );
			isConnected = true;			/* mark that we are connected */

		} catch (IOException e) {
			return(-1);
		} catch (Exception e2) {
			return(-2);
		}
		return(0);
	}

	public synchronized int open(String portName, PortAttribute pa) {

		serverHostname = portName;
		socketPortNumber = ((Integer) pa.getO());

		inetSocketAddress = new InetSocketAddress(serverHostname, socketPortNumber);

		int c = connect();
		if (c < 0) return(c);

//		setAttribute(pa);

		return(0);	// here we have our port :-)
	}
	public synchronized boolean isOpen() {
		return(isConnected);
	}
	public synchronized int close() {

		try {
			socket.shutdownInput();
			socket.shutdownOutput();
			socket.close();
			socketChannel.close();
		} catch (Exception e) { /**/ }

		socketChannel = null;
		socket = null;
		outputStream = null;
		inputStream = null;

		isConnected = false;
		return(0);
	}

	/* ************************************************************************************** */
	public synchronized int setAttribute(PortAttribute pa) {

		close();
		
		socketPortNumber = ((Integer) pa.getO());

		inetSocketAddress = new InetSocketAddress(serverHostname, socketPortNumber);

		if (connect() < 0) return(-1);
		return(0);
	}

	/* ************************************************************************************** */
	public synchronized int purgeInput() {
		try {
			long s;
			do {
				s = inputStream.skip(10000);
			} while (s>0);
		} catch (IOException e) {return(-1); }
		inputBufferReadPointer = 0;
		inputBufferWritePointer = 0;
		bytesInBuffer = 0;

		return(0);
	}

	/* ************************************************************************************** */
	public synchronized int write(byte[] b) {
		try {
			outputStream.write(b);
		} catch (IOException e) { return(-1); }
		return(0);

//		String header;
//		if (isConnected) {
//		//sprintf(DataLengthString, "\n**%09d\n", DataLengthInBytes);
//		header = "\n**" + number9(messageLength) + "\n";
//		try {
//		writer.write(header, 0, 13);
//		writer.write(messageString, 0, messageLength);
//		writer.flush();
//		} catch (IOException e) {
//		isConnected = false;
//		//e.printStackTrace();
//		}
//		}


	}

	/* ************************************************************************************** */
	protected int updateBuffer() {				// read from serial port
		try {
//			int bytesAvailable = inputStream.available();
//			if (bytesAvailable > 0 ) {

				if ((inputBufferReadPointer < inputBufferWritePointer) || (bytesInBuffer == 0)) {

					int byteCountToFetch = inputBufferSize - inputBufferWritePointer;
//					if (byteCountToFetch > bytesAvailable) {
//						byteCountToFetch = bytesAvailable;
//					}
					int bytesObtained = inputStream.read(inputBuffer, inputBufferWritePointer, byteCountToFetch);
					bytesInBuffer += bytesObtained;
					inputBufferWritePointer += bytesObtained;
					inputBufferWritePointer &= inputBufferMask;

//					if (bytesObtained != byteCountToFetch) {
//						System.out.println("tcp warning: read fewer bytes than anticipated(1): ("+bytesObtained+"/"+byteCountToFetch+")\n");
//						bytesAvailable = 0;		// read fewer bytes than requested. assume no more available
//					} else {
//						bytesAvailable -= bytesObtained;
//					}
				}

//				if (bytesAvailable > 0) {
					if (inputBufferWritePointer < inputBufferReadPointer) {
						int byteCountToFetch = (inputBufferReadPointer - inputBufferWritePointer);
//						if (byteCountToFetch > bytesAvailable) {
//							byteCountToFetch = bytesAvailable;
//						}
						int bytesObtained = inputStream.read(inputBuffer, inputBufferWritePointer, byteCountToFetch);
						bytesInBuffer += bytesObtained;
						inputBufferWritePointer += bytesObtained;
						inputBufferWritePointer &= inputBufferMask;

//						if (bytesObtained != byteCountToFetch) {
//							System.out.println("tcp warning: read fewer bytes than anticipated(2): ("+bytesObtained+"/"+byteCountToFetch+")\n");
//							bytesAvailable = 0;		// read fewer bytes than requested. assume no more available
//						} else {
//							bytesAvailable -= bytesObtained;
//							if (bytesAvailable > 0) {
//								System.out.println("rs232 warning: bytes available but input buffer full\n");
//							}
//						}
					}
//				}	// end of if-bytes-available

//			}	// end of if-bytes-available

		} catch (Exception e) { return(-1); }

		return(0);
	}

}
