/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing;

import java.awt.Container;
import java.awt.Window;

import javax.swing.JPanel;

import net.sf.jaer.chip.AEChip;

/**
 *
 * @author Peter
 */
public class MultiInputFrame extends FilterFrame{
    
    ProcessingNetwork procNet;
    Container parentContainer;
    
    Container multiInputControl;
    
    public MultiInputFrame(AEChip chip,ProcessingNetwork pr)
    {   super(chip);
    
        procNet=pr;
        
        rebuildContents();
    }
    
    public MultiInputFrame(AEChip chip,ProcessingNetwork pr,Container parent)
    {   this(chip,pr);
    
        parentContainer=parent;
        
        this.setVisible(true);

        this.getComponents();
        //                            multiInputControl = mif.getContentPane();
        multiInputControl = this.getRootPane();

        this.dispose();

        parent.add(multiInputControl);
    }
    
    public void setHidden(boolean hiddenState)
    {
        if (hiddenState)
            parentContainer.remove(multiInputControl);
        else
            parentContainer.add(multiInputControl);
    }
    
    
    
    /** rebuilds the frame contents using the existing filters in the filterChain */
    @Override
    public void rebuildContents() {
        
        
        
        if (procNet==null) return; // HACKITY HACKITY HACK!
        // This is done just because rebuildContents is called in the super-constructor
        // That's why they give that annoying warning
        
        
        procNet.buildFromFilterChain(filterChain);
        
        clearFiltersPanel();
        int n = 0;
        int w = 100, h = 30;
//        log.info("rebuilding FilterFrame for chip="+chip);
//        if(true){ //(filterChain.size()<=MAX_ROWS){
//            filtersPanel.setLayout(new BoxLayout(filtersPanel,BoxLayout.Y_AXIS));
//            filtersPanel.removeAll();
        for (ProcessingNetwork.Node node : procNet.nodes) {
            MultiInputPanel p = new MultiInputPanel(node);
            addToFiltersPanel(p);
            
            n++;
            h += p.getHeight();
            w = p.getWidth();
        }
        
        
        if (parentContainer!=null)
        {   //this.setPreferredSize(this.getSize());
            ((Window)((JPanel)parentContainer).getTopLevelAncestor()).pack();
//            ((Window)this.getTopLevelAncestor()).pack();
        }
//        if (this.getRoot)
        
//        if (parentContainer!=null)
//            this.parentContainer.revalidate();
//        this.filtersPanel.updateUI();
        
//        this.repaint();
    }    
    
    
}
