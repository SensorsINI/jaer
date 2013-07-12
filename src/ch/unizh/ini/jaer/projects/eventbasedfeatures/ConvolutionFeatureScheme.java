/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.eventbasedfeatures;


import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.swing.JFrame;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.BackgroundActivityFilter;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.ImageDisplay;
import ch.unizh.ini.jaer.projects.eventbasedfeatures.FeatureMethod.KeyPoint;

/** A filter enabling testing of convolution-based feature detection scheme and binary comparison-based
 * detection on an intensity map of the events created by maintaining a ring buffer at each pixel.
 * 
 * (Refer to PixelBuffer class for further details )
 * 
 * @author Varad
 */


public class ConvolutionFeatureScheme extends EventFilter2D implements FrameAnnotater{
	public boolean implementFeatureDetection = getPrefs().getBoolean("ConvolutionFeatureScheme.implementFeatureDetection", false);    //If this flag is set, the intensity map convolved with the selected blurring kernel is displayed
	public boolean implementFeatureDescription = getPrefs().getBoolean("ConvolutionFeatureScheme.implementFeatureDescription", false);
	public double RelativeThreshold = getPrefs().getDouble("ConvolutionFeatureScheme.RelativeThreshold", 0.5);

	public FilterChain filterchain;
	public PixelBuffer pixelbuffer;

	public int sizex;
	public int sizey;

	ImageDisplay display;
	JFrame featureFrame;
	String strFilePath;

	public ConvolutionKernelMethod detector;
	public DescriptorScheme descriptor;

	public enum DetectionKernel {
		LaplacianOfGaussian    //list of available blurring kernels
	};

	public enum DescriptorMethod {
		CROSS, CROSSTwo, SQUARE, SQUARETwo, SQUARECorner, SQUARECornerTwo, OCTAGON,
	};

	public DetectionKernel kernel = DetectionKernel.valueOf(getPrefs().get("ConvolutionFeatureScheme.DetectionKernel", "LaplacianOfGaussian"));
	public DescriptorMethod descheme = DescriptorMethod.valueOf(getPrefs().get("ConvolutionFeatureScheme.DescriptorMethod", "CROSS"));

	public ConvolutionFeatureScheme (AEChip chip) {
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

		initFilter();

		filterchain.add(new BackgroundActivityFilter(chip));
		filterchain.add( pixelbuffer );
		pixelbuffer.setConvolutionFeatureScheme(this);
		setEnclosedFilterChain(filterchain);    //creates a filter chain instance and passes this object to the kernel
		//method for all processing
		pixelbuffer.setEnclosed(true, this);

		strFilePath = "C:/Users/Varad/Downloads/descriptors.txt";
	}

	synchronized public boolean isimplementFeatureDetectionEnabled(){
		return implementFeatureDetection;
	}

	synchronized public void setimplementFeatureDetectionEnabled( boolean implementFeatureDetection ) {
		this.implementFeatureDetection = implementFeatureDetection;
		getPrefs().putBoolean("ConvolutionFeatureScheme.implementFeatureDetection", implementFeatureDetection);
		getSupport().firePropertyChange("implementFeatureDetection", this.implementFeatureDetection, implementFeatureDetection);
		resetFilter();
	}

	synchronized public boolean isimplementFeatureDescriptionEnabled(){
		return implementFeatureDescription;
	}

	synchronized public void setimplementFeatureDescriptionEnabled( boolean implementFeatureDescription ) {
		this.implementFeatureDescription = implementFeatureDescription;
		getPrefs().putBoolean("ConvolutionFeatureScheme.implementFeatureDescription", implementFeatureDescription);
		getSupport().firePropertyChange("implementFeatureDescription", this.implementFeatureDescription, implementFeatureDescription);
		resetFilter();
	}

	synchronized public double getRelativeThreshold (){
		return RelativeThreshold;
	}

	synchronized public void setRelativeThreshold( double RelativeThreshold){
		this.RelativeThreshold = RelativeThreshold;
		getPrefs().putDouble("ConvolutionFeatureScheme.RelativeThreshold", RelativeThreshold);
		getSupport().firePropertyChange("RelativeThreshold", this.RelativeThreshold, RelativeThreshold);
		resetFilter();
	}

	public DetectionKernel getDetectionKernel() {
		return kernel;
	}

	synchronized public void setDetectionKernel(DetectionKernel kernel) {
		getSupport().firePropertyChange("kernel", this.kernel, kernel);
		getPrefs().put("ConvolutionFeatureScheme.DetectionKernel", kernel.toString());
		this.kernel = kernel;
		resetFilter();
	}

	public DescriptorMethod getDescriptorMethod() {
		return descheme;
	}

	synchronized public void setDescriptorMethod(DescriptorMethod descheme) {
		getSupport().firePropertyChange("descheme", this.descheme, descheme);
		getPrefs().put("ConvolutionFeatureScheme.DescriptorMethod", descheme.toString());
		this.descheme = descheme;
		resetFilter();
	}

	@Override
	synchronized public EventPacket filterPacket(EventPacket in) {

		if(!isFilterEnabled() || (getEnclosedFilterChain()== null)) {
			return in;
		}
		sizex = chip.getSizeX();
		sizey = chip.getSizeY();

		if(isimplementFeatureDetectionEnabled() && isFilterEnabled()){
			getEnclosedFilterChain().filterPacket(in);
			if(isimplementFeatureDescriptionEnabled()){
				assignDescriptors(detector.keypoints, in);
			}
		}
		return in;
	}


	synchronized public void assignDescriptors(ArrayList<KeyPoint> keypoints, EventPacket in){
		try
		{
			//create FileOutputStream object
			FileOutputStream fos = new FileOutputStream(strFilePath);
			PrintStream  ps = new PrintStream (fos);

			for(KeyPoint p : keypoints){     //each keypoint needs to be constructed from scratch (10 operations)
				// or just needs to be updated (4 operations per event)
				if(!p.hasDescriptor){
					switch(descheme){
						case CROSS:{
							p.desc = new CROSS(chip, this);
							p.hasDescriptor = true;
							p.descriptorString = new boolean[10];
							//                        if(!p.isOnMap){
								p.desc.constructKeyPointDescriptor(p);
								//                            p.isOnMap = true;
								//                        }
								ps.print(p.x+"\t");
								ps.print(p.y+"\t");
								for(int i = 0; i < 10; i++){
									ps.print(p.descriptorString[i]+"\t");
								}
								ps.print("\n");
								break;
						}
						case CROSSTwo:{
							p.desc = new CROSSTwo(chip, this);
							p.hasDescriptor = true;
							p.descriptorString = new boolean[36];
							//                        if(!p.isOnMap){
							p.desc.constructKeyPointDescriptor(p);
							//                            p.isOnMap = true;
							//                        }
							break;
						}
						case SQUARE:{
							p.desc = new SQUARE(chip, this);
							p.hasDescriptor = true;
							p.descriptorString = new boolean[10];
							//                        if(!p.isOnMap){
							p.desc.constructKeyPointDescriptor(p);
							//                            p.isOnMap = true;
							break;
						}
						case SQUARETwo:{
							p.desc = new SQUARETwo(chip, this);
							p.hasDescriptor = true;
							p.descriptorString = new boolean[300];
							//                        if(!p.isOnMap){
							p.desc.constructKeyPointDescriptor(p);
						}
						case SQUARECorner:{
							p.desc = new SQUARECorner(chip, this);
							p.hasDescriptor = true;
							p.descriptorString = new boolean[10];
							//                        if(!p.isOnMap){
							p.desc.constructKeyPointDescriptor(p);
							//                            p.isOnMap = true;
							//                        }
							break;
						}
						case SQUARECornerTwo:{
							p.desc = new SQUARECornerTwo(chip, this);
							p.hasDescriptor = true;
							p.descriptorString = new boolean[36];
							//                        if(!p.isOnMap){
							p.desc.constructKeyPointDescriptor(p);
							//                            p.isOnMap = true;
							//                        }
							break;
						}
						case OCTAGON:{
							p.desc = new OCTAGON(chip, this);
							p.hasDescriptor = true;
							p.descriptorString = new boolean[78];
							//                        if(!p.isOnMap){
							p.desc.constructKeyPointDescriptor(p);
							//                            p.isOnMap = true;
							//                        }
							break;
						}
					}

				}
				//                else{
				//                    p.desc.updateDescriptorMap(p, in);
				//                }
			}
			ps.close();
			fos.close();
		}
		catch(IOException E){
			System.out.println("IOException : " + E);
		}
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
				for(int i = 0; i < detector.keypoints.size(); i++){
					gl.glVertex2i(detector.keypoints.get(i).x, detector.keypoints.get(i).y);
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
		detector.resetFilter();
		filterchain.reset();
		initFilter();
	}

	@Override
	public void initFilter() {
		switch(kernel){
			case LaplacianOfGaussian:{
				detector = new LaplacianOfGaussianKernel(chip, this);
				break;
			}
		}

		switch(descheme){
			case CROSS:{
				descriptor = new CROSS(chip, this);
				break;
			}
			case CROSSTwo:{
				descriptor = new CROSSTwo(chip, this);
				break;
			}
			case SQUARE:{
				descriptor = new SQUARE(chip, this);
				break;
			}
			case SQUARETwo:{
				descriptor = new SQUARETwo(chip, this);
				break;
			}
			case SQUARECorner:{
				descriptor = new SQUARECorner(chip, this);
				break;
			}
			case SQUARECornerTwo:{
				descriptor = new SQUARECornerTwo(chip, this);
				break;
			}
			case OCTAGON:{
				descriptor = new OCTAGON(chip, this);
				break;
			}
		}
	}
}
