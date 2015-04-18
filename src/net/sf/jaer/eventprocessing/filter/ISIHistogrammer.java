/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing.filter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;

import javax.swing.JFrame;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip2D;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.util.chart.Axis;
import net.sf.jaer.util.chart.Category;
import net.sf.jaer.util.chart.Series;
import net.sf.jaer.util.chart.XYChart;
/**
 * Histograms ISIs along selected direction of chip event space.
 * @author tobi
 *
 * This is part of jAER
<a href="http://jaerproject.net/">jaerproject.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
@Description("Computes ISI (inter spike interval) histogram")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class ISIHistogrammer extends EventFilter2D implements Observer{

    /**
     * @return the bins
     */
    public int[] getBins (){
        return bins;
    }

    /**
     * @return the maxBin
     */
    public int getMaxBin (){
        return maxBin;
    }

    private void checkBins (){
        if(lastTs==null || lastTs.length!=nChans) resetBins();
    }
    public enum Direction{
        XDirection, YDirection, XtimesYDirection;
    };
    private Direction direction = Direction.valueOf(getPrefs().get("ISIHistogrammer.direction",Direction.XtimesYDirection.toString()));
    private int nBins = getPrefs().getInt("ISIHistogrammer.nBins",50);
    private int maxIsiUs = getPrefs().getInt("ISIHistogrammer.maxIsiUs",10000);
    private int minIsiUs = getPrefs().getInt("ISIHistogrammer.minIsiUs",3000);
    private int[] bins = new int[ nBins ];
    private int maxBin = 0;
    int nChans = 1;
    int[] lastTs = null;
    final int MAX_COUNT = 10000000;
    JFrame isiFrame = null;
    int nextDecayTimestamp = 0, lastDecayTimestamp = 0;
    private float tauDecayMs = getPrefs().getFloat("ISIHistogrammer.tauDecayMs",40);
    private int lastts=0; // to catch nonmonotonic ts that prevent decay of histogram

    public ISIHistogrammer (AEChip chip){
        super(chip);
        chip.addObserver(this);
        setPropertyTooltip("maxIsiUs","maximim ISI in us, larger ISI's are discarded");
        setPropertyTooltip("minIsiUs","minimum ISI in us, smaller ISI's are discarded");
        setPropertyTooltip("NBins","number of histogram bins");
        setPropertyTooltip("direction","X to use x AE addresses, y for y addresses, XtimesY for x*y addresses");
        setPropertyTooltip("tauDecayMs","histogram bins are decayed to zero with this time constant in ms");
        setPropertyTooltip("logPlotEnabled","enable to plot log histogram counts, disable for linear scale");
    }

    @Override
    synchronized public EventPacket<?> filterPacket (EventPacket<?> in){
        checkBins();

        for ( BasicEvent e:in ){
            int ts = e.timestamp;
            if(ts-lastts<0){
               nextDecayTimestamp=ts; 
            }
            lastts=ts;
            int ch;
            switch ( direction ){
                case XDirection:
                    ch = e.x;
                    break;
                case YDirection:
                    ch = e.y;
                    break;
                case XtimesYDirection:
                    ch = e.x * e.y;
                    break;
                default:
                    ch = 0;
            }
            int dt = ts - lastTs[ch];
            lastTs[ch] = ts;

            addIsi(dt);
            decayHistogram(ts);
        }
        isiFrame.repaint();
        return in;
    }

    synchronized public void resetBins (){
        nChans=chip.getSizeX()*chip.getSizeY();
        if ( nChans == 0 ){
            return; // not yet
        }
        lastTs = new int[ nChans ];
        if ( bins.length != nBins ){
            bins = new int[ nBins ];
        }
        Arrays.fill(bins,0);
        maxBin = 0;
        if ( activitySeries != null ){
            activitySeries.setCapacity(nBins);
        }
        if ( isiFrame != null ){
            isiFrame.repaint(0);
        }
    }

    private void rescaleBins (){
        for ( int i = 0 ; i < nBins ; i++ ){
            bins[i] = bins[i] >> 1;
        }
    }

    private void addIsi (int isi){
        if ( isi < minIsiUs ){
            return;
        }
        if ( isi >= maxIsiUs ){
            return;
        }

        int bin = ( ( ( isi - minIsiUs ) * nBins ) / ( maxIsiUs - minIsiUs ) );

        bins[bin]++;
        if ( bins[bin] > getMaxBin() ){
            maxBin = bins[bin];
        }
        if ( getMaxBin() > MAX_COUNT ){
            rescaleBins();
            maxBin = getMaxBin() >> 1;
        }
    }

    public void doPrintBins (){
        for ( int i:bins ){
            System.out.print(String.format("%8d ",i));
        }
        System.out.println("");
    }

    @Override
    public void resetFilter (){
        resetBins();
    }

    @Override
    public void initFilter (){
        resetBins();
    }

    public void update (Observable o,Object arg){
        if ( arg.equals(Chip2D.EVENT_SIZEX) || arg.equals(Chip2D.EVENT_SIZEY) ){
            switch ( direction ){
                case XDirection:
                    nChans = chip.getSizeX();
                    break;
                case YDirection:
                    nChans = chip.getSizeY();
                    break;
                case XtimesYDirection:
                    nChans = chip.getSizeX() * chip.getSizeY();
            }
            resetBins();
        }
    }

    /**
     * @return the direction
     */
    public Direction getDirection (){
        return direction;
    }

    /**
     * @param direction the direction to set
     */
    public void setDirection (Direction direction){
        Direction old=this.direction;
        this.direction = direction;
        getPrefs().put("ISIHistogrammer.direction",direction.toString());
        getSupport().firePropertyChange("direction",old,this.direction);
    }

    /**
     * @return the nBins
     */
    public int getNBins (){
        return nBins;
    }

    /**
     * @param nBins the nBins to set
     */
    public void setNBins (int nBins){
        int old=this.nBins;
        if ( nBins < 1 ){
            nBins = 1;
        }
        this.nBins = nBins;
        getPrefs().putInt("ISIHistogrammer.nBins",nBins);
        resetBins();
        getSupport().firePropertyChange("nBins",old,this.nBins);
    }

    /**
     * @return the maxIsiUs
     */
    public int getMaxIsiUs (){
        return maxIsiUs;
    }

    /**
     * @param maxIsiUs the maxIsiUs to set
     */
    synchronized public void setMaxIsiUs (int maxIsiUs){
        int old=this.maxIsiUs;
        if ( maxIsiUs < minIsiUs ){
            maxIsiUs = minIsiUs;
        }
        this.maxIsiUs = maxIsiUs;
        getPrefs().putInt("ISIHistogrammer.maxIsiUs",maxIsiUs);
        resetBins();
        if ( binAxis != null ){
            binAxis.setUnit(String.format("%d,%d us",minIsiUs,maxIsiUs));
        }
        if ( isiFrame != null ){
            isiFrame.repaint();
        }
        getSupport().firePropertyChange("maxIsiUs",old,maxIsiUs);
    }

    /**
     * @return the minIsiUs
     */
    public int getMinIsiUs (){
        return minIsiUs;
    }

    /**
     * @param minIsiUs the minIsiUs to set
     */
    public void setMinIsiUs (int minIsiUs){
        int old=this.minIsiUs;
        if ( minIsiUs > maxIsiUs ){
            minIsiUs = maxIsiUs;
        }
        this.minIsiUs = minIsiUs;
        getPrefs().putInt("ISIHistogrammer.minIsiUs",minIsiUs);
        resetBins();
        if ( binAxis != null ){
            binAxis.setUnit(String.format("%d,%d us",minIsiUs,maxIsiUs));
        }
        if ( isiFrame != null ){
            isiFrame.repaint();
        }
        getSupport().firePropertyChange("minIsiUs",old, minIsiUs);
    }

    /**
     * @return the tauDecayMs
     */
    public float getTauDecayMs (){
        return tauDecayMs;
    }

    /**
     * @param tauDecayMs the tauDecayMs to set
     */
    public void setTauDecayMs (float tauDecayMs){
        float oldtau=this.tauDecayMs;
        this.tauDecayMs = tauDecayMs;
        getPrefs().putFloat("ISIHistogrammer.tauDecayMs",tauDecayMs);
        getSupport().firePropertyChange("tauDecayMs",oldtau,this.tauDecayMs);
    }

    @Override
    public synchronized void setFilterEnabled (boolean yes){
        super.setFilterEnabled(yes);
        setIsiDisplay(yes);
    }

    @Override
    public synchronized void cleanup (){
        super.cleanup();
        setIsiDisplay(false);
    }

    public void decayHistogram (int timestamp){
        if ( tauDecayMs > 0 && timestamp >= nextDecayTimestamp ){
            float decayconstant = (float)java.lang.Math.exp(-( timestamp - lastDecayTimestamp ) / ( tauDecayMs * 1000 ));
            for ( int i = 0 ; i < bins.length ; i++ ){
                bins[i] = (int)( bins[i] * decayconstant );
            }
            nextDecayTimestamp = (int)( timestamp + ( tauDecayMs * 1000 ) / 10 );
            lastDecayTimestamp = timestamp;
        }
    }
    public Series activitySeries;
    private Axis binAxis;
    private Axis activityAxis;
    private Category activityCategory;
    private XYChart chart;
    private boolean logPlotEnabled = getPrefs().getBoolean("ISIHistogrammer.logPlotEnabled",false);

    private void setIsiDisplay (boolean yes){
        if ( !yes ){
            if ( isiFrame != null ){
                isiFrame.dispose();
                isiFrame = null;
            }
        } else{
            isiFrame = new JFrame("ISIs"){
                @Override
                synchronized public void paint (Graphics g){
                    super.paint(g);
                    try{
                        if ( bins != null ){
                            activitySeries.clear();

                            //log.info("numbins="+myBins.numOfBins);
                            for ( int i = 0 ; i < nBins ; i++ ){
                                if ( isLogPlotEnabled() ){
                                    activitySeries.add(i,(float)Math.log(bins[i]));
                                } else{
                                    activitySeries.add(i,bins[i]);
                                }
                            }

                            binAxis.setMaximum(nBins);
                            binAxis.setMinimum(0);
                            if ( isLogPlotEnabled() ){
                                activityAxis.setMaximum((float)Math.log(getMaxBin()));
                            } else{
                                activityAxis.setMaximum(getMaxBin());
                            }
                        } else{
                            log.warning("bins==null");
                        }
                    } catch ( Exception e ){
                        log.warning("while displaying bins chart caught " + e);
                    }
                }
            };
            isiFrame.setPreferredSize(new Dimension(200,100));
            Container pane = isiFrame.getContentPane();
            chart = new XYChart();
            activitySeries = new Series(2,nBins);

            binAxis = new Axis(0,nBins);
            binAxis.setTitle("bin");
            binAxis.setUnit(String.format("%d,%d us",minIsiUs,maxIsiUs));

            activityAxis = new Axis(0,1); // will be normalized
            activityAxis.setTitle("count");

            activityCategory = new Category(activitySeries,new Axis[]{ binAxis,activityAxis });
            activityCategory.setColor(new float[]{ 1f,1f,1f }); // white for visibility
            activityCategory.setLineWidth(3f);

            chart = new XYChart("ISIs");
            chart.setBackground(Color.black);
            chart.setForeground(Color.white);
            chart.setGridEnabled(false);
            chart.addCategory(activityCategory);

            pane.setLayout(new BorderLayout());
            pane.add(chart,BorderLayout.CENTER);

            isiFrame.setVisible(yes);
        }

    }

    /**
     * @return the logPlotEnabled
     */
    public boolean isLogPlotEnabled (){
        return logPlotEnabled;
    }

    /**
     * @param logPlotEnabled the logPlotEnabled to set
     */
    public void setLogPlotEnabled (boolean logPlotEnabled){
        this.logPlotEnabled = logPlotEnabled;
        getPrefs().putBoolean("ISIHistogrammer.logPlotEnabled",logPlotEnabled);
    }
}
