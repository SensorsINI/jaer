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
 * implement this interface for interaction with a StreamCommand instance
 * <br /><br />
 * 
 * methods from this interface will be called asynchronously when new
 * messages arrive, when answers to commands are received or when errors
 * occur
 * <br /><br />
 * 
 * since these methods are called from within the serial event handling thread,
 * they <b>should return as fast as possible and refrain from calling any
 * i/o related methods in <code>StreamCommand</code> (such as <code>close</code>)
 * </b>
 * 
 * @author andstein
 * @see StreamCommand
 */
public interface StreamCommandListener {
    
    /**
     * this method is called when the answer to a command is received; the
     * answer might itself be an error message (depending on the implementation)
     * 
     * @param cmd that was sent before receiving this answer
     * @param answer received
     * @see StreamCommand#sendCommand
     * @see StreamCommandListener
     */
    public void answerReceived(String cmd,String answer);
    
    /**
     * when setting StreamCommand.setSynced(false), this method gets called
     * after each character received -- mainly for debugging purposes
     * 
     * @param c character received
     * @see StreamCommand#setSynced
     */
    public void unsyncedChar(char c);

    /**
     * is called when a new message is completely received
     *
     * @param message containing the implementation specific content; this 
     *      message will be overwritten by the i/o handling inside <code>StreamCommand</code>
     *      and should therefore be copied into a new <code>StreamCommandMessage</code>
     *      instance if used from another thread...
     * @see StreamCommandMessage
     * @see StreamCommandMessage#copy
     * @see StreamCommandListener
     */
    public void messageReceived(StreamCommandMessage message);

    /** received message stream is out of sync; happens when some bits are lost
        during the transmission; most probably, the data received in the last
        message was at least partly corrupted... @see #streamingError */
    public final static int STREAMING_OUT_OF_SYNC =0;
    /** happens if something went wrong when receiving bytes @see #streamingError */
    public final static int STREAMING_IO_EXCEPTION=1;
    /** if the message size receives is bigger than the message's buffer;
        probably due to a message <code>size</code> parameter that was corrupted
        during transmission; <code>StreamCommand</code> ignores the message and
        tries to re-sync automatically...
        @see #streamingError
        @see StreamCommandMessage#BUFSIZE */
    public final static int MESSAGE_TOO_LARGE     =2;
    /**
     * this method is called when the streamed messages get out of sync
     *
     * @param error indicating the type of the error
     * @param msg a descriptive error message
     * 
     * @see #STREAMING_OUT_OF_SYNC
     * @see #STREAMING_IO_EXCEPTION
     * @see #MESSAGE_TOO_LARGE
     */
    public void streamingError(int error, String msg);

    /**
     * this method gets called when a command has timed-out without getting
     * a valid answer
     *
     * @param cmd command that was sent
     * @see StreamCommand#CMD_TIMEOUT
     * @see StreamCommandMessage
     */
    public void commandTimeout(String cmd);
    
}
