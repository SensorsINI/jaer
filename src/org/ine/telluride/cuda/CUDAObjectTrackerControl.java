/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ine.telluride.cuda;

import ch.unizh.ini.caviar.chip.AEChip;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.logging.Logger;

/**
 * Allows control of remote CUDA process for filtering jAER data.
 *<p>
 * This filter does not process events at all. Rather it opens a TCP stream socket connection to a remote (maybe on the same machine)
 * process which is a CUDA GPGPU program which accepts AEs from jAER and which sends its output back to jAER for visualization.
 * <p>
 * The stream socket works with text command lines as though it were a terminal.  
 *
 * 
 * @author tobi/yingxue/jay
 */
public class CUDAObjectTrackerControl extends EventFilter2D {

    private int port=getPrefs().getInt("CUDAObjectTrackerControl.port",9998);
    private String hostname=getPrefs().get("CUDAObjectTrackerControl.hostname","localhost");
    static Logger log=Logger.getLogger("CUDAObjectTrackerControl");
    public static final String CUDA_CMD_PARAMETER="param";
    
    static final String CMD_THRESHOLD="threshold";
    private float threshold=getPrefs().getFloat("CUDAObjectTrackerControl.threshold",1f);
    
    private Socket socket=null;
    private OutputStreamWriter writer=null;
    
    public CUDAObjectTrackerControl(AEChip chip) {
        super(chip);
        setPropertyTooltip("hostname", "hostname or IP address of CUDA server");
        setPropertyTooltip("port", "port number of CUDA server TCP control port");
    }

    private boolean checkSocket(){
        if(socket==null){
            try {
                socket = new Socket(getHostname(), getPort());
                writer=new OutputStreamWriter(socket.getOutputStream());
            } catch (Exception ex) {
                log.warning(ex.getMessage()+" to "+hostname+":"+port);
                return false;
            } 
        }
        return true;
    }
    
    /** does reconnect to CUDA server */
    public void doReconnect(){
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ex) {
                log.warning(ex.toString());
            }
            socket = null;
        }
        checkSocket();
    }
    
    private void writeString(String s){
        if(!checkSocket()) return;
        try {
            writer.write(s);
        } catch (IOException ex) {
            log.warning("writing string "+s+" got "+ex);
        }
    }
    
    private void sendParameter(String name, float value){
        String s=String.format(name+" "+value);
        writeString(s);
    }
    
    /** does nothing for this filter */
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!isFilterEnabled()) return in;
//        checkSocket();
        return in;
    }

    @Override
    public Object getFilterState() {
        return null;
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public float getThreshold() {
        return threshold;
    }

    public void setThreshold(float threshold) {
        this.threshold = threshold;
        sendParameter(CMD_THRESHOLD, threshold);
    }
}
