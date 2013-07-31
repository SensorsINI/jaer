/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 *
 * This class records network spikes and can print them to file.
 *
 * @author Peter
 */
public class SpikeRecorder<SpikeType extends Spike> {

    private boolean recordingState;
    Network net;

    Queue<Spike> spikes;

    SpikeRecorder(Network network)
    {
        net=network;
    }

    public int nSpikes()
    {
        return ((LinkedBlockingQueue) spikes).size();
    }

    public void setRecodingState(boolean state)
    {
        boolean oldState=recordingState;

        if (!oldState && state) // Start Recording
        {
            spikes=net.outputQueue.addReader();
        }
        else if (oldState && !state) // End recording.
        {
            net.outputQueue.removerReader(spikes);
        }
    }

    public void clear()
    {
        if (spikes==null) {
			System.out.println("No queue to clear");
		}
		else {
			spikes.clear();
		}
    }

    public boolean getRecordingState()
    {
        return recordingState;
    }

    public void printToFile()
    {

//        FileOutputStream fos = null;
        JFileChooser fc=new JFileChooser();
        fc.setDialogTitle("Save Spike data file");
        fc.showOpenDialog(null);
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Spike Data Files", "spdat");
//        fc.setFileFilter(filter);
        fc.addChoosableFileFilter(filter);

        File file = fc.getSelectedFile();

        if (file==null) {
			return;
		}

        FileOutputStream fos=null;
        try {
            fos = new FileOutputStream(file);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(SpikeRecorder.class.getName()).log(Level.SEVERE, "File not fount", ex);
            return;
        }

        ByteBuffer b = ByteBuffer.allocate(4*3*spikes.size());
        Iterator<Spike> iter=spikes.iterator();
        while(iter.hasNext())
        {
            Spike sp=iter.next();

            b.putInt(sp.time);

            b.putInt(sp.addr);


            b.putInt(sp.layer);



        }

        try {
            fos.write(b.array());
        } catch (IOException ex) {
            Logger.getLogger(SpikeRecorder.class.getName()).log(Level.SEVERE, null, ex);
        }


    }


    // Return a spike array
    public ArrayList<Spike> toArray()
    {
//        Spike[] sp=new Spike[nSpikes()];

//        Iterator itr=spikes.iterator();

        ArrayList<Spike> spikeList=new ArrayList();

        for (Spike sp:spikes) {
			spikeList.add(sp);
		}

        return spikeList;
    }


//    public void printToFile()
//    {
//        if (spikes==null)
//        {   JOptionPane jp=new JOptionPane("No recording has been run yet.  Nothing to print.",JOptionPane.WARNING_MESSAGE);
//            return;
//        }
//
//        synchronized(spikes)
//        {
//
//            FileOutputStream fos = null;
//            ObjectOutputStream out = null;
//            try {
//
//                JFileChooser fc=new JFileChooser();
//                fc.setDialogTitle("Save Spike data file");
//                fc.showOpenDialog(null);
//                File file = fc.getSelectedFile();
//                fos = new FileOutputStream(file);
//                out = new ObjectOutputStream(fos);
//                out.writeObject(spikes);
//                out.close();
//
//            } catch (IOException ex) {
//                Logger.getLogger(SpikeRecorder.class.getName()).log(Level.SEVERE, null, ex);
//            } finally {
//                try {
//                    fos.close();
//                } catch (IOException ex) {
//                    Logger.getLogger(SpikeRecorder.class.getName()).log(Level.SEVERE, null, ex);
//                }
//                try {
//                    out.close();
//                } catch (IOException ex) {
//                    Logger.getLogger(SpikeRecorder.class.getName()).log(Level.SEVERE, null, ex);
//                }
//            }
//
//        }
//    }

}
