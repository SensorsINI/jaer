/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.sensoryfusion.slaem;

    import net.sf.jaer.chip.*;
    import net.sf.jaer.event.*;
    import net.sf.jaer.eventprocessing.EventFilter2D;
    import java.util.*;
    import javax.media.opengl.*;
    import net.sf.jaer.*;
    import net.sf.jaer.graphics.FrameAnnotater;

/**
 * This filter extracts edges by inferring points and connecting lines into a scene
 * 
 * TODO: expand the event counter to infinity by reseting it when it hits the max int value
 *
 * @author christian
 */
@Description("Extracts edges as linear point interpolations")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class EdgeExtractor extends EventFilter2D implements Observer, FrameAnnotater{
    
    public EdgePixelArray edgePixels;
    public Camera camera;
    public int eventNr;
    public int trimmedEvents;
	
    /**
     * Determines whether events that cannot be assigned to an edge should be filtered out
     */
    private boolean filteringEnabled=getPrefs().getBoolean("EdgeExtractor.filterinEnabled",false);
    {setPropertyTooltip("filteringEnabled","Should the extractor act as filter for unallocated events");}
    
    /**
     * Determines whether edgePixels should be drawn
     */
    private boolean drawEdgePixels=getPrefs().getBoolean("EdgeExtractor.drawEdgePixels",true);
    {setPropertyTooltip("drawEdgePixels","Should the edgePixels be drawn");}
    
    /**
     * Determines whether events that cannot be assigned to an edge should be filtered out
     */
    private int activeEvents=getPrefs().getInt("EdgeExtractor.activeEvents",5000);
    {setPropertyTooltip("activeEvents","The number of most recent events allowed to make up the edges");}
    
    /**
     * Determines the maximal time to neighboring activity for becoming active (us)
     */
    private int deltaTsActivity=getPrefs().getInt("EdgeExtractor.deltaTsActivity",5000);
    {setPropertyTooltip("deltaTsActivity","Determines the maximal time to neighboring activity for becoming active (us)");}
    
	/**
     * Selection of the edge detection method
     */
	public enum EdgePixelMethod {
        LastSignificants, Voxels
    };
    private EdgePixelMethod edgePixelMethod = EdgePixelMethod.valueOf(getPrefs().get("ITDFilter.edgePixelMethod", "LastSignificants"));
	{setPropertyTooltip("edgePixelMethod","Method to do the edge detection");}
    
    public EdgeExtractor(AEChip chip){
        super(chip);
        chip.addObserver(this);
        initFilter();
    }
	
    @Override
    public void initFilter() {
        edgePixels = new EdgePixelArray(this);
        camera = new Camera (chip);
        resetFilter();
    }
    
    @Override
    public void resetFilter() {
        eventNr = 1;
        edgePixels.resetArray();
        edgePixels.setDeltaTsActivity(deltaTsActivity);
    }
    
    @Override
    synchronized public EventPacket filterPacket(EventPacket in) {
        if(!filterEnabled) return in;
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        checkOutputPacketEventType(in);
        OutputEventIterator outItr = out.outputIterator();
        for (Object o : in) {
            TypedEvent e = (TypedEvent) o;
            if(edgePixels.addEvent(e, eventNr)){
                eventNr++;
                TypedEvent oe = (TypedEvent) outItr.nextOutput();
                oe.copyFrom(e);
            }
        }    
        eventNr -= trimmedEvents;
        switch(edgePixelMethod){
            case LastSignificants:
                trimmedEvents = edgePixels.trimActivePixels(eventNr-activeEvents);
                break;
            case Voxels:
                trimmedEvents = edgePixels.trimActivePixelsV(eventNr-activeEvents);
                break;
        }
        if(filteringEnabled){ 
            return out;
        }else{
            return in;
        }    
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if(!isFilterEnabled()) return;
        if(!drawEdgePixels) return;
        GL gl=drawable.getGL();
        
        gl.glColor3f(1,0,0);
        
        gl.glPushMatrix();

        gl.glPointSize(4);
        Iterator edgePixelItr = edgePixels.activePixels.iterator();
        while(edgePixelItr.hasNext()){
            EdgePixelArray.EdgePixel pixel = (EdgePixelArray.EdgePixel)edgePixelItr.next();
            gl.glBegin(GL.GL_POINTS);
            gl.glVertex2i(pixel.posX,pixel.posY);
            gl.glEnd();
        }
        gl.glPopMatrix();
    }

    @Override
    public void update(Observable o, Object arg) {
        
    }
    
     /**
     * @return the activeEvents
     */
    public int getActiveEvents() {
        return activeEvents;
    }

    /**
     * @param setActiveEvents the setActiveEvents to set
     */
    public void setActiveEvents(int activeEvents) {
        this.activeEvents = activeEvents;
        prefs().putInt("EdgeExtractor.activeEvents", activeEvents);
    }
    
     /**
     * @return the activeEvents
     */
    public int getDeltaTsActivity() {
        return deltaTsActivity;
    }

    /**
     * @param deltaTsActivity the deltaTsActivity to set
     */
    public void setDeltaTsActivity(int deltaTsActivity) {
        this.deltaTsActivity = deltaTsActivity;
        edgePixels.setDeltaTsActivity(deltaTsActivity);
        prefs().putInt("EdgeExtractor.deltaTsActivity", deltaTsActivity);
    }
    
    /**
     * @return the filteringEnabled
     */
    public boolean isFilteringEnabled() {
        return filteringEnabled;
    }

    /**
     * @param filteringEnabled the filteringEnabled to set
     */
    public void setFilteringEnabled(boolean filteringEnabled) {
        this.filteringEnabled = filteringEnabled;
    }
    
        /**
     * @return the drawEdgePixels
     */
    public boolean isDrawEdgePixels() {
        return drawEdgePixels;
    }

    /**
     * @param drawEdgePixels the drawEdgePixels to set
     */
    public void setDrawEdgePixels(boolean drawEdgePixels) {
        this.drawEdgePixels = drawEdgePixels;
    }
	
	public EdgePixelMethod getEdgePixelMethod() {
        return edgePixelMethod;
    }

    synchronized public void setEdgePixelMethod(EdgePixelMethod edgePixelMethod) {
        getSupport().firePropertyChange("edgePixelMethod", this.edgePixelMethod, edgePixelMethod);
        getPrefs().put("EdgeExtractor.edgePixelMethod", edgePixelMethod.toString());
        this.edgePixelMethod = edgePixelMethod;
    }
}
