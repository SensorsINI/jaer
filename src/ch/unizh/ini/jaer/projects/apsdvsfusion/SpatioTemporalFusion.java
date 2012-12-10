/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 * Filter class that uses a defined kernel function to compute not spatially filtered output spikes.
 * 
 * @author Dennis Goehlsdorf
 *
 */
public class SpatioTemporalFusion extends EventFilter2D {

	InputKernel inputKernel;
	FiringModelMap firingModelMap;
	
	/**
	 * @param chip
	 */
	public SpatioTemporalFusion(AEChip chip) {
		super(chip);
	}

	/* (non-Javadoc)
	 * @see net.sf.jaer.eventprocessing.EventFilter2D#filterPacket(net.sf.jaer.event.EventPacket)
	 */
	@Override
	public EventPacket<?> filterPacket(EventPacket<?> in) {
        checkOutputPacketEventType(in);
//        OutputEventIterator oi=out.outputIterator();
 //       PolarityEvent e;
		for (BasicEvent be : in) {
			if (be instanceof PolarityEvent) {
				inputKernel.apply(be.x, be.y, be.timestamp, ((PolarityEvent)be).polarity, firingModelMap, out);
			}
		}
		return out;
	}

	/* (non-Javadoc)
	 * @see net.sf.jaer.eventprocessing.EventFilter#initFilter()
	 */
	@Override
	public void initFilter() {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see net.sf.jaer.eventprocessing.EventFilter#resetFilter()
	 */
	@Override
	public void resetFilter() {
		// TODO Auto-generated method stub

	}

}
