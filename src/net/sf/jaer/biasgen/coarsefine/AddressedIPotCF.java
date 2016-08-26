package net.sf.jaer.biasgen.coarsefine;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */



import java.util.prefs.PreferenceChangeEvent;

import javax.swing.JComponent;

import net.sf.jaer.biasgen.AddressedIPot;
import net.sf.jaer.biasgen.Biasgen;
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
public class AddressedIPotCF extends AddressedIPot {

    /** The nominal (designed for) external resistor on the master bias */
    public static final float RX=100e3f;

    /** Estimation of the master bias with 100kOhm external resistor (designed value); 389nA */
    public static final float ACTUAL_MASTER_BIAS_CURRENT = 0.000000389f;

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

    /** The nominal ratio of coarse current between each coarse bias step change. */
    public static final float RATIO_COARSE_CURRENT_STEP=8f;

    /** Bit mask for flag bias enabled (normal operation) or disabled (tied weakly to rail) */
    protected static int enabledMask=0x0001;

    /** Bit mask for flag for bias sex (N or P) */
    protected static int sexMask=0x0002;

    /** Bit mask for flag for bias type (normal or cascode) */
    protected static int typeMask=0x0004;

    /** Bit mask for flag low current mode enabled */
    protected static int lowCurrentModeMask=0x0008;

    /** Bit mask for fine bias current value bits */
    protected static int bitFineMask=0x0FF0; // 8 bits

    /** Bit mask for coarse bias current value bits */
    protected static int bitCoarseMask=0x7000; // 3  bits

    /** Number of bits used for fine bias value */
    protected static int numFineBits=Integer.bitCount(bitFineMask);

    /** the current fine value of the ipot in bits loaded into the shift register */
    protected int fineBitValue = 0;

    /** Max fine bias bit value */
    public static int maxFineBitValue=(1<<numFineBits)-1;

    /** Number of bits used for coarse bias value */
    protected static int numCoarseBits=Integer.bitCount(bitCoarseMask);

    /** the current coarse value of the ipot in bits loaded into the shift register */
    protected int coarseBitValue = 0;

    /** Max bias bit value */
    public static int maxCoarseBitValue=(1<<numCoarseBits)-1;

    /** Number of bits used for bias value */
    protected static int numBiasBits=Integer.bitCount(bitFineMask)+Integer.bitCount(bitCoarseMask);

    /** Max bias bit value */
    public static int maxBitValue=(1<<numBiasBits)-1;

    protected final String SETIC="setic_", SETIF="setif_", SETSEX="setsex_", SETTYPE="settype_", SETLEVEL="setlevel_", SETENABLED="setenabled_";

    public AddressedIPotCF(Biasgen biasgen){
        super(biasgen);
     }

    /** Creates a new instance of IPot
     *@param biasgen
     *@param name
     *@param address the position in the shift register, 0 based, starting on end from which bits are loaded
     *@param type (NORMAL, CASCODE)
     *@param sex Sex (N, P)
     * @param lowCurrentModeEnabled bias is normal (false) or in low current mode (true)
     * @param enabled bias is enabled (true) or weakly tied to rail (false)
     * @param bitValue initial bitValue
     * @param bufferBitValue buffer bias bit value
     *@param displayPosition position in GUI from top (logical order)
     *@param tooltipString a String to display to user of GUI telling them what the pots does
     */
    public AddressedIPotCF(Biasgen biasgen, String name, int address,
            Type type, Sex sex, boolean lowCurrentModeEnabled, boolean enabled, int coarseValue,
            int fineValue, int displayPosition, String tooltipString) {
        this(biasgen);
        numBits=numBiasBits; // overrides IPot value of 24
        setName(name);
        this.setType(type);
        this.setSex(sex);
        this.fineBitValue=fineValue;
        this.coarseBitValue=coarseValue;
        this.currentLevel=lowCurrentModeEnabled?CurrentLevel.Low:CurrentLevel.Normal;
        this.biasEnabled=enabled?BiasEnabled.Enabled:BiasEnabled.Disabled;
        this.displayPosition=displayPosition;
        this.tooltipString=tooltipString;
        this.address=address;
        loadPreferences(); // do this after name is set
        updateBitValue(); // must be after preferences are loaded
      if(chip.getRemoteControl()!=null){
            chip.getRemoteControl().addCommandListener(this, String.format(SETIC+"%s <bitvalue>",getName()), "Set the bitValue of coarse current "+getName());
            chip.getRemoteControl().addCommandListener(this, String.format(SETIF+"%s <bitvalue>",getName()), "Set the bitValue of fine current "+getName());
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
            if(i<(a.length-1)) {
				sb.append("|");
			}
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
                if(s.startsWith(SETIC)){
                    setCoarseBitValue(Integer.parseInt(a));
                }else if(s.startsWith(SETIF)){
                    setFineBitValue(Integer.parseInt(a));
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
        return new AddressedIPotCFGUIControl(this);
    }

    @Override
    public void setBitValue(int value) {
        log.warning("setting the \"bitValue\" of this coarse-fine pot has no effect");
    }

    /** Change fine bit value by ratio from preferred value; can be used for a tweak from a nominal value.
     * If fine bit value is too large, changes coarse bit value as well.
     @param ratio between new current and old value, e.g. 1.1f or 0.9f
     */
    @Override
    public void changeByRatioFromPreferred(float ratio){
        int v = Math.round(getPreferedFineBitValue() * ratio);
        v = v + (ratio >= 1 ? 1 : -1); // ensure at least one step unit of change up or down depending on ratio >1 or <1
        if(v<1){
            v=1; // prevent zero value
        }
//        log.info("changing bit value from "+getFineBitValue()+" to "+v);
//        if (v < 1 ) {
//            setCoarseBitValue(getPreferedCoarseBitValue()+1); // sign inversion on coarse bits means increment bit value here
//                setFineBitValue(Math.round((getPreferedFineBitValue() * ratio) * RATIO_COARSE_CURRENT_STEP));
//        } else if (v > getMaxFineBitValue() ) {
//            setCoarseBitValue(getPreferedCoarseBitValue()-1);
//                setFineBitValue(Math.round((getPreferedFineBitValue() * ratio) / RATIO_COARSE_CURRENT_STEP));
//        }else{
            setFineBitValue(v);
//        }
    }



    public int getFineBitValue() {
        return fineBitValue;
    }

    /** Set the fine control bias bit value
     * @param bufferBitValue the value which has maxBuffeBitValue as maximum and specifies fraction of master bias
     */
    public void setFineBitValue(int fineBitValue) {
        int oldBitValue=this.fineBitValue;
        this.fineBitValue=clippedFineBitValue(fineBitValue);
        updateBitValue();
        if(fineBitValue!=oldBitValue) {
            setChanged();
            notifyObservers(this);
        }
        //log.info("set fine bits: "+getFineBitValue());
    }

    /** returns clipped value of potential new value for buffer bit value, constrained by limits of hardware.
     *
     * @param o candidate new value.
     * @return allowed value.
     */
    protected int clippedFineBitValue(int o){
        int n=o; // new value
        if(o<0) {
			n=0;
		}
        if(o>maxFineBitValue) {
			n=maxFineBitValue;
		}
        return n;
    }

    public int getCoarseBitValue() {
        return coarseBitValue;
    }

    /** Set the course bias bit value.  Note that because of an initial design error, the value of coarse current *decreases* as the bit value increases.
     * The current is nominally the master current for a bit value of 2.
     * @param bufferBitValue the value which has maxBuffeBitValue as maximum and specifies fraction of master bias
     */
    public void setCoarseBitValue(int coarseBitValue) {
        //log.info("set coarse bits: "+coarseBitValue);
        int oldBitValue=this.coarseBitValue;
        this.coarseBitValue=clippedCoarseBitValue(coarseBitValue);
        updateBitValue();
        if(coarseBitValue!=oldBitValue) {
            setChanged();
            notifyObservers(this);
        }
        //log.info("coarse bits set: "+getCoarseBitValue());
    }

    /** returns clipped value of potential new value for buffer bit value, constrained by limits of hardware.
     *
     * @param o candidate new value.
     * @return allowed value.
     */
    protected int clippedCoarseBitValue(int o){
        int n=o; // new value
        if(o<0) {
			n=0;
		}
        if(o>maxCoarseBitValue) {
			n=maxCoarseBitValue;
		}
        return n;
    }

    @Override
    public int getMaxBitValue() {
        return maxBitValue;
    }

    public int getMaxCoarseBitValue() {
        return maxCoarseBitValue;
    }

    public int getMaxFineBitValue() {
        return maxFineBitValue;
    }

    public void updateBitValue(){
        this.bitValue = fineBitValue+(coarseBitValue << (numFineBits));
    }

    @Override
    public int getBitValue(){
        this.bitValue = fineBitValue+(coarseBitValue << (numFineBits));
        //String hex = String.format("%02X",bitValue);
        //log.info("AIPot "+this.getName()+" bit value "+hex);
        return bitValue;
    }

    /** sets the bit value based on desired current and {@link #masterbias} current.
     * Observers are notified if value changes.
     *@param current in amps
     *@return actual float value of current after resolution clipping.
     */
    public float setCoarseCurrent(float current){
        double im=ACTUAL_MASTER_BIAS_CURRENT; //TODO real MasterBias
        setCoarseBitValue(7-(int)Math.round((Math.log(current/im)/Math.log(8))+5));
        return getCoarseCurrent();
    }

    /** Returns estimated coarse current based on master bias current and coarse bit setting
     *
     * @return current in amperes
     */
    public float getCoarseCurrent(){
        double im=ACTUAL_MASTER_BIAS_CURRENT; //TODO real MasterBias
        float i=(float)(im*Math.pow(RATIO_COARSE_CURRENT_STEP, 2-getCoarseBitValue()));
        return i;
    }

    /**
     * Increments coarse current
     *
     * @return true if change was possible, false if coarse current is already
     * maximum value
     */
    public boolean incrementCoarseCurrent() {
        if (getCoarseBitValue() == 0) {
            return false;
        }
        setCoarseBitValue(coarseBitValue - 1);
        return true;
    }

    /**
     * Decrements coarse current
     *
     * @return true if change was possible, false if coarse current is already
     * maximum value
     */
    public boolean decrementCoarseCurrent() {
        if (getCoarseBitValue() == getMaxCoarseBitValue()) {
            return false;
        }
        setCoarseBitValue(coarseBitValue + 1);
        return true;
    }


    /** TODO: real calculations
     */
    public float setFineCurrent(float current){
        float im=getCoarseCurrent();
        float r=current/im;
        setFineBitValue(Math.round(r*(maxFineBitValue+1)));
        return getFineCurrent();
    }

    public float getFineCurrent(){
        float im=getCoarseCurrent();
        float i=(im*getFineBitValue())/(maxFineBitValue+1);
        return i;
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
        if(enabled!=isEnabled()) {
			setChanged();
		}
        if(enabled) {
			biasEnabled=BiasEnabled.Enabled;
		}
		else {
			biasEnabled=BiasEnabled.Disabled;
		}
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
        if(lowCurrentModeEnabled!=isLowCurrentModeEnabled()) {
			setChanged();
		}
        this.currentLevel = lowCurrentModeEnabled?CurrentLevel.Low:CurrentLevel.Normal;
        notifyObservers();
    }

    /** Computes the actual bit pattern to be sent to chip based on configuration values */
    public int computeBinaryRepresentation(){
        int ret=0;
        if(isEnabled()) {
			ret|=enabledMask;
		}
        if(getType()==Pot.Type.NORMAL) {
			ret|=typeMask;
		}
        if(getSex()==Pot.Sex.N) {
			ret|=sexMask;
		}
        if(!isLowCurrentModeEnabled()) {
			ret|=lowCurrentModeMask;
		}
        int sh;
        sh=Integer.numberOfTrailingZeros(bitFineMask);
        ret|=fineBitValue<<sh;
        sh=Integer.numberOfTrailingZeros(bitCoarseMask);
        ret|=computeBinaryInverse(coarseBitValue, numCoarseBits)<<sh; // The coarse bits are reversed (this was a mistake) so we need to mirror them here before we sent them.

        //System.out.println(toString() + " byte repres " + Integer.toHexString(ret));

        return ret;
    }

    /** Computes the bit pattern to be sent to chip based on configuration values,
     * without changing the coarse value at all and assuming higher number is higher bias. */
    public int computeCleanBinaryRepresentation(){
        int ret=0;
        if(isEnabled()) {
			ret|=enabledMask;
		}
        if(getType()==Pot.Type.NORMAL) {
			ret|=typeMask;
		}
        if(getSex()==Pot.Sex.N) {
			ret|=sexMask;
		}
        if(!isLowCurrentModeEnabled()) {
			ret|=lowCurrentModeMask;
		}
        int sh;
        sh=Integer.numberOfTrailingZeros(bitFineMask);
        ret|=fineBitValue<<sh;
        sh=Integer.numberOfTrailingZeros(bitCoarseMask);
        ret|=(7-coarseBitValue)<<sh;

        return ret;
    }

    public int computeInverseBinaryRepresentation(){
        int length = 16;
        int ret=computeBinaryRepresentation();
        int out=0;
        for(int i=0; i<length; i++){
            out |= (((ret&(0x0001<<(length-1-i)))<<i)>>(length-1-i));
        }
        return out;
    }

    /** The coarse bits are reversed (this was a mistake) so we need to mirror them here before we sent them.
     * @param value the bits in
     * @param lenth the number of bits
     * @return the bits mirrored
     */
    protected int computeBinaryInverse(int value, int length){
        int out=0;
        for(int i=0; i<length; i++){
            out |= (((value&(0x0001<<(length-1-i)))<<i)>>(length-1-i));
        }
        return out;
    }

    private byte[] bytes=null;

    /** Computes the actual bit pattern to be sent to chip based on configuration values */
    @Override
	public byte[] getBinaryRepresentation() {
        int n=3;
        if(bytes==null) {
			bytes=new byte[n];
		}
        int val = computeBinaryRepresentation();
        int k=1;
        for(int i=bytes.length-2;i>=0;i--){
            bytes[k++]=(byte)(0xff&(val>>>(i*8)));
        }
        bytes[0]=(byte)(0xff&address);
        return bytes;
    }

    public byte[] getCleanBinaryRepresentation() {
        int n=3;
        if(bytes==null) {
			bytes=new byte[n];
		}
        int val = computeCleanBinaryRepresentation();
        int k=1;
        for(int i=bytes.length-2;i>=0;i--){
            bytes[k++]=(byte)(0xff&(val>>>(i*8)));
        }
        bytes[0]=(byte)(0xff&address);
        return bytes;
	}

    /** Returns the String key by which this pot is known in the Preferences. For IPot's, this
     * name is the Chip simple class name followed by IPot.<potName>, e.g. "Tmpdiff128.IPot.Pr".
     * @return preferences key
     */
    @Override
    protected String prefsKey() {
        return biasgen.getChip().getClass().getSimpleName() + ".AddressedIPotCF." + name;
    }

    static String KEY_BITVALUE_COARSE="BitValueCoarse",
            KEY_BITVALUE_FINE="BitValueFine",
            KEY_SEX="Sex",
            KEY_TYPE="Type",
            KEY_LOWCURRENT_ENABLED="LowCurrent",
            KEY_ENABLED="Enabled";
    static String SEP=".";

    /** stores as a preference the bit value */
    @Override
    public void storePreferences(){
        String s=prefsKey()+SEP;
        prefs.putInt(s+KEY_BITVALUE_COARSE,getCoarseBitValue());
        prefs.putInt(s+KEY_BITVALUE_FINE,getFineBitValue());
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
        int v=prefs.getInt(s+KEY_BITVALUE_COARSE,0);
        setCoarseBitValue(v);
        v=prefs.getInt(s+KEY_BITVALUE_FINE,0);
        setFineBitValue(v);
        updateBitValue();
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
            if(!key.startsWith(base)) {
				return;
			}
            String val = e.getNewValue();
//            log.info("key="+key+" value="+val);
            if (key.equals(base + KEY_BITVALUE_FINE)) {
                if(getFineBitValue()!=Integer.parseInt(val)){
                    log.info(base+" fine bit value change from "+getFineBitValue()+" to "+ Integer.parseInt(val)+" from preferences");
                }
                setFineBitValue(Integer.parseInt(val));
            } else if (key.equals(base + KEY_BITVALUE_COARSE)) {
                if(getCoarseBitValue()!=Integer.parseInt(val)){
                    log.info("coarse bit value change from preferences");
                }
                setCoarseBitValue(Integer.parseInt(val));
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
    public int getPreferedCoarseBitValue(){
        String key=prefsKey()+SEP+KEY_BITVALUE_COARSE;
        int v=prefs.getInt(key,0);
        return v;
    }

    /** returns the preference value of the bias current.
     */
    public int getPreferedFineBitValue(){
        String key=prefsKey()+SEP+KEY_BITVALUE_FINE;
        int v=prefs.getInt(key,0);
        return v;
    }

    @Override
	public String toString(){
        return super.toString()+" Sex="+getSex()+" Type="+getType()+" enabled="+isEnabled()+" lowCurrentModeEnabled="+isLowCurrentModeEnabled()+" coarseBitValue="+coarseBitValue+" fineBitValue="+fineBitValue;
    }

    public CurrentLevel getCurrentLevel() {
        return currentLevel;
    }

    /** Sets whether this is a normal type or low current bias which uses shifted source */
    public void setCurrentLevel(CurrentLevel currentLevel) {
        if(currentLevel==null) {
			return;
		}
        if(currentLevel!=this.currentLevel) {
			setChanged();
		}
        this.currentLevel = currentLevel;
        notifyObservers();
    }

    /** Overrides super of type (NORNAL or CASCODE) to call observers */
    @Override
    public void setType(Type type) {
        if(type==null) {
			return;
		}
        if(type!=this.type) {
			setChanged();
		}
        this.type = type;
        notifyObservers();
    }

    /** Overrides super of setSex (N or P) to call observers */
    @Override
    public void setSex(Sex sex) {
        if(sex==null) {
			return;
		}
        if(sex!=this.sex) {
			setChanged();
		}
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
        if(!(obj instanceof AddressedIPotCF)) {
			return false;
		}
        AddressedIPotCF other=(AddressedIPotCF)obj;
        if(!getName().equals(other.getName())) {
			return false;
		}
        if(getCoarseBitValue()!=other.getCoarseBitValue()) {
			return false;
		}
        if(getFineBitValue()!=other.getFineBitValue()) {
			return false;
		}
        if(getSex()!=other.getSex()) {
			return false;
		}
        if(getType()!=other.getType()) {
			return false;
		}
        if(getCurrentLevel()!=other.getCurrentLevel()) {
			return false;
		}
        if(isEnabled()!=other.isEnabled()) {
			return false;
		}
        return true;
    }



}
