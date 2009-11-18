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
    private int minDt = getPrefs().getInt("EndStoppedOrientationLabeler.minDt",10000);
    private int maxDtToUse = getPrefs().getInt("EndStoppedOrientationLabeler.maxDtToUse",100000);
    private int searchDistance = getPrefs().getInt("EndStoppedOrientationLabeler.searchDistance",5);
    /** holds the times of the last output orientation events that have been generated */
    int[][][] lastOutputTimesMap;

    public static String getDescription (){
        return "End-stopped orientation labeler";
    }

    public EndStoppedOrientationLabeler (AEChip chip){
        super(chip);
        final String endstop = "End Stopping";
        setPropertyTooltip(endstop,"minDt","min average delta time in us betweeen two sides of endstopped orientation cell to pass events");
        setPropertyTooltip(endstop,"searchDistance","search distance in pixels along orientation");
        setPropertyTooltip(endstop,"maxDtToUse","orientation event delta times larger than this in us are ignored and assumed to come from another edge");
    }
    EventPacket esOut = new EventPacket(OrientationEvent.class);

    @Override
    public synchronized EventPacket<?> filterPacket (EventPacket<?> in){
        int sss = getSubSampleShift();

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
            lastOutputTimesMap[ie.x>>>sss][ie.y>>>sss][ie.orientation] = ie.timestamp;
            if ( !ie.hasOrientation ){
                continue;
            }
            // For this event, we look in the past times map in each direction. If we find events in one direction but not the other,
            // then the event is output, otherwise it is not.
            boolean pass = false;
            Dir d = baseOffsets[ie.orientation];  // base direction vector for this orientation event, e.g 1,0 for horizontal
            // one side
            int n0 = 0, n1 = 0; // prior event counts, each side
            for ( int i = -getSearchDistance() ; i < 0 ; i++ ){
                int x = ( ie.x + d.x * i ) >>> sss, y = ( ie.y + d.y * i ) >>> sss;
                if ( x < 0 || x >= sx || y < 0 || y >= sy ){
                    continue;
                }
                int dt = ie.timestamp - lastOutputTimesMap[x][y][ie.orientation];
                if ( dt > maxDtToUse || dt < 0 ){
                    continue;
                }
                n0 ++;


            }
            for ( int i = 1 ; i <= getSearchDistance() ; i++ ){
                int x = ( ie.x + d.x * i ) >>> sss, y = ( ie.y + d.y * i ) >>> sss;
                if ( x < 0 || x >= sx || y < 0 || y >= sy ){
                    continue;
                }
                int dt = ie.timestamp - lastOutputTimesMap[x][y][ie.orientation];
                if ( dt > maxDtToUse || dt < 0 ){
                    continue;
                }
                n1 ++;

            }
            pass = (float)Math.abs(n0 - n1) > getMinDt();
//            System.out.println(String.format(" n0=%d n1=%d pass=%s",n0,n1,pass));
            if ( pass ){
                OrientationEvent oe = (OrientationEvent)outItr.nextOutput();
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
        getPrefs().putInt("EndStoppedOrientationLabeler.minDt",minDt);
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
        if ( searchDistance < 1 ){
            searchDistance = 1;
        }
        this.searchDistance = searchDistance;
        getPrefs().putInt("EndStoppedOrientationLabeler.searchDistance",searchDistance);
    }

    /**
     * @return the maxDtToUse
     */
    public int getMaxDtToUse (){
        return maxDtToUse;
    }

    /**
     * @param maxDtToUse the maxDtToUse to set
     */
    public void setMaxDtToUse (int maxDtToUse){
        this.maxDtToUse = maxDtToUse;
        getPrefs().putInt("EndStoppedOrientationLabeler.maxDtToUse",maxDtToUse);
    }
}
