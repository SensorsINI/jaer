/* 
 * Copyright (C) 2017 Tobi.
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
package ch.unizh.ini.jaer.projects.humanpose;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.eventprocessing.FilterChain;

/**
 * Concrete CNN that classifies frames from DAVIS either of APS of DVS data
 *
 * @author Tobi, Gemma, Enrico
 */
@Description("Classifies frames using CNN from DAVIS APS or DVS frames")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class DavisClassifierCNNProcessor extends AbstractDavisCNNProcessor {

    public DavisClassifierCNNProcessor(AEChip chip) {
        super(chip);
        dvsFramer = new DvsFramerSingleFrame(chip); // must be replaced by the right subsampler object by subclasses TODO not clean
        getEnclosedFilterChain().add(dvsFramer); // only for control, we iterate with it here using the events we recieve by directly calling addEvent in the event processing loop, not by using the subsampler filterPacket method
        setEnclosedFilterChain(getEnclosedFilterChain());
        initFilter();
    }

}
