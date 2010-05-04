package org.capocaccia.cne.jaer.robotarm;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Date;

import sun.security.action.GetLongAction;

/**
 * @author Dennis Göhlsdorf
 * Creates a new thread that listens to UPD packages sent from FGControl program. Packages are decoded and sent to RobotCommunicator.
 */
public class RobotCommunicationThread extends Thread {
	String katanaServerAddress = "10.33.0.30";
	ByteArrayOutputStream baos = new ByteArrayOutputStream();
	DataOutputStream out = new DataOutputStream(baos);
	private InetAddress targetAddress; 
	int targetPort = 45454;
	int receivingPort = 45455;
//	protected DatagramSocket senderSocket = null;
	protected DatagramSocket receiverSocket = null;
	protected MulticastSocket msenderSocket = null;
	protected BufferedReader in = null;
	protected boolean moreQuotes = true;
	volatile boolean runServer = true;
	int packageNumber = 0;
	RobotCommunicator comm = null;
	private int lastID;
	private volatile int lastTimeStamp;
	private int myID = (int)System.nanoTime();
	private Object newDataPointLock = new Object();
	public RobotCommunicationThread(RobotCommunicator comm) throws IOException {
		super("RobotCommunicationThread");
		this.comm = comm;
		try {
			targetAddress = InetAddress.getByName(katanaServerAddress);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		connect();
	}
	public void waitForNextDataPoint() {
		synchronized (newDataPointLock) {
			try {
				newDataPointLock.wait();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	public void connect() throws IOException {
		//		senderSocket = new DatagramSocket(45454);		
		msenderSocket = new MulticastSocket(targetPort);		
		receiverSocket = new MulticastSocket(receivingPort);
//		System.out.println("receiver address is :"+receiverSocket.getLocalAddress().toString()+":"+receiverSocket.getLocalPort());
//		receiverSocket = new MulticastSocket(new InetSocketAddress(katanaServerAddress, receivingPort));
//		receiverSocket = new MulticastSocket(new InetSocketAddress("10.33.0.23", receivingPort));// InetSocketAddress(InetAddress.getLocalHost(), receivingPort));
//		receiverSocket.joinGroup(InetAddress.getByName("10.33.0.255"));
//		NetworkInterface netif = NetworkInterface.getByName( "eth0" );
//		receiverSocket.joinGroup(new InetSocketAddress("10.33.0.30", receivingPort), netif);
//		receiverSocket = new MulticastSocket(new InetSocketAddress("127.0.0.1", receivingPort));// InetSocketAddress(InetAddress.getLocalHost(), receivingPort));
//		receiverSocket.bind(new InetSocketAddress("10.33.0.30", 45456));
	}
	protected void disconnect() {
		msenderSocket.close();
		receiverSocket.close(); 
	}
	public void endThread() {
		this.runServer = false;
	}
	public double[] fetchParameters(DataInputStream stream, int count) throws IOException {
		double[] ret = new double[count];
		for (int i = 0; i < count; i++) {
			ret[i] = stream.readDouble();
		}
		return ret;
	}
	
	
	/**
	 * evaluate a command sent via udp
	 * 
	 * 1: current coordinates, has 12 parameters: x, y, z, alpha, beta, gamma, a1, a2, a3, a4, a5, a6
	 * 2: sensor stuff
	 * 3: close gripper
	 * 4: open gripper
	 * 6: moveto position, 6 parameters: x, y, z, alpha, beta, gamma
	 * 7: moveto angles, 5 parameters: a1... a5
	 * @param stream
	 */
	public void evaluateCommand(DataInputStream stream) {
		try {
			int commandNumber = stream.readInt();
			double [] params = null;
			switch (commandNumber) {
			case 1:
				params = fetchParameters(stream, 12);
				if (comm != null)
					comm.setPosition(params);
				synchronized (newDataPointLock) {
					newDataPointLock.notifyAll();
				}
//				System.out.println("Received command #1 with parameter "+params[0]+", "+params[1]+" and "+params[2]);
				break;
			case 2:
				break;
			default:
				break;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	public void run() {
		runServer = true;
		try {
			receiverSocket.setSoTimeout(100);
		} catch (SocketException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		while (runServer) {
			byte[] buf = new byte[1024];
			DatagramPacket packet = new DatagramPacket(buf, buf.length);
			try {
				receiverSocket.receive(packet);
				String dString = new String(buf);
				ByteArrayInputStream bias = new ByteArrayInputStream(buf,0,packet.getLength());
				DataInputStream stream = new DataInputStream(bias);
				int timeStamp = stream.readInt();
				int id = stream.readInt();
				// only accept packages that are newer than the last one:
				if (id != lastID) {
					lastID = id;
					lastTimeStamp = -1;
				}
				if (timeStamp > lastTimeStamp) {
					lastTimeStamp = timeStamp;
					while (stream.available() > 0) {
						evaluateCommand(stream);
					}
				}
			} catch (SocketTimeoutException e){
				
			}catch (IOException e) {
				runServer = false;
			} 
		}
	}
	public int getCurrentTimeStamp() { return lastTimeStamp; }
	public void sendPacket(int cmd, double[] params) {
		baos.reset();
		try {
			
			out.writeInt(packageNumber++);
			out.writeInt(myID);
			
			out.writeInt(cmd);
			if (params != null) {
				for (int i = 0; i < params.length; i++) {
					out.writeDouble(params[i]);
				}
			}
			DatagramPacket packet = new DatagramPacket(baos.toByteArray(), baos.size(), targetAddress, targetPort);
			msenderSocket.send(packet);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
