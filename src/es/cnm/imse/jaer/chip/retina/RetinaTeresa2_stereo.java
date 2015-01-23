/*
 * Tmpdiff128.java
 *
 * Created on October 5, 2005, 11:36 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package es.cnm.imse.jaer.chip.retina;

import java.io.Serializable;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.TypedEventExtractor;
import net.sf.jaer.event.TypedEvent;

/**
 * Teresa Serrano's 128x128 DVS sensor, as tested by Juan Antonio Lenoro Bardallo.
 *
 * @author tobi (adapted, probably from rafa paz)
 */
public class RetinaTeresa2_stereo extends AEChip implements Serializable {

	/** Creates a new instance of Tmpdiff128 */
	public RetinaTeresa2_stereo() {
		setName("RetinaTeresa2");
		setSizeX(128);
		setSizeY(256);
		setNumCellTypes(2);
		setEventClass(TypedEvent.class);
		setEventExtractor(new Extractor(this));
		setBiasgen(null);
	}

	public class Extractor extends TypedEventExtractor implements java.io.Serializable {
		public Extractor(AEChip chip) {
			super(chip);
			/* setXmask((short)0x003f); */
			setXmask((short) 0x00fe);
			setXshift((byte) 1);
			/* setYmask((short)0x3f00); */
			setYmask((short) 0xff00);
			setYshift((byte) 8);
			setTypemask((short) 1);
			/* setTypeshift((byte)7); */
			setTypeshift((byte) 0);
			setFlipx(false);
			setFlipy(true);
			setFliptype(true);
		}
	}
}
