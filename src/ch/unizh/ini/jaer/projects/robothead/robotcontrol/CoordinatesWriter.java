/*
 * CoordinatesWriter.java
 *
 * Created on 12. Februar 2008, 19:00
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.robothead.robotcontrol;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;

/**
 * 
 *
 * @author jaeckeld
 */
public class CoordinatesWriter {
    
    long startTime;
    String path;
    String filename;
    
    /** Creates a new instance of CoordinatesWriter */
    public CoordinatesWriter() {
        
        startTime=System.currentTimeMillis();
        path = "c:\\ETH\\RobotHead\\DrivingExperiments\\1\\";
        filename= "drivePath.txt";
    }
    
    public void registerCoordinates() throws FileNotFoundException, UnsupportedEncodingException, IOException{
        
        FileOutputStream stream = new FileOutputStream(path+filename,true);
        OutputStreamWriter out = new OutputStreamWriter(stream, "ASCII");
        
        int[] data=new int[3];
        int pos[] =KoalaControl.getMotorPos();
        
        System.out.println("position to write "+pos[0]+" "+pos[1]);
        
        data[0]=pos[0];
        data[1]=pos[1];
        data[2]=(int)(System.currentTimeMillis()-startTime);   // time when this measurement was done
        
        for(int i=0;i<data.length;i++){
            out.write(String.valueOf(data[i]));
            out.write(" ");
        }
        
        out.write("\r\n");
        out.close();
    }
    
    public void registerStartCoordinates() throws FileNotFoundException, UnsupportedEncodingException, IOException{
        
        // sets motor Positions to zero and saves a [0 0 ts ]-line.
        
        KoalaControl.setMotorPos(0,0);
        
        FileOutputStream stream = new FileOutputStream(path+filename,true);
        OutputStreamWriter out = new OutputStreamWriter(stream, "ASCII");
        
        int[] data=new int[3];
        
        data[0]=0;
        data[1]=0;
        data[2]=(int)(System.currentTimeMillis()-startTime);   // time when this measurement was done
        
        for(int i=0;i<data.length;i++){
            out.write(String.valueOf(data[i]));
            out.write(" ");
        }
        
        out.write("\r\n");
        out.close();
    }
    
    public void resetFile() throws FileNotFoundException, UnsupportedEncodingException, IOException{
    
        FileOutputStream stream = new FileOutputStream(path+filename);
        OutputStreamWriter out = new OutputStreamWriter(stream, "ASCII");
       
        out.write("");
        out.close();
    }
    
    
}
