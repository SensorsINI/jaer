/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.einsteintunnel.multicamera;

import ch.unizh.ini.jaer.chip.retina.DVS128;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import net.sf.jaer.aemonitor.AENetworkRawPacket;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.graphics.AEViewer;

/**
 * Encapsulates a whole bunch of networked TDS cameras into this single AEChip object. The size of this virtual chip is set by {@link #MAX_NUM_CAMERAS} times
 * {@link #CAM_WIDTH}.
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
    private JMenu chipMenu = null; // menu for specialized control
    private CameraMapperDialog cameraMapperDialog = null;
    private CameraMap cameraMap = new CameraMap(); // the mapping from InetSocketAddress to camera position
    private MultiUDPNetworkDVS128CameraDisplayMethod displayMethod=null;

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
  }

    private void showCameraMapperDialog() {
        if (cameraMapperDialog == null) {
            cameraMapperDialog = new CameraMapperDialog(getAeViewer(), false, this);
        }
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
        public EventPacket extractPacket(AEPacketRaw in) {
            if (out == null) {
                out = new EventPacket<PolarityEvent>(chip.getEventClass());
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
                while (n / skipBy > getSubsampleThresholdEventCount()) {
                    skipBy++;
                }
            }
            int[] a = in.getAddresses();
            int[] timestamps = in.getTimestamps();
            OutputEventIterator outItr = out.outputIterator();
            for (int i = 0; i < n; i += skipBy) {

                int addr = a[i]; // TODO handle sync events from hardware correctly
                PolarityEvent e = (PolarityEvent) outItr.nextOutput();
                e.timestamp = (timestamps[i]);
                e.type = (byte) (1 - addr & 1);
                e.polarity = e.type == 0 ? PolarityEvent.Polarity.Off : PolarityEvent.Polarity.On;
                e.x = (short) (((short) (((addr & XMASK_DEST) >>> XSHIFT_DEST))));
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
            int cameraLocation = cameraMap.maybeAddCamera(thisSourceInfo.getClient());

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
                        cameraLocation = cameraMap.maybeAddCamera(thisSourceInfo.getClient());
                    }
                    if (eventSourceItr.hasNext()) {
                        nextSourceInfo = eventSourceItr.next();
                        nextSourceIndex = nextSourceInfo.getStartingIndex();
                    }
                }

                int addr = a[i]; // TODO handle sync events from hardware correctly
                PolarityEvent e = (PolarityEvent) outItr.nextOutput();
                int cameraShift = (MAX_NUM_CAMERAS-1-cameraLocation) * CAM_WIDTH;
                e.timestamp = (timestamps[i]);
                e.type = (byte) (1 - addr & 1);
                e.polarity = e.type == 0 ? PolarityEvent.Polarity.Off : PolarityEvent.Polarity.On;
                e.x = (short) (sxm - ((short) (cameraShift + ((addr & XMASK_SRC) >>> XSHIFT_SRC))));
                e.y = (short) ((addr & YMASK_SRC) >>> YSHIFT_SRC);
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
                numCameras = cameraMap.size(); // TODO will grow with old cameras
            } else {
                log.info("no previous clients found - will cache them as data come in");
                numCameras = 1;
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

        public void actionPerformed(ActionEvent e) {
            showCameraMapperDialog();
        }
    }


}
