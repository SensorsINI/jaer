/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.chip.dvs320;

import java.beans.*;

/**
 *
 * @author tobi
 */
public class ADCHardwareInterfaceProxyBeanInfo extends SimpleBeanInfo {

    // Bean descriptor//GEN-FIRST:BeanDescriptor
    /*lazy BeanDescriptor*/
    private static BeanDescriptor getBdescriptor(){
        BeanDescriptor beanDescriptor = new BeanDescriptor  ( ch.unizh.ini.jaer.chip.dvs320.ADCHardwareInterfaceProxy.class , null ); // NOI18N//GEN-HEADEREND:BeanDescriptor

    // Here you can add code for customizing the BeanDescriptor.

        return beanDescriptor;     }//GEN-LAST:BeanDescriptor


    // Property identifiers//GEN-FIRST:Properties
    private static final int PROPERTY_ADCchannel = 0;
    private static final int PROPERTY_ADCEnabled = 1;
    private static final int PROPERTY_chipReset = 2;
    private static final int PROPERTY_hw = 3;
    private static final int PROPERTY_idleTime = 4;
    private static final int PROPERTY_maxADCchannel = 5;
    private static final int PROPERTY_maxIdleTime = 6;
    private static final int PROPERTY_maxRefOffTime = 7;
    private static final int PROPERTY_maxRefOnTime = 8;
    private static final int PROPERTY_maxTrackTime = 9;
    private static final int PROPERTY_minADCchannel = 10;
    private static final int PROPERTY_minIdleTime = 11;
    private static final int PROPERTY_minRefOffTime = 12;
    private static final int PROPERTY_minRefOnTime = 13;
    private static final int PROPERTY_minTrackTime = 14;
    private static final int PROPERTY_refOffTime = 15;
    private static final int PROPERTY_refOnTime = 16;
    private static final int PROPERTY_scanContinuouslyEnabled = 17;
    private static final int PROPERTY_scanX = 18;
    private static final int PROPERTY_scanY = 19;
    private static final int PROPERTY_select5Tbuffer = 20;
    private static final int PROPERTY_trackTime = 21;
    private static final int PROPERTY_useCalibration = 22;

    // Property array 
    /*lazy PropertyDescriptor*/
    private static PropertyDescriptor[] getPdescriptor(){
        PropertyDescriptor[] properties = new PropertyDescriptor[23];
    
        try {
            properties[PROPERTY_ADCchannel] = new PropertyDescriptor ( "ADCchannel", ch.unizh.ini.jaer.chip.dvs320.ADCHardwareInterfaceProxy.class, "getADCchannel", "setADCchannel" ); // NOI18N
            properties[PROPERTY_ADCchannel].setShortDescription ( "The ADC channel number. Must be consistent with chip output mux choice." );
            properties[PROPERTY_ADCEnabled] = new PropertyDescriptor ( "ADCEnabled", ch.unizh.ini.jaer.chip.dvs320.ADCHardwareInterfaceProxy.class, "isADCEnabled", "setADCEnabled" ); // NOI18N
            properties[PROPERTY_ADCEnabled].setShortDescription ( "Enables the ADC for conversion and transfer of samples to the host" );
            properties[PROPERTY_chipReset] = new PropertyDescriptor ( "chipReset", ch.unizh.ini.jaer.chip.dvs320.ADCHardwareInterfaceProxy.class, "isChipReset", null ); // NOI18N
            properties[PROPERTY_hw] = new PropertyDescriptor ( "hw", ch.unizh.ini.jaer.chip.dvs320.ADCHardwareInterfaceProxy.class, "getHw", "setHw" ); // NOI18N
            properties[PROPERTY_idleTime] = new PropertyDescriptor ( "idleTime", ch.unizh.ini.jaer.chip.dvs320.ADCHardwareInterfaceProxy.class, "getIdleTime", "setIdleTime" ); // NOI18N
            properties[PROPERTY_idleTime].setShortDescription ( "The idle time in us  after each AD conversion" );
            properties[PROPERTY_maxADCchannel] = new PropertyDescriptor ( "maxADCchannel", ch.unizh.ini.jaer.chip.dvs320.ADCHardwareInterfaceProxy.class, "getMaxADCchannel", null ); // NOI18N
            properties[PROPERTY_maxIdleTime] = new PropertyDescriptor ( "maxIdleTime", ch.unizh.ini.jaer.chip.dvs320.ADCHardwareInterfaceProxy.class, "getMaxIdleTime", null ); // NOI18N
            properties[PROPERTY_maxRefOffTime] = new PropertyDescriptor ( "maxRefOffTime", ch.unizh.ini.jaer.chip.dvs320.ADCHardwareInterfaceProxy.class, "getMaxRefOffTime", null ); // NOI18N
            properties[PROPERTY_maxRefOnTime] = new PropertyDescriptor ( "maxRefOnTime", ch.unizh.ini.jaer.chip.dvs320.ADCHardwareInterfaceProxy.class, "getMaxRefOnTime", null ); // NOI18N
            properties[PROPERTY_maxTrackTime] = new PropertyDescriptor ( "maxTrackTime", ch.unizh.ini.jaer.chip.dvs320.ADCHardwareInterfaceProxy.class, "getMaxTrackTime", null ); // NOI18N
            properties[PROPERTY_minADCchannel] = new PropertyDescriptor ( "minADCchannel", ch.unizh.ini.jaer.chip.dvs320.ADCHardwareInterfaceProxy.class, "getMinADCchannel", null ); // NOI18N
            properties[PROPERTY_minIdleTime] = new PropertyDescriptor ( "minIdleTime", ch.unizh.ini.jaer.chip.dvs320.ADCHardwareInterfaceProxy.class, "getMinIdleTime", null ); // NOI18N
            properties[PROPERTY_minRefOffTime] = new PropertyDescriptor ( "minRefOffTime", ch.unizh.ini.jaer.chip.dvs320.ADCHardwareInterfaceProxy.class, "getMinRefOffTime", null ); // NOI18N
            properties[PROPERTY_minRefOnTime] = new PropertyDescriptor ( "minRefOnTime", ch.unizh.ini.jaer.chip.dvs320.ADCHardwareInterfaceProxy.class, "getMinRefOnTime", null ); // NOI18N
            properties[PROPERTY_minTrackTime] = new PropertyDescriptor ( "minTrackTime", ch.unizh.ini.jaer.chip.dvs320.ADCHardwareInterfaceProxy.class, "getMinTrackTime", null ); // NOI18N
            properties[PROPERTY_refOffTime] = new PropertyDescriptor ( "refOffTime", ch.unizh.ini.jaer.chip.dvs320.ADCHardwareInterfaceProxy.class, "getRefOffTime", "setRefOffTime" ); // NOI18N
            properties[PROPERTY_refOffTime].setShortDescription ( "Settling time for difference amplifier in us" );
            properties[PROPERTY_refOnTime] = new PropertyDescriptor ( "refOnTime", ch.unizh.ini.jaer.chip.dvs320.ADCHardwareInterfaceProxy.class, "getRefOnTime", "setRefOnTime" ); // NOI18N
            properties[PROPERTY_refOnTime].setShortDescription ( "Settle time for reference current in us" );
            properties[PROPERTY_scanContinuouslyEnabled] = new PropertyDescriptor ( "scanContinuouslyEnabled", ch.unizh.ini.jaer.chip.dvs320.ADCHardwareInterfaceProxy.class, "isScanContinuouslyEnabled", "setScanContinuouslyEnabled" ); // NOI18N
            properties[PROPERTY_scanContinuouslyEnabled].setDisplayName ( "Scan continuosly" );
            properties[PROPERTY_scanContinuouslyEnabled].setShortDescription ( "Enable to scan contnuously (normal mode); disable to freeze scanner on one pixel" );
            properties[PROPERTY_scanX] = new PropertyDescriptor ( "scanX", ch.unizh.ini.jaer.chip.dvs320.ADCHardwareInterfaceProxy.class, "getScanX", "setScanX" ); // NOI18N
            properties[PROPERTY_scanX].setShortDescription ( "column scanner column, from left" );
            properties[PROPERTY_scanY] = new PropertyDescriptor ( "scanY", ch.unizh.ini.jaer.chip.dvs320.ADCHardwareInterfaceProxy.class, "getScanY", "setScanY" ); // NOI18N
            properties[PROPERTY_scanY].setShortDescription ( "row scanner row, from bottom" );
            properties[PROPERTY_select5Tbuffer] = new PropertyDescriptor ( "select5Tbuffer", ch.unizh.ini.jaer.chip.dvs320.ADCHardwareInterfaceProxy.class, "isSelect5Tbuffer", "setSelect5Tbuffer" ); // NOI18N
            properties[PROPERTY_select5Tbuffer].setShortDescription ( "Selects 5 transistor buffer. False uses source follower buffer before track/hold" );
            properties[PROPERTY_trackTime] = new PropertyDescriptor ( "trackTime", ch.unizh.ini.jaer.chip.dvs320.ADCHardwareInterfaceProxy.class, "getTrackTime", "setTrackTime" ); // NOI18N
            properties[PROPERTY_trackTime].setShortDescription ( "Settling time for reading photocurrent after pixel selection in us" );
            properties[PROPERTY_useCalibration] = new PropertyDescriptor ( "useCalibration", ch.unizh.ini.jaer.chip.dvs320.ADCHardwareInterfaceProxy.class, "isUseCalibration", "setUseCalibration" ); // NOI18N
            properties[PROPERTY_useCalibration].setShortDescription ( "true=use on-chip differencing amp to measure calibration value and subtract from each pixel reading" );
        }
        catch(IntrospectionException e) {
            e.printStackTrace();
        }//GEN-HEADEREND:Properties

    // Here you can add code for customizing the properties array.

        return properties;     }//GEN-LAST:Properties

    // EventSet identifiers//GEN-FIRST:Events

    // EventSet array
    /*lazy EventSetDescriptor*/
    private static EventSetDescriptor[] getEdescriptor(){
        EventSetDescriptor[] eventSets = new EventSetDescriptor[0];//GEN-HEADEREND:Events

    // Here you can add code for customizing the event sets array.

        return eventSets;     }//GEN-LAST:Events

    // Method identifiers//GEN-FIRST:Methods
    private static final int METHOD_resetTimestamps0 = 0;

    // Method array 
    /*lazy MethodDescriptor*/
    private static MethodDescriptor[] getMdescriptor(){
        MethodDescriptor[] methods = new MethodDescriptor[1];
    
        try {
            methods[METHOD_resetTimestamps0] = new MethodDescriptor(ch.unizh.ini.jaer.chip.dvs320.ADCHardwareInterfaceProxy.class.getMethod("resetTimestamps", new Class[] {})); // NOI18N
            methods[METHOD_resetTimestamps0].setDisplayName ( "" );
        }
        catch( Exception e) {}//GEN-HEADEREND:Methods

    // Here you can add code for customizing the methods array.
    
        return methods;     }//GEN-LAST:Methods

    private static java.awt.Image iconColor16 = null;//GEN-BEGIN:IconsDef
    private static java.awt.Image iconColor32 = null;
    private static java.awt.Image iconMono16 = null;
    private static java.awt.Image iconMono32 = null;//GEN-END:IconsDef
    private static String iconNameC16 = null;//GEN-BEGIN:Icons
    private static String iconNameC32 = null;
    private static String iconNameM16 = null;
    private static String iconNameM32 = null;//GEN-END:Icons

    private static final int defaultPropertyIndex = -1;//GEN-BEGIN:Idx
    private static final int defaultEventIndex = -1;//GEN-END:Idx

    
//GEN-FIRST:Superclass

    // Here you can add code for customizing the Superclass BeanInfo.

//GEN-LAST:Superclass
	
    /**
     * Gets the bean's <code>BeanDescriptor</code>s.
     * 
     * @return BeanDescriptor describing the editable
     * properties of this bean.  May return null if the
     * information should be obtained by automatic analysis.
     */
    public BeanDescriptor getBeanDescriptor() {
	return getBdescriptor();
    }

    /**
     * Gets the bean's <code>PropertyDescriptor</code>s.
     * 
     * @return An array of PropertyDescriptors describing the editable
     * properties supported by this bean.  May return null if the
     * information should be obtained by automatic analysis.
     * <p>
     * If a property is indexed, then its entry in the result array will
     * belong to the IndexedPropertyDescriptor subclass of PropertyDescriptor.
     * A client of getPropertyDescriptors can use "instanceof" to check
     * if a given PropertyDescriptor is an IndexedPropertyDescriptor.
     */
    public PropertyDescriptor[] getPropertyDescriptors() {
	return getPdescriptor();
    }

    /**
     * Gets the bean's <code>EventSetDescriptor</code>s.
     * 
     * @return  An array of EventSetDescriptors describing the kinds of 
     * events fired by this bean.  May return null if the information
     * should be obtained by automatic analysis.
     */
    public EventSetDescriptor[] getEventSetDescriptors() {
	return getEdescriptor();
    }

    /**
     * Gets the bean's <code>MethodDescriptor</code>s.
     * 
     * @return  An array of MethodDescriptors describing the methods 
     * implemented by this bean.  May return null if the information
     * should be obtained by automatic analysis.
     */
    public MethodDescriptor[] getMethodDescriptors() {
	return getMdescriptor();
    }

    /**
     * A bean may have a "default" property that is the property that will
     * mostly commonly be initially chosen for update by human's who are 
     * customizing the bean.
     * @return  Index of default property in the PropertyDescriptor array
     * 		returned by getPropertyDescriptors.
     * <P>	Returns -1 if there is no default property.
     */
    public int getDefaultPropertyIndex() {
        return defaultPropertyIndex;
    }

    /**
     * A bean may have a "default" event that is the event that will
     * mostly commonly be used by human's when using the bean. 
     * @return Index of default event in the EventSetDescriptor array
     *		returned by getEventSetDescriptors.
     * <P>	Returns -1 if there is no default event.
     */
    public int getDefaultEventIndex() {
        return defaultEventIndex;
    }

    /**
     * This method returns an image object that can be used to
     * represent the bean in toolboxes, toolbars, etc.   Icon images
     * will typically be GIFs, but may in future include other formats.
     * <p>
     * Beans aren't required to provide icons and may return null from
     * this method.
     * <p>
     * There are four possible flavors of icons (16x16 color,
     * 32x32 color, 16x16 mono, 32x32 mono).  If a bean choses to only
     * support a single icon we recommend supporting 16x16 color.
     * <p>
     * We recommend that icons have a "transparent" background
     * so they can be rendered onto an existing background.
     *
     * @param  iconKind  The kind of icon requested.  This should be
     *    one of the constant values ICON_COLOR_16x16, ICON_COLOR_32x32, 
     *    ICON_MONO_16x16, or ICON_MONO_32x32.
     * @return  An image object representing the requested icon.  May
     *    return null if no suitable icon is available.
     */
    public java.awt.Image getIcon(int iconKind) {
        switch ( iconKind ) {
        case ICON_COLOR_16x16:
            if ( iconNameC16 == null )
                return null;
            else {
                if( iconColor16 == null )
                    iconColor16 = loadImage( iconNameC16 );
                return iconColor16;
            }
        case ICON_COLOR_32x32:
            if ( iconNameC32 == null )
                return null;
            else {
                if( iconColor32 == null )
                    iconColor32 = loadImage( iconNameC32 );
                return iconColor32;
            }
        case ICON_MONO_16x16:
            if ( iconNameM16 == null )
                return null;
            else {
                if( iconMono16 == null )
                    iconMono16 = loadImage( iconNameM16 );
                return iconMono16;
            }
        case ICON_MONO_32x32:
            if ( iconNameM32 == null )
                return null;
            else {
                if( iconMono32 == null )
                    iconMono32 = loadImage( iconNameM32 );
                return iconMono32;
            }
	default: return null;
        }
    }

}

