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
 * Used to annotate classes to provide additional description to be used for a
 * short tooltip. Use it like this, just before a class declaration:
 * <pre>
 * {@code
 * @Description ("Labyrinth robot implementation")
 * public class LabyrinthGame extends EventFilter2DMouseAdaptor  {}
 * }
 * </pre>
 *
 * Description can also be used to annotate fields of EventFilter properties,
 * e.g. as in
 * <pre>
 * {@code
 *    @Preferred
 * @Description("Mean velocity for flying objecs")
 * private float velocityMps = getFloat("velocityMps", 10);
 * }
 * </pre>
 *
 * This annotation is used to construct tooltips for the class in class chooser
 * dialogs and EventFilter panels.
 *
 * @author tobi
 * @see Preferred
 * @see https://docs.oracle.com/javase/tutorial/java/annotations/predefined.html
 */
@Retention(RetentionPolicy.RUNTIME) // retain at runtime
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD}) // can annotate classes,  methods and fields
public @interface Description {

    String value();
    String group() default "";
}
