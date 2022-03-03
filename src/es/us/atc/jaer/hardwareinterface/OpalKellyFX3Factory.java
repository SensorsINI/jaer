/*
 * Copyright (C) 2021 arios.
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
package es.us.atc.jaer.hardwareinterface;

import com.opalkelly.frontpanel.okFrontPanel;
import de.thesycon.usbio.PnPNotifyInterface;
import java.util.logging.Logger;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.HardwareInterfaceFactoryInterface;

/**
 *
 * @author Antonio Ríos, University of Seville, arios@us.es, 01/02/2021
 */
public class OpalKellyFX3Factory implements HardwareInterfaceFactoryInterface, PnPNotifyInterface{

    final static Logger LOG = Logger.getLogger("OpalKellyFX3Factory");
    // TODO need to change this value
    private final String GUID; 

    public OpalKellyFX3Factory() {
        System.loadLibrary("okjFrontPanel");
        this.GUID = "{c4caf39f-201c-46d2-813b-9f6542cc7686}";
    }
    
    @Override
    public int getNumInterfacesAvailable() {
        okFrontPanel opalkelly = new okFrontPanel();
        int numInterfacesAvaible = opalkelly.GetDeviceCount();
        opalkelly.delete();
        return numInterfacesAvaible;
    }
    
    public static OpalKellyFX3Factory instance()
    {
        return new OpalKellyFX3Factory();
    }

    @Override
    public HardwareInterface getFirstAvailableInterface() throws HardwareInterfaceException {
        return getInterface(0);
    }

    @Override
    public HardwareInterface getInterface(int n) throws HardwareInterfaceException {
        return new OpalKellyFX3Monitor();
    }

    @Override
    public String getGUID() {
        return this.GUID;
    }

    @Override
    public void onAdd() {
        LOG.info("Opal Kelly FX3 device added");
    }

    @Override
    public void onRemove() {
        LOG.info("Opal Kelly FX3 device removed");
    }
    
}
