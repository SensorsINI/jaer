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
 * Used to annotate classes to provide additional description to be used for a short tooltip.
 * Use it like this, just before a class declaration: 
 * <pre>
 * @Description ("Labyrinth robot implementation")
 public class LabyrinthGame extends EventFilter2DMouseAdaptor  {
</pre>
 * This annotation is used to construct tooltips for the class in class chooser dialogs and other places.
 * @author tobi
 */
@Retention(RetentionPolicy.RUNTIME) // retain at runtime
@Target(ElementType.TYPE) // can only annotate classes, not methods of fields
public @interface Description {
    String value();
}
