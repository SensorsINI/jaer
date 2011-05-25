/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cl.eye;

import net.sf.jaer.Description;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.EventExtractor2D;
import net.sf.jaer.event.EventPacket;

/**
 * A behavioral model of an AE retina using the code laboratories interface to a PS eye camera.
 * 
 * @author tobi
 */
@Description("AE retina using the PS eye camera")
public class CLRetina extends AEChip{

    public CLRetina() {
        setSizeX(320);
        setSizeY(240);
        setEventExtractor(new EventExtractor());
    }
    
    public class EventExtractor implements EventExtractor2D{

        @Override
        public EventPacket extractPacket(AEPacketRaw events) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void extractPacket(AEPacketRaw raw, EventPacket cooked) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public AEPacketRaw reconstructRawPacket(EventPacket packet) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public byte getTypeFromAddress(int addr) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public int getTypemask() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public byte getTypeshift() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public short getXFromAddress(int addr) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public int getXmask() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public byte getXshift() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public short getYFromAddress(int addr) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public int getYmask() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public byte getYshift() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void setTypemask(int typemask) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void setTypeshift(byte typeshift) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void setXmask(int xmask) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void setXshift(byte xshift) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void setYmask(int ymask) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void setYshift(byte yshift) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public boolean isFlipx() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void setFlipx(boolean flipx) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public boolean isFlipy() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void setFlipy(boolean flipy) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public boolean isFliptype() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void setFliptype(boolean fliptype) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public int getUsedBits() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public boolean matchesAddress(int addr1, int addr2) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public int getAddressFromCell(int x, int y, int type) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void setSubsamplingEnabled(boolean subsamplingEnabled) {
            
        }

        @Override
        public boolean isSubsamplingEnabled() {
            return false;
        }

        @Override
        public int getSubsampleThresholdEventCount() {
            return 0;
        }

        @Override
        public void setSubsampleThresholdEventCount(int subsampleThresholdEventCount) {
        }
        
    }
    
}
