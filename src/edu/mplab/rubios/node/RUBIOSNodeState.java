package edu.mplab.rubios.node;

/**
 * RUBIOSNodeState 
 * <br>
 * Generic class for State of RUBIOS Nodes
 * @author Javier R. Movellan 
 * Copyright UCSD, Machine Perception Laboratory,  Javier R. Movellan
 * License  GPL
 * Date April 23, 2006
 *
 */
public class RUBIOSNodeState{

    public String nodeName;
    int nDepends=0;
    String[] dependsOn;
    int nStates;
    String[] stateName;
    String[] state;
    String  message;
    public boolean active = true;
    long sleepStartedAt=0;
    long scheduledWakeUpCallAt; 
    RUBIOSNode rn;
	
    public RUBIOSNodeState(int n, RUBIOSNode _rn){
	rn = _rn;
	nStates = n;
	nodeName = "Default";
	for (int i=0; i<nStates;i++){
	    stateName = new String[nStates];
	    state = new String[nStates];
	}

    }
    
    public void printState(){
      	System.out.println("Node Name: " +nodeName);
	System.out.println("Active: " +active);

	for(int i=0;i<nStates;i++){
	    System.out.println(this.stateName[i]+": "+this.state[i]);
	}

	if(nDepends>0){
	    System.out.println("Depends On:");
	    for(int i=0;i<nDepends;i++){
		System.out.println("             "+this.dependsOn[i]);
	    }
	}
    }
    
    public void sendState(){
	rn.sendMessage("RUBIOS: STATE: "+nodeName+":  Active: " +active);
	if(!active){
	    long secondsToAwake = (scheduledWakeUpCallAt - System.currentTimeMillis())/1000;
	    
		rn.sendMessage("RUBIOS: STATE: "+nodeName+":  Seconds To Awake: " +secondsToAwake);
	}
	for(int i=0;i<nStates;i++){
	    rn.sendMessage("RUBIOS: STATE: "+ nodeName+": "+ this.stateName[i]+": "+this.state[i]);
	}

	if(nDepends>0){
	    for(int i=0;i<nDepends;i++){
		rn.sendMessage("RUBIOS: STATE: "+nodeName+":  Depends On: "+this.dependsOn[i]);
	    }
	}
    }
    
    public static  void main(String args[]){

	RUBIOSNode rn = new RUBIOSNode(args, 1);
	RUBIOSNodeState s = new RUBIOSNodeState(3, rn);
	s.nDepends= 2;
	s.dependsOn = new String[s.nDepends];
	s.dependsOn[0] = "Sound";
	s.dependsOn[1]= "FaceDetector";
	s.stateName[0]= "Brain";
	s.state[0] = "OK";
	s.stateName[1]="Heart";
	s.state[1]= "So So";
	s.stateName[2] = "Lungs";
	s.state[2] ="Excellent";
	s.printState();
    }

}

