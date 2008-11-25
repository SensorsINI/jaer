/*
 * AEPlayerInterface.java
 *
 * Created on February 3, 2006, 5:13 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package net.sf.jaer.graphics;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.eventio.AEFileInputStream;


/**
 * The interface that the JAERViewer AEPlayer uses to control sychronized playback of logged AE data.
 * @author tobi
 */
public interface AEPlayerInterface extends PlayerInterface {
    
    public void openAEInputFileDialog() ;
        
    public AEPacketRaw getNextPacket(AEPlayerInterface player);
    public AEPacketRaw getNextPacket();
    
    public void speedUp();
    public void slowDown();
    public int getSamplePeriodUs();
    public int getSampleNumEvents();

    public AEFileInputStream getAEInputStream();
    
    
}
