/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing.label;
import java.util.Random;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OrientationEvent;
import net.sf.jaer.event.OutputEventIterator;
/**
 * Experimental labeler which only outputs endstopped cell activity. The outputs from an enclosed SimpleOrientationFilter are used to filter
 * the events to only pass events with past orientation events in one of the two directions along the orientation.
 *
 * @author tobi
 *
 * This is part of jAER
<a href="http://jaer.wiki.sourceforge.net">jaer.wiki.sourceforge.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
public class EndStoppedOrientationLabeler extends SimpleOrientationFilter{
    private int minDt = 10000;
    private int searchDistance = 5;
    /** holds the times of the last output orientation events that have been generated */
    int[][][] lastOutputTimesMap;

    public static String getDescription (){
        return "End-stopped orientation labeler";
    }

    public EndStoppedOrientationLabeler (AEChip chip){
        super(chip);
        final String endstop = "End Stopping";
        setPropertyTooltip(endstop,"minDt","min delta time to pass events");
        setPropertyTooltip(endstop,"searchDistance","search distance in pixels along orientation");
    }

    EventPacket esOut = new EventPacket(OrientationEvent.class);

    @Override
    public synchronized EventPacket<?> filterPacket (EventPacket<?> in){
        int sss = 1; // getSubSampleShift();

        int sx = chip.getSizeX(), sy = chip.getSizeY(); // for bounds checking
        checkOutputPacketEventType(in);
        checkMaps();
        EventPacket filt = super.filterPacket(in);
        for ( Object o:filt ){
            OrientationEvent e = (OrientationEvent)o;
        }

        OutputEventIterator outItr = esOut.outputIterator();
        for ( Object o:filt ){
            OrientationEvent ie = (OrientationEvent)o;
            lastOutputTimesMap[ie.x][ie.y][ie.orientation] = ie.timestamp;
            if ( !ie.hasOrientation ){
                continue;
            }
            // For this event, we look in the past times map in each direction. If we find events in one direction but not the other,
            // then the event is output, otherwise it is not.
            OrientationEvent oe = (OrientationEvent)outItr.nextOutput();
            boolean pass = false;
            Dir d = baseOffsets[ie.orientation];  // base direction vector for this orientation event, e.g 1,0 for horizontal
            // one side
            int dt0 = 0, dt1 = 0;
            for ( int i = -getSearchDistance() ; i < 0 ; i++ ){
                int x = ( ie.x + d.x * i ) >> sss, y = ( ie.y + d.y * i ) >> sss;
                if ( x < 0 || x >= sx || y < 0 || y > sy ){
                    continue;
                }
                int dt=ie.timestamp - lastOutputTimesMap[x][y][ie.orientation];
                if(dt<0) continue;
                dt0 += dt;


            }
            for ( int i = 1 ; i <= getSearchDistance() ; i++ ){
                int x = ( ie.x + d.x * i ) >> sss, y = ( ie.y + d.y * i ) >> sss;
                if ( x < 0 || x >= sx || y < 0 || y > sy ){
                    continue;
                }
                int dt=ie.timestamp - lastOutputTimesMap[x][y][ie.orientation];
                if(dt<0) continue;
                dt1 += dt;

            }
            pass = Math.abs(dt0 - dt1) > getMinDt();
            if ( pass ){
                oe.copyFrom(ie);
            }
        }
        return esOut;

    }

    synchronized private void allocateMaps (){
        if ( !isFilterEnabled() ){
            return;
        }
        if ( chip != null ){
            lastOutputTimesMap = new int[ chip.getSizeX() ][ chip.getSizeY() ][ NUM_TYPES ];
        }

    }

    private void checkMaps (){
        if ( lastOutputTimesMap == null ){
            allocateMaps();
        }
    }

    /**
     * @return the minDt
     */
    public int getMinDt (){
        return minDt;
    }

    /**
     * @param minDt the minDt to set
     */
    public void setMinDt (int minDt){
        this.minDt = minDt;
    }

    /**
     * @return the searchDistance
     */
    public int getSearchDistance (){
        return searchDistance;
    }

    /**
     * @param searchDistance the searchDistance to set
     */
    public void setSearchDistance (int searchDistance){
        this.searchDistance = searchDistance;
    }
}
