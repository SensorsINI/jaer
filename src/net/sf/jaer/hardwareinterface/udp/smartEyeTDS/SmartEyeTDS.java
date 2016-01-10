/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.jaer.hardwareinterface.udp.smartEyeTDS;

import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import net.sf.jaer.aemonitor.AEListener;
import net.sf.jaer.aemonitor.AEMonitorInterface;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.eventio.AEUnicastInput;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.udp.UDPInterface;

/**
 * The SmartEyeTDS is a hardware interface class for the AIT SmartEye Traffic Sensor which sends information
 * over UDP.
 * It can also be used for multiple cameras i.e. classes that implement MultiChip.
 *
 * @author braendch
 */
public class SmartEyeTDS implements UDPInterface, HardwareInterface, AEMonitorInterface, BiasgenHardwareInterface{

	//TODO: Make SmartEyeTDS an instance of BiasgenHardwareInterface

	/** Used to store preferences, e.g. the default firmware download file for blank devices */
	protected static Preferences prefs = Preferences.userNodeForPackage(SmartEyeTDS.class);
	/** This support can be used to register this interface for property change events */
	public PropertyChangeSupport support = new PropertyChangeSupport(this);

	protected Logger log = Logger.getLogger("SmartEyeTDS");
	protected AEChip chip;

	/** Time in us of each timestamp count here on host, could be different on board. */
	public final short TICK_US = 1;
	/** the last events from {@link #acquireAvailableEventsFromDriver}, This packet is reused. */
	protected AEPacketRaw lastEventsAcquired = new AEPacketRaw();
	/** default size of AE buffer for user processes. This is the buffer that is written by the hardware capture thread that holds events
	 * that have not yet been transferred via {@link #acquireAvailableEventsFromDriver} to another thread
	 * @see #acquireAvailableEventsFromDriver
	 * @see #setAEBufferSize
	 */
	public static final int AE_BUFFER_SIZE = 100000; // should handle 5Meps at 30FPS
	/** this is the size of the AEPacketRaw that are part of AEPacketRawPool that double buffer the translated events between rendering and capture threads */
	protected int aeBufferSize = prefs.getInt("SmartEyeTDS.aeBufferSize", AE_BUFFER_SIZE);
	/** the device number, out of all potential compatible devices that could be opened */
	protected int interfaceNumber = 0;

	public static boolean isOpen = false;
	public static boolean eventAcquisitionEnabled = true;
	public static boolean overrunOccuredFlag = false;

	private DatagramSocket socket = null;
	public static final int STREAM_PORT = 20020;
	public static final int CONNECT_PORT = 20019;
	public static final int CONTROL_PORT = 20010;
	AEUnicastInput input = null;
	InetSocketAddress client = null;
	private String host; // "localhost"
	private int port;

	public SmartEyeTDS(int devNumber) {
		interfaceNumber = devNumber;
	}

	@Override
	public void open() throws HardwareInterfaceException {
		if (!isOpen){
			try{
				if ( input != null ){
					input.close();
				}

				input = new AEUnicastInput(STREAM_PORT,new AEChip()); // need any AEChip here, won't be used
				input.setSequenceNumberEnabled(false);
				input.setAddressFirstEnabled(true);
				input.setSwapBytesEnabled(true);
				input.set4ByteAddrTimestampEnabled(true);
				input.setTimestampsEnabled(true);
				input.setLocalTimestampEnabled(true);
				input.setBufferSize(1200);
				input.setTimestampMultiplier(0.001f);
				input.open();
				isOpen = true;
			} catch ( IOException ex ){
				throw new HardwareInterfaceException(ex.toString());
			}
		}
	}

	@Override
	public boolean isOpen() {
		return isOpen;
	}

	@Override
	public void close(){
		isOpen=false;
		if(input != null) {
			input.close();
		}
	}

	@Override
	public String getTypeName() {
		return "AIT SmartEye Traffic Data Sensor";
	}

	@Override
	public void setChip(AEChip chip) {
		this.chip = chip;
		host = "localhost";
		port = STREAM_PORT;
		//host = chip.getPrefs().get("ATIS304.host","172.25.48.35"); // "localhost"
		//port = chip.getPrefs().getInt("controlPort",CONTROL_PORT);
	}

	@Override
	public AEChip getChip() {
		return chip;
	}

	@Override
	final public int getTimestampTickUs() {
		return TICK_US;
	}

	private int estimatedEventRate = 0;

	/** @return event rate in events/sec as computed from last acquisition.
	 *
	 */
	@Override
	public int getEstimatedEventRate() {
		return estimatedEventRate;
	}

	/** the max capacity of this UDP interface is max (10 Gbit/s) / (8bytes/event)
	 */
	@Override
	public int getMaxCapacity() {
		return 156250000;
	}

	/** adds a listener for new events captured from the device.
	 * Actually gets called whenever someone looks for new events and there are some using
	 * acquireAvailableEventsFromDriver, not when data is actually captured by AEReader.
	 * Thus it will be limited to the users sampling rate, e.g. the game loop rendering rate.
	 *
	 * @param listener the listener. It is called with a PropertyChangeEvent when new events
	 * are received by a call to {@link #acquireAvailableEventsFromDriver}.
	 * These events may be accessed by calling {@link #getEvents}.
	 */
	@Override
	public void addAEListener(AEListener listener) {
		support.addPropertyChangeListener(listener);
	}

	@Override
	public void removeAEListener(AEListener listener) {
		support.removePropertyChangeListener(listener);
	}

	/** start or stops the event acquisition. sends apropriate vendor request to
	 * device and starts or stops the AEReader
	 * @param enable boolean to enable or disable event acquisition
	 */
	@Override
	public void setEventAcquisitionEnabled(boolean enable) throws HardwareInterfaceException {
		if ( input != null ){
			input.setPaused(enable);
		}
		if ( isOpen() ){
			String s = enable ? "t+\r\n" : "t-\r\n";
			byte[] b = s.getBytes();
			try{
				DatagramPacket d = new DatagramPacket(b,b.length,client);
				if (socket != null){
					socket.send(d);
				}
			} catch ( Exception e ){
				log.warning(e.toString());
			}
		}
		eventAcquisitionEnabled = enable;
	}

	@Override
	public boolean isEventAcquisitionEnabled() {
		return eventAcquisitionEnabled;
	}

	/** @return the size of the double buffer raw packet for AEs */
	@Override
	public int getAEBufferSize() {
		return aeBufferSize; // aePacketRawPool.writeBuffer().getCapacity();
	}

	/** set the size of the raw event packet buffer. Default is AE_BUFFER_SIZE. You can set this larger if you
	 *have overruns because your host processing (e.g. rendering) is taking too long.
	 *<p>
	 *This call discards collected events.
	 * @param size of buffer in events
	 */
	@Override
	public void setAEBufferSize(int size) {
		if ((size < 1000) || (size > 1000000)) {
			log.warning("ignoring unreasonable aeBufferSize of " + size + ", choose a more reasonable size between 1000 and 1000000");
			return;
		}
		aeBufferSize = size;
		prefs.putInt("CypressFX2.aeBufferSize", aeBufferSize);
	}

	/** Is true if an overrun occured in the driver thread during the period before the last time acquireAvailableEventsFromDriver() was called. This flag is cleared by {@link #acquireAvailableEventsFromDriver}, so you need to
	 * check it before you acquire the events.
	 *<p>
	 *If there is an overrun, the events grabbed are the most ancient; events after the overrun are discarded. The timestamps continue on but will
	 *probably be lagged behind what they should be.
	 * @return true if there was an overrun.
	 */
	@Override
	public boolean overrunOccurred() {
		return overrunOccuredFlag;
	}

	/** Resets the timestamp unwrap value, resets the USBIO pipe, and resets the AEPacketRawPool.
	 */
	@Override
	synchronized public void resetTimestamps() {
		//TODO call TDS to reset timestamps
	}

	/** returns last events from {@link #acquireAvailableEventsFromDriver}
	 *@return the event packet
	 */
	@Override
	public AEPacketRaw getEvents() {
		return lastEventsAcquired;
	}

	/** Returns the number of events acquired by the last call to {@link
	 * #acquireAvailableEventsFromDriver }
	 * @return number of events acquired
	 */
	@Override
	public int getNumEventsAcquired() {
		return lastEventsAcquired.getNumEvents();
	}

	/** Gets available events from the socket.  {@link HardwareInterfaceException} is thrown if there is an error.
	 *{@link #overrunOccurred} will be reset after this call.
	 *<p>
	 *This method also starts event acquisition if it is not running already.
	 *
	 *Not thread safe but does use the thread-safe swap() method of AEPacketRawPool to swap data with the acquisition thread.
	 *
	 * @return number of events acquired. If this is zero there is no point in getting the events, because there are none.
	 *@throws HardwareInterfaceException
	 *@see #setEventAcquisitionEnabled
	 *
	 * .
	 */
	@Override
	public AEPacketRaw acquireAvailableEventsFromDriver() throws HardwareInterfaceException {
		if (!isOpen || (socket == null) || (input == null)) {
			open();
		}

		// make sure event acquisition is running
		if (!eventAcquisitionEnabled) {
			setEventAcquisitionEnabled(true);
		}
		if ( input == null ){
			throw new HardwareInterfaceException("no input connection");
		}
		lastEventsAcquired = input.readPacket();
		return lastEventsAcquired;

	}

	//Biasgen

	@Override
	public void setPowerDown (boolean powerDown){
		log.warning("Power down not supported by UDP devices.");
	}

	@Override
	public void sendConfiguration (Biasgen biasgen) throws HardwareInterfaceException{
		//        if ( !isOpen() ){
		//            open();
		//        }
		//        int MAX_COMMAND_LENGTH_BYTES = 256;
		//        for ( Pot p:chip.getBiasgen().getPotArray().getPots() ){
		//            try{
		//                IPot ip = (IPot)p;
		//                int v = (int)(ip.getCurrent()*255/ip.getMaxCurrent());
		//                int n = ip.getShiftRegisterNumber();
		//                String s = String.format("d %d %d mv\n",n,v);
		//                byte[] b = s.getBytes(); // s.getBytes(Charset.forName("US-ASCII"));
		//                if(MultiUDPNetworkDVS128Camera.class.isInstance(chip)){
		//                    MultiUDPNetworkDVS128Camera mc = (MultiUDPNetworkDVS128Camera) chip;
		//                    log.info("selChip " + mc.getSelectedChip());
		//                    if(mc.getSelectedChip()>= mc.getNumChips()){
		//                        for(int i = 0;i<mc.getNumChips();i++){
		//                            client = mc.getCameraMap().getPositionMap().get(i);
		//                            if(client!=null){
		//                                socket.send(new DatagramPacket(b,b.length,client));
		//                                DatagramPacket packet = new DatagramPacket(new byte[ MAX_COMMAND_LENGTH_BYTES ],MAX_COMMAND_LENGTH_BYTES);
		//                                socket.receive(packet);
		//                                ByteArrayInputStream bis;
		//                                BufferedReader reader = new BufferedReader(new InputStreamReader(( bis = new ByteArrayInputStream(packet.getData(),0,packet.getLength()) )));
		//                                String line = reader.readLine(); // .toLowerCase();
		//                                log.info("response from " + packet.getAddress() + " : " + line);
		//                                System.out.println(line); // debug
		//                            }
		//                        }
		//                    } else {
		//                        if(client!=null){
		//                            client = mc.getCameraMap().getPositionMap().get(mc.getSelectedChip());
		//                            socket.send(new DatagramPacket(b,b.length,client));
		//                            DatagramPacket packet = new DatagramPacket(new byte[ MAX_COMMAND_LENGTH_BYTES ],MAX_COMMAND_LENGTH_BYTES);
		//                            socket.receive(packet);
		//                            ByteArrayInputStream bis;
		//                            BufferedReader reader = new BufferedReader(new InputStreamReader(( bis = new ByteArrayInputStream(packet.getData(),0,packet.getLength()) )));
		//                            String line = reader.readLine(); // .toLowerCase();
		//                            log.info("response from " + packet.getAddress() + " : " + line);
		//                            System.out.println(line); // debug
		//                        }
		//                    }
		//                }else{
		//                    socket.send(new DatagramPacket(b,b.length,client));
		//                    DatagramPacket packet = new DatagramPacket(new byte[ MAX_COMMAND_LENGTH_BYTES ],MAX_COMMAND_LENGTH_BYTES);
		//                    socket.receive(packet);
		//                    ByteArrayInputStream bis;
		//                    BufferedReader reader = new BufferedReader(new InputStreamReader(( bis = new ByteArrayInputStream(packet.getData(),0,packet.getLength()) )));
		//                    String line = reader.readLine(); // .toLowerCase();
		//                    log.info("response from " + packet.getAddress() + " : " + line);
		//                    System.out.println(line); // debug
		//                }
		//
		//            } catch ( Exception ex ){
		//                throw new HardwareInterfaceException("while sending biases to " + client + " caught " + ex.toString());
		//            }
		//        }
	}

	@Override
	public void flashConfiguration (Biasgen biasgen){
		log.warning("Flash configuration not supported by UDP devices.");
	}

	@Override
	public byte[] formatConfigurationBytes (Biasgen biasgen){
		throw new UnsupportedOperationException("Not supported yet.");// TODO use this to send all biases at once?
	}

	@Override
	public String toString() {
		return "UDP: SmartEyeTDS";
	}

}
