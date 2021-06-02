/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.chip.cochlea;

/**
 * An event from CochleaTow4Ear
 *
 * @author tobi
 */
public class CochleaTow4EarEvent extends BinauralCochleaEvent {

	@Override
	public Ear getEar() {
		if ((address & 1) != 0) {
			return Ear.LEFT;
		}
		else {
			return Ear.RIGHT;
		}
	}

	@Override
	public int getNumCellTypes() {
		return 4;
	}

	@Override
	public int getType() {
		return type;
	}

}
