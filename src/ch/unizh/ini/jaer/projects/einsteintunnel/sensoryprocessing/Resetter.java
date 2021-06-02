/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.einsteintunnel.sensoryprocessing;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Properties;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import ch.unizh.ini.jaer.projects.einsteintunnel.multicamera.PowerSettingsDialog;

/**
 *
 * @author braendch
 */
public class Resetter extends TimerTask{

	public int sendPort = 75;
	public int receivePort = 77;
	private String powerSupplyIP = "192.168.1.40";
	private String adminPassword = "admin";
	private String adminUser = "admin";
	private String cameraSupplyNumber = "8";
        private String routerSupplyNumber = "5";
	String[] alert_recipients = {"ch.braendli@gmail.com"}; // added this line
	private InetSocketAddress powerSupplySocketAddress;
	private DatagramSocket outputSocket = null;

	public Resetter(){
	}

	@Override
	public void run(){
		try {
			//sendCameraResetCommand();
			reboot();
		} catch (MessagingException ex) {
			Logger.getLogger(Resetter.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	private void sendCameraResetCommand() throws MessagingException{
		try {String s;
			s = "Sw_on"+cameraSupplyNumber+adminUser+adminPassword+"\r\n";
			//make sure thate message gets received
			for(int i=0; i<10; i++)sendString(s);
			Thread.sleep(30000);
			s = "Sw_off"+cameraSupplyNumber+adminUser+adminPassword+"\r\n";
			//make sure thate message gets received
			for(int i=0; i<10; i++)sendString(s);
			Thread.sleep(3000);
		} catch (InterruptedException ex) {
			sendAlertEmail(ex.getMessage());
			Logger.getLogger(Resetter.class.getName()).log(Level.SEVERE, null, ex);
		} catch (SocketException ex) {
			sendAlertEmail(ex.getMessage());
			Logger.getLogger(PowerSettingsDialog.class.getName()).log(Level.SEVERE, null, ex);
		} catch (UnknownHostException ex) {
			sendAlertEmail(ex.getMessage());
			Logger.getLogger(PowerSettingsDialog.class.getName()).log(Level.SEVERE, null, ex);
		} catch (IOException ex) {
			sendAlertEmail(ex.getMessage());
			Logger.getLogger(PowerSettingsDialog.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

        private void sendRouterResetCommand() throws MessagingException{
		try {String s;
			s = "Sw_off"+routerSupplyNumber+adminUser+adminPassword+"\r\n";
			//make sure thate message gets received
			for(int i=0; i<10; i++)sendString(s);
			Thread.sleep(30000);
			s = "Sw_on"+routerSupplyNumber+adminUser+adminPassword+"\r\n";
			//make sure thate message gets received
			for(int i=0; i<10; i++)sendString(s);
			Thread.sleep(3000);
		} catch (InterruptedException ex) {
			sendAlertEmail(ex.getMessage());
			Logger.getLogger(Resetter.class.getName()).log(Level.SEVERE, null, ex);
		} catch (SocketException ex) {
			sendAlertEmail(ex.getMessage());
			Logger.getLogger(PowerSettingsDialog.class.getName()).log(Level.SEVERE, null, ex);
		} catch (UnknownHostException ex) {
			sendAlertEmail(ex.getMessage());
			Logger.getLogger(PowerSettingsDialog.class.getName()).log(Level.SEVERE, null, ex);
		} catch (IOException ex) {
			sendAlertEmail(ex.getMessage());
			Logger.getLogger(PowerSettingsDialog.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	private void sendString(String s) throws SocketException, UnknownHostException, IOException{
		outputSocket = new DatagramSocket(sendPort);
		outputSocket.setSoTimeout(100);
		byte[] b = s.getBytes();
		InetAddress IPAddress =  InetAddress.getByName(powerSupplyIP);
		powerSupplySocketAddress = new InetSocketAddress(IPAddress,sendPort);
		DatagramPacket d = new DatagramPacket(b,b.length,powerSupplySocketAddress);
		if (outputSocket != null){
		    outputSocket.send(d);
		    outputSocket.close();
		}
	}

	public void sendAlertEmail(String alert) throws MessagingException{
		String host = "smtp.gmail.com";
		String from = "einstein.passage@gmail.com";
		String pass = "3inst3in?";
		Properties props = System.getProperties();
		props.put("mail.smtp.starttls.enable", "true"); // added this line
		props.put("mail.smtp.host", host);
		props.put("mail.smtp.user", from);
		props.put("mail.smtp.password", pass);
		props.put("mail.smtp.port", "587");
		props.put("mail.smtp.auth", "true");

		Session session = Session.getDefaultInstance(props, null);
		MimeMessage message = new MimeMessage(session);
		message.setFrom(new InternetAddress(from));

		InternetAddress[] toAddress = new InternetAddress[alert_recipients.length];

		// To get the array of addresses
		for( int i=0; i < alert_recipients.length; i++ ) { // changed from a while loop
			toAddress[i] = new InternetAddress(alert_recipients[i]);
		}
		//System.out.println(Message.RecipientType.TO);

		for( int i=0; i < toAddress.length; i++) { // changed from a while loop
			message.addRecipient(Message.RecipientType.TO, toAddress[i]);
		}
		message.setSubject("WARNING Something went wrong in the Einstein tunnel");
		message.setText(alert);
		Transport transport = session.getTransport("smtp");
		transport.connect(host, from, pass);
		transport.sendMessage(message, message.getAllRecipients());
		transport.close();

	}

	private void reboot() throws MessagingException{
            sendRouterResetCommand();
            try {
                    Runtime.getRuntime().exec("cmd /c start C:/reboot.bat");
            } catch (IOException ex) {
                    sendAlertEmail(ex.getMessage());
                    Logger.getLogger(PowerSettingsDialog.class.getName()).log(Level.SEVERE, null, ex);
            }
	}

}
