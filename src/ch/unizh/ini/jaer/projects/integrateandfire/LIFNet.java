/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.integrateandfire;

// Java  Stuff
import java.io.File;
import java.io.FileNotFoundException;
import java.util.NoSuchElementException;
import java.util.Scanner;

import net.sf.jaer.event.OutputEventIterator;

/**
 * TODO
 * @author Peter O'Connor
 */
public class LIFNet extends Network implements LIFcontroller {

    /* Network
     * Every Event-Source/Neuron has an integer "address".  When the event source
     * generates an event, it is propagated through the network.
     *
     * */
    //------------------------------------------------------
    // Properties-Network Related
    
    
    public int[][] c;          // Arrey of connection c[i][j] is the addresss of neuron i's j'th connection.
    public float[][] w;        // Array of weigths in network.  w[i][j] is the connection strength of connection c[i][j]
    ENeuron[] N;                 // Array of neurons.  N[i] is the ith neuron.
    public int maxdepth = 100;    // Maximum depth of propagation - prevents infinite loops in unstable recurrent nets.
    public byte id;

    short indimX=128;
    short indimY=128;
    
    
    
    
    
    
    /*  
    public void feedfromstandard(short x, short y, int timestamp, OutputEventIterator outItr)
    {   feedfromloc(x/128f,y/128f,timestamp,outItr);
    }
          
    public void feedfromloc(float relx, float rely, int timestamp, OutputEventIterator outItr)
    {   propagate(indimY*E.xp)
        Net.propagate(index,dim*E.xp+dim-1-E.yp,1,E.timestamp); 
    }*/
    
    // Propagate 
    public void propagate(int source, int depth, int timestamp, OutputEventIterator outItr) {
        // Propagate an event through the network.
        // TRICK: if depth=-1, "source" refers not the the source but the destination.

        boolean fire;
        if (depth > maxdepth) {
            System.out.println("This spike has triggered too many (>" + maxdepth + ") propagations.  See maxdepth");
            return;
        }
        int i;
        if (c == null) {
            return; // Handle case when we didn't create output connections
        }
        for (i = 0; i < w[source].length; i++) { // Iterate through connections
            fire = N[c[source][i]].spike(w[source][i], timestamp, outItr);
            if (fire) {
                propagate(c[source][i], depth + 1, timestamp, outItr);
            }
        }
    }

    @Override
    public String networkStatus(){
        return "Network with "+N.length+" Neurons";
    }
    
    public void stimulate(int dest, float weight, int timestamp, OutputEventIterator outItr) {   // Directly stimulate a neuron with a given weight

        boolean fire = N[dest].spike(weight, timestamp, outItr);
        if (fire) {
            propagate(dest, 1, timestamp, outItr);
        }
    }

    @Override
    public void setThresholds(float thresh) {
        for (Neuron n : N) {
            n.thresh = thresh;
        }
    }

    @Override
    public void setTaus(float tc) {
        for (Neuron n : N) {
            n.tau = tc;
        }
    }

    @Override
    public void setSats(float tc) {
        for (Neuron n : N) {
            n.sat = tc;
        }
    }

    @Override
    public void setDoubleThresh(boolean v) {
        for (Neuron n : N) {
            n.doublethresh = v;
        }
    }

    @Override
    public void reset() {
        for (Neuron n : N) {
            n.reset();
        }
    }

    // Propagate an event through the network
    public void propagate(int source, int depth, int timestamp) {
        propagate(source, depth, timestamp, null);
        /*boolean fire;
        if (depth>maxdepth){
        System.out.println("This spike has triggered too many (>"+maxdepth+") propagations.  See maxdepth");
        return;
        }
        int i;
        for (i=0;i<w[source].length;i++){ // Iterate through connections
        fire=N[c[source][i]].spike(w[source][i],timestamp);
        if (fire){
        propagate(c[source][i],depth+1,timestamp);
        }
        }*/

    }

    @Override
    public int[] getConnections(int index) {
        return c[index];
    }

    @Override
    public float[] getWeights(int index) {
        return w[index];
    }
    

//    public File readfile()  throws FileNotFoundException, Exception{
//        File file=getfile();
//        readfile(file);        
//        return file;
//    }
    // Read a network file into a Network Object
    public void readfile(File file) throws FileNotFoundException, Exception {
        // Locate a Network-Description file and read it into a network.


        int i, j;
        int netLen, rowLen;

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

        sc.nextLine();              // Name Line
        sc.nextLine();              // Notes Line
        sc.next();                  // #Units Line
        netLen = sc.nextInt();        // Read Network Length


        sc.nextLine();

        // Initialize arrays
        w = new float[netLen][];
        c = new int[netLen][];
        N = new ENeuron[netLen];

        System.out.println("Reading Network...");

        sc.next("Unit");

        /*
        JFrame f = new JFrame("JProgressBar Sample");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        Container content = f.getContentPane();
        JProgressBar progressBar = new JProgressBar();
        progressBar.setValue(25);
        progressBar.setStringPainted(true);
        Border border = BorderFactory.createTitledBorder("Reading...");
        progressBar.setBorder(border);
        content.add(progressBar, BorderLayout.NORTH);
        f.setSize(300, 100);
        f.setVisible(true);
         */

        for (i = 0; i < netLen; i++) {     //Loop Though Neurons

            // Add new unit
            //sc.nextLine();
            //sc.next();  // Unit
            if (i != sc.nextInt()) {
                //out.println("ERROR: UNIT # DOES NOT MATCH INDEX");
            }
            sc.nextLine();          // Jump to connection-count line
            sc.next();              // Jump to number of connections
            rowLen = sc.nextInt();    // Grab the length of the row

            String lab;


            N[i] = new ENeuron();      // Initialize Neuron
            U=N;
            
            while (true) {
                sc.nextLine();


                try {
                    lab = sc.next();
                } catch (NoSuchElementException E) {
                    break;
                }


                if (lab.equals("W:")) {
                    w[i] = new float[rowLen]; // Start weight array
                    for (j = 0; j < rowLen; j++) { // Loop though forward connections
                        w[i][j] = sc.nextFloat();
                    }
                } else if (lab.equals("C:")) {   // Fill me with Code!    
                    c[i] = new int[rowLen]; // Start weight array
                    for (j = 0; j < rowLen; j++) { // Loop though forward connections
                        c[i][j] = sc.nextInt();
                    }
                } else if (lab.equals("N:")) {
                    N[i].name = sc.next();
                } else if (lab.equals("B:")) {   // Fill me with Code!
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
    public Network copy() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
