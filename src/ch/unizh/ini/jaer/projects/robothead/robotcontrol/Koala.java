package ch.unizh.ini.jaer.projects.robothead.robotcontrol;
// ToDo: Run EmptyPort Routine before each sub...ensure clean answer.

/*
 * Koala.java
 *
 * Created on 29. November 2006, 10:18
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 * This class was modified for the jAER ControlFilter and is not matching with KoalaGui
 */


public class Koala {
     String News;
     RS232Api Comm;
     
     String Settings;
    
     short TIMEOUT = 400;
     short MAXBYTES = 500;
     short SENSOR_THRES = 200;
     double CLICKS_PER_MM = 1000/80;
     double CLICKS_PER_GRADE = 40/360 * CLICKS_PER_MM;
     int TOLERANCE = 10;
     boolean COLLISIONDETECT = true;
    
    /** Creates a new instance of Koala */
    public Koala(boolean ColDetect) {
        Comm = new RS232Api();
        
    }
    
    public  boolean init(int port, int baud, int databits, int parity, int stop) {
        boolean BoolBuffer = false;
        
//        Settings = "COM"+port+": baud="+baud+" data="+databits+" parity="+parity+" stop="+stop;
        BoolBuffer = Comm.startComm("COM"+port,baud,databits,parity,stop);

        return BoolBuffer;
    }
        
    //RICO: This function is added to allow to close the COM connection to the Koala robot
    //      without using the PortDLLWrapper library
    public boolean close(){
        
        
        Comm.stopComm();
//      
        return true;
    }  
    
    
    public  boolean setMotorSpeeds(int left, int right) {
        News = "D,"+left+","+right + (char)10;
        
        Comm.setString(News);
        // read answer...to free comm port
        News = Comm.getString(TIMEOUT,MAXBYTES);
        if (News.compareTo("")>0 && News.substring(0,1).compareTo("d")==0) 
            System.out.println("Command D,"+left+","+right+" sucessfully sent.");
        else
            System.out.println("An error occured while sending: D,"+left+","+right);
//        System.out.print(News.Text);
        
        return true;
    }    
    
    public  int[] getMotorSpeeds() {
        int[] speeds = new int[2];
        
        News = "E" + (char)10;
        
        Comm.setString(News);
        News = Comm.getString(TIMEOUT,MAXBYTES);
        // Note: received String is like: "e,X,X\r\n"
        if (News.compareTo("")>0 && News.substring(0,1).compareTo("e")==0) 
        {
            System.out.println("Command E sucessfully sent.");
            speeds = stringToInt(News, ",", "\r", 2, 2); 
        }
        else
            System.out.println("An error occured while sending: E");       
        return speeds;
    }  
    
    public  int[] getMotorPos() {
        int[] pos = new int[2];
        
        News = "H" + (char)10;
        
        Comm.setString(News);
        News = Comm.getString(TIMEOUT,MAXBYTES);
        // Note: received String is like: "h,X,X\r\n"
        if (News.compareTo("")>0 && News.substring(0,1).compareTo("h")==0) 
        {
            System.out.println("Command H sucessfully sent.");
            pos = stringToInt(News, ",", "\r", 2, 2);
        }
        else
            System.out.println("An error occured while sending: H"); 
        return pos;
    } 
    
    public  boolean setMotorPos(int left, int right) {
        
        News = "G,"+left+","+right + (char)10;
        
        Comm.setString(News);
        // read answer...to free comm port
        News = Comm.getString(TIMEOUT,MAXBYTES);
        if (News.compareTo("")>0 && News.substring(0,1).compareTo("g")==0) 
            System.out.println("Command G,"+left+","+right+" sucessfully sent.");
        else
            System.out.println("An error occured while sending: G,"+left+","+right);
//        System.out.print(News.Text);
        return true;
    }
    
    public  boolean gotoMotorPos(int left, int right) {
        int[] sensors = new int[16];

        News = "C," + left + "," + right + "," + (char)10;
        
        Comm.setString(News);
        // read answer...to free comm port
        News = Comm.getString(TIMEOUT,MAXBYTES);
        if (News.compareTo("")>0 && News.substring(0,1).compareTo("c")==0) 
            System.out.println("Command C,"+left+","+right+" sucessfully sent.");
        else
            System.out.println("An error occured while sending: C,"+left+","+right);
    
        return true;       
    }
    
    public  int[] getSensors() {
        int[] sens = new int[16];
        
//        News.Text = "n,1,2,3,4,5,6,7,8,9,10,12,13,14,15,16\r\n";        
        News = "N" + (char)10;
        
        Comm.setString(News);
        News = Comm.getString(TIMEOUT,MAXBYTES);
        // Note: received String is like: "n,X1,X2,X3,X4,X5,X6,X7,X8,X9,X10,X12,X13,X14,X15,X16\r\n"
        if (News.compareTo("")>0 && News.substring(0,1).compareTo("n")==0) 
        {
            System.out.println("Command N sucessfully sent.");
            sens = stringToInt(News, ",", "\r", 2, 16);
        }
        else
            System.out.println("An error occured while sending: N"); 
        return sens;       
    }    
    

    
    public  int[] stringToInt(String Input, String Separator, String EndMark, int StartPos, int MaxElem) {
        int[] Array = new int[MaxElem];
        int i=0;
        int j=0;
        int k=0;
        
        j=StartPos; k=0;
        for (i=2;i<Input.length();i++){
            if (Input.substring(i,i+1).compareTo(Separator)==0 || Input.substring(i,i+1).compareTo(EndMark)==0){
                try{
                Array[k] = Integer.parseInt(Input.substring(j,i));
                }
                catch(NumberFormatException ex)
                {
                    System.out.println("gubbelgubbel");
                }
                
                j=i+1; k=k+1;
            }
        }
//        System.out.print(Input); 
//        for (i=0;i<k;i++) System.out.print(Array[i] + Separator);        

        return Array;  
    }
}
