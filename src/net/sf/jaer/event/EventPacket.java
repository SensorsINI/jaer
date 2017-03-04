/**
 * EventPacket.java
 *
 * Created on October 29, 2005, 10:18 PM
 */
package net.sf.jaer.event;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.jaer.aemonitor.AEConstants;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.eventprocessing.TimeLimiter;

/**
 * A packet of events that is used for rendering and event processing. For
 * efficiency, these packets are designed to be re-used; they should be
 * allocated rarely and allowed to grow in size. If a packet capacity needs to
 * be increased a substantial performance hit will occur e.g. 1 ms per resizing
 * or initial allocation.
 * <p>
 * The EventPacket is prefilled with Events that have default values. One
 * constructor lets you fill the EventPacket with a subclass of BasicEvent. This
 * prefilling avoids the overhead of object creation. It also allows easy access
 * to all information contained in the event and it allows storing arbitrary
 * event information, including extended type information, in EventPackets.
 * <p>
 * However, this reuse of existing objects means that you need to take
 * particular precautions. The events that are stored in a packet are references
 * to objects. Therefore you can assign an event to a different packet but this
 * event will still be referenced in the original packet and can change.
 * <p>
 * Generally in event processing, you will iterate over an input packet to
 * produce an output packet. You iterate over an existing EventPacket that has
 * input data using the iterator(). This lets you access the events in the input
 * packet.
 * <p>
 * When you want to write these events to an existing output packet, then you
 * need to use the target event's copyFrom(Event e) method that copies all the
 * fields in the source packet event to the target packet event. This lets you
 * copy data such as timestamp, x,y location to a target event. You can then
 * fill in the target event's extended type information.
 * <p>
 * To obtain these output events, when you iterate over an input packet to write
 * to a target packet, you obtain the target event to write your results to by
 * using the target packet's output enumeration by using the outputIterator()
 * method. This iterator is obtained before iterating over the input packet.
 * This iterator has a method nextOutput() that returns the next output event to
 * write to. This nextOutput() method also expands the packet if they current
 * capacity needs to be enlarged. The iterator is initialized by the call to
 * outputIterator().
 * <p>
 * The amount of time iterating over events can also be limited by using the
 * time limiter. 
 * <p>
 * To filter events out of a packet, the BasicEvent's filteredOut flag can be
 * set. Then the event will simply be skipped in iterating over the packet.
 * However, the total number of events in the packet does not change by this
 * filtering operation. <b>
 * * Note that events that are "filteredOut" by a preceding filter (by it
 * calling the setFilteredOut method on the event) may not be filtered out by
 * the InputIterator of this packet if they are the last event in a packet.
 * Individual filters can still check the BasicEvent's isFilteredOut()
 * method.</b>
 *
 *
 *
 * @param <E> the class of the events in this packet
 * @author tobi
 *
 * @see BasicEvent
 * @see BasicEvent#isFilteredOut()
 */
public class EventPacket<E extends BasicEvent> implements /* EventPacketInterface<E>, */ Cloneable, Iterable<E> {

    static final Logger log = Logger.getLogger(EventPacket.class.getName());
    /**
     * The processing time limiter
     */
    public TimeLimiter timeLimitTimer = new TimeLimiter();
    /**
     * Default capacity in events for new EventPackets
     */
    public final int DEFAULT_INITIAL_CAPACITY = 4096;
    private int capacity;
    /**
     * the number of events eventList actually contains (0 to size-1)
     */
    public int size = 0;
    private Class<E> eventClass = null;
    /**
     * Constructs new events for this packet.
     */
    protected Constructor<E> eventConstructor = null;
    private E eventPrototype;
    /**
     * The backing array of element data of type E
     */
    public transient E[] elementData;
    private AEPacketRaw rawPacket = null;
    /**
     * This packet's input iterator.
     */
    public InItr inputIterator = null;
    /**
     * This packet's output iterator.
     */
    private OutItr outputIterator = null;
    final public Comparator<E> TIMESTAMP_COMPARATOR = new TimeStampComparator();

    /**
     * Count of events with filteredOut=true set. This count is accumulated
     * during the InItr iteration using hasNext()
     */
    protected int filteredOutCount = 0;

    /**
     * The modification system timestamp of the EventPacket in ns, from
     * System.nanoTime(). Some hardware interfaces set this field when the
     * packet is started to be filled with events from hardware. This timestamp
     * is not related to the event times of the events in the packet.
     */
    public long systemModificationTimeNs = 0;

    /**
     * Resets the time limiter for input iteration. After the timer times out
     * (time determined by timeLimitMs) input iterators will not return any more
     * events.
     */
    public void restartTimeLimiter() {
        timeLimitTimer.restart();
    }

    /**
     * restart the time limiter with limit timeLimitMs
     *
     * @param timeLimitMs time in ms
     */
    public void restartTimeLimiter(final int timeLimitMs) {
        timeLimitTimer.setTimeLimitMs(timeLimitMs);
        restartTimeLimiter();
    }

    /**
     * Constructs a new EventPacket filled with BasicEvent.
     *
     * @see net.sf.jaer.event.BasicEvent
     */
    public EventPacket() {
        this(BasicEvent.class);
    }

    /**
     * Constructs a new EventPacket filled with the given event class.
     *
     * @see net.sf.jaer.event.BasicEvent
     */
    public EventPacket(final Class<? extends BasicEvent> eventClass) {
        if (!BasicEvent.class.isAssignableFrom(eventClass)) { // Check if evenClass is a subclass of BasicEvent
            throw new Error("making EventPacket that holds " + eventClass + " but these are not assignable from BasicEvent");
        }
        setEventClass(eventClass);
    }

    /**
     * Fills this EventPacket with DEFAULT_INITIAL_CAPACITY of the event class
     */
    protected void initializeEvents() {
        // eventList=new ArrayList<E>(DEFAULT_INITIAL_CAPACITY);
        // elementData = (E[])new BasicEvent[DEFAULT_INITIAL_CAPACITY];
        elementData = (E[]) Array.newInstance(eventClass, DEFAULT_INITIAL_CAPACITY);
        fillWithDefaultEvents(0, DEFAULT_INITIAL_CAPACITY);
        size = 0;
        capacity = DEFAULT_INITIAL_CAPACITY;
    }

    /**
     * Populates the packet with default events from the eventConstructor.
     *
     * @param startIndex
     * @param endIndex
     * @see #eventConstructor
     */
    private void fillWithDefaultEvents(final int startIndex, final int endIndex) {
        try {
            for (int i = startIndex; i < endIndex; i++) {
                final E e = eventConstructor.newInstance();
                // eventList.add(e);
                elementData[i] = e;
                eventPrototype = e;
            }
        } catch (final Exception e) {
            EventPacket.log.warning("while filling packet with default events caught " + e);
            e.printStackTrace();
        }

    }

    /**
     * Returns duration of packet in microseconds.
     *
     * @return 0 if there are less than 2 events, otherwise last timestamp minus
     * first timestamp.
     */
    public int getDurationUs() {
        if (size < 2) {
            return 0;
        }

        return getLastTimestamp() - getFirstTimestamp();
    }

    public String getDescription() {
        return "";
    }

    /**
     * Sets the size to zero.
     */
    public void clear() {
        size = 0; // we don't clear list, because that nulls all the events
    }

    public void setSize(final int n) {
        size = n;
        // eventList.
        // this.numEvents=n;
    }

    /**
     * @return event rate for this packet in Hz measured stupidly by the size in
     * events divided by the packet duration. If packet duration is zero (there
     * are no events or zero time interval between the events), then rate
     * returned is zero. The rate is measured using
     * <code>getSizeNotFilteredOut</code>.
     * @see #getSizeNotFilteredOut()
     */
    public float getEventRateHz() {
        if (getDurationUs() == 0) {
            return 0;
        }
        return getSizeNotFilteredOut() / ((float) getDurationUs() * AEConstants.TICK_DEFAULT_US * 1e-6f);
    }

    /**
     * Returns first event, or null if there are no events.
     *
     * @return the event or null if there are no events.
     */
    public E getFirstEvent() {
        if (size == 0) {
            return null;
        }
        return elementData[0];
        // return eventList.get(0);
    }

    /**
     * Returns last event, or null if there are no events.
     *
     * @return the event or null if there are no events.
     */
    public E getLastEvent() {
        if (size == 0) {
            return null;
        }
        return elementData[size - 1];
        // return eventList.get(size-1);
    }

    /**
     * Returns first timestamp or 0 if there are no events.
     *
     * @return timestamp
     */
    public int getFirstTimestamp() {
        // if(events==null) return 0; else return events[0].timestamp;
        return elementData[0].timestamp;
        // return eventList.get(0).timestamp;
    }

    /**
     * @return last timestamp in packet. If packet is empty, returns zero -
     * which could be important if this time is used for e.g. filtering
     * operations!
     */
    public int getLastTimestamp() {
        // if(size==0) return 0;
        //// if(events==null) return 0; else return events[numEvents-1].timestamp;
        // return elementData[size-1].timestamp;
        //// return eventList.get(size-1).timestamp;
        final int s = size;
        if (s == 0) {
            log.warning("called getLastTimestamp on empty packet, returning 0");
            return 0;
        }
        return elementData[s - 1].timestamp;
    }

    /**
     * Returns the k'th event.
     *
     * @throws ArrayIndexOutOfBoundsException if out of bounds of packet.
     */
    final public E getEvent(final int k) {
        if (k >= size) {
            throw new ArrayIndexOutOfBoundsException();
        }
        return elementData[k];
        // return eventList.get(k);
    }

    /**
     * Constructs a new empty EventPacket containing <code>eventClass</code>.
     *
     * @see #setEventClass(java.lang.Class)
     * @see #setEventClass(java.lang.reflect.Constructor)
     */
    public EventPacket<E> constructNewPacket() {
        final EventPacket<E> packet = new EventPacket<>(getEventClass());
        return packet;
    }

    /**
     * Returns after initializing the iterator over input events of type <E>.
     *
     * @return an iterator that can iterate over the events.
     */
    public Iterator<E> inputIterator() {
        if (inputIterator == null || (inputIterator.getClass() != InItr.class)) {
            inputIterator = new InItr();
        } else {
            inputIterator.reset();
        }
        return inputIterator;
    }

    /**
     * Returns an output-type iterator that iterates over the output events.
     * This iterator is reset by this call to start at the beginning of the
     * output packet.
     *
     * @return the iterator. Use it to obtain new output events which can be
     * then copied from other events or modified.
     */
    final public OutputEventIterator<E> outputIterator() {
        if (outputIterator == null) {
            outputIterator = new OutItr();
        } else {
            outputIterator.reset();
        }
        return outputIterator;
    }

    /**
     * Returns the an output-type iterator of type E that iterates over the
     * packet, The iterator is constructed if necessary. The iterator is not
     * reset by this call.
     *
     * @return the iterator
     */
    public OutputEventIterator<E> getOutputIterator() {
        if (outputIterator == null) {
            outputIterator = new OutItr();
        }
        return outputIterator;
    }

    /**
     * Returns the raw data packet that this originally came from. These are the
     * raw ints that represent the data from the device. This AEPacketRaw may or
     * may not be set by the <code>EventExtractor2D</code>.
     *
     * This packet may or may not actually refer to the same data as when the
     * packet was extracted. This raw data packet may in the meantime have been
     * reused for other purposes.
     *
     * @return the raw packet
     * @see net.sf.jaer.event.EventPacketInterface<T>.
     */
    public AEPacketRaw getRawPacket() {
        return rawPacket;
    }

    /**
     * Sets the raw data packet associated with this.
     *
     * @param rawPacket the rawPacket to set.
     * @see net.sf.jaer.chip.EventExtractorInterface
     * @see net.sf.jaer.chip.EventExtractor2D
     */
    public void setRawPacket(final AEPacketRaw rawPacket) {
        this.rawPacket = rawPacket;
    }

    /**
     * Returns the count of events with filteredOut=true in the packet. This
     * field is accumulated during the InItr hasNext() iteration over the
     * packet. Therefore it is only valid after the iteration over the packet is
     * finished.
     * <p>
     * This count does <b>not</b> include the events set to be filteredOut
     * during the previous iteration, only the ones that were already filtered
     * out and were skipped in the previous iteration.
     *
     * @return the filteredOutCount
     */
    public int getFilteredOutCount() {
        return filteredOutCount;
    }

    /**
     * This iterator is intended for writing events to an output packet. The {@link #nextOutput()
     * } method returns the next element in the packet, enlarging the packet if
     * necessary. The fields in the returned element are copied from an input
     * event or generated in some other manner.
     */
    final public class OutItr implements OutputEventIterator<E> {

        OutItr() {
            size = 0; // reset size because we are starting off output packet
        }

        /**
         * Obtains the next output event suitable for either generating a new
         * event from scratch or copying from input event. Increments the size
         * of the packet, enlarging it if necessary. Sets the event's
         * <code>filteredOut</code> field to false, to ensure the event is not
         * filtered out as an old event from the packet.
         *
         * @return reference to next output event, which must be copied from a
         * different event.
         * @see BasicEvent#copyFrom(net.sf.jaer.event.BasicEvent)
         */
        @Override
        final public E nextOutput() {
            if (size >= capacity) {
                enlargeCapacity();
                // System.out.println("enlarged "+EventPacket.this);
            }
            elementData[size].setFilteredOut(false);
            return elementData[size++];
        }

        /**
         * Sets the packet size to zero, without changing capacity.
         */
        final public void reset() {
            size = 0;
        }

        @Override
        public String toString() {
            return "OutputEventIterator with size/cursor=" + size + " and capacity=" + capacity;
        }

        /**
         * Writes event to next output, and sets the filteredOut field to false.
         *
         * @param event the event to write out.
         */
        @Override
        public void writeToNextOutput(final E event) {
            {
                if (size >= capacity) {
                    enlargeCapacity();
                }
                // System.out.println("at position "+size+" wrote event "+event);
                event.setFilteredOut(false);
                elementData[size++] = event;
            }
        }
    }

    /**
     * An iterator of type <E> over the input events.
     */
    public class InItr implements Iterator<E> {

        protected int cursor;
        protected boolean usingTimeout;

        /**
         * Constructs a new instance of the InItr.
         */
        protected InItr() {
            reset();
        }

        /**
         * Returns boolean if the packet has more input events. This method
         * checks for the existence of an event that is not
         * <code>filteredOut</code> in the remaining part of the packet.
         *
         * @return true if there are more events.
         * @see BasicEvent#filteredOut
         */
        @Override
        public boolean hasNext() {
            if (usingTimeout && timeLimitTimer.isTimedOut()) {
                return false;
            }

            while ((cursor < size) && (elementData[cursor] != null) && elementData[cursor].isFilteredOut()) {
                filteredOutCount++;
                cursor++;
            } // TODO can get null events here which causes null pointer exception; not clear how this is possible

            return cursor < size;
        }

        /**
         * Obtains the next input event.
         *
         * @return the next event
         */
        @Override
        public E next() {
            return elementData[cursor++];
        }

        /**
         * Sets the size to zero.
         */
        public void reset() {
            cursor = 0;
            usingTimeout = timeLimitTimer.isEnabled(); // timelimiter only used if timeLimitTimer is enabled
            // but flag to
            // check it it only set on packet reset
            filteredOutCount = 0;
        }

        /**
         * Implements the optional remove operation to remove the last event
         * returned by next().
         */
        @Override
        public void remove() {
            for (int ctr = cursor; ctr < size; ctr++) {
                elementData[cursor - 1] = elementData[cursor];
            }
            // go back as we removed a packet
            cursor--;
            size--;
            // throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return "InputEventIterator cursor=" + cursor + " for packet with size=" + size + " and capacity=" + capacity;
        }

        /**
         * Returns current cursor position of this iterator
         *
         * @return the cursor
         */
        public int getCursor() {
            return cursor;
        }

    }

    /*
	 * Comparator for ordering events by their timestamp
	 * Order is oldest to newest.
	 * Note: this comparator imposes orderings that are inconsistent with equals.
     */
    final private class TimeStampComparator implements Comparator<E> {

        @Override
        public int compare(final E e1, final E e2) {
            return e1.timestamp - e2.timestamp;
        }
    }

    /*
	 * Method for ordering events by ascending timestamp, i.e. oldest to newest
     */
    public void sortByTimeStamp() {
        if (size == 0) {
            return;
        }
        Arrays.sort(elementData, 0, size, TIMESTAMP_COMPARATOR);
    }

    /**
     * Enlarges capacity by some factor, then copies all event references to the
     * new packet
     */
    private void enlargeCapacity() {
        try {
            EventPacket.log.info("enlarging capacity of " + this);
            final int ncapacity = capacity * 2; // (capacity*3)/2+1;
            Object oldData[] = elementData;
            elementData = (E[]) Array.newInstance(eventClass, ncapacity);
            System.arraycopy(oldData, 0, elementData, 0, size);
            oldData = null;
            // capacity still is old capacity and we have already filled it to there with new events, now fill
            // in up to new capacity with new events
            fillWithDefaultEvents(capacity, ncapacity);
            capacity = ncapacity;
        } catch (final OutOfMemoryError e) {
            EventPacket.log.log(Level.WARNING, "{0}: could not enlarge packet capacity from {1}", new Object[]{e.toString(), capacity});
            throw new ArrayIndexOutOfBoundsException(e.toString() + ":could not enlarge capacity from " + capacity);
        }
    }

    /**
     * Ensures packet has room for n events. The original events are retained
     * and the new capacity is filled with default events.
     *
     * @param n capacity
     * @see #fillWithDefaultEvents(int, int)
     */
    public void allocate(final int n) {
        if (n <= capacity) {
            return;
        }
        EventPacket.log.info("enlarging capacity of " + this + " to " + n + " events");
        final int ncapacity = n; // (capacity*3)/2+1;
        Object oldData[] = elementData;
        elementData = (E[]) Array.newInstance(eventClass, ncapacity);
        System.arraycopy(oldData, 0, elementData, 0, size);
        oldData = null;
        // capacity still is old capacity and we have already filled it to there with new events, now fill
        // in up to new capacity with new events
        fillWithDefaultEvents(size, ncapacity);
        capacity = ncapacity;
    }

    /**
     * Adds the events from another packet to the events of this packet
     *
     * @param packet EventPacket to be added
     */
    public void add(final EventPacket<E> packet) {
        if (packet.getEventClass() != getEventClass()) {
            EventPacket.log.warning("Trying to merge packets that contain different events types");
        }
        final E[] newData = packet.getElementData();
        final Object oldData[] = elementData;
        allocate(size + packet.size);
        System.arraycopy(oldData, 0, elementData, 0, size);
        System.arraycopy(newData, 0, elementData, size, packet.size);
        size = size + packet.size;
    }

    // public static void main(String[] args){
    // EventPacket p=new EventPacket();
    // p.test();
    // }
    //
    /**
     * 0.32913625s for 300 n allocations, 1097.1208 us/packet 0.3350817s for 300
     * n allocations, 1116.939 us/packet 0.3231394s for 300 n allocations,
     * 1077.1313 us/packet 0.32404426s for 300 n allocations, 1080.1475
     * us/packet 0.3472975s for 300 n allocations, 1157.6583 us/packet
     * 0.33720487s for 300 n allocations, 1124.0162 us/packet
     */
    // void test(){
    // int nreps=5;
    // int size=30000;
    // long stime, etime;
    // EventPacket<BasicEvent> p,pout;
    // OutputEventIterator outItr;
    // Iterator<BasicEvent> inItr;
    //
    // System.out.println("make new packets");
    // for(int k=0;k<nreps;k++){
    // stime=System.nanoTime();
    // for(int i=0;i<nreps;i++){
    // p=new EventPacket();
    // }
    // etime=System.nanoTime();
    //
    // float timeSec=(etime-stime)/1e9f;
    //
    // System.out.println(timeSec+ "s"+" for "+nreps+" n allocations, "+1e6f*timeSec/nreps+" us/packet ");
    // System.out.flush();
    // try{
    // Thread.currentThread().sleep(10);
    // }catch(Exception e){}
    // }
    //
    // System.out.println("make a new packet and fill with events");
    // p=new EventPacket<BasicEvent>();
    // for(int k=0;k<nreps;k++){
    // stime=System.nanoTime();
    // outItr=p.outputIterator();
    // for(int i=0;i<size;i++){
    // BasicEvent e=outItr.nextOutput();
    // e.timestamp=i;
    // e.x=((short)i);
    // e.y=(e.x);
    // }
    // etime=System.nanoTime();
    //
    // float timeSec=(etime-stime)/1e9f;
    //
    // System.out.println(timeSec+ "s"+" for "+size+" fill, "+1e6f*timeSec/size+" us/event ");
    // System.out.flush();
    // try{
    // Thread.currentThread().sleep(10);
    // }catch(Exception e){}
    // }
    //
    //
    // System.out.println("iterate over packet, changing all values");
    //// p=new EventPacket();
    // pout=new EventPacket<BasicEvent>();
    //
    // for(int k=0;k<nreps;k++){
    // stime=System.nanoTime();
    // inItr=p.inputIterator();
    // outItr=pout.outputIterator();
    // for(BasicEvent ein:p){
    //
    //// while(inItr.hasNext()){
    //// BasicEvent ein=inItr.next();
    // BasicEvent eout=outItr.nextOutput();
    // eout.copyFrom(ein);
    // }
    // etime=System.nanoTime();
    //
    // float timeSec=(etime-stime)/1e9f;
    //
    // System.out.println(timeSec+ "s"+" for iteration over packet with size="+p.getSize()+", "+timeSec/p.getSize()+" s
    // per event");
    // System.out.flush();
    // try{
    // Thread.currentThread().sleep(10);
    // }catch(Exception e){}
    // }
    //
    // System.out.println("\nmake packet with OrientationEvent and assign polarity and orientation");
    // pout=new EventPacket(OrientationEvent.class);
    // OrientationEvent ori=null;
    // for(int k=0;k<nreps;k++){
    // stime=System.nanoTime();
    // outItr=pout.outputIterator();
    // for(int i=0;i<size;i++){
    // ori=(OrientationEvent)outItr.nextOutput();
    // ((PolarityEvent)ori).type=10;
    // ori.timestamp=i;
    // ori.orientation=(byte)(100);
    //// ori.polarity=(byte)(20);
    // }
    // etime=System.nanoTime();
    // float timeSec=(etime-stime)/1e9f;
    //
    // System.out.println(timeSec+ "s"+" for iteration over packet with size="+p.getSize()+", "+timeSec/p.getSize()+" s
    // per event");
    // System.out.flush();
    // try{
    // Thread.currentThread().sleep(10);
    // }catch(Exception e){}
    // }
    // System.out.println("ori event ="+pout.getEvent(0)+" with type="+ori.getType());
    // System.out.println(pout.toString());
    //
    // }
    //
    /**
     * Returns the number of events in the packet. If the packet has extra data
     * not consisting of events this method could return 0 but there could still
     * be data, e.g. sampled ADC data, image frames, etc.
     *
     *
     * @return size in events.
     */
    final public int getSize() {
        return size;
    }

    /**
     * Returns the size of the packet not counting the filteredOut events.
     *
     * @return size
     * @see #getFilteredOutCount()
     */
    public int getSizeNotFilteredOut() {
        return getSize() - getFilteredOutCount();
    }

    /**
     * Reports if the packet is empty. The default implementation reports true
     * if size in events is zero, but subclasses can override this method to
     * report true if associated data exists.
     *
     * @return true if empty.
     */
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public String toString() {
        final int sz = getSize();
        final String s = "EventPacket #" + hashCode() + " holding " + getEventClass().getSimpleName() + " with size=" + sz + " capacity="
                + capacity + " inputIterator=" + inputIterator + " outputIterator=" + outputIterator + " filteredOutCount=" + filteredOutCount;
        return s;
    }

    /**
     * Returns the number of 'types' of events.
     *
     * @return the number of types, typically a small number like 1,2, or 4.
     */
    final public int getNumCellTypes() {
        return eventPrototype.getNumCellTypes();
    }

    /**
     * Returns a prototype of the events in the packet.
     *
     * @return a single instance of the event.
     */
    final public E getEventPrototype() {
        return eventPrototype;
    }

    /**
     * Sets the prototype event of the packet. E the event prototype which is an
     * instance of an event.
     *
     */
    final public void setEventPrototype(final E e) {
        eventPrototype = e;
    }

    /**
     * Initializes and returns an iterator over elements of type <E>
     *
     * @return an Iterator.
     */
    @Override
    public Iterator<E> iterator() {
        return inputIterator();
    }

    /**
     * Returns the class of event in this packet.
     *
     * @return the event class.
     */
    final public Class<E> getEventClass() {
        return eventClass;
    }

    /**
     * Sets the constructor for new (empty) events and initializes the packet.
     *
     * @param constructor - a zero argument constructor for the new events.
     */
    public final void setEventClass(final Constructor<? extends BasicEvent> constructor) {
        this.eventConstructor = (Constructor<E>) constructor;
        this.eventClass = eventConstructor.getDeclaringClass();
        initializeEvents();
    }

    /**
     * Sets the event class for this packet and fills the packet with these
     * events.
     *
     * @param eventClass which much extend BasicEvent
     */
    public final void setEventClass(final Class<? extends BasicEvent> eventClass) {
        this.eventClass = (Class<E>) eventClass;
        try {
            eventConstructor = (Constructor<E>) eventClass.getConstructor();
        } catch (final NoSuchMethodException e) {
            EventPacket.log.warning("cannot get constructor for constructing Events for building EventPacket: exception=" + e.toString()
                    + ", cause=" + e.getCause());
            e.printStackTrace();
        }
        initializeEvents();
    }

    /**
     * Gets the time limit for iteration in ms
     */
    final public int getTimeLimitMs() {
        return timeLimitTimer.getTimeLimitMs();
    }

    /**
     * Sets the time limit for filtering a packet through the filter chain
     * in ms.
     *
     * @param timeLimitMs the time limit in ms
     * @see #restartTimeLimiter
     */
    final public void setTimeLimitMs(final int timeLimitMs) {
        timeLimitTimer.setTimeLimitMs(timeLimitMs);
    }

    /** Sets the time limit enabled or not
     * 
     * @param yes to enable time limit for iteration
     */
    final public void setTimeLimitEnabled(final boolean yes) {
        timeLimitTimer.setEnabled(yes);
    }

    /**
     * Returns status of time limiting
     *
     * @return true if timelimiting is enabled
     */
    final public boolean isTimeLimitEnabled() {
        return timeLimitTimer.isEnabled();
    }

    /**
     * Returns true if timeLimitTimer is timed out and timeLimitEnabled
     */
    final public boolean isTimedOut() {
        return  timeLimitTimer.isTimedOut();
    }

    /**
     * Returns the element data.
     *
     * @return the underlying element data
     */
    public E[] getElementData() {
        return elementData;
    }

    /**
     * Sets the internal data of the packet. TODO needs more details about
     * elements
     *
     * @param elementData the underlying element data, which should extend
     * BasicEvent
     */
    public void setElementData(final E[] elementData) {
        this.elementData = elementData;
    }

}
