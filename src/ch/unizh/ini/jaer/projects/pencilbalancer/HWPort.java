package ch.unizh.ini.jaer.projects.pencilbalancer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public abstract class HWPort {

	final char START_BINARY_MODE	= ((byte) 01);
	final char NEW_LINE				= ((byte) 10);
	final char CARRIAGE_RETURN		= ((byte) 13);
	final int  TIMEOUT_DELAY_IN_MS	= 20;
	final String CHAR_SET			= "ISO-8859-1";
	
	protected OutputStream outputStream = null;
	protected InputStream inputStream = null;

	protected final int inputBufferSize = (1<<14);		// 16kbytes (must be power of 2)
	protected final int inputBufferMask = (inputBufferSize-1);
	protected int inputBufferReadPointer = 0;
	protected int inputBufferWritePointer = 0;
	protected int bytesInBuffer;
	protected byte[] inputBuffer = new byte[inputBufferSize];


	public class PortIdentifier {
		Object id;
		public String display;
		PortIdentifier(Object id, String display) {
			this.id=id;
			this.display=display;
		}
		public String toString() {
			return(display);
		}
		public Object getID() {
			return(id);
		}
	}
	abstract public List<PortIdentifier> getPortIdentifierList();
	public class PortAttribute {
		Object o;
		String display;
		public PortAttribute(Object o, String display) {
			this.o=o;
			this.display=display;
		}
		public String toString() {
			return(display);
		}
		public Object getO() {
			return(o);
		}
	}
	abstract public List<PortAttribute> getPortAttributeList();

	abstract public int open(String portName, PortAttribute pa);
	abstract public boolean isOpen();
	abstract public int close();

	abstract public int setAttribute(PortAttribute pa);

	public synchronized int purgeInput() {
		try {
			while (inputStream.available() > 0) {
				inputStream.skip(inputStream.available());
			}
		} catch (IOException e) {return(-1); }
		inputBufferReadPointer = 0;
		inputBufferWritePointer = 0;
		bytesInBuffer = 0;

		return(0);
	}
	public synchronized int flushOutput() {
		try {
			outputStream.flush();
		} catch (Exception e) {return (-1);}
		return(0);
	}

	abstract public int write(byte[] b);
	public synchronized int writeChar(char c) {
		try {
			return(write((""+c).getBytes(CHAR_SET)));
		} catch (Exception e) {
			return(-1);
		}
	}
	public synchronized int write(String messageString) {
		try {
			return(write(messageString.getBytes(CHAR_SET)));
		} catch (Exception e) {
			return(-1);
		}
	}
	public synchronized int writeLn(String messageString) {
		return(this.write(messageString + '\n'));
	}

	abstract protected int updateBuffer();

	public synchronized boolean bytesAvailable() {
		return(inputBufferReadPointer != inputBufferWritePointer);
	}
	public synchronized int byteCountAvailable() {
		return(bytesInBuffer);
	}

	public synchronized byte[] readExactBytes(int byteCount) {
		updateBuffer();
		if (bytesInBuffer < byteCount) {
			return(null);
		}

		byte[] b = new byte[byteCount];
		int p=0;
		bytesInBuffer -= byteCount;
		for (;byteCount!=0; byteCount--) {
			b[p++] = inputBuffer[inputBufferReadPointer];
			inputBufferReadPointer = (inputBufferReadPointer+1) & inputBufferMask;
		}
		return(b);
	}
	
	public synchronized byte[] readBytes(int maxBytes) {
		updateBuffer();
		if (bytesInBuffer < maxBytes) {
			return(readExactBytes(bytesInBuffer));
		}
		return(readExactBytes(maxBytes));
	}

	private Integer findEndOfLine() {
		if (bytesInBuffer==0) return(null);
		int t=0;
		int p=inputBufferReadPointer;
		do {
			if (inputBuffer[p] == START_BINARY_MODE) {
//System.out.println("BinaryStart bytesInBuffer: " + bytesInBuffer);
				if ((bytesInBuffer-t) < 5) { return(null); }	// at least (this + 2-bytes-count + 1-byte-data) = 4bytes

				int b1 = inputBuffer[((p+1) & inputBufferMask)]; if (b1<0) b1=256+b1;
				int b2 = inputBuffer[((p+2) & inputBufferMask)]; if (b2<0) b2=256+b2;
				int binaryCount = (b1<<8) + b2;
//System.out.println("BinaryStart binaryCount: " + binaryCount);

				if ((bytesInBuffer-t) < (4+binaryCount)) { return(null); }	// binary data complete + at least one other char?

//System.out.println("Found " + binaryCount + " bytes of binary data");

				// binary data complete in memory. Great!
//				containsBinaryData = true;

				t += (3+binaryCount);
				p = (p+3+binaryCount) & inputBufferMask;

			} else {

				if (inputBuffer[p] == NEW_LINE) break;
				if (inputBuffer[p] == CARRIAGE_RETURN) break;
				p = (p+1) & inputBufferMask;
				t++;
			}
		} while (t<bytesInBuffer);

		if (t==bytesInBuffer) return(null);
		t++;	// this number of bytes to get

		return(t);
	}
	private byte[] extractByteLine() {
		Integer eol = findEndOfLine();
		if (eol==null) { return(null); }

		byte []b = readExactBytes(eol);

		while ((bytesInBuffer>0) &&
				  ((inputBuffer[inputBufferReadPointer] == NEW_LINE) || (inputBuffer[inputBufferReadPointer] == CARRIAGE_RETURN))) {
				inputBufferReadPointer = (inputBufferReadPointer+1) & inputBufferMask;
				bytesInBuffer--;
		}

		return(b);
	}
	private String extractLine() {

		Integer eol = findEndOfLine();
		if (eol==null) { return(null); }

		String l=null;

		try {
			if ((inputBufferReadPointer+eol) < inputBufferSize) {
				l = new String(inputBuffer, inputBufferReadPointer, eol, CHAR_SET);
			} else {
				int diff = inputBufferSize-inputBufferReadPointer;
				l = new String(inputBuffer, inputBufferReadPointer, diff, CHAR_SET) + new String(inputBuffer, 0, eol-diff, CHAR_SET);
			}
		} catch (Exception doesNotOccur) {/**/}

		bytesInBuffer -= eol;
		inputBufferReadPointer = (inputBufferReadPointer+eol) & inputBufferMask;

		while ((bytesInBuffer>0) &&
			  ((inputBuffer[inputBufferReadPointer] == NEW_LINE) || (inputBuffer[inputBufferReadPointer] == CARRIAGE_RETURN))) {
			inputBufferReadPointer = (inputBufferReadPointer+1) & inputBufferMask;
			bytesInBuffer--;
		}

		return(l);
	}

	private String getAllCurrentDataLine() {
		String l = null;
		if (bytesInBuffer > 0) {
			try {
				l=new String(readExactBytes(bytesInBuffer), CHAR_SET);
			} catch (Exception e) { /**/ }
		}
		return(l);
	}
	
	public synchronized byte[] readByteLine() {
		byte []b;
		b = extractByteLine();		// check if line already in buffer
		if (b==null) {				//  if not
			updateBuffer();			//    fetch new input
			b = extractByteLine();	//    and try again
		}
		return(b);
	}
	public synchronized byte[] readByteLine(int timeOutMS) {
		byte []b;
		do {
			b=readByteLine();
			if (b != null) return(b);
			try {
				Thread.sleep(TIMEOUT_DELAY_IN_MS);
			} catch (Exception e) {/**/}
			timeOutMS -= TIMEOUT_DELAY_IN_MS;
		} while (timeOutMS>0);
		return(null);
	}

	public synchronized String readLine() {
		String l;
		l = extractLine();			// check if line already in buffer
		if (l==null) {				//  if not
			updateBuffer();			//    fetch new input
			l = extractLine();		//    and try again
		}
		return(l);
	}
	public synchronized String readLine(int timeOutMS) {
		String l;
		do {
			l=readLine();
			if (l != null) return(l);
			try {
				Thread.sleep(TIMEOUT_DELAY_IN_MS);
			} catch (Exception e) {/**/}
			timeOutMS -= TIMEOUT_DELAY_IN_MS;
		} while (timeOutMS>0);
		return(null);
	}
	public synchronized String readLineFraction(int timeOutMS) {

		String l;
		l = readLine(timeOutMS);
		if (l==null) {	// here return whatever we have, in case we have anything
			l=getAllCurrentDataLine();
		}
		return(l);
	}


	public boolean sendCommand(String command) {
		if (write(command + '\n') < 0) { return (false); }
		return(true);
	}	
	public boolean sendCommand(String command, String expectedReturn, int timeOut) {
		if (write(command + '\n') < 0) { return (false); }

		for (int retryCounter=8; retryCounter>0; retryCounter--) {
			if ((readLine(timeOut)).equals(expectedReturn)) {
				return(true);
			}
		}
		return(false);
	}	
	public boolean sendCommand(String command, String expectedReturn) {
		return(sendCommand(command, expectedReturn, 200));		// assume default time out of 200ms
	}
}
