/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventio;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.ArrayList;
import java.util.logging.Logger;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.aemonitor.EventRaw;

/**
 * An AE input stream that uses a generic InputStream such as a
 * URL connection to an http server or memory input stream.
 * 
 * @author tobi
 */
public class AEInputStream implements Closeable {

    // TODO needs to be combined with AEFileInputStream so that AEFileInputStream extends AEInputStream

    /**
     * Property change event. Listeners should check for events using these static String objects.
     * <p>
     * In order to use these events, an EventFilter must register itself either with the AEInputStream or with the owning AEViewer. 
     * But this registration
     * is only possible after AEViewer is constructed, which is after the
     * EventFilter is constructed. The registration can occur in the EventFilter
     * filterPacket() method as in the code snippet below:
     * <pre><code>
     *    private boolean addedViewerPropertyChangeListener = false;
     *
     * synchronized public EventPacket filterPacket(EventPacket in) { // TODO completely rework this code because IMUSamples are part of the packet now!
     *  if (!addedViewerPropertyChangeListener) {
     *      if (chip.getAeViewer() != null) {
            * chip.getAeViewer().addPropertyChangeListener(this); // AEViewer refires these events for convenience
            * addedViewerPropertyChangeListener = true;
            * }
        * }
        * }
     * </code></pre>
     * <p>
     * <ul>
     * <li>EVENT_EOF end of input file or stream
     * <li>EVENT_WRAPPED_TIME means timestamp has wrapped around 32-bit value
     * <li>EVENT_POSITION means input stream position has changed (called just before returning each new packet) 
     * <li>EVENT_REWOUND means files has just rewound to start
    *  <li>EVENT_MARKSET means the mark has just been set on the stream for
     * later possible rewind 
     * <li>EVENT_MARKS_CLEARED means the marks were cleared
     * <li>EVENT_INIT is called after initialization of the input stream, with new value this
     * <li>EVENT_NON_MONOTONIC_TIMESTAMP a non-monotonic timestamp was detected
     * <li>EVENT_REPOSITIONED the input stream has been repositioned (e.g. by using slider in AEPlayer) to a new position
     * </ul>
     */
     public static final String EVENT_EOF="eof", 

    /**
     * Property change event.Listeners should check for events using these static String objects.
 <p>
 In order to use these events, an EventFilter must register itself either with the AEInputStream or with the owning AEViewer. 
 But this registration
 is only possible after AEViewer is constructed, which is after the
 EventFilter is constructed. The registration can occur in the EventFilter
 filterPacket() method as in the code snippet below:
 <pre><code>
    private boolean addedViewerPropertyChangeListener = false;

 synchronized public EventPacket filterPacket(EventPacket in) { // TODO completely rework this code because IMUSamples are part of the packet now!
  if (!addedViewerPropertyChangeListener) {
      if (chip.getAeViewer() != null) {
 chip.getAeViewer().addPropertyChangeListener(this); // AEViewer refires these events for convenience
 addedViewerPropertyChangeListener = true;
 }
 }
 }
 </code></pre>
 <p>
 <ul>
 <li>EVENT_EOF end of input file or stream
 <li>EVENT_WRAPPED_TIME means timestamp has wrapped around 32-bit value
 <li>EVENT_POSITION means input stream position has changed (called just before returning each new packet) 
 <li>EVENT_REWOUND means files has just rewound to start
 <li>EVENT_MARKSET means the mark has just been set on the stream for
 later possible rewind 
 <li>EVENT_MARKS_CLEARED means the marks were cleared
 <li>EVENT_INIT is called after initialization of the input stream, with new value this
 <li>EVENT_NON_MONOTONIC_TIMESTAMP a non-monotonic timestamp was detected
 <li>EVENT_REPOSITIONED the input stream has been repositioned (e.g. by using slider in AEPlayer) to a new position
 </ul>
     */
    EVENT_WRAPPED_TIME="wrappedTime", 

    /**
     * Property change event.Listeners should check for events using these static String objects.
 <p>
 In order to use these events, an EventFilter must register itself either with the AEInputStream or with the owning AEViewer. 
 But this registration
 is only possible after AEViewer is constructed, which is after the
 EventFilter is constructed. The registration can occur in the EventFilter
 filterPacket() method as in the code snippet below:
 <pre><code>
    private boolean addedViewerPropertyChangeListener = false;

 synchronized public EventPacket filterPacket(EventPacket in) { // TODO completely rework this code because IMUSamples are part of the packet now!
  if (!addedViewerPropertyChangeListener) {
      if (chip.getAeViewer() != null) {
 chip.getAeViewer().addPropertyChangeListener(this); // AEViewer refires these events for convenience
 addedViewerPropertyChangeListener = true;
 }
 }
 }
 </code></pre>
 <p>
 <ul>
 <li>EVENT_EOF end of input file or stream
 <li>EVENT_WRAPPED_TIME means timestamp has wrapped around 32-bit value
 <li>EVENT_POSITION means input stream position has changed (called just before returning each new packet) 
 <li>EVENT_REWOUND means files has just rewound to start
 <li>EVENT_MARKSET means the mark has just been set on the stream for
 later possible rewind 
 <li>EVENT_MARKS_CLEARED means the marks were cleared
 <li>EVENT_INIT is called after initialization of the input stream, with new value this
 <li>EVENT_NON_MONOTONIC_TIMESTAMP a non-monotonic timestamp was detected
 <li>EVENT_REPOSITIONED the input stream has been repositioned (e.g. by using slider in AEPlayer) to a new position
 </ul>
     */
    EVENT_POSITION="position", 

    /**
     * Property change event.Listeners should check for events using these static String objects.
 <p>
 In order to use these events, an EventFilter must register itself either with the AEInputStream or with the owning AEViewer. 
 But this registration
 is only possible after AEViewer is constructed, which is after the
 EventFilter is constructed. The registration can occur in the EventFilter
 filterPacket() method as in the code snippet below:
 <pre><code>
    private boolean addedViewerPropertyChangeListener = false;

 synchronized public EventPacket filterPacket(EventPacket in) { // TODO completely rework this code because IMUSamples are part of the packet now!
  if (!addedViewerPropertyChangeListener) {
      if (chip.getAeViewer() != null) {
 chip.getAeViewer().addPropertyChangeListener(this); // AEViewer refires these events for convenience
 addedViewerPropertyChangeListener = true;
 }
 }
 }
 </code></pre>
 <p>
 <ul>
 <li>EVENT_EOF end of input file or stream
 <li>EVENT_WRAPPED_TIME means timestamp has wrapped around 32-bit value
 <li>EVENT_POSITION means input stream position has changed (called just before returning each new packet) 
 <li>EVENT_REWOUND means files has just rewound to start
 <li>EVENT_MARKSET means the mark has just been set on the stream for
 later possible rewind 
 <li>EVENT_MARKS_CLEARED means the marks were cleared
 <li>EVENT_INIT is called after initialization of the input stream, with new value this
 <li>EVENT_NON_MONOTONIC_TIMESTAMP a non-monotonic timestamp was detected
 <li>EVENT_REPOSITIONED the input stream has been repositioned (e.g. by using slider in AEPlayer) to a new position
 </ul>
     */
    EVENT_REWOUND="rewound", // replaces EVENT_REWIND to be called after the rewind is finished, to avoid non-monotonic timestamp

    /**
     * Property change event.Listeners should check for events using these static String objects.
 <p>
 In order to use these events, an EventFilter must register itself either with the AEInputStream or with the owning AEViewer. 
 But this registration
 is only possible after AEViewer is constructed, which is after the
 EventFilter is constructed. The registration can occur in the EventFilter
 filterPacket() method as in the code snippet below:
 <pre><code>
    private boolean addedViewerPropertyChangeListener = false;

 synchronized public EventPacket filterPacket(EventPacket in) { // TODO completely rework this code because IMUSamples are part of the packet now!
  if (!addedViewerPropertyChangeListener) {
      if (chip.getAeViewer() != null) {
 chip.getAeViewer().addPropertyChangeListener(this); // AEViewer refires these events for convenience
 addedViewerPropertyChangeListener = true;
 }
 }
 }
 </code></pre>
 <p>
 <ul>
 <li>EVENT_EOF end of input file or stream
 <li>EVENT_WRAPPED_TIME means timestamp has wrapped around 32-bit value
 <li>EVENT_POSITION means input stream position has changed (called just before returning each new packet) 
 <li>EVENT_REWOUND means files has just rewound to start
 <li>EVENT_MARKSET means the mark has just been set on the stream for
 later possible rewind 
 <li>EVENT_MARKS_CLEARED means the marks were cleared
 <li>EVENT_INIT is called after initialization of the input stream, with new value this
 <li>EVENT_NON_MONOTONIC_TIMESTAMP a non-monotonic timestamp was detected
 <li>EVENT_REPOSITIONED the input stream has been repositioned (e.g. by using slider in AEPlayer) to a new position
 </ul>
     */
    EVENT_MARKS_CLEARED="marksCleared",

    /**
     * Property change event.Listeners should check for events using these static String objects.
 <p>
 In order to use these events, an EventFilter must register itself either with the AEInputStream or with the owning AEViewer. 
 But this registration
 is only possible after AEViewer is constructed, which is after the
 EventFilter is constructed. The registration can occur in the EventFilter
 filterPacket() method as in the code snippet below:
 <pre><code>
    private boolean addedViewerPropertyChangeListener = false;

 synchronized public EventPacket filterPacket(EventPacket in) { // TODO completely rework this code because IMUSamples are part of the packet now!
  if (!addedViewerPropertyChangeListener) {
      if (chip.getAeViewer() != null) {
 chip.getAeViewer().addPropertyChangeListener(this); // AEViewer refires these events for convenience
 addedViewerPropertyChangeListener = true;
 }
 }
 }
 </code></pre>
 <p>
 <ul>
 <li>EVENT_EOF end of input file or stream
 <li>EVENT_WRAPPED_TIME means timestamp has wrapped around 32-bit value
 <li>EVENT_POSITION means input stream position has changed (called just before returning each new packet) 
 <li>EVENT_REWOUND means files has just rewound to start
 <li>EVENT_MARKSET means the mark has just been set on the stream for
 later possible rewind 
 <li>EVENT_MARKS_CLEARED means the marks were cleared
 <li>EVENT_INIT is called after initialization of the input stream, with new value this
 <li>EVENT_NON_MONOTONIC_TIMESTAMP a non-monotonic timestamp was detected
 <li>EVENT_REPOSITIONED the input stream has been repositioned (e.g. by using slider in AEPlayer) to a new position
 </ul>
     */
    EVENT_MARK_IN_SET="markInSet",

    /**
     * Property change event.Listeners should check for events using these static String objects.
 <p>
 In order to use these events, an EventFilter must register itself either with the AEInputStream or with the owning AEViewer. 
 But this registration
 is only possible after AEViewer is constructed, which is after the
 EventFilter is constructed. The registration can occur in the EventFilter
 filterPacket() method as in the code snippet below:
 <pre><code>
    private boolean addedViewerPropertyChangeListener = false;

 synchronized public EventPacket filterPacket(EventPacket in) { // TODO completely rework this code because IMUSamples are part of the packet now!
  if (!addedViewerPropertyChangeListener) {
      if (chip.getAeViewer() != null) {
 chip.getAeViewer().addPropertyChangeListener(this); // AEViewer refires these events for convenience
 addedViewerPropertyChangeListener = true;
 }
 }
 }
 </code></pre>
 <p>
 <ul>
 <li>EVENT_EOF end of input file or stream
 <li>EVENT_WRAPPED_TIME means timestamp has wrapped around 32-bit value
 <li>EVENT_POSITION means input stream position has changed (called just before returning each new packet) 
 <li>EVENT_REWOUND means files has just rewound to start
 <li>EVENT_MARKSET means the mark has just been set on the stream for
 later possible rewind 
 <li>EVENT_MARKS_CLEARED means the marks were cleared
 <li>EVENT_INIT is called after initialization of the input stream, with new value this
 <li>EVENT_NON_MONOTONIC_TIMESTAMP a non-monotonic timestamp was detected
 <li>EVENT_REPOSITIONED the input stream has been repositioned (e.g. by using slider in AEPlayer) to a new position
 </ul>
     */
    EVENT_MARK_OUT_SET="markOutSet", 

    /**
     * Property change event.Listeners should check for events using these static String objects.
 <p>
 In order to use these events, an EventFilter must register itself either with the AEInputStream or with the owning AEViewer. 
 But this registration
 is only possible after AEViewer is constructed, which is after the
 EventFilter is constructed. The registration can occur in the EventFilter
 filterPacket() method as in the code snippet below:
 <pre><code>
    private boolean addedViewerPropertyChangeListener = false;

 synchronized public EventPacket filterPacket(EventPacket in) { // TODO completely rework this code because IMUSamples are part of the packet now!
  if (!addedViewerPropertyChangeListener) {
      if (chip.getAeViewer() != null) {
 chip.getAeViewer().addPropertyChangeListener(this); // AEViewer refires these events for convenience
 addedViewerPropertyChangeListener = true;
 }
 }
 }
 </code></pre>
 <p>
 <ul>
 <li>EVENT_EOF end of input file or stream
 <li>EVENT_WRAPPED_TIME means timestamp has wrapped around 32-bit value
 <li>EVENT_POSITION means input stream position has changed (called just before returning each new packet) 
 <li>EVENT_REWOUND means files has just rewound to start
 <li>EVENT_MARKSET means the mark has just been set on the stream for
 later possible rewind 
 <li>EVENT_MARKS_CLEARED means the marks were cleared
 <li>EVENT_INIT is called after initialization of the input stream, with new value this
 <li>EVENT_NON_MONOTONIC_TIMESTAMP a non-monotonic timestamp was detected
 <li>EVENT_REPOSITIONED the input stream has been repositioned (e.g. by using slider in AEPlayer) to a new position
 </ul>
     */
    EVENT_INIT="init", 

    /**
     * Property change event.Listeners should check for events using these static String objects.
 <p>
 In order to use these events, an EventFilter must register itself either with the AEInputStream or with the owning AEViewer. 
 But this registration
 is only possible after AEViewer is constructed, which is after the
 EventFilter is constructed. The registration can occur in the EventFilter
 filterPacket() method as in the code snippet below:
 <pre><code>
    private boolean addedViewerPropertyChangeListener = false;

 synchronized public EventPacket filterPacket(EventPacket in) { // TODO completely rework this code because IMUSamples are part of the packet now!
  if (!addedViewerPropertyChangeListener) {
      if (chip.getAeViewer() != null) {
 chip.getAeViewer().addPropertyChangeListener(this); // AEViewer refires these events for convenience
 addedViewerPropertyChangeListener = true;
 }
 }
 }
 </code></pre>
 <p>
 <ul>
 <li>EVENT_EOF end of input file or stream
 <li>EVENT_WRAPPED_TIME means timestamp has wrapped around 32-bit value
 <li>EVENT_POSITION means input stream position has changed (called just before returning each new packet) 
 <li>EVENT_REWOUND means files has just rewound to start
 <li>EVENT_MARKSET means the mark has just been set on the stream for
 later possible rewind 
 <li>EVENT_MARKS_CLEARED means the marks were cleared
 <li>EVENT_INIT is called after initialization of the input stream, with new value this
 <li>EVENT_NON_MONOTONIC_TIMESTAMP a non-monotonic timestamp was detected
 <li>EVENT_REPOSITIONED the input stream has been repositioned (e.g. by using slider in AEPlayer) to a new position
 </ul>
     */
    EVENT_NON_MONOTONIC_TIMESTAMP="nonMonotonicTimestamp", 

    /**
     * Property change event.Listeners should check for events using these static String objects.
 <p>
 In order to use these events, an EventFilter must register itself either with the AEInputStream or with the owning AEViewer. 
 But this registration
 is only possible after AEViewer is constructed, which is after the
 EventFilter is constructed. The registration can occur in the EventFilter
 filterPacket() method as in the code snippet below:
 <pre><code>
    private boolean addedViewerPropertyChangeListener = false;

 synchronized public EventPacket filterPacket(EventPacket in) { // TODO completely rework this code because IMUSamples are part of the packet now!
  if (!addedViewerPropertyChangeListener) {
      if (chip.getAeViewer() != null) {
 chip.getAeViewer().addPropertyChangeListener(this); // AEViewer refires these events for convenience
 addedViewerPropertyChangeListener = true;
 }
 }
 }
 </code></pre>
 <p>
 <ul>
 <li>EVENT_EOF end of input file or stream
 <li>EVENT_WRAPPED_TIME means timestamp has wrapped around 32-bit value
 <li>EVENT_POSITION means input stream position has changed (called just before returning each new packet) 
 <li>EVENT_REWOUND means files has just rewound to start
 <li>EVENT_MARKSET means the mark has just been set on the stream for
 later possible rewind 
 <li>EVENT_MARKS_CLEARED means the marks were cleared
 <li>EVENT_INIT is called after initialization of the input stream, with new value this
 <li>EVENT_NON_MONOTONIC_TIMESTAMP a non-monotonic timestamp was detected
 <li>EVENT_REPOSITIONED the input stream has been repositioned (e.g. by using slider in AEPlayer) to a new position
 </ul>
     */
    EVENT_REPOSITIONED="respositioned", 

    /**
     * Property change event.Listeners should check for events using these static String objects.
 <p>
 In order to use these events, an EventFilter must register itself either with the AEInputStream or with the owning AEViewer. 
 But this registration
 is only possible after AEViewer is constructed, which is after the
 EventFilter is constructed. The registration can occur in the EventFilter
 filterPacket() method as in the code snippet below:
 <pre><code>
    private boolean addedViewerPropertyChangeListener = false;

 synchronized public EventPacket filterPacket(EventPacket in) { // TODO completely rework this code because IMUSamples are part of the packet now!
  if (!addedViewerPropertyChangeListener) {
      if (chip.getAeViewer() != null) {
 chip.getAeViewer().addPropertyChangeListener(this); // AEViewer refires these events for convenience
 addedViewerPropertyChangeListener = true;
 }
 }
 }
 </code></pre>
 <p>
 <ul>
 <li>EVENT_EOF end of input file or stream
 <li>EVENT_WRAPPED_TIME means timestamp has wrapped around 32-bit value
 <li>EVENT_POSITION means input stream position has changed (called just before returning each new packet) 
 <li>EVENT_REWOUND means files has just rewound to start
 <li>EVENT_MARKSET means the mark has just been set on the stream for
 later possible rewind 
 <li>EVENT_MARKS_CLEARED means the marks were cleared
 <li>EVENT_INIT is called after initialization of the input stream, with new value this
 <li>EVENT_NON_MONOTONIC_TIMESTAMP a non-monotonic timestamp was detected
 <li>EVENT_REPOSITIONED the input stream has been repositioned (e.g. by using slider in AEPlayer) to a new position
 </ul>
     */
    EVENT_REPEAT_ON = "repeatOn", 

    /**
     * Property change event.Listeners should check for events using these static String objects.
 <p>
 In order to use these events, an EventFilter must register itself either with the AEInputStream or with the owning AEViewer. 
 But this registration
 is only possible after AEViewer is constructed, which is after the
 EventFilter is constructed. The registration can occur in the EventFilter
 filterPacket() method as in the code snippet below:
 <pre><code>
    private boolean addedViewerPropertyChangeListener = false;

 synchronized public EventPacket filterPacket(EventPacket in) { // TODO completely rework this code because IMUSamples are part of the packet now!
  if (!addedViewerPropertyChangeListener) {
      if (chip.getAeViewer() != null) {
 chip.getAeViewer().addPropertyChangeListener(this); // AEViewer refires these events for convenience
 addedViewerPropertyChangeListener = true;
 }
 }
 }
 </code></pre>
 <p>
 <ul>
 <li>EVENT_EOF end of input file or stream
 <li>EVENT_WRAPPED_TIME means timestamp has wrapped around 32-bit value
 <li>EVENT_POSITION means input stream position has changed (called just before returning each new packet) 
 <li>EVENT_REWOUND means files has just rewound to start
 <li>EVENT_MARKSET means the mark has just been set on the stream for
 later possible rewind 
 <li>EVENT_MARKS_CLEARED means the marks were cleared
 <li>EVENT_INIT is called after initialization of the input stream, with new value this
 <li>EVENT_NON_MONOTONIC_TIMESTAMP a non-monotonic timestamp was detected
 <li>EVENT_REPOSITIONED the input stream has been repositioned (e.g. by using slider in AEPlayer) to a new position
 </ul>
     */
    EVENT_REPEAT_OFF = "repeatOff";

    private InputStream is;
    AEPacketRaw packet = new AEPacketRaw();
    int lastPacketEndTimestamp = 0;
    static Logger log = Logger.getLogger("AEInputStream");
    protected ArrayList<String> header = new ArrayList<String>();
    private Class addressType = Short.TYPE; // default address type, unless file header specifies otherwise
    DataInputStream dis;
    boolean readFirstEventFlag = false;
        PushbackInputStream pbis;

        /** Constructs new instance of AEInputStream, and attempts skip its header, if any.
         *
         * @param is the input stream
         * @throws IOException on error skipping header.
         */
    public AEInputStream(InputStream is) throws IOException {
        this.is = is;
        dis = new DataInputStream(pbis=new PushbackInputStream(is)); // we need to be able to push back bytes after scanning header
        skipHeader();
    }
    
    EventRaw tmpEvent = new EventRaw();
    

    private EventRaw readEvent() throws IOException {
        int addr;
        int ts;
        if (getAddressType() == Integer.TYPE) {
            addr = dis.readInt();
        } else {
            addr = dis.readShort();
        }
        ts = dis.readInt();
        tmpEvent.address = addr;
        tmpEvent.timestamp = ts;
        return tmpEvent;
    }

   /** Reads all available events from the stream
    * @return available event packet
    */
    public AEPacketRaw readAvailablePacket() throws IOException{
        packet.setNumEvents(0);
        int n=dis.available();
        int es=getAddressType()==Integer.TYPE?8:4;
        int ne=n/es;
          for (int i = 0; i < n; i++) {
            packet.addEvent(readEvent());
        }
        return packet;
    }
    
    /** Reads a raw event packet of n events
    @param n the number of events to read
    @throws IOException if there is a problem, e.g. end of file
     */
    public AEPacketRaw readPacketByNumber(int n) throws IOException {
        packet.setNumEvents(0);
        for (int i = 0; i < n; i++) {
            packet.addEvent(readEvent());
        }
        return packet;
    }

    /**
     * returns an AEPacketRaw at least dt long up to the max size of the buffer or until end-of-file.
     * Events are read as long as the timestamp until (and including) the event whose timestamp is greater (for dt>0) than
     * startTimestamp+dt, where startTimestamp is the last timestamp from the previous call.
     *
     * @param dt the timestamp different in units of the timestamp (usually us)
    @return the packet, always at least one event even if there is no event in the interval dt.
    @throws IOException if there is any problem
     */
    public AEPacketRaw readPacketByTime(int dt) throws IOException {
        packet.setNumEvents(0);
        int ts;
        if(!readFirstEventFlag) lastPacketEndTimestamp=readEvent().timestamp;
        do{
            EventRaw e=readEvent();
            packet.addEvent(e);
            ts=e.timestamp;
        }while(ts-lastPacketEndTimestamp<dt);
        return packet;
    }

    /** skips the header lines (if any) */
    protected void skipHeader() throws IOException {
        readHeader();
    }

    /** reads the header comment lines. Assumes we are rewound to position(0).
     */
    protected void readHeader() throws IOException {
        String s;
        while ((s = readHeaderLine()) != null) {
            header.add(s);
            parseFileFormatVersion(s);
        }
        StringBuffer sb = new StringBuffer();
        sb.append("File header:");
        for (String str : getHeader()) {
            sb.append(str);
            sb.append("\n");
        }
        log.info(sb.toString());
    }

    /** parses the file format version 
    @see net.sf.jaer.eventio.AEDataFile
     */
    protected void parseFileFormatVersion(String s) {
        float version = 1f;
        if (s.startsWith(AEDataFile.DATA_FILE_FORMAT_HEADER)) { // # stripped off by readHeaderLine
            try {
                version = Float.parseFloat(s.substring(AEDataFile.DATA_FILE_FORMAT_HEADER.length()));
            } catch (NumberFormatException numberFormatException) {
                log.warning("While parsing header line " + s + " got " + numberFormatException.toString());
            }
            if (version < 2) {
                setAddressType(Short.TYPE);
            } else if (version >= 2) { // this is hack to avoid parsing the AEDataFile. format number string...
                setAddressType(Integer.TYPE);
            }
            log.info("Data file version=" + version + " and has addressType=" + getAddressType());
        }

    }

    /** assumes we are positioned at start of line and that we may either read a comment char '#' or something else
    leaves us after the line at start of next line or of raw data. Assumes header lines are written using the AEOutputStream.writeHeaderLine().
    @return header line
     */
    String readHeaderLine() throws IOException {
        StringBuffer s = new StringBuffer();
        int c = pbis.read();
        if (c != AEDataFile.COMMENT_CHAR) {
            pbis.unread(c);
            return null;
        }
        while (((char) (c = pbis.read())) != '\r') {
            if (c < 32 || c > 126) {
                throw new IOException("Non printable character (<32 || >126) detected in header line, aborting header read and resetting to start of file because this file may not have a real header");
            }
            s.append((char) c);
        }
        if ((c = pbis.read()) != '\n') {
            log.warning("header line \"" + s.toString() + "\" doesn't end with LF"); // get LF of CRLF
        }
        return s.toString();
    }

    /** Gets the header strings from the file
    @return list of strings, one per line
     */
    public ArrayList<String> getHeader() {
        return header;
    }

    public void close() throws IOException {
        if(is!=null) is.close();
    }

    /**
     * @return the addressType, either Integer.TYPE or Short.TYPE;
     */
    public Class getAddressType (){
        return addressType;
    }

    /**
     * @param addressType the addressType to set, either Integer.TYPE or Short.TYPE
     */
    public void setAddressType (Class addressType){
        if(addressType!=Integer.TYPE && addressType!=Short.TYPE ){
            log.warning("tried to set event type to be "+addressType+" but only Integer.TYPE and Short.TYPE addresses are allowed");
            return;
        }
        this.addressType = addressType;
    }
}
