package at.ait.dss.sni.jaer.chip.atis;
/*
created 8.5.2009 for ARC ATIS chip in sardinia at capo caccio workshop on cognitive neuromorphic engineering.

 * This is part of jAER
<a href="http://jaer.wiki.sourceforge.net">jaer.wiki.sourceforge.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.

 */
import ch.unizh.ini.jaer.chip.retina.AERetina;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import javax.swing.JPanel;
import net.sf.jaer.aemonitor.AEListener;
import net.sf.jaer.aemonitor.AEMonitorInterface;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.biasgen.Pot;
import net.sf.jaer.biasgen.PotArray;
import net.sf.jaer.biasgen.VDAC.DAC;
import net.sf.jaer.biasgen.VDAC.VPot;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.chip.RetinaExtractor;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventio.AEUnicastInput;
import net.sf.jaer.graphics.DisplayMethod;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
/**
 * ARC ATIS 304x240
 * @author tobi delbruck, martin lizzenberger, chr posch
 *
 */
public class ATIS304 extends AERetina{
    public static String getDescription (){
        return "ATIS304 304x240 Asynchronous Time Based Image Sensor";
    }

    public ATIS304 (){
        setName("ATIS304");
        setSizeX(304);
        setSizeY(256);
        setNumCellTypes(2); // two are polarity and last is intensity
        setPixelHeightUm(30);
        setPixelWidthUm(30);
        setEventExtractor(new ATIS304xtractor(this));
        DisplayMethod m = getCanvas().getDisplayMethod(); // get default method
        getCanvas().removeDisplayMethod(m);
//        setBiasgen(new ATIS304_Biasgen(this));
//        setHardwareInterface(new ATIS304_HardwareInterface());
    }
    /** The event extractor. Each pixel has two polarities 0 and 1.
     * <p>
     *The bits in the raw data coming from the device are as follows.
     * <p>
     */
    public class ATIS304xtractor extends RetinaExtractor{
        public static final int XMASK = 0x03fe, XSHIFT = 1, YMASK = 0x03fc00, YSHIFT = 10, POLARITY_MASK = 0x01;

        public ATIS304xtractor (ATIS304 chip){
            super(chip);
        }

        /** extracts the meaning of the raw events.
         *@param in the raw events, can be null
         *@return out the processed events. these are partially processed in-place. empty packet is returned if null is supplied as in.
         */
        @Override
        synchronized public EventPacket extractPacket (AEPacketRaw in){
            if ( out == null ){
                out = new EventPacket<PolarityEvent>(chip.getEventClass());
            } else{
                out.clear();
            }
            if ( in == null ){
                return out;
            }
            int n = in.getNumEvents(); //addresses.length;

            int skipBy = 1;
            if ( isSubSamplingEnabled() ){
                while ( n / skipBy > getSubsampleThresholdEventCount() ){
                    skipBy++;
                }
            }
            int[] a = in.getAddresses();
            int[] timestamps = in.getTimestamps();
            OutputEventIterator outItr = out.outputIterator();
            for ( int i = 0 ; i < n ; i += skipBy ){ // bug here
                int addr = a[i];
                PolarityEvent e = (PolarityEvent)outItr.nextOutput();
                e.timestamp = ( timestamps[i] );
                e.x = (short)( ( ( addr & XMASK ) >>> XSHIFT ) );
                e.y = (short)( ( addr & YMASK ) >>> YSHIFT );
                e.type = (byte)( addr & 1 );
                e.polarity = e.type == 1 ? PolarityEvent.Polarity.Off : PolarityEvent.Polarity.On;
                if ( e.x < 0 || e.x >= sizeX || e.y < 0 || e.y >= sizeY ){
                    log.warning(e.toString() + " is outside size of chip"); // TODO should remove for speed once problems with y address outside legal range are sorted out
                }

            }
            return out;
        }
    }
//    /**
//     * Encapsulates biases on ATIS, which are contolled by UDP datagrams here.
//     * @author tobi
//     */
//    class ATIS304_Biasgen extends Biasgen{
//        public ATIS304_Biasgen (Chip chip){
//            super(chip);
//            DAC dac = new DAC(16,12,0,3.3f,3.3f);
//            potArray = new PotArray(this);
//            //UDP_VPot (Chip chip,String name,DAC dac,int channel,Type type,Sex sex,int bitValue,int displayPosition,String tooltipString)
//            potArray.addPot(new UDP_VPot(chip,"cas",dac,5,Pot.Type.NORMAL,Pot.Sex.P,0,0,"photoreceptor"));
//            potArray.addPot(new UDP_VPot(chip,"inv",dac,6,Pot.Type.NORMAL,Pot.Sex.P,0,0,"photoreceptor"));
//            potArray.addPot(new UDP_VPot(chip,"diffOff",dac,7,Pot.Type.NORMAL,Pot.Sex.P,0,0,"photoreceptor"));
//            potArray.addPot(new UDP_VPot(chip,"diffOn",dac,8,Pot.Type.NORMAL,Pot.Sex.P,0,0,"photoreceptor"));
//            potArray.addPot(new UDP_VPot(chip,"diff",dac,9,Pot.Type.NORMAL,Pot.Sex.P,0,0,"photoreceptor"));
//            potArray.addPot(new UDP_VPot(chip,"foll",dac,10,Pot.Type.NORMAL,Pot.Sex.P,0,0,"photoreceptor"));
//            potArray.addPot(new UDP_VPot(chip,"refr",dac,11,Pot.Type.NORMAL,Pot.Sex.P,0,0,"photoreceptor"));
//            potArray.addPot(new UDP_VPot(chip,"pr",dac,12,Pot.Type.NORMAL,Pot.Sex.P,0,0,"photoreceptor"));
//            potArray.addPot(new UDP_VPot(chip,"bulk",dac,13,Pot.Type.NORMAL,Pot.Sex.P,0,0,"photoreceptor"));
//        }
//
//        @Override
//        public JPanel buildControlPanel (){
//            ATIS304_ControlPanel myControlPanel = new ATIS304_ControlPanel(ATIS304.this);
//            return myControlPanel;
//        }
//    }
//    public class UDP_VPot extends VPot{
//        public UDP_VPot (Chip chip,String name,DAC dac,int channel,Type type,Sex sex,int bitValue,int displayPosition,String tooltipString){
//            super(chip,name,dac,channel,type,sex,bitValue,displayPosition,tooltipString);
//        }
//
//        public String getCommandString (){
//            int v = getBitValue();
//            int n = getChannel();
//            String s = String.format("d %d %d mv\n",n,v);
//            return s;
//        }
//    }
//    class ATIS304_HardwareInterface implements BiasgenHardwareInterface,AEMonitorInterface{
////        private DatagramChannel controlChannel = null;
//        private DatagramSocket socket = null;
//        final int DATA_PORT = 22222;
//        final int CONTROL_PORT = 20010;
//        AEUnicastInput input = null;
//        InetSocketAddress client = null;
//        private String host = "172.25.48.35"; // "localhost"
//        private int port = CONTROL_PORT;
//
//        public ATIS304_HardwareInterface (){
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
//                socket = new DatagramSocket(CONTROL_PORT);
////                controlChannel = DatagramChannel.open();
////                socket = controlChannel.socket(); // bind to any available port because we will be sending datagrams with included host:port info
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
//                String s=enable?"t+\n":"t-\n";
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
//            return ATIS304.this;
//        }
//    }
}
