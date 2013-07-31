/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.ListIterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

/**
 *
 *  This is the controller in the semi-MVC architecture setup we got going on here.
 *
 * This class has methods to instantiate/build a neural net and run simulations.
 *
 * @author Peter
 */
public class NetController<AxonType extends Axon,LayerGlobalType extends Controllable,UnitGlobalType extends Controllable> implements Serializable {

    public Network<AxonType> net;
    public UnitGlobalType unitGlobals;
    public LayerGlobalType axonGlobals;


    transient public NetPlotter view;

    transient public SpikeRecorder recorder;

    public static enum AxonTypes {STP,STATIC,SPARSE,SPATIOTEMPORAL};
    public static enum UnitTypes {LIF,ONOFFLIF,BINTHRESH}

    public boolean enable=true;

    public transient Simulation sim=new Simulation();

    public transient Controls controls;

    public NetController()
    {   this(AxonTypes.STP);
    }


    public NetController(AxonTypes t)
    {
        this(t,UnitTypes.LIF);
    }

    public NetController(AxonTypes t,UnitTypes u)
    {

        Axon.AbstractFactory axonFactory;
        Unit.AbstractFactory unitFactory;

        switch(t)
        {
            case STATIC:
                axonFactory=new Axon.Factory();
                break;
            case STP:
                axonFactory=new AxonSTP.Factory();
                break;
            case SPARSE:
                axonFactory=new AxonSparse.Factory();
                break;
            case SPATIOTEMPORAL:
                axonFactory=new AxonSpatioTemporal.Factory();
                break;
            default:
                axonFactory=new Axon.Factory();
                break;
        }

        switch(u)
        {
            case LIF:
                unitFactory=new UnitLIF.Factory();
                break;
            case ONOFFLIF:
                unitFactory=new UnitOnOff.Factory();
                break;
            case BINTHRESH:
                unitFactory=new UnitBinaryThresh.Factory();
                break;
            default:
                unitFactory=new UnitLIF.Factory();
                break;
        }

        net=new Network(axonFactory,unitFactory);
        view=new NetPlotter(net);

        axonGlobals = (LayerGlobalType) axonFactory.getGlobalControls();
        unitGlobals = (UnitGlobalType) unitFactory.getGlobalControls();

        recorder=new SpikeRecorder(net);
//        view=net.plot;
    }

    public void readXML()
    {
        net.read.readFromXML(net);
    }

    public void readXML(String loc)
    {
        net.read.readFromXML(net,new File(loc));
    }


    public void startDisplay()
    {
        view.followState();
    }


    public void setRecordingState(boolean state)
    {

        recorder.setRecodingState(state);
    }

    public void saveRecording()
    {
        recorder.printToFile();
    }


    public void plotRaster()
    {   plotRaster("");
    }

    public void plotRaster(String title)
    {
        if (recorder.spikes==null)
        {   JOptionPane.showMessageDialog(null,"No spikes have been recorded","No Spike Recording",JOptionPane.ERROR_MESSAGE);
        }
        view.raster(recorder.spikes,title);
    }


    public void reset()
    {
        net.reset();
        view.reset();
        recorder.clear();
        sim.inputTime=0;
    }

    public void addAllControls()
    {
        view.addControls(makeControlPanel());
    }

    public ControlPanel makeControlPanel()
    {
        ControlPanel cp=new ControlPanel(getControls());
//        cp.addController();
        return cp;

//        cp.addController(new SimulationControls());
////        cp.addController(net.getControls());
//        cp.addController(unitGlobals);
//        cp.addController(layerGlobals);
//        for (Axon l:net.getAxons())
//        {
//            cp.addController(l.getControls());
//        }
//        for (Layer l:net.getLayers())
//        {
//            cp.addController(l.getControls());
//        }
//        return cp;
    }


    public class Simulation implements Serializable{

        public boolean waitForInputs=false;     // True if network stops when input events run out
        public boolean controlledTime=true;      // True if simulation should run slower than max speed of CPU
        public float timeScaling=1;        // If controlledTime==true, scale factor between real-time and simulation time.  High=fast

        public float simTimeSeconds=Float.POSITIVE_INFINITY; // Timeout for simulation, in simulation time.

        public boolean active=false;

        public ArrayList<PSPInput> inputEvents=new ArrayList();


        public int inputTime=0; // Tracker of time to add input events from

        /** Add a bunch of events to the network input for a given timespan at a given rate. */
        public void generateInputSpikes(float rate,int timeMicros,int unit,int layer)
        {
            int timeStep=(int)(1000000/rate); // timestep in us
            if (timeStep==0) {
				System.out.println("Timestep is zero here.  Can't do that.");
			}


            int endTime=inputTime+timeMicros;

            while(inputTime<endTime)
            {   //int number=i<nEvents/2?8:2;
                inputEvents.add(new PSPInput(inputTime,unit,layer));
                inputTime+=timeStep;
            }
        }


//        public Simulation(final boolean isControlledTime)
//        {
////            Simulation sim=new Simulation();
//            controlledTime=isControlledTime;
//
//        }
//
//        public void Simulation(final float simulationTime)
//        {
////            Simulation sim=new Simulation();
//    //        sim.controlledTime=isControllerTime;
//            simTimeSeconds=simulationTime;
//
//        }
//
//        public void Simulation(boolean isControlledTime, float simulationTime)
//        {
////            Simulation sim=new Simulation();
//            controlledTime=isControlledTime;
//            simTimeSeconds=simulationTime;
//
//        }


        public void run()
        {

            Thread sim =new Thread()
            {

//                public String getName()
//                {
//                    return "SpikeStack Simulation Thread";
//                }

                @Override
                public void run()
                {

                    active=true;
                    try
                    {

                        net.enable=true;
                        enable=true;
                        reset();


                        net.liveMode=waitForInputs;

                        int finalTime;
                //        if (settings.simTimeSeconds>Integer.MAX_VALUE/1000000)
                //            finalTime=Integer.MAX_VALUE;
                //        else
                        finalTime=net.time+(int)(1000000*simTimeSeconds);

                        // Old strategy: Feed all events in advance
//                        if (inputEvents!=null)
//                        {   net.inputBuffer.clear();
//                            for (PSP ev:inputEvents)
//                                net.addToQueue(ev);
//                        }

                        // New strategy: Just tell network to eat what it wants, control pace of feeding.
                        Thread netThread=net.startEventFeast();

                        ListIterator<PSPInput> it=inputEvents.listIterator();

                        int inputEventIndex=0;
                        int plotIntervalMillis=30;

                        if (controlledTime)
                        {
                            view.realTime=true;


                            int plotIntervalMicros=plotIntervalMillis*1000;


                            int targetNetTime=net.time;
                            long targetSystemTime=System.currentTimeMillis();

                            net.liveMode=true;


                            // Loop along, allowing the network to progress up to a certain time
                            // in each iteration.
                            while ((net.time<finalTime) && enable)
                            {
                                targetNetTime+=plotIntervalMicros*timeScaling;
                                targetSystemTime+=plotIntervalMillis;




                                // Old strategy: Feed Events in advance, control pace of eating
//                                net.eatEvents(targetNetTime);
//                                if (!net.hasEvents())
//                                    break;

                                // New strategy: Eat all events as fast as possible, control pace of feeding
                                if (inputEventIndex<inputEvents.size()) {
									while((inputEventIndex<inputEvents.size()) && (inputEvents.get(inputEventIndex).hitTime<targetNetTime)) {
										net.addToQueue(inputEvents.get(inputEventIndex++));
									}
								}
								else
                                {   net.kill();
                                    break;
                                }

                                try {
                                    long sleepTime=Math.max(targetSystemTime-System.currentTimeMillis(),0);
                    //                System.out.println(sleepTime);
                                    Thread.sleep(sleepTime);
                                } catch (InterruptedException ex) {
                                    Logger.getLogger(NetController.class.getName()).log(Level.SEVERE, null, ex);
                                }
                            }
                            enable=true;
                        }
                        else
                        {
                            for (PSPInput ev:inputEvents) {
								net.addToQueue(ev);
							}

                            view.realTime=false;
                            view.updateMicros=30000;
                            view.timeScale=timeScaling;
                            net.liveMode=false;

                            while(net.hasEvents())
                            {
                                try {
                    //                System.out.println(sleepTime);
                                    Thread.sleep(plotIntervalMillis);
                                } catch (InterruptedException ex) {
                                    Logger.getLogger(NetController.class.getName()).log(Level.SEVERE, null, ex);
                                }

                            }


                            net.eatEvents(finalTime);
                        }


                    }
                    catch(Exception ex)
                    {
                        Logger.getLogger(NetController.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    finally
                    {
                        // Notify anyone who's waiting for the simulation to die.
                        synchronized(Simulation.this)
                        {   active=false;
                            Simulation.this.notify();
                        }

                        System.out.println("Simulation Ended");
                    }

                }



            };
            sim.setName("JSpikeStack Simulation Thread");

            sim.start();


        }

        /** Stop the simulation and wait for it to end */
        public void kill()
        {
            if (active)
            {
                synchronized(this)
                {
                    net.enable=false;
                    enable=false;

                    try {
                        this.wait(); // Wait for simulation to notify us that it's ended
                    } catch (InterruptedException ex) {
                        Logger.getLogger(NetController.class.getName()).log(Level.SEVERE, null, ex);
                    }


                }

                net.kill();
                net.reset();
                view.reset();

            }



        }





    }


    public void printStats()
    {



        String s=recorder.nSpikes() +" spikes recorded\n";

//        for (int i=0; i<net.nLayers(); i++)
//        {
//            for (int j=0; j<)
//
//        }

        System.out.println(s);
    }



    public Controls getControls()
    {
        if (controls==null) {
			controls=new Controls();
		}
        return controls;
    }


    public class Controls extends Controllable
    {

        public void doSave()
        {
            saveState();
        }

        public void doLoad()
        {
            loadState();
        }

        public boolean buildersEnabled=false;

        @Override
        public String getName() {
            return "Network Controls";
        }

        @Override
        public ArrayList<Controllable> getSubControllers()
        {
            ArrayList<Controllable> cp=new ArrayList();
            cp.add(new SimulationControls());

            if (buildersEnabled) {
				cp.add(new BuilderControls());
			}

    //        cp.addController(net.getControls());
            cp.add(unitGlobals);
            cp.add(axonGlobals);
            for (Axon l:net.getAxons()) {
				cp.add(l.getControls());
			}

            for (Layer l:net.getLayers()) {
				cp.add(l.getControls());
			}

            return cp;
        }

    }

    public void updateControls()
    {
        controls.updateControl();
    }

    public void updateControlsAndDisplay()
    {
        updateControls();
        view.rebuild();
    }


    public class BuilderControls extends Controllable
    {
        Controllable layerBuilder;
        Controllable axonBuilder;

        @Override
        public String getName() {
            return "Building Controls";
        }

        @Override
        public ArrayList<Controllable> getSubControllers()
        {
            if (layerBuilder==null) {
				layerBuilder= new LayerAdder();
			}
            if (axonBuilder==null) {
				axonBuilder= new AxonAdder();
			}

            ArrayList<Controllable> arr=new ArrayList();

            arr.add(layerBuilder);
            arr.add(axonBuilder);

            return arr;
        }


        public class LayerAdder extends Controllable
        {
            public int dimx;
            public int dimy;

            @Override
            public String getName() {
                return "Layer Builder";
            }

            public void doAdd_Layer()
            {

                if((getDimx()==0) || (getDimy()==0))
                {   showErrorMsg("Cannot add a layer where dimx or dimy are zero.");
                    return;
                }

                net.addLayer(net.nLayers(), getDimx(), getDimy());
                updateControlsAndDisplay();
            }

            public int getDimx() {
                return dimx;
            }

            public void setDimx(int dimx) {
                this.dimx = dimx;
            }

            public int getDimy() {
                return dimy;
            }

            public void setDimy(int dimy) {
                this.dimy = dimy;
            }

        }

        public class AxonAdder extends Controllable
        {
            public int preLayer;
            public int postLayer;

            @Override
            public String getName() {
                return "Axon Builder";
            }

            public void doAdd_Axon()
            {
                try
                {   net.addAxon(getPreLayer(), getPostLayer());
                }
                catch(RuntimeException e)
                {   showErrorMsg(e.getMessage());
                }
                updateControls();
            }

            public int getPreLayer() {
                return preLayer;
            }

            public void setPreLayer(int preLayer) {
                this.preLayer = preLayer;
            }

            public int getPostLayer() {
                return postLayer;
            }

            public void setPostLayer(int postLayer) {
                this.postLayer = postLayer;
            }


        }
    }

    public class SimulationControls extends Controllable
    {

        @Override
        public String getName() {
            return "Simulation Controls";
        }

        /** Stop the simulation */
        public void doSTOP()
        {   sim.kill();
        }

        public void doRerun()
        {
            sim.kill();
            sim.run();
        }

        public void doSave_Recording()
        {
            recorder.printToFile();
        }


        public float getTimeScaling() {
            return sim.timeScaling;
        }

        public void setTimeScaling(float timescaling) {
            sim.timeScaling = timescaling;
        }



    }





    public enum Stats {FastWeights}
    public void addStatDisplay(Stats stat)
    {

        StatDisplay st;

        switch (stat)
        {
            case FastWeights:
                st=new StatDisplay(net,"Ave Fastweight"){
                    @Override
                    public float compute() {

                        AxonSTP ax=(AxonSTP) net.ax(1,2);
                        float w=0;
                        float k=0;

                        for (int i=0; i<ax.w.length; i++) {
							for (int j=0; j<ax.w.length; j++)
                            {   w+=ax.currentFastWeightValue(i, j);
                                k+=1;
                            }
						}
                        return w/k;
                    }
                };

                break;
            default:
                return;

        }



        view.addStatDisplay(st);
    }


    public void saveState()
    {

        FileOutputStream fis=null;
        ObjectOutputStream oos=null;
        try {
            JFileChooser fileChooser = new JFileChooser(System.getProperty("user.dir"));
            int returnVal = fileChooser.showSaveDialog(null);

            File file=fileChooser.getSelectedFile();
            if (file==null) {
				return;
			}
            fis = new FileOutputStream(file);

            oos=new ObjectOutputStream(fis);

            oos.writeObject(this);

        } catch (IOException ex) {
            Logger.getLogger(NetController.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                fis.close();
            } catch (IOException ex) {
                Logger.getLogger(NetController.class.getName()).log(Level.SEVERE, null, ex);
            }
        }



    }



    public void loadState()
    {
        JFileChooser chooser = new JFileChooser();
        chooser.setCurrentDirectory( new File( "./") );
        if ( chooser.showSaveDialog( null ) != JFileChooser.APPROVE_OPTION ) {
			return;
		}

        File file=chooser.getSelectedFile();

        if (file==null) {
			return;
		}


        loadState(file);


    }


    public void loadState(File file)
    {
        this.sim.kill();

        try {
            FileInputStream fileIn = new FileInputStream(file);
            ObjectInputStream in = new ObjectInputStream(fileIn);
            NetController nc = (NetController) in.readObject();

            copyStateFrom(nc);


            fullReset();



        } catch (IOException ex) {
            Logger.getLogger(NetController.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(NetController.class.getName()).log(Level.SEVERE, null, ex);
        }



    }


    public void copyStateFrom(NetController nc)
    {


        this.net=nc.net;
        nc.net.implementQueues();
//        this.sim=nc.sim;
        this.axonGlobals=(LayerGlobalType)nc.axonGlobals;
        this.unitGlobals=(UnitGlobalType)nc.unitGlobals;

        this.controls=null;


    }


    public void fullReset()
    {
        this.view.rebuild(net);
        this.addAllControls();
        this.updateControlsAndDisplay();


    }






}
