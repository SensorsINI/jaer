/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ine.telluride.jaer.tell2013.spinnakeraudrobot;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 * Sends motor commands to Jorg Conradt lab's OmniRobot.
 *
 * @author tobi
 */
@Description("Sends motor commands to Jorg Conradt lab's OmniRobot.")
public class OmniRobotControl extends EventFilter2D {
    private int robotPort = getInt("robotPort", 56236); // telluride 2013 timmer robot
    private String robotHost = getString("robotHost", "10.1.94.236");
    private int speed=35;

    public OmniRobotControl(AEChip chip) {
        super(chip);
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        return in;
    }

    /**
     * @return the speed
     */
    public int getSpeed() {
        return speed;
    }

    /**
     * @param speed the speed to set
     */
    public void setSpeed(int speed) {
        if(speed<0)speed=0; else if(speed>70)speed=70;
        this.speed = speed;
    }

    // spinnaker motor comands
    public enum MotorCommand {

        f(1,0,0), b(-1,0,0), l(-1,0,0), r(1,0,0), cw(0,0,-1), ccw(0,0,1), stop(0,0,0);
        int x,y,a;
        
        MotorCommand(int x, int y, int a) {
            this.x=x;
            this.y=y;
            this.a=a;
        }

    }
    private InetSocketAddress client = null;
    private DatagramChannel channel = null;

    @Override
    public void resetFilter() {
        if (isFilterEnabled()) {
            sendMotorCommand(MotorCommand.stop);
        }
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        if (!yes) {
            sendMotorCommand(MotorCommand.stop);
        }
    }

    @Override
    public void initFilter() {
    }

    /**
     * @return the robotPort
     */
    public int getRobotPort() {
        return robotPort;
    }

    /**
     * @param robotPort the robotPort to set
     */
    synchronized public void setRobotPort(int robotPort) {
        if (robotPort != this.robotPort) {
            client = null;
        }
        this.robotPort = robotPort;
        putInt("robotPort", robotPort);
    }

    /**
     * @return the robotHost
     */
    public String getRobotHost() {
        return robotHost;
    }

    /**
     * @param robotHost the robotHost to set
     */
    synchronized public void setRobotHost(String robotHost) {
        if (robotHost != this.robotHost) {
            client = null;
        }
        this.robotHost = robotHost;
        putString("robotHost", robotHost);
    }
    /*
     * N-OmniRob Control, V3.0 beta: Apr 26 2013, 11:18:15
     * Commands must be terminated with CR or LF in UDP packet
     Commands:
     ?I[g/a/e/c/b/w/A] - get gyro(g), accelero(a),euler(e),
     compass(c),bump(b),wheelVel(w) or all(A)
     !I[1/0],f,data  - Set SD Stream: On/Off, frequency, data
 
     !E[1,2,3]       - 1: All answ. on(default), 2: Cmd Echo off, 3: All answ. off
     !Sx=y[,x=y,...] - set motor x[0..2] to signal y[-03200..+03200] in us
     ?S              - get current motor signals
 
     !C=y            - set PWM total cycle length to y[0..65535] in us
     ?C              - get PWM total cycle length in us
 
     !V+/-           - enable/dis. velocity control (default: +)
     !Vx=y[,x=y,...] - set velocity of motor x[0..2,a] to y [r/m]
     ?Vs             - get set velocity of all motors
     ?Vm             - get meas. velocity of all motors [r/m]
 
     !Dx,y,a         - drive towards x(fwd), y(swd); a(rot) [-70...70]
     ?D              - get x,y,rot
 
     !W0,1,2         - set position of wheel 0,1,2 [deg]
     ?Wd             - get desired int. wheel angles [deg]
     ?Wi             - get current int. wheel position [deg]
 
     ?T              - get bit-pattern of touch-sensors [0..63]
     ?B              - get battery Voltage
     B,BB            - beep
 
     R               - reset board
     ??              - help
 
 
     --
     Jörg Conradt
     Prof. of Neuroscientific System Theory      www.nst.ei.tum.de
     Cluster "Cognition for Technical Systems"   www.cotesys.org
     Institute of Automatic Control Engineering  www.lsr.ei.tum.de
     Technische Universität München, Karlstr. 45, 80333 Munich, Germany
     Tel: +49 89 289-26902, Fax: +49 89 289-26901, E-mail: conradt@tum.de
     */

    synchronized public void sendMotorCommand(int x, int y, int a) {
            checkUDPChannel();
            String cmd=String.format("!D%d,%d,%d\n",x,y,a);
            byte[] b=cmd.getBytes();
            ByteBuffer byteBuffer=ByteBuffer.wrap(b);
            try {
                channel.send(byteBuffer, client);
            } catch (IOException ex) {
                log.warning(ex.toString());
            }
    }
    
   synchronized public void sendMotorCommand(MotorCommand mc) {
       int x=mc.x*speed,y=mc.y*speed,a=mc.a*speed;
       sendMotorCommand(x, y, a);
    }
    

    private boolean checkUDPChannel() {
        // check spinnaker UDP socket and construct bound to any available local port if it is null
        if (channel == null || client == null) {
            try {
                channel = DatagramChannel.open();
                client = new InetSocketAddress(robotHost, robotPort);
            } catch (IOException ex) {
                log.warning(ex.toString());
                return false;
            }
        }
        return true;
    }

    public void doStop() {
        sendMotorCommand(MotorCommand.stop);
    }

    public void doRight() {
        sendMotorCommand(MotorCommand.r);
    }

    public void doLeft() {
        sendMotorCommand(MotorCommand.l);
    }

    public void doFwd() {
        sendMotorCommand(MotorCommand.f);
    }

    public void doBack() {
        sendMotorCommand(MotorCommand.b);
    }

    public void doCW() {
        sendMotorCommand(MotorCommand.cw);
    }

    public void doCCW() {
        sendMotorCommand(MotorCommand.ccw);
    }
}
