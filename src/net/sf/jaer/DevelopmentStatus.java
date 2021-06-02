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
 * Annotates classes with development status, e.g. Stable, Experimental.
 * Use it like this, just before a class declaration: 
 * <pre>
 * @DevelopmentStatus(DevelopmentStatus.Status.Experimental)
 * public class FlickerSuppessor extends EventFilter2D implements FrameAnnotater {
 * </pre>
 * This annotation is used in constructing GUIs in class chooser dialogs and other places.
 * @author tobi
 */
@Retention(RetentionPolicy.RUNTIME) // retain at runtime
@Target(ElementType.TYPE) // can only annotate classes, not methods of fields
public @interface DevelopmentStatus {
    Status value();
    public enum Status {Stable, Experimental, InDevelopment, Abstract}
    
}


