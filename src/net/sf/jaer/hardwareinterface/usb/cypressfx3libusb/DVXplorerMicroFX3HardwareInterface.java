/*
 * Copyright (C) 2023 Pei Haoxiang.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 */
package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

import org.usb4java.Device;

/**
 * DVXplorer Mini / Micro (Cypress CX3 MIPI). Same VID/PID {@code 152a:8419} as
 * classic FX3 {@link DVXplorerFX3HardwareInterface}; factory selects this class
 * from {@code bcdDevice} high byte {@link #DEVICE_TYPE_CX3_MIPI} (no
 * {@code LibUsb.open}). Classic Samsung SPI lives only on the FX3 subclass.
 *
 * @see DVXplorerFX3HardwareInterface
 */
public class DVXplorerMicroFX3HardwareInterface extends DVXplorerFX3HardwareInterface {

    public DVXplorerMicroFX3HardwareInterface(final Device device) {
        super(device);
    }

    @Override
    public boolean isMipiCX3Device() {
        return true;
    }

    @Override
    protected String friendlyUnopenedTypeName() {
        return "DVXplorer Micro";
    }
}
