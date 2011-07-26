package net.sf.jaer.hardwareinterface.serial.edvsviewer;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.util.Arrays;

public class EP_CrossTrackerBGFilter extends EventProcessor {

    private int power = 8;				// getPrefs().getInt("JoshCrossTracker.power", 8);
    private int ignoreRadius = 10;		// getPrefs().getInt("JoshCrossTracker.ignoreRadius", 10);
    private float flipSlope = 1.2f;		// getPrefs().getFloat("JoshCrossTracker.flipSlope", 1.1f);

    private double coefficients[][];
    private double m,x1,y1,length;
    private boolean xHorizontal = true;
    private double maxDist = Math.sqrt(128 * 128 + 128 * 128);


    protected int dt=100;
    long[][] lastTimestamps = new long[128][128];
    long ts=0; // used to reset filter


	public void init() {
		isActive.setText("CrossTrackerBGFilter");
		
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
        
        for(int i=0;i<lastTimestamps.length;i++)
            Arrays.fill(lastTimestamps[i], 0);

	}

	public int processNewEvent(int eventX, int eventY, int eventP) {
		// do Something (eventX, eventY, 0);

		ts=System.currentTimeMillis();

        // for each event stuff the event's timestamp into the lastTimestamps array at neighboring locations
        // lastTimestamps[x][y][type]=ts; // don't write to ourselves, we need support from neighbor for next event
        // bounds checking here to avoid throwing expensive exceptions, even though we duplicate java's bound checking...
        if(eventX>0)   lastTimestamps[eventX-1][eventY]=ts;
        if(eventX<127) lastTimestamps[eventX+1][eventY]=ts;
        if(eventY>0)   lastTimestamps[eventX][eventY-1]=ts;
        if(eventY<127) lastTimestamps[eventX][eventY+1]=ts;
        if(eventX>0   && eventY>0)   lastTimestamps[eventX-1][eventY-1]=ts;
        if(eventX<127 && eventY<127) lastTimestamps[eventX+1][eventY+1]=ts;
        if(eventX>0   && eventY<127) lastTimestamps[eventX-1][eventY+1]=ts;
        if(eventX<127 && eventY>0)   lastTimestamps[eventX+1][eventY-1]=ts;

        long lastt=lastTimestamps[eventX][eventY];
        long deltat=(ts-lastt);
        if(deltat>dt){
        	return(0);
        }

        double var1 = xHorizontal ? eventX : eventY;
        double var2 = xHorizontal ? eventY : eventX;

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
        double distC = Math.sqrt(Math.pow(eventX - c1,2) + Math.pow(eventY - c2,2));

//      PROJECTED DISTANCE - NOT CURRENTLY BEING USED, SO COMMENTED OUT
//        double projectedDistance = Math.sqrt(Math.pow(distC,2) - Math.pow(Math.min(dist1,dist2),2));
//        if(Math.pow(distC,2) - Math.pow(Math.min(dist1,dist2),2) < 0) {
//            return false;
//        }

        //if too close to center or too far, ignore event
        if( (distC <= ignoreRadius) || (distC > ((length) + ignoreRadius)) )
            return(0);


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
        weight = 0.1 * weight;  //update length prediction more slowly then other parameters
        iWeight = 1.0 - weight;
        length = iWeight * length + weight * ((2.0*distC)-(double)ignoreRadius); //calculate length (based on fact we ignore events too close to center)

        if(Math.abs(m) > flipSlope) {

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
        return(0);
	}

	public void processSpecialData(String specialData) {
	}

	public void paintComponent(Graphics g) {
//		g.setColor(Color.cyan);
//		g.drawLine(4*((int) lowX), 0, 4*((int) highX), 4*127);

		
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

		g.setColor(Color.blue);
        g.drawLine(((int) (4*xBottom)), ((int) (4*yLeft)), ((int) (4*xTop)), ((int) (4*yRight)));


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

        g.drawLine(((int) (4*xBottom)), ((int) (4*yLeft)), ((int) (4*xTop)), ((int) (4*yRight)));


        g.fillArc(((int) (4*c1-10)), ((int) (4*c2-10)), 20, 20, 0, 360);
        g.drawArc(((int) (4*(c1-length))), ((int) (4*(c2-length))), ((int) (4*2*length)), ((int) (4*2*length)), 0, 360);
	}

	public void callBackButtonPressed(ActionEvent e) {
		System.out.println("CallBack!");
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

}
