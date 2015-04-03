package ch.unizh.ini.jaer.hardware.pantilt;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.Vector;
import java.util.logging.Logger;


import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.util.Matrix;
/**
 * The calibrator for the PanTilt. It maintains the calibration from retinal to pan tilt values and in conjunction with
 * PanTiltGUI allows manual calibration of the transform from retinal to pan-tilt space.
 * 
 * @author tobi
 */
public class PanTiltCalibrator implements PropertyChangeListener, FrameAnnotater {
	static Logger log=Logger.getLogger("PanTiltCalibrator");
	CalibratedPanTilt calibratedPanTilt;
	private boolean calibrating=false;
	transient PanTiltGUI gui;
	float[][] retinaSamples=null;
	float[][] panTiltSamples=null;
	// sampled pan tilt values for corners
	boolean calibrated=false;
	Vector<PanTiltCalibrationPoint> sampleVector=new Vector<PanTiltCalibrationPoint>();
	float[][] transform=new float[][]{{1, 0, 0}, {0, 1, 0}};
	float[] computedPanTilt=new float[2];

	public PanTiltCalibrator(CalibratedPanTilt panTilt) {
		super();
		calibratedPanTilt=panTilt;
		loadCalibrationPrefs();
	}

	@Override
	public String toString() {
		StringBuffer sb=new StringBuffer();
		int rows=transform.length;
		int cols=transform[0].length;
		for(int i=0; i<rows; i++) {
			sb.append('\n');
			for(int j=0; j<cols; j++) {
				sb.append(transform[i][j]+"  ");
			}
		}
		sb.append("");
		return sb.toString();
	}

	void paint(Graphics g) {
		final int r=6;
		for(PanTiltCalibrationPoint p : sampleVector) {
			Point mp=gui.getMouseFromPanTilt(p.pt);
			g.drawOval(mp.x-r, mp.y-r, r*2, r*2);
		}
	}

	void addSample(PanTiltCalibrationPoint sample) {
		if(calibratedPanTilt.tracker.getNumClusters()>0) {
			RectangularClusterTracker.Cluster c=calibratedPanTilt.tracker.getClusters().get(0);
			if(c.isVisible()) {
				Point2D.Float pRet=c.getLocation();
				sample.ret.x=pRet.x;
				sample.ret.y=pRet.y;
				sampleVector.add(sample);
			} else {
				log.warning("cluster not visible for sample");
			}
		} else {
			log.warning("no cluster for sample, ignoring");
		}
	}

	float[][] getPanTiltSamples() {
		int n=getNumSamples();
		float[][] m=new float[2][n];
		for(int i=0; i<n; i++) {
			m[0][i]=sampleVector.get(i).pt.x;
			m[1][i]=sampleVector.get(i).pt.y;
		}
		return m;
	}

	float[][] getRetinaSamples() {
		int n=getNumSamples();
		float[][] m=new float[3][n];
		for(int i=0; i<n; i++) {
			m[0][i]=sampleVector.get(i).ret.x;
			m[1][i]=sampleVector.get(i).ret.y;
			m[2][i]=1;
		}
		return m;
	}

	int getNumSamples() {
		return sampleVector.size();
	}

	void calibrate() {
		calibrating=true;
		calibratedPanTilt.getPanTiltHardware().acquire();
		if(gui==null) {
			gui=new PanTiltGUI(calibratedPanTilt.getPanTiltHardware(), this);
			gui.getSupport().addPropertyChangeListener(this);
		}
		gui.setVisible(true);
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if(evt.getPropertyName().equals(PanTiltGUI.Message.AddSample.name())) {
			PanTiltCalibrationPoint p=(PanTiltCalibrationPoint) evt.getNewValue();
			addSample(p);
			computeCalibration();
		} else if(evt.getPropertyName().equals(PanTiltGUI.Message.ComputeCalibration.name())) {
			computeCalibration();
			log.info("computing calibration");
			saveCalibrationPrefs();
			calibratedPanTilt.getPanTiltHardware().release();
			setCalibrating(false);
		} else if(evt.getPropertyName().equals(PanTiltGUI.Message.ClearSamples.name())) {
			sampleVector.clear();
		} else if(evt.getPropertyName().equals(PanTiltGUI.Message.RevertCalibration.name())) {
			loadCalibrationPrefs();
			calibratedPanTilt.getPanTiltHardware().release();
			setCalibrating(false);
		} else if(evt.getPropertyName().equals(PanTiltGUI.Message.EraseLastSample.name())) {
			if(sampleVector.size()>0) {
				Object s=sampleVector.lastElement();
				sampleVector.remove(s);
			}
		} else if(evt.getPropertyName().equals(PanTiltGUI.Message.ShowCalibration.name())) {
			showCalibration();
		} else if(evt.getPropertyName().equals(PanTiltGUI.Message.ResetCalibration.name())) {
			resetToDefaultCalibration();
		} else {
			log.warning("bogus PropertyChangeEvent "+evt);
		}
	}

	void showCalibration() {
		Thread t=new Thread() {
			@Override
			public void run() {
				for(PanTiltCalibrationPoint p : sampleVector) {
					log.info("showing "+p);
					try {
						calibratedPanTilt.setPanTiltVisualAim(p.ret.x,p.ret.y);
						//                        calibratedPanTilt.getPanTiltHardware().setPanTiltValues(p.pt.x, p.pt.y);
					} catch(HardwareInterfaceException e) {
					}
					try {
						sleep(500);
					} catch(InterruptedException e) {
					}
				}
			}
		};
		t.start();
	}

	/**
	 * pantiltvalues=transform*retinavalues; P=TR.
	 * we want to find T.
	 * transform is a 2x3 matrix
	 * retinavalues is a 3xn matrix, where each column is x,y,1
	 * pantiltvalues is a 2xn matrix
	 *
	 * This routine finds the least squares fit from the retina to pantilt coordinates.
	 */
	private void computeCalibration() {
		if(getNumSamples()<4) {
			log.warning("Only captured "+getNumSamples()+": need at least 3 non-singular points to calibrate");
			return;
		}
		log.info("computing calibration");
		panTiltSamples=getPanTiltSamples();
		retinaSamples=getRetinaSamples();
		float[][] cctrans=new float[3][3];
		float[][] ctrans=Matrix.transposeMatrix(retinaSamples);
		Matrix.multiply(retinaSamples, ctrans, cctrans);
		Matrix.invert(cctrans);
		// in place
		float[][] ctranscctrans=new float[getNumSamples()][3];
		Matrix.multiply(ctrans, cctrans, ctranscctrans);
		Matrix.multiply(panTiltSamples, ctranscctrans, transform);
		System.out.println("pantilt samples");
		Matrix.print(panTiltSamples);
		System.out.println("retina samples");
		Matrix.print(retinaSamples);
		System.out.println("transform from retina to pantilt");
		Matrix.print(transform);
	}

	public float[] getTransformedPanTiltFromXY(float[] xy1) {
		Matrix.multiply(transform, xy1, computedPanTilt);
		return computedPanTilt;
	}

	/** Resets to a default calibration */
	public synchronized void resetToDefaultCalibration() {
		log.info("reset calibration to default transform");
		transform=new float[][]{{1.0F/calibratedPanTilt.getChip().getSizeX(), 0, 0}, {0, 1.0F/calibratedPanTilt.getChip().getSizeY(), 0}};
	}

	public boolean isCalibrating() {
		return calibrating;
	}

	public void setCalibrating(boolean calibrating) {
		this.calibrating=calibrating;
	}

	private void saveCalibrationPrefs() {
		try {
			// Serialize to a byte array
			ByteArrayOutputStream bos=new ByteArrayOutputStream();
			ObjectOutput oos=new ObjectOutputStream(bos);
			oos.writeObject(transform);
			oos.writeObject(sampleVector);
			oos.close();
			// Get the bytes of the serialized object
			byte[] buf=bos.toByteArray();
			calibratedPanTilt.getPrefs().putByteArray("PanTiltCalibration.calibration", buf);
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings("unchecked")
	private void loadCalibrationPrefs() {
		// Deserialize from a byte array
		try {
			byte[] bytes=calibratedPanTilt.getPrefs().getByteArray("PanTiltCalibration.calibration", null);
			if(bytes!=null) {
				ObjectInputStream in=new ObjectInputStream(new ByteArrayInputStream(bytes));
				transform=(float[][]) in.readObject();
				sampleVector=(Vector<PanTiltCalibrationPoint>) in.readObject();
				in.close();
				log.info("loaded existing transform from vision coordinates to pantilt coordinates");
			} else {
				log.info("no calibration to load, using default transform");
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void setAnnotationEnabled(boolean yes) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public boolean isAnnotationEnabled() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public void annotate(float[][][] frame) {
	}

	public void annotate(Graphics2D g) {
	}

	@Override
	public void annotate(GLAutoDrawable drawable) {
		GL2 gl=drawable.getGL().getGL2(); // when we getString this we are already set up with updateShape 1=1 pixel, at LL corner

		if(gl==null) {
			log.warning("null GL");
			return;
		}
		final float BOX_LINE_WIDTH=3f; // in pixels

		gl.glColor3f(1, 0, 0);
		gl.glLineWidth(BOX_LINE_WIDTH);
		final  int sx=2,    sy=2;
		for(PanTiltCalibrationPoint p : sampleVector) {
			gl.glPushMatrix();
			final  int x=(int) p.ret.x,    y=(int) p.ret.y;
			gl.glBegin(GL.GL_LINE_LOOP);
			{
				gl.glVertex2i(x-sx, y-sy);
				gl.glVertex2i(x+sx, y-sy);
				gl.glVertex2i(x+sx, y+sy);
				gl.glVertex2i(x-sx, y+sy);
			}
			gl.glEnd();
			gl.glPopMatrix();
		}
	}
}
