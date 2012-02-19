/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.information;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.Signal;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.boundary.BoundaryExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.lifetime.LifetimeExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.signal.SignalExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.parameter.ParameterManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.util.file.FileHandler;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import net.sf.jaer.chip.AEChip;

/**
 *
 * @author matthias
 */
public class SignalInformationExtractor extends AbstractInformationExtractor {
    
    /** Indicates whether the signal was allready logged or not. */
    private boolean isLogged;
    
    /**
     * Creates a new instance of a PathInformationExtractor.
     */
    public SignalInformationExtractor(ParameterManager parameters, 
                                      FeatureManager features, 
                                      AEChip chip) {
        super(Features.Signal, parameters, features, Features.InformationSignal, chip);
        
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
        
        this.isLogged = false;
    }
    
    /**
     * Extracts the information of a FeatureExtractor and writes the information
     * to a file.
     * 
     * @param timestamp The timestamp of the current time.
     */
    @Override
    public void update(int timestamp) {
        /*
         * writes the signal to the file.
         */
        if (this.features.has(Features.Signal)) {
            if (this.isLogged) return;
            this.isLogged = false;
            
            Signal s = ((SignalExtractor)this.features.get(Features.Signal)).getSignal();
            
            DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            Date date = new Date();
            
            int lifetime = ((LifetimeExtractor)this.features.get(Features.Lifetime)).getLifetime();
            int creationtime = ((LifetimeExtractor)this.features.get(Features.Lifetime)).getCreationTime();
            float x = ((BoundaryExtractor)this.features.get(Features.Boundary)).getMajorLength();
            float y = ((BoundaryExtractor)this.features.get(Features.Boundary)).getMinorLength();
            
            String header = String.format("%s, %d, %d, %d, %f, %f", dateFormat.format(date), this.hashCode(), creationtime, lifetime, x, y);
            String output = "";
            for (int i = 0; i < s.getSize(); i++) output += String.format(", %d, %d", s.getOriginalTransition(i).state, s.getOriginalTransition(i).time);
            
            FileHandler.getInstance(PATH + "signal.txt").writeLine(String.format("%s%s", header, output));
        }
    }
}
