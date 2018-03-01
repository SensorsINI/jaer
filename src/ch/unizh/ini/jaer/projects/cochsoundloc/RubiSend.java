package ch.unizh.ini.jaer.projects.cochsoundloc;

import edu.mplab.rubios.node.RUBIOSNode;

/** Hello Sends "Hello World" messages once a second
 *
 * Developers: Javier R. Movellan
 *
 * Copyright: UCSD, Machine Perception Laboratory,  Javier R. Movellan
 *
 * License:  GPL
 *
 * La Jolla, California, April 23, 2006

Example RUBIOSNode derived from the Base Class. Sends a "Hello World"
message to RUBIOS. Useful for debugging.



*/




public class RubiSend extends RUBIOSNode {

    public  RubiSend(String [] args, double  mydt){

	super(args,mydt);
    }

    // This method tells the node what to do with received
    // messages. In this case we tell the node that we dont really
    // recognize any messages as ours. By returning false, we say,
    // that this message is not for us.
    /** parseMessage ignores all incoming messages
     */
    @Override
    public boolean parseMessage(String msg){return false;}

    //
    //  is called every time the node awakes, after a
    // period of inactivity determined by the paraemter dt. In this
    // case we tell the node to send a Message to all the other nodes
    // that can listen to us. By default all the nodes will receive
    // our messages but this may be changed using a rubios
    // registration file.
    /** nodeDynamics sends a "Hello World" message
     */
    @Override
    public void nodeDynamics(){

	sendMessage("Hello World.");
	//	System.out.println("Sent Hello World");

    }



    public static  void main(String args[]){

	// this parameter dt determines for how long the node will
	// sleep before waking up and calling the nodeDynamics and
	// other essential methods. It is basically the "sampling
	// rate" of the node.

	double myDt = 0.5; //  Sampling rate in seconds.

	RubiSend rn = new RubiSend(args, myDt );
	rn.run();

    }
}
