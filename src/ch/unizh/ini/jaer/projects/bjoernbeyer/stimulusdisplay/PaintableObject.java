
package ch.unizh.ini.jaer.projects.bjoernbeyer.stimulusdisplay;

import ch.unizh.ini.jaer.projects.bjoernbeyer.visualservo.TrackingSuccessEvaluationPoint;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Point2D;
import java.awt.geom.RectangularShape;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.Serializable;
import javax.swing.JPanel;
import javax.swing.Timer;

/** Basic object for stimulus creation. Can take any Shape and can be decorated 
 * with a path. Can be flashing. Paints itself when provided a canvas.
 *
 * @author Bjoern
 */
public class PaintableObject implements ActionListener, Serializable {
    private static final long serialVersionUID = 42L;
    private static int numberObjects;
    
    private final String objectName;
    private final RectangularShape objectShape;
    private volatile JPanel canvas;
    private float origX, origY;
    private float width, height;
    private float stroke;
    private double angle = 0;
    private int halfScreenWidth, halfScreenHeight;
    private int FlashFreqHz = 20;
    private boolean Flash = true;
    private boolean flashEnabled = false;
    private boolean requestPathPaintEnabled = false;
    private boolean pathLoop = false;
    private boolean hasPath = false;
    private boolean hasGradient = false;
    private float[] gradFractions;
    private float[] gradPos;
    private Color[] gradColors;
    
    private float lastPaintedOrigX, lastPaintedOrigY;
    
    private Paint objectColor;
    
    private Timer timer;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private MouseTrajectory objectPath;
    private TrajectoryPlayer player = null;
    
    public static int getNumberObjects() {
        return numberObjects;
    }
    
    private class TrajectoryPlayer extends Thread implements Serializable{
        private boolean cancelMe = false;
        private int playTimes = 1;
        private int millisSleepBetweenRuns = 0;
        
        TrajectoryPlayer(int playTimes, int millisSleep){
            super();
            this.playTimes = playTimes;
            this.millisSleepBetweenRuns = millisSleep;
        }
        
        void cancel() {
            cancelMe = true;
            synchronized (this) { interrupt(); }
        }

        @Override public void run() {
            int currentPlayTime = 0;
            while (!cancelMe) {
                try {
                    for (MouseTrajectoryPoint p : objectPath) {
                        if (cancelMe) break;

                        Thread.sleep(p.getDifferenceTimeMillis());

                        PaintableObject.this.setRelativeXY(p.getX(),p.getY());
                        PaintableObject.this.getCanvas().repaint();
                    }
                    PaintableObject.this.pcs.firePropertyChange("pathPlayedDone",PaintableObject.this.getObjectName(),null);

                    currentPlayTime++;
                    if(currentPlayTime >= this.playTimes) cancel();
                    
                    Thread.sleep(this.millisSleepBetweenRuns);
                } catch (InterruptedException ex) { 
                    break; 
                }
            }
        }
    }

    public PaintableObject(String objectName, RectangularShape objectShape, JPanel canvas) {
        super();
        origX = 0;  origY  = 0;
        width = -1; height = -1;
        this.objectShape = objectShape;
        this.canvas = canvas;
        
        if(objectName.equals("")) {
            this.objectName = "PrintObj"+String.valueOf(numberObjects);
        }else{
            this.objectName = objectName;
        }
        
        objectPath = new MouseTrajectory();
        
        //Initialize paint and shape to avoid painting errors if user forgets to set those.
        objectColor = new Color(0,0,0,0);//totally transparent
        
        setHalfScreenDimensions(canvas.getWidth()/2,canvas.getHeight()/2);
        
        numberObjects++;
    }
    
    public PaintableObject(String objectName, RectangularShape objectShape, JPanel canvas, float width, float height) {
        this(objectName, objectShape, canvas);
        setRelativeWidth(width); setRelativeHeight(height);
    }
    
    public void startFlashing(){
        if(timer!=null)stopFlashing();
        timer = new Timer(1000/FlashFreqHz,this);
        timer.start(); 
        setFlashEnabled(true);
    }
    
    public void stopFlashing() {
        if(timer!=null) {
            timer.stop();
            timer = null;
        }
        Flash = true; // So that object is visible
        setFlashEnabled(false);
    }
        
    @Override public void actionPerformed(ActionEvent e) {
        Flash = !Flash;
        repaintWholeObjectOnCanvas();
    }
    
    public void playPathOnce() {
        if (player != null) player.cancel();
        player = new TrajectoryPlayer(1,0);
        player.start();
    }
    
    public void playPathLoopToggle() {
        if (player != null) player.cancel();
        setPathLoop(!isPathLoop());
        if (isPathLoop()) {
            player = new TrajectoryPlayer(Integer.MAX_VALUE,0); //technically not a real loop, but close enough
            player.start();
        } else {
            player.cancel();
        }
    }
    public void playPathNumberTimes(int numberTimes, int waitTimeMillis) {
        if (player != null) player.cancel();
        player = new TrajectoryPlayer(numberTimes,waitTimeMillis);
        player.start();
    }
    
    public void playPathCancel() {
        if (player != null) player.cancel();
    }

    public void setObjectColor(Color color){
        this.hasGradient = false;
        this.objectColor = color;
    }
    
    protected void updateShapeFrame() {
        this.objectShape.setFrame(getX() , getY(), getWidth(), getHeight());  
    }
    
    /**
     *
     * @param numberCycles
     * @param startX startPoint relative to Object. 0 is left, 1 is right end of object
     * @param startY startPoint relative to Object. 0 is top, 1 is bottom end of object
     * @param endX endPoint relative to Object. 0 is left, 1 is right end of object
     * @param endY endPoint relative to Object. 0 is top, 1 is bottom end of object
     * @param color
     */
    public void setPaintGradient(int numberCycles, float startX, float startY, float endX, float endY, Color color) {
        this.hasGradient = true;
        int samplePoints = numberCycles *20;
        float[] fractions = new float[samplePoints+1];
        Color[] colors = new Color[samplePoints+1];
        
        for(int i=0;i<=samplePoints;i++) {
            fractions[i] = i*(1/(float)samplePoints); 
            double cosArg = Math.PI+((i*numberCycles*2*Math.PI)/samplePoints);
            double rectifiedCos = (Math.cos(cosArg)+1)/2;
            colors[i]    = new Color(color.getRed(),color.getGreen(),color.getBlue(),(int)(255*rectifiedCos));
        }
        this.gradFractions = fractions;
        this.gradColors = colors;
        this.gradPos = new float[] {startX,startY,endX,endY};
    }
    
    public void setPaintStripes(int numberStripes, float startX, float startY, float endX, float endY, Color color1, Color color2) {
        this.hasGradient = true;
        float[] fractions = new float[4*numberStripes];
        Color[] colors = new Color[4*numberStripes];
        
        for(int i=0;i<2*numberStripes;i++) {
            fractions[2*i]   = i    *(1/(float)(2*numberStripes))+(1/(float)(1000*numberStripes)); 
            fractions[2*i+1] = (i+1)*(1/(float)(2*numberStripes))-(1/(float)(1000*numberStripes)); 
            if(i%2 == 0){
                colors[2*i]   = color1;
                colors[2*i+1] = color1;
            }else{
                colors[2*i]   = color2;
                colors[2*i+1] = color2;
            }
        }
        this.gradFractions = fractions;
        this.gradColors = colors;
        this.gradPos = new float[] {startX,startY,endX,endY};
    }
    
    private void repaintWholeObjectOnCanvas() {
        //If the object is currently displaying a path then we dont want to
        // repaint the whole object. Instead the path-player is repainting
        // by itself when necessary and only the specific regions needed.
        // This method instead paints the WHOLE object, even if it is bigger
        // then the canvas.
        if (player != null) return;
        canvas.repaint();
    }
    
    protected Paint getUpdatedPaint() {
        if(hasGradient){
            Point2D.Double startP = new Point2D.Double(getX()+this.gradPos[0]*getWidth(), getY()+this.gradPos[1]*getHeight());
            Point2D.Double endP   = new Point2D.Double(getX()+this.gradPos[2]*getWidth(), getY()+this.gradPos[3]*getHeight());

            LinearGradientPaint gradient = new LinearGradientPaint(startP, endP, this.gradFractions, this.gradColors);

            return gradient;
        } else {
            return objectColor;
        }
    }
    
    protected void firePropertyChangeIfUpdated() {
        if(lastPaintedOrigX != getRelativeX() || lastPaintedOrigY != getRelativeY()) {
            TrackingSuccessEvaluationPoint evaluationPoint = new TrackingSuccessEvaluationPoint(getObjectName(),getRelativeX(),getRelativeY(),System.nanoTime());
            this.pcs.firePropertyChange("visualObjectChange",evaluationPoint,null);
        }
        lastPaintedOrigX = getRelativeX();
        lastPaintedOrigY = getRelativeY();
    }
    
    public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        
        if(!getFlash()) return;
        
        updateShapeFrame();
        g2.setStroke(new BasicStroke(stroke));
        if(stroke == 0){
            g2.setPaint(Color.white); //if the stroke is 0 we dont want to see a hairline but nothing.
        }else{
            g2.setPaint(Color.black);
        }

        g2.rotate(Math.toRadians(getAngle()), getX()+getWidth()/2, getY()+getHeight()/2);
        g2.draw(this.objectShape);
        g2.setPaint(getUpdatedPaint());
        g2.fill(this.objectShape);

        g2.dispose();
        
        firePropertyChangeIfUpdated();
    }
    
    public boolean getFlash() {
        return Flash;
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        this.pcs.addPropertyChangeListener(listener);
    }
    
    public String getObjectName() {
        return objectName;
    }

    public RectangularShape getObjectShape() {
        return objectShape;
    }
    
    public boolean isHasPath() {
        return this.hasPath;
    }

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --RelativeX/Y and getX/Y and setRelativeXY--">
    public float getRelativeX() {
        return origX;
    }
    
    public void setRelativeX(float origX) {
        this.origX = origX;
        repaintWholeObjectOnCanvas();
    }
    
    public int getX() {
        return (int) ((1+getRelativeX())*getHalfScreenWidth()-getWidth()/2);
    }

    public float getRelativeY() {
        return origY;
    }
    
    public void setRelativeY(float origY) {
        this.origY = origY;
        repaintWholeObjectOnCanvas();
    }

    public int getY() {
        return (int) ((1+getRelativeY())*getHalfScreenHeight()-getHeight()/2);
    }

    public void setRelativeXY(float origX, float origY) {
        this.origX = origX;
        this.origY = origY;
        repaintWholeObjectOnCanvas();
    }

    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --relativeWidth-- & getter for Width">
    public float getRelativeWidth() {
        return width;
    }
    
    public int getWidth() {
        return (int) (getRelativeWidth()*2*getHalfScreenWidth()+getStroke());
    }

    public final void setRelativeWidth(float width) {
        this.width = width;
        repaintWholeObjectOnCanvas();
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --relativeHeight-- & getter for Height">
    public float getRelativeHeight() {
        return height;
    }
    
    public int getHeight() {
        return (int) (getRelativeHeight()*2*getHalfScreenHeight()+getStroke());
    }

    public final void setRelativeHeight(float height) {
        this.height = height;
        repaintWholeObjectOnCanvas();
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --HalfScreenWidth and HelfScreenHeight and setHelfScreenDimensions--">
    public int getHalfScreenWidth() {
        return halfScreenWidth;
    }

    public void setHalfScreenWidth(int halfScreenWidth) {
        this.halfScreenWidth = halfScreenWidth;
        repaintWholeObjectOnCanvas();
    }
    
    public int getHalfScreenHeight() {
        return halfScreenHeight;
    }

    public void setHalfScreenHeight(int halfScreenHeight) {
        this.halfScreenHeight = halfScreenHeight;
        repaintWholeObjectOnCanvas();
    }
    
    public final void setHalfScreenDimensions(int halfScreenWidth,int halfScreenHeight) {
        this.halfScreenHeight = halfScreenHeight;
        this.halfScreenWidth = halfScreenWidth;
        repaintWholeObjectOnCanvas();
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --Angle--">
    public double getAngle() {
        return angle;
    }

    public void setAngle(double angle) {
        this.angle = angle;
        repaintWholeObjectOnCanvas();
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --FlashFreqHz--">
    public int getFlashFreqHz() {
        return FlashFreqHz;
    }

    public void setFlashFreqHz(int FlashFreqHz) {
        this.FlashFreqHz = FlashFreqHz;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --Canvas--">
    public JPanel getCanvas() {
        return canvas;
    }
    
    public void setCanavas(JPanel canvas) {
        this.canvas = canvas;
        setHalfScreenDimensions(canvas.getWidth()/2,canvas.getHeight()/2);
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --ObjectPath--">
    public MouseTrajectory getObjectPath() {
        return objectPath;
    }

    public void setObjectPath(MouseTrajectory objectPath) {
        this.objectPath.clear();
        this.objectPath.addAll(objectPath);
        this.hasPath = !objectPath.isEmpty();
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --RequestPathPaintEnabled--">
    public boolean isRequestPathPaintEnabled() {
        return requestPathPaintEnabled;
    }

    public void setRequestPathPaintEnabled(boolean paintPathEnabled) {
        this.requestPathPaintEnabled = paintPathEnabled;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --FlashEnabled--">
    public boolean isFlashEnabled() {
        return flashEnabled;
    }

    public void setFlashEnabled(boolean flashEnabled) {
        this.flashEnabled = flashEnabled;
    }
    // </editor-fold>  
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --PathLoop--">
    public boolean isPathLoop() {
        return pathLoop;
    }

    public void setPathLoop(boolean loopPath) {
        this.pathLoop = loopPath;
    }
    // </editor-fold>   
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --Stroke--">
    public float getStroke() {
        return stroke;
    }

    public void setStroke(float stroke) {
        this.stroke = stroke;
        repaintWholeObjectOnCanvas();
    }
    // </editor-fold>  

}
