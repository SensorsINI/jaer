package ch.unizh.ini.jaer.projects.robothead.robotcontrol;
/*
 * PanTilt.java
 *
 * Created on 9. Februar 2007, 13:54
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 * This class was modified for the jAER ControlFilter and is not matching with KoalaGui
 */


public class PanTilt {
    String News;
    RS232Api Comm;
    
    short TIMEOUT = 200;
    short MAXBYTES = 500;
    double TICKS_PER_GRADE = 1364/144; //1364 ticks on 2*72?~~ 9 Ticks per grade
    
    
    /** Creates a new instance of PanTilt */
    public PanTilt() {
        Comm = new RS232Api();
    }
    
    
    public boolean init(int port, int baud, int databits, int parity, int stop){
        boolean BoolBuffer = false;
//        String Settings;
//        
//        Settings = "COM"+port+": baud="+baud+" data="+databits+" parity="+parity+" stop="+stop;
        BoolBuffer = Comm.startComm("COM"+port,baud,databits,parity,stop);
       
        return BoolBuffer;
    }
    
    
    public boolean close(){
        Comm.stopComm();
        return true;
    }  
    
    public boolean setDegree(int Servo, int Degrees, boolean Sense) {
        int TicksDegree;
        char DegreeL;
        char DegreeH;
        
        //maximum angle = 70?, Number of Servos = 4
        if (Degrees>70 || Degrees<0){
            System.out.println("Degrees out of range(-70 to +70), abort sending.");
            return false;
        }
        
        TicksDegree = (int)(Degrees * TICKS_PER_GRADE+0.5);
        DegreeL = (char)(TicksDegree%256);
        DegreeH = (char)(int)(TicksDegree/256);
        if (Sense)
            News = "L"+Servo+(char)DegreeH+(char)DegreeL;
        else
            News = "R"+Servo+(char)DegreeH+(char)DegreeL;
            
        Comm.setString(News);
        // read answer...to free comm port
        News = Comm.getString(TIMEOUT,MAXBYTES);
        if (News.compareTo("")>0 && News.substring(0,1).compareTo("l")==0 && Sense) 
            System.out.println("Command L"+Servo+" "+(int)DegreeH+" "+(int)DegreeL+" sucessfully sent.");
        else if (News.compareTo("")>0 && News.substring(0,1).compareTo("r")==0 && !Sense) 
            System.out.println("Command R"+Servo+" "+(int)DegreeH+" "+(int)DegreeL+" sucessfully sent.");
        else
        {
            if (Sense) System.out.println("An error occured while sending: L"+Servo+" "+(int)DegreeH+" "+(int)DegreeL);
            else System.out.println("An error occured while sending: L"+Servo+" "+(int)DegreeH+" "+(int)DegreeL);
        }
        // System.out.print(News.Text);
        
        return true;
    }
    
    public boolean setSpeed(int Servo, int Speed){
    
        if (Speed>250 || Speed<0){  // max2.5 secs
            System.out.println("Speed value out of range (0 to 250), abort sending.");
            return false;
        }
        // set speed in 1/100 seconds
        News = "T"+Servo+(char)Speed;
        
        Comm.setString(News);
        // read answer...to free comm port
        News = Comm.getString(TIMEOUT,MAXBYTES);
        if (News.compareTo("")>0 && News.substring(0,1).compareTo("t")==0) 
            System.out.println("Command T"+Servo+" "+Speed+" sucessfully sent.");
        else
            System.out.println("An error occured while sending: T"+Servo+" "+Speed);
        // System.out.print(News.Text);
        return true;
    }
    
    
    public boolean setAmpl(int Servo, int Ampl) {
        int TicksAmpl;
        char HAmpl, LAmpl;
        if (Ampl>70 || Ampl<0){
            System.out.println("Width out of range (0 to 70), abort sending. Maximum values +/- 70");
            return false;
        }
        
        TicksAmpl = (int)(Ampl * TICKS_PER_GRADE+0.5);
        //if (TicksAmpl>250 || TicksAmpl<0) //max delta ca +/- 28?
        //    return false;
        // set delta for uMove amplitude
        
        LAmpl = (char)(TicksAmpl%256);
        HAmpl = (char)(int)(TicksAmpl/256);
        News = "A"+Servo+(char)HAmpl+(char)LAmpl;
        
        Comm.setString(News);
        // read answer...to free comm port
        News = Comm.getString(TIMEOUT,MAXBYTES);
 
        if (News.compareTo("")>0 && News.substring(0,1).compareTo("a")==0) 
            System.out.println("Command A"+Servo+" "+(int)HAmpl+" "+(int)LAmpl+" sucessfully sent.");
        else
            System.out.println("An error occured while sending: A"+Servo+" "+(int)HAmpl+" "+(int)LAmpl);
        // System.out.print(News.Text);
        return true;
    }
    
    
    public boolean setEnable(int Servo, int Enable){
        if (Enable!=0) Enable=1;
        // enable uMoves
        News = "E"+Servo+(char)Enable;
        
        Comm.setString(News);
        // read answer...to free comm port
        News = Comm.getString(TIMEOUT,MAXBYTES);
        if (News.compareTo("")>0 && News.substring(0,1).compareTo("e")==0) 
            System.out.println("Command E"+Servo+" "+(int)Enable+" sucessfully sent.");
        else
            System.out.println("An error occured while sending: E"+Servo+" "+(int)Enable);
        // System.out.print(News.Text);
        return true;
    }
    
    
    public boolean Reset(){
        News = "Z";
        
        Comm.setString(News);
        // read answer...to free comm port
        News = Comm.getString(TIMEOUT,MAXBYTES);
        if (News.compareTo("")>0 && News.substring(0,1).compareTo("z")==0) 
            System.out.println("Command Z sucessfully sent.");
        else
            System.out.println("An error occured while sending: Z");
        // System.out.print(News.Text);
        return true;        
    }
}
