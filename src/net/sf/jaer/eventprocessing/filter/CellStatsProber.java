/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing.filter;
import com.sun.opengl.util.GLUT;
import com.sun.opengl.util.j2d.TextRenderer;
import java.awt.*;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Observable;
import java.util.Observer;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCanvas;
import javax.swing.JMenu;
import net.sf.jaer.aemonitor.AEConstants;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.ChipRendererDisplayMethod;
import net.sf.jaer.graphics.DisplayMethod;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.SpikeSound;
import net.sf.jaer.util.filter.LowpassFilter;
/**
 * Collects and displays statistics for a selected range of pixels / cells.
 *
 * @author tobi
 *
 * This is part of jAER
<a href="http://jaer.wiki.sourceforge.net">jaer.wiki.sourceforge.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
public class CellStatsProber extends EventFilter2D implements FrameAnnotater,MouseListener,MouseMotionListener,Observer{
    public static String getDescription (){
        return "Collects and displays statistics for a selected range of pixels / cells";
    }

    public static DevelopmentStatus getDevelopementStatus (){
        return DevelopmentStatus.Beta;
    }
    private GLCanvas glCanvas;
    private ChipCanvas canvas;
    private DisplayMethod displayMethod;
    private ChipRendererDisplayMethod chipRendererDisplayMethod;
    Rectangle selection = null;
    private static float lineWidth = 1f;
//    private JMenu popupMenu;
    int startx, starty, endx, endy;
    Point startPoint = null, endPoint = null, clickedPoint = null;
//    private GLUT glut = new GLUT();
    private Stats stats = new Stats();
    private boolean rateEnabled = getPrefs().getBoolean("CellStatsProber.rateEnabled",true);
    private boolean isiHistEnabled = getPrefs().getBoolean("CellStatsProber.isiHistEnabled",true);
    private boolean spikeSoundEnabled = getPrefs().getBoolean("CellStatsProber.spikeSoundEnabled",true);
    SpikeSound spikeSound = null;
    private TextRenderer renderer = null;
    volatile boolean selecting = false;
    volatile float binTime = Float.NaN;
    final private static float[] SELECT_COLOR = { .8f,0,0,.5f };
    final private static float[] GLOBAL_HIST_COLOR = { 0,0,.8f,.5f }, INDIV_HIST_COLOR = { 0,.2f,.6f,.5f };
    private Point currentMousePoint = null;
    private int[] currentAddress = null;

    public CellStatsProber (AEChip chip){
        super(chip);
        if ( chip.getCanvas() != null && chip.getCanvas().getCanvas() != null ){
            canvas = chip.getCanvas();
            glCanvas = (GLCanvas)chip.getCanvas().getCanvas();
            renderer = new TextRenderer(new Font("SansSerif",Font.PLAIN,10),true,true);
        }
        currentAddress = new int[ chip.getNumCellTypes() ];
        Arrays.fill(currentAddress,-1);
        chip.addObserver(this);
        final String h = "ISIs", e = "Event rate";
        setPropertyTooltip(h,"isiHistEnabled","enable histogramming interspike intervals");
        setPropertyTooltip(h,"isiMinUs","min ISI in us");
        setPropertyTooltip(h,"isiMaxUs","max ISI in us");
        setPropertyTooltip(h,"isiAutoScalingEnabled","autoscale bounds for ISI histogram");
        setPropertyTooltip(h,"isiNumBins","number of bins in the ISI");
        setPropertyTooltip(h,"showAverageISIHistogram","shows the average of the individual ISI histograms");
        setPropertyTooltip(h,"showIndividualISIHistograms","show the ISI histograms of all the cells in the selection");

        setPropertyTooltip(e,"rateEnabled","measure event rate");
        setPropertyTooltip(e,"rateTauMs","lowpass filter time constant in ms for measuring event rate");
        setPropertyTooltip("spikeSoundEnabled","enable playing spike sound whenever the selected region has events");
        setPropertyTooltip(h,"individualISIsEnabled","enables individual ISI statistics for each cell in selection. Disabling lumps all cells into one for ISI computation.");

    }

    public void displayStats (GLAutoDrawable drawable){
        if ( drawable == null || selection == null || chip.getCanvas() == null ){
            return;
        }
        canvas = chip.getCanvas();
        glCanvas = (GLCanvas)canvas.getCanvas();
        int sx = chip.getSizeX(), sy = chip.getSizeY();
        Rectangle chipRect = new Rectangle(sx,sy);
        GL gl = drawable.getGL();
        if ( !chipRect.intersects(selection) ){
            return;
        }
        drawSelection(gl,selection,SELECT_COLOR);
        stats.drawStats(drawable);
        stats.play();

    }

    private void getSelection (MouseEvent e){
        Point p = canvas.getPixelFromMouseEvent(e);
        endPoint = p;
        startx = min(startPoint.x,endPoint.x);
        starty = min(startPoint.y,endPoint.y);
        endx = max(startPoint.x,endPoint.x);
        endy = max(startPoint.y,endPoint.y);
        int w = endx - startx;
        int h = endy - starty;
        selection = new Rectangle(startx,starty,w,h);
    }

    private boolean inSelection (BasicEvent e){
        if ( selection.contains(e.x,e.y) ){
            return true;
        }
        return false;
    }

    public void showContextMenu (){
    }

    private void drawSelection (GL gl,Rectangle r,float[] c){
        gl.glPushMatrix();
        gl.glColor3fv(c,0);
        gl.glLineWidth(lineWidth);
        gl.glTranslatef(-.5f,-.5f,0);
        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex2f(selection.x,selection.y);
        gl.glVertex2f(selection.x + selection.width,selection.y);
        gl.glVertex2f(selection.x + selection.width,selection.y + selection.height);
        gl.glVertex2f(selection.x,selection.y + selection.height);
        gl.glEnd();
        gl.glPopMatrix();

    }

    @Override
    public EventPacket filterPacket (EventPacket in){
        if ( !selecting ){
            stats.collectStats(in);
        }
        return in;
    }

    @Override
    synchronized public void resetFilter (){
//        selection = null;
        stats.resetISIs();
    }

    @Override
    public void initFilter (){
    }

    public void annotate (GLAutoDrawable drawable){
        if ( canvas.getDisplayMethod() instanceof ChipRendererDisplayMethod ){
            chipRendererDisplayMethod = (ChipRendererDisplayMethod)canvas.getDisplayMethod();
            displayStats(drawable);
        }
    }

    /**
     * @return the rateEnabled
     */
    public boolean isRateEnabled (){
        return rateEnabled;
    }

    /**
     * @param rateEnabled the rateEnabled to set
     */
    public void setRateEnabled (boolean rateEnabled){
        this.rateEnabled = rateEnabled;
        prefs().putBoolean("CellStatsProber.rateEnabled",rateEnabled);
    }

    /**
     * @return the isiHistEnabled
     */
    public boolean isIsiHistEnabled (){
        return isiHistEnabled;
    }

    /**
     * @param isiHistEnabled the isiHistEnabled to set
     */
    public void setIsiHistEnabled (boolean isiHistEnabled){
        this.isiHistEnabled = isiHistEnabled;
        prefs().putBoolean("CellStatsProber.isiHistEnabled",isiHistEnabled);
    }

    /**
     * @return the spikeSoundEnabled
     */
    public boolean isSpikeSoundEnabled (){
        return spikeSoundEnabled;
    }

    /**
     * @param spikeSoundEnabled the spikeSoundEnabled to set
     */
    public void setSpikeSoundEnabled (boolean spikeSoundEnabled){
        this.spikeSoundEnabled = spikeSoundEnabled;
        prefs().putBoolean("CellStatsProber.spikeSoundEnabled",spikeSoundEnabled);
    }

    public void mouseWheelMoved (MouseWheelEvent e){
    }

    public void mouseReleased (MouseEvent e){
        if ( startPoint == null ){
            return;
        }
        getSelection(e);
        selecting = false;
        stats.resetISIs();
    }

    private int min (int a,int b){
        return a < b ? a : b;
    }

    private int max (int a,int b){
        return a > b ? a : b;
    }

    public void mousePressed (MouseEvent e){
        Point p = canvas.getPixelFromMouseEvent(e);
        startPoint = p;
        selecting = true;
    }

    public void mouseMoved (MouseEvent e){
        currentMousePoint = canvas.getPixelFromMouseEvent(e);
        for ( int k = 0 ; k < chip.getNumCellTypes() ; k++ ){
            currentAddress[k] = chip.getEventExtractor().getAddressFromCell(currentMousePoint.x,currentMousePoint.y,k);
//            System.out.println(currentMousePoint+" gives currentAddress["+k+"]="+currentAddress[k]);
        }
    }

    public void mouseExited (MouseEvent e){
        selecting = false;
    }

    public void mouseEntered (MouseEvent e){
    }

    public void mouseDragged (MouseEvent e){
        if ( startPoint == null ){
            return;
        }
        getSelection(e);
    }

    public void mouseClicked (MouseEvent e){
        Point p = canvas.getPixelFromMouseEvent(e);
        clickedPoint = p;
    }
    
       @Override
    public void setSelected (boolean yes){
        super.setSelected(yes);
          if ( glCanvas == null ){
            return;
        }
        if ( yes ){
            glCanvas.addMouseListener(this);
            glCanvas.addMouseMotionListener(this);

        } else{
            glCanvas.removeMouseListener(this);
            glCanvas.removeMouseMotionListener(this);
        }
    }

    public void setRateTauMs (float tauMs){
        stats.setRateTauMs(tauMs);
    }

    public float getRateTauMs (){
        return stats.getRateTauMs();
    }

    public void setIsiNumBins (int isiNumBins){
        stats.setIsiNumBins(isiNumBins);
    }

    public void setIsiMinUs (int isiMinUs){
        stats.setIsiMinUs(isiMinUs);
    }

    public void setIsiMaxUs (int isiMaxUs){
        stats.setIsiMaxUs(isiMaxUs);
    }

    public void setIsiAutoScalingEnabled (boolean isiAutoScalingEnabled){
        stats.setIsiAutoScalingEnabled(isiAutoScalingEnabled);
    }

    public boolean isIsiAutoScalingEnabled (){
        return stats.isIsiAutoScalingEnabled();
    }

    public void setIndividualISIsEnabled (boolean individualISIsEnabled){
        stats.setIndividualISIsEnabled(individualISIsEnabled);
    }

    public boolean isIndividualISIsEnabled (){
        return stats.isIndividualISIsEnabled();
    }

    public int getIsiNumBins (){
        return stats.getIsiNumBins();
    }

    public int getIsiMinUs (){
        return stats.getIsiMinUs();
    }

    public int getIsiMaxUs (){
        return stats.getIsiMaxUs();
    }

    public void setShowAverageISIHistogram (boolean showAverageISIHistogram){
        stats.setShowAverageISIHistogram(showAverageISIHistogram);
    }

    public boolean isShowAverageISIHistogram (){
        return stats.isShowAverageISIHistogram();
    }

    public void setShowIndividualISIHistograms (boolean showIndividualISIHistograms){
        stats.setShowIndividualISIHistograms(showIndividualISIHistograms);
    }

    public boolean isShowIndividualISIHistograms (){
        return stats.isShowIndividualISIHistograms();
    }

    synchronized public void update (Observable o,Object arg){
        currentAddress = new int[ chip.getNumCellTypes() ];
        Arrays.fill(currentAddress,-1);
    }
    private class Stats{
        public void setRateTauMs (float tauMs){
            rateFilter.setTauMs(tauMs);
            prefs().putFloat("CellStatsProber.rateTauMs",tauMs);
        }

        public float getRateTauMs (){
            return rateFilter.getTauMs();
        }
        private int isiMinUs = prefs().getInt("CellStatsProber.isiMinUs",0),
                isiMaxUs = prefs().getInt("CellStatsProber.isiMaxUs",100000),
                isiNumBins = prefs().getInt("CellStatsProber.isiNumBins",100);
//              private int[] bins = new int[isiNumBins];
//        private int lessCount = 0, moreCount = 0;
//        private int maxCount = 0;
        private boolean isiAutoScalingEnabled = prefs().getBoolean("CellStatsProber.isiAutoScalingEnabled",false);
        private boolean individualISIsEnabled = prefs().getBoolean("CellStatsProber.individualISIsEnabled",true);
        private boolean showAverageISIHistogram = prefs().getBoolean("CellStatsProber.showAverageISIHistogram",true);
        private boolean showIndividualISIHistograms = prefs().getBoolean("CellStatsProber.showIndividualISIHistograms",true);

        public LowpassFilter getRateFilter (){
            return rateFilter;
        }
        private LowpassFilter rateFilter = new LowpassFilter();

        {
            rateFilter.setTauMs(prefs().getFloat("CellStatsProber.rateTauMs",10));
        }
        boolean initialized = false;
        float instantaneousRate = 0, filteredRate = 0;
        int count = 0;
        private HashMap<Integer,ISIHist> histMap = new HashMap();
        ISIHist globalHist = new ISIHist(-1);
        private int nPixels = 0;

        synchronized public void collectStats (EventPacket in){
            if ( selection == null ){
                return;
            }
            nPixels = ( ( selection.width + 1 ) * ( selection.height + 1 ) );
            stats.count = 0;
            for ( Object o:in ){
                BasicEvent e = (BasicEvent)o;
                if ( inSelection(e) ){
                    stats.count++;
                    if ( isiHistEnabled ){
                        if ( individualISIsEnabled ){
                            ISIHist h = histMap.get(e.address);
                            if ( h == null ){
                                h = new ISIHist(e.address);
                                histMap.put(e.address,h);
//                                System.out.println("added hist for "+e);
                            }
                            h.addEvent(e);
                        } else{
                            globalHist.addEvent(e);
                        }
                    }
                    globalHist.lastT = e.timestamp;
                }
            }
            if ( stats.count > 0 ){
                measureAverageEPS(globalHist.lastT,stats.count);
            }
            if ( individualISIsEnabled ){
                globalHist.reset();
                for ( ISIHist h:histMap.values() ){
                    for ( int i = 0 ; i < isiNumBins ; i++ ){
                        int v = globalHist.bins[i] += h.bins[i];
                        if ( v > globalHist.maxCount ){
                            globalHist.maxCount = v;
                        }
                    }
                }
            }
        }

        /**
         * @return the showAverageISIHistogram
         */
        public boolean isShowAverageISIHistogram (){
            return showAverageISIHistogram;
        }

        /**
         * @param showAverageISIHistogram the showAverageISIHistogram to set
         */
        public void setShowAverageISIHistogram (boolean showAverageISIHistogram){
            this.showAverageISIHistogram = showAverageISIHistogram;
            prefs().putBoolean("CellStatsProber.showAverageISIHistogram",showAverageISIHistogram);
        }

        /**
         * @return the showIndividualISIHistograms
         */
        public boolean isShowIndividualISIHistograms (){
            return showIndividualISIHistograms;
        }

        /**
         * @param showIndividualISIHistograms the showIndividualISIHistograms to set
         */
        public void setShowIndividualISIHistograms (boolean showIndividualISIHistograms){
            this.showIndividualISIHistograms = showIndividualISIHistograms;
            prefs().putBoolean("CellStatsProber.showIndividualISIHistograms",showIndividualISIHistograms);
        }
        private class ISIHist{
            int[] bins = new int[ isiNumBins ];
            int lessCount = 0, moreCount = 0;
            int maxCount = 0;
            int lastT = 0, prevLastT = 0;
            boolean virgin = true;
            int address = -1;

            public ISIHist (int addr){
                this.address = addr;
            }

            void addEvent (BasicEvent e){
                if ( virgin ){
                    lastT = e.timestamp;
                    virgin = false;
                    return;
                }
                int isi = e.timestamp - lastT;
                if ( isi < 0 ){
                    lastT = e.timestamp; // handle wrapping
                    return;
                }
                if ( isiAutoScalingEnabled ){
                    if ( isi > isiMaxUs ){
                        setIsiMaxUs(isi);
                    } else if ( isi < isiMinUs ){
                        setIsiMinUs(isi);
                    }
                }
                int bin = getIsiBin(isi);
                if ( bin < 0 ){
                    lessCount++;
                } else if ( bin >= isiNumBins ){
                    moreCount++;
                } else{
                    int v = ++bins[bin];
                    if ( v > maxCount ){
                        maxCount = v;
                    }
                }
                lastT = e.timestamp;
            }

            // must set line width and color first
            void draw (GL gl){

                gl.glBegin(GL.GL_LINES);
                gl.glVertex2f(1,1);
                gl.glVertex2f(chip.getSizeX() - 1,0);
                gl.glEnd();

                if ( maxCount > 0 ){
                    gl.glBegin(GL.GL_LINE_LOOP);
                    float dx = (float)( chip.getSizeX() - 2 ) / isiNumBins;
                    float sy = (float)( chip.getSizeY() - 2 ) / maxCount;
                    for ( int i = 0 ; i < bins.length ; i++ ){
                        float y = 1 + sy * bins[i];
                        float x1 = 1 + dx * i, x2 = x1 + dx;
                        gl.glVertex2f(x1,1);
                        gl.glVertex2f(x1,y);
                        gl.glVertex2f(x2,y);
                        gl.glVertex2f(x2,1);
                    }
                    gl.glEnd();
                }
            }

            void draw (GL gl,float lineWidth,float[] color){
                gl.glPushAttrib(GL.GL_COLOR | GL.GL_LINE_WIDTH);
                gl.glLineWidth(lineWidth);
                gl.glColor4fv(color,0);
                draw(gl);
                gl.glPopAttrib();
            }

            private void reset (){
                if ( bins == null || bins.length != isiNumBins ){
                    bins = new int[ isiNumBins ];
                } else{
                    Arrays.fill(globalHist.bins,0);
                }
                lessCount = 0;
                moreCount = 0;
                maxCount = 0;
                virgin = true;
            }
        }

        @Override
        public String toString (){
            return String.format("n=%d, keps=%.1f",count,filteredRate);
        }

        private void measureAverageEPS (int lastT,int n){
            if ( !rateEnabled ){
                return;
            }
            final float maxRate = 10e6f;
            if ( !initialized ){
                globalHist.prevLastT = lastT;
                initialized = true;
            }
            int dt = lastT - globalHist.prevLastT;
            globalHist.prevLastT = lastT;
            if ( dt < 0 ){
                initialized = false;
            }
            if ( dt == 0 ){
                instantaneousRate = maxRate; // if the time interval is zero, use the max rate
            } else{
                instantaneousRate = 1e6f * (float)n / ( dt * AEConstants.TICK_DEFAULT_US );
            }
            filteredRate = rateFilter.filter(instantaneousRate / nPixels,lastT);
        }

        synchronized private void drawStats (GLAutoDrawable drawable){
            GL gl = drawable.getGL();

            renderer.begin3DRendering();
//            renderer.beginRendering(drawable.getWidth(), drawable.getHeight());
            // optionally set the color
            renderer.setColor(0,0,1,0.8f);
            if ( rateEnabled ){
                renderer.draw3D(this.toString(),1,chip.getSizeY() - 4,0,.5f); // TODO fix string n lines
            }
            // ... more draw commands, color changes, etc.
            renderer.end3DRendering();

            // draw hist
            if ( isiHistEnabled ){
                renderer.draw3D(String.format("%d",isiMinUs),-1,-3,0,0.5f);
                renderer.draw3D(String.format("%d",isiMaxUs),chip.getSizeX() - 8,-3,0,0.5f);
                if ( individualISIsEnabled ){
                    if ( showAverageISIHistogram ){
                        gl.glPushMatrix();
                        globalHist.draw(gl,3,GLOBAL_HIST_COLOR);
                        gl.glPopMatrix();
                    }
                    if ( showIndividualISIHistograms ){
                        int n = histMap.size();
                        gl.glPushMatrix();
                        int k = 0;
                        gl.glLineWidth(1);
                        gl.glColor4fv(INDIV_HIST_COLOR,0);
                        for ( ISIHist h:histMap.values() ){
                            gl.glPushMatrix();
                            gl.glScalef(1,1f / n,1); // if n=10 and sy=128 then scale=1/10 scale so that all n fit in viewpoort of chip, each one is scaled to chip size y
                            boolean sel = false;
                            for ( int a:currentAddress ){
                                if ( a == h.address ){
                                    sel = true;
                                }
                            }
                            if ( !sel ){
                                h.draw(gl);
                            } else{
                                h.draw(gl,2,SELECT_COLOR);
                            }
                            gl.glPopMatrix();
                            gl.glTranslatef(0,(float)chip.getSizeY() / n,0);
                        }
                        gl.glPopMatrix();
                    }
                } else{
                    globalHist.draw(gl,3,GLOBAL_HIST_COLOR);
                }
                if ( currentMousePoint != null ){
                    if ( currentMousePoint.y <= 0 ){
                        binTime = (float)currentMousePoint.x / chip.getSizeX() * ( stats.isiMaxUs - stats.isiMinUs ) + stats.isiMinUs;
                        gl.glColor3fv(SELECT_COLOR,0);
                        renderer.draw3D(String.format("%.0f us",binTime),currentMousePoint.x,-4,0,.5f);
                        gl.glLineWidth(3);
                        gl.glColor3fv(SELECT_COLOR,0);
                        gl.glBegin(GL.GL_LINES);
                        gl.glVertex2f(currentMousePoint.x,0);
                        gl.glVertex2f(currentMousePoint.x,chip.getSizeY());
                        gl.glEnd();
                    }
                }
            }
        }

        private void play (){
            if ( !spikeSoundEnabled ){
                return;
            }
            if ( spikeSound == null ){
                spikeSound = new SpikeSound();
            }
            if ( count > 0 ){
                spikeSound.play();
            }
        }

        /**
         * @return the individualISIsEnabled
         */
        public boolean isIndividualISIsEnabled (){
            return individualISIsEnabled;
        }

        /**
         * @param individualISIsEnabled the individualISIsEnabled to set
         */
        public void setIndividualISIsEnabled (boolean individualISIsEnabled){
            this.individualISIsEnabled = individualISIsEnabled;
            prefs().putBoolean("CellStatsProber.individualISIsEnabled",individualISIsEnabled);
        }

        /**
         * @return the isiMinUs
         */
        public int getIsiMinUs (){
            return isiMinUs;
        }

        /**
         * @param isiMinUs the isiMinUs to set
         */
        synchronized public void setIsiMinUs (int isiMinUs){
            if ( this.isiMinUs == isiMinUs ){
                return;
            }
            int old = this.isiMinUs;
            this.isiMinUs = isiMinUs;
            if ( isiAutoScalingEnabled ){
                support.firePropertyChange("isiMinUs",old,isiMinUs);
            } else{
                prefs().putInt("CellStatsProber.isiMinUs",isiMinUs);
            }
            resetISIs();
        }

        /**
         * @return the isiMaxUs
         */
        public int getIsiMaxUs (){
            return isiMaxUs;
        }

        /**
         * @param isiMaxUs the isiMaxUs to set
         */
        synchronized public void setIsiMaxUs (int isiMaxUs){
            if ( this.isiMaxUs == isiMaxUs ){
                return;
            }
            int old = this.isiMaxUs;
            this.isiMaxUs = isiMaxUs;
            if ( isiAutoScalingEnabled ){
                support.firePropertyChange("isiMaxUs",old,isiMaxUs);
            } else{
                prefs().putInt("CellStatsProber.isiMaxUs",isiMaxUs);
            }
            resetISIs();
        }

        synchronized private void resetISIs (){
            globalHist.reset();
            histMap.clear();
        }

        private int getIsiBin (int isi){
            if ( isi < isiMinUs ){
                return -1;
            } else if ( isi > isiMaxUs ){
                return isiNumBins;
            } else{
                int binSize = ( isiMaxUs - isiMinUs ) / isiNumBins;
                if ( binSize <= 0 ){
                    return -1;
                }
                return ( isi - isiMinUs ) / binSize;
            }
        }

        /**
         * @return the isiNumBins
         */
        public int getIsiNumBins (){
            return isiNumBins;
        }

        /**
         * @param isiNumBins the isiNumBins to set
         */
        synchronized public void setIsiNumBins (int isiNumBins){
            if ( isiNumBins == this.isiNumBins ){
                return;
            }
            int old = this.isiNumBins;
            if ( isiNumBins < 1 ){
                isiNumBins = 1;
            }
            this.isiNumBins = isiNumBins;
            resetISIs();
            prefs().putInt("CellStatsProber.isiNumBins",isiNumBins);
            support.firePropertyChange("isiNumBins",old,isiNumBins);
        }

        /**
         * @return the isiAutoScalingEnabled
         */
        public boolean isIsiAutoScalingEnabled (){
            return isiAutoScalingEnabled;
        }

        /**
         * @param isiAutoScalingEnabled the isiAutoScalingEnabled to set
         */
        synchronized public void setIsiAutoScalingEnabled (boolean isiAutoScalingEnabled){
            this.isiAutoScalingEnabled = isiAutoScalingEnabled;
            prefs().putBoolean("CellStatsProber.isiAutoScalingEnabled",isiAutoScalingEnabled);
        }
    }
}
