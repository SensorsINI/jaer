package net.sf.jaer.hardwareinterface.serial.edvsviewer;

import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;

import javax.swing.JOptionPane;
import net.sf.jaer.hardwareinterface.serial.edvsviewer.HWPort.PortAttribute;



public class IOThread extends Thread {
	private boolean threadRunning;
	private HWPort port = null;
	private boolean	echoOutputOnScreen = false;

	private String rs232Input;
	private char keyboardInputCharacter;

	private EventProcessorWindow iv = null;

	public void openPort(String portName, PortAttribute pa) {
		System.out.print("opening port...");
		try {
			port.open(portName, pa);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(null,
					"exception opening com port",
					"jtp",
					JOptionPane.DEFAULT_OPTION);
			return;
		}
		System.out.println("ok");
	}

	public void sendCommand(String cmd) {
		port.writeLn(cmd);
//		port.flushOutput();
	}

	public IOThread(HWPort port, EmbeddedDVS128Viewer dvs128viewer) {
		this.port = port;
		threadRunning = true;
		this.setPriority(MAX_PRIORITY);
	}
	public void registerEventDisplayWindow(EventProcessorWindow iv) {
		this.iv = iv;
	}

	public void run(){
		KeyboardFocusManager manager;
	    manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
	    KeyDispatcher kd = new KeyDispatcher();
	    manager.addKeyEventDispatcher(kd);

	    while (threadRunning){

	    	yield();

	    	try {
	    		rs232Input = port.getAllData();

				if (rs232Input != null) {

					if (echoOutputOnScreen) {
						System.out.print(rs232Input);
					}

					parseNewInput(rs232Input);

				}
			} catch (Exception e) { //
				System.out.println("Exception! " + e);
				e.printStackTrace();
			}

		}

	    manager.removeKeyEventDispatcher(kd);		// important to remove, otherwise will
	    											// stay alive in background and fetch
	    											// keys!!!
		
		if (port != null) {
			port.close();
		}
	}

	public void terminate(){
		threadRunning = false;
	}

	private int pixelX, pixelY, pixelP;
	private int inputProcessingIndex = 0;
	private String specialData;

	public void parseNewInput(String input) {

		//System.out.println("Lenght: " + input.length());

		//System.out.println("Called at " + System.currentTimeMillis() + " with length of " + input.length());
		for (int n=0; n<input.length(); n++) {

			int c=(int) (input.charAt(n));
			//					System.out.println("Received char " + c);
			switch (inputProcessingIndex) {
			case 0:
				if ((c & 0x80) == 0) {		// check if valid "high byte"
					pixelX = c;
					inputProcessingIndex=1;
				} else {
					if ((c&0xF0) == 0x80) {
						inputProcessingIndex = 100+(c&0x0F)-1;	// remember start of special data sequence
						//System.out.println("Start Special Sequence of length : " +inputProcessingIndex);
						specialData="";
					} else {
						System.out.println("Data transfer hickup at " + System.currentTimeMillis());
					}
					// otherwise ignore and assume next is high byte
					// System.out.println("flip error " + System.currentTimeMillis());
				}
				break;

			case 1:
				pixelY = c & 0x7F;
				pixelP = (c & 0x80) >> 7;
				inputProcessingIndex = 0;
				if (iv != null) {
					iv.processNewEvent(pixelX, pixelY, pixelP);
				}
				break;

			case 100:
				specialData = specialData + input.charAt(n);
				if (iv != null) {
					iv.processSpecialData(specialData);
				}
				inputProcessingIndex=0;
				break;

			default:
				specialData = specialData + input.charAt(n);
				inputProcessingIndex--;
			}
		}
	}

	public void setOutputOnScreenEnabled(boolean o) {
		echoOutputOnScreen = o;
	}
	
	class KeyDispatcher implements KeyEventDispatcher{
		KeyboardFocusManager manager;
		public boolean dispatchKeyEvent(KeyEvent keyEvent) {

			KeyboardFocusManager manager;
		    manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
		    String currentWindow = manager.getActiveWindow().getName();
		    if ((currentWindow != "DVSViewer") && (currentWindow != "DVSViewerWindow")) {
		    	return(false);
		    }
//		    System.out.println("" + manager.getActiveWindow().getName());

		    if (keyEvent.getID() == KeyEvent.KEY_PRESSED) {
//				System.out.println("key: " + (0+keyboardInputCharacter));
//				System.out.println("Modifiers: " + keyEvent.getModifiers());
//				System.out.println("Code: " + keyEvent.getKeyCode());
				switch (keyEvent.getKeyCode()) {
				case KeyEvent.VK_PAGE_UP:
					break;
				case KeyEvent.VK_PAGE_DOWN:
					break;
				case KeyEvent.VK_END:
					break;
				case KeyEvent.VK_HOME:
					break;
				case KeyEvent.VK_LEFT:
					port.write("" + ((char) 27) + "<");
					break;
				case KeyEvent.VK_UP:
					port.write("" + ((char) 27) + "^");
					break;
				case KeyEvent.VK_RIGHT:
					port.write("" + ((char) 27) + ">");
					break;
				case KeyEvent.VK_DOWN:
					port.write("" + ((char) 27) + "v");
					break;
				}
			}

			
			if (keyEvent.getID() == KeyEvent.KEY_TYPED) {
				keyboardInputCharacter = keyEvent.getKeyChar();
					port.write(""+keyboardInputCharacter);
			}

			return(true);
		}
	}
}
