package edu.mplab.rubios.node;

import java.io.BufferedReader;
import java.io.PrintWriter;

/** 
 * RUBIOSPinger
 * <p>
 * Helper class for RUBIOSNode. It is in charge of periodically Pinging
 * the RUBIOSMasterRelayNode. 
 *
 * @author Javier R. Movellan 
 * Copyright UCSD, Machine Perception Laboratory,  Javier R. Movellan
 * License  GPL
 * Date April 23, 2006
 *
 */
public class RUBIOSPinger extends Thread{
    
    long currentTime;
    long timeAtLastPong;
    PrintWriter sout = null;
    BufferedReader sin = null;
    long dt;   
    
    public RUBIOSPinger(BufferedReader myin, PrintWriter myout) {
	
	sin = myin;
	sout = myout;
	timeAtLastPong = System.currentTimeMillis();
	dt = 60000; // 1 minute in millisecs

    }
    
    public void run(){
	

	while(true){
	    try{
		Thread.sleep(dt); // ping once a minute
		currentTime= System.currentTimeMillis();		
		// If no PONG received in last 3 minutes, die. 
		if(currentTime - timeAtLastPong > 3*dt){
		    //System.exit(1);
		}
		sout.println("RUBIOS: PING ");		   
	    }catch(Exception e){System.out.println("Exception");}
	}
    }




}
