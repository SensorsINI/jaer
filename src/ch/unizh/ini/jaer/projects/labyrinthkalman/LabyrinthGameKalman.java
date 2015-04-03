/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.labyrinthkalman;

import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2DMouseAdaptor;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;
import ch.unizh.ini.jaer.projects.labyrinth.LabyrinthMap;

/**
 * Top level labyrinth robot class.
 * @author tobi
 */
@Description("Top level labyinth game class with Kalman Tracking")
public class LabyrinthGameKalman extends EventFilter2DMouseAdaptor  {

    LabyrinthBallController controller;
    LabyrinthVirtualBall virtualBall=null;
    LabyrinthMap map;
    FilterChain filterChain;
    enum State {Starting, Running, Finished, LostTracking, PathNotFound};
    State state=State.Starting;

   public LabyrinthGameKalman(AEChip chip) {
        super(chip);
        controller = new LabyrinthBallController(chip);
        virtualBall=new LabyrinthVirtualBall(chip,this);
         
        filterChain = new FilterChain(chip);

//        filterChain.add(new RotateFilter(chip));
        filterChain.add(virtualBall);
        filterChain.add(controller);
        setEnclosedFilterChain(filterChain);
        setPropertyTooltip("clearMap", "clears the map; use for bare table");
        setPropertyTooltip("loadMap","loads a map from an SVG file");
        setPropertyTooltip("controlTilts", "shows a GUI to directly control table tilts with mouse");
        setPropertyTooltip("centerTilts","centers the table tilts");
        setPropertyTooltip("disableServos","disables the servo motors by turning off the PWM control signals; digital servos may not relax however becuase they remember the previous settings");
        setPropertyTooltip("jiggleTable", "jiggle the table according to the jitter settings for the LabyrinthHardware");
        setPropertyTooltip("enableControl", "enable ball controller");
        setPropertyTooltip("disableControl", "disable ball controller");
    }
     
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        out= filterChain.filterPacket(in);
 
        if(controller.isLostTracking()){
            state=State.LostTracking;
        }else if(controller.isPathNotFound()){
            state=State.PathNotFound;
        }else if(controller.isAtMazeStart()){
            state=State.Starting;
        }else if(controller.isAtMazeEnd()){
            state=State.Finished;
        }else{
            state=State.Running;
        }
        return out;
    }

    @Override
    public void resetFilter() {
        filterChain.reset();
    }

    @Override
    public void initFilter() {
        resetFilter();
    }

    public void doDisableServos() {
        controller.disableServos();
    }

    public void doCenterTilts() {
        controller.centerTilts();
    }

    public void doControlTilts() {
        controller.controlTilts();
    }

    public void doEnableControl(){
        controller.setControllerEnabled(true);
    }
    
    public void doDisableControl(){
        controller.setControllerEnabled(false);
    }
            
    
    
    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        virtualBall.setFilterEnabled(false); // don't enable by default
    }

    public void doLoadMap() {
        controller.loadMap();
    }

    public synchronized void doClearMap() {
        controller.clearMap();
    }

    public void doJiggleTable() {
        controller.doJiggleTable();
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        super.annotate(drawable);
        MultilineAnnotationTextRenderer.resetToYPositionPixels(chip.getSizeY()-5);
        MultilineAnnotationTextRenderer.renderMultilineString("LabyrinthGate: State="+state.toString());
    }

    
    
}
