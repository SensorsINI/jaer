package edu.mplab.rubios.node;

import java.util.StringTokenizer;

/**
 * RUBIOSRequest 
 * <p>
 * Generic class for request format used in the buffer of past
 * 
 * @author Javier R. Movellan 
 * Copyright UCSD, Machine Perception Laboratory,  Javier R. Movellan
 * License  GPL
 * Date April 23, 2006
 *
 */
public class RUBIOSRequest{
    private double timeConstant= 1;
    private double maxTimeConstant = 365*30*24*60*60; //number of secs
						      //in a year
    public double invTimeConstant;
    private double stateConstant= 1;
    public double sigma2;
    public double peakUtility=0;
    int nMessages=1;
    public String  message[];
    public int nX =1;
    public double  X[]; // Represent the target state for that request.
    public String ID = "DefaultID";
    public boolean serializedConstructionOK = false;


    public RUBIOSRequest(int _nM, int _nX){
	nMessages = _nM;
	nX = _nX;
	message = new String[nMessages];
	X = new double[nX];
	for (int i =0;i<nMessages;i++){
	    message[i] = "DefaultMessage";
	}
	for (int i =0;i<nX;i++){
	    X[i]=0;
	}
	
    }
	
	

    public RUBIOSRequest(){
	this(1,0);
    }



    
    public RUBIOSRequest(String serializedRequest){

	
	StringTokenizer st = new StringTokenizer(serializedRequest);
	String str; 
	
		
	if(! st.hasMoreTokens()){return  ;}
	str = st.nextToken();	str.trim();
	try{
	    timeConstant  = Double.valueOf(str).doubleValue();
	}catch (NumberFormatException e){return  ;} 
	
	if(! st.hasMoreTokens()){return  ;}
	str = st.nextToken();	str.trim();
	try{
	    stateConstant  = Double.valueOf(str).doubleValue();
	}catch (NumberFormatException e){return  ;} 
	
	if(! st.hasMoreTokens()){return  ;}
	str = st.nextToken();	str.trim();
	try{
	    peakUtility  = Double.valueOf(str).doubleValue();
	}catch (NumberFormatException e){return  ;} 
	
	if(! st.hasMoreTokens()){return  ;}
	str = st.nextToken();	str.trim();
	ID = str;

	
	if(! st.hasMoreTokens()){return  ;}
	str = st.nextToken();	str.trim();
	try{
	    nMessages  = Integer.valueOf(str).intValue();
	}catch (NumberFormatException e){return  ;} 


	if(! st.hasMoreTokens()){return  ;}
	str = st.nextToken();	str.trim();
	try{
	    nX  = Integer.valueOf(str).intValue();
	}catch (NumberFormatException e){return  ;} 
	

	message = new String[nMessages];
	X = new double[nX];
	
	for(int i=0;i< this.nX; i++){
	    if(! st.hasMoreTokens()){return  ;}
	    str = st.nextToken();	str.trim();
	    try{
		X[i] = Double.valueOf(str).doubleValue();
	    }catch (NumberFormatException e){return  ;} 
	}

	for(int i=0;i< nMessages; i++){
	    if(! st.hasMoreTokens()){return  ;}
	    str = st.nextToken();	str.trim();
	    message[i] = str;
	}
	this.setParameters(timeConstant,stateConstant,peakUtility);
	serializedConstructionOK= true;
	
  }


        

    public final boolean  setParameters( double tc, double sc, double ut){
	if(ut <0 ) ut=0;
	if(ut > 100 ) ut = 100;
	if (tc <=0) tc =0.00001;
	if(sc<=0 ) sc = 0.00001;


	peakUtility= ut;
	timeConstant = tc;
	if(timeConstant > maxTimeConstant -1.0){
	    invTimeConstant =0;
	}
	else{
	    invTimeConstant = 1/tc;
	}
	stateConstant = sc;
	sigma2 = 0.1056/(sc*sc); 
	return true;

    }



   public final boolean  setParameters( String infty, double sc, double ut){
       

       return setParameters(maxTimeConstant, sc, ut);
   }


   public final double  getTimeConstant(){
       return timeConstant;
   }

   public final double  getStateConstant(){
       return stateConstant;
   }



    public final String  serialize(){

	String message = Float.toString((float) this.getTimeConstant());
	message += " "+ Float.toString((float) this.getStateConstant());
	message += " "+ Float.toString((float) this.peakUtility);
	message += " "+this.ID;
	message += " "+ Integer.toString(this.nMessages);
	message += " "+ Integer.toString(this.nX);
	for(int i=0; i< this.nX; i++){
	    message+=" "+Double.toString(this.X[i]);
	}
	for(int i=0; i< this.nMessages; i++){
	    message+=" "+this.message[i];
	}
	return message ;

    }



    public final RUBIOSRequest copyRequest(){
	RUBIOSRequest ar = new RUBIOSRequest(nMessages, nX);
	ar.ID= ID;
	ar.setParameters(timeConstant, stateConstant, peakUtility);
	for(int i=0;i<nMessages;i++){
	    ar.message[i] = message[i];
	}
	for(int i=0;i<nX;i++){
	    ar.X[i] = X[i];
	}
	return ar;
    }



    public void printRequest(){
      	System.out.println("ID= " +ID);
	System.out.println("Time Constant= " +timeConstant);
	System.out.println("State Constant= " +stateConstant);
	System.out.println("PeakUtility= " +peakUtility);
	for (int i=0;i<nMessages;i++){
	    System.out.println("Message["+i+"]= " +message[i]);
	}

	for (int i=0;i<nX;i++){
	    System.out.println("X["+i+"]= " +X[i]);
	}

    }


    public final void printSerializedRequest( ){
	String s =this.serialize();
	if( s == null) return;
	System.out.println(this.serialize());
    }



    public static void main(String[] argc){
	int mynX = 3;
	int mynM = 4;
	
	RUBIOSRequest rr = new RUBIOSRequest(mynM, mynX);
	rr.setParameters(101,201,99);
	rr.ID="UniTest";
	for(int i = 0;i<mynX;i++){
	    rr.X[i] = (double) i;
	}

	for(int i = 0;i<mynM;i++){
	    rr.message[i] = "Test_Message_Number"+ i;
	}

	rr.printRequest();
	String str = rr.serialize();
	System.out.println("---------Serialized----------");
	rr.printSerializedRequest();
	RUBIOSRequest ur = new RUBIOSRequest(str);
	System.out.println("---------UnSerialized----------");
	ur.printRequest();

    }

    
}

