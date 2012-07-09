/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.chip.projects.sensoryfusion;

import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.EnumMap;
import javax.swing.JButton;
import javax.swing.JPanel;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.MultiSourceProcessor;
import net.sf.jaer.graphics.Chip2DRenderer;
import net.sf.jaer.graphics.DisplayWriter;
import net.sf.jaer.graphics.ImageDisplayWriter;

/**
 *
 * @author Peter
 */
public class SensoryFusionExample extends MultiSourceProcessor {

   // Chip2DRenderer mat=new Chip2DRenderer();
    final ShowMat mat=new ShowMat();
    
    ImageDisplayWriter disp;
    
    class ShowMat implements DisplayWriter{

        JPanel p;
        

        @Override
        public void setPanel(JPanel imagePanel) {
            p=imagePanel;
        }

        @Override
        public JPanel getPanel() {
            return p;
        }

        @Override
        public void setDisplayEnabled(boolean state) {
            throw new UnsupportedOperationException("Not supported yet.");
        }
        
    }
    
    public enum xxx {AAA,BBB};
    
    
    public SensoryFusionExample(AEChip chip)
    {   super(chip,2);    
    
    
    
    }
    
    
//    public Class<Enum> getOpts(){
//        return xxx.valueOf(xxx.class,"AAA").getDeclaringClass();
////        return xxx.class.getDeclaringClass();
//    }
    
    @Override
    public EventPacket filterPacket(EventPacket<?> in) {
        
        int nA=0,nB=0;
        
        
        for (Object e:in)
        {   
            if(((BasicEvent)e).source==0)
                nA++;
            else 
                nB++;
            
        }
        
//        System.out.println("Recieved "+nA+" events from input A and "+nB+" from input B.");
        System.out.println("nA:"+nA+"\t nB:"+nB); 
        
        if (disp!=null)
            disp.repaint();
        
        return in;
        
    }

    @Override
    public void resetFilter() {
        
    }

    @Override
    public void initFilter() {
        makeImage();
    }
        
    @Override
    public String[] getInputNames() {
        String[] s = {"inputA","inputB"};
        return s;
    }
    
    
    public void makeImage()
    {
        disp=ImageDisplayWriter.createOpenGLCanvas();
        
        disp.setImageSize(20,20);
        disp.setSize(300,300);
//        disp.setPreferredSize(new Dimension(200,200));
        
//        disp.setDisplayEnabled(true);
        
        disp.setPixmapGray(10,10,.6f);
        
//        disp.setVisible(true);
        disp.setTitleLabel("AAA");
                
        
        
    }
    
    public void doStartImage()
    {
        makeImage();
        this.addDisplayWriter(disp);
//        this.addDisplayWriter(new TestButton());
        
        
    }

    
    public class TestButton extends JButton implements DisplayWriter
    {
        
        public TestButton()
        {
            super("TEST");
        }

        @Override
        public void setPanel(JPanel imagePanel) {
            imagePanel.add(this);
        }

        @Override
        public Component getPanel() {
            return this.getParent();
        }

        @Override
        public void setDisplayEnabled(boolean state) {
            
        }
        
    }

}
