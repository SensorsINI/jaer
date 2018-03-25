/*
 * Copyright (C) 2018 minliu.
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
package ch.unizh.ini.jaer.projects.minliu;

import ch.unizh.ini.jaer.projects.rbodo.opticalflow.AbstractMotionFlowIMU;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;

/**
 *
 * @author minliu
 */
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)

public class VOGTFlow extends AbstractMotionFlowIMU {

    public VOGTFlow(AEChip chip) {
        super(chip);
        resetFilter();
    }

    @Override
    public synchronized void resetFilter() {
        super.resetFilter(); //To change body of generated methods, choose Tools | Templates.
    }
    
    @Override
    public EventPacket filterPacket(EventPacket in) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
