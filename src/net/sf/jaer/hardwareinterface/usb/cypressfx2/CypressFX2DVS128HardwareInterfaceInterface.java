/*
 * Copyright (C) 2019 tobid.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package net.sf.jaer.hardwareinterface.usb.cypressfx2;

import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.HasLEDControl;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.HasResettablePixelArray;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.HasSyncEventOutput;

/**
 * Interface to DVS128 that is implemented by both Thesycon and libusb drivers
 * @author tobid
 */
public interface CypressFX2DVS128HardwareInterfaceInterface extends HasLEDControl, HasResettablePixelArray, HasSyncEventOutput {

    public String FIRMWARE_FILENAME_DVS128_XSVF = "/net/sf/jaer/hardwareinterface/usb/cypressfx2/dvs128CPLD.xsvf";
    /**
     * SYNC events are detected when this bit mask is detected in the input event stream.
     *
     * @see HasSyncEventOutput
     */
    public int SYNC_EVENT_BITMASK = 0x8000;

    /**
     * Returns the last set LED state
     *
     * @param led
     *            ignored
     * @return the last set state, or UNKNOWN if never set from host.
     */
    public LEDState getLEDState(final int led);

    /**
     * Returns 1
     *
     * @return 1
     */
    public int getNumLEDs();

    public boolean isArrayReset();

    public boolean isSyncEventEnabled();

    /** Overrides open() to also set sync event mode. */
    public void open() throws HardwareInterfaceException;

    public void resetTimestamps();

    /**
     * set the pixel array reset
     *
     * @param value
     *            true to reset the pixels, false to let them run normally
     */
    public void setArrayReset(final boolean value);

    /**
     * Sets the LED state. Throws no exception, just prints warning on hardware exceptions.
     *
     * @param led
     *            only 0 in this case
     * @param state
     *            the new state
     */
    public void setLEDState(final int led, final LEDState state);

    public void setSyncEventEnabled(final boolean yes);

    /**
     * Starts reader buffer pool thread and enables in endpoints for AEs. This method is overridden to construct
     * our own reader with its translateEvents method
     */
    public void startAEReader() throws HardwareInterfaceException;
    
}
