/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cl.eye;

import ch.unizh.ini.jaer.projects.thresholdlearner.TemporalContrastEvent;
import net.sf.jaer.Description;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.EventExtractor2D;
import net.sf.jaer.chip.TypedEventExtractor;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;

/**
 * A behavioral model of an AE retina using the code laboratories interface to a PS eye camera.
 * 
 * @author tobi
 */
@Description("AE retina using the PS eye camera")
public class CLRetina extends AEChip{

    private int[] lastEventPixelValues=new int[320*240];
    int n=320*240;
    
    public CLRetina() {
        setSizeX(320);
        setSizeY(240);
        setEventExtractor(new EventExtractor(this));
    }
    
    public class EventExtractor extends TypedEventExtractor<TemporalContrastEvent> {

        public EventExtractor(AEChip aechip) {
            super(aechip);
        }

        @Override
        public synchronized void extractPacket(AEPacketRaw in, EventPacket out) {
            int[] pixVals=in.getAddresses();
            int ts=in.getTimestamps()[0];
            OutputEventIterator itr=out.outputIterator();
            int sx=getSizeX(), sy=getSizeY(), i=0;
            for(int x=0;x<sx;x++){
                for(int y=0;y<sy;y++){
                    if(pixVals[i]!=lastEventPixelValues[i]){
                        BasicEvent e=itr.nextOutput();
                        e.x=(short)x;
                        e.y=(short)y;
                        e.timestamp=ts;
                    }
                    lastEventPixelValues[i]=pixVals[i];
                    i++;
                }
            }
        }
        
        
        
    }
    
}
