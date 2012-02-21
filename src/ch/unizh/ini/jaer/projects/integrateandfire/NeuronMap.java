/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.integrateandfire;

import net.sf.jaer.event.OutputEventIterator;

/**
 *
 * @author Peter
 */
public class NeuronMap implements LIFcontroller {
    
    ENeuron[][] N;
    private float[][]   Wi;  // Input weight kernel
    private float[][]   Wa;  // Auto-weight kernel
    byte type;
    
    public static enum builtFilt {none,mexicanHat,vert,hori,buffer,square,surroundExcite,surroundInhibit};
    
    short dimx=128;
    short dimy=128;
    
    float autoStrength=1;
    
    int maxdepth=100;
    
    
    // =========================================================================
    // Active Methods
    
    
    // Signal from input
    public void inputSpike(short cx, short cy, int timestamp, OutputEventIterator outItr) throws Exception
    {   // Equivalent to inputSpike(1,cx,cy,timestamp,outItr); // Left separate for minor performance gain.
        int midy=(short)(Wi.length-1)/2;
        for (int i=0;i<Wi.length;i++) // Iterate through connections
        {   short y=(short)(cy+i-midy);
            int midx=(short)(Wi[i].length-1)/2;
            for (int j=0;j<Wi[i].length;j++)
            {   short x=(short) (cx+j-midx);
                if (y<0 || y>=dimy || x<0 ||x>=dimx) continue;
                
                boolean fire=N[y][x].spike(Wi[i][j],timestamp,outItr);
                if (fire && Wa.length>0){ // Second clause is for optimization: avoiding unnecessary method call.
                    propagate(x,y,1,timestamp,outItr);
                }
            }
        }
    }
    
    // Signal from input
    public void inputSig(float val,short cx, short cy, int timestamp, OutputEventIterator outItr) throws Exception
    {
        int midy=(short)(Wi.length-1)/2;
        for (int i=0;i<Wi.length;i++) // Iterate through connections
        {   short y=(short)(cy+i-midy);
            int midx=(short)(Wi[i].length-1)/2;
            for (int j=0;j<Wi[i].length;j++)
            {   short x=(short) (cx+j-midx);
                if (y<0 || y>=dimy || x<0 ||x>=dimx) continue;
                
                boolean fire=N[y][x].spike(Wi[i][j]*val,timestamp,outItr);
                if (fire && Wa.length>0){ // Second clause is for optimization: avoiding unnecessary method call.
                    propagate(x,y,1,timestamp,outItr);
                }
            }
        }
    }
    
    // Propagate 
    public void propagate(short cx, short cy, int depth,int timestamp, OutputEventIterator outItr) throws Exception
    {   // Propagate an event through the network.  Call this after a unit has fired.
        // TRICK: if depth=-1, "source" refers not the the source but the destination.
        
        
        if (depth>maxdepth){
            throw new Exception("This spike has triggered too many (>"+maxdepth+") propagations.  See maxdepth");
        }
        int midy=(short)(Wa.length/2);
        
        for (int i=0;i<Wa.length;i++) // Iterate through connections
        {   short y=(short)(i-midy);
            int midx=(short)(Wa[i].length/2);
            for (int j=0;j<Wa[i].length;j++)
            {   
                short x=(short) (j-midx);
                if (y<0 || y>=dimy || x<0 ||x>=dimx) continue;
                
                boolean fire=N[y][x].spike(Wa[i][j]*autoStrength,timestamp,outItr); // TODO: make this more efficient by pre-multiplying
                if (fire){
                    propagate(x,y,depth+1,timestamp,outItr);
                }
            }
        }
    }
    
    public void stimulate(short cx, short cy, float weight, OutputEventIterator outItr) throws Exception
    {   // Directly stimulate a neuron with a given weight
        
        stimulate(cx,cy,weight,outItr,(int)System.nanoTime());
    }    
    
    public void stimulate(short cx, short cy, float weight, OutputEventIterator outItr, int timestamp) throws Exception
    {   // Directly stimulate a neuron with a given weight
        
        boolean fire=N[cy][cx].spike(weight,timestamp,outItr);
        if (fire){
            propagate(cx,cy,1,timestamp,outItr);
        }
    }
    
    @Override
    public String networkStatus(){
        return "Neuron Map of size "+dimx+"x"+dimy;
    }
    
    // =========================================================================
    // Initialization Methods
    
    public void setInputFilter(builtFilt f)
    {   Wi=grabFilter(f);
    }
    
    public void setAutoFilter(builtFilt f)
    {   Wa=grabFilter(f);
    }
    
    float[][] grabFilter(builtFilt f)
    {   float[][] W_;
        switch (f){
            case mexicanHat:
                int size=5;
                W_=new float[size][size];
                for (short i=0;i<size;i++)
                    for (short j=0;j<size;j++)
                    {   float exponent=(float)-((  Math.pow(i-size/2,2)  +  Math.pow(j-size/2,2)  )*12/(Math.pow(size,2)));    
                        W_[i][j]=(float) (2*Math.exp(exponent)-Math.exp(exponent/4));
                    }
                break;
            case square:
                size=2;
                W_=new float[size][size];
                float ww=(float)(1./Math.pow(size,2));
                for (short i=0;i<size;i++)
                    for (short j=0;j<size;j++)
                        W_[i][j]=ww;
                break;
                
            case hori:
                W_=new float[2][1];
                W_[0][0]=1;
                W_[1][0]=-1;
                break;
            case vert:
                W_=new float[1][2];
                W_[0][0]=1;
                W_[0][1]=-1;
                break;
            case buffer:
                W_=new float[1][1];
                W_[0][0]=1;
                break;
            case surroundInhibit:
                size=3;
                W_=new float[size][size];
                ww=(float)-(1./Math.pow(size,2));
                for (short i=0;i<size;i++)
                    for (short j=0;j<size;j++)
                        if (i==size/2 && j==size/2)
                            W_[i][j]=0;
                        else
                            W_[i][j]=ww;
                break;
            case surroundExcite:
                size=3;
                W_=new float[size][size];
                ww=(float)(1./Math.pow(size,2));
                for (short i=0;i<size;i++)
                    for (short j=0;j<size;j++)
                        if (i==size/2 && j==size/2)
                            W_[i][j]=0;
                        else
                            W_[i][j]=ww;
                break;
               
                
            case none: 
            default:
                W_=new float[0][0];
        }
        return W_;
    };
    
    
    
    // =========================================================================
    // Global Network Settings Change Methods
    
    public void setAllOutputStates(boolean state)
    {   for (ENeuron[] nr:N)
            for (ENeuron n:nr)
                n.out=state;    
    }
    
    @Override
    public void setDoubleThresh(boolean dubStep)
    {   for (Neuron[] nr:N)
            for (Neuron n:nr)
                n.doublethresh=dubStep;  
    }
    
    @Override
    public void setThresholds(float thresh)
    {   for (Neuron[] nr:N)
            for (Neuron n:nr)
                n.thresh=thresh;    
    }
    
    @Override
    public void setTaus(float tc)
    {   for (Neuron[] nr:N)
            for (Neuron n:nr)
                n.tau=tc;      
    }
    
    
    @Override
    public void setSats(float tc)
    {   for (Neuron[] nr:N)
            for (Neuron n:nr)
                n.sat=tc;      
    }
    
    @Override
    public void reset()
    {   for (Neuron[] nr:N)
            for (Neuron n:nr)
                n.reset();    
    }
    
    public void build(short idimx,short idimy)
    {
        dimx=idimx;
        dimy=idimy;
        
        N=new ENeuron[dimy][dimx];
        
        //short sWy=(short) Math.floor(W.length);
        //short sWx=(short) Math.floor(W[0].length);
        //W=new float[sWy][sWx];       
        
        //short wdim=(short) (Math.floor(W.length)*Math.floor(W[0].length));
        
        for (short i=0;i<dimy;i++)
        {
            for (short j=0; j<dimx; j++)
            {   coord co=ind2xy(i);
                N[i][j]=new ENeuron();
                N[i][j].y=i;
                N[i][j].x=j;
                N[i][j].type=type;
            }
            
        }
        
        // Default filter settings
        setInputFilter(builtFilt.buffer);
        setAutoFilter(builtFilt.none);
    }
       
    public int xy2ind(short x, short y)
    {   // Given input coordiates x,y, return the index
        
        if (x<0 || x>=dimx || y<0 ||y>=dimy)
            return -1;
        else        
            return (x)*dimy+y/dimx;
    }
    
    class coord
    {   short x;
        short y;
    }
    
    private coord ind2xy(int ix)
    {
        
        coord c=new coord();
        c.x=(short) Math.floor(ix/dimy);
        c.y=(short)(ix%dimy);
        return c;
    }
    
    
    
}
