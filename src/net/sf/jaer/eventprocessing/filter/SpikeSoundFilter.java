/*
 * SpikeSoundFilter.java
 *
 * Created on January 29, 2006, 4:25 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package net.sf.jaer.eventprocessing.filter;

import java.awt.Color;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.util.SpikeSound;

/**
 *
 * @author tobi
 */
public class SpikeSoundFilter extends EventFilter2D {
    protected SpikeSound spikeSound;
    public class SelectedCell{
        boolean selectionEnabled=false;
        int x,y,type;
        boolean playAnyTypeEnabled=true;
        boolean renderToGraphicsEnabled=true;
    }
    private SelectedCell cell=new SelectedCell();
    private Color selectedPixelColor=Color.blue;
    
    /** Creates a new instance of SpikeSoundFilter */
    public SpikeSoundFilter(AEChip chip) {
        super(chip);
        spikeSound=new SpikeSound();
    }
    
    private int selectedPixelSpikeCount=0;
    
    void playSpike(int type){
        spikeSound.play(type);
        selectedPixelSpikeCount++;
    }
    
//    /**
//     * filters in to out. Always returns the input events.
//     *@param in input events can be null or empty.
//     *@return the input events.
//     */
//    synchronized public AEPacket2D filter(AEPacket2D in) {
//        if(in==null) return null;
//        AEPacket2D out=in;
//        if(!filterEnabled) return in;
//        if(enclosedFilter!=null) in=enclosedFilter.filter(in);
//        int i;
//        if(!cell.selectionEnabled) return in;
//        int n=in.getNumEvents();
//        if(n==0) return in;
//        
//        short[] xs=in.getXs(), ys=in.getYs();
//        int[] timestamps=in.getTimestamps();
//        byte[] types=in.getTypes();
//        
//        selectedPixelSpikeCount=0;
//        // for each event only write it to the tmp buffers if it matches
//        if(!cell.playAnyTypeEnabled){
//            for(i=0;i<n;i++){
//                if((xs[i]==cell.x) && (ys[i]==cell.y) && (types[i]==cell.type)){
//                    playSpike(types[i]);
//                }
//            }
//        }else{ // play any type
//            for(i=0;i<n;i++){
//                if((xs[i]==cell.x) && (ys[i]==cell.y) ){
//                    playSpike(types[i]);
//                }
//            }
//            
//        }
//        if(cell.renderToGraphicsEnabled){
//            Graphics2D g=(Graphics2D)chip.getCanvas().getCanvas().getGraphics();
//            g.setColor(selectedPixelColor);
//            int radius=1+selectedPixelSpikeCount;
//            g.setStroke(new BasicStroke(.2f));
//            int xr=cell.x-radius, yr=chip.getSizeY()-(cell.y+radius);
//            g.drawOval(xr, yr, 2*radius, 2*radius);
//        }
//        return in;
//    }
    
    public void resetFilter(){}
    public Object getFilterState(){return null;}
    
    public SelectedCell getCell() {
        return cell;
    }

    public void initFilter() {
    }

    public EventPacket filterPacket(EventPacket in) {
//        if(in==null) return null;
//        if(!filterEnabled) return in;
//        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
//        int i;
//        if(!cell.selectionEnabled) return in;
//        
//        selectedPixelSpikeCount=0;
//        // for each event only write it to the tmp buffers if it matches
//        if(!cell.playAnyTypeEnabled){
//            for(i=0;i<n;i++){
//                if((xs[i]==cell.x) && (ys[i]==cell.y) && (types[i]==cell.type)){
//                    playSpike(types[i]);
//                }
//            }
//        }else{ // play any type
//            for(i=0;i<n;i++){
//                if((xs[i]==cell.x) && (ys[i]==cell.y) ){
//                    playSpike(types[i]);
//                }
//            }
//            
//        }
//        if(cell.renderToGraphicsEnabled){
//            Graphics2D g=(Graphics2D)chip.getCanvas().getCanvas().getGraphics();
//            g.setColor(selectedPixelColor);
//            int radius=1+selectedPixelSpikeCount;
//            g.setStroke(new BasicStroke(.2f));
//            int xr=cell.x-radius, yr=chip.getSizeY()-(cell.y+radius);
//            g.drawOval(xr, yr, 2*radius, 2*radius);
//        }
        return in;
   }

}
