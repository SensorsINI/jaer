/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import java.util.prefs.Preferences;

import ch.unizh.ini.jaer.projects.apsdvsfusion.firingmodel.IntegrateAndFire;
import ch.unizh.ini.jaer.projects.apsdvsfusion.firingmodel.LeakyIntegrateAndFire;
import ch.unizh.ini.jaer.projects.apsdvsfusion.firingmodel.SimplePoissonModel;
import ch.unizh.ini.jaer.projects.apsdvsfusion.gui.ParameterContainer;

/**
 * @author Dennis Goehlsdorf
 *
 */
public abstract class FiringModelCreator extends ParameterContainer {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -3903017569141294350L;
	public FiringModelCreator(String name, Preferences parentPrefs, String nodeName) {
		super(name, parentPrefs, nodeName);
	}
	public abstract FiringModel createUnit(int x, int y, FiringModelMap map);
	
	public enum FiringModelType {
		INTEGRATEANDFIRE, LEAKYINTEGRATEANDFIRE
	}
	
	public static FiringModelCreator getCreator(FiringModelType model, Preferences parentPrefs, String nodeName) {
		if (model == FiringModelType.INTEGRATEANDFIRE)
			return IntegrateAndFire.getCreator(parentPrefs, nodeName);
		else if (model == FiringModelType.LEAKYINTEGRATEANDFIRE) {
			return LeakyIntegrateAndFire.getCreator(parentPrefs, nodeName);
		} 
		else return null;
	}
}
