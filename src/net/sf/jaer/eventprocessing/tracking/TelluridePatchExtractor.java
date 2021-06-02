/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing.tracking;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import net.sf.jaer.Description;

@Description("Extractor of coordinates of the cluster trackers")
/**
 *
 * @author Diederik Paul Moeys
 */
public class TelluridePatchExtractor {

    int xCoordinate;
    int yCoordinate;
    int clusterSize;
    int timeStamp;
    int clusterID;

    FileOutputStream out; // declare a file output object
    PrintStream p; // declare a print stream object 

    public void printToFile() {//Pritn to file
        try {
            // Create a new file output stream
            FileOutputStream out = new FileOutputStream(new File("C:\\Users\\Diederik Paul Moeys\\Desktop\\clusterDetails.txt"), true);
            // Connect print stream to the output stream
            p = new PrintStream(out);
            p.print(getTimeStamp());
            p.print(" ");
            p.print(getXcoordinate());
            p.print(" ");
            p.print(getYcoordinate());
            p.print(" ");
            //p.print(getClusterID());
            //p.print(", ");
            p.println(getClusterSize());
            p.close();
        } catch (Exception e) {
            System.err.println("Error writing to file");
        }
    }
//----------------------------------------------------------------------------//
//-- X coordinate set --------------------------------------------------------//
//----------------------------------------------------------------------------// 

    public void setXcoordinate(int xCoord) {//receive the x pixel coordinate of center of mass
        this.xCoordinate = xCoord;
        //System.out.println(xCoordinate);
    }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//

//----------------------------------------------------------------------------//
//-- X coordinate get --------------------------------------------------------//
//----------------------------------------------------------------------------// 
    public int getXcoordinate() {//get the x pixel coordinate of center of mass
        return xCoordinate;
    }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//

//----------------------------------------------------------------------------//
//-- Y coordinate set --------------------------------------------------------//
//----------------------------------------------------------------------------// 
    public void setYcoordinate(int yCoord) {//receive the y pixel coordinate of center of mass
        this.yCoordinate = yCoord;
        //System.out.println(yCoordinate);
    }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//

//----------------------------------------------------------------------------//
//-- Y coordinate set --------------------------------------------------------//
//----------------------------------------------------------------------------// 
    public int getYcoordinate() {//get the y pixel coordinate of center of mass
        return yCoordinate;
    }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//

//----------------------------------------------------------------------------//
//-- Size of cluster set -----------------------------------------------------//
//----------------------------------------------------------------------------// 
    public void setClusterSize(int size) {//set the size of the cluster
        this.clusterSize = size;
        //System.out.println(clusterSize);
    }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//

//----------------------------------------------------------------------------//
//-- Size of cluster get -----------------------------------------------------//
//----------------------------------------------------------------------------// 
    public int getClusterSize() {//get the size of the cluster
        return clusterSize;
    }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//

//----------------------------------------------------------------------------//
//-- Cluster timeStamp set ---------------------------------------------------//
//----------------------------------------------------------------------------// 
    public void setClusterID(int ID) {//set ID of cluster
        this.clusterID = ID;
        //System.out.println(timeStamp);
    }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//

//----------------------------------------------------------------------------//
//-- Cluster ID get ----------------------------------------------------------//
//----------------------------------------------------------------------------// 
    public int getClusterID() {//get the cluster ID
        return clusterID;
    }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//
//-- Cluster timeStamp set ---------------------------------------------------//
//----------------------------------------------------------------------------// 
    public void setTimeStamp(int time) {//set timestamp of cluster
        this.timeStamp = time;
        //System.out.println(timeStamp);
    }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//

//----------------------------------------------------------------------------//
//-- Cluster timestamp get ---------------------------------------------------//
//----------------------------------------------------------------------------// 
    public int getTimeStamp() {//get the timeStamp
        return timeStamp;
    }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//
}
