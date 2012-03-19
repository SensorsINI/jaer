/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.period;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.correlation.CorrelationStorage;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.autocorrelation.AutoCorrelationExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.sf.jaer.chip.AEChip;

/**
 *
 * @author matthias
 * 
 * Computes the period of the signal of the observed object using the
 * auto-correlation function.
 * 
 * The period is determined by searching local maximas in the auto-correlation
 * function and voting for each local maximas with its correlation value. The 
 * algorithm chooses the period explaining most of the local maximas.
 */
public class MultipleAutoCorrelationPeriodExtractor extends AbstractPeriodExtractor {
    /**
     * Determines how much a local maxima can deviate from the global maxima
     * to be choosen as a possible period.
     */
    public final float deviation = 0.9f;
    
    /** 
     * Defines how much a candidate has to be better than the current one
     * to be choosen.
     */
    public final float quality = 1.3f;
    
    /** 
     * Defines how much a candidate can deviate from the global maximum.
     */
    public final float factor = 0.5f;
    
    /** Defines the allowed temporal error of the period. */
    public final int window = 4;
    
    /** Defines the temporal resolution. */
    public final int resolution = 100;
    
    /** Defines the maximal duration. */
    public final int duration = 1000000;
    
    /** Stores the votes for the period. */
    private float[] votes;
    
    /** Stores the maximum of votes. */
    private float max;
    
    /** Stores the max score reached for the current period. */
    private float maxScore;
    
    /**
     * Creates a new instance of MultipleAutoCorrelationPeriodExtractor.
     */
    public MultipleAutoCorrelationPeriodExtractor (ParameterManager parameters, 
                                                   FeatureManager features, 
                                                   AEChip chip) {
        super(Features.AutoCorrelation, parameters, features, chip);
        
        this.init();
        this.reset();
    }
    
    @Override
    public void init() {
        super.init();
        
        this.votes = new float[this.duration / this.resolution];
    }
    
    @Override
    public void reset() {
        super.reset();
        
        Arrays.fill(this.votes, 0.0f);
        this.max = 0;
        this.maxScore = 0;
    }
    
    /*
     * The period is determined by searching local maximas in the 
     * auto-correlation function and voting for it.
     * 
     * @param timestamp The timestamp of the current time.
     */
    @Override
    public void update(int timestamp) {
        CorrelationStorage correlation = ((AutoCorrelationExtractor)this.features.get(Features.AutoCorrelation)).getCorrelation();
        
        for (int i = 1; i < correlation.getObservations(); i++) {
            this.max = Math.max(this.max, correlation.getItems()[i].score);
        }
        
        for (int i = 2; i < correlation.getObservations() - 1; i++) {
            this.max = Math.max(this.max, correlation.getItems()[i].score);
            
            if (correlation.getItems()[i].score >= max * this.deviation && 
                    correlation.getItems()[i].score > correlation.getItems()[i - 1].score && 
                    correlation.getItems()[i].score > correlation.getItems()[i + 1].score) {

                int discretized = Math.round(((float)correlation.getItems()[i].value - correlation.getItems()[0].value) / this.resolution);

                if (discretized > 0 && discretized < this.votes.length) {
                    this.votes[discretized] += correlation.getItems()[i].score;

                    if (this.votes[discretized] > this.max * this.factor) {
                        float cScore = 0;
                        for (int m = discretized; m < this.votes.length; m *= 2) {
                            for (int d = -window; d <= window; d++) {
                                if (m + d >= 0 && m + d < this.votes.length) {
                                    cScore += this.votes[m + d];
                                }
                            }
                        }
                        if (cScore > this.maxScore * this.quality) {
                            this.maxScore = cScore;
                            this.period = discretized * this.resolution;
                        }
                    }

                    if (this.votes[discretized] < this.max) {
                        this.max = this.votes[discretized];
                    }
                }
            }
        }
    }
    
    @Override
    public void delete() {
        super.delete();

        this.features.delete(Features.Period);
        this.features.delete(Features.AutoCorrelation);
    }
    
    @Override
    public void store() {
        this.delete();
        
        new PeriodExtractorStorage(this.period, this.parameters, this.features, this.chip);
    }
}
