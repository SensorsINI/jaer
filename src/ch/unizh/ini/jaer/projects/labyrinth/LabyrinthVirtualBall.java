/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.labyrinth;

import java.awt.geom.Point2D;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.glu.GLUquadric;

import net.sf.jaer.Description;
import net.sf.jaer.aemonitor.AEConstants;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip2D;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.eventprocessing.EventFilter2DMouseAdaptor;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 *  A virtual ball that generates events and model physics of ball movement.
 * @author Tobi
 */
@Description("Virtual ball for labyrinth game")
public class LabyrinthVirtualBall extends EventFilter2DMouseAdaptor implements Observer {

	LabyrinthMap map = null;
	LabyrinthBallController controller = null;
	VirtualBall ball = new VirtualBall();
	private float slowDownFactor = getFloat("slowDownFactor", 1);
	protected float staticEventRate = getFloat("staticEventRate", 1000);
	Random random = new Random();
	private boolean emitTCEvents = getBoolean("emitTCEvents", true);
	private float backgroundEventRate = getFloat("backgroundEventRate", 10000);
	private float slewRateLimitRadPerSec = getFloat("slewRateLimitRadPerSec", (20f / .1f / 57f));
	volatile Point2D.Float tiltsRadDelayed = new Point2D.Float();
	GLUquadric sphereQuad;
	Point2D.Float tiltOffset = new Point2D.Float(0, 0);
	private float randomTiltOffsetLimit=getFloat("randomTiltOffsetLimit",1e-2f);

	//    public LabyrinthVirtualBall(AEChip chip) {
	//        super(chip);
	//    }
	public LabyrinthVirtualBall(AEChip chip, LabyrinthGame game) {
		super(chip);
		controller = game.controller;
		map = controller.tracker.map;
		checkOutputPacketEventType(TypedEvent.class);
		setPropertyTooltip("slowDownFactor", "slow down real time by this factor");
		setPropertyTooltip("backgroundEventRate", "event rate of all pixels randomly in background");
		setPropertyTooltip("staticEventRate", "event rate when emitting events statically");
		setPropertyTooltip("emitTCEvents", "emit temporal contrast events on movement of virtual ball, intead of statically emitting events always");
		setPropertyTooltip("slewRateLimitRadPerSec", "slew rate limit of tilt control in radians of table tilt per second");
		setPropertyTooltip("randomTiltOffsetLimit", "random table tilt offset in radians assigned at reset");
		chip.addObserver(this);
	}

	@Override
	synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
		if (!isFilterEnabled()) {
			return in;
		}
		ball.update(in.getLastTimestamp());
		return out;
	}

	@Override
	synchronized public void resetFilter() {

		ball.reset();
		tiltOffset.x=randomTiltOffset();
		tiltOffset.y=randomTiltOffset();

		log.info("random tilt offset in virtual ball is "+tiltOffset);

	}

	float randomTiltOffset(){
		return getRandomTiltOffsetLimit()*(random.nextFloat()-.5f);
	}
	@Override
	public void initFilter() {
	}

	/**
	 * @return the slowDownFactor
	 */
	public float getSlowDownFactor() {
		return slowDownFactor;
	}

	/**
	 * @param slowDownFactor the slowDownFactor to set
	 */
	public void setSlowDownFactor(float slowDownFactor) {
		this.slowDownFactor = slowDownFactor;
		putFloat("slowDownFactor", slowDownFactor);
	}

	/**
	 * Get the value of staticEventRate
	 *
	 * @return the value of staticEventRate
	 */
	public float getStaticEventRate() {
		return staticEventRate;
	}

	/**
	 * Set the value of eventRate emitted by ball in Hz.
	 *
	 * @param eventRate new value of eventRate
	 */
	public void setStaticEventRate(float eventRate) {
		staticEventRate = eventRate;
		putFloat("staticEventRate", eventRate);
	}

	/**
	 * @return the emitTCEvents
	 */
	public boolean isEmitTCEvents() {
		return emitTCEvents;
	}

	/**
	 * @param emitTCEvents the emitTCEvents to set
	 */
	public void setEmitTCEvents(boolean emitTCEvents) {
		this.emitTCEvents = emitTCEvents;
		putBoolean("emitTCEvents", emitTCEvents);
	}

	@Override
	public void update(Observable o, Object arg) {
		if (o instanceof AEChip) {
			if (arg instanceof String) {
				String s = (String) arg;
				if (s.equals(Chip2D.EVENT_SIZEY) || s.equals(Chip2D.EVENT_SIZEX)) {
					if (chip.getNumPixels() > 0) {
						ball.reset();
					}
				}
			}
		}
	}

	/**
	 * @return the randomTiltOffsetLimit
	 */
	public float getRandomTiltOffsetLimit() {
		return randomTiltOffsetLimit;
	}

	/**
	 * @param randomTiltOffsetLimit the randomTiltOffsetLimit to set
	 */
	public void setRandomTiltOffsetLimit(float randomTiltOffsetLimit) {
		this.randomTiltOffsetLimit = randomTiltOffsetLimit;
	}

	public class VirtualBall {

		public Point2D.Float posPixels = new Point2D.Float();
		public Point2D.Float velPPS = new Point2D.Float(0, 0);
		public float radiusPixels = 2f;
		long lastUpdateTimeUs = 0;
		boolean initialized = false;

		public VirtualBall() {
		}

		public VirtualBall(Point2D pos) {
			posPixels.setLocation(pos);
		}

		public void update(int timeUs) {
			if (initialized) {

				Point2D.Float tiltsRad = controller.getTiltsRad();

				long tNowUs = System.nanoTime() >> 10;
				long dtUs = tNowUs - lastUpdateTimeUs;
				if ((dtUs < 0) || (dtUs > 100000)) {
					lastUpdateTimeUs = tNowUs;
					return;
				}
				float dtSec = AEConstants.TICK_DEFAULT_US * 1e-6f * dtUs * slowDownFactor;
				// update internal tilt values that model slew rate limit of servos

				//                    float slew = dtSec * slewRateLimitRadPerSec;
				//                if (tiltsRad.distance(tiltsRadDelayed) > slew) {
				//                    tiltsRadDelayed.x += slew * Math.signum(tiltsRad.x - tiltsRadDelayed.x);
				//                    tiltsRadDelayed.y += slew * Math.signum(tiltsRad.y - tiltsRadDelayed.y);
				//                } else {
				tiltsRadDelayed.setLocation(tiltsRad);
				//                }

				//               float lim=controller.getTiltLimitRad();
				//               tiltsRadDelayed.x=clip(tiltsRadDelayed.x,lim);
				//               tiltsRadDelayed.y=clip(tiltsRadDelayed.y,lim);

				tiltsRadDelayed.x = tiltsRadDelayed.x + tiltOffset.x;
				tiltsRadDelayed.y = tiltsRadDelayed.y + tiltOffset.y;

				float gfac = dtSec * controller.gravConstantPixPerSec2;
				velPPS.x += tiltsRadDelayed.x * gfac;
				velPPS.y += tiltsRadDelayed.y * gfac;
				float dx = velPPS.x * dtSec;
				float dy = velPPS.y * dtSec;
				posPixels.x += dx;
				posPixels.y += dy;
				Point2D.Float ur = new Point2D.Float(chip.getSizeX(), chip.getSizeY()), ll = new Point2D.Float(0, 0);
				if ((map != null) && (map.getBoundingBox() != null)) {
					ll.x = (float) map.getBoundingBox().getX();
					ll.y = (float) map.getBoundingBox().getY();
					ur.x = ll.x + (float) map.getBoundingBox().getWidth();
					ur.y = ll.y + (float) map.getBoundingBox().getHeight();
				}
				if (posPixels.x < ll.x) {
					posPixels.x = ll.x;
					velPPS.x = -velPPS.x;
				} else if (posPixels.x >= ur.x) {
					posPixels.x = ur.x;
					velPPS.x = -velPPS.x ;
				}
				if (posPixels.y < ll.y) {
					posPixels.y = ll.y;
					velPPS.y = -velPPS.y;
				} else if (posPixels.y >= ur.y) {
					posPixels.y = ur.y;
					velPPS.y = -velPPS.y;
				}


				int sx = chip.getSizeX(), sy = chip.getSizeY();
				int n;
				if (emitTCEvents) {
					n = (int) (dtSec * staticEventRate * velPPS.distance(0, 0));
				} else {
					n = (int) (dtSec * staticEventRate);
				}
				if (n > 10000) {
					n = 10000;
				}
				int bg = 0;
				if (backgroundEventRate > 1) {
					bg = (int) (dtSec * backgroundEventRate);
				}
				if (bg > 10000) {
					bg = 10000;
				}

				if ((n + bg) > 0) {
					float smalldt = dtUs / (n + bg);
					float frac = (float) n / (n + bg);

					checkOutputPacketEventType(TypedEvent.class);
					OutputEventIterator i = out.outputIterator();
					for (int k = 0; k < (n + bg); k++) {
						TypedEvent e = (TypedEvent) i.nextOutput();
						float r = random.nextFloat();
						if (r < frac) {
							e.x = (short) Math.floor(posPixels.x);
							e.y = (short) Math.floor(posPixels.y);
							e.x = jitter(e.x, sx);
							e.y = jitter(e.y, sy);
						} else {
							e.x = (short) random.nextInt(sx);
							e.y = (short) random.nextInt(sy);
						}
						e.timestamp = (int) (lastUpdateTimeUs + (long) (k * smalldt));
					}
				}

				lastUpdateTimeUs = tNowUs;

			} else {
				lastUpdateTimeUs = System.nanoTime() >> 10;
						initialized = true;
			}
		}

		private void reset() {
			posPixels.setLocation(chip.getSizeX() / 2, chip.getSizeY() / 2);
			velPPS.setLocation(0, 0);
			ball.lastUpdateTimeUs = System.nanoTime() >> 10;
			controller.resetFilter();
			try {
				controller.labyrinthHardware.setPanTiltValues(.1f * (random.nextFloat() - .5f), .1f * (random.nextFloat() - .5f));
			} catch (HardwareInterfaceException ex) {
			}
		}

		private void render(GL2 gl) {
			gl.glPushMatrix();
			gl.glColor4f(1f, 1f, 1f, .25f);
			//            gl.glLightf(chip.getSizeX()/2, chip.getSizeY()/2, chip.getMaxSize()*4);
			//            gl.glEnable(GL.GL_LIGHT0);
			gl.glTranslatef(posPixels.x, posPixels.y, radiusPixels);
			if (sphereQuad == null) {
				sphereQuad = glu.gluNewQuadric();
			}
			if (sphereQuad != null) {
				glu.gluSphere(sphereQuad, 6, 16, 16);
			}
			gl.glPopMatrix();
		}

		private float clip(float x, float lim) {
			if (x > lim) {
				x = lim;
			} else if (x < -lim) {
				x = -lim;
			}
			return x;
		}
	}

	short jitter(int k, int lim) {
		int r = random.nextInt(5) - 2;
		int j = k + r;
		if (j < 0) {
			j = 0;
		} else if (j >= lim) {
			j = lim - 1;
		}
		return (short) j;
	}

	/**
	 * @return the backgroundEventRate
	 */
	public float getBackgroundEventRate() {
		return backgroundEventRate;
	}

	/**
	 * @param backgroundEventRate the backgroundEventRate to set
	 */
	public void setBackgroundEventRate(float backgroundEventRate) {
		this.backgroundEventRate = backgroundEventRate;
		putFloat("backgroundEventRate", backgroundEventRate);
	}

	/**
	 * @return the slewRateLimitRadPerMs
	 */
	public float getSlewRateLimitRadPerSec() {
		return slewRateLimitRadPerSec;
	}

	/**
	 * @param slewRateLimitRadPerSec the slewRateLimitRadPerMs to set
	 */
	public void setSlewRateLimitRadPerSec(float slewRateLimitRadPerSec) {
		this.slewRateLimitRadPerSec = slewRateLimitRadPerSec;
		putFloat("slewRateLimitRadPerSec", slewRateLimitRadPerSec);
	}

	@Override
	public void annotate(GLAutoDrawable drawable) {
		super.annotate(drawable);
		GL2 gl = drawable.getGL().getGL2();
		// draw slew-rate limited tilt values
		final float size = .1f;
		float sx = chip.getSizeX(), sy = chip.getSizeY();
		{
			gl.glPushMatrix();

			gl.glTranslatef(-sx * size * 1.1f, .7f * sy, 0); // move outside chip array to lower left
			gl.glScalef(sx * size, sx * size, sx * size); // scale everything so that when we draw 1 unit we cover this size*sx pixels

			gl.glLineWidth(3f);
			gl.glColor3f(1, 1, 1);

			{
				gl.glBegin(GL.GL_LINE_LOOP); // frame of slew-rate limited modelled controller output
				gl.glVertex2f(0, 0);
				gl.glVertex2f(1, 0);
				gl.glVertex2f(1, 1);
				gl.glVertex2f(0, 1);
				gl.glEnd();
			}

			// draw tilt vector
			float lim = controller.getTiltLimitRad() / 2;
			float xlen = tiltsRadDelayed.x / lim;
			float ylen = tiltsRadDelayed.y / lim;
			gl.glLineWidth(4f);
			if ((Math.abs(xlen) < .5f) && (Math.abs(ylen) < .5f)) {
				gl.glColor4f(0, 1, 0, 1);
			} else {
				gl.glColor4f(1, 0, 0, 1);
			}
			gl.glPointSize(6f);
			{
				gl.glTranslatef(.5f, .5f, 0);
				gl.glBegin(GL.GL_POINTS);
				gl.glVertex2f(0, 0);
				gl.glEnd();
			}
			gl.glLineWidth(4);
			{
				gl.glBegin(GL.GL_LINES);
				gl.glVertex2f(0, 0);
				gl.glVertex2f(xlen, ylen);  // vector showing slew-rate limited controller output
				gl.glEnd();
			}

			gl.glPopMatrix();
		}
		// draw virtual ball
		//        gl.glPointSize(44);
		//        gl.glColor4f(0,0,.5f,.5f);
		//        gl.glBegin(GL.GL_POINTS);
		//        gl.glVertex2f(ball.posPixels.x,ball.posPixels.y);
		//        gl.glEnd();

		chip.getCanvas().checkGLError(gl, glu, "after virtual ball annotations");
		ball.render(gl);
	}
}
