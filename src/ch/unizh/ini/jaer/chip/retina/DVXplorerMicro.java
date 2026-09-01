/*
 * Copyright (C) 2023 Pei Haoxiang.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 */
package ch.unizh.ini.jaer.chip.retina;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.UsbDevice;
import net.sf.jaer.UsbDevices;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.CypressFX3;
import net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.DVXplorerFX3HardwareInterface;

/**
 * DVXplorer Mini / Micro (CX3 MIPI, 640×480). Same USB VID/PID as classic
 * {@link DVXplorer}; the factory binds {@code DVXplorerMicroFX3HardwareInterface}
 * from {@code bcdDevice} type 4. Users can also pick this AEChip from Customize
 * (Davis346 / SciDVS pattern). Note: This is the same Samsung sensor  DVXplorer, 
 * but with different simplified camera electronics. 
 * The Micro does not timestamp individual events in the cameras,
 * but instead on the host computer, 
 * by packet bundles.
 */
@Description("DVXplorer Mini/Micro, 640x480, CX3 MIPI (iniVation); same VID/PID as DVXplorer")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
@UsbDevices({
    @UsbDevice(vid = CypressFX3.VID, pid = DVXplorerFX3HardwareInterface.PID_FX3)
})
public class DVXplorerMicro extends DVXplorer {

    public DVXplorerMicro() {
        super();
        setName("DVXplorerMicro");
    }

    public DVXplorerMicro(final HardwareInterface hardwareInterface) {
        this();
        setHardwareInterface(hardwareInterface);
    }

    @Override
    public boolean isMipiCX3Device() {
        return true;
    }
}
