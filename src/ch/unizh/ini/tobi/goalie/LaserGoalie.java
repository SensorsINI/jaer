/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.tobi.goalie;
import ch.unizh.ini.caviar.chip.AEChip;
import ch.unizh.ini.hardware.pantilt.PanTilt;
/**
 *  The Goalie including control of a pantilt unit to aim a laser pointer at the ball that is being blocked.
 * 
 * @author tobi
 */
public class LaserGoalie extends Goalie{
    
    PanTilt panTilt=new PanTilt();
    
    public LaserGoalie(AEChip chip){
        super(chip);
    }

}
