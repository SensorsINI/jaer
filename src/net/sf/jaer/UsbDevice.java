/*
 * UsbDevice.java
 */
package net.sf.jaer;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * One USB vendor/product ID pair that an {@link net.sf.jaer.chip.AEChip} can
 * use. Prefer referencing {@code static final} constants from the matching
 * {@link net.sf.jaer.hardwareinterface.HardwareInterface} class rather than
 * raw hex literals.
 *
 * @see UsbDevices
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface UsbDevice {

    /** USB vendor ID */
    short vid();

    /** USB product ID */
    short pid();
}
