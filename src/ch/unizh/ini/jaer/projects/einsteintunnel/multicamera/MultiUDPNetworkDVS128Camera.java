/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.einsteintunnel.multicamera;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Iterator;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

import net.sf.jaer.aemonitor.AENetworkRawPacket;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.MultiChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.hardwareinterface.udp.NetworkChip;
import ch.unizh.ini.jaer.chip.retina.DVS128;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;


/**
 * Encapsulates a whole bunch of networked TDS cameras into this single AEChip object. The size of this virtual chip is set by {@link #MAX_NUM_CAMERAS} times
 * {@link #CAM_WIDTH}.
 *
 * @author tobi delbruck, christian braendli
 */
@Description("Encapsulates a whole bunch of networked UDP DVS128 cameras into this single AEChip object")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class MultiUDPNetworkDVS128Camera extends DVS128 implements NetworkChip, MultiChip{

    /** Maximum number of network cameras in the linear array. */
    public static final int MAX_NUM_CAMERAS = 10;
    /** Width in pixels of each camera - same as DVS128. */
    public static final int CAM_WIDTH = 128;

    private String cameraDomain = "192.168.100.";
    private int cameraDomainOffset = 20;
    private boolean useTunnelRotaion = false;
    private int numChips = 1; // actual number of cameras we've gotten data from
    private static final String CLIENT_MAPPING_LIST_PREFS_KEY = "MultiUDPNetworkDVS128Camera.camHashLlist";  // preferences key for mapping table
    private JMenu chipMenu = null; // menu for specialized control
    private CameraMapperDialog cameraMapperDialog = null;
	private PowerSettingsDialog powerSettingsDialog = null;
	private CameraMap cameraMap = new CameraMap(); // the mapping from InetSocketAddress to camera position
    private MultiUDPNetworkDVS128CameraDisplayMethod displayMethod=null;

    private String localhost; // "localhost"
    private int controlPort;
    private DatagramSocket outputSocket = null;
    private InetSocketAddress cameraSocketAddress = null;

    public static final int STREAM_PORT = 20020;
    public static final int CONNECT_PORT = 20019;
    public static final int CONTROL_PORT = 20010;

    public int selChip = -1;

    public MultiUDPNetworkDVS128Camera() {
        setName("MultiUDPNetworkDVS128Camera");

        setSizeX(MAX_NUM_CAMERAS * 128);
        setSizeY(128);
        setEventExtractor(new Extractor(this));
        chipMenu = new JMenu("MultiCamera");
        chipMenu.getPopupMenu().setLightWeightPopupEnabled(false);
        chipMenu.add(new JMenuItem(new ShowClientMapperAction()));
        loadClientMappingPrefs();
        displayMethod=new MultiUDPNetworkDVS128CameraDisplayMethod(getCanvas());
        getCanvas().addDisplayMethod(displayMethod);
        getCanvas().setDisplayMethod(displayMethod);
        chipMenu.add(new JCheckBoxMenuItem(new DisplayCameraInfoAction(displayMethod)));
		chipMenu.add(new JMenuItem(new ShowPowerSettingsAction()));
        activateCameras();
  }


    private void activateCameras() {
        try{
            controlPort = CONTROL_PORT;
            localhost = "localhost";
            outputSocket = new DatagramSocket(CONNECT_PORT);
            outputSocket.setSoTimeout(100);
        } catch ( IOException ex ){
            log.warning(ex.toString());
        }
        log.info("sending activation commands to cameras");
        for(int i=cameraDomainOffset; i<=(cameraDomainOffset+MAX_NUM_CAMERAS); i++){
            String s = "t+\r\n";
            byte[] b = s.getBytes();
            try{
                InetAddress IPAddress =  InetAddress.getByName(cameraDomain+i);
                cameraSocketAddress = new InetSocketAddress(IPAddress,controlPort);
                log.info("send "+b+" to "+IPAddress.getHostAddress()+":"+controlPort);
                DatagramPacket d = new DatagramPacket(b,b.length,cameraSocketAddress);
                if (outputSocket != null){
					//repeat the sending 10 to be sure of data transmission
					for(int j=0; j<10; j++){
						outputSocket.send(d);
					}
                }
            } catch ( Exception e ){
                log.warning(e.toString());
            }
        }
    }

    private void showCameraMapperDialog() {
        if (cameraMapperDialog == null) {
            cameraMapperDialog = new CameraMapperDialog(getAeViewer(), false, this);
        }
        cameraMapperDialog.setVisible(true);
    }

	private void showPowerSettingsDialog() {
        if (powerSettingsDialog == null) {
            powerSettingsDialog = new PowerSettingsDialog(getAeViewer(), false, this);
        }
        powerSettingsDialog.setVisible(true);
    }

    /** Overrides to appendCopy the menu. */
    @Override
    public void setAeViewer(AEViewer v) {
        if (v != null) {
            v.addMenu(chipMenu);
        }
    }

    /**
     * Returns the client map.
     * @return the clientMap
     */
    public CameraMap getCameraMap() {
        return cameraMap;
    }

    /**
     * Sets the client map.
     * @param clientMap the clientMap to set
     */
    synchronized public void setCameraMap(CameraMap clientMap) {
        this.cameraMap = clientMap;
    }

    /** The event extractor. Extracts events from the raw network input using the source IP:port information to map x addresses to position along the array.
     * <p>
     * DVS128 has two polarities 0 and 1. Here the polarity is flipped by the extractor so that the raw polarity 0 becomes 1
    in the extracted event. The ON events have raw polarity 0.
    1 is an ON event after event extraction, which flips the type. Raw polarity 1 is OFF event, which becomes 0 after extraction.
     */
    public class Extractor extends DVS128.Extractor {

        // these are for src raw addresses, from TDS cameras, in original DVS/tmpdiff128 address space
        private final short XMASK_SRC = 0xfe, XSHIFT_SRC = 1, YMASK_SRC = 0x7f00, YSHIFT_SRC = 8;

        // final adddress space is 128 high by 1280 wide, need 7 bits for y, 11 bits for x, 1 for polarity
        // xmask =    1111 1111 1110 = 0xffe
        // ymask = 0111 1111 0000 0000 0000 = 0x7f000
        private final int XMASK_DEST = 0xffe, XSHIFT_DEST = 1, YMASK_DEST = 0x7f000, YSHIFT_DEST = 12; // these are for destination raw addresses, after combining cameras


        public Extractor(DVS128 chip) {
            super(chip);
            setXmask((short) XMASK_DEST);
            setXshift((byte) XSHIFT_DEST);
            setYmask((short) YMASK_DEST);
            setYshift((byte) YSHIFT_DEST);
            setTypemask((short) 1);
            setTypeshift((byte) 0);
            setFlipx(false);
            setFlipy(false);
            setFliptype(true);
        }

        /** Extracts the meaning of the raw events.
         *@param in the raw events, can be null
         *@return out the processed events. these are partially processed in-place. empty packet is returned if null is supplied as in. This event packet is reused
         * and should only be used by a single thread of execution or for a single input stream, or mysterious results may occur!
         */
        @Override
        public synchronized EventPacket extractPacket(AEPacketRaw in) {
            if (out == null) {
                out = new EventPacket<PolarityEvent>(getChip().getEventClass());
            } else {
                out.clear();
            }
            if (!(in instanceof AENetworkRawPacket)) {
                // data is probably coming from a recording, where the address translation has already been applied
//                log.warning("input packet is not AENetworkRawPacket - cannot determine client camera addresses");
                extractRecordedPacket(in, out);
            } else {
                AENetworkRawPacket netPacket = (AENetworkRawPacket) in;
                extractNetworkPacket(netPacket, out);
            }
            return out;
        }

        /**
         * Extracts the meaning of the raw events and re-maps them to proper location in the larger virtual AEChip object.
         *
         * @param in 		the raw events, can be null, this clears output packet
         * @param out 		the processed events. these are partially processed in-place. empty packet is returned if null is
         * 					supplied as input.
         */
        public void extractRecordedPacket(AEPacketRaw in, EventPacket out) {
            if (in == null) {
                out.clear();
                return;
            }
            int n = in.getNumEvents(); //addresses.length;

            int skipBy = 1;
            if (isSubSamplingEnabled()) {
                while ((n / skipBy) > getSubsampleThresholdEventCount()) {
                    skipBy++;
                }
            }
            int[] a = in.getAddresses();
            int[] timestamps = in.getTimestamps();
            OutputEventIterator outItr = out.outputIterator();
            for (int i = 0; i < n; i += skipBy) {

                int addr = a[i]; // TODO handle special events from hardware correctly
                PolarityEvent e = (PolarityEvent) outItr.nextOutput();
                e.timestamp = (timestamps[i]);
                e.type = (byte) ((1 - addr) & 1);
                e.polarity = e.type == 0 ? PolarityEvent.Polarity.Off : PolarityEvent.Polarity.On;
                e.x = (((short) (((addr & XMASK_DEST) >>> XSHIFT_DEST))));
                e.y = (short) ((addr & YMASK_DEST) >>> YSHIFT_DEST);
                e.address = addr ; // new raw address is now suitable for logging and later playback
            }
        }

        /**
         * Extracts the meaning of the raw events and re-maps them to proper location in the larger virtual AEChip object.
         *
         * @param in 		the raw events, can be null, this clears output packet
         * @param out 		the processed events. these are partially processed in-place. empty packet is returned if null is
         * 					supplied as input.
         */
        public void extractNetworkPacket(AENetworkRawPacket in, EventPacket out) {
            if (in == null) {
                out.clear();
                return;
            }
            int n = in.getNumEvents(); //addresses.length;

            AENetworkRawPacket.EventSourceList eventSourceList = in.getEventSourceList(); // list of clients in this raw packet
            if ((eventSourceList == null) || eventSourceList.isEmpty()) {
                log.warning("AENetworkRawPacket  has no client info");
                out.clear();
            }
            AENetworkRawPacket.EventSourceInfo thisSourceInfo = null, nextSourceInfo = null; // current and next event sources

            // we get this client and next client from list. As long as index is
            // less than next client's starting index (or next client is null) we use this clients position.
            // When the index gets >= to next client's starting index, we set this client to next client and get next client.
            Iterator<AENetworkRawPacket.EventSourceInfo> eventSourceItr = eventSourceList.iterator();

            if(!eventSourceItr.hasNext()){
                log.warning("no event source found in the iterator because there are no elements, aborting packet extraction");
                out.clear();
                return;
            }
            thisSourceInfo = eventSourceItr.next();  // get the first client in the packet
            if (eventSourceItr.hasNext()) {
                nextSourceInfo = eventSourceItr.next();
            }
            int nextSourceIndex = nextSourceInfo == null ? Integer.MAX_VALUE : nextSourceInfo.getStartingIndex();
            int cameraLocation = cameraMap.maybeAddCamera(thisSourceInfo.getClient());

            int skipBy = 1;
            if (isSubSamplingEnabled()) {
                while ((n / skipBy) > getSubsampleThresholdEventCount()) {
                    skipBy++;
                }
            }
            int sxm = sizeX - 1;
            int[] a = in.getAddresses();
            int[] timestamps = in.getTimestamps();
            OutputEventIterator outItr = out.outputIterator();
            for (int i = 0; i < n; i += skipBy) {
                // for each raw event, if its index is >= than the nextSourceIndex, make thisSourceInfo be the nextSourceIndex,
                // maybe cache the camera, and set the location. Also get the nextSourceInfo
                if (i >= nextSourceIndex) {
                    thisSourceInfo = nextSourceInfo;
                    if (nextSourceInfo != null) {
                        cameraLocation = cameraMap.maybeAddCamera(thisSourceInfo.getClient());
                    }
                    if (eventSourceItr.hasNext()) {
                        nextSourceInfo = eventSourceItr.next();
                        nextSourceIndex = nextSourceInfo.getStartingIndex();
                    }
                }

                int addr = a[i]; // TODO handle special events from hardware correctly
                PolarityEvent e = (PolarityEvent) outItr.nextOutput();
                int cameraShift = (MAX_NUM_CAMERAS-1-cameraLocation) * CAM_WIDTH;
                e.timestamp = (timestamps[i]);
                e.type = (byte) ((1 - addr) & 1);
                e.polarity = e.type == 0 ? PolarityEvent.Polarity.Off : PolarityEvent.Polarity.On;
                if(useTunnelRotaion){
                    e.x = (short) (sxm - ((short) (cameraShift + ((addr & YMASK_SRC) >>> YSHIFT_SRC))));
                    e.y = (short) ((addr & XMASK_SRC) >>> XSHIFT_SRC);
                } else {
                    e.x = (short) (sxm - ((short) (cameraShift + ((addr & XMASK_SRC) >>> XSHIFT_SRC))));
                    e.y = (short) ((addr & YMASK_SRC) >>> YSHIFT_SRC);
                }
                e.address = (addr&1) | (e.x << XSHIFT_DEST) | (e.y << YSHIFT_DEST); // new raw address is now suitable for logging and later playback
                a[i] = e.address;  // replace raw address in raw packet as well

            }
        }
    }

    private void setDisplayCameraInfo(boolean selected) {
        displayMethod.setDisplayInfo(selected);
    }

    /** Loads camera mapping from preferences for this AEChip.
     *
     */
    public final void loadClientMappingPrefs() {
        try {
            byte[] bytes = getPrefs().getByteArray(CLIENT_MAPPING_LIST_PREFS_KEY, null);
            if (bytes != null) {
                ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes));
                cameraMap = (CameraMap) in.readObject();
                in.close();
                numChips = cameraMap.size(); // TODO will grow with old cameras
            } else {
                log.info("no previous clients found - will cache them as data come in");
                numChips = 1;
            }
        } catch (Exception e) {
            log.warning("caught " + e + " in constructor");
        }
    }

    /** Saves camera mapping to preferences for this AEChip.
     *
     */
    public void saveClientMappingPrefs() {
        if (cameraMap == null) {
            log.warning("clientMap==null, no mapping to save to preferences");
            return;
        }
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(2048);
            ObjectOutputStream os = new ObjectOutputStream(bos);
            os.writeObject(cameraMap);
            getPrefs().putByteArray(CLIENT_MAPPING_LIST_PREFS_KEY, bos.toByteArray());
            log.info("wrote client mapping holding " + cameraMap.size() + " clients to " + getPrefs());
        } catch (Exception e) {
            log.warning(e.toString());
        }

    }

    private class DisplayCameraInfoAction extends AbstractAction {

        public DisplayCameraInfoAction(MultiUDPNetworkDVS128CameraDisplayMethod displayMethod) {
            putValue(NAME, "Display camera info");
            putValue(MNEMONIC_KEY, KeyEvent.VK_I);
            putValue(SHORT_DESCRIPTION, "Displays camera information on the output");
            putValue(SELECTED_KEY,displayMethod.isDisplayInfo());
        }

        @Override
		public void actionPerformed(ActionEvent e) {
            if (e.getSource() instanceof AbstractButton) {
                AbstractButton b = (AbstractButton) e.getSource();
                setDisplayCameraInfo(b.isSelected());
            }
        }
    }

    private class ShowClientMapperAction extends AbstractAction {

        public ShowClientMapperAction() {
            putValue(NAME, "Map camera locations");
            putValue(MNEMONIC_KEY, KeyEvent.VK_S);
            putValue(SHORT_DESCRIPTION, "Shows a dialog to configure mapping from source IP:port to camera position");
        }

        @Override
		public void actionPerformed(ActionEvent e) {
            showCameraMapperDialog();
        }
    }

	private class ShowPowerSettingsAction extends AbstractAction {

        public ShowPowerSettingsAction() {
            putValue(NAME, "UDP Power tools");
            putValue(MNEMONIC_KEY, KeyEvent.VK_S);
            putValue(SHORT_DESCRIPTION, "Allows to switch the power of ");
        }

        @Override
		public void actionPerformed(ActionEvent e) {
            showPowerSettingsDialog();
        }
    }

    //MultiChip interface

    @Override
    public int getNumChips(){
        return numChips;
    }

    @Override
    public void setNumChips(int numChips){
        this.numChips = numChips;
    }

    @Override
    public int getSelectedChip(){
        return selChip;
    }

    @Override
    public void setSelectedChip(int selChip){
        this.selChip = selChip;
    }

    //NetworkChip interface
    /**
     * Returns the address of the first map entry.
     *
     * @return address of first map entry
     */
    @Override
    public InetSocketAddress getAddress(){
        return cameraMap.getPositionMap().get(0);
    }

    /**
     * Sets the address of the first map entry
     */
    @Override
    public void setAddress(InetSocketAddress address){
        cameraMap.getPositionMap().put(0, address);
    }

//       // following not used yet
//    class MultiUDPNetworkCamera_HardwareInterface implements BiasgenHardwareInterface,AEMonitorInterface{
////        private DatagramChannel controlChannel = null;
//        private DatagramSocket socket = null;
//        final int DATA_PORT = 22222;
//        final int CONTROL_PORT = 20010;
//        AEUnicastInput input = null;
//        InetSocketAddress client = null;
//        private String host = getPrefs().get("ATIS304.host","172.25.48.35"); // "localhost"
//        private int port = getPrefs().getInt("controlPort",CONTROL_PORT);
//
//        public MultiUDPNetworkCamera_HardwareInterface (){
//        }
//
//        public String getTypeName (){
//            return "ATIS304 UDP control interface";
//        }
//
//        public void close (){
//            if ( socket != null ){
//                socket.close();
//            }
//            if ( input != null ){
//                input.close();
//            }
//        }
//
//        public void open () throws HardwareInterfaceException{
//            try{
//                if ( socket != null ){
//                    socket.close();
//                }
//                socket = new DatagramSocket(CONTROL_PORT);
//                socket.setSoTimeout(100);
//
//                input = new AEUnicastInput(DATA_PORT);
//                input.setSequenceNumberEnabled(false);
//                input.setAddressFirstEnabled(true);
//                input.setSwapBytesEnabled(false);
//                input.set4ByteAddrTimestampEnabled(true);
//                input.setTimestampsEnabled(false);
//                input.setBufferSize(1200);
//                input.setTimestampMultiplier(1);
//                input.setPort(DATA_PORT);
//                input.open();
//            } catch ( IOException ex ){
//                throw new HardwareInterfaceException(ex.toString());
//            }
//        }
//
//        /** returns true if socket exists and is bound */
//        private boolean checkClient (){
//            if ( socket == null ){
//                return false;
//            }
//            if ( client != null ){
//                return true;
//            }
//            try{
//                client = new InetSocketAddress(host,port);
//                return true;
//
//            } catch ( Exception se ){ // IllegalArgumentException or SecurityException
//                log.warning("While checking client host=" + host + " port=" + port + " caught " + se.toString());
//                return false;
//            }
//        }
//
//        public boolean isOpen (){
//            if ( socket == null ){
//                return false;
//            }
//            if ( checkClient() == false ){
//                return false;
//            }
//            return true;
//        }
//
//        public void setPowerDown (boolean powerDown) throws HardwareInterfaceException{
////            throw new UnsupportedOperationException("Not supported yet.");
//        }
//
//        public void sendConfiguration (Biasgen biasgen) throws HardwareInterfaceException{
//            if ( !isOpen() ){
//                throw new HardwareInterfaceException("not open");
//            }
//            int MAX_COMMAND_LENGTH_BYTES = 256;
//            for ( Pot p:getBiasgen().getPotArray().getPots() ){
//                UDP_VPot vp = (UDP_VPot)p;
//                try{
//                    String s = vp.getCommandString();
//                    byte[] b = s.getBytes(); // s.getBytes(Charset.forName("US-ASCII"));
//                    socket.send(new DatagramPacket(b,b.length,client));
//                    DatagramPacket packet = new DatagramPacket(new byte[ MAX_COMMAND_LENGTH_BYTES ],MAX_COMMAND_LENGTH_BYTES);
//                    socket.receive(packet);
//                    ByteArrayInputStream bis;
//                    BufferedReader reader = new BufferedReader(new InputStreamReader(( bis = new ByteArrayInputStream(packet.getData(),0,packet.getLength()) )));
//                    String line = reader.readLine(); // .toLowerCase();
//                    log.info("response from " + packet.getAddress() + " : " + line);
////                    System.out.println(line); // debug
//                } catch ( SocketTimeoutException to ){
//                    log.warning("timeout on waiting for command response on datagram control socket");
//                } catch ( Exception ex ){
//                    throw new HardwareInterfaceException("while sending biases to " + client + " caught " + ex.toString());
//                }
//            }
//        }
//
//        public void flashConfiguration (Biasgen biasgen) throws HardwareInterfaceException{
//            throw new UnsupportedOperationException("Not supported yet.");
//        }
//
//        public byte[] formatConfigurationBytes (Biasgen biasgen){
//            throw new UnsupportedOperationException("Not supported yet."); // TODO use this to send all biases at once?
//        }
//
//        public AEPacketRaw acquireAvailableEventsFromDriver () throws HardwareInterfaceException{
//            if ( input == null ){
//                throw new HardwareInterfaceException("no input connection");
//            }
//            return input.readPacket();
//        }
//
//        public int getNumEventsAcquired (){
//            if ( input == null ){
//                return 0;
//            }
//            return 0; // fix AEUnicastInput to return current number of events acquired
//        }
//
//        public AEPacketRaw getEvents (){
//            if ( input == null ){
//                return null;
//            }
//            return input.readPacket();
//        }
//
//        public void resetTimestamps (){
//        }
//
//        public boolean overrunOccurred (){
//            return false;
//        }
//
//        public int getAEBufferSize (){
//            if ( input == null ){
//                return 0;
//            }
//            return input.getBufferSize();
//        }
//
//        public void setAEBufferSize (int AEBufferSize){
//            if ( input == null ){
//                return;
//            }
//            input.setBufferSize(AEBufferSize);
//        }
//
//        public void setEventAcquisitionEnabled (boolean enable) throws HardwareInterfaceException{
//            if ( input != null ){
//                input.setPaused(enable);
//            }
//            if ( isOpen() ){
//                String s = enable ? "t+\n" : "t-\n";
//                byte[] b = s.getBytes();
//                try{
//                    DatagramPacket d = new DatagramPacket(b,b.length,client);
//                    socket.send(d);
//                } catch ( Exception e ){
//                    log.warning(e.toString());
//                }
//            }
//        }
//
//        public boolean isEventAcquisitionEnabled (){
//            return isOpen();
//        }
//
//        public void addAEListener (AEListener listener){
//        }
//
//        public void removeAEListener (AEListener listener){
//        }
//
//        public int getMaxCapacity (){
//            return 1000000;
//        }
//        private int estimatedEventRate = 0;
//
//        public int getEstimatedEventRate (){
//            return estimatedEventRate;
//        }
//
//        /** computes the estimated event rate for a packet of events */
//        private void computeEstimatedEventRate (AEPacketRaw events){
//            if ( events == null || events.getNumEvents() < 2 ){
//                estimatedEventRate = 0;
//            } else{
//                int[] ts = events.getTimestamps();
//                int n = events.getNumEvents();
//                int dt = ts[n - 1] - ts[0];
//                estimatedEventRate = (int)( 1e6f * (float)n / (float)dt );
//            }
//        }
//
//        public int getTimestampTickUs (){
//            return 1;
//        }
//
//        public void setChip (AEChip chip){
//        }
//
//        public AEChip getChip (){
//            return MultiUDPNetworkDVS128Camera.this;
//        }
//    }
//
//       public class UDP_VPot extends VPot{
//        public UDP_VPot (Chip chip,String name,DAC dac,int channel,Type type,Sex sex,int bitValue,int displayPosition,String tooltipString){
//            super(chip,name,dac,channel,type,sex,bitValue,displayPosition,tooltipString);
//        }
//
//        public String getCommandString (){
//            int v = Math.round(getVoltage() * 1000);
//            int n = getChannel();
//            String s = String.format("d %d %d mv\n",n,v);
//            return s;
//        }
//    }

}
