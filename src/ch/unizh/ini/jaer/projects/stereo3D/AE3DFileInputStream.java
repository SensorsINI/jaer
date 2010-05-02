/*
 * AE3DFileInputStream.java
 *
 * Created on December 06, 2007
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.stereo3D;

import net.sf.jaer.eventio.*;
import net.sf.jaer.util.EngineeringFormat;
import java.beans.PropertyChangeSupport;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Class to stream in packets of events from binary input stream from a file recorded by 3D reconstructing filters.
 <p>
 File format is very simple: two adresses for left and right retina + timestamp
 <pre>
 int32 x<br>
 int32 y<br>
 int32 z<br>
 float value<br>
 int32 timestamp
 </pre>
 
 <p>
 An optional header consisting of lines starting with '#' is skipped when opening the file and may be retrieved.
 No later comment lines are allowed because the rest ot the file must be pure binary data.
 <p>
 AE3DFileInputStream has PropertyChangeSupport via getSupport(). PropertyChangeListeners will get informed of
 the following events
 <ul>
 <li> "position" - on any new packet of events, either by time chunk or fixed number of events chunk
 <li> "rewind" - on file rewind
 <li> "eof" - on end of file
 <li> "wrappedTime" - on wrap of time timestamps. This happens every int32 us, which is about 4295 seconds which is 71 minutes. Time is negative, then positive, then negative again.
 <li> "init" - on initial read of a file (after creating this with a file input stream). This init event is called on the
 initial packet read because listeners can't be added until the object is created
 </ul>
 
 * @author tobi
 */
public class AE3DFileInputStream extends DataInputStream { //implements AEInputStreamInterface {
//    public final static long MAX_FILE_SIZE=200000000;
    private PropertyChangeSupport support=new PropertyChangeSupport(this);
    static Logger log=Logger.getLogger("net.sf.jaer.eventio");
    FileInputStream in;
    long fileSize=0; // size of file in bytes
    InputStreamReader reader=null;
    private File file=null;
    
    public final int MAX_NONMONOTONIC_TIME_EXCEPTIONS_TO_PRINT=1000;
    private int numNonMonotonicTimeExceptionsPrinted=0;
    private int markPosition=0; // a single MARK position for rewinding to
    private int markInPosition=0, markOutPosition=0; // points to mark IN and OUT positions for editing
    private int numChunks=0; // the number of mappedbytebuffer chunks in the file
    private boolean firstReadCompleted=false;
    private long absoluteStartingTimeMs=0; // parsed from filename if possible
    
    //    private int numEvents,currentEventNumber;
    
    // mostRecentTimestamp is the last event sucessfully read
    // firstTimestamp, lastTimestamp are the first and last timestamps in the file (at least the memory mapped part of the file)
    private int mostRecentTimestamp, firstTimestamp, lastTimestamp;
    
    // this marks the present read time for packets
    private int currentStartTimestamp;
    
    FileChannel fileChannel=null;

    /** Maximum internal buffer sizes in events. */
    public static final int MAX_BUFFER_SIZE_EVENTS=300000;
    // to adapt if file format changes :
 //   public static final int EVENT_SIZE=Float.SIZE/8+Integer.SIZE/2;
    public static final int EVENT_SIZE=Float.SIZE/8+Integer.SIZE/8+Short.SIZE/8*5;
      //public static final int EVENT_SIZE=Short.SIZE/8+Integer.SIZE/8;
    
    // buffers
    /** the size of the memory mapped part of the input file.
     This window is centered over the file posiiton except at the start and end of the file.
     */
    public static final int CHUNK_SIZE_BYTES=EVENT_SIZE*1000000; //10000000
    
    // the packet used for reading events
    private AEPacket3D packet; //=new AEPacket3D(MAX_BUFFER_SIZE_EVENTS);
    
    Event3D tmpEvent=new Event3D();
    MappedByteBuffer byteBuffer=null;
//    private ByteBuffer eventByteBuffer=ByteBuffer.allocateDirect(EVENT_SIZE); // the ByteBuffer that a single event is written into from the fileChannel and read from to get the addr & timestamp
    private int chunkNumber=0;
    
    private int position=0; // absolute position in file in events, points to next event number, 0 based (1 means 2nd event)
    
    protected ArrayList<String> header=new ArrayList<String>();
    private int headerOffset=0; // this is starting position in file for rewind or to add to positioning via slider
    
    volatile private boolean pure3D = false;
    
    /** Creates a new instance of AEInputStream
     @deprecated use the constructor with a File object so that users of this can more easily get file information
     */
    public AE3DFileInputStream(FileInputStream in) {
        super(in);
        init(in);
    }
    
    /** Creates a new instance of AEInputStream
     @param f the file to open
     @throws FileNotFoundException if file doesn't exist or can't be read
     */
    public AE3DFileInputStream(File f) throws FileNotFoundException {
        this(new FileInputStream(f));
        setFile(f);
    }
    
    public String toString(){
        EngineeringFormat fmt=new EngineeringFormat();
        String s="AE3DFileInputStream with size="
                +fmt.format(size())
                +" events, firstTimestamp="
                +getFirstTimestamp()
                +" lastTimestamp="
                +getLastTimestamp()
                +" duration="
                +fmt.format(getDurationUs()/1e6f)+" s"
                +" event rate="
                +fmt.format(size()/(getDurationUs()/1e6f))+" eps";
        return s;
    }
    
    /** fires property change "position"
     */
    private void init(FileInputStream fileInputStream){
        this.in=fileInputStream;
        fileChannel=fileInputStream.getChannel();
        try{
            fileSize=fileChannel.size();
        }catch(IOException e){
            e.printStackTrace();
            fileSize=0;
        }
        mostRecentTimestamp=0; currentStartTimestamp=0; // make sure these are initialized correctly so that an event is always read when file is opened
        boolean openok=false;
        System.gc();
        try{
            mapChunk(0);
        }catch(IOException e){
            log.warning("couldn't map chunk 0 of file");
            e.printStackTrace();
        }
        
        reader=new InputStreamReader(fileInputStream);
        
        try{
            readHeader();
           
            
        }catch(IOException e){
            log.warning("couldn't read header");
           
        }
        
        
         // what if no header?
        int type = readType();
        if(type==Event3D.DIRECT3D) pure3D = true;
        else pure3D = false;
        packet=new AEPacket3D(MAX_BUFFER_SIZE_EVENTS,type);

       
        
//        long totalMemory=Runtime.getRuntime().totalMemory();
//        long maxMemory=Runtime.getRuntime().maxMemory();
//        long maxSize=3*(maxMemory-totalMemory)/4;
//        EngineeringFormat fmt=new EngineeringFormat();
//        log.info("AEInputStream.init(): trying to open file using memory mapped file of size="+fmt.format(fileSize)+" using memory-limited max size="+fmt.format(maxSize));
//        do{
//            try{
//                fileChannel.position(0);
//                long size=fileChannel.size();
//                if(size>maxSize){
//                    log.warning("opening file using byteBuffer with size="+maxSize+" but file size="+size);
//                    size=maxSize;
//                }
//                byteBuffer=fileChannel.map(FileChannel.MapMode.READ_ONLY,0,size);
//                openok=true;
//            }catch(IOException e){
//                System.gc();
//                long newMaxSize=3*maxSize/4;
//                log.warning("AEInputStream.init(): cannot open file "+fileInputStream+" with maxSize="+maxSize+" trying again with size="+newMaxSize);
//                maxSize=newMaxSize;
//            }
//        }while(openok==false && maxSize>20000000);
//        if(!openok){
//            throw new RuntimeException("cannot open preview, not enough memory");
//        }
        try{
            Event3D ev=readEventForwards(); // init timestamp
            firstTimestamp=ev.timestamp;
            position((int)(size()-1));
            ev=readEventForwards();
            lastTimestamp=ev.timestamp;
            position(0);
            currentStartTimestamp=firstTimestamp;
            mostRecentTimestamp=firstTimestamp;
        }catch(IOException e){
            System.err.println("couldn't read first event to set starting timestamp");
        }catch(NonMonotonicTimeException e2){
            log.warning("On AEInputStream.init() caught "+e2.toString());
        }
        log.info(this.toString());
    }
    
    
    private int readType(){
//        int type=byteBuffer.getShort();
//        headerOffset=byteBuffer.position();
        //from header
        int type = 0;
        String s;
        for(int i=0; i<header.size();i++){
            s = header.get(i);
            if(s.contains("type")){
                // extract, return
                int j = s.indexOf(':');
                if(j>0){
                    String stype = s.substring(j+1,j+2);
                    if(stype.contains("0")) return 0;
                    if(stype.contains("1")) return 1;
                }
            }
        }
        return type;        
    }
    
    /** reads the next event forward
     @throws EOFException at end of file
     */
    private Event3D readEventForwards() throws IOException, NonMonotonicTimeException{
        int ts=0;
        int addrx=0;
        int addry=0;
        int addrd=0;
        int method=0;
        int lead_side=0;
        float value=0;
        // if pure 3d
        int addrz=0;
        try{
//            eventByteBuffer.rewind();
//            fileChannel.read(eventByteBuffer);
//            eventByteBuffer.rewind();
//            addr=eventByteBuffer.getShort();
//            ts=eventByteBuffer.getInt();
            if(pure3D){
                addrx=(int)byteBuffer.getShort();
                addry=byteBuffer.getShort();
                addrz=byteBuffer.getShort();
                value=byteBuffer.getFloat();
                ts=byteBuffer.getInt();
//                System.out.println("----------------");
//                System.out.println(addrx);
//                System.out.println(addry);
//                System.out.println(addrz);
//                System.out.println(value);
//                System.out.println(ts);
            } else {
                addrx=(int)byteBuffer.getShort();
                addry=byteBuffer.getShort();
                addrd=byteBuffer.getShort();
                method=byteBuffer.getShort();
                lead_side=byteBuffer.getShort();
                
                value=byteBuffer.getFloat();
                ts=byteBuffer.getInt();
            }
            // check for non-monotonic increasing timestamps, if we get one, reset our notion of the starting time
            if(isWrappedTime(ts,mostRecentTimestamp,1)){
               // throw new WrappedTimeException(ts,mostRecentTimestamp,position);
                throw new EOFException("3D Wrapped Time");
            }
            if(ts<mostRecentTimestamp){
//                log.warning("AEInputStream.readEventForwards returned ts="+ts+" which goes backwards in time (mostRecentTimestamp="+mostRecentTimestamp+")");
               // throw new NonMonotonicTimeException(ts,mostRecentTimestamp,position);
            }
            if(pure3D){
                tmpEvent.x0=addrx;
                tmpEvent.y0=addry;
                tmpEvent.z0=addrz;
                tmpEvent.value=value;
                tmpEvent.timestamp=ts;
            } else {
                tmpEvent.x=addrx;
                tmpEvent.y=addry;
                tmpEvent.d=addrd;
                tmpEvent.method=method;
                tmpEvent.lead_side=lead_side;
                tmpEvent.value=value;
                tmpEvent.timestamp=ts;
            }
            // paul            070208
            mostRecentTimestamp=ts;
            
            position++;
            return tmpEvent;
        }catch(BufferUnderflowException e) {
            try{
                mapChunk(++chunkNumber);
                return readEventForwards();
            }catch(IOException eof){
                byteBuffer=null;
                System.gc(); // all the byteBuffers have referred to mapped files and use up all memory, now free them since we're at end of file anyhow
                getSupport().firePropertyChange("eof",position(),position());
                throw new EOFException("reached end of file");
            }
        }catch(NullPointerException npe){
            rewind();
            return readEventForwards();
        }finally{
            mostRecentTimestamp=ts;
        }
    }
    
    /** Reads the next event backwards and leaves the position and byte buffer pointing to event one earlier
     than the one we just read. I.e., we back up, read the event, then back up again to leave us in state to
     either read forwards the event we just read, or to repeat backing up and reading if we read backwards
     */
    private Event3D readEventBackwards() throws IOException, NonMonotonicTimeException {
        // we enter this with position pointing to next event *to read forwards* and byteBuffer also in this state.
        // therefore we need to decrement the byteBuffer pointer and the position, and read the event.
        
        // update the position first to leave us afterwards pointing one before current position
        int newPos=position-1; // this is new absolute position
        if(newPos<0) {
            // reached start of file
            newPos=0;
//            System.out.println("readEventBackwards: reached start");
            throw new EOFException("reached start of file");
        }
        
        // normally we just update the postiion to be one less, then move the byteBuffer pointer back by
        // one event and read that new event. But if we have reached start of byte buffer, we
        // need to load a new chunk and set the buffer pointer to point to one event before the end
        int newBufPos=byteBuffer.position()-EVENT_SIZE;
        if(newBufPos<0){
            // check if we need to map a new earlier chunk of the file
            int newChunkNumber=getChunkNumber(newPos);
            if(newChunkNumber!=chunkNumber){
                mapChunk(--chunkNumber); // will throw EOFException when reaches start of file
                newBufPos=(EVENT_SIZE*newPos)%CHUNK_SIZE_BYTES;
                byteBuffer.position(newBufPos); // put the buffer pointer at the end of the buffer
            }
        }else{
            // this is usual situation
            byteBuffer.position(newBufPos);
        }
        int ts=0;
        int addrx=0;
        int addry=0;
        int addrd=0;
        int method=0;
        int lead_side=0;
        float value=0;
        // if pure 3d
        int addrz=0;
        if(pure3D){
            addrx=byteBuffer.getShort();
            addry=byteBuffer.getShort();
            addrz=byteBuffer.getShort();
            value=byteBuffer.getFloat();
            ts=byteBuffer.getInt();
        } else {
            addrx=byteBuffer.getShort();
            addry=byteBuffer.getShort();
            addrd=byteBuffer.getShort();
            method=byteBuffer.getShort();
            lead_side=byteBuffer.getShort();
            
            value=byteBuffer.getFloat();
            ts=byteBuffer.getInt();
        }

        
     
        byteBuffer.position(newBufPos);
        
         if(pure3D){
                tmpEvent.x0=addrx;
                tmpEvent.y0=addry;
                tmpEvent.z0=addrz;
                tmpEvent.value=value;
                tmpEvent.timestamp=ts;
            } else {
                tmpEvent.x=addrx;
                tmpEvent.y=addry;
                tmpEvent.d=addrd;
                tmpEvent.method=method;
                tmpEvent.lead_side=lead_side;
                tmpEvent.value=value;
                tmpEvent.timestamp=ts;
            }
        mostRecentTimestamp=ts;
        position--; // decrement position before exception to make sure we skip over a bad timestamp
        if(isWrappedTime(ts,mostRecentTimestamp,-1)){
            throw new WrappedTimeException(ts,mostRecentTimestamp,position);
        }
        if(ts>mostRecentTimestamp){
            throw new NonMonotonicTimeException(ts,mostRecentTimestamp,position);
        }
        return tmpEvent;
    }
    
    /** Uesd to read fixed size packets.
     @param n the number of events to read
     @return a raw packet of events of a specfied number of events
     fires a property change "position" on every call, and a property change "wrappedTime" if time wraps around.
     */
    synchronized public AEPacket3D readPacketByNumber(int n) throws IOException{
        if(!firstReadCompleted) fireInitPropertyChange();
        int an=(int)Math.abs(n);
        if(an>MAX_BUFFER_SIZE_EVENTS) {
            an=MAX_BUFFER_SIZE_EVENTS;
            if(n>0) n=MAX_BUFFER_SIZE_EVENTS; else n=-MAX_BUFFER_SIZE_EVENTS;
        }
//        short[] addr=new short[an];
//        int[] ts=new int[an];
        
        int[] coordinates_x=packet.getCoordinates_x();
        int[] coordinates_y=packet.getCoordinates_y();
        int[] coordinates_z=packet.getCoordinates_z();
        int[] disparities=packet.getDisparities();
        int[] methods=packet.getMethods();
        int[] lead_sides=packet.getLead_sides();
        float[] values = packet.getValues();
        int[] ts=packet.getTimestamps();
        int oldPosition=position();
        Event3D ev;
        int count=0;
        try{
            if(n>0){
                for(int i=0;i<n;i++){
                    ev=readEventForwards();
                    count++;
                    if(pure3D){
                        packet.setType(Event3D.DIRECT3D);
                        coordinates_x[i]=ev.x0;
                        coordinates_y[i]=ev.y0;
                        coordinates_z[i]=ev.z0;
                    } else {
                        packet.setType(Event3D.INDIRECT3D);
                        coordinates_x[i]=ev.x;
                        coordinates_y[i]=ev.y;
                        disparities[i]=ev.d;
                        methods[i]=ev.method;
                        lead_sides[i]=ev.lead_side;
                    }
                    values[i]=ev.value;
                    ts[i]=ev.timestamp;
                }
            }else{ // backwards
                n=-n;
                for(int i=0;i<n;i++){
                    ev=readEventBackwards();
                    count++;
                     if(pure3D){
                        packet.setType(Event3D.DIRECT3D);
                        coordinates_x[i]=ev.x0;
                        coordinates_y[i]=ev.y0;
                        coordinates_z[i]=ev.z0;
                    } else {
                        coordinates_x[i]=ev.x;
                        coordinates_y[i]=ev.y;
                        disparities[i]=ev.d;
                        methods[i]=ev.method;
                        lead_sides[i]=ev.lead_side;
                    }
                    values[i]=ev.value;// or - value?
                    ts[i]=ev.timestamp;
                }
            }
        }catch(WrappedTimeException e){
            getSupport().firePropertyChange("wrappedTime",oldPosition,position());
        }catch(NonMonotonicTimeException e){
//            log.info(e.getMessage());
        }
        packet.setNumEvents(count);
        getSupport().firePropertyChange("position",oldPosition,position());
        return packet;
//        return new AEPacketRaw(addr,ts);
    }
    
    /** returns an AEPacketRaw at least dt long up to the max size of the buffer or until end-of-file.
     *Events are read as long as the timestamp until (and including) the event whose timestamp is greater (for dt>0) than
     * startTimestamp+dt, where startTimestamp is the currentStartTimestamp. currentStartTimestamp is incremented after the call by dt.
     *Fires a property change "position" on each call.
     Fires property change "wrappedTime" when time wraps from positive to negative or vice versa (when playing backwards).
     *@param dt the timestamp different in units of the timestamp (usually us)
     *@see #MAX_BUFFER_SIZE_EVENTS
     */
    synchronized public AEPacket3D readPacketByTime(int dt) throws IOException{
        if(!firstReadCompleted) fireInitPropertyChange();
        int endTimestamp=currentStartTimestamp+dt;
        boolean bigWrap=isWrappedTime(endTimestamp,currentStartTimestamp,dt);
//        if( (dt>0 && mostRecentTimestamp>endTimestamp ) || (dt<0 && mostRecentTimestamp<endTimestamp)){
//            boolean lt1=endTimestamp<0, lt2=mostRecentTimestamp<0;
//            boolean changedSign= ( (lt1 && !lt2) || (!lt1 && lt2) );
//            if( !changedSign ){
//                currentStartTimestamp=endTimestamp;
//                log.info(this+" returning empty packet because mostRecentTimestamp="+mostRecentTimestamp+" is already later than endTimestamp="+endTimestamp);
//                return new AEPacketRaw(0);
//            }
//        }
        int startTimestamp=mostRecentTimestamp;
        int[] coordinates_x=packet.getCoordinates_x();
        int[] coordinates_y=packet.getCoordinates_y();
        int[] coordinates_z=packet.getCoordinates_z();
        int[] disparities=packet.getDisparities();
        int[] methods=packet.getMethods();
        int[] lead_sides=packet.getLead_sides();
        float[] values = packet.getValues();
        int[] ts=packet.getTimestamps();
        int oldPosition=position();
        Event3D ae;
        int i=0;
        try{
         //   if(dt>0){ // read forwards
                if(!bigWrap){
                    do{
                        ae=readEventForwards();
                        if(pure3D){
                            packet.setType(Event3D.DIRECT3D);
                            coordinates_x[i]=ae.x0;
                            coordinates_y[i]=ae.y0;
                            coordinates_z[i]=ae.z0;
                        } else {
                            coordinates_x[i]=ae.x;
                            coordinates_y[i]=ae.y;
                            disparities[i]=ae.d;
                            methods[i]=ae.method;
                            lead_sides[i]=ae.lead_side;
                        }
                        values[i]=ae.value;
                        ts[i]=ae.timestamp;
                        i++;
                    }while(mostRecentTimestamp<endTimestamp && i<values.length-1);
                }else{
                    do{
                        ae=readEventForwards();
                       
                         if(pure3D){
                            packet.setType(Event3D.DIRECT3D);
                            coordinates_x[i]=ae.x0;
                            coordinates_y[i]=ae.y0;
                            coordinates_z[i]=ae.z0;
                        } else {
                            coordinates_x[i]=ae.x;
                            coordinates_y[i]=ae.y;
                            disparities[i]=ae.d;
                            methods[i]=ae.method;
                            lead_sides[i]=ae.lead_side;
                        }
                        values[i]=ae.value;
                        ts[i]=ae.timestamp;
                        i++;
                    }while(mostRecentTimestamp>0 && i<values.length-1);
                    ae=readEventForwards();
                    if(pure3D){
                        packet.setType(Event3D.DIRECT3D);
                        coordinates_x[i]=ae.x0;
                        coordinates_y[i]=ae.y0;
                        coordinates_z[i]=ae.z0;
                    } else {
                        coordinates_x[i]=ae.x;
                        coordinates_y[i]=ae.y;
                        disparities[i]=ae.d;
                        methods[i]=ae.method;
                        lead_sides[i]=ae.lead_side;
                    }
                    values[i]=ae.value;
                    ts[i]=ae.timestamp;
                    i++;
                }
       /**     }else{ // read backwards
                if(!bigWrap){
                    do{
                        ae=readEventBackwards();
                         if(pure3D){
                            packet.setType(Event3D.DIRECT3D);
                            coordinates_x[i]=ae.x0;
                            coordinates_y[i]=ae.y0;
                            coordinates_z[i]=ae.z0;
                        } else {
                            coordinates_x[i]=ae.x;
                            coordinates_y[i]=ae.y;
                            disparities[i]=ae.d;
                            methods[i]=ae.method;
                            lead_sides[i]=ae.lead_side;
                        }
                        values[i]=ae.value;
                        ts[i]=ae.timestamp;
                        i++;
                    }while(mostRecentTimestamp>endTimestamp && i<values.length);
                } else{
                    do{
                        ae=readEventBackwards();
                         if(pure3D){
                            packet.setType(Event3D.DIRECT3D);
                            coordinates_x[i]=ae.x0;
                            coordinates_y[i]=ae.y0;
                            coordinates_z[i]=ae.z0;
                        } else {
                            coordinates_x[i]=ae.x;
                            coordinates_y[i]=ae.y;
                            disparities[i]=ae.d;
                            methods[i]=ae.method;
                            lead_sides[i]=ae.lead_side;
                        }
                        values[i]=ae.value;
                        ts[i]=ae.timestamp;
                        i++;
                    }while(mostRecentTimestamp<0 && i<values.length-1);
                    ae=readEventBackwards();
                     if(pure3D){
                            packet.setType(Event3D.DIRECT3D);
                            coordinates_x[i]=ae.x0;
                            coordinates_y[i]=ae.y0;
                            coordinates_z[i]=ae.z0;
                        } else {
                            coordinates_x[i]=ae.x;
                            coordinates_y[i]=ae.y;
                            disparities[i]=ae.d;
                            methods[i]=ae.method;
                            lead_sides[i]=ae.lead_side;
                        }
                    values[i]=ae.value;
                    ts[i]=ae.timestamp;
                    i++;
                }        
            } **/
            currentStartTimestamp=mostRecentTimestamp;
        }catch(WrappedTimeException w){
            log.warning(w.toString());
            currentStartTimestamp=w.getTimestamp();
            mostRecentTimestamp=w.getTimestamp();
            getSupport().firePropertyChange("3D wrappedTime",lastTimestamp,mostRecentTimestamp);
        }catch(NonMonotonicTimeException e){
//            e.printStackTrace();
            if(numNonMonotonicTimeExceptionsPrinted++<MAX_NONMONOTONIC_TIME_EXCEPTIONS_TO_PRINT){
                log.info(e+" resetting currentStartTimestamp from "+currentStartTimestamp+" to "+e.getTimestamp()+" and setting mostRecentTimestamp to same value");
                if(numNonMonotonicTimeExceptionsPrinted==MAX_NONMONOTONIC_TIME_EXCEPTIONS_TO_PRINT){
                    log.warning("suppressing further warnings about NonMonotonicTimeException");
                }
            }
            currentStartTimestamp=e.getTimestamp();
            mostRecentTimestamp=e.getTimestamp();
        }
        packet.setNumEvents(i);
        getSupport().firePropertyChange("position",oldPosition,position());
        return packet;
    }
    
    /** rewind to the start, or to the marked position, if it has been set. 
     Fires a property change "position" followed by "rewind". */
    synchronized public void rewind()  throws IOException{
        int oldPosition=position();
        position(markPosition);
        try{
            if(markPosition==0) {
                mostRecentTimestamp=firstTimestamp;
//                skipHeader();
            }else{
                readEventForwards(); // to set the mostRecentTimestamp
            }
        }catch(NonMonotonicTimeException e){
            log.info("rewind from timestamp="+e.getLastTimestamp()+" to timestamp="+e.getTimestamp());
        }
        currentStartTimestamp=mostRecentTimestamp;
//        System.out.println("AEInputStream.rewind(): set position="+byteBuffer.position()+" mostRecentTimestamp="+mostRecentTimestamp);
        getSupport().firePropertyChange("position",oldPosition,position());
        getSupport().firePropertyChange("rewind",oldPosition,position());
    }
    
    /** gets the size of the stream in events
     @return size in events */
    public long size() {
        return (fileSize-headerOffset)/EVENT_SIZE;
    }
    
    /** set position in events from start of file
     @param event the number of the event, starting with 0
     */
    synchronized public void position(int event){
//        if(event==size()) event=event-1;
        int newChunkNumber;
        try{
            if((newChunkNumber=getChunkNumber(event))!=chunkNumber){
                mapChunk(newChunkNumber);
            }
            byteBuffer.position((event*EVENT_SIZE)%CHUNK_SIZE_BYTES);
            position=event;
//            fileChannel.position(event*EVENT_SIZE);
//            byteBuffer.position(event*EVENT_SIZE);
        }catch(IOException e){
            e.printStackTrace();
        }
    }
    
    /** gets the current position for reading forwards, i.e., readEventForwards will read this event number.
     @return position in events
     */
    synchronized public int position(){
        return this.position;
//        try{
//            return (int)(fileChannel.position()/EVENT_SIZE);
//        }catch(IOException e){
//            e.printStackTrace();
//            return 0;
//        }
//        return byteBuffer.position()/EVENT_SIZE;
    }
    
    /**Returns the position as a fraction of the total number of events
     @return fractional position in total events*/
    synchronized public float getFractionalPosition(){
        return (float)position()/size();
    }
    
    /** Sets fractional position in events
     * @param frac 0-1 float range, 0 at start, 1 at end
     */
    synchronized public void setFractionalPosition(float frac){
        position((int)(frac*size()));
        try{
            readEventForwards();
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    
    /** AEFileInputStream has PropertyChangeSupport. This support fires events on certain events such as "rewind".
     */
    public PropertyChangeSupport getSupport() {
        return support;
    }
    
    /** mark the current position.
     * @throws IOException if there is some error in reading the data
     */
    synchronized public void mark() throws IOException {
        markPosition=position();
        markPosition=(markPosition/EVENT_SIZE)*EVENT_SIZE; // to avoid marking inside an event
//        System.out.println("AEInputStream.mark() marked position "+markPosition);
    }
    
    /** mark the current position as the IN point for editing.
     * @throws IOException if there is some error in reading the data
     */
    synchronized public void markIn() throws IOException {
        markInPosition=position();
        markInPosition=(markPosition/EVENT_SIZE)*EVENT_SIZE; // to avoid marking inside an event
    }
    
    /** mark the current position as the OUT position for editing.
     * @throws IOException if there is some error in reading the data
     */
    synchronized public void markOut() throws IOException {
        markOutPosition=position();
        markOutPosition=(markPosition/EVENT_SIZE)*EVENT_SIZE; // to avoid marking inside an event
    }
    
    /** clear any marked position */
    synchronized public void unmark(){
        markPosition=0;
    }
    
    public void close() throws IOException{
        super.close();
        fileChannel.close();
        System.gc();
        System.runFinalization(); // try to free memory mapped file buffers so file can be deleted....
    }
    
    /** returns the first timestamp in the stream
     @return the timestamp
     */
    public int getFirstTimestamp() {
        return firstTimestamp;
    }
    
    /** @return last timestamp in file */
    public int getLastTimestamp() {
        return lastTimestamp;
    }
    
    /** @return the duration of the file in us. <p>
     * Assumes data file is timestamped in us. This method fails to provide a sensible value if the timestamp wwaps.
     */
    public int getDurationUs(){
        return lastTimestamp-firstTimestamp;
    }
    
    /** @return the present value of the startTimestamp for reading data */
    synchronized public int getCurrentStartTimestamp() {
        return currentStartTimestamp;
    }
    
    public void setCurrentStartTimestamp(int currentStartTimestamp) {
        this.currentStartTimestamp = currentStartTimestamp;
    }
    
    /** @return returns the most recent timestamp
     */
    public int getMostRecentTimestamp() {
        return mostRecentTimestamp;
    }
    
    public void setMostRecentTimestamp(int mostRecentTimestamp) {
        this.mostRecentTimestamp = mostRecentTimestamp;
    }
    
    /** class used to signal a backwards read from input stream */
    public class NonMonotonicTimeException extends Exception{
        protected int timestamp, lastTimestamp, position;
        public NonMonotonicTimeException(){
            super();
        }
        public NonMonotonicTimeException(String s){
            super(s);
        }
        public NonMonotonicTimeException(int ts){
            this.timestamp=ts;
        }
        public NonMonotonicTimeException(int readTs, int lastTs){
            this.timestamp=readTs;
            this.lastTimestamp=lastTs;
        }
        public NonMonotonicTimeException(int readTs, int lastTs, int position){
            this.timestamp=readTs;
            this.lastTimestamp=lastTs;
            this.position=position;
        }
        public int getTimestamp(){
            return timestamp;
        }
        public int getLastTimestamp(){
            return lastTimestamp;
        }
        public String toString(){
            return "NonMonotonicTimeException: position="+position+" timestamp="+timestamp+" lastTimestamp="+lastTimestamp+" jumps backwards by "+(timestamp-lastTimestamp);
        }
    }
    
    /** Indicates that timestamp has wrapped around from most positive to most negative signed value.
     The de-facto timestamp tick is us and timestamps are represented as int32 in jAER. Therefore the largest possible positive timestamp
     is 2^31-1 ticks which equals 2147.4836 seconds (35.7914 minutes). This wraps to -2147 seconds. The actual total time
     can be computed taking account of these "big wraps" if
     the time is increased by 4294.9673 seconds on each WrappedTimeException (when readimg file forwards).
     */
    public class WrappedTimeException extends NonMonotonicTimeException{
        
        public WrappedTimeException(int readTs, int lastTs){
            super(readTs,lastTs);
        }
        public WrappedTimeException(int readTs, int lastTs,int position){
            super(readTs,lastTs,position);
        }
        public String toString(){
            return "WrappedTimeException: timestamp="+timestamp+" lastTimestamp="+lastTimestamp+" jumps backwards by "+(timestamp-lastTimestamp);
        }
    }
    
    
    private final boolean isWrappedTime(int read, int prevRead, int dt){
        if(dt>0 && read<0 && prevRead>0) return true;
        if(dt<0 && read>0 && prevRead<0) return true;
        return false;
    }
    
    /** cuts out the part of the stream from IN to OUT and returns it as a new AEInputStream
     @return the new stream
     */
    public AEFileInputStream cut(){
        AEFileInputStream out=null;
        return out;
    }
    
    /** copies out the part of the stream from IN to OUT markers and returns it as a new AEInputStream
     @return the new stream
     */
    public AEFileInputStream copy(){
        AEFileInputStream out=null;
        return out;
    }
    
    /** pastes the in stream at the IN marker into this stream
     @param in the stream to paste
     */
    public void paste(AEFileInputStream in){
        
    }
    
    /** returns the chunk number which starts with 0. For position<CHUNK_SIZE_BYTES returns 0
     */
    private int getChunkNumber(int position){
        int chunk=(int)((position*EVENT_SIZE)/CHUNK_SIZE_BYTES);
        return chunk;
    }
    
    /** memory-maps a chunk of the input file.
     @param chunkNumber the number of the chunk, starting with 0
     */
    private void mapChunk(int chunkNumber) throws IOException {
        int chunkSize=CHUNK_SIZE_BYTES;
        int start=chunkStart(chunkNumber);
        if(start>=fileSize){
            throw new EOFException("start of chunk="+start+" but file has fileSize="+fileSize);
        }
        if(start+CHUNK_SIZE_BYTES>=fileSize){
            chunkSize=(int)(fileSize-start);
        }
        byteBuffer=fileChannel.map(FileChannel.MapMode.READ_ONLY,start,chunkSize);
        this.chunkNumber=chunkNumber;
//        log.info("mapped chunk # "+chunkNumber);
    }
    
    /** @return start of chunk in bytes
     @param chunk the chunk number
     */
    private int chunkStart(int chunk){
        if(chunk==0) return headerOffset;
        return (chunk*CHUNK_SIZE_BYTES)+headerOffset;
        
    }
    
    private int chunkEnd(int chunk){
        return headerOffset+(chunk+1)*CHUNK_SIZE_BYTES;
    }
    
    /** skips the header lines (if any) */
    protected void skipHeader() throws IOException{
        readHeader();
    }
    
    /** reads the header comment lines. Assumes we are rewound to position(0).
     */
    protected void readHeader() throws IOException {
        String s;
//        System.out.println("File header:");
        while((s=readHeaderLine())!=null){
            header.add(s);
//            System.out.println(s);
        }
        mapChunk(0); // remap chunk 0 to skip header section of file
        StringBuffer sb=new StringBuffer();
        sb.append("File header:");
        for(String str:getHeader()){
            sb.append(str);
            sb.append("\n");
        }
        log.info(sb.toString());
    }
    
    /** assumes we are positioned at start of line and that we may either read a comment char '#' or something else
     leaves us after the line at start of next line or of raw data. Assumes header lines are written using the AEOutputStream.writeHeaderLine().
     @return header line
     */
    String readHeaderLine() throws IOException{
        StringBuffer s=new StringBuffer();
        byte c=byteBuffer.get();
        if(c!=AEDataFile.COMMENT_CHAR) {
            byteBuffer.position(byteBuffer.position()-1);
            headerOffset=byteBuffer.position();
            return null;
        }
        while(((char)(c=byteBuffer.get()))!='\r'){
            if(c<32 || c>126){
                log.warning("Non printable character (<32 || >126) detected in header line, aborting header read and resetting to start of file because this file may not have a real header");
                byteBuffer.position(0);
                return null;
            }
            s.append((char)c);
        }
        if((c=byteBuffer.get())!='\n'){
            log.warning("header line \""+s.toString()+"\" doesn't end with LF"); // get LF of CRLF
        }
        return s.toString();
    }
    
    /** Gets the header strings from the file
     @return list of strings, one per line
     */
    public ArrayList<String> getHeader(){
        return header;
    }

    private void fireInitPropertyChange() {
        getSupport().firePropertyChange("init",0,0);
        firstReadCompleted=true;
    }

    /** Returns the File that is being read, or null if the instance is constructed from a FileInputStream */
    public File getFile() {
        return file;
    }
    
    /** Sets the File reference but doesn't open the file */
    public void setFile(File f){
        this.file=f;
        absoluteStartingTimeMs=getAbsoluteStartingTimeMsFromFile(getFile());
    }

    /** When the file is opened, the filename is parsed to try to extract the date and time the file was created from the filename.
     @return the time logging was started in ms since 1970
     */
    public long getAbsoluteStartingTimeMs() {
        return absoluteStartingTimeMs;
    }

    public void setAbsoluteStartingTimeMs(long absoluteStartingTimeMs) {
        this.absoluteStartingTimeMs = absoluteStartingTimeMs;
    }

    /** @return start of logging time in ms, i.e., in "java" time, since 1970 */
    private long getAbsoluteStartingTimeMsFromFile(File f){
        if(f==null){
            return 0;
        }
        try{
            String fn=f.getName();
            String dateStr=fn.substring(fn.indexOf('-')+1); // guess that datestamp is right after first - which follows Chip classname
            Date date=AEDataFile.DATE_FORMAT.parse(dateStr);
            return date.getTime();
        }catch(Exception e){
            log.warning(e.toString());
            return 0;
        }
    }

}
