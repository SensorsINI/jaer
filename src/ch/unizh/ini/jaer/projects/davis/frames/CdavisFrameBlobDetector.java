/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.davis.frames;

import static org.bytedeco.javacpp.opencv_core.CV_8U;
import static org.bytedeco.javacpp.opencv_core.inRange;
import static org.bytedeco.javacpp.opencv_core.split;
import static org.bytedeco.javacpp.opencv_imgproc.CV_RGB2HSV;
import static org.bytedeco.javacpp.opencv_imgproc.GaussianBlur;
import static org.bytedeco.javacpp.opencv_imgproc.cvtColor;

import java.awt.Component;
import java.awt.Container;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.nio.FloatBuffer;
import java.util.LinkedList;

import javax.swing.JButton;

import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_core.KeyPoint;
import org.bytedeco.javacpp.opencv_core.KeyPointVector;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.MatVector;
import org.bytedeco.javacpp.opencv_features2d.SimpleBlobDetector;
import org.bytedeco.javacpp.opencv_highgui;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUquadric;

import eu.seebetter.ini.chips.DavisChip;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Detects blobs in CDAVIS frames using OpenCV SimpleBlobDetector.
 *
 * @author Marc Osswald, Tobi Delbruck, Chenghan Li
 */
@Description("Detects blobs in CDAVIS frames using OpenCV SimpleBlobDetector")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class CdavisFrameBlobDetector extends EventFilter2D implements FrameAnnotater, PropertyChangeListener {

    private int sx;
    private int sy;
    private int lastTimestamp = 0;

    private float[] lastFrame = null, outFrame = null;

    /**
     * Fires property change with this string when new calibration is available
     */
    //encapsulated fields
    private boolean colorBlobDetectionEnabled = getBoolean("colorBlobDetectionEnabled", true);
    private boolean tuningEnabled = getBoolean("tuningEnabled", false);

    private Mat imgIn, imgOut;
    private KeyPointVector blobCenterVector;

    private boolean actionTriggered = false;
    private int nAcqFrames = 0;
    private int nMaxAcqFrames = 1;

//    private final ApsFrameExtractor frameExtractor;
    private final FilterChain filterChain;
    private final RectangularClusterTracker tracker;
    private boolean saved=false;

    private AEFrameChipRenderer renderer=null;
    private boolean rendererPropertyChangeListenerAdded=false;
    DavisChip apsDvsChip = null;
//    RectangularClusterTracker.Cluster blobs = null;
    volatile protected LinkedList<RectangularClusterTracker.Cluster> blobs = new LinkedList<>();

    private GLU glu = new GLU();
    private GLUquadric quad = null;
    private float[] compArray = new float[4];

    private int gaussianBlurKernalRadius = getInt("gaussianBlurKernalRadius", 20);
    private int hueUpperBound = getInt("hueUpperBound", 125);
    private int hueLowerBound = getInt("hueLowerBound", 115);
    private int areaUpperBound = getInt("areaUpperBound", 10000);
    private int areaLowerBound = getInt("areaLowerBound", 100);
//    int velocityMedianFilterLengthSamples = getInt("velocityMedianFilterLengthSamples", 9);
//    MedianLowpassFilter velxfilter = new MedianLowpassFilter(velocityMedianFilterLengthSamples);
//    MedianLowpassFilter velyfilter = new MedianLowpassFilter(velocityMedianFilterLengthSamples);
//    Point2D.Float ballVel = new Point2D.Float();

    public CdavisFrameBlobDetector(AEChip chip) {
        super(chip);
//        frameExtractor = new ApsFrameExtractor(chip);
        filterChain = new FilterChain(chip);
//        filterChain.add(frameExtractor);
        filterChain.add((tracker = new RectangularClusterTracker(chip)));
//        frameExtractor.setExtRender(false);
        setEnclosedFilterChain(filterChain);
        lastFrame = new float[640*480*3];
        resetFilter();

        String General = "General", Tuning = "Tuning";
        setPropertyTooltip(General, "colorBlobDetectionEnabled", "Enable color blob detection in the frame");
        setPropertyTooltip(Tuning, "tuningEnabled", "Enable tuning of the color blob detection");
        setPropertyTooltip(Tuning, "gaussianBlurKernalRadius", "Sets the Gaussian blur kernal radius for the hue image");
        setPropertyTooltip(Tuning, "hueUpperBound", "Sets the upper limit of the blob color to be detected in the hue image");
        setPropertyTooltip(Tuning, "hueLowerBound", "Sets the lower limit of the blob color to be detected in the hue image");
        setPropertyTooltip(Tuning, "areaUpperBound", "Sets the upper limit of the blob area to be detected in the hue image");
        setPropertyTooltip(Tuning, "areaLowerBound", "Sets the lower limit of the blob area to be detected in the hue image");
    }

    /**
     * filters in to out. if filtering is enabled, the number of out may be less
     * than the number putString in
     *
     * @param in input events can be null or empty.
     * @return the processed events, may be fewer in number. filtering may occur
     * in place in the in packet.
     */

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!rendererPropertyChangeListenerAdded){
            rendererPropertyChangeListenerAdded=true;
            renderer=(AEFrameChipRenderer)chip.getRenderer();
            renderer.getSupport().addPropertyChangeListener(this);
        }
        apsDvsChip = (DavisChip) chip;
//        apsFrameExtractor.filterPacket(in);
        getEnclosedFilterChain().filterPacket(in);
        return in;
    }

    @Override
    synchronized public void propertyChange(PropertyChangeEvent evt) {
        if ((evt.getPropertyName() == AEFrameChipRenderer.EVENT_NEW_FRAME_AVAILBLE)
                && !chip.getAeViewer().isPaused()) {
            FloatBuffer lastFrameBuffer = ((AEFrameChipRenderer)chip.getRenderer()).getPixmap();
            //int sx=chip.getSizeX(), sy=chip.getSizeY();
            AEFrameChipRenderer r=((AEFrameChipRenderer)chip.getRenderer());
            // rewrite frame to avoid padding for texture
            for (int x = 0; x < sx; x++) {
                for (int y = 0; y < sy; y++) {
                    int i=r.getPixMapIndex(x, y),j=(x*3) + ((sy-1-y)*sx*3);
                    lastFrame[j] = lastFrameBuffer.get(i + 2);
                    lastFrame[j+ 1] = lastFrameBuffer.get(i + 1);
                    lastFrame[j+ 2] = lastFrameBuffer.get(i);
                }
            }

                //process frame
                if (colorBlobDetectionEnabled) {
                    findColorBlobs(false);
                    trackBlobs();
                }
        }
    }

    public void findColorBlobs(boolean drawAndSave) {
        FloatPointer ip = new FloatPointer(lastFrame);
        Mat input = new Mat(ip);
        input.convertTo(input, CV_8U, 255, 0);
        imgIn = input.reshape(3, sy);
        Mat imgHsv = new Mat(ip);
        cvtColor(imgIn, imgHsv, CV_RGB2HSV);
//        log.info(printMatD(imgHsv));

        MatVector hsvChannels = new MatVector();

        split( imgHsv, hsvChannels );
//        log.info(printMatD(hsvChannels.get(1)));
        Mat lowerBound = new Mat(480, 640, CV_8U, new opencv_core.Scalar((float) hueLowerBound));
//        log.info(printMatD(lowerBound));
        Mat upperBound = new Mat(480, 640, CV_8U, new opencv_core.Scalar((float) hueUpperBound));
//        log.info(printMatD(upperBound));
        Mat hueBlurMat = new Mat(480, 640, CV_8U, new opencv_core.Scalar(0.0));
        Mat hueBinMat = new Mat(480, 640, CV_8U, new opencv_core.Scalar(0.0));
//        log.info(printMatD(hueBinMat));

        int kernalSize = (2*gaussianBlurKernalRadius) + 1;
        GaussianBlur(hsvChannels.get(0), hueBlurMat, new opencv_core.Size(kernalSize,kernalSize), 1.5);
        inRange(hsvChannels.get(0),lowerBound,upperBound,hueBinMat);
//        log.info(printMatD(hueBinMat));

        SimpleBlobDetector blobDetector = SimpleBlobDetector.create(new SimpleBlobDetector.Params()
                .filterByArea(true)
                .minArea(areaLowerBound)
                .maxArea(areaUpperBound)

                .filterByColor(true)
                .blobColor((byte) 255)

                .filterByCircularity(false)
//                .minCircularity(circularity.get(0).floatValue())
//                .maxCircularity(circularity.get(1).floatValue())

                .filterByConvexity(false)

                .filterByInertia(false)

        );
        blobCenterVector = new KeyPointVector();
        blobDetector.detect(hueBinMat, blobCenterVector);

        if (tuningEnabled) {
            opencv_highgui.imshow("Input", imgIn);
            opencv_highgui.imshow("Hue", hsvChannels.get(0));

            opencv_highgui.imshow("threshold Hue", hueBinMat);

            opencv_highgui.waitKey(1000);
        }
     }

    public void trackBlobs () {
        if (colorBlobDetectionEnabled && (blobCenterVector != null)) {
            for (int i = 0; i < blobCenterVector.size(); i++) {
                KeyPoint blobCenter = blobCenterVector.get(i);
                float size = blobCenter.size();
                if (size > 5) {
                    RectangularClusterTracker.Cluster b = tracker.createCluster
                    (new BasicEvent(lastTimestamp, (short) blobCenter.pt().x(), (short) (480-blobCenter.pt().y())));
                    b.setMass(10000); // some big number
                    tracker.getClusters().add(b);
                    blobs.add(b);
                }
            }
        }
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {

        GL2 gl = drawable.getGL().getGL2();

        if (colorBlobDetectionEnabled && (blobCenterVector != null)) {
            //draw lines
            gl.glLineWidth(2f);
            gl.glColor3f(0, 0, 1);
            //log.info("width="+w+" height="+h);
//            gl.glBegin(GL.GL_LINES);
            for (int i = 0; i < blobCenterVector.size(); i++) {
                KeyPoint blobCenter = blobCenterVector.get(i);
                float size = blobCenter.size();
                if (size > 5) {
                    float x0 = blobCenter.pt().x()-(size/2);
                    float y0 = blobCenter.pt().y()-(size/2);
                    float x1 = blobCenter.pt().x()+(size/2);
                    float y1 = blobCenter.pt().y()-(size/2);
                    float x2 = blobCenter.pt().x()+(size/2);
                    float y2 = blobCenter.pt().y()+(size/2);
                    float x3 = blobCenter.pt().x()-(size/2);
                    float y3 = blobCenter.pt().y()+(size/2);
                    gl.glBegin(GL.GL_LINES);
                    gl.glVertex2f(x0, 480-y0);
                    gl.glVertex2f(x1, 480-y1);
                    gl.glEnd();
                    gl.glBegin(GL.GL_LINES);
                    gl.glVertex2f(x1, 480-y1);
                    gl.glVertex2f(x2, 480-y2);
                    gl.glEnd();
                    gl.glBegin(GL.GL_LINES);
                    gl.glVertex2f(x2, 480-y2);
                     gl.glVertex2f(x3, 480-y3);
                    gl.glEnd();
                    gl.glBegin(GL.GL_LINES);
                    gl.glVertex2f(x3, 480-y3);
                    gl.glVertex2f(x0, 480-y0);
                    gl.glEnd();
                }
            }
        }
        tracker.annotate(drawable);
    }

    @Override
    public synchronized final void resetFilter() {
        initFilter();
        filterChain.reset();
    }

    @Override
    public final void initFilter() {
        sx = chip.getSizeX();
        sy = chip.getSizeY();
    }

    /**
     * @return the colorBlobDetectionEnabled
     */
    public boolean isColorBlobDetectionEnabled() {
        return colorBlobDetectionEnabled;
    }

    /**
     * @param colorBlobDetectionEnabled the
     * colorBlobDetectionEnabled to set
     */
    public void setColorBlobDetectionEnabled(boolean colorBlobDetectionEnabled) {
        this.colorBlobDetectionEnabled = colorBlobDetectionEnabled;
    }


    /**
     * @return the tuningDetectionEnabled
     */
    public boolean isTuningEnabled() {
        return tuningEnabled;
    }

    /**
     * @param tuningEnabled the
     * tuningEnabled to set
     */
    public void setTuningEnabled(boolean tuningEnabled) {
        this.tuningEnabled = tuningEnabled;
    }

    static void setButtonState(Container c, String buttonString,boolean flag ) {
    int len = c.getComponentCount();
    for (int i = 0; i < len; i++) {
      Component comp = c.getComponent(i);

      if (comp instanceof JButton) {
        JButton b = (JButton) comp;

        if ( buttonString.equals(b.getText()) ) {
            b.setEnabled(flag);
        }

      } else if (comp instanceof Container) {
          setButtonState((Container) comp, buttonString, flag);
      }
    }
    }

    private String printMatD(Mat M) {
        StringBuilder sb = new StringBuilder();
        int c = 0;
        for (int i = 0; i < M.rows(); i++) {
            for (int j = 0; j < M.cols(); j++) {
                sb.append(String.format("%d ", (M.getByteBuffer().get(c) & 0xFF)));
                c++;
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /*user configure gaussianBlurKernalRadius*/
    public final int getGaussianBlurKernalRadius() {
        return gaussianBlurKernalRadius;
    }

    public void setGaussianBlurKernalRadius(final int gaussianBlurKernalRadius) {
        this.gaussianBlurKernalRadius = gaussianBlurKernalRadius;
        putInt("gaussianBlurKernalRadius", gaussianBlurKernalRadius);
    }

    public int getMinGaussianBlurKernalRadius() {
        return 0;
    }

    public int getMaxGaussianBlurKernalRadius() {
	return 10000;
    }


    /*user configure hueUpperBound*/
    public final int getHueUpperBound() {
        return hueUpperBound;
    }

    public void setHueUpperBound(final int hueUpperBound) {
        this.hueUpperBound = hueUpperBound;
        putInt("hueUpperBound", hueUpperBound);
    }

    public int getMinHueUpperBound() {
        return 0;
    }

    public int getMaxHueUpperBound() {
	return 255;
    }


    /*user configure hueLowerBound*/
    public final int getHueLowerBound() {
        return hueLowerBound;
    }

    public void setHueLowerBound(final int hueLowerBound) {
        this.hueLowerBound = hueLowerBound;
        putInt("hueLowerBound", hueLowerBound);
    }

    public int getMinHueLowerBound() {
        return 0;
    }

    public int getMaxHueLowerBound() {
	return 255;
    }

    /*user configure areaUpperBound*/
    public final int getAreaUpperBound() {
        return areaUpperBound;
    }

    public void setAreaUpperBound(final int areaUpperBound) {
        this.areaUpperBound = areaUpperBound;
        putInt("areaUpperBound", areaUpperBound);
    }

    public int getMinAreaUpperBound() {
        return 1;
    }

    public int getMaxAreaUpperBound() {
	return 640*480;
    }

    /*user configure areaLowerBound*/
    public final int getAreaLowerBound() {
        return areaLowerBound;
    }

    public void setAreaLowerBound(final int areaLowerBound) {
        this.areaLowerBound = areaLowerBound;
        putInt("areaLowerBound", areaLowerBound);
    }

    public int getMinAreaLowerBound() {
        return 1;
    }

    public int getMaxAreaLowerBound() {
	return 640*480;
    }
}
