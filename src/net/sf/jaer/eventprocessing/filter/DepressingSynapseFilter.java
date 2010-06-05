/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing.filter;
import com.sun.opengl.util.j2d.TextRenderer;
import java.awt.Font;
import java.awt.Point;
import java.awt.geom.Rectangle2D;
import java.util.Random;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
/**
 *  Filters out high-firing input using probabalistic depressing synaptic connection. Works particularly well for high firing rate flickering lighting sources with the DVS.
 * @author tobi
 *
 * This is part of jAER
<a href="http://jaer.wiki.sourceforge.net">jaer.wiki.sourceforge.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
public class DepressingSynapseFilter extends EventFilter2D implements FrameAnnotater{
    private static Random random = new Random();
    private Neurons neurons;
    private float tauMs = prefs().getFloat("DepressingSynapseFilter.tauMs",10000); // decay constant us
    private float tauUs=tauMs*1000;
    private float weight = prefs().getFloat("DepressingSynapseFilter.weight",.1f); // weight of each input spike on synapse
    private boolean showStateAtMouse=false;

    private TextRenderer renderer;

    public static String getDescription(){ return "Filters out rapidly firing input using depressing probabalistic synapse model.";}
    public static DevelopmentStatus getDevelopementStatus(){ return DevelopmentStatus.Beta;}
    
    public DepressingSynapseFilter (AEChip chip){
        super(chip); // as usual when we're called here we don't have array size yet. Defer memory allocation to running filter.
        setPropertyTooltip("tauMs","recorery time constant in ms of depressing synapse; recovers full strength with this 1st order time constant; increase to make filtering last longer");
        setPropertyTooltip("weight","weight of each incoming spike on depressing synapse; incrrease to reduce number of spikes within tauMs window to filter out events.");
        setPropertyTooltip("showStateAtMouse","Shows synaptic adaptation state at mouse position (expensive)");
    }

    @Override
    synchronized public EventPacket<?> filterPacket (EventPacket<?> in){
        checkOutputPacketEventType(in);
        checkNeuronAllocation();
        OutputEventIterator outItr = out.outputIterator();
        for ( Object o:in ){
            TypedEvent e = (TypedEvent)o;
            if ( neurons.stimulate(e) ){
                TypedEvent oe = (TypedEvent)outItr.nextOutput();
                oe.copyFrom(e);
            }
        }
        return out;
    }

    @Override
    synchronized public void resetFilter (){
        if(neurons!=null) neurons.reset();
    }

    @Override
    public void initFilter (){
    }

    public void annotate (GLAutoDrawable drawable){
        if(showStateAtMouse==false || neurons==null) return;
        if(!chip.getCanvas().isMouseInside()) return;
        Point p=chip.getCanvas().getMousePixel();
        neurons.display(drawable,p);
    }

    private void checkNeuronAllocation (){
        if(chip.getNumCells()==0) return;
        if(neurons==null  || neurons.getNumCells()!=chip.getNumCells()){
            neurons=new Neurons();
        }
    }



    private class Neurons{
        private final float max = 1; // max state
        Neuron[][][] cells;
        private int numCells;

        public Neurons (){
            numCells=chip.getNumCells();
            cells = new Neuron[ chip.getSizeX() ][ chip.getSizeY() ][ chip.getNumCellTypes() ];
            // fill array
            for ( int i = 0 ; i < cells.length ; i++ ){
                for ( int j = 0 ; j < cells[i].length ; j++ ){
                    for ( int k=0;k<cells[i][j].length;k++ ){
                        cells[i][j][k] = new Neuron();
                    }
                }
            }
        }

        void reset (){
            for ( int i = 0 ; i < cells.length ; i++ ){
                for ( int j = 0 ; j < cells[i].length ; j++ ){
                    for ( Neuron n:cells[i][j] ){
                        if ( n != null ){
                            n.reset();
                        }
                    }
                }
            }
        }
        // returns true if incoming spike caused outgoing spike

        boolean stimulate (TypedEvent e){
            return cells[e.x][e.y][e.type].stimulate(e.timestamp);
        }

        private int getNumCells (){
            return numCells;
        }

        private void display (GLAutoDrawable drawable,Point p){
            if ( renderer == null ){
                renderer = new TextRenderer(new Font("SansSerif",Font.PLAIN,10),true,true);
            }
            Neuron[] n = cells[p.x][p.y];
            renderer.begin3DRendering();
            renderer.setColor(0,0,1,0.8f);
            float s1=n[0].getState(), s2=n[1].getState();
            float avg=(s1+s2)/2;
            String s=String.format("%5.3f",avg);
            Rectangle2D rect=renderer.getBounds(s);
           renderer.draw3D(s,p.x,p.y,0,.7f); // TODO fix string n lines
            renderer.end3DRendering();
             GL gl=drawable.getGL();
            gl.glRectf(p.x,p.y-2,p.x+(float)rect.getWidth()*avg*.7f,p.y-1);

        }


        private class Neuron{
            private boolean initialized = false; // flag to init delta time correctly
            private float state = 0; // internal state, clips to 0:1 and the larger the value, the more depressed is the synapse. The probability to transmit is linear in (1-state).
            private int lastt = 0; // time of last spike input


            // returns true if neuron spike caused by input spike at time t in us
            boolean stimulate (int t){
                if ( !initialized ){
                    lastt = t;
                    initialized=true;
                    return true;
                }
                if ( t < lastt ){
                    reset();
                    return true;
                }
                int dt = t - lastt;
                float delta=dt / tauUs;
                float exp=delta>20?0:(float)Math.exp(-delta);
                float newstate = state * exp + weight;
                if ( newstate > max ){
                    newstate = max;
                }
                boolean spike = random.nextFloat() > state; // spike goes through based on decayed state
                state=newstate;
                lastt=t;
                return spike;
            }

            void reset (){
                initialized = false;
                state = 0;
            }

            float getState(){ return state;}
        }
    }

    /**
     * @return the tauMs
     */
    public float getTauMs (){
        return tauMs;
    }

    /**
     * @param tau the tau to set
     */
    public void setTauMs (float tau){
        this.tauMs = tau;
        prefs().putFloat("DepressingSynapseFilter.tauMs",tau);
        tauUs=tauMs*1000;
    }

    /**
     * @return the weight
     */
    public float getWeight (){
        return weight;
    }

    /**
     * @param weight the weight to set
     */
    public void setWeight (float weight){
        this.weight = weight;
        prefs().putFloat("DepressingSynapseFilter.weight",weight);
    }

    /**
     * @return the showStateAtMouse
     */
    public boolean isShowStateAtMouse (){
        return showStateAtMouse;
    }

    /**
     * @param showStateAtMouse the showStateAtMouse to set
     */
    public void setShowStateAtMouse (boolean showStateAtMouse){
        this.showStateAtMouse = showStateAtMouse;
    }
    
}
