/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.davis;

/**
 * CDAVIS camera with heterogenous mixture of DAVIS and RGB APS global shutter pixels camera 
 * @author tobi
 */
public class DavisRGB640 extends Davis346BaseCamera {

    @Override
    public boolean firstFrameAddress(short x, short y) {
        return (x == getSizeX()-1) && (y == getSizeY()-1);
    }

    @Override
    public boolean lastFrameAddress(short x, short y) {
        return (x == 0) && (y == 0); //To change body of generated methods, choose Tools | Templates.
    }
    
}
