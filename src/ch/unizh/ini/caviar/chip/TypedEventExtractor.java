/*
 * RetinaEventExtractor.java
 *
 * Created on October 21, 2005, 6:24 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package ch.unizh.ini.caviar.chip;

import ch.unizh.ini.caviar.aemonitor.*;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.event.BasicEvent;
import ch.unizh.ini.caviar.event.OutputEventIterator;
import java.util.logging.Logger;

/**
 * An abstract 2D event extractor for 16 bit raw addresses. It is called with addresses and timestamps and extracts these to X, Y, type arrays based on methods that you define by subclassing
 *and overriding the abstract methods. xMask, yMask, typeMask mask for x, y  address and cell type, and xShift, yShift, typeShift say how many bits to shift after
 *masking, xFlip,yFlip,typeFlip use the chip size to flip the x,y, and type to invert the addresses.
 *
 
 * @author tobi
 */
abstract public class TypedEventExtractor<T extends BasicEvent> implements EventExtractor2D, java.io.Serializable {
    static Logger log=Logger.getLogger("ch.unizh.ini.caviar.chip");
    
//    protected AEChip chip;
    protected int xmask,ymask;
    protected byte xshift,yshift;
    protected int typemask;
    protected byte typeshift;
    protected AEChip chip=null;
    protected boolean flipx=false, flipy=false, rotate=false;
    protected boolean fliptype=false;
    protected boolean hexArrangement=false;
    // the reused output eventpacket
    protected EventPacket out=null;
//    AEPacket2D outReused=new AEPacket2D();
    Class eventClass=TypedEvent.class;
    
    /** determines subSampling of rendered events (for speed) */
    private boolean subsamplingEnabled=false;
    
    private int subsampleThresholdEventCount=50000;
    
    
    private short sizex,sizey; // these are size-1 (e.g. if 128 pixels, sizex=127). used for flipping below.
    private byte sizetype;
    
//    /** Creates a new instance of RetinaEventExtractor */
//    public TypedEventExtractor() {
//    }
    
    /** Creates a new instance of RetinaEventExtractor
     * @param aechip the chip this extractor extracts
     */
    public TypedEventExtractor(AEChip aechip) {
        chip=aechip;
        setchipsizes();
    }
    
    private void setchipsizes(){
        sizex=(short)(chip.getSizeX()-1);
        sizey=(short)(chip.getSizeY()-1);
        sizetype=(byte)(chip.getNumCellTypes()-1);
    }
    
    /** gets X from raw address. declared final for speed, cannot be overridden in subclass.
     *@param addr the raw address.
     *@return physical address
     */
    public short getXFromAddress(int addr){
        if(!flipx) return ((short)((addr&xmask)>>>xshift));
        else return (short)(sizex - ((int)((addr&xmask)>>>xshift))); // e.g. chip.sizex=32, sizex=31, addr=0, getX=31, addr=31, getX=0
    }
    
    /** gets Y from raw address. declared final for speed, cannot be overridden in subclass.
     *@param addr the raw address.
     *@return physical address
     */
    public short getYFromAddress(int addr){
        if(!flipy) return ((short)((addr&ymask)>>>yshift));
        else return (short)(sizey-((int)((addr&ymask)>>>yshift)));
        
    }
    /** gets type from raw address. declared final for speed, cannot be overridden in subclass.
     *@param addr the raw address.
     *@return physical address
     */
    public byte getTypeFromAddress(int addr){
        if(!fliptype) return (byte)((addr&typemask)>>>typeshift);
        else return (byte)(sizetype-(byte)((addr&typemask)>>>typeshift));
    }
    
    
    
    /** extracts the meaning of the raw events.
     *@param in the raw events, can be null
     *@return out the processed events. these are partially processed in-place. empty packet is returned if null is supplied as in.
     */
    synchronized public EventPacket extractPacket(AEPacketRaw in) {
        if(out==null){
            out=new EventPacket<T>(chip.getEventClass());
        }else{
            out.clear();
        }
        if(in==null) return out;
        extractPacket(in,out);
        return out;
    }
    
    /** extracts the meaning of the raw events. This form is used to supply an output packet. This method is used for real time event filtering using
     a buffer of output events local to data acquisition.
     *@param in the raw events, can be null
     *@param out the processed events. these are partially processed in-place. empty packet is returned if null is supplied as in.
     */
    synchronized public void extractPacket(AEPacketRaw in, EventPacket out) {
        out.clear();
        if(in==null) return;
        int n=in.getNumEvents(); //addresses.length;
        
        int skipBy=1;
        if(subsamplingEnabled){
            while(n/skipBy>getSubsampleThresholdEventCount()){
                skipBy++;
            }
        }
        int[] a=in.getAddresses();
        int[] timestamps=in.getTimestamps();
        boolean hasTypes=false;
        if(chip!=null) hasTypes=chip.getNumCellTypes()>1;
        OutputEventIterator outItr=out.outputIterator();
        for(int i=0;i<n;i+=skipBy){ // bug here?
            int addr=a[i];
            BasicEvent e=(BasicEvent)outItr.nextOutput();
            e.timestamp=(timestamps[i]);
            e.x=getXFromAddress(addr);
            e.y=getYFromAddress(addr);
            if(hasTypes){
                ((TypedEvent)e).type=getTypeFromAddress(addr);
            }
//            System.out.println("a="+a[i]+" t="+e.timestamp+" x,y="+e.x+","+e.y);
        }
    }
    
    
    
    public int getTypemask() {
        return this.typemask;
    }
    
    public void setTypemask(final int typemask) {
        this.typemask = typemask;
    }
    
    public byte getTypeshift() {
        return this.typeshift;
    }
    
    public void setTypeshift(final byte typeshift) {
        this.typeshift = typeshift;
    }
    
    public int getXmask() {
        return this.xmask;
    }
    
    /** bit mask for x address, before shift */
    public void setXmask(final int xmask) {
        this.xmask = xmask;
    }
    
    public byte getXshift() {
        return this.xshift;
    }
    
    /** @param xshift the number of bits to right shift raw address after masking with {@link #setXmask} */
    public void setXshift(final byte xshift) {
        this.xshift = xshift;
    }
    
    public int getYmask() {
        return this.ymask;
    }
    
    /** @param ymask the bit mask for y address, before shift */
    public void setYmask(final int ymask) {
        this.ymask = ymask;
    }
    
    public byte getYshift() {
        return this.yshift;
    }
    
    /** @param yshift the number of bits to right shift raw address after masking with {@link #setYmask} */
    public void setYshift(final byte yshift) {
        this.yshift = yshift;
    }
    
//    short clipx(short v){ short c=(short) (v>(chip.sizeX-1)? chip.sizeX-1: v); c=c<0?0:c; return c;}
//    short clipy(short v){ short c= (short)(v>(chip.sizeY-1)? chip.sizeY-1: v); c=c<0?0:c; return c;}
    
    
    public boolean isFlipx() {
        return this.flipx;
    }
    
    public void setFlipx(final boolean flipx) {
        if(chip.getSizeX()==1){
            log.warning("setFlipx for chip"+chip+": chip sizeX=1, flipping doesn't make sense, disabling");
            this.flipx=false;
            return;
        }
        this.flipx = flipx;
    }
    
    public boolean isFlipy() {
        return this.flipy;
    }
    
    public void setFlipy(final boolean flipy) {
        if(chip.getSizeY()==1){
            log.warning("setFlipy for chip"+chip+": chip sizeY=1, flipping doesn't make sense, disabling");
            this.flipy=false;
            return;
        }
        this.flipy = flipy;
    }
    
    public boolean isFliptype() {
        return this.fliptype;
    }
    
    public void setFliptype(final boolean fliptype) {
        if(chip.getNumCellTypes()==1){
            log.warning("setFliptype for chip"+chip+": chip numTypes=1, flipping doesn't usually make sense, will treat it to make type=1 instead of default 0");
//            this.fliptype=false;
//            return;
        }
        this.fliptype = fliptype;
    }
    
    public int getUsedBits() {
        return (int)((xmask<<xshift+ymask<<yshift+typemask<<typeshift));
    }
    
    public boolean matchesAddress(int addr1, int addr2){
        return (addr1&getUsedBits())==(addr2&getUsedBits());
    }
    
    /** computes the raw address from an x,y, and type. Useful for searching for events in e.g. matlab, given the raw addresses.
     *This function does include flipped addresses - it uses flip booleans to pre-adjust x,y,type for chip.
     *@param x the x address
     *@param y the y address
     *param type the cell type
     *@return the raw address
     */
    public int getAddressFromCell(int x, int y, int type) {
        if(flipx) x=sizex-x;
        if(flipy) y=sizey-y;
        if(fliptype) type=sizetype-type;
        
        return (int)(
                (x<<xshift)
                +(y<<yshift)
                +(type<<typeshift)
                );
    }
    
    public boolean isSubsamplingEnabled() {
        return subsamplingEnabled;
    }
    
    public void setSubsamplingEnabled(boolean subsamplingEnabled) {
        this.subsamplingEnabled = subsamplingEnabled;
    }
    
    public int getSubsampleThresholdEventCount() {
        return subsampleThresholdEventCount;
    }
    
    public void setSubsampleThresholdEventCount(int subsampleThresholdEventCount) {
        this.subsampleThresholdEventCount = subsampleThresholdEventCount;
    }
    
    AEPacketRaw raw=null;
    
    
    /** reconstructs a raw packet suitable for logging to a data file, from an EventPacket that could be the result of filtering operations
     @param packet the EventPacket
     @return a raw packet holding the device events
     */
    public AEPacketRaw reconstructRawPacket(EventPacket packet) {
        if(raw==null) raw=new AEPacketRaw();
        raw.setNumEvents(0);
        EventRaw r=new EventRaw();
        for(Object o:packet){
            TypedEvent e=(TypedEvent)o;
            r.timestamp=e.timestamp;
            r.address=getAddressFromCell(e.x,e.y,e.type);
            raw.addEvent(r);
        }
        return raw;
    }
    
}
