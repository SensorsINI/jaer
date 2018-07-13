/*
 * Copyright (C) 2018 tobid.
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
package org.ine.telluride.jaer.tell2018;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2DMouseAdaptor;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;

/**
 * Live pendulum modeling for studying satellite tracking. 
 * @author tobid, gregc, yiannisa, rohit, emilyh, jenh
 */
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
@Description("Live pendulum modeling for studying satellite tracking (Telluride 2018")
public class PendulumTracker extends EventFilter2DMouseAdaptor{

    private RectangularClusterTracker rct=new RectangularClusterTracker(chip);
    
    public PendulumTracker(AEChip chip) {
        super(chip);
    }
    
    

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        return in;
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }
    
    private class Pendulum{
        float freqHz, amplDeg, phaseDeg, fulcrumX, fulcrumY;
        
    }
}
