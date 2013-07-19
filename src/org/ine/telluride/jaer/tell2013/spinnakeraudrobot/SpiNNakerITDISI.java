/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ine.telluride.jaer.tell2013.spinnakeraudrobot;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import ch.unizh.ini.jaer.projects.cochsoundloc.ISIFilter;
import ch.unizh.ini.jaer.projects.cochsoundloc.ITDFilter;

/**
 * Combines ITDfilter and ISIFilter to supply heading direction to SpiNNaker
 * using UDP packets to steer robot.
 *
 * @author tobi
 */
@Description("Uses ITDFilter and ISI histograms to supply heading direction to SpiNNaker for Telluride 2013 UNS project")
public class SpiNNakerITDISI extends EventFilter2D {

    private ITDFilter itdFilter = null;
    private ISIFilter isiFilter = null;
    private int bestItdBin = -1;
    private int motorSpeed=35;
    
    /* typedef struct // SDP header
     {
     uchar flags; // Flag byte
     uchar tag; // IP Tag byte
     uchar dest_port_cpu; // Destination Port & CPU
     uchar srce_port_cpu // Source Port & CPU
     ushort dest_addr; // Destination P2P Address
     ushort srce_addr; // Source P2P Address
     } sdp_hdr_t;
     */
    // communication to SpiNNaker
    private int spinnakerPort = getInt("spinnakerPort", 17893); // TODO
    private String spinnakerHost = getString("spinnakerHost", "192.168.240.12");
    private int spinnakerDestCPU = getInt("spinnakerDestCPU", 1);
    private int spinnakerDestPort = getInt("spinnakerDestPort", 1);
    private int spinnakerSrcePort = getInt("spinnakerSrcePort", 7);
    private int spinnakerSrceCPU = getInt("spinnakerSrceCPU", 31);
    private int spinnakerDestAddrX = getInt("spinnakerDestAddrX", 0);
    private int spinnakerDestAddrY = getInt("spinnakerDestAddrY", 0);
    private int spinnakerSrceAddrX = getInt("spinnakerSrceAddrX", 0);
    private int spinnakerSrceAddrY = getInt("spinnakerSrceAddrY", 0);

    /**
     * @return the motorSpeed
     */
    public int getMotorSpeed() {
        return motorSpeed;
    }

    /**
     * @param motorSpeed the motorSpeed to set
     */
    public void setMotorSpeed(int motorSpeed) {
        if(motorSpeed<0)motorSpeed=0; else if(motorSpeed>70)motorSpeed=70;
        this.motorSpeed = motorSpeed;
        putInt("motorSpeed",motorSpeed);
    }

    // spinnaker motor comands
    private enum MotorCommand {

        f, b, l, r, cw, ccw, stop(0);
        int speed = 10;

        MotorCommand(int speed) {
            this.speed = speed;
        }

        MotorCommand() {
        }
    }
    private InetSocketAddress client = null;
    private DatagramChannel channel = null;
    private ByteBuffer byteBuffer = ByteBuffer.allocateDirect(255).order(ByteOrder.LITTLE_ENDIAN);// the buffer to render/process first

    public SpiNNakerITDISI(AEChip chip) {
        super(chip);
        FilterChain filterChain = new FilterChain(chip);
        filterChain.add(itdFilter = new ITDFilter(chip));
        filterChain.add(isiFilter = new ISIFilter(chip));
        setEnclosedFilterChain(filterChain);
        String comm="Communication";
        String behav="Behavior";
        setPropertyTooltip(behav, "motorSpeed", "motor speed 0-70");
    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!checkUDPChannel()) {
            return in;
        }
        getEnclosedFilterChain().filterPacket(in);
        int currentBestItdBin = itdFilter.getBestITD();
        if (currentBestItdBin != bestItdBin) { // only do something if bestItdBin changes
            bestItdBin = currentBestItdBin;
            // here is the business logic
            if (bestItdBin > itdFilter.getNumOfBins() / 2) {
                sendMotorCommand(MotorCommand.cw);
            } else if (bestItdBin < itdFilter.getNumOfBins() / 2) {
                sendMotorCommand(MotorCommand.ccw);
            }
        }
        return in;
    }

    @Override
    public void resetFilter() {
        if(isFilterEnabled())sendMotorCommand(MotorCommand.stop);
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
     * @return the spinnakerPort
     */
    public int getSpinnakerPort() {
        return spinnakerPort;
    }

    /**
     * @param spinnakerPort the spinnakerPort to set
     */
    synchronized public void setSpinnakerPort(int spinnakerPort) {
        if (spinnakerPort != this.spinnakerPort) {
            client = null;
        }
        this.spinnakerPort = spinnakerPort;
        putInt("spinnakerPort", spinnakerPort);
    }

    /**
     * @return the spinnakerHost
     */
    public String getSpinnakerHost() {
        return spinnakerHost;
    }

    /**
     * @param spinnakerHost the spinnakerHost to set
     */
    synchronized public void setSpinnakerHost(String spinnakerHost) {
        if (spinnakerHost != this.spinnakerHost) {
            client = null;
        }
        this.spinnakerHost = spinnakerHost;
        putString("spinnakerHost", spinnakerHost);
    }

    /**
     * @return the spinnakerDestCPU
     */
    public int getSpinnakerDestCPU() {
        return spinnakerDestCPU;
    }

    /**
     * @param spinnakerDestCPU the spinnakerDestCPU to set
     */
    public void setSpinnakerDestCPU(int spinnakerDestCPU) {
        this.spinnakerDestCPU = spinnakerDestCPU;
        putInt("spinnakerDestCPU", spinnakerDestCPU);
    }

    /**
     * @return the spinnakerDestPort
     */
    public int getSpinnakerDestPort() {
        return spinnakerDestPort;
    }

    /**
     * @param spinnakerDestPort the spinnakerDestPort to set
     */
    public void setSpinnakerDestPort(int spinnakerDestPort) {
        this.spinnakerDestPort = spinnakerDestPort;
        putInt("spinnakerDestPort", spinnakerDestPort);
    }

    /**
     * @return the spinnakerSrcePort
     */
    public int getSpinnakerSrcePort() {
        return spinnakerSrcePort;
    }

    /**
     * @param spinnakerSrcePort the spinnakerSrcePort to set
     */
    public void setSpinnakerSrcePort(int spinnakerSrcePort) {
        this.spinnakerSrcePort = spinnakerSrcePort;
        putInt("spinnakerSrcePort", spinnakerSrcePort);
    }

    /**
     * @return the spinnakerSrceCPU
     */
    public int getSpinnakerSrceCPU() {
        return spinnakerSrceCPU;
    }

    /**
     * @param spinnakerSrceCPU the spinnakerSrceCPU to set
     */
    public void setSpinnakerSrceCPU(int spinnakerSrceCPU) {
        this.spinnakerSrceCPU = spinnakerSrceCPU;
        putInt("spinnakerSrceCPU", spinnakerSrceCPU);
    }

    /**
     * @return the spinnakerDestAddr
     */
    public int getSpinnakerDestAddrX() {
        return spinnakerDestAddrX;
    }

    /**
     * @param spinnakerDestAddr the spinnakerDestAddr to set
     */
    public void setSpinnakerDestAddrX(int spinnakerDestAddr) {
        this.spinnakerDestAddrX = spinnakerDestAddr;
        putInt("spinnakerDestAddrX", spinnakerDestAddr);
    }

    /**
     * @return the spinnakerDestAddr
     */
    public int getSpinnakerDestAddrY() {
        return spinnakerDestAddrY;
    }

    /**
     * @param spinnakerDestAddr the spinnakerDestAddr to set
     */
    public void setSpinnakerDestAddrY(int spinnakerDestAddr) {
        this.spinnakerDestAddrY = spinnakerDestAddr;
        putInt("spinnakerDestAddrY", spinnakerDestAddr);
    }

    /**
     * @return the spinnakerSrceAddr
     */
    public int getSpinnakerSrceAddrX() {
        return spinnakerSrceAddrX;
    }

    /**
     * @param spinnakerSrceAddr the spinnakerSrceAddr to set
     */
    public void setSpinnakerSrceAddrX(int spinnakerSrceAddr) {
        this.spinnakerSrceAddrX = spinnakerSrceAddr;
        putInt("spinnakerSrceAddrX", spinnakerSrceAddr);
    }

    /**
     * @return the spinnakerSrceAddr
     */
    public int getSpinnakerSrceAddrY() {
        return spinnakerSrceAddrX;
    }

    /**
     * @param spinnakerSrceAddr the spinnakerSrceAddr to set
     */
    public void setSpinnakerSrceAddrY(int spinnakerSrceAddr) {
        this.spinnakerSrceAddrY = spinnakerSrceAddr;
        putInt("spinnakerSrceAddrY", spinnakerSrceAddr);
    }

    synchronized private void sendMotorCommand(MotorCommand mc) {
        checkUDPChannel();
        byteBuffer.clear();
        // construct SDP header, 8 bytes, according to https://spinnaker.cs.man.ac.uk/tiki-download_wiki_attachment.php?attId=16
        byteBuffer.put((byte) 0); // pad
        byteBuffer.put((byte) 0); // pad
        byteBuffer.put((byte) 0x07); // flags, no reply expected
        byteBuffer.put((byte) 0xff); // tags, not used here since we come from internet
        byteBuffer.put((byte) (0xff & (spinnakerDestPort & 0x7) << 5 | (spinnakerDestCPU & 0x1f)));
        byteBuffer.put((byte) (0xff & (spinnakerSrcePort & 0x7) << 5 | (spinnakerSrceCPU & 0x1f)));
        byteBuffer.put((byte) (0xff & spinnakerDestAddrX));
        byteBuffer.put((byte) (0xff & spinnakerDestAddrY));
        byteBuffer.put((byte) (0xff & spinnakerSrceAddrX));
        byteBuffer.put((byte) (0xff & spinnakerSrceAddrY));

        // next is 4 int32 payload
        byteBuffer.putInt(mc.ordinal());
        byteBuffer.putInt(motorSpeed);
        byteBuffer.putInt(0); // unused for now
        byteBuffer.putInt(0);
        // must flip before transmitting
        byteBuffer.flip();
        try {
            channel.send(byteBuffer, client);
        } catch (IOException ex) {
            log.warning(ex.toString());
        }
    }

    private boolean checkUDPChannel() {
        // check spinnaker UDP socket and construct bound to any available local port if it is null
        if (channel == null || client == null) {
            try {
                channel = DatagramChannel.open();
                client = new InetSocketAddress(spinnakerHost, spinnakerPort);
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
