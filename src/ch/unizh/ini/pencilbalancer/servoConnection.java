/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.pencilbalancer;

/**
 *
 * @author conradt
 */
public class servoConnection {

    private static servoConnection instance = null;
    private HWP_RS232 rs232Port = null;
    private boolean isConnectedToServo = false;
    private double currentTablePosX,  currentTablePosY;
    private double baseX,  slopeX;
    private double baseY,  slopeY;
    private double gainAngle,  gainBase;
    private double gainMotion, motionDecay;
    private double[] gain;
    private double offsetX,  offsetY;
    private int lastTime = 0;
    private boolean resendRequired = false;

    public void updateParameter(float gainAngle, float gainBase, double offsetX, double offsetY, double gainMotion, double motionDecay) {
        this.gainAngle = gainAngle;
        this.gainBase = gainBase;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.gainMotion = gainMotion;
        this.motionDecay = motionDecay;
    }
    
    public void updateParameterGain(int which, double newValue) {
        this.gain[which] = newValue;
    }

    public synchronized static servoConnection getInstance() {
        if (instance == null) {
            System.out.println("Creating new ServoConnectionInstance");
            instance = new servoConnection();
        }
        System.out.println("Returning existing ServoConnection");
        return (instance);
    }

    private servoConnection() {
        System.out.println("Setting up connection to servo board");

        rs232Port = new HWP_RS232();

        connectServo(false);

        currentTablePosX = 0.0;
        currentTablePosY = 0.0;
        
        gain = new double[20];
    }

    public synchronized boolean isConnected() {
        return (isConnectedToServo);
    }

    public synchronized void connectServo(boolean connectFlag) {
        if (connectFlag == false) {
            rs232Port.sendCommand("-");
            rs232Port.sendCommand("!D-");  // turn off debug output
            System.out.println("diconnecting!");
            rs232Port.close();
            isConnectedToServo = false;
        } else {
            if (isConnectedToServo == false) {
                HWPort.PortIdentifier thisPI = null;
                HWPort.PortAttribute thisPA = null;

//                System.out.print("Available hardware ports:");
                for (HWPort.PortIdentifier pi : rs232Port.getPortIdentifierList()) {
//                    System.out.print(" -" + pi.display + "- ");
                    if ((pi.display).equals("  COM3")) {
//                        System.out.print("*");
                        thisPI = pi;
                    }
                }
//                System.out.print("\nAvailable Attributes:");
                for (HWPort.PortAttribute pa : rs232Port.getPortAttributeList()) {
//                    System.out.print(" " + pa.display);
                    if ((pa.display).equals("  2000000Bd")) {
//                        System.out.print("*");
                        thisPA = pa;
                    }
                }

                if ((thisPI != null) && (thisPA != null)) {
                    System.out.println("Opening Port " + thisPI.getID() + " with attribute " + thisPA);
                    rs232Port.open(((String) thisPI.getID()), thisPA);
                    rs232Port.setHardwareFlowControl(true);

                    isConnectedToServo = true;

//                    rs232Port.sendCommand("!D5");  // !Dx means enable debug output every 2ms * x
//                    rs232Port.sendCommand("!D+");  // enable debug output
                    rs232Port.sendCommand("");     // send a dummy return to clear pending input
                    rs232Port.sendCommand("+");    // enable servo control
                    rs232Port.sendCommand("!S+2"); // reply only errors and debug output
                } else {
                    System.out.println("\nError, could not find proper port or baud-rate\n");
                    isConnectedToServo = false;
                }
            }
        }
    }

    public synchronized void setBaseSlope(double newBaseX, double newSlopeX, double newBaseY, double newSlopeY, int time) {
        baseX = newBaseX;
        slopeX = newSlopeX;
        baseY = newBaseY;
        slopeY = newSlopeY;
        updateTablePosition();
        if (Math.abs(time - lastTime) > 2000) {         //timestamps might reset to zero, so check abs_diff
            resendRequired = true;
            lastTime = time;
        }
    }

    private double slowx0 = 0,  slowx1 = 0,  slowy0 = 0,  slowy1 = 0;

    public synchronized void updateTablePosition() {
        if ((rs232Port != null) && (resendRequired)) {
            resendRequired = false;
        } else {
            return;
        }


        // First, let's compensate for the perspective problem.
        // The stick is really at (x,y,z)=(x0+t*x1,y0+t*y1,t)
        // (there are many formulas, but this is a nice one when not horizontal)
        // and when we see it from (0,-yr,0), we see
        // every point projected onto (x0+t*x1,y0+t*y1+yr,t)/(y0+t*y1+yr).
        // So, we are given four numbers from the two retinas,
        // which is exactly the number of degrees of freedom of a line.
        // Specifically, from retinas at (0,-yr,0) and from (xr,0,0) we see
        //       (x,z)=  ( (x0+t*x1)/(y0+t*y1+yr) ,  t/(y0+t*y1+yr) )
        //   and (y,z)=  ( (y0+t*y1)/(xr-x0-t*x1) ,  t/(xr-x0-t*x1) )
        // which we receive as a base and slope, for example
        //   base b1 = (x0/(y0+yr), 0)  and slope s1=dx/dz = x1-x0*y1/(y0+yr)
        //   base b2 = (y0/(xr-x0), 0)  and slope s2=dy/dz = y1+y0*x1/(xr-x0)
        // If we solve these for x0,x1,y0,y1 in terms of b1,s1,b2,s2, we get
        //   x0 = (b1*yr + b1*b2*xr) / (b1*b2+1)
        //   x1 = (s1 + b1*s2) / (b1*b2+1)
        //   y0 = (b2*xr - b1*b2*yr) / (b1*b2+1)
        //   y1 = (s2 - b2*s1) / (b1*b2+1)
        // Well that is very nice!  Let's calculate it!
        // First, we have to convert to the coordinate system with origin at the
        // crossing point of the axes of the retinas, somewhat above table center.
        double xr = 450;
        double yr = 450; // I hope we can optimize these over time!
        double b1 = ((baseX - 63.5) + 63.5 * slopeX) / xr;
        double b2 = ((baseY - 63.5) + 63.5 * slopeY) / yr;
        double s1 = slopeX;
        double s2 = slopeY;
        double den = 1.0 / (b1 * b2 + 1.0);
        double x0 = (b1 * yr + b1 * b2 * xr) * den;
        double x1 = (s1 + b1 * s2) * den;
        double y0 = (b2 * xr - b1 * b2 * yr) * den;
        double y1 = (s2 - b2 * s1) * den;
        // There, now we have our 3D line, aren't we happy!
        // It is in units corresponding to pixel widths at the "origin", roughly mm.
        //if (++printcounter % 100 == 0) {
        //    System.out.printf("x0 = %f, x1 = %f, y0 = %f, y1 = %f\n", x0,x1,y0,y1);
        //}

        // estimate an average of recent motion (in pixels/call)
        double newx0 = motionDecay * slowx0 + (1 - motionDecay) * x0;
        double newx1 = motionDecay * slowx1 + (1 - motionDecay) * x1;
        double newy0 = motionDecay * slowy0 + (1 - motionDecay) * y0;
        double newy1 = motionDecay * slowy1 + (1 - motionDecay) * y1;
        double dx0 = newx0 - slowx0;
        double dx1 = newx1 - slowx1;
        double dy0 = newy0 - slowy0;
        double dy1 = newy1 - slowy1;
        slowx0 = newx0; slowx1 = newx1; slowy0 = newy0; slowy1 = newy1;

        // Ok, now do some dumb heuristic...
        double tableX = (x0 + gainAngle * x1) * gainBase + offsetX + gainMotion * dx0;
        double tableY = (y0 + gainAngle * y1) * gainBase + offsetY + gainMotion * dy0;

        // tableX and tableY are now in [-50,50] (roughly millimeters)
        // and we can send them to the table
        tableX = Math.round(10.0 * tableX) / 10.0;
        tableY = -Math.round(10.0 * tableY) / 10.0;

        //only send when position has changed
        if ((tableX != currentTablePosX) || (tableY != currentTablePosY)) {

            currentTablePosX = tableX;
            currentTablePosY = tableY;

            double factor = 1.0;
            String command = "!T" + Math.round(factor * 10.0 * tableX) + "," + Math.round(factor * 10.0 * tableY);
//                  System.out.println("Sending " + command);
            rs232Port.sendCommand(command);

            String r = "";
            int count = 0;
            while (r != null) {
                r = rs232Port.readLine();       // clear in buffer
                if (r != null) {
                    if (r.length() > 3) {
                        count++;
//                        System.out.print("" + tableX + " " + tableY + " " + r);
                    }
                }
            }
        }
    }

    public synchronized void sendCommand(String command) {
        if (rs232Port != null) {

            rs232Port.sendCommand(command);
//            rs232Port.flushOutput();

            String r = "";
            while (r != null) {
                r = rs232Port.readLine();       // clear in buffer
//                    System.out.println("received " + r);
            }
        }
    }

    public synchronized void closeAndTerminate() {
        rs232Port.close();
        rs232Port = null;
        instance = null;            // restart from scratch
    }
}
