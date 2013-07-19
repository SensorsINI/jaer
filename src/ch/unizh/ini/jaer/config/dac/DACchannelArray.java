package ch.unizh.ini.jaer.config.dac;

import java.util.ArrayList;
import java.util.Iterator;

import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.BiasgenPreferences;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**  A collection of channel's that belong to a common device, e.g. a chip or a board. 
 This class allows common operations on the channels and groups the channels together. 
 @author tobi
 */
public class DACchannelArray implements BiasgenPreferences {
    
    /** use for property updates on channels */
//    transient public PropertyChangeSupport support=new PropertyChangeSupport(this);
    transient Biasgen biasgen;
    protected ArrayList<DACchannel> channels=new ArrayList<DACchannel>();
    
    /** Creates a new instance of IchannelArray */
    public DACchannelArray(Biasgen biasgen) {
        this.biasgen=biasgen;
    }
    
    /** store the present values as the preferred values */
    private void storePreferedValues(){
        for(DACchannel p:channels){
            p.storePreferences();
        }
    }
    
    /** Loads  the stored preferred values. */
    private void loadPreferedValues(){
        for(DACchannel p:channels){
            p.loadPreferences();
        }
    }
    
    @Override
    public String toString(){
        return "channelArray with "+channels.size()+" channels";
    }
    
    @Override
    public void exportPreferences(java.io.OutputStream os) {
    }
    
    @Override
    public void importPreferences(java.io.InputStream is) {
    }

    /** Loads preferences (preferred values). */
    @Override
    public void loadPreferences() {
        loadPreferedValues();
    }
    
    /** Flushes the preferred values to the Preferences */
    @Override
    public void storePreferences() {
        storePreferedValues();
    }
    
    /** add a channel to the end of the list. 
     The biasgen is also added as an observer for changes in the channel. 
     * @param channel an channel
     @return true
     */
    public boolean addChannel(DACchannel channel){
        channels.add(channel);
        channel.addObserver(biasgen);
        return true;
    }
    
    /** Get an channel by name.
     * @param name name of channel as assigned in channel
     *@return the channel, or null if there isn't one named that
     */
    public DACchannel getchannelByName(String name){
        DACchannel p=null;
        for(DACchannel channel:channels){
            String channelName=channel.getName();
            if(channelName.equals(name)) p=channel;
        }
        return p;
    }
    
    /** sets all channels to the same value. Does not notify observers but does call biasgen.resend() after all channels are changed.
     * @param val the bitValue
     */
    public void setAllToBitValue(int val) {
        if(biasgen!=null) biasgen.startBatchEdit();
        for(DACchannel p:channels){
            p.setBitValue(val);
        }
        if(biasgen!=null) {
            try{
                biasgen.endBatchEdit();
            } catch(HardwareInterfaceException e){
                System.err.println("channelArray.setAllToBitValue(): "+e.getMessage());
            }
        }
    }
    
    /** Get an channel by number in channelArray. For on-chip biasgen design kit biases, first entry is last one in shift register.
     * @param number name of channel as assigned in channel
     *@return the channel, or null if there isn't one at that array position.
     */
    public DACchannel getchannelByNumber(int number){
        if(number<0 || number>channels.size()) return null;
        return (DACchannel)(channels.get(number));
    }
    
    /** gets the number of ichannels in this array
     * @return the number of channels
     */
    public int getNumChannels(){
        return channels.size();
    }
 
    public ArrayList<DACchannel> getChannels() {
        return this.channels;
    }

    public void setchannels(final ArrayList<DACchannel> channels) {
        this.channels = channels;
    }

    public Iterator getChannelIterator(){
        return channels.iterator();
    }
}
