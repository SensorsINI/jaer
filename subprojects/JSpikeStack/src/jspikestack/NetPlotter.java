/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
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
    
    SpikeStack net;
    
    JPanel frm;
    
    public int updateMillis=30;           // Update interval, in milliseconds
    public float timeScale=1;               // Number of network-seconds per real-second 0: doesn't advance.  Inf advances as fast as CPU will allow
    
    public boolean realTime=false;         // Set true for real-time computation.  In this case, the network will try to display up to the end of the output queue
    
    public boolean enable=true;
    
    long lastnanotime=Integer.MIN_VALUE;
    
    double lastNetTime=Double.NEGATIVE_INFINITY;
    
    JTextComponent jt;
//    LayerStatePlotter[] layerStatePlots;
    volatile Vector<LayerStatePlotter> layerStatePlots;
    
    
    public NetPlotter(SpikeStack network)
    {   net=network;
        
    }
    
    public void raster()
    {   raster("");
        
    }
   
    public void raster(String title)
    {   
        CombinedDomainXYPlot plot = new CombinedDomainXYPlot(new NumberAxis("Time"));
        
        // Build a plot for each layer
        for (Object lay:net.layers)
            plot.add(layerRaster((SpikeStack.Layer)lay),1);
        
        
        
        JFreeChart chart= new JFreeChart("Raster: "+title,JFreeChart.DEFAULT_TITLE_FONT, plot,true);
        
        // Put it in a frame!
        JFrame fr=new JFrame();
        fr.getContentPane().add(new ChartPanel(chart));
        fr.setSize(1200,1000);
        fr.setVisible(true);
    }
    
    /* Create a raster plot for a single layer */
    public XYPlot layerRaster(SpikeStack.Layer lay)
    {
        // Add the data
        Iterator<Spike> itr=lay.outBuffer.iterator();
        XYSeries data=new XYSeries("Events");
        for (int i=0; i<lay.outBuffer.size(); i++)
        {   Spike evt=itr.next();
            data.add((float)evt.time,evt.addr);
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
        JFrame fr=new JFrame();
        
        fr.getContentPane().setBackground(Color.GRAY);
        fr.setLayout(new GridBagLayout());
        fr.setContentPane(createStatePlot());
        
        fr.pack();
//        fr.setSize(1000,400);
        
        fr.setVisible(true);        
        
        return fr;
    }
        
    
    /** Create a figure for plotting the state of the network for the network */
    public JPanel createStatePlot()
    {
        JPanel hostPanel=new JPanel();
        
        hostPanel.setLayout(new GridBagLayout());
        
        int nLayers=net.nLayers();
        
//        layerStatePlots=new LayerStatePlotter[nLayers];
        layerStatePlots=new Vector<LayerStatePlotter>(nLayers);
        
        for (int i=0; i<nLayers; i++)
        {
            JPanel pan=new JPanel();
            
            pan.setBackground(Color.darkGray);
            
            pan.setLayout(new GridBagLayout());
            
            ImageDisplay disp=ImageDisplay.createOpenGLCanvas();
            
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
            
            
//            disp.setBorderSpacePixels(5);
            
            disp.setPreferredSize(new Dimension(300,300));
                        
            
            GridBagConstraints c = new GridBagConstraints();
            c.fill=GridBagConstraints.HORIZONTAL;
            c.gridx=i;
            c.gridy=0;
            c.gridheight=2;
            
            pan.add(disp);  
            hostPanel.add(pan,c);
            
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
        
        hostPanel.add(j,c);
        
        hostPanel.setVisible(true);
        hostPanel.repaint();
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
        return hostPanel;
    }
        
    public void followState()
    {   JFrame fr=new JFrame();
        followState(fr.getContentPane());   
        
        fr.getContentPane().setBackground(Color.GRAY);
        fr.setLayout(new GridBagLayout());
        fr.setContentPane(createStatePlot());
        
        fr.pack();
//        fr.setSize(1000,400);
        
        fr.setVisible(true);   
    }
    
    
    /** Create a plot of the network state and launch a thread that updates this plot.*/
    public void followState(Container pan)
    {
        frm=createStatePlot();
        pan.add(frm);
        
        
                
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
                frm.getTopLevelAncestor().addComponentListener(new ComponentListener(){

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
                
                
                
                while (enable)
                {
                    
                    // System.out.println("Loop checking in at : "+lastNetTime+updateMillis*timeScale);
                    
                    // Initialize display time to starting net time.
                    if (lastNetTime==Double.NEGATIVE_INFINITY)
                        lastNetTime=net.time;
                    
                    if (realTime)
                        state();                    
                    else
                        state(lastNetTime+updateMillis*timeScale);
                    
                    try {
                        Thread.sleep(updateMillis);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(NetPlotter.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    
                    
                }
            }
            
        }
        
        ViewLoop th=new ViewLoop();
        
        th.start();
        
        
        
    }
    
    
    public void reset()
    {
        if (layerStatePlots!=null)
            for (LayerStatePlotter lsp: layerStatePlots)
            {   lsp.reset();            
            }
    }
    
    /** Update the state plot to the current time */
    public void state()
    {   state(net.time);        
    }
    
//    public LayerStatePlotter layStatePlot(int i)
//    {
//        return layerStatePlots[i];
//    }
    
    /** Update the state plot to the specified time */
    public void state(double upToTime)
    {
            // Can't progress further than present.
                upToTime=Math.min(upToTime,net.time);
            
    //        long t=System.nanoTime();
            
            // 
    //        float updatemillis=(float)updateTime;
            
                    
    //        if (realTime) {
    //            try {
    //                Thread.sleep(updatemillis);
    //            } catch (InterruptedException ex) {
    //                Logger.getLogger(NetPlotter.class.getName()).log(Level.SEVERE, null, ex);
    //            }
    //        }
            
    //        // If not yet ready for next frame, return
    //        if ((net.time-lastNetTime)*timeScale < updateTime)
    //            return;
            
    //        double newNetTime=net.time;
                  
            
            
              
            if (layerStatePlots==null)
                createStatePlot();
            
            for (int i=0; i<layerStatePlots.size(); i++)
                layerStatePlots.get(i).update(upToTime);
//                layStatePlot(i).update(upToTime);
//                this.layerStatePlots[i].update(upToTime);
            
            
            jt.setText("Time: "+(int)upToTime+"ms\nNetTime: "+(int)net.time+"ms");
            
                    
            
                    
                  
    //        System.out.println(net.time+" "+(t-lastnanotime));
    // 
    //        lastnanotime=t;
            
            
    //        try {
    //            
    //            Thread.sleep((int)updatemillis);
    //        } catch (InterruptedException ex) {
    //            Logger.getLogger(NetPlotter.class.getName()).log(Level.SEVERE, null, ex);
    //        }
            
    //        frm.getContentPane().setVisible(true);
            
            lastNetTime=upToTime;
    //        lastNetTime=net.time;
            
    }
    
    public class LayerStatePlotter
    {   float tau;
        SpikeStack.Layer layer;
        ImageDisplay disp;
        float[] state;  // Array of unit states.
        
        double lastTime=Double.NEGATIVE_INFINITY;
        
        float minState=Float.NaN;
        float maxState=Float.NaN;
        
        float adaptationRate=.1f;  // Adaptation rate of the limits.
        
        int outBookmark;
        
        public LayerStatePlotter(SpikeStack.Layer lay,ImageDisplay display)
        {   tau=lay.net.tau;
            layer=lay;
            disp=display;

            state=new float[lay.nUnits()];
            outBookmark=0;
            
        }
        
        public void update()
        {   update(net.time);
        }
        
        /* Update layerStatePlots to current network time */
        public void update(double toTime)
        {
//            double time=layer.net.time;
            
            
                        
            float smin=Float.MAX_VALUE;
            float smax=-Float.MAX_VALUE;
            
            if (toTime==Float.NEGATIVE_INFINITY)
                return;
            
            // Step 1: Decay present state
            for (int i=0; i<state.length; i++)
            {   state[i]*=Math.exp((lastTime-toTime)/tau);    
                if (Float.isNaN(state[i]))
                    System.out.println("STOPP");
                
                if (state[i]<smin) smin=state[i];
                if (state[i]>smax) smax=state[i];
            }
            
            
            
            
//        System.out.println(net.time);
        
                
            
            // Step 2: Add new events to state
            while (outBookmark<layer.outBuffer.size())
            {   Spike ev=layer.getOutputEvent(outBookmark);
                if (ev.time>toTime)
                    break;
                state[ev.addr]+=Math.exp((ev.time-toTime)/tau);
                if (state[ev.addr]<smin) smin=state[ev.addr];
                if (state[ev.addr]>smax) smax=state[ev.addr];
                outBookmark++;
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
            
            // Step 4: plot                        
            for (int i=0; i<state.length; i++)
            {   disp.setPixmapGray(i,(state[i]-minState)/(maxState-minState));                
            }
            
            lastTime=toTime;
            
//            disp.setTitleLabel("AAA");
//            disp.drawCenteredString(1, 1, "A");
            disp.repaint();
        }
        
        public void reset()
        {
            lastTime=Double.NEGATIVE_INFINITY;
            
        }
                
    }
//        
//    public void closePlot()
//    {
//        frm.dispose();
//    }
    
}
