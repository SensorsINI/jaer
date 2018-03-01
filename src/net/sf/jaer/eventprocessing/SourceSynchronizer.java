/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import net.sf.jaer.graphics.AEViewer;

/**
 *
 * @author Peter
 */
public class SourceSynchronizer {
    
    ArrayList<AEViewer.Ambassador> viewers;
    Container panel;
    JLabel[] timeStampLabels;
    JLabel[] labelsOfDespair;
        
    public SourceSynchronizer(ArrayList<AEViewer.Ambassador> viewerList,JPanel pan)
    {
        viewers=viewerList;
        
        if (pan!=null)
            panel=pan;
        else
            panel=new JPanel();
        
        
        
        initComponents();
        
    }
    
    public SourceSynchronizer(ArrayList<AEViewer.Ambassador> viewerlist)
    {
        this(viewerlist,null);
        
        JFrame fr=new JFrame();
        fr.setContentPane(panel);
        fr.setTitle("Synchronization");
        fr.setPreferredSize(new Dimension(500,300));
        fr.pack();
        fr.setVisible(true);
        
    }
    
    public void initComponents()
    {
        panel.setLayout(new GridBagLayout());
        GridBagConstraints c=new GridBagConstraints();
        
        c.gridx=GridBagConstraints.RELATIVE;
        c.gridy=0;
        c.weightx=1;
        c.insets=new Insets(10,10,10,10);
        
//        panel.add(vpanes,BorderLayout.CENTER);
        
        
//        vpanes.setLayout(new FlowLayout());
        
        timeStampLabels=new JLabel[viewers.size()];
        for (int i=0; i<viewers.size(); i++)
        {
            panel.add(buildSingleRecorder(i),c);
        }
            
        c.gridx=0;
        c.gridy=1;
        c.gridwidth=viewers.size();
                
        JPanel jointPane=new JPanel();
        jointPane.setLayout(new GridBagLayout());
        panel.add(jointPane,c);
        
        c.gridx=0;
        c.gridy=GridBagConstraints.RELATIVE;
        c.weightx=0;
        c.anchor=GridBagConstraints.EAST;
        
        c.insets=new Insets(0,0,0,0);
        
        jointPane.add(new JLabel("Disparity"),c);
        
        
        labelsOfDespair=new JLabel[(viewers.size()*(viewers.size()+1)/2)];
        for (int i=0; i<viewers.size(); i++)
            for (int j=0; j<i; j++)
            {   JLabel jt=new JLabel();
                jointPane.add(jt,c);
                labelsOfDespair[j+i*j]=jt;
            }
                
        
        JButton but=new JButton("Reset All Timestamps");
        but.addActionListener(new ActionListener(){
            @Override
            public void actionPerformed(ActionEvent e) {
                for (AEViewer.Ambassador v:viewers)
                    v.resetTimeStamps();
            }
        });
        jointPane.add(but,c);
        
        
    }
    
    public JPanel buildSingleRecorder(final int viewerIX)
    {
        JPanel jp=new JPanel();
        jp.setLayout(new GridBagLayout());
        
        GridBagConstraints c=new GridBagConstraints();
        c.gridx=0;
        c.gridy=GridBagConstraints.RELATIVE;
        
        final AEViewer.Ambassador ava=viewers.get(viewerIX);
        
        // Source Label
        JLabel jt=new JLabel(ava.getName());
        jp.add(jt,c);
        
        // Timestamp label
        jt=new JLabel();
        timeStampLabels[viewerIX]=jt;
        jp.add(jt,c);
        
        // Reset Button
        JButton jb=new JButton("Reset");
        jb.addActionListener(new ActionListener(){
            @Override
            public void actionPerformed(ActionEvent e) {
                ava.resetTimeStamps();
            }
        });
        jp.add(jb,c);
        
        
                
        return jp;
    }

    public boolean update() {
        
        if (!panel.isShowing())
            return false;
        
        int[] timestamps=new int[viewers.size()];
        
        for (int i=0; i<viewers.size(); i++)
        {   timestamps[i]= viewers.get(i).getPacket().getFirstTimestamp();
            timeStampLabels[i].setText(timestamps[i]+"us");
//            timeStampLabels[i].getParent().revalidate();
        }
        
        for (int i=0; i<viewers.size(); i++)
            for (int j=0; j<i; j++)
            {   
                labelsOfDespair[j+i*j].setText(timestamps[j]-timestamps[i]+"us");
            }
        
//        panel.revalidate();
//        panel.repaint();
        
        return true;
    }
    
    
}
