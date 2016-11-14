/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.util.histogram;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.awt.TextRenderer;

/**
 *
 * @author matthias
 * 
 * The abstract class Histogram provides some fundamental methods and structures
 * for the histograms.
 */
public abstract class AbstractHistogram implements Histogram {
    protected int window;
    
    protected int start;
    protected int step;
    protected int nBins;
    
    protected float[] gaussian;
    
    protected boolean drawAllBins=true;
    
    
    /**
     * Creates a new AbstractHistogram based on the default values.
     */
    public AbstractHistogram() {
        this(1000, 100, 10000, 0);
    }
    
    /**
     * Creates a new AbstractHistogram.
     * 
     * @param start The starting value of the histogram, i.e. the lower edge of the first bin value.
     * @param step The step size of the histogram.
     * @param nBins The number of bins used by the histogram.
     * @param window The window specifies how the values are distributed over
     * the neighboring bins. Set window to zero to simply bin the values ordinarily. To spread over the nearest neighbor bins
     * in each direction, set window to 1, etc.
     */
    public AbstractHistogram(int start, int step, int nBins, int window) {
        this.start = start;
        this.step = step;
        this.nBins = nBins;
        this.window = window;
    }
    
    @Override
    public void init() {
        this.gaussian = new float[2 * window + 1];
        float s = 0;
        for (int i = -window; i <= window; i++) {
            double p = -Math.pow(i, 2) / 2;
            double e = Math.exp(p);
            this.gaussian[i + window] = (float)(1 / Math.sqrt(2*Math.PI) * e);
            s += this.gaussian[i + window];
        }
        for (int i = 0; i < this.gaussian.length; i++) {
            this.gaussian[i] /= s;
        }
    }

    @Override
    public int getStart() {
        return this.start;
    }

    @Override
    public int getStep() {
        return this.step;
    }
    
    @Override
    public void draw(GLAutoDrawable drawable, TextRenderer renderer, float x, float y, int height, int resolution) {
        
        // TODO if resolution is larger than histogram size (e.g. resolution = 100 bins and histogram is only 32 bins) then drawing does not work correctly.
        GL2 gl = drawable.getGL().getGL2();
        int from, to;
        if (drawAllBins) {
            from = this.start;
            to = this.getSize();
        } else {  // show only bins from 10-90% cummulative bin count
            from = 0;
            float total = 0;
            while (total < 0.1 && from < this.getSize()) {
                total += this.getNormalized(from);
                from++;
            }
            to = from;
            from = Math.max(0, from - 2);
            while (total < 0.9 && to < this.getSize()) {
                total += this.getNormalized(to);
                to++;
            }
            to = Math.min(to + 2, this.nBins);
        }
        int pack = (to - from) / resolution + 1; // e.g. for 1024 bins and 100 pixel resolution, pack is 1025/100=102
        
        float [] sum = new float[resolution + 1]; // make new histogram that adds up bins from original, e.g. 103 bins
        int counter = 0;
        
        for (int i = from; i < to; i++) {
            sum[counter] += this.getNormalized(i);
            
            if (i % pack == 0 && i != 0) { // every 102 i increment packed histogram bin counter
                counter++;
            }
        }
        
        float max = 0;
        for (int i = 0; i < sum.length; i++) {
            if (max < sum[i]) max = sum[i];
        }
        
        for (int i = 0; i < sum.length; i++) { // draw packed histogram
            float h = sum[i] / max * (height - 4);
            gl.glBegin(GL.GL_LINE_LOOP);
            {
                gl.glVertex2f(x + i, y - height + 3);
                gl.glVertex2f(x + i, y - height + h + 3);

                gl.glVertex2f(x + i + 1, y - height + h + 3);
                gl.glVertex2f(x + i + 1, y - height + 3);
            }
            gl.glEnd();
        }
        
        renderer.begin3DRendering();
//        renderer.draw3D("histogram [au]: " + (this.start + from * this.step) + ", " + (this.start + to * this.step) + ".", x, y, 0, 0.5f);
        String s=String.format("range [%d,%d], N=%d, entropy=%.2f",this.start + from * this.step,this.start + to * this.step,this.getN(),computeEntropy());
        renderer.draw3D(s, x, y, 0, 0.2f);
        renderer.end3DRendering();
    }
    
    /**
     * Sets whether all bins are drawn or just a range that includes 10-90% of
     * the filled bins. Default value is true.
     *
     * @param yes true to draw all bins.
     */
    public void setDrawAllBins(boolean yes) {
        this.drawAllBins = yes;
    }

    /**
     * Returns whether all bins are drawn or just a range that includes 10-90%
     * of the filled bins. Default value is true.
     *
     * @return true when drawing all bins.
     */
    public boolean isDrawAllBins() {
        return drawAllBins;
    }
    
    /** Computes entropy measure -sum(p_i*log(p_i)) of the histogram where p_i is the normalized frequency of the bin number i. 
     * This measure is maximum when histogram is flat and takes the value logN, where N is the number of bins. 
     * If all values are concentrated in
     * a few bins then the entropy will be small, e.g., if there is only a single bin filled then the entropy will
     * be zero.
     * 
     * @return the entropy measure 
     */
    public float computeEntropy(){
        int to=getSize();
        double sum=0;
        for(int i=0;i<to;i++){
            float v=getNormalized(i);
            if(v>0) sum+=v*Math.log(v);
        }
        return -(float)sum;
    }
    
    /** Prints to System.out. Empty implementation by default.
     * 
     */
    public void print(){
        // empty by default
    }

}
