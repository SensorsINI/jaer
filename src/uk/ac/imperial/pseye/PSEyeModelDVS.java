
package uk.ac.imperial.pseye;

import java.util.Arrays;
import java.util.ArrayList;
import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.OutputEventIterator;
import ch.unizh.ini.jaer.chip.dvs320.cDVSEvent;
/**
 * @author Mat Katz
 */
@Description("DVS emulator using the PS-Eye Playstation camera")
public class PSEyeModelDVS extends PSEyeModelChip {
    private static ArrayList<PSEyeCamera.Mode> supportedModes =
            new ArrayList<PSEyeCamera.Mode>(Arrays.asList(PSEyeCamera.Mode.MONO));
           
    @Override
    public ArrayList<PSEyeCamera.Mode> getSupportedModes() {
        return this.supportedModes;
    }
    
    @Override
    protected PSEyeEventExtractor createEventExtractor() {
        return new DVSExtractor(this);
    }
    
    class DVSExtractor extends  PSEyeEventExtractor {
        private double valueDelta;
        private int number;
        private int x;
        private int y;
    
        public DVSExtractor(AEChip aechip) {
            super(aechip);
        }
        
        @Override
        protected void createEvents(int pixelIndex, OutputEventIterator itr) {
            valueDelta = valueMapping[pixelValues[pixelIndex] & 0xff] - previousValues[pixelIndex];       
            // brightness change 
            if (valueDelta > sigmaOnThresholds[pixelIndex]) { // if our gray level is sufficiently higher than the stored gray level
                x = pixelIndex % sx;
                y = (int) Math.floor(pixelIndex / sx); 
                number = (int) Math.floor(valueDelta / sigmaOnThresholds[pixelIndex]);
                outputEvents(cDVSEvent.EventType.Brighter, number, itr, x, y);
                previousValues[pixelIndex] += sigmaOnThresholds[pixelIndex] * number; // update stored gray level by events // TODO include mismatch
            } 
            else if (valueDelta < sigmaOffThresholds[pixelIndex]) { // if our gray level is sufficiently higher than the stored gray level
                x = pixelIndex % sx;
                y = (int) Math.floor(pixelIndex / sx); 
                number = (int) Math.floor(valueDelta / sigmaOffThresholds[pixelIndex]);
                outputEvents(cDVSEvent.EventType.Darker, number, itr, x, y);
                previousValues[pixelIndex] += sigmaOffThresholds[pixelIndex] * number; // update stored gray level by events // TODO include mismatch
            } 
        }
        
        @Override
        protected void initValues(int pixelIndex) {
            previousValues[pixelIndex] = valueMapping[pixelValues[pixelIndex] & 0xff];
        }
    }
}

