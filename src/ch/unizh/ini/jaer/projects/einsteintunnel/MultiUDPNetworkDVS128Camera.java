/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.einsteintunnel;

import ch.unizh.ini.jaer.chip.retina.DVS128;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import net.sf.jaer.aemonitor.AENetworkRawPacket;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;

/**
 * Encapsulates a whole bunch of networked TDS cameras into this single AEChip object.
 *
 * @author tobi
 */
public class MultiUDPNetworkDVS128Camera extends DVS128 {

    private int numCameras = 1;
    static final String HASH_KEY = "MultiUDPNetworkDVS128Camera.camHashLlist";
    private static final int CAM_WIDTH=128;

    private class ClientMap {

        private InetSocketAddress clientAddress = null; // the InetSocketAddress for the data
        private int position = 0; // the position of this camera in the array
    }

    private ArrayList<ClientMap> clients = null;

    public MultiUDPNetworkDVS128Camera() {
        setName("MultiUDPNetworkDVS128Camera");

//        setEventExtractor((EventExtractor2D) new MultiUDPNetworkDVS128Camera.Extractor());

        try {
            byte[] bytes = getPrefs().getByteArray(HASH_KEY, null);
            if (bytes != null) {
                ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes));
                clients = (ArrayList<ClientMap>) in.readObject();
                in.close();
                numCameras = clients.size(); // TODO will grow with old cameras
            } else {
                log.info("no previous clients found - will cache them as data come in");
                numCameras = 1;
            }
        } catch (Exception e) {
            log.warning("caught " + e + " in constructor");
        }

        setSizeX(numCameras * 128);
        setSizeY(128);
        setEventExtractor(new Extractor(this));
    }


    /** the event extractor for DVS128. DVS128 has two polarities 0 and 1. Here the polarity is flipped by the extractor so that the raw polarity 0 becomes 1
    in the extracted event. The ON events have raw polarity 0.
    1 is an ON event after event extraction, which flips the type. Raw polarity 1 is OFF event, which becomes 0 after extraction.
     */
    public class Extractor extends DVS128.Extractor {

        final short XMASK = 0xfe, XSHIFT = 1, YMASK = 0x7f00, YSHIFT = 8;

        public Extractor(DVS128 chip) {
            super(chip);
            setXmask((short) 0x00fe);
            setXshift((byte) 1);
            setYmask((short) 0x7f00);
            setYshift((byte) 8);
            setTypemask((short) 1);
            setTypeshift((byte) 0);
            setFlipx(true);
            setFlipy(false);
            setFliptype(true);
        }

        /** extracts the meaning of the raw events.
         *@param in the raw events, can be null
         *@return out the processed events. these are partially processed in-place. empty packet is returned if null is supplied as in. This event packet is reused
         * and should only be used by a single thread of execution or for a single input stream, or mysterious results may occur!
         */
        @Override
        synchronized public EventPacket extractPacket(AEPacketRaw in) {
             if(!(in instanceof AENetworkRawPacket)){
                log.warning("input packet is not AENetworkRawPacket - cannot determine client camera addresses");
                return super.extractPacket(in);
            }
            if (out == null) {
                out = new EventPacket<PolarityEvent>(chip.getEventClass());
            } else {
                out.clear();
            }
            extractPacket(in, out);
            return out;
        }

        private int printedSyncBitWarningCount=3;

        /**
         * Extracts the meaning of the raw events. This form is used to supply an output packet. This method is used for real time
         * event filtering using a buffer of output events local to data acquisition. An AEPacketRaw may contain multiple events,
         * not all of them have to sent out as EventPackets. An AEPacketRaw is a set(!) of addresses and corresponding timing moments.
         *
         * A first filter (independent from the other ones) is implemented by subSamplingEnabled and getSubsampleThresholdEventCount.
         * The latter may limit the amount of samples in one package to say 50,000. If there are 160,000 events and there is a sub sample
         * threshold of 50,000, a "skip parameter" set to 3. Every so now and then the routine skips with 4, so we end up with 50,000.
         * It's an approximation, the amount of events may be less than 50,000. The events are extracted uniform from the input.
         *
         * @param in 		the raw events, can be null
         * @param out 		the processed events. these are partially processed in-place. empty packet is returned if null is
         * 					supplied as input.
         */
        synchronized public void extractPacket(AENetworkRawPacket in, EventPacket out) {
            if (in == null) {
                return;
            }
            int n = in.getNumEvents(); //addresses.length;

            ArrayList<AENetworkRawPacket.ClientInfo> packetClients=in.getClientList();
            if(packetClients==null || packetClients.isEmpty()){
                log.warning("AENetworkRawPacket  has no client info");
                out.clear();
            }
            Iterator<AENetworkRawPacket.ClientInfo> clientItr=packetClients.iterator();
            AENetworkRawPacket.ClientInfo clientInfo=clientItr.next();  // get the first client in the packet
            int clientStartingIndex=clientInfo.getStartingIndex();

            // check if this client is in our list of clients. if it is, then get the position of the camera.
            // if not, warning and (for now) choose the next position


            int skipBy = 1;
            if (isSubSamplingEnabled()) {
                while (n / skipBy > getSubsampleThresholdEventCount()) {
                    skipBy++;
                }
            }
            int sxm = sizeX - 1;
            int[] a = in.getAddresses();
            int[] timestamps = in.getTimestamps();
            OutputEventIterator outItr = out.outputIterator();
            for (int i = 0; i < n; i += skipBy) { // TODO bug here?
                int addr = a[i]; // TODO handle sync events from hardware correctly
                if (addr > 0xefff) {
                    if(printedSyncBitWarningCount>0){
                        log.warning("raw address "+addr+" is >32767 (0xefff); either sync or stereo bit is set, clearing the msb");
                        printedSyncBitWarningCount--;
                        if(printedSyncBitWarningCount==0) log.warning("suppressing futher warnings about msb of raw address");
                    }
                    // TODO handle this by outputting SyncEvent's instead of PolarityEvent's. Some files recorded earlier in 2.0 format have the msb set by hardware.
                    // here we restrict the addresses to 32767 max.
                    addr=addr&0xefff;
                } else {
                    PolarityEvent e = (PolarityEvent) outItr.nextOutput();
                    e.address=addr;
                    e.timestamp = (timestamps[i]);
                    e.type = (byte) (1 - addr & 1);
                    e.polarity = e.type == 0 ? PolarityEvent.Polarity.Off : PolarityEvent.Polarity.On;
                    e.x = (short) (sxm - ((short) ((addr & XMASK) >>> XSHIFT)));
                    e.y = (short) ((addr & YMASK) >>> YSHIFT);
                }

            }
        }

    }

}
