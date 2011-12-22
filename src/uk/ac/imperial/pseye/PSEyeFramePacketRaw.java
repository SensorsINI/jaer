
package uk.ac.imperial.pseye;

import net.sf.jaer.aemonitor.AEPacketRaw;
import java.util.Collection;

/**
 * Wrapper to add frame size information to packet, necessary due to frame size changes
 * @author mlk11
 */
public class PSEyeFramePacketRaw extends AEPacketRaw {
    public int frameSize;
    public int nFrames;

    public PSEyeFramePacketRaw() {
        super();
    }

    public PSEyeFramePacketRaw(int[] addresses, int[] timestamps) {
        super(addresses, timestamps);
    }

    public PSEyeFramePacketRaw(int size) {
        super(size);
    }
    
    public PSEyeFramePacketRaw(AEPacketRaw one, AEPacketRaw two) {
        super(one, two);
    }

    public PSEyeFramePacketRaw(Collection<AEPacketRaw> collection) {
        super(collection);
    }
    
}
