package net.sf.jaer.biasgen;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */



import java.util.prefs.PreferenceChangeEvent;

import javax.swing.JComponent;

import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.IPot;
import net.sf.jaer.biasgen.Pot;
import net.sf.jaer.util.RemoteControlCommand;

/**
 * An IPot with full configurability. 
 * The sex (N/P), type (NORMAL/CASCODE), current level (LOW,NORNAL), enabled state (normal,
 * or weakly tied to rail), buffer bias current, and bias current can
 * all be digitally configured. First implemented on TCVS320, improved on DVS320.
 *
 * @author tobi
 */
public class ConfigurableIPotRev0 extends IPot {

   /** Operating current level, defines whether to use shifted-source current mirrors for small currents. */
    public enum CurrentLevel {Normal, Low}
    
    /** This enum determines whether low-current mode is enabled. In low-current mode, the bias uses
     * shifted n or p source regulated voltages.
     */
    protected CurrentLevel currentLevel=CurrentLevel.Normal;
    
    /** If enabled=true the bias operates normally, if enabled=false,
     * then the bias is disabled by being weakly tied to the appropriate rail (depending on bias sex, N or P). */
    public enum BiasEnabled {Enabled, Disabled}
    protected BiasEnabled biasEnabled=BiasEnabled.Enabled;
    
    
    /** Bit mask for flag bias enabled (normal operation) or disabled (tied weakly to rail) */
    protected static int enabledMask=0x80000000;
    
    /** Bit mask for flag low current mode enabled */
    protected static int lowCurrentModeMask=0x10000000;
    
    /** Bit mask for flag for bias sex (N or P) */
    protected static int sexMask=0x40000000;
    
    /** Bit mask for flag for bias type (normal or cascode) */
    protected static int typeMask=0x20000000;
    
    /** Bit mask for bias current value bits */
    protected static int bitValueMask=0x003fffff; // 22 bits at lsb position
    
    /** Bit mask for buffer bias bits */
    protected static int bufferBiasMask=0x0fc00000; // 6 bits just to left of bias value bits
    
    /** Number of bits used for bias value */
    protected static int numBiasBits=Integer.bitCount(bitValueMask);
    
    /** The number of bits specifying buffer bias currrent as fraction of master bias current */
    protected static int numBufferBiasBits=Integer.bitCount(bufferBiasMask);
    
    /** The bit value of the buffer bias current */
    protected int bufferBitValue=(1<<numBufferBiasBits)-1;
    
    /** Maximum buffer bias value (all bits on) */
    public static int maxBuffeBitValue=(1<<numBufferBiasBits)-1;
    
    /** Max bias bit value */
    public static int maxBitValue=(1<<numBiasBits)-1;
    
    protected final String SETI="seti_", SETIBUF="setibuf_", SETSEX="setsex_", SETTYPE="settype_", SETLEVEL="setlevel_", SETENABLED="setenabled_";

    public ConfigurableIPotRev0(Biasgen biasgen){
        super(biasgen);
     }
    
    /** Creates a new instance of IPot
     *@param biasgen
     *@param name
     *@param shiftRegisterNumber the position in the shift register, 0 based, starting on end from which bits are loaded
     *@param type (NORMAL, CASCODE)
     *@param sex Sex (N, P)
     * @param lowCurrentModeEnabled bias is normal (false) or in low current mode (true)
     * @param enabled bias is enabled (true) or weakly tied to rail (false)
     * @param bitValue initial bitValue
     * @param bufferBitValue buffer bias bit value
     *@param displayPosition position in GUI from top (logical order)
     *@param tooltipString a String to display to user of GUI telling them what the pots does
     */
    public ConfigurableIPotRev0(Biasgen biasgen, String name, int shiftRegisterNumber,
            Type type, Sex sex, boolean lowCurrentModeEnabled, boolean enabled,
            int bitValue, int bufferBitValue, int displayPosition, String tooltipString) {
        this(biasgen);
        numBits=numBiasBits; // overrides IPot value of 24
        setName(name);
        this.setType(type);
        this.setSex(sex);
        this.bitValue=bitValue;
        this.bufferBitValue=bufferBitValue;
        this.currentLevel=lowCurrentModeEnabled?CurrentLevel.Low:CurrentLevel.Normal;
        this.biasEnabled=enabled?BiasEnabled.Enabled:BiasEnabled.Disabled;
        this.displayPosition=displayPosition;
        this.tooltipString=tooltipString;
        this.shiftRegisterNumber=shiftRegisterNumber;
        loadPreferences(); // do this after name is set
      if(chip.getRemoteControl()!=null){
            chip.getRemoteControl().addCommandListener(this, String.format(SETI+"%s <bitvalue>",getName()), "Set the bitValue of IPot "+getName());
            chip.getRemoteControl().addCommandListener(this, String.format(SETIBUF+"%s <bitvalue>",getName()), "Set the bufferBitValue of IPot "+getName());
            chip.getRemoteControl().addCommandListener(this, String.format(SETSEX+"%s "+getEnumOptions(Sex.class),getName()), "Set the sex (N|P) of "+getName());
            chip.getRemoteControl().addCommandListener(this, String.format(SETTYPE+"%s "+getEnumOptions(Type.class),getName()), "Set the type of IPot "+getName());
            chip.getRemoteControl().addCommandListener(this, String.format(SETLEVEL+"%s "+getEnumOptions(CurrentLevel.class),getName()), "Set the current level of IPot "+getName());
           chip.getRemoteControl().addCommandListener(this, String.format(SETENABLED+"%s "+getEnumOptions(BiasEnabled.class),getName()), "Set the current level of IPot "+getName());
        }
//        System.out.println(this);
    }

    // returns e.g. <NORMAL|CASCODE>
    protected String getEnumOptions(final Class<? extends Enum> en){
        StringBuilder sb=new StringBuilder("<");
        Enum[] a=en.getEnumConstants();
        for(int i=0;i<a.length;i++){
            Enum e=a[i];
            sb.append(e.toString());
            if(i<a.length-1) sb.append("|");
        }
        sb.append(">");
        return sb.toString();
    }


    /** Processes custom RemoteControl commands
     */
    @Override
    public String processRemoteControlCommand(RemoteControlCommand command, String input) {

         String[] t=input.split("\\s");
        if(t.length<2){
            return "? "+this+"\n";
        }else{
            try{
                String s=t[0], a=t[1];
                if(s.startsWith(SETI)){
                    setBitValue(Integer.parseInt(a));
                }else if(s.startsWith(SETIBUF)){
                    setBufferBitValue(Integer.parseInt(a));
                }else if(s.startsWith(SETSEX)){
                    setSex(Sex.valueOf(a));
                }else if(s.startsWith(SETTYPE)){
                    setType(Type.valueOf(a));
                }else if(s.startsWith(SETLEVEL)){
                    setCurrentLevel(CurrentLevel.valueOf(a));
               }else if(s.startsWith(SETENABLED)){
                    setBiasEnabled(BiasEnabled.valueOf(a));
                }
                return this+"\n";
            }catch(NumberFormatException e){
                log.warning("Bad number format: "+input+" caused "+e);
                return e.toString()+"\n";
            }catch(IllegalArgumentException iae){
                log.warning("Bad command: "+input+" caused "+iae);
                return iae.toString()+"\n";
            }
        }
    }


    
    /** Builds the component used to control the IPot. This component is the user interface.
     * @return a JComponent that can be added to a GUI
     */
    @Override
    public JComponent makeGUIPotControl() {
        return new ConfigurableIPotGUIControl(this);
    }
    
    public int getBufferBitValue() {
        return bufferBitValue;
    }
    
    /** Set the buffer bias bit value
     * @param bufferBitValue the value which has maxBuffeBitValue as maximum and specifies fraction of master bias
     */
    public void setBufferBitValue(int bufferBitValue) {
        int oldBitValue=this.bufferBitValue;
        this.bufferBitValue=clippedBufferBitValue(bufferBitValue);
        if(bufferBitValue!=oldBitValue) {
            setChanged();
            notifyObservers(this);
        }
    }

    @Override
    public int getMaxBitValue() {
        return maxBitValue;
    }



    /** Change buffer bias current value by ratio, or at least by one bit value.
     @param ratio between new current and old value, e.g. 1.1f or 0.9f
     */
    public void changeBufferBiasByRatio(float ratio){
        int oldv=getBufferBitValue();
        int v=Math.round(oldv*ratio);
        if(v==oldv){
            v = v + (ratio >= 1 ? 1 : -1);
        }
        setBufferBitValue(v);
    }

    
    /** returns clipped value of potential new value for buffer bit value, constrained by limits of hardware.
     *
     * @param o candidate new value.
     * @return allowed value.
     */
    protected int clippedBufferBitValue(int o){
        int n=o; // new value
        if(o<0) n=0;
        if(o>maxBuffeBitValue) n=maxBuffeBitValue;
        return n;
    }
    
    
     /** Returns enabled via enum
     * 
     * @return appropriate BiasEnabled enum
     */
    public BiasEnabled getBiasEnabled() {
        return biasEnabled;
    }

    /** Sets bias enabled via enum
     * 
     * @param biasEnabled
     */
    public void setBiasEnabled(BiasEnabled biasEnabled) {
        setEnabled(biasEnabled==BiasEnabled.Enabled);
        setModified(true);
    }
    
    /** returns enabled via boolean
     * 
     * @return boolean true if enabled
     */
    public boolean isEnabled() {
        return getBiasEnabled()==BiasEnabled.Enabled;
    }
    
    /** sets enabled via boolean
     * 
     * @param enabled
     */
    public void setEnabled(boolean enabled) {
        if(enabled!=isEnabled()) setChanged();
        if(enabled) biasEnabled=BiasEnabled.Enabled; else biasEnabled=BiasEnabled.Disabled;
        notifyObservers();
    }
    
    public boolean isLowCurrentModeEnabled() {
        return currentLevel==CurrentLevel.Low;
    }

    /** Sets the enum currentLevel according to the flag lowCurrentModeEnabled.
     *
     * @param lowCurrentModeEnabled true to set CurrentMode.LowCurrent
     */
    public void setLowCurrentModeEnabled(boolean lowCurrentModeEnabled) {
        if(lowCurrentModeEnabled!=isLowCurrentModeEnabled()) setChanged();
        this.currentLevel = lowCurrentModeEnabled?CurrentLevel.Low:CurrentLevel.Normal;
        notifyObservers();
    }
    
    /** Computes the actual bit pattern to be sent to chip based on configuration values */
    protected int computeBinaryRepresentation(){
        int ret=0;
        if(isEnabled()) ret|=enabledMask;
        if(getType()==Pot.Type.NORMAL) ret|=typeMask;
        if(getSex()==Pot.Sex.N) ret|=sexMask;
        if(!isLowCurrentModeEnabled()) ret|=lowCurrentModeMask;
        int sh;
        sh=Integer.numberOfTrailingZeros(bufferBiasMask);
        ret|=bufferBitValue<<sh;
        sh=Integer.numberOfTrailingZeros(bitValueMask);
        ret|=bitValue<<sh;
        
     //   System.out.println(toString() + " byte repres " + Integer.toHexString(ret));
        
        return ret;
    }
    
    private byte[] bytes=null;
    
    @Override
    public byte[] getBinaryRepresentation() {
        int n=4;
        if(bytes==null) bytes=new byte[n];
        int val=computeBinaryRepresentation();
        int k=0;
        for(int i=bytes.length-1;i>=0;i--){
            bytes[k++]=(byte)(0xff&(val>>>(i*8)));
        }
        return bytes;
    }
    
    
    
    /** Returns the String key by which this pot is known in the Preferences. For IPot's, this
     * name is the Chip simple class name followed by IPot.<potName>, e.g. "Tmpdiff128.IPot.Pr".
     * @return preferences key
     */
    @Override
    protected String prefsKey() {
        return biasgen.getChip().getClass().getSimpleName() + ".ConfigurableIPot." + name;
    }
    
    static String KEY_BITVALUE="BitValue",
            KEY_BUFFER_BITVALUE="BufferBitValue",
            KEY_SEX="Sex",
            KEY_TYPE="Type",
            KEY_LOWCURRENT_ENABLED="LowCurrent",
            KEY_ENABLED="Enabled";
    static String SEP=".";
    
    /** stores as a preference the bit value */
    @Override
    public void storePreferences(){
        String s=prefsKey()+SEP;
        prefs.putInt(s+KEY_BITVALUE,getBitValue());
        prefs.putInt(s+KEY_BUFFER_BITVALUE,getBufferBitValue());
        prefs.putBoolean(s+KEY_ENABLED,isEnabled());
        prefs.putBoolean(s+KEY_LOWCURRENT_ENABLED,isLowCurrentModeEnabled());
        prefs.put(s+KEY_SEX,getSex().toString());
        prefs.put(s+KEY_TYPE,getType().toString());
        setModified(false);
    }
    
    /** loads and makes active the preference value. The name should be set before this is called. */
    @Override
    public void loadPreferences(){
        String s=prefsKey()+SEP;
        int v=prefs.getInt(s+KEY_BITVALUE,0);
        setBitValue(v);
        int bv=prefs.getInt(s+KEY_BUFFER_BITVALUE,ConfigurableIPotRev0.maxBuffeBitValue);
        setBufferBitValue(bv);
        setEnabled(prefs.getBoolean(s+KEY_ENABLED, true));
        setLowCurrentModeEnabled(prefs.getBoolean(s+KEY_LOWCURRENT_ENABLED, false));
        setSex(Pot.Sex.valueOf(prefs.get(s+KEY_SEX, Sex.N.toString())));
        setType(Pot.Type.valueOf(prefs.get(s+KEY_TYPE,Type.NORMAL.toString())));
        setModified(false);
    }

    @Override
    public void preferenceChange(PreferenceChangeEvent e) {
        // TODO we get pref change events here but by this time the new values have already been set and there is no change in value so the GUI elements are not updated
        try {
            String base = prefsKey() + SEP;
            String key = e.getKey();
            if(!key.startsWith(base)) return;
            String val = e.getNewValue();
//            log.info("key="+key+" value="+val);
            if (key.equals(base + KEY_BITVALUE)) {
                if(getBitValue()!=Integer.parseInt(val)){
                    log.info("bit value change from preferences");
                }
                setBitValue(Integer.parseInt(val));
            } else if (key.equals(base + KEY_BUFFER_BITVALUE)) {
                setBufferBitValue(Integer.parseInt(val));
            } else if (key.equals(base + KEY_ENABLED)) {
                setEnabled(Boolean.parseBoolean(val));
            } else if (key.equals(base + KEY_LOWCURRENT_ENABLED)) {
                setLowCurrentModeEnabled(Boolean.parseBoolean(val));
            } else if (key.equals(base + KEY_SEX)) {
                setSex(Pot.Sex.valueOf(val));
            } else if (key.equals(base + KEY_TYPE)) {
                setType(Pot.Type.valueOf(val));
            }
        } catch (Exception ex) {
            log.warning("while responding to preference change event " + e + ", caught " + ex.toString());
        }
    }

    /** returns the preference value of the bias current.
     */
    @Override
    public int getPreferedBitValue(){
        String key=prefsKey()+SEP+KEY_BITVALUE;
        int v=prefs.getInt(key,0);
        return v;
    }
    
    /** sets the bit value based on desired current and {@link #masterbias} current.
     * Observers are notified if value changes.
     *@param current in amps
     *@return actual float value of current after resolution clipping.
     */
    public float setBufferCurrent(float current){
        float im=masterbias.getCurrent();
        float r=current/im;
        setBufferBitValue(Math.round(r*maxBuffeBitValue));
        return getBufferCurrent();
    }
    
    /** Computes the estimated current based on the bit value for the current splitter and the {@link #masterbias}
     * @return current in amps */
    public float getBufferCurrent(){
        float im=masterbias.getCurrent();
        float i=im*getBufferBitValue()/maxBuffeBitValue;
        return i;
    }
    
    public String toString(){
        return super.toString()+" Sex="+getSex()+" Type="+getType()+" enabled="+isEnabled()+" lowCurrentModeEnabled="+isLowCurrentModeEnabled()+" bufferBitValue="+bufferBitValue;
    }
    
    public CurrentLevel getCurrentLevel() {
        return currentLevel;
    }
    
    /** Sets whether this is a normal type or low current bias which uses shifted source */
    public void setCurrentLevel(CurrentLevel currentLevel) {
        if(currentLevel==null) return;
        if(currentLevel!=this.currentLevel) setChanged();
        this.currentLevel = currentLevel;
        notifyObservers();
    }
    
    /** Overrides super of type (NORNAL or CASCODE) to call observers */
    @Override 
    public void setType(Type type) {
        if(type==null) return;
        if(type!=this.type) setChanged();
        this.type = type;
        notifyObservers();
    }
    
    /** Overrides super of setSex (N or P) to call observers */
    @Override
    public void setSex(Sex sex) {
        if(sex==null) return;
        if(sex!=this.sex) setChanged();
        this.sex = sex;
        notifyObservers();
    }

    /** Returns true if all parameters are identical, otherwise false.
     * 
     * @param obj another ConfigurableIPotRev0
     * @return true if all parameters are identical, otherwise false.
     */
    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof ConfigurableIPotRev0)) return false;
        ConfigurableIPotRev0 other=(ConfigurableIPotRev0)obj;
        if(!getName().equals(other.getName())) return false;
        if(getBitValue()!=other.getBitValue()) return false;
        if(getBufferBitValue()!=other.getBufferBitValue()) return false;
        if(getSex()!=other.getSex()) return false;
        if(getType()!=other.getType()) return false;
        if(getCurrentLevel()!=other.getCurrentLevel()) return false;
        if(isEnabled()!=other.isEnabled()) return false;
        return true;
    }


    
}
