/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.virtualslotcar;
import com.sun.opengl.util.j2d.TextRenderer;
import java.awt.Font;
import javax.media.opengl.GLAutoDrawable;
import javax.swing.JOptionPane;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.FrameAnnotater;
/**
 * Controls slot car tracked from eye of god view.
 *
 * @author tobi
 *
 * This is part of jAER
<a href="http://jaer.wiki.sourceforge.net">jaer.wiki.sourceforge.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
public class SlotCarRacer extends EventFilter2D implements FrameAnnotater{
    private boolean showTrackEnabled = prefs().getBoolean("SlotCarRacer.showTrack",true);
    private boolean virtualCarEnabled = prefs().getBoolean("SlotCarRacer.virtualCar",false);
//    JFrame trackFrame = null;
//    GLUT glut = new GLUT();
//    GLU glu = new GLU();
//    GLCanvas trackCanvas;
    private SlotCarHardwareInterface hw;
    private SlotcarFrame slotCarFrame;
    private CarTracker carTracker;
    private FilterChain filterChain;
    private boolean overrideThrottle = prefs().getBoolean("SlotCarRacer.overrideThrottle",true);
    private float overriddenThrottleSetting = prefs().getFloat("SlotCarRacer.overriddenThrottleSetting",0);
    private SlotCarController controller = null;
    private SlotcarTrack trackModel;
    TextRenderer renderer;

    public SlotCarRacer (AEChip chip){
        super(chip);
        hw = new SlotCarHardwareInterface();
        carTracker = new CarTracker(chip);
        slotCarFrame = new SlotcarFrame();
        filterChain = new FilterChain(chip);
        filterChain.add(carTracker);
        setEnclosedFilterChain(filterChain);
        controller = new SimpleSpeedController(this);

        // tooltips for properties
        String con="Controller", dis="Display", ov="Override", vir="Virtual car";
        setPropertyTooltip(con,"desiredSpeed","Desired speed from speed controller");
        setPropertyTooltip(ov,"overrideThrottle","Select to override the controller throttle setting");
        setPropertyTooltip(ov,"overriddenThrottleSetting","Manual overidden throttle setting");
        setPropertyTooltip(vir,"virtualCarEnabled","Enable display of virtual car on virtual track");
    }

    public void doLearnTrack (){
        JOptionPane.showMessageDialog(chip.getAeViewer(),"I should do something");
    }

    @Override
    public EventPacket<?> filterPacket (EventPacket<?> in){
        getEnclosedFilterChain().filterPacket(in);
        if ( isOverrideThrottle() ){
            hw.setThrottle(getOverriddenThrottleSetting());
        } else{
            hw.setThrottle(controller.computeControl(carTracker,trackModel));
        }
        return in;
    }

    @Override
    public void resetFilter (){
    }

    @Override
    public void initFilter (){
    }

    public void annotate (GLAutoDrawable drawable){
        carTracker.annotate(drawable);
        if ( renderer == null ){
            renderer = new TextRenderer(new Font("SansSerif",Font.PLAIN,8),true,true);
        }
        renderer.begin3DRendering();
        renderer.draw("Throttle:"+controller.getThrottle(),10,10);
        renderer.end3DRendering();
    }

    /**
     * @return the overrideThrottle
     */
    public boolean isOverrideThrottle (){
        return overrideThrottle;
    }

    /**
     * @param overrideThrottle the overrideThrottle to set
     */
    public void setOverrideThrottle (boolean overrideThrottle){
        this.overrideThrottle = overrideThrottle;
    }

    /**
     * @return the overriddenThrottleSetting
     */
    public float getOverriddenThrottleSetting (){
        return overriddenThrottleSetting;
    }

    /**
     * @param overriddenThrottleSetting the overriddenThrottleSetting to set
     */
    public void setOverriddenThrottleSetting (float overriddenThrottleSetting){
        this.overriddenThrottleSetting = overriddenThrottleSetting;
        prefs().putFloat("SlotCarRacer.overriddenThrottleSetting",overriddenThrottleSetting);
    }

    // for GUI slider
    public float getMaxOverriddenThrottleSetting (){
        return 1;
    }

    public float getMinOverriddenThrottleSetting (){
        return 0;
    }

    /**
     * @return the virtualCarEnabled
     */
    public boolean isVirtualCarEnabled (){
        return virtualCarEnabled;
    }

    /**
     * @param virtualCarEnabled the virtualCarEnabled to set
     */
    public void setVirtualCarEnabled (boolean virtualCarEnabled){
        this.virtualCarEnabled = virtualCarEnabled;
        prefs().putBoolean("SlotCarRacer.virtualCarEnabled",virtualCarEnabled);

    }
}
