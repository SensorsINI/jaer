/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.brainfair;

import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.LinkedList;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.ApsDvsOrientationEvent;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.label.DvsOrientationFilter;

/**
 * Computes statistics of the event input and provides an interface for 
 * a graphics object that visualizes these statistics.
 * @author Michael Pfeiffer
 */
public class StatisticsCalculator extends EventFilter2D {
     
    // Time constants for various dynamic histograms
    private float orientTau = 0.001f;  // for orientation
    private float overallTau = 0.001f; // for overall event rate
    
    private float overallRange = 10.0f; // maximum delay range for overall rate
    
    private int numEvents = 0;  // number of events during filtering step
    private int minTime, maxTime; // min and max timestep during filtering step
    
    private float[] orientHistory;    // Statistics of orientations
    
    private LinkedList overallRateHistory;
    
    private int nBins = 50; // number of histogram bins
    private int[] ISIbins;  // bins of ISI histogram
    private int maxIsiUs=10000;   // maximum ISI in us
    private int minIsiUs=3000;   // minimum ISI in us
    private float ISITau = 0.001f;  // decay rate for ISI histogram
    private int nChans = 1;
    private int[] lastTs = null;  // time of last event in channel
    private int maxBin = 0;
    final int MAX_COUNT = 10000000;
    int nextDecayTimestamp = 0, lastDecayTimestamp = 0;

    private int nPixelBins; // number of pixels
    private int[] Pixelbins;  // bins of ISI histogram
    private float PixelTau = 0.001f;  // decay rate for ISI histogram
    private int maxPixelBin = 0;
    int nextPixelDecayTimestamp = 0, lastPixelDecayTimestamp = 0;
    
    private int[] PixelTime;  // last time step of pixel firing
    int timeWindowWidth = 0;  // length of pixel time window
    int maxPixelTime = 0;     // latest firing time of any pixel
    int nPixelTime = 0;
    
    
    private FilterChain filterChain;  // Enclosed filter chain for orientations
    private DvsOrientationFilter orientFilter;


    public StatisticsCalculator(AEChip chip) {
        super(chip);
        
        orientHistory = new float[4];
        ISIbins = new int[nBins];
        
        nPixelBins = 128*128;
        Pixelbins = new int[nPixelBins];
        
        nPixelTime = 128*128;
        PixelTime = new int[nPixelTime];
        
        // Create enclosed filter and filter chain
        orientFilter = new DvsOrientationFilter(chip);
        filterChain = new FilterChain(chip);
        filterChain.add(orientFilter);
        setEnclosedFilterChain(filterChain);
        
        overallRateHistory = new LinkedList();
    
       
    }

    @Override
    public synchronized EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!filterEnabled) return in;

        // Helper variables
        int i;
        
        EventPacket<?> nextOut = getEnclosedFilterChain().filterPacket(in);

        if ( in == null ){
            return null;
        }
        
        // checkOutputPacketEventType(ApsDvsOrientationEvent.class);

        // OutputEventIterator outItr=out.outputIterator();
        
        resetOverallStatistics();
        
        for (BasicEvent e : in) { // iterate over all input events
                updatePixelStatistics(e);
                updateOverallStatistics(e, false);
                // updateISIStatistics(e);
                updateTimeStatistics(e);
        } // BasicEvent e

        for (BasicEvent e : nextOut) { // iterate over all orientation events
            // BasicEvent o=(BasicEvent)outItr.nextOutput();
            // o.copyFrom(e);

            if (e instanceof ApsDvsOrientationEvent) {
                // Update orientation statistics
                ApsDvsOrientationEvent oe = (ApsDvsOrientationEvent) e;

                updateOrientationStatistics(oe);
            } // e instanceof ApsDvsOrientationEvent

        } // BasicEvent e

        // Commit update of overall firing rate
        updateOverallStatistics(null, true);
        
        return in;
        
    }
    
    // Updates statistics about orientations
    private void updateOrientationStatistics(ApsDvsOrientationEvent oe) {
        byte orient = oe.orientation;
        if ((orient>=0) && (orient<4)) {
            orientHistory[orient]++;
        }
        // Decay histogram for orientation
        for (int i=0; i<4; i++)
            orientHistory[i]*=(1-orientTau);
        
    }

    
   
    // Resets overall statistics
    private void resetOverallStatistics() {
        numEvents = 0;
        minTime = Integer.MAX_VALUE;
        maxTime = Integer.MIN_VALUE;
    }
    
    // Updates information about overall activity
    private synchronized void updateOverallStatistics(BasicEvent e, boolean finished) {
        if (!finished) {
            // Update for a single event
            numEvents++;
            minTime = Math.min(e.timestamp, minTime);
            maxTime = Math.max(e.timestamp, maxTime);
        }
        else {
            // Finished with event queue
            if (numEvents > 0) {
                float dt = (maxTime-minTime)/1000000.0f;
                float currentRate = numEvents / dt;
                if (currentRate > numEvents)
                    currentRate = numEvents;
                
                double newRate = currentRate;
                if ((overallRateHistory != null) && (!overallRateHistory.isEmpty())) {
                    Point2D lastEvent = (Point2D) overallRateHistory.getLast();
                    double lastTime = lastEvent.getX();
                    double lastRate = lastEvent.getY();
                    double updateFactor = Math.exp(-overallTau * (maxTime-lastTime) / 1000000.0);
                    newRate = updateFactor*lastRate + (1-updateFactor)*currentRate;
/*                    System.out.println("mu: " + updateFactor + "[" + maxTime + " / " + lastTime
                            + "], lastRate: " + lastRate + " newRate: " + newRate); */
                }
                overallRateHistory.add(new Point2D.Double(maxTime, newRate));
                
                // Delete events older than some delay
                float minRangeTime = maxTime - overallRange*1000000.0f;
                boolean finishedList = overallRateHistory.isEmpty();
                while (!finishedList) {
                    Point2D firstEvent = (Point2D) overallRateHistory.getFirst();
                    double firstTime = firstEvent.getX();
                    if (firstTime < minRangeTime) {
                        // Remove from list
                        overallRateHistory.removeFirst();
                        finishedList = overallRateHistory.isEmpty();
                    } else {
                        finishedList = true;
                    }
                }
            }
        }
    }
    
    
    // Updates information about ISI distribution
    private void updateISIStatistics(BasicEvent e) {
            int ts = e.timestamp;
            int ch = e.x * e.y;
            int dt = ts - lastTs[ch];
            lastTs[ch] = ts;

            addIsi(dt);
            decayHistogram(ts);
        
    }
    
    private void rescaleBins (){
        for ( int i = 0 ; i < nBins ; i++ ){
            ISIbins[i] = ISIbins[i] >> 1;
        }
    }

    private void addIsi (int isi){
        // System.out.println("ISI:" + isi + " [" + minIsiUs + ", " + maxIsiUs + "]");
        if ( isi < minIsiUs ){
            return;
        }
        if ( isi >= maxIsiUs ){
            return;
        }

        int bin = ( ( ( isi - minIsiUs ) * nBins ) / ( maxIsiUs - minIsiUs ) );

        ISIbins[bin]++;
        if ( ISIbins[bin] > getMaxBin() ){
            maxBin = ISIbins[bin];
        }
        if ( getMaxBin() > MAX_COUNT ){
            rescaleBins();
            maxBin = getMaxBin() >> 1;
        }
    }
    
   public void decayHistogram (int timestamp){
        if ( this.ISITau > 0 && timestamp > nextDecayTimestamp ){
            float decayconstant = (float)java.lang.Math.exp(-( timestamp - lastDecayTimestamp ) / ( ISITau * 1000 ));
            for ( int i = 0 ; i < ISIbins.length ; i++ ){
                ISIbins[i] = (int)( ISIbins[i] * decayconstant );
            }
            nextDecayTimestamp = (int)( timestamp + ( ISITau * 1000 ) / 10 );
            lastDecayTimestamp = timestamp;
        }
    }


    /**
     * @return the maxBin
     */
    public int getMaxBin (){
        return maxBin;
    }

    synchronized public void resetBins (){
        nChans=chip.getSizeX()*chip.getSizeY();
        if ( nChans == 0 ){
            return; // not yet
        }
        lastTs = new int[ nChans ];
        if ( ISIbins.length != nBins ){
            ISIbins = new int[ nBins ];
        }
        Arrays.fill(ISIbins,0);
        this.maxBin = 0;
        
    }

    /**
     * @return the maxBin
     */
    public int getMaxPixelBin (){
        return maxPixelBin;
    }

    // Updates information about individual pixel statistics
    private void updatePixelStatistics(BasicEvent e) {
            int ts = e.timestamp;
            int ch = e.x + chip.getSizeX()* e.y;

            Pixelbins[ch]++;
            if ( Pixelbins[ch] > getMaxPixelBin() ){

                maxPixelBin = Pixelbins[ch];
            }
            if ( getMaxPixelBin() > MAX_COUNT ){
                rescalePixelBins();
                maxPixelBin = getMaxPixelBin() >> 1;
            }
            decayPixelHistogram(ts);
       
    }
    
   public void decayPixelHistogram (int timestamp){
        if ( this.PixelTau > 0 && timestamp > nextPixelDecayTimestamp ){
            float decayconstant = (float)java.lang.Math.exp(-( timestamp - lastPixelDecayTimestamp ) / 
                    ( PixelTau * 1000000f ));
            maxPixelBin = 0;
            for ( int i = 0 ; i < Pixelbins.length ; i++ ){
               
                Pixelbins[i] = (int)( Pixelbins[i] * decayconstant );
                if (Pixelbins[i] > maxPixelBin) {
                    maxPixelBin = Pixelbins[i];
                }
            }
            nextPixelDecayTimestamp = (int)( timestamp + ( PixelTau * 1000000 ) / 10 );
            lastPixelDecayTimestamp = timestamp;
        }
    }

   synchronized public void resetPixelBins (){
        if ( Pixelbins.length != nPixelBins ){
            Pixelbins = new int[ nPixelBins ];
        }
        Arrays.fill(Pixelbins,0);
        this.maxPixelBin = 0;
        nextPixelDecayTimestamp = 0;
        lastPixelDecayTimestamp = 0;
    }
    
    private void rescalePixelBins (){
        for ( int i = 0 ; i < nPixelBins ; i++ ){
            Pixelbins[i] = Pixelbins[i] >> 1;
        }
    }
    
    public int[] getPixelHistogram() {
        return Pixelbins;
    }

    
    // Updates information about individual pixel statistics
    private void updateTimeStatistics(BasicEvent e) {
            int ts = e.timestamp;
            int ch = e.x + chip.getSizeX()* e.y;

            if (ts > PixelTime[ch])
                PixelTime[ch] = ts;
            if ( PixelTime[ch] > getMaxPixelTime() ){

                maxPixelTime = PixelTime[ch];
            }
    }

   synchronized public void resetTime (){
        if ( PixelTime.length != nPixelTime ){
            PixelTime = new int[ nPixelTime ];
        }
        Arrays.fill(PixelTime,0);
        this.maxPixelTime = 0;
    }
    
    @Override
    public void resetFilter() {
        filterChain.reset();
        // Reset history
        for (int i=0; i<4; i++) {
            orientHistory[i]=0.0f;
        }
        
        if (overallRateHistory != null)
            overallRateHistory.clear();
        
        resetBins();
        resetPixelBins();
        resetTime();
    }

    @Override
    public void initFilter() {
        resetFilter();
    }
    

    public void setOrientTau(float orientTau) {
        this.orientTau = orientTau;
    }

    public void setOverallTau(float overallTau) {
        this.overallTau = overallTau;
    }

    public void setPixelTau(float pixelTau) {
        this.PixelTau = pixelTau;
    }

    public float[] getOrientHistory() {
        return orientHistory;
    }
    
    public synchronized LinkedList getOverallRate() {
        return (LinkedList) overallRateHistory.clone();
    }

    public void setOverallRange(float overallRange) {
        this.overallRange = overallRange;
    }

    int[] getISIBins() {
        return ISIbins;
    }

    int getNumISIBins() {
        return nBins;
    }

    public void setnBins(int nBins) {
        int old=this.nBins;
        if ( nBins < 1 ){
            nBins = 1;
        }
        this.nBins = nBins;
        resetBins();
    }
    

    int getISIMax() {
        int maxBin = Integer.MIN_VALUE;
        for (int i=0; i<nBins; i++) {
            if (ISIbins[i] > maxBin)
                maxBin = ISIbins[i];
        }
        return maxBin;
    }

    void setMaxIsiUs(int maxIsiUs) {
        int old=this.maxIsiUs;
        if ( maxIsiUs < minIsiUs ){
            maxIsiUs = minIsiUs;
        }
        this.maxIsiUs = maxIsiUs;
        resetBins();

    }

    void setMinIsiUs(int minIsiUs) {
        int old=this.minIsiUs;
        if ( minIsiUs > maxIsiUs ){
            minIsiUs = maxIsiUs;
        }
        this.minIsiUs = minIsiUs;
        resetBins();

    }

    void setISITau(float ISIHistoryFactor) {
        this.ISITau = ISIHistoryFactor;
    }

    public int[] getPixelTime() {
        return PixelTime;
    }

    public int getMaxPixelTime() {
        return maxPixelTime;
    }

    public int getTimeWindowWidth() {
        return timeWindowWidth;
    }
    
    
    
    
    void setMaxTime(int maxTime) {
        this.timeWindowWidth = maxTime;
    }
   
}
