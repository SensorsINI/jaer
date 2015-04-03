
package ch.unizh.ini.jaer.projects.bjoernbeyer.visualservo;

import com.jogamp.opengl.GLAutoDrawable;
import java.beans.PropertyChangeListener;
import net.sf.jaer.event.EventPacket;

/**
 *
 * @author Bjoern
 */
public interface SimpleVStrackerInterface {
    
    public void setTrackerEnabled(boolean TrackerEnabled);
    public boolean isTrackerEnabled();
    public EventPacket<?> filterPacket(EventPacket<?> in);
    public void doCenterPT();
    public void addPropertyChangeListener(PropertyChangeListener listener);
    public void annotate(GLAutoDrawable drawable);
    public void doDisableServos();
    public void resetFilter();
}
