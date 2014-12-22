package ch.ethz.hest.balgrist.microscopetracker;

import java.awt.geom.Point2D;
import java.util.LinkedList;
import javax.media.opengl.GL;

import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.util.DrawGL;

/**
 * Tracks multiple particles and computes global estimate of mean linear
 * velocity
 *
 * @author Niklaus Amrein
 */
@Description("Tracks multiple particles and computes global estimate of mean linear velocity")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class MicroscopeTracker extends RectangularClusterTracker {

    // constructor of the class
    public MicroscopeTracker(AEChip chip) {
        super(chip);
        String mt="1: MicroscropeTracker";
        setPropertyTooltip(mt,"showGlobalAverageVelocity","draws the estimated velocity vector");
        setPropertyTooltip(mt,"sendDataToLabview","sends estimated position and velocity to LavView via TCP link on \"127.0.0.1\", port 23");
    }

    // our new functions are deactivated when the programm is started
    private boolean showGlobalAverageVelocity = getBoolean("showGlobalAverageVelocity", true);
    private boolean sendDataToLabview = getBoolean("sendDataToLabview", false);

    // these values have to be initialized before the first time we send a vector
    private Point2D.Float globalAverageVelocity = new Point2D.Float(0, 0);			// [px/s]
    private Point2D.Float globalPosition = new Point2D.Float(0, 0);					// [px]
    float time1 = System.nanoTime() / 1000000;										// [ms]
    float time2 = System.nanoTime() / 1000000;										// [ms]

    // construct a client for the TCP communication with labview
    MicroscopeTrackerTCPclient Client;

	// this function has to be changed to also draw the average speed vector
    // when we activate it
    @Override
    public synchronized void annotate(GLAutoDrawable drawable) {
        super.annotate(drawable);

        if (showGlobalAverageVelocity) {
            GL2 gl = drawable.getGL().getGL2();
            gl.glColor3f(1, 1, 0);
            gl.glPointSize(5f);
            gl.glBegin(GL.GL_POINTS);
            gl.glVertex2d(64, 64);
            gl.glEnd();
            DrawGL.drawVector(gl, 64, 64, globalAverageVelocity.x, globalAverageVelocity.y);
        }
    }

    // Override Factory Methods
    @Override
    public Cluster createCluster(BasicEvent ev) {
        return new Cluster(ev);
    }

    @Override
    public Cluster createCluster(RectangularClusterTracker.Cluster one, RectangularClusterTracker.Cluster two) {
        return new Cluster(one, two);
    }

    @Override
    public Cluster createCluster(BasicEvent ev, OutputEventIterator itr) {
        return new Cluster(ev, itr);
    }

    // the Cluster class needs to be extended
    public class Cluster extends RectangularClusterTracker.Cluster {

        // new Constructors
        public Cluster() {
            super();
        }

        public Cluster(BasicEvent ev) {
            super(ev);
        }

        public Cluster(BasicEvent ev, OutputEventIterator outItr) {
            super(ev, outItr);
        }

        public Cluster(RectangularClusterTracker.Cluster one, RectangularClusterTracker.Cluster two) {
            super(one, two);
        }

        // update average velocity when Cluster vanishes
        @Override
        protected void prune() {
            if (sendDataToLabview) {
                updateAverageVelocity();
            }
        }

        // update average velocity when speed of one of the clusters changes
        @Override
        protected void updateVelocity() {
            super.updateVelocity();
            if (sendDataToLabview) {
                if (Client.sendVector(globalPosition.x, globalPosition.y) == true) {
                    // send was successful
                } else {
                    // send previous vector in [px/ms] with corresponding dt = time2 - time1 in [ms]
                    setsendDataToLabview(false);
                }
            }
            updateAverageVelocity();
        }

        // Method to send previous vector and to calculate new average velocity
        public void updateAverageVelocity() {
            time1 = time2;							// [ms]
            time2 = System.nanoTime() / 1000000;	// [ms]

            // calculate new velocity vector
            globalAverageVelocity.x = 0;
            globalAverageVelocity.y = 0;

            LinkedList<RectangularClusterTracker.Cluster> Clusters = getVisibleClusters();
            int NoC = Clusters.size();

            // 0/0 results in NaN -> error when sending NaN as float
            if (NoC > 0) {
                for (int i = 0; i < NoC; i++) {
                    globalAverageVelocity.x += Clusters.get(i).getVelocityPPS().x;		// [px/s]
                    globalAverageVelocity.y += Clusters.get(i).getVelocityPPS().y;		// [px/s]
                }
                globalAverageVelocity.x /= NoC;
                globalAverageVelocity.y /= NoC;
            }

            // calculate new global Position
            globalPosition.x += (globalAverageVelocity.x * (time2 - time1)) / 1000;		// [px]
            globalPosition.y += (globalAverageVelocity.y * (time2 - time1)) / 1000;		// [px]
        }
    }

    // check if vector should be shown
    public boolean getshowGlobalAverageVelocity() {
        return showGlobalAverageVelocity;
    }

    // set if vector should be shown
    public void setshowGlobalAverageVelocity(boolean v) {
        if (v) {
            log.info("displaying vector");
        } else {
            log.info("not displaying vector");
        }
        this.showGlobalAverageVelocity = v;
        putBoolean("showGlobalAverageVelocity", v);
    }

    // check if data should be sent to labview
    public boolean getsendDataToLabview() {
        return sendDataToLabview;
    }

    // set if vector should be sent to labview
    public void setsendDataToLabview(boolean v) {
        if (v) {
            Client = new MicroscopeTrackerTCPclient();

            // reset global position
            globalPosition.x = 0;
            globalPosition.y = 0;

            if (Client.createClient("127.0.0.1", 23) == true) {
                log.info("sending data");
            } else {
                log.warning("failed to connect to server");
            }

        } else {
            Client.closeClient();

            // reset  global position
            globalPosition.x = 0;
            globalPosition.y = 0;

            log.info("not sending data");
        }
        this.sendDataToLabview = v;
        putBoolean("sendDataToLabview", v);
    }
}
