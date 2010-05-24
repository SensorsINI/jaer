/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.chip.dollbrain.coltmpdiff;

import net.sf.jaer.event.SyncEvent;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.TypedEventExtractor;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.graphics.SpaceTimeEventDisplayMethod;

/**
 * The coltmpdiff test chip.
 * @author tobi
 */
public class ColTmpDiff extends AEChip {

    public ColTmpDiff() {
        setSizeX(2);
        setSizeY(1);
        setNumCellTypes(3); // TODO add more types
        setEventClass(SyncEvent.class);
        setEventExtractor(new Extractor(this));
        getCanvas().addDisplayMethod(new SynchronizedSpikeRasterDisplayMethod(getCanvas()));
        getCanvas().addDisplayMethod(new SpaceTimeEventDisplayMethod(getCanvas()));
        setName("ColTmpDiff");

    }
    public class Extractor extends TypedEventExtractor implements java.io.Serializable{

        protected int colormask;
        protected byte colorshift;

        public Extractor(AEChip chip){
            super(chip);
            setEventClass(SyncEvent.class);
        }

        /** extracts the meaning of the raw events. This form is used to supply an output packet. This method is used for real time event filtering using
            a buffer of output events local to data acquisition.
         *@param in the raw events, can be null
         *@param out the processed events. these are partially processed in-place. empty packet is returned if null is supplied as in.
         */
        synchronized public void extractPacket(AEPacketRaw in, EventPacket out) {
            out.clear();
            if(in==null) return;
            int n=in.getNumEvents(); //addresses.length;

            // SimpleMonitorBoard has sync from function generator tied to AE14. This sync goes up and down in a square wave.
            // Therefore the real spike polarity is OR'ed with the sync phase.
            // e.g. OFF spike is 0 when sync is low and 0x4000 when sync is high.

            int[] a=in.getAddresses();
            int[] timestamps=in.getTimestamps();
            OutputEventIterator outItr=out.outputIterator();
            for(int i=0;i<n;i++){
                int addr=a[i];
//                System.out.println("addr["+i+"]="+addr);
                final int syncAddr=-2;  // 0xfffe mapped to -2
                SyncEvent e=(SyncEvent)outItr.nextOutput();
                e.address=addr;
                e.timestamp=(timestamps[i]);
                e.x=(short)((addr==syncAddr)? 1:0); // silabs sends 0xfffe for sync event, turns into -2 in java, set x=1 for sync, x=0 for others
                e.y=0;
                e.type=(byte)((addr==syncAddr)? SyncEvent.SYNC_TYPE:addr&1);

                e.polarity=((addr&1)!=0?PolarityEvent.Polarity.On:PolarityEvent.Polarity.Off);
            }
        }
    }
}
