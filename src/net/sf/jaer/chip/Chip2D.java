package net.sf.jaer.chip;

import java.lang.reflect.Constructor;
import java.util.prefs.Preferences;

import net.sf.jaer.eventprocessing.FilterFrame;
import net.sf.jaer.graphics.Chip2DRenderer;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.ChipRendererDisplayMethod;
import net.sf.jaer.graphics.DisplayMethod;

/**
 * A Chip with a 2D (or 1D) array of pixels.
 *
 * @author tobi
 */
public class Chip2D extends Chip {

    /**
     * Argument to notifyObservers. EVENT_SIZE_SET is fired to
     * PropertyChangeObservers when a finite Chip2D size is set. Other events
     * fire events to Observers
     */
    public static final String EVENT_SIZEX = "sizeX", EVENT_SIZEY = "sizeY", EVENT_NUM_CELL_TYPES = "numCellTypes", EVENT_SIZE_SET = "EVENT_SIZE_SET";

    /**
     * Creates a new instance of Chip2D
     */
    public Chip2D() {
        super();
        setRenderer(new Chip2DRenderer(this));
//        setCanvas(new ChipCanvas(this)); // subclass must do this now
    }

    /**
     * Horizontal dimension of pixel array. Each pixel may have multiple cell
     * types.
     */
    protected int sizeX = 0;

    /**
     * Vertical dimension of pixel array. Each pixel may have multiple cell
     * types.
     */
    protected int sizeY = 0;

    /**
     * Number of cell types per pixel.
     */
    protected int numCellTypes = 0;
    protected ChipCanvas canvas = null;
    private Chip2DRenderer renderer = null;
    /**
     * the filter frame holding filters that can be applied to the events
     */
    protected FilterFrame filterFrame = null;

    /**
     * Size of chip in x (horizontal) direction.
     *
     * @return number of pixels.
     */
    public int getSizeX() {
        return sizeX;
    }

    /**
     * Updates the chip size and calls Observers with the string EVENT_SIZEX.
     *
     * @param sizeX the horizontal dimension
     */
    public void setSizeX(int sizeX) {
        int oldsize = this.sizeX * this.sizeY * this.numCellTypes;
        this.sizeX = sizeX;
        setChanged();
        notifyObservers(EVENT_SIZEX);
        int newsize = sizeX * sizeY * numCellTypes;
        if (newsize > 0) {
            getSupport().firePropertyChange(EVENT_SIZE_SET, oldsize, newsize);
        }
    }

    /**
     * Size of chip in y (vertical) direction.
     *
     * @return number of pixels.
     */
    public int getSizeY() {
        return sizeY;
    }

    /**
     * Updates the chip size and calls Observers with the string EVENT_SIZEY.
     *
     * @param sizeY the vertical dimension
     */
    public void setSizeY(int sizeY) {
        int oldsize = this.sizeX * this.sizeY * this.numCellTypes;
        this.sizeY = sizeY;
        setChanged();
        notifyObservers(EVENT_SIZEY);
        int newsize = sizeX * sizeY * numCellTypes;
        if (newsize > 0) {
            getSupport().firePropertyChange(EVENT_SIZE_SET, oldsize, newsize);
        }
    }

    public int getMaxSize() {
        return (int) Math.max(sizeX, sizeY);
    }

    public int getMinSize() {
        return (int) Math.min(sizeX, sizeY);
    }

    /**
     * Total number of cells on the chip; sizeX*sizeY*numCellTypes.
     *
     * @return number of cells.
     * @see #getNumPixels
     */
    public int getNumCells() {
        return sizeX * sizeY * numCellTypes;
    }

    /**
     * Number of pixels; sizeX*sizeY
     *
     * @return number of pixels.
     * @see #getNumCells
     */
    public int getNumPixels() {
        return sizeX * sizeY;
    }

    /**
     * The ChipCanvas that renders this Chip2D's output.
     *
     * @return the ChipCanvas.
     */
    public ChipCanvas getCanvas() {
        return canvas;
    }

    /**
     * sets the ChipCanvas for this AEChip. Notifies observers (e.g.
     * EventFilter2D) of this chip with the new ChipCanvas object in case they
     * need to do anything in response, e.g. add FrameAnnotater.
     */
    public void setCanvas(ChipCanvas canvas) {
        this.canvas = canvas;
        setChanged();
        notifyObservers(canvas);
    }

    /**
     * Sets the name of the chip and sets the FilterFrame (if there is one) with
     * a new title
     */
    public void setName(String name) {
        super.setName(name);
        if (filterFrame != null) {
            filterFrame.setTitle(getName() + " filters");
        }
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
    private float pixelWidthUm = 10;
    private float pixelHeightUm = 10;

    public Chip2DRenderer getRenderer() {
        return renderer;
    }

    public void setRenderer(Chip2DRenderer renderer) {
        this.renderer = renderer;
    }

    /**
     * This string key is where the chip's preferred display method is stored.
     *
     * @return the key
     */
    private String preferredDisplayMethodKey() { // TODO shouldn't need this public method, should put display method inside chip not ChipCanvas maybe
        String s = getClass() + ".preferredDisplayMethod";
        if (s.length() > Preferences.MAX_KEY_LENGTH) {
            s = s.substring(s.length() - Preferences.MAX_KEY_LENGTH, s.length());
        }
        return s;
    }

    /**
     * Sets the preferrred DisplayMethod for this Chip2D. This method is the one
     * intially used after startup.
     *
     * @param clazz the method.
     */
    public void setPreferredDisplayMethod(Class<? extends DisplayMethod> clazz) {
        if (clazz == null) {
            log.warning("null class name, not storing preference");
            return;
        }
        // store the preferred method
        getPrefs().put(preferredDisplayMethodKey(), clazz.getName());
        log.info("set preferred diplay method to be " + clazz.getName());
        if (getCanvas() != null) {
            getCanvas().setDisplayMethod(clazz.getName());
        }
    }

    /**
     * Returns the preferred DisplayMethod, or ChipRendererDisplayMethod if null
     * preference.
     *
     * @return the method, or null.
     * @see #setPreferredDisplayMethod
     */
    public DisplayMethod getPreferredDisplayMethod() {
        String className = getPrefs().get(preferredDisplayMethodKey(), null);
        if (className == null) {
            return new ChipRendererDisplayMethod(getCanvas());
        }
        try {
            Class clazz = Class.forName(className);
            Constructor constructor = clazz.getConstructor(ChipCanvas.class);
            Object[] args = {getCanvas()};
            DisplayMethod method = (DisplayMethod) constructor.newInstance(args);
            return method;
        } catch (Throwable e) {
            log.warning(e.toString() + ": couldn't construct preferred display method \"" + className + "\", returning ChipRendererDisplayMethod");
            return new ChipRendererDisplayMethod(getCanvas());
        }

    }
}
