/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiotemporalcloseness;

import ch.unizh.ini.jaer.projects.spatiotemporalcloseness.util.EventGroup;
import ch.unizh.ini.jaer.projects.spatiotemporalcloseness.util.SimpleEventGroup;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 *
 * @author matthias
 */
@Description("Groups events together according their spatial and temporal closeness")
public class LabelingFilter extends EventFilter2D implements Observer, FrameAnnotater {
    
    /*
     * Parameters
     */
    protected boolean drawingrotated = getPrefs().getBoolean("LabelingFilter.drawingrotated", false);
    {setPropertyTooltip("drawingrotated","indicates whether the drawing has to be rotated or not.");}
    
    public boolean getDrawingrotated() {
        return this.drawingrotated;
    }
    
    public void setDrawingrotated(final boolean drawingrotated) {
        getPrefs().putBoolean("LabelingFilter.drawingrotated", drawingrotated);
        getSupport().firePropertyChange("drawingrotated",this.drawingrotated, drawingrotated);
        this.drawingrotated = drawingrotated;
    }
    
    protected int drawingtemporalresolution=getPrefs().getInt("LabelingFilter.drawingtemporalresolution", 100);
    {setPropertyTooltip("drawingtemporalresolution","defines the temporal resolution for the drawings.");}
    
    public int getDrawingtemporalresolution() {
        return this.drawingtemporalresolution;
    }
    
    public void setDrawingtemporalresolution(final int drawingtemporalresolution) {
        getPrefs().putInt("LabelingFilter.drawingtemporalresolution", drawingtemporalresolution);
        getSupport().firePropertyChange("drawingtemporalresolution",this.drawingtemporalresolution, drawingtemporalresolution);
        this.drawingtemporalresolution = drawingtemporalresolution;
    }
    
    protected int drawingtemporalmemory=getPrefs().getInt("LabelingFilter.drawingtemporalmemory", 10000);
    {setPropertyTooltip("drawingtemporalmemory","defines how long the groups are stored.");}
    
    public int getDrawingtemporalmemory() {
        return this.drawingtemporalmemory;
    }
    
    public void setDrawingtemporalmemory(final int drawingtemporalmemory) {
        getPrefs().putInt("LabelingFilter.drawingtemporalmemory", drawingtemporalmemory);
        getSupport().firePropertyChange("drawingtemporalmemory",this.drawingtemporalmemory, drawingtemporalmemory);
        this.drawingtemporalmemory = drawingtemporalmemory;
    }
    
    protected int drawingminsize=getPrefs().getInt("LabelingFilter.drawingminsize", 0);
    {setPropertyTooltip("drawingminsize","defines the minimal size of a group to be drawn.");}
    
    public int getDrawingminsize() {
        return this.drawingminsize;
    }
    
    public void setDrawingminsize(final int drawingminsize) {
        getPrefs().putInt("LabelingFilter.drawingminsize", drawingminsize);
        getSupport().firePropertyChange("drawingminsize",this.drawingminsize, drawingminsize);
        this.drawingminsize = drawingminsize;
    }
    
    protected float temporalthreshold=getPrefs().getFloat("LabelingFilter.temporalthreshold", 200);
    {setPropertyTooltip("temporalthreshold","threshold for the temporal cost function.");}
    
    public float getTemporalthreshold() {
        return this.temporalthreshold;
    }
    
    public void setTemporalthreshold(final float temporalthreshold) {
        getPrefs().putFloat("LabelingFilter.temporalthreshold", temporalthreshold);
        getSupport().firePropertyChange("temporalthreshold",this.temporalthreshold, temporalthreshold);
        this.temporalthreshold = temporalthreshold;
    }
    
    protected float temporaldelay=getPrefs().getFloat("LabelingFilter.temporaldelay", 100);
    {setPropertyTooltip("temporaldelay","defines the temporal delay of the events.");}
    
    public float getTemporaldelay() {
        return this.temporaldelay;
    }
    
    public void setTemporaldelay(final float temporaldelay) {
        getPrefs().putFloat("LabelingFilter.temporaldelay", temporaldelay);
        getSupport().firePropertyChange("temporaldelay",this.temporaldelay, temporaldelay);
        this.temporaldelay = temporaldelay;
    }
    
    /*
     * Comparator
     */
    public class TypedEventComparator implements Comparator<TypedEvent> {

        @Override
        public int compare(TypedEvent o1, TypedEvent o2) {
            return (int)Math.signum(o1.timestamp - o2.timestamp);
        }
    }
    
    /*
     * Filter
     */
    
    /** Stores the currently used groups. */
    private Set<EventGroup> groups;
    
    /** Stores the delayed events. */
    private Queue<TypedEvent> queue;
    
    /** Stores the groups to draw. */
    private List<EventGroup> visualization;
    
    /** Stores the pointers from the pixel to the group they belong */
    private EventGroup[][][] pointers;
    
    /** Stores the timestamp of the last event for each pixel and type. */
    private int[][][] timestamps;
    
    /** The timestamp of the last event. */
    private int current;
    
    /** Temporary list to store events for various reasons. */
    List<TypedEvent> temporaryEventHolder;
    
    /** Temporary list to store groups for various reasons. */
    List<EventGroup> temporaryGroupHolder;
    
    public LabelingFilter(AEChip chip){
        super(chip);
        chip.addObserver(this);
        
        initFilter();
        resetFilter();
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!filterEnabled) return in;
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        checkOutputPacketEventType(in);
        
        OutputEventIterator outItr=out.outputIterator();
        
        double diff;
        for(Object o : in){
            TypedEvent e = (TypedEvent)o;
            
            this.current = e.timestamp;
            
            this.temporaryEventHolder.clear();
            this.temporaryGroupHolder.clear();
            for (short x = (short)Math.max(0, e.x - 1); x <= Math.min(this.chip.getSizeX() - 1, e.x + 1); x++) {
                for (short y = (short)Math.max(0, e.y - 1); y <= Math.min(this.chip.getSizeY() - 1, e.y + 1); y++) {
                    if (x != e.x || y != e.y) {
                        
                        if (this.pointers[e.type][x][y] == null) {
                            // case where pixel is not in a group
                            diff = e.timestamp - this.timestamps[e.type][x][y];
                            if (diff >= 0 && diff < this.temporalthreshold) {
                                TypedEvent ne = new PolarityEvent();
                                ne.type = e.type;
                                ne.x = x;
                                ne.y = y;
                                ne.timestamp = this.timestamps[e.type][x][y];
                                
                                this.temporaryEventHolder.add(ne);
                            }
                        }
                        else {
                            // case where pixel is in a group
                            diff = e.timestamp - this.pointers[e.type][x][y].getTimestamp();
                            if (diff >= 0 && diff < this.temporalthreshold) {
                                EventGroup candidate = this.pointers[e.type][x][y];
                                
                                if (!this.temporaryGroupHolder.contains(candidate)) {
                                    this.temporaryGroupHolder.add(candidate);
                                }
                            }
                        }
                    }
                }
            }
            
            this.timestamps[e.type][e.x][e.y] = e.timestamp;
            this.pointers[e.type][e.x][e.y] = null;
            
            EventGroup host = null;
            if (this.temporaryGroupHolder.isEmpty()) {
                if (!this.temporaryEventHolder.isEmpty()) {
                    // add new EventGroup for similar events
                    TypedEvent ne = new PolarityEvent();
                    ne.type = e.type;
                    ne.x = e.x;
                    ne.y = e.y;
                    ne.timestamp = e.timestamp;
                    
                    host = new SimpleEventGroup(ne);
                    this.groups.add(host);

                    this.pointers[e.type][e.x][e.y] = host;
                }
            }
            else {
                // first merge groups
                host = this.temporaryGroupHolder.remove(0);
                
                TypedEvent ne = new PolarityEvent();
                ne.type = e.type;
                ne.x = e.x;
                ne.y = e.y;
                ne.timestamp = e.timestamp;
                
                this.pointers[e.type][e.x][e.y] = host;
                host.add(ne);
                
                for (EventGroup eg : this.temporaryGroupHolder) {
                    this.groups.remove(eg);
                    
                    for (TypedEvent eeg : eg.getEvents()) {
                        this.pointers[eeg.type][eeg.x][eeg.y] = host;
                    }
                    host.add(eg);
                }
            }
            // add single pixels to group
            for (TypedEvent te : this.temporaryEventHolder) {
                this.pointers[te.type][te.x][te.y] = host;
                host.add(te);
            }
        }
        
        /*
         * post process the groups
         */
        this.temporaryGroupHolder.clear();
        for (Iterator<EventGroup> it = this.groups.iterator(); it.hasNext(); ) {
            EventGroup eg = it.next();
            if (eg.getTimestamp() < this.current - this.temporalthreshold) {
                this.temporaryGroupHolder.add(eg);
                
                for (TypedEvent eeg : eg.getEvents()) {
                    this.pointers[eeg.type][eeg.x][eeg.y] = null;
                }
                this.queue.addAll(eg.getEvents());
            }
        }
        this.groups.removeAll(this.temporaryGroupHolder);
        this.visualization.addAll(this.temporaryGroupHolder);
        
        /*
         * select events to pass the filter
         */
        while (!this.queue.isEmpty() && queue.peek().timestamp < this.current - (this.temporalthreshold + this.temporaldelay)) {
            TypedEvent o=(TypedEvent)outItr.nextOutput();
            o.copyFrom(this.queue.remove());
        }
        return out;
    }

    @Override
    public void initFilter() {
        this.groups = new HashSet<EventGroup>();
        this.queue = new PriorityQueue<TypedEvent>(1000, new TypedEventComparator());
        
        this.visualization = new ArrayList<EventGroup>();
        
        this.temporaryEventHolder = new ArrayList<TypedEvent>();
        this.temporaryGroupHolder = new ArrayList<EventGroup>();
        
        this.pointers = new EventGroup[2][this.chip.getSizeX()][this.chip.getSizeY()];
        this.timestamps = new int[2][this.chip.getSizeX()][this.chip.getSizeY()];
    }
    
    @Override
    public void resetFilter() {
        this.groups.clear();
        this.queue.clear();
        
        this.visualization.clear();
        
        this.temporaryEventHolder.clear();
        this.temporaryGroupHolder.clear();
        
        for (int i = 0; i < this.pointers.length; i++) {
            for (int j = 0; j < this.pointers[i].length; j++) Arrays.fill(this.pointers[i][j], null);
        }
        for (int i = 0; i < this.timestamps.length; i++) {
            for (int j = 0; j < this.timestamps[i].length; j++) Arrays.fill(this.timestamps[i][j], 0);
        }
    }

    @Override
    public void update(Observable o, Object arg) {
        this.initFilter();
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        GL gl = drawable.getGL();
        
        if (this.drawingrotated) {
            gl.glRotatef(this.chip.getCanvas().getAngley(), 0, 1, 0); // rotate viewpoint by angle deg around the upvector
            gl.glRotatef(this.chip.getCanvas().getAnglex(), 1, 0, 0); // rotate viewpoint by angle deg around the upvector

            gl.glTranslatef(this.chip.getCanvas().getOrigin3dx(), this.chip.getCanvas().getOrigin3dy(), 0);
        }
        
        for (int i = 0; i < this.visualization.size(); i++) {
            if (this.visualization.get(i) == null ||
                    this.visualization.get(i).getTimestamp() < this.current - this.drawingtemporalmemory) {
                
                this.visualization.remove(i--);
            }
        }
        
        EventGroup[] pg = this.visualization.toArray(new EventGroup[0]);
        for (int i = 0; i < pg.length; i++) {
            if (pg[i] != null) {
                if (pg[i].getSize() >= this.drawingminsize) pg[i].draw(drawable, this.current, this.drawingtemporalresolution);
            }
        }
    }
}
