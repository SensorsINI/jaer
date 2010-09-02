/*
 * BallShooterCochlea.java
 *
 * Created on July 16, 2007, 10:38 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.ballshooter;
import net.sf.jaer.chip.*;
import ch.unizh.ini.jaer.chip.cochlea.CochleaEventRate;
import net.sf.jaer.event.*;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.FrameAnnotater;
import java.awt.Graphics2D;
import javax.media.opengl.GLAutoDrawable;
import java.util.concurrent.ArrayBlockingQueue;
/**
 *
 * @author Vaibhav Garg
 */
public class BallShooterCochlea extends EventFilter2D implements FrameAnnotater
{
    private ArrayBlockingQueue Q;
    private boolean isITDFilterEnabled;
    private boolean ITDsent;
    private int packetBufferSize=getPrefs().getInt("BallShooterCochlea.packetBufferSize",10);
    private ch.unizh.ini.jaer.chip.cochlea.CochleaXCorrelator itd;
    private CochleaEventRate rate;
    FilterChain filterchain;
    int pacsum=0; //just a hack for now
    float calcITD=0;
    public BallShooterCochlea(AEChip chip)
    {
        super(chip);
        itd=new ch.unizh.ini.jaer.chip.cochlea.CochleaXCorrelator(chip);
        rate=new CochleaEventRate(chip);
        filterchain=new FilterChain(chip);
        ITDsent=false;
        filterchain.add(itd);
        filterchain.add(rate);
        
        //itd.setEnclosed(true, this);
        itd.setFilterEnabled(false); //false when starting
        rate.setFilterEnabled(false);
        itd.setEnclosed(true,this);
        rate.setEnclosed(true,this);
        setEnclosedFilterChain(filterchain);
        initFilter();
        //setEnclosedFilter(itd);
    }
    public EventPacket<?> filterPacket(EventPacket<?> in)
    {
        if(!isFilterEnabled()) return in;
        EventPacket out=null;
        //log.info("Box info "+xyfilter.getStartX()+" "+xyfilter.getStartY()+" "+xyfilter.getEndX()+" "+xyfilter.getEndY()+"\n ");
        if(!isITDFilterEnabled) //if the itd filter was not enabled
        {
            checkITDCommandRecd();
            //log.info("trying to check for command");
            return in;
        }
        //else //filter it!
        // {
        //out=getEnclosedFilter().filterPacket(in);
        //log.info("filteringpacket!");
        out=filterchain.filterPacket(in);
        if(in.getSize()>0)
        { pacsum++;
          
          if(pacsum>packetBufferSize)//if pacsum has hundred packets
          { if(!ITDsent)//if ITD is not sent already
                sendITD();
          }
          else
              calcITD+=itd.getITD();
          //calcITD+=-1;
        }
        return out;
        
        //}
    }
    void sendITD()
    {
        ArrayBlockingQueue Q=Tmpdiff128CochleaCommunication.getBlockingQ();
        if(Q==null)
        {
            
            Tmpdiff128CochleaCommunication.initBlockingQ();
            log.info("q was null in cochlea wantign to send ITD. should not happen!");
        }
        CommunicationObject co=new CommunicationObject();
        co.setForRetina(true);
        co.setControllerMsgFromCochleaType(CommunicationObject.ITDVAL);
        co.setItdValue(calcITD/packetBufferSize);
        try
        {
            //Q.putString(co);
            //System.out.println("Size before "+Tmpdiff128CochleaCommunication.sizeBlockingQ());
            Tmpdiff128CochleaCommunication.putBlockingQ(co);
            log.info("Gave retina ITD");
            ITDsent=true;
            itd.setFilterEnabled(false);
            //System.out.println("Size after "+Tmpdiff128CochleaCommunication.sizeBlockingQ());
        }
        catch(Exception e)
        {
            log.info("Problem putting packet for retina in cochlea");
            e.printStackTrace();
        }
    }
    void checkITDCommandRecd()
    {
        ArrayBlockingQueue Q=Tmpdiff128CochleaCommunication.getBlockingQ();
        if(Q==null)
        {
            
            Tmpdiff128CochleaCommunication.initBlockingQ();
            log.info("q was null in cochlea");
        }
        if(Tmpdiff128CochleaCommunication.sizeBlockingQ()>0)//if something there in queue
        {
            log.finest("Got info from retina");
            CommunicationObject co=(CommunicationObject)Tmpdiff128CochleaCommunication.peekBlockingQ();
            if(co.isForCochlea())//if packet is for cochlea
            {
                try
                {
                    co=(CommunicationObject)Q.poll();
                    if(co.isItdFilterEnabled())
                    {
                        log.info("enable itd filter");
                        itd.setFilterEnabled(true);//enable itd filter
                        itd.setIldMax(500);
                        itd.setItdMax(500);
                        itd.setIDis(20);
                        //itd.setLpFilter3dBFreqHz(10.00f);
                        isITDFilterEnabled=true;
                    }
                }
                catch (Exception e)
                {
                    log.info("Problem in Cochlea while polling");
                    e.printStackTrace();
                }
            }
        }
        // else
        //   log.info("Queue Empty!");
    }
    public void initFilter()
    {
        isITDFilterEnabled=false;
        ITDsent=false;
        int pacsum=0; //just a hack for now
        float calcITD=0;
        //filterchain.reset();
    }
    public Object getFilterState()
    {
        return null;
    }
    
    /** Overrides to avoid setting preferences for the enclosed filters */
    @Override public void setFilterEnabled(boolean yes)
    {
        this.filterEnabled=yes;
        getPrefs().putBoolean("filterEnabled",yes);
    }
    
    public void resetFilter()
    {
        initFilter();
        //setFilterEnabled(false);
    }
    public void annotate(GLAutoDrawable drawable)
    {
        //((FrameAnnotater)clusterFinder).annotate(drawable);
    }
    public void annotate(Graphics2D g)
    {
    }
    public void annotate(float[][][] frame)
    {
    }
    
    public int getPacketBufferSize()
    {
        return packetBufferSize;
    }
    
    public void setPacketBufferSize(int packetBufferSize)
    {
        this.packetBufferSize = packetBufferSize;
        getPrefs().putInt("BallShooterCochlea.packteBufferSize",packetBufferSize);
    }
}
