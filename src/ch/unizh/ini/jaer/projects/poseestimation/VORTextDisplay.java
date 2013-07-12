/**
 * VORTextDisplay.java
 * Uses VORTextDisplay Sensor for ...
 * 
 * Created on November 14, 2012, 2:24 PM
 *
 * @author Haza
 */

package ch.unizh.ini.jaer.projects.poseestimation;

import java.util.Observable;
import java.util.Observer;

import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

import com.jogamp.opengl.util.gl2.GLUT;

@Description("Shows VOR Sensor data on screen")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class VORTextDisplay extends EventFilter2D implements FrameAnnotater, Observer {

	// Sensor Outputs
	private double[] acceleration,
	gyro,
	compass = new double[3];        // Sensor values

	/**
	 * Constructor
	 * @param chip Called with AEChip properties
	 */
	public VORTextDisplay(AEChip chip) {
		super(chip);
		chip.addObserver(this);
		initFilter();
	} // END CONSTRUCTOR

	/**
	 * Called on creation
	 */
	@Override
	public void initFilter() {

	} // END METHOD

	/**
	 * Called on filter reset
	 */
	@Override
	public void resetFilter() {

	} // END METHOD

	/**
	 * Called when objects being observed change and send a message
	 * @param o Object that has changed
	 * @param arg Message object has sent about change
	 */
	@Override
	public void update(Observable o, Object arg) {
		initFilter();
	} // END METHOD

	@Override
	synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
		// Check for empty packet
		if(in.getSize() == 0) {
			return in;
		}
		// Check that filtering is in fact enabled
		if(!filterEnabled) {
			return in;
		}
		// If necessary, pre filter input packet
		if(enclosedFilter!=null) {
			in=enclosedFilter.filterPacket(in);
		}

		return in;
	} // END METHOD

	/**
	 * Annotation or drawing method
	 * @param drawable OpenGL Rendering Object
	 */
	@Override
	synchronized public void annotate(GLAutoDrawable drawable) {
		if (!isAnnotationEnabled()) {
			return;
		}
		GL2 gl = drawable.getGL().getGL2();
		if (gl == null) {
			return;
		}

		if (chip.getClass() == DVS128Phidget.class) {
			acceleration = ((DVS128Phidget)chip).getAcceleration().clone();
			gyro = ((DVS128Phidget)chip).getGyro().clone();
			compass = ((DVS128Phidget)chip).getCompass().clone();

			final int font = GLUT.BITMAP_HELVETICA_18;
			GLUT glut = chip.getCanvas().getGlut();
			gl.glColor3f(1, 1, 1);
			gl.glRasterPos3f(108, 123, 0);
			// Accelerometer x, y, z info
			glut.glutBitmapString(font, String.format("a_x=%+.2f", acceleration[0]));
			gl.glRasterPos3f(108, 118, 0);
			glut.glutBitmapString(font, String.format("a_y=%+.2f", acceleration[1]));
			gl.glRasterPos3f(108, 113, 0);
			glut.glutBitmapString(font, String.format("a_z=%+.2f", acceleration[2]));
			// Gyroscope
			gl.glRasterPos3f(108, 103, 0);
			glut.glutBitmapString(font, String.format("g_x=%+.2f", gyro[0]));
			gl.glRasterPos3f(108, 98, 0);
			glut.glutBitmapString(font, String.format("g_y=%+.2f", gyro[1]));
			gl.glRasterPos3f(108, 93, 0);
			glut.glutBitmapString(font, String.format("g_z=%+.2f", gyro[2]));
			// Magnet
			gl.glRasterPos3f(108, 83, 0);
			glut.glutBitmapString(font, String.format("c_x=%+.2f", compass[0]));
			gl.glRasterPos3f(108, 78, 0);
			glut.glutBitmapString(font, String.format("c_y=%+.2f", compass[1]));
			gl.glRasterPos3f(108, 73, 0);
			glut.glutBitmapString(font, String.format("c_z=%+.2f", compass[2]));
		} // END IF
	} // END METHOD
} // END CLASS