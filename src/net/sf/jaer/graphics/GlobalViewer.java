/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * MultiViewer.java
 *
 * Created on Apr 2, 2012, 2:16:00 PM
 */
package net.sf.jaer.graphics;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.concurrent.CyclicBarrier;

import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.InternalFrameListener;

import net.sf.jaer.JAERViewer;
import net.sf.jaer.eventprocessing.MultiInputFrame;
import net.sf.jaer.eventprocessing.PacketStream;
import net.sf.jaer.eventprocessing.ProcessingNetwork;
import net.sf.jaer.eventprocessing.SourceSynchronizer;

/**
 *
 * @author Peter
 */
public class GlobalViewer extends javax.swing.JFrame {

        // <editor-fold defaultstate="collapsed" desc=" Properties " >
    
        
        private final ViewLoop viewLoop=new ViewLoop();
        JAERViewer jaerView;
        public boolean enabled=true;
        
        public ProcessingNetwork procNet=new ProcessingNetwork();
                
        public final ArrayList<AEViewer.Ambassador> aeviewers=new ArrayList();
        public final ArrayList<DisplayWriter> inputDisplays=new ArrayList();
        public final ArrayList<DisplayWriter> internalDisplays=new ArrayList();
        
        ArrayList<PacketStream> packetStreams=new ArrayList();
        int[] packetStreamIndeces;
        CyclicBarrier waitFlag;
        
        boolean synchDisp=false;
        SourceSynchronizer srcSync;
        
        boolean makeitsynch=true;
        
        // </editor-fold>
        
        // <editor-fold defaultstate="collapsed" desc=" Builder/Startup Methods " >
         
        // Methods -----------------------------------------
        public void addAllInputs(ArrayList<AEViewer> viewers){
            packetStreams.clear();            
            for (int i=0; i<viewers.size(); i++) 
            {   
                AEViewer.Ambassador v=viewers.get(i).getAmbassador();
                
                v.setID(i);
                aeviewers.add(v);
                addPacketStream(v);
            }
        }
        public void displayAllInputs(){
            
            // Add all the viewers
            for (int i=0; i<aeviewers.size(); i++) 
            {   
                AEViewer.Ambassador v=aeviewers.get(i);

                JPanel jp=new JPanel();
                jp.setLayout(new BorderLayout());
                jp.add(v.getPanel(),BorderLayout.CENTER);
                JMenuBar m=v.getToolBar();
                
                // Disable spawning new windows inside here
                m.getMenu(0).getItem(0).setEnabled(false);
                jp.add(m,BorderLayout.NORTH);

                addDisplayWriter(jp,new Dimension(400,400));
                refreshPlayingLocks();   
                v.setWatched(true);
            }
            
            resynchronize();
        }
        
        private int countPlayingViewers(){
            int active_players = 0;
            for (AEViewer.Ambassador v:aeviewers){
                if(v.getMode() == AEViewer.PlayMode.PLAYBACK || 
                   v.getMode() == AEViewer.PlayMode.LIVE ) {
                    active_players++;
                }
            }
            return active_players;
        }
        
        public void refreshPlayingLocks(){
            int playing = countPlayingViewers();
            playing = (playing > 0) ? playing : aeviewers.size();
            waitFlag = new CyclicBarrier(playing, new ViewLoop());                
            for (AEViewer.Ambassador v:aeviewers)
                v.setSemaphore(waitFlag);
        }
                
        
        public void resynchronize()
        {
            // In (Hopefully) rapid succession, zero the time-stamps
            for (AEViewer.Ambassador v:aeviewers)
                v.resetTimeStamps();
        }
        
        public ArrayList<PacketStream> getInputStreams()
        {
            ArrayList  arr=aeviewers;
                        
            return arr;
        }
        
                
        public void start(){            
            enabled=true;
            
            addAllInputs(jaerView.getViewers());                     
            initComponents();            
            displayAllInputs();
            
        }
                
        /** Add a new packet source 
         * @TODO: semaphore concerns
         */
        public void addPacketStream(PacketStream v){
            packetStreams.add(v);            
        }
        
        // </editor-fold>
        
        // <editor-fold defaultstate="collapsed" desc=" Access Methods " >
                
        public void setJaerViewer(JAERViewer v){
            this.jaerView=v;
        }
        
        
        public ArrayList<DisplayWriter> getAllDisplays()
        {
            ArrayList<DisplayWriter> displist=new ArrayList(inputDisplays);
            displist.addAll(internalDisplays);
            return displist;
        }
        
                            
//        public void setPaused(boolean desiredState)
//        {
//            viewLoop.paused=desiredState;
//            
//            if (desiredState)
//                // Wait for current thread to updause
//                synchronized(Thread.currentThread()){
//                {try {
//                    Thread.currentThread().wait();
//                } catch (InterruptedException ex) {
//                    Logger.getLogger(GlobalViewer.class.getName()).log(Level.SEVERE, null, ex);
//                }
//            }}
//        }
        
        // </editor-fold>
                
        // <editor-fold defaultstate="collapsed" desc=" ViewLoop Thread " >
        
        class ViewLoop implements Runnable{
                        
            public volatile boolean paused;
            
            @Override
            public void run() {
                // Update loop of the Merger object
                // 1) Wait for all other threads to finish their "run" commands
                // 2) Filter Packet & display
                // 3) Release other threads
                
                // Synchronization display: 
                if (synchDisp) //Update if window is open, otherwise turn off.
                {   synchDisp=srcSync.update();
                }
                
                if (makeitsynch)
                {
                    resynchronize();
                    makeitsynch=false;
                }
                

                // 1) Process Packets
                procNet.crunch();
                
            }
        }
        
        
        // </editor-fold>
        
        // <editor-fold defaultstate="collapsed" desc=" GUI Methods " >
        
        JToolBar bottomBar;
        JDesktopPane viewPanel;
        JPanel filterPanel;
        MultiInputFrame multiInputControl;
//        ArrayList<JPanel> viewPanels=new ArrayList();
        
        /** Add Custom Controls Panel to your filter! */
//        public void addControlsToFilter(JPanel controls,EventFilter2D filt)
//        {
//            procNet.addControlsToFilter(controls,filt);
//            
//        }
//        JScrollPane jsc;
        
        void initComponents()
        {
            this.setTitle("Global Viewer");
            
            this.setBackground(Color.DARK_GRAY);
            this.setLayout(new BorderLayout());
   
            filterPanel=new JPanel();
            filterPanel.setLayout(new FlowLayout());
            this.add(new JScrollPane(filterPanel,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_NEVER),BorderLayout.WEST);
            bottomBar=new JToolBar();
            this.add(bottomBar,BorderLayout.SOUTH);
            
            JButton button;
            
            button=new JButton("Old View");
            button.addActionListener(new ActionListener(){

                @Override
                public void actionPerformed(ActionEvent e) {
                    
                    for (AEViewer.Ambassador v:aeviewers)
                    {   
//                        
//                        ((AEViewer) v).getContentPane().add(v.getPanel());
////                        v.getPanel().removeAll();
//                        
//                        
//                        GlobalViewer.this.enabled=false;
////                        GlobalViewer.this.dispose();                        
////                        v.getContentPane().add
//                        ((AEViewer) v).setVisible(true);
//                        ((AEViewer) v).globalized=false;
                    }
                    
//                    GlobalViewer.this.inputDisplays.clear();
                    
                }

            
            });
            bottomBar.add(button);            
            
            final JToggleButton but=new JToggleButton("Hide Filters");
            but.addActionListener(new ActionListener(){
                @Override
                public void actionPerformed(ActionEvent e) {
                    
                    if (!but.isSelected()){
                        launchShowFiltersPanel();      
                        but.setText("Hide Filters");                                            
                    }
                    else {   
                        multiInputControl.setHidden(true);
                        multiInputControl.setVisible(false);
                        but.setText("Show Filters");
                    }
                    GlobalViewer.this.pack();    
                }
            });
            bottomBar.add(but);
            
            button=new JButton("Synchronization");
            button.addActionListener(new ActionListener(){
                @Override
                public void actionPerformed(ActionEvent e) {
                    ArrayList arr=getInputStreams();
                    srcSync=new SourceSynchronizer(arr);
                    synchDisp=true;
                }
            });
            bottomBar.add(button);
            
            
            button=new JButton("?");
            button.addActionListener(new ActionListener(){
                @Override
                public void actionPerformed(ActionEvent e) {
                    
                    String mid="d.ocon";
                    JOptionPane.showMessageDialog(new JFrame(),
                    "This viewer allows you to view input from multiple source, "+
                    "and dispatch them to filters with variable numbers of input "+
                    "sources. (see filter SensoryFusionExample)\n\n"
                            + "TODO:\n"
                            + "Allow Processed Packets to render\n"
                            + "Implement max-wait time for each input source\n"
                            + "Allow better control of opening/closing displays\n"
                            + "Allow access to AEViewer controls\n"
                            + "Fix the display jitter problem.  Quick fix is to "
                            + "change default argument for JAERViewer constuctor to true.\n\n"
                            + "Please report any problems/questions/comments to "
                            + "peter.e"+mid+"nor@gmail.com"
                            );
                }
            });
            bottomBar.add(button);
            
            viewPanel=new JDesktopPane();
            viewPanel.setVisible(true);

            this.add(viewPanel,BorderLayout.CENTER);
            viewPanel.setBackground(Color.DARK_GRAY); 
            
            this.setState(JFrame.NORMAL);
            Toolkit toolkit = Toolkit.getDefaultToolkit();
            Dimension dimension = toolkit.getScreenSize();
            dimension.height*=.8;
            this.setPreferredSize(dimension);
            
            // Start with show filters enabled
            launchShowFiltersPanel();
            
            pack();
            setVisible(true);
            toFront();
            
            // Hide the Viewers
            for (AEViewer v:jaerView.getViewers())
                v.setVisible(false);
            
        }
        public void launchShowFiltersPanel(){
            procNet.setInputStreams(getInputStreams());

             if (multiInputControl == null) {                            
                 multiInputControl = new MultiInputFrame(jaerView.getViewers().get(0).getChip(), procNet,filterPanel);
             }
             else
                 multiInputControl.setHidden(false);
        }
//        
//        public void buildDisplay()
//        {   viewPanel.removeAll();
//        
//            viewPanel.setLayout(new FlowLayout());
//        
//            viewPanel.setLayout(new GridBagLayout());
//            GridBagConstraints c=new GridBagConstraints();
//            
//            c.weightx=c.weighty=1;
//            
//            int i=0;
//            for (DisplayWriter d:inputDisplays)
//            {
//                c.gridx=i++;
//                c.gridy=1;
//                c.weightx=c.weighty=1;
//                
////                viewPanel.add(d.getPanel(),c);
//                
//                JPanel imagePanel=new JPanel();
////            
//                imagePanel.setLayout(new GridLayout());
//                
//                
//                imagePanel.setPreferredSize(new Dimension(400,400));
////            Dimension dims=this.getSize();
////            int dx=dims.width/numPanels;
//////            
////            imagePanel.setBounds(new Rectangle(panelNumber*dx,0,dx,dims.height));
////            
////                imagePanel.setBounds(getPanelLoc(1,1));
//                imagePanel.setBackground(Color.DARK_GRAY);
//
//                viewPanel.add(imagePanel,c);
//               
//                
//                imagePanel.setVisible(true);
//                
//                d.setPanel(imagePanel);
////                d.setPanel(null);
//            }
//        }
//        
        
        public void addDisplayWriter(DisplayWriter d)
        {
            addDisplayWriter(d,null);
        }
        
        
        public void addDisplayWriter(final DisplayWriter d, Dimension dim)
        {
//            viewPanel.setLayout(new FlowLayout());
            
            GridBagConstraints c=new GridBagConstraints();
            
            c.weightx=c.weighty=1;
            
            c.gridx=GridBagConstraints.RELATIVE;
            c.gridy=1;
            c.weightx=c.weighty=1;

//            JPanel imagePanel=new JPanel();
            JInternalFrame imagePanel=new JInternalFrame("",true,true);
//            
            imagePanel.setLayout(new BorderLayout());

            
//           
            imagePanel.setBackground(Color.DARK_GRAY);

            viewPanel.add(imagePanel);
            
            Component content=d.getPanel();
            
            imagePanel.add(content);
            
            if (dim==null)
                imagePanel.setPreferredSize(content.getPreferredSize());
            else
                imagePanel.setPreferredSize(dim);
            
            d.getPanel().setPreferredSize(imagePanel.getSize());

            imagePanel.setResizable(true);
            
            imagePanel.setVisible(true);
            
            
            imagePanel.addInternalFrameListener(new InternalFrameListener(){

                @Override
                public void internalFrameOpened(InternalFrameEvent e) {
                }

                @Override
                public void internalFrameClosing(InternalFrameEvent e) {
                }

                @Override
                public void internalFrameClosed(InternalFrameEvent e) {
                    d.setDisplayEnabled(false);
                }

                @Override
                public void internalFrameIconified(InternalFrameEvent e) {
                }

                @Override
                public void internalFrameDeiconified(InternalFrameEvent e) {
                }

                @Override
                public void internalFrameActivated(InternalFrameEvent e) {
                }

                @Override
                public void internalFrameDeactivated(InternalFrameEvent e) {
                }
            
            });
        }
//              
        
        
        public void addDisplayWriter(Component d)
        {
            addDisplayWriter(d,null);
        }
        
        Point windowPos=new Point(20,20);
        
        public void addDisplayWriter(final Component d, Dimension dim)
        {
//            viewPanel.setLayout(new FlowLayout());
            
            GridBagConstraints c=new GridBagConstraints();
            
            c.weightx=c.weighty=1;
            
            c.gridx=GridBagConstraints.RELATIVE;
            c.gridy=1;
            c.weightx=c.weighty=1;

//            JPanel imagePanel=new JPanel();
            JInternalFrame imagePanel=new JInternalFrame("",true,true);
//            
            imagePanel.setLayout(new BorderLayout());

            imagePanel.setBackground(Color.DARK_GRAY);

            viewPanel.add(imagePanel);
                        
            imagePanel.add(d);
            
            
            
            
            if (dim==null)
                if (d.getPreferredSize()==null)
                    dim=new Dimension(300,300);
                else
                    dim=d.getPreferredSize();
            else
                
            imagePanel.setPreferredSize(dim);
                        
            imagePanel.setSize(dim);
            
            boolean hori=windowPos.x+dim.width>viewPanel.getWidth() && (windowPos.x>0.1*viewPanel.getWidth());            
            if (hori)
            {   windowPos.x=0;           
                windowPos.y=(windowPos.y+imagePanel.getHeight())%viewPanel.getHeight();            
            }
            
            
            imagePanel.setLocation(windowPos);
            
            windowPos.x+=imagePanel.getWidth();
            
            
            
            d.setPreferredSize(imagePanel.getSize());

            imagePanel.setResizable(true);
            
            imagePanel.setVisible(true);
            
        }
        
        
        public void removeDisplay(Component disp)
        {   viewPanel.remove(disp.getParent().getParent().getParent().getParent());
            
//            viewPanel.repaint();
//            disp.setDisplayEnabled(false);
        }
        
        public Container getFilterPane()
        {
            return multiInputControl;
        }
        
        
        // </editor-fold>       
}
