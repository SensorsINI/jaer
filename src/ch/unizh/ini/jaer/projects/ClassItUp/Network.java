/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.ClassItUp;


// Java  Stuff
import java.io.File;
import java.io.FileNotFoundException;


import java.util.Scanner;
import java.util.NoSuchElementException;
import javax.swing.*;
import javax.swing.border.Border;

import java.awt.BorderLayout;
import java.awt.Container;


/**
 *
 * @author tobi
 */
public class Network {

    /* Network
     * Every Event-Source/Neuron has an integer "address".  When the event source
     * generates an event, it is propagated through the network.
     *
     * */

    //------------------------------------------------------
    // Properties-Network Related
    public int[][] c;          // Arrey of connection c[i][j] is the addresss of neuron i's j'th connection.
    public float[][] w;        // Array of weigths in network.  w[i][j] is the connection strength of connection c[i][j]
    Neuron[] N;                 // Array of neurons.  N[i] is the ith neuron.
    public int maxdepth=100;    // Maximum depth of propagation - prevents infinite loops in unstable recurrent nets.

    // Propagate an event through the network
    public void propagate(int source,int depth) {

        boolean fire;
        if (depth>maxdepth){
            System.out.println("This spike has triggered too many propagations.  See maxdepth");
            return;
        }
        int i;
        for (i=0;i<w[source].length;i++){ // Iterate through connections
            fire=N[c[source][i]].spike(w[source][i]);
            if (fire){
                propagate(c[source][i],depth+1);
            }
        }

    }

    // Read a network file into a Network Object
    public void readfile()  throws FileNotFoundException, Exception  {
        // Locate a Network-Description file and read it into a network.


        int i,j;
        int netLen, rowLen;


        File file;
        JFrame frame = new JFrame("FileChooserDemo");


        // Locate file
        JFileChooser fc = new JFileChooser();
        
        fc.showOpenDialog(frame);
        file=fc.getSelectedFile();
        //file=new File("C:\\Documents and Settings\\tobi\\My Documents\\Simple Net.txt");


        // Parse Out First Info
        Scanner sc = new Scanner(file);

        sc.nextLine();              // Name Line
        sc.nextLine();              // Notes Line
        sc.next();                  // #Units Line
        netLen=sc.nextInt();        // Read Network Length


        sc.nextLine();

        // Initialize arrays
        w=new float[netLen][];
        c=new int[netLen][];
        N=new Neuron[netLen];

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

        for (i=0;i<netLen;i++){     //Loop Though Neurons

            // Add new unit
            //sc.nextLine();
            //sc.next();  // Unit
            if (i!=sc.nextInt()){
                //out.println("ERROR: UNIT # DOES NOT MATCH INDEX");
            }
            sc.nextLine();          // Jump to connection-count line
            sc.next();              // Jump to number of connections
            rowLen=sc.nextInt();    // Grab the length of the row

            String lab;

            
            N[i]=new Neuron();      // Initialize Neuron

            while (true)
            {
                sc.nextLine();
                                

                try
                {    lab=sc.next();}
                catch (NoSuchElementException E)
                {    break;}

                       
                if (lab.equals("W:"))
                {   w[i]=new float[rowLen]; // Start weight array
                    for (j=0;j<rowLen;j++){ // Loop though forward connections
                        w[i][j]=sc.nextFloat();
                    }
                }
                else if (lab.equals("C:"))
                {   // Fill me with Code!    
                    c[i]=new int[rowLen]; // Start weight array
                    for (j=0;j<rowLen;j++){ // Loop though forward connections
                        c[i][j]=sc.nextInt();
                    }
                }
                else if (lab.equals("N:"))
                {   N[i].name=sc.next();
                }
                else if (lab.equals("B:"))
                {   // Fill me with Code!

                }
                else if (lab.equals("Unit"))
                {   break;
                }
                else
                {   throw new Exception("Unknown Neuron parameter :'"+lab+"'");
                }

                
            }
/*
            progressBar.setValue(i);
            progressBar.updateUI();
            f.update(null);*/
                    
                  
            /*
            // Add forward connection weights
            sc.nextLine();          // Jump to start of Weight line
            String lab=sc.next();              // Jump over label
            w[i]=new float[rowLen]; // Start weight array
            for (j=0;j<rowLen;j++){ // Loop though forward connections
                w[i][j]=sc.nextFloat();
            }

            // Add neuron bias
            sc.nextLine();          // Jump to bias line
            sc.next();              // Jump to bias value
            N[i]=new Neuron();      // Initialize Neuron
            //bias=sc.nextInt;      // Note: include bias

            // Add forward connection locations
            sc.nextLine();          // Jump to connections line
            sc.next();              // Jump to first connection
            c[i]=new int[rowLen]; // Start weight array
            for (j=0;j<rowLen;j++){ // Loop though forward connections
                c[i][j]=sc.nextInt();
            }

            sc.nextLine();          // Jump to next Neuron
*/
        }
        System.out.println("Done");
     }

    
    
}
