/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.util;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.logging.Logger;
/**
 * Remote control via a datagram connection. Listeners add themselves with a command string and list of arguments.
 * Remote control builds a parser and returns calls the appropriate listener. The listener can access the arguments by name.
 * A remote client can connect to the UDP port and obtain a list of commands with the help command.
 * Using, for example, netcat (or nc.exe on Windows), a session might look as follows (the -u means use UDP connection)
 * <pre>
 * >nc -u localhost 8995

> ?
? is unknown command - type help for help
> help
Available commands are
setifoll bitvalue - Set the bitValue of IPot foll
setidiffoff bitvalue - Set the bitValue of IPot diffOff
seticas bitvalue - Set the bitValue of IPot cas
setidiffon bitvalue - Set the bitValue of IPot diffOn
setirefr bitvalue - Set the bitValue of IPot refr
setipux bitvalue - Set the bitValue of IPot puX
setipuy bitvalue - Set the bitValue of IPot puY
setireqpd bitvalue - Set the bitValue of IPot reqPd
setireq bitvalue - Set the bitValue of IPot req
setiinjgnd bitvalue - Set the bitValue of IPot injGnd
setidiff bitvalue - Set the bitValue of IPot diff
setipr bitvalue - Set the bitValue of IPot Pr
>
 *
 * </pre>
 *
 * Commands are added to an object as shown next; getRemoteControl accesses in this example the Chip's built-in RemoteControl.
 * This object implements RemoteControlled. It adds a single command "setbufferbias".
 * <pre>
 *                 if (getRemoteControl() != null) {
getRemoteControl().addCommandListener(this, "setbufferbias bitvalue", "Sets the buffer bias value");
}
</pre>
 * This RemoteControlled implements the processRemoteControlCommand method like this; processRemoteControlCommand returns a String which contains the results.
 * <pre>
 *            public String processRemoteControlCommand(RemoteControlCommand command, String input) {
String[] tok = input.split("\\s");
if (tok.length < 2) {
return "bufferbias " + getValue()+"\n";
} else {
try {
int val = Integer.parseInt(tok[1]);
setValue(val);
} catch (NumberFormatException e) {
return "?\n";
}

}
return "bufferbias " + getValue()+"\n";
}
 * </pre>
 *
 *
 *
 * @author tobi
 */
public class RemoteControl /* implements RemoteControlled */{
    /** Max length of remote control packet in bytes. */
    public static final int MAX_COMMAND_LENGTH_BYTES = 8196;
    private static Logger log = Logger.getLogger("RemoteControl");
    /** The default UDP local port for the default constructor. */
    public static final int PORT_DEFAULT = 8995;
    private int port;
    private HashMap<String,RemoteControlled> controlledMap = new HashMap<String,RemoteControlled>();
    private HashMap<String,RemoteControlCommand> cmdMap = new HashMap<String,RemoteControlCommand>();
    private HashMap<String,String> descriptionMap = new HashMap<String,String>();
    private DatagramSocket datagramSocket;
    private final String HELP = "help";
    public final String PROMPT = "> ";
    private boolean promptEnabled = true;
    private Thread T;
    private static final int MAX_WARNINGS=2;
    private int warningCount=0;

    /** Makes a new RemoteControl on the default port */
    public RemoteControl () throws SocketException{
        this(PORT_DEFAULT);
    }

    /** Creates a new instance.
     *
     * @param port the UDP port number this RemoteControl listens on.
     *
     */
    public RemoteControl (int port) throws SocketException{
        this.port = port;
        try{
            datagramSocket = new DatagramSocket(port);
        } catch ( SocketException e ){
            throw new SocketException(e + " on port " + port);
        }
        log.info("Constructed uninitialized and empty " + this);
        ( T = new RemoteControlDatagramSocketThread() ).start();
    }

    @Override
	public String toString (){
        return "RemoteControl on port=" + port;
    }

    public void close (){
        datagramSocket.close();
        try{
            T.join(1000);
        } catch ( InterruptedException ex ){
            log.warning("join for waiting for socket close interrupted");
        }
    }


    // TODO add removeCommandListener method

    /** Objects that want to receive commands should add themselves here with a
     * command string and command description (for showing help).
     *
     * @param remoteControlled the remote controlled object.
     * @param cmd a string such as "setipr bitvalue". "setipr" is the command
     * and the RemoteControlled is responsible for parsing the rest of the line.
     * @param description for showing help.
     */
    public void addCommandListener (RemoteControlled remoteControlled,String cmd,String description){
        RemoteControlCommand command = new RemoteControlCommand(cmd,description);
        String cmdKey = command.getCmdName();
        if ( cmdMap.containsKey(cmdKey) && (warningCount++<MAX_WARNINGS)){
            log.warning("remote control commands already contains command " + cmdKey + ", replacing existing command with " + cmd + ": " + description);
            if(warningCount==MAX_WARNINGS) {
				log.warning("suppressing further warnings about replacing commands");
			}
        }
        cmdMap.put(cmdKey,command);
        controlledMap.put(cmdKey,remoteControlled);
        descriptionMap.put(cmdKey,description);
    }

    private String getHelp (){
        StringBuffer s = new StringBuffer("Available commands are\n");
        Map<String,RemoteControlled> sortedMap = new TreeMap(cmdMap);
        for ( Entry e:sortedMap.entrySet() ){
            RemoteControlCommand c = (RemoteControlCommand)e.getValue();
            s.append(String.format("%s - %s\n",c.getCmd(),c.getDescription()));
        }
        return s.toString();
    }

    /**
     * @return the port
     */
    public int getPort (){
        return port;
    }
    private class RemoteControlDatagramSocketThread extends Thread{
        DatagramPacket packet;
        private String CHARSET="UTF8";

        RemoteControlDatagramSocketThread (){
            setName("RemoteControlDatagramSocketThread");
        }

        @Override
        public void run (){
            while ( true ){
                try{
                    packet = new DatagramPacket(new byte[ MAX_COMMAND_LENGTH_BYTES ],MAX_COMMAND_LENGTH_BYTES);
                    datagramSocket.receive(packet);
                    InputStream is = new ByteArrayInputStream(packet.getData(),0,packet.getLength());
                    byte[] b=new byte[packet.getLength()];
                    is.read(b);
//                    ByteArrayOutputStream bos=new ByteArrayOutputStream(b.length+10);
//                    boolean showedWarning=false;
//                    StringBuilder deb1=new StringBuilder(), deb2=new StringBuilder();
//                    for(int i=0;i<b.length;i++){
//
//                        deb1.append((char)b[i]+"    ");
//                        deb2.append(HexString.toString(b[i])+" ");
//                        // all kinds of machinations here to make sure to warn the sender that they have possibly sent a java string escape
//                        // sequence as part of their command!
////                        if((0xff&b[i])==0x5c){ // backslash
////                            bos.write(b[i]);
////                            bos.write(b[i]);
////                            if(!showedWarning){
////                            StringBuilder sb=new StringBuilder("WARNING: Received command line contains at least one \"\\\" at position "+i+" - this backslash could be interpreted as escape sequence; replace with \"/\" for file separation:\n"+new String(b));
////                            for(int j=0;j<i;j++){
////                                sb.append(" ");
////                            }
////                            sb.append("^");
////                            log.warning(sb.toString());
////                            echo(sb.toString()+"\n");
////                            showedWarning=true;
////                            }
////                        }else{
//                            bos.write(b[i]);
////                        }
//                    }
//                    System.out.println(deb1.toString());
//                    System.out.println(deb2.toString());
//                    String line=new String(bos.toByteArray());
                    String line=new String(b);  // decode using default encoding
                    if(line.endsWith("\n")){
                        line=line.substring(0,line.length()-1);
                    }
//                    char[] c=new char[b.length];
//                    for(int i=0;i<b.length;i++){
//                        c[i]=(char)b[i];
//                    }

//                    String line=new String(c);
//                    StringBuilder sb=new StringBuilder();
//                    for(int i=0;i<b.length;i++){
//                        sb.append((char)b[i]);
//                    }
//                    String line = sb.toString(); //new String(b,CHARSET); // .toLowerCase();
//                    line.replaceAll("\n","\\n");
                    log.info("received line \""+line+"\""); // debug
                    parseAndDispatchCommand(line);

                } catch ( SocketException e ){
                    log.info("closed " + this);
                    break;
                } catch ( IOException ex ){
                    log.warning(ex.toString());
                    break;
                }

            }
        }

        private void parseAndDispatchCommand (String line) throws IOException{
            if ( (line == null) || (line.length() == 0) ){
                echo(PROMPT);
                return;
            }
            if ( line.startsWith(HELP) ){
                String help = getHelp();
                echo(help + PROMPT);
                log.info(help);
            } else{
                String[] tokens = line.split("\\s");
                String cmdTok = tokens[0];
                if ( (cmdTok == null) || (cmdTok.length() == 0) ){
                    return;
                }
                if ( !cmdMap.containsKey(cmdTok) ){
                    String s=String.format("%s is unknown command - type %s for help\n%s",line,HELP,PROMPT);
                    echo(s);
                    log.warning(line+" is unknown command - send \""+HELP+"\" for help");
                    return;
                }
                RemoteControlled controlled = controlledMap.get(cmdTok);
                String response = controlled.processRemoteControlCommand(cmdMap.get(cmdTok),line);
                if ( response != null ){
                    if ( !( response.endsWith("\n") ) ){
                        response = response + "\n";
                    }
                    echo(response + PROMPT);
                } else{
                    if ( promptEnabled ){
                        echo(PROMPT);
                    }
                }
            }
        }

        private void echo (String s) throws IOException{
            if ( (s == null) || (s.length() == 0) ){
                return;
            }
            byte[] b = s.getBytes();
            DatagramPacket echogram = new DatagramPacket(b,b.length);
            echogram.setSocketAddress(new InetSocketAddress(packet.getAddress(),packet.getPort()));
            datagramSocket.send(echogram);
        }
    }
    // debug

    public static void main (String[] args) throws SocketException{
        RemoteControl remoteControl = new RemoteControl(10000);
        CommandProcessor processor = new CommandProcessor();
        remoteControl.addCommandListener(processor,"startlogging","start logging doit");
        remoteControl.addCommandListener(processor,"doit thismanytimes","i am doit");
        remoteControl.addCommandListener(processor,"dd","i am dd");
        remoteControl.addCommandListener(processor,"dd","i am dd also");
        remoteControl.addCommandListener(new RemoteControlled(){
            @Override
			public String processRemoteControlCommand (RemoteControlCommand command,String input){
                return "got bogus";
            }
        },"bogus","bogus description");
    }
}
class CommandProcessor implements RemoteControlled{
    @Override
	public String processRemoteControlCommand (RemoteControlCommand command,String line){
        String[] tokens = line.split("\\s");
        try{
            if ( command.getCmdName().equals("doit") ){
                if ( tokens.length < 2 ){
                    return "not enough arguments\n";
                }
                int val = Integer.parseInt(tokens[1]);
            } else if ( command.getCmdName().equals("dd") ){
                return "got dd\n";
            } else {
                StringBuilder sb=new StringBuilder();
                sb.append("got cmd=\"" + command.getCmd()+"\" line=\""+line+"\" tokens=");
                for(String s:tokens){
                    sb.append("\n"+s);
                }
                return sb.toString();
            }
        } catch ( Exception e ){
            return e.toString() + "\n";
        }
        return null;
    }
}