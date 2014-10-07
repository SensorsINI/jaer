/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.neuralnets;




import java.io.File;

import jspikestack.AxonSTP;
import jspikestack.Network;
import jspikestack.UnitLIF;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import ch.unizh.ini.jaer.projects.integrateandfire.ClusterEvent;
import ch.unizh.ini.jaer.projects.integrateandfire.ClusterSet;

/**
 * This filter contains one or more networks and routes events to them.
 *
 * @author oconnorp
 */
public class SpikeStackArrayFilter extends EventFilter2D{

    NetworkList netArr;

    Network net;
    AxonSTP.Globals lg;
    UnitLIF.Globals ug;

    File startDir=new File(getClass().getClassLoader().getResource(".").getPath().replaceAll("%20", " ")+"subprojects/JSpikeStack/files/nets");


    public SpikeStackArrayFilter(AEChip chip)
    {   super(chip);
    }


    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {

        // Initialize Remapper
        if (netArr==null) {
			return in;
		}
		else if (!netArr.R.isBaseTimeSet()) {
			netArr.R.setBaseTime(in.getFirstTimestamp());
		}

        // If it's a clusterset event
        if (in.getEventClass()==ClusterSet.class)
        {

            for (BasicEvent ev:in)
            {   netArr.routeTo((ClusterEvent)ev);
            }
        }
        else
        {   // Otherwise, route all events to network 0.
            for (BasicEvent ev:in)
            {   netArr.routeTo(ev,0);
            }
        }

        netArr.crunch();

        return in;
    }

    /** Grab the network */
    public void doGrab_Network()
    {

        /* Step 1: Grab the network */
//        STPLayer.Factory<STPLayer> layerFactory=new STPLayer.Factory();
//        LIFUnit.Factory unitFactory=new LIFUnit.Factory();
//
//        lg= layerFactory.glob;
//        ug = (LIFUnit.Globals)unitFactory.getGlobalControls();
//
//        net=new SpikeStack(layerFactory,unitFactory);
////        buildFromXML(net);
////        STPStack<STPStack,STPStack.Layer> net = new STPStack();
//
//        net.read.readFromXML(net,startDir);
//
//        if (!net.isBuilt())
//            return;
//
//
//        if (!net.isBuilt())
//            return;
//
//        ug.setTau(200000);
//        net.delay=10000;
//        ug.setTref(5);
//
//        nc.view.timeScale=1f;
//
//        // Set up connections
//        float[] sigf={1, 1, 0, 0};
//        net.setForwardStrength(sigf);
//        float[] sigb={0, 0, 0, 1};
//        net.setBackwardStrength(sigb);
//
//        // Up the threshold
////        net.scaleThresholds(500);
//
//
//        for (int i=0; i<net.nLayers(); i++)
//            for (Unit u:net.lay(i).units)
//                u.thresh*=400;
//
//
////        net.fastWeightTC=2;
////
////        net.lay(1).enableFastSTDP=true;
////        net.lay(3).enableFastSTDP=true;
////
////
////        net.fastSTDP.plusStrength=-.001f;
////        net.fastSTDP.minusStrength=-.001f;
////        net.fastSTDP.stdpTCminus=10;
////        net.fastSTDP.stdpTCplus=10;
//
//        net.plot.timeScale=1f;
//
//        net.liveMode=true;
//        net.plot.realTime=true;
//
//        net.plot.updateMicros=100000;
//
//        net.inputCurrents=true;
//        net.lay(0).inputCurrentStrength=.5f;
//
////        STPStack<STPStack,STPStack.Layer> net2=net.read.copy();
//
////        net.eatEvents(10000);
//
//
//
//        netArr=new NetworkList(net,getNetMapper(net));
    }

    public NetMapper getNetMapper(Network net)
    {
        VisualMapper R=new VisualMapper();
        R.inDimX=(short)chip.getSizeX();
        R.inDimY=(short)chip.getSizeY();
        R.outDimX=(short)net.lay(0).dimx;
        R.outDimY=(short)net.lay(0).dimy;
        return R;
    }

    public void doPlot_Network()
    {
        netArr.setPlottingState(0, true);
//        this.chip.getAeViewer().getContentPane().setLayout(new GridBagLayout());
//        this.chip.getAeViewer().getContentPane().add(netArr.initialNet.plot.getFrame().getContentPane());
    }


    @Override
    public void resetFilter() {
//        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void initFilter() {



//        throw new UnsupportedOperationException("Not supported yet.");
    }





}
