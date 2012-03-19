/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.event.packet;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.AbstractFeatureExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.util.Color;
import com.sun.opengl.util.j2d.TextRenderer;
import java.util.ArrayList;
import java.util.List;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.TypedEvent;

/**
 *
 * @author matthias
 */
public abstract class AbstractPacketEventExtractor extends AbstractFeatureExtractor implements PacketEventExtractor {

    /** Stores the packet of the extractor. */
    protected List<TypedEvent> packet;
    
    /**
     * Creates a new instance of a AbstractPacketEventExtractor.
     */
    public AbstractPacketEventExtractor(Features interrupt, 
                                        ParameterManager parameters, 
                                        FeatureManager features,
                                        AEChip chip) {
        super(interrupt, parameters, features, Features.Event, Color.getBlue(), chip);
    }
    
    @Override
    public void init() {
        super.init();
        
        this.packet = new ArrayList<TypedEvent>();
    }
    
    @Override
    public void reset() {
        super.reset();
        
        this.packet.clear();
    }
    
    @Override
    public List<TypedEvent> getPacket() {
        return this.packet;
    }
    
    @Override
    public void draw(GLAutoDrawable drawable, TextRenderer renderer, float x, float y) { }

    @Override
    public int getHeight() {
        return 0;
    }
}
