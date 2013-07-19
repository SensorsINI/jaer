/*
 * TrackerLogger.java
 * logs positions of trackers
 * Created on December 7, 2009, 10:32 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package ch.unizh.ini.jaer.projects.stereo3D;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * logs positions of trackers
 * @author rogister
 */
public class TrackerLogger {
    
    File logFile;
    BufferedWriter logWriter;
    boolean recordingStarted = false;
    /** Creates a new instance of EventRaw */
    public TrackerLogger() {
        
    }
     

    public void init( String filename ){

        try {


            logFile=new File(filename);
            logWriter = new BufferedWriter(new FileWriter(logFile));

            recordingStarted=true;


        }catch(IOException e){

            e.printStackTrace();
        }
    }

     public void log( String s ){

         if(recordingStarted&&logWriter!=null){
             try {
                 logWriter.write( s );
             }catch(IOException e){

                 e.printStackTrace();
             }
         }

     }

     public void close(  ){
         recordingStarted = false;
         if(logWriter!=null){
             try {
                 logWriter.close( );
             }catch(IOException e){

                 e.printStackTrace();
             }
             logWriter = null;
         }


     }

   

    public String toString(){
        return "TrackerLogger";
    }
    
}
