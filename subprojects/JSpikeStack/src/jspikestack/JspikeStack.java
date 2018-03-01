/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;




import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Queue;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JTextArea;
import jspikestack.AERFile.ChangeRule;
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
 * package.  Just run and click on a demo.  All demos are run from static
 * methods in this class.  Take a look at these to see you can build and simulate
 * a network.
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
        
        demoMenu();
        
//        simpleDemo();
        
//         learningDemo();

//        readNet();
        
//        liquidDemo();
        
//        convDemo();
        
//        numberDemo();
        
//        spatioTemporalDemo();
        
//        rcNetDemo();
        
//        retinaDemo();
        
//        binNet();
        
    }
    
    /** Read in a network from XML, do stuff with it */
    public static void readNet()
    {           
        NetController<AxonSTP,AxonSTP.Globals,UnitLIF.Globals> nc=new NetController(NetController.AxonTypes.STP);
        Network<AxonSTP> net=nc.net;
        UnitLIF.Globals un=nc.unitGlobals;
        AxonSTP.Globals lg=nc.axonGlobals;
        
        nc.readXML();
       
        net.addAllReverseAxons();
        net.rax(0,1).enable=false;
        
        
        net.check();
        
        un.tau=100000;
//        net.delay=20000;
        lg.delay=20000;
        un.tref=5000;
        nc.view.timeScale=1f;
//        
//        nc.setForwardStrengths(new boolean[] {false,true,false,true});
//        nc.setBackwardStrengths(new boolean[] {true,true,false,true});
                
        un.useGlobalThresh=true;
        un.thresh=2.6f;
        
        
        lg.delay=20000;
        
        lg.setFastWeightTC(1000000);
        lg.doRandomJitter=true;
        lg.randomJitter=20;
        
        
        
        net.ax(1,2).setEnableFastSTDP(true);
        net.ax(3,2).setEnableFastSTDP(true);
//        net.ax(1,4).setEnableFastSTDP(true);
//        net.ax(2,4).setEnableFastSTDP(true);
//        net.ax(3,4).setEnableFastSTDP(true);
//        un.thresh=1.5f;
        
        lg.stdpWin=30000;
        lg.fastSTDP.plusStrength=-.0005f;
        lg.fastSTDP.minusStrength=-.0005f;   
        lg.fastSTDP.stdpTCminus=20000;
        lg.fastSTDP.stdpTCplus=20000;
        
        lg.doRandomJitter=true;
        lg.randomJitter=10;
        
        // Run the numbers!
        float rate=100;
        int timeMicros=1000000;
        int targetLayer=3;
        for (int i=0; i<10; i++)
        {    nc.sim.generateInputSpikes(rate,timeMicros,i,3);
        }              
        
        nc.setRecordingState(true);
        nc.addAllControls();   
        
//        NetController.Simulation sim=new NetController.Simulation();        
        nc.sim.controlledTime=false;
        nc.sim.timeScaling=1;
        nc.sim.waitForInputs=false;
        
        
        nc.startDisplay();
        nc.sim.run();
        
//        nc.saveRecoding();
        
        
    }
    
    
    public static void binNet()
    {
        NetController<Axon,Axon.Globals,UnitBinaryThresh.Globals> nc=new NetController(NetController.AxonTypes.STATIC,NetController.UnitTypes.BINTHRESH);
        Network<Axon> net=nc.net;
        UnitBinaryThresh.Globals un=nc.unitGlobals;
        Axon.Globals lg=nc.axonGlobals;
        
        nc.readXML();
       
        net.addAllReverseAxons();
        net.rax(0,1).enable=false;
        
        
        net.check();
                
        nc.startDisplay();
        nc.sim.inputEvents=AERFile.getRetinaEvents();
        nc.sim.run();
        
        
    }
    
    public static void retinaDemo()
    {        
        
        
        // Read Events
//        ArrayList<PSPInput> events=AERFile.getRetinaEvents("MovingDarkBox.aedat");
        ArrayList<PSPInput> events=AERFile.getRetinaEvents();
        
        AERFile.modifySpikes(events, new AERFile.ChangeRule(){
            @Override
            public PSPInput change(PSPInput in) {                                
                return new PSPInput(in.hitTime,in.sp.addr,in.sp.act==1?1:0,in.sp.act);                
            }
        });
        
        NetController<AxonSparse,AxonSparse.Globals,UnitLIF.Globals> nc=grabRetinaNetwork();
        
        // Setup simulation
        nc.getControls().buildersEnabled=true;
        nc.addAllControls();
        nc.setRecordingState(true);   
        nc.startDisplay();       
//        nc.view.zeroCenterDisplays(true);
        
//        NetController.Simulation sim=new NetController.Simulation();        
        nc.sim.controlledTime=true;
        nc.sim.timeScaling=1;
        nc.sim.waitForInputs=false;
        
        // And GO
        nc.sim.inputEvents=events;     
        nc.sim.timeScaling=.3f;
        nc.sim.run();
        
        
        
        
    }
    
    
    public static NetController<AxonSparse,AxonSparse.Globals,UnitLIF.Globals> grabRetinaNetwork()
    {
        
        NetController<AxonSparse,AxonSparse.Globals,UnitLIF.Globals> nc=new NetController(NetController.AxonTypes.SPARSE,NetController.UnitTypes.LIF);
        Network<AxonSparse> net=nc.net;
        UnitLIF.Globals unitGlobs=nc.unitGlobals;
        AxonSparse.Globals axonGlobs=nc.axonGlobals;
        
        net.addLayer(0).initializeUnits(128, 128);
        net.addLayer(1).initializeUnits(128, 128);
        net.addLayer(2).initializeUnits(10,10);
        net.addLayer(3).initializeUnits(64, 64);
        net.addLayer(4).initializeUnits(64, 64);
        net.addAxon(0,2);
        net.addAxon(1,2);
        net.addAxon(0,3);
        net.addAxon(1,3);
        net.addAxon(0,4);
        net.addAxon(1,4);
        
        net.lay(0).name="OFF cells";
        net.lay(1).name="ON cells";
        net.lay(2).name="PV5 cells";
        net.lay(3).name="OFF brisk";
        net.lay(4).name="ON Local Edge Detector";
        
        KernelMaker2D.Gaussian k02=new KernelMaker2D.Gaussian();
        k02.mag=.15f;
        k02.majorWidth=25;
        net.ax(0,2).setKernelControl(k02, 50, 50);
        
        KernelMaker2D.Gaussian k12=new KernelMaker2D.Gaussian();
        k12.mag=-.3f;
        k12.majorWidth=50;
        net.ax(1,2).setKernelControl(k12, 90, 90);
                
        KernelMaker2D.MexiHat k03=new KernelMaker2D.MexiHat(); // OFF to OFF brisk
        k03.mag=1f;
        k03.ratio=6f;
        k03.majorWidth=4;
        net.ax(0,3).setKernelControl(k03, 7, 7);
        
        KernelMaker2D.MexiHat k13=new KernelMaker2D.MexiHat(); // ON to OFF brisk
        k13.mag=-4.5f;
        k13.ratio=5f;
        k13.majorWidth=4;
        net.ax(1,3).setKernelControl(k13, 7, 7);
        
        KernelMaker2D.MexiHat k14=k03.copy(); // ON to ON Local
        net.ax(1,4).setKernelControl(k14, 7, 7);
        
        KernelMaker2D.MexiHat k04=k13.copy(); // OFF to ON Local
        net.ax(0,4).setKernelControl(k04, 7, 7);
        
        unitGlobs.tau=36000;
        unitGlobs.tref=7000;
        unitGlobs.useGlobalThresh=true;
        unitGlobs.thresh=1.5f;
        
        axonGlobs.delay=0;
        
        net.lay(0).fireInputsTo=false;
        net.lay(1).fireInputsTo=false;
        
        
        
//        net.inputCurrents=false;
                
        
        
        
        
        
        
        return nc;
    }
    
    
    
    
    /** Read an AEFile and do STDP learning beginning from a random net */
    public static void learningDemo()
    {        
        // Read Events
//        AERFile aef=new AERFile();          
//        aef.read("VowelSounds.aedat");
//        if (!aef.hasValidFile())
//            return;      
//        ArrayList<Spike> events=cochlea2spikes(aef.events);
        ArrayList<PSPInput> events=AERFile.getCochleaEvents("VowelSounds.aedat");
                
        // Construct Network
        NetController<AxonSTP,AxonSTP.Globals,UnitLIF.Globals> nc=new NetController(NetController.AxonTypes.STP);
        Network<AxonSTP> net=nc.net;
        UnitLIF.Globals ug=nc.unitGlobals;
        AxonSTP.Globals lg=nc.axonGlobals;
                   
        // Set Layer Global Controls
        lg.stdp.plusStrength=0.0002f;
        lg.stdp.minusStrength=-0.0001f;
        lg.stdp.stdpTCplus=5000;
        lg.stdp.stdpTCminus=10000;
        lg.doRandomJitter=true;
        lg.randomJitter=10;
        lg.stdpWin         = 30000;
//        net.delay=0;
        lg.delay=0;
        
        // Set Unit Global Controls
        ug.setTau(100000);
        ug.setTref(10000);     
        ug.setThresh(1);
        ug.useGlobalThresh=true;
        
        // Assemble Network
        Network.Initializer ini=new Network.Initializer();                
        ini.lay(0).nUnits   = 64;
        ini.lay(0).name     = "Input";
        
        ini.lay(1).nUnits   = 20;
        ini.lay(1).name     = "A1";
        
        ini.ax(0,1).wMean = .4f;
        ini.ax(0,1).wStd  = .1f;
        ini.ax(1,1).wMean = -5;
        ini.ax(1,1).wStd  = .1f;  
        
        net.buildFromInitializer(ini);
                
        // Some More post-initialization settings        
//        nc..net.ax((new boolean[] {false, true});
//        net.inputCurrents=false;
        net.lay(0).fireInputsTo=false;
        net.lay(0).inputCurrentStrength=0.5f;
        net.ax(0,1).setEnableSTDP(false);
                
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
        net.ax(0,1).setEnableSTDP(true);
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
    
    public static void spatioTemporalDemo()
    {
        NetController<AxonSpatioTemporal,AxonSpatioTemporal.Globals,UnitLIF.Globals> nc=new NetController(NetController.AxonTypes.SPATIOTEMPORAL);
        Network<AxonSpatioTemporal> net=nc.net;
        UnitLIF.Globals un=nc.unitGlobals;
        AxonSpatioTemporal.Globals lg=nc.axonGlobals;
        
        Network.Initializer ini=new Network.Initializer();
        ini.lay(0).dimx=ini.lay(0).dimy=32;
        ini.lay(1).dimx=ini.lay(1).dimy=32;
        ini.ax(0,1);
        
        net.buildFromInitializer(ini);
        
        un.tau=100000;
        un.useGlobalThresh=true;
        un.thresh=1f;
               
        // Define Spatial Kernel
        KernelMaker2D.MexiHat skern=new KernelMaker2D.MexiHat();
        skern.setAngle(45);
        skern.mag=1f;
        skern.ratio=5;
        skern.majorWidth=8;
        skern.squeezeFactorCenter=2;
        skern.squeezeFactorSurround=2;
        float[][] spatialKernel=KernelMaker2D.makeKernel(skern, 12, 12);
//        KernelMaker2D.plot(spatialKernel);
        
        // Define Temporal Kernel
        KernelMaker2D.Parabola tkern=new KernelMaker2D.Parabola();
        tkern.setWidth(20);
        tkern.setMag(2000000);
        float[][] tempKern=KernelMaker2D.makeKernel(tkern, 12, 12);
//        KernelMaker2D.plot(tempKern);
        int[][] temporalKernel=KernelMaker2D.float2int(tempKern);
        
        net.ax(0,1).defineKernel(spatialKernel, temporalKernel);
        
        float inRate=20;
        int timeMicros=1000000;
        int unit=500;
        int layer=0;
        nc.sim.generateInputSpikes(inRate, timeMicros, unit, layer);
        
        net.lay(0).fireInputsTo=true;
        nc.startDisplay();
        
        
        
//        NetController.Simulation sim=new NetController.Simulation();
        nc.sim.timeScaling=.1f;        
        nc.sim.controlledTime=true;
        
        nc.sim.run();
        
    }
        
    
    public static void rcNetDemo()
    {
        // Instantiate
        NetController<AxonSparse,AxonSparse.Globals,UnitLIF.Globals> nc=new NetController(NetController.AxonTypes.SPARSE);
        Network<AxonSparse> net=nc.net;
        UnitLIF.Globals un=nc.unitGlobals;
        AxonSparse.Globals lg=nc.axonGlobals;
        
        // Build
        net.addLayer(0);
        net.lay(0).initializeUnits(128, 128);
        net.addLayer(new LayerRC(net,1));
        net.lay(1).initializeUnits(128, 128);
        net.addAxon(0,1).defineKernel(new float[][]{new float[]{1}}); // 1-to-1 weights
                
        // Set up RC-net
        LayerRC lay=(LayerRC) net.lay(1);
        lay.lambda=5;
        
                
        // Run simulation
        nc.startDisplay();
        nc.sim.inputEvents=AERFile.getRetinaEvents("MovingDarkBox.aedat");
//        nc.sim.inputEvents=AERFile.getRetinaEvents("C:\\Users\\Peter\\Desktop\\Dropbox\\Data\\Retina\\naturalscenefast.aedat");
        nc.sim.run();
        
        
        
        
    }
    
    /** Load some AER data and test a convolutional kernel */
    public static void convDemo()
    {
        NetController<AxonSparse,AxonSparse.Globals,UnitLIF.Globals> nc=
        		new NetController(NetController.AxonTypes.SPARSE,NetController.UnitTypes.ONOFFLIF);
        Network<AxonSparse> net=nc.net;
        UnitLIF.Globals un=nc.unitGlobals;
        AxonSparse.Globals lg=nc.axonGlobals;
        
        // Initialize
//        Network.Initializer ini=new Network.Initializer();   
//        ini.lay(0).dimx=ini.lay(0).dimy=128;
//        ini.lay(1).dimx=ini.lay(1).dimy=128;
////        ini.lay(2).dimx=ini.lay(2).dimy=64;   
//        ini.ax(0,1); 
//        ini.ax(0,2);
//        net.buildFromInitializer(ini);  
        
        
        
        net.addLayer(0);
        net.lay(0).initializeUnits(128, 128);
        net.addLayer(1);
        net.lay(1).initializeUnits(64, 64);
        net.addAxon(0,1);
        net.addAxon(1,1);
                
        KernelMaker2D.MexiHat k01=new KernelMaker2D.MexiHat();
        k01.setAngle(90);
        k01.mag=2;
        k01.majorWidth=4;
        k01.ratioCenterSurround=1;
        k01.squeezeFactorCenter=4;
        k01.squeezeFactorSurround=1;
        net.ax(0,1).setKernelControl(k01, 7, 7);
        
        KernelMaker2D.Gaussian k11=new KernelMaker2D.Gaussian();
        k11.angle=90;
        k11.mag=.3f;
//        k11.mag1=.2f;
//        k11.mag2=.1f;
        k11.majorWidth=4;
        k11.squeezeFactor=4;
//        k11.majorWidth2=4;
//        k11.minorWidth2=2;        
        net.ax(1,1).setKernelControl(k11, 7, 7);
        
        
//        KernelMaker2D.Gaussian k11=new KernelMaker2D.Gaussian();
//        k11.mag=-.2f;
//        k11.majorWidth=3;
//        k11.minorWidth=3;   
//        net.ax(1,1).setKernelControl(k01, 8, 8);
        
        
        
        
        net.check(); // Check to see that the network is ready to run.
        
//        float[][] w=KernelMaker2D.makeKernel(hat, 10, 10);        
//        KernelMaker2D.plot(w);
//        net.ax(0,1).defineKernel(w);
                
//        float[][] wt=KernelMaker2D.transpose(w);
//        net.ax(0,2).defineKernel(wt);
//        KernelMaker2D.plot(wt);
                
        // Define global parameters
        un.useGlobalThresh=true;
        un.thresh=3;
        un.tau=50000;
        un.tref=5000;  
        
        // Read Events
        ArrayList<PSPInput> events=AERFile.getRetinaEvents("MovingDarkBox.aedat");
        
        // Setup simulation
        nc.getControls().buildersEnabled=true;
        
        nc.addAllControls();
        nc.setRecordingState(true);   
//        nc.view.zeroCentred=true;
        nc.startDisplay();       
//        nc.view.zeroCenterDisplays(true);
        
//        NetController.Simulation sim=new NetController.Simulation();        
        nc.sim.controlledTime=true;
        nc.sim.timeScaling=1;
        nc.sim.waitForInputs=false;
        
        // And GO
        nc.sim.inputEvents=events;
//        net.feedEvents(events);        
        nc.sim.run();
                
    }
    
    /** Number Recognition Demo */
    public static void numberDemo()
    {
        
        NetController<AxonSTP,AxonSTP.Globals,UnitLIF.Globals> nc=new NetController(NetController.AxonTypes.STP);
        Network<AxonSTP> net=nc.net;
        UnitLIF.Globals un=nc.unitGlobals;
        AxonSTP.Globals lg=nc.axonGlobals;
        
        nc.readXML();
        net.rbmify(true);
       
        un.tau=100000;
        un.tref=5000;
        un.useGlobalThresh=true;
        un.thresh=2.4f;
        
        nc.view.timeScale=1f;
        
        
//        net.rax(1,0).enable=false;
        
//        nc.setForwardStrengths(new boolean[] {true,true,false,false});
//        nc.setBackwardStrengths(new boolean[] {false,true,false,true});
        
        
        
        lg.delay=20000;
        
        lg.setFastWeightTC(500000);
        lg.doRandomJitter=true;
        lg.randomJitter=100;
        
        net.ax(1,2).setEnableFastSTDP(true);
        net.ax(3,2).setEnableFastSTDP(true);
        
        lg.stdpWin=30000;
        lg.fastSTDP.plusStrength=-.00008f;
        lg.fastSTDP.minusStrength=-.00008f;   
        lg.fastSTDP.stdpTCminus=20000;
        lg.fastSTDP.stdpTCplus=20000;
        
        lg.doRandomJitter=true;
        lg.randomJitter=10;
        
        
//        net.inputCurrents=true;
        net.lay(0).fireInputsTo=true;
        net.lay(0).inputCurrentStrength=.4f;
        
        
        // Read Events
        ArrayList<PSPInput> events=AERFile.getRetinaEvents(null);
        AERFile.filterPolarity(events, true);
        events=AERFile.resampleSpikes(events, 128, 128, 28, 28);
        
        // Setup simulation
        nc.addAllControls();
        nc.setRecordingState(true);   
        nc.startDisplay();       
//        NetController.Simulation sim=new NetController.Simulation();        
        nc.sim.controlledTime=false;
        nc.sim.timeScaling=1;
        nc.sim.waitForInputs=false;
        nc.sim.inputEvents=events;
        
        // And GO        
        nc.sim.run();
        
        
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
    public static enum Demos {GENERATE,LEARN,CONV,RCNET,RETINA};
    
    /** Start a menu that allows the user to launch a number of demos for the 
     * JSpikeStack package.  To add a new demo to the menu:
     * 1) Add the appropriate element to the "Demos" enumerator (above);
     * 2) Add the button in demoMenu()
     * 3) Connect the enumerator element to the appropriate function in DemoLauncher through the switch statement.
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
        addDemoButton("Convolution Demo",  "Here we read data from the Silicon retina.  Two output layers respond to vertically and horizontally oriented features.",Demos.CONV,pane);
        addDemoButton("RC Network",  "Takes retina inputs and fires them to a smoothing network.",Demos.RCNET,pane);
        addDemoButton("Retina",  "In this demo we mimic the behaviour of a variety of types of retinal ganglion cell.",Demos.RETINA,pane);
        
        
        frm.setPreferredSize(new Dimension(500,500));
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
                    case CONV:
                        convDemo();
                        break;
                    case RCNET:
                        rcNetDemo();
                        break;
                    case RETINA:
                        retinaDemo();
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
        
        c.fill=GridBagConstraints.CENTER;
        
        pane.add(jt,c);
        
    }
    
    
    
}
   
