/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.lang.reflect.InvocationTargetException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Queue;
import javax.swing.*;
import javax.swing.text.JTextComponent;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYDotRenderer;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/**
 *
 * @author oconnorp
 */
public class NetPlotter {
    
    Network net;
    
    JPanel frm;
    
    Component controlPanel;
    
    public int updateMicros=30000;           // Update interval, in milliseconds
    public float timeScale=1;               // Number of network-seconds per real-second 0: doesn't advance.  Inf advances as fast as CPU will allow
    
    public boolean realTime=false;         // Set true for real-time computation.  In this case, the network will try to display up to the end of the output queue
    
    public boolean enable=true;
    
    int lastNetTime=0;
    
    public ArrayList<StatDisplay> stats=new ArrayList();
    
    Thread viewLoop;
    
    
//    public boolean useneuronstates=true;
    
//    public boolean zeroCentred=false;
    
    
    JTextComponent jt;
//    LayerStatePlotter[] layerStatePlots;
    public volatile ArrayList<LayerStatePlotter> layerStatePlots;
    
        
    public NetPlotter(Network network)
    {   net=network;
        
    }
    
    public void raster(Collection<Spike> spikes)
    {   raster(spikes,"");
        
    }
    
    public void addStatDisplay(StatDisplay st)
    {
        stats.add(st);
        
    }
   
    public void raster(Collection<Spike> spikes,String title)
    {   
        CombinedDomainXYPlot plot = new CombinedDomainXYPlot(new NumberAxis("Time"));
        
        // Build a plot for each layer
        for (int i=0; i<net.nLayers(); i++)
            plot.add(layerRaster(spikes,net.lay(i)),1);
                
        JFreeChart chart= new JFreeChart("Raster: "+title,JFreeChart.DEFAULT_TITLE_FONT, plot,true);
        
        // Put it in a frame!
        JFrame fr=new JFrame();
        fr.getContentPane().add(new ChartPanel(chart));
        fr.setSize(1200,1000);
        fr.setVisible(true);
    }
        
    /* Create a raster plot for a single layer */
    public XYPlot layerRaster(Collection<Spike> spikes,Layer lay)
    {
//        throw new UnsupportedOperationException("This is broken for now!");
        
        // Add the data
        Iterator<Spike> itr=spikes.iterator();
        XYSeries data=new XYSeries("Events");
        for (int i=0; i<spikes.size(); i++)
        {   Spike evt=itr.next();
            if (evt.layer==lay.ixLayer)
                data.add((float)evt.time/1000,evt.addr);
        }
        XYDataset raster = new XYSeriesCollection(data);
        
        //SamplingXYLineAndShapeRenderer renderer = new SamplingXYLineAndShapeRenderer(false, true);
        XYDotRenderer renderer = new XYDotRenderer();
        renderer.setDotWidth(2);
        renderer.setDotHeight(5);

        return new XYPlot(raster, null, new NumberAxis("Layer "+lay.ixLayer), renderer);
        
    }

    public JFrame createStateFigure()
    {
        final JFrame fr=new JFrame();
        
//        fr.getContentPane().setBackground(Color.GRAY);
//        fr.setLayout(new GridBagLayout());
        
        final JPanel hostPanel=createStatePlot();
        
        
        
        
        
        
        fr.setContentPane(hostPanel);
        
        
        
//        fr.getContentPane().addComponentListener(new ComponentListener(){
//
//            @Override
//            public void componentResized(ComponentEvent e) {
//                fr.getContentPane().repaint();
//            }
//
//            @Override
//            public void componentMoved(ComponentEvent e) {}
//
//            @Override
//            public void componentShown(ComponentEvent e) {}
//
//            @Override
//            public void componentHidden(ComponentEvent e) {}
//        
//        });
        
        
//        fr.setPreferredSize(new Dimension(1000,1200));
//        fr.setPreferredSize(new Dimension(1000,1200));
        fr.setSize(1200,800);
        hostPanel.setPreferredSize(fr.getSize());
        
        fr.pack();
        
        
        fr.setVisible(true);        
        
        return fr;
    }
        
    
    public JPanel createStatePlot()
    {
        final JPanel hostPanel=new JPanel();
                
        hostPanel.setPreferredSize(Toolkit.getDefaultToolkit().getScreenSize());
        
        
        
        
        
        
        if (!SwingUtilities.isEventDispatchThread())
        {  
            try {
                SwingUtilities.invokeAndWait(new Runnable(){
                    @Override
                    public void run() {
                        createStatePlot(hostPanel);
                    }
                });
            } catch (InterruptedException ex) {
                Logger.getLogger(NetPlotter.class.getName()).log(Level.SEVERE, null, ex);
            } catch (InvocationTargetException ex) {
                Logger.getLogger(NetPlotter.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        else
            createStatePlot(hostPanel);
        return hostPanel;
    }
   
    
    /** Create a figure for plotting the state of the network for the network */
    public void createStatePlot(final JPanel hostPanel)
    {
        
//        JPanel hostPanel=new JPanel();
        hostPanel.setLayout(new BorderLayout());
        
        
        
        
//        hostPanel.setPreferredSize(new Dimension(1000,800));
        
        JPanel statePanel=new JPanel();
        statePanel.setBackground(Color.BLACK);
        
//        statePanel.setLayout(new GridBagLayout());
        statePanel.setLayout(new FlowLayout());
        
        final int nLayers=net.nLayers();
        
//        layerStatePlots=new LayerStatePlotter[nLayers];
        layerStatePlots=new ArrayList<LayerStatePlotter>(nLayers);
        
//        Toolkit toolkit =  Toolkit.getDefaultToolkit ().getS
        
        
        for (int i=0; i<nLayers; i++)
        {
            JPanel pan=new JPanel();
            
            pan.setBackground(Color.darkGray);
            
//            pan.setLayout(new GridBagLayout());
            pan.setLayout(new BorderLayout());
            
            final ImageDisplay disp=ImageDisplay.createOpenGLCanvas();
            
           // disp.resetFrame(.5f);
            
            // Assign sizes to the layers
            int sizeX;
            int sizeY;            
            if (net.lay(i).dimx * net.lay(i).dimy < net.lay(i).nUnits())
            {   sizeY=(int)Math.ceil(Math.sqrt(net.lay(i).nUnits()));
                sizeX=(int)Math.ceil(net.lay(i).nUnits()/(double)sizeY);
            }
            else 
            {   sizeX=net.lay(i).dimx;
                sizeY=net.lay(i).dimy;
            }
            disp.setImageSize(sizeX,sizeY);
            
//            disp.setSize(200,200);
            disp.setPreferredSize(new Dimension(200,200));
            disp.setBorderSpacePixels(18);
            
            
//            hostPanel.addComponentListener(new ComponentListener(){
//
//                @Override
//                public void componentResized(ComponentEvent e) {
////                    int dim=e.getComponent().getWidth()/nLayers;
////                    disp.setPreferredSize(new Dimension(dim,dim));
//                    
//                }
//
//                @Override
//                public void componentMoved(ComponentEvent e) {
//                }
//
//                @Override
//                public void componentShown(ComponentEvent e) {
//                }
//
//                @Override
//                public void componentHidden(ComponentEvent e) {
//                }
//            
//
//            });

            
            
//            disp.setBorderSpacePixels(5);
            
//            disp.setMinimumSize(new Dimension(100,100));
            
//            disp.setPreferredSize(new Dimension(300,300));
                        
            
            GridBagConstraints c = new GridBagConstraints();
            c.fill=GridBagConstraints.HORIZONTAL;
            c.gridx=i;
            c.gridy=0;
            c.gridheight=2;
            
            pan.add(disp,BorderLayout.CENTER);  
//            statePanel.add(pan,c);
            statePanel.add(pan);
            
//            disp.setVisible(true);
//            pan.setVisible(true);
            
            layerStatePlots.add(new LayerStatePlotter(net.lay(i),disp));
        }
        
        GridBagConstraints c = new GridBagConstraints();
        c.fill=GridBagConstraints.HORIZONTAL;
        c.gridx=0;
        c.gridy=2;
        c.gridwidth=nLayers;
        JPanel j=new JPanel();
        j.setBackground(Color.black);
        
        jt=new JTextArea();
        jt.setBackground(Color.BLACK);
        jt.setForeground(Color.white);
        jt.setEditable(false);
        jt.setAlignmentY(.5f);
        
        
        
        jt.setPreferredSize(new Dimension(400,100));
        
        j.add(jt);
        
        hostPanel.add(j,BorderLayout.SOUTH);
        
        statePanel.setVisible(true);
        statePanel.repaint();
//        j.setVisible(true);
        
//        fr.getContentPane().add(j,c);
////        fr.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//        
//        fr.pack();
////        fr.setSize(1000,400);
//        
//        
//        fr.setVisible(true);        
////        fr.validate();       
////        fr.repaint();
//        
//        return fr;
        
        hostPanel.add(statePanel,BorderLayout.CENTER);
        
        // If controls have been added, create control panel.
        if (controlPanel!=null)
        {   
            
            JScrollPane jsp=new JScrollPane(controlPanel,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            
            jsp.setPreferredSize(new Dimension(controlPanel.getPreferredSize().width,800));
            
            hostPanel.add(jsp,BorderLayout.WEST);
            
        }
        
        
        
        
//        return hostPanel;
    }
    
    public void addControls(ControlPanel cp)
    {   //JScrollPane js=new JScrollPane();
//        js.setLayout(new BorderLayout());
//        js.add(cp,BorderLayout.CENTER);
//        controlPanel=js;
        
        controlPanel=cp;
    
        
    }
    
//    public void addControls(Controllable con)
//    {
//        if (controlPanel==null)
//            controlPanel=new ControlPanel();
//        
//        controlPanel.addController(con);
//        
//    }
        
    public void followState()
    {   
        SwingUtilities.invokeLater(new Runnable()
        {
            @Override
            public void run() {
                JFrame fr=new JFrame();
                
                JPanel pan=new JPanel();
                               
                
                followState(pan);   

                fr.getContentPane().setBackground(Color.GRAY);
                fr.setLayout(new GridBagLayout());
                fr.setContentPane(pan);
                
                

                fr.pack();
                fr.setVisible(true);   
            }
            
        });
        
        
    }
    
    
    /** Create a plot of the network state and launch a thread that updates this plot.*/
    public void followState(final Container pan)
    {
        frm=createStatePlot();
        pan.add(frm);
        
        if (viewLoop!=null && viewLoop.isAlive())
            throw new RuntimeException("You're trying to start the View Loop, but it's already running");
        
                
        class ViewLoop extends Thread{
            
            boolean wasshowing;

            public ViewLoop() {
                super();
                setName("NetPlotter");
            }
            
            /* TODO: Ponder about whether this is the best solution */
            @Override
            public void run()
            {
//                frm.getTopLevelAncestor().addComponentListener(new ComponentListener(){
                 pan.addComponentListener(new ComponentListener(){

                    @Override
                    public void componentResized(ComponentEvent e) {
//                        throw new UnsupportedOperationException("Not supported yet.");
                    }

                    @Override
                    public void componentMoved(ComponentEvent e) {
//                        throw new UnsupportedOperationException("Not supported yet.");
                    }

                    @Override
                    public void componentShown(ComponentEvent e) {
//                        throw new UnsupportedOperationException("Not supported yet.");
                    }

                    @Override
                    public void componentHidden(ComponentEvent e) {
                        enable=false; 
                    }


                });
                
                
                int kk=0;
                
                while (enable)
                {
                    
                    // System.out.println("Loop checking in at : "+lastNetTime+updateMicros*timeScale);
                    
                    
//                    if (lastNetTime==Integer.MIN_VALUE)
//                    {   // Wait to get initial event time
//                        lastNetTime=0;
//                        kk++;
//                    }
//                    else
//                    {   // In normal operation, this block should be on.
                    
                    synchronized(NetPlotter.this)
                    {

//                        SwingUtilities.invokeLater(new Runnable()
//                        {   @Override
//                            public void run(){
                                if (realTime)
                                    state();                    
                                else
                                    state(lastNetTime+(int)(updateMicros*timeScale));

                                for (StatDisplay st:stats)
                                    st.display();

//                        }});
    //                    System.out.println("PlotThread: "+lastNetTime/1000);

                    }

                        try {
                            Thread.sleep(updateMicros/1000);
                        } catch (InterruptedException ex) {
                            break;
    //                            Logger.getLogger(NetPlotter.class.getName()).log(Level.SEVERE, null, ex);
                        }

                    
                    }
                
                
                synchronized(NetPlotter.this)
                {

                    NetPlotter.this.notify();


                }
            }
            
        }
        
//        ViewLoop th=new ViewLoop();
//        
//        th.start();
        
        viewLoop=new ViewLoop();
        viewLoop.start();
        
        
    }
    
        
    public void kill()
    {
        if (viewLoop!=null && viewLoop.isAlive())
        {
            synchronized(this)
            {
                viewLoop.interrupt();
                try {
                    this.wait();
                } catch (InterruptedException ex) {
                    Logger.getLogger(NetPlotter.class.getName()).log(Level.SEVERE, null, ex);
                }

                System.out.println("Display Terminated");

            }
        }
    }
    
    public void rebuild(Network netw)
    {   
        kill();
        
        layerStatePlots=null;
        frm.removeAll();
        
        net=netw;
        followState(frm);
        
        
    }
    
    
    public void rebuild()
    {
        kill();
        followState(frm);
        
    }
    
    
    public void reset()
    {
//        kill();
        synchronized(this)
        {
        lastNetTime=0;
        if (layerStatePlots!=null)
            for (LayerStatePlotter lsp: layerStatePlots)
            {   lsp.reset();            
            }
        }
    }
    
    /** Update the state plot to the current time */
    public void state()
    {   state(net.time);        
    }
        
    /** Update the state plot to the specified time */
    public void state(int upToTime)
    {
        
            if (net.time==Integer.MIN_VALUE)
                return;
        
            // Can't progress further than present.
                upToTime=Math.min(upToTime,net.time);
                          
            if (layerStatePlots==null)
                createStatePlot();
            
            for (int i=0; i<layerStatePlots.size(); i++)
                layerStatePlots.get(i).update(upToTime);
//                layStatePlot(i).update(upToTime);
//                this.layerStatePlots[i].update(upToTime);
            
            
            jt.setText("Time: "+(int)upToTime/1000+"ms\nNetTime: "+(int)net.time/1000+"ms");
            
                    
            
            lastNetTime=upToTime;            
    }
    
    public class LayerStatePlotter
    {   float tau;
        Layer layer;
        ImageDisplay disp;
        float[] state;  // Array of unit states.
        
        int lastTime=0;
               
        float minState=Float.NaN;
        float maxState=Float.NaN;
        
        float adaptationRate=.03f;  // Adaptation rate of the limits.
        
        Queue<Spike> spikeQueue;
        
        Unit.StateTracker[] stateTrackers;
        
//        public boolean zeroCenter=false; // Center the color-scale about 0
        
        
//        int outBookmark;
        
        
        public LayerStatePlotter(Layer lay,ImageDisplay display)
        {   
//            if (!useneuronstates)
//                tau=((LIFUnit.Globals)(lay.unitFactory.getGlobalControls())).getTau();
            
            layer=lay;
            disp=display;

            state=new float[lay.nUnits()];
//            outBookmark=0;
            
            
            disp.setFontSize(14);
            
            spikeQueue=net.outputQueue.addReader(new Comparable<Spike>(){
                @Override
                public int compareTo(Spike o) {
                    return o.layer==layer.ixLayer?1:0;
                }
            });
            
            
            stateTrackers=new Unit.StateTracker[lay.nUnits()];
            for (int i=0; i<stateTrackers.length; i++)
                stateTrackers[i]=lay.units[i].getStateTracker();
            
            
            
        }
        
        public void update()
        {   update(net.time);
        }
        
        /* Update layerStatePlots to current network time */
        public void update(final int toTime)
        {
//            double time=layer.net.time;
            
//            synchronized(this)
//            {
            
//            final int toTime=net.time;
            
                        
            float smin=Float.MAX_VALUE;
            float smax=-Float.MAX_VALUE;
            int imin=0;
            int imax=0;
            
            
//            if (toTime==Integer.MIN_VALUE)
//                return;
//            else if (lastTime==Integer.MIN_VALUE)
//                lastTime=toTime;
            
            if (toTime==lastTime) // Nothing has changed.  Return
                return;
            
//            if (useneuronstates)
//            {
                // Step 1: Add new events to state
                while (!spikeQueue.isEmpty()) 
                {   
                    if (spikeQueue.peek().time>toTime)
                        break;

                    Spike ev=spikeQueue.poll();

                    stateTrackers[ev.addr].updatestate(ev);

                }
                
                // Step 2: Advance to present time
                for (int i=0; i<state.length; i++)
                {   state[i]=stateTrackers[i].getState(toTime); 
                    if (Float.isNaN(state[i]))
                    {   System.out.println("Trying to plot NaN state");
                        return;
                    }

                    if (state[i]<smin) {smin=state[i]; imin=i;}
                    if (state[i]>smax) {smax=state[i]; imax=i;}
                }
                               
//                
//            }
//            else
//            {
//                // Step 1: Decay present state
//                for (int i=0; i<state.length; i++)
//                {   state[i]*=Math.exp((lastTime-toTime)/tau);    
//                    if (Float.isNaN(state[i]))
//                        System.out.println("Trying to plot NaN state");
//    //                else if (lastTime>toTime)
//    //                    System.out.println("Troubles");
//
//                    if (state[i]<smin) smin=state[i];
//                    if (state[i]>smax) smax=state[i];
//                }
//
//                
//                // Step 2: Add new events to state
//                while (!spikeQueue.isEmpty()) 
//                {   
//                    if (spikeQueue.peek().time>toTime)
//                        break;
//
//                    Spike ev=spikeQueue.poll();
//
//                    state[ev.addr]+=Math.exp((ev.time-toTime)/tau);
//
//                    if (state[ev.addr]<smin) smin=state[ev.addr];
//                    if (state[ev.addr]>smax) smax=state[ev.addr];
//                }
//                
//                
//            }
            
            
            
            
            
            
            
            
//        System.out.println(net.time);
        
            if (stateTrackers[0].isZeroCentered())
            {
                float bigState=Math.max(Math.abs(smin),Math.abs(smax));
                
                smin=-bigState;
                smax=bigState;
                
            }
            
            
            // Step 3: Set the color scale limits
            if (Float.isNaN(minState))
            {   minState=smin;
                maxState=smax;
            }
            else if (adaptationRate==1)
            {   minState=smin;
                maxState=smax; 
            }
            else
            {   float invad=1-adaptationRate;
                minState=smin*adaptationRate+invad*minState;
                maxState=smax*adaptationRate+invad*maxState;                
            }
            
            
            
            
            
            final int iimax=imax;
            final float ssmin=smin;
            final float ssmax=smax;
            
            SwingUtilities.invokeLater(new Runnable(){
                    @Override
                    public void run() {
            
                        // Step 4: plot           
                        float rate=0;
                        for (int i=0; i<state.length; i++)
                        {   disp.setPixmapGray(i,(state[i]-minState)/(maxState-minState));    
                            rate=Math.max(state[i],rate);
                        }
                        rate=rate*1000000/tau;


                        lastTime=toTime;

                        disp.setTitleLabel(layer.getName()+" "+stateTrackers[iimax].getLabel(ssmin,ssmax));
            //            disp.drawCenteredString(1, 1, "A");

            
                        disp.repaint();
                    }
            });
            
//            disp.repaint();
            
//            }
        }
        
        public void reset()
        {   // outBookmark=0;
            
            synchronized(this)
            {
                for(Unit.StateTracker s:stateTrackers)
                    s.reset();
                    
                lastTime=0;
                minState=Float.NaN;
                maxState=Float.NaN;
                for (int i=0; i<state.length; i++)
                    state[i]=0;
            }
        }
                
    }
//        
//    public void closePlot()
//    {
//        frm.dispose();
//    }
    
}
