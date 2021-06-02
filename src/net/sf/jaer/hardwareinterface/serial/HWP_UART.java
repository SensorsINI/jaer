package net.sf.jaer.hardwareinterface.serial;

import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import gnu.io.UnsupportedCommOperationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Enumeration;
import java.util.logging.Logger;

import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * Hardware port serial.
 *
 * @author jorg conradt
 */
public class HWP_UART implements HardwareInterface {

    static Logger log = Logger.getLogger("HWP_UART");
    protected SerialPort serialPort;
    final char START_BINARY_MODE = ((byte) 01);
    final char NEW_LINE = ((byte) 10);
    final char CARRIAGE_RETURN = ((byte) 13);
    final int TIMEOUT_DELAY_IN_MS = 20;
    final String CHAR_SET = "ISO-8859-1";
    protected OutputStream outputStream = null;
    protected InputStream inputStream = null;
    protected final int inputBufferSize = (1 << 14);		// 16kbytes (must be power of 2)
    protected final int inputBufferMask = (inputBufferSize - 1);
    protected int inputBufferReadPointer = 0;
    protected int inputBufferWritePointer = 0;
    protected int bytesInBuffer;
    protected byte[] inputBuffer = new byte[inputBufferSize];

    public synchronized void flushOutput() throws IOException {
        outputStream.flush();
    }

    public synchronized void writeChar(char c) throws UnsupportedEncodingException, IOException {
        write(("" + c).getBytes(CHAR_SET));
    }

    public synchronized void write(String messageString) throws UnsupportedEncodingException, IOException {
        write(messageString.getBytes(CHAR_SET));
    }

    public synchronized void writeLn(String messageString) throws UnsupportedEncodingException, IOException {
        write(messageString + '\n');
    }

    public synchronized boolean bytesAvailable() {
        return (inputBufferReadPointer != inputBufferWritePointer);
    }

    public synchronized int byteCountAvailable() {
        return (bytesInBuffer);
    }

    public synchronized byte[] readExactBytes(int byteCount) {
        updateBuffer();
        if (bytesInBuffer < byteCount) {
            return (null);
        }

        byte[] b = new byte[byteCount];
        int p = 0;
        bytesInBuffer -= byteCount;
        for (; byteCount != 0; byteCount--) {
            b[p++] = inputBuffer[inputBufferReadPointer];
            inputBufferReadPointer = (inputBufferReadPointer + 1) & inputBufferMask;
        }
        return (b);
    }

    public synchronized byte[] readBytes(int maxBytes) {
        updateBuffer();
        if (bytesInBuffer < maxBytes) {
            return (readExactBytes(bytesInBuffer));
        }
        return (readExactBytes(maxBytes));
    }

    private Integer findEndOfLine() {
        if (bytesInBuffer == 0) {
            return (null);
        }
        int t = 0;
        int p = inputBufferReadPointer;
        do {
            if (inputBuffer[p] == START_BINARY_MODE) {
//System.out.println("BinaryStart bytesInBuffer: " + bytesInBuffer);
                if ((bytesInBuffer - t) < 5) {
                    return (null);
                }	// at least (this + 2-bytes-count + 1-byte-data) = 4bytes

                int b1 = inputBuffer[((p + 1) & inputBufferMask)];
                if (b1 < 0) {
                    b1 = 256 + b1;
                }
                int b2 = inputBuffer[((p + 2) & inputBufferMask)];
                if (b2 < 0) {
                    b2 = 256 + b2;
                }
                int binaryCount = (b1 << 8) + b2;
//System.out.println("BinaryStart binaryCount: " + binaryCount);

                if ((bytesInBuffer - t) < (4 + binaryCount)) {
                    return (null);
                }	// binary data complete + at least one other char?

//System.out.println("Found " + binaryCount + " bytes of binary data");

                // binary data complete in memory. Great!
//				containsBinaryData = true;

                t += (3 + binaryCount);
                p = (p + 3 + binaryCount) & inputBufferMask;

            } else {

                if (inputBuffer[p] == NEW_LINE) {
                    break;
                }
                if (inputBuffer[p] == CARRIAGE_RETURN) {
                    break;
                }
                p = (p + 1) & inputBufferMask;
                t++;
            }
        } while (t < bytesInBuffer);

        if (t == bytesInBuffer) {
            return (null);
        }
        t++;	// this number of bytes to get

        return (t);
    }

    private byte[] extractByteLine() {
        Integer eol = findEndOfLine();
        if (eol == null) {
            return (null);
        }

        byte[] b = readExactBytes(eol);

        while ((bytesInBuffer > 0)
                && ((inputBuffer[inputBufferReadPointer] == NEW_LINE) || (inputBuffer[inputBufferReadPointer] == CARRIAGE_RETURN))) {
            inputBufferReadPointer = (inputBufferReadPointer + 1) & inputBufferMask;
            bytesInBuffer--;
        }

        return (b);
    }

    private String extractLine() {

        Integer eol = findEndOfLine();
        if (eol == null) {
            return (null);
        }

        String l = null;

        try {
            if ((inputBufferReadPointer + eol) < inputBufferSize) {
                l = new String(inputBuffer, inputBufferReadPointer, eol, CHAR_SET);
            } else {
                int diff = inputBufferSize - inputBufferReadPointer;
                l = new String(inputBuffer, inputBufferReadPointer, diff, CHAR_SET) + new String(inputBuffer, 0, eol - diff, CHAR_SET);
            }
        } catch (Exception doesNotOccur) {/*
             * 
             */

        }

        bytesInBuffer -= eol;
        inputBufferReadPointer = (inputBufferReadPointer + eol) & inputBufferMask;

        while ((bytesInBuffer > 0)
                && ((inputBuffer[inputBufferReadPointer] == NEW_LINE) || (inputBuffer[inputBufferReadPointer] == CARRIAGE_RETURN))) {
            inputBufferReadPointer = (inputBufferReadPointer + 1) & inputBufferMask;
            bytesInBuffer--;
        }

        return (l);
    }

    public synchronized String getAllDataInBuffer() {
        String l = null;
        if (bytesInBuffer > 0) {
            try {
                l = new String(readExactBytes(bytesInBuffer), CHAR_SET);
            } catch (Exception e) { /*
                 * 
                 */ }
        }
        return (l);
    }

    public synchronized String getAllData() {
        updateBuffer();
        return (getAllDataInBuffer());
    }

    public synchronized byte[] readByteLine() {
        byte[] b;
        b = extractByteLine();		// check if line already in buffer
        if (b == null) {				//  if not
            updateBuffer();			//    fetch new input
            b = extractByteLine();	//    and try again
        }
        return (b);
    }

    public synchronized byte[] readByteLine(int timeOutMS) {
        byte[] b;
        do {
            b = readByteLine();
            if (b != null) {
                return (b);
            }
            try {
                Thread.sleep(TIMEOUT_DELAY_IN_MS);
            } catch (Exception e) {/*
                 * 
                 */

            }
            timeOutMS -= TIMEOUT_DELAY_IN_MS;
        } while (timeOutMS > 0);
        return (null);
    }

    public synchronized String readLine() {
        String l;
        l = extractLine();			// check if line already in buffer
        if (l == null) {				//  if not
            updateBuffer();			//    fetch new input
            l = extractLine();		//    and try again
        }
        return (l);
    }

    public synchronized String readLine(int timeOutMS) {
        String l;
        do {
            l = readLine();
            if (l != null) {
                return (l);
            }
            try {
                Thread.sleep(TIMEOUT_DELAY_IN_MS);
            } catch (Exception e) {/*
                 * 
                 */

            }
            timeOutMS -= TIMEOUT_DELAY_IN_MS;
        } while (timeOutMS > 0);
        return (null);
    }

    public synchronized String readLineFraction(int timeOutMS) {

        String l;
        if (timeOutMS > 0) {
            l = readLine(timeOutMS);
        } else {
            l = readLine();
        }
        if (l == null) {	// here return whatever we have, in case we have anything
            l = getAllDataInBuffer();
        }
        return (l);
    }

    public void sendCommand(String command) throws UnsupportedEncodingException, IOException {
        write(command + '\n');
    }

    public boolean sendCommand(String command, String expectedReturn, int timeOut) throws UnsupportedEncodingException, IOException {
        write(command + '\n');
//
//        for (int retryCounter = 8; retryCounter > 0; retryCounter--) {
            String line=readLine(timeOut);
            if(line.endsWith("\n")) line=line.substring(0, line.length()-1);
            
            if ((line==null && expectedReturn==null)|| (line!=null && line.equals(expectedReturn))) {
                return (true);
            }
//        }
            log.warning("sent "+command+", read "+line+" expected "+expectedReturn);
        return false;
    }

    public boolean sendCommand(String command, String expectedReturn) throws UnsupportedEncodingException, IOException {
        return (sendCommand(command, expectedReturn, 10));		// assume default time out of 200ms
    }

    /*
     * **************************************************************************************
     */
    @SuppressWarnings("unchecked")
    public void showPortList() {
        CommPortIdentifier portId;
        Enumeration<CommPortIdentifier> portList = CommPortIdentifier.getPortIdentifiers();

        while (portList.hasMoreElements()) {
            portId = (CommPortIdentifier) portList.nextElement();
            System.out.println("Port: " + portId);
        }

    }

    /*
     * **************************************************************************************
     */
    public HWP_UART() {
        serialPort = null;
    }

    @SuppressWarnings("unchecked")
    public synchronized int open(String portName, int baudRate) throws UnsupportedCommOperationException, IOException {
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
                        serialPort.enableReceiveThreshold(256);
                        serialPort.setOutputBufferSize(1024);

                        setBaudRate(baudRate);
                    } catch (Exception e) {
                        log.warning("opening port, caught exception "+ e.toString());
                        return (-1);
                    }



                    outputStream = serialPort.getOutputStream();
                    inputStream = serialPort.getInputStream();
                    log.info("port " + portName + " opened with baudRate=" + baudRate);

                }		// end of equals portName
            }		// end of IsSerialPort
        }		// end of moreElements

        if (portFoundFlag == false) {
            throw new IOException("com port " + portName + " not found");
        }
        return (0);	// here we have our port :-)
    }

    public synchronized boolean isOpen() {
        return (serialPort != null);
    }

    public synchronized void close() {
        if (!isOpen()) {
            return;
        }
        try {		// close streams
            inputStream.close();
            outputStream.close();
        } catch (Exception e) {
            log.warning(e.toString());
        }
        serialPort.close();

        serialPort = null;
        outputStream = null;
        inputStream = null;
        return;
    }

    /*
     * **************************************************************************************
     */
    public synchronized void setBaudRate(int baudRate) throws UnsupportedCommOperationException {
        if (isOpen()) {
            serialPort.setSerialPortParams(baudRate,
                    SerialPort.DATABITS_8,
                    SerialPort.STOPBITS_1,
                    SerialPort.PARITY_NONE);
        }
    }

    public synchronized void setHardwareFlowControl(boolean flowControlFlag) throws UnsupportedCommOperationException {
        if (isOpen()) {
            if (flowControlFlag) {
                serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_RTSCTS_OUT | SerialPort.FLOWCONTROL_RTSCTS_IN);
                serialPort.setRTS(true);
                log.info("Set HW flow control on!");
            } else {
                serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);
                log.info("Set HW flow control off!");
            }
        }
    }

    /*
     * **************************************************************************************
     */
    public synchronized int purgeInput() {
        try {
            while (inputStream.available() > 0) {
                inputStream.skip(inputStream.available());
            }
        } catch (IOException e) {
            return (-1);
        }
        inputBufferReadPointer = 0;
        inputBufferWritePointer = 0;
        bytesInBuffer = 0;

        return (0);
    }

    /*
     * **************************************************************************************
     */
    public synchronized void write(byte[] b) throws IOException {
        if (outputStream == null) {
            throw new IOException("null output stream; port not opened?");
        }
        outputStream.write(b);
    }

    /*
     * **************************************************************************************
     */
    protected int updateBuffer() {				// read from serial port
        try {
            int bytesAvailable = inputStream.available();

            if (bytesAvailable > 0) {

                if ((inputBufferReadPointer < inputBufferWritePointer) || (bytesInBuffer == 0)) {
                    int byteCountToFetch = inputBufferSize - inputBufferWritePointer;
                    if (byteCountToFetch > bytesAvailable) {
                        byteCountToFetch = bytesAvailable;
                    }
                    int bytesObtained = inputStream.read(inputBuffer, inputBufferWritePointer, byteCountToFetch);
                    bytesInBuffer += bytesObtained;
                    inputBufferWritePointer += bytesObtained;
                    inputBufferWritePointer &= inputBufferMask;

//					if (bytesObtained != byteCountToFetch) {
//						System.out.println("rs232 warning: read fewer bytes than anticipated(1): ("+bytesObtained+"/"+byteCountToFetch+")\n");
//						bytesAvailable = 0;		// read fewer bytes than requested. assume no more available
//					} else {
                    bytesAvailable -= bytesObtained;
//					}
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

//						if (bytesObtained != byteCountToFetch) {
//							System.out.println("rs232 warning: read fewer bytes than anticipated(2): ("+bytesObtained+"/"+byteCountToFetch+")\n");
//							bytesAvailable = 0;		// read fewer bytes than requested. assume no more available
//						} else {
                        bytesAvailable -= bytesObtained;
//							if (bytesAvailable > 0) {
//								System.out.println("rs232 warning: bytes available but input buffer full\n");
//							}
//						}
                    }
                }	// end of if-bytes-available

            }	// end of if-bytes-available

        } catch (Exception e) {
            return (-1);
        }

        return (0);
    }

    @Override
    public String getTypeName() {
        return "Serial Port";
    }

    @Override
    public void open() throws HardwareInterfaceException {
        throw new UnsupportedOperationException("Not supported - use the open(port,baudrate) method.");
    }
}
