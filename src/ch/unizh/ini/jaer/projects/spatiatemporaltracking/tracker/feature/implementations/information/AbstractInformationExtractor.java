/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.information;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.AbstractFeatureExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.parameter.ParameterManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.util.Color;
import com.sun.opengl.util.j2d.TextRenderer;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.AEChip;

/**
 *
 * @author matthias
 * 
 * The concrete implementations of this abstract class have to extract information
 * from the observed object and to write it to a file.
 */
public abstract class AbstractInformationExtractor extends AbstractFeatureExtractor implements InformationExtractor {
    
    /** Defines the path of the file. */
    public final String PATH = "C:\\Users\\matthias\\Documents\\02_eth\\05_semester\\01_master\\02_repo\\doc\\experiments\\tracking\\results\\";
    //public final String PATH = "C:\\Users\\matthias\\Documents\\02_eth\\05_semester\\01_master\\02_repo\\doc\\experiments\\tracking\\results\\precition\\";
    
    /**
     * Creates a new instance of a AbstractPathInformationExtractor.
     */
    public AbstractInformationExtractor(Features interrupt, 
                                        ParameterManager parameters, 
                                        FeatureManager features,
                                        Features feature,
                                        AEChip chip) {
        super(interrupt, parameters, features, feature, Color.getBlue(), chip);
        
        this.init();
        this.reset();
    }
    
    @Override
    public void init() {
        super.init();
    }
    
    @Override
    public void reset() {
        super.reset();
    }
    
    @Override
    public void draw(GLAutoDrawable drawable, TextRenderer renderer, float x, float y) { }

    @Override
    public int getHeight() {
        return 0;
    }
}
