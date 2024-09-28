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
 * Used to annotate fields in EventFilter's to indicate they are "preferred" (i.e. commonly used).
 * This is alternative to 
 * @see net.sf.jaer.eventprocessing.EventFilter#setPropertyTooltipPreferred()
</pre>
 * This annotation is mark preferred properties for EventFilter's.
 * @author tobi
 */
@Retention(RetentionPolicy.RUNTIME) // retain at runtime
@Target(ElementType.FIELD) // can only annotate fields
public @interface Preferred {
}
