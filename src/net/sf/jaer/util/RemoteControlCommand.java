/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.jaer.util;

/**
 * A single command description.
 * 
 * @author tobi
 */
public class RemoteControlCommand {
    private String cmdName;
    private String cmd;
    private String description;
    private String[] tokens;
    
    /** Creates a new instance.
     * 
     * @param cmd the specifier. The first token is the command, 
     * e.g. "set". The following tokens are arguments that can 
     * be retrieved by name from an input command. 
     * For example, "set value enabled" specifies a command "set" 
     * with two arguments "value" and "enabled".
     * @param description help for the command.
     */
    public RemoteControlCommand(String cmd, String description){
        this.cmd=cmd;
       if(cmd==null || cmd.length()==0) throw new Error("tried to add null or empty commad");
        tokens=cmd.split("\\s");
        cmdName=tokens[0];
        this.description=description;
    }
    
    /** Returns "Command "+cmdName
     * 
     * @return  "Command "+cmdName
     */
    public String toString(){
        return "Command "+cmdName;
    }

    /** Returns initial token of command
     * 
     * @return Initial token of command
     */
    public String getCmdName() {
        return cmdName;
    }

    public void setCmdName(String cmdName) {
        this.cmdName = cmdName;
    }

    /** Returns full command, which could have several tokens
     * 
     * @return Initial token of command
     */   
    public String getCmd() {
        return cmd;
    }

    public void setCmd(String cmd) {
        this.cmd = cmd;
    }

    /** Returns description of command.
     * 
     * @return description 
     */
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /** Returns pre-parsed array of tokens
     * 
     * @return prebuilt array of tokens 
     */
    public String[] getTokens() {
        return tokens;
    }

    public void setTokens(String[] tokens) {
        this.tokens = tokens;
    }
    

}
