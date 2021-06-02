/*
Copyright June 13, 2011 Andreas Steiner, Inst. of Neuroinformatics, UNI-ETH Zurich

This file is part of dsPICserial.

dsPICserial is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

dsPICserial is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with dsPICserial.  If not, see <http://www.gnu.org/licenses/>.
*/


package ch.unizh.ini.jaer.projects.dspic.serial;

/**
 *
 * @author andstein
 */
public class RetinaMessage extends StreamCommandMessage
{
    
    public final static int MSG_FRAME_BYTES		=0x0001;
    public final static int MSG_FRAME_WORDS		=0x0003;
    public final static int MSG_FRAME_WORDS_DXDY	=0x0004;
    public final static int MSG_DXDY            	=0x0005;
    
    public final static int WIDTH  = 20;
    public final static int HEIGHT = 20;
    
    public RetinaMessage(StreamCommandMessage msg) {
        copy(msg);
    }
    
    public static boolean canParse(StreamCommandMessage msg) {
        return  msg.getType() == MSG_FRAME_BYTES ||
                msg.getType() == MSG_FRAME_WORDS ||
                msg.getType() == MSG_FRAME_WORDS_DXDY ||
                msg.getType() == MSG_DXDY;
    }
    
    public int getPixelAt(int x,int y) {
        if (getType() == MSG_FRAME_BYTES)
            return getByteAt(y*WIDTH + x);
        else if (getType() == MSG_FRAME_WORDS)
            return getUnsignedWordAt(y*WIDTH + x);
        else if (getType() == MSG_FRAME_WORDS_DXDY)
            return getUnsignedWordAt(y*WIDTH + x);
        else
            return 0;
    }
    
    public float getDx() {
        if (getType() == MSG_DXDY) {
            if (getUnsignedWordAt(0) == 0xFFFF) return -1;
            else return getSignedFloatAt(0);
        } else if (getType() == MSG_FRAME_WORDS_DXDY) {
            if (getUnsignedWordAt(WIDTH*HEIGHT) == 0xFFFF) return -1;
            else return getSignedFloatAt(0);
        } else 
            return 0;
    }
    
    public float getDy() {
        if (getType() == MSG_DXDY) {
            if (getUnsignedWordAt(0) == 0xFFFF) return -1;
            else return getSignedFloatAt(1);
        } else if (getType() == MSG_FRAME_WORDS_DXDY) {
            if (getUnsignedWordAt(0) == 0xFFFF) return -1;
            else return getSignedFloatAt(0);
        } else
            return 0;
    }
    
    public boolean includesErrorInformation() {
        if (getType() == MSG_FRAME_WORDS_DXDY &&
                getUnsignedWordAt(WIDTH*HEIGHT +1) == 0xFFFF)
            return true;
        if (getType() == MSG_DXDY &&
                getUnsignedWordAt(0) == 0xFFFF)
            return true;
        return false;
    }
    
    public String getErrorInformation() {
        if (!includesErrorInformation())
            return "(no error)";
        int code= getUnsignedWordAt(1);
        if (getType() == MSG_FRAME_WORDS_DXDY)
            code= getUnsignedWordAt(WIDTH*HEIGHT +1);
        return String.format("error code = 0x%4X",code);
    }
    
}
