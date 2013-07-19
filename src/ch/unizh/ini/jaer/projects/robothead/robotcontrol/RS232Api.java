package ch.unizh.ini.jaer.projects.robothead.robotcontrol;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Vector;

import javax.comm.CommPortIdentifier;
/// TO DO
///  Forget about Message class... read string length with length() function
///  and only return/pass String
import javax.comm.PortInUseException;
import javax.comm.SerialPort;
import javax.comm.UnsupportedCommOperationException;


/*
 * RS232.java
 *
 * Created on 27. November 2006, 15:07
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

public class RS232Api {
    CommPortIdentifier PortId=null;  //Mark which Port we are talking about
    SerialPort sPort=null;
    InputStream Input=null;
    OutputStream Output=null;
    Vector PortList=new Vector();    //List of allavailable comm ports (static would be sufficient...)
    
    /** Creates a new instance of RS232 */
    public RS232Api() {
    }
   
    public Vector portEnum(){
       CommPortIdentifier pId=null;
       Enumeration pList=null;
       
       pList = CommPortIdentifier.getPortIdentifiers();   //get list of all awailable ports     
       while (pList.hasMoreElements()) {                  //cycle through solutions
            pId = (CommPortIdentifier) pList.nextElement();
            if (pId.getPortType() == CommPortIdentifier.PORT_SERIAL){   //only take serial ports (RS232)
                PortList.add(pId);
            }
       }
       return PortList;
    }
    public boolean startComm(String pName, int Baud, int Data, int Parity, int Stop){
        int i=0;
        boolean Success=false;
        Vector pList=PortList;
        CommPortIdentifier pId=null;
        
        portEnum();
        
        for (i=0;i<pList.size();i++) {
            pId = (CommPortIdentifier) pList.get(i);
            if (pId.getName().compareTo(pName)==0){
                try {
                    if (sPort!=null)
                    {
                        sPort.close();
                        sPort=null;
                    }
                    PortId=pId;
                    sPort = (SerialPort)pId.open("RS232Api", 1000); 
                    Success = setParams(sPort, Baud, Data, Parity, Stop);
                    sPort.setDTR(false);    //do not reset uC permanentely        
                    sPort.setRTS(false);    //do not accidentally set into programming mode
                    
                    Input = sPort.getInputStream();
                    Output = sPort.getOutputStream();
                } 
                catch (PortInUseException ex) {
//                    ex.printStackTrace();
                    System.out.println("Port "+pName+" is in use");
                    Success=false;
                } 
                catch (IOException ex) {
//                    ex.printStackTrace();
                    System.out.println("I/O exception at port "+pName);
                    Success=false;
                }
            }
        }
        
        if (!Success)
        {  
            try {
                if (Input!=null) Input.close();
                if (Output!=null) Output.close();
            } catch (IOException ex) {
//                ex.printStackTrace();
                System.out.println("I/O exception while error in opening port "+pName);
            }
            if (sPort!=null) sPort.close();
            System.out.println("Cannot open or init port: "+pName+": baud="+Baud+" data="+Data+" parity="+Parity+" stop="+Stop);
            PortId=null; Input=null; Output=null; sPort=null;
        }
        else
        {
            System.out.println("Port successfully initialized at: "+pName+": baud="+Baud+" data="+Data+" parity="+Parity+" stop="+Stop);
        }
        return Success;
    }
    
    public  void stopComm(){
        if (PortId!=null) System.out.println("Port " + PortId.getName() + " disconnected.");
        try {
            if (Input!=null) Input.close();
            if (Output!=null) Output.close();
        } catch (IOException ex) {
//            ex.printStackTrace();
              System.out.print("I/O exception while closing port ");
              if (PortId!=null) System.out.print(PortId.getName());
              System.out.println("Check if port is closed cleanly");
        }
        if (sPort!=null) sPort.close();
        PortId=null; Input=null; Output=null; sPort=null;
  
    }
    
    public  String getString(short TimeOut, long MaxBytes){
        int CharBuffer;
        int i; int Timer = 0;
        String Buffer = "";
        
        if (sPort==null){
            System.out.println("Not connected. Cannot read port");
            return Buffer;
        }
        
        try {
            
            sPort.enableReceiveTimeout(TimeOut);
        } catch (UnsupportedCommOperationException ex) {
//            ex.printStackTrace();
        }
        
        for (i=0;i<MaxBytes;i++){
            try { 
//                if (Input.available()<=0){ // stop if all data read
//                    if (Input.available()<=0) break;
//                }
                CharBuffer = Input.read();
                Buffer = Buffer + (char)CharBuffer;
//                if (CharBuffer==-1 && Buffer=="" && Timer<TimeOut){
//                    try {
//                        Thread.sleep(10);
//                        Timer=Timer+10;
//                        i=i-1;
//                    } 
//                    catch (InterruptedException ex) {
//                        ex.printStackTrace();
//                    }
//                }
                if (CharBuffer==-1) break;
    //          for logging: System.out.println("readbyte() = " + (char)s + " " + s);
            } 
            catch (IOException ex) {
//                ex.printStackTrace();
                System.out.println("I/O exception while reading from port "+PortId.getName());
            }
        }
        return Buffer;
    }
    
   public  void setString(String Message){
       int i;
       char[] Transmit = new char[Message.length()+1];
       
       if (sPort==null){
            System.out.println("Not connected. Cannot write port");
            return;
        }
       
       Transmit = Message.toCharArray();
       for (i=0;i<Message.length();i++){
            try {
                Output.write((int)Transmit[i]);
                Output.flush();
            } catch (IOException ex) {
//                ex.printStackTrace();
                System.out.println("I/O exception while writing on port "+PortId.getName());
            }
       }
   }  
   
   private boolean setParams(SerialPort tmpPort, int B, int D, int P, int S){
       boolean success = true;
       
       switch (D){
           case 5: D=tmpPort.DATABITS_5;
           break;  
           case 6: D=tmpPort.DATABITS_6;
           break;  
           case 7: D=tmpPort.DATABITS_7;
           break;  
           case 8: D=tmpPort.DATABITS_8;
           break;  
           default: success=false; 
       }
       switch (P){           
           case 0: P=tmpPort.PARITY_NONE;
           break;  
           case 1: P=tmpPort.PARITY_EVEN;
           break;  
           case 2: P=tmpPort.PARITY_ODD;
           break;  
           case 3: P=tmpPort.PARITY_MARK;
           break;
           case 4: P=tmpPort.PARITY_SPACE;
           break;  
           default: success=false;
       }
       switch (S){
           case 1: S=tmpPort.STOPBITS_1;
           break;  
           case 2: S=tmpPort.STOPBITS_2;
           break;
           case 3: S=tmpPort.STOPBITS_1_5;
           break;  
           default: success=false;           
       }
       if(success) {
            try {tmpPort.setSerialPortParams(B,D,S,P);
            } catch (UnsupportedCommOperationException ex) {
                ex.printStackTrace();
                System.out.println("Cannot set port properties, please check your settings");
            }
       }
       return success;
   }
   
}
