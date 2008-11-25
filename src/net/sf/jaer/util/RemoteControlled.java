/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.jaer.util;

/**
 * A RemoteContolled object implements this interface to process commands.
 * @author tobi
 */
public interface RemoteControlled {
    
    /** Called when remote control recieved for this RemoteControlled.
     * 
     * @param command the received command that was parsed as being the type sent.
     * @param input the input line which starts with the command token.
     * @return an optional response to the command which can be null.
     */
    public String processCommand(RemoteControlCommand command, String input);

}
