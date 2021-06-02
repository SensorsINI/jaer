/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import java.util.prefs.Preferences;

import ch.unizh.ini.jaer.projects.apsdvsfusion.firingmodel.IntegrateAndFire;
import ch.unizh.ini.jaer.projects.apsdvsfusion.firingmodel.LeakyIntegrateAndFire;
import ch.unizh.ini.jaer.projects.apsdvsfusion.firingmodel.LinearThresholdIF;

/**
 * Abstract class to produce many instances of different kinds of FiringModels.
 * @author Dennis Goehlsdorf
 *
 */
public abstract class FiringModelCreator extends ParameterContainer {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -3903017569141294350L;

	public FiringModelCreator(String name, Preferences prefs) {
		super(name, prefs);
	}
	public abstract FiringModel createUnit(int x, int y, FiringModelMap map);
	
	public static enum FiringModelType {
		INTEGRATEANDFIRE, LINEARTHRESHOLDIF, LEAKYINTEGRATEANDFIRE
	}
	
	public static FiringModelCreator getCreator(FiringModelType model, Preferences prefs) {
		if (model == FiringModelType.INTEGRATEANDFIRE)
			return IntegrateAndFire.getCreator(prefs);
		else if (model == FiringModelType.LINEARTHRESHOLDIF) {
			return LinearThresholdIF.getCreator(prefs);
		} 
		else if (model == FiringModelType.LEAKYINTEGRATEANDFIRE) {
			return LeakyIntegrateAndFire.getCreator(prefs);
		} 
		else return null;
	}
}
