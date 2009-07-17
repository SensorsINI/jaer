package org.ine.telluride.jaer.eyeinthesky;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.*;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GL;
import java.awt.*;

/**
 * User: jauerbac
 * Date: Jul 14, 2009
 * Time: 12:54:54 PM
 */
public class JoshCrossTracker extends EventFilter2D implements FrameAnnotater {

    private int power = getPrefs().getInt("JoshCrossTracker.power", 8);
    private int ignoreRadius = getPrefs().getInt("JoshCrossTracker.ignoreRadius", 10);
    private float flipSlope = getPrefs().getFloat("JoshCrossTracker.flipSlope", 1.1f);

    private double coefficients[][];

    private double m,x1,y1,length;

    private boolean xHorizontal = true;
    private double maxDist;

    public JoshCrossTracker(AEChip chip) {
        super(chip);
        resetFilter();
    }

    public Object getFilterState() {
        return null;
    }

    public void resetFilter() {
        coefficients = new double[3][9];
        length = 50;

        //start near center
        coefficients[0][0]=4487.929789503349;
        coefficients[0][1]=-5880.26899138613;
        coefficients[0][2]=1.747913285251941E-164;
        coefficients[0][3]=1.2319050791956217E-164;
        coefficients[0][4]=0.9999999999999944;
        coefficients[0][5]=-91.79054108226751;
        coefficients[0][6]=9.747673002237948E-165;
        coefficients[0][7]=124.71706338316264;
        coefficients[0][8]=2123.9070962374158;
        coefficients[1][0]=4176.715697500955;
        coefficients[1][1]=9052.402841852214;
        coefficients[1][2]=0.9999999999999944;
        coefficients[1][3]=-156.8891673679832;
        coefficients[1][4]=1.5610213512990959E-62;
        coefficients[1][5]=1.5428048695524322E-62;
        coefficients[1][6]=-118.08915361547771;
        coefficients[1][7]=4.588689436053689E-63;
        coefficients[1][8]=6179.080184987505;

        updatePrediction();
    }

    public void initFilter() {
    }


    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!isFilterEnabled()) return in;
        if(maxDist == 0)
            maxDist = Math.sqrt(chip.getSizeX() * chip.getSizeX() + chip.getSizeY() * chip.getSizeY());

        checkOutputPacketEventType(PolarityEvent.class);
        OutputEventIterator outItr= out.outputIterator();
        for(Object o :  in) {

            PolarityEvent ein = (PolarityEvent) o;
            PolarityEvent eout = (PolarityEvent) outItr.nextOutput();
            if(updateCoefficients(ein))
                ein.polarity = PolarityEvent.Polarity.On;
            else
                ein.polarity = PolarityEvent.Polarity.Off;
            eout.copyFrom(ein);

        }
        return out;

    }

    public void annotate(float[][][] frame) {}
    public void annotate(Graphics2D g) {}

    public void annotate(GLAutoDrawable drawable) {
         if (!isAnnotationEnabled()) {
            return;
        }
        GL gl = drawable.getGL();
        gl.glBegin((GL.GL_LINES));
        gl.glColor3d(1, 0, 0);

        double c[] = getCenter();
        double c1 = c[0];
        double c2 = c[1];

        double xBottom,xTop,yLeft,yRight;

        double xOffset, yOffset;
        if(xHorizontal) {
            xOffset = (length)/Math.sqrt(1 + (-1/m)*(-1/m));
            yOffset = xOffset * -1/m;
        } else {
            yOffset = (length)/Math.sqrt(1 +  (-1/m)*(-1/m));
            xOffset = yOffset * -1/m;
        }

        xBottom = c1 - xOffset;
        xTop = c1 + xOffset;
        yLeft = c2 - yOffset;
        yRight = c2 + yOffset;

        gl.glVertex2d(xBottom, yLeft);
        gl.glVertex2d(xTop, yRight);
        gl.glEnd();

        gl.glBegin((GL.GL_LINES));
        gl.glColor3d(0, 0, 1);

        if(xHorizontal) {
            xOffset = (length)/Math.sqrt(1 + m*m);
            yOffset = xOffset * m;
        } else {
            yOffset = (length)/Math.sqrt(1 + m*m);
            xOffset = yOffset * m;
        }

        xBottom = c1 - xOffset;
        xTop = c1 + xOffset;
        yLeft = c2 - yOffset;
        yRight = c2 + yOffset;

        gl.glVertex2d(xBottom, yLeft);
        gl.glVertex2d(xTop, yRight);
        gl.glEnd();


        drawCircle(gl, c1, c2);

    }

    private boolean updateCoefficients(PolarityEvent e) {
        double var1 = xHorizontal ? e.x : e.y;
        double var2 = xHorizontal ? e.y : e.x;

        //calculate distance from the current point to both of the two lines
        double A1,B1,C1,A2,B2,C2;
        A1 = m;
        B1 = -1;
        C1 = y1;
        A2 = 1;
        B2 = m;
        C2 = -x1;

        double dist1 = Math.abs(A1 * var1 + B1 * var2 + C1)/Math.sqrt(A1*A1 + B1*B1);
        double dist2 = Math.abs(A2 * var1 + B2 * var2 + C2)/Math.sqrt(A2*A2 + B2*B2);

        //get the coordinates of the center
        double c[] = getCenter();
        double c1 = c[0];
        double c2 = c[1];

        //calculate distance to the center
        double distC = Math.sqrt(Math.pow(e.x - c1,2) + Math.pow(e.y - c2,2));

//      PROJECTED DISTANCE - NOT CURRENTLY BEING USED, SO COMMENTED OUT
//        double projectedDistance = Math.sqrt(Math.pow(distC,2) - Math.pow(Math.min(dist1,dist2),2));
//        if(Math.pow(distC,2) - Math.pow(Math.min(dist1,dist2),2) < 0) {
//            return false;
//        }

        //if too close to center or too far, ignore event
        if( (distC <= ignoreRadius) || (distC > ((length) + ignoreRadius)) )
            return false;


        double weight, iWeight;

        if(dist1 <= dist2) {
            //if closer to the first line, update those coefficients
            weight = 0.01 * Math.pow((maxDist - dist1)/maxDist,power);
            iWeight = 1.0 - weight;

            coefficients[0][0] = iWeight * coefficients[0][0] + (weight * var1 * var1) ;
            coefficients[0][1] = iWeight * coefficients[0][1] + (weight * -2 * var1 * var2) ;
            coefficients[0][2] = iWeight * coefficients[0][2];
            coefficients[0][3] = iWeight * coefficients[0][3];
            coefficients[0][4] = iWeight * coefficients[0][4] + weight ;
            coefficients[0][5] = iWeight * coefficients[0][5] + (weight * -2 * var2) ;
            coefficients[0][6] = iWeight * coefficients[0][6];
            coefficients[0][7] = iWeight * coefficients[0][7] + (weight * 2 * var1) ;
            coefficients[0][8] = iWeight * coefficients[0][8] + (weight * var2 * var2) ;
        } else {
            //otherwise update coefficients for second line
            weight = 0.01 * Math.pow((maxDist - dist2)/maxDist,power);
            iWeight = 1.0 - weight;
            coefficients[1][0] = iWeight * coefficients[1][0] + (weight * var2 * var2) ;
            coefficients[1][1] = iWeight * coefficients[1][1] + (weight * 2 * var1 * var2) ;
            coefficients[1][2] = iWeight * coefficients[1][2] + weight ;
            coefficients[1][3] = iWeight * coefficients[1][3] + (weight * -2 * var1) ;
            coefficients[1][4] = iWeight * coefficients[1][4];
            coefficients[1][5] = iWeight * coefficients[1][5];
            coefficients[1][6] = iWeight * coefficients[1][6] + (weight * -2  * var2) ;
            coefficients[1][7] = iWeight * coefficients[1][7];
            coefficients[1][8] = iWeight * coefficients[1][8] + (weight * var1 * var1) ;
        }
        weight = 0.05 * weight;  //update length prediction more slowly then other parameters
        iWeight = 1.0 - weight;
        length = iWeight * length + weight * ((2.0*distC)-(double)ignoreRadius); //calculate length (based on fact we ignore events too close to center)

        if(Math.abs(m) > flipSlope) {
            log.info(("FLIP!")); //flipping the coordinate frame rotate counter clockwise 90 deg then reflect across y-axis

            double temp = coefficients[0][0];
            coefficients[0][0] = coefficients[0][8];
            coefficients[0][8] = temp;
            temp = coefficients[0][5];
            coefficients[0][5] = -coefficients[0][7];
            coefficients[0][7] = -temp;

            temp = coefficients[1][0];
            coefficients[1][0] = coefficients[1][8];
            coefficients[1][8] = temp;
            temp = coefficients[1][3];
            coefficients[1][3] = coefficients[1][6];
            coefficients[1][6] = temp;

            xHorizontal = !xHorizontal;

        }

        updatePrediction();
        return true;
    }

    private void updatePrediction() {
        //combines coefficient contributions from both lines, weighting them equally
        double weight1 = 0.5;
        double weight2 = 0.5;

        for(int i = 0; i<coefficients[0].length; i++) {
            coefficients[2][i] = weight1*coefficients[0][i] + weight2*coefficients[1][i];
        }

        //now update predictions from coefficients -- derived using sage math
        m = -(2*coefficients[2][1]*coefficients[2][2]*coefficients[2][4] - coefficients[2][2]*coefficients[2][5]*coefficients[2][7] - coefficients[2][3]*coefficients[2][4]*coefficients[2][6])/(4*coefficients[2][0]*coefficients[2][2]*coefficients[2][4] - coefficients[2][2]*coefficients[2][7]*coefficients[2][7] - coefficients[2][4]*coefficients[2][6]*coefficients[2][6]);
        x1 = -1.0/2.0*(4*coefficients[2][0]*coefficients[2][3]*coefficients[2][4] - 2*coefficients[2][1]*coefficients[2][4]*coefficients[2][6] - coefficients[2][3]*Math.pow(coefficients[2][7],2) + coefficients[2][5]*coefficients[2][6]*coefficients[2][7])/(4*coefficients[2][0]*coefficients[2][2]*coefficients[2][4] - coefficients[2][2]*Math.pow(coefficients[2][7],2) - coefficients[2][4]*Math.pow(coefficients[2][6],2));
        y1 = -1.0/2.0*(4*coefficients[2][0]*coefficients[2][2]*coefficients[2][5] - coefficients[2][5]*Math.pow(coefficients[2][6],2) - (2*coefficients[2][1]*coefficients[2][2] - coefficients[2][3]*coefficients[2][6])*coefficients[2][7])/(4*coefficients[2][0]*coefficients[2][2]*coefficients[2][4] - coefficients[2][2]*Math.pow(coefficients[2][7],2) - coefficients[2][4]*Math.pow(coefficients[2][6],2));
    }

    private double[] getCenter() {
        //determine coordinates of center
        double c[] = new double[2];
        if(xHorizontal) {
            c[0] = (x1/m - y1) / (m + 1.0/m);
            c[1] = m*c[0]+y1;
        } else {
            c[1] = (x1/m - y1) / (m + 1.0/m);
            c[0] = m*c[1]+y1;
        }
        return c;
    }

    private static void drawCircle( GL gl, double xc, double yc ) {
        double r = 2; // Radius.

        gl.glBegin( GL.GL_TRIANGLE_FAN );
        gl.glColor3d(0,1,1);

        gl.glVertex2d( xc, yc ); // Center.

        for(double a = 0;  a <= 360; a+=60 ) {
            double ang = Math.toRadians( a );
            double x = xc + (r*Math.cos( ang ));
            double y = yc + (r*Math.sin( ang ));
            gl.glVertex2d( x, y );
        }
        gl.glEnd();
    }

    public int getPower() {
        return power;
    }

    public void setPower(int power) {
        this.power = power;
    }

    public int getIgnoreRadius() {
        return ignoreRadius;
    }

    public float getFlipSlope() {
        return flipSlope;
    }

    public void setFlipSlope(float flipSlope) {
        this.flipSlope = flipSlope;
    }

    public void setIgnoreRadius(int ignoreRadius) {
        this.ignoreRadius = ignoreRadius;
    }

}

