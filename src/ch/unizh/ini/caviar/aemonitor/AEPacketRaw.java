/*
 * AEPacket.java
 *
 * Created on October 28, 2005, 5:57 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package ch.unizh.ini.caviar.aemonitor;


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
 These AEPacketRaw are used only for device events (raw events). For processed events, see the ch.unizh.ini.caviar.event package.
 *
 * @author tobi
 */
public class AEPacketRaw extends AEPacket {
    
    /** the  index of the start of the last packet captured from a device, used for processing data on acquisition. The hardware interface class is responsible for
     setting this value.*/
    public int lastCaptureIndex=0;
    /** the number of events last captured. The hardware interface class is responsible for
     setting this value. */
    public int lastCaptureLength=0;
    
    /** The raw AER addresses */
    public int[] addresses;

    /** Signals that an overrun occured on this packet */
    public boolean overrunOccuredFlag=false;
    
    EventRaw event=new EventRaw();
    
    /** Creates a new instance of AEPacketRaw with 0 capacity */
    public AEPacketRaw() {
    }
    /** Creates a new instance of AEPacketRaw from addresses and timestamps
     * @param addresses
     * @param timestamps
     */
    public AEPacketRaw(int[] addresses, int[] timestamps) {
        if(addresses==null || timestamps==null) return;
        setAddresses(addresses);
        setTimestamps(timestamps);
        if(addresses.length!=timestamps.length) throw new RuntimeException("addresses.length="+addresses.length+"!=timestamps.length="+timestamps.length);
        capacity=addresses.length;
        numEvents=addresses.length;
    }
    
    /** Creates a new instance of AEPacketRaw with an initial capacity
     *@param size capacity in events
     */
    public AEPacketRaw(int size){
        allocateArrays(size);
    }
    
    protected void allocateArrays(int size){
        addresses=new int[size]; //new E[size];
        timestamps=new int[size];
        this.capacity=size;
        numEvents=0;
    }
    
    public int[] getAddresses() {
        return this.addresses;
    }
    
    public void setAddresses(final int[] addresses) {
        this.addresses = addresses;
        if(addresses==null) numEvents=0; else numEvents=addresses.length;
    }
    
    /** uses local EventRaw to return packaged event. (Does not create a new object instance.) */
    public EventRaw getEvent(int k){
        event.timestamp=timestamps[k];
        event.address=addresses[k];
        return event;
    }
    
    
    public int getCapacity() {
        return this.capacity;
    }
    
    /** ensure the capacity given. If present capacity is less than capacity, then arrays are newly allocated.
     *@param c the desired capacity
     */
    public void ensureCapacity(final int c) {
        super.ensureCapacity(c);
        if(addresses==null) {
            addresses=new int[c];
            this.capacity=c;
        }else if(addresses.length<c){
            int newcap=(int)ENLARGE_CAPACITY_FACTOR*c;
            int[] newaddresses=new int[newcap];
            System.arraycopy(addresses, 0, newaddresses, 0, addresses.length);
            addresses=newaddresses;
            this.capacity=newcap;
        }
    }
    
    /** @param e an Event to add to the ones already present. Capacity is enlarged if necessary. */
    public void addEvent(EventRaw e){
        if(e==null){
            log.warning("tried to add null event, not adding it");
        }
        super.addEvent(e); // will increment numEvents
        int n=getCapacity();    // make sure our address array is big enough
        this.ensureCapacity(n); // enlarge the array if necessary
        addresses[numEvents-1]=e.address; // store the address at the end of the array
        // numEvents++; // we already incremented the number of events in the super call
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
    public AEPacketRaw getPrunedCopy(){
        int n=getNumEvents();
        AEPacketRaw dest=new AEPacketRaw(n);
        int[] srcTs=getTimestamps();
        int[] srcAddr=getAddresses();
        int[] destTs=dest.getTimestamps();
        int[] destAddr=dest.getAddresses();
        System.arraycopy(srcTs,0,destTs,0,n);
        System.arraycopy(srcAddr,0,destAddr,0,n);
        dest.setNumEvents(n);
        return dest;
    }
}
