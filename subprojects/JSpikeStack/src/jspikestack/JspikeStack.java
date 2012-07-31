/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;
//import org.ejml.simple.SimpleMatrix;
//import org.ejml.data.DenseMatrix64F;


import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JTextArea;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYDotRenderer;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.experimental.chart.plot.CombinedXYPlot;

/**
 * Here's a collection of Demos that make use of the Spiking Network 
 * package.  To run, just uncomment the appropriate line in the main function.
 * 
 * HAVE FUN.
 * 
 * WebSite:  <a href="http://sites.google.com/site/thebrainbells/home/jspikestack">http://sites.google.com/site/thebrainbells/home/jspikestack</a>
 * 
 * @author oconnorp
 */
public class JspikeStack {

    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args){
        
//        simpleDemo();
        
//         learningDemo();

//        readNet();
        
//        liquidDemo();
        
        demoMenu();
        
    }
    
    /** Read in a network from XML, do stuff with it */
    public static void readNet()
    {           
        NetController<STPLayer,STPLayer.Globals,LIFUnit.Globals> nc=new NetController(NetController.Types.STP_LIF);
        SpikeStack<STPLayer,Spike> net=nc.net;
        LIFUnit.Globals un=nc.unitGlobals;
        STPLayer.Globals lg=nc.layerGlobals;
        
        nc.readXML();
       
        un.tau=100000;
        net.delay=20000;
        un.tref=5000;
        nc.view.timeScale=1f;
        
        nc.setForwardStrengths(new boolean[] {false,true,false,true});
        nc.setBackwardStrengths(new boolean[] {true,true,false,true});
                        
        un.useGlobalThresh=true;
        un.thresh=2.6f;
        
        
        lg.setFastWeightTC(2000000);
        lg.doRandomJitter=true;
        
        net.lay(1).setEnableFastSTDP(true);
        net.lay(3).setEnableFastSTDP(true);
        
        lg.stdpWin=30000;
        lg.fastSTDP.plusStrength=-.001f;
        lg.fastSTDP.minusStrength=-.001f;   
        lg.fastSTDP.stdpTCminus=20000;
        lg.fastSTDP.stdpTCplus=20000;
        
        
        // Run the numbers!
        float rate=100;
        int timeMicros=1000000;
        for (int i=0; i<10; i++)
        {    nc.generateInputSpikes(rate,timeMicros,i,3);
        }              
        
        nc.setRecordingState(true);
        nc.addAllControls();   
        
        NetController.SimulationSettings sim=new NetController.SimulationSettings();        
        sim.controlledTime=false;
        sim.timeScaling=1;
        sim.waitForInputs=false;
        
        
        nc.startDisplay();
        nc.simulate(sim);
        
//        nc.saveRecoding();
        
        // Why does this not give identical results?
//        nc.startDisplay();
//        net.eatEvents(20000000);
        
    }
    
    /** Read an AEFile and do STDP learning beginning from a random net */
    public static void learningDemo()
    {
        
        // Read Events
        AERFile aef=new AERFile();
        aef.read();
        ArrayList<Spike> events=aevents2events(aef.events);
        
        // Construct Network
        NetController<STPLayer,STPLayer.Globals,LIFUnit.Globals> nc=new NetController(NetController.Types.STP_LIF);
        SpikeStack<STPLayer,Spike> net=nc.net;
        LIFUnit.Globals ug=nc.unitGlobals;
        STPLayer.Globals lg=nc.layerGlobals;
                   
        // Set Layer Global Controls
        lg.stdp.plusStrength=0.0002f;
        lg.stdp.minusStrength=-0.0001f;
        lg.stdp.stdpTCplus=5000;
        lg.stdp.stdpTCminus=10000;
        lg.doRandomJitter=true;
        lg.randomJitter=10;
        lg.stdpWin         = 30000;
        net.delay=0;
        
        // Set Unit Global Controls
        ug.setTau(100000);
        ug.setTref(10000);     
        ug.setThresh(1);
        ug.useGlobalThresh=true;
        
        // Assemble Network
        SpikeStack.Initializer ini=new SpikeStack.Initializer();                
        ini.lay(0).nUnits   = 64;
        ini.lay(0).targ     = 1;
        ini.lay(0).name     = "Input";
        ini.lay(0).WoutMean = .4f;
        ini.lay(0).WoutStd  = .1f;
//        ini.lay(0).WlatMean = -0f;
//        ini.lay(0).WlatStd  = 0f;        
        ini.lay(1).nUnits   = 20;
        ini.lay(1).name     = "A1";
        ini.lay(1).WlatMean = -5;
        ini.lay(1).WlatStd  = .1f;        
        net.buildFromInitializer(ini);
                
        // Some More post-initialization settings        
        nc.setLateralStrengths(new boolean[] {false, true});
        net.inputCurrents=false;
        net.lay(0).inputCurrentStrength=0.5f;
        net.lay(0).setEnableSTDP(false);
                
        // Prepare Simulation
        nc.addAllControls();
        nc.setRecordingState(true);        
//        NetController.SimulationSettings sim=new NetController.SimulationSettings();        
//        sim.controlledTime=true;
//        sim.timeScaling=10;        
//        nc.startPlotting();
        
        
        // Run the first round, plot.
        net.feedEventsAndGo(events);      
        
        
        nc.printStats();
        nc.plotRaster("Before Learning");
        
        // Run the next rounds
        net.lay(0).setEnableSTDP(true);
//        sim.controlledTime=false;        
        int nEpochs     = 10;
        for (int i=0; i<nEpochs; i++)
        {   System.out.println("Pass "+i);
            nc.reset();
            net.feedEventsAndGo(events);
        }
        nc.printStats();
        nc.plotRaster("After "+nEpochs+" training cycles");
         
//        
    }
    
    /** Experiment with liquid-state machines */
    public static void liquidDemo()
    {
        
        // Read Events
        AERFile aef=new AERFile();
        aef.read();
        int nLayers=2;
        
        
        // Plot Events
//        
//        // Initialize Network
//        STDPStack.Initializer ini=new STDPStack.Initializer(nLayers);
//        
//        ini.tau             = 500f;
//        ini.thresh          = 1;
//        ini.tref            = 0.005f;        
//        ini.stdp.plusStrength    = .0001f;
//        ini.stdp.stdpTCminus     = 10f;
//        ini.stdp.stdpTCplus      = 10;
//        ini.stdpWin         = 30;
//        
//        ini.lay(0).nUnits   = 64;
//        ini.lay(0).targ     = 1;
//        ini.lay(0).name     = "Input";
//        ini.lay(0).WoutMean = .06f;
//        ini.lay(0).WoutStd  = 0.06f;
//        ini.lay(0).enableSTDP=true;
//        
//        ini.lay(0).WlatMean = -0.5f;
//        ini.lay(0).WlatStd  = 1f;
//        
//        ini.lay(1).nUnits   = 20;
//        ini.lay(1).name     = "A1";
//        ini.lay(1).WlatMean = -.5f;
//        ini.lay(1).WlatStd  = 0f;
//        
//        
//        STDPStack<STDPStack,STDPStack.Layer> es=new STDPStack(ini);
//        
//        
//        es.lay(1).latSend=1;
//        es.lay(0).latSend=1;
//        
//        es.inputCurrents=true;
//        es.inputCurrentStrength=0.8f;
//        
//        
//        int nEpochs     = 5;
//        
//        
//        ArrayList<Spike> events=aevents2events(aef.events);
//        
//        
//        es.lay(0).enableSTDP=false;
//        es.feedEvents(events);
//        es.plot.raster();
//        
//        es.plot.timeScale=0;
//        
//        es.lay(0).enableSTDP=true;
//        // Feed Events to Network
//        for (int i=0; i<nEpochs; i++)
//        {   System.out.println("Pass "+i);
//            es.reset();
//            es.feedEvents(events);
//        }
//        
//        
//        es.eatEvents();
//        es.plot.raster();
//        
        
        
    }
    
    /** Simple Demo, no Learning */
    public static void simpleDemo()
    {
//        
//        // Read Events
//        AERFile aef=new AERFile();
//        aef.read();
//        ArrayList<Spike> events=aevents2events(aef.events);
//        
//        // Plot Events
//        
//        // Initialize Network
//        int nLayers=2;
//        SpikeStack.Initializer ini=new SpikeStack.Initializer(nLayers);
//        ini.tau=20;
//        ini.thresh=1;
//        ini.lay(0).nUnits=64;
//        ini.lay(0).targ=1;
//        ini.lay(0).name="Input";
//        ini.lay(0).WoutMean=.01f;
//        ini.lay(0).WoutStd=0.005f;
//        
//        ini.lay(1).nUnits=10;
//        ini.lay(1).name="A1";
//        ini.lay(1).WlatMean=-1;
//        ini.lay(1).WlatStd=-1;
//        
//        SpikeStack es=new SpikeStack(ini);
//        
//        // Feed Events to Network
//        es.feedEventsAndGo(events);
//        
//        es.plot.raster();
//        
    }
    
    /** Best working settings for voice separation */
    public static void voiceSepDemo()
    {
//        // Read Events
//        AERFile aef=new AERFile();
//        aef.read();
//        int nLayers=2;
//        
//        
//        // Plot Events
//        
//        // Initialize Network
//        STDPStack.Initializer ini=new STDPStack.Initializer(nLayers);
//        
//        ini.tau             = 5f;
//        ini.thresh          = 1;
//        ini.tref            = 0;        
//        ini.stdp.plusStrength    = .0001f;
//        ini.stdp.stdpTCminus     = 10f;
//        ini.stdp.stdpTCplus      = 10;
//        ini.stdpWin         = 30;
//        
//        ini.lay(0).nUnits   = 64;
//        ini.lay(0).targ     = 1;
//        ini.lay(0).name     = "Input";
//        ini.lay(0).WoutMean = .05f;
//        ini.lay(0).WoutStd  = 0.0005f;
//        ini.lay(0).enableSTDP=true;
//        
//        ini.lay(1).nUnits   = 10;
//        ini.lay(1).name     = "A1";
//        ini.lay(1).WlatMean = -1;
//        ini.lay(1).WlatStd  = 0f;
//        
//        
//        STDPStack es=new STDPStack(ini);
//        
//        
//        es.lay(1).latSend=1;
//        
//        
//        // Feed Events to Network
//        for (int i=0; i<10; i++)
//        {   es.reset();
//            ArrayList<Spike> events=aevents2events(aef.events);
//            es.feedEvents(events);
//        }
//        es.plot.raster();
//        
    }
    
    /** Convert AEViewer file events to JspikeStack Events */
    public static ArrayList<Spike> aevents2events(ArrayList<AERFile.Event> events)
    {   
        ArrayList<Spike> evts =new ArrayList<Spike>();
        
        for (int i=0; i<events.size(); i++)
        {   AERFile.Event ev=events.get(i);
        
            if (ev.addr>255) continue;
        
            Spike enew=new Spike((int)(ev.timestamp-events.get(0).timestamp), ev.addr/4, 0);
            
            evts.add(enew);
        }
        return evts;
    }
    
    /** Plot a list of events read from a file */
    public static void plotEvents(Queue<Spike> evts)
    {   
        Iterator<Spike> itr=evts.iterator();
        XYSeries data=new XYSeries("Size");
        for (int i=0; i<evts.size(); i++)
        {   Spike ev=itr.next();
            data.add((float)ev.time,ev.addr);
        }
        XYDataset raster = new XYSeriesCollection(data);
        
        XYDotRenderer renderer = new XYDotRenderer();
        renderer.setDotWidth(2);
        renderer.setDotHeight(2);

        XYPlot subplot1 = new XYPlot(raster, null, new NumberAxis("address"), renderer);
        XYPlot subplot2 = new XYPlot(raster, null, new NumberAxis("address"), renderer);

        CombinedXYPlot plot = new CombinedXYPlot(new NumberAxis("Time"),new NumberAxis("Address"));
        plot.add(subplot1, 1);
        plot.add(subplot2, 1);
        
        JFreeChart chart= new JFreeChart("Raster",JFreeChart.DEFAULT_TITLE_FONT, plot,true);

        JFrame fr=new JFrame();
        fr.getContentPane().add(new ChartPanel(chart));
        fr.setSize(1200,1000);
        fr.setVisible(true);

    }
    
    /** Enumeration of the list of available demos */
    public static enum Demos {GENERATE,LEARN};
    
    /** Start a menu that allows the user to launch a number of demos for the 
     * JSpikeStack package.  To add a new demo to the menu:
     * 1) Add the appropriate element to the "Demos" enumerator;
     * 2) Add the button in demoMenu
     * 3) Connect the enumerator element to the appropriate function in DemoLauncher
     */
    public static void demoMenu()
    {
                
        JFrame frm=new JFrame();
        frm.setTitle("JSpikeStack demos");
        Container pane=frm.getContentPane();
        JButton button;
        
        pane.setLayout(new GridBagLayout());
        
        addDemoButton("Network Generation Demo","Read a network From XML and let it generate",Demos.GENERATE,pane);
        addDemoButton("Learning Demo","Read an AER file, initialize a random net, and run STDP learning",Demos.LEARN,pane);
        
        
        frm.setPreferredSize(new Dimension(500,300));
        frm.pack();
        frm.setVisible(true);
        frm.toFront();
        frm.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
    }
    
    static class DemoLauncher extends Thread{
            Demos demoNumber;
            public DemoLauncher(Demos demoNum)
            {   demoNumber=demoNum;
            }
            
            @Override
            public void run()
            {   switch (demoNumber)
                {   case GENERATE:
                        readNet();       
                        break;
                    case LEARN:
                        learningDemo();
                        break;
                }
            }
        }        
    
    /** Add a demo button */
    public static void addDemoButton(String demoName,String description,final Demos demoNumber,Container pane)
    {        
        JButton button=new JButton(demoName);
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                new DemoLauncher(demoNumber).start();
            }
        });
        
        
        GridBagConstraints c = new GridBagConstraints();
        
        c.fill=GridBagConstraints.BOTH;
        c.gridx=0;
        c.gridy=demoNumber.ordinal();
        c.weightx=1;
        c.weighty=1;
        pane.add(button,c);
        
        JTextArea jt=new JTextArea(description);
        jt.setLineWrap(true);
        jt.setWrapStyleWord(true);
        
        c.gridx=1;
        //c.anchor=GridBagConstraints.CENTER;
        c.fill=GridBagConstraints.CENTER;
        
        pane.add(jt,c);
        
    }
    
    
    
}
   
