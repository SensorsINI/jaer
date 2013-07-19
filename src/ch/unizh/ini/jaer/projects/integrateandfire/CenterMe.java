/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */




package ch.unizh.ini.jaer.projects.integrateandfire;


// JAER Stuff
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;





/**
 * @depricated
 * @author Peter
 */
public class CenterMe extends EventFilter2D {
/* Centers the image by taking a moving average.  Note: it'd be nice to transform 
 * this to a moving median, to make it more noise tolerant, but it's a little 
 * trickier to program. */


        int N=1000;  // Window length
        float xc=64;    // x-center
        float yc=64;    // y-center
        float dxc=0;    // x-delta
        float dyc=0;    // y-delta

        
        public void update(int x,int y){

            float delx,dely;
                    
            delx=(x-xc);
            dely=(y-yc);

            xc=xc+delx/N;
            yc=yc+dely/N;

            // Update expected deviation from center
            dxc=dxc+(delx-dxc)/N;
            dyc=dyc+(delx-dyc)/N;

        }

    // Deal with incoming packet
    @Override public EventPacket<?> filterPacket( EventPacket<?> P){

        int dim=128;
        short xx,yy;
        
        for(Object e:P){ // iterate over the input packet**
            PolarityEvent E=(PolarityEvent)e; // cast the object to basic event to get timestamp, x and y**
            update(E.x,E.y);
            
            E.x= (short) (Math.max(Math.min(64+E.x-xc, 127),0));
            E.y= (short) (Math.max(Math.min(64+E.y-yc, 127),0));
            

        }
        
        return P;
    }

    // Read the Network File on filter Reset
    @Override public void resetFilter(){
       
    }

    //------------------------------------------------------
    // Obligatory method overrides

    //  Initialize the filter
    public  CenterMe(AEChip  chip){
        super(chip);

        setPropertyTooltip("N", "Weight");
    }

    // Nothing
    @Override public void initFilter()
    {
    }



    public int getN() {
        
        return this.N;
    }

    public void setN(int dt) {
        getPrefs().putInt("CenterMe.N",dt);
        //support.firePropertyChange("CenterMe.N",this.N,dt);
        this.N = dt;
    }



}
