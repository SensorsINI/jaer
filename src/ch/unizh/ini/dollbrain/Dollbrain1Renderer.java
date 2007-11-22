/*
 * UioCameraRenderer.java
 *
 * Created on 16. mai 2006, 10:19
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.dollbrain;
import ch.unizh.ini.caviar.chip.AEChip;
//import ch.unizh.ini.caviar.aemonitor.AEPacket2D;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.graphics.*;

/**
 * This class overrides the render(AEPacket2D ae) to display an image
 * with gray levels on black. The number of spikes that a packet needs to
 * saturate a pixels is given by the colorScale variable which is adjustable
 * with the up and down arrow keys (i.e. contrast).
 *
 * Future plans (TODO's): 
 * - The contrast step height should be normalized by the packet size and 
 * time-frame to give a more stable image at high rendering speed and 
 * to keep contrast when swapping packetsize.
 * - Implement a fixed-pattern noise canceler
 * @author hansbe@ifi.uio.no
 */
public class Dollbrain1Renderer extends AEChipRenderer {
    
//    public enum ColorMode {PositiveGrayLevelOnBlack};
    
    /** Creates a new instance of UioCameraRenderer
     * @param chip the chip we're rendering for
     */
    public Dollbrain1Renderer(AEChip chip) {        
        super(chip); 
        
        nextframe=new int[chip.getSizeY()][5][2];
    }
    
    private int frameStart;
    private int lastTimestamp;
    private int[][][] nextframe; 
    
    private int typeMax=0;
    private int typeMin=256;
    
    
    
    /**
     * Does the rendering (only one colorMode is available)
     * 
     * @param packet a packet of events (already extracted to x,y,type
     * @return reference to the frame   [y][x][rgb]  
     */
    public synchronized float[][][] render(EventPacket packet){
        if(packet==null) return fr;
        this.packet=packet;
        int numEvents = packet.getSize();
      //  System.out.println("packet size: " + numEvents);
       

        int tt;        
        checkFr();
      
        
  //      boolean ignorePolarity=isIgnorePolarityEnabled();
        try{
            for(Object obj:packet){
                TypedEvent e=(TypedEvent)obj;
                
                if (e.getX()==7 && e.getY()==3) {
                    
                    float val;
                    //     System.out.println("FrameStart received " + frameStart+" lastTimestamp "+ lastTimestamp);
                    if (lastTimestamp>frameStart) {
                        lastTimestamp=lastTimestamp-frameStart;
                    }
                    
                    frameStart=e.getTimestamp();
                    
                    //     System.out.println("lts "+lastTimestamp);
                    for(int i=0;i<chip.getSizeY();i++) {
                        for (int j=0;j<5;j++) {
                            //        System.out.println("nextframe "+ i + " " + j + " :"+nextframe[i][j]);
                            val=1-(float)nextframe[i][j][0]/(float)lastTimestamp;
                            //System.out.println("val: "+ val);
                            fr[3-i][j][0]=val;//(0.4f * val) + (0.6f * (float)nextframe[i][j][1]/(float)256);
                            fr[3-i][j][1]=val;
                            fr[3-i][j][2]=val; //(float)  (0.4 * val + 0.6* (float)nextframe[i][j][1]/(float)256); 
                            
                            fr[3-i][j+6][0]=(float)nextframe[i][j][1]/(float)128;
                            fr[3-i][j+6][1]=(float)nextframe[i][j][1]/(float)128;
                            fr[3-i][j+6][2]=(float)nextframe[i][j][1]/(float)128;
                        }
                    }  
                } else {
                    tt = e.getTimestamp();
                    
                    nextframe[e.y][e.x][0] = tt-frameStart;
                    nextframe[e.y][e.x][1] = e.type+128;
                    
                   // log.info("type "  +e.type);
                    
                    lastTimestamp=tt;
                    
                    if (e.type+128 > typeMax)
                    {
                        typeMax=e.type +128;
                        log.info("TypeMax = " + typeMax);
                    }
                    if (e.type +128 < typeMin)
                    {
                        typeMin=e.type +128;
                        log.info("TypeMin = " + typeMin);
                    }
                   //  System.out.println("x " + e.x + " y " + e.y + " type" + (e.type+128) );
                    
                }
            }
            
        }catch (ArrayIndexOutOfBoundsException e){
            e.printStackTrace();
            log.warning(e.getCause()+": ChipRenderer.render(), some event out of bounds for this chip type?");
        }
        return fr;  
    }
}
