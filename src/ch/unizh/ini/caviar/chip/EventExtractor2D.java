package ch.unizh.ini.caviar.chip;

import ch.unizh.ini.caviar.aemonitor.*;
import ch.unizh.ini.caviar.event.EventPacket;
/**
 *The interface a 2D event extractor must implmeent
 */

public interface EventExtractor2D {
    
//    public AEPacket2D extract(AEPacketRaw events);
    
    /** 
     Extracts raw packet to EventPacket
     @param events the raw aer events
     @return an EventPacket that holds the digested events. This packet may be reused
     */
    public EventPacket extractPacket(AEPacketRaw events);
    
    /** 
     Extracts raw packet to EventPacket
     @param raw the raw aer events
     @param cooked events are written to this cooked event packet
     */
    public void extractPacket(AEPacketRaw raw, EventPacket cooked);
    
    /** reconstructs a raw packet suitable for logging to a data file, from an EventPacket that could be the result of filtering operations
     @param packet the EventPacket
     @return a raw packet holding the device events
     */
    public AEPacketRaw reconstructRawPacket(EventPacket packet);
    
//    public int[] getTimestamps();
    
    public byte getTypeFromAddress(short addr);
    
    public short getTypemask();
    
//    public byte[] getTypes();
    
    public byte getTypeshift();
    
//    public short[] getXAddresses();
    
    public short getXFromAddress(short addr);
    
    public short getXmask();
    
    public byte getXshift();
    
//    public short[] getYAddresses();
    
    public short getYFromAddress(short addr);
    
    public short getYmask();
    
    public byte getYshift();
    
    public void setTypemask(final short typemask);
    
    public void setTypeshift(final byte typeshift);
    
    public void setXmask(final short xmask);
    
    public void setXshift(final byte xshift);
    
    public void setYmask(final short ymask);
    
    public void setYshift(final byte yshift);
    
    public boolean isFlipx() ;
    public void setFlipx(final boolean flipx) ;
    
    public boolean isFlipy() ;
    
    public void setFlipy(final boolean flipy) ;
    
    public boolean isFliptype() ;
    public void setFliptype(final boolean fliptype) ;
    
    public short getUsedBits();
    
    public boolean matchesAddress(short addr1, short addr2);
    
    public short getAddressFromCell(int x, int y, int type);
    
    public void setSubsamplingEnabled(boolean subsamplingEnabled);
    
    public boolean isSubsamplingEnabled();
    
    public int getSubsampleThresholdEventCount();
    
    public void setSubsampleThresholdEventCount(int subsampleThresholdEventCount);
    
}
