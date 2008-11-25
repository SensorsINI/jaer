/*
 * AEPacket3D.java
 *
 * Created on October 28, 2005, 5:57 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package ch.unizh.ini.jaer.projects.stereo3D;

import net.sf.jaer.aemonitor.*;


/**
 * A structure containing a packer of AEs: addresses, timestamps.
 * The AE packet efficiently packages a set of events: rather than
 * using an object per event, it packs a lot of events into an object that references
 * arrays of primitives. These arrays can be newly allocated or statically
 * allocated to the capacity of the maximum buffer that is transferred from a device.
 * Callers must use {@link #getNumEvents} to find out the capacity of the packet in the case
 * that the arrays contain less events than their capacity, which is usually the case when the packet is reused
 in a device acquisition.
 <p>
 These AEPacket3D are used only for packaged 3D events (reconstructed from 3D filters/tarckers). For processed events, see the net.sf.jaer.event package.
 *
 * @author tobi/rogister
 @see net.sf.jaer.event.EventPacket
 */
public class AEPacket3D extends AEPacket {
    
    /** the  index of the start of the last packet captured from a device, used for processing data on acquisition. The hardware interface class is responsible for
     setting this value.*/
  //  public int lastCaptureIndex=0;
    /** the number of events last captured. The hardware interface class is responsible for
     setting this value. */
  //  public int lastCaptureLength=0;
    
    /** The 3D matrix coordinates for x,y in two arrays Left and Right */
     int type;
     int[] coordinates_x;
     int[] coordinates_y;
     int[] coordinates_z;
     int[] disparities;
     int[] methods;
     int[] lead_sides;
    // events intensities
     float[] values;
  
    
    /** Signals that an overrun occured on this packet */
    public boolean overrunOccuredFlag=false;
    
    Event3D event=new Event3D();
    
    /** Creates a new instance of AEPacketRaw with 0 capacity */
    public AEPacket3D() {
    }
    /** Creates a new instance of AEPacketRaw from addresses and timestamps
     * @param timestamps
     */
    public AEPacket3D(int[] coordinates_x, int[] coordinates_y, int[] disparities, int[] methods, int[] lead_sides, float[] values, int[] timestamps) {
        if(coordinates_x==null || coordinates_y==null || disparities==null || lead_sides==null  || methods==null || values == null || timestamps==null) return;
        type = Event3D.INDIRECT3D;
        setCoordinates_x(coordinates_x);
        setCoordinates_y(coordinates_y);
        setDisparities(disparities);
        setMethods(methods);        
        setLead_sides(lead_sides);
        setTimestamps(timestamps);
        if(coordinates_x.length!=timestamps.length) throw new RuntimeException("coordinates_x.length="+coordinates_x.length+"!=timestamps.length="+timestamps.length);
        if(coordinates_y.length!=timestamps.length) throw new RuntimeException("coordinates_y.length="+coordinates_y.length+"!=timestamps.length="+timestamps.length);
        if(disparities.length!=timestamps.length) throw new RuntimeException("disparities.length="+disparities.length+"!=timestamps.length="+timestamps.length);
        if(methods.length!=timestamps.length) throw new RuntimeException("methods.length="+methods.length+"!=timestamps.length="+timestamps.length);
        if(lead_sides.length!=timestamps.length) throw new RuntimeException("lead_sides.length="+lead_sides.length+"!=timestamps.length="+timestamps.length);
  
        capacity=coordinates_x.length;
        numEvents=coordinates_x.length;
    }
    
     public AEPacket3D(int[] coordinates_x, int[] coordinates_y, int[] coordinates_z,  float[] values, int[] timestamps) {
        if(coordinates_x==null || coordinates_y==null || coordinates_z==null || values == null || timestamps==null) return;
        type = Event3D.DIRECT3D;
        setCoordinates_x(coordinates_x);
        setCoordinates_y(coordinates_y);
        setCoordinates_z(coordinates_z);
        setTimestamps(timestamps);
        if(coordinates_x.length!=timestamps.length) throw new RuntimeException("coordinates_x.length="+coordinates_x.length+"!=timestamps.length="+timestamps.length);
        if(coordinates_y.length!=timestamps.length) throw new RuntimeException("coordinates_y.length="+coordinates_y.length+"!=timestamps.length="+timestamps.length);
        if(coordinates_z.length!=timestamps.length) throw new RuntimeException("coordinates_z.length="+coordinates_z.length+"!=timestamps.length="+timestamps.length);
      
       
        capacity=coordinates_x.length;
        numEvents=coordinates_x.length;
    }
    
    /** Creates a new instance of AEPacketRaw with an initial capacity
     *@param size capacity in events
     */
    public AEPacket3D(int size, int type){
        allocateArrays(size);
        this.type = type;
    }
    
    protected synchronized void allocateArrays(int size){
        coordinates_x=new int[size]; 
        coordinates_y=new int[size];
        coordinates_z=new int[size]; 
        disparities=new int[size]; 
        methods=new int[size]; 
        lead_sides=new int[size]; 
        values=new float[size];
        timestamps=new int[size];
        this.capacity=size;
        super.capacity = size;
        numEvents=0;
    }
    
    
    public synchronized int getType() {
        return this.type;
    }
    public synchronized void setType( int type ) {
        this.type = type;
    }
    public synchronized int[] getTimestamps() {
        return this.timestamps;
    }
    public synchronized float[] getValues() {
        return this.values;
    }
    
    public synchronized int[] getCoordinates_x() {
        return this.coordinates_x;
    }
    public synchronized int[] getCoordinates_y() {
        return this.coordinates_y;
    }
    public synchronized int[] getCoordinates_z() {
        return this.coordinates_z;
    }
    public synchronized int[] getDisparities() {
        return this.disparities;
    }
    public synchronized int[] getMethods() {
        return this.methods;
    }
    public synchronized int[] getLead_sides() {
        return this.lead_sides;
    }
    
    public synchronized void setCoordinates_x(final int[] coordinates ) {
        this.coordinates_x = coordinates;
        
        if(coordinates==null) numEvents=0; else numEvents=coordinates.length;
    }
    public synchronized void setCoordinates_y(final int[] coordinates ) {
        this.coordinates_y = coordinates;
        
        if(coordinates==null) numEvents=0; else numEvents=coordinates.length;
    }
    public synchronized void setCoordinates_z(final int[] coordinates ) {
        this.coordinates_z = coordinates;
        
        if(coordinates==null) numEvents=0; else numEvents=coordinates.length;
    }
    public synchronized void setDisparities(final int[] disparities ) {
        this.disparities = disparities;
        
        if(disparities==null) numEvents=0; else numEvents=disparities.length;
    }
    public synchronized void setMethods(final int[] methods ) {
        this.methods = methods;
        
        if(methods==null) numEvents=0; else numEvents=methods.length;
    }
    public synchronized void setLead_sides(final int[] lead_sides ) {
        this.lead_sides = lead_sides;
        
        if(lead_sides==null) numEvents=0; else numEvents=lead_sides.length;
    }
  
    
    /** uses local EventRaw to return packaged event. (Does not create a new object instance.) */
    public synchronized Event3D getEvent(int k){
        if(type==Event3D.DIRECT3D){
            event.type=type;
            event.timestamp=timestamps[k];
            event.x0=coordinates_x[k];
            event.y0=coordinates_y[k];
            event.z0=coordinates_z[k];                       
            event.value=values[k];
        } else {
            event.type=type;
            event.timestamp=timestamps[k];
            event.x=coordinates_x[k];
            event.y=coordinates_y[k];
            event.d=disparities[k];
            event.method=methods[k];
            event.lead_side=lead_sides[k];
            event.value=values[k];
        }
        return event;
    }
    
    
    public synchronized int getCapacity() {
        return this.capacity;
    }
    
    /** ensure the capacity given. If present capacity is less than capacity, then arrays are newly allocated.
     *@param c the desired capacity
     */
    public synchronized int[] ensureCapacity(int[] array, final int c) {
       // super.ensureCapacity(c);
        if(array==null) {
            array=new int[c];
            this.capacity=c;
        }else if(array.length<c){
            int newcap=(int)2*c; // check ENLARGE_CAPACITY_FACTOR           
            int[] newarray=new int[newcap];
            System.arraycopy(array, 0, newarray, 0, array.length);
            array=newarray;
            this.capacity=newcap;
        }
        return array;
    }
    public synchronized float[] ensureCapacity(float[] array, final int c) {
        //super.ensureCapacity(c);
        if(array==null) {
            array=new float[c];
            this.capacity=c;
        }else if(array.length<c){
            int newcap=(int)2*c; // check ENLARGE_CAPACITY_FACTOR          
            float[] newarray=new float[newcap];
            System.arraycopy(array, 0, newarray, 0, array.length);
            array=newarray;
            this.capacity=newcap;
        }
        return array;
    }
    
    /** @param e an Event to add to the ones already present. Capacity is enlarged if necessary. */
    public synchronized void addEvent(Event3D e){
        if(e==null){
            //log.warning("tried to add null event, not adding it");
            return;
        }
        
      
        
        super.addEvent(e); 
        int n=getCapacity();    // make sure our address array is big enough
        super.ensureCapacity(numEvents);
    //     System.out.println("******* 1. n:"+n+" numEvents:"+numEvents);
        
        coordinates_x = this.ensureCapacity(coordinates_x,numEvents); // enlarge the array if necessary
        coordinates_y = this.ensureCapacity(coordinates_y,numEvents); // enlarge the array if necessary
          values = this.ensureCapacity(values,numEvents);
          
          if(type==Event3D.DIRECT3D){
              coordinates_z = this.ensureCapacity(coordinates_z,numEvents); // enlarge the array if necessary
              
              coordinates_x[numEvents-1]=e.x0; // store the location at the end of the array
              coordinates_y[numEvents-1]=e.y0;
              coordinates_z[numEvents-1]=e.z0;
              
          } else {
              disparities = this.ensureCapacity(disparities,numEvents); // enlarge the array if necessary
              methods = this.ensureCapacity(methods,numEvents); // enlarge the array if necessary
              lead_sides = this.ensureCapacity(lead_sides,numEvents); // enlarge the array if necessary
              
              coordinates_x[numEvents-1]=e.x; // store the location at the end of the array
              coordinates_y[numEvents-1]=e.y;
              disparities[numEvents-1]=e.d;
              methods[numEvents-1]=e.method;
              lead_sides[numEvents-1]=e.lead_side;
          }
  //      System.out.println("******* 2. n:"+n+" numEvents:"+numEvents);
  //      System.out.println("******* coordinates3D_x.length:"+coordinates3D_x.length);
  //      System.out.println("******* coordinates3D_y.length:"+coordinates3D_y.length);
        
       
        values[numEvents-1]=e.value;
          
    }
    
    /** sets number of events to zero */
    @Override public synchronized void clear(){
        setNumEvents(0);
    }
    
    /**
     Allocates a new AEPacketRaw and copies the events from this packet into the new one, returning it.
     The size of the new packet that is returned is exactly the number of events stored in the this packet.
     This method can be used to more efficiently use matlab memory, which handles java garbage collection poorly.
     @return a new packet sized to the src packet number of events
     */
    public synchronized AEPacket3D getPrunedCopy(){
        int n=getNumEvents();
        AEPacket3D dest=new AEPacket3D(n,type);
      
        int[] srcTs=getTimestamps();
        int[] srcAddrx=getCoordinates_x();
        int[] srcAddry=getCoordinates_y();
        int[] srcAddrz=getCoordinates_z();
        int[] srcAddrd=getDisparities();
        int[] srcMethods=getMethods();
        int[] srcLead_sides=getLead_sides();
        float[] srcValues=getValues();
        int[] destTs=dest.getTimestamps();
        int[] destAddrx=dest.getCoordinates_x();
        int[] destAddry=dest.getCoordinates_y();
        int[] destAddrz=dest.getCoordinates_z();
        int[] destAddrd=dest.getDisparities();
        int[] destMethods=dest.getMethods();
        int[] destLead_sides=dest.getLead_sides();
        float[] destValues=dest.getValues();
        System.arraycopy(srcTs,0,destTs,0,n);
        System.arraycopy(srcAddrx,0,destAddrx,0,n);
        System.arraycopy(srcAddry,0,destAddry,0,n);
        System.arraycopy(srcAddrz,0,destAddrz,0,n);//maybe check on type here also
        System.arraycopy(srcAddrd,0,destAddrd,0,n);
        System.arraycopy(srcMethods,0,destMethods,0,n);
        System.arraycopy(srcLead_sides,0,destLead_sides,0,n);
        System.arraycopy(srcValues,0,destValues,0,n);
        return dest;
    }
}
