/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Used to annotate fields and getter or setter methods in EventFilter to
 * indicate they are "preferred" (i.e. commonly used). This is alternative to
 * setPropertyTooltipPreferred()
 * <p>
 * Use it like this, either on property field or get or set method of the
 * property
 * <pre>
 * {@code
 * @Preferred
 * private float noiseRateCoVDecades = getFloat("noiseRateCoVDecades", 0.5f);
 *
 * @Preferred
 * synchronized public void doToggleOnROCSweep() {
 * rocSweep = new ROCSweep(selectedNoiseFilter);
 * rocSweep.start();
 * }
 * }
 * </pre>
 *
 * @author tobi
 * @see net.sf.jaer.eventprocessing.FilterPanel
 * @see Description
 */
@Retention(RetentionPolicy.RUNTIME) // retain at runtime
@Target({ElementType.FIELD, ElementType.METHOD}) // can annotate fields or methods
public @interface Preferred {
}
