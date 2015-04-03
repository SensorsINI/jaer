/*
 * PcaTrackingFilter.java
 *
 * @author David Mascarnas
 * Created on July 2, 2010
 *
 * "Tracks a single object pose by Principal Components Analysis"
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package net.sf.jaer.eventprocessing.filter;
import java.awt.geom.Point2D;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

//DLM additions
import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;



@Description("Tracks a single object pose by Principle Components Analysis")
public class PcaTrackingFilter extends EventFilter2D implements FrameAnnotater {

    //Required variables for this class.
    float xsum = 0f;
    float ysum = 0f;
    float xxsum = 0f;
    float yysum = 0f;
    float xysum = 0f;

    float xmean = 0f;
    float ymean = 0f;
    float xxvar = 0f;
    float yyvar = 0f;
    float xycovar = 0f;

    int n = 4096;
    //int n = 2048;
    //int n = 1024;                    //This is the length of the ring buffer
    //int n = 512;
    //int n = 256;
    //int n = 128;          //This worked well for alot of the card data
    //int n = 64;

    //int scale_stddev = 1;


    //protected int dt=getPrefs().getInt("BackgroundActivityFilter.dt",30000);
    //{setPropertyTooltip("dt","Events with less than this delta time in us to neighbors pass through");}

    double stddev_inc = .1;     //I need this so I can exert fine control over the size of the bounding box.
    int scale_stddev=getPrefs().getInt("PcaTracker.scale_stddev",10);
    {setPropertyTooltip("scale_stddev","Scale of bounding box relative to the standard deviation (.1 * standard dev)");}

     int ring_buffer_length=getPrefs().getInt("PcaTracker.ring_buffer_length",1024);
    {setPropertyTooltip("ring_buffer_length","Length of the ring buffer.  Max 4092");}


    float[] ring_bufferX = new float[n];      //This is the array for the the ring buffer.  I need to make sure this is initialized to zeros
    float[] ring_bufferY = new float[n];
    int rb_index = 0;       //This is the ring buffer index


    //rotation matrix elements
    /*
    [R1 R2]
    [R3 R4]
     *
     * theta is the angle through which the coordinate frame rotates.
     * R1 = cos(theta)
     * R2 = sin(theta)
     * R3 = -sin(theta)
     * R4 = cos(theta)
     */

    double theta = 0f;      //The angle the data is decorrelated at

    double R1 = 0f;
    double R2 = 0f;
    double R3 = 0f;
    double R4 = 0f;

    /////////////////////////////////////////


    Point2D medianPoint=new Point2D.Float(), stdPoint=new Point2D.Float(), meanPoint=new Point2D.Float(), eigvec1Point = new Point2D.Float(), eigvec2Point = new Point2D.Float();
    Point2D square1Point=new Point2D.Float(), square2Point=new Point2D.Float(), square3Point=new Point2D.Float(), square4Point=new Point2D.Float() ;
    Point2D eventPoint = new Point2D.Float();
    Point2D stddev1Point=new Point2D.Float();
    Point2D stddev2Point=new Point2D.Float();
    float xmedian=0f;
    float ymedian=0f;
    float xstd=0f;
    float ystd=0f;
    //float xmean=0, ymean=0;
    int lastts=0, dt=0;
    int prevlastts=0;


    /** Creates a new instance of PcaTracker */
    public PcaTrackingFilter(AEChip chip) {
        super(chip);

        //This initializes the ring_buffers to all zeros.
        //These must start as all zeros for my algorithm to work.
        for (int k = 0 ; k < n; k++) {
            ring_bufferX[k] = 0;
            ring_bufferY[k] = 0;
        }

    }


    public Object getFilterState() {
        return null;
    }

    public boolean isGeneratingFilter() {
        return false;
    }

    public void resetFilter() {
        medianPoint.setLocation(chip.getSizeX()/2,chip.getSizeY()/2);
        meanPoint.setLocation(chip.getSizeX()/2,chip.getSizeY()/2);
        stdPoint.setLocation(1,1);
    }

    public Point2D getMedianPoint() {
        return this.medianPoint;
    }

    public Point2D getStdPoint() {
        return this.stdPoint;
    }

    public Point2D getMeanPoint() {
        return this.meanPoint;
    }




    public int getScale_stddev() {
        return this.scale_stddev;
    }

    /**
     * sets the scale of the bounding box relative to stddev
     <p>
     Fires a PropertyChangeEvent "scale_stddev"

     * @param scale_stddev delay in us
     */
    public void setScale_stddev(final int scale_stddev) {
        getPrefs().putInt("PcaTrackingFilter.scale_stddev",scale_stddev);
        getSupport().firePropertyChange("scale_std",this.scale_stddev,scale_stddev);
        this.scale_stddev = scale_stddev;
    }

    public int getRing_buffer_length() {
        return this.ring_buffer_length;
    }

    /**
     * sets the scale of the bounding box relative to stddev
     <p>
     Fires a PropertyChangeEvent "scale_stddev"

     */
    public void setRing_buffer_length(final int ring_buffer_length) {

        if (ring_buffer_length < 16) {
            getPrefs().putInt("PcaTrackingFilter.ring_buffer_length",16);
            getSupport().firePropertyChange("ring_buffer_length",this.ring_buffer_length,16);
            this.ring_buffer_length = 16;
        } else if (ring_buffer_length > 4096) {
            getPrefs().putInt("PcaTrackingFilter.ring_buffer_length",4096);
            getSupport().firePropertyChange("ring_buffer_length",this.ring_buffer_length,4096);
            this.ring_buffer_length = 4096;
        } else {
            getPrefs().putInt("PcaTrackingFilter.ring_buffer_length",ring_buffer_length);
            getSupport().firePropertyChange("ring_buffer_length",this.ring_buffer_length,ring_buffer_length);
            this.ring_buffer_length = ring_buffer_length;
        }
    //At this point I think I need to reinitialize the filter

    //This initializes the ring_buffers to all zeros.
    //These must start as all zeros for my algorithm to work.
    for (int k = 0 ; k < n; k++) {
        ring_bufferX[k] = 0;
        ring_bufferY[k] = 0;
    }

    xsum = 0f;
    ysum = 0f;
    xxsum = 0f;
    yysum = 0f;
    xysum = 0f;

    xmean = 0f;
    ymean = 0f;
    xxvar = 0f;
    yyvar = 0f;
    xycovar = 0f;

    rb_index = 0;

    }



    public void initFilter() {
    }


    public EventPacket filterPacket(EventPacket in) {

        checkOutputPacketEventType(in); //This is what actually initiates the out EventPacket of the correct type.
        OutputEventIterator outItr=out.outputIterator();

            for(Object o:in){
                BasicEvent e=(BasicEvent)o;

                //This is to correctly increment the ring buffer.
                if (rb_index >= ring_buffer_length ){
                    rb_index = 0;
                }

                //System.out.println("=============================================");
                //System.out.println("x=" + e.x + " y=" + e.y);



                //Now calculate the mean information


                xsum = xsum + e.x - ring_bufferX[rb_index];
                ysum = ysum + e.y - ring_bufferY[rb_index];

                //System.out.println("x sum=" + xsum + " y sum=" + ysum + " rb index = " + rb_index);

                xmean = xsum/ring_buffer_length;
                ymean = ysum/ring_buffer_length;


                //System.out.println("x mean=" + xmean + " y mean=" + ymean);

                //Now calculate the variance information
                xxsum = xxsum + e.x*e.x - ring_bufferX[rb_index]*ring_bufferX[rb_index];
                yysum = yysum + e.y*e.y - ring_bufferY[rb_index]*ring_bufferY[rb_index];
                xysum = xysum + e.x*e.y - ring_bufferX[rb_index]*ring_bufferY[rb_index];

                //System.out.println("xx sum=" + xxsum + " yy sum=" + yysum + " xysum" + xysum);

                xxvar = (xxsum - ((xsum*xsum)/ring_buffer_length))/(ring_buffer_length-1);
                yyvar = (yysum - ((ysum*ysum)/ring_buffer_length))/(ring_buffer_length-1);
                xycovar = (xysum - ((xsum*ysum)/ring_buffer_length))/(ring_buffer_length);

                //System.out.println("xxvar=" + xxvar + " yyvar=" + yyvar + " xycovar=" + xycovar);

                //Now calculate the eigenvalues directly

                double a = 1;
                double b = -1 * (xxvar + yyvar);
                double c = xxvar * yyvar - xycovar * xycovar;

                double eig1 = (-1 * b + java.lang.Math.sqrt(b*b - 4*a*c))/(2 * a);
                double eig2 = (-1 * b - java.lang.Math.sqrt(b*b - 4*a*c))/(2 * a);

                double stddev1 = java.lang.Math.sqrt(eig1);
                double stddev2 = java.lang.Math.sqrt(eig2);

                //System.out.println("Eig1=" + eig1 + " Eig2=" + eig2);

                //now calculate the eigenvectors

                double eigvec1x = eig1 - yyvar;
                double eigvec2x = eig2 - yyvar;

                double eigvec1y = xycovar;
                double eigvec2y = xycovar;

                //Now normalize the eignvectors

               double eig1norm = java.lang.Math.sqrt(eigvec1x*eigvec1x + eigvec1y*eigvec1y);
               double eig2norm = java.lang.Math.sqrt(eigvec2x*eigvec2x + eigvec2y*eigvec2y);

               eigvec1x = eigvec1x/eig1norm;
               eigvec1y = eigvec1y/eig1norm;

               eigvec2x = eigvec2x/eig2norm;
               eigvec2y = eigvec2y/eig2norm;


                //Set the points for drawing (I am not sure if this should be outside or inside the for loop)
                meanPoint.setLocation(xmean,ymean);

                eigvec1Point.setLocation(xmean + eigvec1x * stddev1, ymean + eigvec1y * stddev1);
                eigvec2Point.setLocation(xmean + eigvec2x * stddev2, ymean + eigvec2y * stddev2);      //The length of this vector is scaled by the eigenvalues.

                /*
                //This stuff really slowed everything down, but it seems to work fine without it.
                scale_stddev = getScale_stddev();
                setScale_stddev(scale_stddev);
                 */

                square1Point.setLocation(xmean + scale_stddev * stddev_inc *(     (eigvec1x * stddev1) - (eigvec2x * stddev2)), ymean + scale_stddev * stddev_inc *(      (eigvec1y * stddev1) - (eigvec2y * stddev2)));
                square2Point.setLocation(xmean + scale_stddev * stddev_inc *(-1 * (eigvec1x * stddev1) - (eigvec2x * stddev2)), ymean + scale_stddev * stddev_inc *( -1 * (eigvec1y * stddev1) - (eigvec2y * stddev2)));
                square3Point.setLocation(xmean + scale_stddev * stddev_inc *(-1 * (eigvec1x * stddev1) + (eigvec2x * stddev2)), ymean + scale_stddev * stddev_inc *( -1 * (eigvec1y * stddev1) + (eigvec2y * stddev2)));
                square4Point.setLocation(xmean + scale_stddev * stddev_inc *(     (eigvec1x * stddev1) + (eigvec2x * stddev2)), ymean + scale_stddev * stddev_inc *(      (eigvec1y * stddev1) + (eigvec2y * stddev2)));

                //eventPoint.setLocation(e.x,e.y);        //Just a point to show where the latest event occurs


                stddev1Point.setLocation(10, 10 + stddev1);
                stddev2Point.setLocation(20, 10 + stddev2);


                //Put the event data in the ring buffer
                if (rb_index < ring_buffer_length ) {
                ring_bufferX[rb_index] = e.x;
                ring_bufferY[rb_index] = e.y;

                } else {
                    rb_index = 0;
                    ring_bufferX[rb_index] = e.x;
                    ring_bufferY[rb_index] = e.y;

                }


                //This is the ultimate filter that decides what events pass on to the next stage.
                //This should only pass points within the std_dev based bounding box.

                //First subtract out the mean
                double relx = e.x - xmean;
                double rely = e.y - ymean;

                //Try the dot product method to determine how far you are from the mean.
                double dot_eigvec1 = (relx * eigvec1x + rely * eigvec1y);
                double dot_eigvec2 = (relx * eigvec2x + rely * eigvec2y);

                 if( (java.lang.Math.abs(dot_eigvec1) <= (scale_stddev * stddev_inc *  stddev1)) && (java.lang.Math.abs(dot_eigvec2) <= (scale_stddev * stddev_inc *  stddev2))   ){
                    BasicEvent i=(BasicEvent)outItr.nextOutput();
                    i.copyFrom(e);
                }


/*
                //Now rotate {relx;rely} to the principle frame.
                theta = java.lang.Math.atan2(rely,relx);
                R1 = java.lang.Math.cos(theta);
                R2 = java.lang.Math.sin(theta);
                R3 = -1 * R2;   //R3 = -1 * sin(theta)
                R4 = R1;        //R4 = cos(theta)

                relx = R1 * relx + R2 * rely;
                rely = R3 * relx + R4 * rely;

                //Now test to see where the relx and rely are



                if( (java.lang.Math.abs(relx) <= (scale_stddev * stddev_inc *  stddev2)) && (java.lang.Math.abs(rely) <= (scale_stddev * stddev_inc *  stddev1))   ){
                    BasicEvent i=(BasicEvent)outItr.nextOutput();
                    i.copyFrom(e);
                }
                */



                rb_index++;
            }



        //medianPoint.setLocation(xmedian, ymedian);
        //meanPoint.setLocation(xmean,ymean);
        //principlePoint.setLocation(xmean + rx,ymean + ry);
        //stdPoint.setLocation(xstd,ystd);







        return out; //I just putString this there since it seems necessary and Tobi said to do it.
    }

    /** JOGL annotation */
    public void annotate(GLAutoDrawable drawable) {
        if(!isFilterEnabled()) return;
        Point2D p=meanPoint;
        Point2D s=eventPoint;
        //Point2D o=orthoPoint;
        Point2D q=eigvec1Point;
        Point2D r=eigvec2Point;

        Point2D t = square1Point;
        Point2D u = square2Point;
        Point2D v = square3Point;
        Point2D w = square4Point;

        Point2D sxx = stddev1Point;
        Point2D syy = stddev2Point;

        //THese are points to make a square


        GL2 gl=drawable.getGL().getGL2();
        // already in chip pixel context with LL corner =0,0
        gl.glPushMatrix();

        gl.glColor3f(0,1,0);
        gl.glLineWidth(4);
        gl.glBegin(GL2.GL_LINES);

        //Draw the eigenvectors
        gl.glVertex2d(p.getX(), p.getY());      //Origen
        gl.glVertex2d(q.getX(), q.getY());      //End point

        gl.glVertex2d(p.getX(), p.getY());      //Origen
        gl.glVertex2d(r.getX(), r.getY());      //End point

        //Draw the current point
        //gl.glVertex2d(p.getX(), p.getY());      //Origen
        //gl.glVertex2d(s.getX(), s.getY());     //Origen

        gl.glEnd();

//==============================================================================
        // Draw the lines corresponding to the std deviation
        /*
        gl.glColor3f(0,1,1);
        gl.glLineWidth(2);
        gl.glBegin(GL2.GL_LINES);
        //Draw the current point
        gl.glVertex2d(10, 10);      //Origen
        gl.glVertex2d(sxx.getX(), sxx.getY());      //Origen

        gl.glVertex2d(20, 10);      //Origen
        gl.glVertex2d(syy.getX(), syy.getY());      //Origen

        gl.glEnd();
        */

        gl.glColor3f(1,0,0);
        gl.glLineWidth(4);
        gl.glBegin(GL2.GL_LINES);

        //Draw the bounding square
        gl.glVertex2d(t.getX(), t.getY());      //Origen
        gl.glVertex2d(u.getX(), u.getY());      //End point

        gl.glVertex2d(u.getX(), u.getY());      //Origen
        gl.glVertex2d(v.getX(), v.getY());      //End point

        gl.glVertex2d(v.getX(), v.getY());      //Origen
        gl.glVertex2d(w.getX(), w.getY());      //End point

        gl.glVertex2d(w.getX(), w.getY());      //Origen
        gl.glVertex2d(t.getX(), t.getY());      //return to beginning
        gl.glEnd();

        /*
        gl.glColor3f(1,1,0);
        gl.glLineWidth(4);
        gl.glBegin(GL.GL_POINTS);
        gl.glVertex2d(s.getX(), s.getY());
        gl.glEnd( );
         *
         */




        gl.glPopMatrix();
    }

}
