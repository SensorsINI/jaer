package ch.ethz.hest.balgrist.microscopetracker;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * class that handles the TCP connection
 *
 * @author Niklaus Amrein
 *
 * TODO make sure that filter doesn't throw error when no TCP listener is running
 */
public class MicroscopeTrackerTCPclient {

	private Socket MTClient;
	private DataInputStream input;
	private DataOutputStream output;

	public MicroscopeTrackerTCPclient() {

	}

	public boolean createClient(String name, int PortNumber) {
		try {
			MTClient = new Socket(name, PortNumber);
			try {
				input = new DataInputStream(MTClient.getInputStream());
				try {
					output = new DataOutputStream(MTClient.getOutputStream());
					return true;
				}
				catch (IOException e) {
					System.out.println(e + " at createClient()");
				}
			}
			catch (Exception e) {
				System.out.println(e + " at createClient()");
			}
		}
		catch (UnknownHostException e) {
			System.out.println(e + " at createClient()");
		}
		catch (IOException e) {
			System.out.println(e + " at createClient()");
		}
		return false;
	}

	public boolean closeClient() {
		try {
			output.close();
			input.close();
			MTClient.close();
			return true;
		}
		catch (IOException e) {
			System.out.println(e + " at closeClient()");
		}
		catch (NullPointerException e) {
			System.out.println(e + " at closeClient()");
		}
		return false;

	}

	public boolean sendVector(float x, float y) {
		try {
			output.writeBytes("x");
			output.writeFloat(x);
			output.writeBytes("y");
			output.writeFloat(y);
			return true;
		}
		catch (IOException e) {
			System.out.println(e + " at sendvector()");
		}
		return false;
	}

	public boolean sendVector(String x, String y) {
		try {
			output.writeBytes("x" + x + "y" + y + "\n");
			return true;
		}
		catch (IOException e) {
			System.out.println(e + " at sendVectorAsString");
		}
		return false;
	}
}
