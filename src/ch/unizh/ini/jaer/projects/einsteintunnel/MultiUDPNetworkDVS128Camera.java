/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.einsteintunnel;

import ch.unizh.ini.jaer.chip.retina.AERetina;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import net.sf.jaer.chip.EventExtractor2D;

/**
 * Encapsulates a whole bunch of networked TDS cameras into this single AEChip object.
 *
 * @author tobi
 */
public class MultiUDPNetworkDVS128Camera extends AERetina {

    private int numCameras = 1;
    static final String HASH_KEY = "MultiUDPNetworkDVS128Camera.camHashLlist";
    
    private class ClientMap{
        private InetSocketAddress clientAddress=null;
        private int position=0;
    }


    private ArrayList<ClientMap> clients = null;

    public MultiUDPNetworkDVS128Camera() {

//        setEventExtractor((EventExtractor2D) new MultiUDPNetworkDVS128Camera.Extractor());

        try {
            byte[] bytes = getPrefs().getByteArray(HASH_KEY, null);
            if (bytes != null) {
                ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes));
                clients = (ArrayList<ClientMap>) in.readObject();
                in.close();
            } else {
                log.info("no previous clients found - will cache them as data come in");
            }
        } catch (Exception e) {
            log.warning("caught " + e + " in constructor");
        }
    }


}
