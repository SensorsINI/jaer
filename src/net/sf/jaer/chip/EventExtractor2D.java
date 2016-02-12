package net.sf.jaer.chip;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
/**
 *The interface a 2D event extractor must implement. It is parameterized by the primitive int raw event address type.
 */

public interface EventExtractor2D<E extends BasicEvent> {

//    public AEPacket2D extract(AEPacketRaw events);

    /**
     Extracts raw packet to EventPacket
     @param events the raw AER events
     @return an EventPacket that holds the digested events. This packet may be reused
     */
    public EventPacket<E> extractPacket(AEPacketRaw events);

    /**
     Extracts raw packet to EventPacket
     @param raw the raw aer events
     @param cooked events are written to this cooked event packet
     */
    public void extractPacket(AEPacketRaw raw, EventPacket<E> cooked);

    /** reconstructs a raw packet suitable for logging to a data file,
     * from an EventPacket that could be the result of filtering operations.
     @param packet the EventPacket
     @return a raw packet holding the device events
     */
    public AEPacketRaw reconstructRawPacket(EventPacket<E> packet);

    public byte getTypeFromAddress(int addr);

    public int getTypemask();


    public byte getTypeshift();

    public short getXFromAddress(int addr);

    public int getXmask();

    public byte getXshift();

    public short getYFromAddress(int addr);

    public int getYmask();

    public byte getYshift();

    public void setTypemask(final int typemask);

    public void setTypeshift(final byte typeshift);

    public void setXmask(final int xmask);

    public void setXshift(final byte xshift);

    public void setYmask(final int ymask);

    public void setYshift(final byte yshift);

    public boolean isFlipx() ;
    public void setFlipx(final boolean flipx) ;

    public boolean isFlipy() ;

    public void setFlipy(final boolean flipy) ;

    public boolean isFliptype() ;
    public void setFliptype(final boolean fliptype) ;

    public int getUsedBits();

    public boolean matchesAddress(int addr1, int addr2);

    public int getAddressFromCell(int x, int y, int type);


    public void setSubsamplingEnabled(boolean subsamplingEnabled);

    public boolean isSubsamplingEnabled();

    public int getSubsampleThresholdEventCount();

    public void setSubsampleThresholdEventCount(int subsampleThresholdEventCount);
    
       /**
     * Returns the AEChip that this extractor is used for.
     * @return the chip
     */
    public AEChip getChip();

    /**
     * Sets the AEChip that this extractor is used for. This method can be used to set the AEChip if the extractor for an AEChip is replaced with
     * a different one, for instance.
     * 
     * @param chip the chip to set
     */
    public void setChip(AEChip chip);

}
