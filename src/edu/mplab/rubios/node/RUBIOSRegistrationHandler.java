package edu.mplab.rubios.node;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.PrintWriter;
import java.util.StringTokenizer;

/** 
 * RUBIOSRegistrationHandler
 * <p>
 * Provides methods for registering a node into a RUBIOS robot, once a
 * connection has already been established.
 * 
 * @author Javier R. Movellan 
 * Copyright UCSD, Machine Perception Laboratory,  Javier R. Movellan
 * License  GPL
 * Date April 23, 2006
 *
 */
public class RUBIOSRegistrationHandler extends Thread{
    String[] dependsOn;
    int nDepends=0;
    String fileName =null;

    PrintWriter sout = null;
    BufferedReader sin = null;
       
    FileReader fr  =null;
    BufferedReader fin=null;
    boolean registrationCompleted = false;
    boolean noFile = false;
    // login and password needed to register with the master relay
    // server These are set in the RUBIOSAuthenticator class of the
    // master relay server
    String r_login = "RUBIOS2";
    String r_pass = "ytttm";


    /** Creates a new instance of RUBIOSRegistrationHandler */
    public RUBIOSRegistrationHandler(String myFile,  BufferedReader myin, PrintWriter myout) {
	sin = myin;
	sout = myout;
	fileName = myFile;

	try {
	    fr = new FileReader(fileName);
	    fin = new BufferedReader(fr);
	}catch(Exception e){
	    noFile = true;
	    // System.out.println("Registration File Not Available: "+noFile+"  Using Default Parameters");
	};
	  
	this.start();

	try{Thread.sleep(500);}catch(Exception e){}
	this.TransmitFile();// Send the registration file to the server
	
	try{Thread.sleep(1000);}catch(Exception e){}
	//	System.out.println("Sending RUBIOS BroadcastNodeInfo Request");
	
	
    }
    
    public void run(){
	String str; 

	try{
	    while((str = sin.readLine())!= null){
		if(str.equals("RUBIOS Registration Successful")){
		    registrationCompleted = true;
		    System.out.println("Registration Successful");
		    System.out.println("---------------------------------------");
		    return;}  // terminate the thread 

	    }
	} catch(Exception e){System.out.println("Master Relay Server Not Responding" ); }
	System.exit(1); // die on disconnect
    }
    //--------------------------
    public void TransmitFile(){
	String str; 
	str = fileName.replace(".rrf","");
	str.trim();
	sout.println(" ");// this is needed for some misterious reason
	sout.println(r_login+ " " +r_pass);
	//default NodeName is name of the registration file minus the .rrf
	sout.println("NodeName "+str);// shall be overriden by RNN command
				 // in registration file

	if(noFile){// send defaults
	    sout.println("OutputNodes AllNodes");
	}
	else {
	    //default RNN is name of RNN file minus the .rrf
	    try{ sout.println("RNN "+ str); ;}catch(Exception e){}
	    
	    // Here we feed the info in the registration file
	    try{
		while((str = fin.readLine()) != null){
		    parseRegistrationMessage(str);
		    //System.out.println(str);
		    sout.println(str);
		}
	    } catch(Exception e){};
	}
	sout.println("");// let's print out a final carriage return in
			 // case the registration file did not have it
    }
    
    //------------------------
       
    public void parseRegistrationMessage(String msg){
	StringTokenizer  st = new StringTokenizer(msg);
	String  token;
	int c=0;
	while(st.hasMoreTokens()){
	    token =st.nextToken();
	    if(token.equals("DependsOn")){
		nDepends = st.countTokens();
		if(nDepends>0){
		    dependsOn = new String[nDepends];
		    while (st.hasMoreTokens()){
			dependsOn[c] = st.nextToken();
			c++;
		    }
		
		}

	    }
	}
    }

}
