/** EventPacket.java
 *
 * Created on October 29, 2005, 10:18 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */
package net.sf.jaer.event;
import java.util.logging.Level;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.eventprocessing.*;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.util.Iterator;
import java.util.Comparator;
import java.util.Arrays;
import java.util.logging.Logger;
import net.sf.jaer.aemonitor.AEConstants;
/**
 * A packet of events that is used for rendering and event processing.
For efficiency, these packets are designed to be re-used;
they should be allocated rarely and allowed to grow in size. If a packet capacity needs to be
increased a substantial performance hit will occur e.g. 1 ms per resizing or initial allocation.
<p>
The EventPacket is prefilled with Events that have default values. One constructor lets
you fill the EventPacket with a subclass of BasicEvent. This prefilling avoids
the overhead of object creation. It also allows easy access to all information contained in the
event and it allows storing arbitrary event information, including
extended type information, in EventPackets.
<p>
However, this reuse of existing objects means that you need to take particular precautions.
 * The events that are stored
in a packet are references to objects. Therefore you can assign an event to a different packet
but this event will still be referenced in the original packet
and can change.
<p>
Generally in event processing, you will iterate over an input packet to produce an output packet.
You iterate over an existing EventPacket that has input data using the iterator().
This lets you access the events in the input packet.
<p>
When you want to write these events to an existing output packet,
then you need to use the target event's copyFrom(Event e) method
that copies all the fields in the
source packet to the target packet. This lets you copy data such as
timestamp, x,y location to a target event. You can then fill in the target event's extended
type information.
<p>
When you iterate over an input packet to write to a target packet,
you obtain the target event to write your results to by using the target packet's
output enumeration by using the outputIterator() method. This enumeration has a method nextOutput() that returns the
next output event to write to. This nextOutput() method also expands the packet if they current capacity needs to be enlarged.
The iterator is initialized by the call to outputIterator().
<p>
The amount of time iterating over events can also be limited by using the time limiter.
This static (class) method starts a timer when it is restarted and after timeout, no more events are returned
from input iteration. These methods are used in FilterChain to limit processing time.

 * @author tobi
 */
public class EventPacket<E extends BasicEvent> implements /*EventPacketInterface<E>,*/ Cloneable, Iterable<E> {
    static Logger log=Logger.getLogger(EventPacket.class.getName());
    /** The time limiting Timer - this is command to JVM and will be shared by all filters on all viewers. */
    public static TimeLimiter timeLimitTimer=new TimeLimiter();
    /** Default capacity in events for new EventPackets */
    public final int DEFAULT_INITIAL_CAPACITY=4096;
    private int capacity;
    /** the number of events eventList actually contains (0 to size-1) */
    public int size=0;
    private Class eventClass=null;
    /** Constructs new events for this packet. */
    protected Constructor eventConstructor=null;
    private E eventPrototype;
    public transient E[] elementData;
    private AEPacketRaw rawPacket=null;
    
    /** The modification system timestamp of the EventPacket in ns, from System.nanoTime(). Some hardware interfaces set this field 
     * when the packet is started to be filled with events from hardware.
     * This timestamp is not related to the event times of the events in the packet.
     */
    public long systemModificationTimeNs=0; 


    /** Resets the time limiter for input iteration. After the timer times out
    (time determined by timeLimitMs) input iterators will not return any more events.
     */
    static public void restartTimeLimiter() {
        timeLimitTimer.restart();
    }

    /** restart the time limiter with limit timeLimitMs
    @param timeLimitMs time in ms
     */
    public static void restartTimeLimiter(int timeLimitMs) {
        setTimeLimitMs(timeLimitMs);
        restartTimeLimiter();
    }

    /** Constructs a new EventPacket filled with BasicEvent. 
    @see net.sf.jaer.event.BasicEvent
     */
    public EventPacket() {
        this(BasicEvent.class);
    }

    /** Constructs a new EventPacket filled with the given event class.
    @see net.sf.jaer.event.BasicEvent
     */
    public EventPacket(Class<? extends BasicEvent> eventClass) {
        if(!BasicEvent.class.isAssignableFrom(eventClass)) { //Check if evenClass is a subclass of BasicEvent
            throw new Error("making EventPacket that holds "+eventClass+" but these are not assignable from BasicEvent");
        }
        setEventClass(eventClass);
    }

    /** Fills this with DEFAULT_INITIAL_CAPACITY of the event class */
    protected void initializeEvents() {
//        eventList=new ArrayList<E>(DEFAULT_INITIAL_CAPACITY);
//        elementData = (E[])new BasicEvent[DEFAULT_INITIAL_CAPACITY];
        elementData=(E[]) Array.newInstance(eventClass, DEFAULT_INITIAL_CAPACITY);
        fillWithDefaultEvents(0, DEFAULT_INITIAL_CAPACITY);
        size=0;
        capacity=DEFAULT_INITIAL_CAPACITY;
    }

    /** Populates the packet with default events from the eventConstructor.
     * 
     * @param startIndex 
     * @param endIndex 
     * @see #eventConstructor
     */
    private void fillWithDefaultEvents(int startIndex, int endIndex) {
        try {
            for(int i=startIndex; i<endIndex; i++) {
                E e=(E) eventConstructor.newInstance();
//                eventList.add(e);
                elementData[i]=e;
                eventPrototype=e;
            }
        } catch(Exception e) {
            log.warning("while filling packet with default events caught "+e);
            e.printStackTrace();
        }

    }

    /** Returns duration of packet in microseconds.
     *
     * @return 0 if there are less than 2 events, otherwise last timestamp minus first timestamp.
     */
    public int getDurationUs() {
        if(size<2) {
            return 0;
        } else {
            return getLastTimestamp()-getFirstTimestamp();
        }
    }

    public String getDescription() {
        return "";
    }

    /** Sets the size to zero. */
    public void clear() {
        size=0; // we don't clear list, because that nulls all the events
    }

    public void setSize(int n) {
        size=n;
//        eventList.
//        this.numEvents=n;
    }

    /** @return event rate for this packet in Hz measured stupidly by
     * the size in events divided by the packet duration.
     * If packet duration is zero (there are no events or zero time interval between the events),
     * then rate returned is zero.
     @return rate of events in Hz.
     */
    public float getEventRateHz() {
        if(getDurationUs()==0) {
            return 0;
        }
        return (float) getSize()/((float)getDurationUs()*AEConstants.TICK_DEFAULT_US*1e-6f);
    }

//    public void copyTo(EventPacket packet) {
//    }
    /** Returns first event, or null if there are no events.
     *
     * @return the event or null if there are no events.
     */
    public E getFirstEvent() {
        if(size==0) {
            return null;
        }
        return elementData[0];
//        return eventList.get(0);
    }

    /** Returns last event, or null if there are no events. 
     * 
     * @return the event or null if there are no events.
     */
    public E getLastEvent() {
        if(size==0) {
            return null;
        }
        return elementData[size-1];
//        return eventList.get(size-1);
    }

    /** Returns first timestamp or 0 if there are no events.
     *
     * @return timestamp
     */
    public int getFirstTimestamp() {
//        if(events==null) return 0; else return events[0].timestamp;
        return elementData[0].timestamp;
//        return eventList.get(0).timestamp;
    }

    /** @return last timestamp in packet. 
    If packet is empty, returns zero - which could be important if this time is used for e.g. filtering operations!
     */
    public int getLastTimestamp() {
//        if(size==0) return 0;
////        if(events==null) return 0; else return events[numEvents-1].timestamp;
//        return elementData[size-1].timestamp;
////        return eventList.get(size-1).timestamp;
        int s=size;
        if(s==0) {
            return 0;
        }
        return elementData[s-1].timestamp;
    }

    /** Returns the k'th event.
     * @throws  ArrayIndexOutOfBoundsException if out of bounds of packet.
     */
    final public E getEvent(int k) {
        if(k>=size) {
            throw new ArrayIndexOutOfBoundsException();
        }
        return elementData[k];
//        return eventList.get(k);
    }
    public InItr inputIterator=null;

    /** Returns after initializing the iterator over input events.
    @return an iterator that can iterate over the events.
     */
    public Iterator<E> inputIterator() {
        if(inputIterator==null) {
            inputIterator=new InItr();
        } else {
            inputIterator.reset();
        }
        return inputIterator;
    }
    private OutItr outputIterator=null;

    /** Returns an iterator that iterates over the output events.
     *
     * @return the iterator. Use it to obtain new output events which can be then copied from other events or modfified.
     */
    final public OutputEventIterator<E> outputIterator() {
        if(outputIterator==null) {
            outputIterator=new OutItr();
        } else {
            outputIterator.reset();
        }
        return outputIterator;
    }

    /**
     * Returns the raw data packet that this originally came from. These are the raw ints that represent the data from the device.
     * This AEPacketRaw may or may not be set by the <code>EventExtractor2D</code>.
     *
     * This packet may or may not actually refer to the same data as when the packet was extracted. This raw data packet may in the meantime
     * have been reused for other purposes.
     *
     * @return the raw packet
     * @see net.sf.jaer.event.EventPacketInterface<T>.
     */
    public AEPacketRaw getRawPacket() {
        return rawPacket;
    }

    /**
     * Sets the raw data packet associated with this.
     * @param rawPacket the rawPacket to set.
     *  @see net.sf.jaer.chip.EventExtractorInterface
     * @see net.sf.jaer.chip.EventExtractor2D
     */
    public void setRawPacket(AEPacketRaw rawPacket) {
        this.rawPacket = rawPacket;
    }


    final private class OutItr implements OutputEventIterator<E> {
        OutItr() {
            size=0; // reset size because we are starting off output packet
        }

        /** obtains the next output event. Increments the size of the packet */
        final public E nextOutput() {
            if(size>=capacity) {
                enlargeCapacity();
//                System.out.println("enlarged "+EventPacket.this);
            }
            return elementData[size++];
        }
        
        

        final public void reset() {
            size=0;
        }

        public String toString() {
            return "OutputEventIterator for packet with size="+size+" capacity="+capacity;
        }

        @Override
        public void writeToNextOutput(E event) {
            {   if(size>=capacity) {
                    enlargeCapacity();
                }
                elementData[size++]=event;
            }
        }
    }
    public class InItr implements Iterator<E> {
        int cursor;
        boolean usingTimeout=timeLimitTimer.isEnabled();

        public InItr() {
            reset();
        }

        public boolean hasNext() {
            if(usingTimeout) {
                return cursor<size&&!timeLimitTimer.isTimedOut();
            } else {
                return cursor<size;
            }
        }

        public E next() {
            return elementData[cursor++];
        }

        public void reset() {
            cursor=0;
            usingTimeout=timeLimitTimer.isEnabled(); // timelimiter only used if timeLimitTimer is enabled but flag to check it it only set on packet reset
        }

        public void remove() {
            for(int ctr=cursor; ctr<size; ctr++) {
                elementData[cursor-1]=elementData[cursor];
            }
            //go back as we removed a packet
            cursor--;
            size--;
        //throw new UnsupportedOperationException();
        }

        public String toString() {
            return "InputEventIterator cursor="+cursor+" for packet with size="+size;
        }
    }

    /* 
     * Comparator for ordering events by their timestamp
     * Order is oldest to newest.
     * Note: this comparator imposes orderings that are inconsistent with equals.
     */
    final private class TimeStampComparator implements Comparator<E> {
        public int compare(E e1, E e2) {
            return e1.timestamp - e2.timestamp;
        }
    }
    
    final public Comparator<E> TIMESTAMP_COMPARATOR = new TimeStampComparator();
    
    /*
     * Method for ordering events by ascending timestamp, i.e. oldest to newest
     */
    public void sortByTimeStamp() {
        if (size == 0) {
            return;
        }
        Arrays.sort(elementData, 0, size, TIMESTAMP_COMPARATOR);
    }
    
    /** Enlarges capacity by some factor, then copies all event references to the new packet */
    private void enlargeCapacity() {
        try {
            log.info("enlarging capacity of " + this);
            int ncapacity = capacity * 2; // (capacity*3)/2+1;
            Object oldData[] = elementData;
            elementData = (E[]) new BasicEvent[ncapacity];
            System.arraycopy(oldData, 0, elementData, 0, size);
            oldData = null;
            // capacity still is old capacity and we have already filled it to there with new events, now fill
            // in up to new capacity with new events
            fillWithDefaultEvents(capacity, ncapacity);
            capacity = ncapacity;
        } catch (OutOfMemoryError e) {
            log.log(Level.WARNING, "{0}: could not enlarge packet capacity from {1}", new Object[]{e.toString(), capacity});
            throw new ArrayIndexOutOfBoundsException("could not enlarge capacity from "+capacity);
        }
    }
    
    /** Ensures packet has room for n events. The original events are retained and the new capacity is filled with default
     * events.
     * 
     * @param n capacity
     * @see #fillWithDefaultEvents(int, int) 
     */
    public void allocate(int n) {
        if(n<=capacity) return;
        log.info("enlarging capacity of "+this+" to "+n+" events");
        int ncapacity=n; // (capacity*3)/2+1;
        Object oldData[]=elementData;
        elementData=(E[]) new BasicEvent[ncapacity];
        System.arraycopy(oldData, 0, elementData, 0, size);
        oldData=null;
        // capacity still is old capacity and we have already filled it to there with new events, now fill
        // in up to new capacity with new events
        fillWithDefaultEvents(size, ncapacity);
        capacity=ncapacity;
    }
    
    /** Adds the events from another packet to the events of this packet
     * 
     * @param packet EventPacket to be added
     */
    public void add(EventPacket packet) {
        if(packet.getEventClass() != getEventClass()){
            log.warning("Trying to merge packets that contain different events types");
        }
        E[] newData = (E[]) packet.getElementData();
        Object oldData[]=elementData;
        allocate(size+packet.size);
        System.arraycopy(oldData, 0, elementData, 0, size);
        System.arraycopy(newData, 0, elementData, size, packet.size);
        size=size+packet.size;
    }

//    public static void main(String[] args){
//        EventPacket p=new EventPacket();
//        p.test();
//    }
//    
    /**
    0.32913625s for 300 n allocations, 1097.1208 us/packet
    0.3350817s for 300 n allocations, 1116.939 us/packet
    0.3231394s for 300 n allocations, 1077.1313 us/packet
    0.32404426s for 300 n allocations, 1080.1475 us/packet
    0.3472975s for 300 n allocations, 1157.6583 us/packet
    0.33720487s for 300 n allocations, 1124.0162 us/packet
     */
//    void test(){
//        int nreps=5;
//        int size=30000;
//        long stime, etime;
//        EventPacket<BasicEvent> p,pout;
//        OutputEventIterator outItr;
//        Iterator<BasicEvent> inItr;
//        
//        System.out.println("make new packets");
//        for(int k=0;k<nreps;k++){
//            stime=System.nanoTime();
//            for(int i=0;i<nreps;i++){
//                p=new EventPacket();
//            }
//            etime=System.nanoTime();
//            
//            float timeSec=(etime-stime)/1e9f;
//            
//            System.out.println(timeSec+ "s"+" for "+nreps+" n allocations, "+1e6f*timeSec/nreps+" us/packet ");
//            System.out.flush();
//            try{
//                Thread.currentThread().sleep(10);
//            }catch(Exception e){}
//        }
//        
//        System.out.println("make a new packet and fill with events");
//        p=new EventPacket<BasicEvent>();
//        for(int k=0;k<nreps;k++){
//            stime=System.nanoTime();
//            outItr=p.outputIterator();
//            for(int i=0;i<size;i++){
//                BasicEvent e=outItr.nextOutput();
//                e.timestamp=i;
//                e.x=((short)i);
//                e.y=(e.x);
//            }
//            etime=System.nanoTime();
//            
//            float timeSec=(etime-stime)/1e9f;
//            
//            System.out.println(timeSec+ "s"+" for "+size+" fill, "+1e6f*timeSec/size+" us/event ");
//            System.out.flush();
//            try{
//                Thread.currentThread().sleep(10);
//            }catch(Exception e){}
//        }
//        
//        
//        System.out.println("iterate over packet, changing all values");
////        p=new EventPacket();
//        pout=new EventPacket<BasicEvent>();
//        
//        for(int k=0;k<nreps;k++){
//            stime=System.nanoTime();
//            inItr=p.inputIterator();
//            outItr=pout.outputIterator();
//            for(BasicEvent ein:p){
//                
////                while(inItr.hasNext()){
////                BasicEvent ein=inItr.next();
//                BasicEvent eout=outItr.nextOutput();
//                eout.copyFrom(ein);
//            }
//            etime=System.nanoTime();
//            
//            float timeSec=(etime-stime)/1e9f;
//            
//            System.out.println(timeSec+ "s"+" for iteration over packet with size="+p.getSize()+", "+timeSec/p.getSize()+" s per event");
//            System.out.flush();
//            try{
//                Thread.currentThread().sleep(10);
//            }catch(Exception e){}
//        }
//        
//        System.out.println("\nmake packet with OrientationEvent and assign polarity and orientation");
//        pout=new EventPacket(OrientationEvent.class);
//        OrientationEvent ori=null;
//        for(int k=0;k<nreps;k++){
//            stime=System.nanoTime();
//            outItr=pout.outputIterator();
//            for(int i=0;i<size;i++){
//                ori=(OrientationEvent)outItr.nextOutput();
//                ((PolarityEvent)ori).type=10;
//                ori.timestamp=i;
//                ori.orientation=(byte)(100);
////                ori.polarity=(byte)(20);
//            }
//            etime=System.nanoTime();
//            float timeSec=(etime-stime)/1e9f;
//            
//            System.out.println(timeSec+ "s"+" for iteration over packet with size="+p.getSize()+", "+timeSec/p.getSize()+" s per event");
//            System.out.flush();
//            try{
//                Thread.currentThread().sleep(10);
//            }catch(Exception e){}
//        }
//        System.out.println("ori event ="+pout.getEvent(0)+" with type="+ori.getType());
//        System.out.println(pout.toString());
//        
//    }
//
    /** Returns the number of events in the packet.
     * If the packet has extra data not consisting of events this method could return 0 but there could still be data, e.g. sampled ADC data, image frames, etc.
     *
     * @return size in events.
     */
    final public int getSize() {
        return size;
    }

    /** Reports if the packet is empty. The default implementation reports true if size in events is zero, but subclasses can override this method
     * to report true if associated data exists.
     *
     * @return true if empty.
     */
    public boolean isEmpty() {
        return size==0?true:false;
    }

    @Override
    public String toString() {
        int sz=getSize();
        String s="EventPacket #"+this.hashCode()+" holding "+getEventClass().getSimpleName()+" with size="+sz+" capacity="+capacity;
        return s;
    }

    /** Returns the number of 'types' of events.
     *
     * @return the number of types, typically a small number like 1,2, or 4.
     */
    final public int getNumCellTypes() {
        return eventPrototype.getNumCellTypes();
    }

    /** Returns a prototype of the events in the packet.
     *
     * @return a single instance of the event.
     */
    final public E getEventPrototype() {
        return eventPrototype;
    }

    /** Sets the prototype event of the packet.
     * E the event prototype which is an instance of an event.
     *
     */
    final public void setEventPrototype(E e) {
        eventPrototype=e;
    }

    /** Initializes and returns the iterator */
    public Iterator<E> iterator() {
        return inputIterator();
    }

    /** Returns the class of event in this packet.
    @return the event class.
     */
    final public Class getEventClass() {
        return eventClass;
    }

    /** Sets the constructor for new (empty) events and initializes the packet. 
     * 
     * @param constructor - a zero argument constructor for the new events.
     */
    public final void setEventClass(Constructor constructor){
        this.eventConstructor=constructor;
        this.eventClass=eventConstructor.getDeclaringClass();
        initializeEvents();
    }
    
    /** Sets the event class for this packet and fills the packet with these events.
     *
     * @param eventClass which much extend BasicEvent
     */
    public final void setEventClass(Class<? extends BasicEvent> eventClass) {
        this.eventClass=eventClass;
        try {
            eventConstructor=eventClass.getConstructor();
        } catch(NoSuchMethodException e) {
            log.warning("cannot get constructor for constructing Events for building EventPacket: exception="+e.toString()+", cause="+e.getCause());
            e.printStackTrace();
        }
        initializeEvents();
    }

    /** Gets the class time limit for iteration in ms
     */
    final public static int getTimeLimitMs() {
        return timeLimitTimer.getTimeLimitMs();
    }

    /** Sets the class time limit for filtering a packet through the filter chain in ms.
    @param timeLimitMs the time limit in ms
    @see #restartTimeLimiter
     */
    final public static void setTimeLimitMs(int timeLimitMs) {
        timeLimitTimer.setTimeLimitMs(timeLimitMs);
    }

    final public static void setTimeLimitEnabled(boolean yes) {
        timeLimitTimer.setEnabled(yes);
    }

    /** Returns status of time limiting
    @return true if timelimiting is enabled
     */
    final public static boolean isTimeLimitEnabled() {
        return timeLimitTimer.isEnabled();
    }

    /** Returns true if timeLimitTimer is timed out and timeLimitEnabled */
    final public static boolean isTimedOut() {
        return timeLimitTimer.isTimedOut();
    }

    /** Returns the element data.
     *
     * @return the underlying element data
     */
    public E[] getElementData() {
        return elementData;
    }

    /** Sets the internal data of the packet. TODO needs more details about elements
     *
     * @param elementData the underlying element data, which should extend BasicEvent
     */
    public void setElementData(E[] elementData) {
        this.elementData = elementData;
    }

}

