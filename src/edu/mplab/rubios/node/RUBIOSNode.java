package edu.mplab.rubios.node;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.util.StringTokenizer;


/** 
 * RUBIOSNode
 * <p>
 * Base Class for RUBIOS Control nodes. It provides methods for
 * connecting to the RUBIOS robot, and registering the node. 
 *
 * @author Javier R. Movellan 
 * Copyright UCSD, Machine Perception Laboratory,  Javier R. Movellan
 * License  GPL
 * Date April 23, 2006 
 * <br> Revised on September 10 2006 to include the concept of node 
 * requests, messageParse and requestIntegrator.
 * <br>Revised June 2007.
 */
public class RUBIOSNode {
    

    
    
    RUBIOSPinger rPing ;

    boolean registered = false; // whether registration was succesful
    PrintWriter outLine = null;  // This is how you can talk to the
                                 // world outline.println("My
                                 // Message") Sends the message to the
                                 // master relay server which
                                 // transmits it to all the nodes that
                                 // listen to this node. The function
                                 // sendMessage() does this for you
                                 // automatically

    BufferedReader inLine = null;
    public String[] messagesReceived;
    public int nMessagesReceived;
    int nMessagesRecognized=0;
    RUBIOSInputHandler  ih = null;

    
    public double dt; // time step (sampling rate) seconds for
	       // discrete tim approximation of dynamics


    int bufferSize=1000; //number of input messages that can be held

    long startTimeInMillis ;
    long cycleWorkTime ;
    long cycleStartTime;
    public long runTimeInMillis;
    public double runTimeInSecs;
    // holds all  requests received since the node started 
  

    public RUBIOSRequest integratedRequest = null;

    public RUBIOSNodeState nodeState; 



    /**
     * Handles the parameters ["Host IP", "Port #", "Registration File Name"]
     * used when calling the base program; called on RUBIOSNode construction. 
     * These command line parameters, by convention, are the first argument 
     * to the constructor. Your own array may be supplied to the constructor
     * in the place of the command line arguments if you wish to create a 
     * node manually. 
     * <p> 
     * Three values must be set: "Host IP", "Port #", "Registration File Name". 
     * Takes care of defaults if no command line parameters
     * were used. Missing values are given default values.
     * The default are "localhost", "1357", and "[nameOfClass].rrf" 
     * respectively. For more in depth description, see parameter and
     * return value documentation.
     *
     * @param args String arguments passed into the "main" method by 
     * the command line can be passed to this function. By default, 
     * the arguments are expected to be "Host IP", "Port #", and 
     * "Registration File Name" in that order. The default values for
     * these are "localhost", "1357", and "[nameOfClass].rrf" respectively.
     * 0, 1, 2, or 3 arguments may be supplied, but in the case of 1 
     * argument, it is implied that that argument is "Host IP", etc.
     *
     * @return A 3 dimensional string array, which will contains the 
     * desired host ip, port #, and registration file name.  Default
     * values are filled in for missing values in String[] args, and 
     * additional arguments are discarded.
     */
    public  static final  String[]  parseCommandLine(String[] args){
	String[] cla = new String[3];	
	Exception e = new Exception();
	String thisClassName =  new CurrentClassGetter().getClassName();

	if(args.length >0 && args[0].equalsIgnoreCase("help")){
	    System.out.println("Arguments are host, port, and rubios registration file");
	    System.out.println("Default for 0 Arguments are:  localhost 1357 nameOfClass.rrf");
  System.out.println("Default for 1 arguments: <argument 1> 1357  nameOfClass.rrf");
  System.out.println("Default for 2 arguments: <argument 1> <argument 2>   nameOfClass.rrf");
	    System.exit(1);
	}
	else if( args.length == 3){
	    cla[0] = args[0];
	    cla[1] = args[1];
	    cla[2] = args[2]; // default registration file name
	}
	else if( args.length == 2){
	    cla[0] = args[0];
	    cla[1] = args[1];
	    cla[2] = thisClassName+".rrf"; // default registration file name
	}
	else if( args.length == 1){
	    cla[0] = args[0]; // default host
	    cla[1] = "1357";
	    cla[2] = thisClassName+".rrf";
	}
	
	else if( args.length == 0){
	    cla[0] = "localhost"; //default host
	    cla[1] = "1357"; // default port
	    cla[2] = thisClassName + ".rrf";
	}

	return cla;
    }


/**
 * Constructor: Supply parameters [see below], sleep time; 
 * Assume 1 state dimenstion.	Default constructor behavior sets the node
 * name to the class name, the state names to "State [#]", and the value
 * of all states to "OK". Handles registration with the RUBIOS Server.
 * Establishes a Ping connection to the server to avoid disconnect. Starts
 * all nodes on which this node depends (as specified in RRF  registration 
 * file) which are not already started. Begins reception of messages.
 *
 * @param args A String[] of length 0 (null), 1, 2, or 3. Should contain,
 * in order, the desired values for "Host IP", "Port #", "Registration
 * File Name". Defaults will be supplied for missing values. Defaults
 * are set by parseCommandLine().
 *
 * @param _dt Time to sleep (in seconds) after each "run()" call.  
 **/
    public  RUBIOSNode(String []args,   double  _dt){
	// assume 1 dimensional state
	this(args, _dt,1);
    }
  
	/**
	 * Constructor: Supply parameters [see below], Sleep Time, Dimensionality
	 * of State Representation. Default constructor behavior sets the node
	 * name to the class name, the state names to "State [#]", and the value
	 * of all states to "OK". Handles registration with the RUBIOS Server.
	 * Establishes a Ping connection to the server to avoid disconnect. Starts
	 * all nodes on which this node depends (as specified in RRF  registration 
	 * file) which are not already started. Begins reception of messages.
	 *
	 * @param args A String[] of length 0 (null), 1, 2, or 3. Should contain,
	 * in order, the desired values for "Host IP", "Port #", "Registration
	 * File Name". Defaults will be supplied for missing values. Defaults
	 * are set by parseCommandLine().
	 *
	 * @param _dt Time to sleep (in seconds) after each "run()" call.  
	 *
	 * @param nStateDimensions Number of dimensions needed to summarize this
	 * node's current state. 
	 **/
    public  RUBIOSNode(String []args,   double  _dt, int nStateDimensions){

	String[] cla = parseCommandLine(args);
	String _Host = cla[0];
	int _Portno = Integer.parseInt(cla[1]);
	String _FileName =  cla[2];
	nodeState = new RUBIOSNodeState(nStateDimensions, this);
	
	nodeState.nodeName = new CurrentClassGetter().getClassName();
	nodeState.nStates = nStateDimensions;
	for(int i=0;i<nStateDimensions;i++){
	    nodeState.stateName[i] = "State "+Integer.toString(i);
	    nodeState.state[i] = "OK";
	}


	dt = _dt;



		    System.out.println("---------------------------------------");
		    System.out.println("Name:  RUBIOS "+nodeState.nodeName);
		    System.out.println("---------------------------------------");
		    System.out.println("   ");
		    System.out.flush();
		    messagesReceived = new String[bufferSize];

	//establish sockets connection
	RUBIOSConnectionHandler ch= new RUBIOSConnectionHandler(_Host,_Portno);
	
	if( ch.connectionCompleted== false){
	    System.out.println("Operation timed out."); 
	    System.out.println("Unable to connect to address " +_Host+", Port "+ _Portno ); 
	    RUBIOSDebug.message("Unable to Establish Connection");
	    //System.exit(1);
            throw new RuntimeException();
        }
	System.out.println("Connected To MasterRelayServer"); 
	System.out.println("Starting Registration Process");
	System.out.flush(); 
	RUBIOSRegistrationHandler rh = new RUBIOSRegistrationHandler(_FileName, ch.sin, ch.sout);
	
	if( rh.registrationCompleted== false){
	    System.out.println("Unable to Register");
	    RUBIOSDebug.message("Unable to Register"); 
	    //System.exit(1);
            throw new RuntimeException();
        }

	

	//System.out.println("Registration Successful. Waiting for New Input");
	RUBIOSDebug.message("Registration Successful. Waiting for New Input");
	inLine = rh.sin;
	outLine = rh.sout;

       
	rPing = new RUBIOSPinger(ch.sin, ch.sout);
	rPing.start(); // runs on separate thread and once a minute
		       // pings the RUBIOSMasterRelayNode so we dont
		       // get disconnected.

	// Provides access to buffer with messages received from other nodes.	
	ih= new  RUBIOSInputHandler(inLine,bufferSize);

	nodeState.nDepends = rh.nDepends;	
	nodeState.dependsOn = new String[rh.nDepends];
	for(int i=0; i< rh.nDepends;i++){
	    try{Thread.sleep(1000);} catch(Exception e){}
	    nodeState.dependsOn[i] = rh.dependsOn[i];
	    sendMessage("RUBIOS: RUN "+ rh.dependsOn[i]);
	    System.out.println("RUBIOS: RUN "+ rh.dependsOn[i]);
	}


	


    }



 /**
  *	Provides access to the buffer of messages collected during
  * time between time steps. While the node is asleep, the messages
  * are kept in a "write" buffer. When we call collectInputMessages,
  * the write buffer becomes the read buffer and new messages start
  * accumulating in a new empty buffer. 
  * <p>
  * collectInputMessages simply copies the input messages to the
  * "messagesReceived" array and sets the "nMessagesReceived" variable.
  **/
    public final void collectInputMessages(){// do not override
	// Provides access to the buffer of messages collected during
	// time between time steps
	
	nMessagesReceived=0;
	ih.doubleBuffer.switchReadBuffer(); // We read the buffer that
	// was written upon since
	// last time we call this method
	while(ih.doubleBuffer.notEmpty()){
	    messagesReceived[ih.getNumRemainingMessages() - 1] =
		ih.doubleBuffer.read();
	    nMessagesReceived++;
	}


    }


   
/** 
 * Sends a string to the RUBIOS server, which then takes care of sending the
 * string to other appropriate nodes; since this is not a formal request it
 * will not be kept by other nodes' request buffers. By default messages are 
 * sent to all other nodes, unless specified in the RRF registration file. 
 * 
 * @param msg The string that will be sent to the Server, and from there to 
 * other nodes.
 **/
    public final void sendMessage(String msg){
	outLine.println(msg);
    }



/** 
 * Sends a formal RUBIOSRequest object to the RUBIOS server, 
 * which then takes care of sending the request to other appropriate nodes; 
 * since this is a formal request it
 * will be kept by other nodes' request buffers. By default messages are 
 * sent to all other nodes, unless specified in the RRF registration file. 
 * <p>
 * By default the request is sent in a serialized form (i.e. sendRequest 
 * simply calls "sendSerializedRequest"). One could imagine memory-sharing
 * schemes in which the pointer to the request may be sent directly, and so
 * one may wish to override this function in a subclass.
 * 
 * @param rr The request that will be sent to the Server, and from there to 
 * other nodes.
 **/
    public final void sendRequest(RUBIOSRequest rr){
	sendSerializedRequest(rr);
    }

	/** 
	 * Sends a formal RUBIOSRequest object to the RUBIOS server, 
	 * which then takes care of sending the request to other appropriate nodes; 
	 * since this is a formal request it
	 * will be kept by other nodes' request buffers. By default messages are 
	 * sent to all other nodes, unless specified in the RRF registration file. 
	 * <p>
	 * This function takes care of serializing the request for "network readiness"
	 * before sending the actual request.
	 * 
	 * @param rr The request that will be sent to the Server, and from there to 
	 * other nodes.
	 **/
    public final void sendSerializedRequest( RUBIOSRequest rr){
	sendMessage(rr.serialize());
    }




	/** 
	 * Sends the state of the node to the RUBIOS server, 
	 * which then takes care of sending the request to other appropriate nodes; 
	 * By default states are 
	 * sent to all other nodes, unless specified in the RRF registration file. 
	 * 
	 **/
    public final void sendNodeState(){
	nodeState.sendState();
    }




    /** Goes over the messages received since the last time step, with the
     * aim of doing all that can be done on a message-by-message basis 
     * (i.e. without considering multiple messages, and without performing 
     * actions that are message independent). 
     * <p>
     * Calls parseRUBIOSMessage to see if there 
     * are any RUBIOS specific messages (e.g. "go to sleep", "quit", etc.) 
     * and handles these messages. If the message is not for RUBIOS it
     * is sent to parseMessage, which should be overridden on a class-by-class
     * basis to a) figure out whether this message is for the specific node
     * and b) whether something can be done immediately (rather than later) 
     * with the message. Increments "nMessagesRecognized" for every message 
     * that parseMessage returns "true" for.  
     **/

    public final  void inputCommandHandler(){

	nMessagesRecognized = 0;
	// First we catch messages directed to the Master relay
	// server. These are recognized by the header "RUBIOS:"
	for(int i=0;i<nMessagesReceived;i++){
	    if( parseRUBIOSMessage(messagesReceived[i])){
	    }
	    else {
		if(parseMessage(messagesReceived[i])){
		    nMessagesRecognized ++;//  count only parseable messages
		}
	    }
	}
    }




    private final boolean parseRUBIOSMessage(String msg){ //Override
	String str;
	RUBIOSRequest request = new RUBIOSRequest();
	
	
	StringTokenizer st = new StringTokenizer(msg);

	if(!st.hasMoreTokens()){return false;}// message was empty
	
	if(! st.hasMoreTokens()){return false;} 
	str = st.nextToken();
	if (!str.equalsIgnoreCase("RUBIOS:")){
	    return false;
	}
	if(! st.hasMoreTokens()){
	    return false;
	} 
	str = st.nextToken();
	if(str.equalsIgnoreCase("STATE")){
	    sendNodeState();
	    return true;
	}
	if(str.equalsIgnoreCase("PONG")){
	    rPing.timeAtLastPong = System.currentTimeMillis();
	}

	if(str.equalsIgnoreCase("RUN")|| str.equalsIgnoreCase("START")){
	    if(!st.hasMoreTokens()){ return false;}
	    str = st.nextToken(); // the next token should be our node
	    if(str.equals(nodeState.nodeName)){
		nodeState.active = true;
		sendNodeState();
	    }
	}
	
	if(str.equalsIgnoreCase("SLEEP")|| str.equalsIgnoreCase("STOP")){
	    if(!st.hasMoreTokens()){ return false;}
	    str = st.nextToken(); 
	    if(!str.equals(nodeState.nodeName)){return false;} // next token is our node Name
	    if(! st.hasMoreTokens()){ // By default sleep for 1 week
      		nodeState.active = false; 
		nodeState.sleepStartedAt = System.currentTimeMillis();
		nodeState.scheduledWakeUpCallAt = nodeState.sleepStartedAt + 7*24*60*60*1000; 
		sendNodeState();
		return true;
	    }  
	    else{
		str = st.nextToken(); // number of seconds to sleep
		try{
		    nodeState.active = false;
		    long sleepTimeInSeconds  = Long.valueOf(str).longValue();
		    nodeState.sleepStartedAt = System.currentTimeMillis();
		    nodeState.scheduledWakeUpCallAt = nodeState.sleepStartedAt + sleepTimeInSeconds*1000; 
		    sendNodeState();
		}catch (NumberFormatException e){return false ;} 
	
	    }
	}

	else if(str.equalsIgnoreCase("REMOVE")|| str.equalsIgnoreCase("KILL")){
	    if(!st.hasMoreTokens()){ return false;}
	    str = st.nextToken(); 
	    if(!str.equals(nodeState.nodeName)){return false;} 
	    sendMessage("RUBIOS: STATE: Node "+ nodeState.nodeName+":  Performing System exit(1)");
	    finalize();
	    System.exit(1);
	}



	return false;
    }


    public void finalize(){
    }


    /**
     * A simple embedded class for extracting the name of the current class.
     * The context of the embedded class is one-removed (child) from the 
     * current class, so the current class is one-up on the context list.
     * Makes use of the "SecurityManager" functionality to perform this
     * kind of reflection. Class names are important in RUBIOS as 
     * Identifiers. They distringuish one node from another in the server, 
     * and tell the nodes where to look for their default registration files.
     **/
    public static class CurrentClassGetter extends SecurityManager {
	public String getClassName() {
	    int j= getClassContext().length-1;
	    return getClassContext()[j].getName();
	}
    }







    /////////////////// THE NODE's Infinite RUN LOOP /////////////////////



    
    /**
     * The node's infinite loop: This controls the basic cycle 
     * RUBIOSNodes go through.
	 * RUBIOSNodes are time driven. Every dt it awakes collects the
	 * inputs received since the last time step and updates its state.
	 * <p>
	 * The run cycle consists of: <br> 
	 * --sleep for dt seconds <br>
	 * --collectInputMessages <br>
	 * --parseRubiosMessages (via inputCommandHandler) <br>
	 * --parseMessages (via inputCommandHandler) <br>
	 * --if active: nodeDynamics <br>
	 * --if not active: check to see if it's time to wake up.
     **/
    public final void run(){ // Do not override. 
	// This is a time driven node. Every dt it awakes collects the
	// inputs received since the last time step and updates its state.
	// We may want to think about event driven nodes also, which
	// awake only when they receive an input.  We may also want to
	// think about nodes that addaptively change their sampling
	// rate.
	startTimeInMillis = System.currentTimeMillis();

	int dtms = (int) (1000*dt);// dtms is dt in millisecs
	while(true){
	    try{
		cycleStartTime = System.currentTimeMillis();

		collectInputMessages(); // Do not override.
		// Grabs messages
		// received during the dt  period
		// and puts them into
		// messagesReceived[]
		inputCommandHandler(); // Dopn not override. Calls
				       // parseMessage() over the
				       // messages received during
				       // the dt period

		if(nodeState.active){
		    nodeDynamics(); // Override.
		}
		else{
		    long currentTime = System.currentTimeMillis();
		    if(currentTime> nodeState.scheduledWakeUpCallAt){
			nodeState.active = true;
		    }
		    
		}
		runTimeInMillis = cycleStartTime- startTimeInMillis ;
		runTimeInSecs = ((double) runTimeInMillis)/1000.0;
		cycleWorkTime = System.currentTimeMillis() - cycleStartTime;
		Thread.sleep(Math.max(1,dtms-cycleWorkTime)); 

	    }catch(Exception e){
		System.out.println("Exception at RUBIOSNode.run()"  + e.toString());
		e.printStackTrace(); 
		RUBIOSDebug.message("Exception at RUBIOSNode.run(): " + e.toString());}
	}
    }

    

    //////////  OVERRIDE IN DERIVATIVE CLASSES ///////////////////////////



    /**
     * OVERWRITE IN CHILD CLASSES; 
     * Has two functions: (a) to determine if a given message is relevant
     * to this node, and (b) if the message is relevant and can be reacted
     * to immediately, to handle that reaction.
     * <p>
     * By default we return false to all messages (asserting that they're 
     * irrelevant) and do nothing. 
     * <p>
     * TODO:  Under the current structure, parseMessage is called while the 
     * node is asleep. A simple way around this is to get ride of 
     * inputCommandHandler entirely..
     *
     * @param msg A string containing a message for potentially any node,
     * so we need to figure out whether it is relevant to our node, and if
     * so, can we do anything with that information right now?
     *
     * @return True or False, indicating whether the message was relevant to 
     * this node. 
     **/
    public boolean parseMessage(String msg){ //Override
	return false;
    }
        
    
    
   /**
    * OVERWRITE IN CHILD CLASSES;
    * The basic operation of the node. This should include things like  
    * updating the current state, integrating across messages, performing
    * actions, sending messages to other nodes, etc.
    * <p>
    * By default we do nothing. 
    **/
    public void nodeDynamics(){ // Override

    }



    public static  void main(String args[]){

	double myDt = 3; //  Sampling rate in seconds
	    	    
	RUBIOSNode rn = new RUBIOSNode(args, myDt);
	rn.run();
    }
}
