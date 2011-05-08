package at.ait.dss.sni.jaer.chip.atis;
/*
created 8.5.2009 for ARC ATIS chip in sardinia at capo caccio workshop on cognitive neuromorphic engineering.

 * This is part of jAER
<a href="http://jaer.wiki.sourceforge.net">jaer.wiki.sourceforge.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.

 */
import ch.unizh.ini.jaer.chip.retina.AETemporalConstastRetina;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.Observable;
import javax.swing.JPanel;
import net.sf.jaer.Description;
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
@Description("ATIS304 304x240 Asynchronous Time Based Image Sensor")
public class ATIS304 extends AETemporalConstastRetina{

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
        setBiasgen(new ATIS304_Biasgen(this));
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
                e.address=addr;
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
    /**
     * Encapsulates biases on ATIS, which are controlled by UDP datagrams here.
     * @author tobi
     */
    public class ATIS304_Biasgen extends Biasgen{
        private DatagramSocket socket = null;
        public static final int CONTROL_PORT = 20010;
        public static final String HOST = "172.25.48.35"; // TODO fix hardcoded host string, should implement Chip specific menu in AEViewer
        private InetSocketAddress client = null;
        private String host = getPrefs().get("ATIS304_Biasgen.host",HOST); // "localhost"
        private int port = getPrefs().getInt("ATIS304_Biasgen.port",CONTROL_PORT);
        private int ECHO_TIMEOUT_MS = 10;
//        private volatile boolean connFailed=false;

        public ATIS304_Biasgen (Chip chip){
            super(chip);
            DAC dac = new DAC(26,12,0,3.3f,3.3f);
            potArray = new PotArray(this);
            //UDP_VPot (Chip chip,String name,DAC dac,int channel,Type type,Sex sex,int bitValue,int displayPosition,String tooltipString)

            potArray.addPot(new UDP_VPot(chip,"APSvrefL",dac,0,Pot.Type.NORMAL,Pot.Sex.P,682,0,"unspecified"));
            potArray.addPot(new UDP_VPot(chip,"APSvrefH",dac,1,Pot.Type.NORMAL,Pot.Sex.P,1365,0,"unspecified"));
            potArray.addPot(new UDP_VPot(chip,"APSbiasOut",dac,2,Pot.Type.NORMAL,Pot.Sex.P,512,0,"unspecified"));
            potArray.addPot(new UDP_VPot(chip,"APSbiasHyst",dac,3,Pot.Type.NORMAL,Pot.Sex.P,409,0,"unspecified"));
            potArray.addPot(new UDP_VPot(chip,"APSbiasTail",dac,4,Pot.Type.NORMAL,Pot.Sex.P,477,0,"unspecified"));




            potArray.addPot(new UDP_VPot(chip,"cas",dac,5,Pot.Type.NORMAL,Pot.Sex.P,1365,0,"photoreceptor cascode"));
            potArray.addPot(new UDP_VPot(chip,"inv",dac,6,Pot.Type.NORMAL,Pot.Sex.P,546,0,"pixel request inverter"));
            potArray.addPot(new UDP_VPot(chip,"diffOff",dac,7,Pot.Type.NORMAL,Pot.Sex.P,238,0,"off threshold"));
            potArray.addPot(new UDP_VPot(chip,"diffOn",dac,8,Pot.Type.NORMAL,Pot.Sex.P,433,0,"on threshold"));
            potArray.addPot(new UDP_VPot(chip,"diff",dac,9,Pot.Type.NORMAL,Pot.Sex.P,341,0,"diff bias"));
            potArray.addPot(new UDP_VPot(chip,"foll",dac,10,Pot.Type.NORMAL,Pot.Sex.P,1979,0,"photoreceptor follower"));
            potArray.addPot(new UDP_VPot(chip,"refr",dac,11,Pot.Type.NORMAL,Pot.Sex.P,2048,0,"refractory period"));
            potArray.addPot(new UDP_VPot(chip,"pr",dac,12,Pot.Type.NORMAL,Pot.Sex.P,1911,0,"photoreceptor"));
            potArray.addPot(new UDP_VPot(chip,"bulk",dac,13,Pot.Type.NORMAL,Pot.Sex.P,1774,0,"switch bulk bias"));


            potArray.addPot(new UDP_VPot(chip,"CtrlbiasP",dac,14,Pot.Type.NORMAL,Pot.Sex.P,910,0,"unspecified"));
            potArray.addPot(new UDP_VPot(chip,"CtrlbiasLBBuff",dac,15,Pot.Type.NORMAL,Pot.Sex.P,682,0,"unspecified"));
            potArray.addPot(new UDP_VPot(chip,"CtrlbiasDelTD",dac,16,Pot.Type.NORMAL,Pot.Sex.P,341,0,"unspecified"));
            potArray.addPot(new UDP_VPot(chip,"CtrlbiasseqDelAPS",dac,17,Pot.Type.NORMAL,Pot.Sex.P,398,0,"unspecified"));
            potArray.addPot(new UDP_VPot(chip,"CtrlbiasDelAPS",dac,18,Pot.Type.NORMAL,Pot.Sex.P,455,0,"unspecified"));
            potArray.addPot(new UDP_VPot(chip,"biasSendReqPdY",dac,19,Pot.Type.NORMAL,Pot.Sex.P,1137,0,"unspecified"));
            potArray.addPot(new UDP_VPot(chip,"biasSendReqPdX",dac,20,Pot.Type.NORMAL,Pot.Sex.P,796,0,"unspecified"));
            potArray.addPot(new UDP_VPot(chip,"CtrlBiasGB",dac,21,Pot.Type.NORMAL,Pot.Sex.P,1080,0,"unspecified"));
            potArray.addPot(new UDP_VPot(chip,"TDbiasReqPuY",dac,22,Pot.Type.NORMAL,Pot.Sex.P,910,0,"unspecified"));
            potArray.addPot(new UDP_VPot(chip,"TDbiasReqPuX",dac,23,Pot.Type.NORMAL,Pot.Sex.P,1422,0,"unspecified"));
            potArray.addPot(new UDP_VPot(chip,"APSbiasReqPuY",dac,24,Pot.Type.NORMAL,Pot.Sex.P,1251,0,"unspecified"));
            potArray.addPot(new UDP_VPot(chip,"APSbiasReqPuX",dac,25,Pot.Type.NORMAL,Pot.Sex.P,1024,0,"unspecified"));

            try{
                open();
            }catch(HardwareInterfaceException e){
                log.warning(e.toString());
            }
        }

        @Override
        public JPanel buildControlPanel (){
            ATIS304_ControlPanel myControlPanel = new ATIS304_ControlPanel(ATIS304.this);
            return myControlPanel;
        }

        /** called when observable (masterbias) calls notifyObservers.
        Sets the powerDown state.
        If there is not a batch edit occurring, opens device if not open and calls sendConfiguration.
         */
        @Override
        public void update (Observable observable,Object object){
            if ( object != null && object.equals("powerDownEnabled") ){
                log.warning("no powerdown capability");
            } else if ( object instanceof UDP_VPot ){
                UDP_VPot vp = (UDP_VPot)object;
                try{
                    if ( !isBatchEditOccurring() ){
                        if ( !isOpen() ){
                            open();
                        }
                    }
                    try{
                        String s = vp.getCommandString();
                        log.info("sending " + vp + " with command " + s);
                        sendString(s);
                        printEchoDatagram();
                        printEchoDatagram();
                        try{
                            Thread.sleep(10);
                        } catch ( InterruptedException e ){
                        }
                    } catch ( SocketTimeoutException to ){
                        throw new HardwareInterfaceException("timeout on sending datagram for " + vp);
                    } catch ( Exception ex ){
                        throw new HardwareInterfaceException("while sending biases to " + client + " caught " + ex.toString());
                    }
                } catch ( HardwareInterfaceException e ){
//                    connFailed=true;
                    log.warning("error sending pot value for Pot " + object + " : " + e);
                }
            }
        }

        void printEchoDatagram (){
//            if(connFailed) return;
            if(socket==null){
                log.warning("null socket, maybe open() not called?");
                return;
            }
            int MAX_COMMAND_LENGTH_BYTES = 1500;
            DatagramPacket packet = new DatagramPacket(new byte[ MAX_COMMAND_LENGTH_BYTES ],MAX_COMMAND_LENGTH_BYTES);
           try{
                socket.receive(packet);
                ByteArrayInputStream bis;
                BufferedReader reader = new BufferedReader(new InputStreamReader(( bis = new ByteArrayInputStream(packet.getData(),0,packet.getLength()) )));
                String line = reader.readLine(); // .toLowerCase();
                log.info("response from " + packet.getAddress() + " : " + line);
            } catch ( SocketTimeoutException to ){
                log.warning("timeout on waiting for command response");
            } catch ( Exception ex ){
                log.warning("caught " + ex);
            }
        }

        /**
         * @return the host
         */
        public String getHost (){
            return host;
        }

        /**
         * @param host the host to set
         */
        public void setHost (String host){
            this.host = host;
            getPrefs().put("ATIS304_Biasgen.host",host);
        }

        /**
         * @return the port
         */
        public int getPort (){
            return port;
        }

        /**
         * @param port the port to set
         */
        public void setPort (int port){
            int old = port;
            try{
                close();
                this.port = port;
                open();
                getPrefs().putInt("ATIS304_Biasgen.port",port);
            } catch ( HardwareInterfaceException ex ){
                log.warning(ex.toString());
                this.port = old;
            }
        }

        @Override
        public void open () throws HardwareInterfaceException{
            try{
                if ( socket != null ){
                    socket.close();
                }

                socket = new DatagramSocket(port);
                socket.setSoTimeout(ECHO_TIMEOUT_MS);
                setEventAcquisitionEnabled(true);

            } catch ( IOException ex ){
                throw new HardwareInterfaceException(ex.toString());
            }
        }

        public void setEventAcquisitionEnabled (boolean yes) throws IOException{
            String s = yes ? "t+" : "t-";
            sendString(s);
            printEchoDatagram();
            printEchoDatagram();
        }

        private void sendString (String s) throws IOException{
//            if(connFailed) return;
            if ( checkClient(host,port) ){
                DatagramPacket d = new DatagramPacket(s.getBytes(),s.length(),client);
                socket.send(d);
            }
        }

        /** returns true if socket exists and is bound */
        private boolean checkClient (String host,int port){
            if ( socket == null ){
                return false;
            }
            if ( client != null && getHost().equals(host) && getPort() == port ){
                return true;
            }
            try{
                client = new InetSocketAddress(host,port);
//                connFailed=false;
                return true;

            } catch ( Exception se ){ // IllegalArgumentException or SecurityException
                log.warning("While checking client host=" + host + " port=" + port + " caught " + se.toString());
                return false;
            }
        }

        @Override
        public boolean isOpen (){
            if ( socket == null ){
                return false;
            }
            if ( checkClient(host,port) == false ){
                return false;
            }
            return true;
        }
    }
    public class UDP_VPot extends VPot{
        public UDP_VPot (Chip chip,String name,DAC dac,int channel,Type type,Sex sex,int bitValue,int displayPosition,String tooltipString){
            super(chip,name,dac,channel,type,sex,bitValue,displayPosition,tooltipString);
        }

        public String getCommandString (){
            int v = Math.round(getVoltage() * 1000);
            int n = getChannel();
            String s = String.format("d %d %d mv\n",n,v);
            return s;
        }
    }
    // following not used yet
    class ATIS304_HardwareInterface implements BiasgenHardwareInterface,AEMonitorInterface{
//        private DatagramChannel controlChannel = null;
        private DatagramSocket socket = null;
        final int DATA_PORT = 22222;
        final int CONTROL_PORT = 20010;
        AEUnicastInput input = null;
        InetSocketAddress client = null;
        private String host = getPrefs().get("ATIS304.host","172.25.48.35"); // "localhost"
        private int port = getPrefs().getInt("controlPort",CONTROL_PORT);

        public ATIS304_HardwareInterface (){
        }

        public String getTypeName (){
            return "ATIS304 UDP control interface";
        }

        public void close (){
            if ( socket != null ){
                socket.close();
            }
            if ( input != null ){
                input.close();
            }
        }

        public void open () throws HardwareInterfaceException{
            try{
                if ( socket != null ){
                    socket.close();
                }
                socket = new DatagramSocket(CONTROL_PORT);
                socket.setSoTimeout(100);

                input = new AEUnicastInput(DATA_PORT);
                input.setSequenceNumberEnabled(false);
                input.setAddressFirstEnabled(true);
                input.setSwapBytesEnabled(false);
                input.set4ByteAddrTimestampEnabled(true);
                input.setTimestampsEnabled(false);
                input.setBufferSize(1200);
                input.setTimestampMultiplier(1);
                input.setPort(DATA_PORT);
                input.open();
            } catch ( IOException ex ){
                throw new HardwareInterfaceException(ex.toString());
            }
        }

        /** returns true if socket exists and is bound */
        private boolean checkClient (){
            if ( socket == null ){
                return false;
            }
            if ( client != null ){
                return true;
            }
            try{
                client = new InetSocketAddress(host,port);
                return true;

            } catch ( Exception se ){ // IllegalArgumentException or SecurityException
                log.warning("While checking client host=" + host + " port=" + port + " caught " + se.toString());
                return false;
            }
        }

        public boolean isOpen (){
            if ( socket == null ){
                return false;
            }
            if ( checkClient() == false ){
                return false;
            }
            return true;
        }

        public void setPowerDown (boolean powerDown) throws HardwareInterfaceException{
//            throw new UnsupportedOperationException("Not supported yet.");
        }

        public void sendConfiguration (Biasgen biasgen) throws HardwareInterfaceException{
            if ( !isOpen() ){
                throw new HardwareInterfaceException("not open");
            }
            int MAX_COMMAND_LENGTH_BYTES = 256;
            for ( Pot p:getBiasgen().getPotArray().getPots() ){
                UDP_VPot vp = (UDP_VPot)p;
                try{
                    String s = vp.getCommandString();
                    byte[] b = s.getBytes(); // s.getBytes(Charset.forName("US-ASCII"));
                    socket.send(new DatagramPacket(b,b.length,client));
                    DatagramPacket packet = new DatagramPacket(new byte[ MAX_COMMAND_LENGTH_BYTES ],MAX_COMMAND_LENGTH_BYTES);
                    socket.receive(packet);
                    ByteArrayInputStream bis;
                    BufferedReader reader = new BufferedReader(new InputStreamReader(( bis = new ByteArrayInputStream(packet.getData(),0,packet.getLength()) )));
                    String line = reader.readLine(); // .toLowerCase();
                    log.info("response from " + packet.getAddress() + " : " + line);
//                    System.out.println(line); // debug
                } catch ( SocketTimeoutException to ){
                    log.warning("timeout on waiting for command response on datagram control socket");
                } catch ( Exception ex ){
                    throw new HardwareInterfaceException("while sending biases to " + client + " caught " + ex.toString());
                }
            }
        }

        public void flashConfiguration (Biasgen biasgen) throws HardwareInterfaceException{
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public byte[] formatConfigurationBytes (Biasgen biasgen){
            throw new UnsupportedOperationException("Not supported yet."); // TODO use this to send all biases at once?
        }

        public AEPacketRaw acquireAvailableEventsFromDriver () throws HardwareInterfaceException{
            if ( input == null ){
                throw new HardwareInterfaceException("no input connection");
            }
            return input.readPacket();
        }

        public int getNumEventsAcquired (){
            if ( input == null ){
                return 0;
            }
            return 0; // fix AEUnicastInput to return current number of events acquired
        }

        public AEPacketRaw getEvents (){
            if ( input == null ){
                return null;
            }
            return input.readPacket();
        }

        public void resetTimestamps (){
        }

        public boolean overrunOccurred (){
            return false;
        }

        public int getAEBufferSize (){
            if ( input == null ){
                return 0;
            }
            return input.getBufferSize();
        }

        public void setAEBufferSize (int AEBufferSize){
            if ( input == null ){
                return;
            }
            input.setBufferSize(AEBufferSize);
        }

        public void setEventAcquisitionEnabled (boolean enable) throws HardwareInterfaceException{
            if ( input != null ){
                input.setPaused(enable);
            }
            if ( isOpen() ){
                String s = enable ? "t+\n" : "t-\n";
                byte[] b = s.getBytes();
                try{
                    DatagramPacket d = new DatagramPacket(b,b.length,client);
                    socket.send(d);
                } catch ( Exception e ){
                    log.warning(e.toString());
                }
            }
        }

        public boolean isEventAcquisitionEnabled (){
            return isOpen();
        }

        public void addAEListener (AEListener listener){
        }

        public void removeAEListener (AEListener listener){
        }

        public int getMaxCapacity (){
            return 1000000;
        }
        private int estimatedEventRate = 0;

        public int getEstimatedEventRate (){
            return estimatedEventRate;
        }

        /** computes the estimated event rate for a packet of events */
        private void computeEstimatedEventRate (AEPacketRaw events){
            if ( events == null || events.getNumEvents() < 2 ){
                estimatedEventRate = 0;
            } else{
                int[] ts = events.getTimestamps();
                int n = events.getNumEvents();
                int dt = ts[n - 1] - ts[0];
                estimatedEventRate = (int)( 1e6f * (float)n / (float)dt );
            }
        }

        public int getTimestampTickUs (){
            return 1;
        }

        public void setChip (AEChip chip){
        }

        public AEChip getChip (){
            return ATIS304.this;
        }
    }
}
