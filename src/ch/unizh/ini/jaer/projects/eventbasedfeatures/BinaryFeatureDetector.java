/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.eventbasedfeatures;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import java.awt.BorderLayout;
import java.awt.Dimension;

import javax.swing.JFrame;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.BackgroundActivityFilter;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.ImageDisplay;

/**
 *
 * @author Varad
 */
public class BinaryFeatureDetector extends EventFilter2D implements FrameAnnotater{

	public boolean implementFeatureDetection = getPrefs().getBoolean("BinaryFeatureDetector.implementFeatureDetection", true);    //If this flag is set, the intensity map convolved with the selected blurring kernel is displayed
	public double threshold = getPrefs().getDouble("BinaryFeatureDetector.threshold", 0.3);

	public int sizex;
	public int sizey;

	public FilterChain filterchain;
	public PixelBuffer pixelbuffer;

	ImageDisplay display;
	JFrame featureFrame;

	public ConvolutionKernelMethod kernel;
	public BinaryScheme binaryMethod;


	public enum BinaryMethod{
		FAST, FASTer
	};

	public BinaryMethod method = BinaryMethod.valueOf(getPrefs().get("BinaryFeatureDetector.BinaryMethod", "FAST"));

	public BinaryFeatureDetector (AEChip chip) {

		super(chip);
		this.chip = chip;

		pixelbuffer = new PixelBuffer(chip);
		filterchain = new FilterChain(chip);

		sizex = chip.getSizeX();
		sizey = chip.getSizeY();

		display = ImageDisplay.createOpenGLCanvas(); // makde a new ImageDisplay GLCanvas with default OpenGL capabilities
		featureFrame = new JFrame("ImageFrameth");  // make a JFrame to hold it
		featureFrame.setPreferredSize(new Dimension(sizex, sizey));  // set the window size
		featureFrame.getContentPane().add(display, BorderLayout.CENTER); // add the GLCanvas to the center of the window
		featureFrame.pack(); // otherwise it wont fill up the display



		filterchain.add(new BackgroundActivityFilter(chip));
		filterchain.add( pixelbuffer );
		pixelbuffer.setBinaryFeatureDetector(this);
		initFilter();
		setEnclosedFilterChain(filterchain);    //creates a filter chain instance and passes this object to the kernel
		//method for all processing
		//        initFilter();
		pixelbuffer.setEnclosed(true, this);

		//        initFilter();


	}

	synchronized public boolean isimplementFeatureDetectionEnabled(){
		return implementFeatureDetection;
	}

	synchronized public void setimplementFeatureDetectionEnabled( boolean implementFeatureDetection ) {

		this.implementFeatureDetection = implementFeatureDetection;
		getPrefs().putBoolean("BinaryFeatureDetector.implementFeatureDetection", implementFeatureDetection);
		getSupport().firePropertyChange("implementFeatureDetection", this.implementFeatureDetection, implementFeatureDetection);
		resetFilter();
	}

	synchronized public double getThreshold (){
		return threshold;
	}

	synchronized public void setThreshold( double threshold){
		this.threshold = threshold;
		getPrefs().putDouble("BinaryFeatureDetector.threshold", threshold);
		getSupport().firePropertyChange("threshold", this.threshold, threshold);
		resetFilter();
	}

	public BinaryMethod getBinaryMethod() {
		return method;
	}

	synchronized public void setBinaryMethod(BinaryMethod method) {
		getSupport().firePropertyChange("method", this.method, method);
		getPrefs().put("BinaryFeatureDetector.BinaryMethod", method.toString());
		this.method = method;
		resetFilter();
	}

	@Override
	public EventPacket filterPacket(EventPacket in) {

		if(!isFilterEnabled() || (getEnclosedFilterChain()== null)) {
			return in;
		}

		sizex = chip.getSizeX();
		sizey = chip.getSizeY();

		if(isimplementFeatureDetectionEnabled() && isFilterEnabled()){

			getEnclosedFilterChain().filterPacket(in);
		}
		//        pixelbuffer.bindetect.binaryMethod.keypointlist
		//        getKeyoints
		return in;
	}


	@Override
	public void annotate(GLAutoDrawable drawable) {

		if(!isFilterEnabled()) {
			return;
		}
		GL2 gl=drawable.getGL().getGL2();

		if (gl == null) {
			return;
		}
		gl.glColor3f(0,1,0);

		gl.glPointSize(4f);
		try{
			gl.glBegin(GL.GL_POINTS);
			{
				for(int i = 0; i < binaryMethod.keypointlist.size(); i++){
					gl.glVertex2i(binaryMethod.keypointlist.get(i).x, binaryMethod.keypointlist.get(i).y);

				}
			}
		} finally{
			gl.glEnd();
		}
	}

	@Override
	public void resetFilter() {

		if(!isFilterEnabled()) {
			return;
		}

		binaryMethod.resetFilter();
		filterchain.reset();
		initFilter();
	}

	@Override
	public void initFilter() {

		kernel = new GaussianBlurKernel(chip, this);

		switch(method){

			case FASTer:
				binaryMethod = new FAST(chip, this);
				break;

			case FAST:
			default:
				binaryMethod = new FAST(chip, this);
				break;
		}
	}
}
