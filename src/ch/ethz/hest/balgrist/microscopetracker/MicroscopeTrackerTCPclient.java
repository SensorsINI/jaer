package ch.ethz.hest.balgrist.microscopetracker;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

public class MicroscopeTrackerTCPclient {

	public MicroscopeTrackerTCPclient() {

	}

	Socket MTClient;
	DataInputStream input;
	DataOutputStream output;

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

	public boolean sendVectorAsString(String x, String y) {
		try {
			output.writeBytes("x" + x + "y" + y + "\n");
			return true;
		}
		catch (IOException e) {
			System.out.println(e + " at sendVectorAsString");
		}
		return false;
	}
/*
	 public boolean sendString(String str) {
	 try {
	 output.writeBytes(str);
	 return true;
	 }
	 catch (IOException e) {
	 System.out.println(e);
	 }
	 return false;
	 }
*/
}
