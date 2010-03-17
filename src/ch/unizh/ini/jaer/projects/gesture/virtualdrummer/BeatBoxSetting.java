/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.virtualdrummer;

import java.awt.*;
import javax.swing.*;
import javax.sound.midi.*;
import java.util.*;
import java.awt.event.*;
import java.io.*;
import java.beans.*;
/**
 *
 * @author Administrator
 */
public class BeatBoxSetting extends javax.swing.JFrame{
    //    JFrame bbsFrame;
    int selectedIns = 35;

    // Names of instruments
    String[] instrumentNames = {"Bass Drum","Closed Hi-Hat","Open Hi-Hat",
                                "Acoustic Snare","Crash Cymbal","Hand Clap",
                                "High Tom","Hi Bongo","Maracas","Whistle",
                                "Low Conga","Cowbell","Vibraslap","Low-mid Tom",
                                "High Agogo","Open Hi Conga"};

    // Instruments
    int[] instruments={35,42,46,38,49,39,50,60,70,72,64,56,58,47,67,63};

    // Hashmap for instruments search
    HashMap<String, Integer> imap = new HashMap();

    public BeatBoxSetting() {
        setTitle("VirtualDrummer.BeatBoxSetting");

        ButtonGroup bg = new ButtonGroup();

        setLayout(new GridLayout(16,1));
        for(int i=0;i<16;++i){
            JRadioButton rb = new JRadioButton(instrumentNames[i]);

            // initial instrument
            if(i == 0)
                rb.setSelected(true);

            rb.addItemListener(new BbsEventHandler());
            bg.add(rb);
            add(rb);

            imap.put(instrumentNames[i], new Integer(instruments[i]));
        }

        setBounds(50,50,300,300);
        pack();
   }

    public void showUp(){
        setVisible(true);
    }

    public void close(){
        setVisible(false);
    }

    public int getSelectedIns() {
        return selectedIns;
    }

    class BbsEventHandler implements ItemListener{
        public void itemStateChanged(ItemEvent e){
            if(e.getStateChange() == ItemEvent.SELECTED)
            {
                JRadioButton rb = (JRadioButton) e.getSource();
                String iname = rb.getText();
                selectedIns = imap.get(iname);
//                System.out.println("Selected item = " + iname + selectedIns);
            }
        }
    }
}
