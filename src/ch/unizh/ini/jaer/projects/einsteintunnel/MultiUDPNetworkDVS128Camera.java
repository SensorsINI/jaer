/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.einsteintunnel;

import ch.unizh.ini.jaer.chip.retina.DVS128;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import javax.swing.AbstractAction;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import net.sf.jaer.aemonitor.AENetworkRawPacket;
import net.sf.jaer.aemonitor.AENetworkRawPacket.EventSourceInfo;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.graphics.AEViewer;

/**
 * Encapsulates a whole bunch of networked TDS cameras into this single AEChip object.
 *
 * @author tobi delbruck, christian braendli
 */
public class MultiUDPNetworkDVS128Camera extends DVS128 {

    /** Maximum number of network cameras in the linear array. */
    public static final int MAX_NUM_CAMERAS = 10;
    /** Width in pixels of each camera - same as DVS128. */
    public static final int CAM_WIDTH = 128;
    private int numCameras = 10; // actual number of cameras we've gotten data from
    private static final String CLIENT_MAPPING_LIST_PREFS_KEY = "MultiUDPNetworkDVS128Camera.camHashLlist";  // preferences key for mapping table
    private int nextFreeCameraLocation = 0; // next camera location in linear array to be automatically assigned to incoming data
    private JMenu chipMenu = null; // menu for specialized control
    private CameraMapperDialog cameraMapperDialog = null;
    private ClientMap clientMap = new ClientMap(); // the mapping from InetSocketAddress to camera position

    public MultiUDPNetworkDVS128Camera() {
        setName("MultiUDPNetworkDVS128Camera");


        setSizeX(MAX_NUM_CAMERAS * 128);
        setSizeY(128);
        setEventExtractor(new Extractor(this));
        chipMenu = new JMenu("MultiCamera");
        chipMenu.getPopupMenu().setLightWeightPopupEnabled(false);
        chipMenu.add(new JMenuItem(new ShowClientMapperAction()));
        loadClientMappingPrefs();
    }

    private void showCameraMapperDialog() {
        if (cameraMapperDialog == null) {
            cameraMapperDialog = new CameraMapperDialog(getAeViewer(), false, this);
        }
        cameraMapperDialog.refreshTable();
        cameraMapperDialog.setVisible(true);
    }

    /** Overrides to add the menu. */
    @Override
    public void setAeViewer(AEViewer v) {
        if (v != null) {
            v.setMenuItem(chipMenu);
        }
    }

    /**
     * Returns the client map.
     * @return the clientMap
     */
    public ClientMap getClientMap() {
        return clientMap;
    }

    /**
     * Sets the client map.
     * @param clientMap the clientMap to set
     */
    public void setClientMap(ClientMap clientMap) {
        this.clientMap = clientMap;
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
            if (!(in instanceof AENetworkRawPacket)) {
                log.warning("input packet is not AENetworkRawPacket - cannot determine client camera addresses");
                return super.extractPacket(in);
            }
            if (out == null) {
                out = new EventPacket<PolarityEvent>(chip.getEventClass());
            } else {
                out.clear();
            }
            AENetworkRawPacket netPacket = (AENetworkRawPacket) in;
            extractPacket(netPacket, out);
            return out;
        }
        private int printedSyncBitWarningCount = 3;

        /**
         * Extracts the meaning of the raw events and re-maps them to proper location in the larger virtual AEChip object.
         *
         * <p>
         * This form is used to supply an output packet. This method is used for real time
         * event filtering using a buffer of output events local to data acquisition. An AEPacketRaw may contain multiple events,
         * not all of them have to sent out as EventPackets. An AEPacketRaw is a set(!) of addresses and corresponding timing moments.
         * <p>
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

            AENetworkRawPacket.EventSourceList eventSourceList = in.getEventSourceList(); // list of clients in this raw packet
            if (eventSourceList == null || eventSourceList.isEmpty()) {
                log.warning("AENetworkRawPacket  has no client info");
                out.clear();
            }
            AENetworkRawPacket.EventSourceInfo thisSourceInfo = null, nextSourceInfo = null; // current and next event sources

            // we get this client and next client from list. As long as index is 
            // less than next client's starting index (or next client is null) we use this clients position.
            // When the index gets >= to next client's starting index, we set this client to next client and get next client.
            Iterator<AENetworkRawPacket.EventSourceInfo> eventSourceItr = eventSourceList.iterator();

            thisSourceInfo = eventSourceItr.next();  // get the first client in the packet
            if (eventSourceItr.hasNext()) {
                nextSourceInfo = eventSourceItr.next();
            }
            int nextSourceIndex = nextSourceInfo == null ? Integer.MAX_VALUE : nextSourceInfo.getStartingIndex();
            int cameraLocation = clientMap.maybeAddCamera(thisSourceInfo.getClient());

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
            for (int i = 0; i < n; i += skipBy) {
                // for each raw event, if its index is >= than the nextSourceIndex, make thisSourceInfo be the nextSourceIndex,
                // maybe cache the camera, and set the location. Also get the nextSourceInfo
                if (i >= nextSourceIndex) {
                    thisSourceInfo = nextSourceInfo;
                    if (nextSourceInfo != null) {
                        cameraLocation = clientMap.maybeAddCamera(thisSourceInfo.getClient());
                    }
                    if (eventSourceItr.hasNext()) {
                        nextSourceInfo = eventSourceItr.next();
                        nextSourceIndex = nextSourceInfo.getStartingIndex();
                    }
                }

                int addr = a[i]; // TODO handle sync events from hardware correctly
                if (addr > 0xefff) {
                    if (printedSyncBitWarningCount > 0) {
                        log.warning("raw address " + addr + " is >32767 (0xefff); either sync or stereo bit is set, clearing the msb");
                        printedSyncBitWarningCount--;
                        if (printedSyncBitWarningCount == 0) {
                            log.warning("suppressing futher warnings about msb of raw address");
                        }
                    }
                    // TODO handle this by outputting SyncEvent's instead of PolarityEvent's. Some files recorded earlier in 2.0 format have the msb set by hardware.
                    // here we restrict the addresses to 32767 max.
                    addr = addr & 0xefff;
                } else {
                    PolarityEvent e = (PolarityEvent) outItr.nextOutput();
                    int cameraShift = cameraLocation * CAM_WIDTH;
                    e.timestamp = (timestamps[i]);
                    e.type = (byte) (1 - addr & 1);
                    e.polarity = e.type == 0 ? PolarityEvent.Polarity.Off : PolarityEvent.Polarity.On;
                    e.x = (short) (sxm - ((short) (cameraShift + ((addr & XMASK) >>> XSHIFT))));
                    e.y = (short) ((addr & YMASK) >>> YSHIFT);
                    e.address = addr | e.x << XSHIFT | e.y << YSHIFT; // new raw address is now suitable for logging and later playback
                    a[i] = e.address;  // replace raw address in raw packet as well
                }

            }
        }
    }

    public final void loadClientMappingPrefs() {
        //        setEventExtractor((EventExtractor2D) new MultiUDPNetworkDVS128Camera.Extractor());
        try {
            byte[] bytes = getPrefs().getByteArray(CLIENT_MAPPING_LIST_PREFS_KEY, null);
            if (bytes != null) {
                ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes));
                clientMap = (ClientMap) in.readObject();
                in.close();
                numCameras = clientMap.size(); // TODO will grow with old cameras
            } else {
                log.info("no previous clients found - will cache them as data come in");
                numCameras = 1;
            }
        } catch (Exception e) {
            log.warning("caught " + e + " in constructor");
        }
    }

    public void saveClientMappingPrefs() {
        if (clientMap == null) {
            log.warning("clientMap==null, no mapping to save to preferences");
            return;
        }
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(2048);
            ObjectOutputStream os = new ObjectOutputStream(bos);
            os.writeObject(clientMap);
            getPrefs().putByteArray(CLIENT_MAPPING_LIST_PREFS_KEY, bos.toByteArray());
            log.info("wrote client mapping holding " + clientMap.size() + " clients to " + getPrefs());
        } catch (Exception e) {
            log.warning(e.toString());
        }

    }

    /** Maps from InetSocketAddress to a position in the array of cameras. This mapping is loaded from Preferences and saved to Preferences
    through the CameraMapperDialog.
     */
    public class ClientMap extends LinkedHashMap<InetSocketAddress, Integer> implements Serializable {

        public static final long serialVersionUID = 42L;

        public int maybeAddCamera(InetSocketAddress key) {
            if (key == null) {
                throw new RuntimeException("tried to add a null address for a camera");
            }
            if (containsKey(key)) {
                return get(key).intValue();
            } else {
                put(key, size());
                log.info("automatically added new camera client from " + key + " to camera location=" + size());
            }
            return size() - 1;
        }

        private void writeObject(java.io.ObjectOutputStream out)
                throws IOException {
            out.writeInt(size());
            for (InetSocketAddress a : keySet()) {
                out.writeObject(a);
                out.writeInt(get(a));
            }
        }

        private void readObject(java.io.ObjectInputStream in)
                throws IOException, ClassNotFoundException {
            clear();
            int n = in.readInt();
            for (int i = 0; i < n; i++) {
                InetSocketAddress a = (InetSocketAddress) in.readObject();
                int pos = in.readInt();
                put(a, pos);
            }
        }

        private void readObjectNoData()
                throws ObjectStreamException {
            clear();
        }
    }

    private class ShowClientMapperAction extends AbstractAction {

        public ShowClientMapperAction() {
            putValue(NAME, "Map camera locations");
            putValue(MNEMONIC_KEY, KeyEvent.VK_S);
            putValue(SHORT_DESCRIPTION, "Shows a dialog to configure mapping from source IP:port to camera position");
        }

        public void actionPerformed(ActionEvent e) {
            showCameraMapperDialog();
        }
    }
}
