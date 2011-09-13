/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.sensoryfusion.slaem;

    import net.sf.jaer.chip.*;
    import net.sf.jaer.event.*;
    import net.sf.jaer.event.EventPacket;
    import net.sf.jaer.eventprocessing.EventFilter2D;
    import java.util.*;
    import net.sf.jaer.Description;
    import net.sf.jaer.DevelopmentStatus;

/**
 * This filter extracts edges by inferring points and connecting lines into a scene
 *
 * @author christian
 */
@Description("Extracts edges as linear point interpolations")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class EdgeExtractor extends EventFilter2D implements Observer {
    
	public ArrayList<EdgeVertex> vertices;
	public Iterator<EdgeVertex> vertexItr;
	
    /**
     * Determines whether events that cannot be assigned to an edge should be filtered out
     */
    private boolean filteringEnabled=getPrefs().getBoolean("EdgeExtractor.filterinEnabled",false);
    {setPropertyTooltip("filteringEnabled","Should the extractor act as filter for unallocated events");}
    
    /**
     * Determines whether events that cannot be assigned to an edge should be filtered out
     */
    private float vertexDiameter=getPrefs().getFloat("EdgeExtractor.vertexDiameter",1.5f);
    {setPropertyTooltip("tolerance","The distance belonging to one vertex");}
    
	    public EdgeExtractor(AEChip chip){
        super(chip);
        chip.addObserver(this);
        initFilter();
        resetFilter();
    }
	
    @Override
    public void initFilter() {
        resetFilter();
    }
    
    @Override
    public void resetFilter() {
        vertices = new ArrayList();
		vertexItr = vertices.iterator();
    }
    
    @Override
    synchronized public EventPacket filterPacket(EventPacket in) {
        if(!filterEnabled) return in;
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        checkOutputPacketEventType(in);
        for (Object o : in) {
            TypedEvent e = (TypedEvent) o;
            updateVertices(e);
        }
        
        if(filteringEnabled){ 
            return out;
        }else{
            return in;
        }
    }
	
	private boolean isFree;
	
	private void updateVertices(TypedEvent e){
		isFree = true;
		while(vertexItr.hasNext()){
			if(vertexItr.next().checkEvent(e)){
				isFree = false;
			}
		}
		if(isFree){
			vertices.add(new EdgeVertex(e, vertexDiameter));
		}
	}

    @Override
    public void update(Observable o, Object arg) {
        
    }
    
     /**
     * @return the tolerance
     */
    public float getTolerance() {
        return vertexDiameter;
    }

    /**
     * @param tolerance the tolerance to set
     */
    public void setTolerance(float vertexDiameter) {
        this.vertexDiameter = vertexDiameter;
        prefs().putFloat("EdgeExtractor.vertexDiameter", vertexDiameter);
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
    
}
