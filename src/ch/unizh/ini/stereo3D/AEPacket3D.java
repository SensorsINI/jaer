/*
 * AEPacket3D.java
 *
 * Created on October 28, 2005, 5:57 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package ch.unizh.ini.stereo3D;

import ch.unizh.ini.caviar.aemonitor.*;


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
 These AEPacket3D are used only for packaged 3D events (reconstructed from 3D filters/tarckers). For processed events, see the ch.unizh.ini.caviar.event package.
 *
 * @author tobi/rogister
 @see ch.unizh.ini.caviar.event package
 */
public class AEPacket3D extends AEPacket {
    
    /** the  index of the start of the last packet captured from a device, used for processing data on acquisition. The hardware interface class is responsible for
     setting this value.*/
  //  public int lastCaptureIndex=0;
    /** the number of events last captured. The hardware interface class is responsible for
     setting this value. */
  //  public int lastCaptureLength=0;
    
    /** The 3D matrix coordinates for x,y,z */
    public int[] coordinates3D_x;
    public int[] coordinates3D_y;
    public int[] coordinates3D_z;
    // events intensities
    public float[] values;
    
    /** Signals that an overrun occured on this packet */
    public boolean overrunOccuredFlag=false;
    
    Event3D event=new Event3D();
    
    /** Creates a new instance of AEPacketRaw with 0 capacity */
    public AEPacket3D() {
    }
    /** Creates a new instance of AEPacketRaw from addresses and timestamps
     * @param addresses
     * @param timestamps
     */
    public AEPacket3D(int[] coordinates3D_x, int[] coordinates3D_y, int[] coordinates3D_z, float[] values, int[] timestamps) {
        if(coordinates3D_x==null || coordinates3D_y==null || coordinates3D_z==null || values == null || timestamps==null) return;
        setCoordinates3D_x(coordinates3D_x);
        setCoordinates3D_y(coordinates3D_y);
        setCoordinates3D_z(coordinates3D_z);
        setTimestamps(timestamps);
        if(coordinates3D_x.length!=timestamps.length) throw new RuntimeException("coordinates3D_x.length="+coordinates3D_x.length+"!=timestamps.length="+timestamps.length);
        if(coordinates3D_y.length!=timestamps.length) throw new RuntimeException("coordinates3D_y.length="+coordinates3D_y.length+"!=timestamps.length="+timestamps.length);
        if(coordinates3D_z.length!=timestamps.length) throw new RuntimeException("coordinates3D_z.length="+coordinates3D_z.length+"!=timestamps.length="+timestamps.length);
        
        capacity=coordinates3D_x.length;
        numEvents=coordinates3D_x.length;
    }
    
    /** Creates a new instance of AEPacketRaw with an initial capacity
     *@param size capacity in events
     */
    public AEPacket3D(int size){
        allocateArrays(size);
    }
    
    protected void allocateArrays(int size){
        coordinates3D_x=new int[size]; 
        coordinates3D_y=new int[size]; 
        coordinates3D_z=new int[size]; 
        values=new float[size];
        timestamps=new int[size];
        this.capacity=size;
        super.capacity = size;
        numEvents=0;
    }
    
    public float[] getValues() {
        return this.values;
    }
    
    public int[] getCoordinates3D_x() {
        return this.coordinates3D_x;
    }
    public int[] getCoordinates3D_y() {
        return this.coordinates3D_y;
    }
    public int[] getCoordinates3D_z() {
        return this.coordinates3D_z;
    }
    
    public void setCoordinates3D_x(final int[] coordinates3D ) {
        this.coordinates3D_x = coordinates3D;
        
        if(coordinates3D==null) numEvents=0; else numEvents=coordinates3D.length;
    }
    public void setCoordinates3D_y(final int[] coordinates3D ) {
        this.coordinates3D_y = coordinates3D;
        
        if(coordinates3D==null) numEvents=0; else numEvents=coordinates3D.length;
    }
    public void setCoordinates3D_z(final int[] coordinates3D ) {
        this.coordinates3D_z = coordinates3D;
        
        if(coordinates3D==null) numEvents=0; else numEvents=coordinates3D.length;
    }
    
    /** uses local EventRaw to return packaged event. (Does not create a new object instance.) */
    public Event3D getEvent(int k){
        event.timestamp=timestamps[k];
        event.x=coordinates3D_x[k];
        event.y=coordinates3D_y[k];
        event.z=coordinates3D_z[k];
        event.value=values[k];
        return event;
    }
    
    
    public int getCapacity() {
        return this.capacity;
    }
    
    /** ensure the capacity given. If present capacity is less than capacity, then arrays are newly allocated.
     *@param c the desired capacity
     */
    public int[] ensureCapacity(int[] array, final int c) {
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
    public float[] ensureCapacity(float[] array, final int c) {
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
    public void addEvent(Event3D e){
        if(e==null){
            //log.warning("tried to add null event, not adding it");
            return;
        }
        
      
        
        super.addEvent(e); 
        int n=getCapacity();    // make sure our address array is big enough
        super.ensureCapacity(numEvents);
    //     System.out.println("******* 1. n:"+n+" numEvents:"+numEvents);
        coordinates3D_x = this.ensureCapacity(coordinates3D_x,numEvents); // enlarge the array if necessary
        coordinates3D_y = this.ensureCapacity(coordinates3D_y,numEvents); // enlarge the array if necessary
        coordinates3D_z = this.ensureCapacity(coordinates3D_z,numEvents); // enlarge the array if necessary
        values = this.ensureCapacity(values,numEvents);
        
  //      System.out.println("******* 2. n:"+n+" numEvents:"+numEvents);
  //      System.out.println("******* coordinates3D_x.length:"+coordinates3D_x.length);
  //      System.out.println("******* coordinates3D_y.length:"+coordinates3D_y.length);
        
        coordinates3D_x[numEvents-1]=e.x; // store the location at the end of the array
        coordinates3D_y[numEvents-1]=e.y;
        coordinates3D_z[numEvents-1]=e.z;
        values[numEvents-1]=e.value;
          
    }
    
    /** sets number of events to zero */
    @Override public void clear(){
        setNumEvents(0);
    }
    
    /**
     Allocates a new AEPacketRaw and copies the events from this packet into the new one, returning it.
     The size of the new packet that is returned is exactly the number of events stored in the this packet.
     This method can be used to more efficiently use matlab memory, which handles java garbage collection poorly.
     @return a new packet sized to the src packet number of events
     */
    public AEPacket3D getPrunedCopy(){
        int n=getNumEvents();
        AEPacket3D dest=new AEPacket3D(n);
        int[] srcTs=getTimestamps();
        int[] srcAddrx=getCoordinates3D_x();
        int[] srcAddry=getCoordinates3D_y();
        int[] srcAddrz=getCoordinates3D_z();
        float[] srcValues=getValues();
        int[] destTs=dest.getTimestamps();
        int[] destAddrx=dest.getCoordinates3D_x();
        int[] destAddry=dest.getCoordinates3D_y();
        int[] destAddrz=dest.getCoordinates3D_z();
        float[] destValues=getValues();
        System.arraycopy(srcTs,0,destTs,0,n);
        System.arraycopy(srcAddrx,0,destAddrx,0,n);
        System.arraycopy(srcAddry,0,destAddry,0,n);
        System.arraycopy(srcAddrz,0,destAddrz,0,n);
        System.arraycopy(srcValues,0,destValues,0,n);
        return dest;
    }
}
