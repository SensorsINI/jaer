/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.integrateandfire;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.NoSuchElementException;
import java.util.Scanner;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.OutputEventIterator;

/**
 *
 * @author Peter
 */
public class BinNet extends Network {

    BinNeuron[] N;   
    
    
    class BinNeuron implements Unit
    {   float vmem;             // Membrane Potential
        boolean state=false;    // State
        float[] w;              // Outgoing connections weights
        int[] c;                // Outgoing connection addresses
        float thresh;           // Threshold of unit
        String tag;             // String identifying neuron
        
        // ===== Core Methods =====
               
        void setFire(boolean newstate)  // Set a state, then fire if the new state is different than the old
        {   boolean change=state!=newstate;
            state=newstate;
            if (change)
                fire();            
        }
                
        void fire()             // Fire to downstream neurons
        {   // Fire your current state.  
            // NOTE: must bookkeep to ensure that input neurons don't double-fire their statuses
            
            int wmod=state ? 1 : -1;
            
            for (int i=0; i<c.length; i++)
                if (N[c[i]].hitme(w[i]*wmod))
                    N[c[i]].fire();
        }
        
        /* Input a current wi. */
        boolean hitme(float wi)       
        {   
            boolean oldState=state;
            vmem+=wi;
            state=vmem>thresh;
            boolean statechange=(oldState!=state);
            if (statechange)
                fire();
            return statechange;
            
        }
        
        void reset()
        {   vmem=0;
            state=false;
        }

        // ===== Implementation of Unit =====
        @Override
        public float getVsig(int timestamp) { return vmem; }

        @Override
        public float getAsig() { return state ? 1:0;}

        @Override
        public String getName() { return tag; }

        @Override
        public String getInfo() {
            return "thresh: "+thresh;
        }

    }
    
    @Override
    public int[] getConnections(int index) {
        return N[index].c;
    }

    @Override
    public float[] getWeights(int index) {
        return N[index].w;
    }
    
    public void setFire(int index, boolean newstate)
    {   N[index].setFire(newstate);        
    }

    public void hitThat(int index, float w)
    {   N[index].hitme(w);        
    }
        
    public void printStates()
    {   // Assuming inputs are indexed y-first (as in matlab), print out the 
        // State of the network.
        System.out.println("Inputs:");
        for (short i=0; i<R.outDimY; i++)
        {   for (short j=0; j<R.outDimX; j++)
            {   System.out.print(N[R.ixy2ind(j,i)].state?"##":"``");
            }
            System.out.println(" ");
        }
        System.out.println(" ");
        System.out.println("Internal:");
        for (short i=(short)(R.outDimY*R.outDimX); i<N.length; i++)
            System.out.println(i+"("+N[i].tag+"):\t"+N[i].state);
    }
    
    public void setInputThresh(float thresh)
    {
        for (int i=0; i<R.outDimX*R.outDimY; i++)
            N[i].thresh=thresh;
        
    }
    
    // Read a file
    @Override
    public void readfile(File file) throws FileNotFoundException, Exception {
        // Locate a Network-Description file and read it into a network.


        int i, j;
        int netLen=-1; 
        int rowLen;

        /*
        File file;
        JFrame frame = new JFrame("FileChooserDemo");


        // Locate file
        JFileChooser fc = new JFileChooser();

        fc.showOpenDialog(frame);
        file=fc.getSelectedFile();
        //file=new File("C:\\Documents and Settings\\tobi\\My Documents\\Simple Net.txt");
         */

        // Parse Out First Info
        Scanner sc = new Scanner(file);

        // Loop Through Items in header
        String lab;
        while (true){
            sc.nextLine();
            try {
                lab = sc.next();
            } catch (NoSuchElementException E) {
                break;
            }
            
            if (lab.equals("inputDims:"))
            {   this.R.outDimX=sc.nextShort();
                this.R.outDimY=sc.nextShort();
            }
            else if (lab.equals("nUnits:"))
                netLen=sc.nextInt();
            else if (lab.equals("end"))    // End of header is denoted by spacer line.
                break;
            
        }
        
        if (netLen==-1)
            throw new Exception("Parameter 'nUnits' was not defined in the header");
        
        
//        sc.nextLine();              // Name Line
//        sc.nextLine();              // Notes Line
//        sc.next();                  // #Units Line
//        netLen = sc.nextInt();        // Read Network Length
//        sc.nextLine();              // Input dimensions Line
//        int dimx=sc.nextInt();
//        int dimy=sc.nextInt();
        
//        sc.nextLine();

        // Initialize arrays
//            w = new float[netLen][];
//            c = new int[netLen][];
//            N = new ENeuron[netLen];

        System.out.println("Reading Network...");

        sc.next("Unit");

        N=new BinNeuron[netLen];
        U=N;    // See super
        
        // Loop Though Neurons
        for (i = 0; i < netLen; i++) {     

            // Add new unit
            //sc.nextLine();
            //sc.next();  // Unit
            if (i != sc.nextInt()) {
                //out.println("ERROR: UNIT # DOES NOT MATCH INDEX");
            }
            sc.nextLine();          // Jump to connection-count line
            sc.next();              // Jump to number of connections
            rowLen = sc.nextInt();    // Grab the length of the row

            N[i] = new BinNeuron();      // Initialize Neuron
            
            
            while (true) {
                sc.nextLine();
                try {
                    lab = sc.next();
                } catch (NoSuchElementException E) {
                    break;
                }

                if (lab.equals("W:")) {
                    N[i].w = new float[rowLen]; // Start weight array
                    for (j = 0; j < rowLen; j++) { // Loop though forward connections
                        N[i].w[j] = sc.nextFloat();
                    }
                } else if (lab.equals("C:")) {   // Fill me with Code!    
                    N[i].c = new int[rowLen]; // Start weight array
                    for (j = 0; j < rowLen; j++) { // Loop though forward connections
                        N[i].c[j] = sc.nextInt();
                    }
                } else if (lab.equals("tag:")) {
                    N[i].tag = sc.next();
                } else if (lab.equals("T:")) {   // Fill me with Code!
                    N[i].thresh=sc.nextFloat();
                } else if (lab.equals("Unit")) {
                    break;
                } else {
                    throw new Exception("Unknown Neuron parameter :'" + lab + "'");
                }

            }

        }
        System.out.println("Done");

    }
    
    @Override
    public void reset() {
        for (BinNeuron n:N)
            n.reset();
    }
    
    
}
