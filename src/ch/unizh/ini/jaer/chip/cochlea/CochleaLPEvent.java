/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.chip.cochlea;

/**
 * An event from CochleaLP
 *
 * @author tobi
 */
public class CochleaLPEvent extends BinauralCochleaEvent {

	@Override
	public Ear getEar() {
		if ((address & 2) != 0) {
			return Ear.LEFT;
		}
		else {
			return Ear.RIGHT;
		}
	}

	@Override
	public int getNumCellTypes() {
		return 4; // on|off * left|right
	}

	@Override
	public int getType() {
		return address & 3; // To change body of generated methods, choose Tools | Templates.
	}

}
