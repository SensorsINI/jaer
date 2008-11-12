package ch.unizh.ini.caviar.chip;

import ch.unizh.ini.caviar.eventprocessing.FilterFrame;
import ch.unizh.ini.caviar.graphics.*;
import ch.unizh.ini.caviar.graphics.ChipCanvas;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLContext;

/**
 * A Chip with a 2D (or 1D) array of pixels.
 *
 * @author tobi
 */
public class Chip2D extends Chip {

    /** Creates a new instance of Chip2D */
    public Chip2D() {
        super();
        setRenderer(new Chip2DRenderer(this));
        setCanvas(new ChipCanvas(this));
    }
    
    protected int sizeX=0;
    protected int sizeY=0;
    protected int numCellTypes=0;

    protected ChipCanvas canvas=null;
    private Chip2DRenderer renderer=null;

    /** the filter frame holding filters that can be applied to the events */
    protected FilterFrame filterFrame=null;

    
    public int getSizeX() {
        return sizeX;
    }

    /** updates the chip size and calls Observers 
     @param sizeX the horizontal dimension
     */
    public void setSizeX(int sizeX) {
        this.sizeX = sizeX;
        setChanged();
        notifyObservers("sizeX");
    }

    public int getSizeY() {
        return sizeY;
    }

    /** updates the chip size and calls Observers 
     @param sizeY the vertical dimension
     */
    public void setSizeY(int sizeY) {
        this.sizeY = sizeY;
        setChanged();
        notifyObservers("sizeY");
    }
    
    public int getMaxSize(){
        return (int)Math.max(sizeX,sizeY);
    }
    
    public int getMinSize(){
        return (int)Math.min(sizeX,sizeY);
    }
    
    public int getNumCells(){
        return sizeX*sizeY*numCellTypes;
    }

    public int getNumPixels(){
        return sizeX*sizeY;
    }

    public ChipCanvas getCanvas() {
        return canvas;
    }

    /** sets the ChipCanvas for this AEChip. Notifies observers (e.g. EventFilter2D) of this chip in case they need to do anything in response, e.g.
     add FrameAnnotater.
     */
    public void setCanvas(ChipCanvas canvas) {
        this.canvas = canvas;
        setChanged();
        notifyObservers(canvas);
    }


    
    /** Sets the name of the chip and sets the FilterFrame (if there is one) with a new title */
    public void setName(String name) {
        super.setName(name);
        if(filterFrame!=null) filterFrame.setTitle(getName()+" filters");
    }

//    public FilterChain getRealTimeFilterChain() {
//        return realTimeFilterChain;
//    }
//
//    public void setRealTimeFilterChain(FilterChain realTimeFilterChain) {
//        this.realTimeFilterChain = realTimeFilterChain;
//    }

    public float getPixelWidthUm() {
        return pixelWidthUm;
    }

    public void setPixelWidthUm(float pixelWidthUm) {
        this.pixelWidthUm = pixelWidthUm;
    }

    public float getPixelHeightUm() {
        return pixelHeightUm;
    }

    public void setPixelHeightUm(float pixelHeightUm) {
        this.pixelHeightUm = pixelHeightUm;
    }
    private float pixelWidthUm=10;
    private float pixelHeightUm=10;

    public Chip2DRenderer getRenderer() {
        return renderer;
    }

    public void setRenderer(Chip2DRenderer renderer) {
        this.renderer = renderer;
    }


}
