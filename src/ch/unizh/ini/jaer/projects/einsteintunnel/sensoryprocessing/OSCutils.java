/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.einsteintunnel.sensoryprocessing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Date;
import java.util.Enumeration;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * OSCutils is a collection of functions which are helpful for handling OSC in java.
 * They manly originate from the javaOSC project.
 */
public class OSCutils {
    
    protected DatagramSocket socket;
    public int port;
    public InetAddress address;

    /**
     * 2208988800 seconds -- includes 17 leap years
     */
    public static final BigInteger SECONDS_FROM_1900_to_1970 =
            new BigInteger("2208988800");

    /**
     * The Java representation of an OSC timestamp with the semantics of "immediately"
     */		
    public static final Date TIMESTAMP_IMMEDIATE = new Date(0);


    public OSCutils(InetAddress address, int port){
        if (address == null){
            Logger.getLogger(OSCutils.class.getName()).log(Level.SEVERE, null, "No UDP address has bee specified.");
        }
        this.port = port;
        this.address = address;
        try {
            socket = new DatagramSocket();
        } catch (SocketException ex) {
            Logger.getLogger(OSCutils.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void sendMessage(String destination, Object[] args){
        OSCMessage msg = new OSCMessage(destination, args);
        byte[] byteArray = msg.getByteArray();
        DatagramPacket packet = new DatagramPacket(byteArray, byteArray.length, address, port);
        try {
            socket.send(packet);
        } catch (IOException ex) {
            Logger.getLogger(OSCutils.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void sendBundle(OSCBundle bundle){
        byte[] byteArray = bundle.getByteArray();
        DatagramPacket packet = new DatagramPacket(byteArray, byteArray.length, address, port);
        try {
            socket.send(packet);
        } catch (IOException ex) {
            Logger.getLogger(OSCutils.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * A bundle represents a collection of osc packets (either messages or other bundles) and
     * has a timetag which can be used by a scheduler to execute a bundle in the future instead
     * of immediately (OSCMessages are executed immediately). Bundles should be used if you want
     * to send multiple messages to be executed atomically together, or you want to schedule one
     * or more messages to be executed in the future.
     * <p>
     * Internally, I use Vector to maintain jdk1.1 compatability
     * <p>
     * Copyright (C) 2003-2006, C. Ramakrishnan / Illposed Software.
     * All rights reserved.
     * <p>
     * See license.txt (or license.rtf) for license information.
     *
     * @author Chandrasekhar Ramakrishnan
     * @version 1.0
     */
    public class OSCBundle extends OSCPacket {

	protected Date timestamp;
	protected Vector packets;

	/**
	 * Create a new empty OSCBundle with a timestamp of immediately.
	 * You can add packets to the bundle with addPacket()
	 */
	public OSCBundle() {
		this(null, TIMESTAMP_IMMEDIATE);
	}

	/**
	 * Create an OSCBundle with the specified timestamp.
	 * @param timestamp the time to execute the bundle
	 */
	public OSCBundle(Date timestamp) {
		this(null, timestamp);
	}

	/**
	 * Create an OSCBundle made up of the given packets with a timestamp of now.
	 * @param packets array of OSCPackets to initialize this object with
	 */
	public OSCBundle(OSCPacket[] packets) {
		this(packets, TIMESTAMP_IMMEDIATE);
	}

	/**
	 * Create an OSCBundle, specifying the packets and timestamp.
	 * @param packets the packets that make up the bundle
	 * @param timestamp the time to execute the bundle
	 */
	public OSCBundle(OSCPacket[] packets, Date timestamp) {
		super();
		if (null != packets) {
			this.packets = new Vector(packets.length);
			for (int i = 0; i < packets.length; i++) {
				this.packets.add(packets[i]);
			}
		} else
			this.packets = new Vector();
		this.timestamp = timestamp;
		init();
	}

	/**
	 * Return the time the bundle will execute.
	 * @return a Date
	 */
	public Date getTimestamp() {
		return timestamp;
	}

	/**
	 * Set the time the bundle will execute.
	 * @param timestamp Date
	 */
	public void setTimestamp(Date timestamp) {
		this.timestamp = timestamp;
	}

	/**
	 * Add a packet to the list of packets in this bundle.
	 * @param packet OSCMessage or OSCBundle
	 */
	public void addPacket(OSCPacket packet) {
		packets.add(packet);
	}

	/**
	 * Get the packets contained in this bundle.
	 * @return an array of packets
	 */
	public OSCPacket[] getPackets() {
		OSCPacket[] packetArray = new OSCPacket[packets.size()];
		packets.toArray(packetArray);
		return packetArray;
	}

	/**
	 * Convert the timetag (a Java Date) into the OSC byte stream. Used Internally.
	 */
	protected void computeTimeTagByteArray(OSCJavaToByteArrayConverter stream) {
		if ((null == timestamp) || (timestamp == TIMESTAMP_IMMEDIATE)) {
			stream.write((int) 0);
			stream.write((int) 1);
			return;
		}

		long millisecs = timestamp.getTime();
		long secsSince1970 = (long) (millisecs / 1000);
		long secs = secsSince1970 + SECONDS_FROM_1900_to_1970.longValue();
			// the next line was cribbed from jakarta commons-net's NTP TimeStamp code
		long fraction = ((millisecs % 1000) * 0x100000000L) / 1000;

		stream.write((int) secs);
		stream.write((int) fraction);
	}

	/**
	 * Compute the OSC byte stream representation of the bundle. Used Internally.
	 * @param stream OscPacketByteArrayConverter
	 */
	protected void computeByteArray(OSCJavaToByteArrayConverter stream) {
		stream.write("#bundle");
		computeTimeTagByteArray(stream);
		Enumeration enumer = packets.elements();
		OSCPacket nextElement;
		byte[] packetBytes;
		while (enumer.hasMoreElements()) {
			nextElement = (OSCPacket) enumer.nextElement();
			packetBytes = nextElement.getByteArray();
			stream.write(packetBytes.length);
			stream.write(packetBytes);
		}
		byteArray = stream.toByteArray();
	}
    }


    /**
     * An simple (non-bundle) OSC message. An OSC message is made up of
     * an address (the receiver of the message) and arguments
     * (the content of the message).
     * <p>
     * Internally, I use Vector to maintain jdk1.1 compatability
     * <p>
     * Copyright (C) 2003-2006, C. Ramakrishnan / Illposed Software.
     * All rights reserved.
     * <p>
     * See license.txt (or license.rtf) for license information.
     *
     * @author Chandrasekhar Ramakrishnan
     * @version 1.0
     */
    public class OSCMessage extends OSCPacket {

	protected String address;
	protected Vector arguments;

	/**
	 * Create an empty OSC Message.
	 * In order to send this osc message, you need to set the address
	 * and, perhaps, some arguments.
	 */
	public OSCMessage() {
		super();
		arguments = new Vector();
	}

	/**
	 * Create an OSCMessage with an address already initialized.
	 * @param newAddress the recepient of this OSC message
	 */
	public OSCMessage(String newAddress) {
		this(newAddress, null);
	}

	/**
	 * Create an OSCMessage with an address and arguments already initialized.
	 * @param newAddress    the recepient of this OSC message
	 * @param newArguments  the data sent to the receiver
	 */
	public OSCMessage(String newAddress, Object[] newArguments) {
		super();
		address = newAddress;
		if (null != newArguments) {
			arguments = new Vector(newArguments.length);
			for (int i = 0; i < newArguments.length; i++) {
				arguments.add(newArguments[i]);
			}
		} else
			arguments = new Vector();
		init();
	}

	/**
	 * The receiver of this message.
	 * @return the receiver of this OSC Message
	 */
	public String getAddress() {
		return address;
	}

	/**
	 * Set the address of this messsage.
	 * @param anAddress the receiver of the message
	 */
	public void setAddress(String anAddress) {
		address = anAddress;
	}

	/**
	 * Add an argument to the list of arguments.
	 * @param argument a Float, String, Integer, BigInteger, Boolean or array of these
	 */
	public void addArgument(Object argument) {
		arguments.add(argument);
	}

	/**
	 * The arguments of this message.
	 * @return the arguments to this message
	 */
	public Object[] getArguments() {
		return arguments.toArray();
	}

	/**
	 * Convert the address into a byte array. Used internally.
	 * @param stream OscPacketByteArrayConverter
	 */
	protected void computeAddressByteArray(OSCJavaToByteArrayConverter stream) {
		stream.write(address);
	}

	/**
 	 * Convert the arguments into a byte array. Used internally.
	 * @param stream OscPacketByteArrayConverter
	 */
	protected void computeArgumentsByteArray(OSCJavaToByteArrayConverter stream) {
		stream.write(',');
		if (null == arguments)
			return;
		stream.writeTypes(arguments);
		Enumeration enumer = arguments.elements();
		while (enumer.hasMoreElements()) {
			stream.write(enumer.nextElement());
		}
	}

	/**
	 * Convert the message into a byte array. Used internally.
	 * @param stream OscPacketByteArrayConverter
	 */
	protected void computeByteArray(OSCJavaToByteArrayConverter stream) {
		computeAddressByteArray(stream);
		computeArgumentsByteArray(stream);
		byteArray = stream.toByteArray();
	}

    }



    /**
     * OSCPacket is the abstract superclass for the various
     * kinds of OSC Messages. The actual packets are:
     * <ul>
     * <li>OSCMessage &mdash; simple OSC messages
     * <li>OSCBundle &mdash; OSC messages with timestamps and/or made up of multiple messages
     * </ul>
     *<p>
     * This implementation is based on <a href="http://www.emergent.de/Goodies/">Markus Gaelli</a> and
     * Iannis Zannos' OSC implementation in Squeak Smalltalk.
     */
    public abstract class OSCPacket {

	protected boolean isByteArrayComputed;
	protected byte[] byteArray;

	/**
	 * Default constructor for the abstract class
	 */
	public OSCPacket() {
		super();
	}

	/**
	 * Generate a representation of this packet conforming to the
	 * the OSC byte stream specification. Used Internally.
	 */
	protected void computeByteArray() {
		OSCJavaToByteArrayConverter stream = new OSCJavaToByteArrayConverter();
		computeByteArray(stream);
	}

	/**
	 * Subclasses should implement this method to product a byte array
	 * formatted according to the OSC specification.
	 * @param stream OscPacketByteArrayConverter
	 */
	protected abstract void computeByteArray(OSCJavaToByteArrayConverter stream);

	/**
	 * Return the OSC byte stream for this packet.
	 * @return byte[]
	 */
	public byte[] getByteArray() {
		if (!isByteArrayComputed)
			computeByteArray();
		return byteArray;
	}

	/**
	 * Run any post construction initialization. (By default, do nothing.)
	 */
	protected void init() {

	}
    }



    public class OSCByteArrayToJavaConverter {

	byte[] bytes;
	int bytesLength;
	int streamPosition;

	/**
	 * Create a helper object for converting from a byte array to an OSCPacket object.
	 */
	public OSCByteArrayToJavaConverter() {
		super();
	}

	/**
	 * Convert a byte array into an OSCPacket (either an OSCMessage or OSCBundle).
	 * @return an OSCPacket
	 */
	public OSCPacket convert(byte[] byteArray, int bytesLength) {
		bytes = byteArray;
		this.bytesLength = bytesLength;
		streamPosition = 0;
		if (isBundle())
			return convertBundle();
		else
			return convertMessage();
	}

	/**
	 * Is my byte array a bundle?
	 * @return true if it the byte array is a bundle, false o.w.
	 */
	private boolean isBundle() {
			// only need the first 7 to check if it is a bundle
		String bytesAsString = new String(bytes, 0, 7);
		return bytesAsString.startsWith("#bundle");
	}

	/**
	 * Convert the byte array a bundle. Assumes that the byte array is a bundle.
	 * @return a bundle containing the data specified in the byte stream
	 */
	private OSCBundle convertBundle() {
		// skip the "#bundle " stuff
		streamPosition = 8;
		Date timestamp = readTimeTag();
		OSCBundle bundle = new OSCBundle(timestamp);
		OSCByteArrayToJavaConverter myConverter = new OSCByteArrayToJavaConverter();
		while (streamPosition < bytesLength) {
			// recursively read through the stream and convert packets you find
			int packetLength = ((Integer) readInteger()).intValue();
			byte[] packetBytes = new byte[packetLength];
			for (int i = 0; i < packetLength; i++)
				packetBytes[i] = bytes[streamPosition++];
			OSCPacket packet = myConverter.convert(packetBytes, packetLength);
			bundle.addPacket(packet);
		}
		return bundle;
	}

	/**
	 * Convert the byte array a simple message. Assumes that the byte array is a message.
	 * @return a message containing the data specified in the byte stream
	 */
	private OSCMessage convertMessage() {
		OSCMessage message = new OSCMessage();
		message.setAddress(readString());
		char[] types = readTypes();
		if (null == types) {
			// we are done
			return message;
		}
		moveToFourByteBoundry();
		for (int i = 0; i < types.length; ++i) {
			if ('[' == types[i]) {
				// we're looking at an array -- read it in
				message.addArgument(readArray(types, ++i));
				// then increment i to the end of the array
				while (']' != types[i])
					i++;
			} else
				message.addArgument(readArgument(types[i]));
		}
		return message;
	}

	/**
	 * Read a string from the byte stream.
	 * @return the next string in the byte stream
	 */
	private String readString() {
		int strLen = lengthOfCurrentString();
		char[] stringChars = new char[strLen];
		for (int i = 0; i < strLen; i++)
			stringChars[i] = (char) bytes[streamPosition++];
		moveToFourByteBoundry();
		return new String(stringChars);
	}

	/**
	 * Read the types of the arguments from the byte stream.
	 * @return a char array with the types of the arguments
	 */
	private char[] readTypes() {
		// the next byte should be a ","
		if (bytes[streamPosition] != 0x2C)
			return null;
		streamPosition++;
		// find out how long the list of types is
		int typesLen = lengthOfCurrentString();
		if (0 == typesLen) {
			return null;
		}

		// read in the types
		char[] typesChars = new char[typesLen];
		for (int i = 0; i < typesLen; i++) {
			typesChars[i] = (char) bytes[streamPosition++];
		}
		return typesChars;
	}

	/**
	 * Read an object of the type specified by the type char.
	 * @param c type of argument to read
	 * @return a Java representation of the argument
	 */
	private Object readArgument(char c) {
		switch (c) {
			case 'i' :
				return readInteger();
			case 'h' :
				return readBigInteger();
			case 'f' :
				return readFloat();
			case 'd' :
				return readDouble();
			case 's' :
				return readString();
			case 'c' :
				return readChar();
			case 'T' :
				return Boolean.TRUE;
			case 'F' :
				return Boolean.FALSE;
		}

		return null;
	}

	/**
	 * Read a char from the byte stream.
	 * @return a Character
	 */
	private Object readChar() {
		return new Character((char) bytes[streamPosition++]);
	}

	/**
	 * Read a double &mdash; this just read a float.
	 * @return a Double
	 */
	private Object readDouble() {
		return readFloat();
	}

	/**
	 * Read a float from the byte stream.
	 * @return a Float
	 */
	private Object readFloat() {
		byte[] floatBytes = new byte[4];
		floatBytes[0] = bytes[streamPosition++];
		floatBytes[1] = bytes[streamPosition++];
		floatBytes[2] = bytes[streamPosition++];
		floatBytes[3] = bytes[streamPosition++];
//		int floatBits =
//			(floatBytes[0] << 24)
//				| (floatBytes[1] << 16)
//				| (floatBytes[2] << 8)
//				| (floatBytes[3]);
		BigInteger floatBits = new BigInteger(floatBytes);
		return new Float(Float.intBitsToFloat(floatBits.intValue()));
	}

	/**
	 * Read a Big Integer (64 bit int) from the byte stream.
	 * @return a BigInteger
	 */
	private Object readBigInteger() {
		byte[] longintBytes = new byte[8];
		longintBytes[0] = bytes[streamPosition++];
		longintBytes[1] = bytes[streamPosition++];
		longintBytes[2] = bytes[streamPosition++];
		longintBytes[3] = bytes[streamPosition++];
		longintBytes[4] = bytes[streamPosition++];
		longintBytes[5] = bytes[streamPosition++];
		longintBytes[6] = bytes[streamPosition++];
		longintBytes[7] = bytes[streamPosition++];
		return new BigInteger(longintBytes);
	}

	/**
	 * Read an Integer (32 bit int) from the byte stream.
	 * @return an Integer
	 */
	private Object readInteger() {
		byte[] intBytes = new byte[4];
		intBytes[0] = bytes[streamPosition++];
		intBytes[1] = bytes[streamPosition++];
		intBytes[2] = bytes[streamPosition++];
		intBytes[3] = bytes[streamPosition++];
		BigInteger intBits = new BigInteger(intBytes);
		return new Integer(intBits.intValue());
	}

	/**
	 * Read the time tag and convert it to a Java Date object. A timestamp is a 64 bit number
	 * representing the time in NTP format. The first 32 bits are seconds since 1900, the
	 * second 32 bits are fractions of a second.
	 * @return a Date
	 */
	private Date readTimeTag() {
		byte[] secondBytes = new byte[8];
		byte[] fractionBytes = new byte[8];
		for (int i = 0; i < 4; i++) {
			// clear the higher order 4 bytes
			secondBytes[i] = 0; fractionBytes[i] = 0;
		}
			// while reading in the seconds & fraction, check if
			// this timetag has immediate semantics
		boolean isImmediate = true;
		for (int i = 4; i < 8; i++) {
			secondBytes[i] = bytes[streamPosition++];
			if (secondBytes[i] > 0)
				isImmediate = false;
		}
		for (int i = 4; i < 8; i++) {
			fractionBytes[i] = bytes[streamPosition++];
			if (i < 7) {
				if (fractionBytes[i] > 0)
					isImmediate = false;
			} else {
				if (fractionBytes[i] > 1)
					isImmediate = false;
			}
		}

		if (isImmediate) return OSCutils.TIMESTAMP_IMMEDIATE;

		BigInteger secsSince1900 = new BigInteger(secondBytes);
		long secsSince1970 =  secsSince1900.longValue() - OSCutils.SECONDS_FROM_1900_to_1970.longValue();
		if (secsSince1970 < 0) secsSince1970 = 0; // no point maintaining times in the distant past
		long fraction = (new BigInteger(fractionBytes).longValue());
			// the next line was cribbed from jakarta commons-net's NTP TimeStamp code
		fraction = (fraction * 1000) / 0x100000000L;
			// I don't where, but I'm losing 1ms somewhere...
		fraction = (fraction > 0) ? fraction + 1 : 0;
		long millisecs = (secsSince1970 * 1000) + fraction;
		return new Date(millisecs);
	}

	/**
	 * Read an array from the byte stream.
	 * @param types
	 * @param i
	 * @return an Array
	 */
	private Object[] readArray(char[] types, int i) {
		int arrayLen = 0;
		while (types[i + arrayLen] != ']')
			arrayLen++;
		Object[] array = new Object[arrayLen];
		for (int j = 0; j < arrayLen; j++) {
			array[j] = readArgument(types[i + j]);
		}
		return array;
	}

	/**
	 * Get the length of the string currently in the byte stream.
	 */
	private int lengthOfCurrentString() {
		int i = 0;
		while (bytes[streamPosition + i] != 0)
			i++;
		return i;
	}

	/**
	 * Move to the next byte with an index in the byte array divisable by four.
	 */
	private void moveToFourByteBoundry() {
		// If i'm already at a 4 byte boundry, I need to move to the next one
		int mod = streamPosition % 4;
		streamPosition += (4 - mod);
	}
    }

    public class OSCJavaToByteArrayConverter {

	protected ByteArrayOutputStream stream = new ByteArrayOutputStream();
	private byte[] intBytes = new byte[4];
	private byte[] longintBytes = new byte[8];
		// this should be long enough to accomodate any string
	private char[] stringChars = new char[2048];
	private byte[] stringBytes = new byte[2048];

	public OSCJavaToByteArrayConverter() {
		super();
	}

	/**
	 * Line up the Big end of the bytes to a 4 byte boundry
	 * @return byte[]
	 * @param bytes byte[]
	 */
	private byte[] alignBigEndToFourByteBoundry(byte[] bytes) {
		int mod = bytes.length % 4;
		// if the remainder == 0 then return the bytes otherwise pad the bytes to
		// lineup correctly
		if (mod == 0)
			return bytes;
		int pad = 4 - mod;
		byte[] newBytes = new byte[pad + bytes.length];
		for (int i = 0; i < pad; i++)
			newBytes[i] = 0;
		for (int i = 0; i < bytes.length; i++)
			newBytes[pad + i] = bytes[i];
		return newBytes;
	}

	/**
	 * Pad the stream to have a size divisible by 4.
	 */
	public void appendNullCharToAlignStream() {
		int mod = stream.size() % 4;
		int pad = 4 - mod;
		for (int i = 0; i < pad; i++)
			stream.write(0);
	}

	/**
	 * Convert the contents of the output stream to a byte array.
	 * @return the byte array containing the byte stream
	 */
	public byte[] toByteArray() {
		return stream.toByteArray();
	}

	/**
	 * Write bytes into the byte stream.
	 * @param bytes byte[]
	 */
	public void write(byte[] bytes) {
		writeUnderHandler(bytes);
	}

	/**
	 * Write an int into the byte stream.
	 * @param i int
	 */
	public void write(int i) {
		writeInteger32ToByteArray(i);
	}

	/**
	 * Write a float into the byte stream.
	 * @param f java.lang.Float
	 */
	public void write(Float f) {
		writeInteger32ToByteArray(Float.floatToIntBits(f.floatValue()));
	}

	/**
	 * @param i java.lang.Integer
	 */
	public void write(Integer i) {
		writeInteger32ToByteArray(i.intValue());
	}

	/**
	 * @param i java.lang.Integer
	 */
	public void write(BigInteger i) {
		writeInteger64ToByteArray(i.longValue());
	}

	/**
	 * Write a string into the byte stream.
	 * @param aString java.lang.String
	 */
	public void write(String aString) {
		int stringLength = aString.length();
			// this is a deprecated method -- should use get char and convert
			// the chars to bytes
//		aString.getBytes(0, stringLength, stringBytes, 0);
		aString.getChars(0, stringLength, stringChars, 0);
			// pad out to align on 4 byte boundry
		int mod = stringLength % 4;
		int pad = 4 - mod;
		for (int i = 0; i < pad; i++)
			stringChars[stringLength++] = 0;
		// convert the chars into bytes and write them out
		for (int i = 0; i < stringLength; i++) {
			stringBytes[i] = (byte) (stringChars[i] & 0x00FF);
		}
		stream.write(stringBytes, 0, stringLength);
	}

	/**
	 * Write a char into the byte stream.
	 * @param c char
	 */
	public void write(char c) {
		stream.write(c);
	}

	/**
	 * Write an object into the byte stream.
	 * @param anObject one of Float, String, Integer, BigInteger, or array of these.
	 */
	public void write(Object anObject) {
		// Can't do switch on class
		if (null == anObject)
			return;
		if (anObject instanceof Object[]) {
			Object[] theArray = (Object[]) anObject;
			for(int i = 0; i < theArray.length; ++i) {
				write(theArray[i]);
			}
			return;
		}
		if (anObject instanceof Float) {
			write((Float) anObject);
			return;
		}
		if (anObject instanceof String) {
			write((String) anObject);
			return;
		}
		if (anObject instanceof Integer) {
			write((Integer) anObject);
			return;
		}
		if (anObject instanceof BigInteger) {
			write((BigInteger) anObject);
			return;
		}
	}

	/**
	 * Write the type tag for the type represented by the class
	 * @param c Class of a Java object in the arguments
	 */
	public void writeType(Class c) {
		// A big ol' case statement -- what's polymorphism mean, again?
		// I really wish I could extend the base classes!

		// use the appropriate flags to tell SuperCollider what kind of
		// thing it is looking at

		if (Integer.class.equals(c)) {
			stream.write('i');
			return;
		}
		if (java.math.BigInteger.class.equals(c)) {
			stream.write('h');
			return;
		}
		if (Float.class.equals(c)) {
			stream.write('f');
			return;
		}
		if (Double.class.equals(c)) {
			stream.write('d');
			return;
		}
		if (String.class.equals(c)) {
			stream.write('s');
			return;
		}
		if (Character.class.equals(c)) {
			stream.write('c');
			return;
		}
	}

	/**
	 * Write the types for an array element in the arguments.
	 * @param array java.lang.Object[]
	 */
	public void writeTypesArray(Object[] array) {
		// A big ol' case statement in a for loop -- what's polymorphism mean, again?
		// I really wish I could extend the base classes!

		for (int i = 0; i < array.length; i++) {
			if (null == array[i])
				continue;
			// Create a way to deal with Boolean type objects
			if (Boolean.TRUE.equals(array[i])) {
				stream.write('T');
				continue;
			}
			if (Boolean.FALSE.equals(array[i])) {
				stream.write('F');
				continue;
			}
			// this is an object -- write the type for the class
			writeType(array[i].getClass());
		}
	}

	/**
	 * Write types for the arguments (use a vector for jdk1.1 compatibility, rather than an ArrayList).
	 * @param vector  the arguments to an OSCMessage
	 */
	public void writeTypes(Vector vector) {
		// A big ol' case statement in a for loop -- what's polymorphism mean, again?
		// I really wish I could extend the base classes!

		Enumeration enm = vector.elements();
		Object nextObject;
		while (enm.hasMoreElements()) {
			nextObject = enm.nextElement();
			if (null == nextObject)
				continue;
			// if the array at i is a type of array write a [
			// This is used for nested arguments
			if (nextObject.getClass().isArray()) {
				stream.write('[');
				// fill the [] with the SuperCollider types corresponding to the object
				// (e.g., Object of type String needs -s).
				writeTypesArray((Object[]) nextObject);
				// close the array
				stream.write(']');
				continue;
			}
			// Create a way to deal with Boolean type objects
			if (Boolean.TRUE.equals(nextObject)) {
				stream.write('T');
				continue;
			}
			if (Boolean.FALSE.equals(nextObject)) {
				stream.write('F');
				continue;
			}
			// go through the array and write the superCollider types as shown in the
			// above method. the Classes derived here are used as the arg to the above method
			writeType(nextObject.getClass());
		}
		// align the stream with padded bytes
		appendNullCharToAlignStream();
	}

	/**
	 * Write bytes to the stream, catching IOExceptions and converting them to RuntimeExceptions.
	 * @param bytes byte[]
	 */
	private void writeUnderHandler(byte[] bytes) {

		try {
			stream.write(alignBigEndToFourByteBoundry(bytes));
		} catch (IOException e) {
			throw new RuntimeException("You're screwed: IOException writing to a ByteArrayOutputStream");
		}
	}

	/**
	 * Write a 32 bit integer to the byte array without allocating memory.
	 * @param value a 32 bit int.
	 */
	private void writeInteger32ToByteArray(int value) {
		//byte[] intBytes = new byte[4];
		//I allocated the this buffer globally so the GC has less work

		intBytes[3] = (byte)value; value>>>=8;
		intBytes[2] = (byte)value; value>>>=8;
		intBytes[1] = (byte)value; value>>>=8;
		intBytes[0] = (byte)value;

		try {
			stream.write(intBytes);
		} catch (IOException e) {
			throw new RuntimeException("You're screwed: IOException writing to a ByteArrayOutputStream");
		}
	}

	/**
	 * Write a 64 bit integer to the byte array without allocating memory.
	 * @param value a 64 bit int.
	 */
	private void writeInteger64ToByteArray(long value) {
		longintBytes[7] = (byte)value; value>>>=8;
		longintBytes[6] = (byte)value; value>>>=8;
		longintBytes[5] = (byte)value; value>>>=8;
		longintBytes[4] = (byte)value; value>>>=8;
		longintBytes[3] = (byte)value; value>>>=8;
		longintBytes[2] = (byte)value; value>>>=8;
		longintBytes[1] = (byte)value; value>>>=8;
		longintBytes[0] = (byte)value;

		try {
			stream.write(longintBytes);
		} catch (IOException e) {
			throw new RuntimeException("You're screwed: IOException writing to a ByteArrayOutputStream");
		}
	}

    }

}
