/*
 * AEInputStream.java
 *
 * Created on December 26, 2005, 1:03 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.caviar.eventio;

import ch.unizh.ini.caviar.aemonitor.*;
import ch.unizh.ini.caviar.util.EngineeringFormat;
import java.beans.PropertyChangeSupport;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 * Class to stream in packets of events from binary input stream from a file recorded by AEViewer.
 *<p>
 *File format is very simple:
 *<pre>
 * int16 address
 *int32 timestamp
 *
 * int16 address
 *int32 timestamp
 *</pre>
 
<p>
 An optional header consisting of lines starting with '#' is skipped when opening the file and may be retrieved.
 No later comment lines are allowed because the rest ot the file should be pure binary.
 
 * @author tobi
 */
public class AEFileInputStream extends DataInputStream implements AEInputStreamInterface {
//    public final static long MAX_FILE_SIZE=200000000;
    private PropertyChangeSupport support=new PropertyChangeSupport(this);
    static Logger log=Logger.getLogger("ch.unizh.ini.caviar.eventio");
    FileInputStream in;
    long fileSize=0; // size of file in bytes
    InputStreamReader reader=null;
    
    public final int MAX_NONMONOTONIC_TIME_EXCEPTIONS_TO_PRINT=1000;
    private int numNonMonotonicTimeExceptionsPrinted=0;
    private int markPosition=0; // a single MARK position for rewinding to
    private int markInPosition=0, markOutPosition=0; // points to mark IN and OUT positions for editing
    private int numChunks=0; // the number of mappedbytebuffer chunks in the file
    
    //    private int numEvents,currentEventNumber;
    
    // mostRecentTimestamp is the last event sucessfully read
    // firstTimestamp, lastTimestamp are the first and last timestamps in the file (at least the memory mapped part of the file)
    private int mostRecentTimestamp, firstTimestamp, lastTimestamp;
    
    // this marks the present read time for packets
    private int currentStartTimestamp;
    
    FileChannel fileChannel=null;
    
    public static final int MAX_BUFFER_SIZE_EVENTS=300000;
    public static final int EVENT_SIZE=Short.SIZE/8+Integer.SIZE/8;
    
    // buffers
    /** the size of the memory mapped part of the input file.
     This window is centered over the file posiiton except at the start and end of the file.
     */
    public static final int CHUNK_SIZE_BYTES=EVENT_SIZE*10000000;
    
    // the packet used for reading events
    private AEPacketRaw packet=new AEPacketRaw(MAX_BUFFER_SIZE_EVENTS);
    
    EventRaw tmpEvent=new EventRaw();
    MappedByteBuffer byteBuffer=null;
//    private ByteBuffer eventByteBuffer=ByteBuffer.allocateDirect(EVENT_SIZE); // the ByteBuffer that a single event is written into from the fileChannel and read from to get the addr & timestamp
    private int chunkNumber=0;
    
    private int position=0; // absolute position in file in events, points to next event number, 0 based (1 means 2nd event)
    
    protected ArrayList<String> header=new ArrayList<String>();
    private int headerOffset=0; // this is starting position in file for rewind or to add to positioning via slider
    
    /** Creates a new instance of AEInputStream */
    public AEFileInputStream(FileInputStream in) {
        super(in);
        init(in);
    }
    
    public String toString(){
        EngineeringFormat fmt=new EngineeringFormat();
        String s="AEInputStream with size="
                +fmt.format(size())
                +" events, firstTimestamp="
                +getFirstTimestamp()
                +" lastTimestamp="
                +getLastTimestamp()
                +" duration="
                +fmt.format(getDurationUs()/1e6f)+" s";
        return s;
    }
    
    // fires property change "position"
    void init(FileInputStream fileInputStream){
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
            EventRaw ev=readEventForwards(); // init timestamp
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
        getSupport().firePropertyChange("position",0,position());
//        try{
//            rewind();
//        }catch(IOException e){
//            e.printStackTrace();
//        }
    }
    
    
    private EventRaw readEventForwards() throws IOException, NonMonotonicTimeException{
        int ts=0;
        short addr=0;
        try{
//            eventByteBuffer.rewind();
//            fileChannel.read(eventByteBuffer);
//            eventByteBuffer.rewind();
//            addr=eventByteBuffer.getShort();
//            ts=eventByteBuffer.getInt();
            addr=byteBuffer.getShort();
            ts=byteBuffer.getInt();
            // check for non-monotonic increasing timestamps, if we get one, reset our notion of the starting time
            if(isWrappedTime(ts,mostRecentTimestamp,1)){
                throw new WrappedTimeException(ts,mostRecentTimestamp,position);
            }
            if(ts<mostRecentTimestamp){
//                log.warning("AEInputStream.readEventForwards returned ts="+ts+" which goes backwards in time (mostRecentTimestamp="+mostRecentTimestamp+")");
                throw new NonMonotonicTimeException(ts,mostRecentTimestamp,position);
            }
            tmpEvent.address=addr;
            tmpEvent.timestamp=ts;
            position++;
            return tmpEvent;
        }catch(BufferUnderflowException e) {
            try{
                mapChunk(++chunkNumber);
                return readEventForwards();
            }catch(IOException eof){
                byteBuffer=null;
                System.gc(); // all the byteBuffers have referred to mapped files and use up all memory, now free them since we're at end of file anyhow
                throw new EOFException("reached end of file");
            }
        }catch(NullPointerException npe){
            rewind();
            return readEventForwards();
        }finally{
            mostRecentTimestamp=ts;
        }
    }
    
    private EventRaw readEventBackwards() throws IOException, NonMonotonicTimeException {
        int newPos=position-1; // this is new absolute position
        if(newPos<0) {
            // reached start of file
            newPos=0;
//            System.out.println("readEventBackwards: reached start");
            throw new EOFException("reached start of file");
        }
        // check if we need to map a new earlier chunk of the file
        int newChunkNumber=getChunkNumber(newPos);
        if(newChunkNumber!=chunkNumber){
            mapChunk(--chunkNumber); // will throw EOFException when reaches start of file
            byteBuffer.position((EVENT_SIZE*newPos)%CHUNK_SIZE_BYTES); // put the buffer pointer at the end of the buffer
        }
        int newBufPos=byteBuffer.position()-EVENT_SIZE;
        if(newBufPos<0){
            throw new IOException();
        }
        byteBuffer.position(newBufPos);
        short addr=byteBuffer.getShort();
        int ts=byteBuffer.getInt();
        byteBuffer.position(newBufPos);
        if(isWrappedTime(ts,mostRecentTimestamp,-1)){
            throw new WrappedTimeException(ts,mostRecentTimestamp,position);
        }
        if(ts>mostRecentTimestamp){
            throw new NonMonotonicTimeException(ts,mostRecentTimestamp,position);
        }
        tmpEvent.address=addr;
        tmpEvent.timestamp=ts;
        mostRecentTimestamp=ts;
        position--;
        
        return tmpEvent;
    }
    
    /** fires a property change "position"
     */
    synchronized public AEPacketRaw readPacketByNumber(int n) throws IOException{
        int an=(int)Math.abs(n);
        if(an>MAX_BUFFER_SIZE_EVENTS) {
            an=MAX_BUFFER_SIZE_EVENTS;
            if(n>0) n=MAX_BUFFER_SIZE_EVENTS; else n=-MAX_BUFFER_SIZE_EVENTS;
        }
//        short[] addr=new short[an];
//        int[] ts=new int[an];
        short[] addr=packet.getAddresses();
        int[] ts=packet.getTimestamps();
        int oldPosition=position();
        EventRaw ev;
        try{
            if(n>0){
                for(int i=0;i<n;i++){
                    ev=readEventForwards();
                    addr[i]=ev.address;
                    ts[i]=ev.timestamp;
                }
            }else{ // backwards
                n=-n;
                for(int i=0;i<n;i++){
                    ev=readEventBackwards();
                    addr[i]=ev.address;
                    ts[i]=ev.timestamp;
                }
            }
        }catch(NonMonotonicTimeException e){
//            log.info(e.getMessage());
        }
        packet.setNumEvents(an);
        getSupport().firePropertyChange("position",oldPosition,position());
        return packet;
//        return new AEPacketRaw(addr,ts);
    }
    
    /** returns an AEPacketRaw at least dt long up to the max size of the buffer or until end-of-file.
     *Events are read as long as the timestamp until (and including) the event whose timestamp is greater (for dt>0) than
     * startTimestamp+dt, where startTimestamp is the currentStartTimestamp. currentStartTimestamp is incremented after the call by dt.
     *Fires a property change "position".
     *@param dt the timestamp different in units of the timestamp (usually us)
     *@see #MAX_BUFFER_SIZE_EVENTS
     */
    synchronized public AEPacketRaw readPacketByTime(int dt) throws IOException{
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
        short[] addr=packet.getAddresses();
        int[] ts=packet.getTimestamps();
        int oldPosition=position();
        EventRaw ae;
        int i=0;
        try{
            if(dt>0){ // read forwards
                if(!bigWrap){
                    do{
                        ae=readEventForwards();
                        addr[i]=ae.address;
                        ts[i]=ae.timestamp;
                        i++;
                    }while(mostRecentTimestamp<endTimestamp && i<addr.length-1);
                }else{
                    do{
                        ae=readEventForwards();
                        addr[i]=ae.address;
                        ts[i]=ae.timestamp;
                        i++;
                    }while(mostRecentTimestamp>0 && i<addr.length-1);
                    ae=readEventForwards();
                    addr[i]=ae.address;
                    ts[i]=ae.timestamp;
                    i++;
                }
            }else{ // read backwards
                if(!bigWrap){
                    do{
                        ae=readEventBackwards();
                        addr[i]=ae.address;
                        ts[i]=ae.timestamp;
                        i++;
                    }while(mostRecentTimestamp>endTimestamp && i<addr.length);
                }else{
                    do{
                        ae=readEventBackwards();
                        addr[i]=ae.address;
                        ts[i]=ae.timestamp;
                        i++;
                    }while(mostRecentTimestamp<0 && i<addr.length-1);
                    ae=readEventBackwards();
                    addr[i]=ae.address;
                    ts[i]=ae.timestamp;
                    i++;
                }
            }
            currentStartTimestamp=mostRecentTimestamp;
        }catch(WrappedTimeException w){
            w.printStackTrace();
            currentStartTimestamp=w.getTimestamp();
            mostRecentTimestamp=w.getTimestamp();
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
    
    /** rewind to the start, or to the marked position, if it has been set. Fires a property change "position". */
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
    
    /** gets the current position
     @return position in events */
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
//            e.printStackTrace();
        }
    }
    
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
    
    
    final boolean isWrappedTime(int read, int prevRead, int dt){
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
    
}
