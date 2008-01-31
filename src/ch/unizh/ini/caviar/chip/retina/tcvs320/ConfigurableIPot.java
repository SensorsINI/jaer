package ch.unizh.ini.caviar.chip.retina.tcvs320;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */



import ch.unizh.ini.caviar.biasgen.*;
import ch.unizh.ini.caviar.biasgen.IPot;
import javax.swing.JComponent;

/**
 * An IPot with full configurability. The sex (N/P), type (NORMAL/CASCODE), current level (LOW,NORNAL), enabled state (normal, or weakly tied to rail), buffer bias current, and bias current can
 * all be digitally configured. First implemented on TCVS320.
 *
 * @author tobi
 */
public class ConfigurableIPot extends IPot {
    
    /** Operating current level, defines whether to use shifted-source current mirrors for small currents. */
    public enum CurrentLevel {Normal, Low}
    private CurrentLevel currentLevel=CurrentLevel.Normal;
    
    /** If enabled=true the bias operates normally, if enabled=false,
     * then the bias is disabled by being weakly tied to the appropriate rail (depending on bias sex, N or P). */
    public enum BiasEnabled {Enabled, Disabled}
    private BiasEnabled biasEnabled=BiasEnabled.Enabled;
    
    
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
    public static int maxBufferValue=(1<<numBufferBiasBits)-1;
    
    /** Max bias bit value */
    public static int maxBitValue=(1<<numBiasBits)-1;
    
    public ConfigurableIPot(Biasgen biasgen){
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
    public ConfigurableIPot(Biasgen biasgen, String name, int shiftRegisterNumber,
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
//        System.out.println(this);
    }
    
    /** Builds the component used to control the IPot. This component is the user interface.
     * @return a JComponent that can be added to a GUI
     * @param frame the BiasgenFrame in which it sits
     */
    @Override
    public JComponent makeGUIPotControl(BiasgenFrame frame) {
        return new ConfigurableIPotGUIControl(this,frame);
    }
    
//    public int getNumBufferBiasBits() {
//        return numBufferBiasBits;
//    }
//
//    public void setNumBufferBiasBits(int numBufferBiasBits) {
//        this.numBufferBiasBits = numBufferBiasBits;
//    }
    
    public int getBufferBitValue() {
        return bufferBitValue;
    }
    
    /** Set the buffer bias bit value
     * @param bufferBitValue the value which has maxBufferValue as maximum and specifies fraction of master bias
     */
    public void setBufferBitValue(int bufferBitValue) {
        int oldBitValue=this.bufferBitValue;
        this.bufferBitValue=clipBuffer(bufferBitValue);
        if(bufferBitValue!=oldBitValue) {
            setChanged();
            notifyObservers(this);
        }
    }
    
    // returns clipped value of potential new value
    private int clipBuffer(int o){
        int n=o; // new value
        if(o<0) n=0;
        if(o>maxBufferValue) n=maxBufferValue;
        return n;
    }
    
    
    
    public boolean isEnabled() {
        return biasEnabled==BiasEnabled.Enabled;
    }
    
    public void setEnabled(boolean enabled) {
        if(enabled) biasEnabled=BiasEnabled.Enabled; else biasEnabled=BiasEnabled.Disabled;
    }
    
    public boolean isLowCurrentModeEnabled() {
        return currentLevel==CurrentLevel.Low;
    }
    
    public void setLowCurrentModeEnabled(boolean lowCurrentModeEnabled) {
        this.currentLevel = lowCurrentModeEnabled?CurrentLevel.Low:CurrentLevel.Normal;
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
        
        System.out.println(toString() + " byte repres " + Integer.toHexString(ret));
        
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
    }
    
    /** loads and makes active the preference value. The name should be set before this is called. */
    @Override
    public void loadPreferences(){
        String s=prefsKey()+SEP;
        bitValue=prefs.getInt(s+KEY_BITVALUE,0);
        bufferBitValue=prefs.getInt(s+KEY_BUFFER_BITVALUE,ConfigurableIPot.maxBufferValue);
        setEnabled(prefs.getBoolean(s+KEY_ENABLED, true));
        setLowCurrentModeEnabled(prefs.getBoolean(s+KEY_LOWCURRENT_ENABLED, false));
        setSex(Pot.Sex.valueOf(prefs.get(s+KEY_SEX, Sex.N.toString())));
        setType(Pot.Type.valueOf(prefs.get(s+KEY_TYPE,Type.NORMAL.toString())));
    }
    
    /** returns the preference value */
    @Override
    public int getPreferedBitValue(){
        String key=prefsKey();
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
        setBufferBitValue(Math.round(r*maxBufferValue));
        return getBufferCurrent();
    }
    
    /** Computes the estimated current based on the bit value for the current splitter and the {@link #masterbias}
     * @return current in amps */
    public float getBufferCurrent(){
        float im=masterbias.getCurrent();
        float i=im*getBufferBitValue()/maxBufferValue;
        return i;
    }
    
    public String toString(){
        return super.toString()+" Sex="+getSex()+" Type="+getType()+" enabled="+isEnabled()+" lowCurrentModeEnabled="+isLowCurrentModeEnabled()+" bufferBitValue="+bufferBitValue;
    }
    
    public CurrentLevel getCurrentLevel() {
        return currentLevel;
    }
    
    public void setCurrentLevel(CurrentLevel currentLevel) {
        this.currentLevel = currentLevel;
    }
    
    public BiasEnabled getBiasEnabled() {
        return biasEnabled;
    }
    
    public void setBiasEnabled(BiasEnabled biasEnabled) {
        this.biasEnabled = biasEnabled;
    }
    
}
