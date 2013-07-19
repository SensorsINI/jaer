package org.capocaccia.cne.jaer.robotarm;

import java.io.IOException;

/**
 * @author Dennis Göhlsdorf
 * Interface class to communicates with FGControl program. 
 */
public class RobotCommunicator {
	private double[] pos;
	RobotCommunicationThread commThread; 
	public RobotCommunicator() throws IOException {
		pos = new double[12];
		commThread = new RobotCommunicationThread(this);
	}
	public void waitForNextDataPoint() {
//		int currentTimeStamp = commThread.getCurrentTimeStamp();
//		while (commThread.getCurrentTimeStamp() == currentTimeStamp) {
//			try {
//				Thread.sleep(10);
//			} catch (InterruptedException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//		}
		commThread.waitForNextDataPoint();
	}
	public void start() {
		commThread.start();
	}
	public void stop() {
		commThread.endThread();
	}
	public void close() {
		commThread.disconnect();
	}
	public synchronized void moveRight() {
		commThread.sendPacket(8, null);
	}
	public synchronized void moveLeft() {
		commThread.sendPacket(9, null);
	}
	public synchronized void stopLeftRight() {
		commThread.sendPacket(16,null);
	}
	public synchronized void moveOut() {
		commThread.sendPacket(10, null);
	}
	public synchronized void moveIn() {
		commThread.sendPacket(11, null);
	}
	public synchronized void stopInOut() {
		commThread.sendPacket(17,null);
	}
	public synchronized void moveBack() {
		commThread.sendPacket(12, null);
	}
	public synchronized void moveForward() {
		commThread.sendPacket(13, null);
	}
	public synchronized void stopForwardBackward() {
		commThread.sendPacket(18,null);
	}
	public synchronized void turnRight() {
		commThread.sendPacket(14, null);
	}
	public synchronized void turnLeft() {
		commThread.sendPacket(15, null);
	}
	public synchronized void stopTurning() {
		commThread.sendPacket(19,null);
	}
	public synchronized void stopMovement() {
		commThread.sendPacket(20,null);
	}
	public synchronized void moveToPosition(double x, double y, double z, double alpha, double beta, double gamma) {
		commThread.sendPacket(6, new double[] {x, y, z, alpha, beta, gamma});
	}
	public synchronized void moveToAngles(double a0, double a1, double a2, double a3, double a4) {
		commThread.sendPacket(7, new double[] {a0, a1, a2, a3, a4});
	}
	public synchronized void closeGripper() {
		commThread.sendPacket(3, null);
	}
	public synchronized void openGripper() {
		commThread.sendPacket(4, null);
	}
	public synchronized void setPosition(double[] pos) {
		if (pos.length == 12) {
			this.pos = pos;
//			System.out.println("robot now at "+getX() +", "+getY()+", "+getZ()+")!");
		}
	}
	public synchronized double getX() {
		return pos[0];
	}
	public synchronized  double getY() {
		return pos[1];
	}
	public synchronized double getZ() {
		return pos[2];
	}
	public synchronized double getAlpha() {
		return pos[3];
	}
	public synchronized double getBeta() {
		return pos[4];
	}
	public synchronized double getGamma() {
		return pos[5];
	}
	public synchronized double getA0() {
		return pos[6];
	}
	public synchronized double getA1() {
		return pos[7];
	}
	public synchronized double getA2() {
		return pos[8];
	}
	public synchronized double getA3() {
		return pos[9];
	}
	public synchronized double getA4() {
		return pos[10];
	}
	public synchronized double getA5() {
		return pos[11];
	}
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		RobotCommunicator roboComm;
		try {
			roboComm = new RobotCommunicator();
			// movement is possible while robot is disconnected:
			roboComm.start();
			roboComm.closeGripper();
//			roboComm.openGripper();
//			roboComm.moveToAngles(a0, a1, a2, a3, a4)
			roboComm.waitForNextDataPoint();
			roboComm.turnRight();
//
//			for (int i = 0; i < 3; i++) {
//				roboComm.moveToAngles(0, roboComm.getA1(), roboComm.getA2(), roboComm.getA3(), roboComm.getA4());
//			}
			roboComm.stop();
			roboComm.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		
//		try {
//			commThread.connect();
//		} catch (IOException e1) {
//			// TODO Auto-generated catch block
//			e1.printStackTrace();
//		}
//		System.out.println("waiting for enter to start server...");
//		try {
//			System.in.read();
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		commThread.start();
//		System.out.println("press enter to stop...");
//		try {
//			System.in.read();
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		commThread.endThread();
//		for (int i = 0; i < 3; i++) {
//			moveToAngles(0, getA1(), getA2(), getA3(), getA4());
//		}
//		System.out.println("end.");
	}

}
