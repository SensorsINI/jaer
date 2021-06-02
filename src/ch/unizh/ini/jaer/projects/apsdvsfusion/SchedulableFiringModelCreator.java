/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import java.util.prefs.Preferences;

import ch.unizh.ini.jaer.projects.apsdvsfusion.firingmodel.DelayedResponseModel;
import ch.unizh.ini.jaer.projects.apsdvsfusion.firingmodel.SimplePoissonModel;


/**
 * @author Dennis
 *
 */
public abstract class SchedulableFiringModelCreator extends ParameterContainer {

	public static enum FiringModelType {
		SIMPLEPOISSON, DELAYING
	}
	
	public static SchedulableFiringModelCreator getCreator(FiringModelType model, Preferences prefs) {
		if (model == FiringModelType.SIMPLEPOISSON)
			return SimplePoissonModel.getCreator(prefs);
		if (model == FiringModelType.DELAYING)
			return new DelayedResponseModel.Creator("DelayedResponse",prefs);
		else return null;
	}
	
	
	public SchedulableFiringModelCreator(String name, Preferences prefs) {
		super(name, prefs);
	}

	public abstract SchedulableFiringModel createUnit(int x, int y, SchedulableFiringModelMap map);

}
