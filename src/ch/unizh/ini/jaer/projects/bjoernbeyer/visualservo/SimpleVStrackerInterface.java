
package ch.unizh.ini.jaer.projects.bjoernbeyer.visualservo;

import java.beans.PropertyChangeListener;
import javax.media.opengl.GLAutoDrawable;
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
