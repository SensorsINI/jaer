/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ine.telluride.jaer.cuda;
import ch.unizh.ini.jaer.chip.retina.DVS128;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;


/** 
 * This synthetic AEChip has an EventExtractor2D that understands the CUDA event output.
 * @author tobi
 *
 */
public class CUDAChip extends DVS128 {
    public CUDAChip() {
        setEventClass(CUDAEvent.class); // modify to elaborated event type, e.g. TypedEvent, and write extractPacket to understand it
        setEventExtractor(new CUDAExtractor(this));
    }
    public class CUDAExtractor extends CUDAChip.Extractor {
        public CUDAExtractor(CUDAChip chip) {
            super(chip);
            setNumCellTypes(5); // TODO make parameter
            setEventClass(CUDAEvent.class);
            out=new EventPacket<CUDAEvent>(chip.getEventClass());
        }

        /** extracts the meaning of the raw events.
         *@param in the raw events, can be null
         *@return out the processed events. these are partially processed in-place. empty packet is returned if null is supplied as in.
         */
        @Override
        synchronized public EventPacket extractPacket(AEPacketRaw in) {
            if(out==null) {
                out=new EventPacket<CUDAEvent>(CUDAEvent.class);
            } else {
                out.clear();
            }
            if(in==null) {
                return out;
            }
            int n=in.getNumEvents(); //addresses.length;

            int skipBy=1;
            if(isSubSamplingEnabled()) {
                while(n/skipBy>getSubsampleThresholdEventCount()) {
                    skipBy++;
                }
            }
            final int XMASK=getXmask(),  YMASK=getYmask(),  XSHIFT=getXshift(),  YSHIFT=getYshift();
            final int TYPEMASK=0xf0000,  TYPESHIFT=16;
            int sxm=sizeX-1;
            int[] a=in.getAddresses();
            int[] timestamps=in.getTimestamps();
            OutputEventIterator outItr=out.outputIterator();
            for(int i=0; i<n; i+=skipBy) { // bug here
                CUDAEvent e=(CUDAEvent) outItr.nextOutput();
                int addr=a[i];
                e.timestamp=(timestamps[i]);
                e.x=(short) (sxm-((short) ((addr&XMASK)>>>XSHIFT)));
                e.y=(short) ((addr&YMASK)>>>YSHIFT);
                e.type=(byte) ((addr&TYPEMASK)>>>TYPESHIFT);

//                e.polarity=e.type==0? PolarityEvent.Polarity.Off:PolarityEvent.Polarity.On;
            }
            return out;
        }
    }
}
