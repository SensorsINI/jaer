package ch.unizh.ini.jaer.projects.einsteintunnel.multicamera;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Maps from InetSocketAddress to a position in the array of cameras. This mapping is loaded from Preferences and saved to Preferences
 * through the CameraMapperDialog.
 */
public class CameraMap extends ConcurrentHashMap<InetSocketAddress, Integer> implements Serializable {

    /** PropertyChange events fired when map changes. */
    public static final String MAP_CHANGED = "MapChanged";
    
    /** Defined to ensure serialization works across JVMs */
    public static final long serialVersionUID = 42L;

    // private fields, must be initialized in init() for readObject to work
    private static Logger log = Logger.getLogger("MultiUDPNetworkDVS128Camera");
    private Map<Integer, InetSocketAddress> positionMap;
    private PropertyChangeSupport support;

    public CameraMap() {
        init();
    }

    private void init() {
        support = new PropertyChangeSupport(this);
        positionMap = new ConcurrentHashMap(); // maps back from position to InetSocketAddress
    }

    public synchronized void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }

    /** Returns the camera location, adding the camera to the map at the end of the array if it is not already part of the map.
     *
     * @param key the source IP:port
     * @return camera location
     */
    public synchronized int maybeAddCamera(InetSocketAddress key) {
        if (key == null) {
            throw new RuntimeException("tried to add a null address for a camera");
        }
        if (containsKey(key)) {
            return get(key).intValue();
        } else {
            int pos = size();
            put(key, pos);
            getPositionMap().put(pos, key);
            log.info("automatically added new camera client from " + key + " to camera location=" + pos + ", map now has " + size() + " entries");
            support.firePropertyChange(MAP_CHANGED, null, key);
            return pos;
        }
    }

    /**
     * @return the positionMap
     */
    public Map<Integer, InetSocketAddress> getPositionMap() {
        return positionMap;
    }

    /** Increments a camera position, swapping with next camera in array.
     *
     * @param fromPos  If already at size()-1, does nothing.
     */
    public void incrementCameraPosition(int fromPos) {
        if (fromPos < 0 || fromPos >= size() - 1) {
            return;
        }
        InetSocketAddress fromAddr = positionMap.get(fromPos);
        InetSocketAddress toAddr = positionMap.get(fromPos + 1);
        put(fromAddr, fromPos + 1);
        positionMap.put(fromPos + 1, fromAddr);
        if (toAddr != null) {
            put(toAddr, fromPos);
            positionMap.put(fromPos, toAddr);
        }
        support.firePropertyChange(MAP_CHANGED, null, fromAddr);
    }

    /** Decrements the camera positioned at fromPos, swapping with camera at one less.
     *
     * @param fromPos if <1 or >=size(), does nothing.
     */
    public void decrementCameraPosition(int fromPos) {
        if (fromPos < 1 || fromPos >= size()) {
            return;
        }
        InetSocketAddress fromAddr = positionMap.get(fromPos);
        InetSocketAddress toAddr = positionMap.get(fromPos - 1);
        put(fromAddr, fromPos - 1);
        positionMap.put(fromPos - 1, fromAddr);
        if (toAddr != null) {
            put(toAddr, fromPos);
            positionMap.put(fromPos, toAddr);
        }
        support.firePropertyChange(MAP_CHANGED, null, fromAddr);
    }

    /** Deletes a camera from a position
    @param pos the position from 0 to size-1
     */
    public void deleteCameraAtPosition(int pos) {
        if (pos < 0 || pos >= size()) {
            return;
        }
        remove(positionMap.get(pos));
        positionMap.remove(pos);
        support.firePropertyChange(MAP_CHANGED, null, null);
    }

    @Override
    public void clear() {
        super.clear();
        if (positionMap == null) {
            positionMap = new ConcurrentHashMap<Integer, InetSocketAddress>();
        } else {
            positionMap.clear();
        }
    }

    // writes out the inet addresses and int positions, with prepended size
    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        out.writeInt(size());
        for (InetSocketAddress a : keySet()) {
            out.writeObject(a);
            out.writeInt(get(a));
        }
    }

    // reads in the size, then the list of inet addresses and positions
    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        init();
        clear();
        int n = in.readInt();
        for (int i = 0; i < n; i++) {
            InetSocketAddress a = (InetSocketAddress) in.readObject();
            int pos = in.readInt();
            put(a, pos);
            getPositionMap().put(pos, a);
        }
    }

    private void readObjectNoData() throws ObjectStreamException {
        clear();
    }
}
