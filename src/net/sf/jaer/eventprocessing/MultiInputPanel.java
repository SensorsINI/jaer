/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

/**
 *
 * @author Peter
 */
public class MultiInputPanel extends FilterPanel {
    
//    ArrayList<PacketStream> sources=new ArrayList();
   
    ProcessingNetwork.Node node;
    Container sourcePanel;
    Container controlPanel;
    
    public MultiInputPanel(ProcessingNetwork.Node p) {
        super(p.filt);
        node=p;
        
//        this.setLayout(new GridBagLayout());
        
        
        // Hackity hackity hack hack hack hack
        
        JPanel old=this.jPanel1;
        
        this.removeAll();
        
        this.setLayout(new BorderLayout());
        
        this.add(old,BorderLayout.CENTER);
        
        JCheckBox butdisp = new JCheckBox("Display");
        butdisp.addActionListener(new ActionListener(){
            @Override
            public void actionPerformed(ActionEvent e) {
                
            }        
        });
        
        old.add(butdisp);
        
        
        sourcePanel=new JPanel();
        sourcePanel.setLayout(new GridBagLayout());
        
//        this.add(sourcePanel,BoxLayout.PAGE_AXIS);
        
        this.add(sourcePanel,BorderLayout.EAST);
        
        controlPanel=new JPanel(); 
        controlPanel.setLayout(new BoxLayout(controlPanel,BoxLayout.Y_AXIS));
        this.add(controlPanel,BorderLayout.SOUTH);
        
        addSources();
        
        
        this.enabledCheckBox.addActionListener(new ActionListener(){

            @Override
            public void actionPerformed(ActionEvent e) {
                node.setEnabled(enabledCheckBox.isSelected());
            }
        });
        
        
    }
        
    @Override
    public void setControlsVisible(boolean visible)
    {   super.setControlsVisible(visible);
//        this.revalidate();
        
        
        if (visible)
            for (Container c:controls)
            {
                controlPanel.add(c,Component.LEFT_ALIGNMENT);
            }
        
        this.revalidate();
//        this.getParent().getParent().revalidate();
        
        this.repaint();
        
    }
           
        
    public String[] getSourceNames()
    {
        ArrayList<PacketStream> sources=node.getSourceOptions();
        
        String[] names=new String[sources.size()];
        for (int i=0; i<sources.size();i++)
            names[i]=sources.get(i).getName();
        
        return names;
    }
    
    
    
    /** Add a list of select boxes for choosing your source */
    public void addSources()
    {
        
        GridBagConstraints c=new GridBagConstraints();
        c.gridx=0;
        c.gridy=GridBagConstraints.RELATIVE;
                
                
        String[] inputNames=node.getInputNames();
        for (int i=0; i<node.nInputs(); i++)
        {
            // Add source control boxes
            SourceControl sc=new SourceControl(node.getSourceOptions(),node,inputNames[i],i);
            
            
            // Select different boxes
            sc.control.setSelectedIndex(i);
            
            sourcePanel.add(sc,c);        
        }
               
    }
        
    
    class SourceControl extends JPanel {

        ProcessingNetwork.Node filter;
        boolean initValue = false, nval;
        final JComboBox control;

        public void set(Object o) {
            control.setSelectedItem(o);
        }

        /** Add a source controller that changes the packet source streams upon selection */
        public SourceControl(final ArrayList<PacketStream> sources, final ProcessingNetwork.Node f, final String name, final int sourceIndex) {
            super();
            filter = f;
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setAlignmentX(ALIGNMENT);
            final JLabel label = new JLabel(name);
            label.setAlignmentX(ALIGNMENT);
            label.setFont(label.getFont().deriveFont(fontSize));
//            addTip(f, label);
                        
            add(label);

            String[] names=new String[sources.size()];
            for (int i=0; i<sources.size();i++)
                names[i]=sources.get(i).getName();
                        
            control = new JComboBox(names);
            control.setFont(control.getFont().deriveFont(fontSize));
//            control.setHorizontalAlignment(SwingConstants.LEADING);
            
            control.addActionListener(new ActionListener(){

                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        node.setSource(sourceIndex, sources.get(control.getSelectedIndex()));
                    } catch (Exception ex) {
                        node.setEnabled(false);
                        Logger.getLogger(MultiInputPanel.class.getName()).log(Level.SEVERE, "Source index higher than number of sources", ex);
                    }
                }
            });
            
            
            add(label);
            add(control);
            
            
        }
        
        
        
    }
    
    
    
}
