package ch.unizh.ini.jaer.projects.holger;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
//import gnu.io.SerialPortEvent;
//import gnu.io.SerialPortEventListener;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Logger;

/**
 * This version of the TwoWaySerialComm example makes use of the
 * SerialPortEventListener to avoid polling.
 *
 */
public class PanTiltControl
{
    private Logger log = Logger.getLogger("PanTiltControl");
    public OutputStream out;
    public InputStream in;
    private boolean moving = false;
    private byte[] buffer = new byte[1024];
    private boolean connected = false;
    private boolean waitingForStarResponse = false;

    public PanTiltControl()
    {
        super();
    }

    void connect ( String portName ) throws Exception
    {
        CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(portName);
        if ( portIdentifier.isCurrentlyOwned() )
        {
            log.warning("Error: Port for Pan-Tilt-Communication is currently in use");
        }
        else
        {
            CommPort commPort = portIdentifier.open(this.getClass().getName(),2000);

            if ( commPort instanceof SerialPort )
            {
                SerialPort serialPort = (SerialPort) commPort;
                serialPort.setSerialPortParams(9600,SerialPort.DATABITS_8,SerialPort.STOPBITS_1,SerialPort.PARITY_NONE);

                in = serialPort.getInputStream();
                out = serialPort.getOutputStream();

                //(new Thread(new SerialWriter(out))).start();
                //serialPort.addEventListener(new SerialReader(in));
                serialPort.notifyOnDataAvailable(true);
                connected = true;
                
                log.info("Connected to Pan-Tilt-Unit!");

            }
            else
            {
                log.warning("Error: Cannot connect to Pan-Tilt-Unit!");
            }
        }
    }

    public void setPanPos(int pos) {
        String testPanTilt = "PP" + pos + "\nA\n";
        try {
            this.out.write(testPanTilt.getBytes());
            this.moving = true;
        } catch (IOException ex) {
            log.warning("In setPanPos(position) caught IOexception " + ex);
        }
    }

    public String getResponse() {
        int data;
        String response = "";
        try {
            int len = 0;
            while ((data = in.read()) > -1) {
                if (data == '\n') {
                    break;
                }
                buffer[len++] = (byte) data;
            }
            response = new String(buffer, 0, len);
            if (waitingForStarResponse == true && response.contains("*")) {
                this.moving = false;
                this.waitingForStarResponse = false;
                //log.info("Movement is done!");
            }
            else if (response.equalsIgnoreCase("A")) {
                waitingForStarResponse = true;
                //log.info("Waiting for the next star response!");
            }
            
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }
        return response;
    }

    /**
     * @return the isMoving
     */
    public boolean isMoving() {
        return moving;
    }

    /**
     * @return the isConnected
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Handles the input coming from the serial port. A new line character
     * is treated as the end of a block in this example.
     */
//    public static class SerialReader implements SerialPortEventListener
//    {
//        private InputStream in;
//        private byte[] buffer = new byte[1024];
//
//        public SerialReader ( InputStream in )
//        {
//            this.in = in;
//        }
//
//        public void serialEvent(SerialPortEvent arg0) {
//            int data;
//
//            try
//            {
//                int len = 0;
//                while ( ( data = in.read()) > -1 )
//                {
//                    if ( data == '\n' ) {
//                        break;
//                    }
//                    buffer[len++] = (byte) data;
//                }
//                System.out.print(new String(buffer,0,len));
//            }
//            catch ( IOException e )
//            {
//                e.printStackTrace();
//                System.exit(-1);
//            }
//        }
//
//    }

//    /** */
//    public static class SerialWriter implements Runnable
//    {
//        OutputStream out;
//
//        public SerialWriter ( OutputStream out )
//        {
//            this.out = out;
//        }
//
//        public void run ()
//        {
//            try
//            {
//                int c = 0;
//                while ( ( c = System.in.read()) > -1 )
//                {
//                    this.out.write(c);
//                }
//            }
//            catch ( IOException e )
//            {
//                e.printStackTrace();
//                System.exit(-1);
//            }
//        }
//    }


//
//    public static void main ( String[] args )
//    {
//        System.out.println("test");
//        try
//        {
//            (new PanTiltControl()).connect("COM9");
//        }
//        catch ( Exception e )
//        {
//            // TODO Auto-generated catch block
//            e.printStackTrace();
//        }
//    }


}
